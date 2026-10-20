// ─── VODOSTAJI REKA (izvor: RHMZ Srbije, hidmet.gov.rs) ───
// Server preuzima podatke umesto aplikacije, pa RHMZ dobija isti broj zahteva
// bez obzira na broj korisnika:
//   * osveziVodostaje: jednom na sat XML sa svim stanicama -> Vodostaji/sve
//   * detaljiStanice: merenja na sat za 7 dana, keširana 1 h u VodostajiDetalji/{hmId}
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { Timestamp } = require("firebase-admin/firestore");

const XML_URL = "https://www.hidmet.gov.rs/latin/osmotreni/stanje_voda.xml";
const NRT_URL = (hmId) => `https://www.hidmet.gov.rs/latin/osmotreni/nrt_tabela_grafik.php?hm_id=${hmId}&period=7`;
const ZAGLAVLJA = { "User-Agent": "FishSpot/1.0 (aplikacija za ribolov; podaci RHMZ Srbije)" };
const DETALJI_VAZE_MS = 60 * 60 * 1000;

async function preuzmi(url) {
    const odgovor = await fetch(url, { headers: ZAGLAVLJA, signal: AbortSignal.timeout(20000) });
    if (!odgovor.ok) throw new Error(`RHMZ je vratio ${odgovor.status} za ${url}`);
    return odgovor.text();
}

// "-153" -> -153, a "*", "-" i prazno -> null
function broj(tekst) {
    const t = (tekst ?? "").trim().replace(",", ".");
    if (!/^-?\d+(\.\d+)?$/.test(t)) return null;
    return Number(t);
}

// RHMZ piše ćirilicom: "пораст", "опадање", "стагнација", a "-" kad nema podatka
function tendencija(tekst) {
    const t = (tekst ?? "").toLowerCase();
    if (t.includes("пораст") || t.includes("porast")) return "raste";
    if (t.includes("опад") || t.includes("opad")) return "opada";
    if (t.includes("стагн") || t.includes("stagn")) return "miruje";
    return "nepoznato";
}

// Parsira Atom XML sa stanjem voda u listu stanica
function parsirajStanjeVoda(xml) {
    const naslov = /<feed[\s\S]*?<title>([\s\S]*?)<\/title>/.exec(xml)?.[1] ?? "";
    const merenje = /(\d{2}\.\d{2}\.\d{4})\.?\s*vreme:\s*(\d{1,2}:\d{2})/.exec(naslov);

    const stanice = [];
    for (const [, entry] of xml.matchAll(/<entry>([\s\S]*?)<\/entry>/g)) {
        const naslovStanice = /<title>\s*Reka:\s*(.+?)\s+-\s+Hidrološka stanica:\s*(.+?)\s*<\/title>/.exec(entry);
        const hmId = /hm_id=(\d+)/.exec(entry)?.[1];
        const sazetak = /<summary>([\s\S]*?)<\/summary>/.exec(entry)?.[1] ?? "";
        if (!naslovStanice || !hmId) continue;

        const polje = (ime) => new RegExp(`${ime}:\\s*([^;]*?)\\s*(?:cm|m3/s|\\(°C\\))?;`).exec(sazetak)?.[1];
        stanice.push({
            id: hmId,
            reka: naslovStanice[1].trim(),
            stanica: naslovStanice[2].trim(),
            vodostaj: broj(polje("Vodostaj")),
            promena: broj(polje("Promena vodostaja")),
            proticaj: broj(polje("Proticaj vode")),
            temperatura: broj(polje("Temperatura vode")),
            tendencija: tendencija(polje("Tendencija vodostaja")),
        });
    }
    return { merenje: merenje ? `${merenje[1]}. u ${merenje[2]}` : naslov.trim(), stanice };
}

// Parsira stranicu sa merenjima na sat; vraća hronološki: [{ t: "20.10.2026 15:00", v: -149 }, ...]
function parsirajMerenja(html) {
    const tekst = html.replace(/<[^>]+>/g, " ").replace(/&nbsp;/g, " ").replace(/\s+/g, " ");
    const merenja = [...tekst.matchAll(/(\d{2}\.\d{2}\.\d{4})\s+(\d{2}:\d{2})\s+(-?\d+)/g)]
        .map((m) => ({ t: `${m[1]} ${m[2]}`, v: Number(m[3]) }));
    return merenja.reverse();
}

async function azurirajVodostaje(db = admin.firestore()) {
    const { merenje, stanice } = parsirajStanjeVoda(await preuzmi(XML_URL));
    if (stanice.length < 20) {
        // RHMZ je verovatno promenio format: ne gazimo dobre podatke praznom listom
        throw new Error(`Pronađeno samo ${stanice.length} stanica, proveri format RHMZ XML-a`);
    }
    await db.collection("Vodostaji").doc("sve").set({
        merenje,
        stanice,
        brojStanica: stanice.length,
        azurirano: Timestamp.now(),
        izvor: "RHMZ Srbije",
    });
    return stanice.length;
}

exports.osveziVodostaje = functions.pubsub
    .schedule("every 60 minutes")
    .timeZone("Europe/Belgrade")
    .onRun(async () => {
        const broj = await azurirajVodostaje();
        console.log(`Vodostaji osveženi: ${broj} stanica`);
        return null;
    });

// Aplikacija traži merenja za jednu stanicu; RHMZ se pita najviše jednom na sat po stanici
exports.detaljiStanice = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Moraš biti prijavljen.");
    }
    const hmId = String(data?.hmId ?? "");
    if (!/^\d{3,8}$/.test(hmId)) {
        throw new functions.https.HttpsError("invalid-argument", "Neispravan ID stanice.");
    }

    const ref = admin.firestore().collection("VodostajiDetalji").doc(hmId);
    const kes = await ref.get();
    if (kes.exists && Date.now() - kes.data().preuzeto.toMillis() < DETALJI_VAZE_MS) {
        return { merenja: kes.data().merenja, preuzeto: kes.data().preuzeto.toMillis() };
    }

    try {
        const merenja = parsirajMerenja(await preuzmi(NRT_URL(hmId)));
        const preuzeto = Timestamp.now();
        await ref.set({ merenja, preuzeto });
        return { merenja, preuzeto: preuzeto.toMillis() };
    } catch (e) {
        console.error(`Merenja za stanicu ${hmId} nisu preuzeta`, e);
        // Ako RHMZ ne odgovara, vraćamo i starije podatke iz keša
        if (kes.exists) return { merenja: kes.data().merenja, preuzeto: kes.data().preuzeto.toMillis() };
        throw new functions.https.HttpsError("unavailable", "RHMZ trenutno nije dostupan.");
    }
});

// Za testove i ručno pokretanje
exports._interno = { parsirajStanjeVoda, parsirajMerenja, azurirajVodostaje };

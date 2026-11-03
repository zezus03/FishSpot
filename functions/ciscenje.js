// Nedeljno čišćenje (nedeljom u 4:00 po beogradskom vremenu). Briše samo ono što više niko ne koristi:
//   1. ulove koje je moderacija uklonila posle prijava, a admin ih 90 dana nije pregledao (slike, komentari, otisak)
//   2. zapise o prijavama starije od 90 dana: trajno obrisane ulove i ulove kojima su prijave prestale
//      (ulovi koje je admin vratio zadržavaju zapis, da ih nove prijave ne bi ponovo uklonile)
//   3. slike starije od 7 dana koje nisu deo nijednog ulova (prekinuto otpremanje, odustao od objave)
//   4. razgovore sa Deda Perom starije od 30 dana
//   5. e-adresu i telefon iz javnih profila (Korisnici), ako su ostali od starih naloga
// Ulovi, slike ulova, poeni, bedževi, izazovi i privatne poruke se ne diraju.
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { FieldValue, Timestamp } = require("firebase-admin/firestore");

const DAN_MS = 24 * 3600 * 1000;
const DANA_ZA_PRIJAVE = 90;
const DANA_ZA_SLIKE_BEZ_ULOVA = 7;
const DANA_ZA_DEDU_PERU = 30;

// "spot_slike/abc_e1_2" iz linka ili putanje, bez nastavka i bez oznake smanjene kopije
function osnovaSlike(putanjaIliLink) {
    if (!putanjaIliLink || typeof putanjaIliLink !== "string") return null;
    const m = putanjaIliLink.match(/\/o\/([^?]+)/);
    const putanja = m ? decodeURIComponent(m[1]) : putanjaIliLink;
    return putanja.replace(/\.[^./]+$/, "").replace(/_\d+x\d+$/, "");
}

function slikeUlova(u) {
    return [u?.slikaUrl, ...(Array.isArray(u?.dodatneSlike) ? u.dodatneSlike : [])].map(osnovaSlike).filter(Boolean);
}

const uMs = (t) => (t instanceof Timestamp ? t.toMillis() : typeof t === "number" ? t : 0);

async function obrisiSlike(osnove) {
    const bucket = admin.storage().bucket();
    for (const o of osnove) {
        if (!o.startsWith("spot_slike/")) continue;
        await bucket.deleteFiles({ prefix: o }).catch((e) => console.warn(`Slika ${o}:`, e.message));
    }
}

async function ocistiPrijave(sada) {
    const db = admin.firestore();
    const granica = sada - DANA_ZA_PRIJAVE * DAN_MS;
    const snap = await db.collection("PrijavljeniUlovi").get();
    let ulova = 0, zapisa = 0;
    for (const d of snap.docs) {
        const p = d.data();
        if (p.status === "uklonjen" && uMs(p.uklonjen) && uMs(p.uklonjen) < granica) {
            // Uklonjen ulov koji admin nije pregledao: briše se trajno, kao "Obriši trajno" u admin panelu
            const spotRef = db.collection("MestaZaPecanje").doc(d.id);
            if (!(await spotRef.get()).exists) {
                await obrisiSlike(slikeUlova(p.ulov));
                await db.recursiveDelete(spotRef.collection("komentari"));
                await db.collection("OtisciSlika").doc("svi").update({ [`o.${d.id}`]: FieldValue.delete() }).catch(() => null);
            }
            await d.ref.delete();
            ulova++;
        } else if ((p.status === "obrisan" || p.status === "aktivan") && uMs(p.poslednjaPrijava) < granica &&
            uMs(p.resenoVreme) < granica) {
            await d.ref.delete();
            zapisa++;
        }
    }
    return { ulova, zapisa };
}

async function ocistiSlikeBezUlova(sada) {
    const db = admin.firestore();
    const granica = sada - DANA_ZA_SLIKE_BEZ_ULOVA * DAN_MS;
    // Slike koje se koriste: svi ulovi na mapi i uklonjeni ulovi koji čekaju admina
    const koriste = new Set();
    (await db.collection("MestaZaPecanje").select("slikaUrl", "dodatneSlike").get()).docs
        .forEach((d) => slikeUlova(d.data()).forEach((o) => koriste.add(o)));
    (await db.collection("PrijavljeniUlovi").where("status", "==", "uklonjen").get()).docs
        .forEach((d) => slikeUlova(d.data().ulov).forEach((o) => koriste.add(o)));

    const [fajlovi] = await admin.storage().bucket().getFiles({ prefix: "spot_slike/" });
    const zaBrisanje = fajlovi.filter((f) => {
        const napravljen = Date.parse(f.metadata?.timeCreated || "") || sada;
        return napravljen < granica && !koriste.has(osnovaSlike(f.name));
    });
    for (const f of zaBrisanje) await f.delete({ ignoreNotFound: true }).catch((e) => console.warn(f.name, e.message));
    return zaBrisanje.length;
}

async function ocistiDeduPeru(sada) {
    const db = admin.firestore();
    const granica = sada - DANA_ZA_DEDU_PERU * DAN_MS;
    let poruka = 0;
    for (const korisnik of await db.collection("bot_chats").listDocuments()) {
        const stare = await korisnik.collection("messages").where("timestamp", "<", granica).get();
        if (stare.empty) continue;
        const paket = db.batch();
        stare.docs.slice(0, 450).forEach((d) => paket.delete(d.ref));
        await paket.commit();
        poruka += Math.min(stare.size, 450);
    }
    return poruka;
}

async function ocistiLicnePodatke() {
    const db = admin.firestore();
    const snap = await db.collection("Korisnici").get();
    const zaCiscenje = snap.docs.filter((d) => "email" in d.data() || "telefon" in d.data());
    for (let i = 0; i < zaCiscenje.length; i += 400) {
        const paket = db.batch();
        zaCiscenje.slice(i, i + 400).forEach((d) => paket.update(d.ref, { email: FieldValue.delete(), telefon: FieldValue.delete() }));
        await paket.commit();
    }
    return zaCiscenje.length;
}

async function ocisti(sada = Date.now()) {
    const prijave = await ocistiPrijave(sada);
    const slika = await ocistiSlikeBezUlova(sada);
    const dedaPera = await ocistiDeduPeru(sada);
    const profila = await ocistiLicnePodatke();
    const rezultat = { uklonjenihUlova: prijave.ulova, zapisaPrijava: prijave.zapisa, slikaBezUlova: slika, porukaDedePere: dedaPera, profila };
    console.log("Nedeljno čišćenje:", JSON.stringify(rezultat));
    return rezultat;
}

exports.nedeljnoCiscenje = functions
    .runWith({ timeoutSeconds: 540, memory: "512MB" })
    .pubsub.schedule("0 4 * * 0")
    .timeZone("Europe/Belgrade")
    .onRun(async () => {
        await ocisti();
        return null;
    });

exports._interno = { ocisti, osnovaSlike, ocistiLicnePodatke };

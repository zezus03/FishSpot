// Provera slike ulova i dodela poena.
// Kad UploadWorker upiše slikaUrl, funkcija izračuna otisak slike (dHash, 64 bita) i uporedi ga
// sa otiscima svih ranijih ulova. Ista ili skoro ista slika ne donosi poene drugi put.
// Poeni se dodeljuju tek ovde (aplikacija ih više ne dodaje sama); za ribu preko 5 kg čekaju admina.
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { Timestamp } = require("firebase-admin/firestore");
const jpeg = require("jpeg-js");
const { putanjaIzLinka, stanjePoena } = require("./ulovi")._interno;

// Najviše toliko različitih bitova (od 64) da bi se dve slike smatrale istom
const NAJVISE_RAZLIKA = 6;

// Svi otisci u jednom dokumentu: o.{ulovId} = { h: otisak, a: autor }
function indeksRef() {
    return admin.firestore().collection("OtisciSlika").doc("svi");
}

// dHash: slika se svede na 9x8 sivih polja; svaki bit kaže da li je polje svetlije od desnog suseda.
// Otisak ostaje isti posle smanjivanja, ponovnog čuvanja kao JPEG ili blage promene boja.
function otisakSlike(buffer) {
    const slika = jpeg.decode(buffer, { useTArray: true, formatAsRGBA: true, maxMemoryUsageInMB: 512 });
    const { width: w, height: h, data } = slika;
    const S = 9, V = 8;
    const zbir = new Float64Array(S * V);
    const broj = new Float64Array(S * V);
    for (let y = 0; y < h; y++) {
        const red = Math.min(V - 1, Math.floor((y * V) / h));
        for (let x = 0; x < w; x++) {
            const kol = Math.min(S - 1, Math.floor((x * S) / w));
            const i = (y * w + x) * 4;
            zbir[red * S + kol] += 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
            broj[red * S + kol]++;
        }
    }
    let bitovi = 0n;
    for (let red = 0; red < V; red++) {
        for (let kol = 0; kol < S - 1; kol++) {
            const levo = zbir[red * S + kol] / broj[red * S + kol];
            const desno = zbir[red * S + kol + 1] / broj[red * S + kol + 1];
            bitovi = (bitovi << 1n) | (levo > desno ? 1n : 0n);
        }
    }
    return bitovi.toString(16).padStart(16, "0");
}

// Broj različitih bitova između dva otiska
function razlikaOtisaka(a, b) {
    let x = BigInt("0x" + a) ^ BigInt("0x" + b);
    let n = 0;
    while (x) { n += Number(x & 1n); x >>= 1n; }
    return n;
}

function nadjiDuplikat(otisak, otisci, spotId) {
    for (const [id, zapis] of Object.entries(otisci)) {
        if (id !== spotId && zapis?.h && razlikaOtisaka(otisak, zapis.h) <= NAJVISE_RAZLIKA) return id;
    }
    return null;
}

exports.proveriSlikuUlova = functions
    .runWith({ memory: "512MB" })
    .firestore.document("MestaZaPecanje/{spotId}")
    .onUpdate(async (change, context) => {
        const pre = change.before.data();
        const posle = change.after.data();
        if (pre.slikaUrl || !posle.slikaUrl) return null; // samo kad slika stigne prvi put
        if (posle.izvorSlike === undefined) return null;  // stara verzija aplikacije je sama dodala poene

        const spotId = context.params.spotId;
        const db = admin.firestore();
        console.log(`Provera slike ulova ${spotId} (${posle.izvorSlike})`);

        let otisak = null;
        try {
            const [sadrzaj] = await admin.storage().bucket().file(putanjaIzLinka(posle.slikaUrl)).download();
            otisak = otisakSlike(sadrzaj);
        } catch (e) {
            console.warn(`Otisak slike za ulov ${spotId} nije izračunat:`, e.message);
        }

        const ishod = await db.runTransaction(async (tx) => {
            const autorRef = db.collection("Korisnici").doc(posle.authorId);
            const [ulovSnap, indeksSnap, autorSnap] = await Promise.all([
                tx.get(change.after.ref), tx.get(indeksRef()), tx.get(autorRef),
            ]);
            if (!ulovSnap.exists) return "ulov obrisan";
            const ulov = ulovSnap.data();
            if (ulov.poeni !== undefined) return "već provereno";

            const otisci = indeksSnap.exists ? (indeksSnap.data().o ?? {}) : {};
            const duplikatOd = otisak ? nadjiDuplikat(otisak, otisci, spotId) : null;

            // Ista slika ne sme dvaput kao ulov. Aplikacija to proverava pre objave (proveriOtisakSlike);
            // ako ipak stigne (stara verzija, zaobilaženje), ulov se briše (obrisanUlov čisti sliku i ostalo)
            if (duplikatOd) {
                tx.delete(ulovSnap.ref);
                return `ista slika kao ulov ${duplikatOd}, ulov obrisan`;
            }
            const proveraSlike = { otisak: otisak ?? "", duplikatOd: duplikatOd ?? "", vreme: Timestamp.now() };
            // Riba preko 5 kg (50+ poena): poeni čekaju odobrenje admina
            const stanje = stanjePoena({ ...ulov, proveraSlike });
            const poeni = stanje.poeni;

            tx.update(ulovSnap.ref, { ...stanje, proveraSlike });
            if (otisak) tx.set(indeksRef(), { o: { [spotId]: { h: otisak, a: ulov.authorId } } }, { merge: true });
            if (poeni > 0 && autorSnap.exists) {
                tx.update(autorRef, { poeni: (autorSnap.data().poeni ?? 0) + poeni });
            }
            if (Object.keys(otisci).length > 8000) console.warn("OtisciSlika/svi se približava granici veličine dokumenta");
            return `${ulov.izvorSlike}, poena ${poeni}` +
                (stanje.poeniNaCekanju ? `, ${stanje.poeniNaCekanju} čeka admina` : "");
        });

        console.log(`Slika ulova ${spotId}: ${ishod}`);
        return null;
    });

// Aplikacija pre objave pita da li je slika (njen otisak) već objavljena; ne otkriva koji je to ulov
exports.proveriOtisakSlike = functions.https.onCall(async (data, context) => {
    if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Moraš biti prijavljen.");
    const otisak = String(data?.otisak || "");
    if (!/^[0-9a-f]{16}$/.test(otisak)) throw new functions.https.HttpsError("invalid-argument", "Neispravan otisak.");
    const snap = await indeksRef().get();
    const otisci = snap.exists ? (snap.data().o ?? {}) : {};
    return { postoji: !!nadjiDuplikat(otisak, otisci, "") };
});

exports._interno = { otisakSlike, razlikaOtisaka, nadjiDuplikat };

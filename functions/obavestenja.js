// Obaveštenja u aplikaciji (bez slanja na telefon): Korisnici/{uid}/obavestenja/{spotId}
// Jedan red po ulovu: "Marko i još 3 korisnika su lajkovali tvoj ulov: Šaran 4,2 kg".
// Aplikacija ih samo čita i označava kao pročitane; upisuje ih samo server.
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

const MAKS_LAJKOVA = 500; // koliko uid-ova pamtimo po ulovu (da se ponovni lajk ne računa)
const DANA_ZA_OBAVESTENJA = 60;

function kg(t) {
    if (!(t > 0)) return "";
    return ` ${String(Math.round(t * 100) / 100).replace(".", ",")} kg`;
}

// "Šaran 4,2 kg", a kod čuvarke "Čuvarka: Šaran, Deverika"
function opisUlova(ulov) {
    const { nazivVrsta } = require("./ulovi")._interno;
    const naziv = nazivVrsta(ulov) || "ulov";
    return ulov.cuvarka === true ? naziv : naziv + kg(ulov.tezinaRibe);
}

// Novi lajk: upisuje ili osvežava red obaveštenja za autora ulova
exports.lajkObavestenje = functions.firestore
    .document("MestaZaPecanje/{spotId}")
    .onUpdate(async (change, context) => {
        const pre = change.before.data();
        const posle = change.after.data();
        const autor = posle.authorId;
        if (!autor) return null;
        const bili = new Set(pre.likedBy || []);
        // Samo novi lajkovi, bez sopstvenog
        const novi = (posle.likedBy || []).filter((uid) => !bili.has(uid) && uid !== autor);
        if (!novi.length) return null;

        const db = admin.firestore();
        const korisnikRef = db.collection("Korisnici").doc(autor);
        // Lajkovi blokiranih korisnika se ne prijavljuju
        const blokirani = await Promise.all(novi.map((uid) => korisnikRef.collection("blokirani").doc(uid).get()));
        const dozvoljeni = novi.filter((_, i) => !blokirani[i].exists);
        if (!dozvoljeni.length) return null;

        const ref = korisnikRef.collection("obavestenja").doc(context.params.spotId);
        await db.runTransaction(async (tx) => {
            const snap = await tx.get(ref);
            const lajkovali = snap.exists ? (snap.data().lajkovali || []) : [];
            // Odlajk pa ponovni lajk istog korisnika se ne računa ponovo
            const zaista = dozvoljeni.filter((uid) => !lajkovali.includes(uid));
            if (!zaista.length) return;
            const poslednji = zaista[zaista.length - 1];
            const profil = await tx.get(db.collection("Korisnici").doc(poslednji));
            const svi = [...lajkovali, ...zaista].slice(-MAKS_LAJKOVA);
            tx.set(ref, {
                tip: "lajk",
                ulovId: context.params.spotId,
                ulov: opisUlova(posle),
                lajkovali: svi,
                broj: svi.length,
                poslednjiUid: poslednji,
                poslednjeIme: (profil.exists && profil.data().userName) || "Pecaroš",
                poslednjaSlika: (profil.exists && profil.data().slikaURL) || "",
                vreme: Date.now(),
                procitano: false,
            });
        });
        return null;
    });

// Brisanje starih obaveštenja (poziva nedeljno čišćenje)
async function ocistiObavestenja(sada = Date.now()) {
    const db = admin.firestore();
    const stara = await db.collectionGroup("obavestenja").where("vreme", "<", sada - DANA_ZA_OBAVESTENJA * 24 * 3600 * 1000).get();
    for (let i = 0; i < stara.docs.length; i += 400) {
        const paket = db.batch();
        stara.docs.slice(i, i + 400).forEach((d) => paket.delete(d.ref));
        await paket.commit();
    }
    return stara.size;
}

exports._interno = { opisUlova, ocistiObavestenja, DANA_ZA_OBAVESTENJA };

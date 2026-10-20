// Izmena i brisanje ulova i brisanje celog naloga.
// Aplikacija samo menja ili briše dokument u MestaZaPecanje (i poziva obrisiNalog),
// a ove funkcije srede sve ostalo: poene, spisak ulova po vrsti, komentare i slike.
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

// Isti pragovi kao u aplikaciji (SpotsViewModel.addSpot)
function poeniZaUlov(tezinaKg) {
    if (tezinaKg > 5.0) return 50;
    if (tezinaKg > 2.0) return 20;
    if (tezinaKg > 1.0) return 10;
    if (tezinaKg > 0.5) return 5;
    if (tezinaKg > 0) return 2; // i mala riba donosi poene
    return 0;
}

// Polja ulova koja se čuvaju i pod VrsteRiba/{vrstaId}/Ulovi/{id}
function ulovPoVrsti(ulov) {
    return {
        authorId: ulov.authorId,
        authorUsername: ulov.authorUsername ?? "",
        duzinaRibe: ulov.duzinaRibe ?? 0,
        tezinaRibe: ulov.tezinaRibe ?? 0,
        mamac: ulov.mamac ?? "",
        tehnika: ulov.tehnika ?? "",
        timestamp: ulov.timestamp ?? Date.now(),
    };
}

// Menja poene korisniku; ako je nalog u međuvremenu obrisan, ne pravi ga ponovo
// Poeni nikad ne idu ispod nule (npr. brisanje starog ulova za koji pravilo tada nije davalo poene)
async function promeniPoene(uid, razlika) {
    if (!uid || !razlika) return;
    const db = admin.firestore();
    const ref = db.collection("Korisnici").doc(uid);
    await db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists) return;
        const novi = Math.max(0, (snap.data().poeni ?? 0) + razlika);
        tx.update(ref, { poeni: novi });
    });
}

// Putanja fajla u Storage-u iz download linka (.../o/spot_slike%2Fabc.jpg?alt=media...)
function putanjaIzLinka(url) {
    if (!url || typeof url !== "string") return null;
    const m = url.match(/\/o\/([^?]+)/);
    return m ? decodeURIComponent(m[1]) : null;
}

// Briše sliku ulova i njene smanjene kopije (npr. abc_200x200.jpg)
async function obrisiSliku(url) {
    const putanja = putanjaIzLinka(url);
    if (!putanja || !putanja.startsWith("spot_slike/")) return;
    const osnova = putanja.replace(/\.[^.]+$/, "");
    try {
        await admin.storage().bucket().deleteFiles({ prefix: osnova });
    } catch (e) {
        console.warn(`Slika ${putanja} nije obrisana:`, e.message);
    }
}

// ─── BRISANJE ULOVA ───
// Autor obriše ulov: brišu se komentari, zapis pod vrstom i slika, a poeni se oduzimaju.
// Ulov koji je uklonila moderacija (status "uklonjen") se ne dira, jer admin može da ga vrati.
exports.obrisanUlov = functions.firestore
    .document("MestaZaPecanje/{spotId}")
    .onDelete(async (snap, context) => {
        const db = admin.firestore();
        const spotId = context.params.spotId;
        const ulov = snap.data();

        const evidencija = await db.collection("PrijavljeniUlovi").doc(spotId).get();
        if (evidencija.exists && evidencija.data().status === "uklonjen") {
            console.log(`Ulov ${spotId} je uklonila moderacija, preskačemo čišćenje`);
            return null;
        }

        await Promise.all([
            db.recursiveDelete(db.collection("MestaZaPecanje").doc(spotId).collection("komentari")),
            ulov.vrstaId
                ? db.collection("VrsteRiba").doc(ulov.vrstaId).collection("Ulovi").doc(spotId).delete()
                : Promise.resolve(),
            obrisiSliku(ulov.slikaUrl),
            promeniPoene(ulov.authorId, -poeniZaUlov(ulov.tezinaRibe ?? 0)),
        ]);

        console.log(`Ulov ${spotId} obrisan i počišćen`);
        return null;
    });

// ─── IZMENA ULOVA ───
// Kad autor promeni težinu, poeni se usklađuju; kad promeni vrstu, ulov se seli pod novu vrstu.
exports.izmenjenUlov = functions.firestore
    .document("MestaZaPecanje/{spotId}")
    .onUpdate(async (change, context) => {
        const db = admin.firestore();
        const spotId = context.params.spotId;
        const pre = change.before.data();
        const posle = change.after.data();

        const poljaVrste = ["vrstaId", "duzinaRibe", "tezinaRibe", "mamac", "tehnika", "authorUsername"];
        if (poljaVrste.every((p) => pre[p] === posle[p])) return null; // npr. lajk ili slika

        const poslovi = [];

        const razlika = poeniZaUlov(posle.tezinaRibe ?? 0) - poeniZaUlov(pre.tezinaRibe ?? 0);
        if (razlika !== 0) poslovi.push(promeniPoene(posle.authorId, razlika));

        if (pre.vrstaId && pre.vrstaId !== posle.vrstaId) {
            poslovi.push(db.collection("VrsteRiba").doc(pre.vrstaId).collection("Ulovi").doc(spotId).delete());
        }
        if (posle.vrstaId) {
            poslovi.push(db.collection("VrsteRiba").doc(posle.vrstaId).collection("Ulovi").doc(spotId)
                .set(ulovPoVrsti(posle)));
        }

        await Promise.all(poslovi);
        console.log(`Ulov ${spotId} izmenjen (poeni ${razlika >= 0 ? "+" : ""}${razlika})`);
        return null;
    });

// ─── BRISANJE NALOGA ───
// Google Play traži da korisnik može sam da obriše nalog i sve svoje podatke iz aplikacije.
exports.obrisiNalog = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "Moraš biti prijavljen.");
    }
    const uid = context.auth.uid;
    const db = admin.firestore();
    const bucket = admin.storage().bucket();

    // 1. Ulovi (komentari ispod njih se brišu zajedno sa njima; obrisanUlov čisti ostatak)
    const ulovi = await db.collection("MestaZaPecanje").where("authorId", "==", uid).get();
    for (const doc of ulovi.docs) {
        const ulov = doc.data();
        await Promise.all([
            ulov.vrstaId
                ? db.collection("VrsteRiba").doc(ulov.vrstaId).collection("Ulovi").doc(doc.id).delete()
                : Promise.resolve(),
            obrisiSliku(ulov.slikaUrl),
        ]);
        await db.recursiveDelete(doc.ref);
    }

    // 2. Ulovi koje je uklonila moderacija (čuvaju se kopije zbog vraćanja)
    const uklonjeni = await db.collection("PrijavljeniUlovi").where("autorUlovaId", "==", uid).get();
    for (const doc of uklonjeni.docs) {
        const ulov = doc.data().ulov;
        if (ulov?.vrstaId) {
            await db.collection("VrsteRiba").doc(ulov.vrstaId).collection("Ulovi").doc(doc.id).delete();
        }
        await obrisiSliku(ulov?.slikaUrl);
        await doc.ref.delete();
    }

    // 3. Komentari na tuđim ulovima
    const komentari = await db.collectionGroup("komentari").where("authorId", "==", uid).get();
    await Promise.all(komentari.docs.map((d) => d.ref.delete()));

    // 4. Prijave koje je korisnik poslao
    const prijave = await db.collection("Prijave").where("prijavioId", "==", uid).get();
    await Promise.all(prijave.docs.map((d) => d.ref.delete()));

    // 5. Privatne poruke (soba "uid1_uid2"), razgovor sa Deda Perom, lokacija uživo
    const sobe = await db.collection("PrivateChats").listDocuments();
    for (const soba of sobe) {
        if (soba.id.split("_").includes(uid)) await db.recursiveDelete(soba);
    }
    await db.recursiveDelete(db.collection("bot_chats").doc(uid));
    await db.collection("ActiveUsers").doc(uid).delete();

    // 6. Profil i profilna slika
    await db.recursiveDelete(db.collection("Korisnici").doc(uid));
    await Promise.all([`profilne_slike/${uid}.jpg`, `profilne_slike/${uid}_200x200.jpg`]
        .map((p) => bucket.file(p).delete({ ignoreNotFound: true })));

    // 7. Na kraju i sam nalog za prijavu
    await admin.auth().deleteUser(uid);

    console.log(`Nalog ${uid} obrisan (ulova: ${ulovi.size}, komentara: ${komentari.size})`);
    return { obrisano: true };
});

exports._interno = { poeniZaUlov, putanjaIzLinka };

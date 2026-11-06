// Admin: prebacivanje svih ulova sa jednog naloga na drugi (npr. sa probnog naloga iz testiranja na pravi).
// Pored vlasnika ulova prebacuju se i poeni tekuće sezone, a bedževi i izazovi se ponovo računaju za oba naloga.
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

// data: { sa: uid, na: uid, probno: true|false }
// probno: true samo vraća koliko ulova i poena bi se prebacilo, bez ikakve izmene
exports.adminPrebaciUlove = functions.runWith({ timeoutSeconds: 300 }).https.onCall(async (data, context) => {
    const email = require("./admin")._interno.proveriAdmina(context);
    const { dodeljeniPoeni, promeniPoene } = require("./ulovi")._interno;
    const { uTekucojSezoni } = require("./sezona")._interno;
    const db = admin.firestore();

    const sa = String(data?.sa || "");
    const na = String(data?.na || "");
    if (!sa || !na || sa === na) {
        throw new functions.https.HttpsError("invalid-argument", "Izaberi dva različita naloga.");
    }
    const [saDoc, naDoc] = await Promise.all([db.collection("Korisnici").doc(sa).get(), db.collection("Korisnici").doc(na).get()]);
    if (!saDoc.exists || !naDoc.exists) throw new functions.https.HttpsError("not-found", "Nalog ne postoji.");
    const naIme = naDoc.data().userName || "Pecaroš";

    const ulovi = (await db.collection("MestaZaPecanje").where("authorId", "==", sa).get()).docs;
    const poeni = ulovi.reduce((zbir, d) => zbir + (uTekucojSezoni(d.data()) ? dodeljeniPoeni(d.data()) : 0), 0);
    const odgovor = { ulova: ulovi.length, poena: poeni, sa: saDoc.data().userName || "", na: naIme };
    if (data?.probno !== false || ulovi.length === 0) return odgovor;

    // Vlasnik i ime autora; promena imena pokreće izmenjenUlov, koji prepisuje i kopiju pod VrsteRiba (rekordi)
    for (let i = 0; i < ulovi.length; i += 400) {
        const paket = db.batch();
        ulovi.slice(i, i + 400).forEach((d) => paket.update(d.ref, { authorId: na, authorUsername: naIme }));
        await paket.commit();
    }
    // Kopija pod VrsteRiba se prepisuje i ovde (kad su imena ista, izmenjenUlov se ne pokreće)
    await Promise.all(ulovi.map((d) => {
        const u = d.data();
        if (!u.vrstaId || u.cuvarka === true) return null;
        return db.collection("VrsteRiba").doc(u.vrstaId).collection("Ulovi").doc(d.id)
            .set({ authorId: na, authorUsername: naIme }, { merge: true }).catch(() => null);
    }));
    // Otisci slika pamte autora (provera iste slike)
    const otisci = {};
    ulovi.forEach((d) => { otisci[`o.${d.id}.a`] = na; });
    await db.collection("OtisciSlika").doc("svi").update(otisci).catch(() => null);

    if (poeni > 0) {
        await promeniPoene(sa, -poeni);
        await promeniPoene(na, poeni);
    }
    const { proveriBedzeve } = require("./bedzevi")._interno;
    const { preracunaj } = require("./izazovi")._interno;
    await proveriBedzeve(sa, false);
    await proveriBedzeve(na, false);
    await preracunaj(sa);
    await preracunaj(na);

    console.log(`Admin ${email}: prebaceno ${ulovi.length} ulova (${poeni} poena) sa ${sa} na ${na}`);
    return { ...odgovor, prebaceno: true };
});

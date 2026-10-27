// Admin panel u aplikaciji. Sve ide preko ovih funkcija, a svaka proverava da je pozivalac
// jedan od admin naloga sa potvrđenom e-adresom. PrijavljeniUlovi aplikacija ne može da čita direktno.
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { Timestamp } = require("firebase-admin/firestore");

const ADMINI = ["djordjejovanovic28@gmail.com", "fishspot.rs@gmail.com"];

function proveriAdmina(context) {
    const token = context.auth?.token;
    const email = (token?.email || "").toLowerCase();
    if (!context.auth || !token.email_verified || !ADMINI.includes(email)) {
        throw new functions.https.HttpsError("permission-denied", "Samo za administratore.");
    }
    return email;
}

// Timestamp -> milisekunde, da aplikacija dobije obične brojeve
function ms(v) {
    if (v instanceof Timestamp) return v.toMillis();
    if (typeof v === "number") return v;
    return null;
}

function zaAplikaciju(id, p) {
    const u = p.ulov || {};
    return {
        id,
        status: p.status || "aktivan",
        brojPrijava: p.brojPrijava || 0,
        autorUlova: p.autorUlova || u.authorUsername || "",
        autorUlovaId: p.autorUlovaId || u.authorId || "",
        poslednjaPrijava: ms(p.poslednjaPrijava),
        oduzetoPoena: p.oduzetoPoena || 0,
        resioAdmin: p.resioAdmin || "",
        ulov: {
            vrstaRibe: u.vrstaRibe || "", naziv: u.naziv || "", opis: u.opis || "",
            duzinaRibe: u.duzinaRibe ?? 0, tezinaRibe: u.tezinaRibe ?? 0,
            mamac: u.mamac || "", tehnika: u.tehnika || "", slikaUrl: u.slikaUrl || "",
            lat: u.lat ?? 0, lng: u.lng ?? 0, timestamp: ms(u.timestamp),
        },
        razlozi: (p.razlozi || []).map((r) => ({
            razlog: r.razlog || "", opis: r.opis || "", prijavio: r.prijavio || "", vreme: ms(r.vreme),
        })),
    };
}

const DAN_MS = 24 * 3600 * 1000;

exports.adminPregled = functions.https.onCall(async (data, context) => {
    proveriAdmina(context);
    const db = admin.firestore();
    const [prijavljeni, korisnici, ulovi, prijaveDanas] = await Promise.all([
        db.collection("PrijavljeniUlovi").orderBy("poslednjaPrijava", "desc").limit(100).get(),
        db.collection("Korisnici").count().get(),
        db.collection("MestaZaPecanje").count().get(),
        db.collection("Prijave").where("timestamp", ">=", Date.now() - DAN_MS).count().get(),
    ]);
    return {
        statistika: {
            korisnika: korisnici.data().count,
            ulova: ulovi.data().count,
            prijavaDanas: prijaveDanas.data().count,
            uklonjenih: prijavljeni.docs.filter((d) => d.data().status === "uklonjen").length,
        },
        prijavljeni: prijavljeni.docs.map((d) => zaAplikaciju(d.id, d.data())),
    };
});

// "Ulov je u redu": vraća uklonjen ulov i poene (postojeća funkcija vratiPrijavljeniUlov),
// a aktivan ulov ostaje i nove prijave ga više ne uklanjaju
exports.adminUlovURedu = functions.https.onCall(async (data, context) => {
    const email = proveriAdmina(context);
    const spotId = String(data?.spotId || "");
    const ref = admin.firestore().collection("PrijavljeniUlovi").doc(spotId);
    const snap = await ref.get();
    if (!spotId || !snap.exists) throw new functions.https.HttpsError("not-found", "Prijava ne postoji.");
    if (snap.data().status === "obrisan") throw new functions.https.HttpsError("failed-precondition", "Ulov je već trajno obrisan.");
    await ref.update({ status: "vrati", resioAdmin: email, resenoVreme: Timestamp.now() });
    return { ok: true };
});

// "Obriši trajno": ulov, slika, komentari i zapis pod vrstom se brišu zauvek
exports.adminObrisiUlov = functions.https.onCall(async (data, context) => {
    const email = proveriAdmina(context);
    const spotId = String(data?.spotId || "");
    const db = admin.firestore();
    const evidencija = db.collection("PrijavljeniUlovi").doc(spotId);
    const snap = await evidencija.get();
    if (!spotId || !snap.exists) throw new functions.https.HttpsError("not-found", "Prijava ne postoji.");
    const p = snap.data();
    if (p.status === "obrisan") return { ok: true };

    const bioUklonjen = p.status === "uklonjen";
    // Status "obrisan" pre brisanja: funkcija obrisanUlov tada ne preskače čišćenje
    await evidencija.update({ status: "obrisan", resioAdmin: email, resenoVreme: Timestamp.now() });

    const spotRef = db.collection("MestaZaPecanje").doc(spotId);
    if ((await spotRef.get()).exists) {
        // Ulov je još na mapi: brisanje pokreće obrisanUlov (komentari, slika, vrsta, poeni)
        await db.recursiveDelete(spotRef);
    } else if (bioUklonjen) {
        // Moderacija ga je već sklonila i oduzela poene; ostaje samo slika
        const putanja = (p.ulov?.slikaUrl || "").match(/\/o\/([^?]+)/);
        if (putanja) {
            const osnova = decodeURIComponent(putanja[1]).replace(/\.[^.]+$/, "");
            if (osnova.startsWith("spot_slike/")) {
                await admin.storage().bucket().deleteFiles({ prefix: osnova }).catch((e) => console.warn(e.message));
            }
        }
    }
    console.log(`Admin ${email} trajno obrisao ulov ${spotId}`);
    return { ok: true };
});

exports._interno = { ADMINI, zaAplikaciju };

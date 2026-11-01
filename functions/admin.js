// Admin panel u aplikaciji. Sve ide preko ovih funkcija, a svaka proverava da je pozivalac
// jedan od admin naloga sa potvrđenom e-adresom. PrijavljeniUlovi aplikacija ne može da čita direktno.
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { Timestamp } = require("firebase-admin/firestore");
const { nazivVrsta } = require("./ulovi")._interno;

// Admin adrese su u functions/.env (ADMIN_EMAILS=adresa1,adresa2); taj fajl se objavljuje, ali nije na GitHub-u
const ADMINI = (process.env.ADMIN_EMAILS || "").split(",").map((a) => a.trim().toLowerCase()).filter(Boolean);

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
        // Vraćen ulov sa novim prijavama se prikazuje kao ponovo prijavljen
        status: (p.status === "vracen" || p.status === "vrati") ? "ponovo" : (p.status || "aktivan"),
        brojPrijava: p.brojPrijava || 0,
        autorUlova: p.autorUlova || u.authorUsername || "",
        autorUlovaId: p.autorUlovaId || u.authorId || "",
        poslednjaPrijava: ms(p.poslednjaPrijava),
        oduzetoPoena: p.oduzetoPoena || 0,
        resioAdmin: p.resioAdmin || "",
        ulov: {
            vrstaRibe: nazivVrsta(u), naziv: u.naziv || "", opis: u.opis || "",
            duzinaRibe: u.duzinaRibe ?? 0, tezinaRibe: u.tezinaRibe ?? 0,
            mamac: u.mamac || "", tehnika: u.tehnika || "", slikaUrl: u.slikaUrl || "",
            dodatneSlike: Array.isArray(u.dodatneSlike) ? u.dodatneSlike : [],
            lat: u.lat ?? 0, lng: u.lng ?? 0, timestamp: ms(u.timestamp),
        },
        razlozi: (p.razlozi || []).map((r) => ({
            razlog: r.razlog || "", opis: r.opis || "", prijavio: r.prijavio || "", vreme: ms(r.vreme),
        })),
    };
}

function velikiZaAplikaciju(id, u) {
    return {
        id,
        autorUlova: u.authorUsername || "", vrstaRibe: nazivVrsta(u), naziv: u.naziv || "", opis: u.opis || "",
        duzinaRibe: u.duzinaRibe ?? 0, tezinaRibe: u.tezinaRibe ?? 0, mamac: u.mamac || "", tehnika: u.tehnika || "",
        slikaUrl: u.slikaUrl || "", dodatneSlike: Array.isArray(u.dodatneSlike) ? u.dodatneSlike : [],
        izvorSlike: u.izvorSlike || "", poeniNaCekanju: u.poeniNaCekanju ?? 0,
        timestamp: ms(u.timestamp),
    };
}

const DAN_MS = 24 * 3600 * 1000;

function trebaOdluka(p) {
    if (p.status === "obrisan") return false;
    if (p.status === "vracen" || p.status === "vrati") {
        const reseno = Math.max(ms(p.resenoVreme) ?? 0, ms(p.vracen) ?? 0);
        return (ms(p.poslednjaPrijava) ?? 0) > reseno;
    }
    return true;
}

// Aplikacija pita da li da prikaže ulaz u admin panel (adrese nisu u aplikaciji)
exports.jesamLiAdmin = functions.https.onCall(async (data, context) => {
    try { proveriAdmina(context); return { admin: true, potvrda: false }; } catch (e) {
        const email = (context.auth?.token?.email || "").toLowerCase();
        // Admin adresa koja još nije potvrđena: ulaz se prikazuje, a panel traži potvrdu
        return { admin: false, potvrda: !!context.auth && ADMINI.includes(email) };
    }
});

exports.adminPregled = functions.https.onCall(async (data, context) => {
    proveriAdmina(context);
    const db = admin.firestore();
    const [prijavljeni, korisnici, ulovi, prijaveDanas, veliki] = await Promise.all([
        db.collection("PrijavljeniUlovi").orderBy("poslednjaPrijava", "desc").limit(100).get(),
        db.collection("Korisnici").count().get(),
        db.collection("MestaZaPecanje").count().get(),
        db.collection("Prijave").where("timestamp", ">=", Date.now() - DAN_MS).count().get(),
        db.collection("MestaZaPecanje").where("proveraAdmina", "==", "ceka").limit(50).get(),
    ]);
    return {
        statistika: {
            korisnika: korisnici.data().count,
            ulova: ulovi.data().count,
            prijavaDanas: prijaveDanas.data().count,
            uklonjenih: prijavljeni.docs.filter((d) => d.data().status === "uklonjen").length,
        },
        // Ulovi od 50+ poena koji čekaju odobrenje (najstariji prvi)
        veliki: veliki.docs.map((d) => velikiZaAplikaciju(d.id, d.data())).sort((a, b) => (a.timestamp ?? 0) - (b.timestamp ?? 0)),
        // Rešeni ulovi se ne prikazuju; vraćen ulov se ponovo pojavi samo ako stignu nove prijave posle odluke
        prijavljeni: prijavljeni.docs.filter((d) => trebaOdluka(d.data())).map((d) => zaAplikaciju(d.id, d.data())),
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

// Veliki ulov (50+ poena): "odobri" dodeljuje poene, "odbij" ostavlja ulov na mapi bez poena
exports.adminVelikiUlov = functions.https.onCall(async (data, context) => {
    const email = proveriAdmina(context);
    const spotId = String(data?.spotId || "");
    const odluka = data?.odluka;
    if (!spotId || !["odobri", "odbij"].includes(odluka)) {
        throw new functions.https.HttpsError("invalid-argument", "Neispravan zahtev.");
    }
    const { stanjePoena } = require("./ulovi")._interno;
    const db = admin.firestore();
    const ref = db.collection("MestaZaPecanje").doc(spotId);

    const dodato = await db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists) throw new functions.https.HttpsError("not-found", "Ulov više ne postoji.");
        const ulov = snap.data();
        const autorRef = db.collection("Korisnici").doc(ulov.authorId);
        const autor = await tx.get(autorRef);
        const stanje = stanjePoena({ ...ulov, proveraAdmina: odluka === "odobri" ? "odobren" : "odbijen" });
        const razlika = stanje.poeni - (ulov.poeni ?? 0);
        tx.update(ref, { ...stanje, resioAdmin: email, resenoVreme: Timestamp.now() });
        if (razlika !== 0 && autor.exists) {
            tx.update(autorRef, { poeni: Math.max(0, (autor.data().poeni ?? 0) + razlika) });
        }
        return razlika;
    });
    console.log(`Admin ${email}: veliki ulov ${spotId} ${odluka === "odobri" ? "odobren" : "odbijen"} (poeni ${dodato >= 0 ? "+" : ""}${dodato})`);
    return { ok: true, poeni: dodato };
});

exports._interno = { ADMINI, proveriAdmina, zaAplikaciju, trebaOdluka, velikiZaAplikaciju };

// Izazovi (takmičenja): pravi ih admin, korisnici se prijavljuju, a rang računa server.
// Izazovi/{id}: naziv, opis, nagrada, od, do (ms), merilo, vrste (prazno = sve), samoKamera, brojUcesnika
// Izazovi/{id}/ucesnici/{uid}: ime, slika, rezultat, brojUlova, najbolji (ulov koji se prikazuje)
// Aplikacija samo čita; novi izazov ne traži novu verziju aplikacije.
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { FieldValue, Timestamp } = require("firebase-admin/firestore");

// najteza: najteža pojedinačna riba (bez čuvarke), ukupno: zbir težina, broj: broj ulova
const MERILA = ["najteza", "ukupno", "broj"];
const DAN_MS = 24 * 3600 * 1000;

function vremeObjave(u) {
    if (u.objavljeno instanceof Timestamp) return u.objavljeno.toMillis();
    return typeof u.timestamp === "number" ? u.timestamp : 0;
}

// Da li ulov važi za izazov: provereni ulovi sa poenima (slika prošla proveru, 5+ kg odobrio admin),
// objavljeni tokom izazova, odgovarajuće vrste
function ulovVazi(izazov, u) {
    if (!((u.poeni ?? 0) > 0)) return false;
    const t = vremeObjave(u);
    if (t < izazov.od || t > izazov.do) return false;
    if (izazov.samoKamera && u.izvorSlike !== "aplikacija") return false;
    if (izazov.merilo === "najteza" && u.cuvarka === true) return false;
    const vrste = izazov.vrste || [];
    if (vrste.length) {
        const ids = u.cuvarka === true ? (u.cuvarkaVrste || []).map((v) => v.id) : [u.vrstaId];
        if (!ids.some((id) => vrste.includes(id))) return false;
    }
    return true;
}

function nazivVrste(u) {
    if (u.cuvarka === true && Array.isArray(u.cuvarkaVrste) && u.cuvarkaVrste.length) {
        return "Čuvarka: " + u.cuvarkaVrste.map((v) => v.naziv).join(", ");
    }
    return u.vrstaRibe || "";
}

function izracunaj(izazov, ulovi) {
    const vazeci = ulovi.filter((u) => ulovVazi(izazov, u));
    const najveci = vazeci.reduce((m, u) => ((u.tezinaRibe ?? 0) > (m?.tezinaRibe ?? -1) ? u : m), null);
    const ukupno = vazeci.reduce((z, u) => z + (u.tezinaRibe ?? 0), 0);
    let rezultat = 0;
    if (izazov.merilo === "najteza") rezultat = najveci?.tezinaRibe ?? 0;
    else if (izazov.merilo === "ukupno") rezultat = ukupno;
    else rezultat = vazeci.length;
    return {
        rezultat: Math.round(rezultat * 100) / 100,
        brojUlova: vazeci.length,
        ukupnaTezina: Math.round(ukupno * 100) / 100,
        // Ulovi sa kojima se učesnik takmiči (najteži prvi), za pregled u aplikaciji
        ulovi: [...vazeci].sort((a, b) => (b.tezinaRibe ?? 0) - (a.tezinaRibe ?? 0)).slice(0, 100).map((u) => ({
            id: u.id, slikaUrl: u.slikaUrl || "", vrsta: nazivVrste(u), tezina: u.tezinaRibe ?? 0,
            vreme: vremeObjave(u), cuvarka: u.cuvarka === true,
        })),
        najbolji: najveci ? {
            id: najveci.id, slikaUrl: najveci.slikaUrl || "", vrsta: nazivVrste(najveci), tezina: najveci.tezinaRibe ?? 0,
        } : null,
    };
}

// Ponovo računa rezultat korisnika u izazovima u kojima učestvuje (i završenim u poslednjih 60 dana,
// jer admin može kasnije da odobri ulov preko 5 kg)
async function preracunaj(uid, samoIzazov = null) {
    const db = admin.firestore();
    const izazovi = samoIzazov
        ? [await db.collection("Izazovi").doc(samoIzazov).get()].filter((d) => d.exists)
        : (await db.collection("Izazovi").where("do", ">=", Date.now() - 60 * DAN_MS).get()).docs;
    if (!izazovi.length) return;
    const moji = [];
    for (const iz of izazovi) {
        const uc = await iz.ref.collection("ucesnici").doc(uid).get();
        if (uc.exists) moji.push(iz);
    }
    if (!moji.length) return;
    const ulovi = (await db.collection("MestaZaPecanje").where("authorId", "==", uid).get()).docs
        .map((d) => ({ id: d.id, ...d.data() }));
    for (const iz of moji) {
        const r = izracunaj(iz.data(), ulovi);
        await iz.ref.collection("ucesnici").doc(uid).update({ ...r, azurirano: Date.now() });
    }
}

// Promena ulova (nov, poeni, težina, vrsta, brisanje) menja rezultat autora u izazovima
const POLJA = ["poeni", "tezinaRibe", "vrstaId", "cuvarka", "cuvarkaVrste", "slikaUrl", "izvorSlike", "objavljeno"];
exports.izazoviPromenaUlova = functions.firestore
    .document("MestaZaPecanje/{spotId}")
    .onWrite(async (change) => {
        const pre = change.before.exists ? change.before.data() : null;
        const posle = change.after.exists ? change.after.data() : null;
        if (pre && posle && POLJA.every((p) => JSON.stringify(pre[p]) === JSON.stringify(posle[p]))) return null;
        const uid = (posle || pre).authorId;
        if (uid) await preracunaj(uid);
        return null;
    });

exports.pridruziSeIzazovu = functions.https.onCall(async (data, context) => {
    if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Moraš biti prijavljen.");
    if (context.auth.token.email_verified !== true) {
        throw new functions.https.HttpsError("failed-precondition", "Prvo potvrdi e-adresu (link je u mailu).");
    }
    const uid = context.auth.uid;
    const id = String(data?.izazovId || "");
    const db = admin.firestore();
    const izRef = db.collection("Izazovi").doc(id);
    await db.runTransaction(async (tx) => {
        const [iz, uc, korisnik] = await Promise.all([
            tx.get(izRef), tx.get(izRef.collection("ucesnici").doc(uid)), tx.get(db.collection("Korisnici").doc(uid)),
        ]);
        if (!id || !iz.exists) throw new functions.https.HttpsError("not-found", "Izazov ne postoji.");
        if (Date.now() > iz.data().do) throw new functions.https.HttpsError("failed-precondition", "Izazov je završen.");
        if (uc.exists) return;
        tx.set(uc.ref, {
            ime: String(korisnik.data()?.userName || "Pecaroš").slice(0, 40), slika: korisnik.data()?.slikaURL || "",
            rezultat: 0, brojUlova: 0, ukupnaTezina: 0, ulovi: [], najbolji: null, prijavljen: Date.now(),
        });
        tx.update(izRef, { brojUcesnika: FieldValue.increment(1) });
    });
    // Ulovi objavljeni od početka izazova se odmah računaju
    await preracunaj(uid, id);
    return { ok: true };
});

// ─── ADMIN ───
function proveriAdmina(context) {
    return require("./admin")._interno.proveriAdmina(context);
}

exports.adminSacuvajIzazov = functions.https.onCall(async (data, context) => {
    const email = proveriAdmina(context);
    const naziv = String(data?.naziv || "").trim().slice(0, 80);
    const od = Number(data?.od);
    const kraj = Number(data?.do);
    const merilo = String(data?.merilo || "");
    if (!naziv || !Number.isFinite(od) || !Number.isFinite(kraj) || kraj <= od || !MERILA.includes(merilo)) {
        throw new functions.https.HttpsError("invalid-argument", "Proveri naziv, datume i merilo.");
    }
    const db = admin.firestore();
    const id = String(data?.id || "") || db.collection("Izazovi").doc().id;
    const ref = db.collection("Izazovi").doc(id);
    const postoji = (await ref.get()).exists;
    await ref.set({
        naziv,
        opis: String(data?.opis || "").trim().slice(0, 600),
        nagrada: String(data?.nagrada || "").trim().slice(0, 120),
        od, do: kraj, merilo,
        vrste: (Array.isArray(data?.vrste) ? data.vrste : []).map(String).slice(0, 30),
        samoKamera: data?.samoKamera === true,
        ...(postoji ? {} : { brojUcesnika: 0, napravljeno: Date.now(), napravio: email }),
    }, { merge: true });

    if (postoji) {
        // Pravila su možda promenjena: rezultati svih učesnika se računaju ponovo
        const ucesnici = await ref.collection("ucesnici").get();
        for (const u of ucesnici.docs) await preracunaj(u.id, id);
    } else {
        await admin.messaging().send({
            topic: "izazovi",
            notification: { title: `Novi izazov: ${naziv} 🏆`, body: "Pogledaj u Rang listi → Izazovi i prijavi se." },
            data: { tip: "izazov", IZAZOV: id },
            android: { notification: { channelId: "fishspot_obavestenja" } },
        }).catch((e) => console.warn("Obaveštenje o izazovu nije poslato:", e.message));
    }
    console.log(`Admin ${email} ${postoji ? "izmenio" : "napravio"} izazov ${id}`);
    return { ok: true, id };
});

exports.adminObrisiIzazov = functions.https.onCall(async (data, context) => {
    const email = proveriAdmina(context);
    const id = String(data?.id || "");
    if (!id) throw new functions.https.HttpsError("invalid-argument", "Nedostaje izazov.");
    await admin.firestore().recursiveDelete(admin.firestore().collection("Izazovi").doc(id));
    console.log(`Admin ${email} obrisao izazov ${id}`);
    return { ok: true };
});

exports._interno = { ulovVazi, izracunaj, preracunaj };

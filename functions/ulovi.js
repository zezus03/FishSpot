// Izmena i brisanje ulova i brisanje celog naloga.
// Aplikacija samo menja ili briše dokument u MestaZaPecanje (i poziva obrisiNalog),
// a ove funkcije srede sve ostalo: poene, spisak ulova po vrsti, komentare i slike.
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { FieldValue } = require("firebase-admin/firestore");

// Osnovni poeni po težini ribe
function poeniZaUlov(tezinaKg) {
    if (tezinaKg > 5.0) return 50;
    if (tezinaKg > 2.0) return 20;
    if (tezinaKg > 1.0) return 10;
    if (tezinaKg > 0.5) return 5;
    if (tezinaKg > 0) return 2; // i mala riba donosi poene
    return 0;
}

// Izvor slike određuje aplikacija pri izboru slike (ProveraSlike.kt):
//   "aplikacija" - slikano kamerom iz FishSpot-a (+20% poena)
//   "kamera"     - iz galerije, sa podacima kamere i ne starije od par dana
//   "poslata"    - skora slika primljena preko Vibera, WhatsApp-a ili Messengera
//   ostalo ("ai", "screenshot", "stara", "nepoznata") - ulov se objavljuje, ali bez poena
//   ("ai" = slika nosi oznaku da je napravljena ili izmenjena veštačkom inteligencijom)
const IZVORI_SA_POENIMA = ["aplikacija", "kamera", "poslata"];
const BONUS_ZA_APLIKACIJU = 1.2;

// Poeni za novi ulov (sa poljem izvorSlike); slika koja je već objavljena ne donosi poene
function poeniZaNoviUlov(ulov) {
    if (ulov.proveraSlike?.duplikatOd) return 0;
    if (!IZVORI_SA_POENIMA.includes(ulov.izvorSlike)) return 0;
    const osnovni = poeniZaUlov(ulov.tezinaRibe ?? 0);
    const poeni = ulov.izvorSlike === "aplikacija" ? Math.round(osnovni * BONUS_ZA_APLIKACIJU) : osnovni;
    // Čuvarka (više riba zajedno) donosi najviše 20 poena
    return ulov.cuvarka === true ? Math.min(poeni, MAKS_POENA_CUVARKA) : poeni;
}

const MAKS_POENA_CUVARKA = 20;

// Naziv za prikaz adminu i u mailovima: kod čuvarke sve vrste
function nazivVrsta(u) {
    if (u?.cuvarka && Array.isArray(u.cuvarkaVrste) && u.cuvarkaVrste.length) {
        return "Čuvarka: " + u.cuvarkaVrste.map((v) => v.naziv).join(", ");
    }
    return u?.vrstaRibe || "";
}

// Ulovi od ovoliko poena i više (riba preko 5 kg) dobijaju poene tek kad ih admin odobri
const PRAG_ZA_ADMINA = 50;

// Koliko poena ulov treba da ima sada i koliko čeka odluku admina.
// proveraAdmina: "ceka" | "odobren" | "odbijen" | "" (nije potrebna)
function stanjePoena(ulov) {
    const n = poeniZaNoviUlov(ulov);
    if (ulov.proveraAdmina === "odbijen") return { poeni: 0, poeniNaCekanju: 0, proveraAdmina: "odbijen" };
    if (n < PRAG_ZA_ADMINA) return { poeni: n, poeniNaCekanju: 0, proveraAdmina: ulov.proveraAdmina === "odobren" ? "odobren" : "" };
    if (ulov.proveraAdmina === "odobren") return { poeni: n, poeniNaCekanju: 0, proveraAdmina: "odobren" };
    return { poeni: 0, poeniNaCekanju: n, proveraAdmina: "ceka" };
}

// Koliko je autor do sada dobio za ulov (to se oduzima pri brisanju ili uklanjanju)
function dodeljeniPoeni(ulov) {
    // Stari ulovi, pre provere slike: poene je dodala aplikacija odmah po težini
    if (ulov.izvorSlike === undefined) return poeniZaUlov(ulov.tezinaRibe ?? 0);
    // Novi ulovi: poeni postoje tek kad server proveri sliku (proveriSlikuUlova)
    return ulov.poeni ?? 0;
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
            // Ulov iz prošle sezone: poeni su već u istoriji, tekuća sezona se ne dira
            require("./sezona")._interno.uTekucojSezoni(ulov) ? promeniPoene(ulov.authorId, -dodeljeniPoeni(ulov)) : Promise.resolve(),
            // Otisak slike se briše, pa ista slika posle brisanja ulova opet može da se objavi
            db.collection("OtisciSlika").doc("svi")
                .update({ [`o.${spotId}`]: FieldValue.delete() })
                .catch(() => null),
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

        // Dodatne slike koje je autor uklonio u izmeni ulova brišu se iz Storage-a. Briše se samo fajl
        // sa istim početkom imena kao glavna slika ulova, da niko ne može da obriše tuđu sliku.
        const uklonjene = (pre.dodatneSlike || []).filter((u) => !(posle.dodatneSlike || []).includes(u));
        const osnova = putanjaIzLinka(posle.slikaUrl || pre.slikaUrl)?.replace(/\.[^.]+$/, "");
        if (uklonjene.length && osnova) {
            const bucket = admin.storage().bucket();
            await Promise.all(uklonjene.map(putanjaIzLinka)
                .filter((p) => p && p.startsWith(osnova + "_") && p !== osnova + ".jpg")
                .flatMap((p) => [p, p.replace(/\.[^.]+$/, "_200x200.jpg")])
                .map((p) => bucket.file(p).delete({ ignoreNotFound: true }).catch((e) => console.warn(e.message))));
            console.log(`Ulov ${spotId}: obrisano dodatnih slika ${uklonjene.length}`);
        }

        const poljaVrste = ["vrstaId", "duzinaRibe", "tezinaRibe", "mamac", "tehnika", "authorUsername", "cuvarka"];
        if (poljaVrste.every((p) => pre[p] === posle[p])) return null; // npr. lajk ili slika

        const poslovi = [];

        let razlika = 0;
        if (posle.izvorSlike === undefined) {
            razlika = poeniZaUlov(posle.tezinaRibe ?? 0) - poeniZaUlov(pre.tezinaRibe ?? 0);
        } else if (posle.poeni !== undefined) {
            // Novi ulov čija je slika proverena: poeni se računaju ponovo sa novom težinom
            // (riba koja pređe 5 kg ide adminu na proveru)
            const stanje = stanjePoena(posle);
            razlika = stanje.poeni - posle.poeni;
            if (razlika !== 0 || stanje.poeniNaCekanju !== (posle.poeniNaCekanju ?? 0) ||
                stanje.proveraAdmina !== (posle.proveraAdmina ?? "")) {
                poslovi.push(change.after.ref.update(stanje));
            }
        }
        if (razlika !== 0 && require("./sezona")._interno.uTekucojSezoni(posle)) poslovi.push(promeniPoene(posle.authorId, razlika));

        // Čuvarka ne ulazi u spisak ulova po vrsti (rekordi)
        if (pre.vrstaId && (pre.vrstaId !== posle.vrstaId || posle.cuvarka === true)) {
            poslovi.push(db.collection("VrsteRiba").doc(pre.vrstaId).collection("Ulovi").doc(spotId).delete());
        }
        if (posle.vrstaId && posle.cuvarka !== true) {
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
    await db.recursiveDelete(db.collection("Razgovori").doc(uid));
    // Učešće u izazovima
    for (const iz of (await db.collection("Izazovi").get()).docs) {
        const uc = iz.ref.collection("ucesnici").doc(uid);
        if ((await uc.get()).exists) {
            await uc.delete();
            await iz.ref.update({ brojUcesnika: FieldValue.increment(-1) });
        }
    }
    await db.collection("ActiveUsers").doc(uid).delete();
    await Promise.all(["BotLimit", "VodeLimit"].map((k) => db.collection(k).doc(uid).delete()));

    // 6. Profil i profilna slika
    await db.recursiveDelete(db.collection("Korisnici").doc(uid));
    await Promise.all([`profilne_slike/${uid}.jpg`, `profilne_slike/${uid}_200x200.jpg`]
        .map((p) => bucket.file(p).delete({ ignoreNotFound: true })));

    // 7. Na kraju i sam nalog za prijavu
    await admin.auth().deleteUser(uid);

    console.log(`Nalog ${uid} obrisan (ulova: ${ulovi.size}, komentara: ${komentari.size})`);
    return { obrisano: true };
});

exports._interno = { nazivVrsta, poeniZaUlov, poeniZaNoviUlov, stanjePoena, dodeljeniPoeni, promeniPoene, putanjaIzLinka, PRAG_ZA_ADMINA };

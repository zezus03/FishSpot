const functions = require("firebase-functions/v1"); // <-- OVO JE BILA GREŠKA, DODATO JE /v1
const admin = require("firebase-admin");
const { FieldValue } = require("firebase-admin/firestore");
admin.initializeApp();

// Slušamo kreiranje novog dokumenta u subkolekciji 'komentari'
exports.posaljiNotifikacijuZaKomentar = functions.firestore
    .document("MestaZaPecanje/{spotId}/komentari/{commentId}")
    .onCreate(async (snap, context) => {

        const noviKomentar = snap.data();
        const spotId = context.params.spotId;

        // 1. Izvlačimo podatke o komentaru
        const imeKomentatora = noviKomentar.authorUsername;
        const idKomentatora = noviKomentar.authorId;
        const tekstKomentara = noviKomentar.text;

        // 2. Tražimo glavni ulov da vidimo ko ga je objavio
        const spotDoc = await admin.firestore().collection("MestaZaPecanje").doc(spotId).get();
        if (!spotDoc.exists) {
            console.log("Ulov ne postoji.");
            return null;
        }

        const vlasnikUlovaId = spotDoc.data().authorId;

        // 3. Logika: Ne šalji notifikaciju ako ja komentarišem svoj sopstveni ulov!
        if (idKomentatora === vlasnikUlovaId) {
            console.log("Korisnik je komentarisao svoj post, prekidamo.");
            return null;
        }

        // 4. Nađi profil vlasnika ulova da bismo uzeli njegov FCM Token
        const userDoc = await admin.firestore().collection("Korisnici").doc(vlasnikUlovaId).get();
        if (!userDoc.exists) {
            console.log("Vlasnik ulova ne postoji u bazi.");
            return null;
        }

        const fcmToken = userDoc.data().fcmToken;
        if (!fcmToken) {
            console.log("Vlasnik ulova nema sačuvan FCM token (možda je obrisao app).");
            return null;
        }

        // 5. Sastavljanje Push Notifikacije
        const poruka = {
            notification: {
                title: `Novi komentar na tvoj ulov! 🎣`,
                body: `${imeKomentatora}: ${tekstKomentara}`
            },
            token: fcmToken
        };

        // 6. Slanje!
        try {
            const response = await admin.messaging().send(poruka);
            console.log("✅ Uspešno poslata notifikacija:", response);
        } catch (error) {
            console.error("❌ Greška pri slanju notifikacije:", error);
        }

        return null;
    });
// ─── MODERACIJA: PRIJAVE ULOVA ───
// Svaka prijava (Prijave/{spotId}_{uid}) se upisuje u PrijavljeniUlovi/{spotId}, gde admin
// u konzoli vidi broj prijava, razloge i kopiju ulova. Na BROJ_PRIJAVA_ZA_UKLANJANJE prijava
// ulov se uklanja sa mape, a autoru se oduzimaju poeni koje je za njega dobio.
const BROJ_PRIJAVA_ZA_UKLANJANJE = 3;

// Isti pragovi kao u aplikaciji (SpotsViewModel.addSpot)
function poeniZaUlov(tezinaKg) {
    if (tezinaKg > 5.0) return 50;
    if (tezinaKg > 2.0) return 20;
    if (tezinaKg > 1.0) return 10;
    if (tezinaKg > 0.5) return 5;
    return 0;
}

exports.obradiPrijavuUlova = functions.firestore
    .document("Prijave/{prijavaId}")
    .onCreate(async (snap) => {
        const prijava = snap.data();
        const db = admin.firestore();
        const spotRef = db.collection("MestaZaPecanje").doc(prijava.spotId);
        const evidencijaRef = db.collection("PrijavljeniUlovi").doc(prijava.spotId);
        const sada = Date.now();

        const ishod = await db.runTransaction(async (tx) => {
            const [spotSnap, evidencijaSnap] = await Promise.all([tx.get(spotRef), tx.get(evidencijaRef)]);
            const prethodno = evidencijaSnap.exists ? evidencijaSnap.data() : null;

            if (prethodno?.prijavili?.includes(prijava.prijavioId)) return "duplikat";

            const brojPrijava = (prethodno?.brojPrijava ?? 0) + 1;
            const podaci = {
                spotId: prijava.spotId,
                autorUlovaId: prijava.autorUlovaId,
                brojPrijava,
                prijavili: FieldValue.arrayUnion(prijava.prijavioId),
                razlozi: FieldValue.arrayUnion({
                    razlog: prijava.razlog,
                    opis: prijava.opis ?? "",
                    prijavioId: prijava.prijavioId,
                    timestamp: prijava.timestamp ?? sada,
                }),
                poslednjaPrijava: sada,
                status: prethodno?.status ?? "aktivan",
            };
            if (!prethodno) podaci.prvaPrijava = sada;
            // Kopija ulova, da ostane za pregled i kad se ulov ukloni
            if (spotSnap.exists) podaci.ulov = spotSnap.data();

            if (brojPrijava >= BROJ_PRIJAVA_ZA_UKLANJANJE && podaci.status === "aktivan" && spotSnap.exists) {
                const ulov = spotSnap.data();
                tx.delete(spotRef);
                if (ulov.vrstaId) {
                    tx.delete(db.collection("VrsteRiba").doc(ulov.vrstaId).collection("Ulovi").doc(prijava.spotId));
                }
                const poeni = poeniZaUlov(ulov.tezinaRibe ?? 0);
                if (poeni > 0 && ulov.authorId) {
                    tx.set(db.collection("Korisnici").doc(ulov.authorId), {
                        poeni: FieldValue.increment(-poeni),
                    }, { merge: true });
                }
                podaci.status = "uklonjen";
                podaci.uklonjen = sada;
                podaci.oduzetoPoena = poeni;
            }

            tx.set(evidencijaRef, podaci, { merge: true });
            return podaci.status;
        });

        console.log(`Prijava ulova ${prijava.spotId} od ${prijava.prijavioId}: ${ishod}`);
        return null;
    });

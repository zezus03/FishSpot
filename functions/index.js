const functions = require("firebase-functions/v1"); // <-- OVO JE BILA GREŠKA, DODATO JE /v1
const admin = require("firebase-admin");
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
const functions = require("firebase-functions/v1"); // <-- OVO JE BILA GREŠKA, DODATO JE /v1
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
admin.initializeApp();

// Gemini ključ živi samo na serveru (firebase functions:secrets:set GEMINI_API_KEY)
const GEMINI_API_KEY = defineSecret("GEMINI_API_KEY");
const GEMINI_MODEL = "gemini-2.5-flash";
const DNEVNI_LIMIT = 30; // Broj prepoznavanja po korisniku dnevno

// Aplikacija šalje sliku ribe (JPEG u base64) i opciono dužinu,
// a dobija nazad vrstu i procenjenu težinu.
// invoker "public" dozvoljava aplikaciji da pozove funkciju; prijavu proveravamo sami ispod (request.auth)
exports.prepoznajRibu = onCall({ secrets: [GEMINI_API_KEY], timeoutSeconds: 60, invoker: "public" }, async (request) => {
    if (!request.auth) {
        throw new HttpsError("unauthenticated", "Moraš biti prijavljen.");
    }

    const slika = request.data?.slika;
    const duzina = Number(request.data?.duzina) || 0;
    if (typeof slika !== "string" || slika.length === 0) {
        throw new HttpsError("invalid-argument", "Nedostaje slika.");
    }
    if (slika.length > 4 * 1024 * 1024) {
        throw new HttpsError("invalid-argument", "Slika je prevelika.");
    }

    await proveriDnevniLimit(request.auth.uid);

    const prompt = duzina > 0
        ? "Ti si stručni ihtiolog. Prepoznaj vrstu ribe sa slike. " +
          `Takođe, poznato je da je riba sa slike dugačka tačno ${duzina} cm. ` +
          "Na osnovu njene vrste i dužine, proceni njenu prosečnu težinu u kilogramima. " +
          "Vrati mi rezultat ISKLJUČIVO u ovom formatu: 'Narodni naziv (Latinski naziv)|težina'. " +
          "Na primer: 'Štuka (Esox lucius)|2.4'. Ne piši nikakav drugi tekst. " +
          "Ako ne možeš da prepoznaš ribu, vrati 'Nepoznato|0'."
        : "Ti si stručni ihtiolog. Prepoznaj vrstu ribe sa slike. " +
          "Vrati mi rezultat ISKLJUČIVO u ovom formatu: 'Narodni naziv (Latinski naziv)|0'. " +
          "Na primer: 'Štuka (Esox lucius)|0'. Ne piši nikakav drugi tekst. " +
          "Ako ne možeš da prepoznaš ribu, vrati 'Nepoznato|0'.";

    const odgovor = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`,
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "x-goog-api-key": GEMINI_API_KEY.value(),
            },
            body: JSON.stringify({
                contents: [{
                    parts: [
                        { inline_data: { mime_type: "image/jpeg", data: slika } },
                        { text: prompt },
                    ],
                }],
            }),
        }
    );

    if (!odgovor.ok) {
        console.error("Gemini greška:", odgovor.status, await odgovor.text());
        throw new HttpsError("unavailable", "AI trenutno nije dostupan, pokušaj ponovo.");
    }

    const json = await odgovor.json();
    const tekst = (json.candidates?.[0]?.content?.parts ?? [])
        .map((p) => p.text ?? "")
        .join("")
        .trim() || "Nepoznato|0";
    console.log("Gemini odgovor:", tekst.replace(/\n/g, " "));

    // Sečemo tekst po uspravnoj crti: "Vrsta|težina"
    const [vrsta, tezina] = tekst.split("|");
    return { vrsta: vrsta.trim(), tezina: (tezina ?? "").trim() };
});

// Broji pozive po korisniku i danu u kolekciji GeminiLimit (klijent je ne vidi, pravila je ne dozvoljavaju)
async function proveriDnevniLimit(uid) {
    const danas = new Date().toISOString().slice(0, 10);
    const ref = admin.firestore().collection("GeminiLimit").doc(`${uid}_${danas}`);

    await admin.firestore().runTransaction(async (tx) => {
        const doc = await tx.get(ref);
        const broj = doc.exists ? doc.data().broj : 0;
        if (broj >= DNEVNI_LIMIT) {
            throw new HttpsError("resource-exhausted", `Dostignut je dnevni limit od ${DNEVNI_LIMIT} prepoznavanja.`);
        }
        tx.set(ref, { uid, dan: danas, broj: broj + 1 });
    });
}

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
// Vremenska prognoza preko servera: OpenWeather ključ je samo ovde (tajna OPENWEATHER_KEY),
// a odgovori se keširaju po području (~1 km), pa korisnici iz iste okoline dele isti poziv.
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

const KES_MS = { weather: 15 * 60 * 1000, forecast: 30 * 60 * 1000 };

exports.vremenskaPrognoza = functions
    .runWith({ secrets: ["OPENWEATHER_KEY"], memory: "256MB", timeoutSeconds: 20 })
    .https.onCall(async (data, context) => {
        if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Moraš biti prijavljen.");
        const lat = Number(data?.lat);
        const lng = Number(data?.lng);
        const tip = data?.tip === "forecast" ? "forecast" : "weather";
        if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
            throw new functions.https.HttpsError("invalid-argument", "Neispravna lokacija.");
        }

        const db = admin.firestore();
        const kesRef = db.collection("PrognozaKes").doc(`${tip}_${lat.toFixed(2)}_${lng.toFixed(2)}`);
        const kes = await kesRef.get();
        if (kes.exists && Date.now() - kes.data().vreme < KES_MS[tip]) return { json: kes.data().json };

        const url = `https://api.openweathermap.org/data/2.5/${tip}?lat=${lat}&lon=${lng}` +
            `&units=metric&lang=sr&appid=${process.env.OPENWEATHER_KEY}`;
        const odgovor = await fetch(url);
        if (!odgovor.ok) {
            console.error(`OpenWeather ${tip}: ${odgovor.status}`);
            // Ako servis ne radi, bolje i stariji podatak nego ništa
            if (kes.exists) return { json: kes.data().json };
            throw new functions.https.HttpsError("unavailable", "Prognoza trenutno nije dostupna.");
        }
        const json = await odgovor.text();
        await kesRef.set({ json, vreme: Date.now() });
        return { json };
    });

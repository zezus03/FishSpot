// Vode u blizini za korisnike VAN Srbije (u Srbiji aplikacija koristi svoj fajl).
// Geoapify ključ je samo ovde (Firebase tajna GEOAPIFY_KEY), aplikacija ga ne zna.
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { Timestamp } = require("firebase-admin/firestore");

const RADIJUS_M = 5000;
const KES_DANA = 30;            // vode se ne menjaju, pa odgovor važi mesec dana
const DNEVNO_PO_KORISNIKU = 40; // više nego dovoljno za ceo dan pecanja

// Okrugli koordinate (~1 km), da susedni korisnici dele isti keš
const kljucKesa = (lat, lng) => `${lat.toFixed(2)}_${lng.toFixed(2)}`;

// Geoapify kategorije -> isti tip koji aplikacija koristi za tekst obaveštenja
function tipVode(kategorije = []) {
    if (kategorije.some((k) => k.includes("river"))) return "river";
    if (kategorije.some((k) => k.includes("stream"))) return "stream";
    if (kategorije.some((k) => k.includes("canal"))) return "canal";
    return "lake";
}

function uprosti(geojson) {
    return (geojson.features || [])
        .map((f) => f.properties || {})
        .filter((p) => typeof p.lat === "number" && typeof p.lon === "number")
        .map((p) => ({
            id: p.place_id || `${p.lat},${p.lon}`,
            naziv: p.name || "Nepoznata voda",
            lat: p.lat,
            lon: p.lon,
            tip: tipVode(p.categories),
        }));
}

// Najviše DNEVNO_PO_KORISNIKU poziva Geoapify-ja dnevno po korisniku
async function dozvoljenPoziv(db, uid) {
    const dan = new Date().toISOString().slice(0, 10);
    const ref = db.collection("VodeLimit").doc(uid);
    return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        const d = snap.exists ? snap.data() : {};
        const broj = d.dan === dan ? (d.broj || 0) : 0;
        if (broj >= DNEVNO_PO_KORISNIKU) return false;
        tx.set(ref, { dan, broj: broj + 1 });
        return true;
    });
}

exports.vodeUBlizini = functions
    .runWith({ secrets: ["GEOAPIFY_KEY"], memory: "256MB", timeoutSeconds: 20 })
    .https.onCall(async (data, context) => {
        if (!context.auth) {
            throw new functions.https.HttpsError("unauthenticated", "Moraš biti prijavljen.");
        }
        const lat = Number(data?.lat);
        const lng = Number(data?.lng);
        if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
            throw new functions.https.HttpsError("invalid-argument", "Neispravna lokacija.");
        }

        const db = admin.firestore();
        const kesRef = db.collection("VodeKes").doc(kljucKesa(lat, lng));
        const kes = await kesRef.get();
        if (kes.exists && kes.data().vazi.toMillis() > Date.now()) {
            return { vode: kes.data().vode, izKesa: true };
        }

        if (!(await dozvoljenPoziv(db, context.auth.uid))) {
            throw new functions.https.HttpsError("resource-exhausted", "Dnevna granica je dostignuta.");
        }

        const url = "https://api.geoapify.com/v2/places" +
            "?categories=natural.water,waterway" +
            `&filter=circle:${lng},${lat},${RADIJUS_M}` +
            `&bias=proximity:${lng},${lat}` +
            "&limit=50" +
            `&apiKey=${process.env.GEOAPIFY_KEY}`;
        const odgovor = await fetch(url, { headers: { Accept: "application/json" } });
        if (!odgovor.ok) {
            console.error(`Geoapify je vratio ${odgovor.status}`);
            throw new functions.https.HttpsError("unavailable", "Servis za vode trenutno ne radi.");
        }
        const vode = uprosti(await odgovor.json());

        await kesRef.set({
            vode,
            vazi: Timestamp.fromMillis(Date.now() + KES_DANA * 24 * 3600 * 1000),
        });
        return { vode, izKesa: false };
    });

exports._interno = { kljucKesa, tipVode, uprosti };

// Sezona rang liste traje jednu kalendarsku godinu (do 31.12. po beogradskom vremenu).
// 1.1. u ponoć poeni svih korisnika se čuvaju u Korisnici.sezone.{godina} i vraćaju na 0.
// Ulov iz ranije sezone posle toga više ne menja poene (brisanje, odobrenje, prijava, izmena).
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { Timestamp } = require("firebase-admin/firestore");

function godinaUBeogradu(ms) {
    return Number(new Intl.DateTimeFormat("en-GB", { timeZone: "Europe/Belgrade", year: "numeric" })
        .format(new Date(ms)));
}

function vremeObjaveUlova(u) {
    if (u?.objavljeno instanceof Timestamp) return u.objavljeno.toMillis();
    return typeof u?.timestamp === "number" ? u.timestamp : 0;
}

// Da li poeni ulova pripadaju tekućoj sezoni
function uTekucojSezoni(ulov, sada = Date.now()) {
    return godinaUBeogradu(vremeObjaveUlova(ulov)) === godinaUBeogradu(sada);
}

// Zatvara sezonu: poeni idu u istoriju (sezone.{godina}), rang lista počinje od nule.
// Pobednici (prvih 10) se čuvaju i u Sezone/{godina}.
async function zatvoriSezonu(godina) {
    const db = admin.firestore();
    const snap = await db.collection("Korisnici").where("poeni", ">", 0).get();
    const svi = snap.docs.map((d) => ({ ref: d.ref, id: d.id, ...d.data() }))
        .sort((a, b) => (b.poeni ?? 0) - (a.poeni ?? 0));
    await db.collection("Sezone").doc(String(godina)).set({
        godina,
        zatvoreno: Date.now(),
        brojKorisnika: svi.length,
        najbolji: svi.slice(0, 10).map((k, i) => ({
            mesto: i + 1, uid: k.id, ime: k.userName || "Pecaroš", slika: k.slikaURL || "", poeni: k.poeni,
        })),
    });
    // Batch prima najviše 500 upisa
    for (let i = 0; i < svi.length; i += 400) {
        const paket = db.batch();
        svi.slice(i, i + 400).forEach((k) => paket.update(k.ref, { poeni: 0, [`sezone.${godina}`]: k.poeni }));
        await paket.commit();
    }
    console.log(`Sezona ${godina} zatvorena: ${svi.length} korisnika vraćeno na 0 poena`);
    return svi.length;
}

exports.novaSezona = functions.pubsub
    .schedule("0 0 1 1 *")
    .timeZone("Europe/Belgrade")
    .onRun(async () => {
        // Pokreće se u ponoć 1.1., pa je sezona koja se zatvara prošla godina
        await zatvoriSezonu(godinaUBeogradu(Date.now()) - 1);
        return null;
    });

exports._interno = { godinaUBeogradu, uTekucojSezoni, zatvoriSezonu };

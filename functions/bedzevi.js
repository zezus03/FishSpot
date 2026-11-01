// Bedževi: računa ih server iz proverenih ulova (oni koji su dobili poene) i upisuje u
// Korisnici/{uid}/bedzevi/{id} = { id, naziv, dobijen }. Bedž važi dok postoje ulovi koji ga
// zaslužuju: posle brisanja ulova (ili uklanjanja zbog prijava) bedž se oduzima ako uslov više ne važi.
// Kad korisnik osvoji nov bedž, dobija obaveštenje. Isti spisak (id, naziv) je u aplikaciji (Bedzevi.kt).
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { Timestamp } = require("firebase-admin/firestore");

function vremeUlova(u) {
    if (u.objavljeno instanceof Timestamp) return u.objavljeno.toMillis();
    return typeof u.timestamp === "number" ? u.timestamp : 0;
}

// Sat i mesec po beogradskom vremenu
function satIMesec(ms) {
    const delovi = new Intl.DateTimeFormat("en-GB", {
        timeZone: "Europe/Belgrade", hour: "2-digit", month: "numeric", hour12: false,
    }).formatToParts(new Date(ms));
    const sat = Number(delovi.find((d) => d.type === "hour")?.value ?? 12) % 24;
    const mesec = Number(delovi.find((d) => d.type === "month")?.value ?? 6);
    return { sat, mesec };
}

function statistika(ulovi) {
    const vazeci = ulovi.filter((u) => (u.poeni ?? 0) > 0);
    const vrste = new Set();
    let najveca = 0, varalica = 0, musica = 0, feeder = 0, noc = 0, zima = 0, aplikacija = 0;
    for (const u of vazeci) {
        if (u.cuvarka === true) (u.cuvarkaVrste || []).forEach((v) => v.id && vrste.add(v.id));
        else {
            if (u.vrstaId && u.vrstaId !== "ostalo") vrste.add(u.vrstaId);
            najveca = Math.max(najveca, u.tezinaRibe ?? 0);
        }
        const mamac = String(u.mamac || "");
        if (mamac === "Varalica" || mamac.startsWith("Varalica ")) varalica++;
        if (mamac === "Mušica" || mamac.startsWith("Mušica ")) musica++;
        if (u.tehnika === "Feeder") feeder++;
        if (u.izvorSlike === "aplikacija") aplikacija++;
        const { sat, mesec } = satIMesec(vremeUlova(u));
        if (sat >= 22 || sat < 4) noc++;
        if (mesec === 12 || mesec === 1 || mesec === 2) zima++;
    }
    return { broj: vazeci.length, najveca, vrsta: vrste.size, varalica, musica, feeder, noc, zima, aplikacija };
}

// Spisak opštih bedževa: id (isto kao ime slike u aplikaciji), naziv i uslov
const BEDZEVI = [
    { id: "pecaros_1", naziv: "Pecaroš (bronza)", uslov: (s) => s.broj >= 1 },
    { id: "pecaros_2", naziv: "Pecaroš (srebro)", uslov: (s) => s.broj >= 25 },
    { id: "pecaros_3", naziv: "Pecaroš (zlato)", uslov: (s) => s.broj >= 100 },
    { id: "trofej_1", naziv: "Trofej (bronza)", uslov: (s) => s.najveca > 3 },
    { id: "trofej_2", naziv: "Trofej (srebro)", uslov: (s) => s.najveca > 8 },
    { id: "trofej_3", naziv: "Trofej (zlato)", uslov: (s) => s.najveca > 15 },
    { id: "kolekcionar_1", naziv: "Kolekcionar (bronza)", uslov: (s) => s.vrsta >= 5 },
    { id: "kolekcionar_2", naziv: "Kolekcionar (srebro)", uslov: (s) => s.vrsta >= 10 },
    { id: "kolekcionar_3", naziv: "Kolekcionar (zlato)", uslov: (s) => s.vrsta >= 20 },
    { id: "varalicar", naziv: "Varaličar", uslov: (s) => s.varalica >= 10 },
    { id: "musicar", naziv: "Mušičar", uslov: (s) => s.musica >= 5 },
    { id: "fiderista", naziv: "Fiderista", uslov: (s) => s.feeder >= 10 },
    { id: "nocna_smena", naziv: "Noćna smena", uslov: (s) => s.noc >= 1 },
    { id: "zimski", naziv: "Zimski pecaroš", uslov: (s) => s.zima >= 1 },
    { id: "fotograf", naziv: "Fotograf", uslov: (s) => s.aplikacija >= 10 },
];

// Usklađuje bedževe korisnika sa ulovima: nove upisuje (uz obaveštenje), nezaslužene briše. Vraća nove.
async function proveriBedzeve(uid, obavesti = true) {
    const db = admin.firestore();
    const korisnikRef = db.collection("Korisnici").doc(uid);
    const [korisnik, imaSnap, uloviSnap] = await Promise.all([
        korisnikRef.get(),
        korisnikRef.collection("bedzevi").get(),
        db.collection("MestaZaPecanje").where("authorId", "==", uid).get(),
    ]);
    if (!korisnik.exists) return [];
    const ima = new Set(imaSnap.docs.map((d) => d.id));
    const s = statistika(uloviSnap.docs.map((d) => d.data()));
    const novi = BEDZEVI.filter((b) => !ima.has(b.id) && b.uslov(s));
    const oduzeti = BEDZEVI.filter((b) => ima.has(b.id) && !b.uslov(s));
    if (!novi.length && !oduzeti.length) return [];

    const sada = Date.now();
    const paket = db.batch();
    novi.forEach((b) => paket.set(korisnikRef.collection("bedzevi").doc(b.id), { id: b.id, naziv: b.naziv, dobijen: sada }));
    oduzeti.forEach((b) => paket.delete(korisnikRef.collection("bedzevi").doc(b.id)));
    await paket.commit();
    if (oduzeti.length) console.log(`Oduzeti bedževi za ${uid}: ${oduzeti.map((b) => b.id).join(", ")}`);
    if (!novi.length) return [];

    const token = korisnik.data().fcmToken;
    if (obavesti && token) {
        const naslov = novi.length === 1 ? `Osvojio si bedž: ${novi[0].naziv} 🏅` : `Osvojio si ${novi.length} nova bedža 🏅`;
        await admin.messaging().send({
            token,
            notification: { title: naslov, body: "Pogledaj ih na svom profilu." },
            data: { tip: "bedz" },
            android: { notification: { channelId: "fishspot_obavestenja" } },
        }).catch((e) => console.warn("Obaveštenje o bedžu nije poslato:", e.message));
    }
    console.log(`Bedževi za ${uid}: ${novi.map((b) => b.id).join(", ")}`);
    return novi;
}

const POLJA = ["poeni", "tezinaRibe", "vrstaId", "cuvarka", "cuvarkaVrste", "mamac", "tehnika", "izvorSlike"];
exports.bedzeviPromenaUlova = functions.firestore
    .document("MestaZaPecanje/{spotId}")
    .onWrite(async (change) => {
        const pre = change.before.exists ? change.before.data() : null;
        const posle = change.after.exists ? change.after.data() : null;
        if (pre && posle && POLJA.every((p) => JSON.stringify(pre[p]) === JSON.stringify(posle[p]))) return null;
        // Nov ulov bez poena (slika još nije proverena) ne menja ništa
        if (!pre && (posle.poeni ?? 0) <= 0) return null;
        // Brisanje, izmena ili uklanjanje ulova: bedževi se usklađuju (dodaju ili oduzimaju)
        await proveriBedzeve((posle || pre).authorId);
        return null;
    });

// Aplikacija poziva pri otvaranju profila: stari korisnici dobijaju bedževe za ranije ulove (bez obaveštenja)
exports.osveziMojeBedzeve = functions.https.onCall(async (data, context) => {
    if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Moraš biti prijavljen.");
    const novi = await proveriBedzeve(context.auth.uid, false);
    return { novih: novi.length };
});

exports._interno = { statistika, BEDZEVI, proveriBedzeve, satIMesec };

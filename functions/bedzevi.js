// Bedževi: računa ih server iz proverenih ulova (oni koji su dobili poene) i upisuje u
// Korisnici/{uid}/bedzevi/{id} = { id, naziv, dobijen }. Bedž važi dok postoje ulovi koji ga
// zaslužuju: posle brisanja ulova (ili uklanjanja zbog prijava) bedž se oduzima ako uslov više ne važi.
// Kad korisnik osvoji nov bedž, dobija obaveštenje. Isti spisak (id, naziv) je u aplikaciji (Bedzevi.kt).
// Jedinstveni bedževi se nikad ne oduzimaju automatski:
//   pionir    - nalog napravljen do kraja 2026. (dodeljuje se sam)
//   prijatelj - "Prijatelj FishSpot-a", dodeljuje ga admin (adminBedzPrijatelj); spisak je u PosebniBedzevi/prijatelj
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { Timestamp, FieldValue } = require("firebase-admin/firestore");

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

// ─── JEDINSTVENI BEDŽEVI ───
const PIONIR = { id: "pionir", naziv: "Pionir" };
const PRIJATELJ = { id: "prijatelj", naziv: "Prijatelj FishSpot-a" };
// 1.1.2027. u 00:00 po beogradskom vremenu
const ROK_PIONIR = Date.UTC(2026, 11, 31, 23, 0);

async function nalogNapravljen(uid) {
    try {
        const u = await admin.auth().getUser(uid);
        return Date.parse(u.metadata.creationTime) || null;
    } catch (e) {
        return null;
    }
}

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
    if (!ima.has(PIONIR.id)) {
        const napravljen = await nalogNapravljen(uid);
        if (napravljen && napravljen < ROK_PIONIR) novi.push(PIONIR);
    }
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

// Admin: "Prijatelj FishSpot-a". akcija: "lista" | "trazi" (upit = početak korisničkog imena) | "dodeli" | "oduzmi"
exports.adminBedzPrijatelj = functions.https.onCall(async (data, context) => {
    const email = require("./admin")._interno.proveriAdmina(context);
    const db = admin.firestore();
    const spisakRef = db.collection("PosebniBedzevi").doc(PRIJATELJ.id);
    const akcija = String(data?.akcija || "lista");

    if (akcija === "trazi") {
        const upit = String(data?.upit || "").trim().slice(0, 40);
        if (!upit) return { korisnici: [] };
        const snap = await db.collection("Korisnici").orderBy("userName").startAt(upit).endAt(upit + "\uf8ff").limit(10).get();
        return {
            korisnici: snap.docs.map((d) => ({
                uid: d.id, ime: d.data().userName || "", imePrezime: d.data().imePrezime || "", slika: d.data().slikaURL || "",
            })),
        };
    }

    if (akcija === "dodeli" || akcija === "oduzmi") {
        const uid = String(data?.uid || "");
        const korisnik = uid ? await db.collection("Korisnici").doc(uid).get() : null;
        if (!korisnik?.exists) throw new functions.https.HttpsError("not-found", "Korisnik ne postoji.");
        const bedzRef = korisnik.ref.collection("bedzevi").doc(PRIJATELJ.id);
        if (akcija === "dodeli") {
            const sada = Date.now();
            await bedzRef.set({ id: PRIJATELJ.id, naziv: PRIJATELJ.naziv, dobijen: sada });
            await spisakRef.set({ [uid]: { ime: korisnik.data().userName || "Pecaroš", slika: korisnik.data().slikaURL || "", dobijen: sada } }, { merge: true });
            const token = korisnik.data().fcmToken;
            if (token) {
                await admin.messaging().send({
                    token,
                    notification: { title: `Postao si ${PRIJATELJ.naziv} 💜`, body: "Hvala što podržavaš FishSpot! Bedž je na tvom profilu." },
                    data: { tip: "bedz" },
                    android: { notification: { channelId: "fishspot_obavestenja" } },
                }).catch((e) => console.warn("Obaveštenje o bedžu nije poslato:", e.message));
            }
        } else {
            await bedzRef.delete();
            await spisakRef.update({ [uid]: FieldValue.delete() }).catch(() => null);
        }
        console.log(`Admin ${email}: ${akcija} bedž ${PRIJATELJ.id} korisniku ${uid}`);
    }

    const spisak = (await spisakRef.get()).data() || {};
    return {
        prijatelji: Object.entries(spisak).map(([uid, v]) => ({ uid, ime: v.ime || "", slika: v.slika || "", dobijen: v.dobijen || 0 }))
            .sort((a, b) => b.dobijen - a.dobijen),
    };
});

// Aplikacija poziva pri otvaranju profila: stari korisnici dobijaju bedževe za ranije ulove (bez obaveštenja)
exports.osveziMojeBedzeve = functions.https.onCall(async (data, context) => {
    if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Moraš biti prijavljen.");
    const novi = await proveriBedzeve(context.auth.uid, false);
    return { novih: novi.length };
});

exports._interno = { statistika, BEDZEVI, proveriBedzeve, satIMesec, ROK_PIONIR };

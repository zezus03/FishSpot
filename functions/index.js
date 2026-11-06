const functions = require("firebase-functions/v1"); // <-- OVO JE BILA GREŠKA, DODATO JE /v1
const admin = require("firebase-admin");
const { FieldValue, Timestamp } = require("firebase-admin/firestore");
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
            // Lokacija ulova: dodir na obaveštenje zumira mapu na komentarisani ulov
            data: {
                ZUMIRAJ_LAT: String(spotDoc.data().lat ?? ""),
                ZUMIRAJ_LNG: String(spotDoc.data().lng ?? ""),
            },
            android: { notification: { channelId: "fishspot_obavestenja" } },
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
// u konzoli vidi broj prijava, razloge, imena i kopiju ulova. Na BROJ_PRIJAVA_ZA_UKLANJANJE
// prijava ulov se uklanja sa mape, a autoru se oduzimaju poeni koje je za njega dobio.
//
// Status u PrijavljeniUlovi:
//   "aktivan"  - ulov je na mapi, prijave se broje
//   "uklonjen" - ulov je automatski uklonjen
//   "vrati"    - ADMIN upisuje ručno u konzoli; funkcija vraća ulov i menja status u "vracen"
//   "vracen"   - admin je pregledao ulov; nove prijave se beleže, ali ga više ne uklanjaju
const BROJ_PRIJAVA_ZA_UKLANJANJE = 3;

// Ime za prikaz adminu: "Ime Prezime (korisnickoIme)"
function podaciKorisnika(snap) {
    const k = snap.exists ? snap.data() : {};
    const imePrezime = k.imePrezime || "";
    const korisnickoIme = k.userName || k.username || "";
    let prikaz = imePrezime && korisnickoIme && imePrezime !== korisnickoIme
        ? `${imePrezime} (${korisnickoIme})`
        : (imePrezime || korisnickoIme || "Nepoznat korisnik");
    return { uid: snap.id, imePrezime, korisnickoIme, prikaz };
}

exports.obradiPrijavuUlova = functions.firestore
    .document("Prijave/{prijavaId}")
    .onCreate(async (snap) => {
        const prijava = snap.data();
        const db = admin.firestore();
        const spotRef = db.collection("MestaZaPecanje").doc(prijava.spotId);
        const evidencijaRef = db.collection("PrijavljeniUlovi").doc(prijava.spotId);
        const sada = Timestamp.now();

        const ishod = await db.runTransaction(async (tx) => {
            const [spotSnap, evidencijaSnap, prijavioSnap, autorSnap] = await Promise.all([
                tx.get(spotRef),
                tx.get(evidencijaRef),
                tx.get(db.collection("Korisnici").doc(prijava.prijavioId)),
                tx.get(db.collection("Korisnici").doc(prijava.autorUlovaId)),
            ]);
            const prethodno = evidencijaSnap.exists ? evidencijaSnap.data() : null;

            if (prethodno?.prijavili?.includes(prijava.prijavioId)) return "duplikat";

            const prijavio = podaciKorisnika(prijavioSnap);
            const brojPrijava = (prethodno?.brojPrijava ?? 0) + 1;
            const podaci = {
                spotId: prijava.spotId,
                autorUlovaId: prijava.autorUlovaId,
                autorUlova: podaciKorisnika(autorSnap).prikaz,
                brojPrijava,
                prijavili: FieldValue.arrayUnion(prijava.prijavioId),
                prijaviliImena: FieldValue.arrayUnion(prijavio.prikaz),
                razlozi: FieldValue.arrayUnion({
                    razlog: prijava.razlog,
                    opis: prijava.opis ?? "",
                    prijavioId: prijava.prijavioId,
                    prijavio: prijavio.prikaz,
                    vreme: prijava.timestamp ? Timestamp.fromMillis(prijava.timestamp) : sada,
                }),
                poslednjaPrijava: sada,
                status: prethodno?.status ?? "aktivan",
            };
            if (!prethodno) podaci.prvaPrijava = sada;
            // Kopija ulova, da ostane za pregled i vraćanje kad se ulov ukloni
            if (spotSnap.exists) podaci.ulov = spotSnap.data();

            if (brojPrijava >= BROJ_PRIJAVA_ZA_UKLANJANJE && podaci.status === "aktivan" && spotSnap.exists) {
                const ulov = spotSnap.data();
                tx.delete(spotRef);
                if (ulov.vrstaId) {
                    tx.delete(db.collection("VrsteRiba").doc(ulov.vrstaId).collection("Ulovi").doc(prijava.spotId));
                }
                // Oduzimaju se samo poeni tekuće sezone (vraćanje ulova vraća isto toliko)
                const poeni = require("./sezona")._interno.uTekucojSezoni(ulov) ? require("./ulovi")._interno.dodeljeniPoeni(ulov) : 0;
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

// Admin u konzoli promeni status u "vrati": ulov se vraća na mapu i autoru se vraćaju poeni
exports.vratiPrijavljeniUlov = functions.firestore
    .document("PrijavljeniUlovi/{spotId}")
    .onUpdate(async (change, context) => {
        const pre = change.before.data();
        const posle = change.after.data();
        if (posle.status !== "vrati" || pre.status === "vrati") return null;

        const db = admin.firestore();
        const spotId = context.params.spotId;
        const spotRef = db.collection("MestaZaPecanje").doc(spotId);

        await db.runTransaction(async (tx) => {
            const spotSnap = await tx.get(spotRef);
            const ulov = posle.ulov;
            let vracenoPoena = 0;

            // Ulov je bio uklonjen: vraćamo ga iz sačuvane kopije
            if (!spotSnap.exists && ulov) {
                tx.set(spotRef, ulov);
                if (ulov.vrstaId && ulov.cuvarka !== true) {
                    tx.set(db.collection("VrsteRiba").doc(ulov.vrstaId).collection("Ulovi").doc(spotId), {
                        authorId: ulov.authorId,
                        authorUsername: ulov.authorUsername ?? "",
                        duzinaRibe: ulov.duzinaRibe ?? 0,
                        tezinaRibe: ulov.tezinaRibe ?? 0,
                        mamac: ulov.mamac ?? "",
                        tehnika: ulov.tehnika ?? "",
                        timestamp: ulov.timestamp ?? Date.now(),
                    });
                }
                vracenoPoena = posle.oduzetoPoena ?? 0;
                if (vracenoPoena > 0 && ulov.authorId) {
                    tx.set(db.collection("Korisnici").doc(ulov.authorId), {
                        poeni: FieldValue.increment(vracenoPoena),
                    }, { merge: true });
                }
            }

            tx.update(change.after.ref, {
                status: "vracen",
                vracen: Timestamp.now(),
                vracenoPoena,
                oduzetoPoena: 0,
            });
        });

        console.log(`Ulov ${spotId} vraćen od strane admina`);
        return null;
    });

// Vodostaji reka (functions/vodostaji.js)
const vodostaji = require("./vodostaji");
exports.osveziVodostaje = vodostaji.osveziVodostaje;
exports.detaljiStanice = vodostaji.detaljiStanice;

// Izmena i brisanje ulova, brisanje naloga (functions/ulovi.js)
const ulovi = require("./ulovi");
exports.noviUlov = ulovi.noviUlov;
exports.obrisanUlov = ulovi.obrisanUlov;
exports.izmenjenUlov = ulovi.izmenjenUlov;
exports.obrisiNalog = ulovi.obrisiNalog;

// Otisak slike ulova, provera iste slike i dodela poena (functions/slike.js)
exports.proveriSlikuUlova = require("./slike").proveriSlikuUlova;
exports.proveriOtisakSlike = require("./slike").proveriOtisakSlike;

// Vode u blizini van Srbije, Geoapify ključ je samo na serveru (functions/vode.js)
exports.vodeUBlizini = require("./vode").vodeUBlizini;

// Zaštita Deda Pere od zloupotrebe (functions/deda.js)
exports.proveriOdgovorDedePere = require("./deda").proveriOdgovorDedePere;

// Mailovi adminu: dnevni pregled prijava, nedeljni pregled, uklonjen ulov (functions/izvestaji.js)
const izvestaji = require("./izvestaji");
exports.dnevniPregledPrijava = izvestaji.dnevniPregledPrijava;
exports.nedeljniPregled = izvestaji.nedeljniPregled;
exports.obavestiUklonjenUlov = izvestaji.obavestiUklonjenUlov;
exports.obavestiVelikiUlov = izvestaji.obavestiVelikiUlov;
exports.obavestiPrijavuKorisnika = izvestaji.obavestiPrijavuKorisnika;

// Admin panel u aplikaciji (functions/admin.js)
const adminPanel = require("./admin");
exports.jesamLiAdmin = adminPanel.jesamLiAdmin;
exports.adminPregled = adminPanel.adminPregled;
exports.adminUlovURedu = adminPanel.adminUlovURedu;
exports.adminObrisiUlov = adminPanel.adminObrisiUlov;
exports.adminVelikiUlov = adminPanel.adminVelikiUlov;

// Vremenska prognoza preko servera, ključ i keš na serveru (functions/vreme.js)
exports.vremenskaPrognoza = require("./vreme").vremenskaPrognoza;

// ─── PRIVATNE PORUKE: push obaveštenje primaocu ───
// Dodir na obaveštenje otvara razgovor sa pošiljaocem (OTVORI_CHAT); dok je aplikacija otvorena,
// poruku prikazuje balončić, pa aplikacija ovo obaveštenje preskače.
exports.posaljiObavestenjeZaPoruku = functions.firestore
    .document("PrivateChats/{sobaId}/Messages/{porukaId}")
    .onCreate(async (snap, context) => {
        const poruka = snap.data();
        const ucesnici = context.params.sobaId.split("_");
        const primalac = poruka.primalacId || ucesnici.find((u) => u !== poruka.senderId);
        if (!primalac || primalac === poruka.senderId || !ucesnici.includes(primalac)) return null;

        const db = admin.firestore();
        const [korisnik, posiljalac] = await Promise.all([
            db.collection("Korisnici").doc(primalac).get(),
            db.collection("Korisnici").doc(poruka.senderId).get(),
        ]);
        const ime = String(poruka.senderUsername || posiljalac.data()?.userName || "Pecaroš").slice(0, 40);
        const tekst = String(poruka.text || "").slice(0, 120);

        // Spisak razgovora (ekran Poruke): Razgovori/{uid}/sa/{drugi} za oba učesnika
        const vreme = typeof poruka.timestamp === "number" ? poruka.timestamp : Date.now();
        const kratko = tekst.slice(0, 80);
        const paket = db.batch();
        paket.set(db.doc(`Razgovori/${primalac}/sa/${poruka.senderId}`), {
            uid: poruka.senderId, ime, slika: posiljalac.data()?.slikaURL || "",
            poslednja: kratko, vreme, odMene: false, neprocitano: FieldValue.increment(1),
        }, { merge: true });
        paket.set(db.doc(`Razgovori/${poruka.senderId}/sa/${primalac}`), {
            uid: primalac, ime: String(korisnik.data()?.userName || "Pecaroš").slice(0, 40), slika: korisnik.data()?.slikaURL || "",
            poslednja: kratko, vreme, odMene: true,
        }, { merge: true });
        await paket.commit().catch((e) => console.error("Spisak razgovora nije ažuriran:", e.message));

        const token = korisnik.exists ? korisnik.data().fcmToken : null;
        if (!token) return null;
        try {
            await admin.messaging().send({
                token,
                notification: { title: `Nova poruka od ${ime} 💬`, body: tekst },
                data: { tip: "poruka", OTVORI_CHAT: String(poruka.senderId), IME: ime },
                android: { notification: { channelId: "fishspot_obavestenja" } },
            });
        } catch (e) {
            console.error("Obaveštenje o poruci nije poslato:", e.message);
        }
        return null;
    });

// Izazovi: prijava, rang i pravljenje iz admin panela (functions/izazovi.js)
const izazovi = require("./izazovi");
exports.izazoviPromenaUlova = izazovi.izazoviPromenaUlova;
exports.pridruziSeIzazovu = izazovi.pridruziSeIzazovu;
exports.adminSacuvajIzazov = izazovi.adminSacuvajIzazov;
exports.adminObrisiIzazov = izazovi.adminObrisiIzazov;

// Bedževi: računanje iz proverenih ulova i obaveštenje (functions/bedzevi.js)
const bedzevi = require("./bedzevi");
exports.bedzeviPromenaUlova = bedzevi.bedzeviPromenaUlova;
exports.osveziMojeBedzeve = bedzevi.osveziMojeBedzeve;
exports.adminBedzPrijatelj = bedzevi.adminBedzPrijatelj;

// ─── SEZONA RANG LISTE (do 31.12.) ───
exports.novaSezona = require("./sezona").novaSezona;

// ─── NEDELJNO ČIŠĆENJE (stare prijave, slike bez ulova, Deda Pera) ───
exports.nedeljnoCiscenje = require("./ciscenje").nedeljnoCiscenje;

// ─── ADMIN: PREBACIVANJE ULOVA SA NALOGA NA NALOG (functions/prenos.js) ───
exports.adminPrebaciUlove = require("./prenos").adminPrebaciUlove;
exports.adminYoutube = require("./admin").adminYoutube;

// Mailovi adminu preko Brevo-a (tajna BREVO_API_KEY):
//  - dnevniPregledPrijava: svako jutro u 8:00, samo ako je bilo novih prijava ulova
//  - nedeljniPregled: ponedeljkom u 8:05, uvek (drži Brevo ključ aktivnim, ističe posle 90 dana bez slanja)
//  - obavestiUklonjenUlov: odmah, kada moderacija ukloni ulov posle 3 prijave
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { Timestamp } = require("firebase-admin/firestore");

const ADMIN_MAIL = "fishspot.rs@gmail.com";
const KONZOLA = "https://console.firebase.google.com/project/fishspot-52735/firestore/databases/-default-/data/~2FPrijavljeniUlovi~2F";
const DAN_MS = 24 * 3600 * 1000;

// Tekst korisnika ide u HTML, pa ga obavezno "pretvaramo u tekst"
function esc(t) {
    return String(t ?? "").replace(/[&<>"']/g, (z) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[z]));
}

function datum(ts) {
    const d = ts?.toDate ? ts.toDate() : new Date(ts);
    return d.toLocaleString("sr-RS", { timeZone: "Europe/Belgrade", day: "numeric", month: "numeric", hour: "2-digit", minute: "2-digit" });
}

function omotac(naslov, sadrzaj) {
    return `<div style="background:#0B1612;padding:24px;font-family:Arial,sans-serif;color:#B0BDB9">
  <div style="max-width:640px;margin:0 auto">
    <div style="color:#6B8078;font-size:12px;font-weight:bold;letter-spacing:1px">FISHSPOT • ADMIN</div>
    <h1 style="color:#fff;font-size:22px;margin:6px 0 18px">${esc(naslov)}</h1>
    ${sadrzaj}
    <p style="color:#6B8078;font-size:12px;margin-top:24px">Ovaj mail šalje FishSpot automatski. Ulov vraćaš tako što u konzoli
    u dokumentu PrijavljeniUlovi upišeš <b>status: "vrati"</b>.</p>
  </div></div>`;
}

function karticaPrijave(id, p) {
    const u = p.ulov || {};
    const uklonjen = p.status === "uklonjen";
    const boja = uklonjen ? "#E74C3C" : p.status === "vracen" ? "#2ECC71" : "#F1C40F";
    const oznaka = uklonjen ? `UKLONJEN (oduzeto ${p.oduzetoPoena ?? 0} poena)` : p.status === "vracen" ? "VRAĆEN" : "AKTIVAN";
    const razlozi = (p.razlozi || []).map((r) => `<li style="margin:4px 0"><b style="color:#fff">${esc(r.razlog)}</b>
        ${r.opis ? `– ${esc(r.opis)}` : ""}<br><span style="color:#6B8078;font-size:12px">${esc(r.prijavio)} · ${datum(r.vreme)}</span></li>`).join("");
    const slika = u.slikaUrl ? `<img src="${esc(u.slikaUrl)}" width="120" style="border-radius:10px;float:right;margin-left:12px">` : "";
    return `<div style="background:#132218;border:1px solid #1E3328;border-radius:14px;padding:16px;margin-bottom:14px;overflow:hidden">
    ${slika}
    <div style="color:${boja};font-size:12px;font-weight:bold">${oznaka} · ${p.brojPrijava ?? 0} prijav${(p.brojPrijava ?? 0) === 1 ? "a" : "e"}</div>
    <div style="color:#fff;font-size:18px;font-weight:bold;margin:4px 0">${esc(u.vrstaRibe || "Nepoznata vrsta")}
      <span style="color:#B0BDB9;font-size:14px;font-weight:normal">· ${esc(u.duzinaRibe ?? "?")} cm · ${esc(u.tezinaRibe ?? "?")} kg</span></div>
    <div style="font-size:13px">Autor: <b style="color:#fff">${esc(p.autorUlova || u.authorUsername || "?")}</b> · ${esc(u.naziv || "")}</div>
    <ul style="padding-left:18px;margin:10px 0">${razlozi}</ul>
    <a href="${KONZOLA}${encodeURIComponent(id)}" style="display:inline-block;background:#2ECC71;color:#0B1612;padding:8px 14px;border-radius:10px;text-decoration:none;font-weight:bold;font-size:13px">Otvori u konzoli</a>
  </div>`;
}

async function posaljiMail(naslov, html) {
    // U emulatoru se mail ne šalje, nego upisuje u kolekciju TestMailovi (za testove)
    if (process.env.FUNCTIONS_EMULATOR === "true") {
        await admin.firestore().collection("TestMailovi").add({ naslov, html, vreme: Date.now() });
        return;
    }
    const odgovor = await fetch("https://api.brevo.com/v3/smtp/email", {
        method: "POST",
        headers: { "api-key": process.env.BREVO_API_KEY, "content-type": "application/json", accept: "application/json" },
        body: JSON.stringify({
            sender: { name: "FishSpot", email: ADMIN_MAIL },
            to: [{ email: ADMIN_MAIL }],
            subject: naslov,
            htmlContent: html,
        }),
    });
    if (!odgovor.ok) throw new Error(`Brevo ${odgovor.status}: ${await odgovor.text()}`);
}

async function napraviDnevniPregled(db, sada = Date.now()) {
    const od = Timestamp.fromMillis(sada - DAN_MS);
    const snap = await db.collection("PrijavljeniUlovi").where("poslednjaPrijava", ">=", od).get();
    if (snap.empty) return null;
    const docs = snap.docs.sort((a, b) => (b.data().brojPrijava ?? 0) - (a.data().brojPrijava ?? 0));
    const uklonjenih = docs.filter((d) => d.data().status === "uklonjen").length;
    const novihPrijava = docs.reduce((zbir, d) => zbir + (d.data().razlozi || [])
        .filter((r) => (r.vreme?.toMillis ? r.vreme.toMillis() : 0) >= od.toMillis()).length, 0);
    const naslov = `FishSpot: ${novihPrijava} nov${novihPrijava === 1 ? "a prijava" : "e prijave"}` +
        (uklonjenih ? `, ${uklonjenih} uklonjen${uklonjenih === 1 ? " ulov" : "a ulova"}` : "");
    const zbir = `<p style="font-size:15px">U poslednja 24 sata: <b style="color:#fff">${novihPrijava}</b> novih prijava za
        <b style="color:#fff">${docs.length}</b> ulov(a), od toga <b style="color:#E74C3C">${uklonjenih}</b> uklonjeno automatski.</p>`;
    return { naslov, html: omotac("Dnevni pregled prijava", zbir + docs.map((d) => karticaPrijave(d.id, d.data())).join("")) };
}

async function napraviNedeljniPregled(db, sada = Date.now()) {
    const nedelja = sada - 7 * DAN_MS;
    const [ulovi, prijave, korisnici, svihUlova] = await Promise.all([
        db.collection("MestaZaPecanje").where("timestamp", ">=", nedelja).count().get(),
        db.collection("Prijave").where("timestamp", ">=", nedelja).count().get(),
        db.collection("Korisnici").count().get(),
        db.collection("MestaZaPecanje").count().get(),
    ]);
    const uklonjeni = await db.collection("PrijavljeniUlovi").where("status", "==", "uklonjen").count().get();
    const red = (naziv, vrednost) => `<tr><td style="padding:8px 0;color:#B0BDB9">${naziv}</td>
        <td style="padding:8px 0;color:#fff;font-weight:bold;text-align:right">${vrednost}</td></tr>`;
    const tabela = `<table style="width:100%;background:#132218;border:1px solid #1E3328;border-radius:14px;padding:12px 16px">
        ${red("Novih ulova ove nedelje", ulovi.data().count)}
        ${red("Prijava ulova ove nedelje", prijave.data().count)}
        ${red("Ulova trenutno uklonjenih (čekaju pregled)", uklonjeni.data().count)}
        ${red("Ukupno korisnika", korisnici.data().count)}
        ${red("Ukupno ulova na mapi", svihUlova.data().count)}
      </table>`;
    return { naslov: `FishSpot: nedeljni pregled (${ulovi.data().count} novih ulova)`, html: omotac("Nedeljni pregled", tabela) };
}

const saKljucem = functions.runWith({ secrets: ["BREVO_API_KEY"] });

exports.dnevniPregledPrijava = saKljucem.pubsub.schedule("0 8 * * *").timeZone("Europe/Belgrade").onRun(async () => {
    const mail = await napraviDnevniPregled(admin.firestore());
    if (!mail) { console.log("Dnevni pregled: nema novih prijava, mail se ne šalje"); return null; }
    await posaljiMail(mail.naslov, mail.html);
    console.log(`Dnevni pregled poslat: ${mail.naslov}`);
    return null;
});

exports.nedeljniPregled = saKljucem.pubsub.schedule("5 8 * * 1").timeZone("Europe/Belgrade").onRun(async () => {
    const mail = await napraviNedeljniPregled(admin.firestore());
    await posaljiMail(mail.naslov, mail.html);
    console.log(`Nedeljni pregled poslat: ${mail.naslov}`);
    return null;
});

exports.obavestiUklonjenUlov = saKljucem.firestore.document("PrijavljeniUlovi/{spotId}").onWrite(async (change, context) => {
    const pre = change.before.exists ? change.before.data() : {};
    const posle = change.after.exists ? change.after.data() : null;
    if (!posle || posle.status !== "uklonjen" || pre.status === "uklonjen") return null;
    const vrsta = posle.ulov?.vrstaRibe || "ulov";
    await posaljiMail(`FishSpot: uklonjen ulov (${vrsta}) posle ${posle.brojPrijava} prijave`,
        omotac("Ulov je automatski uklonjen", karticaPrijave(context.params.spotId, posle)));
    console.log(`Mail o uklonjenom ulovu ${context.params.spotId} poslat`);
    return null;
});

exports._interno = { napraviDnevniPregled, napraviNedeljniPregled, esc };

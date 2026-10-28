// Zaštita Deda Pere (Gemini ekstenzija firestore-genai-chatbot).
// Ekstenzija upiše odgovor u bot_chats/{uid}/messages/{id}.response; ova funkcija ga zatim proveri:
// ako je pitanje pokušaj zloupotrebe (promena uloge, traženje sistemskog prompta) ili odgovor
// otkriva uputstva, odgovor se zamenjuje odbijanjem. Aplikacija prikazuje samo odgovore sa provereno: true.
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

const ODBIJANJE = "Bistro, sinko! Deda Pera priča samo o pecanju: pribor, mamci, mesta, riba i lovostaj. " +
    "Te druge stvari ostavi nekom drugom, a mene pitaj nešto o ribolovu pa da ti pomognem!";

const ZAUZET = "Bistro, sinko! Deda Pera trenutno priča sa mnogo pecaroša odjednom, pa nije stigao da ti odgovori. " +
    "Pošalji mi pitanje ponovo za minut-dva, ovo ti se ne računa u dnevna pitanja.";

// Vraća jedno pitanje u dnevni limit (BotLimit/{uid}.broj - 1, ne ispod nule)
async function vratiPitanje(uid) {
    const db = admin.firestore();
    const ref = db.collection("BotLimit").doc(uid);
    await db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists) return;
        tx.update(ref, { broj: Math.max(0, (snap.data().broj || 0) - 1) });
    });
}

const ZAMISLIO_SE = "Bistro, sinko! Deda Pera se malo zamislio pa mu je misao pobegla. Pitaj me ponovo, " +
    "ovo pitanje ti se ne računa u dnevna.";

// Tragovi "razmišljanja" modela na engleskom, koje ne sme da vidi korisnik
function sadrziRazmisljanje(odgovor) {
    const t = String(odgovor || "");
    return /^\s*(THOUGHT|THINKING|Thought|Reasoning)\s*:/.test(t) ||
        /\bTHOUGHT:/.test(t) ||
        /\b(the user is asking|the user corrected|I need to|I must|I should|safety rules|Rule \d)\b/i.test(t);
}

function normalizuj(tekst) {
    return String(tekst || "")
        .toLowerCase()
        .replace(/[šś]/g, "s").replace(/[čć]/g, "c").replace(/ž/g, "z").replace(/đ/g, "dj")
        .replace(/\s+/g, " ");
}

// Izrazi koji u pitanju znače pokušaj da se promeni uloga ili izvuku uputstva
const ZLOUPOTREBA = [
    "system prompt", "sistemski prompt", "systemprompt", "system message", "sistemska poruka",
    "user prompt", "<system", "[system", "### system",
    "ignore previous", "ignore all", "ignore the above", "ignore your", "disregard",
    "ignorisi", "zanemari prethodn", "zanemari sve", "zaboravi sve", "zaboravi prethodn", "zaboravi sta",
    "forget everything", "forget all", "forget your", "forget what",
    "you are now", "from now on you", "ti si sada", "od sada si", "od sada ces", "sada si ti",
    "pretvaraj se", "pretend to be", "pretend you", "act as", "glumi ", "budi ti ",
    "tvoj zadatak je", "your task is", "your new task", "nova uloga", "new role", "roleplay",
    "jailbreak", "developer mode", "dan mode", "do anything now",
    "repeat the prompt", "repeat your", "print your", "show your prompt", "reveal your",
    "ponovi prompt", "ponovi uputstv", "ponovi instrukcij", "napisi svoj prompt", "koji su tvoji uputstv",
    "kakva su tvoja uputstv", "tvoja uputstva", "tvoje instrukcije", "your instructions", "initial prompt",
    "prompt that was", "original prompt", "pocetni prompt",
];

// Delovi uputstava koji ne smeju da se pojave u odgovoru
const DELOVI_UPUTSTAVA = [
    "dp-reka-7731", "pravila tvog karaktera", "bezbednosna pravila", "najvisi prioritet",
    "ti si deda pera, stari", "prekaljeni alas i strastveni", "autenticnom stilu starog recnog vuka",
    "lokalni ribolovacki sleng i izraze", "moraju biti 100% tacni", "obracaj domacinski i blisko",
    "svaki odgovor zapocni ili zavrsi",
];

function jeZloupotreba(pitanje) {
    const t = normalizuj(pitanje);
    return ZLOUPOTREBA.some((izraz) => t.includes(izraz));
}

function otkrivaUputstva(odgovor) {
    const t = normalizuj(odgovor);
    return DELOVI_UPUTSTAVA.some((deo) => t.includes(deo));
}

exports.proveriOdgovorDedePere = functions.firestore
    .document("bot_chats/{uid}/messages/{porukaId}")
    .onWrite(async (change, context) => {
        if (!change.after.exists) return null;
        const poruka = change.after.data();
        if (poruka.provereno === true) return null;

        // Gemini nije odgovorio (najčešće prekoračen broj pitanja u minutu na besplatnom planu):
        // umesto tišine korisnik dobija poruku, a pitanje mu se vraća u dnevni limit
        if (!poruka.response && poruka.status?.state === "ERROR") {
            console.warn(`Deda Pera: Gemini greška za ${context.params.uid}: ${poruka.status.error || "nepoznato"}`);
            await change.after.ref.update({ response: ZAUZET, provereno: true, zauzet: true });
            await vratiPitanje(context.params.uid);
            return null;
        }
        if (!poruka.response) return null; // čeka se odgovor ekstenzije

        // Model je u odgovor ubacio svoje "razmišljanje" (npr. "THOUGHT: The user..."): korisnik to ne sme da vidi
        if (sadrziRazmisljanje(poruka.response)) {
            console.warn(`Deda Pera: odgovor sa razmišljanjem modela za ${context.params.uid}, zamenjen`);
            await change.after.ref.update({ response: ZAMISLIO_SE, provereno: true, zauzet: true });
            await vratiPitanje(context.params.uid);
            return null;
        }

        const blokirano = jeZloupotreba(poruka.prompt) || otkrivaUputstva(poruka.response);
        const izmene = { provereno: true };
        if (blokirano) {
            izmene.response = ODBIJANJE;
            izmene.blokirano = true;
            console.warn(`Deda Pera: blokiran odgovor za ${context.params.uid}/${context.params.porukaId}`);
        }
        await change.after.ref.update(izmene);
        return null;
    });

exports._interno = { jeZloupotreba, otkrivaUputstva, sadrziRazmisljanje, ODBIJANJE, ZAUZET };

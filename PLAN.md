# FishSpot – plan razvoja

Verzija **1.0** je završena: ulovi na mapi (do 5 slika, čuvarka, mamci po kategorijama, AR merenje), provera fotografija i poeni, rang lista po sezonama, izazovi, bedževi, poruke, Deda Pera, vreme, vodostaji, lovostaj, obaveštenja o vodama, blokiranje i prijava korisnika, potvrda e-adrese, uslovi korišćenja i politika privatnosti.

Plan je okvir, ne obaveza: posle svake verzije pomeramo napred ono što korisnici najviše traže.

---

## 1.0.x – tokom zatvorenog testiranja
Samo popravke, bez novih funkcija.

- [ ] Greške koje prijave testeri
- [ ] Opis vremena latinicom (OpenWeather vraća ćirilicu: „Ниски облаци“)
- [ ] Posle prvog otpremanja na Play: SHA-1 ključa za potpisivanje u Firebase (Google prijava), Maps ključ i App Check
- [ ] Ograničiti API ključeve u Google Cloud konzoli (paket + SHA-1, samo potrebni API-ji)
- [ ] Probati dodavanje ulova bez interneta (režim „avion“) na pravom telefonu

## 1.1 – 2 do 4 nedelje posle javne objave
Poverenje i moderacija, pre razgovora sa jezerima i sponzorima.

- [ ] **Sakrij tačnu lokaciju**: drugi vide krug od ~3 km i naziv vode, tačno mesto vidi samo autor (i admin)
- [ ] **Zabrana korisnika** iz admin panela: gasi nalog, sklanja njegov sadržaj, pravila zabranjuju upis
- [ ] **App Check uključen** za Firestore, Storage i funkcije (kad su svi korisnici na Play verziji)
- [ ] **Bedževi izazova**: jedinstven bedž za svaki izazov, polje sponzor i logo na izazovu
- [ ] Manji APK: sažet model za prepoznavanje ribe (11 MB → ~3 MB)
- [ ] Pristupačnost: opisi ikonica, kontrast sitnog teksta

## 1.2 – zima 2026/27
Razlozi da se korisnici vraćaju i van sezone.

- [ ] **Statistika na profilu**: najveći ulov po vrsti, ulovi po mesecima, omiljeni mamac i tehnika, moja mapa ulova
- [ ] **Praćenje pecaroša** i spisak ulova onih koje pratiš
- [ ] **Kuća slavnih**: pobednici sezone (podaci se čuvaju od 1.1.2027.)
- [ ] **„Dobar dan za pecanje“**: jutarnje obaveštenje kad su vreme i vodostaj povoljni za omiljene vode
- [ ] **Partner jezera i ribnjaci** na mapi: opis, cena karte, kontakt

## 1.3 – proleće 2027 (početak sezone)
- [ ] **Prepoznavanje vrste ribe** sa slike (skup podataka je pripremljen)
- [ ] **Doterati rad bez interneta** (osnova već radi: ulov se čuva u telefonu, slike se šalju kad se pojavi signal):
  - bez čekanja od 10 s na proveru iste slike
  - uslovi pri ulovu i naziv mesta se dopune kad stigne internet
  - oznaka „čeka slanje“ na ulovu
- [ ] **Privatni ulovi i dnevnik**: ulov koji vidi samo autor, sa beleškama
- [ ] **Ribolovna područja i dozvole**: koja dozvola važi na kojoj vodi

## 2.0 – kad aplikacija stane na noge
- [ ] **iPhone verzija**
- [ ] **Sajt FishSpot-a**: rang lista i izazovi na webu, stranica za sponzore
- [ ] **Klubovi i timovi**: ekipna takmičenja, rang lista udruženja
- [ ] **Region**: BiH, Crna Gora, Hrvatska (jezik, lovostaji, vode)
- [ ] **Prihod**: sponzori izazova, partner jezera, dobrovoljna podrška (bez reklama)

---

## Van koda
| Kada | Šta |
|---|---|
| sada | testeri iz pecaroških grupa (12–20 Gmail adresa) |
| dan izlaska na Play | objava u Facebook grupama i na Instagramu |
| 2–4 nedelje posle | jezera i ribnjaci (besplatno mesto na mapi) |
| 1–2 meseca posle | sponzori, sa brojkama i prvim izazovom iza sebe |

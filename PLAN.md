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
- [ ] **Obaveštenja u aplikaciji** (bez slanja na telefon, da ne smaramo ljude):
  - na objavi klik na broj lajkova otvara spisak ko je lajkovao (slika, ime, klik vodi na profil)
  - na kartici „Moj profil“, levo od promene slike, zvonce sa brojem nepročitanih; crvena tačkica na ikonici profila na mapi
  - jedan red po ulovu: „Marko i još 3 korisnika su lajkovali tvoj ulov: Šaran 4,2 kg“, klik otvara ulov
  - otvaranjem spiska nestaju broj i tačkica; upisuje server; ne broje se odlajk/ponovni lajk, svoj lajk i blokirani; brisanje posle 60 dana
  - kasnije u isto mesto: osvojen bedž, novi komentar, odobren ulov preko 5 kg
- [ ] **Bedž „Plovkaš“**: 10 proverenih ulova sa tehnikom Plovak (kao Fiderista; nova slika bedža, ista pravila: samo ulovi sa poenima). Razmotriti i bedž za Dubinac, jedinu tehniku koja ostaje bez bedža
- [ ] **„Oceni FishSpot“**: Google-ov prozor za ocenu u aplikaciji, npr. posle trećeg ulova (Google sam ograničava koliko često se pojavljuje)
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
- [ ] **Slike i opisi riba** (predlog testera): sličica uz svaku vrstu u kalendaru lovostaja (i pri izboru vrste kod ulova); klik otvara prozor: velika slika, izgled i razlika od sličnih vrsta, stanište, prosečna i rekordna težina, najbolji mamci i tehnika, lovostaj i najmanja mera
  - slike u aplikaciji (rade bez interneta), ~30 vrsta kao mali WebP, ukupno ~1 MB; samo slobodna licenca (Wikimedia Commons) ili sopstvene, uz ekran „Izvori slika“
  - opisi u bazi (VrsteRiba), da se ispravljaju iz admin panela bez nove verzije
  - procena: ~2 dana (pola dana kod, ostalo slike i opisi; opise pregleda Đorđe)
- [ ] **Doterati rad bez interneta** (osnova već radi: ulov se čuva u telefonu, slike se šalju kad se pojavi signal):
  - bez čekanja od 10 s na proveru iste slike
  - uslovi pri ulovu i naziv mesta se dopune kad stigne internet
  - oznaka „čeka slanje“ na ulovu
- [ ] **Privatni ulovi i dnevnik**: ulov koji vidi samo autor, sa beleškama
- [ ] **Mapa ribarskih područja**: Srbija je podeljena na 17 područja (Rešenje ministra, Sl. glasnik 90/2015), granice su opisane po opštinama i rekama. Aplikacija kaže „Nalaziš se u RP Južna Morava 1, dnevnu dozvolu izdaje …“, i upozori kad si u zaštićenom području (posebna dozvola, negde zabrana)
- [ ] **Dozvole**: cene za tekuću godinu (2027: godišnja 9.000, dnevna 1.300, višednevna 2.600 din), ko je korisnik kog područja, gde se kupuje (adrese, telefoni, linkovi; onlajn gde postoji, npr. Ribolovački savez Vojvodine)
- [ ] **Moja dozvola**: slikaš svoju dozvolu, aplikacija podseti pred istek
- [ ] **Uplata dozvole preko IPS QR koda** (prvo JVP „Vode Vojvodine“, koje primaju uplatu na račun): aplikacija pravi NBS IPS QR sa računom, iznosom, svrhom i pozivom na broj, korisnik plaća svojom bankom, novac ide direktno korisniku područja (FishSpot ne dira novac, ne treba firma ni procesor).
  - Zakon (čl. 41): **godišnja dozvola se kupuje u mestu prebivališta ili boravišta**, kod korisnika područja za tu opštinu. Pecaroš iz Niša ne kupuje godišnju od Voda Vojvodine, ali dnevnu i višednevnu za njihove vode može. Aplikacija mora da nudi godišnju samo prema mestu prebivališta.
  - Dozvola je štampani dokument (štampa je korisnik), zakon ne pominje elektronsku dozvolu. Pre izrade od Voda Vojvodine (Služba za ribarstvo 021/4881-575) saznati: kako se posle uplate dobija dozvola (preuzimanje, pošta), da li potvrda o uplati važi na vodi, kako se dokazuje popust, koji model poziva na broj
- [ ] **Evidencija ulova** (čl. 45): pecaroš je dužan da vodi evidenciju ulova na jedinstvenom obrascu i preda je korisniku do kraja godine, a bez nje ne dobija dozvolu za sledeću godinu. FishSpot već beleži ulove: izvoz u obliku obrasca (PDF za štampu) i podsetnik u decembru. Pitati korisnike područja da li prihvataju odštampan izvoz
- [ ] Prvi korak: spisak korisnika svih 17 područja (ministarstvo, katastar ribolovnih voda, lokalni SRD-ovi)

## 2.0 – kad aplikacija stane na noge
- [ ] **iPhone verzija**
- [ ] **Sajt FishSpot-a**: rang lista i izazovi na webu, stranica za sponzore
- [ ] **Klubovi i timovi**: ekipna takmičenja, rang lista udruženja
- [ ] **Region**: BiH, Crna Gora, Hrvatska (jezik, lovostaji, vode)
- [ ] **Prihod**: sponzori izazova, partner jezera, dobrovoljna podrška (bez reklama)
- [ ] **Prodaja dozvola u aplikaciji**: samo uz ugovor sa korisnikom ribarskog područja (npr. Srbijašume, Vode Vojvodine), registrovanu firmu, domaći procesor kartica i potvrdu ministarstva da elektronska dozvola važi na terenu

---

## Van koda
| Kada | Šta |
|---|---|
| sada | testeri iz pecaroških grupa (12–20 Gmail adresa) |
| dan izlaska na Play | objava u Facebook grupama i na Instagramu |
| 2–4 nedelje posle | jezera i ribnjaci (besplatno mesto na mapi) |
| 1–2 meseca posle | sponzori, sa brojkama i prvim izazovom iza sebe |

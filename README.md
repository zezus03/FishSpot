# FishSpot 🎣

**FishSpot** je Android aplikacija i društvena mreža za ribolovce u Srbiji. Korisnici objavljuju ulove na mapi, a model veštačke inteligencije na samom telefonu proverava da li je na fotografiji zaista riba. Aplikacija računa težinu po vrsti i dužini, upozorava na lovostaj i minimalnu meru, i prikazuje vremensku prognozu sa indeksom pecanja i vodostaje reka.

<p align="center">
  <img src="screenshots/mapa.jpg" width="200" alt="Mapa ulova"/>
  <img src="screenshots/novi-ulov.jpg" width="200" alt="Dodavanje ulova sa prepoznavanjem ribe"/>
  <img src="screenshots/lista-ulova.jpg" width="200" alt="Lista ulova"/>
</p>
<p align="center">
  <img src="screenshots/prognoza.jpg" width="200" alt="Vremenska prognoza i indeks za pecanje"/>
  <img src="screenshots/vodostaj.jpg" width="200" alt="Vodostaj reka (RHMZ)"/>
</p>

## Prepoznavanje ribe na fotografiji (AI)

Pre objave ulova model na telefonu proverava da li je na slici riba, bez interneta. Razvijen je u dve verzije; kod za trening obe je u [`ml/`](ml).

| | Verzija 1 | Verzija 2 (u aplikaciji) |
|---|---|---|
| **Mreža** | EfficientNetB1 + Dense 256 → 128 → sigmoid | EfficientNetV2B0 + Dropout → sigmoid |
| **Podaci** | ~5.000 sopstvenih slika | sopstvene slike + Open Images V7 (~9.000), uz „teške" primere bez ribe: ruke, kutije, voda, čamci, druge životinje |
| **Trening** | transfer learning, zatim fine-tuning 30 slojeva | transfer learning, zatim fine-tuning 60 slojeva (BatchNorm zamrznut), težine klasa, label smoothing, jača augmentacija |
| **Evaluacija** | validacioni skup (98,4%), ali slab na pravim slikama sa telefona | **128 pravih slika sa telefona** koje model nije video u treningu: **96,1% tačnosti**, prag 0,85 izabran po preciznosti i odzivu |
| **Na telefonu** | TFLite | TFLite float16, ulaz 256×256, ista priprema slike kao u treningu (EXIF rotacija) |

Glavna pouka: bolji podaci i poštena evaluacija na pravim slikama doneli su više od veće mreže. Skripte: [`ml/v1/train_fish_detector_v1.py`](ml/v1/train_fish_detector_v1.py), [`ml/v2/train_fish_detector_v2.py`](ml/v2/train_fish_detector_v2.py) (Google Colab).

## Funkcionalnosti

**Ulovi i mapa**
- Mapa ulova (tamni stil, satelit), filteri, lista ulova, profil i ulovi svakog korisnika.
- Novi ulov: fotografija (provera da je riba), vrsta sa liste od 43 vrste, dužina, težina, mamac, tehnika, ocena mesta.
- **Procena težine** po formuli W = a·Lᵇ sa koeficijentima po vrsti (FishBase), i **granice dužine i težine po vrsti**, tako da se ne mogu objaviti nerealni ulovi.
- **Merenje dužine AR kamerom** (Sceneform).
- Izmena i brisanje svog ulova; poeni se automatski usklađuju na serveru.
- Komentari, lajkovi i push obaveštenja (FCM) kad neko komentariše tvoj ulov.

**Pecanje**
- **Vremenska prognoza** sa **indeksom uspešnosti pecanja** (pritisak i njegova promena, temperatura, vetar, kiša, doba dana i godine) za danas i naredna 3 dana.
- **Vodostaji reka** (RHMZ Srbije): server osvežava podatke svakog sata, sa grafikonom za 7 dana i omiljenim stanicama.
- **Kalendar lovostaja** i minimalne mere za sve domaće vrste, uz upozorenje pri objavi ulova.
- Obaveštenja o vodama u blizini (u Srbiji iz lokalnih podataka, van Srbije preko servera).

**Zajednica**
- Rang lista po poenima (poeni po težini ulova).
- Privatne poruke sa pecarošima u blizini; deljenje lokacije samo uz pristanak.
- **Deda Pera**, AI savetnik za pecanje (Gemini), sa zaštitom od zloupotrebe i dnevnom granicom.
- Uvod sa Deda Perom pri prvom ulasku.

**Moderacija i administracija**
- Prijava neprikladnog ulova; posle 3 prijave ulov se automatski uklanja i autoru se oduzimaju poeni.
- **Admin panel** u aplikaciji: pregled prijava sa razlozima, vraćanje ili trajno brisanje ulova.
- **Mailovi adminu** (Brevo): dnevni pregled prijava, nedeljni pregled i odmah-obaveštenje o uklonjenom ulovu.
- Brisanje naloga iz aplikacije (sa svim podacima) i politika privatnosti.

## Arhitektura

- **Android:** Kotlin, Jetpack Compose, MVVM (ViewModel + StateFlow), kod organizovan po funkcijama (vertical slice: `feature/map`, `feature/spots`, `feature/weather`, `feature/fish`, `feature/admin`…).
- **Backend:** Firebase (Authentication, Firestore, Storage, Cloud Functions na Node.js, Cloud Messaging, Crashlytics, Hosting).
- **Šta radi server, a ne aplikacija**, da korisnik ne može da vara i da ključevi ne budu u aplikaciji:
  - poeni pri izmeni i brisanju ulova, moderacija prijava, brisanje naloga;
  - vodostaji (RHMZ), vremenska prognoza (OpenWeather) i vode van Srbije (Geoapify), sa keširanjem;
  - provera odgovora AI savetnika i admin akcije.
- **Bezbednost:**
  - Firestore pravila: poeni smeju samo da rastu (najviše 50 odjednom), granice dužine i težine po vrsti, usklađeni mamac i tehnika, ograničena dužina tekstova, dnevna granica pitanja AI savetniku;
  - API ključevi su Firebase tajne, a ne deo aplikacije;
  - lokacija se deli samo uz pristanak; admin pristup proverava server.
- **Testiranje:** unit testovi (indeks pecanja), Firebase emulator za funkcije i pravila, test modela na telefonu.

## Tehnologije

| Oblast | Tehnologije |
|---|---|
| Aplikacija | Kotlin, Jetpack Compose, Material 3, Navigation Compose, Coil, WorkManager |
| Backend | Firebase Auth (e-mail i Google), Firestore, Storage, Cloud Functions (Node.js 22), FCM, Crashlytics, Hosting |
| AI | TensorFlow Lite, EfficientNet, Python, TensorFlow/Keras, Google Colab, Gemini (Firebase ekstenzija) |
| Mape i podaci | Google Maps SDK, Maps Compose, OpenWeatherMap, RHMZ Srbije, Geoapify, OpenStreetMap |
| Ostalo | Sceneform (AR), Brevo (mailovi) |

## Pokretanje

1. Klonirati repozitorijum i otvoriti ga u Android Studio-u.
2. U `local.properties` dodati ključ za Google Maps, a za sopstveni Firebase projekat zameniti `app/google-services.json` (paket `rs.fishspot.app`).
3. Funkcije (`functions/`) traže Firebase tajne `OPENWEATHER_KEY`, `GEOAPIFY_KEY` i `BREVO_API_KEY`, i fajl `functions/.env` sa `ADMIN_EMAILS`.
4. Pokrenuti na uređaju sa Android 7.0 (API 24) ili novijim.

## Autor

**Đorđe Jovanović**, student Računarstva i informatike, Elektronski fakultet u Nišu
[github.com/zezus03](https://github.com/zezus03)

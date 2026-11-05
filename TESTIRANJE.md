# Zatvoreno testiranje (Google Play)

Početak: 4.11.2026. (verzija 2, 1.0). Uslov za objavu: 12+ testera prijavljenih 14 dana bez prekida.
Ovo se koristi i za odgovore na „Apply for production“ (šta su testeri prijavili i šta je popravljeno).

## Povratne informacije

| Datum | Ko | Šta je prijavljeno | Status |
|---|---|---|---|
| 4.11. | tester | Pri Google prijavi piše da se prijavljuje u „project-154890695958“ umesto FishSpot | Rešeno: javni naziv projekta podešen na FishSpot (Firebase / Google Cloud) |
| 4.11. | ja | Google prijava ne radi u verziji sa Play-a („Requests from this Android client application are blocked“) | Rešeno: Play SHA-1 dodat u API ključ i Firebase |
| 5.11. | 2 testera | Bedž „Fotograf“ se u spisku bedževa vidi samo do pola | Popravljeno u kodu (razmak na dnu spiska), ide u sledeću verziju |
| 5.11. | tester | Crno-zelena tema je previše tamna | Za razmatranje: svetlija pozadina ili svetla tema |
| 5.11. | tester | Slika ribe uz svaku vrstu u kalendaru lovostaja | Predlog: ide u verziju 1.3 (uz vrste ribe), upisano u PLAN.md |
| 5.11. | tester | U komentarima nema profilne slike autora, samo prvo slovo | Za verziju 3: učitati profilne slike autora komentara |
| 5.11. | tester | U chatu uvek piše „na mapi u blizini“, i kad sagovornik nije na mapi | Za verziju 3: „Aktivan sada“ samo kad je stvarno aktivan |
| 5.11. | ja | „Drimska pastrmka“ ne postoji kao zasebna vrsta | Sklonjena iz kalendara lovostaja (ide u verziju 3); u bazi vrsta (VrsteRiba) ukloniti ručno |
| 4.11. | ja | Opis vremena u prognozi je na ćirilici („Испрекидани облаци“), ostalo latinicom | Za sledeću verziju |

## Verzije u testiranju

| Verzija | Datum | Izmene |
|---|---|---|
| 2 (1.0) | 4.11.2026. | Prva verzija na Play-u (target API 36) |

"""
FishSpot - trening modela "da li je riba na slici" (fish_detector.tflite)

Pokreće se u Google Colab-u (Runtime -> Change runtime type -> GPU), ćelija po ćelija
ili ceo fajl odjednom. Rezultat se snima na Google Drive i preuzima.

USKLAĐENOST SA APLIKACIJOM (FishDetector.kt) - ne menjati bez izmene aplikacije:
  * ulaz: 256 x 256 RGB, float32, pikseli u opsegu 0-255 (model sam skalira)
  * izlaz: jedan broj (sigmoid) = verovatnoća klase "NoFish"
    (image_dataset_from_directory sortira foldere po abecedi: Fish = 0, NoFish = 1)

PODACI (Google Drive, MyDrive/):
  fishspot_dataset/      tvoje slike po vrstama (Carp, Pike, ...) -> Fish;
                         folder sa "nofish" u imenu -> NoFish
  fishspot_dodatno/      (opciono) Fish/ i NoFish/ - dodatne teške slike koje sam ubaciš
  fishspot_test/         Fish/ i NoFish/ - SLIKE SA TELEFONA, samo za proveru, nikad za trening
                         (preporuka: bar 50 + 50; ubaci i slike na kojima je stari model grešio)
  + Open Images V7 (preuzima se automatski): prave fotografije ribe i "teški" primeri bez ribe
"""

import hashlib
import json
import os
import random
import shutil
import subprocess
import sys
from datetime import datetime

# ════════════════════════════════════════════════════════════
# 0. PODEŠAVANJA
# ════════════════════════════════════════════════════════════
DRIVE = "/content/drive/MyDrive"
DRIVE_DATASET = f"{DRIVE}/fishspot_dataset"
DRIVE_DODATNO = f"{DRIVE}/fishspot_dodatno"
DRIVE_TEST = f"{DRIVE}/fishspot_test"
DRIVE_IZLAZ = f"{DRIVE}/fishspot_model"
DRIVE_CHECKPOINT = f"{DRIVE_IZLAZ}/trening"   # faza1.keras i najbolji.keras (za nastavak posle prekida)

RADNI = "/content/fish_trening"
TRENING = f"{RADNI}/train"          # Fish/ i NoFish/ (imena foldera određuju redosled klasa!)

VELICINA = 256                      # mora da odgovara VELICINA_ULAZA u FishDetector.kt
BATCH = 32
SEED = 42

BROJ_OI_RIBA = 3000                 # Open Images slike sa ribom
BROJ_OI_BEZ_RIBE = 6000             # Open Images slike bez ribe (teški primeri)

# Klase iz Open Images koje NE smeju da budu u "NoFish" (slike sa ribom ili morskim životinjama)
RIBLJE_OZNAKE = {"Fish", "Goldfish", "Shark", "Seafood", "Sushi", "Shellfish", "Oyster",
                 "Shrimp", "Lobster", "Crab", "Squid", "Jellyfish", "Starfish", "Seahorse", "Whale",
                 "Dolphin", "Marine mammal", "Marine invertebrates", "Sea lion", "Harbor seal"}

# Teški primeri za "NoFish": stvari koje model lako pomeša sa ulovom
KLASE_BEZ_RIBE = [
    "Person", "Human hand", "Man", "Woman", "Boy", "Girl",          # ljudi koji drže nešto
    "Box", "Bottle", "Handbag", "Book", "Mobile phone", "Toy",       # predmeti u rukama
    "Boat", "Canoe", "Kayak", "Lake", "River", "Beach", "Swimming pool",  # voda i čamci
    "Dog", "Cat", "Bird", "Duck", "Frog", "Turtle", "Lizard", "Snake",    # druge životinje
    "Food", "Vegetable", "Bread", "Tree", "Grass", "Rock", "Shoe",
]

random.seed(SEED)


def pip(*paketi):
    subprocess.run([sys.executable, "-m", "pip", "install", "-q", *paketi], check=True)


# ════════════════════════════════════════════════════════════
# 1. GOOGLE DRIVE I PAKETI
# ════════════════════════════════════════════════════════════
print("[1/8] Povezivanje Google Drive-a i instalacija paketa...")
from google.colab import drive, files  # noqa: E402

drive.mount("/content/drive", force_remount=True)
pip("fiftyone")

import numpy as np  # noqa: E402
import tensorflow as tf  # noqa: E402
from PIL import Image, ImageOps  # noqa: E402

print(f"   TensorFlow {tf.__version__}, GPU: {tf.config.list_physical_devices('GPU')}")

# Ako je Colab prekinuo trening, a runtime je i dalje isti, slike su već na disku: ne preuzimamo ih ponovo
POSTOJE_PODACI = all(os.path.isdir(f"{TRENING}/{k}") and len(os.listdir(f"{TRENING}/{k}")) > 200
                     for k in ("Fish", "NoFish"))
if not POSTOJE_PODACI:
    shutil.rmtree(RADNI, ignore_errors=True)
    for klasa in ("Fish", "NoFish"):
        os.makedirs(f"{TRENING}/{klasa}", exist_ok=True)

vidjeni_hashovi = set()
brojac = {"Fish": 0, "NoFish": 0, "duplikati": 0, "lose": 0}


def dodaj_sliku(izvor, klasa, prefiks):
    """Kopira sliku u trening folder: uspravna (EXIF), RGB, najviše 512 px, bez duplikata."""
    try:
        with open(izvor, "rb") as f:
            h = hashlib.md5(f.read()).hexdigest()
        if h in vidjeni_hashovi:
            brojac["duplikati"] += 1
            return
        with Image.open(izvor) as img:
            img = ImageOps.exif_transpose(img).convert("RGB")
            if min(img.size) < 100:
                brojac["lose"] += 1
                return
            img.thumbnail((512, 512))
            img.save(f"{TRENING}/{klasa}/{prefiks}_{h[:12]}.jpg", quality=92)
        vidjeni_hashovi.add(h)
        brojac[klasa] += 1
    except Exception:
        brojac["lose"] += 1


def je_slika(ime):
    return ime.lower().endswith((".jpg", ".jpeg", ".png", ".webp", ".bmp"))


if not POSTOJE_PODACI:
    # ════════════════════════════════════════════════════════════
    # 2. TVOJE SLIKE SA DRIVE-A
    # ════════════════════════════════════════════════════════════
    print("\n[2/8] Tvoje slike sa Drive-a...")
    for koren in (DRIVE_DATASET, DRIVE_DODATNO):
        if not os.path.exists(koren):
            print(f"   (nema foldera {koren}, preskačem)")
            continue
        for folder, _, fajlovi in os.walk(koren):
            ime = os.path.basename(folder).lower().replace("-", "").replace("_", "")
            klasa = "NoFish" if any(x in ime for x in ("nofish", "negative", "noriba", "nijeriba")) else "Fish"
            if folder == koren:
                continue
            for fajl in fajlovi:
                if je_slika(fajl):
                    dodaj_sliku(os.path.join(folder, fajl), klasa, "moje")
    print(f"   Fish: {brojac['Fish']}, NoFish: {brojac['NoFish']}")

    # ════════════════════════════════════════════════════════════
    # 3. OPEN IMAGES V7 (prave fotografije)
    # ════════════════════════════════════════════════════════════
    print("\n[3/8] Preuzimanje Open Images V7 (traje 10-20 min)...")
    import fiftyone.utils.openimages as fouo  # noqa: E402
    import fiftyone.zoo as foz  # noqa: E402

    # Zadržavamo samo klase koje zaista postoje u Open Images (nepostojeća klasa obara preuzimanje)
    dostupne_klase = set(fouo.get_classes(version="v7"))
    preskocene = [k for k in KLASE_BEZ_RIBE if k not in dostupne_klase]
    KLASE_BEZ_RIBE = [k for k in KLASE_BEZ_RIBE if k in dostupne_klase]
    if preskocene:
        print(f"   Preskačem klase koje ne postoje u Open Images: {preskocene}")
    assert "Fish" in dostupne_klase


    def oznake(uzorak):
        pozitivne = uzorak["positive_labels"]
        return {c.label for c in pozitivne.classifications} if pozitivne else set()


    ribe = foz.load_zoo_dataset(
        "open-images-v7", split="train", label_types=["classifications"],
        classes=["Fish"], max_samples=BROJ_OI_RIBA, shuffle=True, seed=SEED,
        dataset_name="oi_ribe",
    )
    for uzorak in ribe:
        if "Fish" in oznake(uzorak):
            dodaj_sliku(uzorak.filepath, "Fish", "oi")

    bez_ribe = foz.load_zoo_dataset(
        "open-images-v7", split="train", label_types=["classifications"],
        classes=KLASE_BEZ_RIBE, max_samples=BROJ_OI_BEZ_RIBE, shuffle=True, seed=SEED,
        dataset_name="oi_bez_ribe",
    )
    for uzorak in bez_ribe:
        if not (oznake(uzorak) & RIBLJE_OZNAKE):
            dodaj_sliku(uzorak.filepath, "NoFish", "oi")

    print(f"   Ukupno -> Fish: {brojac['Fish']}, NoFish: {brojac['NoFish']}, "
          f"duplikata: {brojac['duplikati']}, loših: {brojac['lose']}")
else:
    print("\n[2-3/8] Slike su već pripremljene na disku, preskačem preuzimanje.")

# ════════════════════════════════════════════════════════════
# 4. DATASET + AUGMENTACIJA
# ════════════════════════════════════════════════════════════
print("\n[4/8] Priprema dataset-a...")
zajednicko = dict(directory=TRENING, validation_split=0.15, seed=SEED,
                  image_size=(VELICINA, VELICINA), batch_size=BATCH, label_mode="binary")
train_ds = tf.keras.utils.image_dataset_from_directory(subset="training", **zajednicko)
val_ds = tf.keras.utils.image_dataset_from_directory(subset="validation", **zajednicko)
assert train_ds.class_names == ["Fish", "NoFish"], train_ds.class_names

# Nova varijacija svake epohe: augmentacija ide POSLE cache() (ranije je bila pre, pa se nije menjala)
augmentacija = tf.keras.Sequential([
    tf.keras.layers.RandomFlip("horizontal_and_vertical"),
    tf.keras.layers.RandomRotation(0.5, fill_mode="reflect"),        # do ±180° (uspravne i položene slike)
    tf.keras.layers.RandomZoom((-0.35, 0.15), fill_mode="reflect"),  # i krupni kadar samo dela ribe
    tf.keras.layers.RandomTranslation(0.1, 0.1, fill_mode="reflect"),
    tf.keras.layers.RandomContrast(0.3),
    tf.keras.layers.RandomBrightness(0.25, value_range=(0, 255)),
], name="augmentacija")

AUTOTUNE = tf.data.AUTOTUNE


def u_uint8(x, y):
    return tf.cast(tf.clip_by_value(tf.round(x), 0, 255), tf.uint8), y


def u_float(x, y):
    return tf.cast(x, tf.float32), y


# Keš u memoriji čuva slike kao uint8 (4x manje RAM-a nego float32: ~1.5 GB umesto ~6 GB).
# Shuffle bafer je mali (u serijama), da ne pravi još jednu kopiju celog skupa u memoriji.
train_ds = (train_ds.map(u_uint8, num_parallel_calls=AUTOTUNE).cache()
            .shuffle(48, seed=SEED)
            .map(lambda x, y: (augmentacija(tf.cast(x, tf.float32), training=True), y),
                 num_parallel_calls=AUTOTUNE)
            .prefetch(AUTOTUNE))
val_ds = (val_ds.map(u_uint8, num_parallel_calls=AUTOTUNE).cache()
          .map(u_float, num_parallel_calls=AUTOTUNE).prefetch(AUTOTUNE))

# Težine klasa, da veća klasa ne preovlada (broj slika čitamo iz foldera, radi i pri nastavku)
n_fish = len(os.listdir(f"{TRENING}/Fish"))
n_nofish = len(os.listdir(f"{TRENING}/NoFish"))
tezine_klasa = {0: (n_fish + n_nofish) / (2 * n_fish), 1: (n_fish + n_nofish) / (2 * n_nofish)}
print(f"   Težine klasa: {tezine_klasa}")

# ════════════════════════════════════════════════════════════
# 5. MODEL (EfficientNetV2B0, ima ugrađeno skaliranje piksela 0-255)
# ════════════════════════════════════════════════════════════
print("\n[5/8] Trening...")

# Modeli se čuvaju na Drive-u, da trening može da se nastavi ako Colab prekine sesiju
os.makedirs(DRIVE_CHECKPOINT, exist_ok=True)
faza1_putanja = f"{DRIVE_CHECKPOINT}/faza1.keras"
najbolji = f"{DRIVE_CHECKPOINT}/najbolji.keras"

metrike = [tf.keras.metrics.BinaryAccuracy(name="acc"), tf.keras.metrics.AUC(name="auc")]
gubitak = tf.keras.losses.BinaryCrossentropy(label_smoothing=0.05)


def napravi_callbacks():
    return [
        tf.keras.callbacks.ModelCheckpoint(najbolji, monitor="val_auc", mode="max", save_best_only=True),
        tf.keras.callbacks.EarlyStopping(monitor="val_auc", mode="max", patience=5, restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.3, patience=2, min_lr=1e-7),
    ]


if os.path.exists(faza1_putanja):
    print(f"   Faza 1 je već završena, nastavljam od {faza1_putanja}")
    model = tf.keras.models.load_model(faza1_putanja)
    osnova = next(sloj for sloj in model.layers if isinstance(sloj, tf.keras.Model))
else:
    osnova = tf.keras.applications.EfficientNetV2B0(
        input_shape=(VELICINA, VELICINA, 3), include_top=False, weights="imagenet", include_preprocessing=True)
    osnova.trainable = False

    ulaz = tf.keras.Input(shape=(VELICINA, VELICINA, 3))
    x = osnova(ulaz, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.3)(x)
    izlaz = tf.keras.layers.Dense(1, activation="sigmoid", name="nofish")(x)
    model = tf.keras.Model(ulaz, izlaz)

    # Faza 1: uči se samo nova "glava"
    # Argumenti po imenu: u Keras 3 je treći pozicioni argument loss_weights, a ne metrics
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3), loss=gubitak, metrics=metrike)
    model.fit(train_ds, validation_data=val_ds, epochs=10, class_weight=tezine_klasa, callbacks=napravi_callbacks())
    model.save(faza1_putanja)
    print(f"   Faza 1 sačuvana: {faza1_putanja}")

# Faza 2: fino podešavanje gornjih slojeva osnove (BatchNorm ostaje zamrznut)
osnova.trainable = True
for sloj in osnova.layers[:-60]:
    sloj.trainable = False
for sloj in osnova.layers:
    if isinstance(sloj, tf.keras.layers.BatchNormalization):
        sloj.trainable = False
model.compile(optimizer=tf.keras.optimizers.Adam(2e-5), loss=gubitak, metrics=metrike)
model.fit(train_ds, validation_data=val_ds, epochs=20, class_weight=tezine_klasa, callbacks=napravi_callbacks())

model = tf.keras.models.load_model(najbolji)

# ════════════════════════════════════════════════════════════
# 6. PROVERA NA TVOJIM SLIKAMA SA TELEFONA + IZBOR PRAGA
# ════════════════════════════════════════════════════════════
print("\n[6/8] Provera na test slikama (fishspot_test)...")


def ucitaj_za_model(putanja):
    """Ista priprema kao u aplikaciji: uspravno, RGB, 256x256, pikseli 0-255."""
    with Image.open(putanja) as img:
        img = ImageOps.exif_transpose(img).convert("RGB").resize((VELICINA, VELICINA), Image.BILINEAR)
        return np.asarray(img, dtype=np.float32)


test_putanje, test_oznake = [], []
for klasa, oznaka in (("Fish", 1), ("NoFish", 0)):
    folder = f"{DRIVE_TEST}/{klasa}"
    if os.path.exists(folder):
        for fajl in sorted(os.listdir(folder)):
            if je_slika(fajl):
                test_putanje.append(f"{folder}/{fajl}")
                test_oznake.append(oznaka)

prag = 0.55  # podrazumevano, isto kao trenutno u aplikaciji
izvestaj = {}
if test_putanje:
    slike = np.stack([ucitaj_za_model(p) for p in test_putanje])
    p_ribe = 1.0 - model.predict(slike, batch_size=BATCH, verbose=0).ravel()
    y = np.array(test_oznake)

    # Prag biramo tako da odbijemo što manje pravih ulova (recall), uz što manje lažnih (preciznost)
    najbolji_f1 = -1
    print(f"\n   {'prag':>5} {'preciznost':>11} {'odziv':>7} {'F1':>6}")
    for t in np.arange(0.30, 0.91, 0.05):
        pred = p_ribe > t
        tp, fp, fn = int(((pred == 1) & (y == 1)).sum()), int(((pred == 1) & (y == 0)).sum()), int(((pred == 0) & (y == 1)).sum())
        prec = tp / (tp + fp) if tp + fp else 0.0
        odz = tp / (tp + fn) if tp + fn else 0.0
        f1 = 2 * prec * odz / (prec + odz) if prec + odz else 0.0
        print(f"   {t:5.2f} {prec:11.1%} {odz:7.1%} {f1:6.3f}")
        if f1 > najbolji_f1:
            najbolji_f1, prag = f1, float(round(t, 2))

    pred = p_ribe > prag
    tacnost = float((pred == y).mean())
    print(f"\n   Predloženi prag: {prag}  ->  tačnost na tvojim slikama: {tacnost:.1%}")
    print("   Pogrešno klasifikovane slike (dobar materijal za fishspot_dodatno):")
    for putanja, ocekivano, p in zip(test_putanje, y, p_ribe):
        if (p > prag) != bool(ocekivano):
            print(f"     {'RIBA' if ocekivano else 'NIJE RIBA':>9} | model: {p:.2f} | {os.path.basename(putanja)}")
    izvestaj = {"test_slika": len(y), "tacnost": tacnost, "f1": float(najbolji_f1)}
else:
    print("   ⚠️  Nema foldera fishspot_test - prava tačnost nije izmerena! Prag ostaje 0.55.")

# ════════════════════════════════════════════════════════════
# 7. KONVERZIJA U TFLITE (float16) + PROVERA DA DAJE ISTO KAO KERAS
# ════════════════════════════════════════════════════════════
print("\n[7/8] Konverzija u TFLite...")
# Preko SavedModel-a: direktna konverzija iz Keras 3 modela zna da zakaže
saved_model_dir = f"{RADNI}/saved_model"
model.export(saved_model_dir)
konverter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
konverter.optimizations = [tf.lite.Optimize.DEFAULT]
konverter.target_spec.supported_types = [tf.float16]   # manji model, bez gubitka tačnosti
tflite_model = konverter.convert()

interpreter = tf.lite.Interpreter(model_content=tflite_model)
interpreter.allocate_tensors()
ul, iz = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
assert list(ul["shape"]) == [1, VELICINA, VELICINA, 3], ul["shape"]
assert list(iz["shape"]) == [1, 1], iz["shape"]

provera = test_putanje[:20] or [os.path.join(f"{TRENING}/Fish", f) for f in os.listdir(f"{TRENING}/Fish")[:20]]
razlike = []
for putanja in provera:
    slika = ucitaj_za_model(putanja)[None, ...]
    interpreter.set_tensor(ul["index"], slika)
    interpreter.invoke()
    razlike.append(abs(float(interpreter.get_tensor(iz["index"])[0, 0]) - float(model.predict(slika, verbose=0)[0, 0])))
print(f"   Najveća razlika TFLite/Keras: {max(razlike):.4f} (treba da bude ispod 0.02)")

# ════════════════════════════════════════════════════════════
# 8. ČUVANJE NA DRIVE I PREUZIMANJE
# ════════════════════════════════════════════════════════════
print("\n[8/8] Čuvanje...")
os.makedirs(DRIVE_IZLAZ, exist_ok=True)
oznaka_verzije = datetime.now().strftime("%Y%m%d_%H%M")
putanja_modela = f"/content/fish_detector.tflite"
with open(putanja_modela, "wb") as f:
    f.write(tflite_model)
shutil.copy(putanja_modela, f"{DRIVE_IZLAZ}/fish_detector_{oznaka_verzije}.tflite")

info = {
    "verzija": oznaka_verzije,
    "osnova": "EfficientNetV2B0",
    "ulaz": [VELICINA, VELICINA, 3],
    "izlaz": "sigmoid = verovatnoća NoFish (Fish = 0, NoFish = 1)",
    "prag_ribe": prag,
    "slika_za_trening": {"Fish": n_fish, "NoFish": n_nofish},
    "test": izvestaj,
}
with open(f"{DRIVE_IZLAZ}/fish_detector_{oznaka_verzije}.json", "w") as f:
    json.dump(info, f, ensure_ascii=False, indent=2)

print(json.dumps(info, ensure_ascii=False, indent=2))
print(f"\n✅ Gotovo. Model: {DRIVE_IZLAZ}/fish_detector_{oznaka_verzije}.tflite")
print(f"   U FishDetector.kt postavi PRAG_RIBE = {prag}f")
files.download(putanja_modela)

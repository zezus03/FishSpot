from google.colab import drive, files
import os, shutil, tensorflow as tf
from PIL import Image

print("=" * 60)
print("FISH DETECTOR v1 - TRENING")
print("=" * 60)

# Faza 1: Povezivanje Google Drive-a
print("\n[1/5] Montiranje Google Drive-a...")
drive.mount('/content/drive', force_remount=True)

WORK_DIR = '/content/fish_training'
FISH_FOLDER = f'{WORK_DIR}/Fish'
NO_FISH_FOLDER = f'{WORK_DIR}/NoFish'

os.makedirs(FISH_FOLDER, exist_ok=True)
os.makedirs(NO_FISH_FOLDER, exist_ok=True)

print(f"Radni folder: {WORK_DIR}")

# Faza 2: Priprema slika
print("\n[2/5] Učitavanje slika sa Google Drive-a...")
DRIVE_DATASET_PATH = '/content/drive/MyDrive/fishspot_dataset'

fish_count = 0
nofish_count = 0

if os.path.exists(DRIVE_DATASET_PATH):
    for root, dirs, files_list in os.walk(DRIVE_DATASET_PATH):
        for file in files_list:
            if not file.lower().endswith(('.jpg', '.jpeg', '.png', '.gif', '.webp')):
                continue

            src = os.path.join(root, file)
            folder_name = os.path.basename(root).lower()

            if any(x in folder_name for x in ['nofish', 'negative', 'no_fish', 'no-fish']):
                dst = os.path.join(NO_FISH_FOLDER, f"{nofish_count:04d}_{file}")
                nofish_count += 1
            else:
                dst = os.path.join(FISH_FOLDER, f"{fish_count:04d}_{file}")
                fish_count += 1

            try:
                shutil.copy2(src, dst)
            except Exception as e:
                print(f"   Problem sa {file}: {str(e)[:30]}")

print(f"Učitane slike - Fish: {fish_count}, NoFish: {nofish_count}")

if fish_count < 30 or nofish_count < 30:
    print("UPOZORENJE: manje od 30 slika po klasi, dodaj još slika na Drive")

# Faza 3: Validacija i dataset
print("\n[3/5] Validacija slika...")


def validate_image(path):
    try:
        img = Image.open(path)
        img.verify()
        return img.size[0] >= 100 and img.size[1] >= 100
    except Exception:
        return False


for folder in [FISH_FOLDER, NO_FISH_FOLDER]:
    for f in os.listdir(folder):
        path = os.path.join(folder, f)
        if not validate_image(path):
            os.remove(path)

fish_valid = len(os.listdir(FISH_FOLDER))
nofish_valid = len(os.listdir(NO_FISH_FOLDER))
print(f"Validne slike - Fish: {fish_valid}, NoFish: {nofish_valid}")

BATCH_SIZE = 32
IMG_SIZE = (256, 256)

train_ds = tf.keras.utils.image_dataset_from_directory(
    WORK_DIR,
    validation_split=0.2,
    subset="training",
    seed=42,
    image_size=IMG_SIZE,
    batch_size=BATCH_SIZE,
    label_mode='binary'
)

val_ds = tf.keras.utils.image_dataset_from_directory(
    WORK_DIR,
    validation_split=0.2,
    subset="validation",
    seed=42,
    image_size=IMG_SIZE,
    batch_size=BATCH_SIZE,
    label_mode='binary'
)

data_aug = tf.keras.Sequential([
    tf.keras.layers.RandomFlip("horizontal"),
    tf.keras.layers.RandomRotation(0.15),
    tf.keras.layers.RandomZoom(0.15),
    tf.keras.layers.RandomBrightness(0.2),
])

train_ds = train_ds.map(lambda x, y: (data_aug(x, training=True), y)).cache().prefetch(tf.data.AUTOTUNE)
val_ds = val_ds.cache().prefetch(tf.data.AUTOTUNE)

print(f"Dataset - ulaz: {IMG_SIZE}, batch: {BATCH_SIZE}")

# Faza 4: Trening modela (EfficientNetB1)
print("\n[4/5] Treniranje modela...")

base = tf.keras.applications.EfficientNetB1(
    input_shape=(256, 256, 3),
    include_top=False,
    weights='imagenet'
)
base.trainable = False

inputs = tf.keras.Input(shape=(256, 256, 3))
x = base(inputs, training=False)
x = tf.keras.layers.GlobalAveragePooling2D()(x)
x = tf.keras.layers.BatchNormalization()(x)
x = tf.keras.layers.Dense(256, activation='relu')(x)
x = tf.keras.layers.Dropout(0.4)(x)
x = tf.keras.layers.Dense(128, activation='relu')(x)
x = tf.keras.layers.Dropout(0.3)(x)
outputs = tf.keras.layers.Dense(1, activation='sigmoid')(x)

model = tf.keras.Model(inputs, outputs)

callbacks = [
    tf.keras.callbacks.ModelCheckpoint(
        '/content/drive/MyDrive/best_fish_detector.keras',
        monitor='val_loss',
        save_best_only=True,
        verbose=1
    ),
    tf.keras.callbacks.EarlyStopping(
        monitor='val_loss',
        patience=6,
        restore_best_weights=True,
        verbose=1
    ),
]

print("\n   Feature extraction")
model.compile(
    optimizer=tf.keras.optimizers.Adam(1e-3),
    loss='binary_crossentropy',
    metrics=['accuracy']
)
model.fit(train_ds, validation_data=val_ds, epochs=15, callbacks=callbacks, verbose=1)

print("\n   Fine-tuning")
base.trainable = True
for layer in base.layers[:-30]:
    layer.trainable = False

model.compile(
    optimizer=tf.keras.optimizers.Adam(1e-5),
    loss='binary_crossentropy',
    metrics=['accuracy']
)
model.fit(train_ds, validation_data=val_ds, epochs=12, callbacks=callbacks, verbose=1)

print("Trening završen")

# Faza 5: Konverzija u TFLite i preuzimanje
print("\n[5/5] Konverzija u TensorFlow Lite...")

best_model = tf.keras.models.load_model('/content/drive/MyDrive/best_fish_detector.keras')

converter = tf.lite.TFLiteConverter.from_keras_model(best_model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS
]

tflite_model = converter.convert()

tflite_path_drive = '/content/drive/MyDrive/fish_detector.tflite'
tflite_path_local = '/content/fish_detector.tflite'

with open(tflite_path_drive, 'wb') as f:
    f.write(tflite_model)
with open(tflite_path_local, 'wb') as f:
    f.write(tflite_model)

size_mb = os.path.getsize(tflite_path_local) / (1024 * 1024)
print(f"TFLite model sačuvan: {tflite_path_drive} ({size_mb:.2f} MB)")

files.download(tflite_path_local)
print("Gotovo: stavi fish_detector.tflite u app/src/main/assets/")

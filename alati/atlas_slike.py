# Priprema slika za atlas riba: iz foldera sa ilustracijama (Desktop/FishSpot_ilustracije)
# iseca samo ribu (bez natpisa i okvira table) i cuva kao mali WebP u assets/atlas.
import sys, os, glob
from PIL import Image, ImageFilter, ImageChops

IZVOR = sys.argv[1]
CILJ = sys.argv[2]
os.makedirs(CILJ, exist_ok=True)
SIRINA = 480

# Koja slika se koristi kad postoji vise (podrazumevano prva: <id>_1)
IZBOR = {
    "pastruga": "pastruga_3", "grgec": "grgec_bandar_1", "beli_tolstolobik": "beli_tolstolobik_1",
    "recni_rak": "recni_rak_3", "cverglan": "cverglan_2", "sim": "sim_2",
}

def nadji(id_):
    ime = IZBOR.get(id_, id_ + "_1")
    for ext in (".webp", ".jpg", ".png"):
        p = os.path.join(IZVOR, ime + ext)
        if os.path.exists(p): return p
    return None

def najveca_celina(maska):
    # Okvir najvece povezane celine (riba), da natpisi i druge sitnice ne udju u isecak
    w, h = maska.size
    px = maska.load()
    videno = bytearray(w * h)
    najbolji, najveci = None, 0
    for y in range(h):
        for x in range(w):
            if px[x, y] == 0 or videno[y * w + x]: continue
            stek = [(x, y)]; videno[y * w + x] = 1
            n = 0; x0 = x1 = x; y0 = y1 = y
            while stek:
                cx, cy = stek.pop(); n += 1
                x0 = min(x0, cx); x1 = max(x1, cx); y0 = min(y0, cy); y1 = max(y1, cy)
                for nx, ny in ((cx+1, cy), (cx-1, cy), (cx, cy+1), (cx, cy-1)):
                    if 0 <= nx < w and 0 <= ny < h and px[nx, ny] and not videno[ny * w + nx]:
                        videno[ny * w + nx] = 1; stek.append((nx, ny))
            if n > najveci: najveci, najbolji = n, (x0, y0, x1 + 1, y1 + 1)
    return najbolji

def iseci(slika):
    rgb = slika.convert("RGB")
    w, h = rgb.size
    # pozadina = srednja boja ivice slike
    ivica = [rgb.getpixel((x, y)) for x in range(0, w, max(1, w // 50)) for y in (2, h - 3)]
    pozadina = tuple(sorted(c[i] for c in ivica)[len(ivica) // 2] for i in range(3))
    razlika = ImageChops.difference(rgb, Image.new("RGB", rgb.size, pozadina)).convert("L")
    maska = razlika.point(lambda v: 255 if v > 38 else 0)
    # tanke linije (okvir, natpisi) nestaju posle "erozije", riba ostaje
    mala = maska.resize((max(1, w // 4), max(1, h // 4)))
    mala = mala.filter(ImageFilter.MinFilter(5)).filter(ImageFilter.MaxFilter(7))
    okvir = najveca_celina(mala)
    if not okvir: return rgb
    x0, y0, x1, y1 = [v * 4 for v in okvir]
    pad = int(0.04 * max(x1 - x0, y1 - y0))
    return rgb.crop((max(0, x0 - pad), max(0, y0 - pad), min(w, x1 + pad), min(h, y1 + pad)))

for md in sorted(glob.glob(os.path.join(sys.argv[3], "*.md"))):
    id_ = os.path.splitext(os.path.basename(md))[0]
    p = nadji(id_)
    if not p:
        print("NEMA SLIKE:", id_); continue
    s = iseci(Image.open(p))
    s.thumbnail((SIRINA, SIRINA))
    out = os.path.join(CILJ, id_ + ".webp")
    s.save(out, "WEBP", quality=78)
    print(f"{id_}: {s.size} {os.path.getsize(out)//1024} KB")

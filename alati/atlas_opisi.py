# Pretvara opise iz sadrzaj/atlas_riba/*.md u app/src/main/assets/atlas/atlas.json,
# koji aplikacija čita (radi i bez interneta). Pokreni posle svake izmene opisa:
#   python alati/atlas_opisi.py
import glob, json, os, re

KOREN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IZVOR = os.path.join(KOREN, "sadrzaj", "atlas_riba")
CILJ = os.path.join(KOREN, "app", "src", "main", "assets", "atlas", "atlas.json")

vrste = []
for putanja in sorted(glob.glob(os.path.join(IZVOR, "*.md"))):
    id_ = os.path.splitext(os.path.basename(putanja))[0]
    redovi = open(putanja, encoding="utf-8").read().replace("\r\n", "\n").split("\n")
    naslov = re.match(r"#\s+(.+?)\s+\((.+)\)\s*$", redovi[0])
    if not naslov:
        raise SystemExit(f"{id_}: prvi red mora biti '# Naziv (*Latinski*)'")
    zaglavlje = next((r for r in redovi[1:] if r.strip()), "")
    if "TRAJNA ZABRANA" in zaglavlje:
        status = "zabrana"
    elif "nesena" in zaglavlje:
        status = "unesena"
    else:
        status = "domaca"
    porodica = re.match(r"\*\*(Porodica[^*]*)\*\*", zaglavlje)
    drugo_ime = re.search(r"Drugo ime:\s*([^·]+)", zaglavlje)

    odeljci, uvod, trenutni = [], [], None
    for r in redovi[redovi.index(zaglavlje) + 1:]:
        if r.startswith("## "):
            trenutni = {"naslov": r[3:].strip(), "tekst": []}
            odeljci.append(trenutni)
        elif trenutni is not None:
            trenutni["tekst"].append(r)
        elif r.strip():
            uvod.append(r)
    for o in odeljci:
        o["tekst"] = "\n".join(o["tekst"]).strip()

    vrste.append({
        "id": id_,
        "naziv": naslov.group(1).strip(),
        "latinski": naslov.group(2).replace("*", "").strip(),
        "porodica": porodica.group(1).strip() if porodica else "",
        "drugoIme": drugo_ime.group(1).strip() if drugo_ime else "",
        "status": status,
        "uvod": "\n".join(uvod).strip(),
        "odeljci": odeljci,
    })

os.makedirs(os.path.dirname(CILJ), exist_ok=True)
with open(CILJ, "w", encoding="utf-8") as f:
    json.dump(vrste, f, ensure_ascii=False, separators=(",", ":"))
print(f"{len(vrste)} vrsta, {os.path.getsize(CILJ) // 1024} KB -> {CILJ}")
for s in ("domaca", "unesena", "zabrana"):
    print(s, sum(v["status"] == s for v in vrste))

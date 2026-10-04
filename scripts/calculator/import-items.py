"""Read item names from the same verified champout export as the dex."""
import csv
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[2]
dex = json.loads((root / 'app/src/main/assets/dex.json').read_text(encoding='utf8'))
source = root.parent / 'champout/output' / dex['version']['champout']
manifest = json.loads((source / 'manifest.json').read_text(encoding='utf8'))
item_file = source / 'item.csv'
digest = hashlib.sha256(item_file.read_bytes()).hexdigest()
assert digest == manifest['files']['item.csv'], 'item.csv source hash mismatch'
with item_file.open(encoding='utf8', newline='') as f:
    names = {r['name_en'].replace('’', "'"): r['name_zh'] for r in csv.DictReader(f)}
print(json.dumps({'names': names, 'sha256': digest}, ensure_ascii=True))

// Export the upstream Champions data the Kotlin engine needs into catalog.json. Mechanics themselves are ported
// to Kotlin; this only maps dex forms/moves/abilities/items to upstream records and copies their mechanical fields.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const {load, root} = require('./upstream.cjs');
const out = path.join(root, 'app/src/main/assets/calculator');
const upstream = load();
const calc = upstream.calc, gen = calc.Generations.get(0), gen9 = calc.Generations.get(9);
const items = upstream.require('items');
const sha = f => crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex');
const dexPath = path.join(root, 'app/src/main/assets/dex.json');
const dex = JSON.parse(fs.readFileSync(dexPath));

// Chinese item names come from the champout export that produced the dex. Without that sibling export, the labels of a
// previous import are reused only if it was generated from this exact dex.json.
function itemNames() {
  const source = path.join(root, '..', 'champout/output', dex.version.champout);
  if (fs.existsSync(source)) {
    return JSON.parse(require('child_process').execFileSync('python', [path.join(__dirname, 'import-items.py')], {encoding: 'utf8'}));
  }
  const previous = JSON.parse(fs.readFileSync(path.join(out, 'provenance.json')));
  if (previous.dexSha256 !== sha(dexPath)) throw new Error(`缺少 ${source}，且上次导入的图鉴版本不同，无法复用道具中文名`);
  console.warn('champout export not found; reusing item labels verified by the previous import of this dex');
  const catalog = JSON.parse(fs.readFileSync(path.join(out, 'catalog.json')));
  return {names: Object.fromEntries(catalog.items.map(i => [i.name, i.label])), sha256: previous.itemSourceSha256};
}
const itemSource = itemNames();

console.log('Champions engine:', Array.from(gen.species).length, 'species,', Array.from(gen.moves).length, 'moves');
const aliasesPath = path.join(__dirname, 'form-aliases.json');
const aliases = fs.existsSync(aliasesPath) ? JSON.parse(fs.readFileSync(aliasesPath)) : {};
function speciesName(p) {
  const f = p.formEnglish;
  let name = aliases[p.id] || p.english;
  if (!aliases[p.id] && f) {
    if (f.startsWith('Mega ')) name = p.english + '-Mega' + f.slice(('Mega ' + p.english).length).replace(/ /g, '-');
    else if (f === 'Alolan Form') name += '-Alola';
    else if (f === 'Galarian Form') name += '-Galar';
    else if (f === 'Hisuian Form') name += '-Hisui';
    else return null;
  }
  return gen.species.get(calc.toID(name))?.name || null;
}
const mapping = Object.fromEntries(dex.pokemon.map(p => [p.id, speciesName(p)]));
const missing = dex.pokemon.filter(p => !mapping[p.id]);
const available = dex.moves.filter(m => m.available);
// "Pound" is open in the dex but absent from the upstream Champions table; it alone uses the gen 9 record.
const moveRecord = m => gen.moves.get(calc.toID(m.english)) || (m.id === 1 ? gen9.moves.get('pound') : null);

// Only the fields the damage formula reads. Type, category, priority, contact and fixed power come from the dex.
function mechanics(data) {
  const flags = Object.keys(data.flags || {}).filter(f => f !== 'contact' && data.flags[f]).sort();
  const record = {basePower: data.basePower || 0};
  if (flags.length) record.flags = flags;
  if (data.multihit) record.multihit = data.multihit;
  for (const key of ['multiaccuracy', 'hasCrashDamage', 'willCrit', 'ignoreDefensive', 'breaksProtect']) if (data[key]) record[key] = true;
  if (data.secondaries) record.secondaries = true;
  if (data.recoil) record.recoil = true;
  if (data.target) record.target = data.target;
  if (data.overrideDefensiveStat) record.overrideDefensiveStat = data.overrideDefensiveStat;
  const self = Object.fromEntries(['atk', 'spa'].filter(s => data.self?.boosts?.[s]).map(s => [s, data.self.boosts[s]]));
  if (Object.keys(self).length) record.selfBoosts = self;
  return record;
}

function itemRecord(item) {
  const record = {name: item.name, label: itemSource.names[item.name] || item.name};
  const boost = items.getItemBoostType(item.name), resist = items.getBerryResistType(item.name), fling = items.getFlingPower(item.name, 0);
  if (boost) record.boostType = boost;
  if (resist) record.resistType = resist;
  if (fling) record.fling = fling;
  if (item.megaStone) record.megaStone = item.megaStone;
  return record;
}

const typeNames = Array.from(gen.types).map(t => t.name);
fs.mkdirSync(out, {recursive: true});
fs.writeFileSync(path.join(out, 'catalog.json'), JSON.stringify({
  species: mapping,
  moves: Object.fromEntries(available.map(m => [m.id, gen.moves.get(calc.toID(m.english))?.name || (m.id === 1 ? 'Pound' : null)])),
  moveData: Object.fromEntries(available.filter(m => moveRecord(m)).map(m => [m.id, mechanics(moveRecord(m))])),
  moveHits: Object.fromEntries(available.map(m => {
    const data = gen.moves.get(calc.toID(m.english));
    const hits = data?.multihit;
    return [m.id, Array.isArray(hits) ? hits : (typeof hits === 'number' ? [data.multiaccuracy ? 1 : hits, hits] : [1, 1])];
  })),
  abilities: Object.fromEntries(dex.abilities.map(a => [a.id, gen.abilities.get(calc.toID(a.english))?.name || null])),
  items: Array.from(gen.items).map(itemRecord),
  natures: Array.from(gen.natures).map(n => ({name: n.name, plus: n.plus, minus: n.minus})),
  types: Object.fromEntries(Array.from(gen.types).map(t =>
    [t.name, Object.fromEntries(typeNames.filter(d => t.effectiveness[d] !== undefined).map(d => [d, t.effectiveness[d]]))])),
}, null, 2) + '\n');
for (const stale of ['engine.js', 'adapter.js', 'LICENSE-Rhino.txt']) fs.rmSync(path.join(out, stale), {force: true});

fs.writeFileSync(path.join(out, 'provenance.json'), JSON.stringify({
  repository: 'https://github.com/smogon/damage-calc', commit: 'eb9af6d94e7ee0ce034d2d6f2d44d4b2d66ff981', generation: 0,
  dexSha256: sha(dexPath),
  itemSourceSha256: itemSource.sha256,
  catalogSha256: sha(path.join(out, 'catalog.json')),
  formAliasesSha256: sha(aliasesPath),
  sources: Object.fromEntries(upstream.sources.map(id => [id, sha(path.join(upstream.src, id))])),
  unmappedSpecies: missing.map(p => ({id: p.id, name: p.name, form: p.form, english: p.english, formEnglish: p.formEnglish})),
}, null, 2) + '\n');
console.log('Unmapped forms:', JSON.stringify(missing.map(p => [p.id, p.english, p.formEnglish])));

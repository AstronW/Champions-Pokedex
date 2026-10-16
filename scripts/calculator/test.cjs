// Reference cases for the Kotlin engine: the upstream source plus the original JavaScript boundary (adapter.js) compute
// the expected results; CalculatorEngineTest.kt replays every case and compares field by field.
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const {load,root}=require('./upstream.cjs');
const assets=path.join(root,'app/src/main/assets');
const read=p=>fs.readFileSync(path.join(assets,p),'utf8');
const calc=load().calc,gen=calc.Generations.get(0);
const context=vm.createContext({calc});
vm.runInContext(fs.readFileSync(path.join(__dirname,'adapter.js'),'utf8'),context);
context.dexCalc.init(read('dex.json'),read('calculator/catalog.json'));
const dex=JSON.parse(read('dex.json')), catalog=JSON.parse(read('calculator/catalog.json'));
const stats=['hp','atk','def','spa','spd','spe'];
const fixedGender=p=>({1:'M',2:'N',3:'F'}[p.sex]);
function side(name) {
  const p=dex.pokemon.find(p=>catalog.species[p.id]===name); assert(p,name);
  return {id:p.id,ability:p.abilities[0],nature:'Serious',item:'',sp:[0,0,0,0,0,0],boosts:[0,0,0,0,0,0],hp:100,status:'',gender:fixedGender(p)||'M',abilityOn:false,alliesFainted:0};
}
function request(attacker='Charizard',defender='Venusaur',move='Flamethrower') {
  return {attacker:side(attacker),defender:side(defender),move:dex.moves.find(m=>m.english===move).id,critical:false,hits:0,field:{gameType:'Singles'}};
}
function run(q) {const r=JSON.parse(context.dexCalc.calculate(JSON.stringify(q)));delete r.applied;return r;}
const fixtures=[];
function check(name,q,verify) {
  const r=run(q); assert(!r.error,name+': '+r.error);if(verify) verify(r);
  fixtures.push({name,request:q,expected:r});return r;
}
const normal=check('STAB + super effective',request());
for(const weather of ['Sun','Rain','Sand','Snow']) {
  const q=request();q.field.weather=weather;check(weather,q);
}
const rain=request();rain.field.weather='Rain';assert(run(rain).max<normal.max);
for(const terrain of ['Electric','Grassy','Misty','Psychic']) {
  const q=request('Raichu','Blastoise','Thunderbolt');q.field.terrain=terrain;check(terrain,q);
}
for(const [a,d,m] of [
  ['Gengar','Snorlax','Shadow Ball'],['Kangaskhan-Mega','Blastoise','Body Slam'],
  ['Dragonite','Blastoise','Scale Shot'],['Blastoise','Snorlax','Seismic Toss'],
  ['Blastoise','Snorlax','Water Spout'],['Snorlax','Gengar','Pound'],
  ['Blastoise','Gyarados','Grass Knot'],['Tyranitar','Blastoise','Low Kick'],
  ['Aegislash-Shield','Blastoise','Body Press'],['Gourgeist-Super','Charizard','Foul Play'],
  ['Meganium-Mega','Tyranitar','Giga Drain'],['Morpeko-Hangry','Blastoise','Aura Wheel'],
  ['Kangaskhan-Mega','Blastoise','Seismic Toss'],['Kangaskhan-Mega','Gengar','Struggle'],
]) check(a+' / '+m,request(a,d,m));
for(const flag of ['isReflect','isLightScreen','isAuroraVeil','isProtected','isFriendGuard']) {
  const q=request();q.field.defenderSide={[flag]:true};check(flag,q);
}
for(const item of ['Life Orb','Occa Berry','Leftovers','Air Balloon']) {
  const q=request();q[item==='Life Orb'?'attacker':'defender'].item=item;check(item,q);
}
const burn=request('Snorlax','Blastoise','Body Slam');burn.attacker.status='brn';check('Burn',burn);
const critical=request();critical.critical=true;critical.attacker.boosts[3]=-3;critical.defender.boosts[4]=4;check('Critical bypass',critical);
const spread=request('Charizard','Venusaur','Heat Wave');spread.field.gameType='Doubles';check('Doubles spread',spread);
const points=request();points.attacker.sp=[0,0,0,32,0,32];points.attacker.nature='Modest';check('Champions points',points,r=>assert.equal(r.attackerStats.spa,Math.floor((109+32+20)*1.1)));
const multi=request('Dragonite','Blastoise','Scale Shot');multi.hits=5;multi.defender.hp=40;check('Independent multihit',multi,r=>assert.equal(r.rolls.length,5));
// Upstream caps speed at 999 for generation numbers <= 2, which includes Champions (0); Electro Ball exposes it.
const capped=request('Jolteon','Aerodactyl','Electro Ball');capped.attacker.sp[5]=32;capped.attacker.boosts[5]=6;capped.attacker.item='Choice Scarf';
capped.field.attackerSide={isTailwind:true};capped.defender.sp[5]=32;capped.defender.boosts[5]=2;check('Speed cap 999',capped);
const named=fixtures.length;

// Exhaustive adapter coverage: every form and every available damaging move.
const attacks=dex.moves.filter(m=>m.available&&m.category!==2);
for(const p of dex.pokemon) {const q=request();q.attacker.id=p.id;q.attacker.ability=p.abilities[0];q.attacker.gender=fixedGender(p)||'M';const r=run(q);assert(!r.error,p.id+': '+r.error);assert.equal(r.attackerStats.hp,p.stats[0]+75);}
for(const m of attacks) {const q=request();q.move=m.id;assert(!run(q).error,m.name+': '+run(q).error);}
for(const p of dex.pokemon) {
  assert(catalog.species[p.id],p.id);
  for(const a of p.abilities) assert(catalog.abilities[a],String(a));
}

// Seeded combinations: three rounds over all forms so every form meets each of its abilities on both sides, every
// open attack appears several times, and items, conditions and field states are mixed in.
let seed=20261016;
function random() {let t=seed+=0x6d2b79f5;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return ((t^t>>>14)>>>0)/4294967296;} // mulberry32
const pick=a=>a[Math.floor(random()*a.length)];
const chance=p=>random()<p;
const natures=catalog.natures.map(n=>n.name), items=catalog.items.map(i=>i.name), forms=dex.pokemon;
function randomSide(p,abilityIndex,item) {
  return {id:p.id,ability:p.abilities[abilityIndex%p.abilities.length],nature:pick(natures),item:chance(.75)?item:'',
    sp:stats.map(()=>chance(.5)?Math.floor(random()*33):pick([0,2,32])),
    boosts:stats.map((_,i)=>i&&chance(.2)?Math.floor(random()*13)-6:0),
    hp:chance(.35)?1+Math.floor(random()*100):100,status:chance(.25)?pick(['brn','par','psn','tox','slp','frz']):'',
    gender:fixedGender(p)||pick(['M','F']),abilityOn:chance(.35),alliesFainted:chance(.2)?Math.floor(random()*6):0};
}
const sideFlags=['isReflect','isLightScreen','isAuroraVeil','isProtected','isTailwind','isHelpingHand','isFriendGuard','isBattery',
  'isPowerSpot','isSteelySpirit','isCharge','isFlowerGift','isSR','isPowerTrick'];
function randomField() {
  const field={gameType:chance(.3)?'Doubles':'Singles',weather:pick(['','','Sun','Rain','Sand','Snow']),terrain:pick(['','','Electric','Grassy','Misty','Psychic'])};
  for(const key of ['isGravity','isWonderRoom','isMagicRoom','isFairyAura','isDarkAura']) if(chance(.08)) field[key]=true;
  for(const key of ['attackerSide','defenderSide']) {
    const s={};for(const flag of sideFlags) if(chance(.07)) s[flag]=true;
    if(chance(.05)) s.spikes=1+Math.floor(random()*3);
    if(key==='defenderSide'&&chance(.05)) s.isSwitching='out';
    field[key]=s;
  }
  return field;
}
let failures=0;
for(let i=0;i<forms.length*3;i++) {
  const round=Math.floor(i/forms.length), a=forms[i%forms.length], d=forms[(i*131+17)%forms.length], m=attacks[i%attacks.length];
  const range=catalog.moveHits[m.id];
  const q={attacker:randomSide(a,round,items[i%items.length]),defender:randomSide(d,round+1,items[(i*7+11)%items.length]),move:m.id,
    critical:chance(.15),hits:range[1]>1&&chance(.5)?range[0]+Math.floor(random()*(range[1]-range[0]+1)):0,field:randomField()};
  const r=run(q);
  if(r.error) {failures++;assert.equal(r.error,'此组合没有有效的计算结果',`case ${i}`);}
  fixtures.push({name:`combination ${i}`,request:q,expected:r});
}

// Input validation messages.
function invalid(name,edit) {const q=request();edit(q);const r=run(q);assert(r.error,name);fixtures.push({name,request:q,expected:r});}
invalid('points above 32',q=>q.attacker.sp[0]=33);
invalid('empty points',q=>q.attacker.sp[0]='');
invalid('status move',q=>q.move=dex.moves.find(m=>m.available&&m.category===2).id);
invalid('closed move',q=>q.move=dex.moves.find(m=>!m.available).id);
invalid('foreign ability',q=>q.attacker.ability=dex.pokemon.find(p=>!p.abilities.includes(q.attacker.ability)).abilities[0]);
invalid('unknown item',q=>q.defender.item='Master Ball');
invalid('unknown nature',q=>q.attacker.nature='Brave2');
invalid('fixed gender',q=>{const p=dex.pokemon.find(p=>p.sex===2);q.defender=side(catalog.species[p.id]);q.defender.gender='M';});
invalid('boost below -6',q=>q.defender.boosts[2]=-7);
invalid('hp zero',q=>q.defender.hp=0);
invalid('allies fainted',q=>q.attacker.alliesFainted=6);
invalid('hits outside range',q=>{q.attacker=side('Dragonite');q.move=dex.moves.find(m=>m.english==='Scale Shot').id;q.hits=6;});

// Independently invoke the upstream public API with equal source stats and compare exact rolls.
for(const c of fixtures.slice(0,named)) {
  const q=c.request;
  function direct(s) {
    const p=dex.pokemon.find(p=>p.id===s.id);
    const opts={nature:s.nature,item:s.item||undefined,ability:catalog.abilities[s.ability],abilityOn:s.abilityOn,
      evs:Object.fromEntries(stats.map((k,i)=>[k,s.sp[i]])),boosts:Object.fromEntries(stats.map((k,i)=>[k,s.boosts[i]])),
      status:s.status,gender:s.gender,alliesFainted:s.alliesFainted,
      overrides:{baseStats:Object.fromEntries(stats.map((k,i)=>[k,p.stats[i]])),weightkg:p.weight/10,
        types:p.types.map(i=>['Normal','Fighting','Flying','Poison','Ground','Rock','Bug','Ghost','Steel','Fire','Water','Grass','Electric','Psychic','Ice','Dragon','Dark','Fairy'][i])}};
    const mon=new calc.Pokemon(gen,catalog.species[p.id],opts);mon.originalCurHP=Math.max(1,Math.floor(mon.maxHP()*s.hp/100));return mon;
  }
  const a=direct(q.attacker),d=direct(q.defender),m=dex.moves.find(m=>m.id===q.move);
  const fallback=m.id===1?calc.Generations.get(9).moves.get('pound'):{};
  const move=new calc.Move(gen,m.english,{ability:a.ability,item:a.item,isCrit:q.critical,hits:q.hits||undefined,
    overrides:Object.assign({},fallback,m.power>1?{basePower:m.power}:{})});
  const r=calc.calculate(gen,a,d,move,new calc.Field(q.field));
  assert.deepEqual(Array.from(r.range()),[c.expected.min,c.expected.max],c.name);
}
// One case per line keeps the reference readable and its diffs reviewable.
fs.writeFileSync(path.join(__dirname,'fixtures.json'),'[\n'+fixtures.map(c=>JSON.stringify(c)).join(',\n')+'\n]\n');
console.log(`PASS: ${fixtures.length} reference cases (${named} named, ${forms.length*3} seeded, ${failures} without a valid range), ${dex.pokemon.length} forms, ${attacks.length} attacks; direct upstream parity and invalid-input rejection`);

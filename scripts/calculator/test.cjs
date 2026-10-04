const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const root=path.resolve(__dirname,'../..'), assets=path.join(root,'app/src/main/assets');
const read=p=>fs.readFileSync(path.join(assets,p),'utf8');
const context=vm.createContext({});
vm.runInContext(read('calculator/engine.js'),context);
vm.runInContext(read('calculator/adapter.js'),context);
context.dexCalc.init(read('dex.json'),read('calculator/catalog.json'));
const dex=JSON.parse(read('dex.json')), catalog=JSON.parse(read('calculator/catalog.json'));
const calc=context.calc,gen=calc.Generations.get(0);
const stats=['hp','atk','def','spa','spd','spe'];
function side(name) {
  const p=dex.pokemon.find(p=>catalog.species[p.id]===name); assert(p,name);
  return {id:p.id,ability:p.abilities[0],nature:'Serious',item:'',sp:[0,0,0,0,0,0],boosts:[0,0,0,0,0,0],hp:100,status:'',gender:({1:'M',2:'N',3:'F'}[p.sex]||'M'),abilityOn:false,alliesFainted:0};
}
function request(attacker='Charizard',defender='Venusaur',move='Flamethrower') {
  return {attacker:side(attacker),defender:side(defender),move:dex.moves.find(m=>m.english===move).id,critical:false,hits:0,field:{gameType:'Singles'}};
}
function run(q) {return JSON.parse(context.dexCalc.calculate(JSON.stringify(q)));}
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
// Exhaustive adapter coverage: every form and every available damaging move.
for(const p of dex.pokemon) {const q=request();q.attacker.id=p.id;q.attacker.ability=p.abilities[0];q.attacker.gender=({1:'M',2:'N',3:'F'}[p.sex]||'M');const r=run(q);assert(!r.error,p.id+': '+r.error);assert.equal(r.attackerStats.hp,p.stats[0]+75);}
for(const m of dex.moves.filter(m=>m.available&&m.category!==2)) {const q=request();q.move=m.id;assert(!run(q).error,m.name+': '+run(q).error);}
for(const p of dex.pokemon) {
  assert(catalog.species[p.id],p.id);
  for(const a of p.abilities) assert(catalog.abilities[a],String(a));
}
const invalid=request();invalid.attacker.sp[0]=33;assert(run(invalid).error);
invalid.attacker.sp[0]='';assert(run(invalid).error);
invalid.attacker.sp[0]=0;invalid.move=10;assert(run(invalid).error);
// Independently invoke the upstream public API with equal source stats and compare exact rolls.
for(const c of fixtures) {
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
fs.writeFileSync(path.join(__dirname,'fixtures.json'),JSON.stringify(fixtures,null,2)+'\n');
console.log(`PASS: ${fixtures.length} golden cases, ${dex.pokemon.length} forms, ${dex.moves.filter(m=>m.available&&m.category!==2).length} attacks; direct upstream parity and invalid-input rejection`);

/* Native app boundary. The upstream engine and its mechanics remain unchanged. */
var dexCalc = (function () {
  var gen = calc.Generations.get(0), dex, catalog, pokemon = {}, moves = {};
  var stats = ['hp','atk','def','spa','spd','spe'];
  var types = ['Normal','Fighting','Flying','Poison','Ground','Rock','Bug','Ghost','Steel','Fire','Water','Grass','Electric','Psychic','Ice','Dragon','Dark','Fairy'];
  function init(dexJson, catalogJson) {
    dex=JSON.parse(dexJson); catalog=JSON.parse(catalogJson);
    dex.pokemon.forEach(function(p){pokemon[p.id]=p;});
    dex.moves.forEach(function(m){moves[m.id]=m;});
  }
  function integer(value,min,max,label) {
    if(value==='' || value===null || value===undefined) throw new Error(label+'不能为空');
    var n=Number(value);
    if(!isFinite(n) || Math.floor(n)!==n || n<min || n>max) throw new Error(label+'须为 '+min+'～'+max+' 的整数');
    return n;
  }
  function makePokemon(s) {
    var p=pokemon[s.id], name=catalog.species[s.id];
    if(!p || !name) throw new Error('此形态尚未适配计算器');
    var ability=catalog.abilities[s.ability];
    if(!ability || p.abilities.indexOf(Number(s.ability))<0) throw new Error('请选择此形态的特性');
    if(s.item && !gen.items.get(calc.toID(s.item))) throw new Error('道具不在 Champions 计算表中');
    if(!gen.natures.get(calc.toID(s.nature))) throw new Error('性格无效');
    var fixedGender={1:'M',2:'N',3:'F'}[p.sex];
    if(fixedGender && s.gender!==fixedGender) throw new Error('性别与当前形态不符');
    var base={},sp={},boosts={};
    stats.forEach(function(k,i){base[k]=p.stats[i];sp[k]=integer(s.sp[i],0,32,'能力点');boosts[k]=i ? integer(s.boosts[i],-6,6,'能力等级') : 0;});
    var opts={nature:s.nature,ability:ability,abilityOn:!!s.abilityOn,item:s.item || undefined,
      evs:sp, boosts:boosts, status:s.status || '', gender:s.gender || 'M',
      alliesFainted:integer(s.alliesFainted,0,5,'已倒下队友'),
      overrides:{baseStats:base,types:p.types.map(function(t){return types[t];}),weightkg:p.weight/10}};
    var result=new calc.Pokemon(gen,name,opts);
    result.originalCurHP=Math.max(1,Math.floor(result.maxHP()*integer(s.hp,1,100,'剩余 HP 百分比')/100));
    return result;
  }
  function calculate(request) {
    try {
      var q=JSON.parse(request), a=makePokemon(q.attacker), d=makePokemon(q.defender);
      var m=moves[q.move], name=catalog.moves[q.move];
      if(!m || !m.available || !name) throw new Error('该招式未开放或尚未适配');
      if(m.category===2) throw new Error('变化招式不直接计算伤害，请选择攻击招式');
      var data=gen.moves.get(calc.toID(name)) || (m.id===1 ? calc.Generations.get(9).moves.get('pound') : null);
      if(!data) throw new Error('缺少招式机制数据');
      if(q.hits) {
        var hitRange=catalog.moveHits[m.id];
        integer(q.hits,hitRange[0],hitRange[1],'命中次数');
      }
      var overrides={type:types[m.type],category:['Physical','Special','Status'][m.category],priority:m.priority,
        flags:Object.assign({},data.flags,{contact:m.contact ? 1 : 0})};
      if(m.id===1) overrides=Object.assign({},data,overrides);
      // Sentinel 1 is dynamic power; retain the engine rule instead of using 1 BP.
      if(m.power>1 || m.power===0) overrides.basePower=m.power;
      var move=new calc.Move(gen,name,{ability:a.ability,item:a.item,isCrit:!!q.critical,
        hits:q.hits ? integer(q.hits,1,10,'命中次数') : undefined,timesUsed:1,overrides:overrides});
      var result=calc.calculate(gen,a,d,move,new calc.Field(q.field || {})), range=result.range();
      if(!isFinite(range[0]) || !isFinite(range[1])) throw new Error('此组合没有有效的计算结果');
      // Preserve independent per-hit rolls rather than zipping them into 16 invented totals.
      var damage=result.damage, rolls=typeof damage==='number' ? [[damage]] :
        (typeof damage[0]==='number' ? (damage.length<16 ? damage.map(function(v){return [v];}) : [damage]) : damage);
      var distribution={'0':1};
      rolls.forEach(function(row){var next={};Object.keys(distribution).forEach(function(sum){row.forEach(function(v){var k=Number(sum)+v;next[k]=(next[k]||0)+distribution[sum]/row.length;});});distribution=next;});
      var ohko=0;Object.keys(distribution).forEach(function(sum){if(Number(sum)>=d.curHP()) ohko+=distribution[sum];});
      return JSON.stringify({min:range[0],max:range[1],maxHP:d.maxHP(),currentHP:d.curHP(),
        percentMin:Math.floor(range[0]*1000/d.maxHP())/10,percentMax:Math.floor(range[1]*1000/d.maxHP())/10,
        ohko:Math.round(ohko*1000)/10,rolls:rolls,attackerStats:a.rawStats,defenderStats:d.rawStats,
        applied:result.rawDesc,hits:move.hits});
    } catch(e) { return JSON.stringify({error:String(e.message || e)}); }
  }
  return {init:init,calculate:calculate};
})();

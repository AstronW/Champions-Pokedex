"""Import verified champout exports and Champions sprites; source checkouts are read-only."""
from __future__ import annotations
import argparse, collections, csv, hashlib, html, json, re, shutil, subprocess
from pathlib import Path
from urllib.parse import quote
import mwparserfromhell as mw
from opencc import OpenCC

ROOT = Path(__file__).resolve().parents[1]
CC = OpenCC('t2s')
TYPE_NAMES = ['一般','格斗','飞行','毒','地面','岩石','虫','幽灵','钢','火','水','草','电','超能力','冰','龙','恶','妖精']
# masterdata uses a different ordering from PokeAPI (1-based).
TYPE_API = [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18]

def digest(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read_json(path): return json.loads(Path(path).read_text(encoding='utf-8'))
def rows(path):
    with Path(path).open(encoding='utf-8', newline='') as f: return list(csv.DictReader(f))
def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')

class WikiRenderer:
    """Render the actual finite set of remaining Wiki constructs; never execute source HTML."""
    def __init__(self, entities, aliases):
        self.names = {(e['kind'], e['name_zh']): e['key'] for e in entities if e['name_zh'] and e['kind'] in ['pokemon','move','ability']}
        self.aliases = aliases
        self.unknown = collections.Counter()

    def link(self, title, label, kind=None):
        title = CC.convert(title.strip()).replace('_',' ')
        if title.lower().startswith(('category:', '分类:', 'file:', 'image:', '文件:')): return ''
        name = self.aliases.get(f'{kind}:{title}', title)
        key = self.names.get((kind, name)) if kind else None
        if not key:
            match = re.fullmatch(r'(.+)[（(](招式|特性|道具)[）)]', title)
            if match: key = self.names.get(({'招式':'move','特性':'ability','道具':'item'}[match[2]],match[1]))
        href = 'dex://entity/' + key if key else 'https://wiki.52poke.com/zh-hans/' + quote(title, safe='')
        return f'<a href="{html.escape(href, quote=True)}">{label}</a>'

    def render(self, source):
        result = []
        code = source if isinstance(source, mw.wikicode.Wikicode) else mw.parse(source)
        for n in code.nodes:
            name = type(n).__name__
            if name == 'Text': result.append(html.escape(CC.convert(str(n))))
            elif name == 'Comment': continue
            elif name == 'HTMLEntity': result.append(html.escape(n.normalize()))
            elif name == 'Wikilink': result.append(self.link(str(n.title), self.render(str(n.text if n.text is not None else n.title))))
            elif name == 'ExternalLink':
                label = self.render(str(n.title or n.url)); url = str(n.url)
                result.append(f'<a href="{html.escape(url,quote=True)}">{label}</a>' if url.startswith('https://') else label)
            elif name == 'Heading':
                level = min(4, max(2,n.level-1)); result.append(f'<h{level}>'+self.render(str(n.title))+f'</h{level}>')
            elif name == 'Template':
                tag = str(n.name).strip().lower()
                vals = [str(p.value).strip() for p in n.params if not p.showkey]
                if tag in ['m','a','s','i'] and vals:
                    kind = {'m':'move','a':'ability','s':'status','i':'item'}[tag]
                    result.append(self.link(vals[0], self.render(vals[1] if len(vals)>1 else vals[0]),kind))
                elif tag == 'gen' and vals: result.append('第'+html.escape(vals[0])+'世代')
                elif tag == 'game' and vals: result.append('《'+html.escape('/'.join({'SV':'朱／紫'}.get(v,v) for v in vals))+'》')
                elif tag == 'sup/md': result.append('<sup>'+('宝可梦超不可思议迷宫' if vals==['SMD'] else '宝可梦不可思议迷宫')+'</sup>')
                else:
                    self.unknown[str(n.name)] += 1
                    result.append(html.escape(CC.convert(str(n))))
            elif name == 'Tag':
                tag = str(n.tag).lower()
                if tag in ['li','dd','dt'] and not str(n.contents): result.append(' • ')
                elif tag in ['br','hr']: result.append(f'<{tag}>')
                elif tag in ['b','i','strong','em','sup','sub','table','tbody','tr','td','th','caption','ul','ol','li','p','div','span','small','center']:
                    attrs = ''.join(f' {a.name}="{a.value}"' for a in n.attributes if str(a.name) in ['colspan','rowspan'] and str(a.value).isdigit())
                    result.append(f'<{tag}{attrs}>'+self.render(n.contents)+f'</{tag}>')
                else: result.append(html.escape(CC.convert(str(n))))
            else: result.append(html.escape(CC.convert(str(n))))
        return ''.join(result)

def slug(text): return text.lower().replace('é','e').replace('’','').replace("'",'').replace(' ', '-')

def sprite_identifier(r, api):
    no, form = int(r['raw_no']), r['form_en']
    name = slug(r['name_en'])
    # These varieties' national IDs name a different form than the game's form 0.
    special = {
        128: {'Paldean Form (Combat Breed)':'tauros-paldea-combat-breed','Paldean Form (Blaze Breed)':'tauros-paldea-blaze-breed','Paldean Form (Aqua Breed)':'tauros-paldea-aqua-breed'},
        678: {'Male':'meowstic-male','Female':'meowstic-female'},
        681: {'Shield Forme':'aegislash-shield','Blade Forme':'aegislash-blade'},
        711: {'Medium Variety':'gourgeist-average','Small Variety':'gourgeist-small','Large Variety':'gourgeist-large','Jumbo Variety':'gourgeist-super'},
        745: {'Midday Form':'lycanroc-midday','Midnight Form':'lycanroc-midnight','Dusk Form':'lycanroc-dusk'},
        778: {'Disguised Form':'mimikyu-disguised','Busted Form':'mimikyu-busted'},
        849: {'Amped Form':'toxtricity-amped','Low Key Form':'toxtricity-low-key'},
        876: {'Male':'indeedee-male','Female':'indeedee-female'},
        877: {'Full Belly Mode':'morpeko-full-belly','Hangry Mode':'morpeko-hangry'},
        902: {'Male':'basculegion-male','Female':'basculegion-female'},
        925: {'Family of Three':'maushold-family-of-three','Family of Four':'maushold-family-of-four'},
        931: {c+' Plumage':'squawkabilly-'+c.lower()+'-plumage' for c in ['Green','Blue','Yellow','White']},
        964: {'Zero Form':'palafin-zero','Hero Form':'palafin-hero'},
        670: {'Eternal Flower':'floette-eternal'},
    }
    if no == 678 and form == 'Mega Meowstic': return 'meowstic-'+('female' if r['raw_fo']=='3' else 'male')+'-mega'
    if form in special.get(no,{}): return special[no][form]
    if form.startswith('Mega '): return name+'-mega'+slug(form[5:]).removeprefix(name)
    for prefix, suffix in [('Alolan','alola'),('Galarian','galar'),('Hisuian','hisui')]:
        if form.startswith(prefix): return name+'-'+suffix
    if no == 479 and form != 'Rotom': return 'rotom-'+slug(form.split()[0])
    if no == 351 and form != 'Normal': return 'castform-'+slug(form.split()[0])
    # Cosmetic forms have no distinct Pokémon variety IDs in this sprites directory.
    # Inspected Champions 666.png: pink Meadow wings. Game form 0 is Icy Snow.
    if no == 666: return 'vivillon' if form == 'Meadow Pattern' else None
    if no == 671: return 'florges' if form == 'Red Flower' else None
    if no == 676: return 'furfrou' if form == 'Natural Form' else None
    if no == 869: return 'alcremie' if form == 'Vanilla Cream' else None
    if no == 855: return 'polteageist' if form == 'Phony Form' else None
    if no == 1013: return 'sinistcha' if form == 'Unremarkable Form' else None
    if r['raw_fo']=='0':
        candidates = [x['identifier'] for x in api if x['species_id']==str(no) and x['is_default']=='1']
        return candidates[0] if len(candidates)==1 else None
    return None

def import_data(champout, sprites, explicit_output=None):
    outputs = [explicit_output] if explicit_output else sorted((champout/'output').glob('*/manifest.json'), key=lambda p:p.stat().st_mtime,reverse=True)
    output = Path(outputs[0]); output = output.parent if output.name=='manifest.json' else output
    manifest=read_json(output/'manifest.json')
    for name, expected in manifest['files'].items():
        if digest(output/name)!=expected: raise ValueError(f'Corrupt champout output: {name}')
    inputs=read_json(output/'inputs.json')
    for name, expected in inputs[1].items():
        if digest(champout/name)!=expected: raise ValueError(f'Stale champout source: {name}; run champout prepare')
    xmls=sorted(champout.glob('52poke-*.xml'))
    if not xmls or digest(xmls[-1])!=inputs[2]: raise ValueError('Wiki dump does not match the champout export')
    entities=read_json(output/'entities.json')
    pages={p['entity_key']:p for p in read_json(output/'pages.json')}
    config=read_json(output/'effective_config.json')
    renderer=WikiRenderer(entities, config['reference_aliases'])
    assets=ROOT/'app/src/main/assets'; assets.mkdir(parents=True,exist_ok=True)
    (assets/'wiki').mkdir(exist_ok=True)
    (assets/'pokemon').mkdir(exist_ok=True)
    (assets/'types').mkdir(exist_ok=True)
    api=rows(ROOT/'data/pokeapi/pokemon.csv'); api_index={r['identifier']:r for r in api}
    sprite_root=sprites/'sprites/pokemon/versions/generation-ix/champions'
    audit=[]; wiki=[]
    for e in entities:
        if e['kind'] not in ['move','ability']: continue
        page=pages.get(e['key'])
        if not page or page['status']!='ok': continue
        body=renderer.render(page['expanded'])
        filename=e['key'].replace(':','_')+'.html'
        (assets/'wiki'/filename).write_text(body,encoding='utf-8')
        wiki.append({'key':e['key'],'file':filename,'title':page['title'],'revision':page['revision_id'],'hash':page['source_hash'], 'url':'https://wiki.52poke.com/w/index.php?oldid='+page['revision_id']})
    pokemon=[]
    for r in rows(output/'pokemon.csv'):
        identifier=sprite_identifier(r,api)
        candidate=api_index.get(identifier)
        image_id=candidate['id'] if candidate else ''
        if candidate and candidate['species_id']!=r['raw_no']: raise ValueError('Cross-species sprite')
        filename=image_id+'.png'
        exists=bool(image_id and (sprite_root/filename).is_file())
        shiny=exists and (sprite_root/'shiny'/filename).is_file()
        if exists: shutil.copyfile(sprite_root/filename,assets/'pokemon'/filename)
        if shiny: shutil.copyfile(sprite_root/'shiny'/filename,assets/'pokemon'/('shiny_'+filename))
        audit.append({'id':r['raw_id'],'name':r['name_zh'],'form':r['form_zh'],'identifier':identifier,'sprite':filename if exists else None,'status':'exact' if exists else 'missing'})
        pokemon.append({'id':r['raw_id'],'no':int(r['raw_no']),'formId':int(r['raw_fo']),'name':r['name_zh'],'english':r['name_en'],'form':r['form_zh'],'formEnglish':r['form_en'], 'types':list(dict.fromkeys([int(r['raw_type1']),int(r['raw_type2'])])), 'stats':[int(r['raw_'+k]) for k in ['hp','atk','def','spatk','spdef','agi']], 'abilities':[int(r['raw_toku'+str(i)]) for i in range(3)], 'weight':int(r['raw_weight']), 'sex':int(r['raw_sex']), 'sprite':filename if exists else '', 'shiny':('shiny_'+filename) if shiny else ''})
    moves=[]
    for e in entities:
        if e['kind']!='move': continue
        r=e['raw']
        moves.append({'id':int(e['id']),'name':e['name_zh'] or '招式 #'+e['id'],'english':e['name_en'],'description':e['description_zh'],'available':e['available'],'type':int(r['type']),'category':int(r['category']),'power':int(r['power']),'accuracy':int(r['accuracy']),'pp':int(r['pp']),'priority':int(r['priority']),'contact':r['direct']=='1'})
    abilities=[{'id':int(e['id']),'name':e['name_zh'],'english':e['name_en'],'description':e['description_zh']} for e in entities if e['kind']=='ability']
    learns=collections.defaultdict(list)
    for r in rows(output/'learnsets.csv'): learns[r['personal_id']].append(int(r['move_id']))
    for i,api_id in enumerate(TYPE_API):
        source=sprites/f'sprites/types/generation-ix/champions/small/{api_id}.png'
        shutil.copyfile(source,assets/'types'/f'{i}.png')
    version={'champout':output.name,'pipeline':manifest['version'],'wikiDump':xmls[-1].name,'spritesCommit':subprocess.check_output(['git','-C',str(sprites),'rev-parse','HEAD'],text=True).strip(),'species':len({p['no'] for p in pokemon}),'forms':len(pokemon),'availableMoves':sum(m['available'] for m in moves),'abilities':len(abilities),'missingSprites':sum(x['status']=='missing' for x in audit)}
    data={'version':version,'types':TYPE_NAMES,'pokemon':pokemon,'moves':moves,'abilities':abilities,'learnsets':learns,'wiki':wiki}
    write_json(assets/'dex.json',data)
    write_json(ROOT/'data/sprite-map.json',audit)
    write_json(ROOT/'data/import-report.json',{'version':version,'learnsets':sum(map(len,learns.values())),'wikiPages':len(wiki),'unhandledTemplates':renderer.unknown,'inputManifest':manifest,'metadataHashes':{p.name:digest(p) for p in (ROOT/'data/pokeapi').glob('*.csv')}})
    # Calculator resources have their own provenance and are rebuilt after this import.
    write_json(ROOT/'data/bundle-sha256.json',{p.relative_to(assets).as_posix():digest(p) for p in sorted(assets.rglob('*')) if p.is_file() and p.relative_to(assets).parts[0] != 'calculator'})
    print(json.dumps(version,ensure_ascii=False,indent=2))
    print('Wiki:',len(wiki),'unhandled templates:',dict(renderer.unknown))

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--champout',type=Path,default=ROOT.parent/'champout')
    parser.add_argument('--sprites',type=Path,default=ROOT.parent/'sprites')
    parser.add_argument('--output',type=Path)
    args=parser.parse_args();import_data(args.champout,args.sprites,args.output)

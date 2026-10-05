import collections
import csv
import json
from pathlib import Path
import unittest
from import_data import ROOT, WikiRenderer, read_json, rows, sprite_identifier, status_excerpt, CC

class DataContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data=read_json(ROOT/'app/src/main/assets/dex.json')
        cls.source=ROOT.parent/'champout'/'output'/cls.data['version']['champout']
        cls.entities=read_json(cls.source/'entities.json')
        cls.bodies=read_json(ROOT/'app/src/main/assets/wiki.json')

    def test_all_game_fields_and_relations_match_source(self):
        source={r['raw_id']:r for r in rows(self.source/'pokemon.csv')}
        self.assertEqual(len(source),len(self.data['pokemon']))
        for p in self.data['pokemon']:
            r=source[p['id']]
            self.assertEqual(p['stats'],[int(r['raw_'+k]) for k in ['hp','atk','def','spatk','spdef','agi']])
            self.assertEqual(p['types'],list(dict.fromkeys([int(r['raw_type1']),int(r['raw_type2'])])))
            self.assertEqual(p['abilities'],[int(r['raw_toku'+str(i)]) for i in range(3)])
        source_learns=collections.defaultdict(list)
        for r in rows(self.source/'learnsets.csv'): source_learns[r['personal_id']].append(int(r['move_id']))
        self.assertEqual(dict(source_learns),self.data['learnsets'])

    def test_game_move_values_not_overwritten_by_wiki(self):
        source={e['id']:e for e in self.entities if e['kind']=='move'}
        for m in self.data['moves']:
            e=source[str(m['id'])]
            for field in ['power','accuracy','pp','priority','category','type']: self.assertEqual(m[field],int(e['raw'][field]))
            self.assertEqual(m['available'],e['available'])
        self.assertEqual(512,sum(m['available'] for m in self.data['moves']))

    def test_references_are_complete(self):
        moves={m['id'] for m in self.data['moves']};abilities={a['id'] for a in self.data['abilities']}
        for p in self.data['pokemon']:
            self.assertTrue(set(p['abilities'])<=abilities)
            self.assertTrue(set(self.data['learnsets'][p['id']])<=moves)
        wiki={w['key'] for w in self.data['wiki']}
        self.assertTrue({f"move:{m['id']}" for m in self.data['moves'] if m['available']} <= wiki)
        self.assertTrue({f"ability:{a['id']}" for a in self.data['abilities']} <= wiki)

    def test_form_image_mapping_and_missing_are_explicit(self):
        mapping={x['id']:x for x in read_json(ROOT/'data/sprite-map.json')}
        self.assertEqual(mapping['0925000']['sprite'],'10257.png')
        self.assertEqual(mapping['0925001']['sprite'],'925.png')
        self.assertEqual(mapping['0006001']['sprite'],'10034.png')
        self.assertEqual(mapping['0678002']['sprite'],'10314.png')
        self.assertEqual(mapping['0678003']['sprite'],'10326.png')
        self.assertEqual(mapping['0666006']['sprite'],'666.png')
        self.assertIsNone(mapping['0666000']['sprite'])
        for p in self.data['pokemon']:
            if p['sprite']: self.assertTrue((ROOT/'app/src/main/assets/pokemon'/p['sprite']).is_file())
            else: self.assertEqual(mapping[p['id']]['status'],'missing')

    def test_wiki_markup_is_readable_and_safe(self):
        renderer=WikiRenderer(self.entities,{'move:空气斩':'空气之刃'})
        rendered=renderer.render("'''粗体''' {{m|喷射火焰}} [[Category:测试]]<script>alert(1)</script><sup>1</sup>&frasl;<sub>3</sub>")
        self.assertIn('<b>粗体</b>',rendered)
        self.assertIn('dex://entity/move:53',rendered)
        self.assertNotIn('Category',rendered)
        self.assertNotIn('<script>',rendered)
        self.assertIn('<sup>1</sup>⁄<sub>3</sub>',rendered)
        table=renderer.render('{|\n!招式!!威力\n|-\n|火花||40\n|}')
        self.assertIn('<table>',table);self.assertIn('<td>',table);self.assertIn('火花',table)
        self.assertNotIn('dex://entity/item:',renderer.render('{{I|王者之证}}'))

    def test_no_unknown_templates_and_source_tracing_present(self):
        self.assertEqual({},read_json(ROOT/'data/import-report.json')['unhandledTemplates'])
        for w in self.data['wiki']:
            self.assertTrue(w['revision'].isdigit())
            self.assertEqual(len(w['hash']),64)
            self.assertIn(w['key'],self.bodies)

    def test_mechanics_preserve_relations_unknown_flags_and_source_evidence(self):
        records = read_json(ROOT/'data/mechanics-records.json')
        mechanics = read_json(ROOT/'app/src/main/assets/mechanics.json')
        names = {'categories':'wiki_move_categories.csv','relations':'wiki_category_entities.csv',
                 'flags':'wiki_special_flags.csv','targets':'wiki_move_targets.csv',
                 'references':'wiki_status_references.csv','issues':'wiki_mechanics_issues.csv'}
        for key, name in names.items():
            source = rows(self.source/name)
            self.assertEqual(source, records[key])
            self.assertEqual(len(source), len(mechanics[key]))
            for original, imported in zip(source, mechanics[key]):
                self.assertEqual(imported, {k:original[k] for k in imported})
        self.assertEqual({'true','false',''}, {f['value'] for f in mechanics['flags']})
        self.assertEqual({'member','related_move','related_ability','section_reference'}, {r['relation'] for r in mechanics['relations']})
        game = next(m for m in self.data['moves'] if m['id'] == 515)
        wiki = next(f for f in mechanics['flags'] if f['entity_key'] == 'move:515' and f['field'] == 'contact')
        self.assertFalse(game['contact']); self.assertEqual('true', wiki['value'])

    def test_status_aliases_bodies_and_game_descriptions_match_source(self):
        mechanics = read_json(ROOT/'app/src/main/assets/mechanics.json')
        bodies = read_json(self.source/'wiki_status_bodies.json')
        self.assertEqual(bodies, read_json(ROOT/'data/status-sources.json'))
        self.assertEqual(len(bodies), len(mechanics['statuses']))
        entities = {e['key']:e for e in self.entities}
        pages = {w['key']:w for w in self.data['wiki']}
        for body, status in zip(bodies, mechanics['statuses']):
            self.assertEqual(body['body_key'], status['bodyKey'])
            self.assertEqual(body['source_hash'], pages[status['key']]['hash'])
            self.assertEqual(body['revision_id'], pages[status['key']]['revision'])
            self.assertEqual(list(dict.fromkeys(CC.convert(t) for t in body['requested_titles'])),status['aliases'])
            self.assertEqual(body['existing_status_keys'], [g['key'] for g in status['game']])
            for g in status['game']: self.assertEqual(entities[g['key']]['description_zh'], g['description'])
            self.assertTrue(status_excerpt(body)[0].strip())

    def test_all_offline_links_resolve_and_status_move_names_do_not_collide(self):
        import re
        mechanics = read_json(ROOT/'app/src/main/assets/mechanics.json')
        valid = {f"move:{m['id']}" for m in self.data['moves']} | {f"ability:{a['id']}" for a in self.data['abilities']}
        valid |= {s['key'] for s in mechanics['statuses']} | {'category:'+c['category_key'] for c in mechanics['categories']}
        for w in self.data['wiki']:
            content = self.bodies[w['key']]
            for key in re.findall(r'href="dex://entity/([^\"]+)"',content): self.assertIn(key,valid,w['key'])
        poison = next(s for s in mechanics['statuses'] if s['name'] == '中毒（状态）')
        local = {alias:s['key'] for s in mechanics['statuses'] for alias in [s['name'],*s['aliases']]}
        renderer = WikiRenderer(self.entities, {}, local)
        self.assertIn('dex://entity/'+poison['key'],renderer.render('{{s|剧毒}}'))
        substitute = next(s for s in mechanics['statuses'] if s['name'] == '替身（状态）')
        self.assertIn('dex://entity/'+substitute['key'],renderer.render('{{s|替身}}'))
        self.assertIn('dex://entity/move:164',renderer.render('{{m|替身}}'))
        self.assertIn('dex://entity/move:273',renderer.render('[[祈愿（招式）]]'))
        self.assertIn('dex://entity/'+next(s['key'] for s in mechanics['statuses'] if '祈愿（状态）' in s['aliases']), renderer.render('{{s|祈愿}}'))

if __name__=='__main__': unittest.main()

import collections
import csv
import json
from pathlib import Path
import unittest
from import_data import ROOT, WikiRenderer, read_json, rows, sprite_identifier

class DataContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data=read_json(ROOT/'app/src/main/assets/dex.json')
        cls.source=ROOT.parent/'champout'/'output'/cls.data['version']['champout']
        cls.entities=read_json(cls.source/'entities.json')

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
            self.assertTrue((ROOT/'app/src/main/assets/wiki'/w['file']).is_file())

if __name__=='__main__': unittest.main()

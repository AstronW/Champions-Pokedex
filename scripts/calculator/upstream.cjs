// Load the checked-in upstream source in Node without modifying it. Used only at import and test time;
// the app ships the Kotlin port (CalculatorEngine.kt / DamageCalc.kt), never this JavaScript.
const fs = require('fs');
const path = require('path');
const ts = require('typescript');
const root = path.resolve(__dirname, '../..');
const src = path.join(root, 'vendor/damage-calc/src');

function load() {
  const modules = new Map();
  function collect(id) {
    if (modules.has(id)) return;
    const source = fs.readFileSync(path.join(src, id + '.ts'), 'utf8');
    const js = ts.transpileModule(source, {compilerOptions: {
      target: ts.ScriptTarget.ES2019, module: ts.ModuleKind.CommonJS, removeComments: true,
    }}).outputText;
    modules.set(id, js);
    for (const [, dep] of js.matchAll(/require\("([^"]+)"\)/g)) collect(resolve(id, dep));
  }
  function resolve(from, dep) {
    let next = path.posix.normalize(path.posix.join(path.posix.dirname(from), dep));
    if (!fs.existsSync(path.join(src, next + '.ts'))) next += '/index';
    return next;
  }
  collect('index');
  const cache = {};
  function require(id) {
    if (cache[id]) return cache[id].exports;
    const module = cache[id] = {exports: {}};
    new Function('require', 'module', 'exports', modules.get(id))(dep => require(resolve(id, dep)), module, module.exports);
    return module.exports;
  }
  return {calc: require('index'), require, sources: [...modules.keys()].map(id => id + '.ts'), src};
}

module.exports = {load, root};

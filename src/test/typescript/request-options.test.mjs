import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import ts from 'typescript';

function load(file, modules) {
    const exports = {};
    const compiled = ts.transpile(readFileSync(file, 'utf8'), { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, experimentalDecorators: true });
    new Function('exports', 'require', compiled)(exports, (name) => modules[name]);
    return exports;
}

const serviceFile = process.argv[2];
const calls = [];
const record = (method) => (...args) => {
    calls.push({ method, args });
    return undefined;
};
const http = { get: record('get'), post: record('post') };
const api = new (load(serviceFile, {
    '@angular/core': { inject: () => http, Injectable: () => (target) => target },
    '@angular/common/http': { HttpClient: class {} },
    './query-params': load(join(dirname(serviceFile), 'query-params.ts'), {}),
    './request-options': {},
}).RepositoryApi)();
const context = { token: 'skip-alert' };
const lastOptions = () => calls.at(-1).args.at(-1);

api.getExercise(1, { context, headers: { 'ngsw-bypass': 'true' } });
assert.equal(lastOptions().context, context, 'the caller context reaches HttpClient');
assert.deepEqual({ ...lastOptions().headers }, { 'ngsw-bypass': 'true' }, 'caller headers reach HttpClient');

api.getExercise(1);
assert.equal(calls.at(-1).args[1], undefined, 'a call without options passes no options to HttpClient');

api.getRepository(1, 'Bearer declared', undefined, undefined, { context, headers: { Authorization: 'Bearer caller', 'X-Extra': 'kept' } });
assert.equal(lastOptions().context, context);
assert.deepEqual(
    { ...lastOptions().headers },
    { Authorization: 'Bearer declared', 'X-Extra': 'kept' },
    'a declared header parameter wins over a caller header of the same name, other caller headers stay',
);

api.getRepository(1, 'Bearer declared', undefined, undefined, {
    headers: { Authorization: 'Bearer first', authorization: 'Bearer caller' },
});
const normalized = new Map(Object.entries(lastOptions().headers).map(([key, value]) => [key.toLowerCase(), value]));
assert.equal(normalized.get('authorization'), 'Bearer declared', 'declared headers win regardless of caller key casing');

api.getExercise(1, { context, observe: 'response', responseType: 'text' });
assert.equal(lastOptions().observe, undefined, 'unsupported options cannot change the response mode');
assert.equal(lastOptions().responseType, undefined, 'unsupported options cannot change response parsing');

api.submitRepositoryFeedback(1, 'Bearer declared', { text: 'ok' }, { context });
assert.deepEqual(calls.at(-1).args[1], { text: 'ok' }, 'the body stays the second argument');
assert.equal(lastOptions().context, context);

api.getRepositoryToken(1, 'Bearer declared', { context });
assert.equal(lastOptions().responseType, 'text', 'a text response keeps its responseType next to the caller options');
assert.equal(lastOptions().context, context);

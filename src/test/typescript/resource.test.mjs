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

const [resourcesFile, serviceFile] = process.argv.slice(2);
const request = (callback) => callback;
const http = { get: (url, options) => ({ url, ...options }) };
const angular = (file) => ({
    '@angular/common/http': { httpResource: Object.assign(request, { text: request, blob: request }), HttpClient: class {} },
    '@angular/core': { inject: () => http, Injectable: () => (target) => target },
    './query-params': load(join(dirname(file), 'query-params.ts'), {}),
});
const generated = load(resourcesFile, angular(resourcesFile));
const api = new (load(serviceFile, angular(serviceFile)).ReviewApi)();
const plain = (request) => (request?.headers ? { ...request, headers: { ...request.headers } } : request);

assert.deepEqual(
    plain(generated.readReviewsResource('first', 'second')()),
    { url: '/api/reviews', headers: { token: 'first', tokenValue: 'second' } },
    'each header argument keeps its own value',
);
assert.deepEqual(
    plain(generated.readReviewResource('path-value', 'header-value', () => ({ tokenPath: 'query-value' }))()),
    { url: '/api/reviews/path-value?tokenPath=query-value', headers: { tokenValue: 'header-value' } },
    'path, header and query arguments keep their own values',
);
assert.deepEqual(
    plain(api.readReview('path-value', 'header-value', 'query-value')),
    { url: '/api/reviews/path-value?tokenPath=query-value', headers: { tokenValue: 'header-value' } },
    'the service keeps the value of each argument',
);

const quoted = { url: "/api/quoted?it%27s=a&back%5Cslash=b", headers: { "X-It's": 'h' } };
assert.deepEqual(plain(generated.readQuotedResource('h', () => ({ its: 'a', backSlash: 'b' }))()), quoted, 'a resource keeps each wire name');
assert.deepEqual(plain(api.readQuoted('h', 'a', 'b')), quoted, 'the service keeps each wire name');
assert.doesNotThrow(() => new Headers(quoted.headers), 'the expected header name is a valid HTTP header name');

assert.equal(generated.readReviewsResource(() => undefined, 'second')(), undefined, 'a required header that is not known yet keeps the resource idle');
assert.deepEqual(
    plain(generated.readQuotedResource(() => undefined)()),
    { url: '/api/quoted', headers: {} },
    'an optional header that is not known yet is left out',
);

const shapes = [new Set(['blue', 'black']), ['blue', 'black'], { R: '100', G: '200' }, { R: '100', G: '200' }];
const simpleStyle = [['X-Set', 'blue,black'], ['X-Array', 'blue,black'], ['X-Object', 'R,100,G,200'], ['X-Exploded', 'R=100,G=200']];
assert.deepEqual(Object.entries(generated.readHeaderShapesResource(...shapes)().headers), simpleStyle, 'a resource sends collections and objects in simple style');
assert.deepEqual(Object.entries(api.readHeaderShapes(...shapes).headers), simpleStyle, 'the service sends collections and objects in simple style');

const proto = [['__proto__', 'expected-value']];
assert.deepEqual(Object.entries(generated.readProtoResource('expected-value')().headers), proto, 'a resource keeps a header named __proto__');
assert.deepEqual(Object.entries(api.readProto('expected-value').headers), proto, 'the service keeps a header named __proto__');

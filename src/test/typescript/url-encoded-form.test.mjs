import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const [apiFile, helperFile, pathParameter] = process.argv.slice(2);
const transpile = (file) => ts.transpile(readFileSync(file, 'utf8'), { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, experimentalDecorators: true });

const helper = {};
new Function('exports', transpile(helperFile))(helper);

const calls = [];
const http = { post: (url, body, options) => calls.push({ url, body, options }) };
const modules = {
    '@angular/core': { inject: () => http, Injectable: () => (target) => target },
    '@angular/common/http': { HttpClient: class {} },
    './query-params': helper,
};
const exports = {};
new Function('exports', 'require', transpile(apiFile))(exports, (name) => modules[name]);
const api = new exports.LoginApi();

api.login(...(pathParameter === 'true' ? ['tenant'] : []), 'a b&c', 'header.payload.sig', 'trace-1', false, 0, ['x', 'y'], ['p', 'q'], new Date('2026-10-06T12:34:56Z'), { level: 2 }, ['r', 's'], { count: 3 }, new Date('2026-10-06T00:00:00Z'), [new Date('2026-12-25T00:00:00Z')]);
let { url, body, options } = calls.pop();
assert.equal(url, pathParameter === 'true' ? '/api/login/tenant' : '/api/login');
assert.equal(typeof body, 'string', 'the body is a string, so Angular sends it as it is');
assert.equal(
    body,
    'state=a+b%26c&id_token=header.payload.sig&remember=false&attempt=0&scopes=x&scopes=y&joined=p%2Cq' +
        '&since=2026-10-06T12%3A34%3A56.000Z&filter%5Blevel%5D=2&piped=r%7Cs&count=3&day=2026-10-06&holidays=2026-12-25',
    'each field goes out in the style of its encoding, form with explode by default',
);
assert.deepEqual(options, { headers: { 'X-Trace': 'trace-1', 'Content-Type': 'application/x-www-form-urlencoded' } }, 'the form type joins the header parameters');

api.login(...(pathParameter === 'true' ? ['tenant'] : []), '', 'token');
({ body, options } = calls.pop());
assert.equal(body, 'state=&id_token=token', 'an empty string is sent and absent fields are left out');
assert.deepEqual(options, { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } });

api.logout();
({ body, options } = calls.pop());
assert.equal(body, '', 'a body without fields is empty');
assert.deepEqual(options, { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } });

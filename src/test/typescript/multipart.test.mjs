import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const source = readFileSync(process.argv[2], 'utf8');
const http = { post: (url, body) => body };
const angular = {
    '@angular/core': { inject: () => http, Injectable: () => (target) => target },
    '@angular/common/http': { HttpClient: class {} },
};
const exports = {};
const compiled = ts.transpile(source, { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, experimentalDecorators: true });
new Function('exports', 'require', compiled)(exports, (name) => angular[name]);

const form = new exports.UploadApi().createUpload(
    { title: 'Algorithms' },
    new Blob(['slides']),
    [new Blob(['a']), null, new Blob(['b'])],
    'n',
    3,
    0.5,
    false,
    'DRAFT',
    [{ number: 1 }],
    { key: 'value' },
    new Set(['x', 'y']),
    new Set([{ number: 2 }]),
    ['DRAFT', 'FINAL'],
    ['FINAL'],
    'quoted',
    ['a', 'b'],
    [{ id: 7 }, ['x', 'y'], 'a'],
    [new Date('2026-10-06T12:34:56Z')],
    ['a', 'b'],
    new Date('2026-10-07T08:00:00Z'),
    new Date('2026-10-08T00:00:00Z'),
    [new Date('2026-10-09T10:00:00Z'), null],
    [new Blob(['first']), new Blob(['second'])],
    new Blob(['any']),
    ['a', 'b'],
);

const parts = {};
for (const [name, value] of form.entries()) {
    const part = value instanceof Blob ? { type: value.type, body: await value.text() } : value;
    (parts[name] ??= []).push(part);
}
const json = (body) => [{ type: 'application/json', body }];

assert.deepEqual(parts.course, json('{"title":"Algorithms"}'));
assert.deepEqual(parts.file, [{ type: '', body: 'slides' }]);
assert.deepEqual(parts.files, [{ type: '', body: 'a' }, { type: '', body: 'b' }], 'each binary of an array is its own part');
assert.deepEqual(parts.name, ['n']);
assert.deepEqual(parts.count, ['3']);
assert.deepEqual(parts.ratio, ['0.5']);
assert.deepEqual(parts.active, ['false']);
assert.deepEqual(parts.mode, ['DRAFT']);
assert.deepEqual(parts.pages, json('[{"number":1}]'));
assert.deepEqual(parts.labels, json('{"key":"value"}'));
assert.deepEqual(parts.tags, ['x', 'y'], 'an array of strings is sent as repeated fields, the default encoding of OpenAPI');
assert.deepEqual(parts.sections, json('[{"number":2}]'), 'a set of objects is sent as a JSON array');
assert.deepEqual(parts.modes, ['DRAFT', 'FINAL'], 'an array of enum values is sent as repeated fields');
assert.deepEqual(parts.modeRefs, ['FINAL'], 'an array of enum references is sent as repeated fields');
assert.deepEqual(parts["it's"], ['quoted'], 'a field name with an apostrophe keeps its name');
assert.deepEqual(parts.jsonLabels, json('["a","b"]'), 'an encoding with contentType application/json sends one JSON part');
assert.deepEqual(
    parts.anyItems,
    [...json('{"id":7}'), ...json('["x","y"]'), 'a'],
    'an untyped item is sent by its value: an object or array as a JSON part, a primitive as text',
);
assert.deepEqual(parts.attachments, [{ type: '', body: 'first' }, { type: '', body: 'second' }], 'untyped items that are files are sent as files');
assert.deepEqual(parts.anything, [{ type: '', body: 'any' }], 'an untyped field that is a file is sent as a file');
assert.deepEqual(parts.day, ['2026-10-08'], 'a date of format date is sent as a full date');
assert.deepEqual(parts.stamps, ['2026-10-09T10:00:00.000Z'], 'a null item is left out');
assert.deepEqual(
    parts.choiceLabels,
    [{ type: 'application/json', body: '["a","b"]' }],
    'an encoding that lists several JSON media types sends one part typed with the first',
);
assert.deepEqual(parts.dates, ['2026-10-06T12:34:56.000Z'], 'an array of dates is sent as repeated ISO 8601 fields');
assert.deepEqual(parts.due, ['2026-10-07T08:00:00.000Z'], 'a date is sent as an ISO 8601 field');
assert.deepEqual(
    parts.vendorLabels,
    [{ type: 'application/vnd.example+json', body: '["a","b"]' }],
    'an encoding with a +json media type sends one part of that type',
);

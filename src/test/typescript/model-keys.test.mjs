import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const [file, interfaceName, ...jsonKeys] = process.argv.slice(2);
const model = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.ES2022);
const declaration = model.statements.find((statement) => ts.isInterfaceDeclaration(statement) && statement.name.text === interfaceName);
assert.ok(declaration, `interface ${interfaceName} not found`);
assert.deepEqual(model.parseDiagnostics.map((diagnostic) => ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n')), []);
assert.deepEqual(declaration.members.map((member) => member.name.text), jsonKeys, 'each property key parses back to its JSON key');

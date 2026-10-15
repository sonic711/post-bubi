import assert from 'node:assert/strict'
import test from 'node:test'
import { applyHttpFieldRules } from './http-field-rules.mjs'

const rule = (path, action = 'INCREMENT', changes = {}) => ({ id: path, name: path, path, action,
  enabled: true, color: '#AB005F', targetType: 'HTTP_JSON_VALUE', ...changes })
const payload = (body, rules) => ({ bodyType: 'json', body, fieldRules: rules })

test('cURL preparation applies nested rules and escaped keys without changing unrelated source or input', () => {
  const input = payload('{ "a/b" : {"~id":"0099"}, "keep":1.00e+2, "day":"00" }\n',
    [rule('/a~1b/~0id'), rule('/day', 'TODAY_DD'), rule('/keep', 'NONE')])
  const output = applyHttpFieldRules(input, new Date('2026-10-15T16:01:00Z'))
  assert.equal(output.body, '{ "a/b" : {"~id":"0100"}, "keep":1.00e+2, "day":"16" }\n')
  assert.equal(input.body.includes('0099'), true)
})

test('cURL integer increments preserve precision beyond Number.MAX_SAFE_INTEGER', () => {
  assert.equal(applyHttpFieldRules(payload('{"id":9007199254740993}', [rule('/id')])).body, '{"id":9007199254740994}')
  assert.equal(applyHttpFieldRules(payload('{"id":-1}', [rule('/id')])).body, '{"id":0}')
})

test('disabled rules do not validate or modify non-JSON bodies', () => {
  const input = { bodyType: 'raw', body: 'raw', fieldRules: [rule('/id', 'invalid', { enabled: false })] }
  assert.equal(applyHttpFieldRules(input), input)
})

test('invalid targets, arrays, overflow, non-integers and date types are rejected', () => {
  const cases = [
    [payload('{"id":"99"}', [rule('/id')]), /超過/],
    [payload('{"id":1.0}', [rule('/id')]), /整數/],
    [payload('{"id":12}', [rule('/id', 'TODAY_DD')]), /字串/],
    [payload('{"items":[{"id":1}]}', [rule('/items/0/id')]), /array/],
    [payload('[]', [rule('/id')]), /array/],
    [payload('{"id":{}}', [rule('/id', 'NONE')]), /單一值/],
    [payload('{}', [rule('/id')]), /找不到/],
    [payload('{"id":1}', [rule('/id'), rule('/id', 'NONE', { id: 'two' })]), /多條/],
    [payload('{"id":1}', [rule('/bad~2key')]), /Pointer/],
    [payload('{"id":1}', [rule('/id', 'OTHER')]), /動作/],
    [payload('{"id":1}', [rule('/id', 'NONE', { color: 'red' })]), /顏色/],
  ]
  for (const [input, error] of cases) assert.throws(() => applyHttpFieldRules(input), error)
})

test('unresolved rule targets require Environment resolution; unrelated templates stay unchanged', () => {
  assert.throws(() => applyHttpFieldRules(payload('{"id":"{{sequence}}"}', [rule('/id')])), /Environment/)
  assert.throws(() => applyHttpFieldRules(payload('{{body}}', [rule('/id')])), /Environment/)
  assert.equal(applyHttpFieldRules(payload('{"id":"0009","other":"{{keep}}"}', [rule('/id')])).body,
    '{"id":"0010","other":"{{keep}}"}')
})

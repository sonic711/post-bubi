import assert from 'node:assert/strict'
import test from 'node:test'
import { formatJsonSource, jsonSourceTree, utf8SourceRanges } from './json-source.mjs'

test('strict source parsing rejects comments, trailing commas and incomplete input', () => {
  for (const text of ['{"a":1,}', '{/*comment*/"a":1}', '{"a":', '']) assert.equal(jsonSourceTree(text), undefined)
})

test('explicit formatting preserves large integers, exponents, escapes and duplicate keys', () => {
  const result = formatJsonSource('{"id":9007199254740993,"n":1.00e+2,"s":"\\u7e41","id":2}')
  assert.match(result, /9007199254740993/)
  assert.match(result, /1\.00e\+2/)
  assert.match(result, /\\u7e41/)
  assert.equal((result.match(/"id"/g) || []).length, 2)
  assert.ok(result.endsWith('\n'))
})

test('UTF-8 byte ranges map to exact raw JSON escapes and supplementary Unicode', () => {
  const raw = '"\\uD83D\\uDE000009\\n"'
  const ranges = utf8SourceRanges(raw, [{ start: 1, end: 4 }, { start: 5, end: 8 }, { start: 9, end: 9 }])
  assert.deepEqual(ranges.map(({ start, end }) => raw.slice(start, end)), ['\\uD83D\\uDE00', '0009', '\\n'])
  assert.equal(utf8SourceRanges(raw, [{ start: 2, end: 5 }]).length, 0)
})

import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import vm from 'node:vm'
import { renderJsonSource, utf8SourceRanges } from './json-source.mjs'

const source = await readFile(new URL('./App.vue', import.meta.url), 'utf8')
function editor() {
  const context = {
    TextEncoder,
    renderJsonSource, utf8SourceRanges,
    activeBodyText: { value: '' }, grpcEncodePayloadDataBase64: { value: false },
    requestType: { value: 'HTTP' }, bodyType: { value: 'json' },
    activeFieldRules: { value: [] }, batchUseFieldRules: { value: true },
    tryJsonParse: (value) => { try { return JSON.parse(value) } catch { return null } },
    escapeHtml: (value) => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;'),
    resolveExecutionPayload: (value) => value,
    executePayload: () => ({ fieldRules: context.activeFieldRules.value }),
    grpcExecutePayload: () => ({ fieldRules: context.activeFieldRules.value }),
    grpcBurExecutePayload: () => ({ settings: { fieldRules: context.activeFieldRules.value, incrementBasicLabelSequence: true } }),
  }
  context.escapeAttribute = context.escapeHtml
  vm.createContext(context)
  for (const name of ['newFieldRuleForType', 'restoreFieldRules', 'ruleColor', 'fieldRuleSummary',
    'fieldRuleValidationMessage', 'collectHttpJsonFieldOptions', 'renderJsonValue',
    'renderFieldRuleSpan', 'renderJsonPrimitive', 'indent', 'batchExecutePayload', 'batchExecutePayloadForRules', 'loadHistoryItem',
    'highlightRequestBody', 'activeJsonFieldRuleMap', 'renderSourceFieldRuleValue', 'renderPayloadBase64JsonValue', 'highlightJson']) {
    const start = source.search(new RegExp(`^function ${name}\\(`, 'm'))
    assert.ok(start >= 0, name)
    const remaining = source.slice(start)
    const end = remaining.slice(1).search(/^(?:async )?function \w+\(/m)
    vm.runInContext(remaining.slice(0, end + 1), context)
  }
  return context
}
const rule = (changes = {}) => ({ id: 'one', name: '序號', color: '#AB005F', enabled: true,
  targetType: 'HTTP_JSON_VALUE', action: 'INCREMENT', path: '/id', start: 1, end: 4, ...changes })

test('legacy BUR flags migrate to three rules; explicit empty rules suppress migration', () => {
  const context = editor()
  const restored = context.restoreFieldRules({ requestType: 'GRPC_BUR', grpcBurIncrementBasicLabelSequence: true })
  assert.deepEqual(JSON.parse(JSON.stringify(restored)).map(({ start, end, enabled }) => [start, end, enabled]),
    [[8, 14, true], [37, 46, true], [88, 89, false]])
  assert.equal(context.restoreFieldRules({ requestType: 'GRPC_BUR', fieldRules: [] }).length, 0)
  assert.equal(context.restoreFieldRules({ requestType: 'HTTP', fieldRules: [rule()] })[0].path, '/id')
})

test('HTTP picker supports escaped nested scalar keys and excludes arrays', () => {
  const context = editor()
  const fields = context.collectHttpJsonFieldOptions('{"a/b":{"~key":1},"items":[{"id":2}],"nil":null}')
  assert.deepEqual(Array.from(fields, (field) => field.path), ['/a~1b/~0key', '/nil'])
})

test('HTTP History restores saved rules instead of retaining another request rules', () => {
  const context = editor()
  for (const name of ['fieldRules', 'selectedRequestId', 'requestName', 'method', 'url', 'paramsText', 'headersText',
    'headerRows', 'bodyText', 'formDataParts', 'responseDecodeRows', 'timeoutMillis', 'followRedirects',
    'ignoreSslVerification', 'response', 'errorText', 'cancellationText', 'activeRequestTab', 'workspaceStatus']) {
    context[name] = { value: null }
  }
  Object.assign(context, { confirmDiscardUnsavedChanges: () => true, clearBatchRunHistory: () => {},
    safeJsonParse: JSON.parse, nameValueToText: () => '', restoreNameValueRows: () => [],
    restoreFormDataParts: () => [], markEditorSaved: () => {} })
  context.requestType.value = 'GRPC'
  context.fieldRules.value = [rule({ path: '/wrong' })]
  context.loadHistoryItem({ requestJson: JSON.stringify({ fieldRules: [rule()] }) })
  assert.equal(context.requestType.value, 'HTTP')
  assert.equal(context.fieldRules.value[0].path, '/id')
  context.loadHistoryItem({ requestJson: JSON.stringify({ fieldRules: [rule({ action: 'NONE' })], sourceFieldRules: [rule()] }) })
  assert.equal(context.fieldRules.value[0].action, 'INCREMENT')
  context.loadHistoryItem({ requestJson: '{}' })
  assert.equal(context.fieldRules.value.length, 0)
})

test('validation rejects duplicate paths, malformed escapes, non-JSON bodies and overlapping ranges', () => {
  const context = editor()
  context.activeFieldRules.value = [rule(), rule({ id: 'two' })]
  assert.match(context.fieldRuleValidationMessage(), /不可設定多條/)
  context.activeFieldRules.value = [rule({ path: '/bad~2key' })]
  assert.match(context.fieldRuleValidationMessage(), /跳脫格式/)
  context.activeFieldRules.value = [rule()]
  context.bodyType.value = 'raw'
  assert.match(context.fieldRuleValidationMessage(), /只支援 JSON/)
  context.requestType.value = 'GRPC'
  context.activeFieldRules.value = [rule({ targetType: 'GRPC_PAYLOAD_DATA_RANGE' }),
    rule({ id: 'two', targetType: 'GRPC_PAYLOAD_DATA_RANGE', start: 4, end: 7 })]
  assert.match(context.fieldRuleValidationMessage(), /不可重疊/)
})

for (const type of ['HTTP', 'GRPC', 'GRPC_BUR']) {
  test(`${type} Batch opt-out sends an empty snapshot without mutating source rules`, () => {
    const context = editor()
    context.requestType.value = type
    context.activeFieldRules.value = [rule()]
    context.batchUseFieldRules.value = false
    const payload = context.batchExecutePayload()
    assert.equal((type === 'GRPC_BUR' ? payload.settings.fieldRules : payload.fieldRules).length, 0)
    assert.equal(context.activeFieldRules.value.length, 1)
    context.batchUseFieldRules.value = true
    const enabled = context.batchExecutePayload()
    assert.equal((type === 'GRPC_BUR' ? enabled.settings.fieldRules : enabled.fieldRules).length, 1)
  })
}

test('gRPC highlighting supports multiple UTF-8 ranges without changing JSON text', () => {
  const context = editor()
  const rules = [rule({ targetType: 'GRPC_PAYLOAD_DATA_RANGE', start: 4, end: 7 }),
    rule({ id: 'two', targetType: 'GRPC_PAYLOAD_DATA_RANGE', start: 8, end: 11, action: 'NONE' })]
  context.requestType.value = 'GRPC'
  context.grpcEncodePayloadDataBase64.value = true
  context.activeFieldRules.value = rules
  context.activeBodyText.value = JSON.stringify({ payload: { data: '繁0009<>&"' } })
  const html = context.highlightRequestBody()
  assert.equal((html.match(/class="json-field-rule"/g) || []).length, 2)
  assert.match(html, /&lt;&gt;&amp;/)
  assert.match(html, /BASE64/)
  assert.match(html, /0009/)
  context.activeBodyText.value = JSON.stringify({ payload: { data: '繁0009' } })
  context.activeFieldRules.value = [rule({ targetType: 'GRPC_PAYLOAD_DATA_RANGE', start: 2, end: 4 })]
  const split = context.highlightRequestBody()
  assert.ok(!split.includes('class="json-field-rule"'))
})

test('HTTP highlighting uses canonical JSON Pointer rather than ambiguous dotted keys', () => {
  const context = editor()
  context.activeBodyText.value = JSON.stringify({ 'a.b': { 'x/y': '0099' }, a: { b: 'not highlighted' } })
  context.activeFieldRules.value = [rule({ path: '/a.b/x~1y' })]
  const html = context.highlightRequestBody()
  assert.equal((html.match(/class="json-field-rule"/g) || []).length, 1)
  assert.match(html, /0099/)
})

function sourceText(html) {
  return html.replace(/<span class="json-(?:field-rule-badge|base64-send-badge)">.*?<\/span>/g, '')
    .replace(/<[^>]*>/g, '').replaceAll('&quot;', '"').replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&')
}

test('Request overlay preserves compact JSON, arbitrary whitespace, escapes, numeric spelling and trailing lines', () => {
  const context = editor()
  for (const value of ['{"payload":{"id":"0009"}}', ' \t{\r\n\t"a": 1.00e+2, "b": "\\u7e41\\n<>&"\r\n}\n\n',
    '{"id":9007199254740993,"id":2}', '[ 1, true, null ]\n', '{"incomplete": ', '']) {
    context.activeBodyText.value = value
    assert.equal(sourceText(context.highlightRequestBody()), value)
  }
})

test('source-based HTTP and gRPC rule/Base64 badges never change the underlying JSON text', () => {
  const context = editor()
  context.activeBodyText.value = '{ "a.b" : {"x/y":"\\u00300009"} }\n'
  context.activeFieldRules.value = [rule({ path: '/a.b/x~1y' })]
  const http = context.highlightRequestBody()
  assert.equal(sourceText(http), context.activeBodyText.value)
  assert.match(http, /class="json-field-rule"/)
  context.requestType.value = 'GRPC'
  context.grpcEncodePayloadDataBase64.value = true
  context.activeBodyText.value = '{"payload" : {"data" : "\\u7e41\\u0030000900"}}\n\n'
  context.activeFieldRules.value = [rule({ targetType: 'GRPC_PAYLOAD_DATA_RANGE', start: 4, end: 7 }),
    rule({ id: 'day', targetType: 'GRPC_PAYLOAD_DATA_RANGE', start: 8, end: 9 })]
  const grpc = context.highlightRequestBody()
  assert.equal(sourceText(grpc), context.activeBodyText.value)
  assert.equal((grpc.match(/class="json-field-rule"/g) || []).length, 2)
  assert.match(grpc, /BASE64/)
})

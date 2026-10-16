import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { mergePreparedValues, prepareFieldRuleDispatch } from './request-preparation.mjs'

const rule = (targetType, path = '/id') => ({ id: 'seq', name: '序號', enabled: true, action: 'INCREMENT', color: '#AB005F', targetType, path, start: 1, end: 4 })
const app = await readFile(new URL('./App.vue', import.meta.url), 'utf8')
function install(context, names) {
  vm.createContext(context)
  for (const name of names) {
    const start = app.search(new RegExp(`^(?:async )?function ${name}\\(`, 'm'))
    const remaining = app.slice(start)
    const end = remaining.slice(1).search(/^(?:async )?function \w+\(/m)
    vm.runInContext(remaining.slice(0, end + 1), context)
  }
  return context
}

test('HTTP merges only prepared target values, preserving templates, formatting and large integers', async () => {
  const source = '{ "id":"{{seq}}", "other":"{{keep}}", "large":9007199254740993 }\n'
  const payload = { body: '{"id":"0009","other":"secret","large":9007199254740993}', fieldRules: [rule('HTTP_JSON_VALUE')] }
  const result = await prepareFieldRuleDispatch({ type: 'HTTP', payload, sourceBody: source,
    prepare: async (path, request) => {
      assert.equal(path, '/api/http/prepare')
      assert.equal(request.fieldRuleIncrement, 1)
      return { body: '{"id":"0010","other":"secret","large":9007199254740993}' }
    } })
  assert.equal(result.body, '{ "id":"0010", "other":"{{keep}}", "large":9007199254740993 }\n')
  assert.equal(result.executionPayload.fieldRules[0].action, 'NONE')
  assert.equal(result.executionPayload.sourceFieldRules[0].action, 'INCREMENT')
  assert.equal(payload.fieldRules[0].action, 'INCREMENT')
  assert.equal(result.payload.body, payload.body)
})

test('gRPC keeps Base64 enabled for one server encoding and replaces only payload.data', async () => {
  const result = await prepareFieldRuleDispatch({ type: 'GRPC', sourceBody: '{"payload":{"data":"{{data}}"},"token":"{{secret}}"}',
    payload: { body: '{"payload":{"data":"0009"},"token":"secret"}', encodePayloadDataBase64: true,
      fieldRules: [rule('GRPC_PAYLOAD_DATA_RANGE')] },
    prepare: async () => ({ body: '{"payload":{"data":"0010"},"token":"secret"}' }) })
  assert.equal(result.body, '{"payload":{"data":"0010"},"token":"{{secret}}"}')
  assert.equal(result.executionPayload.encodePayloadDataBase64, true)
  assert.equal(result.executionPayload.fieldRules[0].action, 'NONE')
})

test('BUR reserves N via preview while retaining original Batch snapshot and legacy override', async () => {
  const payload = { basicLabel: '0009', settings: { fieldRules: [rule('BUR_BASIC_LABEL_RANGE')], incrementBasicLabelSequence: true } }
  const result = await prepareFieldRuleDispatch({ type: 'GRPC_BUR', payload, increment: 3,
    prepare: async (path, request) => {
      assert.equal(path, '/api/grpc-bur/preview')
      assert.equal(request.basicLabelSequenceIncrement, 3)
      return { effectiveBasicLabel: '0012' }
    } })
  assert.equal(result.basicLabel, '0012')
  assert.equal(result.executionPayload.settings.fieldRules[0].action, 'NONE')
  assert.equal(result.payload.basicLabel, '0009')
  assert.equal(result.payload.settings.fieldRules[0].action, 'INCREMENT')
})

test('empty/mark-only rules do not prepare or mutate; errors leave source unchanged', async () => {
  for (const rules of [[], [rule('HTTP_JSON_VALUE', '/id')].map((value) => ({ ...value, action: 'NONE' }))]) {
    const payload = { body: '{"id":"0009"}', fieldRules: rules }
    const result = await prepareFieldRuleDispatch({ type: 'HTTP', payload, prepare: () => assert.fail('must not prepare') })
    assert.equal(result.changed, false)
    assert.equal(result.executionPayload, payload)
  }
  await assert.rejects(() => prepareFieldRuleDispatch({ type: 'HTTP', payload: { fieldRules: [rule('HTTP_JSON_VALUE')] },
    prepare: async () => { throw new Error('overflow') } }), /overflow/)
})

test('source updates support escaped Pointer keys, integer precision and whole Body templates', () => {
  assert.equal(mergePreparedValues('{"a/b":{"~id":9007199254740993},"keep":"{{keep}}"}',
    '{"a/b":{"~id":9007199254740994}}', ['/a~1b/~0id']), '{"a/b":{"~id":9007199254740994},"keep":"{{keep}}"}')
  assert.equal(mergePreparedValues('{{body}}', '{"id":2}', ['/id']), '{"id":2}')
})

test('actual preparation workflow rejects edits or Environment switches while waiting', async () => {
  for (const changeEnvironment of [false, true, 'variables']) {
    let finish
    const pending = new Promise((resolve) => { finish = resolve })
    const context = {
      prepareFieldRuleDispatch, requestType: { value: 'HTTP' }, activeEnvironmentId: { value: 'local' },
      activeEnvironment: { value: { variables: [{ key: 'host', value: 'old' }] } },
      bodyText: { value: '{"id":"0009"}' }, grpcBodyText: { value: '' },
      apiJson: () => pending,
    }
    context.snapshotEditorState = () => context.bodyText.value
    context.batchExecutePayloadForRules = () => ({ body: context.bodyText.value, fieldRules: [rule('HTTP_JSON_VALUE')] })
    install(context, ['prepareCurrentFieldRules', 'executionEnvironmentState'])
    const prepared = context.prepareCurrentFieldRules()
    if (changeEnvironment === 'variables') context.activeEnvironment.value.variables[0].value = 'new'
    else if (changeEnvironment) context.activeEnvironmentId.value = 'sit'
    else context.bodyText.value = 'new edit'
    finish({ body: '{"id":"0010"}' })
    await assert.rejects(prepared, /已變更/)
    assert.equal(context.bodyText.value, changeEnvironment ? '{"id":"0009"}' : 'new edit')
  }
})

function batchEditor(type, { useRules = true, failSave = false, failStart = false } = {}) {
  const events = []
  const context = {
    nextTick: async () => {}, fieldRuleValidationMessage: () => '', readableError: (error) => error.message,
    batchApiBase: () => '/batch', batchRequestProperty: (payload) => ({ request: payload }),
    uiValue: '0009',
    async prepareCurrentFieldRules(increment, enabled) {
      assert.equal(increment, 3)
      assert.equal(enabled, useRules)
      return { changed: enabled, payload: { body: '0009', fieldRules: enabled ? [rule('HTTP_JSON_VALUE')] : [] } }
    },
    applyPreparedFieldRules: (prepared) => { if (prepared.changed) context.uiValue = '0012' },
    async saveRequest() { events.push('save'); assert.equal(context.uiValue, '0012'); return !failSave },
    async apiJson(path, options) {
      events.push(JSON.parse(options.body))
      assert.equal(context.uiValue, useRules ? '0012' : '0009')
      if (failStart) throw new Error('start failed')
      return { id: 1, totalCount: 3 }
    },
    async loadBatchRunHistory() {}, startBatchPolling: () => events.push('poll'),
  }
  for (const [name, value] of Object.entries({ batchStarting: false, preparingRequest: false, saving: false, sending: false,
    batchUseFieldRules: useRules, batchStartError: '', selectedRequestId: 7, batchTotalCount: 3, batchMode: 'CONCURRENCY',
    batchMaxConcurrency: 2, batchIntervalMillis: 0, batchDeadlineMillis: 1000, hasUnsavedChanges: false,
    batchRun: null, batchItems: [], batchItemsPage: 0, batchItemsTotal: 0, selectedBatchItemId: null,
    batchRunHistoryPage: 0, batchRunHistory: [], batchRunHistoryTotal: 0, showBatchRunner: true,
    activeResponseTab: 'body', workspaceStatus: '', batchProtocolLabel: type, requestType: type })) context[name] = { value }
  install(context, ['startBatchRun', 'validateBatchConfiguration'])
  return { context, events }
}

for (const type of ['HTTP', 'GRPC', 'GRPC_BUR']) {
  test(`${type} Batch updates/saves the final reserved value before starting the original snapshot`, async () => {
    const { context, events } = batchEditor(type)
    await context.startBatchRun()
    assert.equal(events[0], 'save')
    assert.equal(events[1].request.body, '0009')
    assert.equal(events[1].request.fieldRules[0].action, 'INCREMENT')
    assert.equal(context.uiValue, '0012')
  })
}

test('Batch opt-out does not reserve/save; failed save/start never rolls back reserved values', async () => {
  const off = batchEditor('HTTP', { useRules: false })
  await off.context.startBatchRun()
  assert.equal(off.events.includes('save'), false)
  assert.equal(off.context.uiValue, '0009')
  for (const options of [{ failSave: true }, { failStart: true }]) {
    const { context, events } = batchEditor('GRPC_BUR', options)
    await context.startBatchRun()
    assert.equal(context.uiValue, '0012')
    assert.equal(events.includes('poll'), false)
    assert.ok(context.batchStartError.value)
  }
})

test('invalid Batch settings are rejected before reserving or saving values', async () => {
  for (const values of [{ batchTotalCount: 2147483648 }, { batchMaxConcurrency: 0 },
    { batchMode: 'RESPONSE_INTERVAL', batchIntervalMillis: '' }]) {
    const { context, events } = batchEditor('HTTP')
    for (const [name, value] of Object.entries(values)) context[name].value = value
    await context.startBatchRun()
    assert.equal(context.uiValue, '0009')
    assert.equal(events.length, 0)
    assert.ok(context.batchStartError.value)
  }
})

test('Batch dispatch freezes the same count used for reservation while preparing', async () => {
  const { context, events } = batchEditor('HTTP')
  const prepare = context.prepareCurrentFieldRules
  context.prepareCurrentFieldRules = async (...args) => {
    const result = await prepare(...args)
    context.batchTotalCount.value = 5
    return result
  }
  await context.startBatchRun()
  assert.equal(events[1].totalCount, 3)
  assert.equal(context.uiValue, '0012')
})

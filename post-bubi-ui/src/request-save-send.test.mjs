import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import vm from 'node:vm'

const source = await readFile(new URL('./App.vue', import.meta.url), 'utf8')

// Exercise the actual editor workflow with API responses controlled by each test.
function editor(options = {}) {
  const calls = []
  const context = {
    saving: { value: false }, sending: { value: false },
    selectedCollectionId: { value: 2 }, selectedFolderId: { value: 3 },
    selectedRequestId: { value: options.requestId ?? 7 },
    requestName: { value: '測試' }, requestType: { value: options.type || 'HTTP' },
    activeResponseTab: { value: 'headers' },
    errorText: { value: '' }, cancellationText: { value: '已取消' },
    workspaceStatus: { value: '' }, savedEditorState: { value: '' },
    collections: { value: [{ id: 2, requests: [{ id: 7, sortOrder: 9 }] }] },
    payload: { body: '{{message}}', headers: [{ name: 'X-Test', value: 'saved' }] },
    readableError: (error) => error.message,
    cacheCurrentResponse: () => {},
    async apiJson(path, request) {
      calls.push({ path, ...request })
      if (options.saveError) throw new Error(options.saveError)
      if (options.onSave) await options.onSave(context)
      return { id: 11 }
    },
    async loadCollections() {},
    async sendHttpRequest() { calls.push({ execute: 'HTTP', requestId: context.selectedRequestId.value }) },
    async sendGrpcRequest() { calls.push({ execute: 'GRPC' }) },
    async sendGrpcBurRequest() { calls.push({ execute: 'GRPC_BUR' }) },
  }
  Object.defineProperty(context, 'hasUnsavedChanges', {
    get: () => ({ value: context.savedEditorState.value !== context.snapshotEditorState() }),
  })
  context.editorPayload = () => context.payload
  vm.createContext(context)
  for (const name of ['snapshotEditorState', 'saveRequest', 'sendCurrentRequest']) {
    const start = source.search(new RegExp(`^(?:async )?function ${name}\\(`, 'm'))
    assert.ok(start >= 0, `${name} must exist`)
    const remaining = source.slice(start)
    const end = remaining.slice(1).search(/^(?:async )?function \w+\(/m)
    assert.ok(end >= 0)
    vm.runInContext(remaining.slice(0, end + 1), context)
  }
  return { context, calls }
}

for (const type of ['HTTP', 'GRPC', 'GRPC_BUR']) {
  test(`${type} saves current content before execution and keeps request ordering`, async () => {
    const { context, calls } = editor({ type })
    await context.sendCurrentRequest()
    assert.equal(calls[0].method, 'PUT')
    assert.equal(calls[0].path, '/api/requests/7')
    const saved = JSON.parse(calls[0].body)
    assert.equal(saved.sortOrder, 9)
    assert.equal(JSON.parse(saved.payloadJson).body, '{{message}}')
    assert.equal(calls[1].execute, type)
    assert.equal(context.hasUnsavedChanges.value, false)
    assert.equal(context.activeResponseTab.value, 'body')
    assert.equal(context.cancellationText.value, '')
  })
}

test('new request is created in selected collection/folder before HTTP execution', async () => {
  const { context, calls } = editor({ requestId: 0 })
  await context.sendCurrentRequest()
  assert.equal(calls[0].path, '/api/requests')
  const saved = JSON.parse(calls[0].body)
  assert.equal(saved.collectionId, 2)
  assert.equal(saved.folderId, 3)
  assert.equal(calls[1].requestId, 11)
  assert.equal(context.hasUnsavedChanges.value, false)
})

test('save failures prevent execution and retain dirty input', async () => {
  const { context, calls } = editor({ saveError: 'database unavailable' })
  await context.sendCurrentRequest()
  assert.equal(calls.length, 1)
  assert.match(context.errorText.value, /database unavailable/)
  assert.equal(context.hasUnsavedChanges.value, true)
  assert.equal(context.saving.value, false)
})

test('missing collection and blank request name prevent saving and sending', async () => {
  for (const options of [{ noCollection: true }, { blankName: true }]) {
    const { context, calls } = editor(options)
    if (options.noCollection) context.selectedCollectionId.value = null
    if (options.blankName) context.requestName.value = '  '
    await context.sendCurrentRequest()
    assert.equal(calls.length, 0)
    assert.match(context.errorText.value, /Request 未儲存/)
  }
})

test('edits made while saving remain dirty and prevent unsaved execution', async () => {
  const { context, calls } = editor({ onSave: async (state) => { state.payload.body = 'new edit' } })
  await context.sendCurrentRequest()
  assert.equal(calls.length, 1)
  assert.equal(context.hasUnsavedChanges.value, true)
  assert.match(context.errorText.value, /內容已變更/)
})

test('repeated Send during pending save produces one save and one execution', async () => {
  let completeSave
  const pending = new Promise((resolve) => { completeSave = resolve })
  const { context, calls } = editor({ onSave: () => pending })
  const first = context.sendCurrentRequest()
  await context.sendCurrentRequest()
  assert.equal(calls.length, 1)
  completeSave()
  await first
  assert.equal(calls.length, 2)
})

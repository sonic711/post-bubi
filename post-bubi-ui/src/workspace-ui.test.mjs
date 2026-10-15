import assert from 'node:assert/strict'
import test from 'node:test'
import { copyText, filterCollections, handleTabKeydown } from './workspace-ui.mjs'

const collections = [{
  id: 1, name: 'Payment APIs',
  folders: [
    { id: 10, name: 'SIT', parentFolderId: null },
    { id: 11, name: 'Accounts', parentFolderId: 10 },
    { id: 12, name: 'Unused', parentFolderId: null },
  ],
  requests: [
    { id: 1, name: '健康檢查', folderId: null, type: 'HTTP' },
    { id: 2, name: '查詢帳戶', folderId: 11, type: 'GRPC' },
    { id: 3, name: '交易電文', folderId: 10, type: 'GRPC_BUR' },
  ],
}, { id: 2, name: 'Empty', folders: [], requests: [] }]

test('empty search returns the original data without changing order', () => {
  assert.equal(filterCollections(collections, '  '), collections)
})

test('collection name match retains the complete collection, case-insensitively', () => {
  assert.equal(filterCollections(collections, 'PAYMENT')[0], collections[0])
})

test('request match includes its complete folder ancestor path', () => {
  const [result] = filterCollections(collections, '帳戶')
  assert.deepEqual(result.requests.map((r) => r.id), [2])
  assert.deepEqual(result.folders.map((f) => f.id), [10, 11])
})

test('folder name match includes descendants and their requests', () => {
  const [result] = filterCollections(collections, 'sit')
  assert.deepEqual(result.requests.map((r) => r.id), [2, 3])
  assert.deepEqual(result.folders.map((f) => f.id), [10, 11])
})

test('protocol search uses the saved request type and does not mutate source', () => {
  const before = JSON.stringify(collections)
  const [result] = filterCollections(collections, 'grpc')
  assert.deepEqual(result.requests.map((r) => r.id), [2, 3])
  assert.equal(JSON.stringify(collections), before)
  assert.deepEqual(filterCollections(collections, 'not found'), [])
})

test('missing folder references and cycles do not cause unbounded ancestor traversal', () => {
  const source = [{ name: '', requests: [{ name: 'match', folderId: 1 }], folders: [
    { id: 1, parentFolderId: 2 }, { id: 2, parentFolderId: 1 },
  ] }]
  assert.equal(filterCollections(source, 'match')[0].folders.length, 2)
})

test('tabs support arrows, wrapping, Home/End and leave unrelated keys alone', () => {
  let focused = -1
  let clicked = -1
  let prevented = false
  const tabs = [0, 1, 2].map((index) => ({
    focus: () => { focused = index }, click: () => { clicked = index }, scrollIntoView() {},
  }))
  for (const [key, index, expected] of [['ArrowRight', 2, 0], ['ArrowLeft', 0, 2], ['Home', 1, 0], ['End', 1, 2]]) {
    prevented = false
    handleTabKeydown({ key, target: tabs[index], currentTarget: { querySelectorAll: () => tabs }, preventDefault: () => { prevented = true } })
    assert.equal(focused, expected)
    assert.equal(clicked, expected)
    assert.equal(prevented, true)
  }
  prevented = false
  handleTabKeydown({ key: 'Tab', target: tabs[0], currentTarget: { querySelectorAll: () => tabs }, preventDefault: () => { prevented = true } })
  assert.equal(prevented, false)
})

test('clipboard API supports Unicode and exact multiline text', async (context) => {
  const text = '{\n  "資料": "Post Bubi"\n}'
  let copied
  replaceGlobal(context, 'navigator', { clipboard: { async writeText(value) { copied = value } } })
  await copyText(text)
  assert.equal(copied, text)
})

for (const reject of [true, false]) {
  test(`clipboard fallback cleans up and restores focus (success=${!reject})`, async (context) => {
    let selected = false
    let removed = false
    let restored = false
    const target = { style: {}, setAttribute() {}, select() { selected = true }, remove() { removed = true } }
    replaceGlobal(context, 'navigator', { clipboard: { async writeText() { throw new Error('denied') } } })
    replaceGlobal(context, 'document', {
      activeElement: { focus() { restored = true } },
      createElement: () => target,
      body: { appendChild() {} },
      execCommand: () => !reject,
    })
    if (reject) await assert.rejects(copyText('copy me'), /剪貼簿/)
    else await copyText('copy me')
    assert.equal(target.value, 'copy me')
    assert.ok(selected && removed && restored)
  })
}

function replaceGlobal(context, name, value) {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, name)
  Object.defineProperty(globalThis, name, { configurable: true, value })
  context.after(() => {
    if (descriptor) Object.defineProperty(globalThis, name, descriptor)
    else delete globalThis[name]
  })
}

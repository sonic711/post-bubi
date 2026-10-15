import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { mkdir } from 'node:fs/promises'

assert.equal(process.env.UIUX_TEST_DATABASE, 'isolated', 'Use a separate test database')
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const base = process.env.UIUX_BASE_URL || 'http://127.0.0.1:18081'
const output = '/tmp/post-bubi-field-rules-screenshots'
await mkdir(output, { recursive: true })
const received = []
const receiver = createServer(async (request, response) => {
  const chunks = []
  for await (const chunk of request) chunks.push(chunk)
  const body = JSON.parse(Buffer.concat(chunks).toString())
  received.push(body)
  response.setHeader('Content-Type', 'application/json')
  response.end(JSON.stringify(body))
})
await new Promise((resolve) => receiver.listen(0, '127.0.0.1', resolve))
async function api(path, body, method = 'POST') {
  const result = await fetch(`${base}/api${path}`, { method, headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body) })
  assert.ok(result.ok, `${path}: ${await result.clone().text()}`)
  return result.status === 204 ? null : result.json()
}
const browser = await chromium.launch(process.env.CHROME_EXECUTABLE ? { executablePath: process.env.CHROME_EXECUTABLE } : {})
let collection
const errors = []
const checks = []
try {
  collection = await api('/collections', { name: '欄位規則還原驗證' })
  const body = JSON.stringify({ payload: { id: '0009', day: '00', unchanged: '固定內容' } }, null, 2)
  const http = await api('/requests', { collectionId: collection.id, type: 'HTTP', name: 'HTTP 欄位規則',
    payloadJson: JSON.stringify({ requestType: 'HTTP', method: 'POST', url: `http://127.0.0.1:${receiver.address().port}`,
      bodyType: 'json', body, headers: [], timeoutMillis: 30000 }) })
  await api('/requests', { collectionId: collection.id, type: 'GRPC', name: 'gRPC BUR 回應',
    payloadJson: JSON.stringify({ requestType: 'GRPC', grpcHost: '127.0.0.1', grpcPort: '50051',
      grpcServiceName: 'demo.Service', grpcMethodName: 'Echo', grpcBody: '{"payload":{"data":"繁000900"}}',
      fieldRules: [{ id: 'seq', name: '序號', enabled: true, color: '#AB005F', action: 'INCREMENT', targetType: 'GRPC_PAYLOAD_DATA_RANGE', start: 4, end: 7 },
        { id: 'day', name: '交易日', enabled: true, color: '#3484D4', action: 'TODAY_DD', targetType: 'GRPC_PAYLOAD_DATA_RANGE', start: 8, end: 9 }] }) })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('dialog', (dialog) => dialog.accept())
  await page.goto(base)
  await page.getByRole('button', { name: 'HTTP 欄位規則', exact: true }).click()
  const requestTab = (name) => page.getByRole('tablist', { name: 'Request 分頁' }).getByRole('tab', { name, exact: true })
  const responseTab = (name) => page.getByRole('tablist', { name: 'Response 分頁' }).getByRole('tab', { name, exact: true })
  await requestTab('Settings').click()
  for (const [name, action, path, color] of [['交易序號', 'INCREMENT', '/payload/id', '#AB005F'],
    ['交易日', 'TODAY_DD', '/payload/day', '#3484D4'], ['固定內容', 'NONE', '/payload/unchanged', '#2E9B6B']]) {
    await page.getByRole('button', { name: '新增欄位規則', exact: true }).click()
    const row = page.locator('.field-rule-row').last()
    await row.getByLabel('規則名稱', { exact: true }).fill(name)
    await row.getByLabel('規則動作', { exact: true }).selectOption(action)
    await row.getByLabel('HTTP JSON Pointer', { exact: true }).fill(path)
    await row.getByLabel('規則顏色', { exact: true }).fill(color)
  }
  assert.equal(await page.locator('.field-rule-error').count(), 0)
  await page.screenshot({ path: `${output}/settings-desktop.png` })
  await page.getByRole('button', { name: '送出', exact: true }).click()
  await page.locator('.response-summary.ok').waitFor()
  assert.equal(received.at(-1).payload.id, '0010')
  assert.equal(received.at(-1).payload.day, new Intl.DateTimeFormat('en', { day: '2-digit', timeZone: 'Asia/Taipei' }).format(new Date()))
  let saved = JSON.parse((await api(`/requests/${http.id}`, undefined, 'GET')).payloadJson)
  assert.equal(saved.body, body)
  assert.equal(saved.fieldRules.length, 3)
  await requestTab('Body').click()
  assert.equal(await page.locator('.json-field-rule').count(), 3)
  checks.push('HTTP Settings CRUD, custom colors, actual increment/DD, autosave preserves original Body')

  const duplicate = await api(`/requests/${http.id}/duplicate`, {})
  assert.deepEqual(JSON.parse(duplicate.payloadJson).fieldRules, saved.fieldRules)
  const zip = await fetch(`${base}/api/requests/${http.id}/export`)
  assert.ok(zip.ok)
  const form = new FormData()
  form.append('file', new Blob([await zip.arrayBuffer()], { type: 'application/zip' }), 'request.zip')
  const imported = await fetch(`${base}/api/workspace/import?targetCollectionId=${collection.id}`, { method: 'POST', body: form })
  assert.ok(imported.ok, await imported.text())
  const collections = await api('/collections', undefined, 'GET')
  const requests = collections.find((item) => item.id === collection.id).requests
  assert.equal(requests.filter((item) => JSON.parse(item.payloadJson).fieldRules?.length === 3).length, 3)
  checks.push('Request duplicate and single Request ZIP export/import preserve rules')

  const runBatch = async (useRules) => {
    const before = received.length
    await page.getByRole('button', { name: 'HTTP 批次執行', exact: true }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel('總筆數', { exact: true }).fill('2')
    await dialog.getByLabel('套用欄位規則', { exact: false }).setChecked(useRules)
    const started = page.waitForResponse((response) => response.url().endsWith('/api/http/batch-runs') && response.request().method() === 'POST')
    await dialog.getByRole('button', { name: '開始批次', exact: true }).click()
    await started
    for (let attempt = 0; attempt < 100 && received.length - before < 2; attempt++) {
      await new Promise((resolve) => setTimeout(resolve, 50))
    }
    await page.locator('.batch-results-head .batch-status-completed').waitFor()
    assert.equal(received.length - before, 2)
    assert.deepEqual(received.slice(before).map((item) => item.payload.id).sort(), useRules ? ['0010', '0011'] : ['0009', '0009'])
  }
  await runBatch(true)
  await runBatch(false)
  checks.push('Actual HTTP Batch sequence increments and opt-out')

  await page.getByRole('button', { name: 'gRPC BUR 回應', exact: true }).click()
  await requestTab('Body').click()
  assert.equal(await page.locator('.json-field-rule').count(), 2)
  // Transport conversion is covered by Java integration tests; here verify the actual UI renders its DTO.
  await page.route('**/api/grpc/execute', (route) => route.fulfill({ json: {
    statusCode: 'OK', durationMillis: 12, metadata: [], errorMessage: '',
    body: JSON.stringify({ payload: { 0: { charsets: 'BUR', format: 'TEXT', data: 'K9z59tyukryiLA==' },
      1: { charsets: 'BUR', data: '!invalid!' } } }),
    decodedPayloads: [{ key: '0', charsets: 'BUR', format: 'TEXT', length: 10, text: '繁體測試', error: '' },
      { key: '1', charsets: 'BUR', format: '', length: 0, text: '', error: 'BUR payload 解碼失敗' }],
  } }))
  await page.getByRole('button', { name: '送出', exact: true }).click()
  await page.locator('.response-summary.ok').waitFor()
  assert.match(await page.locator('.response-content .json-viewer').textContent(), /繁體測試/)
  assert.match(await page.locator('.response-content .json-viewer').textContent(), /!invalid!/)
  assert.equal(await page.locator('.json-decoded-field').getAttribute('title'), '原始 base64: K9z59tyukryiLA==')
  await responseTab('Decoded').click()
  assert.equal(await page.locator('.auto-bur-decoded-result-list .decoded-result').count(), 2)
  assert.match(await page.locator('.auto-bur-decoded-result-list').textContent(), /繁體測試/)
  await page.screenshot({ path: `${output}/grpc-decoded-desktop.png` })
  checks.push('gRPC multi-range highlight, BUR Body replacement, raw tooltip and individual Decoded errors')

  await requestTab('Settings').click()
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport)
    for (const theme of ['Light', 'Dark']) {
      await page.getByRole('button', { name: `${theme} theme`, exact: true }).click()
      await page.locator('.field-rule-row').first().scrollIntoViewIfNeeded()
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
      const clipped = await page.locator('.field-rule-row input, .field-rule-row select').evaluateAll((elements) =>
        elements.filter((element) => { const rect = element.getBoundingClientRect(); const row = element.closest('article').getBoundingClientRect();
          return rect.right > row.right + 1 || rect.left < row.left - 1 }).length)
      assert.equal(clipped, 0)
      await page.screenshot({ path: `${output}/rules-${theme.toLowerCase()}-${viewport.width}.png` })
    }
  }
  checks.push('Custom rule controls fit desktop/mobile Light/Dark layouts')
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ passed: checks, errors, screenshots: output }, null, 2))
} catch (error) {
  await browser.contexts().at(-1)?.pages()[0]?.screenshot({ path: `${output}/failure.png`, fullPage: true })
  console.error(JSON.stringify({ completed: checks, errors }))
  throw error
} finally {
  await browser.close()
  if (collection) await api(`/collections/${collection.id}`, undefined, 'DELETE')
  await new Promise((resolve) => receiver.close(resolve))
}

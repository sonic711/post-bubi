import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'

assert.equal(process.env.UIUX_TEST_DATABASE, 'isolated', 'Use an isolated backend')
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const base = process.env.UIUX_BASE_URL || 'http://127.0.0.1:18081'
const output = '/tmp/post-bubi-bur-layout-screenshots'
await mkdir(output, { recursive: true })
async function api(path, body, method = 'POST') {
  const response = await fetch(`${base}/api${path}`, { method, headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body) })
  assert.ok(response.ok, `${path}: ${await response.clone().text()}`)
  return response.status === 204 ? null : response.json()
}
const browser = await chromium.launch(process.env.CHROME_EXECUTABLE ? { executablePath: process.env.CHROME_EXECUTABLE } : {})
const errors = []
const checks = []
let collection
let environment
try {
  collection = await api('/collections', { name: 'TOTA BOX 組包驗收' })
  environment = await api('/environments', { name: 'TOTA BOX 變數', variables: [{ key: 'box', value: '00 01 02 03 04 05' }] })
  const samples = [
    { name: 'A98 FIX', label: '983029400020734AU07000001 A98100500', text: 'A98  0001002222', length: 68 },
    { name: 'A99 FIX', label: '981045800001372AI07000001 A99100410', text: 'A99  2', length: 59 },
  ]
  for (const sample of samples) {
    sample.request = await api('/requests', { collectionId: collection.id, type: 'GRPC_BUR', name: sample.name,
      payloadJson: JSON.stringify({ requestType: 'GRPC_BUR', grpcBurHost: '127.0.0.1', grpcBurPort: '1', fieldRules: [] }) })
  }
  const legacy = await api('/requests', { collectionId: collection.id, type: 'GRPC_BUR', name: '舊 MCS 測案',
    payloadJson: JSON.stringify({ requestType: 'GRPC_BUR', grpcBurBasicLabel: '0'.repeat(158), grpcBurTextArea: 'OLD' }) })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('dialog', (dialog) => dialog.accept())
  const tab = (name) => page.getByRole('tablist', { name: 'Request 分頁' }).getByRole('tab', { name, exact: true }).click()
  const saved = async (request) => JSON.parse((await api(`/requests/${request.id}`, undefined, 'GET')).payloadJson)
  await page.goto(base)
  for (const sample of samples) {
    await page.getByRole('button', { name: sample.name, exact: true }).click()
    await tab('Settings')
    assert.equal(await page.getByLabel('BUR 電文結構', { exact: true }).inputValue(), 'MCS')
    assert.equal(await page.getByLabel('依電文總 bytes 自動更新 TCPIP 長度', { exact: true }).isChecked(), false)
    await page.getByLabel('BUR 電文結構', { exact: true }).selectOption('TOTA_BOX')
    await page.getByLabel('TOTA BOX Hex', { exact: true }).fill('00 00 00 00 00 00')
    await page.getByLabel('TCPIP Header Hex', { exact: true }).fill('0F 0F 0F 00 06 12 01 00 22 23 1F 0F')
    await page.getByLabel('Basic Label Length', { exact: true }).fill('35')
    await page.getByLabel('Text Area Length', { exact: true }).fill('0')
    await page.getByLabel('依電文總 bytes 自動更新 TCPIP 長度', { exact: true }).check()
    await tab('Body')
    await page.getByLabel('Basic Label', { exact: true }).fill(sample.label)
    await page.getByLabel('Text Area', { exact: true }).fill(sample.text)
    await page.getByRole('button', { name: '產生預覽', exact: true }).click()
    await page.waitForFunction(() => document.querySelector('.bur-preview pre')?.textContent.includes('"messageLayout": "TOTA_BOX"'))
    const preview = JSON.parse(await page.locator('.bur-preview pre').textContent())
    assert.equal(preview.payloadLength, sample.length)
    assert.equal(preview.mcsHeaderLength, 0)
    assert.equal(preview.totaBoxLength, 6)
    assert.ok(preview.payloadHex.startsWith(`0F 0F 0F 00 00 ${sample.length} 01 00 22 23 1F 0F 00 00 00 00 00 00`))
    assert.ok(preview.decodedText.endsWith(sample.label + sample.text))
    const stored = page.waitForResponse((response) => response.url().endsWith(`/api/requests/${sample.request.id}`)
      && response.request().method() === 'PUT')
    await page.getByRole('button', { name: '儲存 Request', exact: true }).click()
    assert.ok((await stored).ok())
    assert.equal((await saved(sample.request)).grpcBurMessageLayout, 'TOTA_BOX')
    checks.push(`${sample.name}: UI configuration and exact ${sample.length}-byte preview without MCS`)
  }
  await page.reload()
  await page.getByRole('button', { name: 'A98 FIX', exact: true }).click()
  await tab('Settings')
  assert.equal(await page.getByLabel('BUR 電文結構', { exact: true }).inputValue(), 'TOTA_BOX')
  assert.equal(await page.getByLabel('依電文總 bytes 自動更新 TCPIP 長度', { exact: true }).isChecked(), true)
  await page.getByLabel('目前 Environment', { exact: true }).selectOption(String(environment.id))
  await page.getByLabel('TOTA BOX Hex', { exact: true }).fill('{{box}}')
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport)
    for (const theme of ['Light', 'Dark']) {
      await page.getByRole('button', { name: `${theme} theme`, exact: true }).click()
      await page.getByLabel('TOTA BOX Hex', { exact: true }).scrollIntoViewIfNeeded()
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
      const field = await page.getByLabel('TOTA BOX Hex', { exact: true }).boundingBox()
      assert.ok(field.x >= 0 && field.x + field.width <= viewport.width + 1)
      await page.screenshot({ path: `${output}/settings-${theme.toLowerCase()}-${viewport.width}.png` })
    }
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await tab('Body')
  let execution
  await page.route('**/api/grpc-bur/execute', async (route) => {
    execution = route.request().postDataJSON()
    const preview = await api('/grpc-bur/preview', execution)
    await route.fulfill({ json: { statusCode: 'OK', durationMillis: 1, metadata: [], body: '{}',
      errorMessage: '', requestPreview: preview, decodedPayloads: [] } })
  })
  await page.getByRole('button', { name: '送出', exact: true }).click()
  await page.locator('.response-summary.ok').waitFor()
  assert.equal(execution.settings.messageLayout, 'TOTA_BOX')
  assert.equal(execution.settings.totaBoxHex, '00 01 02 03 04 05')
  assert.equal(execution.settings.autoUpdateTcpipLength, true)
  assert.equal((await saved(samples[0].request)).grpcBurTotaBoxHex, '{{box}}')
  checks.push('Save/reload, Environment substitution, pre-send save and fixed single execution snapshot')
  await tab('Settings')
  await page.getByRole('button', { name: '新增欄位規則', exact: true }).click()
  const rule = page.locator('.field-rule-row').last()
  await rule.getByLabel('規則名稱', { exact: true }).fill('短 Label 序號')
  await rule.getByLabel('規則動作', { exact: true }).selectOption('INCREMENT')
  await rule.getByLabel('起始位置', { exact: true }).fill('8')
  await rule.getByLabel('結束位置', { exact: true }).fill('15')
  await tab('Body')
  for (const useRules of [true, false]) {
    await page.getByRole('button', { name: 'gRPC BUR 批次執行', exact: true }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel('總筆數', { exact: true }).fill('2')
    await dialog.getByLabel('套用欄位規則', { exact: false }).setChecked(useRules)
    const started = page.waitForResponse((response) => response.url().endsWith('/api/grpc-bur/batch-runs') && response.request().method() === 'POST')
    await dialog.getByRole('button', { name: '開始批次', exact: true }).click()
    const response = await started
    assert.ok(response.ok())
    const payload = response.request().postDataJSON().grpcBurRequest
    assert.equal(payload.settings.messageLayout, 'TOTA_BOX')
    assert.equal(payload.settings.totaBoxHex, '00 01 02 03 04 05')
    assert.equal(payload.settings.autoUpdateTcpipLength, true)
    await page.locator('.batch-results-head .batch-status-completed').waitFor()
  }
  checks.push('Actual Batch API receives layout, raw TOTA BOX and auto-length with rules on/off; unreachable test target is recorded as failure')
  const expected = await saved(samples[0].request)
  const duplicate = await api(`/requests/${samples[0].request.id}/duplicate`, {})
  assert.deepEqual(JSON.parse(duplicate.payloadJson), expected)
  const zip = await fetch(`${base}/api/requests/${samples[0].request.id}/export`)
  assert.ok(zip.ok)
  const form = new FormData()
  form.append('file', new Blob([await zip.arrayBuffer()], { type: 'application/zip' }), 'a98.zip')
  const imported = await fetch(`${base}/api/workspace/import?targetCollectionId=${collection.id}`, { method: 'POST', body: form })
  assert.ok(imported.ok, await imported.text())
  const collections = await api('/collections', undefined, 'GET')
  const matches = collections.find((item) => item.id === collection.id).requests.filter((item) =>
    JSON.parse(item.payloadJson).grpcBurTotaBoxHex === '{{box}}')
  assert.equal(matches.length, 3)
  for (const item of matches) assert.deepEqual(JSON.parse(item.payloadJson), expected)
  checks.push('Request duplicate and ZIP export/import preserve all new settings and templates')
  await page.getByRole('button', { name: '舊 MCS 測案', exact: true }).click()
  await tab('Settings')
  assert.equal(await page.getByLabel('BUR 電文結構', { exact: true }).inputValue(), 'MCS')
  assert.equal(await page.getByLabel('TOTA BOX Hex', { exact: true }).count(), 0)
  assert.equal(await page.locator('.field-rule-row').count(), 3)
  assert.equal((await saved(legacy)).grpcBurMessageLayout, undefined)
  checks.push('Legacy MCS default, legacy rule migration and no silent stored-data rewrite')
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ checks, errors, screenshots: output }, null, 2))
} finally {
  await browser.close()
  if (collection) await api(`/collections/${collection.id}`, undefined, 'DELETE')
  if (environment) await api(`/environments/${environment.id}`, undefined, 'DELETE')
}

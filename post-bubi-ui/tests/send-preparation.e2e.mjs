import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { mkdir } from 'node:fs/promises'

assert.equal(process.env.UIUX_TEST_DATABASE, 'isolated', 'Use an isolated backend')
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const base = process.env.UIUX_BASE_URL || 'http://127.0.0.1:18081'
const output = '/tmp/post-bubi-presend-screenshots'
await mkdir(output, { recursive: true })
const requests = []
let httpGate
let releaseHttp
let status = 200
function holdHttp() { httpGate = new Promise((resolve) => { releaseHttp = resolve }) }
const receiver = createServer(async (request, response) => {
  const chunks = []
  for await (const chunk of request) chunks.push(chunk)
  const body = JSON.parse(Buffer.concat(chunks).toString())
  requests.push(body)
  if (httpGate) await httpGate
  if (!response.destroyed) {
    response.writeHead(status, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify(body))
  }
})
await new Promise((resolve) => receiver.listen(0, '127.0.0.1', resolve))
async function api(path, body, method = 'POST') {
  const response = await fetch(`${base}/api${path}`, { method, headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body) })
  assert.ok(response.ok, `${path}: ${await response.clone().text()}`)
  return response.status === 204 ? null : response.json()
}
async function until(check) {
  for (let attempt = 0; attempt < 200; attempt++) {
    if (await check()) return
    await new Promise((resolve) => setTimeout(resolve, 25))
  }
  assert.fail('condition timed out')
}
const rule = (id, targetType, changes = {}) => ({ id, name: id, enabled: true, color: '#AB005F', action: 'INCREMENT', targetType, ...changes })
const browser = await chromium.launch(process.env.CHROME_EXECUTABLE ? { executablePath: process.env.CHROME_EXECUTABLE } : {})
let collection
let environment
const errors = []
const checks = []
try {
  collection = await api('/collections', { name: '送出前更新驗收' })
  environment = await api('/environments', { name: '準備流程', variables: [{ key: 'seq', value: '0009' }, { key: 'keep', value: 'resolved-secret' }] })
  const cases = [
    { type: 'HTTP', bodyKey: 'body', name: 'HTTP 預留序號', payload: { requestType: 'HTTP', method: 'POST',
      url: `http://127.0.0.1:${receiver.address().port}`, bodyType: 'json',
      body: '{ "id":"{{seq}}", "day":"00", "keep":"{{keep}}" }',
      fieldRules: [rule('seq', 'HTTP_JSON_VALUE', { path: '/id' }), rule('day', 'HTTP_JSON_VALUE', { path: '/day', action: 'TODAY_DD' })] } },
    { type: 'GRPC', bodyKey: 'grpcBody', name: 'gRPC 預留序號', payload: { requestType: 'GRPC', grpcHost: '127.0.0.1', grpcPort: '1',
      grpcServiceName: 'demo.Service', grpcMethodName: 'Echo', grpcEncodePayloadDataBase64: true,
      grpcBody: '{"payload":{"data":"繁000900"},"keep":"{{keep}}"}',
      fieldRules: [rule('seq', 'GRPC_PAYLOAD_DATA_RANGE', { start: 4, end: 7 }),
        rule('day', 'GRPC_PAYLOAD_DATA_RANGE', { start: 8, end: 9, action: 'TODAY_DD' })] } },
    { type: 'GRPC_BUR', bodyKey: 'grpcBurBasicLabel', name: 'BUR 預留序號', payload: { requestType: 'GRPC_BUR',
      grpcBurHost: '127.0.0.1', grpcBurPort: '1', grpcBurBasicLabel: '0'.repeat(158), grpcBurTextArea: 'TEST',
      fieldRules: [rule('seq', 'BUR_BASIC_LABEL_RANGE', { start: 8, end: 14 }),
        rule('day', 'BUR_BASIC_LABEL_RANGE', { start: 88, end: 89, action: 'TODAY_DD' })] } },
  ]
  for (const item of cases) {
    item.request = await api('/requests', { collectionId: collection.id, type: item.type, name: item.name, payloadJson: JSON.stringify(item.payload) })
  }
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('dialog', (dialog) => dialog.accept())
  await page.goto(base)
  await page.getByLabel('目前 Environment', { exact: true }).selectOption(String(environment.id))
  const bodyTab = () => page.getByRole('tablist', { name: 'Request 分頁' }).getByRole('tab', { name: 'Body', exact: true }).click()
  const dd = () => new Intl.DateTimeFormat('en', { timeZone: 'Asia/Taipei', day: '2-digit' }).format(new Date())
  const saved = async (item) => JSON.parse((await api(`/requests/${item.request.id}`, undefined, 'GET')).payloadJson)
  for (const item of cases) {
    await page.getByRole('button', { name: item.name, exact: true }).click()
    await bodyTab()
    let executePayload
    let release
    let gate
    const endpoint = item.type === 'GRPC' ? 'grpc' : 'grpc-bur'
    if (item.type === 'HTTP') holdHttp()
    else {
      gate = new Promise((resolve) => { release = resolve })
      await page.route(`**/api/${endpoint}/execute`, async (route) => {
        executePayload = route.request().postDataJSON()
        await gate
        await route.fulfill({ json: { statusCode: 'UNAVAILABLE', durationMillis: 12, statusDescription: 'test target error', errorMessage: 'test target error',
          body: '{}', metadata: [], decodedPayloads: [], requestPreview: { effectiveBasicLabel: 'stale-response-value' } } })
      })
    }
    await page.getByRole('button', { name: '送出', exact: true }).click()
    await until(() => item.type === 'HTTP' ? requests.length === 1 : !!executePayload)
    assert.ok(await page.getByRole('button', { name: '取消', exact: true }).isVisible())
    const stored = await saved(item)
    assert.equal(stored.fieldRules[0].action, 'INCREMENT')
    if (item.type === 'HTTP') {
      const text = await page.getByLabel('JSON Body', { exact: true }).inputValue()
      assert.equal(JSON.parse(text).id, '0010')
      assert.equal(JSON.parse(text).keep, '{{keep}}')
      assert.equal(JSON.parse(stored.body).id, '0010')
      assert.equal(requests[0].id, '0010')
      assert.equal(requests[0].day, dd())
      await page.screenshot({ path: `${output}/http-updated-before-response.png` })
      releaseHttp()
    } else if (item.type === 'GRPC') {
      const text = await page.getByLabel('JSON Body', { exact: true }).inputValue()
      assert.equal(JSON.parse(text).payload.data, `繁0010${dd()}`)
      assert.equal(JSON.parse(text).keep, '{{keep}}')
      assert.equal(JSON.parse(stored.grpcBody).payload.data, `繁0010${dd()}`)
      assert.equal(JSON.parse(executePayload.body).payload.data, `繁0010${dd()}`)
      assert.equal(executePayload.encodePayloadDataBase64, true)
      assert.ok(executePayload.fieldRules.every((value) => value.action === 'NONE'))
      await page.screenshot({ path: `${output}/grpc-updated-before-response.png` })
      release()
    } else {
      const label = await page.getByLabel('Basic Label', { exact: true }).inputValue()
      assert.equal(label.slice(7, 14), '0000001')
      assert.equal(label.slice(87, 89), dd())
      assert.equal(stored.grpcBurBasicLabel, label)
      assert.equal(executePayload.basicLabel, label)
      assert.ok(executePayload.settings.fieldRules.every((value) => value.action === 'NONE'))
      await page.screenshot({ path: `${output}/grpc_bur-updated-before-response.png` })
      await page.getByLabel('Basic Label', { exact: true }).fill(label.slice(0, -1) + 'Z')
      release()
    }
    await page.getByRole('button', { name: '送出', exact: true }).waitFor({ state: 'visible' })
    if (item.type === 'GRPC_BUR') assert.ok((await page.getByLabel('Basic Label', { exact: true }).inputValue()).endsWith('Z'))
    checks.push(`${item.type}: UI and persisted value updated before target response; no double action or late overwrite`)
    if (item.type !== 'HTTP') await page.unroute(`**/api/${endpoint}/execute`)
  }

  const http = cases[0]
  await page.getByRole('button', { name: http.name, exact: true }).click()
  await bodyTab()
  status = 500
  holdHttp()
  await page.getByRole('button', { name: '送出', exact: true }).click()
  await until(() => requests.length === 2)
  assert.equal(requests.at(-1).id, '0011')
  releaseHttp()
  await page.getByRole('button', { name: '送出', exact: true }).waitFor({ state: 'visible' })
  assert.equal(JSON.parse(await page.getByLabel('JSON Body', { exact: true }).inputValue()).id, '0011')
  holdHttp()
  await page.getByRole('button', { name: '送出', exact: true }).click()
  await until(() => requests.length === 3)
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('.response-summary.cancelled').waitFor()
  assert.equal(JSON.parse((await saved(http)).body).id, '0012')
  releaseHttp()
  httpGate = null
  status = 200
  checks.push('Repeated HTTP sends reserve successive values; HTTP errors and cancellation never roll back persisted sequence')

  for (const item of cases) {
    await page.getByRole('button', { name: item.name, exact: true }).click()
    await bodyTab()
    const protocol = item.type === 'HTTP' ? 'http' : item.type === 'GRPC' ? 'grpc' : 'grpc-bur'
    const before = await saved(item)
    let startedBody
    let release
    const gate = new Promise((resolve) => { release = resolve })
    await page.route(`**/api/${protocol}/batch-runs`, async (route) => {
      if (route.request().method() !== 'POST') return route.continue()
      startedBody = route.request().postDataJSON()
      await gate
      await route.continue()
    })
    await page.getByRole('button', { name: `${item.type === 'HTTP' ? 'HTTP' : item.type === 'GRPC' ? 'gRPC' : 'gRPC BUR'} 批次執行`, exact: true }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel('總筆數', { exact: true }).fill('2')
    await dialog.getByRole('button', { name: '開始批次', exact: true }).click()
    await until(() => !!startedBody)
    const after = await saved(item)
    const snapshot = startedBody.httpRequest || startedBody.grpcRequest || startedBody.grpcBurRequest
    if (item.type === 'HTTP') {
      const old = JSON.parse(before.body).id
      assert.equal(JSON.parse(snapshot.body).id, old)
      assert.equal(JSON.parse(after.body).id, String(Number(old) + 2).padStart(4, '0'))
    } else if (item.type === 'GRPC') {
      const old = JSON.parse(before.grpcBody).payload.data
      assert.equal(JSON.parse(snapshot.body).payload.data, old)
      assert.equal(JSON.parse(after.grpcBody).payload.data.slice(1, 5), '0012')
    } else {
      assert.equal(snapshot.basicLabel, before.grpcBurBasicLabel)
      assert.equal(after.grpcBurBasicLabel.slice(7, 14), '0000003')
    }
    assert.equal((snapshot.fieldRules || snapshot.settings.fieldRules)[0].action, 'INCREMENT')
    release()
    await page.locator('.batch-results-head .batch-status-completed').waitFor()
    await page.unroute(`**/api/${protocol}/batch-runs`)
    checks.push(`${item.type} Batch: reserve/save N before start; server receives original snapshot and rules`)
  }
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ passed: checks, errors, screenshots: output }, null, 2))
} catch (error) {
  await browser.contexts().at(-1)?.pages()[0]?.screenshot({ path: `${output}/failure.png`, fullPage: true })
  throw error
} finally {
  releaseHttp?.()
  await browser.close()
  if (collection) await api(`/collections/${collection.id}`, undefined, 'DELETE')
  if (environment) await api(`/environments/${environment.id}`, undefined, 'DELETE')
  await new Promise((resolve) => receiver.close(resolve))
}

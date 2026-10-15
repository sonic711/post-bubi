import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { mkdir } from 'node:fs/promises'

// Run only against an isolated backend: the suite creates and deletes its own fixtures.
assert.equal(process.env.UIUX_TEST_DATABASE, 'isolated', 'Set UIUX_TEST_DATABASE=isolated with a separate test backend')
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const baseUrl = process.env.UIUX_BASE_URL || 'http://127.0.0.1:5174'
const apiUrl = process.env.UIUX_API_URL || 'http://127.0.0.1:18081'
const output = process.env.UIUX_SCREENSHOTS || '/tmp/post-bubi-uiux-screenshots'
await mkdir(output, { recursive: true })
const fixtures = []
const errors = []
const checks = []
const receiver = createServer(async (request, response) => {
  const chunks = []
  for await (const chunk of request) chunks.push(chunk)
  if (request.url === '/slow') await new Promise((resolve) => setTimeout(resolve, 1500))
  response.setHeader('Content-Type', 'application/json')
  response.end(JSON.stringify({
    message: 'API 測試成功', method: request.method, body: Buffer.concat(chunks).toString(),
    data: Buffer.from('Post Bubi 解碼驗證').toString('base64'),
    items: Array.from({ length: 80 }, (_, index) => ({ id: index + 1, name: `回應資料 ${index + 1}`, active: true })),
  }))
})
await new Promise((resolve) => receiver.listen(0, '127.0.0.1', resolve))
const target = `http://127.0.0.1:${receiver.address().port}`
async function api(path, body, method = 'POST') {
  const response = await fetch(`${apiUrl}/api${path}`, {
    method, headers: { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body),
  })
  assert.ok(response.ok, `${method} ${path}: ${await response.clone().text()}`)
  return response.status === 204 ? null : response.json()
}
const browser = await chromium.launch(process.env.CHROME_EXECUTABLE ? { executablePath: process.env.CHROME_EXECUTABLE } : {})
try {
  const collection = await api('/collections', { name: 'UIUX 驗收', description: '' })
  fixtures.push(`/collections/${collection.id}`)
  const environment = await api('/environments', { name: 'UIUX Local', variables: [{ key: 'baseUrl', value: target }] })
  fixtures.push(`/environments/${environment.id}`)
  const headerRows = Array.from({ length: 35 }, (_, index) => ({ name: `X-Test-${index}`, value: String(index), enabled: true }))
  const longJson = JSON.stringify({ name: 'Post Bubi', payload: { data: '測試文字' }, rows: Array.from({ length: 60 }, (_, index) => ({ index, text: 'JSON 編輯器驗證' })) }, null, 2)
  const payload = {
    requestType: 'HTTP', method: 'POST', url: '{{baseUrl}}/echo', bodyType: 'json', body: longJson,
    headers: headerRows, timeoutMillis: 30000, ignoreSslVerification: true,
    responseDecodeRows: [{ name: 'data', value: '解碼文字', enabled: true }],
  }
  const http = await api('/requests', { collectionId: collection.id, type: 'HTTP', name: 'HTTP JSON 回應', payloadJson: JSON.stringify(payload) })
  await api('/requests', { collectionId: collection.id, type: 'GRPC', name: 'gRPC 服務查詢', payloadJson: JSON.stringify({ requestType: 'GRPC', grpcHost: 'localhost', grpcPort: '50051', grpcServiceName: 'test.Service', grpcMethodName: 'Echo', grpcBody: longJson, grpcEncodePayloadDataBase64: true }) })
  await api('/requests', { collectionId: collection.id, type: 'GRPC_BUR', name: 'BUR 交易電文', payloadJson: JSON.stringify({ requestType: 'GRPC_BUR', grpcBurBasicLabel: '0'.repeat(158), grpcBurTextArea: 'TEST', grpcBurBasicLabelLength: 158, grpcBurIncrementBasicLabelSequence: true, grpcBurFillBasicLabelDayOfMonth: true }) })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('dialog', (dialog) => dialog.accept())
  await page.goto(baseUrl)
  await page.getByRole('button', { name: 'HTTP JSON 回應', exact: true }).click()
  await page.getByLabel('目前 Environment', { exact: true }).selectOption(String(environment.id))
  const requestTab = (name) => page.getByRole('tablist', { name: 'Request 分頁' }).getByRole('tab', { name, exact: true })
  const responseTab = (name) => page.getByRole('tablist', { name: 'Response 分頁' }).getByRole('tab', { name, exact: true })
  const noOverflow = async () => assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'page must not overflow horizontally')
  const noToolbarClipping = async () => {
    const clipped = await page.locator('.toolbar > button, .toolbar > input, .toolbar > select').evaluateAll((elements) => elements.filter((element) => {
      const rect = element.getBoundingClientRect()
      const toolbar = element.parentElement.getBoundingClientRect()
      return rect.right > Math.min(toolbar.right, innerWidth) + 1 || rect.left < toolbar.left - 1 || rect.bottom > toolbar.bottom + 1
    }).map((element) => ({ name: element.getAttribute('aria-label') || element.textContent, rect: element.getBoundingClientRect().toJSON() })))
    assert.deepEqual(clipped, [], 'toolbar controls must remain inside the toolbar')
  }
  const checkContrast = async () => {
    const ratios = await page.evaluate(() => {
      const style = getComputedStyle(document.documentElement)
      const luminance = (hex) => {
        const channels = hex.trim().slice(1).match(/../g).map((value) => parseInt(value, 16) / 255)
        return channels.map((channel) => channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4)
          .reduce((total, channel, index) => total + channel * [0.2126, 0.7152, 0.0722][index], 0)
      }
      return ['--text-primary', '--text-secondary', '--text-muted'].flatMap((text) =>
        ['--surface', '--surface-subtle'].map((surface) => {
          const values = [luminance(style.getPropertyValue(text)), luminance(style.getPropertyValue(surface))].sort((a, b) => b - a)
          return { text, surface, ratio: (values[0] + 0.05) / (values[1] + 0.05) }
        }))
    })
    assert.ok(ratios.every((entry) => entry.ratio >= 4.5), JSON.stringify(ratios))
  }
  await page.getByLabel('搜尋 Collections 與 Requests').fill('grpc')
  assert.equal(await page.locator('.request-item').count(), 2)
  await page.getByRole('button', { name: '清除搜尋', exact: true }).click()
  assert.equal(await page.locator('.request-item').count(), 3)
  checks.push('Collection search and reset')

  await requestTab('Body').click()
  await requestTab('Body').press('ArrowRight')
  assert.equal(await requestTab('Settings').getAttribute('aria-selected'), 'true')
  await requestTab('Settings').press('Home')
  assert.equal(await requestTab('Params').getAttribute('aria-selected'), 'true')
  await page.getByRole('tab', { name: 'Headers 35', exact: true }).click()
  const scroll = page.locator('.key-value-scroll')
  assert.ok(await scroll.evaluate((element) => element.scrollHeight > element.clientHeight))
  await scroll.evaluate((element) => { element.scrollTop = element.scrollHeight })
  assert.ok(await scroll.evaluate((element) => element.scrollTop > 0))
  await page.getByRole('button', { name: '收合 Headers', exact: true }).click()
  assert.ok(await page.getByText('已收合 35 筆 Header').isVisible())
  await page.getByRole('button', { name: '展開 Headers', exact: true }).click()
  checks.push('Keyboard tabs; long Headers scroll/collapse')

  await requestTab('Body').click()
  await page.getByLabel('JSON Body', { exact: true }).evaluate((element) => {
    element.scrollTop = element.scrollHeight
    element.dispatchEvent(new Event('scroll'))
  })
  const alignment = await page.locator('.json-editor').evaluate((element) => {
    const input = element.querySelector('textarea')
    const highlight = element.querySelector('pre')
    return { input: input.scrollTop, highlight: highlight.scrollTop, inputLine: getComputedStyle(input).lineHeight, highlightLine: getComputedStyle(highlight).lineHeight }
  })
  assert.equal(alignment.inputLine, alignment.highlightLine)
  assert.ok(Math.abs(alignment.input - alignment.highlight) <= 1)
  checks.push('JSON overlay line-height and bottom scroll alignment')

  const saveResponse = page.waitForResponse((response) => response.url().endsWith(`/api/requests/${http.id}`) && response.request().method() === 'PUT')
  await page.getByRole('button', { name: '送出', exact: true }).click()
  await saveResponse
  await page.locator('.response-summary.ok').waitFor()
  assert.equal(await responseTab('Body').getAttribute('aria-selected'), 'true')
  const saved = await api(`/requests/${http.id}`, undefined, 'GET')
  assert.equal(JSON.parse(saved.payloadJson).body, longJson)
  const body = page.locator('.response-content .json-viewer')
  assert.match(await body.textContent(), /Post Bubi 解碼驗證/)
  assert.ok(await body.evaluate((element) => element.scrollHeight > element.clientHeight))
  await body.evaluate((element) => { element.scrollTop = element.scrollHeight })
  assert.ok(await body.evaluate((element) => element.scrollTop > 0))
  await page.getByRole('button', { name: 'Response Body 自動換行' }).click()
  assert.equal(await body.evaluate((element) => getComputedStyle(element).whiteSpace), 'pre-wrap')
  await body.evaluate((element) => { element.scrollTop = 0 })
  await page.screenshot({ path: `${output}/desktop-light.png`, animations: 'disabled' })
  await checkContrast()
  await page.getByRole('button', { name: 'Dark theme', exact: true }).click()
  await page.screenshot({ path: `${output}/desktop-dark.png`, animations: 'disabled' })
  await checkContrast()
  await noOverflow()
  checks.push('Actual HTTP + autosave, Environment resolution, decoded Body, long Response, themes')

  const separator = page.getByRole('separator', { name: '調整 Response 高度', exact: true })
  const beforeHeight = await page.locator('.response').evaluate((element) => element.clientHeight)
  await separator.press('ArrowUp')
  assert.ok(await page.locator('.response').evaluate((element) => element.clientHeight) > beforeHeight)
  const sidebarSeparator = page.locator('.sidebar-resize-handle')
  await sidebarSeparator.focus()
  await sidebarSeparator.press('ArrowLeft')
  const sidebar = page.locator('.sidebar')
  const width = await sidebar.evaluate((element) => element.clientWidth)
  for (let index = 0; index < 6; index++) await sidebarSeparator.press('ArrowLeft')
  assert.ok((await sidebar.getAttribute('class')).includes('is-collapsed'))
  const handle = await sidebarSeparator.boundingBox()
  await page.mouse.move(handle.x + handle.width / 2, handle.y + 100)
  await page.mouse.down()
  await page.mouse.move(handle.x + 230, handle.y + 100, { steps: 12 })
  await page.mouse.up()
  assert.ok(!(await sidebar.getAttribute('class')).includes('is-collapsed'))
  assert.ok(await sidebar.evaluate((element) => element.clientWidth) >= width)
  checks.push('Response keyboard resize; sidebar keyboard collapse and pointer re-expand')

  await page.getByRole('button', { name: '產生 cURL', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.waitFor()
  assert.ok(await page.locator('.panel').evaluate((element) => element.inert))
  const dialogButtons = dialog.getByRole('button')
  await dialogButtons.last().focus()
  await page.keyboard.press('Tab')
  assert.equal(await dialogButtons.first().evaluate((element) => element === document.activeElement), true)
  const command = await page.locator('.curl-command-output').boundingBox()
  await page.mouse.move(command.x + 30, command.y + 30)
  await page.mouse.down()
  await page.mouse.move(4, 4, { steps: 8 })
  await page.mouse.up()
  assert.equal(await page.getByRole('dialog').count(), 1, 'dragging text onto backdrop must not dismiss dialog')
  await dialog.press('Escape')
  assert.equal(await page.getByRole('dialog').count(), 0)
  assert.equal(await page.getByRole('button', { name: '產生 cURL', exact: true }).evaluate((element) => element === document.activeElement), true)
  checks.push('Dialog focus trap, inert background, Escape and focus restoration')

  await page.getByRole('button', { name: 'HTTP 批次執行', exact: true }).click()
  const batchDialog = page.getByRole('dialog')
  await batchDialog.getByLabel('總筆數', { exact: true }).fill('3')
  await batchDialog.getByRole('button', { name: '開始批次', exact: true }).click()
  await page.locator('.batch-results-head .batch-status-completed').waitFor()
  assert.equal(await page.locator('.batch-item-row').count(), 3)
  assert.ok(await page.locator('.batch-items-table').evaluate((element) => element.clientHeight > 0))
  await page.screenshot({ path: `${output}/batch-dark.png`, animations: 'disabled' })
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: '匯出目前 Batch 為 CSV', exact: true }).click()
  assert.ok((await download).suggestedFilename().endsWith('.csv'))
  checks.push('Actual HTTP Batch completion, items visible and CSV download')

  await page.getByLabel('URL', { exact: true }).fill(`${target}/slow`)
  await page.getByRole('button', { name: '送出', exact: true }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('.response-summary.cancelled').waitFor()
  await page.getByLabel('URL', { exact: true }).fill('{{baseUrl}}/echo')
  await page.getByRole('button', { name: '儲存 Request', exact: true }).click()
  await page.locator('.status-pill:not(.dirty)').waitFor()
  checks.push('Actual in-flight HTTP cancellation and editor recovery')

  for (const [title, type] of [['gRPC 服務查詢', 'GRPC'], ['BUR 交易電文', 'GRPC_BUR']]) {
    await page.getByRole('button', { name: title, exact: true }).click()
    await requestTab('Body').click()
    if (type === 'GRPC') {
      assert.ok(await page.locator('.json-base64-send-badge').count())
    } else {
      assert.ok(await page.locator('.basic-label-chip').filter({ hasText: '8-14' }).isVisible())
      assert.ok(await page.locator('.basic-label-chip').filter({ hasText: '37-46' }).isVisible())
    }
    await requestTab('Settings').click()
    const settings = page.locator('.settings-pane')
    await settings.evaluate((element) => { element.scrollTop = element.scrollHeight })
    if (type === 'GRPC_BUR') assert.ok(await settings.evaluate((element) => element.scrollTop > 0))
    await page.getByRole('button', { name: `${type === 'GRPC' ? 'gRPC' : 'gRPC BUR'} 批次執行`, exact: true }).click()
    if (type === 'GRPC_BUR') assert.ok(await page.getByLabel('套用欄位規則', { exact: false }).isVisible())
    await page.getByRole('dialog').press('Escape')
    await requestTab('Body').click()
    await noOverflow()
    await noToolbarClipping()
    await page.screenshot({ path: `${output}/${type.toLowerCase()}-dark.png`, animations: 'disabled' })
  }
  checks.push('gRPC/BUR Body, Basic Label markings, Settings scrolling and Batch options')

  await page.getByRole('button', { name: 'HTTP JSON 回應', exact: true }).click()
  await requestTab('Body').click()
  await responseTab('Body').click()
  for (const viewport of [{ width: 1024, height: 768 }, { width: 768, height: 1024 }, { width: 390, height: 844 }, { width: 375, height: 667 }]) {
    await page.setViewportSize(viewport)
    await noOverflow()
    await noToolbarClipping()
    await page.getByRole('button', { name: 'Light theme', exact: true }).click()
    await page.screenshot({ path: `${output}/light-${viewport.width}.png`, fullPage: true, animations: 'disabled' })
    await page.getByRole('button', { name: 'Dark theme', exact: true }).click()
    await page.screenshot({ path: `${output}/dark-${viewport.width}.png`, fullPage: true, animations: 'disabled' })
    if (viewport.width <= 860) {
      const toggle = page.getByRole('button', { name: '開啟工作區導覽', exact: true })
      await toggle.click()
      await page.getByLabel('搜尋 Collections 與 Requests').waitFor({ state: 'visible' })
      await page.getByRole('button', { name: '關閉工作區導覽', exact: true }).click({ position: { x: 340, y: 300 } })
      assert.equal(await toggle.getAttribute('aria-expanded'), 'false')
    }
    for (const title of ['gRPC 服務查詢', 'BUR 交易電文', 'HTTP JSON 回應']) {
      if (viewport.width <= 860) await page.getByRole('button', { name: '開啟工作區導覽', exact: true }).click()
      await page.getByRole('button', { name: title, exact: true }).click()
      await requestTab('Body').click()
      await noOverflow()
      await noToolbarClipping()
    }
  }
  checks.push('1024 / 768 / 390 / 375 layouts and navigation drawer')
  await page.emulateMedia({ reducedMotion: 'reduce' })
  assert.ok(await page.locator('.sidebar').evaluate((element) => getComputedStyle(element).transitionDuration.split(',').every((duration) => parseFloat(duration) < 0.001)))
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ passed: checks, browserErrors: errors, screenshots: output }, null, 2))
} catch (error) {
  await browser.contexts().at(-1)?.pages()[0]?.screenshot({ path: `${output}/failure.png`, fullPage: true })
  console.error(JSON.stringify({ completed: checks, browserErrors: errors }))
  throw error
} finally {
  await browser.close()
  for (const path of fixtures.reverse()) await api(path, undefined, 'DELETE')
  await new Promise((resolve) => receiver.close(resolve))
}

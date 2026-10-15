import assert from 'node:assert/strict'
import { execFile } from 'node:child_process'
import http from 'node:http'
import { promisify } from 'node:util'
import test from 'node:test'

import { buildCurlCommand, hasCurlTemplateVariables } from './curl-command.mjs'

const execFileAsync = promisify(execFile)

test('Bash cURL 可傳送含 query、排序 header 與多行 JSON body 的 Request', async () => {
  let received
  const server = http.createServer((request, response) => {
    const body = []
    request.on('data', (chunk) => body.push(chunk))
    request.on('end', () => {
      received = {
        body: Buffer.concat(body).toString('utf8'),
        headers: request.rawHeaders,
        method: request.method,
        url: request.url,
      }
      response.setHeader('Content-Type', 'application/json')
      response.end(JSON.stringify({ ok: true }))
    })
  })
  await listen(server)

  try {
    const port = server.address().port
    const body = '{\n  "message": "O\'Reilly",\n  "note": "line one\\nline two"\n}'
    const command = buildCurlCommand({
      body,
      bodyType: 'json',
      followRedirects: true,
      headers: [
        { name: 'Content-Type', value: 'application/json' },
        { name: 'X-First', value: 'alpha' },
        { name: 'X-Second', value: 'beta' },
      ],
      ignoreSslVerification: false,
      method: 'POST',
      params: [
        { name: 'existing', value: 'updated value' },
        { name: 'disabled', value: 'ignored', enabled: false },
      ],
      timeoutMillis: 1250,
      url: `http://127.0.0.1:${port}/echo?fixed=yes`,
    }, 'bash')

    const { stdout } = await execFileAsync('bash', ['-c', command])
    assert.deepEqual(JSON.parse(stdout), { ok: true })
    assert.equal(received.method, 'POST')
    assert.equal(received.url, '/echo?fixed=yes&existing=updated%20value')
    assert.equal(received.body, body)
    assertHeaderOrder(received.headers, 'content-type', 'x-first', 'x-second')
  } finally {
    await close(server)
  }
})

test('PowerShell 使用 curl.exe，並以 PowerShell 單引號規則跳脫字串', () => {
  const command = buildCurlCommand({
    method: 'POST',
    url: 'https://example.test/api',
    bodyType: 'raw',
    body: "it's valid",
    timeoutMillis: 30000,
  }, 'powershell')

  assert.match(command, /^curl\.exe `\n/)
  assert.match(command, /--data 'it''s valid'/)
})

test('可辨識未解析的 Environment 模板', () => {
  assert.equal(hasCurlTemplateVariables('https://{{host}}/api'), true)
  assert.equal(hasCurlTemplateVariables('https://example.test/api'), false)
})

test('Bash cURL 實際送出套用規則的 JSON，且不改寫保存內容', async () => {
  let received
  const server = http.createServer(async (request, response) => {
    const chunks = []
    for await (const chunk of request) chunks.push(chunk)
    received = Buffer.concat(chunks).toString()
    response.end('ok')
  })
  await listen(server)
  const rule = (path, action) => ({ id: path, name: path, path, action, enabled: true,
    color: '#AB005F', targetType: 'HTTP_JSON_VALUE' })
  try {
    const payload = { method: 'POST', url: `http://127.0.0.1:${server.address().port}`, bodyType: 'json',
      body: '{"id":"0009","count":9007199254740993,"day":"00","keep":"原文"}',
      fieldRules: [rule('/id', 'INCREMENT'), rule('/count', 'INCREMENT'), rule('/day', 'TODAY_DD'), rule('/keep', 'NONE')] }
    const original = payload.body
    const bash = buildCurlCommand(payload, 'bash')
    const powershell = buildCurlCommand(payload, 'powershell')
    await execFileAsync('bash', ['-c', bash])
    assert.match(received, /"id":"0010"/)
    assert.match(received, /"count":9007199254740994/)
    assert.equal(JSON.parse(received).day, new Intl.DateTimeFormat('en', { timeZone: 'Asia/Taipei', day: '2-digit' }).format(new Date()))
    assert.equal(JSON.parse(received).keep, '原文')
    assert.ok(powershell.includes(received))
    assert.equal(payload.body, original)
  } finally {
    await close(server)
  }
})

test('cURL 拒絕尚未解析的遞增變數，而非產生未套用規則的指令', () => {
  assert.throws(() => buildCurlCommand({ bodyType: 'json', body: '{"id":"{{sequence}}"}',
    fieldRules: [{ id: 'id', name: '序號', enabled: true, color: '#AB005F', action: 'INCREMENT', targetType: 'HTTP_JSON_VALUE', path: '/id' }] }, 'bash'), /Environment/)
})

function assertHeaderOrder(headers, ...names) {
  const normalized = headers
    .filter((_, index) => index % 2 === 0)
    .map((name) => name.toLowerCase())
  const indexes = names.map((name) => normalized.indexOf(name))
  assert.ok(indexes.every((index) => index >= 0))
  assert.ok(indexes.every((index, current) => current === 0 || indexes[current - 1] < index))
}

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
}

function close(server) {
  return new Promise((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())))
}

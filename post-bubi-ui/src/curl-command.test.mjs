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

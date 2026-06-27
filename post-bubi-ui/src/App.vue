<template>
  <main class="workspace">
    <aside class="sidebar">
      <div class="brand">Post Bubi</div>
      <button class="primary-button" type="button" disabled>新增 Collection</button>
      <section class="tree">
        <div class="tree-title">目前階段</div>
        <button class="tree-item active" type="button">HTTP Request</button>
        <button class="tree-item" type="button" disabled>Collection 儲存</button>
        <button class="tree-item" type="button" disabled>gRPC Request</button>
      </section>
    </aside>

    <section class="panel">
      <header class="toolbar">
        <select v-model="method" aria-label="HTTP method">
          <option>GET</option>
          <option>POST</option>
          <option>PUT</option>
          <option>PATCH</option>
          <option>DELETE</option>
        </select>
        <input v-model="url" aria-label="URL" />
        <button class="send-button" type="button" :disabled="sending" @click="sendHttpRequest">
          {{ sending ? '送出中' : '送出' }}
        </button>
      </header>

      <section class="editor">
        <nav class="tabs">
          <button
            v-for="tab in requestTabs"
            :key="tab.key"
            class="tab"
            :class="{ active: activeRequestTab === tab.key }"
            type="button"
            @click="activeRequestTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </nav>

        <div v-if="activeRequestTab === 'params'" class="editor-pane">
          <label>Query Params</label>
          <textarea v-model="paramsText" spellcheck="false" aria-label="Query Params"></textarea>
        </div>

        <div v-if="activeRequestTab === 'headers'" class="editor-pane">
          <label>Headers</label>
          <textarea v-model="headersText" spellcheck="false" aria-label="Headers"></textarea>
        </div>

        <div v-if="activeRequestTab === 'body'" class="editor-pane body-pane">
          <div class="body-toolbar">
            <label for="body-type">Body Type</label>
            <select id="body-type" v-model="bodyType">
              <option value="none">none</option>
              <option value="json">JSON</option>
              <option value="raw">raw text</option>
              <option value="x-www-form-urlencoded">x-www-form-urlencoded</option>
            </select>
          </div>
          <textarea v-model="bodyText" spellcheck="false" aria-label="Body" :disabled="bodyType === 'none'"></textarea>
        </div>

        <div v-if="activeRequestTab === 'settings'" class="settings-pane">
          <label>
            Timeout
            <input v-model.number="timeoutMillis" type="number" min="1" max="300000" />
          </label>
          <label class="check-line">
            <input v-model="followRedirects" type="checkbox" />
            Follow redirects
          </label>
          <label class="check-line">
            <input v-model="ignoreSslVerification" type="checkbox" />
            Ignore SSL certificate verification
          </label>
        </div>
      </section>

      <section class="response">
        <div class="response-bar">
          <strong>Response</strong>
          <span>{{ responseSummary }}</span>
        </div>
        <nav class="tabs response-tabs">
          <button
            v-for="tab in responseTabs"
            :key="tab.key"
            class="tab"
            :class="{ active: activeResponseTab === tab.key }"
            type="button"
            @click="activeResponseTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </nav>
        <pre v-if="activeResponseTab === 'body'">{{ responseBody }}</pre>
        <pre v-else-if="activeResponseTab === 'headers'">{{ responseHeaders }}</pre>
        <pre v-else>{{ responseInfo }}</pre>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue'

const requestTabs = [
  { key: 'params', label: 'Params' },
  { key: 'headers', label: 'Headers' },
  { key: 'body', label: 'Body' },
  { key: 'settings', label: 'Settings' },
]

const responseTabs = [
  { key: 'body', label: 'Body' },
  { key: 'headers', label: 'Headers' },
  { key: 'info', label: 'Info' },
]

const method = ref('GET')
const url = ref('http://localhost:18080/api/health')
const paramsText = ref('')
const headersText = ref('Accept=application/json')
const bodyType = ref('none')
const bodyText = ref('{\n  "name": "Post Bubi"\n}')
const timeoutMillis = ref(30000)
const followRedirects = ref(true)
const ignoreSslVerification = ref(false)
const activeRequestTab = ref('params')
const activeResponseTab = ref('body')
const sending = ref(false)
const response = ref(null)
const errorText = ref('')

const responseSummary = computed(() => {
  if (sending.value) return '送出中'
  if (errorText.value) return '錯誤'
  if (!response.value) return '尚未送出'
  return `${response.value.statusCode} ${response.value.reasonPhrase || ''} · ${response.value.durationMillis} ms · ${response.value.sizeBytes} bytes`
})

const responseBody = computed(() => {
  if (errorText.value) return errorText.value
  if (!response.value) return '尚未送出 request。'
  return prettyText(response.value.body)
})

const responseHeaders = computed(() => {
  if (!response.value) return ''
  return response.value.headers
    .map((header) => `${header.name}: ${header.value}`)
    .join('\n')
})

const responseInfo = computed(() => {
  if (!response.value) return ''
  return JSON.stringify({
    statusCode: response.value.statusCode,
    reasonPhrase: response.value.reasonPhrase,
    durationMillis: response.value.durationMillis,
    sizeBytes: response.value.sizeBytes,
    bodyBase64Encoded: response.value.bodyBase64Encoded,
  }, null, 2)
})

async function sendHttpRequest() {
  sending.value = true
  errorText.value = ''
  response.value = null

  try {
    const backendResponse = await fetch('/api/http/execute', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        method: method.value,
        url: url.value,
        params: parseNameValueLines(paramsText.value),
        headers: parseNameValueLines(headersText.value),
        bodyType: bodyType.value,
        body: bodyType.value === 'none' ? '' : bodyText.value,
        timeoutMillis: timeoutMillis.value,
        followRedirects: followRedirects.value,
        ignoreSslVerification: ignoreSslVerification.value,
      }),
    })

    const payload = await backendResponse.json()
    if (!backendResponse.ok) {
      throw new Error(`${payload.code || backendResponse.status}: ${payload.message || backendResponse.statusText}`)
    }
    response.value = payload
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : String(error)
  } finally {
    sending.value = false
  }
}

function parseNameValueLines(text) {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .filter((line) => !line.startsWith('#'))
    .map((line) => {
      const delimiterIndex = line.indexOf('=')
      if (delimiterIndex < 0) {
        return { name: line, value: '', enabled: true }
      }
      return {
        name: line.slice(0, delimiterIndex).trim(),
        value: line.slice(delimiterIndex + 1).trim(),
        enabled: true,
      }
    })
    .filter((entry) => entry.name)
}

function prettyText(text) {
  if (!text) return ''
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}
</script>

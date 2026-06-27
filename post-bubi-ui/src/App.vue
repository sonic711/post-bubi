<template>
  <main class="workspace">
    <aside class="sidebar">
      <div class="brand">Post Bubi</div>
      <button class="primary-button" type="button">新增 Collection</button>
      <section class="tree">
        <div class="tree-title">Collection</div>
        <button class="tree-item active" type="button">未命名 HTTP Request</button>
        <button class="tree-item" type="button">未命名 gRPC Request</button>
      </section>
    </aside>

    <section class="panel">
      <header class="toolbar">
        <select aria-label="Request type">
          <option>HTTP</option>
          <option>gRPC</option>
        </select>
        <select aria-label="HTTP method">
          <option>GET</option>
          <option>POST</option>
          <option>PUT</option>
          <option>PATCH</option>
          <option>DELETE</option>
        </select>
        <input aria-label="URL" value="http://localhost:18080/api/health" />
        <button class="send-button" type="button" @click="sendHealthCheck">送出</button>
      </header>

      <section class="editor">
        <nav class="tabs">
          <button class="tab active" type="button">Params</button>
          <button class="tab" type="button">Headers</button>
          <button class="tab" type="button">Body</button>
          <button class="tab" type="button">Settings</button>
        </nav>
        <textarea spellcheck="false" :value="requestText" aria-label="Request body"></textarea>
      </section>

      <section class="response">
        <div class="response-bar">
          <strong>Response</strong>
          <span>{{ responseStatus }}</span>
        </div>
        <pre>{{ responseText }}</pre>
      </section>
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue'

const requestText = `{
  "bodyType": "none",
  "timeoutMillis": 30000,
  "followRedirects": true,
  "ignoreSslVerification": false
}`

const responseStatus = ref('尚未送出')
const responseText = ref('按下送出後會呼叫 /api/health，後續會擴充成完整 HTTP 與 gRPC 測試工具。')

async function sendHealthCheck() {
  responseStatus.value = '送出中'
  try {
    const response = await fetch('/api/health')
    const body = await response.json()
    responseStatus.value = `${response.status} ${response.statusText}`
    responseText.value = JSON.stringify(body, null, 2)
  } catch (error) {
    responseStatus.value = '錯誤'
    responseText.value = error instanceof Error ? error.message : String(error)
  }
}
</script>

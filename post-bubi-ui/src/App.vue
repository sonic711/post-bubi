<template>
  <main class="workspace">
    <aside class="sidebar">
      <div class="brand">Post Bubi</div>
      <button class="primary-button" type="button" :disabled="loadingCollections" @click="createCollection">
        新增 Collection
      </button>
      <div class="archive-actions">
        <button class="secondary-button" type="button" @click="exportWorkspace">匯出 ZIP</button>
        <label class="secondary-button import-button">
          匯入 ZIP
          <input type="file" accept=".zip,application/zip" @change="importWorkspace" />
        </label>
      </div>
      <section class="tree">
        <div class="tree-title">Collections</div>
        <p v-if="!collections.length" class="empty-text">尚無 Collection</p>
        <div v-for="collection in collections" :key="collection.id" class="collection-block">
          <div class="collection-row">
            <button
              class="tree-item collection-item"
              type="button"
              :class="{ active: selectedCollectionId === collection.id && !selectedFolderId && !selectedRequestId }"
              @click="selectCollection(collection.id)"
            >
              {{ collection.name }}
            </button>
            <button
              class="icon-action-button"
              type="button"
              :title="`新增 Folder 到 ${collection.name}`"
              @click="createFolder(collection.id, null)"
            >
              +F
            </button>
            <button
              class="icon-danger-button"
              type="button"
              :disabled="deletingCollection"
              :title="`刪除 Collection ${collection.name}`"
              @click="deleteCollection(collection)"
            >
              刪除
            </button>
          </div>
          <div v-for="folder in folderRows(collection)" :key="folder.id" class="folder-group">
            <div class="folder-row" :style="{ paddingLeft: `${folder.depth * 14}px` }">
              <button
                class="tree-item folder-item"
                type="button"
                :class="{ active: selectedFolderId === folder.id && !selectedRequestId }"
                @click="selectFolder(folder)"
              >
                {{ folder.name }}
              </button>
              <button
                class="icon-action-button"
                type="button"
                :title="`新增子 Folder 到 ${folder.name}`"
                @click="createFolder(collection.id, folder.id)"
              >
                +F
              </button>
              <button
                class="icon-danger-button"
                type="button"
                :disabled="deletingFolder"
                :title="`刪除 Folder ${folder.name}`"
                @click="deleteFolder(collection, folder)"
              >
                刪除
              </button>
            </div>
            <button
              v-for="request in requestsInFolder(collection, folder.id)"
              :key="request.id"
              class="tree-item request-item"
              type="button"
              :style="{ paddingLeft: `${folder.depth * 14 + 38}px` }"
              :class="{ active: selectedRequestId === request.id }"
              @click="selectRequest(request)"
            >
              {{ request.name }}
            </button>
          </div>
          <button
            v-for="request in requestsInFolder(collection, null)"
            :key="request.id"
            class="tree-item request-item"
            type="button"
            :class="{ active: selectedRequestId === request.id }"
            @click="selectRequest(request)"
          >
            {{ request.name }}
          </button>
        </div>
      </section>
      <section class="proto-panel">
        <div class="tree-title">Protos</div>
        <label class="secondary-button proto-upload-button">
          上傳 Proto
          <input type="file" accept=".proto" @change="uploadProto" />
        </label>
        <p v-if="!protos.length" class="empty-text">尚無 Proto</p>
        <button
          v-for="proto in protos"
          :key="proto.protoId"
          class="tree-item proto-item"
          type="button"
          :class="{ active: selectedProto?.protoId === proto.protoId }"
          @click="inspectProto(proto)"
        >
          {{ proto.filename }}
        </button>
        <div v-if="selectedProtoInspect" class="proto-inspect">
          <div class="proto-package">{{ selectedProtoInspect.packageName || '無 package' }}</div>
          <div v-for="service in selectedProtoInspect.services" :key="service.name" class="proto-service">
            <strong>{{ service.name }}</strong>
            <span v-for="method in service.methods" :key="method.name">
              {{ method.name }}({{ method.requestType }}) returns ({{ method.responseType }})
            </span>
          </div>
          <div v-if="selectedProtoInspect.messages.length" class="proto-messages">
            Messages: {{ selectedProtoInspect.messages.join(', ') }}
          </div>
        </div>
      </section>
    </aside>

    <section class="panel">
      <header class="toolbar">
        <select v-model="requestType" aria-label="Request type">
          <option value="HTTP">HTTP</option>
          <option value="GRPC">gRPC</option>
        </select>
        <template v-if="requestType === 'HTTP'">
          <select v-model="method" aria-label="HTTP method">
            <option>GET</option>
            <option>POST</option>
            <option>PUT</option>
            <option>PATCH</option>
            <option>DELETE</option>
          </select>
          <input v-model="url" aria-label="URL" />
        </template>
        <template v-else>
          <input v-model="grpcTarget" aria-label="gRPC target" placeholder="localhost:50051" />
          <input v-model="grpcFullMethod" aria-label="gRPC method" placeholder="package.Service/Method" />
        </template>
        <button class="secondary-button" type="button" :disabled="!selectedCollectionId || saving" @click="saveRequest">
          {{ selectedRequestId ? '儲存' : '另存 Request' }}
        </button>
        <button class="send-button" type="button" :disabled="sending" @click="sendCurrentRequest">
          {{ sending ? '送出中' : '送出' }}
        </button>
      </header>

      <section class="request-meta">
        <label>
          Request 名稱
          <input v-model="requestName" aria-label="Request name" />
        </label>
        <div class="meta-actions">
          <button class="secondary-button" type="button" :disabled="!selectedCollectionId" @click="newDraftRequest">
            新 Request
          </button>
          <button class="danger-button" type="button" :disabled="!selectedRequestId || deleting" @click="deleteSelectedRequest">
            刪除 Request
          </button>
        </div>
        <span class="status-text">{{ workspaceStatus }}</span>
      </section>

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

        <div v-if="activeRequestTab === 'params' && requestType === 'HTTP'" class="editor-pane">
          <label>Query Params</label>
          <textarea v-model="paramsText" spellcheck="false" aria-label="Query Params"></textarea>
        </div>

        <div v-if="activeRequestTab === 'params' && requestType === 'GRPC'" class="grpc-pane">
          <label>
            Host
            <input v-model="grpcHost" placeholder="localhost" />
          </label>
          <label>
            Port
            <input v-model.number="grpcPort" type="number" min="1" max="65535" />
          </label>
          <label>
            Service
            <input v-model="grpcServiceName" placeholder="package.Service" />
          </label>
          <label>
            Method
            <input v-model="grpcMethodName" placeholder="UnaryMethod" />
          </label>
        </div>

        <div v-if="activeRequestTab === 'headers'" class="editor-pane">
          <label>{{ requestType === 'GRPC' ? 'Metadata' : 'Headers' }}</label>
          <textarea v-if="requestType === 'HTTP'" v-model="headersText" spellcheck="false" aria-label="Headers"></textarea>
          <textarea v-else v-model="grpcMetadataText" spellcheck="false" aria-label="Metadata"></textarea>
        </div>

        <div v-if="activeRequestTab === 'body'" class="editor-pane body-pane">
          <div v-if="requestType === 'HTTP'" class="body-toolbar">
            <label for="body-type">Body Type</label>
            <select id="body-type" v-model="bodyType">
              <option value="none">none</option>
              <option value="json">JSON</option>
              <option value="raw">raw text</option>
              <option value="x-www-form-urlencoded">x-www-form-urlencoded</option>
              <option value="form-data">form-data</option>
            </select>
          </div>
          <div v-if="requestType === 'HTTP' && bodyType === 'form-data'" class="form-data-editor">
            <div class="form-data-header">
              <span>Type</span>
              <span>Name</span>
              <span>Value / File</span>
              <span></span>
            </div>
            <div v-for="part in formDataParts" :key="part.id" class="form-data-row">
              <select v-model="part.type" aria-label="form-data type" @change="resetFormDataFile(part)">
                <option value="text">text</option>
                <option value="file">file</option>
              </select>
              <input v-model="part.name" aria-label="form-data name" placeholder="name" />
              <input
                v-if="part.type === 'text'"
                v-model="part.value"
                aria-label="form-data value"
                placeholder="value"
              />
              <label v-else class="file-picker">
                <input type="file" @change="uploadFormDataFile(part, $event)" />
                <span>{{ part.fileName || '選擇檔案' }}</span>
              </label>
              <button class="icon-danger-button" type="button" @click="removeFormDataPart(part.id)">刪除</button>
            </div>
            <button class="secondary-button add-row-button" type="button" @click="addFormDataPart">新增欄位</button>
          </div>
          <label v-if="requestType === 'GRPC'" class="grpc-body-label">JSON Request</label>
          <textarea
            v-if="requestType === 'GRPC' || bodyType !== 'form-data'"
            v-model="activeBodyText"
            spellcheck="false"
            aria-label="Body"
            :disabled="requestType === 'HTTP' && bodyType === 'none'"
          ></textarea>
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
          <label v-if="requestType === 'GRPC'" class="check-line">
            <input v-model="grpcPlaintext" type="checkbox" />
            Plaintext
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
        <div v-else-if="activeResponseTab === 'history'" class="history-list">
          <div class="history-actions">
            <button class="secondary-button" type="button" :disabled="loadingHistory" @click="loadHistory">
              {{ loadingHistory ? '載入中' : '重新整理' }}
            </button>
          </div>
          <p v-if="!historyItems.length" class="empty-text">尚無執行紀錄</p>
          <button
            v-for="item in historyItems"
            :key="item.id"
            class="history-item"
            type="button"
            @click="loadHistoryItem(item)"
          >
            <span class="history-method">{{ item.method }}</span>
            <span class="history-url">{{ item.url }}</span>
            <span class="history-status" :class="{ failed: !item.success }">
              {{ item.success ? item.statusCode : 'ERR' }}
            </span>
            <span class="history-time">{{ formatDateTime(item.createdAt) }}</span>
          </button>
        </div>
        <pre v-else>{{ responseInfo }}</pre>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'

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
  { key: 'history', label: 'History' },
]

const collections = ref([])
const protos = ref([])
const selectedCollectionId = ref(null)
const selectedFolderId = ref(null)
const selectedRequestId = ref(null)
const selectedProto = ref(null)
const selectedProtoInspect = ref(null)
const requestName = ref('健康檢查')
const requestType = ref('HTTP')
const method = ref('GET')
const url = ref('http://localhost:18080/api/health')
const grpcHost = ref('localhost')
const grpcPort = ref(50051)
const grpcServiceName = ref('')
const grpcMethodName = ref('')
const grpcMetadataText = ref('')
const grpcBodyText = ref('{}')
const grpcPlaintext = ref(true)
const paramsText = ref('')
const headersText = ref('Accept=application/json')
const bodyType = ref('none')
const bodyText = ref('{\n  "name": "Post Bubi"\n}')
const formDataParts = ref([newFormDataPart()])
const timeoutMillis = ref(30000)
const followRedirects = ref(true)
const ignoreSslVerification = ref(false)
const activeRequestTab = ref('params')
const activeResponseTab = ref('body')
const loadingCollections = ref(false)
const loadingHistory = ref(false)
const saving = ref(false)
const deleting = ref(false)
const deletingCollection = ref(false)
const deletingFolder = ref(false)
const sending = ref(false)
const response = ref(null)
const errorText = ref('')
const workspaceStatus = ref('')
const historyItems = ref([])

const grpcTarget = computed({
  get() {
    return `${grpcHost.value}:${grpcPort.value}`
  },
  set(value) {
    const [host, port] = value.split(':')
    grpcHost.value = host || ''
    grpcPort.value = Number(port || 50051)
  },
})

const grpcFullMethod = computed({
  get() {
    return grpcServiceName.value && grpcMethodName.value ? `${grpcServiceName.value}/${grpcMethodName.value}` : ''
  },
  set(value) {
    const [service, methodName] = value.split('/')
    grpcServiceName.value = service || ''
    grpcMethodName.value = methodName || ''
  },
})

const activeBodyText = computed({
  get() {
    return requestType.value === 'GRPC' ? grpcBodyText.value : bodyText.value
  },
  set(value) {
    if (requestType.value === 'GRPC') {
      grpcBodyText.value = value
    } else {
      bodyText.value = value
    }
  },
})

const responseSummary = computed(() => {
  if (sending.value) return '送出中'
  if (errorText.value) return '錯誤'
  if (!response.value) return '尚未送出'
  if (requestType.value === 'GRPC') {
    return `${response.value.statusCode} · ${response.value.durationMillis} ms`
  }
  return `${response.value.statusCode} ${response.value.reasonPhrase || ''} · ${response.value.durationMillis} ms · ${response.value.sizeBytes} bytes`
})

const responseBody = computed(() => {
  if (errorText.value) return errorText.value
  if (!response.value) return '尚未送出 request。'
  if (requestType.value === 'GRPC') return prettyText(response.value.body || response.value.errorMessage)
  return prettyText(response.value.body)
})

const responseHeaders = computed(() => {
  if (!response.value) return ''
  const entries = requestType.value === 'GRPC' ? response.value.metadata || [] : response.value.headers
  return entries
    .map((entry) => `${entry.name}: ${entry.value}`)
    .join('\n')
})

const responseInfo = computed(() => {
  if (!response.value) return ''
  if (requestType.value === 'GRPC') {
    return JSON.stringify({
      statusCode: response.value.statusCode,
      statusDescription: response.value.statusDescription,
      durationMillis: response.value.durationMillis,
      errorMessage: response.value.errorMessage,
    }, null, 2)
  }
  return JSON.stringify({
    statusCode: response.value.statusCode,
    reasonPhrase: response.value.reasonPhrase,
    durationMillis: response.value.durationMillis,
    sizeBytes: response.value.sizeBytes,
    bodyBase64Encoded: response.value.bodyBase64Encoded,
  }, null, 2)
})

onMounted(() => {
  loadCollections()
  loadHistory()
  loadProtos()
})

async function loadCollections() {
  loadingCollections.value = true
  try {
    collections.value = await apiJson('/api/collections')
    if (!selectedCollectionId.value && collections.value.length) {
      selectedCollectionId.value = collections.value[0].id
    }
    if (selectedFolderId.value && !folderExists(selectedFolderId.value)) {
      selectedFolderId.value = null
    }
    workspaceStatus.value = collections.value.length ? 'Collection 已載入' : '請先新增 Collection'
  } catch (error) {
    workspaceStatus.value = readableError(error)
  } finally {
    loadingCollections.value = false
  }
}

async function createCollection() {
  const name = window.prompt('Collection 名稱', '預設 Collection')
  if (!name || !name.trim()) {
    return
  }

  try {
    const collection = await apiJson('/api/collections', {
      method: 'POST',
      body: JSON.stringify({ name: name.trim(), description: '' }),
    })
    selectedCollectionId.value = collection.id
    selectedFolderId.value = null
    selectedRequestId.value = null
    await loadCollections()
    workspaceStatus.value = 'Collection 已新增'
  } catch (error) {
    workspaceStatus.value = readableError(error)
  }
}

async function createFolder(collectionId, parentFolderId) {
  const name = window.prompt('Folder 名稱', parentFolderId ? '子資料夾' : '新資料夾')
  if (!name || !name.trim()) {
    return
  }

  try {
    const folder = await apiJson('/api/folders', {
      method: 'POST',
      body: JSON.stringify({
        collectionId,
        parentFolderId,
        name: name.trim(),
        sortOrder: nextFolderSortOrder(collectionId),
      }),
    })
    selectedCollectionId.value = collectionId
    selectedFolderId.value = folder.id
    selectedRequestId.value = null
    await loadCollections()
    workspaceStatus.value = 'Folder 已新增'
  } catch (error) {
    workspaceStatus.value = readableError(error)
  }
}

async function loadProtos() {
  try {
    protos.value = await apiJson('/api/protos')
  } catch (error) {
    workspaceStatus.value = readableError(error)
  }
}

async function uploadProto(event) {
  const file = event.target.files?.[0]
  if (!file) {
    return
  }

  try {
    const formData = new FormData()
    formData.append('file', file)
    const response = await fetch('/api/protos', {
      method: 'POST',
      body: formData,
    })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(`${payload.code || response.status}: ${payload.message || response.statusText}`)
    }
    await loadProtos()
    const uploaded = protos.value.find((proto) => proto.protoId === payload.protoId)
    if (uploaded) {
      await inspectProto(uploaded)
    }
    workspaceStatus.value = `Proto 已上傳：${payload.originalFilename}`
  } catch (error) {
    workspaceStatus.value = readableError(error)
  } finally {
    event.target.value = ''
  }
}

async function inspectProto(proto) {
  selectedProto.value = proto
  try {
    selectedProtoInspect.value = await apiJson(`/api/protos/${proto.protoId}/inspect`)
    workspaceStatus.value = 'Proto inspect 已載入'
  } catch (error) {
    workspaceStatus.value = readableError(error)
  }
}

async function exportWorkspace() {
  try {
    const response = await fetch('/api/workspace/export')
    if (!response.ok) {
      const payload = await response.json()
      throw new Error(`${payload.code || response.status}: ${payload.message || response.statusText}`)
    }
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'post-bubi-workspace.zip'
    link.click()
    URL.revokeObjectURL(url)
    workspaceStatus.value = 'Workspace 已匯出'
  } catch (error) {
    workspaceStatus.value = readableError(error)
  }
}

async function importWorkspace(event) {
  const file = event.target.files?.[0]
  if (!file) {
    return
  }

  try {
    const formData = new FormData()
    formData.append('file', file)
    const response = await fetch('/api/workspace/import', {
      method: 'POST',
      body: formData,
    })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(`${payload.code || response.status}: ${payload.message || response.statusText}`)
    }
    selectedCollectionId.value = null
    selectedRequestId.value = null
    await loadCollections()
    workspaceStatus.value = `匯入完成：${payload.collections} 個 Collection、${payload.requests} 個 Request`
  } catch (error) {
    workspaceStatus.value = readableError(error)
  } finally {
    event.target.value = ''
  }
}

async function deleteCollection(collection) {
  const folderCount = collection.folders?.length || 0
  const requestCount = collection.requests?.length || 0
  const confirmed = window.confirm(`確定刪除 Collection「${collection.name}」？底下 ${folderCount} 個 Folder 與 ${requestCount} 個 Request 會一起刪除。`)
  if (!confirmed) {
    return
  }

  deletingCollection.value = true
  try {
    await apiJson(`/api/collections/${collection.id}`, { method: 'DELETE' })
    if (selectedCollectionId.value === collection.id) {
      selectedCollectionId.value = null
      selectedFolderId.value = null
      selectedRequestId.value = null
      newDraftRequest()
    }
    await loadCollections()
    if (!collections.value.length) {
      selectedCollectionId.value = null
      workspaceStatus.value = 'Collection 已刪除，請新增 Collection'
    } else {
      selectedCollectionId.value = collections.value[0].id
      selectedFolderId.value = null
      workspaceStatus.value = 'Collection 已刪除'
    }
  } catch (error) {
    workspaceStatus.value = readableError(error)
  } finally {
    deletingCollection.value = false
  }
}

function selectCollection(collectionId) {
  selectedCollectionId.value = collectionId
  selectedFolderId.value = null
  selectedRequestId.value = null
  newDraftRequest()
  workspaceStatus.value = '已選擇 Collection'
}

function selectFolder(folder) {
  selectedCollectionId.value = folder.collectionId
  selectedFolderId.value = folder.id
  selectedRequestId.value = null
  newDraftRequest()
  workspaceStatus.value = '已選擇 Folder'
}

async function deleteFolder(collection, folder) {
  const requestCount = requestsInFolder(collection, folder.id).length
  const confirmed = window.confirm(`確定刪除 Folder「${folder.name}」？底下 ${requestCount} 個 Request 會一起刪除。若有子 Folder，請先刪除子 Folder。`)
  if (!confirmed) {
    return
  }

  deletingFolder.value = true
  try {
    await apiJson(`/api/folders/${folder.id}`, { method: 'DELETE' })
    if (selectedFolderId.value === folder.id) {
      selectedFolderId.value = null
      selectedRequestId.value = null
      newDraftRequest()
    }
    await loadCollections()
    workspaceStatus.value = 'Folder 已刪除'
  } catch (error) {
    workspaceStatus.value = readableError(error)
  } finally {
    deletingFolder.value = false
  }
}

function selectRequest(request) {
  selectedCollectionId.value = request.collectionId
  selectedFolderId.value = request.folderId || null
  selectedRequestId.value = request.id
  requestName.value = request.name
  const payload = safeJsonParse(request.payloadJson)
  payload.requestType = payload.requestType || request.type || 'HTTP'
  loadPayloadToEditor(payload)
  workspaceStatus.value = '已載入 Request'
}

function newDraftRequest() {
  selectedRequestId.value = null
  requestName.value = requestType.value === 'GRPC' ? '未命名 gRPC Request' : '未命名 HTTP Request'
  method.value = 'GET'
  url.value = 'http://localhost:18080/api/health'
  paramsText.value = ''
  headersText.value = 'Accept=application/json'
  bodyType.value = 'none'
  bodyText.value = '{\n  "name": "Post Bubi"\n}'
  formDataParts.value = [newFormDataPart()]
  timeoutMillis.value = 30000
  followRedirects.value = true
  ignoreSslVerification.value = false
  response.value = null
  errorText.value = ''
}

async function saveRequest() {
  if (!selectedCollectionId.value) {
    workspaceStatus.value = '請先新增或選擇 Collection'
    return
  }
  if (!requestName.value.trim()) {
    workspaceStatus.value = 'Request 名稱不可空白'
    return
  }

  saving.value = true
  try {
    const payloadJson = JSON.stringify(editorPayload())
    if (selectedRequestId.value) {
      await apiJson(`/api/requests/${selectedRequestId.value}`, {
        method: 'PUT',
        body: JSON.stringify({
          folderId: selectedFolderId.value,
          type: requestType.value,
          name: requestName.value.trim(),
          sortOrder: 0,
          payloadJson,
        }),
      })
      workspaceStatus.value = 'Request 已儲存'
    } else {
      const created = await apiJson('/api/requests', {
        method: 'POST',
        body: JSON.stringify({
          collectionId: selectedCollectionId.value,
          folderId: selectedFolderId.value,
          type: requestType.value,
          name: requestName.value.trim(),
          sortOrder: 0,
          payloadJson,
        }),
      })
      selectedRequestId.value = created.id
      workspaceStatus.value = 'Request 已新增'
    }
    await loadCollections()
  } catch (error) {
    workspaceStatus.value = readableError(error)
  } finally {
    saving.value = false
  }
}

async function deleteSelectedRequest() {
  if (!selectedRequestId.value) {
    return
  }
  const confirmed = window.confirm(`確定刪除 Request「${requestName.value}」？`)
  if (!confirmed) {
    return
  }

  deleting.value = true
  try {
    await apiJson(`/api/requests/${selectedRequestId.value}`, { method: 'DELETE' })
    selectedRequestId.value = null
    newDraftRequest()
    await loadCollections()
    workspaceStatus.value = 'Request 已刪除'
  } catch (error) {
    workspaceStatus.value = readableError(error)
  } finally {
    deleting.value = false
  }
}

async function sendCurrentRequest() {
  if (requestType.value === 'GRPC') {
    await sendGrpcRequest()
  } else {
    await sendHttpRequest()
  }
}

async function sendHttpRequest() {
  sending.value = true
  errorText.value = ''
  response.value = null

  try {
    response.value = await apiJson('/api/http/execute', {
      method: 'POST',
      body: JSON.stringify(executePayload()),
    })
    await loadHistory()
  } catch (error) {
    errorText.value = readableError(error)
    await loadHistory()
  } finally {
    sending.value = false
  }
}

async function sendGrpcRequest() {
  sending.value = true
  errorText.value = ''
  response.value = null

  try {
    response.value = await apiJson('/api/grpc/execute', {
      method: 'POST',
      body: JSON.stringify(grpcExecutePayload()),
    })
  } catch (error) {
    errorText.value = readableError(error)
  } finally {
    sending.value = false
  }
}

async function loadHistory() {
  loadingHistory.value = true
  try {
    historyItems.value = await apiJson('/api/http/history')
  } catch (error) {
    workspaceStatus.value = readableError(error)
  } finally {
    loadingHistory.value = false
  }
}

function loadHistoryItem(item) {
  const payload = safeJsonParse(item.requestJson)
  selectedRequestId.value = payload.requestId || null
  requestName.value = item.method && item.url ? `${item.method} ${item.url}` : '歷史紀錄'
  method.value = payload.method || item.method || 'GET'
  url.value = payload.url || item.url || 'http://localhost:18080/api/health'
  paramsText.value = nameValueToText(payload.params)
  headersText.value = nameValueToText(payload.headers)
  bodyType.value = payload.bodyType || 'none'
  bodyText.value = payload.body || ''
  formDataParts.value = restoreFormDataParts(payload.formData)
  timeoutMillis.value = payload.timeoutMillis || 30000
  followRedirects.value = payload.followRedirects !== false
  ignoreSslVerification.value = payload.ignoreSslVerification === true
  response.value = null
  errorText.value = ''
  activeRequestTab.value = 'params'
  workspaceStatus.value = '已載入 History request'
}

function editorPayload() {
  return {
    requestType: requestType.value,
    folderId: selectedFolderId.value,
    method: method.value,
    url: url.value,
    paramsText: paramsText.value,
    headersText: headersText.value,
    bodyType: bodyType.value,
    body: bodyText.value,
    formData: cleanFormDataParts(),
    timeoutMillis: timeoutMillis.value,
    followRedirects: followRedirects.value,
    ignoreSslVerification: ignoreSslVerification.value,
    grpcHost: grpcHost.value,
    grpcPort: grpcPort.value,
    grpcServiceName: grpcServiceName.value,
    grpcMethodName: grpcMethodName.value,
    grpcMetadataText: grpcMetadataText.value,
    grpcBody: grpcBodyText.value,
    grpcPlaintext: grpcPlaintext.value,
  }
}

function grpcExecutePayload() {
  return {
    host: grpcHost.value,
    port: grpcPort.value,
    plaintext: grpcPlaintext.value,
    metadata: parseNameValueLines(grpcMetadataText.value),
    serviceName: grpcServiceName.value,
    methodName: grpcMethodName.value,
    body: grpcBodyText.value,
    timeoutMillis: timeoutMillis.value,
  }
}

function executePayload() {
  return {
    requestId: selectedRequestId.value,
    method: method.value,
    url: url.value,
    params: parseNameValueLines(paramsText.value),
    headers: parseNameValueLines(headersText.value),
    bodyType: bodyType.value,
    body: bodyType.value === 'none' ? '' : bodyText.value,
    formData: bodyType.value === 'form-data' ? cleanFormDataParts() : [],
    timeoutMillis: timeoutMillis.value,
    followRedirects: followRedirects.value,
    ignoreSslVerification: ignoreSslVerification.value,
  }
}

function folderRows(collection) {
  const folders = [...(collection?.folders || [])]
  const childrenByParent = new Map()
  for (const folder of folders) {
    const key = folder.parentFolderId || null
    const children = childrenByParent.get(key) || []
    children.push(folder)
    childrenByParent.set(key, children)
  }

  for (const children of childrenByParent.values()) {
    children.sort(compareBySortOrder)
  }

  const rows = []
  const append = (parentId, depth) => {
    for (const folder of childrenByParent.get(parentId) || []) {
      rows.push({ ...folder, depth })
      append(folder.id, depth + 1)
    }
  }
  append(null, 0)
  return rows
}

function requestsInFolder(collection, folderId) {
  return [...(collection?.requests || [])]
    .filter((request) => (request.folderId || null) === (folderId || null))
    .sort(compareBySortOrder)
}

function folderExists(folderId) {
  return collections.value.some((collection) => {
    return (collection.folders || []).some((folder) => folder.id === folderId)
  })
}

function nextFolderSortOrder(collectionId) {
  const collection = collections.value.find((item) => item.id === collectionId)
  return (collection?.folders || []).length + 1
}

function compareBySortOrder(left, right) {
  const leftSort = Number.isFinite(left.sortOrder) ? left.sortOrder : 0
  const rightSort = Number.isFinite(right.sortOrder) ? right.sortOrder : 0
  if (leftSort !== rightSort) {
    return leftSort - rightSort
  }
  return left.id - right.id
}

function loadPayloadToEditor(payload) {
  requestType.value = payload.requestType || 'HTTP'
  method.value = payload.method || 'GET'
  url.value = payload.url || 'http://localhost:18080/api/health'
  paramsText.value = payload.paramsText || ''
  headersText.value = payload.headersText || ''
  bodyType.value = payload.bodyType || 'none'
  bodyText.value = payload.body || ''
  formDataParts.value = restoreFormDataParts(payload.formData)
  timeoutMillis.value = payload.timeoutMillis || 30000
  followRedirects.value = payload.followRedirects !== false
  ignoreSslVerification.value = payload.ignoreSslVerification === true
  grpcHost.value = payload.grpcHost || 'localhost'
  grpcPort.value = payload.grpcPort || 50051
  grpcServiceName.value = payload.grpcServiceName || ''
  grpcMethodName.value = payload.grpcMethodName || ''
  grpcMetadataText.value = payload.grpcMetadataText || ''
  grpcBodyText.value = payload.grpcBody || '{}'
  grpcPlaintext.value = payload.grpcPlaintext !== false
  response.value = null
  errorText.value = ''
}

async function apiJson(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
  })

  if (response.status === 204) {
    return null
  }

  const payload = await response.json()
  if (!response.ok) {
    throw new Error(`${payload.code || response.status}: ${payload.message || response.statusText}`)
  }
  return payload
}

function addFormDataPart() {
  formDataParts.value.push(newFormDataPart())
}

function removeFormDataPart(id) {
  formDataParts.value = formDataParts.value.filter((part) => part.id !== id)
  if (!formDataParts.value.length) {
    addFormDataPart()
  }
}

function resetFormDataFile(part) {
  part.fileId = ''
  part.fileName = ''
  part.contentType = ''
  part.sizeBytes = 0
  if (part.type === 'text') {
    part.value = part.value || ''
  }
}

async function uploadFormDataFile(part, event) {
  const file = event.target.files?.[0]
  if (!file) {
    return
  }

  try {
    const formData = new FormData()
    formData.append('file', file)
    const uploaded = await uploadFile(formData)
    part.fileId = uploaded.fileId
    part.fileName = uploaded.originalFilename
    part.contentType = uploaded.contentType
    part.sizeBytes = uploaded.sizeBytes
    workspaceStatus.value = `檔案已上傳：${uploaded.originalFilename}`
  } catch (error) {
    workspaceStatus.value = readableError(error)
    event.target.value = ''
  }
}

async function uploadFile(formData) {
  const response = await fetch('/api/files', {
    method: 'POST',
    body: formData,
  })
  const payload = await response.json()
  if (!response.ok) {
    throw new Error(`${payload.code || response.status}: ${payload.message || response.statusText}`)
  }
  return payload
}

function cleanFormDataParts() {
  return formDataParts.value
    .map((part) => ({
      type: part.type,
      name: part.name.trim(),
      value: part.value,
      fileId: part.fileId,
      fileName: part.fileName,
      contentType: part.contentType,
      enabled: true,
    }))
    .filter((part) => part.name)
    .filter((part) => part.type !== 'file' || part.fileId)
}

function restoreFormDataParts(parts) {
  if (!Array.isArray(parts) || !parts.length) {
    return [newFormDataPart()]
  }
  return parts.map((part) => ({
    id: nextFormDataId(),
    type: part.type === 'file' ? 'file' : 'text',
    name: part.name || '',
    value: part.value || '',
    fileId: part.fileId || '',
    fileName: part.fileName || '',
    contentType: part.contentType || '',
    sizeBytes: part.sizeBytes || 0,
  }))
}

function newFormDataPart() {
  return {
    id: nextFormDataId(),
    type: 'text',
    name: '',
    value: '',
    fileId: '',
    fileName: '',
    contentType: '',
    sizeBytes: 0,
  }
}

function nextFormDataId() {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function nameValueToText(entries) {
  if (!Array.isArray(entries)) {
    return ''
  }
  return entries
    .filter((entry) => entry && entry.name)
    .map((entry) => `${entry.name}=${entry.value || ''}`)
    .join('\n')
}

function formatDateTime(value) {
  if (!value) {
    return ''
  }
  return new Date(value).toLocaleString('zh-TW', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
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

function safeJsonParse(text) {
  try {
    return JSON.parse(text || '{}')
  } catch {
    return {}
  }
}

function readableError(error) {
  return error instanceof Error ? error.message : String(error)
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

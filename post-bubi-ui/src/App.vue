<template>
  <main class="workspace">
    <aside class="sidebar">
      <div class="sidebar-head">
        <div class="brand" aria-label="Post Bubi">
          <img :src="postBubiLogo" alt="Post Bubi" />
          <div class="brand-copy">
            <strong>Post Bubi</strong>
            <span>API Workspace</span>
          </div>
        </div>
        <div class="theme-switch" role="group" aria-label="Theme">
          <button
            type="button"
            :class="{ active: themeMode === 'light' }"
            @click="setTheme('light')"
          >
            <span aria-hidden="true">☀</span>
            Light
          </button>
          <button
            type="button"
            :class="{ active: themeMode === 'dark' }"
            @click="setTheme('dark')"
          >
            <span aria-hidden="true">☾</span>
            Dark
          </button>
        </div>
      </div>
      <div class="sidebar-actions">
        <button class="primary-button full-button" type="button" :disabled="loadingCollections" @click="createCollection">
          <span class="button-icon" aria-hidden="true">＋</span>
          新增 Collection
        </button>
        <div class="archive-actions">
          <button class="secondary-button" type="button" title="匯出工作區 ZIP" @click="exportWorkspace">
            <span class="button-icon" aria-hidden="true">↓</span>
            匯出
          </button>
          <label class="secondary-button import-button">
            <span class="button-icon" aria-hidden="true">↑</span>
            匯入
            <input type="file" accept=".zip,application/zip" @change="importWorkspace" />
          </label>
        </div>
      </div>
      <section class="environment-panel" aria-label="Environment">
        <div class="section-title">
          <span>Environment</span>
          <span class="section-count">{{ activeEnvironmentVariableCount }}</span>
        </div>
        <div class="environment-controls">
          <select v-model="activeEnvironmentId" aria-label="目前 Environment" @change="selectActiveEnvironment">
            <option value="">未選擇 Environment</option>
            <option v-for="environment in environments" :key="environment.id" :value="String(environment.id)">
              {{ environment.name }}
            </option>
          </select>
          <button class="icon-action-button" type="button" title="新增 Environment" aria-label="新增 Environment" @click="startCreateEnvironment">＋</button>
          <button class="icon-action-button" type="button" title="管理 Environment 變數" aria-label="管理 Environment 變數" :disabled="!activeEnvironment" @click="openEnvironmentManager">⚙</button>
        </div>
        <p v-if="activeEnvironment" class="environment-summary">
          {{ activeEnvironment.name }}：送出時替換 <code v-text="'{{variable}}'"></code>
        </p>
        <p v-else class="environment-summary empty-text">未選擇 Environment；含變數的 Request 送出前會提示。</p>
      </section>
      <section class="sidebar-section tree">
        <div class="section-title">
          <span>Collections</span>
          <span class="section-count">{{ collections.length }}</span>
        </div>
        <p v-if="!collections.length" class="empty-text">尚無 Collection</p>
        <div v-for="collection in collections" :key="collection.id" class="collection-block">
          <div class="collection-row">
            <button
              class="tree-item collection-item"
              type="button"
              :class="{ active: selectedCollectionId === collection.id && !selectedFolderId && !selectedRequestId }"
              :title="collection.name"
              @click="selectCollection(collection.id)"
            >
              {{ collection.name }}
            </button>
            <button
              class="tree-menu-button"
              type="button"
              :title="`${collection.name} 操作`"
              @click.stop="toggleTreeMenu(treeMenuId('collection', collection.id))"
            >
              ⋯
            </button>
            <div v-if="openTreeMenu === treeMenuId('collection', collection.id)" class="tree-action-menu">
              <button type="button" @click="prepareNewRequest(collection.id, null)">＋ 新增 Request</button>
              <button type="button" @click="createFolderFromMenu(collection.id, null)">▣ 新增 Folder</button>
              <button type="button" class="danger-menu-item" :disabled="deletingCollection" @click="deleteCollectionFromMenu(collection)">⌫ 刪除 Collection</button>
            </div>
          </div>
          <div v-for="folder in folderRows(collection)" :key="folder.id" class="folder-group">
            <div class="folder-row" :style="{ paddingLeft: `${folder.depth * 14}px` }">
              <button
                class="tree-item folder-item"
                type="button"
                draggable="true"
                :class="{ active: selectedFolderId === folder.id && !selectedRequestId }"
                :title="folder.name"
                @dragstart="startTreeDrag('folder', folder, collection)"
                @dragover.prevent
                @drop="dropTreeItem('folder', folder, collection)"
                @click="selectFolder(folder)"
              >
                {{ folder.name }}
              </button>
              <button
                class="tree-menu-button"
                type="button"
                :title="`${folder.name} 操作`"
                @click.stop="toggleTreeMenu(treeMenuId('folder', folder.id))"
              >
                ⋯
              </button>
              <div v-if="openTreeMenu === treeMenuId('folder', folder.id)" class="tree-action-menu">
                <button type="button" @click="prepareNewRequest(collection.id, folder.id)">＋ 新增 Request</button>
                <button type="button" @click="createFolderFromMenu(collection.id, folder.id)">▣ 新增子 Folder</button>
                <button type="button" class="danger-menu-item" :disabled="deletingFolder" @click="deleteFolderFromMenu(collection, folder)">⌫ 刪除 Folder</button>
              </div>
            </div>
            <div
              v-for="request in requestsInFolder(collection, folder.id)"
              :key="request.id"
              :style="{ paddingLeft: `${folder.depth * 14 + 38}px` }"
              class="request-row"
            >
              <button
                class="tree-item request-item"
                type="button"
                draggable="true"
                :class="{ active: selectedRequestId === request.id }"
                :title="request.name"
                @dragstart="startTreeDrag('request', request, collection)"
                @dragover.prevent
                @drop="dropTreeItem('request', request, collection)"
                @click="selectRequest(request)"
              >
                {{ request.name }}
              </button>
              <button class="tree-menu-button" type="button" title="Request 操作" @click.stop="toggleTreeMenu(treeMenuId('request', request.id))">
                ⋯
              </button>
              <div v-if="openTreeMenu === treeMenuId('request', request.id)" class="tree-action-menu request-action-menu">
                <button type="button" @click="duplicateRequestFromMenu(request)">⧉ 複製 Request</button>
                <button type="button" class="danger-menu-item" :disabled="deleting" @click="deleteRequestFromMenu(request)">⌫ 刪除 Request</button>
              </div>
            </div>
          </div>
          <div
            v-for="request in requestsInFolder(collection, null)"
            :key="request.id"
            class="request-row"
          >
            <button
              class="tree-item request-item"
              type="button"
              draggable="true"
              :class="{ active: selectedRequestId === request.id }"
              :title="request.name"
              @dragstart="startTreeDrag('request', request, collection)"
              @dragover.prevent
              @drop="dropTreeItem('request', request, collection)"
              @click="selectRequest(request)"
            >
              {{ request.name }}
            </button>
            <button class="tree-menu-button" type="button" title="Request 操作" @click.stop="toggleTreeMenu(treeMenuId('request', request.id))">
              ⋯
            </button>
            <div v-if="openTreeMenu === treeMenuId('request', request.id)" class="tree-action-menu request-action-menu">
              <button type="button" @click="duplicateRequestFromMenu(request)">⧉ 複製 Request</button>
              <button type="button" class="danger-menu-item" :disabled="deleting" @click="deleteRequestFromMenu(request)">⌫ 刪除 Request</button>
            </div>
          </div>
        </div>
      </section>
      <section class="sidebar-section proto-panel">
        <div class="section-title">
          <span>Protos</span>
          <span class="section-count">{{ protos.length }}</span>
        </div>
        <label class="secondary-button proto-upload-button">
          <span class="button-icon" aria-hidden="true">↑</span>
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
          :title="proto.filename"
          @click="inspectProto(proto)"
        >
          {{ proto.filename }}
        </button>
        <div v-if="selectedProtoInspect" class="proto-inspect">
          <div class="proto-package">{{ selectedProtoInspect.packageName || '無 package' }}</div>
          <div v-for="service in selectedProtoInspect.services" :key="service.name" class="proto-service">
            <strong>{{ service.name }}</strong>
            <button
              v-for="method in service.methods"
              :key="method.name"
              class="proto-method-button"
              type="button"
              @click="applyProtoMethod(service, method)"
            >
              {{ method.name }}({{ method.requestType }}) returns ({{ method.responseType }})
            </button>
          </div>
          <div v-if="selectedProtoInspect.messages.length" class="proto-messages">
            Messages: {{ selectedProtoInspect.messages.join(', ') }}
          </div>
        </div>
      </section>
    </aside>

    <section class="panel">
      <header class="toolbar" :class="`toolbar-${requestType.toLowerCase()}`">
        <select v-model="requestType" class="type-select" aria-label="Request type">
          <option value="HTTP">HTTP</option>
          <option value="GRPC">gRPC</option>
          <option value="GRPC_BUR">gRPC BUR</option>
        </select>
        <template v-if="requestType === 'HTTP'">
          <select v-model="method" class="method-select" :class="`method-${method.toLowerCase()}`" aria-label="HTTP method">
            <option>GET</option>
            <option>POST</option>
            <option>PUT</option>
            <option>PATCH</option>
            <option>DELETE</option>
          </select>
          <input v-model="url" class="target-input" aria-label="URL" />
        </template>
        <template v-else-if="requestType === 'GRPC'">
          <input v-model="grpcTarget" class="target-input" aria-label="gRPC target" placeholder="localhost:50051" />
          <input v-model="grpcFullMethod" class="target-input" aria-label="gRPC method" placeholder="package.Service/Method" />
        </template>
        <template v-else>
          <input v-model="grpcBurTarget" class="target-input" aria-label="gRPC BUR target" placeholder="10.1.11.34:50003" />
          <input class="target-input fixed-method-input" aria-label="gRPC BUR method" value="Service/rpcPeriphery" disabled />
        </template>
        <button class="secondary-button" type="button" :disabled="!selectedCollectionId || saving" @click="saveRequest">
          <span class="button-icon" aria-hidden="true">◇</span>
          {{ selectedRequestId ? '儲存' : '另存 Request' }}
        </button>
        <button class="send-button" type="button" :disabled="sending" @click="sendCurrentRequest">
          <span class="button-icon send-icon" aria-hidden="true">→</span>
          {{ sending ? '送出中' : '送出' }}
        </button>
        <button v-if="sending" class="cancel-button" type="button" :disabled="cancelling" @click="cancelCurrentRequest">
          <span class="button-icon" aria-hidden="true">×</span>
          {{ cancelling ? '取消中' : '取消' }}
        </button>
      </header>

      <section class="request-meta">
        <div class="request-title">
          <label>
            Request 名稱
            <input v-model="requestName" aria-label="Request name" />
          </label>
          <span class="context-text">{{ selectedContextLabel }}</span>
        </div>
        <span class="status-pill" :class="{ dirty: hasUnsavedChanges }" aria-live="polite">
          {{ hasUnsavedChanges ? '未儲存變更' : (workspaceStatus || 'Ready') }}
        </span>
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

        <div v-if="activeRequestTab === 'params' && requestType === 'GRPC_BUR'" class="grpc-pane">
          <label>
            Host
            <input v-model="grpcBurHost" placeholder="10.1.11.34" />
          </label>
          <label>
            Port
            <input v-model.number="grpcBurPort" type="number" min="1" max="65535" />
          </label>
          <label>
            Service
            <input value="com.bot.fsap.model.grpc.common.Service" disabled />
          </label>
          <label>
            Method
            <input value="rpcPeriphery" disabled />
          </label>
        </div>

        <div v-if="activeRequestTab === 'headers'" class="editor-pane">
          <label>{{ requestType === 'HTTP' ? 'Headers' : 'Metadata' }}</label>
          <div v-if="requestType === 'HTTP'" class="key-value-editor">
            <div class="key-value-header">
              <span></span>
              <span>Key</span>
              <span>Value</span>
              <span></span>
            </div>
            <div
              v-for="header in headerRows"
              :key="header.id"
              class="key-value-row"
              draggable="true"
              @dragstart="startHeaderDrag(header.id)"
              @dragover.prevent
              @drop="dropHeaderRow(header.id)"
            >
              <input v-model="header.enabled" type="checkbox" aria-label="啟用 Header" />
              <input v-model="header.name" aria-label="Header key" placeholder="Header name" />
              <input v-model="header.value" aria-label="Header value" placeholder="Value" />
              <button class="icon-danger-button" type="button" title="刪除 Header" aria-label="刪除 Header" @click="removeHeaderRow(header.id)">×</button>
            </div>
            <button class="secondary-button add-row-button" type="button" @click="addHeaderRow"><span aria-hidden="true">＋</span>新增 Header</button>
          </div>
          <textarea v-else-if="requestType === 'GRPC'" v-model="grpcMetadataText" spellcheck="false" aria-label="Metadata"></textarea>
          <textarea v-else v-model="grpcBurMetadataText" spellcheck="false" aria-label="gRPC BUR Metadata"></textarea>
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
            <button
              v-if="bodyType === 'json'"
              class="secondary-button compact-button"
              type="button"
              @click="formatActiveJson"
            >
              <span aria-hidden="true">{ }</span>
              自動排版
            </button>
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
              <button class="icon-danger-button" type="button" title="刪除欄位" aria-label="刪除欄位" @click="removeFormDataPart(part.id)">×</button>
            </div>
            <button class="secondary-button add-row-button" type="button" @click="addFormDataPart"><span aria-hidden="true">＋</span>新增欄位</button>
          </div>
          <div v-if="requestType === 'GRPC'" class="json-editor-header">
            <label class="grpc-body-label">JSON Request</label>
            <button class="secondary-button compact-button" type="button" @click="formatActiveJson">
              <span aria-hidden="true">{ }</span>
              自動排版
            </button>
          </div>
          <div v-if="requestType === 'GRPC_BUR'" class="bur-composer">
            <div class="bur-body-header">
              <label>GRPC input 參數</label>
              <button class="secondary-button compact-button" type="button" :disabled="sending" @click="previewGrpcBurRequest">
                <span aria-hidden="true">◉</span>
                產生預覽
              </button>
            </div>
            <label>
              Basic Label
              <textarea v-model="grpcBurBasicLabel" spellcheck="false" aria-label="Basic Label"></textarea>
              <span class="field-hint">{{ grpcBurBasicLabel.length }} / {{ grpcBurBasicLabelLength }} chars</span>
            </label>
            <label>
              Text Area
              <textarea v-model="grpcBurTextArea" spellcheck="false" aria-label="Text Area"></textarea>
              <span class="field-hint">{{ grpcBurTextAreaLength || '不限' }} chars limit</span>
            </label>
            <details class="bur-preview" :open="Boolean(grpcBurPreview)">
              <summary>Payload Preview</summary>
              <pre>{{ grpcBurPreviewText }}</pre>
            </details>
          </div>
          <div v-if="isJsonBodyEditor" class="json-editor">
            <pre class="json-highlight" aria-hidden="true" v-html="highlightedBodyText"></pre>
            <textarea
              v-model="activeBodyText"
              class="json-input"
              spellcheck="false"
              aria-label="JSON Body"
              @blur="formatActiveJson({ silent: true })"
              @scroll="syncJsonScroll"
            ></textarea>
          </div>
          <textarea
            v-else-if="requestType !== 'GRPC_BUR' && bodyType !== 'form-data'"
            v-model="activeBodyText"
            spellcheck="false"
            aria-label="Body"
            :disabled="requestType === 'HTTP' && bodyType === 'none'"
          ></textarea>
        </div>

        <div v-if="activeRequestTab === 'settings'" class="settings-pane">
          <label>
            Timeout (ms)
            <input v-model.number="timeoutMillis" type="number" min="1" max="300000" />
            <span class="field-hint">預設 30,000 ms（30 秒），最長 300,000 ms</span>
          </label>
          <label class="check-line">
            <input v-model="followRedirects" type="checkbox" />
            Follow redirects
          </label>
          <label class="check-line">
            <input v-model="ignoreSslVerification" type="checkbox" />
            略過 HTTPS 憑證驗證
          </label>
          <label v-if="requestType === 'GRPC'" class="check-line">
            <input v-model="grpcPlaintext" type="checkbox" />
            Plaintext
          </label>
          <label v-if="requestType === 'GRPC' && !grpcPlaintext" class="check-line">
            <input v-model="grpcIgnoreTlsVerification" type="checkbox" />
            Ignore TLS certificate verification
          </label>
          <label v-if="requestType === 'GRPC_BUR'" class="check-line">
            <input v-model="grpcBurPlaintext" type="checkbox" />
            Plaintext
          </label>
          <label v-if="requestType === 'GRPC_BUR' && !grpcBurPlaintext" class="check-line">
            <input v-model="grpcBurIgnoreTlsVerification" type="checkbox" />
            Ignore TLS certificate verification
          </label>
          <label v-if="requestType === 'GRPC_BUR'">
            Proto ID
            <input v-model="grpcBurProtoId" placeholder="可留空；若 server 無 reflection 則需填入 protoId" />
          </label>
          <label v-if="requestType === 'GRPC_BUR'">
            TCPIP Header Hex
            <input v-model="grpcBurTcpipHeaderHex" />
          </label>
          <label v-if="requestType === 'GRPC_BUR'">
            Basic Label Length
            <input v-model.number="grpcBurBasicLabelLength" type="number" min="1" />
          </label>
          <label v-if="requestType === 'GRPC_BUR'">
            Text Area Length
            <input v-model.number="grpcBurTextAreaLength" type="number" min="0" />
          </label>
        </div>
      </section>

      <section class="response">
        <div class="response-bar">
          <div class="response-heading">
            <strong>Response</strong>
            <span>{{ activeResponseTab }}</span>
          </div>
          <span class="response-summary" :class="responseSummaryClass">{{ responseSummary }}</span>
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
        <pre
          v-if="activeResponseTab === 'body'"
          class="json-viewer"
          :class="{ 'is-empty': !response && !errorText && !cancellationText }"
          v-html="highlightedResponseBody"
        ></pre>
        <pre v-else-if="activeResponseTab === 'headers'">{{ responseHeaders }}</pre>
        <div v-else-if="activeResponseTab === 'decoded'" class="decoded-response">
          <div v-if="requestType === 'GRPC_BUR'" class="decoded-result-list">
            <p v-if="!grpcBurDecodedPayloads.length" class="empty-text">尚無 gRPC BUR 解碼結果</p>
            <article
              v-for="payload in grpcBurDecodedPayloads"
              :key="payload.key"
              class="decoded-result"
              :class="{ failed: payload.error }"
            >
              <div class="decoded-result-head">
                <strong>payload {{ payload.key }}</strong>
                <span>{{ payload.charsets }} / {{ payload.format }} / {{ payload.length }} bytes</span>
              </div>
              <pre>{{ payload.error || payload.text }}</pre>
            </article>
          </div>
          <div v-else>
            <div class="decode-config">
              <div class="decode-help">
                使用 JSON path 指定 base64 欄位，例如 <code>data.payload</code>、<code>items[0].body</code>、<code>items[*].body</code>。
              </div>
              <div class="key-value-header decode-header">
                <span></span>
                <span>JSON Path</span>
                <span>Label</span>
                <span></span>
              </div>
              <div v-for="row in responseDecodeRows" :key="row.id" class="key-value-row decode-row">
                <input v-model="row.enabled" type="checkbox" aria-label="啟用解碼欄位" />
                <input v-model="row.name" aria-label="Response JSON path" placeholder="data.payload" />
                <input v-model="row.value" aria-label="Decoded label" placeholder="顯示名稱，可留空" />
                <button class="icon-danger-button" type="button" title="刪除解碼欄位" aria-label="刪除解碼欄位" @click="removeResponseDecodeRow(row.id)">×</button>
              </div>
              <button class="secondary-button add-row-button" type="button" @click="addResponseDecodeRow"><span aria-hidden="true">＋</span>新增欄位</button>
            </div>
            <div class="decoded-result-list">
              <p v-if="!decodedResponseResults.length" class="empty-text">尚無解碼結果</p>
              <article
                v-for="result in decodedResponseResults"
                :key="`${result.configPath}-${result.matchPath}`"
                class="decoded-result"
                :class="{ failed: result.error }"
              >
                <div class="decoded-result-head">
                  <strong>{{ result.label }}</strong>
                  <span>{{ result.matchPath }}</span>
                </div>
                <pre>{{ result.error || result.decoded }}</pre>
              </article>
            </div>
          </div>
        </div>
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
            :title="`${item.method} ${item.url}`"
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
    <div v-if="showEnvironmentManager" class="modal-backdrop" @click.self="closeEnvironmentManager">
      <section class="environment-modal" role="dialog" aria-modal="true" aria-labelledby="environment-modal-title">
        <header class="environment-modal-head">
          <div>
            <h2 id="environment-modal-title">Environment</h2>
            <p>Request 內的 <code v-text="'{{variable}}'"></code> 會在送出時替換。</p>
          </div>
          <button class="icon-action-button" type="button" title="關閉" aria-label="關閉" @click="closeEnvironmentManager">×</button>
        </header>
        <label class="environment-name-field">
          Environment 名稱
          <input v-model="environmentDraftName" aria-label="Environment 名稱" placeholder="例如 SIT、UAT、Production" />
        </label>
        <div class="environment-variable-editor">
          <div class="environment-variable-header">
            <span>Variable</span>
            <span>Value</span>
            <span></span>
          </div>
          <div v-for="variable in environmentDraftVariables" :key="variable.id" class="environment-variable-row">
            <input v-model="variable.name" aria-label="Variable 名稱" placeholder="baseUrl" />
            <input v-model="variable.value" aria-label="Variable 值" placeholder="https://example.internal" />
            <button class="icon-danger-button" type="button" title="刪除變數" aria-label="刪除變數" @click="removeEnvironmentVariable(variable.id)">×</button>
          </div>
          <button class="secondary-button add-row-button" type="button" @click="addEnvironmentVariable"><span aria-hidden="true">＋</span>新增變數</button>
        </div>
        <footer class="environment-modal-actions">
          <button v-if="environmentDraftId" class="danger-button" type="button" :disabled="deletingEnvironment || savingEnvironment" @click="deleteEnvironment">
            刪除 Environment
          </button>
          <span></span>
          <button class="secondary-button" type="button" :disabled="savingEnvironment" @click="closeEnvironmentManager">取消</button>
          <button class="primary-button" type="button" :disabled="savingEnvironment" @click="saveEnvironment">
            {{ savingEnvironment ? '儲存中' : '儲存 Environment' }}
          </button>
        </footer>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import postBubiLogo from './assets/post-bubi-logo.png'

const requestTabs = [
  { key: 'params', label: 'Params' },
  { key: 'headers', label: 'Headers' },
  { key: 'body', label: 'Body' },
  { key: 'settings', label: 'Settings' },
]

const responseTabs = [
  { key: 'body', label: 'Body' },
  { key: 'headers', label: 'Headers' },
  { key: 'decoded', label: 'Decoded' },
  { key: 'info', label: 'Info' },
  { key: 'history', label: 'History' },
]

const collections = ref([])
const protos = ref([])
const environments = ref([])
const activeEnvironmentId = ref('')
const showEnvironmentManager = ref(false)
const environmentDraftId = ref(null)
const environmentDraftName = ref('')
const environmentDraftVariables = ref([newNameValueRow()])
const savingEnvironment = ref(false)
const deletingEnvironment = ref(false)
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
const grpcProtoId = ref('')
const grpcMetadataText = ref('')
const grpcBodyText = ref('{}')
const grpcPlaintext = ref(true)
const grpcIgnoreTlsVerification = ref(false)
const grpcBurHost = ref('10.1.11.34')
const grpcBurPort = ref(50003)
const grpcBurMetadataText = ref('')
const grpcBurProtoId = ref('')
const grpcBurTcpipHeaderHex = ref('0F 0F 0F 00 02 65 01 F0 F0 F0 0B 0F')
const grpcBurMcsHeader = ref('')
const grpcBurBasicLabel = ref('983000020260708000000000000000  00  NM00100S00                    000000000000000000000080000000000000000000  000                00000000  0   000000000000000          ')
const grpcBurTextArea = ref('yoman   00000000000000123         4000000')
const grpcBurBasicLabelLength = ref(158)
const grpcBurTextAreaLength = ref(0)
const grpcBurPadTextAreaRight = ref(true)
const grpcBurPlaintext = ref(true)
const grpcBurIgnoreTlsVerification = ref(false)
const grpcBurPreview = ref(null)
const paramsText = ref('')
const headersText = ref('Accept=application/json')
const headerRows = ref([newNameValueRow('Accept', 'application/json')])
const bodyType = ref('none')
const bodyText = ref('{\n  "name": "Post Bubi"\n}')
const formDataParts = ref([newFormDataPart()])
const responseDecodeRows = ref([newNameValueRow()])
const timeoutMillis = ref(30000)
const followRedirects = ref(true)
const ignoreSslVerification = ref(true)
const activeRequestTab = ref('params')
const activeResponseTab = ref('body')
const loadingCollections = ref(false)
const loadingHistory = ref(false)
const saving = ref(false)
const deleting = ref(false)
const deletingCollection = ref(false)
const deletingFolder = ref(false)
const sending = ref(false)
const cancelling = ref(false)
const activeExecution = ref(null)
const response = ref(null)
const errorText = ref('')
const cancellationText = ref('')
const workspaceStatus = ref('')
const historyItems = ref([])
const themeMode = ref('light')
const savedEditorState = ref('')
const draggingTreeItem = ref(null)
const draggingHeaderRowId = ref('')
const openTreeMenu = ref('')

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

const grpcBurTarget = computed({
  get() {
    return `${grpcBurHost.value}:${grpcBurPort.value}`
  },
  set(value) {
    const [host, port] = value.split(':')
    grpcBurHost.value = host || ''
    grpcBurPort.value = Number(port || 50003)
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

const isJsonBodyEditor = computed(() => requestType.value === 'GRPC' || bodyType.value === 'json')

const highlightedBodyText = computed(() => highlightJson(activeBodyText.value))

const decodedResponseMap = computed(() => {
  const entries = decodedResponseResults.value
    .filter((result) => !result.error)
    .map((result) => [result.matchPath, result])
  return new Map(entries)
})

const highlightedResponseBody = computed(() => highlightResponseBody())

const grpcBurPreviewText = computed(() => {
  if (!grpcBurPreview.value) {
    return '尚未產生 preview。'
  }
  return JSON.stringify(grpcBurPreview.value, null, 2)
})

const grpcBurDecodedPayloads = computed(() => response.value?.decodedPayloads || [])

const hasUnsavedChanges = computed(() => savedEditorState.value !== snapshotEditorState())

const responseSummary = computed(() => {
  if (sending.value) return '送出中'
  if (cancellationText.value) return '已取消'
  if (errorText.value) return '錯誤'
  if (!response.value) return '尚未送出'
  if (requestType.value === 'GRPC' || requestType.value === 'GRPC_BUR') {
    return `${response.value.statusCode} · ${response.value.durationMillis} ms`
  }
  return `${response.value.statusCode} ${response.value.reasonPhrase || ''} · ${response.value.durationMillis} ms · ${response.value.sizeBytes} bytes`
})

const responseSummaryClass = computed(() => ({
  pending: sending.value,
  cancelled: Boolean(cancellationText.value),
  error: Boolean(errorText.value) || isResponseError(response.value),
  ok: Boolean(response.value) && !errorText.value && !isResponseError(response.value),
}))

const responseBody = computed(() => {
  if (cancellationText.value) return cancellationText.value
  if (errorText.value) return errorText.value
  if (!response.value) return '尚未送出 request。'
  if (requestType.value === 'GRPC' || requestType.value === 'GRPC_BUR') return prettyText(response.value.body || response.value.errorMessage)
  return prettyText(response.value.body)
})

const responseHeaders = computed(() => {
  if (!response.value) return ''
  const entries = requestType.value === 'GRPC' || requestType.value === 'GRPC_BUR' ? response.value.metadata || [] : response.value.headers
  return entries
    .map((entry) => `${entry.name}: ${entry.value}`)
    .join('\n')
})

const responseInfo = computed(() => {
  if (!response.value) return ''
  if (requestType.value === 'GRPC' || requestType.value === 'GRPC_BUR') {
    return JSON.stringify({
      statusCode: response.value.statusCode,
      statusDescription: response.value.statusDescription,
      durationMillis: response.value.durationMillis,
      errorMessage: response.value.errorMessage,
      requestPreview: response.value.requestPreview || null,
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

const rawResponseBody = computed(() => {
  if (errorText.value || !response.value) {
    return ''
  }
  return requestType.value === 'GRPC' || requestType.value === 'GRPC_BUR' ? response.value.body || '' : response.value.body || ''
})

const decodedResponseResults = computed(() => decodeResponseFields())

const selectedContextLabel = computed(() => {
  const collection = collections.value.find((item) => item.id === selectedCollectionId.value)
  if (!collection) {
    return '尚未選擇 Collection'
  }
  const folder = (collection.folders || []).find((item) => item.id === selectedFolderId.value)
  if (folder) {
    return `${collection.name} / ${folder.name}`
  }
  return collection.name
})

const activeEnvironment = computed(() => {
  return environments.value.find((environment) => String(environment.id) === String(activeEnvironmentId.value)) || null
})

const activeEnvironmentVariableCount = computed(() => {
  return (activeEnvironment.value?.variables || []).filter((variable) => variable.key?.trim()).length
})

initializeTheme()
markEditorSaved()

onMounted(() => {
  window.addEventListener('beforeunload', warnBeforeUnload)
  document.addEventListener('click', closeTreeMenuOnOutsideClick)
  loadCollections()
  loadHistory()
  loadProtos()
  loadEnvironments()
})

onBeforeUnmount(() => {
  activeExecution.value?.controller.abort()
  window.removeEventListener('beforeunload', warnBeforeUnload)
  document.removeEventListener('click', closeTreeMenuOnOutsideClick)
})

function initializeTheme() {
  const savedTheme = window.localStorage.getItem('post-bubi-theme')
  setTheme(savedTheme === 'dark' ? 'dark' : 'light')
}

function setTheme(theme) {
  themeMode.value = theme === 'dark' ? 'dark' : 'light'
  document.documentElement.dataset.theme = themeMode.value
  window.localStorage.setItem('post-bubi-theme', themeMode.value)
}

async function loadEnvironments() {
  try {
    environments.value = await apiJson('/api/environments')
    const savedId = window.localStorage.getItem('post-bubi-active-environment-id') || ''
    const currentId = String(activeEnvironmentId.value || savedId)
    const selected = environments.value.find((environment) => String(environment.id) === currentId)
    activeEnvironmentId.value = selected ? String(selected.id) : (environments.value[0] ? String(environments.value[0].id) : '')
    persistActiveEnvironment()
  } catch (error) {
    workspaceStatus.value = readableError(error)
  }
}

function selectActiveEnvironment() {
  persistActiveEnvironment()
  if (activeEnvironment.value) {
    workspaceStatus.value = `目前 Environment：${activeEnvironment.value.name}`
  }
}

function persistActiveEnvironment() {
  if (activeEnvironmentId.value) {
    window.localStorage.setItem('post-bubi-active-environment-id', String(activeEnvironmentId.value))
  } else {
    window.localStorage.removeItem('post-bubi-active-environment-id')
  }
}

function startCreateEnvironment() {
  environmentDraftId.value = null
  environmentDraftName.value = ''
  environmentDraftVariables.value = [newNameValueRow()]
  showEnvironmentManager.value = true
}

function openEnvironmentManager() {
  if (!activeEnvironment.value) {
    startCreateEnvironment()
    return
  }
  environmentDraftId.value = activeEnvironment.value.id
  environmentDraftName.value = activeEnvironment.value.name
  environmentDraftVariables.value = (activeEnvironment.value.variables || []).map((variable) => newNameValueRow(variable.key, variable.value))
  if (!environmentDraftVariables.value.length) {
    environmentDraftVariables.value = [newNameValueRow()]
  }
  showEnvironmentManager.value = true
}

function closeEnvironmentManager() {
  if (savingEnvironment.value || deletingEnvironment.value) {
    return
  }
  showEnvironmentManager.value = false
}

function addEnvironmentVariable() {
  environmentDraftVariables.value.push(newNameValueRow())
}

function removeEnvironmentVariable(id) {
  environmentDraftVariables.value = environmentDraftVariables.value.filter((variable) => variable.id !== id)
  if (!environmentDraftVariables.value.length) {
    addEnvironmentVariable()
  }
}

async function saveEnvironment() {
  const name = environmentDraftName.value.trim()
  if (!name) {
    workspaceStatus.value = 'Environment 名稱不可空白'
    return
  }

  savingEnvironment.value = true
  try {
    const payload = {
      name,
      variables: environmentDraftVariables.value
        .map((variable) => ({ key: variable.name.trim(), value: variable.value || '' }))
        .filter((variable) => variable.key),
    }
    const saved = environmentDraftId.value
      ? await apiJson(`/api/environments/${environmentDraftId.value}`, { method: 'PUT', body: JSON.stringify(payload) })
      : await apiJson('/api/environments', { method: 'POST', body: JSON.stringify(payload) })
    await loadEnvironments()
    activeEnvironmentId.value = String(saved.id)
    persistActiveEnvironment()
    showEnvironmentManager.value = false
    workspaceStatus.value = `Environment 已儲存：${saved.name}`
  } catch (error) {
    workspaceStatus.value = readableError(error)
  } finally {
    savingEnvironment.value = false
  }
}

async function deleteEnvironment() {
  if (!environmentDraftId.value) {
    return
  }
  const confirmed = window.confirm(`確定刪除 Environment「${environmentDraftName.value}」？Request 內容不會被修改。`)
  if (!confirmed) {
    return
  }

  deletingEnvironment.value = true
  try {
    await apiJson(`/api/environments/${environmentDraftId.value}`, { method: 'DELETE' })
    if (String(activeEnvironmentId.value) === String(environmentDraftId.value)) {
      activeEnvironmentId.value = ''
    }
    await loadEnvironments()
    showEnvironmentManager.value = false
    workspaceStatus.value = 'Environment 已刪除'
  } catch (error) {
    workspaceStatus.value = readableError(error)
  } finally {
    deletingEnvironment.value = false
  }
}

function formatActiveJson(options = {}) {
  if (!isJsonBodyEditor.value) {
    return
  }
  const value = activeBodyText.value.trim()
  if (!value) {
    activeBodyText.value = ''
    return
  }
  try {
    activeBodyText.value = `${JSON.stringify(JSON.parse(value), null, 2)}\n`
    if (!options.silent) {
      workspaceStatus.value = 'JSON 已自動排版'
    }
  } catch (error) {
    if (!options.silent) {
      workspaceStatus.value = 'JSON 格式錯誤，無法自動排版'
    }
  }
}

function syncJsonScroll(event) {
  const highlight = event.target.previousElementSibling
  if (!highlight) {
    return
  }
  highlight.scrollTop = event.target.scrollTop
  highlight.scrollLeft = event.target.scrollLeft
}

function highlightJson(value) {
  const escaped = escapeHtml(value || '')
  const highlighted = escaped.replace(
    /("(?:\\u[a-fA-F0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|-?\d+(?:\.\d+)?(?:[eE][+\-]?\d+)?\b|\btrue\b|\bfalse\b|\bnull\b)/g,
    (match) => {
      let className = 'json-number'
      if (match.endsWith(':')) {
        className = 'json-key'
      } else if (match.startsWith('"')) {
        className = 'json-string'
      } else if (match === 'true' || match === 'false') {
        className = 'json-boolean'
      } else if (match === 'null') {
        className = 'json-null'
      }
      return `<span class="${className}">${match}</span>`
    },
  )
  return highlighted || '<span class="json-placeholder">{}</span>'
}

function highlightResponseBody() {
  if (errorText.value || !response.value || !rawResponseBody.value) {
    return highlightJson(responseBody.value)
  }
  const parsed = tryJsonParse(rawResponseBody.value)
  if (!parsed || typeof parsed !== 'object') {
    return highlightJson(responseBody.value)
  }
  return renderJsonValue(parsed, '$', 0, decodedResponseMap.value)
}

function renderJsonValue(value, path, depth, decodedMap) {
  const decoded = decodedMap.get(path)
  if (decoded) {
    return renderDecodedJsonValue(decoded, depth)
  }
  if (Array.isArray(value)) {
    if (!value.length) {
      return '[]'
    }
    const childDepth = depth + 1
    const rows = value.map((item, index) => {
      return `${indent(childDepth)}${renderJsonValue(item, `${path}[${index}]`, childDepth, decodedMap)}`
    })
    return `[\n${rows.join(',\n')}\n${indent(depth)}]`
  }
  if (value && typeof value === 'object') {
    const entries = Object.entries(value)
    if (!entries.length) {
      return '{}'
    }
    const childDepth = depth + 1
    const rows = entries.map(([key, item]) => {
      const keyPath = `${path}.${key}`
      return `${indent(childDepth)}<span class="json-key">${escapeHtml(JSON.stringify(key))}:</span> ${renderJsonValue(item, keyPath, childDepth, decodedMap)}`
    })
    return `{\n${rows.join(',\n')}\n${indent(depth)}}`
  }
  return renderJsonPrimitive(value)
}

function renderDecodedJsonValue(decoded, depth) {
  const parsed = tryJsonParse(decoded.decoded)
  const rendered = parsed && typeof parsed === 'object'
    ? renderJsonValue(parsed, decoded.matchPath, depth, new Map())
    : renderJsonPrimitive(decoded.decoded)
  return `<span class="json-decoded-field" title="原始 base64: ${escapeAttribute(decoded.original || '')}">${rendered}</span><span class="json-decoded-badge">decoded</span>`
}

function renderJsonPrimitive(value) {
  if (value === null) {
    return '<span class="json-null">null</span>'
  }
  if (typeof value === 'string') {
    return `<span class="json-string">${escapeHtml(JSON.stringify(value))}</span>`
  }
  if (typeof value === 'number') {
    return `<span class="json-number">${escapeHtml(String(value))}</span>`
  }
  if (typeof value === 'boolean') {
    return `<span class="json-boolean">${value}</span>`
  }
  return `<span class="json-string">${escapeHtml(JSON.stringify(String(value)))}</span>`
}

function indent(depth) {
  return '  '.repeat(depth)
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

function escapeAttribute(value) {
  return escapeHtml(value).replaceAll('"', '&quot;')
}

function warnBeforeUnload(event) {
  if (!hasUnsavedChanges.value) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}

function confirmDiscardUnsavedChanges() {
  if (!hasUnsavedChanges.value) {
    return true
  }
  return window.confirm('目前 Request 有未儲存變更，切換後會遺失。是否繼續？')
}

function snapshotEditorState() {
  return JSON.stringify({
    selectedRequestId: selectedRequestId.value,
    selectedCollectionId: selectedCollectionId.value,
    selectedFolderId: selectedFolderId.value,
    requestName: requestName.value,
    payload: editorPayload(),
  })
}

function markEditorSaved() {
  savedEditorState.value = snapshotEditorState()
}

async function loadCollections() {
  loadingCollections.value = true
  const wasDirty = hasUnsavedChanges.value
  let autoSelectedCollection = false
  try {
    collections.value = await apiJson('/api/collections')
    if (!selectedCollectionId.value && collections.value.length) {
      selectedCollectionId.value = collections.value[0].id
      autoSelectedCollection = true
    }
    if (selectedFolderId.value && !folderExists(selectedFolderId.value)) {
      selectedFolderId.value = null
    }
    workspaceStatus.value = collections.value.length ? 'Collection 已載入' : '請先新增 Collection'
    if (autoSelectedCollection && !wasDirty) {
      markEditorSaved()
    }
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

function treeMenuId(kind, id) {
  return `${kind}:${id}`
}

function toggleTreeMenu(menuId) {
  openTreeMenu.value = openTreeMenu.value === menuId ? '' : menuId
}

function closeTreeMenu() {
  openTreeMenu.value = ''
}

function closeTreeMenuOnOutsideClick(event) {
  if (!openTreeMenu.value) {
    return
  }
  const target = event.target
  if (target instanceof Element && target.closest('.tree-action-menu')) {
    return
  }
  closeTreeMenu()
}

function prepareNewRequest(collectionId, folderId) {
  closeTreeMenu()
  if (!confirmDiscardUnsavedChanges()) {
    return
  }
  selectedCollectionId.value = collectionId
  selectedFolderId.value = folderId || null
  newDraftRequest({ force: true })
  activeRequestTab.value = 'params'
  workspaceStatus.value = '已建立新 Request 草稿'
}

async function createFolderFromMenu(collectionId, parentFolderId) {
  closeTreeMenu()
  await createFolder(collectionId, parentFolderId)
}

async function deleteCollectionFromMenu(collection) {
  closeTreeMenu()
  await deleteCollection(collection)
}

async function deleteFolderFromMenu(collection, folder) {
  closeTreeMenu()
  await deleteFolder(collection, folder)
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

function applyProtoMethod(service, methodDefinition) {
  if (!confirmDiscardUnsavedChanges()) {
    return
  }
  const serviceName = fullProtoServiceName(service.name)
  requestType.value = 'GRPC'
  grpcServiceName.value = serviceName
  grpcMethodName.value = methodDefinition.name
  grpcProtoId.value = selectedProto.value?.protoId || ''
  grpcBodyText.value = '{}'
  requestName.value = `${serviceName}/${methodDefinition.name}`
  activeRequestTab.value = 'body'
  response.value = null
  errorText.value = ''
  cancellationText.value = ''
  workspaceStatus.value = '已套用 Proto method 到 gRPC request'
}

function fullProtoServiceName(serviceName) {
  const packageName = selectedProtoInspect.value?.packageName || ''
  if (!packageName || serviceName.includes('.')) {
    return serviceName
  }
  return `${packageName}.${serviceName}`
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
  if (!confirmDiscardUnsavedChanges()) {
    event.target.value = ''
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
    newDraftRequest({ force: true })
    await loadProtos()
    await loadEnvironments()
    workspaceStatus.value = `匯入完成：${payload.collections} 個 Collection、${payload.requests} 個 Request、${payload.protos || 0} 個 Proto、${payload.environments || 0} 個 Environment`
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
      newDraftRequest({ force: true })
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

async function duplicateRequest(request) {
  try {
    const duplicated = await apiJson(`/api/requests/${request.id}/duplicate`, { method: 'POST' })
    await loadCollections()
    workspaceStatus.value = `Request 已複製：${duplicated.name}`
  } catch (error) {
    workspaceStatus.value = readableError(error)
  }
}

async function duplicateRequestFromMenu(request) {
  closeTreeMenu()
  await duplicateRequest(request)
}

async function deleteRequestFromMenu(request) {
  closeTreeMenu()
  const confirmed = window.confirm(`確定刪除 Request「${request.name}」？`)
  if (!confirmed) {
    return
  }

  deleting.value = true
  try {
    await apiJson(`/api/requests/${request.id}`, { method: 'DELETE' })
    if (selectedRequestId.value === request.id) {
      selectedRequestId.value = null
      newDraftRequest({ force: true })
    }
    await loadCollections()
    workspaceStatus.value = 'Request 已刪除'
  } catch (error) {
    workspaceStatus.value = readableError(error)
  } finally {
    deleting.value = false
  }
}

function startTreeDrag(kind, item, collection) {
  draggingTreeItem.value = {
    kind,
    id: item.id,
    collectionId: collection.id,
    parentId: kind === 'folder' ? item.parentFolderId || null : item.folderId || null,
  }
}

async function dropTreeItem(kind, target, collection) {
  const dragged = draggingTreeItem.value
  draggingTreeItem.value = null
  if (!dragged || dragged.kind !== kind || dragged.id === target.id || dragged.collectionId !== collection.id) {
    return
  }
  const targetParentId = kind === 'folder' ? target.parentFolderId || null : target.folderId || null
  if (dragged.parentId !== targetParentId) {
    workspaceStatus.value = '目前僅支援同層排序'
    return
  }

  try {
    if (kind === 'folder') {
      const siblings = (collection.folders || [])
        .filter((folder) => (folder.parentFolderId || null) === targetParentId)
        .sort(compareBySortOrder)
      const ordered = reorderItems(siblings, dragged.id, target.id)
      await Promise.all(ordered.map((folder, index) => updateFolderSortOrder(folder, index + 1)))
    } else {
      const siblings = requestsInFolder(collection, targetParentId)
      const ordered = reorderItems(siblings, dragged.id, target.id)
      await Promise.all(ordered.map((request, index) => updateRequestSortOrder(request, index + 1)))
    }
    await loadCollections()
    workspaceStatus.value = '排序已更新'
  } catch (error) {
    workspaceStatus.value = readableError(error)
  }
}

function reorderItems(items, draggedId, targetId) {
  const ordered = [...items]
  const draggedIndex = ordered.findIndex((item) => item.id === draggedId)
  const targetIndex = ordered.findIndex((item) => item.id === targetId)
  if (draggedIndex < 0 || targetIndex < 0) {
    return ordered
  }
  const [dragged] = ordered.splice(draggedIndex, 1)
  ordered.splice(targetIndex, 0, dragged)
  return ordered
}

function updateFolderSortOrder(folder, sortOrder) {
  return apiJson(`/api/folders/${folder.id}`, {
    method: 'PUT',
    body: JSON.stringify({
      parentFolderId: folder.parentFolderId || null,
      name: folder.name,
      sortOrder,
    }),
  })
}

function updateRequestSortOrder(request, sortOrder) {
  return apiJson(`/api/requests/${request.id}`, {
    method: 'PUT',
    body: JSON.stringify({
      folderId: request.folderId || null,
      type: request.type,
      name: request.name,
      sortOrder,
      payloadJson: request.payloadJson,
    }),
  })
}

function selectCollection(collectionId) {
  if (selectedCollectionId.value === collectionId && !selectedFolderId.value && !selectedRequestId.value) {
    return
  }
  if (!confirmDiscardUnsavedChanges()) {
    return
  }
  selectedCollectionId.value = collectionId
  selectedFolderId.value = null
  selectedRequestId.value = null
  newDraftRequest({ force: true })
  workspaceStatus.value = '已選擇 Collection'
}

function selectFolder(folder) {
  if (selectedFolderId.value === folder.id && !selectedRequestId.value) {
    return
  }
  if (!confirmDiscardUnsavedChanges()) {
    return
  }
  selectedCollectionId.value = folder.collectionId
  selectedFolderId.value = folder.id
  selectedRequestId.value = null
  newDraftRequest({ force: true })
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
      newDraftRequest({ force: true })
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
  if (selectedRequestId.value === request.id) {
    return
  }
  if (!confirmDiscardUnsavedChanges()) {
    return
  }
  selectedCollectionId.value = request.collectionId
  selectedFolderId.value = request.folderId || null
  selectedRequestId.value = request.id
  requestName.value = request.name
  const payload = safeJsonParse(request.payloadJson)
  payload.requestType = payload.requestType || request.type || 'HTTP'
  loadPayloadToEditor(payload)
  markEditorSaved()
  workspaceStatus.value = '已載入 Request'
}

function newDraftRequest(options = {}) {
  if (options?.force !== true && !confirmDiscardUnsavedChanges()) {
    return
  }
  selectedRequestId.value = null
  requestName.value = defaultRequestName()
  method.value = 'GET'
  url.value = 'http://localhost:18080/api/health'
  paramsText.value = ''
  headersText.value = 'Accept=application/json'
  headerRows.value = [newNameValueRow('Accept', 'application/json')]
  bodyType.value = 'none'
  bodyText.value = '{\n  "name": "Post Bubi"\n}'
  formDataParts.value = [newFormDataPart()]
  responseDecodeRows.value = [newNameValueRow()]
  timeoutMillis.value = 30000
  followRedirects.value = true
  ignoreSslVerification.value = true
  grpcBurPreview.value = null
  response.value = null
  errorText.value = ''
  cancellationText.value = ''
  markEditorSaved()
}

function defaultRequestName() {
  if (requestType.value === 'GRPC_BUR') {
    return '未命名 gRPC BUR Request'
  }
  return requestType.value === 'GRPC' ? '未命名 gRPC Request' : '未命名 HTTP Request'
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
    markEditorSaved()
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
    newDraftRequest({ force: true })
    await loadCollections()
    workspaceStatus.value = 'Request 已刪除'
  } catch (error) {
    workspaceStatus.value = readableError(error)
  } finally {
    deleting.value = false
  }
}

async function sendCurrentRequest() {
  activeResponseTab.value = 'body'
  if (requestType.value === 'GRPC_BUR') {
    await sendGrpcBurRequest()
  } else if (requestType.value === 'GRPC') {
    await sendGrpcRequest()
  } else {
    await sendHttpRequest()
  }
}

async function sendHttpRequest() {
  sending.value = true
  errorText.value = ''
  cancellationText.value = ''
  response.value = null
  const execution = startExecution()

  try {
    response.value = await apiJson('/api/http/execute', {
      method: 'POST',
      signal: execution.controller.signal,
      body: JSON.stringify(withExecutionId(resolveExecutionPayload(executePayload()), execution.id)),
    })
    await loadHistory()
  } catch (error) {
    if (!isExecutionCancelled(error, execution)) {
      errorText.value = readableError(error)
      await loadHistory()
    }
  } finally {
    finishExecution(execution)
  }
}

async function sendGrpcRequest() {
  sending.value = true
  errorText.value = ''
  cancellationText.value = ''
  response.value = null
  const execution = startExecution()

  try {
    response.value = await apiJson('/api/grpc/execute', {
      method: 'POST',
      signal: execution.controller.signal,
      body: JSON.stringify(withExecutionId(resolveExecutionPayload(grpcExecutePayload()), execution.id)),
    })
  } catch (error) {
    if (!isExecutionCancelled(error, execution)) {
      errorText.value = readableError(error)
    }
  } finally {
    finishExecution(execution)
  }
}

async function previewGrpcBurRequest() {
  errorText.value = ''
  try {
    grpcBurPreview.value = await apiJson('/api/grpc-bur/preview', {
      method: 'POST',
      body: JSON.stringify(resolveExecutionPayload(grpcBurExecutePayload())),
    })
    workspaceStatus.value = 'gRPC BUR payload preview 已產生'
  } catch (error) {
    workspaceStatus.value = readableError(error)
  }
}

async function sendGrpcBurRequest() {
  sending.value = true
  errorText.value = ''
  cancellationText.value = ''
  response.value = null
  const execution = startExecution()

  try {
    response.value = await apiJson('/api/grpc-bur/execute', {
      method: 'POST',
      signal: execution.controller.signal,
      body: JSON.stringify(withExecutionId(resolveExecutionPayload(grpcBurExecutePayload()), execution.id)),
    })
    grpcBurPreview.value = response.value.requestPreview || null
  } catch (error) {
    if (!isExecutionCancelled(error, execution)) {
      errorText.value = readableError(error)
    }
  } finally {
    finishExecution(execution)
  }
}

function startExecution() {
  const execution = {
    id: window.crypto?.randomUUID?.() || `execution-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    controller: new AbortController(),
  }
  activeExecution.value = execution
  return execution
}

function withExecutionId(payload, executionId) {
  return {
    ...payload,
    executionId,
  }
}

function finishExecution(execution) {
  if (activeExecution.value?.id === execution.id) {
    activeExecution.value = null
    cancelling.value = false
    sending.value = false
  }
}

async function cancelCurrentRequest() {
  const execution = activeExecution.value
  if (!execution || cancelling.value) {
    return
  }

  cancelling.value = true
  try {
    const result = await apiJson(`/api/executions/${encodeURIComponent(execution.id)}/cancel`, { method: 'POST' })
    if (result?.cancelled) {
      cancellationText.value = '請求已由使用者取消。'
      execution.controller.abort()
      workspaceStatus.value = '已取消送出中的請求'
    } else {
      workspaceStatus.value = '請求已完成，無需取消'
    }
  } catch (error) {
    workspaceStatus.value = readableError(error)
  } finally {
    if (activeExecution.value?.id === execution.id) {
      cancelling.value = false
    }
  }
}

function isExecutionCancelled(error, execution) {
  return execution.controller.signal.aborted || error?.name === 'AbortError'
}

function resolveExecutionPayload(payload) {
  const variables = new Map(
    (activeEnvironment.value?.variables || [])
      .filter((variable) => variable?.key?.trim())
      .map((variable) => [variable.key.trim(), variable.value || '']),
  )
  const unresolved = new Set()
  const circular = new Set()

  const resolveText = (value, resolving = new Set()) => String(value).replace(/{{\s*([^{}]+?)\s*}}/g, (placeholder, rawKey) => {
    const key = rawKey.trim()
    if (!variables.has(key)) {
      unresolved.add(key)
      return placeholder
    }
    if (resolving.has(key)) {
      circular.add(key)
      return placeholder
    }
    const nextResolving = new Set(resolving)
    nextResolving.add(key)
    return resolveText(variables.get(key), nextResolving)
  })

  const resolveValue = (value) => {
    if (typeof value === 'string') {
      return resolveText(value)
    }
    if (Array.isArray(value)) {
      return value.map(resolveValue)
    }
    if (value && typeof value === 'object') {
      return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, resolveValue(item)]))
    }
    return value
  }

  const resolved = resolveValue(payload)
  if (circular.size) {
    throw new Error(`ENVIRONMENT_VARIABLE_CIRCULAR: Environment 變數循環引用：${[...circular].join(', ')}`)
  }
  if (unresolved.size) {
    const environmentName = activeEnvironment.value?.name || '未選擇 Environment'
    throw new Error(`ENVIRONMENT_VARIABLE_NOT_FOUND: ${environmentName} 找不到變數：${[...unresolved].join(', ')}`)
  }
  return resolved
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
  if (!confirmDiscardUnsavedChanges()) {
    return
  }
  const payload = safeJsonParse(item.requestJson)
  selectedRequestId.value = payload.requestId || null
  requestName.value = item.method && item.url ? `${item.method} ${item.url}` : '歷史紀錄'
  method.value = payload.method || item.method || 'GET'
  url.value = payload.url || item.url || 'http://localhost:18080/api/health'
  paramsText.value = nameValueToText(payload.params)
  headersText.value = nameValueToText(payload.headers)
  headerRows.value = restoreNameValueRows(payload.headers)
  bodyType.value = payload.bodyType || 'none'
  bodyText.value = payload.body || ''
  formDataParts.value = restoreFormDataParts(payload.formData)
  responseDecodeRows.value = restoreNameValueRows(payload.responseDecodeRows)
  timeoutMillis.value = payload.timeoutMillis || 30000
  followRedirects.value = payload.followRedirects !== false
  ignoreSslVerification.value = payload.ignoreSslVerification !== false
  response.value = null
  errorText.value = ''
  cancellationText.value = ''
  markEditorSaved()
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
    headersText: nameValueRowsToText(headerRows.value),
    headers: serializeNameValueRows(headerRows.value),
    bodyType: bodyType.value,
    body: bodyText.value,
    formData: cleanFormDataParts(),
    responseDecodeRows: serializeNameValueRows(responseDecodeRows.value),
    timeoutMillis: timeoutMillis.value,
    followRedirects: followRedirects.value,
    ignoreSslVerification: ignoreSslVerification.value,
    grpcHost: grpcHost.value,
    grpcPort: grpcPort.value,
    grpcServiceName: grpcServiceName.value,
    grpcMethodName: grpcMethodName.value,
    grpcProtoId: grpcProtoId.value,
    grpcMetadataText: grpcMetadataText.value,
    grpcBody: grpcBodyText.value,
    grpcPlaintext: grpcPlaintext.value,
    grpcIgnoreTlsVerification: grpcIgnoreTlsVerification.value,
    grpcBurHost: grpcBurHost.value,
    grpcBurPort: grpcBurPort.value,
    grpcBurMetadataText: grpcBurMetadataText.value,
    grpcBurProtoId: grpcBurProtoId.value,
    grpcBurTcpipHeaderHex: grpcBurTcpipHeaderHex.value,
    grpcBurMcsHeader: grpcBurMcsHeader.value,
    grpcBurBasicLabel: grpcBurBasicLabel.value,
    grpcBurTextArea: grpcBurTextArea.value,
    grpcBurBasicLabelLength: grpcBurBasicLabelLength.value,
    grpcBurTextAreaLength: grpcBurTextAreaLength.value,
    grpcBurPadTextAreaRight: grpcBurPadTextAreaRight.value,
    grpcBurPlaintext: grpcBurPlaintext.value,
    grpcBurIgnoreTlsVerification: grpcBurIgnoreTlsVerification.value,
  }
}

function grpcExecutePayload() {
  return {
    host: grpcHost.value,
    port: grpcPort.value,
    plaintext: grpcPlaintext.value,
    ignoreTlsVerification: grpcIgnoreTlsVerification.value,
    metadata: parseNameValueLines(grpcMetadataText.value),
    protoId: grpcProtoId.value || null,
    serviceName: grpcServiceName.value,
    methodName: grpcMethodName.value,
    body: grpcBodyText.value,
    timeoutMillis: timeoutMillis.value,
  }
}

function grpcBurExecutePayload() {
  return {
    host: grpcBurHost.value,
    port: grpcBurPort.value,
    plaintext: grpcBurPlaintext.value,
    ignoreTlsVerification: grpcBurIgnoreTlsVerification.value,
    metadataText: grpcBurMetadataText.value,
    protoId: grpcBurProtoId.value || null,
    tcpipHeaderHex: grpcBurTcpipHeaderHex.value,
    mcsHeader: grpcBurMcsHeader.value,
    basicLabel: grpcBurBasicLabel.value,
    textArea: grpcBurTextArea.value,
    timeoutMillis: timeoutMillis.value,
    settings: {
      mcsHeaderLength: 72,
      basicLabelLength: grpcBurBasicLabelLength.value,
      textAreaLength: grpcBurTextAreaLength.value || null,
      padTextAreaRight: grpcBurPadTextAreaRight.value,
    },
  }
}

function executePayload() {
  return {
    requestId: selectedRequestId.value,
    method: method.value,
    url: url.value,
    params: parseNameValueLines(paramsText.value),
    headers: cleanNameValueRows(headerRows.value),
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
  headerRows.value = restoreNameValueRows(payload.headers || parseNameValueLines(payload.headersText || ''))
  bodyType.value = payload.bodyType || 'none'
  bodyText.value = payload.body || ''
  formDataParts.value = restoreFormDataParts(payload.formData)
  responseDecodeRows.value = restoreNameValueRows(payload.responseDecodeRows)
  timeoutMillis.value = payload.timeoutMillis || 30000
  followRedirects.value = payload.followRedirects !== false
  ignoreSslVerification.value = payload.ignoreSslVerification !== false
  grpcHost.value = payload.grpcHost || 'localhost'
  grpcPort.value = payload.grpcPort || 50051
  grpcServiceName.value = payload.grpcServiceName || ''
  grpcMethodName.value = payload.grpcMethodName || ''
  grpcProtoId.value = payload.grpcProtoId || ''
  grpcMetadataText.value = payload.grpcMetadataText || ''
  grpcBodyText.value = payload.grpcBody || '{}'
  grpcPlaintext.value = payload.grpcPlaintext !== false
  grpcIgnoreTlsVerification.value = payload.grpcIgnoreTlsVerification === true
  grpcBurHost.value = payload.grpcBurHost || '10.1.11.34'
  grpcBurPort.value = payload.grpcBurPort || 50003
  grpcBurMetadataText.value = payload.grpcBurMetadataText || ''
  grpcBurProtoId.value = payload.grpcBurProtoId || ''
  grpcBurTcpipHeaderHex.value = payload.grpcBurTcpipHeaderHex || '0F 0F 0F 00 02 65 01 F0 F0 F0 0B 0F'
  grpcBurMcsHeader.value = payload.grpcBurMcsHeader || ''
  grpcBurBasicLabel.value = payload.grpcBurBasicLabel || '983000020260708000000000000000  00  NM00100S00                    000000000000000000000080000000000000000000  000                00000000  0   000000000000000          '
  grpcBurTextArea.value = payload.grpcBurTextArea || 'yoman   00000000000000123         4000000'
  grpcBurBasicLabelLength.value = payload.grpcBurBasicLabelLength || 158
  grpcBurTextAreaLength.value = payload.grpcBurTextAreaLength || 0
  grpcBurPadTextAreaRight.value = payload.grpcBurPadTextAreaRight !== false
  grpcBurPlaintext.value = payload.grpcBurPlaintext !== false
  grpcBurIgnoreTlsVerification.value = payload.grpcBurIgnoreTlsVerification === true
  grpcBurPreview.value = null
  response.value = null
  errorText.value = ''
  cancellationText.value = ''
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

function addHeaderRow() {
  headerRows.value.push(newNameValueRow())
}

function removeHeaderRow(id) {
  headerRows.value = headerRows.value.filter((row) => row.id !== id)
  if (!headerRows.value.length) {
    addHeaderRow()
  }
}

function startHeaderDrag(id) {
  draggingHeaderRowId.value = id
}

function dropHeaderRow(targetId) {
  const draggedId = draggingHeaderRowId.value
  draggingHeaderRowId.value = ''
  if (!draggedId || draggedId === targetId) {
    return
  }
  headerRows.value = reorderRowsById(headerRows.value, draggedId, targetId)
}

function reorderRowsById(rows, draggedId, targetId) {
  const ordered = [...rows]
  const draggedIndex = ordered.findIndex((row) => row.id === draggedId)
  const targetIndex = ordered.findIndex((row) => row.id === targetId)
  if (draggedIndex < 0 || targetIndex < 0) {
    return ordered
  }
  const [dragged] = ordered.splice(draggedIndex, 1)
  ordered.splice(targetIndex, 0, dragged)
  return ordered
}

function newNameValueRow(name = '', value = '', enabled = true) {
  return {
    id: nextFormDataId(),
    enabled,
    name,
    value,
  }
}

function restoreNameValueRows(entries) {
  if (!Array.isArray(entries) || !entries.length) {
    return [newNameValueRow()]
  }
  const rows = entries.map((entry) => newNameValueRow(
    entry?.name || '',
    entry?.value || '',
    entry?.enabled !== false,
  ))
  return rows.length ? rows : [newNameValueRow()]
}

function cleanNameValueRows(rows) {
  return rows
    .map((row) => ({
      name: (row.name || '').trim(),
      value: row.value || '',
      enabled: row.enabled !== false,
    }))
    .filter((row) => row.name)
    .filter((row) => row.enabled)
}

function serializeNameValueRows(rows) {
  return rows
    .map((row) => ({
      name: (row.name || '').trim(),
      value: row.value || '',
      enabled: row.enabled !== false,
    }))
    .filter((row) => row.name)
}

function nameValueRowsToText(rows) {
  return cleanNameValueRows(rows)
    .map((row) => `${row.name}=${row.value || ''}`)
    .join('\n')
}

function addResponseDecodeRow() {
  responseDecodeRows.value.push(newNameValueRow())
}

function removeResponseDecodeRow(id) {
  responseDecodeRows.value = responseDecodeRows.value.filter((row) => row.id !== id)
  if (!responseDecodeRows.value.length) {
    addResponseDecodeRow()
  }
}

function cleanResponseDecodeRows() {
  return responseDecodeRows.value
    .map((row) => ({
      name: (row.name || '').trim(),
      value: row.value || '',
      enabled: row.enabled !== false,
    }))
    .filter((row) => row.name)
    .filter((row) => row.enabled)
}

function decodeResponseFields() {
  const configs = cleanResponseDecodeRows()
  if (!configs.length || !rawResponseBody.value) {
    return []
  }
  const parsed = tryJsonParse(rawResponseBody.value)
  const source = parsed && typeof parsed === 'object' ? parsed : rawResponseBody.value
  return configs.flatMap((config) => {
    const matches = resolveJsonPath(source, config.name)
    if (!matches.length) {
      return [{
        configPath: config.name,
        matchPath: config.name,
        label: config.value || config.name,
        decoded: '',
        original: '',
        error: '找不到指定欄位',
      }]
    }
    return matches.map((match) => {
      const decoded = decodeBase64Text(match.value)
      return {
        configPath: config.name,
        matchPath: match.path,
        label: config.value || config.name,
        decoded: decoded.error ? '' : prettyText(decoded.value),
        original: typeof match.value === 'string' ? match.value : JSON.stringify(match.value),
        error: decoded.error,
      }
    })
  })
}

function resolveJsonPath(source, path) {
  const normalizedPath = path.trim()
  if (!normalizedPath) {
    return []
  }
  if (normalizedPath === '$') {
    return [{ path: '$', value: source }]
  }
  const tokens = parseJsonPath(normalizedPath)
  if (!tokens.length) {
    return []
  }
  let matches = [{ path: '$', value: source }]
  for (const token of tokens) {
    matches = matches.flatMap((match) => resolveJsonPathToken(match, token))
  }
  return matches
}

function parseJsonPath(path) {
  const normalized = path.startsWith('$.') ? path.slice(2) : path.startsWith('$') ? path.slice(1) : path
  const tokens = []
  let buffer = ''
  for (let index = 0; index < normalized.length; index += 1) {
    const char = normalized[index]
    if (char === '.') {
      if (buffer) {
        tokens.push({ type: 'key', value: buffer })
        buffer = ''
      }
      continue
    }
    if (char === '[') {
      if (buffer) {
        tokens.push({ type: 'key', value: buffer })
        buffer = ''
      }
      const endIndex = normalized.indexOf(']', index)
      if (endIndex < 0) {
        return []
      }
      const rawToken = normalized.slice(index + 1, endIndex).trim()
      if (rawToken === '*') {
        tokens.push({ type: 'wildcard' })
      } else if (/^\d+$/.test(rawToken)) {
        tokens.push({ type: 'index', value: Number(rawToken) })
      } else {
        tokens.push({ type: 'key', value: rawToken.replace(/^['"]|['"]$/g, '') })
      }
      index = endIndex
      continue
    }
    buffer += char
  }
  if (buffer) {
    tokens.push({ type: 'key', value: buffer })
  }
  return tokens
}

function resolveJsonPathToken(match, token) {
  const value = match.value
  if (token.type === 'key') {
    if (value && typeof value === 'object' && Object.prototype.hasOwnProperty.call(value, token.value)) {
      return [{ path: `${match.path}.${token.value}`, value: value[token.value] }]
    }
    return []
  }
  if (token.type === 'index') {
    if (Array.isArray(value) && token.value < value.length) {
      return [{ path: `${match.path}[${token.value}]`, value: value[token.value] }]
    }
    return []
  }
  if (token.type === 'wildcard') {
    if (Array.isArray(value)) {
      return value.map((item, index) => ({ path: `${match.path}[${index}]`, value: item }))
    }
    if (value && typeof value === 'object') {
      return Object.entries(value).map(([key, item]) => ({ path: `${match.path}.${key}`, value: item }))
    }
  }
  return []
}

function decodeBase64Text(value) {
  if (typeof value !== 'string' || !value.trim()) {
    return { error: '欄位不是可解碼的 base64 字串' }
  }
  try {
    const binary = window.atob(value.trim())
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
    return { value: new TextDecoder('utf-8', { fatal: false }).decode(bytes) }
  } catch (error) {
    return { error: 'base64 解碼失敗' }
  }
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

function tryJsonParse(text) {
  try {
    return JSON.parse(text)
  } catch {
    return null
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

function isResponseError(payload) {
  if (!payload) {
    return false
  }
  if (requestType.value === 'GRPC' || requestType.value === 'GRPC_BUR') {
    return payload.statusCode && payload.statusCode !== 'OK'
  }
  return Number(payload.statusCode || 0) >= 400
}
</script>

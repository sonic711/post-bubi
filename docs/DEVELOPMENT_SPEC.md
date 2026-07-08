# Post Bubi 開發規格文件

本文是 Post Bubi 的主要開發依據。後續功能設計、資料模型、API、前端畫面與打包方式都必須優先遵守本文件；若需求改變，應先更新本文件再修改實作。

## 1. 產品目標

Post Bubi 是一個可部署在離線主機上的 API 測試工具，目標是提供接近 Postman 的核心測試體驗，但第一版只聚焦在可用、穩定、單機離線部署。

核心要求：

- 使用 Java 17 作為後端。
- 使用 Vue 3 作為前端(參考fsap-adm的fsap-admin-api,fsap-admin-ui)。
- 前後端最終必須打包成單一可執行 JAR。
- 啟動 JAR 後，使用者透過瀏覽器操作 Web UI。
- 不需要登入。
- 第一版以單人使用為主。
- 測試案例必須可以匯出給其他人匯入使用。
- 介面語言使用繁體中文。
- 資料儲存在 H2 file database。
- 第一版優先支援 HTTP 與 gRPC unary API 測試。

## 2. 使用者與部署情境

### 2.1 使用者

第一版假設只有單一使用者在本機或離線主機上操作。系統不提供帳號、密碼、角色、權限或雲端同步。

### 2.2 部署方式

系統必須能用以下方式啟動：

```bash
java -jar post-bubi.jar
```

啟動後預設提供：

- Web UI：`http://localhost:18080`
- API base path：`/api`
- H2 database file：`./data/post-bubi.mv.db`
- 上傳檔案與匯入資源目錄：`./data/files`
- gRPC proto 資源目錄：`./data/protos`

以上路徑與 port 後續應可透過設定檔或啟動參數調整。

## 3. 技術選型

### 3.1 後端

後端技術：

- Java 17
- Spring Boot 3
- Gradle
- Spring Web
- Spring Data JPA
- H2 Database
- Jackson
- Apache HttpClient 5
- grpc-java
- grpc-netty-shaded
- protobuf-java
- protobuf-java-util
- grpc-services

HTTP request 執行必須由後端發送，避免瀏覽器 CORS 限制。

### 3.2 前端

前端技術：

- Vue 3
- Vite
- JavaScript
- 中文介面
- 不需要登入頁

可視需要使用下列套件：

- Monaco Editor：編輯 JSON、XML、raw body、gRPC JSON request
- Pinia：前端狀態管理
- Vue Router：若需要多頁路由
- Split.js 或等效方案：左右與上下可調整面板

前端 build 後的靜態檔案必須先由 `post-bubi-ui` 子專案打包成 UI resource JAR，JAR 內路徑使用 `public/`。`post-bubi-api` 再依賴該 UI JAR，最終由 Spring Boot executable JAR 一起帶出。此方式比照 `fsap-admin-api` / `fsap-admin-ui` 的前端打包模式。

### 3.3 品牌視覺

Post Bubi 的品牌主色為酒紅色，必須用於標誌、主操作按鈕、目前選取狀態、主要互動焦點與第一層主視覺元素。

- HEX：`#AB005F`
- RGB：`171, 0, 95`
- CMYK：`0%, 100%, 44%, 33%`

前端樣式應透過 CSS 變數集中管理品牌色，避免在元件中散落硬編碼。工具型介面仍應維持中性、清楚、可長時間操作的視覺風格，不應讓整體畫面變成單一酒紅色系。

品牌 logo 圖檔應放在前端 source asset 目錄，由 Vue/Vite import 後參與打包，避免使用部署主機上的外部路徑。第一版 logo 檔案位置為 `post-bubi-ui/src/assets/post-bubi-logo.png`。

前端必須支援 Light Theme 與 Dark Theme。主題切換控制應放在主要操作畫面中，不需要登入即可使用；使用者選擇需保存在瀏覽器本機，重新整理後保留。主題色彩應透過 CSS 變數管理，Light/Dark 都必須維持品牌酒紅色識別、足夠文字對比與工具型介面的可讀性。

前端工作台 UI/UX 應以專業 API 工具為標準，優先確保高頻操作清楚、低干擾且可長時間使用。主要原則：

- 側欄需清楚區分品牌、全域動作、Collection tree 與 Proto 區塊。
- Request toolbar 必須讓 request type、method/target、保存與送出形成單一主要工作流。
- Request 名稱、保存位置與目前狀態必須可掃描。
- Response 區塊需有清楚標題、目前 tab 與執行摘要。
- hover、focus、active、disabled 狀態需一致，並支援 Light/Dark Theme。
- 桌面較窄時不得讓 toolbar 與狀態文字互相擠壓或溢出。
- UI/UX 優化不得影響既有 API 呼叫、request 保存、匯入匯出與 gRPC/HTTP 執行流程；若只是視覺或互動回饋調整，應保持資料 payload 與後端契約不變。
- 窄版與小視窗需優先避免水平溢出、按鈕文字互相遮擋、長檔名/URL 撐破容器；可用截斷、title、可捲動 tab 或單欄 layout 保持可操作。
- Response 成功、送出中、錯誤狀態需有一致且可掃描的視覺回饋，且不只依賴顏色，仍需保留文字摘要。

## 4. 第一版 MVP 範圍

第一版目標是「可用的 API 測試工具」，不是完整複製 Postman。

### 4.1 必做功能

- HTTP request editor
- HTTP request sender
- HTTP response viewer
- gRPC unary request editor
- gRPC unary request sender
- Collection / Folder / Request 管理
- Request 儲存、修改、刪除
- Request history
- H2 持久化
- 匯出 ZIP
- 匯入 ZIP
- 中文 UI
- 單一 JAR 打包

### 4.2 第一版不做

- 使用者登入
- 權限管理
- Postman Collection 格式相容
- Pre-request script
- Test script
- OAuth 2.0
- Collection runner
- Mock server
- Team collaboration
- Cloud sync
- gRPC streaming
- API 文件產生器

## 5. HTTP 功能規格

### 5.1 Request

HTTP 第一版必須支援：

- Methods：
  - `GET`
  - `POST`
  - `PUT`
  - `PATCH`
  - `DELETE`
- URL 輸入
- Query Params 表格
- Headers 表格
- Body types：
  - none
  - raw text
  - JSON
  - x-www-form-urlencoded
  - form-data
  - file upload
- Timeout 設定
- Follow redirects 開關
- Ignore SSL certificate verification 開關

### 5.2 Form-data 與 File Upload

form-data 欄位需支援：

- text field
- file field

file field 的檔案由前端上傳至後端，後端保存到本機資料目錄，request 只保存檔案引用資訊。

### 5.3 Response

HTTP response viewer 必須顯示：

- Status code
- Duration
- Response size
- Response headers
- Response body
- JSON pretty print
- Raw view

後續可擴充：

- XML pretty print
- HTML preview
- Image preview
- Download binary response

## 6. gRPC 功能規格

### 6.1 第一版支援範圍

gRPC 第一版只支援 unary RPC。

Unary RPC 定義：client 傳送一個 request，server 回傳一個 response。此模式最接近一般 HTTP API 測試。

第一版不支援：

- Server streaming
- Client streaming
- Bidirectional streaming

### 6.2 Service Definition 來源

第一版 gRPC service definition 必須支援：

- 匯入 `.proto` 檔案
- 使用 server reflection，若目標 gRPC server 有開啟 reflection

執行 gRPC request 時，descriptor 解析優先順序為：

1. 匯入 `.proto`
2. Server reflection

若 request 有指定 `protoId`，後端必須優先使用本機已匯入的 `.proto` 與其相依 import 建立 descriptor，不可強制依賴目標 server 開啟 reflection。若 request 沒有指定 `protoId`，才使用 server reflection 作為 fallback。

本機 proto resolver 第一階段需支援 proto3 常見 unary 測試情境：

- `package`
- `import`
- `message`
- nested message
- `enum`
- scalar 欄位
- message 欄位
- repeated 欄位
- map 欄位
- unary `service rpc`

不支援或尚未驗證的進階 proto 語法需回傳結構化錯誤，避免誤判為 gRPC target server 問題。

### 6.3 Request

gRPC unary request editor 必須支援：

- Host
- Port
- Plaintext / TLS
- Ignore TLS certificate verification，若使用 TLS
- Metadata 表格
- Proto file 選擇或上傳
- Package 選擇
- Service 選擇
- Method 選擇
- JSON 格式 request body

### 6.4 Response

gRPC response viewer 必須顯示：

- Status code
- Status description
- Duration
- Response message，JSON 格式顯示
- Response metadata
- Error message

## 7. Collection 與 Request 管理

### 7.1 Tree 結構

左側 collection tree 應支援：

- Collection
- Folder
- Request

Request 類型：

- `HTTP`
- `GRPC`

### 7.2 管理功能

第一版必須支援：

- 新增 collection
- 新增 folder
- 新增 HTTP request
- 新增 gRPC request
- 重新命名
- 儲存
- 複製
- 刪除

移動排序可在第一版後期或第二版加入。

## 8. 匯入與匯出

### 8.1 格式

第一版不需要相容 Postman Collection。匯入與匯出採用 Post Bubi 自訂 ZIP 格式。

ZIP 內容：

```text
collection.json
files/
protos/
```

### 8.2 collection.json

`collection.json` 必須包含：

- schema version
- collections
- folders
- HTTP requests
- gRPC requests
- request body settings
- headers
- params
- gRPC metadata
- proto references
- file references

不包含：

- request history
- 本機絕對路徑
- 使用者機器相關設定

### 8.3 檔案處理

匯出 ZIP 時：

- HTTP file upload 相關檔案放入 `files/`
- gRPC proto 檔案放入 `protos/`
- `collection.json` 使用相對路徑引用檔案

匯入 ZIP 時：

- 解壓並驗證 `collection.json`
- 將檔案複製到本機資料目錄
- 建立新的 collection 資料
- 不覆蓋既有 collection，除非後續新增明確覆蓋選項

## 9. 資料庫設計

H2 使用 file mode。第一版資料表建議如下，實作時可用 JPA entity 對應。

### 9.1 collections

- `id`
- `name`
- `description`
- `created_at`
- `updated_at`

### 9.2 folders

- `id`
- `collection_id`
- `parent_folder_id`
- `name`
- `sort_order`
- `created_at`
- `updated_at`

### 9.3 requests

- `id`
- `collection_id`
- `folder_id`
- `type`
- `name`
- `sort_order`
- `payload_json`
- `created_at`
- `updated_at`

`type` values：

- `HTTP`
- `GRPC`

`payload_json` 保存 request 類型特定設定。

### 9.4 request_history

- `id`
- `request_id`
- `request_snapshot_json`
- `response_snapshot_json`
- `created_at`

History 保存執行當下的 request 與 response snapshot，不應因 request 後續修改而改變。

### 9.5 files

- `id`
- `original_name`
- `stored_name`
- `relative_path`
- `content_type`
- `size_bytes`
- `sha256`
- `created_at`

### 9.6 proto_files

- `id`
- `original_name`
- `stored_name`
- `relative_path`
- `content`
- `created_at`
- `updated_at`

若後續需要支援多檔 proto import，可新增 proto dependency 關聯表。

## 10. 後端 API 規格方向

所有 API path 使用 `/api` prefix。

### 10.1 Collection API

- `GET /api/collections`
- `POST /api/collections`
- `PUT /api/collections/{id}`
- `DELETE /api/collections/{id}`

### 10.2 Folder API

- `POST /api/folders`
- `PUT /api/folders/{id}`
- `DELETE /api/folders/{id}`

### 10.3 Request API

- `GET /api/requests/{id}`
- `POST /api/requests`
- `PUT /api/requests/{id}`
- `DELETE /api/requests/{id}`
- `POST /api/requests/{id}/duplicate`

### 10.4 HTTP Execute API

- `POST /api/http/execute`

此 API 接收完整 HTTP request 設定，立即執行並回傳 response，不要求 request 已儲存。

### 10.5 gRPC Execute API

- `POST /api/grpc/execute`

此 API 接收完整 gRPC request 設定，立即執行並回傳 response，不要求 request 已儲存。

Request 可選欄位：

- `protoId`：指定已匯入的 `.proto`。若提供此欄位，後端必須使用本機 proto descriptor 執行，不依賴 server reflection。

### 10.6 File API

- `POST /api/files`
- `GET /api/files/{id}`
- `DELETE /api/files/{id}`

### 10.7 Proto API

- `POST /api/protos`
- `GET /api/protos`
- `GET /api/protos/{id}`
- `DELETE /api/protos/{id}`
- `POST /api/protos/{id}/inspect`

### 10.8 Import / Export API

- `GET /api/export/collections/{id}`
- `POST /api/import`

## 11. 前端畫面規格

### 11.1 主畫面

第一版主畫面採單頁工作台：

- 左側：Collection tree
- 中間上方：Request editor
- 中間下方：Response viewer
- 右上方工具列：環境、匯入、匯出、設定

不要做行銷 landing page。啟動後第一個畫面就是可操作的 API 測試工作台。

### 11.2 Request Editor

HTTP request editor：

- Method selector
- URL input
- Send button
- Save button
- Tabs：
  - Params
  - Headers
  - Body
  - Settings

gRPC request editor：

- Host input
- Port input
- TLS selector
- Send button
- Save button
- Tabs：
  - Proto
  - Metadata
  - Message
  - Settings

### 11.3 Response Viewer

Response viewer tabs：

- Body
- Headers / Metadata
- Info

HTTP 與 gRPC response viewer 可共用外層 layout，但內容欄位依 request type 切換。

## 12. 錯誤處理

後端 API error response 應統一格式：

```json
{
  "code": "ERROR_CODE",
  "message": "可讀錯誤訊息",
  "details": {}
}
```

前端必須顯示使用者可理解的中文錯誤訊息。

常見錯誤：

- URL 格式錯誤
- 連線逾時
- DNS 解析失敗
- SSL 憑證驗證失敗
- gRPC proto 解析失敗
- gRPC method 找不到
- 匯入 ZIP 格式錯誤
- 檔案不存在

## 13. 安全與限制

雖然第一版不登入，但仍需注意：

- 不應執行使用者提供的任意 script。
- 第一版不支援 pre-request script 或 test script。
- 匯入 ZIP 時必須防止 zip slip path traversal。
- 檔案儲存時不得直接使用使用者提供的原始檔名作為實體檔名。
- 匯出資料不得包含使用者機器上的本機絕對路徑。
- Ignore SSL certificate verification 必須是 request settings 中的明確開關。

## 14. 打包與建置

最終產物必須是單一可執行 JAR。

建置流程：

1. `post-bubi-ui` 安裝或還原前端依賴。
2. `post-bubi-ui` build Vue app。
3. `post-bubi-ui` 將前端 dist 複製到 `build/prodJar` 或 `build/devJar`。
4. `post-bubi-ui` 將 dist 內容打包成 `public/` resource JAR。
5. `post-bubi-api` 依 profile 引用 UI resource JAR。
6. `post-bubi-api` build Spring Boot executable JAR。

目標指令形式固定為 Gradle：

```bash
./gradlew clean :post-bubi-api:bootJar
```

不維護 Maven build。若需要離線建置，使用 `gradle/offline-maven-repo.gradle` 產生 Maven layout 離線依賴包，再用 `-PofflineRepo=/path/to/repo` 建置。

## 15. 開發順序

建議依以下順序開發：

1. 建立 Spring Boot + Vue 3 專案骨架。
2. 設定單一 JAR 打包流程。
3. 建立 H2 schema 與 JPA entities。
4. 實作 collection / folder / request CRUD。
5. 實作 HTTP execute API。
6. 實作 Vue HTTP request editor 與 response viewer。
7. 實作 file upload 與 multipart form-data。
8. 實作 request history。
9. 實作 ZIP export / import。
10. 實作 proto upload 與 inspect。
11. 實作 gRPC unary execute API。
12. 實作 Vue gRPC request editor 與 response viewer。
13. 補齊錯誤處理、中文訊息與基本測試。

## 16. 驗收條件

第一版完成時至少必須通過以下驗收：

- 可以用 Java 17 執行單一 JAR。
- 開啟瀏覽器後直接進入中文 API 測試工作台。
- 可以新增 collection。
- 可以新增並保存 HTTP request。
- 可以發送 GET request 並顯示 response。
- 可以發送 JSON POST request 並顯示 response。
- 可以發送 multipart form-data request，包含檔案。
- 可以設定忽略 SSL 憑證驗證。
- 可以新增並保存 gRPC unary request。
- 可以透過 proto 呼叫 gRPC unary method。
- 可以匯出 collection ZIP。
- 可以在另一個乾淨資料庫匯入 collection ZIP。
- 匯入後 HTTP request、gRPC proto 與上傳檔案引用可正常使用。

## 17. 後續版本候選功能

第一版完成後可考慮：

- Environment variables
- `{{variable}}` 替換
- Authorization helpers
- Postman Collection import / export
- cURL import / export
- OpenAPI import
- gRPC server streaming
- gRPC client streaming
- gRPC bidirectional streaming
- Collection runner
- Test assertions
- Request tabs
- Cookie jar
- Response binary download
- HTML / image preview
- Proxy setting

## 18. Git 與推送規範

所有開發工作都必須遵守以下規範：

- 推送程式到遠端 repository 前，必須先取得使用者明確同意。
- Commit 訊息必須使用繁體中文。
- Commit 訊息必須盡可能清楚描述異動內容，不可只使用「更新」、「修改」、「fix」等過度模糊的文字。
- 若異動包含建置、後端、前端、文件等多個範圍，commit 訊息應涵蓋主要變更。
- 未經使用者同意，不得擅自執行 `git push`。

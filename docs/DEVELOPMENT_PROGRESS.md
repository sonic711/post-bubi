# Post Bubi 開發進度紀錄

本文記錄目前開發進度、每項功能的驗證方式與未完成事項。後續每完成一項功能，都必須更新本文件。

## 狀態定義

- `完成`：功能已實作並通過本文件記錄的驗證。
- `進行中`：正在開發，尚未完成驗證。
- `未開始`：尚未實作。
- `阻擋`：因外部條件或決策未定而無法繼續。

## 目前進度

| 順序 | 功能 | 狀態 | 驗證 |
| --- | --- | --- | --- |
| 1 | Spring Boot + Vue 3 專案骨架 | 完成 | 已可打包並啟動 JAR |
| 2 | 單一 JAR 打包流程 | 完成 | `:post-bubi-api:bootJar` 成功，首頁與 `/api/health` 回應 200 |
| 3 | H2 schema 與 JPA entities | 完成 | `compileJava`、bootRun 啟動後 JPA 掃描到 3 個 repository |
| 4 | Collection / Folder / Request CRUD | 完成 | curl 驗證 CRUD API 均符合預期 |
| 5 | HTTP execute API | 完成 | curl 驗證 GET、POST JSON、錯誤 URL |
| 6 | Vue HTTP request editor 與 response viewer | 完成 | `bootJar` 後首頁載入新版 assets，可從 UI 送 HTTP request |
| 6.1 | Vue Collection / Request 保存 UI | 完成 | `bootJar` 後首頁載入新版 assets，CRUD API 驗證保存與刪除流程 |
| 6.2 | Vue Folder tree UI | 完成 | `bootJar`、首頁載入新版 assets、Folder CRUD 與 Request folderId 驗證 |
| 7 | File upload 與 multipart form-data | 完成 | `bootJar`、`/api/files` 上傳、`form-data` execute 成功 |
| 8 | Request history | 完成 | `bootJar`、HTTP execute 後 `/api/http/history` 可查到紀錄 |
| 9 | ZIP export / import | 完成 | `bootJar`、匯出 ZIP、匯入 ZIP 後 Collection 增加 |
| 9.1 | ZIP proto 檔案匯出 / 匯入 | 完成 | `:post-bubi-api:test` 通過，已覆蓋 ZIP 內 `protos/` 與匯入後 Proto 列表 |
| 10 | Proto upload 與 inspect | 完成 | `bootJar`、`.proto` 上傳、列表與 inspect API 成功 |
| 11 | gRPC unary execute API | 完成 | `bootJar`、API 參數驗證、reflection 失敗格式驗證；實際成功呼叫需可用的 reflection gRPC server |
| 12 | Vue gRPC request editor 與 response viewer | 完成 | `bootJar`、首頁載入新版 assets、gRPC execute 錯誤路徑驗證 |
| 12.1 | Proto method 套用到 gRPC editor | 完成 | `bootJar`、首頁載入新版 assets、Proto 上傳與 inspect 驗證 |
| 12.2 | gRPC TLS 忽略憑證驗證 | 完成 | `:post-bubi-api:test`、`:post-bubi-api:bootJar` 通過，首頁載入新版 assets，TLS 設定 payload 驗證 |
| 12.3 | 品牌主色與 Logo 套用 | 完成 | `:post-bubi-api:bootJar` 通過，前端 CSS 產物包含 `#ab005f`，Logo asset 已打包進 UI JAR |
| 12.4 | Light / Dark Theme | 完成 | `:post-bubi-api:bootJar` 通過，首頁載入新版 assets，CSS 包含 Light/Dark 變數與本機偏好保存 |
| 12.5 | 工作台 UI/UX 基礎升級 | 完成 | `:post-bubi-api:bootJar` 通過，首頁載入新版 assets，完成 sidebar、toolbar、request meta、response summary 與窄版 layout 優化 |
| 13 | 錯誤處理、中文訊息與基本測試 | 完成 | `:post-bubi-api:test` 通過，已覆蓋 Workspace CRUD 與統一錯誤格式 |
| 13.1 | HTTP execute 自動化測試 | 完成 | `:post-bubi-api:test` 通過，已覆蓋 HTTP GET、History 與 invalid URL |
| 13.2 | File upload / form-data 自動化測試 | 完成 | `:post-bubi-api:test` 通過，已覆蓋 `/api/files` 與 HTTP execute form-data file |
| 13.3 | ZIP export / import 自動化測試 | 完成 | `:post-bubi-api:test` 通過，已覆蓋 ZIP 匯出/匯入與 zip slip 防護 |
| 13.4 | Proto upload / inspect 自動化測試 | 完成 | `:post-bubi-api:test` 通過，已覆蓋 Proto 上傳、列表、inspect 與副檔名錯誤 |
| 13.5 | gRPC execute 自動化測試 | 完成 | `:post-bubi-api:test` 通過，已覆蓋 reflection unary 成功呼叫與 JSON 錯誤 |

## 程式碼比對摘要

- 比對日期：2026-06-29
- 比對範圍：
  - `post-bubi-api/src/main/java`
  - `post-bubi-ui/src`
  - `docs/DEVELOPMENT_SPEC.md`
- 比對結論：本文件目前記錄的完成狀態與程式碼一致。已完成項目在程式碼中可找到對應實作；未完成項目尚未出現完整可用的後端 API 與前端操作畫面。

### 已完成且程式碼已存在

- 單一 JAR 打包流程：`post-bubi-ui` 先打包 Vue 靜態資源，再由 `post-bubi-api` 產生可執行 JAR。
- H2/JPA 持久化：已建立 `CollectionEntity`、`FolderEntity`、`RequestEntity` 與對應 repository。
- Collection / Folder / Request 後端 CRUD：已建立 controller、service、DTO 與統一錯誤回應。
- Collection 刪除：後端 `DELETE /api/collections/{id}` 會移除該 Collection 底下的 Request 與 Folder；前端已提供刪除按鈕與確認訊息。
- HTTP execute API：已建立 `POST /api/http/execute`，支援 GET、POST、PUT、PATCH、DELETE、params、headers、JSON/raw/x-www-form-urlencoded/form-data body、timeout、redirect 與忽略 SSL 驗證。
- File upload：已建立 `POST /api/files`，可將前端選取檔案存到本機 `post-bubi.storage.files-dir`，並回傳 `fileId` 供 form-data request 引用。
- Request history：已建立 `request_histories` 與 `GET /api/http/history`，HTTP execute 後會保存最近執行紀錄供前端載入。
- ZIP export / import：已建立 `GET /api/workspace/export` 與 `POST /api/workspace/import`，可匯出/匯入 Collection、Folder、Request、file references 與 proto files。
- Proto upload / inspect：已建立 `POST /api/protos`、`GET /api/protos` 與 `GET /api/protos/{protoId}/inspect`，可上傳 `.proto` 並解析 package、imports、messages、services 與 rpc methods。
- gRPC unary execute API：已建立 `POST /api/grpc/execute`，第一階段透過 server reflection 取得 descriptor，將 JSON request 轉為 `DynamicMessage` 後呼叫 unary method。
- Vue gRPC request editor / response viewer：前端已可切換 HTTP/gRPC，填寫 gRPC target、service、method、metadata、JSON body 與 settings，並顯示 gRPC response body、metadata 與 info。
- gRPC TLS 設定：gRPC editor 已支援 Plaintext / TLS 切換；使用 TLS 時可開啟 Ignore TLS certificate verification，後端會使用不驗證憑證的 TLS channel 執行 request。
- Proto method 套用到 gRPC editor：前端 Proto inspect 的 rpc method 已可點選，並自動切換到 gRPC、填入完整 service/method 與 JSON body。
- 品牌主色與 Logo 套用：前端 CSS 已集中定義品牌色變數，將 `#AB005F` 套用到主操作按鈕、送出按鈕、目前選取狀態、active tab、Proto method hover、history method 與 keyboard focus；sidebar 品牌區已使用 `post-bubi-ui/src/assets/post-bubi-logo.png`。
- Light / Dark Theme：左側 sidebar 已新增 Light/Dark segmented control，使用 `data-theme` 與 CSS 變數切換主題，使用者選擇會保存到 `localStorage`。
- 工作台 UI/UX 基礎升級：已重整 sidebar、全域動作、Collection/Proto 區塊、request toolbar、request meta、response summary、hover/focus/active 狀態與窄版 layout。
- UI resource JAR 打包清理：`post-bubi-ui` 的 dev/prod resource 目錄會在每次複製前清空，避免舊 hash asset 累積進單一 JAR。
- Vue HTTP request editor：已可編輯 HTTP method、URL、params、headers、body、settings 並送出 request。
- Vue response viewer：已可顯示 status、duration、size、headers、body 與 info。
- Vue Collection / Request 保存流程：已可新增 Collection、保存 HTTP Request、載入、更新與刪除 Request。
- Vue Folder tree UI：已可在 Collection 下新增 Folder/子 Folder、選取 Folder、刪除 Folder，並將 Request 保存到選取的 Folder。
- 基本後端整合測試：已新增 `WorkspaceApiIntegrationTest`，覆蓋 Workspace CRUD 主要流程與統一錯誤格式。
- HTTP execute 自動化測試：已新增 `HttpExecuteIntegrationTest`，覆蓋實際 HTTP GET、history 保存與 invalid URL 錯誤格式。
- File upload / form-data 自動化測試：已新增 `FileUploadIntegrationTest`，覆蓋檔案上傳、HTTP execute multipart file 與空 form-data 錯誤格式。
- ZIP export / import 自動化測試：已新增 `WorkspaceArchiveIntegrationTest`，覆蓋 workspace ZIP 匯出、匯入、file/proto reference 重新對應與 zip slip 防護。
- Proto upload / inspect 自動化測試：已新增 `ProtoIntegrationTest`，覆蓋 Proto 上傳、列表、inspect parsing 與副檔名錯誤格式。
- gRPC execute 自動化測試：已新增 `GrpcExecuteIntegrationTest`，覆蓋本機 reflection gRPC server 的 unary 成功呼叫與 JSON 錯誤格式。

### 尚未完成且程式碼尚未完整存在

- 更完整的自動化測試覆蓋：後續可持續擴充前端端對端測試與更多錯誤情境。

## 已完成驗證紀錄

### 專案骨架與單一 JAR

- 日期：2026-06-27
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:compileJava
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
java -jar post-bubi-api/build/libs/post-bubi.jar
curl -s -i http://127.0.0.1:18080/
curl -s -i http://127.0.0.1:18080/api/health
```

- 結果：
  - `compileJava` 成功。
  - `bootJar` 成功。
  - 首頁回應 HTTP 200。
  - `/api/health` 回應 HTTP 200。

### H2/JPA 與 Collection / Folder / Request CRUD

- 日期：2026-06-27
- 實作範圍：
  - 新增 `collections`、`folders`、`requests` 對應 JPA entity。
  - 新增 `CollectionRepository`、`FolderRepository`、`RequestRepository`。
  - 新增 `CollectionController`、`FolderController`、`RequestController`。
  - 新增 `WorkspaceService` 管理 CRUD 與關聯驗證。
  - 新增統一錯誤格式 `ApiErrorResponse`、`ApiException`、`GlobalExceptionHandler`。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:compileJava
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootRun -Pprofile=local --args='--spring.datasource.url=jdbc:h2:file:./build/verify/post-bubi-crud-2;AUTO_SERVER=TRUE --server.port=18080'
curl -s -i -X POST http://127.0.0.1:18080/api/collections -H 'Content-Type: application/json' -d '{"name":"CRUD 驗證 Collection","description":"第二輪驗證"}'
curl -s -i -X POST http://127.0.0.1:18080/api/folders -H 'Content-Type: application/json' -d '{"collectionId":1,"name":"HTTP 測試","sortOrder":1}'
curl -s -i -X POST http://127.0.0.1:18080/api/requests -H 'Content-Type: application/json' -d '{"collectionId":1,"folderId":1,"type":"HTTP","name":"健康檢查","sortOrder":1,"payloadJson":"{\"method\":\"GET\",\"url\":\"http://localhost:18080/api/health\"}"}'
curl -s -i -X PUT http://127.0.0.1:18080/api/requests/1 -H 'Content-Type: application/json' -d '{"folderId":1,"type":"HTTP","name":"健康檢查 API","sortOrder":2,"payloadJson":"{\"method\":\"GET\",\"url\":\"http://localhost:18080/api/health\",\"timeoutMillis\":30000}"}'
curl -s -i http://127.0.0.1:18080/api/requests/1
curl -s -i -X POST http://127.0.0.1:18080/api/requests/1/duplicate
curl -s -i http://127.0.0.1:18080/api/collections
curl -s -i -X DELETE http://127.0.0.1:18080/api/requests/3
curl -s -i -X DELETE http://127.0.0.1:18080/api/collections/1
curl -s -i -X POST http://127.0.0.1:18080/api/collections -H 'Content-Type: application/json' -d '{"name":"   "}'
curl -s -i http://127.0.0.1:18080/api/requests/999
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
```

- 結果：
  - `compileJava` 成功。
  - bootRun 啟動成功，Spring Data JPA 掃描到 3 個 repository。
  - `POST /api/collections` 回應 201。
  - `POST /api/folders` 回應 201。
  - `POST /api/requests` 可建立 `HTTP` 與 `GRPC` request。
  - `PUT /api/requests/{id}` 回應 200，且 `updatedAt` 正確更新。
  - `GET /api/requests/{id}` 回應 200。
  - `POST /api/requests/{id}/duplicate` 回應 201。
  - `GET /api/collections` 會帶出 collection 下的 folders 與 requests。
  - `DELETE /api/requests/{id}` 回應 204。
  - `DELETE /api/folders/{id}` 回應 204，並刪除 folder 下 request。
  - `DELETE /api/collections/{id}` 回應 204。
  - 必填欄位錯誤回應 400，格式為 `code/message/details`。
  - 查無 request 回應 404，格式為 `code/message/details`。
  - `bootJar` 成功。

### HTTP Execute API 與 Vue HTTP Request Editor

- 日期：2026-06-27
- 實作範圍：
  - 新增 `POST /api/http/execute`。
  - 後端使用 Apache HttpClient 5 發送 HTTP request，避免前端 CORS 限制。
  - 支援 HTTP methods：`GET`、`POST`、`PUT`、`PATCH`、`DELETE`。
  - 支援 Query Params、Headers。
  - 支援 body types：`none`、`raw`、`json`、`x-www-form-urlencoded`。
  - 支援 timeout、follow redirects、ignore SSL certificate verification 設定。
  - 前端主畫面可輸入 method、URL、params、headers、body、settings 並送出。
  - Response viewer 顯示 status、duration、size、headers、body、info。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
java -jar post-bubi-api/build/libs/post-bubi.jar --spring.datasource.url=jdbc:h2:file:./build/verify/post-bubi-http;AUTO_SERVER=TRUE --server.port=18080
curl -s -i -X POST http://127.0.0.1:18080/api/http/execute -H 'Content-Type: application/json' -d '{"method":"GET","url":"http://127.0.0.1:18080/api/health","headers":[{"name":"Accept","value":"application/json","enabled":true}],"params":[{"name":"stage","value":"http-execute","enabled":true}],"bodyType":"none","timeoutMillis":30000,"followRedirects":true,"ignoreSslVerification":false}'
curl -s -i -X POST http://127.0.0.1:18080/api/http/execute -H 'Content-Type: application/json' -d '{"method":"POST","url":"http://127.0.0.1:18080/api/collections","headers":[{"name":"Content-Type","value":"application/json","enabled":true}],"bodyType":"json","body":"{\"name\":\"HTTP Execute 建立\",\"description\":\"透過 HTTP execute API 建立\"}","timeoutMillis":30000,"followRedirects":true,"ignoreSslVerification":false}'
curl -s -i -X POST http://127.0.0.1:18080/api/http/execute -H 'Content-Type: application/json' -d '{"method":"GET","url":"not-a-url","bodyType":"none"}'
curl -s -i http://127.0.0.1:18080/
```

- 結果：
  - `bootJar` 成功。
  - `GET /api/http/execute` 目標為 `/api/health` 時，外層 API 回應 200，內層 response statusCode 為 200。
  - `POST /api/http/execute` 目標為 `/api/collections` 且 body type 為 JSON 時，內層 response statusCode 為 201。
  - URL 格式錯誤時回應 400，錯誤格式為 `code/message/details`。
  - 首頁回應 200，載入新版前端 assets。

### Vue Collection / Request 保存 UI

- 日期：2026-06-27
- 實作範圍：
  - 前端啟動後自動讀取 `GET /api/collections`。
  - 左側 sidebar 顯示 Collection 與 Request。
  - 可從畫面新增 Collection。
  - 可從畫面刪除 Collection，並連同底下 Request 一起移除。
  - 可從目前 HTTP editor 內容新增 Request。
  - 可選取已儲存 Request 並載入 method、URL、params、headers、body、settings。
  - 可更新已儲存 Request。
  - 可刪除已儲存 Request。
  - 畫面顯示目前操作狀態與錯誤訊息。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
java -jar post-bubi-api/build/libs/post-bubi.jar --spring.datasource.url=jdbc:h2:file:./build/verify/post-bubi-save-ui;AUTO_SERVER=TRUE --server.port=18080
curl -s -i -X POST http://127.0.0.1:18080/api/collections -H 'Content-Type: application/json' -d '{"name":"UI 保存驗證","description":""}'
curl -s -i -X POST http://127.0.0.1:18080/api/requests -H 'Content-Type: application/json' -d '{"collectionId":1,"folderId":null,"type":"HTTP","name":"UI 健康檢查","sortOrder":0,"payloadJson":"{\"method\":\"GET\",\"url\":\"http://localhost:18080/api/health\",\"paramsText\":\"\",\"headersText\":\"Accept=application/json\",\"bodyType\":\"none\",\"body\":\"\",\"timeoutMillis\":30000,\"followRedirects\":true,\"ignoreSslVerification\":false}"}'
curl -s -i http://127.0.0.1:18080/api/collections
curl -s -i http://127.0.0.1:18080/
curl -s -i -X DELETE http://127.0.0.1:18080/api/requests/1
curl -s -i -X POST http://127.0.0.1:18080/api/collections -H 'Content-Type: application/json' -d '{"name":"刪除 Collection 驗證","description":""}'
curl -s -i -X POST http://127.0.0.1:18080/api/requests -H 'Content-Type: application/json' -d '{"collectionId":1,"folderId":null,"type":"HTTP","name":"會被連帶刪除","sortOrder":0,"payloadJson":"{\"method\":\"GET\",\"url\":\"http://localhost:18080/api/health\"}"}'
curl -s -i -X DELETE http://127.0.0.1:18080/api/collections/1
curl -s -i http://127.0.0.1:18080/api/collections
```

- 結果：
  - `bootJar` 成功。
  - Collection 建立 API 回應 201。
  - Request 建立 API 回應 201。
  - Collection 列表可回傳保存的 Request 與 payload。
  - 首頁回應 200，載入新版前端 assets。
  - Request 刪除 API 回應 204。
  - Collection 刪除 API 回應 204。
  - 刪除 Collection 後列表回應 `[]`，確認底下 Request 已被連帶移除。

### Vue Folder Tree UI

- 日期：2026-06-28
- 實作範圍：
  - Collection row 新增「+F」操作，可在 Collection 下建立 Folder。
  - Folder row 新增「+F」操作，可建立子 Folder。
  - Sidebar 以縮排方式顯示 Folder 階層。
  - 可選取 Folder，新增或更新 Request 時會保存到目前選取的 Folder。
  - 選取已儲存 Request 時會同步選取其所在 Folder。
  - 可刪除 Folder；刪除時後端會移除該 Folder 底下 Request。
  - 若 Folder 仍有子 Folder，刪除會由後端回傳 `FOLDER_HAS_CHILDREN`。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
java -jar post-bubi-api/build/libs/post-bubi.jar --spring.datasource.url=jdbc:h2:file:./build/verify/post-bubi-folder-ui --server.port=18080
curl -s -i http://127.0.0.1:18080/
curl -s -X POST http://127.0.0.1:18080/api/collections -H 'Content-Type: application/json' -d '{"name":"Folder UI 驗證","description":""}'
curl -s -X POST http://127.0.0.1:18080/api/folders -H 'Content-Type: application/json' -d '{"collectionId":1,"parentFolderId":null,"name":"HTTP APIs","sortOrder":1}'
curl -s -X POST http://127.0.0.1:18080/api/folders -H 'Content-Type: application/json' -d '{"collectionId":1,"parentFolderId":1,"name":"Health","sortOrder":2}'
curl -s -X POST http://127.0.0.1:18080/api/requests -H 'Content-Type: application/json' -d '{"collectionId":1,"folderId":2,"type":"HTTP","name":"Folder 內健康檢查","sortOrder":1,"payloadJson":"{\"requestType\":\"HTTP\",\"method\":\"GET\",\"url\":\"http://localhost:18080/api/health\"}"}'
curl -s http://127.0.0.1:18080/api/collections
curl -s -i -X DELETE http://127.0.0.1:18080/api/folders/2
curl -s http://127.0.0.1:18080/api/collections
curl -s -i -X DELETE http://127.0.0.1:18080/api/folders/1
```

- 結果：
  - `bootJar` 成功。
  - 首頁回應 200，載入新版前端 assets。
  - Collection 建立 API 回應 201。
  - Root Folder 建立 API 回應 201。
  - 子 Folder 建立 API 回應 201。
  - Request 建立 API 回應 201，且 `folderId` 為子 Folder ID。
  - Collection 列表可回傳 Folder 階層資料與 Folder 內 Request。
  - 刪除子 Folder 回應 204，且底下 Request 一併移除。
  - 刪除空的 root Folder 回應 204。

### File upload 與 multipart form-data

- 日期：2026-06-28
- 實作範圍：
  - 新增 `POST /api/files`，使用 multipart form-data 上傳單一檔案。
  - 檔案會儲存在 `post-bubi.storage.files-dir`，預設為 `./data/files`。
  - Spring multipart 上傳限制設定為單檔 100MB、單次 request 120MB。
  - 上傳後回傳 `fileId`、原始檔名、儲存檔名、content type 與大小。
  - `POST /api/http/execute` 新增 `bodyType=form-data`。
  - form-data 欄位支援 `text` 與 `file`。
  - file 欄位使用已上傳檔案的 `fileId` 引用本機檔案。
  - multipart execute 會自動產生 boundary，若 headers 手動填入 `Content-Type`，後端會略過該 header，避免 boundary 錯誤。
  - 前端 Body tab 新增 `form-data` 選項。
  - 前端 form-data 編輯器可新增 text/file 欄位，file 欄位選檔後會先上傳並保存 `fileId`。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
java -jar post-bubi-api/build/libs/post-bubi.jar --spring.datasource.url=jdbc:h2:file:./build/verify/post-bubi-form-data;AUTO_SERVER=TRUE --post-bubi.storage.files-dir=./build/verify/files --server.port=18080
curl -s -i http://127.0.0.1:18080/
curl -s -i -X POST http://127.0.0.1:18080/api/files -F file=@build/verify/multipart-sample.txt
curl -s -i -X POST http://127.0.0.1:18080/api/http/execute -H 'Content-Type: application/json' -d '{"method":"POST","url":"http://127.0.0.1:18080/api/health","bodyType":"form-data","formData":[],"timeoutMillis":30000,"followRedirects":true,"ignoreSslVerification":false}'
curl -s -i -X POST http://127.0.0.1:18080/api/http/execute -H 'Content-Type: application/json' -d '{"method":"POST","url":"http://127.0.0.1:18080/api/files","headers":[{"name":"Content-Type","value":"multipart/form-data","enabled":true}],"bodyType":"form-data","formData":[{"type":"file","name":"file","fileId":"24a2180e-acc0-4358-bec2-936d3a91433f","fileName":"multipart-sample.txt","contentType":"text/plain","enabled":true}],"timeoutMillis":30000,"followRedirects":true,"ignoreSslVerification":false}'
```

- 結果：
  - `bootJar` 成功。
  - 首頁回應 200，載入新版前端 assets。
  - `POST /api/files` 回應 201，並回傳 `fileId`。
  - 空的 form-data execute 回應 400，錯誤碼為 `HTTP_FORM_DATA_REQUIRED`。
  - multipart file execute 外層回應 200，內層目標 `/api/files` 回應 201，確認 `file` part 已成功送出。

### Request History

- 日期：2026-06-28
- 實作範圍：
  - 新增 `request_histories` JPA entity 與 repository。
  - 新增 `RequestHistoryService`，HTTP execute 成功或連線執行失敗時會保存歷史紀錄。
  - 新增 `GET /api/http/history`，回傳最近 50 筆執行紀錄。
  - History 紀錄包含 method、url、status code、duration、size、success、error message、request JSON、response body preview 與建立時間。
  - 前端 Response 區塊新增 `History` tab。
  - 前端送出 HTTP request 後會重新載入 history。
  - 點擊 history item 可將該筆 request 載回 HTTP editor。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
java -jar post-bubi-api/build/libs/post-bubi.jar --spring.datasource.url=jdbc:h2:file:./build/verify/post-bubi-history --server.port=18080
curl -s -i http://127.0.0.1:18080/
curl -s -i http://127.0.0.1:18080/api/http/history
curl -s -i -X POST http://127.0.0.1:18080/api/http/execute -H 'Content-Type: application/json' -d '{"method":"GET","url":"http://127.0.0.1:18080/api/health","headers":[{"name":"Accept","value":"application/json","enabled":true}],"params":[{"name":"stage","value":"history","enabled":true}],"bodyType":"none","timeoutMillis":30000,"followRedirects":true,"ignoreSslVerification":false}'
curl -s -i http://127.0.0.1:18080/api/http/history
```

- 結果：
  - `bootJar` 成功。
  - JPA 啟動時掃描到 4 個 repository。
  - 首頁回應 200，載入新版前端 assets。
  - 初始 `GET /api/http/history` 回應 `[]`。
  - `POST /api/http/execute` 目標為 `/api/health` 時，外層 API 回應 200，內層 response statusCode 為 200。
  - 再次查詢 `GET /api/http/history` 可取得剛才執行紀錄，包含 method、url、statusCode、requestJson、responseBodyPreview 與 createdAt。

### ZIP Export / Import

- 日期：2026-06-28
- 實作範圍：
  - 新增 `GET /api/workspace/export`。
  - 新增 `POST /api/workspace/import`。
  - 匯出 ZIP 內容包含 `collection.json` 與 `protos/` 目錄。
  - `collection.json` 包含 schema version、collections、folders、requests、file references。
  - 匯出時會掃描 HTTP request payload 中的 form-data file references，若有對應本機檔案會放入 `files/`。
  - 匯入時會建立新的 Collection，不覆蓋既有 Collection。
  - 匯入時會重建 Folder 與 Request，並重新對應 Collection/Folder ID。
  - 匯入時若 ZIP 內包含 file references，會將檔案複製到本機 `post-bubi.storage.files-dir` 並重新對應 `fileId`。
  - 前端 sidebar 新增「匯出 ZIP」與「匯入 ZIP」操作。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
java -jar post-bubi-api/build/libs/post-bubi.jar --spring.datasource.url=jdbc:h2:file:./build/verify/post-bubi-archive-2 --server.port=18080
curl -s -i -X POST http://127.0.0.1:18080/api/collections -H 'Content-Type: application/json' -d '{"name":"ZIP 驗證 Collection","description":"匯出匯入驗證"}'
curl -s -i -X POST http://127.0.0.1:18080/api/requests -H 'Content-Type: application/json' -d '{"collectionId":1,"folderId":null,"type":"HTTP","name":"ZIP 健康檢查","sortOrder":0,"payloadJson":"{\"method\":\"GET\",\"url\":\"http://localhost:18080/api/health\",\"bodyType\":\"none\"}"}'
curl -s -o build/verify/post-bubi-workspace.zip http://127.0.0.1:18080/api/workspace/export
unzip -l build/verify/post-bubi-workspace.zip
curl -s -i -X POST http://127.0.0.1:18080/api/workspace/import -F file=@build/verify/post-bubi-workspace.zip
curl -s -i http://127.0.0.1:18080/api/collections
```

- 結果：
  - `bootJar` 成功。
  - 首頁回應 200，載入新版前端 assets。
  - Collection 建立 API 回應 201。
  - Request 建立 API 回應 201。
  - 匯出的 ZIP 可由 `unzip -l` 讀取，包含 `collection.json` 與 `protos/`。
  - `POST /api/workspace/import` 回應 200，結果為 `{"collections":1,"folders":0,"requests":1,"protos":0}`。
  - 匯入後 `GET /api/collections` 可看到原 Collection 與「匯入」副本，確認未覆蓋既有資料。

### ZIP Proto 檔案匯出 / 匯入

- 日期：2026-06-29
- 實作範圍：
  - `GET /api/workspace/export` 會將已上傳 proto 檔案放入 ZIP 的 `protos/` 目錄。
  - `collection.json` 新增 protos metadata，記錄 `protoId`、相對路徑與原始檔名。
  - `POST /api/workspace/import` 會將 ZIP 內 proto 檔案寫入本機 `post-bubi.storage.protos-dir`。
  - 匯入 proto 會重新產生新的 `protoId`，避免與既有檔案衝突。
  - 匯入結果新增 `protos` 數量。
  - Vue 匯入完成訊息會顯示 Proto 匯入數量。
  - 舊版 ZIP 若沒有 protos 欄位仍可匯入。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:test
```

- 結果：
  - `compileJava` 成功。
  - `compileTestJava` 成功。
  - `:post-bubi-api:test` 成功。
  - ZIP 內包含 `protos/{protoId}-archive.proto`。
  - 匯入後 `/api/protos` 可看到匯入的 proto。
  - 匯入結果 `protos` 數量正確。

### Proto Upload 與 Inspect

- 日期：2026-06-28
- 實作範圍：
  - 新增 `POST /api/protos`，可上傳單一 `.proto` 檔。
  - Proto 檔會儲存在 `post-bubi.storage.protos-dir`，預設為 `./data/protos`。
  - 新增 `GET /api/protos`，回傳已上傳 Proto 列表。
  - 新增 `GET /api/protos/{protoId}/inspect`，解析 Proto 內容。
  - Inspect 結果包含 package、imports、messages、services、rpc methods、request/response type、client/server streaming flag。
  - 前端 sidebar 新增 Protos 區塊，可上傳 `.proto`、顯示列表、點選後顯示 inspect 結果。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
java -jar post-bubi-api/build/libs/post-bubi.jar --spring.datasource.url=jdbc:h2:file:./build/verify/post-bubi-proto-2 --post-bubi.storage.protos-dir=./build/verify/protos-2 --server.port=18080
curl -s -i http://127.0.0.1:18080/
curl -s -i -X POST http://127.0.0.1:18080/api/protos -F file=@data/proto/common/service.proto
curl -s -i http://127.0.0.1:18080/api/protos
curl -s -i http://127.0.0.1:18080/api/protos/65781cc1-e4b2-4588-a9c7-b661f6a1bb1f/inspect
```

- 結果：
  - `bootJar` 成功。
  - 首頁回應 200，載入新版前端 assets。
  - `POST /api/protos` 回應 201，回傳完整 `protoId`。
  - `GET /api/protos` 回應 200，列表包含完整 UUID 格式 `protoId` 與原始檔名。
  - `GET /api/protos/{protoId}/inspect` 回應 200，可解析 `packageName`、`imports`、service `Service` 與 unary method `rpcPeriphery`。

### gRPC Unary Execute API

- 日期：2026-06-28
- 實作範圍：
  - 新增 `POST /api/grpc/execute`。
  - 第一階段支援使用 target gRPC server 的 server reflection 取得 service/method descriptor。
  - 支援 plaintext channel。
  - 支援 host、port、serviceName、methodName、JSON request body、timeout。
  - 使用 Protobuf `DynamicMessage` 與 `JsonFormat` 將 JSON request 轉成 gRPC request message。
  - 使用動態 `MethodDescriptor` 呼叫 unary method。
  - 回應包含 status code、status description、duration、metadata、JSON response body 與 error message。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
java -jar post-bubi-api/build/libs/post-bubi.jar --spring.datasource.url=jdbc:h2:file:./build/verify/post-bubi-grpc --server.port=18080
curl -s -i http://127.0.0.1:18080/
curl -s -i -X POST http://127.0.0.1:18080/api/grpc/execute -H 'Content-Type: application/json' -d '{"host":"","port":50051,"plaintext":true,"serviceName":"demo.EchoService","methodName":"Echo","body":"{}","timeoutMillis":1000}'
curl -s -i -X POST http://127.0.0.1:18080/api/grpc/execute -H 'Content-Type: application/json' -d '{"host":"127.0.0.1","port":59999,"plaintext":true,"serviceName":"demo.EchoService","methodName":"Echo","body":"{}","timeoutMillis":1000}'
```

- 結果：
  - `bootJar` 成功。
  - 首頁回應 200。
  - Host 空白時回應 400，錯誤碼為 `GRPC_HOST_REQUIRED`。
  - 連到沒有 gRPC server 的 port 時回應 400，錯誤碼為 `GRPC_REFLECTION_FAILED`。
  - 本階段尚未在實際可用且有開 server reflection 的 gRPC server 上驗證成功呼叫；後續使用者可提供 target server 測試。

### Vue gRPC Request Editor 與 Response Viewer

- 日期：2026-06-28
- 實作範圍：
  - Toolbar 新增 HTTP/gRPC request type 切換。
  - gRPC 模式可填 target `host:port`。
  - gRPC 模式可填 `package.Service/Method`。
  - Params tab 在 gRPC 模式顯示 Host、Port、Service、Method 欄位。
  - Headers tab 在 gRPC 模式作為 Metadata 編輯區。
  - Body tab 在 gRPC 模式作為 JSON request body 編輯區。
  - Settings tab 在 gRPC 模式新增 Plaintext 設定。
  - 送出 gRPC request 時呼叫 `POST /api/grpc/execute`。
  - Response viewer 可顯示 gRPC body、metadata 與 info。
  - Request 保存流程支援 `GRPC` 類型與 gRPC payload。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
java -jar post-bubi-api/build/libs/post-bubi.jar --spring.datasource.url=jdbc:h2:file:./build/verify/post-bubi-grpc-ui --server.port=18080
curl -s -i http://127.0.0.1:18080/
curl -s -i -X POST http://127.0.0.1:18080/api/grpc/execute -H 'Content-Type: application/json' -d '{"host":"","port":50051,"plaintext":true,"serviceName":"demo.EchoService","methodName":"Echo","body":"{}","timeoutMillis":1000}'
curl -s -i -X POST http://127.0.0.1:18080/api/grpc/execute -H 'Content-Type: application/json' -d '{"host":"127.0.0.1","port":59999,"plaintext":true,"serviceName":"demo.EchoService","methodName":"Echo","body":"{}","timeoutMillis":1000}'
```

- 結果：
  - `bootJar` 成功。
  - 首頁回應 200，載入新版前端 assets。
  - Host 空白時回應 400，錯誤碼為 `GRPC_HOST_REQUIRED`。
  - 連到沒有 gRPC server 的 port 時回應 400，錯誤碼為 `GRPC_REFLECTION_FAILED`。
  - 本階段尚未在實際可用且有開 server reflection 的 gRPC server 上驗證成功呼叫；後續使用者可提供 target server 測試。

### gRPC TLS 忽略憑證驗證

- 日期：2026-06-29
- 實作範圍：
  - `POST /api/grpc/execute` 新增 `ignoreTlsVerification` request setting。
  - Plaintext 開啟時維持原本 plaintext channel。
  - Plaintext 關閉且 `ignoreTlsVerification=false` 時使用一般 TLS channel。
  - Plaintext 關閉且 `ignoreTlsVerification=true` 時使用不驗證憑證鏈的 TLS channel。
  - Vue gRPC Settings tab 在 TLS 模式顯示 `Ignore TLS certificate verification` 開關。
  - gRPC request 保存與載入流程會保留 `grpcIgnoreTlsVerification`。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:test
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
java -jar post-bubi-api/build/libs/post-bubi.jar --spring.datasource.url=jdbc:h2:file:./build/verify/post-bubi-grpc-tls --server.port=18080
curl -s -i http://127.0.0.1:18080/
curl -s -X POST http://127.0.0.1:18080/api/grpc/execute -H 'Content-Type: application/json' -d '{"host":"127.0.0.1","port":59999,"plaintext":false,"ignoreTlsVerification":true,"serviceName":"demo.EchoService","methodName":"Echo","body":"{}","timeoutMillis":1000}'
```

- 結果：
  - `:post-bubi-api:test` 成功。
  - `:post-bubi-api:bootJar` 成功。
  - 首頁回應 200，載入新版前端 asset `index-Du1ZGR93.js`。
  - API 可接收 `ignoreTlsVerification=true` 並進入 TLS channel 建立流程；連到沒有 gRPC server 的 port 時回應 400，錯誤碼為 `GRPC_REFLECTION_FAILED`。
  - 本階段尚未在實際使用自簽憑證且有開 server reflection 的 gRPC TLS server 上驗證成功呼叫；後續使用者可提供 target server 測試。

### 品牌主色與 Logo 套用

- 日期：2026-06-29
- 實作範圍：
  - `docs/DEVELOPMENT_SPEC.md` 新增品牌視覺規範。
  - 將使用者提供的 `etc/img/PostBubi.png` 複製到 `post-bubi-ui/src/assets/post-bubi-logo.png`。
  - Vue sidebar 品牌區改用 logo 圖片，並透過 Vite asset import 參與打包。
  - 前端 CSS 新增品牌色變數，品牌主色為酒紅色 `#AB005F`。
  - 主操作按鈕與送出按鈕套用品牌主色與 hover 色。
  - Collection/Folder/Request/Proto 目前選取狀態使用淡酒紅底色與品牌色文字。
  - active tab、Proto method hover、History method 與 keyboard focus 套用品牌色系。
  - 移除原本作為主視覺的青綠色。
  - `post-bubi-ui` 打包時清空 `build/devJar` 與 `build/prodJar` 後再複製前端產物，避免舊 hash asset 累積。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
rg -n "ab005f|930052|fce7f2|e6a9ca|0f766e|e6f4f1" post-bubi-ui/dist/post-bubi/assets/index-CTO8UZA6.css post-bubi-ui/src/styles.css
jar tf post-bubi-ui/build/libs/post-bubi-ui-0.1.0-SNAPSHOT.jar | rg "public/(index.html|assets/)"
curl -s -i http://127.0.0.1:18080/
curl -s -i http://127.0.0.1:18080/assets/index-CTO8UZA6.css
curl -s -i http://127.0.0.1:18080/assets/post-bubi-logo-Xq1HpFiw.png
```

- 結果：
  - `:post-bubi-api:bootJar` 成功。
  - 前端 CSS 產物 `index-CTO8UZA6.css` 包含 `#ab005f`、`#930052`、`#fce7f2` 與 `#e6a9ca`。
  - 前端 CSS 產物未再出現舊主色 `#0f766e` 與舊淡青綠底色 `#e6f4f1`。
  - UI resource JAR 包含 `public/index.html`、新版 CSS/JS asset 與 logo 圖片 asset。
  - 首頁回應 200，載入新版前端 asset `index-CTO8UZA6.css`。
  - CSS asset 回應 200，Content-Type 為 `text/css`。
  - Logo asset 回應 200，Content-Type 為 `image/png`。

### Light / Dark Theme

- 日期：2026-06-29
- 實作範圍：
  - 左側 sidebar 新增 Light / Dark segmented control。
  - 使用 `themeMode` 前端狀態管理目前主題。
  - 使用 `document.documentElement.dataset.theme` 套用主題。
  - 使用 `localStorage` 保存 `post-bubi-theme`，重新整理後保留使用者選擇。
  - CSS 以 Light/Dark 兩組變數管理文字、背景、邊框、狀態色與品牌色。
  - 補齊 input、select、textarea、pre、file picker、history list 等區塊在 Dark Theme 的背景與文字色。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
rg -n "data-theme|post-bubi-theme|themeMode|Light|Dark" post-bubi-ui/src/App.vue post-bubi-ui/src/styles.css
rg -n "color-scheme: dark|--surface: #151a1f|--text-primary: #edf2f5" post-bubi-ui/src/styles.css post-bubi-ui/dist/post-bubi/assets/*.css
curl -s -i http://127.0.0.1:18080/
curl -s -i http://127.0.0.1:18080/assets/index-CTO8UZA6.css
```

- 結果：
  - `:post-bubi-api:bootJar` 成功。
  - 前端已包含 Light/Dark 切換控制。
  - CSS source 與打包產物包含 Dark Theme 變數。
  - 首頁回應 200，載入新版前端 assets。

### 工作台 UI/UX 基礎升級

- 日期：2026-06-29
- 實作範圍：
  - Sidebar 拆分為品牌區、Theme 控制、全域動作、Collection tree 與 Proto 區塊。
  - Sidebar Collection 與 Proto 區塊新增數量標示。
  - Sidebar 內容區改為可捲動，避免資料多時壓縮主要操作。
  - Request toolbar 調整為更明確的 request type、method/target、保存與送出工作流。
  - Request meta 顯示目前 request 名稱、保存位置與狀態 pill。
  - Response bar 新增目前 tab 與 response summary pill。
  - 統一 tree item、button、input、select 的 hover、focus、active、disabled 狀態。
  - 加入中等寬度桌面的 toolbar/request meta layout 保護，避免內容擠壓。
  - Light/Dark Theme 下維持同一套視覺層級與互動狀態。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
curl -s -i http://127.0.0.1:18080/
curl -s -i http://127.0.0.1:18080/assets/index--w5qe_Mp.css
```

- 結果：
  - `:post-bubi-api:bootJar` 成功。
  - 前端 CSS 產物 `index--w5qe_Mp.css` 已產生。
  - 首頁回應 200，載入新版前端 assets。
  - CSS asset 回應 200，Content-Type 為 `text/css`。

### Proto Method 套用到 gRPC Editor

- 日期：2026-06-29
- 實作範圍：
  - Proto inspect 的 rpc method 由純文字改為可點選項。
  - 點選 rpc method 後自動切換 request type 為 `GRPC`。
  - 自動帶入完整 service name；若 proto 有 package，格式為 `package.Service`。
  - 自動帶入 method name。
  - 自動將 request 名稱設為 `package.Service/Method`。
  - 自動將 gRPC JSON request body 初始化為 `{}`。
  - 點選後清空既有 response 與錯誤訊息，方便重新送出。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
java -jar post-bubi-api/build/libs/post-bubi.jar --spring.datasource.url=jdbc:h2:file:./build/verify/post-bubi-proto-apply --server.port=18080
curl -s -i http://127.0.0.1:18080/
curl -s -i -X POST http://127.0.0.1:18080/api/protos -F file=@data/proto/common/service.proto
curl -s http://127.0.0.1:18080/api/protos
curl -s http://127.0.0.1:18080/api/protos/d8cfce0f-f4a5-4869-ba13-26774bfef7f9/inspect
```

- 結果：
  - `bootJar` 成功。
  - 首頁回應 200，載入新版前端 assets。
  - Proto 上傳 API 回應 201。
  - Proto 列表可查到上傳的 `service.proto`。
  - Proto inspect 可解析出 package `com.bot.fsap.model.grpc.common`、service `Service` 與 method `rpcPeriphery`。
  - UI 可由 inspect 結果套用成 gRPC method `com.bot.fsap.model.grpc.common.Service/rpcPeriphery`。

### 基本後端整合測試

- 日期：2026-06-29
- 實作範圍：
  - 新增 `post-bubi-api/src/test/java/com/postbubi/web/WorkspaceApiIntegrationTest.java`。
  - 使用 Spring Boot Test 與 MockMvc 測試 Workspace REST API。
  - 測試使用 H2 memory DB 與 `ddl-auto=create-drop`，不污染本機 `./data/post-bubi`。
  - 覆蓋 Collection 建立。
  - 覆蓋 Folder 建立。
  - 覆蓋 Request 保存到 Folder。
  - 覆蓋刪除 Folder 後一併移除底下 Request。
  - 覆蓋 Collection 名稱空白時的統一錯誤格式。
  - 覆蓋查無 Request 時的統一錯誤格式。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:test
```

- 結果：
  - `compileTestJava` 成功。
  - `:post-bubi-api:test` 成功。
  - Workspace CRUD 整合測試通過。
  - `REQUIRED_FIELD` 與 `REQUEST_NOT_FOUND` 錯誤格式測試通過。

### HTTP Execute 自動化測試

- 日期：2026-06-29
- 實作範圍：
  - 新增 `post-bubi-api/src/test/java/com/postbubi/web/HttpExecuteIntegrationTest.java`。
  - 使用 Spring Boot random port 與 `TestRestTemplate` 測試真實 HTTP server。
  - 測試使用 H2 memory DB 與 `ddl-auto=create-drop`，不污染本機 `./data/post-bubi`。
  - 覆蓋 `POST /api/http/execute` 送出 GET request。
  - GET target 使用同一個測試 server 的 `/api/health`，避免依賴外部網路。
  - 覆蓋 HTTP execute response 的 `statusCode`、`body` 與 `bodyBase64Encoded`。
  - 覆蓋 execute 後 `GET /api/http/history` 可查到成功紀錄。
  - 覆蓋 invalid URL 回傳 `HTTP_URL_INVALID` 與中文錯誤訊息。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:test
```

- 結果：
  - `compileTestJava` 成功。
  - `:post-bubi-api:test` 成功。
  - HTTP GET execute 整合測試通過。
  - Request history 保存測試通過。
  - `HTTP_URL_INVALID` 錯誤格式測試通過。

### File Upload / Form-data 自動化測試

- 日期：2026-06-29
- 實作範圍：
  - 新增 `post-bubi-api/src/test/java/com/postbubi/web/FileUploadIntegrationTest.java`。
  - 使用 Spring Boot random port 與 `TestRestTemplate` 測試 multipart 上傳。
  - 測試使用 H2 memory DB 與測試專用 `post-bubi.storage.files-dir`。
  - 覆蓋 `POST /api/files` 直接上傳檔案。
  - 覆蓋檔案上傳 response 的 `fileId`、`originalFilename`、`contentType` 與 `sizeBytes`。
  - 覆蓋 `POST /api/http/execute` 的 `bodyType=form-data` 與 file part。
  - 覆蓋 multipart execute 會略過手動 `Content-Type` header，讓後端自動產生 boundary。
  - 覆蓋空 form-data 回傳 `HTTP_FORM_DATA_REQUIRED` 與中文錯誤訊息。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:test
```

- 結果：
  - `compileTestJava` 成功。
  - `:post-bubi-api:test` 成功。
  - `/api/files` multipart 上傳測試通過。
  - HTTP execute form-data file 測試通過。
  - `HTTP_FORM_DATA_REQUIRED` 錯誤格式測試通過。

### ZIP Export / Import 自動化測試

- 日期：2026-06-29
- 實作範圍：
  - 新增 `post-bubi-api/src/test/java/com/postbubi/web/WorkspaceArchiveIntegrationTest.java`。
  - 使用 Spring Boot random port 與 `TestRestTemplate` 測試 workspace ZIP API。
  - 測試使用 H2 memory DB 與測試專用 `post-bubi.storage.files-dir`。
  - 覆蓋 Collection、Folder、Request 與 file reference 建立後匯出 ZIP。
  - 覆蓋匯出 ZIP 內包含 `collection.json` 與 `files/{fileId}-filename`。
  - 覆蓋匯入 ZIP 後新增 Collection、Folder、Request。
  - 覆蓋匯入後 Request 的 `folderId` 重新對應。
  - 覆蓋匯入後 form-data file `fileId` 重新對應且移除 `archivePath`。
  - 覆蓋 ZIP 內含不合法路徑時回傳 `WORKSPACE_IMPORT_PATH_INVALID`。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:test
```

- 結果：
  - `compileTestJava` 成功。
  - `:post-bubi-api:test` 成功。
  - ZIP export/import 整合測試通過。
  - file reference 重新對應測試通過。
  - zip slip path traversal 防護測試通過。

### Proto Upload / Inspect 自動化測試

- 日期：2026-06-29
- 實作範圍：
  - 新增 `post-bubi-api/src/test/java/com/postbubi/web/ProtoIntegrationTest.java`。
  - 使用 Spring Boot random port 與 `TestRestTemplate` 測試 Proto API。
  - 測試使用 H2 memory DB 與測試專用 `post-bubi.storage.protos-dir`。
  - 覆蓋 `POST /api/protos` 上傳 `.proto`。
  - 覆蓋 `GET /api/protos` 列表。
  - 覆蓋 `GET /api/protos/{protoId}/inspect` 解析 package、imports、messages、services 與 rpc methods。
  - 覆蓋 server streaming method 解析。
  - 覆蓋非 `.proto` 檔案回傳 `PROTO_FILE_EXTENSION_INVALID` 與中文錯誤訊息。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:test
```

- 結果：
  - `compileTestJava` 成功。
  - `:post-bubi-api:test` 成功。
  - Proto upload/list/inspect 整合測試通過。
  - streaming method 解析測試通過。
  - `PROTO_FILE_EXTENSION_INVALID` 錯誤格式測試通過。

### gRPC Execute 自動化測試

- 日期：2026-06-29
- 實作範圍：
  - 新增 `post-bubi-api/src/test/java/com/postbubi/web/GrpcExecuteIntegrationTest.java`。
  - 測試內啟動本機 gRPC server，不依賴外部服務。
  - 測試 gRPC server 提供動態建立的 `demo.EchoService/Echo` unary method。
  - 測試 gRPC server 開啟 server reflection。
  - 覆蓋 `POST /api/grpc/execute` 透過 reflection 解析 descriptor 並成功呼叫 unary method。
  - 覆蓋 gRPC JSON body 轉 Protobuf `DynamicMessage`。
  - 覆蓋 response body 轉 JSON。
  - 覆蓋 invalid gRPC JSON body 回傳 `GRPC_REQUEST_JSON_INVALID` 與中文錯誤訊息。
- 驗證指令：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:test
```

- 結果：
  - `compileTestJava` 成功。
  - `:post-bubi-api:test` 成功。
  - gRPC reflection descriptor resolver 成功路徑測試通過。
  - gRPC unary execute 成功路徑測試通過。
  - `GRPC_REQUEST_JSON_INVALID` 錯誤格式測試通過。

## 使用者測試方式

目前可測階段：HTTP request editor、Request 保存、Folder tree UI、form-data/file upload、Request history、ZIP 匯出/匯入、Proto upload/inspect、Proto method 套用到 gRPC editor、gRPC unary execute API、Vue gRPC request editor、gRPC TLS 忽略憑證驗證、品牌主色與 Logo 套用、Light / Dark Theme、工作台 UI/UX 基礎升級。

1. 建置：

```bash
GRADLE_USER_HOME=.gradle-home ./gradlew :post-bubi-api:bootJar
```

2. 啟動：

```bash
java -jar post-bubi-api/build/libs/post-bubi.jar
```

3. 開啟：

```text
http://localhost:18080
```

4. 可測項目：

- 點「新增 Collection」建立 Collection。
- 點 Collection 右側「刪除」可刪除 Collection；刪除時會確認，底下 Request 會一起移除。
- 點 Collection 或 Folder 右側「+F」可新增 Folder 或子 Folder。
- 點 Folder 可把接下來另存或儲存的 Request 放入該 Folder。
- 點 Folder 右側「刪除」可刪除 Folder；若 Folder 內有 Request 會一併移除，若仍有子 Folder 則需先刪除子 Folder。
- 編輯 Request 名稱、method、URL、params、headers、body、settings。
- 點「另存 Request」把目前 HTTP request 存進左側選取的 Collection。
- 點左側已儲存 Request，可載入 request 設定。
- 修改已載入 Request 後點「儲存」，重新整理頁面後應仍保留修改內容。
- 點「刪除 Request」可刪除目前選取的 Request。
- 預設 URL 為 `http://localhost:18080/api/health`，按「送出」應看到 response body。
- Params tab 可用每行 `name=value` 加 query string。
- Headers tab 可用每行 `name=value` 加 header。
- Body tab 可切換 `none`、`JSON`、`raw text`、`x-www-form-urlencoded`。
- Body tab 可切換 `form-data`，新增 text 欄位或 file 欄位；file 欄位選檔後會先上傳，送出時會以 multipart form-data 發送。
- Settings tab 可調整 timeout、follow redirects、ignore SSL certificate verification。
- Response 區塊的 History tab 可查看最近 50 筆 HTTP execute 紀錄。
- 點 History 內任一筆紀錄，可把該筆 request 載回上方 HTTP editor。
- 左側 sidebar 可點「匯出 ZIP」下載目前 workspace。
- 左側 sidebar 可點「匯入 ZIP」匯入 Post Bubi ZIP；匯入會新增 Collection，不覆蓋既有資料。
- 左側 sidebar 的 Protos 區塊可點「上傳 Proto」選擇 `.proto` 檔。
- 點 Protos 列表內的檔案，可查看 package、services、rpc methods 與 messages。
- 點 Proto inspect 內任一 rpc method，可自動切換到 gRPC editor 並帶入 `package.Service/Method`。
- gRPC unary execute API 可用 `POST /api/grpc/execute` 或 Vue gRPC editor 測試。
- gRPC target server 必須開啟 server reflection，第一階段才能動態解析 descriptor 並呼叫 unary method。
- Toolbar 左側可切換 `HTTP` / `gRPC`。
- gRPC 模式可輸入 `host:port`、`package.Service/Method`、Metadata、JSON body、Plaintext，再按「送出」。
- gRPC Settings tab 取消勾選 Plaintext 後，會顯示 `Ignore TLS certificate verification`；若目標是自簽憑證 TLS gRPC server，可勾選後測試。
- gRPC request 可另存為 Collection request，類型會保存為 `GRPC`。
- 介面主色應為酒紅色 `#AB005F`，可檢查主按鈕、送出按鈕、選取狀態與 active tab。
- 左側 sidebar 頂部應顯示 Post Bubi logo 圖片。
- 左側 sidebar 的 Theme 控制可切換 `Light` / `Dark`。
- 切換到 Dark 後，背景、表單、editor、response、history 與 sidebar 應改為深色且文字可讀。
- 重新整理頁面後，前一次選擇的 Theme 應保留。
- 左側 sidebar 應清楚分為品牌、Theme、全域動作、Collections、Protos。
- Collections 與 Protos 標題右側應顯示數量。
- Request 名稱下方應顯示目前 Collection / Folder 保存位置。
- Response 標題列右側應顯示執行摘要 pill。
- 在較窄桌面寬度下 toolbar 不應溢出或互相遮擋。

## 本輪開發目標

上一輪目標：

- 建立 H2/JPA entities：
  - `collections`
  - `folders`
  - `requests`
- 實作 CRUD API：
  - `GET /api/collections`
  - `POST /api/collections`
  - `PUT /api/collections/{id}`
  - `DELETE /api/collections/{id}`
  - `POST /api/folders`
  - `PUT /api/folders/{id}`
  - `DELETE /api/folders/{id}`
  - `GET /api/requests/{id}`
  - `POST /api/requests`
  - `PUT /api/requests/{id}`
  - `DELETE /api/requests/{id}`
  - `POST /api/requests/{id}/duplicate`
- 建立基本統一錯誤回應格式。

本輪結果：

- 已完成 H2/JPA entities。
- 已完成 Collection / Folder / Request CRUD API。
- 已完成 CRUD API 的基本統一錯誤回應。
- 已完成本輪編譯、API 與 `bootJar` 驗證。

本輪目標：

- 建立 HTTP execute API。
- 串接 Vue HTTP request editor 與 response viewer。
- 讓使用者啟動服務後能從畫面送出 HTTP request。

本輪結果：

- 已完成 `POST /api/http/execute`。
- 已完成 Vue HTTP request editor 與 response viewer。
- 已完成 GET、POST JSON、錯誤 URL、首頁載入與 `bootJar` 驗證。

本輪目標：

- 串接前端 Collection / Request CRUD。
- 讓使用者可以從 UI 保存、載入、更新、刪除 HTTP request。

本輪結果：

- 已完成 Collection 載入與新增。
- 已完成 Collection 刪除。
- 已完成 HTTP Request 保存、載入、更新、刪除。
- 已完成 `bootJar`、首頁載入與 CRUD API 保存流程驗證。

本輪目標：

- 實作 HTTP multipart form-data。
- 實作本機 file upload，讓 form-data file 欄位可引用已上傳檔案。
- 串接 Vue Body tab 的 form-data 編輯器。

本輪結果：

- 已完成 `POST /api/files`。
- 已完成 `POST /api/http/execute` 的 `bodyType=form-data`。
- 已完成 Vue form-data text/file 欄位編輯與檔案上傳。
- 已完成 `bootJar`、首頁載入、檔案上傳、空 form-data 錯誤與 multipart file execute 驗證。

本輪目標：

- 實作 Request history 資料表與查詢 API。
- HTTP execute 後保存執行紀錄。
- 前端可查看最近 history 並載回 request editor。

本輪結果：

- 已完成 `request_histories`。
- 已完成 `GET /api/http/history`。
- 已完成 HTTP execute 成功與執行失敗的 history 保存。
- 已完成 Vue History tab 與載回 editor。
- 已完成 `bootJar`、首頁載入、初始 history、HTTP execute 後 history 查詢驗證。

本輪目標：

- 實作 Post Bubi ZIP export。
- 實作 Post Bubi ZIP import。
- 前端提供匯出與匯入操作入口。

本輪結果：

- 已完成 `GET /api/workspace/export`。
- 已完成 `POST /api/workspace/import`。
- 已完成 ZIP 內 `collection.json`、`files/` references 與 `protos/` 目錄處理。
- 已完成 Vue sidebar 匯出/匯入按鈕。
- 已完成 `bootJar`、ZIP 匯出、ZIP 結構檢查、ZIP 匯入與 Collection 副本驗證。

本輪目標：

- 實作 Proto upload。
- 實作 Proto list。
- 實作 Proto inspect。
- 前端提供 Proto 上傳、列表與 inspect 結果。

本輪結果：

- 已完成 `POST /api/protos`。
- 已完成 `GET /api/protos`。
- 已完成 `GET /api/protos/{protoId}/inspect`。
- 已完成 Vue Protos sidebar 區塊。
- 已完成 `bootJar`、首頁載入、Proto 上傳、Proto 列表與 Proto inspect 驗證。

本輪目標：

- 實作 gRPC unary execute API。
- 透過 server reflection 取得 service/method descriptor。
- 支援 JSON request body 轉 Protobuf DynamicMessage。

本輪結果：

- 已完成 `POST /api/grpc/execute`。
- 已完成 server reflection descriptor resolver。
- 已完成 DynamicMessage unary call 流程。
- 已完成 `bootJar`、首頁載入、參數驗證與 reflection 失敗格式驗證。
- 實際成功呼叫需使用有開 server reflection 的 gRPC target server 測試。

本輪目標：

- 實作 Vue gRPC request editor。
- 串接 `POST /api/grpc/execute`。
- gRPC response viewer 顯示 body、metadata 與 info。
- gRPC request 可保存到 Collection。

本輪結果：

- 已完成 HTTP/gRPC 模式切換。
- 已完成 gRPC target、method、metadata、JSON body、settings 編輯。
- 已完成 gRPC 送出與 response viewer。
- 已完成 gRPC request payload 保存。
- 已完成 `bootJar`、首頁載入與 gRPC execute 錯誤路徑驗證。
- 實際成功呼叫需使用有開 server reflection 的 gRPC target server 測試。

本輪目標：

- 實作 Vue Folder tree UI。
- 支援 Collection 下新增 Folder 與 Folder 下新增子 Folder。
- Request 保存時可指定目前選取的 Folder。
- 支援刪除 Folder 並更新 sidebar。

本輪結果：

- 已完成 Folder 新增、子 Folder 新增與縮排顯示。
- 已完成 Folder 選取與 Request `folderId` 保存。
- 已完成選取 Request 時同步選取所在 Folder。
- 已完成 Folder 刪除操作與錯誤訊息串接。
- 已完成 `bootJar`、首頁載入、Folder CRUD 與 Request folderId 驗證。

本輪目標：

- 將 Proto inspect 的 rpc method 串接到 gRPC editor。
- 點選 method 後自動帶入完整 `package.Service/Method`。
- 讓使用者從已上傳 proto 更快建立 gRPC unary request。

本輪結果：

- 已完成 Proto method 可點選操作。
- 已完成 request type 自動切換為 `GRPC`。
- 已完成 service/method、request 名稱與 JSON body 初始化。
- 已完成 `bootJar`、首頁載入、Proto 上傳與 inspect 驗證。

本輪目標：

- 補上第一批後端自動化測試。
- 覆蓋 Workspace CRUD 主要流程。
- 覆蓋統一錯誤格式與中文錯誤訊息。

本輪結果：

- 已新增 `WorkspaceApiIntegrationTest`。
- 已完成 Collection、Folder、Request 保存到 Folder 與刪除 Folder 的整合測試。
- 已完成 `REQUIRED_FIELD` 與 `REQUEST_NOT_FOUND` 錯誤格式測試。
- 已完成 `:post-bubi-api:test` 驗證。

本輪目標：

- 補上 HTTP execute 自動化測試。
- 覆蓋不依賴外部網路的實際 HTTP GET execute。
- 覆蓋 HTTP execute history 與 invalid URL 錯誤格式。

本輪結果：

- 已新增 `HttpExecuteIntegrationTest`。
- 已完成 random port 測試 server 下的 `/api/http/execute` GET 測試。
- 已完成 `/api/http/history` 成功紀錄測試。
- 已完成 `HTTP_URL_INVALID` 錯誤格式測試。
- 已完成 `:post-bubi-api:test` 驗證。

本輪目標：

- 補上 file upload 自動化測試。
- 覆蓋 `/api/files` multipart upload。
- 覆蓋 HTTP execute 的 `form-data` file 欄位。

本輪結果：

- 已新增 `FileUploadIntegrationTest`。
- 已完成 `/api/files` multipart 上傳測試。
- 已完成 HTTP execute form-data file 測試。
- 已完成 `HTTP_FORM_DATA_REQUIRED` 錯誤格式測試。
- 已完成 `:post-bubi-api:test` 驗證。

本輪目標：

- 補上 ZIP export/import 自動化測試。
- 覆蓋含 Folder、Request、file reference 的 workspace 匯出與匯入。
- 覆蓋 ZIP import 的 path traversal 防護。

本輪結果：

- 已新增 `WorkspaceArchiveIntegrationTest`。
- 已完成 workspace ZIP 匯出結構測試。
- 已完成 ZIP 匯入後 Collection、Folder、Request 重建測試。
- 已完成 form-data file reference 重新對應測試。
- 已完成 `WORKSPACE_IMPORT_PATH_INVALID` 錯誤格式測試。
- 已完成 `:post-bubi-api:test` 驗證。

本輪目標：

- 補上 Proto upload / inspect 自動化測試。
- 覆蓋 package、imports、messages、services 與 rpc methods 解析。
- 覆蓋副檔名錯誤格式。

本輪結果：

- 已新增 `ProtoIntegrationTest`。
- 已完成 Proto upload/list/inspect 測試。
- 已完成 unary 與 server streaming rpc method 解析測試。
- 已完成 `PROTO_FILE_EXTENSION_INVALID` 錯誤格式測試。
- 已完成 `:post-bubi-api:test` 驗證。

本輪目標：

- 補上 gRPC execute 自動化測試。
- 使用本機 reflection gRPC server 驗證 unary 成功呼叫。
- 覆蓋 gRPC JSON request body 錯誤格式。

本輪結果：

- 已新增 `GrpcExecuteIntegrationTest`。
- 已完成本機 gRPC server reflection 測試。
- 已完成 `/api/grpc/execute` unary 成功呼叫測試。
- 已完成 `GRPC_REQUEST_JSON_INVALID` 錯誤格式測試。
- 已完成 `:post-bubi-api:test` 驗證。

上一輪目標：

- 補齊 ZIP export / import 的 proto 檔案處理。
- 匯出 ZIP 時將已上傳 proto 放入 `protos/`。
- 匯入 ZIP 時將 proto 寫回本機 proto storage。
- 匯入完成訊息顯示 proto 數量。

上一輪結果：

- 已完成 proto 檔案匯出至 ZIP。
- 已完成 proto 檔案從 ZIP 匯入。
- 已完成匯入結果 `protos` 數量回傳與前端顯示。
- 已更新 `WorkspaceArchiveIntegrationTest` 覆蓋 proto 匯出/匯入。
- 已完成 `:post-bubi-api:test` 驗證。

上一輪目標：

- 補齊 gRPC TLS 模式的 Ignore TLS certificate verification 設定。
- 前端只在 TLS 模式顯示忽略憑證驗證開關。
- 後端依 request setting 建立一般 TLS 或不驗證憑證的 TLS channel。
- gRPC request 保存與載入需保留此設定。

上一輪結果：

- 已完成 `ignoreTlsVerification` request setting。
- 已完成後端 TLS channel 建立邏輯。
- 已完成 Vue gRPC Settings tab 條件顯示與 payload 保存。
- 已完成 `:post-bubi-api:test` 與 `:post-bubi-api:bootJar` 驗證。
- 已啟動本機 JAR 供使用者測試；實際自簽憑證 TLS 成功呼叫需搭配可用 target server 驗證。

上一輪目標：

- 將使用者提供的品牌主色 `#AB005F` 與 logo 納入開發規格。
- 將前端主視覺、logo 與主要互動狀態改用品牌視覺。
- 移除原本青綠色主視覺，保留工具型介面的中性底色與可讀性。

上一輪結果：

- 已完成 `docs/DEVELOPMENT_SPEC.md` 品牌視覺規範。
- 已完成 logo 圖片納入 `post-bubi-ui/src/assets/post-bubi-logo.png`。
- 已完成 Vue sidebar 品牌區顯示 logo。
- 已完成前端 CSS 品牌色變數。
- 已完成主按鈕、送出按鈕、選取狀態、active tab、Proto method hover、History method 與 keyboard focus 的品牌色套用。
- 已修正 UI resource JAR 打包流程，避免舊 hash asset 累積。
- 已完成 `:post-bubi-api:bootJar` 驗證。
- 已確認前端 CSS 產物包含品牌色並移除舊青綠主色。
- 已確認 UI resource JAR 包含最新前端 asset 與 logo 圖片 asset。

上一輪目標：

- 新增 Light / Dark Theme 切換。
- 使用 CSS 變數管理兩套主題色。
- 主題選擇需保存到瀏覽器本機，重新整理後保留。
- Dark Theme 必須覆蓋工具主要區塊，不留下明顯白底。

上一輪結果：

- 已完成 sidebar Light / Dark segmented control。
- 已完成 `data-theme` 主題套用與 `localStorage` 保存。
- 已完成 Light/Dark CSS 變數。
- 已完成 input、select、textarea、pre、file picker、history list 等區塊暗色樣式。
- 已完成 `:post-bubi-api:bootJar` 驗證。

本輪目標：

- 大幅優化工作台 UI/UX 的第一階段基礎。
- 重整 sidebar、toolbar、request meta 與 response 區塊的視覺層級。
- 讓主要操作流更清楚，降低資訊擁擠感。
- 保持 Light/Dark Theme 與品牌色一致。

本輪結果：

- 已完成 sidebar 品牌區、Theme、全域動作、Collection、Proto 的區塊化整理。
- 已完成 Collection/Proto 數量標示。
- 已完成 request toolbar 的 layout 與焦點狀態優化。
- 已完成 request meta 保存位置提示與狀態 pill。
- 已完成 response summary pill 與目前 tab 顯示。
- 已完成 hover/focus/active/disabled 狀態一致化。
- 已完成中等寬度桌面的 layout 保護。
- 已完成 `:post-bubi-api:bootJar` 驗證。

## 未完成事項

- gRPC unary execute API 已透過本機 reflection gRPC server 自動化測試驗證成功呼叫；若需驗證使用者實際 target server，仍需由使用者提供可連線服務。
- gRPC TLS 忽略憑證驗證已完成設定串接；若需驗證自簽憑證成功呼叫，仍需可用且開啟 server reflection 的 TLS gRPC server。
- 後續可持續擴充前端端對端測試與更多錯誤情境。

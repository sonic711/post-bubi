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
| 7 | File upload 與 multipart form-data | 未開始 | 尚未驗證 |
| 8 | Request history | 未開始 | 尚未驗證 |
| 9 | ZIP export / import | 未開始 | 尚未驗證 |
| 10 | Proto upload 與 inspect | 未開始 | 尚未驗證 |
| 11 | gRPC unary execute API | 未開始 | 尚未驗證 |
| 12 | Vue gRPC request editor 與 response viewer | 未開始 | 尚未驗證 |
| 13 | 錯誤處理、中文訊息與基本測試 | 進行中 | 已完成 CRUD API 基本錯誤格式驗證，完整測試尚未完成 |

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

## 使用者測試方式

目前可測階段：HTTP request editor。

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

- 預設 URL 為 `http://localhost:18080/api/health`，按「送出」應看到 response body。
- Params tab 可用每行 `name=value` 加 query string。
- Headers tab 可用每行 `name=value` 加 header。
- Body tab 可切換 `none`、`JSON`、`raw text`、`x-www-form-urlencoded`。
- Settings tab 可調整 timeout、follow redirects、ignore SSL certificate verification。

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

## 未完成事項

- HTTP request 執行已完成第一階段；尚未支援 multipart form-data 與 file upload。
- gRPC unary request 執行尚未開始。
- 匯入 / 匯出 ZIP 尚未開始。
- 檔案上傳與 proto 管理尚未開始。
- 前端已可送 HTTP request，但 Collection / Folder / Request CRUD UI 尚未串接。
- 尚未加入自動化測試，目前本輪以編譯、bootRun 與 curl 驗證。

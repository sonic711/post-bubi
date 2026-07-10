# Post Bubi 開發進度

最後整理日期：2026-07-10

本文只記錄目前可用狀態、驗證方式與已知限制。歷史異動由 Git commit 保存，不在此重複累積逐輪開發日誌。

## 整體狀態

Post Bubi 已達到可供單人日常使用的 HTTP、gRPC unary 與 gRPC BUR 測試工具階段，可打包成單一 JAR 並部署到離線主機。現有功能以穩定、維護與使用者體驗為優先，暫不擴充新的協定類型。

| 領域 | 狀態 | 驗證依據 |
| --- | --- | --- |
| Java 17 + Spring Boot + Vue 3 專案 | 完成 | `:post-bubi-api:bootJar` |
| 單一 executable JAR | 完成 | UI、TBConvert、CodeTable 均包含於 JAR |
| H2 持久化 | 完成 | Workspace 整合測試 |
| Collection / Folder / Request | 完成 | CRUD、排序、複製、刪除整合測試 |
| HTTP request | 完成 | GET/POST、headers、body、form-data、history 整合測試 |
| gRPC unary | 完成 | reflection 與本機 proto 兩種成功呼叫測試 |
| gRPC BUR | 完成 | preview、固定長度組包、TBConvert 與內建 CodeTable 測試 |
| Proto 管理 | 完成 | upload、list、inspect、ZIP import/export 測試 |
| Workspace ZIP | 完成 | Collection、Folder、Request、files、protos 與 zip slip 測試 |
| Response 工具 | 完成 | JSON 上色、headers、info、history、base64 decoded |
| Light / Dark 與響應式 UI | 完成 | 1280、620、390px 瀏覽器驗證 |
| JAR 同層 log 與關閉開關 | 完成 | bootJar 與啟動參數驗證 |
| 離線 Maven repository task | 完成 | `zipOfflineMavenRepo` 與 `--offline` 流程 |

## 已完成功能

### Workspace

- Collection、Folder、Request 新增、修改與刪除。
- Folder 與 Request 同層拖拉排序。
- Request 複製。
- 未保存內容切換提示。
- Workspace ZIP 匯入與匯出，包含 uploaded files 與 protos。

### HTTP

- GET、POST、PUT、PATCH、DELETE。
- Query params。
- 多列 header key/value、啟用切換與拖拉排序。
- none、JSON、raw、x-www-form-urlencoded、multipart form-data。
- File upload 與 form-data file reference。
- Timeout、redirect、忽略 SSL certificate verification。
- 執行紀錄與歷史 request 載入。

### gRPC unary

- Plaintext 與 TLS。
- TLS certificate verification 關閉選項。
- Metadata。
- JSON request body。
- 使用 server reflection 取得 descriptor。
- 指定 `protoId` 時使用已上傳 proto，不依賴 target server reflection。
- Proto inspect 後可直接套用 service、method 與 request type。
- 目前只支援 unary method，不支援 client/server/bidirectional streaming。

### gRPC BUR

- 獨立 `GRPC_BUR` request type。
- Target host、port、metadata、TLS 與 timeout。
- TCPIP Header、MCS Header、Basic Label、Text Area 組包。
- 固定長度檢查與右補空白。
- UTF-8/BUR 雙向轉碼。
- Payload hex 與 decoded preview。
- 呼叫 `com.bot.fsap.model.grpc.common.Service/rpcPeriphery`。
- 解析 `PeripheryResponse.payload` 並顯示 BUR 明碼。
- `TBConvert.jar` 與 CodeTable 包入 executable JAR，部署時不需要外部 CodeTable。

### UI/UX

- 品牌色 `#AB005F` 與 logo。
- Light / Dark Theme，偏好保存在 browser local storage。
- Request/Response 工作區、狀態摘要與語意色。
- JSON request/response key/value 語法上色與自動排版。
- Response JSON path base64 decoded 設定，成功時可在 Body 原欄位直接顯示明碼。
- Collection、Folder、Request 水平三點操作選單。
- 1280、620、390px 無非預期水平溢位。
- Focus、disabled、reduced motion 與長文字處理。

## 自動化測試

| 測試 | 覆蓋範圍 |
| --- | --- |
| `WorkspaceApiIntegrationTest` | Collection、Folder、Request CRUD、排序、複製、錯誤格式 |
| `HttpExecuteIntegrationTest` | 本機 HTTP GET、history、invalid URL |
| `FileUploadIntegrationTest` | multipart upload、HTTP form-data file |
| `WorkspaceArchiveIntegrationTest` | ZIP export/import、file/proto reference、zip slip |
| `ProtoIntegrationTest` | Proto upload、list、inspect、rpc parsing |
| `GrpcExecuteIntegrationTest` | reflection unary、本機 proto unary、JSON 錯誤 |
| `GrpcBurExecuteIntegrationTest` | BUR payload preview、固定長度、JAR 內建 CodeTable |

標準驗證：

```bash
./gradlew :post-bubi-api:test
./gradlew :post-bubi-api:bootJar
```

2026-07-10 清理後驗證結果：

- 從無 `node_modules`、dist 與 build output 的狀態執行 `./gradlew --offline clean :post-bubi-api:test :post-bubi-api:bootJar` 成功。
- 修正同時指定 `test` 與 `bootJar` 時前端 production build 被錯誤略過的 Gradle task 判斷。
- Executable JAR 包含 UI resource JAR、`TBConvert.jar`、`TB_UCS2_BUR.bin` 與 `TB_BUR_UCS2.bin`。
- `zipOfflineMavenRepo` 成功產生 Maven layout repository 與 zip。
- 實際啟動 JAR 後，首頁回應 HTTP 200，`/api/health` 回應 `UP`。

前端正式建置：

```bash
./gradlew :post-bubi-ui:yarn_build_prod
```

離線驗證：

```bash
./gradlew --offline \
  -PofflineRepo=/path/to/offline-maven-repo \
  :post-bubi-api:test \
  :post-bubi-api:bootJar
```

## 部署狀態

正式部署最少只需要：

```text
post-bubi.jar
```

第一次執行會在工作目錄產生 runtime data：

```text
data/post-bubi.mv.db
data/files/
data/protos/
logs/post-bubi.log
```

這些檔案包含使用者資料，不可當作 build artifact 任意刪除，也不提交 Git。

建置環境仍需保留：

```text
data/TBConvert.jar
data/CodeTable/TB_UCS2_BUR.bin
data/CodeTable/TB_BUR_UCS2.bin
```

Gradle 會把它們放入 executable JAR。`data/proto/` 是開發與 proto import 測試資源，不是 runtime 上傳目錄。

## 已知限制

- gRPC 僅支援 unary method。
- 未提供 `protoId` 時，target server 必須支援 server reflection。
- Proto method 套用後 request body 預設為 `{}`，尚未依 message schema 自動產生完整欄位範本。
- gRPC BUR 已完成自動化組包與轉碼驗證；實際目標系統成功回應仍取決於正確業務資料與可連線環境。
- 本專案是單人離線工具，不包含登入、權限、雲端同步與多人協作。

## 維護規則

- 每個功能變更都需同步更新本文件或對應專題文件。
- 完成功能後至少執行相關整合測試與 `bootJar`。
- UI 變更需驗證 Light/Dark 與桌面、620px、390px。
- 不提交 `.idea`、cache、node_modules、build、dist、runtime data、zip 備份或 notebook。
- 推送遠端前必須取得使用者明確同意，commit 訊息使用繁體中文並清楚描述異動。

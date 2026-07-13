# Post Bubi 開發進度

最後整理日期：2026-07-13

本文只記錄目前可用狀態、驗證方式與已知限制。歷史異動由 Git commit 保存，不在此重複累積逐輪開發日誌。

## 整體狀態

Post Bubi 已達到可供單人日常使用的 HTTP、gRPC unary 與 gRPC BUR 測試工具階段，可打包成單一 JAR 並部署到離線主機。現有功能以穩定、維護與使用者體驗為優先，暫不擴充新的協定類型。

| 領域 | 狀態 | 驗證依據 |
| --- | --- | --- |
| Java 17 + Spring Boot + Vue 3 專案 | 完成 | `:post-bubi-api:bootJar` |
| 單一 executable JAR | 完成 | UI、TBConvert、CodeTable 均包含於 JAR |
| H2 持久化 | 完成 | Workspace 整合測試 |
| Collection / Folder / Request | 完成 | CRUD、Collection 排序與改名、跨 Collection Request 移動、複製、刪除整合測試 |
| HTTP request | 完成 | GET/POST、headers、body、form-data、history、timeout 與取消整合測試 |
| gRPC unary | 完成 | reflection、本機 proto、timeout 與取消整合測試 |
| gRPC BUR | 完成 | preview、固定長度組包、TBConvert、內建 CodeTable 與取消機制 |
| Proto 管理 | 完成 | upload、list、inspect、ZIP import/export 測試 |
| Workspace ZIP | 完成 | Collection、Folder、Request、files、protos、Environment 與 zip slip 測試 |
| Response 工具 | 完成 | JSON 上色、headers、info、history、base64 decoded、BUR 解碼與本次瀏覽暫存 |
| Light / Dark 與響應式 UI | 完成 | 1280、620、390px 瀏覽器驗證 |
| Environment variables | 完成 | CRUD、ZIP schema v2、`{{variable}}` 替換與未定義變數保護驗證 |
| JAR 同層 log 與關閉開關 | 完成 | bootJar 與啟動參數驗證 |
| 離線 Maven repository task | 完成 | `zipOfflineMavenRepo` 與 `--offline` 流程 |

## 已完成功能

### Workspace

- Collection、Folder、Request 新增、修改與刪除。
- Collection 可由左側拖拉排序，並可由水平三點選單重新命名。
- Folder 與 Request 同層拖拉排序。
- Request 可拖拉至其他 Collection 根層、Folder，或其他 Request 前方；移動後會更新所屬位置與排序。
- Request 複製。
- 未保存內容切換提示。
- Workspace ZIP 匯入與匯出，包含 uploaded files、protos 與 Environment；仍支援匯入 schema v1。

### HTTP

- GET、POST、PUT、PATCH、DELETE。
- Query params。
- 多列 header key/value、啟用切換與拖拉排序。
- none、JSON、raw、x-www-form-urlencoded、multipart form-data。
- File upload 與 form-data file reference。
- Timeout 預設 30 秒，可於 Settings 調整為 1 至 300 秒；redirect、忽略 SSL certificate verification。
- 送出後可按取消；前端會停止等待，後端同步關閉對應 HTTP client。
- 新建 HTTP Request 預設略過 HTTPS 憑證驗證；已保存 Request 保留原設定。
- 執行紀錄與歷史 request 載入。

### gRPC unary

- Plaintext 與 TLS。
- TLS certificate verification 關閉選項。
- Metadata。
- JSON request body。
- Timeout 預設 30 秒，可於 Settings 調整為 1 至 300 秒。
- 送出後可按取消；後端會關閉對應 gRPC channel。
- 使用 server reflection 取得 descriptor。
- 指定 `protoId` 時使用已上傳 proto，不依賴 target server reflection。
- Proto inspect 後可直接套用 service、method 與 request type。
- 目前只支援 unary method，不支援 client/server/bidirectional streaming。

### gRPC BUR

- 獨立 `GRPC_BUR` request type。
- Target host、port、metadata、TLS 與 timeout（預設 30 秒，可調整）。
- 送出後可按取消，沿用 gRPC channel 中止機制。
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
- Collection tree 提供拖曳把手、來源淡化、放置目標高亮與插入提示，提升拖拉可辨識性。
- Collection 可個別收合與展開，狀態保存在 browser local storage；收合後仍可作為 Request 拖拉目標。
- 側欄樹狀列改用一致的語意圖示：Collection 箱體、Folder 資料夾、Request 文件；主要動作、選單與列編輯操作統一使用 Lucide icon system。
- Request tree 依類型顯示不同圖示與語意色：HTTP 網路地球、gRPC 服務通訊塔、gRPC BUR 二進位資料。
- Post Bubi Logo 已放大，並在窄版維持不擠壓 Theme 切換的尺寸。
- 上傳 Proto 後，Proto 側欄預設收合為檔名或數量摘要；展開偏好保存在 browser local storage。
- 桌面版 Request editor 與 Response viewer 間可拖拉分隔列調整 Response 高度，設定保存在 browser local storage；窄版維持固定區塊高度。
- Response 以固定標題列與 tabs 加可捲動內容列呈現；Body、Headers、Info、Decoded、History 的長內容只在內容區內捲動。
- gRPC BUR Decoded tab 使用滿版 payload 結果清單；成功解碼的 `payload.*.data` 會回填至 Response Body，保留 decoded 標示與原始 base64 tooltip。
- 已儲存 Request 的最新 Response、錯誤或取消結果會在本次瀏覽期間暫存；切換後可恢復 Response 與最後瀏覽的 Response tab，不寫入 H2、ZIP 或 browser local storage。
- 1280、620、390px 無非預期水平溢位。
- Focus、disabled、reduced motion 與長文字處理。
- Environment 選擇、變數 key/value 管理與送出時模板替換。
- 任一 Request 送出後，Response 自動切換至 Body tab。
- 取消送出後，Response 摘要會顯示「已取消」，Body 保留取消結果，避免使用者誤認請求仍在執行。

### Environment variables

- 可建立、修改、刪除及切換命名 Environment。
- 變數使用 `{{variable}}` 語法，保存 Request 時保留模板，送出時才解析。
- HTTP、gRPC、gRPC BUR 的可輸入字串欄位皆可替換。
- 不存在或循環引用的變數會阻止送出並顯示變數名稱。
- Workspace ZIP schema v2 會包含 Environment；仍可匯入 schema v1。
- 匯入名稱重複的 Environment 會新增副本，不覆蓋既有資料。

## 自動化測試

| 測試 | 覆蓋範圍 |
| --- | --- |
| `WorkspaceApiIntegrationTest` | Collection、Folder、Request CRUD、Collection 改名與排序、跨 Collection / Folder Request 移動、複製、錯誤格式 |
| `HttpExecuteIntegrationTest` | 本機 HTTP GET、history、invalid URL、執行中 HTTP 取消 |
| `FileUploadIntegrationTest` | multipart upload、HTTP form-data file |
| `WorkspaceArchiveIntegrationTest` | ZIP export/import、file/proto reference、Environment schema v2、schema v1 相容與 zip slip |
| `ProtoIntegrationTest` | Proto upload、list、inspect、rpc parsing |
| `GrpcExecuteIntegrationTest` | reflection unary、本機 proto unary、JSON 錯誤、執行中 gRPC 取消 |
| `GrpcBurExecuteIntegrationTest` | BUR payload preview、固定長度、timeout 範圍、JAR 內建 CodeTable |
| `EnvironmentIntegrationTest` | Environment CRUD、變數名稱驗證 |

標準驗證：

```bash
./gradlew :post-bubi-ui:yarn_build_prod
./gradlew :post-bubi-api:test
./gradlew :post-bubi-api:bootJar
```

2026-07-10 清理後驗證結果：

- 從無 `node_modules`、dist 與 build output 的狀態執行 `./gradlew --offline clean :post-bubi-api:test :post-bubi-api:bootJar` 成功。
- 修正同時指定 `test` 與 `bootJar` 時前端 production build 被錯誤略過的 Gradle task 判斷。
- Executable JAR 包含 UI resource JAR、`TBConvert.jar`、`TB_UCS2_BUR.bin` 與 `TB_BUR_UCS2.bin`。
- `zipOfflineMavenRepo` 成功產生 Maven layout repository 與 zip。
- 實際啟動 JAR 後，首頁回應 HTTP 200，`/api/health` 回應 `UP`。
- Environment UI 實測建立、選擇與 `{{baseUrl}}` 替換成功；未定義變數不會送出網路請求。

2026-07-13 文件與實作核對結果：

- `./gradlew :post-bubi-ui:yarn_build_prod :post-bubi-api:test :post-bubi-api:bootJar` 成功。
- 8 個整合測試類別均無失敗或錯誤；gRPC BUR 已驗證 timeout 範圍錯誤會回傳 `GRPC_TIMEOUT_INVALID`。

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
- Environment value 會包含於 Workspace ZIP；匯出前需確認其中不含不應分享的敏感資訊。
- Folder 僅支援同 Collection、同父層級排序；跨 Collection 移動 Folder 不在目前範圍。

## 近期人工驗證重點

1. 以水平三點選單重新命名 Collection，拖拉 Collection 或 Request 後重新整理，確認名稱、位置與順序皆保留。
2. 收合任一 Collection 或 Proto 區塊後重新整理，確認收合狀態保留；收合 Collection 仍可作為 Request 拖拉目標。
3. 送出長 JSON Response，確認僅 Response 內容區捲動，標題與 tabs 保持可見；gRPC BUR 成功解碼時，Body 的 `payload.*.data` 顯示明碼與 `decoded` 標示。
4. 送出已儲存 Request 後切換到其他 Request 再切回，確認 Response 與最後的 Response tab 還原；重新整理或刪除 Request 後確認暫存清除。
5. 在 Light/Dark、1280px、620px 與 390px 寬度下確認樹狀列、Logo、Theme 切換、Request toolbar 與 Response 區塊均無重疊或非預期水平捲動。

## 維護規則

- 每個功能變更都需同步更新本文件或對應專題文件。
- 完成功能後至少執行相關整合測試與 `bootJar`。
- UI 變更需驗證 Light/Dark 與桌面、620px、390px。
- 不提交 `.idea`、cache、node_modules、build、dist、runtime data、zip 備份或 notebook。
- 推送遠端前必須取得使用者明確同意，commit 訊息使用繁體中文並清楚描述異動。

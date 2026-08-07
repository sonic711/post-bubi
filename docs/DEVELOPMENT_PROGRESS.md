# Post Bubi 開發進度

最後整理日期：2026-08-07

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
| Workspace / Collection ZIP | 完成 | schema v3、單一 Collection 封存、資源與 Proto ID 重映射、舊版相容 |
| Response 工具 | 完成 | JSON 上色、headers、info、history、base64 decoded、BUR 解碼與本次瀏覽暫存 |
| Light / Dark 與響應式 UI | 完成 | Sidebar 拖拉自動收合、拖曳展開、可見分隔列與窄版驗證 |
| Environment variables | 完成 | CRUD、`{{variable}}`、獨立 ZIP 匯入匯出與複製 Environment |
| JAR 同層 log 與關閉開關 | 完成 | bootJar 與啟動參數驗證 |
| 離線 Maven repository task | 完成 | `zipOfflineMavenRepo` 與 `--offline` 流程 |
| Maven POM 產生 | 完成 | `maven-publish`、`generatePomXml` 與離線 POM 產物驗證完成 |
| 完整依賴 POM | 完成 | compile、runtime、test 與 Gradle buildscript POM；總 task 與 Maven 離線驗證完成 |
| HTTP 批次執行 | 完成，待使用者驗收 | H2 Batch Run / Item、三種模式、依 Request 分隔的可選擇歷程、期限/手動取消與整合測試完成 |
| HTTP 轉 cURL 匯出 | 完成，待使用者驗收 | Bash/zsh 實際執行測試、PowerShell、Environment 模板/已解析值、form-data file placeholder 與本機剪貼簿複製 |

## 下一階段

1. 已完成 HTTP Batch Run / Batch Item H2 model、背景排程、三種模式與整批取消。
2. 已完成批次 API、結果輪詢與 HTTP 整合測試，驗證併發限制、回應後間隔、期限取消、手動取消與不寫入單筆歷程。
3. 已完成批次執行 UI、進度摘要、結果列表與單筆 headers/body preview；已儲存 HTTP Request 可瀏覽專屬的 Batch Run 歷程並切換任一筆。
4. 已完成 HTTP 轉 cURL generator：工具列 icon 開啟預覽，支援 Bash/zsh 與 PowerShell，並能選擇保留或解析目前 Environment；Bash 指令已以本機 HTTP server 實際執行驗證。
5. 下一階段：待使用者驗收 HTTP 批次執行與 cURL 匯出，再依回饋處理修正或進入既有文件列出的後續項目。

## 已完成功能

### Workspace

- Collection、Folder、Request 新增、修改與刪除。
- Collection 可由左側拖拉排序，並可由水平三點選單重新命名。
- Folder 與 Request 同層拖拉排序。
- Request 可拖拉至其他 Collection 根層、Folder，或其他 Request 前方；移動後會更新所屬位置與排序。
- Request 複製。
- 未保存內容切換提示。
- Workspace ZIP 匯入與匯出，包含 uploaded files 與實際引用的 protos；新版 schema v3 不包含 Environment，仍可匯入 schema v1 / v2 既有封存檔。
- Collection 三點選單可匯出單一 Collection ZIP；匯入後若同名，自動使用「原名稱 匯入 N」，不覆蓋既有 Collection。
- 匯入時會重建 file ID 與 proto ID，Request 的 form-data 與 gRPC / gRPC BUR proto 引用會改為新資源 ID。
- Environment 可獨立匯出與匯入 ZIP，並可複製既有 Environment 後指定新名稱；Collection ZIP 不會包含 Environment value。

### HTTP

- GET、POST、PUT、PATCH、DELETE。
- Query params。
- 多列 header key/value、啟用切換、可見拖曳把手與放置提示排序；可由標題旁圖示收合且偏好保存在 browser local storage；列數過多時只有清單內捲動、新增按鈕保持可見，Response Body 不會被推離工作台。
- none、JSON、raw、x-www-form-urlencoded、multipart form-data。
- File upload 與 form-data file reference。
- Timeout 預設 30 秒，可於 Settings 調整為 1 至 300 秒；redirect、忽略 SSL certificate verification。
- 送出後可按取消；前端會停止等待，後端同步關閉對應 HTTP client。
- HTTP 工具列提供批次執行 icon，可設定並行、回應後間隔或完成期限模式；Response 的 Batch 分頁顯示已排程、已送出、進行中、成功、失敗、取消、未送出、總耗時與平均/最快/最慢回應時間，可取消進行中批次並查看項目 headers/body preview。
- 已儲存 HTTP Request 的 Batch 分頁會載入同一 `requestId` 的歷程；預設顯示最新一筆，可切換任何已保存 Run 並以前後頁瀏覽，切換至其他 Request 不會混入前一測案結果。未儲存 Request 的批次結果只保留於本次瀏覽。
- HTTP 工具列提供 cURL icon，可產生 Bash/zsh 或 PowerShell 指令並複製到剪貼簿。輸出保留啟用中的 Params、Headers 排序、redirect、HTTPS 憑證設定、timeout 與 Body；form-data file 使用 `@/path/to/file` placeholder。
- cURL 預設保留 `{{variable}}`；若輸出含模板，畫面會提示 Linux shell 不會自動替換，必須套用目前 Environment 或自行替換後才能執行。套用 Environment 時會提示可能包含敏感值。產生與複製完全在瀏覽器內進行，不寫入後端 log、History 或 Batch Result。
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

- 品牌色 `#AB005F`、logo 與使用 Post Bubi 圖像的瀏覽器分頁 icon。
- Light / Dark Theme，偏好保存在 browser local storage。
- Request/Response 工作區、狀態摘要與語意色。
- JSON request/response key/value 語法上色與自動排版；Request Body 上色層與文字輸入層採相同字型度量及可捲動範圍，選取、複製與高對比反白行為一致。
- Response JSON path base64 decoded 設定，成功時可在 Body 原欄位直接顯示明碼。
- Collection、Folder、Request 水平三點操作選單。
- Collection tree 提供拖曳把手、來源淡化、放置目標高亮與插入提示，提升拖拉可辨識性。
- Collection 可個別收合與展開，狀態保存在 browser local storage；收合後仍可作為 Request 拖拉目標。
- 側欄樹狀列改用一致的語意圖示：Collection 箱體、Folder 資料夾、Request 文件；主要動作、選單與列編輯操作統一使用 Lucide icon system。
- Request tree 依類型顯示不同圖示與語意色：HTTP 網路地球、gRPC 服務通訊塔、gRPC BUR 二進位資料。
- Post Bubi Logo 已放大，並在窄版維持不擠壓 Theme 切換的尺寸。
- Desktop sidebar 可拖拉調整 220–520px 寬度，縮至 180px 以下自動收合為 64px；收合後向右拖曳分隔列即可展開，無額外收合按鈕。860px 以下維持既有單欄工作台，不顯示桌面拖曳控制。
- 上傳 Proto 後，Proto 側欄預設收合為檔名或數量摘要；展開偏好保存在 browser local storage。
- 使用精細指標的桌面版 Request editor 與 Response viewer 間可拖拉分隔列調整 Response 高度，依實際工作區高度計算範圍且不受瀏覽器縮放影響，設定保存在 browser local storage；觸控窄版維持固定區塊高度。
- Response 以固定標題列與 tabs 加可捲動內容列呈現；Body、Headers、Info、Decoded、History 的長內容只在內容區內捲動。
- Request 的 Params、Headers、Body、Settings 與 gRPC BUR 組包欄位同樣使用 tab 內部捲動；內容過長不會裁切、推開 Response 或依賴整頁捲動。
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
- 可獨立匯出與匯入單一 Environment；ZIP 會含 variable value，匯出前必須確認可安全分享。
- 可複製既有 Environment 以快速建立新環境，且不自動切換目前使用中的 Environment。
- 新版 Workspace / Collection ZIP 不會包含 Environment；仍可匯入 schema v1 / v2 舊封存檔中的 Environment。
- 匯入名稱重複的 Environment 會新增副本，不覆蓋既有資料。

## 自動化測試

| 測試 | 覆蓋範圍 |
| --- | --- |
| `WorkspaceApiIntegrationTest` | Collection、Folder、Request CRUD、Collection 改名與排序、跨 Collection / Folder Request 移動、複製、錯誤格式 |
| `HttpExecuteIntegrationTest` | 本機 HTTP GET、history、invalid URL、執行中 HTTP 取消 |
| `HttpBatchIntegrationTest` | HTTP 批次併發、回應後間隔、完成期限取消、手動取消與單筆 history 隔離 |
| `curl-command.test.mjs` | Bash cURL 實際執行、query、Header 順序、單引號/多行 JSON body、PowerShell 轉義與模板辨識 |
| `FileUploadIntegrationTest` | multipart upload、HTTP form-data file |
| `WorkspaceArchiveIntegrationTest` | Workspace / Collection ZIP、file/proto reference、舊版 Environment schema v2、schema v1 相容與 zip slip |
| `ProtoIntegrationTest` | Proto upload、list、inspect、rpc parsing |
| `GrpcExecuteIntegrationTest` | reflection unary、本機 proto unary、JSON 錯誤、執行中 gRPC 取消 |
| `GrpcBurExecuteIntegrationTest` | BUR payload preview、固定長度、timeout 範圍、JAR 內建 CodeTable |
| `EnvironmentIntegrationTest` | Environment CRUD、變數名稱驗證、複製與獨立 ZIP 匯入匯出 |

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

2026-07-21 Collection / Environment 封存驗證結果：

- `./gradlew :post-bubi-api:test` 成功，8 個整合測試類別均無失敗或錯誤。
- `WorkspaceArchiveIntegrationTest` 已驗證 schema v3 Workspace ZIP 不含 Environment、單一 Collection ZIP 只含目標內容、同名 Collection 使用「匯入 N」、file / proto 引用重映射，以及 schema v1 / v2 匯入相容。
- `EnvironmentIntegrationTest` 已驗證 Environment 複製、獨立 ZIP 匯出與同名匯入為「匯入 2」。
- `./gradlew :post-bubi-ui:yarn_build_prod :post-bubi-api:bootJar` 成功；以 JAR 啟動於 `18081` 後，`GET /api/health` 回應 `200` 與 `status: UP`。

2026-07-22 sidebar 調整驗證結果：

- `./gradlew :post-bubi-ui:yarn_build_prod :post-bubi-api:bootJar` 成功。
- 以 JAR 實測桌面工作台：sidebar 縮至 180px 以下會自動收合為 64px；收合後向右拖曳分隔列可展開至最小可用寬度，中央工作台未重疊。分隔列平時可見，hover 或拖曳時以品牌色強調。
- 以 860px viewport 實測：sidebar 保持完整單欄內容，桌面版拖曳分隔列與收合控制均不顯示。

2026-07-24 Maven POM 產生驗證結果：

- `./gradlew generatePomXml --offline` 成功，產生 `build/maven-poms/pom.xml` 與 API、UI 子模組 POM。
- 根 POM 為 `pom` packaging 並列出 `post-bubi-api`、`post-bubi-ui`；API POM 含 Spring Boot BOM 與 runtime 相依；UI POM 對應前端資源 JAR。
- `./gradlew publishToMavenLocal --offline` 成功，根專案、API、UI 的 POM 與 JAR 均已發布至本機 Maven repository。
- `./gradlew :post-bubi-api:generateResolvedRuntimePom --offline` 成功，產生列出所有 Gradle runtime Maven module 與固定版本的 POM。
- `mvn -o -f post-bubi-api/build/maven-poms/post-bubi-api/resolved-runtime-pom.xml validate` 成功，確認最終 POM XML 與 Maven model 有效。
- `generateAllResolvedDependencyPoms` 成功，產生 buildscript、compile、runtime、test 四份固定版本的依賴 POM；首次以隔離 Gradle user home 執行時，也完成前端正式建置。
- 四份 POM 均以 `mvn -o -B -ntp -f <pom> validate` 驗證成功；依賴數依序為 buildscript 21、compile 70、runtime 89、test 114，且每一個 `<dependency>` 都有固定 `<version>`。
- 即使四份 POM 已補齊 Maven 類型的 buildscript plugin，仍不可只用 POM 填滿新的 Maven `.m2` 後期待 Gradle 離線打包成功；Gradle plugin marker、wrapper distribution 與 Node / Yarn / npm 前端資源仍須依既有離線交付流程提供。完整結論已記錄於 `MAVEN_FULL_DEPENDENCY_POM_GUIDE.md`。
- 2026-07-26 以全新 Maven repository（由四份 POM 從 Maven Central、Gradle Plugin Portal 下載）及全新 `GRADLE_USER_HOME` 實測 `:post-bubi-api:bootJar --rerun-tasks --offline`。補入 Node Plugin DSL marker POM 後，仍在 `org.nodejs:node:18.17.0` 無 Gradle cache 時失敗，證實 Maven repository 無法單獨完成此專案的 Gradle 離線打包。

2026-08-07 HTTP 批次執行後端驗證結果：

- `POST /api/http/batch-runs` 可建立最多 100 筆的 HTTP-only 批次；`GET /api/http/batch-runs/{id}` 輪詢摘要，`GET /api/http/batch-runs/{id}/items` 取得分頁結果，`POST /api/http/batch-runs/{id}/cancel` 取消整批。
- `GET /api/http/batch-runs?requestId={requestId}` 可取得已儲存 HTTP Request 的最近 Batch Run；Batch UI 會以此還原重新整理前的結果。
- H2 保存 Batch Run / Item 狀態、response headers、最多 4000 字元的 response body preview 與錯誤原因；批次子 Request 不寫入既有 HTTP Request History。
- `./gradlew :post-bubi-api:test --tests com.postbubi.web.HttpBatchIntegrationTest` 成功，驗證併發模式、回應後間隔模式、完成期限取消、手動取消與 history 隔離。
- `./gradlew :post-bubi-api:test --tests '*' --rerun-tasks` 成功，9 個整合測試類別共 28 個測試均無失敗或錯誤；`./gradlew :post-bubi-api:bootJar` 成功產生 `post-bubi-api/build/libs/post-bubi.jar`。
- 前端以實際 JAR 驗證 HTTP 批次設定、發送、輪詢、完成摘要與項目結果；桌面與 390px viewport 均無水平溢位，窄版 Modal 可完整操作。

2026-08-07 HTTP 轉 cURL 驗證結果：

- `./gradlew :post-bubi-ui:yarn_build_prod` 成功。
- `./gradlew :post-bubi-api:test --tests '*' --rerun-tasks` 成功，既有後端整合測試皆通過。
- HTTP 工具列可開啟 cURL 對話框；Bash/zsh 使用 `curl`，PowerShell 使用 `curl.exe`，並以適合各 shell 的單引號規則輸出多行指令。
- 產生器會納入啟用的 Params 與 Headers（維持 Header 順序）、`--location`、`--insecure`、`--max-time`、`--data` 或 `-F`。form-data file 一律輸出 `@/path/to/file`，不會洩漏上傳時的本機路徑。
- cURL 預覽與複製不呼叫 API；Environment 解析僅在使用者勾選後執行，找不到或循環引用變數時會在對話框顯示錯誤。

2026-08-07 Batch 歷程與 Linux cURL 驗證結果：

- Batch 分頁改用 `GET /api/http/batch-runs?requestId={requestId}&page={page}&size=20` 載入歷程。實際 JAR 以 `測試Headers` 驗證兩筆已保存 Run 皆可顯示，且切換歷程選單後會載入對應的統計與項目結果。
- 修正切換 HTTP Request 時停留在 Batch 分頁卻未顯示歷程的問題：切換前會保留 Batch 分頁狀態，載入目標 Request 後立即依新的 `requestId` 查詢歷程。實際 JAR 以「測試Headers -> 測試Headers 複本 -> 測試Headers」驗證，兩個測案均直接顯示各自的 Batch 結果。
- `node --test src/curl-command.test.mjs` 成功。測試啟動本機 HTTP server 並由產生的 Bash 多行 cURL 指令實際呼叫，確認 query、Header 順序、含單引號與多行 JSON body 均正確傳送；同時驗證 PowerShell 使用 `curl.exe` 與模板辨識。

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
- 新版 Workspace / Collection ZIP 不含 Environment；分享 variable value 時必須使用 Environment ZIP，並確認其中沒有不應分享的敏感資訊。
- Folder 僅支援同 Collection、同父層級排序；跨 Collection 移動 Folder 不在目前範圍。
- HTTP Batch Run 第一版僅支援 HTTP 單一 Request；不支援 gRPC、gRPC BUR 或多 Request Collection Runner。

## 近期人工驗證重點

1. 以水平三點選單重新命名 Collection，拖拉 Collection 或 Request 後重新整理，確認名稱、位置與順序皆保留。
2. 收合任一 Collection 或 Proto 區塊後重新整理，確認收合狀態保留；收合 Collection 仍可作為 Request 拖拉目標。
3. 在長 JSON Request Body 從頂端捲至最底端後選取並複製文字，確認上色內容、游標位置與複製結果仍對齊；送出長 JSON Response 時，確認僅 Response 內容區捲動，標題與 tabs 保持可見；gRPC BUR 成功解碼時，Body 的 `payload.*.data` 顯示明碼與 `decoded` 標示。
4. 送出已儲存 Request 後切換到其他 Request 再切回，確認 Response 與最後的 Response tab 還原；重新整理或刪除 Request 後確認暫存清除。
5. 在 Light/Dark、1280px、620px 與 390px 寬度下確認樹狀列、Logo、Theme 切換、Request toolbar 與 Response 區塊均無重疊或非預期水平捲動。

## 維護規則

- 每個功能變更都需同步更新本文件或對應專題文件。
- 完成功能後至少執行相關整合測試與 `bootJar`。
- UI 變更需驗證 Light/Dark 與桌面、620px、390px。
- 不提交 `.idea`、cache、node_modules、build、dist、runtime data、zip 備份或 notebook。
- 推送遠端前必須取得使用者明確同意，commit 訊息使用繁體中文並清楚描述異動。

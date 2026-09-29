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
- Log file：JAR 同層 `logs/post-bubi.log`

以上路徑與 port 後續應可透過設定檔或啟動參數調整。

檔案 log 預設啟用，方便離線主機與本機開發除錯。必須可用啟動參數或環境變數關閉：

```bash
java -jar post-bubi.jar --post-bubi.logging.file.enabled=false
POST_BUBI_LOGGING_FILE_ENABLED=false java -jar post-bubi.jar
```

也必須可指定自訂 log 檔案：

```bash
java -jar post-bubi.jar --post-bubi.logging.file.name=/path/to/post-bubi.log
```

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

瀏覽器分頁 favicon 與 Apple touch icon 必須使用同一份 Post Bubi logo，由 Vite 打包，不依賴部署主機的外部圖片路徑。

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
- JSON request editor 的上色層與實際 textarea 必須使用相同文字度量與可捲動範圍；滑鼠選取、複製、貼上與捲動到最底端時必須以原始 Request Body 為準，不得因上色樣式造成位置或內容錯位。
- JSON request editor 的選取狀態必須使用高對比反白並顯示選取文字，不得使用品牌色或透明文字而降低可讀性；Light/Dark Theme 均須維持一般文字選取行為。
- 品牌色只用於主要操作、選取與重要狀態；成功、錯誤、資訊與警告需使用各自語意色，避免形成單一色系介面。
- Collection、Folder、Request、HTTP method 與 Response state 應可在不閱讀完整文字時快速區分，但圖示與顏色不可取代必要的可讀標籤。
- 主要控制尺寸、editor/response 比例與 toolbar grid 必須穩定，hover、狀態文字或長 URL 不得造成 layout shift。
- UI 變更完成時至少驗證一般桌面、620px 窄視窗與 390px 行動寬度；頁面不得產生非預期水平捲動。
- 所有彈出視窗的灰底背景只可在使用者於灰底按下且於同一灰底放開時關閉；從視窗內容拖曳到灰底後放開、文字選取或其他視窗內 pointer 操作不得關閉視窗。
- 動畫與 transition 應節制，並支援 `prefers-reduced-motion`。
- 樹狀拖拉必須在拖曳來源、可放置目標與目前插入位置提供可辨識回饋；不得只依賴游標或顏色，應保留可見的拖曳把手或文字提示。
- 所有功能性圖示應採用同一套語意化 icon system；Collection 使用箱體圖示、Folder 使用資料夾圖示，Request 依類型使用不同圖示：HTTP 使用網路地球、gRPC 使用服務通訊塔、gRPC BUR 使用二進位資料。並以可辨識的常見圖示取代混用的 Unicode 符號。只有純圖示按鈕時，必須提供 `title` 與 `aria-label`。
- 側欄品牌 Logo 應保持清楚的第一視覺辨識，尺寸須高於一般操作圖示，但不得壓縮 Theme 切換或造成窄版溢位。

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
- Environment variables 與 `{{variable}}` 執行期替換
- HTTP 單一 Request Batch 執行與歷程管理
- HTTP Request 轉 Bash/zsh 或 PowerShell `curl.exe`
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

### 4.3 gRPC BUR 組包請求

`gRPC BUR` request type 已依原始驗證腳本的組包概念完成。此模式與一般 gRPC JSON request 不同，使用者只需關注目標系統 host / port 與業務 input 內容，系統負責完成 TCPIP header、MCS header、Basic Label、Text Area 串接、UTF-8 / BUR 轉碼、`RqPayload` 建立與 `Service/rpcPeriphery` 呼叫。原始 notebook 不屬於建置或執行必要檔案，實際行為以 Java 實作與 `docs/GRPC_BUR_COMPOSER_FEATURE.md` 為準。

詳細設計記錄於 `docs/GRPC_BUR_COMPOSER_FEATURE.md`。第一階段已完成 `GRPC_BUR` request type、payload preview、送出流程與 `TBConvert.jar` / CodeTable 正式 BUR codec 接入。CodeTable 與 `TBConvert.jar` 會包入 executable JAR，部署到其他主機時不需要額外攜帶 CodeTable；實際目標系統驗收仍需使用正確業務資料、固定長度設定與相符 proto。

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
- Headers 表格：多列 key/value、啟用切換、可見拖曳把手排序與拖曳放置提示；提供位於標題旁的收合/展開控制並保存在 browser local storage；列數過多時僅列清單可內部捲動，新增按鈕保持可見，Response 區塊不得被內容推離工作台可視範圍
- Body types：
  - none
  - raw text
  - JSON
  - x-www-form-urlencoded
  - form-data
  - file upload
- Timeout 設定，預設 `30000` 毫秒（30 秒），可調整範圍為 `1` 至 `300000` 毫秒
- 送出中的 HTTP、gRPC unary 與 gRPC BUR request 必須提供取消操作；取消時前端停止等待，後端同時關閉對應 HTTP client 或 gRPC channel，避免長時間等待阻塞後續操作，並在 Response Body 保留已取消結果
- Follow redirects 開關
- Ignore SSL certificate verification 開關
- HTTP 新建 Request 預設勾選 Ignore SSL certificate verification；已保存 Request 仍保留各自設定。
- URL、params、headers、text body、form-data text、gRPC metadata/body 與 gRPC BUR 組包欄位可使用 `{{variable}}`。

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

### 5.4 HTTP 批次執行

HTTP 批次執行只支援目前編輯中的單一 HTTP Request；gRPC unary 與 gRPC BUR 的對應規格見 6.5。所有協定都不支援多 Request 的 Collection Runner。

- 使用目前 Request 與目前 Environment 在啟動當下的解析結果建立快照；批次開始後修改 Request 或 Environment 不影響執行中的批次。
- 單批總筆數必須為正整數，沒有產品層固定上限；實際可執行數量受 Java 整數範圍、H2 儲存空間、批次模式的完成期限與主機資源限制。
- 批次提供以下互斥模式：
  - **並行模式**：設定總筆數與最大同時執行數；第一批立即送出，之後每有一筆完成才補送下一筆。最大同時執行數預設 `1`，範圍 `1` 至 `100`。
  - **回應後間隔模式**：每一筆收到 Response 或失敗結果後，等待指定間隔才送出下一筆；間隔範圍 `0` 至 `300000` 毫秒。
  - **完成期限模式**：設定總筆數、最大同時執行數與完成期限；所有 Request 必須在期限內取得成功、失敗或取消結果。完成期限範圍 `1000` 至 `3600000` 毫秒。
- 完成期限模式到達期限時，系統必須停止未送出的項目、取消正在執行的項目，並將批次標記為 `DEADLINE_EXCEEDED`。子 Request 的有效 timeout 不得超過剩餘完成期限。
- 單筆 HTTP `4xx`、`5xx`、timeout、TLS 或連線錯誤只標記該項目失敗；第一版不自動重試，且仍繼續執行其他項目。
- 使用者可取消整批；系統必須停止未送出的項目，並使用既有 HTTP cancellation 機制取消進行中的子 Request。
- 批次與項目結果必須保存於 H2。批次狀態至少包括 `RUNNING`、`COMPLETED`、`DEADLINE_EXCEEDED`、`CANCELLED`；項目狀態至少包括 `SUCCESS`、`FAILED`、`CANCELLED`、`NOT_DISPATCHED`。
- 結果畫面必須顯示已排程、已送出、進行中、成功、失敗、取消、未送出、總耗時、平均/最快/最慢回應時間。每筆可查看 status、實際發送時間、回應完成時間、耗時、錯誤原因、response headers 與最多 `4000` 字元的 body preview。
- 後端以背景工作執行，不可占用啟動批次的 HTTP request thread；前端以輪詢取得批次狀態與分頁項目結果。
- Batch 項目結果每頁最多顯示 `100` 筆。畫面必須顯示目前起訖與總筆數，並提供前後頁切換；切換頁面或 Batch Run 時不得保留其他頁項目的詳細選取狀態。
- API 提供：`POST /api/http/batch-runs`、`GET /api/http/batch-runs?requestId={requestId}`、`DELETE /api/http/batch-runs?requestId={requestId}`、`GET /api/http/batch-runs/{id}`、`GET /api/http/batch-runs/{id}/items`、`GET /api/http/batch-runs/{id}/export.csv`、`POST /api/http/batch-runs/{id}/cancel`。
- 後端批次執行完成後，批次子 Request 不得寫入既有單筆 Request History；避免批次測試洗掉使用者的單筆執行紀錄。
- HTTP toolbar 必須提供批次執行 icon；Batch 分頁必須顯示進度統計、項目結果與可展開的 headers/body preview。
- Batch Run 歷程必須以 `requestId` 分隔保存。已儲存 HTTP Request 開啟 Batch 分頁時，必須載入該 Request 的歷程清單，預設選擇最新一筆；使用者可切換同一 Request 的任何已保存 Batch Run，且歷程要提供分頁瀏覽與總筆數。切換至其他 Request 時，不可顯示前一個 Request 的 Batch Run。
- Batch Run 歷程與項目結果必須在重新整理、重新啟動 JAR 後可由 H2 還原。未儲存 Request 因沒有 `requestId`，其 Batch Run 只在目前瀏覽工作階段可查看。
- 已儲存 HTTP Request 的 Batch 歷程工具列必須提供目前選取 Run 的 CSV 匯出。CSV 使用 UTF-8 BOM，供 Windows Excel 直接辨識中文字；所有時間欄位固定使用台灣時區 `Asia/Taipei`，格式為 `yyyy-MM-dd HH:mm:ss.SSS +08:00`。每筆項目輸出 Run 識別、Request、時間、模式與狀態，以及序號、子 Request 的 `itemStartedAt`（實際發送時間）、`itemCompletedAt`（收到回應、失敗、取消或期限結束的完成時間）、HTTP status、耗時、size、錯誤、response headers 及保存的 body preview，欄位中的逗號、雙引號與換行必須符合 CSV 跳脫規則。
- Batch 歷程工具列必須提供清除記錄。清除範圍為目前 Request 的所有非 `RUNNING` Batch Run 與其項目資料；執行中的 Batch 必須保留，操作前需確認且不可復原。未儲存 Request 不顯示清除控制。

### 5.5 HTTP 轉 cURL

HTTP Request editor 必須提供 cURL 匯出與複製功能，第一版只產生指令，不支援 cURL 匯入。

- 支援 Bash/zsh 與 Windows PowerShell 兩種輸出格式；PowerShell 必須使用 `curl.exe`，避免使用 PowerShell 的 `curl` alias。
- 指令必須包含 method、完整 URL 與啟用中的 query params/header，並維持 header 原本排序。
- `followRedirects` 必須轉為 `--location`；`ignoreSslVerification` 必須轉為 `--insecure`；timeout 必須轉為 `--max-time`。
- JSON、raw、`x-www-form-urlencoded` 必須轉為相應的 `--data`；`form-data` 必須轉為 `-F`。
- 已上傳的 form-data file 不保存使用者原始本機路徑，cURL 必須輸出明確的 `@/path/to/file` placeholder。
- 預設輸出保留 `{{variable}}`，避免將 Environment 的敏感值直接複製。使用者可選擇套用目前 Environment 產生可直接執行的指令，UI 必須先提示指令可能包含 Token、帳密或內網位址。
- 保留 `{{variable}}` 的 Bash/zsh 指令是供分享或後續替換的模板，不能直接在 Linux shell 執行；UI 必須清楚提示使用者勾選套用目前 Environment 後再複製，或自行將模板替換為實際值。
- Bash/zsh 輸出必須可由 Linux `bash` 直接執行。至少要以含 query params、排序 headers 與含單引號、多行 JSON body 的 Request 進行自動化實際執行驗證。
- cURL 產生、顯示與複製不得寫入 application log、Request History 或 Batch Result。

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
- Settings 可選擇將 JSON Body 精確路徑 `payload.data` 的字串值，以 UTF-8 標準 Base64 編碼後送出；此設定只適用一般 gRPC unary，不適用 gRPC BUR。啟用時，Request JSON editor 必須以不改變文字選取、複製或游標排版的外框、底色與 `BASE64` 標示，明確標記 `payload.data`。保存 Request 時必須保留未編碼的原始 Body；若有 `{{variable}}`，先解析變數再編碼。啟用時 `payload.data` 必須存在且為字串，否則後端回傳 `GRPC_PAYLOAD_DATA_BASE64_INVALID`，不可送出未編碼資料。已是 Base64 的值仍視為一般文字再次編碼。
- Timeout 設定，預設 `30000` 毫秒（30 秒），可調整範圍為 `1` 至 `300000` 毫秒
- 送出期間可取消 request

### 6.4 Response

gRPC response viewer 必須顯示：

- Status code
- Status description
- Duration
- Response message，JSON 格式顯示
- Response metadata
- Error message

### 6.5 gRPC Batch 執行

gRPC unary 與 gRPC BUR 都必須支援單一 Request 的 Batch 執行。Batch 只重複執行啟動當下的同一份 Request snapshot，不支援多 Request Collection Runner，也不支援 streaming RPC。

- gRPC 與 gRPC BUR toolbar 都提供批次執行 icon；開啟的設定視窗沿用 HTTP Batch 的「並行」、「回應後間隔」與「完成期限」三種模式。
- 總筆數沒有產品層固定上限；最大同時執行數為 `100`。完成期限到達時，系統取消已開始但尚未完成的 unary call，未送出的項目不再執行，Batch Run 標記為 `DEADLINE_EXCEEDED`。
- 每個 Batch item 都使用獨立 `executionId`，手動取消 Run 時必須取消所有進行中的 gRPC call。個別 item 沿用單筆 gRPC / gRPC BUR 的 timeout，但完成期限較短時以剩餘期限為準。
- gRPC BUR Batch 必須沿用既有組包、轉碼、proto 解析、呼叫與回應 BUR 解碼流程；不寫入一般單筆 Request History。
- 啟用 gRPC unary `payload.data` Base64 編碼的 Batch snapshot 必須保留該設定，並由後端在每個 item 解析 Protobuf JSON 前套用相同 UTF-8 Base64 轉換。
- Batch Run 以 `requestId` 及協定類型分開保存。已保存 Request 在 Batch tab 可查看歷程、取消進行中 Run、匯出 CSV、清除同一 Request 的已完成 Run，以及每頁最多 100 筆的 item 結果；未保存 Request 只在目前瀏覽工作階段可查看。
- gRPC item 結果保存 status code、status description、duration、metadata、body preview、`itemStartedAt`（實際發送時間）、`itemCompletedAt`（回應、失敗、取消或期限結束的完成時間）與錯誤；gRPC BUR 另保存已解碼 payload 結果，供 Batch 詳細檢視。所有 Batch 顯示與 CSV 匯出的時間固定使用台灣時區 `Asia/Taipei`。所有 response body preview 最多保存 4000 字元。

## 7. Collection 與 Request 管理

### 7.1 Tree 結構

左側 collection tree 應支援：

- Collection
- Folder
- Request

Request 類型：

- `HTTP`
- `GRPC`
- `GRPC_BUR`

### 7.2 管理功能

第一版必須支援：

- 新增 collection 與 Collection 重新命名
- 新增 folder
- 新增 HTTP request
- 新增 gRPC request
- 重新命名
- 儲存
- 複製
- 刪除

排序與移動規則：

- Collection 可在左側樹狀清單中拖拉排序。
- Folder 保持同層排序；跨 Collection 移動 Folder 不在本階段範圍。
- Request 可拖拉至任一 Collection 根層，或拖拉至任一 Folder；移動後必須更新所屬 Collection、Folder 與同層 sort order。
- 拖曳中的來源列需有半透明或拖曳中狀態；可放置目標與插入位置需以明顯邊線或區塊高亮呈現；放置完成後重新載入樹狀資料並顯示結果。
- Collection 可獨立收合或展開其 Folder 與 Request；收合時仍可作為 Request 的拖拉放置目標。收合狀態應保存在瀏覽器本機，重新整理後保留。

## 8. 匯入與匯出

### 8.1 格式

第一版不需要相容 Postman Collection。匯入與匯出採用 Post Bubi 自訂 ZIP 格式，並區分下列三種封存檔：

| 封存類型 | 匯出範圍 | 根描述檔 |
| --- | --- | --- |
| Workspace ZIP | 全部 Collection、Folder、Request、其引用檔案與 Proto | `collection.json` |
| Collection ZIP | 單一指定 Collection、其 Folder、Request、引用檔案與 Proto | `collection.json` |
| Environment ZIP | 單一指定 Environment 與其全部 key/value variables | `environment.json` |

Workspace ZIP 與 Collection ZIP **不包含 Environment**。Environment value 可能含有 token、帳密或內網位址，必須由使用者在 Environment 區塊另行匯出與分享。

Collection 類封存 ZIP 內容：

```text
collection.json
files/
protos/
```

Environment ZIP 內容：

```text
environment.json
```

### 8.2 collection.json

`collection.json` 必須包含：

- schema version
- archive type（Workspace 或 Collection）
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
- Collection、Folder、Request 的 sort order

不包含：

- request history
- 本機絕對路徑
- 使用者機器相關設定
- 本次瀏覽期間的 Response 暫存
- Environment 與其 variables

`environment.json` 必須包含 schema version、archive type（Environment），以及一個命名 Environment 與其 key/value variables。Environment ZIP 僅含目前選定的單一 Environment，不含任何 Collection、Request、Proto 或上傳檔案。

### 8.3 檔案處理

匯出 ZIP 時：

- HTTP file upload 相關檔案放入 `files/`
- gRPC proto 檔案只匯出該 Workspace / Collection Request 實際引用的 Proto，放入 `protos/`
- `collection.json` 使用相對路徑引用檔案
- Collection 匯出不得包含任何 Environment，即使 Request 內容包含 `{{variable}}` 模板。
- Environment 匯出前介面必須提示該檔案包含 variable value，使用者需確認可安全分享。

匯入 ZIP 時：

- 自動依根描述檔識別 Workspace、Collection 或 Environment ZIP。
- 解壓並驗證 `collection.json` 或 `environment.json`
- 將檔案複製到本機資料目錄
- 建立新的 Collection 或 Environment，不覆蓋既有資料；後續若需覆蓋，必須新增明確選項。
- Collection 或 Environment 名稱重複時，自動使用「原名稱 匯入 2」、「原名稱 匯入 3」等未使用名稱。
- 匯入 Environment 後不自動切換目前選用的 Environment，避免意外以新設定送出 Request。
- 為相容既有使用者資料，仍可匯入舊版 schema v1 與 schema v2 Workspace ZIP；其中 schema v2 內嵌的 Environment 會依既有規則一併匯入。

## 9. 資料庫設計

H2 使用 file mode。第一版資料表建議如下，實作時可用 JPA entity 對應。

### 9.1 collections

- `id`
- `name`
- `description`
- `sort_order`
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
- `GRPC_BUR`

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

### 9.7 environments

- `id`
- `name`，不分大小寫唯一
- `variables_json`
- `created_at`
- `updated_at`

Environment 變數名稱需符合 `[A-Za-z_][A-Za-z0-9_.-]*`，同一 Environment 內不可重複。Request 保存原始 `{{variable}}` 模板；實際替換只發生在送出前。

### 9.8 http_batch_runs

- `id`
- `request_id`，未儲存 Request 時可為 null
- `mode`
- `status`
- `total_count`
- `max_concurrency`
- `interval_millis`
- `deadline_millis`
- `request_snapshot_json`
- `created_at`
- `started_at`
- `completed_at`

`status` values：`RUNNING`、`COMPLETED`、`DEADLINE_EXCEEDED`、`CANCELLED`。Batch Run 保存啟動當下的 HTTP Request snapshot，不因後續修改 Request 或 Environment 而改變。

### 9.9 http_batch_items

- `id`
- `batch_run_id`
- `sequence_number`
- `status`
- `status_code`
- `reason_phrase`
- `duration_millis`
- `size_bytes`
- `error_message`
- `response_headers_json`
- `response_body_preview`
- `started_at`
- `completed_at`

`status` values：`NOT_DISPATCHED`、`RUNNING`、`SUCCESS`、`FAILED`、`CANCELLED`。Response body preview 最多保存 4000 字元。批次子 Request 不寫入 `request_history`。

### 9.10 grpc_batch_runs

- `id`
- `request_id`，未儲存 Request 時可為 null
- `protocol`，`GRPC` 或 `GRPC_BUR`
- `mode`
- `status`
- `total_count`
- `max_concurrency`
- `interval_millis`
- `deadline_millis`
- `request_snapshot_json`
- `created_at`
- `started_at`
- `completed_at`

`status`、模式與 HTTP Batch 一致。snapshot 保存 gRPC unary 或 gRPC BUR 啟動時已解析 Environment variables 後的 request，不因後續 Request 或 Environment 修改而改變。

### 9.11 grpc_batch_items

- `id`
- `batch_run_id`
- `sequence_number`
- `status`
- `status_code`
- `status_description`
- `duration_millis`
- `size_bytes`
- `error_message`
- `response_metadata_json`
- `response_body_preview`
- `decoded_payloads_json`，僅 gRPC BUR 有值
- `started_at`
- `completed_at`

`status` values：`NOT_DISPATCHED`、`RUNNING`、`SUCCESS`、`FAILED`、`CANCELLED`。批次子 Request 不寫入 `request_history`。

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
- `GET /api/http/history`

此 API 接收完整 HTTP request 設定，立即執行並回傳 response，不要求 request 已儲存。

- `timeoutMillis` 未提供時預設為 `30000`。
- 可選 `executionId` 可搭配取消 API 中止進行中的 request。

### 10.5 gRPC Execute API

- `POST /api/grpc/execute`

此 API 接收完整 gRPC request 設定，立即執行並回傳 response，不要求 request 已儲存。

Request 可選欄位：

- `protoId`：指定已匯入的 `.proto`。若提供此欄位，後端必須使用本機 proto descriptor 執行，不依賴 server reflection。
- `encodePayloadDataBase64`：預設 `false`。為 `true` 時，後端在解析 Protobuf JSON 前，將 `body.payload.data` 的字串以 UTF-8 標準 Base64 取代；`payload.data` 缺失或非字串時回傳 `400 GRPC_PAYLOAD_DATA_BASE64_INVALID`。此轉換不適用 `/api/grpc-bur/execute`。
- `timeoutMillis` 未提供時預設為 `30000`。
- `executionId` 可搭配取消 API 中止進行中的 request。

### 10.5.1 gRPC BUR API

- `POST /api/grpc-bur/preview`
- `POST /api/grpc-bur/execute`

`preview` 只驗證、轉碼及產生組包結果，不會呼叫 target server。`execute` 使用預設 `com.bot.fsap.model.grpc.common.Service/rpcPeriphery`，但可選擇覆寫 service 與 method。

- `timeoutMillis` 未提供時預設為 `30000`，且必須介於 `1` 至 `300000`。
- `executionId` 可搭配取消 API 中止進行中的 request。

### 10.5.2 Execution Cancel API

- `POST /api/executions/{executionId}/cancel`

執行中的 HTTP、gRPC unary 與 gRPC BUR request 會以 `executionId` 註冊。API 回傳 `cancelled: true` 時，後端已要求關閉對應執行資源；若 request 已完成，則回傳 `cancelled: false`。

### 10.6 File API

- `POST /api/files`

### 10.7 Proto API

- `POST /api/protos`
- `GET /api/protos`
- `GET /api/protos/{id}/inspect`

### 10.8 Import / Export API

- `GET /api/workspace/export`
- `POST /api/workspace/import`
- `GET /api/collections/{id}/export`
- `GET /api/environments/{id}/export`
- `POST /api/environments/import`

### 10.9 Environment API

- `GET /api/environments`
- `POST /api/environments`
- `PUT /api/environments/{id}`
- `POST /api/environments/{id}/copy`
- `DELETE /api/environments/{id}`

### 10.10 HTTP Batch API

- `POST /api/http/batch-runs`
- `GET /api/http/batch-runs?requestId={requestId}&page={page}&size={size}`
- `DELETE /api/http/batch-runs?requestId={requestId}`：清除指定 Request 的所有非 `RUNNING` Batch Run 及其 Item；進行中的 Batch 保留。
- `GET /api/http/batch-runs/{id}`
- `GET /api/http/batch-runs/{id}/items?page={page}&size={size}`
- `GET /api/http/batch-runs/{id}/export.csv`：下載目前 Run 的 UTF-8 BOM CSV。
- `POST /api/http/batch-runs/{id}/cancel`

單批總筆數沒有產品層固定上限；最大同時執行數仍為 100，Batch Run 與 Item 結果查詢以每頁最多 100 筆分頁，前端提供前後頁瀏覽與起訖筆數。實際批次規模受 Java 整數範圍、H2 儲存空間與主機資源限制。CSV 欄位包含 Batch Run 識別、Request、時間、模式與狀態，以及每筆序號、`itemStartedAt`、`itemCompletedAt`、HTTP 結果、response headers 及保存的 body preview；CSV 欄位遵守雙引號跳脫規則。

### 10.11 gRPC Batch API

- `POST /api/grpc/batch-runs`
- `GET /api/grpc/batch-runs?requestId={requestId}&page={page}&size={size}`
- `DELETE /api/grpc/batch-runs?requestId={requestId}`
- `GET /api/grpc/batch-runs/{id}`
- `GET /api/grpc/batch-runs/{id}/items?page={page}&size={size}`
- `GET /api/grpc/batch-runs/{id}/export.csv`
- `POST /api/grpc/batch-runs/{id}/cancel`

`POST` request 包含 `requestId`、完整 `grpcRequest` 與 Batch 設定。API 僅接受 gRPC unary snapshot。

### 10.12 gRPC BUR Batch API

- `POST /api/grpc-bur/batch-runs`
- `GET /api/grpc-bur/batch-runs?requestId={requestId}&page={page}&size={size}`
- `DELETE /api/grpc-bur/batch-runs?requestId={requestId}`
- `GET /api/grpc-bur/batch-runs/{id}`
- `GET /api/grpc-bur/batch-runs/{id}/items?page={page}&size={size}`
- `GET /api/grpc-bur/batch-runs/{id}/export.csv`
- `POST /api/grpc-bur/batch-runs/{id}/cancel`

`POST` request 包含 `requestId`、完整 `grpcBurRequest` 與 Batch 設定。結果 API 只會讀取其對應協定的 Run，避免 gRPC 與 gRPC BUR 歷程混用。

## 11. 前端畫面規格

### 11.1 主畫面

第一版主畫面採單頁工作台：

- 左側：Environment、Collection tree 與 Proto 區塊
- 中央上方：Request toolbar 與 Request editor
- 中央下方：可調整高度的 Response viewer

Environment 切換、Workspace 匯入/匯出與全域操作位於左側工作台區域；Request type、目標、儲存、送出與取消位於中央 Request toolbar，不另設右側工具欄。

桌面版左側 sidebar 必須支援：

- 拖曳右側分隔列調整寬度，範圍限制為 220–520px，且不得推擠或遮蔽中央工作台。
- 拖曳縮小至 180px 以下時自動收合為 64px 最小列；收合後向右拖曳同一分隔列超過門檻時自動展開至最小可用寬度，不得提供額外收合或展開按鈕。
- 寬度與收合狀態保存在瀏覽器本機；收合或調整 sidebar 不得修改 Collection、Environment、Proto 或 Request 資料。
- 860px 以下採既有單欄窄版工作台，不顯示桌面版拖曳分隔列或強制收合行為。

不要做行銷 landing page。啟動後第一個畫面就是可操作的 API 測試工作台。

左側樹狀操作補充：

- Collection 的三點選單必須提供重新命名。
- Collection 的三點選單必須提供「匯出 Collection」，下載單一 Collection ZIP。
- Collection 列提供獨立收合控制，不得將收合與選取 Collection 混為同一操作；收合控制應有清楚的展開/收合圖示與無障礙標籤。
- 左側樹狀列、主要動作按鈕、三點選單與輸入列的操作圖示必須使用一致的 icon system；保留文字標籤的控制可顯示 icon 加文字，純圖示控制必須提供 tooltip。
- 已上傳 Proto 時，Proto 區塊預設收合，只保留數量與目前選擇的檔名摘要；使用者可展開查看、上傳及套用 service / method。
- Proto 區塊的展開狀態應保存在瀏覽器本機，重新整理後保留。

### Environment variables

- 可建立、修改、刪除及切換命名 Environment。
- 變數使用 `{{variable}}` 語法，保存 Request 時保留模板，送出時才解析；HTTP、gRPC、gRPC BUR 的可輸入字串欄位皆可替換。
- Environment 操作選單提供「匯出 Environment」、「匯入 Environment」與「複製 Environment」。
- 複製 Environment 時，使用者輸入目標名稱；名稱不可與既有 Environment 重複。複製成功後保留目前選用的 Environment，不自動切換。
- 新版 Workspace / Collection ZIP 不包含 Environment；需使用 Environment ZIP 分享變數值。

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
- Decoded
- Info
- History

HTTP 與 gRPC response viewer 可共用外層 layout，但內容欄位依 request type 切換。

任何 Request 送出時，Response viewer 自動切換到 Body tab；變數缺少或循環引用時，不發出網路請求並在 Response Body 顯示可讀錯誤。

已儲存 Request 送出後，前端必須在本次瀏覽期間依 Request 暫存最新 Response、錯誤或取消結果與最後瀏覽的 Response tab。使用者切換至其他 Request 再切回時，應立即還原該 Request 的暫存結果；暫存不得寫入 H2、Workspace ZIP 或 browser local storage，重新整理或關閉頁面後清除，以避免回應中的敏感資訊被持久化。

Response 內容過長時，Response 外框、標題與 tabs 必須固定於其可調整高度內，僅 Body、Headers、Info、Decoded 或 History 的內容區可垂直捲動；不得因內容高度而把工作區底部推出可視範圍或失去捲動能力。

gRPC BUR Response 的 `payload.*.data` 由後端成功 BUR 解碼時，Body tab 必須以解碼明碼取代原始 base64 值呈現，並保留 `decoded` 註記與原始值 tooltip；Decoded tab 不提供一般 JSON path 設定，應以滿版清單顯示每個 payload 的解碼結果、格式、編碼、長度與錯誤資訊。

Response viewer 與 Request editor 之間必須提供垂直可拖拉分隔列：

- 使用滑鼠或其他精細指標的桌面瀏覽器可用拖拉調整兩區高度，並依實際工作區高度限制最小 editor 與 Response 高度；不得因瀏覽器縮放使分隔列失效。
- 使用者調整後的比例保存在瀏覽器本機，重新整理後保留。
- 窄版以穩定的固定區塊高度為優先，不要求拖拉調整，避免觸控捲動衝突。

Request 與 Response 的每個 tab 必須在固定工作台高度內運作。當 Params、Headers、Body、Settings、gRPC BUR 組包欄位、Response Headers、Decoded、Info 或 History 內容超出可用高度時，僅該 tab 的內容區可垂直捲動；不得依賴整頁捲動、裁切內容，或將其他工作區推離可視範圍。

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
- Environment value 可能包含敏感資訊，只保存於本機 H2；僅 Environment ZIP 會帶入 value，因此使用者需自行控管匯出檔案。

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

專案必須套用 Gradle `maven-publish` 插件，並提供以下 POM 產生方式：

```bash
./gradlew generatePomXml
```

指令會在 `build/maven-poms/pom.xml` 產生根專案 POM，並在 `build/maven-poms/post-bubi-api/pom.xml`、`build/maven-poms/post-bubi-ui/pom.xml` 產生模組 POM。根專案 POM 為聚合描述；API POM 描述 Spring Boot 後端相依與 executable JAR；UI POM 描述前端靜態資源 JAR。POM 用於 Maven 相依資訊交換與發布，不取代既有 Gradle 建置、單一 JAR 打包或離線建置流程。

`TBConvert.jar` 是專案內本機檔案相依，會隨 Spring Boot executable JAR 打包，但沒有可對應的 Maven 座標，因此不會列入 API POM 的 `<dependencies>`。若要讓 Maven 直接建置此專案，必須另行進行 Maven build migration，將前端 Vite 與 Spring Boot Gradle task 改寫為 Maven plugin 設定。

若需產生已展開 BOM 且列出所有實際 compile、runtime、test Maven module 與 Gradle buildscript plugin 的最終 POM，必須依 [`MAVEN_FULL_DEPENDENCY_POM_GUIDE.md`](MAVEN_FULL_DEPENDENCY_POM_GUIDE.md) 執行 `generateAllResolvedDependencyPoms`；此產物僅供依賴交付與稽核，不可取代原始 Gradle build。

`resolved-runtime-pom.xml` 不可用來單獨填滿 Maven `.m2` 後，期待全新的 Gradle user home 能完成離線打包。Gradle buildscript plugin、wrapper distribution 與 Node / Yarn / npm 前端資源必須依既有 Gradle 與 Yarn 離線交付流程另外提供。

## 15. 已完成開發里程碑

以下核心里程碑已完成，後續異動以修正、驗證與使用者體驗改善為優先：

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
14. 實作 Environment variables、`{{variable}}` 執行期替換、timeout 與取消。
15. 實作 gRPC BUR 組包、TBConvert/CodeTable JAR 內建與回應解碼。
16. 實作 Collection/Request 拖拉管理、Response 暫存與工作台 UI/UX 整理。
17. 實作 HTTP、gRPC unary 與 gRPC BUR 單一 Request Batch 執行、H2 歷程、期限/手動取消、CSV 匯出與已完成記錄清除。
18. 實作 HTTP Request 轉 Bash/zsh 與 PowerShell `curl.exe` 指令產生及複製。
19. 實作一般 gRPC `payload.data` 送出前 UTF-8 Base64 編碼設定，並套用至單次與 Batch 執行。

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
- 可以建立 Environment，並在 HTTP、gRPC 與 gRPC BUR request 使用 `{{variable}}`。
- HTTP、gRPC unary 與 gRPC BUR 預設 timeout 為 30 秒，送出中可取消。
- gRPC BUR 可完成組包、預覽、送出與 payload BUR 解碼。
- HTTP、gRPC unary 與 gRPC BUR Batch 可執行沒有產品層固定上限的單一 Request，並可查看依 Request 保存的歷程、取消、匯出 CSV、清除已完成記錄及每頁最多 100 筆的項目結果；實際規模受 Java 整數範圍、H2 儲存空間與主機資源限制。
- HTTP Request 可產生包含目前 Params、Headers、Body、timeout 與 TLS 設定的 Bash/zsh 或 PowerShell `curl.exe` 指令。

## 17. 後續版本候選功能

第一版完成後可考慮：

- Authorization helpers
- Postman Collection import / export
- cURL import
- OpenAPI import
- gRPC server streaming
- gRPC client streaming
- gRPC bidirectional streaming
- 多 Request Collection runner
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

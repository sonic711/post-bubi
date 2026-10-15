# Request 欄位規則與 gRPC BUR 回應解碼

## 本次還原範圍

2026-10-15 經使用者審核，完整還原 A（Request 自訂欄位規則）及 B（一般 gRPC BUR Response 自動解碼），保留新版 UIUX 與送出前自動儲存。C（Windows 離線建置包、工具腳本、Gradle 離線擴充及 Yarn 路徑修正）全部捨棄，不在本次還原範圍。未經另外授權不 commit 或 push。

## Request 欄位規則

Settings 維護每筆 Request 自有的 `fieldRules`，包含 `id`、名稱、啟用、`#RRGGBB` 顏色、動作與目標。允許新增、刪除及修改，不提供跨 Request 模板。

| 類型 | 目標 | 定位 |
| --- | --- | --- |
| HTTP | JSON Body scalar value | 巢狀 JSON Pointer，例如 `/payload/transaction/id`；提供欄位選項及手動輸入 |
| gRPC | JSON `payload.data` 字串 | 轉成 UTF-8 bytes 後計算位置，不可切開多位元字元 |
| gRPC BUR | Basic Label | 字元位置 |

所有範圍採 1 起算、包含起訖位置。

- `NONE`：僅依規則顏色標示，不修改送出值。
- `INCREMENT`：單次及 Preview 加 1，Batch 第 N 筆加 N；數字字串保留長度與前導零，溢位拒絕；HTTP JSON 整數維持數字型別。
- `TODAY_DD`：採 `Asia/Taipei` 當日兩碼日期；範圍長度必須為 2，HTTP 只能套用字串欄位。

先解析 Environment，再套用規則，最後才處理一般 gRPC 的 `payload.data` Base64。儲存的 Body 保留原始輸入，BUR 執行後的序號前推維持既有行為。

啟用規則必須驗證名稱、識別碼、顏色、動作、目標、範圍、UTF-8 邊界、數字、溢位及 HTTP 欄位型別。禁止重疊範圍及重複 JSON Pointer。HTTP array index 尚不支援，路徑含陣列時明確提示；object / array 不是可設定的 scalar 目標。

規則須隨 Request 保存、複製與 Workspace / Collection / Request ZIP 攜帶；Headless 一般 gRPC CLI 讀取並套用。單次與 Batch 後端亦重新驗證，不能只依賴前端。

Batch 視窗可選擇是否套用欄位規則；取消勾選須以空規則 snapshot 執行，且 BUR 不得回退套用舊旗標。

舊 BUR 測案沒有 `fieldRules` 時轉成三條相容規則：8-14 遞增、37-46 固定綠色標示、88-89 台灣今日 DD；原先未啟用的動作仍停用。保存後以規則陣列為準；直接使用舊 API 的旗標仍需相容。

## 一般 gRPC BUR Response 解碼

一般 gRPC 與 gRPC BUR 的 JSON Response 若含 `payload` map，對 `payload.*.charsets` 不分大小寫為 `BUR` 的項目，自動將 `data` Base64 還原為 bytes，再使用內建 TBConvert / CodeTable 轉為 UTF-8，不需額外轉碼檔。

Decoded 顯示明碼、編碼、格式、byte 長度與個別錯誤；Body 成功解碼欄位回填明碼，保留 `decoded` 註記與原始 Base64 tooltip。非 BUR 項目不自動轉碼，既有手動 JSON Path Base64 解碼仍保留。失敗不得偽裝成成功明碼，也不得使整個 gRPC 回應消失。

一般 gRPC Batch 保存自動解碼結果，供 item 詳細內容檢視。Headless CLI 維持原始 response JSON 輸出，不新增 UI 專用 Decoded 顯示。

## 驗證

### 2026-10-15 整合修正合約

- Request JSON 上色必須以原始文字的 token offset 呈現，保留縮排、空白、數字原文、Unicode / JSON escape 與尾端換行，不可因語法上色改變顯示文字；HTTP Pointer、gRPC UTF-8 byte 範圍與 Base64 標記仍保留。JSON 尚未輸入完整時採一般語法上色，不改寫原文。
- HTTP 轉 cURL 必須套用啟用的欄位規則：單次遞增加 1、台灣今日 DD、僅標示不改值，與網頁送出順序一致。可選 Environment 解析先於規則；規則目標仍含未解析模板時，不可猜測遞增結果，須提示套用 Environment 並停用複製。
- cURL 規則驗證、轉換與複製維持瀏覽器內處理，不新增後端 API、History 或 log，不修改保存的 Body；Bash / PowerShell 共用轉換結果。整數不可因 JavaScript Number 失去精度。
- 使用固定版本 [Microsoft jsonc-parser](https://github.com/microsoft/node-jsonc-parser) `3.3.1` 保留 token 座標，不自行重寫 JSON parser；程式隨前端打包進 JAR，不使用 CDN。離線重建的 Yarn cache 須包含新增的 `jsonc-parser-3.3.1.tgz`，舊 cache 需更新；不涉及 Windows 離線包或 Gradle task 擴充。
- 原有自動排版仍保留，但只調整空白，不透過 JavaScript Number 重新序列化大整數；數字字面、重複 key 與 Unicode escape 維持原文。

還原後驗證規則單元測試、三協定單次 / Batch、CLI、ZIP 與新版 UIUX 回歸，重新建置正式 JAR；執行結果與待驗收事項記錄於 `DEVELOPMENT_PROGRESS.md`。

### 人工驗收

1. HTTP：Body 設為 JSON，例如 `{"payload":{"id":"0009","day":"00"}}`；Settings 新增 `/payload/id` 遞增、`/payload/day` 今日 DD，分別選擇顏色。送出應為 `0010` / 台灣今日 DD，但保存的 Body 仍為原文。
2. gRPC：Body 設為 `{"payload":{"data":"繁000900"}}`；新增 byte 4-7 遞增、byte 8-9 今日 DD。Body 應呈現兩個顏色範圍；有勾 Base64 時先套用規則再編碼。目標 proto 必須接受該 JSON 結構。
3. BUR：開啟舊測案，確認既有 8-14 / 37-46 / 88-89 轉成三條規則；新增或修改範圍、名稱及顏色，再用 Preview 確認 `effectiveBasicLabel`，Preview 不消耗序號。
4. 三種協定各執行 2 筆 Batch：勾選「套用欄位規則」時分別加 1 / 2；停用時不套用遞增或日期，BUR 亦不前推編輯器。僅標示的規則不可修改值。
5. 保存後重新整理、複製 Request、匯出單筆或 Collection ZIP 後匯入，確認規則完整保留。一般 gRPC ZIP 也可用既有 `run-grpc` CLI 執行。
6. 普通 gRPC 接收含 `payload["0"].charsets="BUR"` 的真實回應，檢查 Body 明碼、`decoded`、原 Base64 tooltip 與 Decoded 詳細內容；非 BUR 不自動轉碼，壞 Base64 顯示個別錯誤且保留原值。Batch item 亦應保留解碼明碼。
7. 輸入重疊範圍、切開「繁」的 UTF-8 bytes、遞增溢位、數字欄位套今日 DD 或 array Pointer，應得到明確錯誤，不可悄悄送出錯誤資料。

### 開發端驗證

```bash
./gradlew :post-bubi-api:test :post-bubi-api:bootJar
node --test post-bubi-ui/src/field-rules.test.mjs
```

瀏覽器需開發機已有 Playwright / Chrome，沿用 `UIUX_REDESIGN.md` 的隔離 JAR 啟動方式。設定 `UIUX_TEST_DATABASE=isolated`、`UIUX_BASE_URL`、`PLAYWRIGHT_MODULE` 及必要時的 `CHROME_EXECUTABLE`，執行 `node post-bubi-ui/tests/field-rules.e2e.mjs`。測試會建立 / 清除專用 Collection，禁止指向正式資料庫；Playwright 不加入正式應用程式依賴。

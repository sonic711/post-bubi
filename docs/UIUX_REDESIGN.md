# UIUX 工作台改版規格

日期：2026-10-15。改版前本機還原 Tag：`pre-uiux-redesign-20261015`（`51e94a0`）。

## 範圍與限制

本次重整既有 Vue 3 工作台，不修改 API、H2 schema、請求組包、轉碼、Batch 排程與 CLI。HTTP、gRPC unary、gRPC BUR、送出前自動儲存、匯入匯出及所有既有設定必須保留。維持單一 JAR、離線使用、酒紅品牌色 `#AB005F`、原 Logo 及 Light / Dark。

上述為 UIUX 改版本身的邊界；後續依使用者授權另還原 A/B 欄位規則及一般 gRPC BUR 解碼，包含必要 Java / DTO / CLI 異動，見 `REQUEST_FIELD_RULES.md`。兩者共同交付，H2 schema 不變。還原 Tag 位於被捨棄功能之前，並不包含本次還原的 A/B。

## Skills 選擇

- [UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)：使用官方 skill 的 Developer Tool / IDE 產品檢索、緊湊設計系統、Vue 指引與 UX 檢核。採用高密度、清晰層級與語意色彩；捨棄檢索結果中的文件入口頁、深藍底及遠端字型，因為不符合既有 API 工具與離線需求。
- [Vercel Web Design Guidelines](https://github.com/vercel-labs/agent-skills/tree/main/skills/web-design-guidelines)：依官方 [Web Interface Guidelines](https://github.com/vercel-labs/web-interface-guidelines) 檢查焦點、圖示命名、表單、捲動、主題、長內容與鍵盤操作。
- 比較過 [Anthropic frontend-design](https://github.com/anthropics/skills/tree/main/skills/frontend-design)；本次以更適合既有高密度工作台的前兩者為主要依據。

Skill 下載至開發機暫存目錄供本次檢索，不加入應用程式依賴，不需終端使用者安裝，也不引入 CDN 或網路資源。

## 介面要求

1. 全域列：品牌、工作區、目前 Environment、主題。桌面側欄持續可拖曳收合；窄版使用可關閉的導覽抽屜。
2. 側欄：緊湊的 Collection / ZIP 操作、Environment、可搜尋的 Collection / Folder / Request 樹與 Proto。搜尋只影響顯示，不改動資料、排序或展開偏好。
3. 請求區：Collection 路徑、可編輯名稱、儲存狀態；統一的協定 / 目標 / 方法工具列，保留儲存、送出、取消、cURL、Batch。
4. 編輯區：清楚的分頁、Header 筆數、內部獨立捲動；既有 JSON 選取與語法上色維持相同文字尺寸及捲動座標。
5. 回應區：狀態、時間與大小容易掃讀，保留 Body / Headers / Batch / Decoded / Info / History 與可調整高度。無回應、執行中、錯誤、取消均有區別。
6. 對話框：Escape 關閉、焦點限制與返回原觸發點；明確點擊灰底才關閉，拖曳文字到灰底放開不關閉。
7. 可及性：可見鍵盤焦點、圖示標籤、分頁方向鍵 / Home / End、主題選取狀態、減少動態效果、兩種主題的可讀對比。

## 設計與實作決策

- 品牌動作固定 `#AB005F` 搭配白字；Dark 的文字 / 選取提示採較淺酒紅，避免低對比。
- Light 以白色 `#FFFFFF`、側欄 `#F7F8FA`、文字 `#16191D` 分層；Dark 以 `#1B1C20`、`#202126`、文字 `#F1F3F4` 分層。成功、錯誤、警示、協定仍有綠 / 紅 / 藍等語意色。
- 系統 sans-serif 搭配現有等寬 JSON 字型 fallback；不載入遠端字型。間距以 4 / 8px 為基礎，控制項圓角 4 / 6px。
- 一般 gRPC 的 Settings 增加已上傳 Proto 選擇，仍使用既有 `grpcProtoId`，不改變 reflection / proto 的執行規則。
- Response 自動換行是檢視狀態；「複製原始 Response Body」保留原始欄位值，不複製 UI 解碼標示。剪貼簿共用 helper 保留非安全 HTTP 網址的相容 fallback。
- 修正首次開啟時不存在的尺寸偏好被轉成 0，以及側欄鍵盤方向鍵無法跨越收合門檻的問題。
- Grid 的單欄軌道使用 `minmax(0, 1fr)`，避免長 BUR 電文將工具列擠出畫面。Batch 的歷程 / 統計 / 結果分列，狹小高度時可捲動。

## 驗證與交付

- 自動化：既有 cURL、送出前儲存測試；新增搜尋 / UI 行為驗證。
- 瀏覽器：桌面與窄版、Light / Dark、三協定、長 Headers / Settings / Body / Response 捲動、側欄 / 回應調整、對話框與鍵盤。
- API 串接：使用獨立測試資料庫驗證 HTTP 送出與自動儲存，不碰既有使用者資料。
- 打包：前端正式建置與單一 `bootJar`。
- 進度與測試結果統一記錄於 `DEVELOPMENT_PROGRESS.md`；目標系統與 Windows 實機仍需使用者驗收。
- 本次只建立本機還原 Tag；未取得另外授權前不 commit / push 改版程式或推送 Tag。

## 開發端瀏覽器回歸

`post-bubi-ui/tests/workspace.e2e.mjs` 需要開發機已有 Playwright 與可用 Chromium，不包含於正式應用程式或離線使用者依賴。測試會建立並清除自己的 Collection / Environment，必須使用獨立測試資料庫，不得對正式資料執行。

1. 用獨立 port 與記憶體 H2 啟動打包後 JAR：

   ```bash
   java -jar post-bubi-api/build/libs/post-bubi.jar --server.port=18082 \
     --spring.datasource.url=jdbc:h2:mem:uiux-test \
     --post-bubi.storage.files-dir=/tmp/post-bubi-uiux/files \
     --post-bubi.storage.protos-dir=/tmp/post-bubi-uiux/protos
   ```

2. 從專案根目錄執行（Unix shell；`PLAYWRIGHT_MODULE` 可指定既有 Playwright 的 module 路徑，`CHROME_EXECUTABLE` 可指定現有瀏覽器）：

   ```bash
   UIUX_TEST_DATABASE=isolated \
   UIUX_BASE_URL=http://127.0.0.1:18082 \
   UIUX_API_URL=http://127.0.0.1:18082 \
   node post-bubi-ui/tests/workspace.e2e.mjs
   ```

3. 預設截圖在 `/tmp/post-bubi-uiux-screenshots`，可由 `UIUX_SCREENSHOTS` 覆寫。檢查零 browser error、兩種主題與桌面 / 1024 / 768 / 390 / 375px；這不能取代 Windows 與實際目標系統驗收。

# gRPC BUR 組包功能

最後整理日期：2026-10-20

本功能已完成第一個可用版本。設計源自原始驗證 notebook 的組包流程；notebook 已不再是建置或執行必要檔案，實際行為以目前 Java 實作與本文件為準。

## 使用目的

一般使用者只需要處理：

- 目標系統 host 與 port；port 可填數字或 Environment `{{variable}}`，送出前解析並驗證為 `1` 至 `65535`
- Basic Label
- Text Area
- 必要的 metadata、TLS 與 timeout

Post Bubi 會負責 TCPIP Header、MCS Header 或 TOTA BOX、固定長度補空白、BUR 轉碼、payload 串接、protobuf JSON request 與 response 解碼。

固定呼叫目標預設為：

```text
com.bot.fsap.model.grpc.common.Service/rpcPeriphery
```

## 使用流程

1. 在 Request Type 選擇 `gRPC BUR`。
2. 填寫 target `host:port`。
3. 在 Body 編輯 Basic Label 與 Text Area。
4. 使用「產生預覽」檢查各段長度、最終 hex 與 decoded text。
5. 必要時在 Settings 指定 protoId、TLS、TCPIP Header、固定長度與自訂 Basic Label 欄位規則；可設定名稱、顏色、起訖位置與動作。
6. 送出後在 Body 查看 protobuf JSON，在 Decoded 查看各 payload 的 BUR 明碼。

Request 可以保存到 Collection，載入後會還原所有 gRPC BUR 欄位。

## Payload 組成

最終 bytes 順序依 Settings 的電文結構選擇：

```text
MCS:      TCPIP Header + MCS Header + Basic Label + Text Area
TOTA_BOX: TCPIP Header + TOTA BOX   + Basic Label + Text Area
```

預設值：

| 欄位 | 預設 |
| --- | --- |
| TCPIP Header | `0F 0F 0F 00 02 65 01 F0 F0 F0 0B 0F` |
| 電文結構 `settings.messageLayout` | `MCS`；舊測案未設定時維持原組包 |
| TOTA BOX `settings.totaBoxHex` | `00 00 00 00 00 00`；只在 `TOTA_BOX` 使用，必須為 6 bytes |
| 自動更新長度 `settings.autoUpdateTcpipLength` | false；舊測案不改 Header |
| MCS Header 長度 | 72 字元 |
| Basic Label 長度 | 158 字元 |
| Text Area 長度 | `0`，代表不限制 |
| Timeout | 30000 ms |
| Plaintext | true |

處理規則（自訂欄位規則完整合約見 [REQUEST_FIELD_RULES.md](REQUEST_FIELD_RULES.md)）：

- TCPIP Header 由 hex 轉 bytes，格式錯誤時拒絕送出。
- MCS Header 使用 UTF-8 bytes，長度不足時右補空白。
- `TOTA_BOX` 不插入 MCS；TOTA BOX 由 Hex 直接轉為 bytes，不經 BUR 或 UTF-8 轉碼。切換結構不改寫 Label、Text、Header 或既有規則，使用者需依新格式調整長度及規則範圍。
- 自動更新長度開啟時，TCPIP Header 必須為 12 bytes，且固定識別碼為前 3 bytes `0F 0F 0F`、第 7 byte `01`、第 12 byte `0F`；組包完成後，以總 byte 數的 6 碼十進位 packed BCD 寫入第 4-6 bytes（含所有 Header）。上限 999999 bytes，超出或 Header 無效時拒絕組包；不改第 11 byte 交易類型或其他 Header 內容。關閉時保留原始 Header，包括原有短 Hex 的 Preview 相容行為。
- Basic Label 使用 TBConvert 從 UTF-8 轉 BUR，長度不足時右補空白。
- Text Area 使用 TBConvert 從 UTF-8 轉 BUR；設定正整數長度時依設定右補空白。
- 任一固定欄位超過設定長度時拒絕送出，不自動截斷。
- 串接結果放入 protobuf JSON 的 `payload.data`，使用 base64 表示。
- Settings 可新增多條 Basic Label 字元範圍規則，採 1 起算、含起訖；可僅標示、遞增數字或填入 `Asia/Taipei` 今日兩碼 DD。啟用範圍不得重疊；遞增保留長度且拒絕溢位，DD 範圍必須為 2。
- 舊測案未保存 `fieldRules` 時，UI 轉成第 8-14 碼遞增、第 37-46 碼綠色標示、第 88-89 碼今日 DD 三條規則，保留舊動作啟用狀態。仍接受未帶 `fieldRules` 的舊 API 旗標；有規則陣列時以規則為準。
- 單次送出先經 Preview 計算 `+1` / 今日 DD，更新並保存 Basic Label 後才正式發送，正式快照改用 `NONE` 動作避免多加一次。Batch 先計算並保存第 N 筆值，但仍使用更新前起始值逐筆加 N。失敗或取消不回退；回應不再前推或覆寫 Basic Label。獨立 Preview 不消耗序號，詳見 `REQUEST_FIELD_RULES.md`。
- Batch 視窗「套用欄位規則」預設勾選；停用時 snapshot 傳 `settings.fieldRules=[]`，不得回退舊旗標，也不前推 Basic Label。

傳給共用 gRPC executor 的 body：

```json
{
  "payload": {
    "charsets": "BUR",
    "format": "TEXT",
    "data": "<base64>"
  }
}
```

## API

### A98 / A99 TOTA BOX 測案（2026-10-20）

兩筆仍使用 `GRPC_BUR`，不新增 Request 類型。Settings 選擇 `TOTA_BOX`、TOTA BOX 為 `00 00 00 00 00 00`、Basic Label Length 為 35、Text Area Length 為 0（不補至 notebook 註解中的 151）；Header 填 `0F 0F 0F 00 06 12 01 00 22 23 1F 0F`，可開啟自動更新長度。

| 測案 | Basic Label | Text Area | 總長度 | 自動更新後 Header 第 4-6 bytes |
| --- | --- | --- | --- | --- |
| A98 FIX | `983029400020734AU07000001 A98100500` | `A98  0001002222` | 68 bytes | `00 00 68` |
| A99 FIX | `981045800001372AI07000001 A99100410` | `A99  2` | 59 bytes | `00 00 59` |

使用相符的 Service proto、Plaintext 與部署的 host / port。原 notebook 的 `00 06 12` 宣告長度為 612，而非實際總長。若需原樣重現，關閉自動更新長度。35 字元 Label 不可啟用超出範圍的舊規則（如 37-46、88-89）；新 TOTA BOX 測案未提供規則時不自動建立舊 MCS 規則。

新設定須隨 Request 保存、複製、Workspace / Collection / Request ZIP，以及單次及 Batch 快照保留；Environment 可解析 TOTA BOX Hex。Preview 增加 `messageLayout`、`totaBoxLength`，以 `payloadHex` 顯示實際發送 Header；舊欄位保留，TOTA BOX 時 `mcsHeaderLength=0`。H2 schema、ZIP schema 與 Headless CLI 範圍不變。

兩份 FIX notebook 都記錄 `CANCELLED: RST_STREAM closed stream`，沒有成功 Response；此次功能只支援正確組包與維護測案，不修正或宣稱解決 Gateway / Runtime 回程。以本機 gRPC 接收端驗證傳輸與回應，真實目標仍待使用者驗收。Gateway 原始碼僅為比對資料，不修改或提交。

### Preview

```http
POST /api/grpc-bur/preview
```

只執行驗證、補空白、轉碼與組包，不呼叫 target server。

### Execute

```http
POST /api/grpc-bur/execute
```

Request 主要欄位：

```json
{
  "host": "10.1.11.34",
  "port": 50003,
  "timeoutMillis": 30000,
  "plaintext": true,
  "ignoreTlsVerification": false,
  "metadataText": "",
  "protoId": "",
  "serviceName": "com.bot.fsap.model.grpc.common.Service",
  "methodName": "rpcPeriphery",
  "tcpipHeaderHex": "0F 0F 0F 00 02 65 01 F0 F0 F0 0B 0F",
  "mcsHeader": "",
  "basicLabel": "...",
  "textArea": "...",
  "settings": {
    "messageLayout": "MCS",
    "totaBoxHex": "00 00 00 00 00 00",
    "autoUpdateTcpipLength": false,
    "mcsHeaderLength": 72,
    "basicLabelLength": 158,
    "textAreaLength": 0,
    "padTextAreaRight": true,
    "incrementBasicLabelSequence": false,
    "fillBasicLabelDayOfMonth": false
  }
}
```

`serviceName` 與 `methodName` 是可選覆寫欄位；未提供時分別使用 `com.bot.fsap.model.grpc.common.Service` 與 `rpcPeriphery`。`timeoutMillis` 未提供時預設為 `30000`，可設定範圍為 `1` 至 `300000`。

Response 包含：

- gRPC status 與 description
- duration
- response metadata
- 原始 protobuf JSON body
- request preview
- decoded payload list

## Proto 解析

執行順序：

1. Request 有 `protoId` 時直接使用指定 proto。
2. 未指定時掃描 UI 已上傳到 `data/protos/` 的 proto，尋找相符 service/method。
3. 找不到相符 proto 時，回退到 target server reflection。

使用 UI 上傳 proto 是正式操作方式。Repository 內的 `data/proto/` 用於開發、範例與 import dependency 解析。

## TBConvert 與 CodeTable

建置輸入：

```text
data/TBConvert.jar
data/CodeTable/TB_UCS2_BUR.bin
data/CodeTable/TB_BUR_UCS2.bin
```

Gradle 會產生：

```text
BOOT-INF/lib/TBConvert.jar
BOOT-INF/classes/bur/CodeTable/TB_UCS2_BUR.bin
BOOT-INF/classes/bur/CodeTable/TB_BUR_UCS2.bin
```

Runtime 載入順序：

1. 若 `post-bubi.bur.code-table-dir` 指向完整外部 CodeTable，優先使用外部檔案。
2. 否則從 JAR resource 解出 `TB_UCS2_BUR.bin` 與 `TB_BUR_UCS2.bin` 到暫存目錄。

因此部署主機只需要 executable JAR，不需要另外攜帶 CodeTable 或 TBConvert.jar。

外部覆寫範例：

```bash
java -jar post-bubi.jar \
  --post-bubi.bur.code-table-dir=./data/CodeTable
```

## Response 解碼

若 response body 是 `PeripheryResponse` JSON 且包含 `payload` map，系統會：

1. 讀取每個 entry 的 `data` base64。
2. 轉為 bytes。
3. 使用 BUR CodeTable 轉成 UTF-8。
4. 回傳 key、charsets、format、length、hex、text 與 error。

單一 payload 解碼失敗不會丟棄整個 response。成功解碼時，Body 會以明碼呈現原本的 `payload.*.data` 值，並標示 `decoded`、保留原始 base64 tooltip；Decoded tab 以滿版清單顯示各 entry 的資訊。失敗 entry 會保留原始值並在 Decoded 顯示錯誤原因。

## 錯誤代碼

| Code | 說明 |
| --- | --- |
| `GRPC_BUR_LENGTH_INVALID` | 固定長度設定不合法 |
| `GRPC_BUR_TEXT_TOO_LONG` | 輸入超過設定長度 |
| `GRPC_BUR_HEX_INVALID` | TCPIP Header 或 TOTA BOX hex 格式錯誤 |
| `GRPC_BUR_LAYOUT_INVALID` | 電文結構不是 MCS / TOTA_BOX |
| `GRPC_BUR_TOTA_BOX_LENGTH_INVALID` | TOTA BOX 不是 6 bytes |
| `GRPC_BUR_TCPIP_HEADER_INVALID` | 開啟自動長度，但 TCPIP Header 長度或識別碼不正確 |
| `GRPC_BUR_PAYLOAD_LENGTH_OVERFLOW` | 開啟自動長度，電文超出 999999 bytes |
| `GRPC_BUR_CONVERT_FAILED` | UTF-8/BUR 轉碼失敗 |
| `GRPC_BUR_CODE_TABLE_MISSING` | 外部與 JAR 內建 CodeTable 都不可用 |
| `GRPC_BUR_CODE_TABLE_LOAD_FAILED` | TBConvert 載入 CodeTable 失敗 |

共用 gRPC 連線、proto、JSON 與 TLS 錯誤沿用一般 gRPC executor 的統一錯誤格式。

## 驗證

自動化測試：

```bash
./gradlew :post-bubi-api:test --tests com.postbubi.web.GrpcBurExecuteIntegrationTest
./gradlew :post-bubi-api:bootJar
```

測試涵蓋：

- 無外部 CodeTable 時從 JAR resource 載入。
- TCPIP、MCS、Basic Label、Text Area 串接結果。
- A98 / A99 TOTA BOX 分別 68 / 59 bytes，Hex 原樣保留、無 MCS；可選 packed BCD 長度、舊 MCS 預設不改值、無效格式及短 Label 範圍驗證。
- 本機 gRPC 接收端驗證單次及三種 Batch 模式傳輸、回應及逐筆規則加值。
- Basic Label 超長錯誤。
- Timeout 預設與合法範圍驗證。

瀏覽器 TOTA BOX 驗收沿用 `UIUX_REDESIGN.md` 的隔離 H2 / storage 啟動方式，設定 `UIUX_TEST_DATABASE=isolated`、`UIUX_BASE_URL`、`PLAYWRIGHT_MODULE` 及必要時的 `CHROME_EXECUTABLE`，執行 `node post-bubi-ui/tests/grpc-bur-layout.e2e.mjs`。Playwright 只使用開發機工具，不加入正式依賴；不得指向正式資料庫。2026-10-20 後端 63 項、前端 60 項、瀏覽器 31 組通過，細節與測試替身範圍見 `DEVELOPMENT_PROGRESS.md`。

人工操作：

1. 新增 gRPC BUR Request，在 Settings 選 `TCPIP + TOTA BOX + Label + Text`，設定上述 Header、6-byte TOTA BOX、Basic Label Length 35、Text Area Length 0，勾選「依電文總 bytes 自動更新 TCPIP 長度」。
2. Body 填入表中的 A98 或 A99 Label / Text（保留空白），產生預覽，確認 `mcsHeaderLength=0`、`totaBoxLength=6`、`payloadLength=68` 或 `59`，以及 Header 第 4-6 bytes。
3. 新建適合短 Label 的欄位規則，或保留空規則；不要啟用原本 37-46 / 88-89 的長 Label 規則。切換結構不會替使用者刪除規則。
4. 選擇部署目標及相符的 proto / Plaintext，保存、重新整理並再次預覽，確認設定保留；一次送出驗收真實回應，再以少量 Batch 驗收。若有 CANCEL，依 Gateway log 追查，不反覆重送交易。
- TBConvert 與 CodeTable 打入 executable JAR。

實際 target server 驗收仍需要正確業務資料、可連線環境與相符 proto。

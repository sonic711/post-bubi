# gRPC BUR 組包請求功能設計

本文根據 `data/[SIT]FAS_MOCK_NM001_S00.ipynb` 的腳本內容整理，作為後續開發與驗收依據。

目前狀態：第一階段已完成 `GRPC_BUR` request type、payload preview、`/api/grpc-bur/execute` 與前端 UI；後端 `BurCodecService` 已接入 `TBConvert.jar` 與 CodeTable，使用 `TB_UCS2_BUR.bin`、`TB_BUR_UCS2.bin` 執行 UTF-8 / BUR 雙向轉碼。CodeTable 已包入 JAR，部署時不需要額外攜帶 `data/CodeTable`。

## 1. 功能目標

新增一種 gRPC 請求模式，讓使用者不需要理解 Notebook 內的 Java 腳本、hex 串接、BUR 轉碼與 protobuf 組裝細節，只需要填寫：

- 目標系統 IP
- 目標系統 port
- `## 請輸入GRPC input 參數` 對應的業務輸入內容

系統會自動完成：

- 固定 TCPIP header 串接
- MCS header 補空白
- Basic Label 與 Text Area 欄位串接
- UTF-8 文字轉 BUR bytes
- hex / bytes 組合
- 建立 `RqPayload`
- 建立 `PeripheryRequest`
- 呼叫 `com.bot.fsap.model.grpc.common.Service/rpcPeriphery`
- 將 response payload 的 BUR bytes 轉回 UTF-8 明碼顯示

## 2. Notebook 行為摘要

Notebook 目前執行流程如下：

1. 載入 gRPC proto 產生的 Java classes。
2. 載入 `TBConvert.jar` 與 code table。
3. 建立 UTF-8 與 BUR 雙向轉碼函式。
4. 設定目標 `HOST` 與 `PORT`。
5. 設定三段 request payload：
   - TCPIP Header：固定 hex，長度 12 bytes。
   - MCS Header：固定 72 個空白字元。
   - Basic Label：固定長度 158 的文字區塊。
   - Text Area：固定長度業務資料文字區塊。
6. 將 MCS Header 用一般 bytes 轉 hex。
7. 將 Basic Label 與 Text Area 從 UTF-8 轉 BUR，再轉 hex。
8. 依序串接 `TCPIP Header + MCS Header + Basic Label + Text Area`。
9. 將 hex decode 成 bytes，放入 `RqPayload.data`。
10. 設定 `RqPayload.charsets = BUR`、`RqPayload.format = TEXT`。
11. 建立 `PeripheryRequest` 並呼叫 `rpcPeriphery`。
12. 顯示 request / response 的 payload bytes 與 BUR 轉 UTF-8 後的明碼內容。

## 3. 使用者操作設計

### 3.1 建議入口

在 Request Type 增加一個模式：

```text
HTTP | gRPC | gRPC BUR
```

`gRPC BUR` 是針對此類固定格式 FSAP / FAS mock 呼叫的高階模式，不取代既有 gRPC JSON request。

### 3.2 使用者主要畫面

畫面分成三個區塊。

#### Target

使用者填寫：

- Host：例如 `10.1.11.34`
- Port：例如 `50003`
- Timeout：預設 `60000 ms`
- Plaintext / TLS：第一版先預設 plaintext，保留後續擴充

#### Input

使用者只需填寫 Notebook 標題 `## 請輸入GRPC input 參數` 下方真正需要變動的內容。

第一版建議使用「表單 + 原始文字預覽」：

- Basic Label：多行文字或固定長度欄位表單。
- Text Area：多行文字。
- 顯示每段目前 byte / char 長度。
- 若長度不足，依規則右補空白。
- 若長度超過，阻止送出並提示超出的段落。

為了降低使用者負擔，初版可以先提供 Notebook 內的預設值：

```text
Basic Label 預設：
983000020260708000000000000000  00  NM00100S00                    000000000000000000000080000000000000000000  000                00000000  0   000000000000000          

Text Area 預設：
yoman   00000000000000123         4000000
```

使用者可直接修改這兩段，不需要接觸 TCPIP Header、MCS Header、BUR 轉碼與 protobuf。

#### Preview

送出前提供可展開的預覽：

- TCPIP Header hex
- MCS Header 長度
- Basic Label UTF-8 原文
- Basic Label BUR hex
- Text Area UTF-8 原文
- Text Area BUR hex
- 最終 payload bytes 長度
- 最終 payload hex
- BUR decode 後的 request 明碼

Preview 預設收合，避免干擾一般使用者；除錯時可展開。

### 3.3 Response 顯示

Response 建議沿用目前 response viewer，但針對 gRPC BUR 增加更直接的顯示：

- Summary：gRPC status、duration、payload 數量。
- Body：protobuf response JSON。
- Decoded：每個 `payloadMap` entry 的 decoded 結果。

Decoded 內容包含：

- payload key
- charsets
- format
- data bytes 長度
- data hex
- BUR 轉 UTF-8 明碼

若 BUR decode 失敗，Body 仍顯示原始 protobuf JSON，Decoded 顯示錯誤原因。

## 4. 技術實作設計

### 4.1 後端 API

新增 API：

```http
POST /api/grpc-bur/execute
```

另提供送出前預覽 API：

```http
POST /api/grpc-bur/preview
```

Request payload：

```json
{
  "host": "10.1.11.34",
  "port": 50003,
  "timeoutMillis": 60000,
  "plaintext": true,
  "tcpipHeaderHex": "0F 0F 0F 00 02 65 01 F0 F0 F0 0B 0F",
  "mcsHeader": "                                                                        ",
  "basicLabel": "983000020260708000000000000000  00  NM00100S00                    000000000000000000000080000000000000000000  000                00000000  0   000000000000000          ",
  "textArea": "yoman   00000000000000123         4000000",
  "settings": {
    "basicLabelLength": 158,
    "mcsHeaderLength": 72,
    "padTextAreaRight": true
  }
}
```

Response payload：

```json
{
  "grpcStatus": "OK",
  "durationMillis": 123,
  "requestPreview": {
    "payloadLength": 309,
    "payloadHex": "0F 0F ...",
    "decodedText": "..."
  },
  "response": {
    "json": "{...}",
    "decodedPayloads": [
      {
        "key": "0",
        "charsets": "BUR",
        "format": "TEXT",
        "length": 123,
        "hex": "....",
        "text": "..."
      }
    ]
  }
}
```

### 4.2 後端元件

建議拆成下列元件：

- `BurCodecService`
  - 封裝 UTF-8 -> BUR 與 BUR -> UTF-8。
  - 負責載入 `TBConvert.jar` 所需 code table。
- `GrpcBurPayloadComposer`
  - 驗證與補齊 header / label / text area。
  - 組合最終 bytes。
  - 產生 preview。
- `GrpcBurExecuteService`
  - 建立 channel。
  - 建立 `RqPayload` / `PeripheryRequest`。
  - 呼叫 `ServiceGrpc.ServiceBlockingStub.rpcPeriphery`。
  - 解碼 `PeripheryResponse.payloadMap`。
- `GrpcBurController`
  - 提供前端 API。
  - 回傳統一錯誤格式。

### 4.3 依賴與離線打包

此功能依賴 Notebook 目前使用的外部資源：

- `TBConvert.jar`，已包入 executable JAR 的 `BOOT-INF/lib/`
- CodeTable：
  - `TB_UCS2_BUR.bin`
  - `TB_BUR_UCS2.bin`
- CodeTable 會包入 executable JAR 的 `BOOT-INF/classes/bur/CodeTable/`；若部署主機同時存在外部 `post-bubi.bur.code-table-dir`，會優先使用外部檔案，否則自動從 JAR 內建 resource 解出到暫存目錄供 `TBConvert.jar` 載入。
- `commons-codec`
- `com.bot.fsap.model.grpc.common.*` protobuf / grpc classes

後續實作前必須先確認：

- `TBConvert.jar` 需保留在 build 可讀的位置，供 Gradle 打入 executable JAR。
- CodeTable 已由 Gradle `processResources` 從 `data/CodeTable` 打入 JAR。
- 若需要覆蓋 JAR 內建 CodeTable，可用啟動參數指定外部目錄，例如：

```bash
java -jar post-bubi.jar --post-bubi.bur.code-table-dir=./data/CodeTable
```

## 5. Collection 保存設計

Request 保存時新增 type：

```text
GRPC_BUR
```

保存內容放在既有 `payloadJson`，包含：

- host
- port
- timeoutMillis
- plaintext
- tcpipHeaderHex
- mcsHeader
- basicLabel
- textArea
- composer settings

匯出 ZIP / 匯入 ZIP 必須保留 `GRPC_BUR` request。

若 CodeTable 或 TBConvert jar 不隨 workspace export 匯出，匯入到其他環境後仍需該環境已安裝相同轉碼資源。

## 6. 初版範圍建議

第一階段只做 Notebook 等價功能的操作流程：

- 固定呼叫 `com.bot.fsap.model.grpc.common.Service/rpcPeriphery`。
- 固定 `Charsets.BUR`。
- 固定 `Format.TEXT`。
- TCPIP Header、MCS Header、Basic Label、Text Area 組包。
- 使用者可填 host、port、Basic Label、Text Area。
- 顯示 request preview 與 response decoded text。
- Request 可保存、載入、匯出、匯入。

注意：目前第一階段已完成上述 UI/API 骨架、preview 與正式 BUR byte 轉碼；部署使用時只需攜帶 `post-bubi.jar`。建置此 JAR 的環境仍需保留 `data/TBConvert.jar` 與 `data/CodeTable`，讓 Gradle 能把資源包入 JAR。

第一階段先不做：

- 多種交易格式模板。
- Basic Label 欄位級拆解表單。
- 自動解析不同 proto service / method。
- Streaming gRPC。
- 多 CodeTable 切換。

## 7. 待確認事項

實作前需要使用者確認：

1. `Text Area` 是否有固定長度限制？Notebook 註解寫「長度 67」，但實際字串後方包含大量空白，需確認真實規格。
2. `Basic Label` 是否永遠固定 158 字元？超長時應阻止送出，還是截斷？
3. `MCS Header` 是否永遠 72 個空白？是否需要使用者可修改？
4. `TCPIP Header hex` 是否固定為 `0F 0F 0F 00 02 65 01 F0 F0 F0 0B 0F`？是否需要依交易自動變化？
5. `Service/rpcPeriphery` 是否所有此類交易都固定使用同一個 service 與 method？
6. CodeTable 與 `TBConvert.jar` 是否可以放入專案並隨 JAR 發布？
7. Response decoded 後是否只需顯示整段明碼，還是也要依欄位規格切欄顯示？

## 8. 驗收標準

第一階段完成時，至少需驗證：

- `:post-bubi-api:test` 通過。
- `:post-bubi-api:bootJar` 通過。
- UI 可建立 `gRPC BUR` request。
- 使用者只填 host、port、Basic Label、Text Area 即可送出。
- 後端產生的 request payload bytes 與 Notebook 同輸入時一致。
- Response payload 可顯示 raw JSON、hex 與 BUR 轉 UTF-8 明碼。
- Request 可保存、重新載入、匯出 ZIP、匯入 ZIP。
- 缺少 CodeTable 或轉碼失敗時，錯誤訊息需為繁體中文且能指出缺少的資源。

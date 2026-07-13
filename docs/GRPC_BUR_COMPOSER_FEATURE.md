# gRPC BUR 組包功能

最後整理日期：2026-07-13

本功能已完成第一個可用版本。設計源自原始驗證 notebook 的組包流程；notebook 已不再是建置或執行必要檔案，實際行為以目前 Java 實作與本文件為準。

## 使用目的

一般使用者只需要處理：

- 目標系統 host 與 port
- Basic Label
- Text Area
- 必要的 metadata、TLS 與 timeout

Post Bubi 會負責 TCPIP Header、MCS Header、固定長度補空白、BUR 轉碼、payload 串接、protobuf JSON request 與 response 解碼。

固定呼叫目標預設為：

```text
com.bot.fsap.model.grpc.common.Service/rpcPeriphery
```

## 使用流程

1. 在 Request Type 選擇 `gRPC BUR`。
2. 填寫 target `host:port`。
3. 在 Body 編輯 Basic Label 與 Text Area。
4. 使用「產生預覽」檢查各段長度、最終 hex 與 decoded text。
5. 必要時在 Settings 指定 protoId、TLS、TCPIP Header 與固定長度。
6. 送出後在 Body 查看 protobuf JSON，在 Decoded 查看各 payload 的 BUR 明碼。

Request 可以保存到 Collection，載入後會還原所有 gRPC BUR 欄位。

## Payload 組成

最終 bytes 順序：

```text
TCPIP Header + MCS Header + Basic Label + Text Area
```

預設值：

| 欄位 | 預設 |
| --- | --- |
| TCPIP Header | `0F 0F 0F 00 02 65 01 F0 F0 F0 0B 0F` |
| MCS Header 長度 | 72 字元 |
| Basic Label 長度 | 158 字元 |
| Text Area 長度 | `0`，代表不限制 |
| Timeout | 30000 ms |
| Plaintext | true |

處理規則：

- TCPIP Header 由 hex 轉 bytes，格式錯誤時拒絕送出。
- MCS Header 使用 UTF-8 bytes，長度不足時右補空白。
- Basic Label 使用 TBConvert 從 UTF-8 轉 BUR，長度不足時右補空白。
- Text Area 使用 TBConvert 從 UTF-8 轉 BUR；設定正整數長度時依設定右補空白。
- 任一固定欄位超過設定長度時拒絕送出，不自動截斷。
- 串接結果放入 protobuf JSON 的 `payload.data`，使用 base64 表示。

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
    "mcsHeaderLength": 72,
    "basicLabelLength": 158,
    "textAreaLength": 0,
    "padTextAreaRight": true
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
| `GRPC_BUR_HEX_INVALID` | TCPIP Header hex 格式錯誤 |
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
- Basic Label 超長錯誤。
- Timeout 預設與合法範圍驗證。
- TBConvert 與 CodeTable 打入 executable JAR。

實際 target server 驗收仍需要正確業務資料、可連線環境與相符 proto。

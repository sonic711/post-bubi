# Headless gRPC CLI Phase 1

## 目的

正式 Linux 主機無法以瀏覽器操作時，使用者只攜帶 Post Bubi executable JAR 與由 UI 匯出的 Request、Collection 或 Workspace ZIP，即可在終端執行一筆一般 `GRPC` unary 測試案例。

此功能不啟動 Web server、不匯入或修改既有 H2 workspace，也不需要 UI。HTTP、gRPC BUR、Batch 與 Request History 不屬於本階段。

## 命令列介面

```bash
java -jar post-bubi.jar run-grpc \
  --archive ./payment-collection.zip \
  --request "查詢帳務" \
  --environment sit \
  --var host=10.20.30.40 \
  --var port=50115
```

必要參數：

- `run-grpc`：第一個位置參數，啟用 headless gRPC CLI。
- `--archive <path>`：Request、Collection 或 Workspace export ZIP。
- `--request <name>`：要執行的 Request 名稱。

選用參數：

- `--collection <name>`：Workspace ZIP 內 Request 名稱重複時，用於限定 Collection；Request ZIP 僅有一筆 Request 時不需要提供。
- `--folder <path>`：同一 Collection 仍有同名 Request 時，以 `/` 分隔的 Folder 路徑限定，例如 `付款/查詢`；根目錄使用 `/`。
- `--environment <name>`：指定 ZIP 中的 Environment；未指定時只允許 Request 不含 `{{variable}}`。
- `--var <key=value>`：可重複使用，優先覆寫指定 Environment 的變數；不得輸出其值至終端或 log。
- `--output <path>`：將完整 JSON 結果寫入指定檔案；未指定時輸出至 stdout。
- `--help`：輸出用法並以成功狀態結束。

Request、Collection 或 Environment 名稱必須完全相符。找不到、重複或 Folder 路徑不明確時拒絕執行，不猜測目標。

## 執行流程

1. `PostBubiApplication` 偵測第一個參數為 `run-grpc` 後直接啟動 CLI runner，不建立 Spring context，因此不綁定 `18080` port，也不初始化 H2 或 Web controller。
2. CLI 驗證 ZIP 存在、參數格式與 ZIP entry path；遵守既有 zip slip 防護原則。
3. 讀取 ZIP 的 `collection.json` schema v1 至 v3，僅接受 archive 中 `type = GRPC` 的 Request。
4. 依 `collection`、`folder`、`request` 解析唯一 Request，讀取其保存的 `payloadJson`。
5. 解析指定 Environment 與 `--var`，以與 UI 相同的 `{{variable}}` 規則替換 host、port、metadata、service、method 與 body；未定義變數時拒絕送出。
6. 將 ZIP 內的 proto 安全寫入 JVM 暫存目錄。若 Request 指定 `grpcProtoId`，依 ZIP archive 的 proto ID 對映至暫存檔；未指定時沿用 server reflection。
7. 透過既有 `GrpcExecuteService` 呼叫 unary gRPC。TLS、timeout、metadata、`payload.data` Base64 設定都沿用已保存 Request 值。
8. 將 response、status、metadata、耗時與錯誤輸出為 UTF-8 JSON；若指定 `--output`，同時寫入該檔案。JVM 結束前刪除 CLI 專用暫存 proto 目錄。

## Exit Code

| Code | 意義 |
| --- | --- |
| `0` | gRPC status 為 `OK`。 |
| `1` | gRPC 已完成但 status 非 `OK`。 |
| `2` | CLI 參數、ZIP、選擇條件、Environment、變數或 proto 驗證失敗。 |
| `3` | 無法建立輸出檔、暫存資源或發生未預期內部錯誤。 |

所有可預期錯誤以 JSON 寫入 stderr。stdout 只保留成功或非 `OK` gRPC response JSON，利於 shell pipeline 與 CI 使用。

## 資源與安全

- 不讀取或寫入既有 `data/post-bubi` H2、`data/protos`、`data/files`。
- ZIP 可含 Environment 密碼；使用者應以檔案權限保護 ZIP。CLI 不得在 stdout、stderr 或 log 回顯 Environment 變數值。
- `--var` 僅存在於單次 process memory，不保存回 ZIP、H2 或 log。
- 指定 ZIP 中 proto 時，僅使用該 ZIP 解壓出的 proto 與其 imports；無 proto ID 時才允許 server reflection。

## 驗證範圍

- `--help` 與參數缺失的 exit code。
- Request、Collection、Workspace ZIP 內一般 gRPC request 均可透過 reflection 執行成功。
- ZIP 內 proto 及 import dependency 可在 server 未啟用 reflection 時執行。
- Environment 與 `--var` 覆寫、未定義變數、同名 Request/Folders 選擇錯誤。
- 非 `GRPC` request、streaming method、惡意 ZIP path 與輸出檔錯誤。
- 以 `java -jar ... run-grpc --help` 與不存在 ZIP 的 smoke test 確認 CLI 不啟動 Web server、可輸出結構化錯誤與正確 exit code。

## 後續階段

- Phase 2：HTTP CLI，包含 multipart archive 檔案。
- Phase 3：gRPC BUR CLI，重用 BUR codec 與 Basic Label 設定。
- Phase 4：Batch CLI，加入 count、concurrency、interval、deadline 與 CSV 結果。

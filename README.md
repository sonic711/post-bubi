# Post Bubi

Post Bubi 是可部署於離線主機、免登入使用的 API 測試工具。後端使用 Java 17 與 Spring Boot，前端使用 Vue 3 與 Vite，正式產物會整合成單一 executable JAR。

目前支援：

- HTTP GET、POST、PUT、PATCH、DELETE
- Query params、multi-value headers、JSON、raw、form-urlencoded、multipart form-data
- gRPC unary，支援 server reflection 或已上傳 proto
- gRPC BUR 固定格式組包、TBConvert 轉碼、payload preview 與 response 解碼
- Collection、Folder、Request 保存、排序、複製與 ZIP 匯入匯出
- Request history、JSON 語法上色、base64 response 欄位解碼
- HTTP、gRPC unary 與 gRPC BUR 預設 30 秒 timeout、可調整並可取消送出中的請求
- 命名 Environment、`{{variable}}` 執行期替換與 ZIP 匯入匯出
- Light / Dark Theme

## 環境需求

- Java 17
- 線上首次建置需要可取得 Gradle、Node.js 與 Yarn 依賴
- 離線建置需先準備 Gradle Maven repository 與 Yarn cache，詳見 [離線建置指南](docs/java-gradle-offline-maven-repo-task.md)

## 專案結構

```text
post-bubi/
  post-bubi-api/       Spring Boot API、資料存取與整合測試
  post-bubi-ui/        Vue 3 工作台與 UI resource JAR
  data/CodeTable/      兩個必要 BUR CodeTable，會打入 executable JAR
  data/TBConvert.jar   BUR 轉碼函式庫，會打入 executable JAR
  data/proto/          gRPC proto 樣本與 import root 測試資料
  gradle/              Gradle wrapper 與離線 Maven repository task
  docs/                規格、進度與專題文件
```

`data/files/`、`data/protos/`、H2 database、logs、build、dist、node_modules 與 IDE 設定都是本機執行資料或可重建產物，不納入 Git。

## 建置與啟動

建立正式 JAR：

```bash
./gradlew clean :post-bubi-api:bootJar
```

產物：

```text
post-bubi-api/build/libs/post-bubi.jar
```

啟動：

```bash
java -jar post-bubi-api/build/libs/post-bubi.jar
```

預設網址：

```text
http://localhost:18080
```

正式部署時，CodeTable 與 TBConvert 已包含在 JAR 內，不需要另外複製。使用者上傳的 proto、Collection 與執行資料會寫在 JAR 執行目錄下的 `data/`。

## 開發指令

完整自動化測試：

```bash
./gradlew :post-bubi-api:test
```

後端開發模式：

```bash
./gradlew :post-bubi-api:bootRun -Pprofile=local
```

前端開發模式：

```bash
cd post-bubi-ui
yarn
yarn dev
```

前端 production build：

```bash
./gradlew :post-bubi-ui:yarn_build_prod
```

## 離線建置

產生 Maven layout 離線依賴包：

```bash
./gradlew zipOfflineMavenRepo
```

使用離線 repository 建置：

```bash
./gradlew --offline \
  -PofflineRepo=/path/to/offline-maven-repo \
  :post-bubi-api:bootJar
```

`data/proto/common/*.proto` 使用 `proto/common/...` import path，因此直接解析樣本檔時 include root 是 `data`。一般使用者可從 UI 上傳 proto，系統會保存到本機 `data/protos/`。

## Log

預設輸出至 JAR 同層：

```text
logs/post-bubi.log
```

關閉檔案 log：

```bash
java -jar post-bubi.jar --post-bubi.logging.file.enabled=false
```

指定位置：

```bash
java -jar post-bubi.jar --post-bubi.logging.file.name=/path/to/post-bubi.log
```

## 文件

- [開發規格](docs/DEVELOPMENT_SPEC.md)：產品範圍、架構、API、資料模型與驗收條件
- [開發進度](docs/DEVELOPMENT_PROGRESS.md)：目前完成狀態、驗證方式與已知限制
- [gRPC BUR 功能](docs/GRPC_BUR_COMPOSER_FEATURE.md)：組包、轉碼、API 與部署細節
- [離線 Maven repository](docs/java-gradle-offline-maven-repo-task.md)：離線依賴包製作與驗證

## Git 規範

- 推送遠端前必須先取得使用者明確同意。
- Commit 訊息必須使用繁體中文並清楚描述異動。
- 不提交 IDE 設定、runtime data、cache、build output、壓縮備份或原始分析 notebook。

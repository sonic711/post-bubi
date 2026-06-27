# Post Bubi

Post Bubi 是離線可部署的 API 測試工具。第一版依照 `docs/DEVELOPMENT_SPEC.md` 開發，優先支援 HTTP 與 gRPC unary 測試。

## 專案結構

```text
post-bubi/
  post-bubi-api/   Spring Boot 後端與最終可執行 JAR
  post-bubi-ui/    Vue 3 + Vite 前端，打包成 UI resource JAR
  data/proto/      gRPC proto 樣本與後續測試資料
  gradle/          Gradle wrapper 與離線 Maven repo task
  docs/            開發規格與參考文件
```

## 前端打包方式

本專案比照 `fsap-admin-api` / `fsap-admin-ui` 的模式：

1. `post-bubi-ui` 使用 Gradle node plugin 執行 yarn build。
2. Vite 輸出到 `post-bubi-ui/dist/post-bubi`。
3. UI Gradle task 將 dist 複製到 `build/prodJar` 或 `build/devJar`。
4. UI JAR 內的靜態資源放在 `public/`。
5. `post-bubi-api` 依 profile 依賴 `post-bubi-ui` 的 UI JAR。
6. `post-bubi-api:bootJar` 產出最終 `post-bubi.jar`。

## 常用指令

線上環境建置：

```bash
./gradlew clean :post-bubi-api:bootJar
```

只啟動後端開發模式，不打包 UI：

```bash
./gradlew :post-bubi-api:bootRun -Pprofile=local
```

前端開發模式：

```bash
cd post-bubi-ui
yarn
yarn dev
```

產生 Gradle 離線 Maven repository：

```bash
./gradlew zipOfflineMavenRepo
```

使用離線 Maven repository 建置：

```bash
./gradlew --offline -PofflineRepo=/path/to/offline-maven-repo :post-bubi-api:bootJar
```

## gRPC proto 注意事項

目前樣本 proto 使用 import path：

```proto
import "proto/common/periphery/message.proto";
import "proto/common/common.proto";
```

實際檔案位於 `data/proto/common/...`，因此後續解析 proto 時 include root 應設為 `data`。

## Git 協作規則

- 未來任何推送到遠端 repository 的動作，都必須先取得使用者明確同意。
- Commit 訊息必須使用繁體中文。
- Commit 訊息應盡可能清楚描述異動內容，避免只寫「更新」、「修正」這類無法判斷範圍的訊息。
- 若一次異動包含多個面向，commit 訊息應摘要主要範圍，例如：`建立 Gradle 多模組專案與 Vue 初始工作台`。

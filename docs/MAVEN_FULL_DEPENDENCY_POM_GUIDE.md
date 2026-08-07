# 產生包含完整已解析套件的 Maven POM 指南

更新日期：2026-07-24

本文件說明 Gradle 專案如何產生 Maven POM，並以「所有已解析 classpath 與 Gradle buildscript Maven 套件都必須在對應 POM 有固定版本」為目標。流程可套用到其他 Gradle Java 專案。

## 1. 先釐清產物定義

Maven 的 POM 有三種容易混淆的形式：

| 產物 | 用途 | 是否列出所有 runtime transitive 套件 |
| --- | --- | --- |
| 發布 POM | 描述直接相依與 BOM import，供 Maven / Gradle consumer 使用 | 否 |
| Effective POM | 展開 parent、property 與 BOM 的版本管理 | 否，會展開版本管理，但不會把所有 transitive 套件改列為直接 dependency |
| Resolved compile POM | 將 Gradle `compileClasspath` 的外部 Maven module 列為固定版本 dependency | 是，compile classpath |
| Resolved runtime POM | 將 Gradle `runtimeClasspath` 的外部 Maven module 列為固定版本 dependency | 是，runtime classpath |
| Resolved test POM | 將 Gradle `testRuntimeClasspath` 的外部 Maven module 列為固定版本 dependency | 是，test classpath |
| Resolved buildscript POM | 彙整 root 與子專案 `buildscript.classpath` 的 Gradle plugin | 是，Maven 類型的 build plugin |

上述四種 resolved POM 共同構成可交付的 Maven 依賴清單；它們適合比對其他專案版本、建立可重現的 Java / Gradle plugin 依賴描述，或交由弱掃工具讀取。

此 POM 是依賴閉包描述，不是來源專案的 Maven build descriptor。它不包含 Gradle task、前端 Vite 流程、annotation processor、測試設定、repository 認證或沒有 Maven 座標的本機檔案。

## 2. 前置條件

- Java 與 Gradle wrapper 可執行。
- 專案已能在 Gradle 下成功解析 `runtimeClasspath`。
- 需要產生 effective POM 時，需安裝 Maven 3.9 以上。
- 首次產生 effective POM 需要能存取專案使用的 Maven repository；後續可在相同 Maven local repository 使用 `-o` 離線執行。
- 本機檔案相依，例如 `implementation files('libs/vendor.jar')`，沒有 group / artifact / version，不能自動寫進 Maven POM；必須先發布到 Maven repository 或另外交付該 JAR。

## 3. 建立標準發布 POM

在要發布的 Gradle 子專案 `build.gradle` 加入：

```groovy
apply plugin: 'java'
apply plugin: 'maven-publish'

publishing {
    publications {
        mavenJava(MavenPublication) {
            from components.java
            pom {
                name = 'Your Project API'
                description = 'Your project description.'
            }
        }
    }
}
```

若是 Spring Boot executable JAR，將 `bootJar` 加入 publication：

```groovy
mavenJava(MavenPublication) {
    from components.java
    artifact tasks.named('bootJar')
}
```

若子專案不是 Java library，例如 Vue 靜態資源 JAR，不要使用 `components.java`；改以實際 JAR task 作為 artifact：

```groovy
publishing {
    publications {
        webAssets(MavenPublication) {
            artifact tasks.named('prodJar')
        }
    }
}
```

執行標準 POM task：

```bash
./gradlew :your-module:generatePomFileForMavenJavaPublication
```

Gradle 預設輸出位置：

```text
your-module/build/publications/mavenJava/pom-default.xml
```

## 4. 產生 Effective POM，展開 Spring BOM

先將標準 POM 複製或集中到固定目錄。以下假設檔案是 `build/maven-poms/your-module/pom.xml`：

```bash
mvn -f build/maven-poms/your-module/pom.xml \
  help:effective-pom \
  -Doutput="$PWD/build/maven-poms/your-module/effective-pom.xml"
```

Windows PowerShell：

```powershell
mvn -f build/maven-poms/your-module/pom.xml `
  help:effective-pom `
  "-Doutput=$PWD/build/maven-poms/your-module/effective-pom.xml"
```

`effective-pom.xml` 會把 `spring-boot-dependencies` 等 BOM 的版本管理展開。例如原本未指定版本的 `spring-boot-starter-web`、H2，會出現實際版本。

首次成功後可離線執行：

```bash
mvn -o -f build/maven-poms/your-module/pom.xml \
  help:effective-pom \
  -Doutput="$PWD/build/maven-poms/your-module/effective-pom.xml"
```

注意：Gradle cache 與 Maven local repository 是兩種不同格式。即使 Gradle 已可離線建置，Maven 仍可能因缺少 BOM POM 或 Help Plugin 而無法離線產生 effective POM。

## 5. 產生 compile、runtime、test 與 buildscript POM

將本專案的 [`gradle/resolved-runtime-pom.gradle`](../gradle/resolved-runtime-pom.gradle) 複製到目標專案，例如：

```text
your-project/
  build.gradle
  gradle/
    resolved-runtime-pom.gradle
```

在要輸出 compile、runtime、test POM 的 Java 子專案 `build.gradle` 加入：

```groovy
apply from: "${rootProject.projectDir}/gradle/resolved-runtime-pom.gradle"
```

執行：

```bash
./gradlew :your-module:generateResolvedCompilePom
./gradlew :your-module:generateResolvedRuntimePom
./gradlew :your-module:generateResolvedTestPom
```

Windows PowerShell：

```powershell
.\gradlew.bat :your-module:generateResolvedCompilePom
.\gradlew.bat :your-module:generateResolvedRuntimePom
.\gradlew.bat :your-module:generateResolvedTestPom
```

產物位置：

```text
your-module/build/maven-poms/your-module/resolved-compile-pom.xml
your-module/build/maven-poms/your-module/resolved-runtime-pom.xml
your-module/build/maven-poms/your-module/resolved-test-pom.xml
```

三份 classpath POM 的 `<dependencies>` 會列出對應 Gradle configuration 中每個已解析的外部 Maven module 的 `groupId`、`artifactId`、固定 `version`、必要時的 `type` 與 `classifier`，並依序使用 `compile`、`runtime`、`test` scope。它們不再依賴 Spring BOM 才能知道版本。Gradle project dependency 不會列入這些 POM，因為它們應由同一份原始碼中的 Gradle 子專案建置；若要提供給外部 Maven consumer，必須先將每個內部模組各自發布到 Maven repository。

在 root `build.gradle` 套用 `gradle/resolved-buildscript-pom.gradle` 後，可執行：

```bash
./gradlew generateResolvedBuildscriptPom
```

它會產生 `build/maven-poms/resolved-buildscript-pom.xml`，列出所有 Maven 類型 Gradle buildscript plugin。Post Bubi 可一次產生四份 POM：

```bash
./gradlew generateAllResolvedDependencyPoms
```

## 6. 驗證最終 POM

### 6.1 驗證 XML 與 Maven model

```bash
mvn -f build/maven-poms/resolved-buildscript-pom.xml validate
mvn -f your-module/build/maven-poms/your-module/resolved-compile-pom.xml validate
mvn -f your-module/build/maven-poms/your-module/resolved-runtime-pom.xml validate
mvn -f your-module/build/maven-poms/your-module/resolved-test-pom.xml validate
```

若 Maven local repository 已備妥，可加上 `-o` 確認驗證過程不會對外下載。四份 POM 都應可通過 `validate`，且每個 `<dependency>` 都必須有固定的 `<version>`。

### 6.2 比對 Gradle runtime 依賴樹

```bash
./gradlew :your-module:dependencies --configuration runtimeClasspath \
  > your-module/build/maven-poms/your-module/runtime-dependency-tree.txt
```

比較 `runtime-dependency-tree.txt` 與 `resolved-runtime-pom.xml`，確認應交付的 Maven module 都存在。若同一 group / artifact 有多個版本，Gradle 會使用解析後的單一版本；最終 POM 也會固定該版本。

### 6.3 需要發布內部子模組時

若 runtime classpath 內含其他子專案，先發布全部子模組：

```bash
./gradlew publishToMavenLocal
```

再讓 Maven consumer 使用相同 Maven local repository 或公司 Nexus repository。Post Bubi 的 Maven local 由使用者 `settings.xml` 設定為 `/Users/sonic711/repository`；其他環境請以自己的 Maven `settings.xml` 為準。

## 7. Post Bubi 實際指令

```bash
./gradlew generatePomXml
./gradlew generateAllResolvedDependencyPoms
mvn -o -f build/maven-poms/resolved-buildscript-pom.xml validate
mvn -o -f post-bubi-api/build/maven-poms/post-bubi-api/resolved-compile-pom.xml validate
mvn -o -f post-bubi-api/build/maven-poms/post-bubi-api/resolved-runtime-pom.xml validate
mvn -o -f post-bubi-api/build/maven-poms/post-bubi-api/resolved-test-pom.xml validate
```

Post Bubi 產物：

```text
build/maven-poms/pom.xml
build/maven-poms/post-bubi-api/pom.xml
build/maven-poms/post-bubi-api/effective-pom.xml
build/maven-poms/resolved-buildscript-pom.xml
post-bubi-api/build/maven-poms/post-bubi-api/resolved-compile-pom.xml
post-bubi-api/build/maven-poms/post-bubi-api/resolved-runtime-pom.xml
post-bubi-api/build/maven-poms/post-bubi-api/resolved-test-pom.xml
```

Post Bubi 的 `TBConvert.jar` 是本機檔案相依，會被 Spring Boot executable JAR 內嵌，但不會出現在 `resolved-runtime-pom.xml`。若其他專案也有同類依賴，請為它建立 Maven 座標後發布，或在交付清單中明確附上該 JAR。

## 8. 使用時的限制與建議

- 不要以 `resolved-runtime-pom.xml` 取代原始專案的 `pom.xml` 來建置原始碼；它只描述已解析 runtime 套件。
- 不要手動把 BOM 的全部 artifact 複製到原始發布 POM；會失去 BOM 統一管理與版本升級能力。
- 需要可追蹤授權、CVE 與完整元件識別時，應另外產生 CycloneDX SBOM；POM 不是完整軟體物料清單格式。
- 要在真正離線的 Maven 環境驗證 effective POM 或 consumer build，除 Gradle 離線包外，也必須把 Maven 的 BOM、plugin 與相關 POM / JAR 一併預先快取或發布到內部 repository。

## 9. 終端到端驗證結論：不可只靠 `.m2` 建置 Gradle 專案

`resolved-runtime-pom.xml` 只能讓 Maven 下載應用程式的外部 runtime 套件。`resolved-buildscript-pom.xml` 可補上 Maven 類型的 Gradle plugin，但四份 POM 仍不能單獨建立可供全新 Gradle user home 離線打包的環境。

2026-07-26 已以全新的 Maven local repository 驗證：先依四份 POM 從 Maven Central 與 Gradle Plugin Portal 下載套件，再以全新的 `GRADLE_USER_HOME`、已安裝的 Gradle 8.13 與 `--offline -Dmaven.repo.local=<temporary-m2>` 執行 `:post-bubi-api:bootJar --rerun-tasks`。補入 Plugin DSL marker `com.github.node-gradle.node:com.github.node-gradle.node.gradle.plugin:7.1.0` 的 POM 後，建置繼續至前端 Node plugin，但因 `org.nodejs:node:18.17.0` 不在 Gradle cache 而失敗。Node distribution 由 Gradle Node plugin 的專用解析流程取得，並非本專案 POM 可完整表達的 Maven runtime module。

```text
org.springframework.boot:spring-boot-gradle-plugin:3.3.6
io.spring.gradle:dependency-management-plugin:1.1.6
com.github.node-gradle.node:com.github.node-gradle.node.gradle.plugin:7.1.0
```

即使額外建立 buildscript POM 並下載上述 Maven plugin，前端專案仍需要下列非 Maven 資源：

- Gradle wrapper distribution，例如 Gradle 8.13。
- Node.js 與 Yarn 執行檔。
- `yarn.lock` 對應的 npm packages 或 Yarn offline mirror。

因此「只用 Maven POM 填滿 `.m2`，再用全新 Gradle 完整離線打包」不適用於含 Node／Yarn 前端的 Gradle 專案。正確做法是：

1. 將本文件的 resolved runtime POM 用於 runtime 套件清單、版本比對或 Maven consumer。
2. 使用專案的 Gradle 離線 repository 流程取得 Gradle plugin、Java runtime 與 metadata：[`java-gradle-offline-maven-repo-task.md`](java-gradle-offline-maven-repo-task.md)。
3. 另行交付 Gradle distribution、Node / Yarn distribution 與 Yarn offline mirror，或預先交付已打包的 UI resource JAR。
4. 在真正乾淨主機以 `GRADLE_USER_HOME`、Maven local repository、Node workDir 與 Yarn mirror 都指向交付內容後，執行 `./gradlew --offline :post-bubi-api:bootJar`。

# BuBu 車庫

BuBu 是供臺灣自用燃油汽車與機車使用的離線優先 Android App。正式版本為 **v1.0.0**；Phase 0–7 的功能與 Release Readiness 品質閘門已完成。

## 已完成功能

- 新增多台自用汽車或機車，並保存於本機 Room 資料庫
- 編輯名稱、車種、機車級別、廠牌、型號、年份、排氣量、車牌、液體燃料動力類型、追蹤起始資料與備註
- 以穩定 UUID 在 Preferences DataStore 保存目前車輛選擇
- 封存、解除封存，以及永久刪除尚無關聯資料的車輛
- 新增、編輯與刪除加油紀錄，並以 ml、milli-TWD 與 TWD 整數保存油量和金額
- 公升數、每公升單價、總金額任填兩項即精確計算第三項；人工／自助加油與每台車偏好會獨立保存
- 新增、編輯與刪除保養／維修工單；常用或自訂項目先以名稱、項目金額與備註確認後才加入，項目金額即小計
- 管理汽車／機車分開的內建與自訂常用服務類型；項目選擇先顯示快速項目，再展開更多項目
- 工單支援多張 App 私有 JPEG、PNG 與 WebP 附件，可預覽、刪除並在檔案失敗時補償清理
- 服務項目的下次里程／日期提醒關聯與完成鏈會隨工單交易一致更新
- 新增、編輯與刪除其他支出紀錄
- 依追蹤起始值、加油與服務紀錄的最大里程重建目前里程；補登舊紀錄不會降低目前里程
- 單車統一時間軸支援 Fuel／Service 混合排序、類型、關鍵字與本地日期範圍篩選，並可直接編輯來源紀錄
- 報表支援車輛／日期範圍摘要、月支出、油耗、每公里成本、服務花費、支出類別與累積里程趨勢
- 提醒中心支援服務、手動、臺灣法定稅費／驗車與里程預估提醒，含完成、延後與狀態顯示
- 本機通知支援 Android 13+ 權限、WorkManager 排程、狀態去重與停用後清理
- 法定提醒依臺灣官方規則建立牌照稅、公路使用養護安全管理費與定期驗車日期，不推算金額
- 匯出供 Excel 閱讀的 CSV ZIP 報表，採 UTF-8 BOM、RFC 4180 quoting 與公式注入防護；CSV 不可反向匯入或還原
- 手動建立完整 `.bubu` 備份，包含紀錄、提醒狀態與引用附件，並以格式版本、檔案大小與 SHA-256 驗證
- 還原前先驗證及預覽備份，再完全覆蓋；操作前會建立最新一份 App 私有復原備份，可另行匯出或刪除
- 可開關每月手動備份提醒；它只保存為 App 偏好，不會被備份或還原，也不會自動寫入檔案
- Material 3 深色／淺色主題與首頁、報表、設定三個頂層導覽頁
- 完全離線，無帳號、遠端服務、廣告或追蹤 SDK

## 技術版本

| 元件 | 版本 |
| --- | --- |
| App | 1.0.0 (versionCode 1) |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |
| Android Gradle Plugin | 9.3.1 |
| Gradle Wrapper | 9.6.1 |
| JDK / Gradle JVM | Android Studio embedded JBR 25.0.2 |
| Kotlin / Compose Compiler plugin | 2.4.10 |
| KSP | 2.3.10 |
| Compose BOM | 2026.06.01 |
| Activity Compose | 1.13.0 |
| Navigation Compose | 2.9.8 |
| Lifecycle | 2.11.0 |
| Room | 2.8.4 |
| Preferences DataStore | 1.2.1 |
| WorkManager | 2.11.2 |

AGP 9 使用內建 Kotlin，因此專案不套用 `org.jetbrains.kotlin.android`。所有可升級版本集中在 `gradle/libs.versions.toml`。

## 環境需求

1. Android Studio embedded JBR 25.0.2（`C:\Program Files\Android\Android Studio\jbr`）
2. Android SDK Platform 37
3. Android SDK Build-Tools 37.0.0
4. Android SDK Platform-Tools
5. 執行儀器測試時，需有 API 26 以上的 emulator 或實體裝置

以 Android Studio 開啟專案時，將 Gradle JDK 設為 **Embedded JDK**（目前為 JBR 25.0.2），並確保 `local.properties` 的 `sdk.dir` 指向本機 Android SDK。若從終端執行，請將 `JAVA_HOME` 指向 Android Studio 的 `jbr` 目錄，並將 `ANDROID_SDK_ROOT` 指向本機 Android SDK；設定後需重新開啟終端。`local.properties` 不納入版本控制。

Release 驗證時一次只可有一個 Gradle build 寫入本工作區。先執行 `./gradlew.bat --stop`，並等待 Android Studio 的 build/sync 完成後，再執行下列指令；不得與 Android Studio 或另一個終端的 Gradle build 並行，以避免同時寫入 `app-debug.apk`。

## 驗證指令

```powershell
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

安裝並啟動 Debug APK：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -W -n com.kumo.bubu/.MainActivity
```

## 專案結構

```text
app/src/main/java/com/kumo/bubu/
├── core/          # Room 建立、應用程式容器與共用 UI
├── data/          # Room Entity、DAO、mapper、私有附件與離線 repository
├── domain/        # 車輛、加油、服務與支出 model／repository 介面
├── feature/       # 車輛、加油、服務、支出與頂層畫面
├── navigation/    # 頂層 route 與 NavHost
├── BuBuApplication.kt
└── MainActivity.kt
```

`Spec.md` 是主要規格來源，`CONTEXT.md` 定義專案用語，架構決策記錄於 `docs/adr/`。若其他文件與 `Spec.md` 衝突，以 `Spec.md` 為準。

## 範圍邊界

目前資料庫包含車輛、加油、服務工單／項目／常用類型、其他支出、服務提醒關聯、法定提醒、里程預估與工單圖片附件。滿箱油耗、統一時間軸、報表、提醒中心、本機通知、CSV 報表 ZIP、完整 `.bubu` 備份與完全覆蓋還原已實作；資料不足情境的里程預估驗收依本輪範圍暫緩。Drivvo 匯入待另立規格。

# BuBu Phase 0–2 任務清單

## Google Drive Cloud Backup enhancement

**狀態：** 進行中（2026-08-25；雲端備份、還原、手動刪除與正式版簽署已完成；單一 Google 帳號的附件、五份保留與解除再連結實機驗收尚待完成）

### CB-01：Google Cloud 設定與憑證安全文件

**狀態：** 已完成（2026-08-25；實際 client ID 與正式版簽署已完成；單一帳號的完整實機驗收待 CB-12）

**前置任務：** 無 — 可立即開始

**任務目標：** 讓開發與發布人員能安全設定 BuBu 的 Google Drive appDataFolder 存取，而不把 OAuth secret、keystore 密碼或 token 放入 Git。

**完成條件：**

- 新增 Google Cloud Project、Drive API、OAuth consent screen、Android OAuth client、Debug／Release SHA-1 與測試／正式發布注意事項。
- 明確記錄 package name `com.kumo.bubu`、`drive.appdata` 與狹義 `drive.file` scope、以及實機驗收所需的非敏感 client ID 設定方式。
- 文件說明不建立 BuBu 帳號、Firebase 或自架後端，且不提交任何長期 token 或 secret。

### CB-02：Google 帳號識別與 Drive 授權連結

**前置任務：** CB-01

**任務目標：** 使用者可連結自己的 Google 帳號、看到已連結帳號，並能重新授權或解除連結，而不要求完整 Drive 權限。

**完成條件：**

- 使用 Credential Manager 取得帳號識別，並以 AuthorizationClient 要求 `drive.appdata` 與狹義 `drive.file`，不要求完整 Drive 權限。
- 不保存 access token 或 refresh token；授權失效可轉為 NeedsAuthorization 而不清除手機資料。
- 解除連結會撤回 BuBu 的 Google 授權、清除本機連結資訊但保留 appDataFolder 備份檔。

### CB-03：appDataFolder 雲端備份資料層

**前置任務：** CB-02

**任務目標：** BuBu 可透過獨立的 CloudBackupRepository 存取自己的 appDataFolder，並將 Drive metadata 映射為不洩漏 API model 的 Cloud Backup 模型。

**完成條件：**

- 支援取得授權狀態／帳號、上傳、列出、下載與永久刪除 BuBu 建立的雲端備份。
- 只查詢 appDataFolder 的 BuBu `.bubu` 檔案，並使用 app properties 保存 format version、app version、建立時間與資料摘要。
- Drive API 在 data 層，既有 BackupRepository 不承擔 Drive transport；可在測試中替換 Drive API client。

### CB-04：設定頁 Google Drive 連線狀態

**前置任務：** CB-02、CB-03

**任務目標：** 設定頁在本機備份與還原保留原功能的前提下，提供 Google Drive 未連線、連線中、已連線與重新授權的明確 UI。

**完成條件：**

- 未連線時顯示連結入口；已連線時顯示帳號、最後雲端備份時間與可用操作。
- UI 使用 immutable StateFlow 狀態，Composable 不直接操作 Google API。
- 所有新使用者可見文字位於 Android string resources，採用現有 Material 3 主題。

### CB-05：手動立即 Google Drive 備份

**前置任務：** CB-03、CB-04

**任務目標：** 已連結的使用者可手動建立既有格式的 `.bubu`、先完成既有 validation，再上傳自己的 appDataFolder。

**完成條件：**

- 雲端檔名完全沿用本機 `bubu-backup-yyyy-MM-dd-HHmmss.bubu` 格式。
- 成功後更新 lastCloudBackupAt 與摘要，並顯示「Google Drive 備份完成」。
- 本機產生、驗證、網路或上傳失敗皆顯示具體原因，且不影響手機資料或既有雲端備份。

### CB-06：雲端備份保留策略

**前置任務：** CB-05

**任務目標：** 每次新備份完整上傳成功後，BuBu 自動只保留最新五份自己的雲端備份。

**完成條件：**

- 保留數量為可集中調整的常數或設定值，清單依建立時間由新到舊排序。
- 僅在新檔成功上傳後才永久刪除超額的最舊檔案；新檔失敗時不刪除任何既有檔案。
- 對 appDataFolder 不可 trash 的限制使用永久刪除 API，且僅針對 BuBu metadata 符合的檔案。

### CB-07：雲端備份清單

**前置任務：** CB-03、CB-04

**任務目標：** 已連結的使用者可由設定頁查看自己的雲端備份，並依時間新到舊閱讀每份備份的摘要。

**完成條件：**

- 每筆顯示日期時間、App version、檔案大小、車輛、加油及保養／維修工單數量。
- 僅顯示 BuBu 自己建立且 metadata 完整的 `.bubu` 檔案；UI 不接收 Google Drive File model。
- 清單具 loading、空白、重新整理與錯誤狀態。

### CB-08：雲端下載與既有驗證橋接

**前置任務：** CB-07

**任務目標：** 選擇雲端備份時，BuBu 先下載到私有暫存空間，再交給既有 `.bubu` validation 與 Restore Preview 核心。

**完成條件：**

- 下載失敗或 validation／formatVersion 不支援時，禁止進入還原確認並顯示明確原因。
- 暫存檔以受控生命週期保留至預覽／確認結束後清除。
- 不重寫 archive reader、備份驗證或 Restore Preview 資料摘要。

### CB-09：雲端備份還原整合

**前置任務：** CB-08

**任務目標：** 使用者可從已驗證的雲端備份預覽，確認後走既有復原備份、完全覆蓋還原與 rollback 流程。

**完成條件：**

- 使用現有 Restore Preview UI，點選雲端備份不會立即覆蓋手機資料。
- 確認還原前建立目前資料的 Recovery Backup；還原失敗時沿用既有復原機制。
- 還原完成或取消後清除雲端下載的暫存檔。

### CB-10：離線、錯誤與授權失效狀態

**前置任務：** CB-05、CB-07、CB-08、CB-09

**任務目標：** 使用者在沒有網路、授權失效或 Drive API 發生錯誤時，得到可行動的明確訊息而非 crash 或籠統錯誤。

**完成條件：**

- 離線設定頁仍可開啟；備份與清單分別顯示離線狀態及重新整理／重試入口。
- 授權錯誤將 Cloud Backup state 轉為 NeedsAuthorization，不清除本機資料或雲端清單資訊。
- 上傳、下載、驗證與刪除錯誤依使用者可理解的原因映射到 UI。

### CB-11：雲端備份測試覆蓋

**前置任務：** CB-02 至 CB-10

**任務目標：** 不連線 Google 真實帳號，即可自動驗證雲端備份的核心規則與設定頁使用流程。

**完成條件：**

- Unit tests 覆蓋保留最新五份、Drive metadata mapping、authorization state、雲端錯誤 mapping、清單排序與不支援格式。
- Compose instrumentation tests 覆蓋未連線／已連線、備份 loading／成功／失敗、雲端清單與還原確認。
- 測試只在 Drive API 邊界使用 fake／mock，不連線 Google。

### CB-12：雲端備份品質閘門與實機驗收

**前置任務：** CB-01 至 CB-11

**任務目標：** 在 Google Cloud 設定完成後，以真實帳號確認雲端備份、還原與附件，並完成本 enhancement 的品質檢查。

**完成條件：**

- 執行 clean、Debug APK、JVM unit tests、Android test APK、相容裝置的 connected tests 與 lint，修正本次變更造成的問題。
- 以單一 Google 帳號驗證上傳、下載、預覽、還原、附件、最新五份保留與解除再連結。
- 使用 code-review 僅審查本次 Google Drive Cloud Backup enhancement；確認後才考慮將 versionName 升為 1.1.0。

## Dashboard title, report and settings UX refinement

**狀態：** 已完成（2026-08-23；Debug APK、JVM unit tests、Android test APK、API 35 完整 61 項 instrumentation tests 與 lint 全部通過）

### 任務目標

將首頁左上角品牌標題由「BuBu 車庫」縮為「BuBu」，修正設定頁可用性，並將報表整理為單一車輛、近六個月預設的花費與油耗摘要。

### 完成條件

- 首頁只改變標題文字；通知圖示、車輛卡片、最近紀錄與 Bottom Navigation 均維持原有行為與樣式。
- 報表只依目前選取的一台未封存車輛查詢；車輛切換區可水平滑動，不同車輛不會再混入同一份統計。
- 預設期間為近 6 個月，可切換近 12 個月、今年或自訂日期；本期花費、月均與月趨勢優先顯示，並可切換全部、加油或保養／維修。
- 油耗以最近最多 8 筆有效滿箱區段呈現；有兩筆才畫折線，單筆改顯示目前油耗與資料不足提示，避免孤立資料點。
- 設定頁改為車輛、提醒、資料與備份三組；內容可垂直捲動至底部，固定 Bottom Navigation 不遮住最後操作；復原備份只顯示可讀日期與大小，不暴露 UUID 檔名。
- 已移除正式 UI 與 debug build 中已無用途的舊測試資料匯入流程；既有資料與所有 CSV、備份、還原流程維持不變。
- Compose 測試覆蓋單車切換、單筆油耗空狀態、期間自訂、花費分類切換、設定分組、匯出入口與移除舊匯入入口。

---

## Service item confirmation and form IME usability

**狀態：** 已完成（2026-08-15；實機 UI 測試套件既有 Compose activity 啟動失敗，詳見驗證紀錄）

### 任務目標

將保養／維修常用項目改為先以 Bottom Sheet 確認名稱、項目金額與備註後才加入工單；已存在項目再次選取時直接開啟相同編輯器，不建立重複項目。移除所有服務項目數量／單位 UI 與表單運算，以「項目金額即小計」組成工單總額。同時修正所有輸入表單在 IME 顯示時的捲動與 focused field 可視性。

### 準備修改的檔案

- `app/src/main/java/com/kumo/bubu/core/ui/components/BringIntoViewOnFocus.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormState.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceItemsSection.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceWorkOrderCard.kt`
- `app/src/main/java/com/kumo/bubu/feature/fuel/FuelFormScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehicleFormScreen.kt`
- `app/src/main/res/values/strings.xml`
- 對應 Service ViewModel 與 Compose UI 測試

### 完成條件

- 選擇常用或自訂項目只開啟編輯 Sheet；按完成、且名稱與金額有效後才提交。既有項目再次選取只編輯該項目，取消不改寫既有資料。
- UI state 不再包含數量、數量單位、數量輸入驗證或數量乘法；儲存仍以既有 schema 的固定 `quantityMilli = 1000`、`PIECE` 與 `unitPriceTwd = subtotalTwd = 項目金額` 保存，無資料庫遷移。
- 所有指定表單使用單一、非重複的 IME inset 策略；focused TextField 會帶入可見區域，內容可捲動，底部動作列不產生異常空白。
- 不變更 Room schema、既有服務項目的資料可讀性、提醒模型、報表、匯入或其他 Phase 功能。

### 驗證指令

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
```

---

## Phase 5：報表

**狀態：** 已完成（2026-08-14；clean、debug／Android test APK、JVM 單元測試與 lint 通過；實機上的新報表 Compose 測試與既有 Dashboard 類別皆通過。完整套件重跑曾因裝置缺少 `androidx.test.services` UID 而有 Compose hierarchy 暫態失敗，獨立重跑已通過。）

### 任務目標

提供可依車輛與日期範圍重建的報表：支出摘要、分類比較、月支出、平均油耗、每公里成本、服務花費、支出類別及累積里程趨勢；每個圖表資料點都保留可導向既有來源紀錄的識別。

### 準備修改的檔案

- `app/src/main/java/com/kumo/bubu/domain/model/Report.kt`
- `app/src/main/java/com/kumo/bubu/domain/repository/ReportRepository.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ReportDao.kt`
- `app/src/main/java/com/kumo/bubu/data/repository/OfflineReportRepository.kt`
- `app/src/main/java/com/kumo/bubu/core/database/BuBuDatabase.kt`
- `app/src/main/java/com/kumo/bubu/core/database/AppContainer.kt`
- `app/src/main/java/com/kumo/bubu/feature/reports/ReportsViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/reports/ReportsScreen.kt`
- `app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt`
- `app/src/main/res/values/strings.xml`
- 對應報表 domain／ViewModel 與 Compose instrumentation 測試

### 完成條件

- 可選擇單一車輛或全部啟用車輛，並支援本月、今年、最近 12 個月、全部及自訂日期範圍。
- 顯示本月／本年／範圍總支出，並分開顯示燃料、保養、維修、牌照稅、公路養管費、保險與其他支出。
- 月支出、保養／維修花費、支出類別與累積里程以可讀的 Material 3 圖表／文字資料點呈現；平均油耗只使用有效滿箱區段，每公里成本只在資料足夠時顯示。
- 多車總支出清楚標示為合計；油耗和每公里成本不跨車混算。空資料、零除或不足資料不顯示 0、NaN 或誤導性的圖表。
- 成本類彙總由限制車輛與日期範圍的 Room SQL 查詢取得；Composables 不直接使用 DAO 或進行商業過濾。
- 不新增 Room schema、提醒、CSV、備份、同步、帳號、雲端、廣告或其他 Phase 功能。

### 驗證指令

```powershell
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
```

---

## Maintenance work-order UX refactor

**狀態：** 已完成（2026-08-14；clean、debug APK、Android test APK、JVM 單元測試、lint，以及實機 instrumentation 測試皆通過；不啟動或拆解新的 Phase）

### 任務目標

將新增／編輯保養／維修工單改為「基本資料、工單類型、常用項目快速選取、已加入項目、唯讀總額、標題、備註、附件、儲存」的精簡流程。常用項目的排序、隱藏／顯示與自訂類型管理移至設定中的保養／維修設定；既有 `ServiceRecord`、`ServiceItem`、付款方式與提醒欄位資料維持可讀可寫，不進行資料庫遷移。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormState.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceWorkOrderCard.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceItemsSection.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceTypePickerSheet.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceTypeManagementScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceTypeManagementViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/settings/SettingsScreen.kt`
- `app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt`
- `app/src/main/res/values/strings.xml`
- 對應服務表單、常用項目管理的單元與 Compose instrumentation 測試

### 完成條件

- 付款方式不再出現在工單 UI、表單 UI state 或驗證；編輯既有工單時原付款方式資料保留，新增工單不寫入付款方式。
- 常用項目以具圖示、圓角、可快速點選的小型雙欄卡片或 Chip 顯示；點選立即新增一個數量為 1 的項目，不跳轉編輯器、不重複新增，並給予已加入／已存在提示。
- 已加入項目以緊湊卡片顯示名稱、數量、單價與小計；只有明確點擊編輯才開啟項目編輯 Sheet。編輯器不顯示下次保養里程／日期，並以唯讀小計避免與單價衝突。
- 工單總額為所有有效項目小計加總，顯示於項目清單下方；標題、備註、附件與儲存依指定順序排列。
- 設定可依序進入保養／維修設定與常用項目管理；內建類型只能排序或隱藏，自訂類型可新增、改名、排序、封存／顯示，不在建立工單流程暴露管理操作。
- 不變更 Room schema，不刪除既有資料欄位，不建立提醒、報表、同步、帳號或其他未要求功能，也不開始新的 Phase。

### 驗證指令

```powershell
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
```

---

## Vehicle history navigation and fuel-price interaction refactor

**狀態：** 已完成（2026-08-14；實作、單元測試、debug APK、Android test APK、37 項 connected instrumentation 測試與 lint 均已完成。依使用者明確指派執行，屬既有功能的範圍限定 refactor）

### 任務目標

讓首頁的每台車輛卡片開啟該車的統一歷史紀錄，而車輛編輯只從設定中的車輛管理進入；在同一個單車歷史頁以篩選與關鍵字搜尋混合顯示加油與服務工單，並使新增加油在選擇 92／95／98 無鉛時立即帶入對應的當日中油人工牌價，且不覆蓋使用者手動輸入的單價。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt`
- `app/src/main/java/com/kumo/bubu/feature/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/history/VehicleHistoryScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/history/VehicleHistoryViewModel.kt`
- `app/src/main/java/com/kumo/bubu/domain/model/FuelEconomy.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceRecordDao.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceItemDao.kt`
- `app/src/main/java/com/kumo/bubu/data/repository/OfflineServiceRepository.kt`
- `app/src/main/java/com/kumo/bubu/domain/repository/ServiceRepository.kt`
- `app/src/main/java/com/kumo/bubu/feature/fuel/FuelFormState.kt`
- `app/src/main/java/com/kumo/bubu/feature/fuel/FuelFormViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/fuel/FuelFormScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/kumo/bubu/feature/history/VehicleHistoryViewModelTest.kt`
- `app/src/test/java/com/kumo/bubu/feature/fuel/FuelFormViewModelTest.kt`
- `app/src/androidTest/java/com/kumo/bubu/feature/dashboard/DashboardScreenTest.kt`
- 對應的單元與 Compose instrumentation 測試

### 完成條件

- Vehicle Card 本體進入該車輛歷史頁，卡片的「＋」仍只開啟新增加油／新增保養維修選擇；底部導航只保留首頁、報表、設定。
- 單車歷史頁顯示名稱、車牌與目前里程，將加油與服務工單依本地日期、選填時間、同日順序混合由新到舊排序；每種卡片顯示本任務指定的摘要，點擊可編輯，刪除有確認。
- 篩選「全部／加油／保養／維修」與忽略大小寫、會 trim 的關鍵字搜尋可同時作用；過濾在 ViewModel 層完成，UI 不接收 Room Entity。
- 搜尋只涵蓋目前可持久化的欄位：油品、加油備註、工單標題、服務項目名稱與工單備註；加油站與店家尚未納入資料模型，不能假裝可搜尋。
- 開啟新增加油表單會讀取每車上次油品並立即嘗試帶入今日對應中油人工牌價；重新選擇 92／95／98 或按「重新取得油價」才可自動重帶。取得失敗不填入 0，仍可手動輸入並保存；最近有效價格必須明確標示為最近價格。
- 使用者手動輸入單價後，畫面重組、日期或其他欄位變更不可覆蓋該值；既有兩欄推算第三欄的精確計算規則維持不變。

### 驗證指令

```powershell
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
```

### 禁止提前實作的內容

- 不變更 Room schema，不新增加油站、店家、付款、提醒、報表、CSV、備份、雲端、帳號或權限。
- 不變更其他 Phase 的既有任務與完成紀錄；本 ticket 的 Phase 4 對應交付由 P4-01 追蹤。

---

## P4-01：單車統一時間軸基礎

**狀態：** 已完成（2026-08-14；使用者明確授權開始並完成 P4-01。37 項 connected instrumentation 測試全數通過，lint 為 0 errors、11 個既有警告）

### 任務目標

將既有的單車加油與服務工單歷史頁納入 Phase 4 的第一個可驗收垂直切片：首頁車輛卡片進入該車歷史頁，依本地日期、選填時間與同日順序以統一時間軸顯示 Fuel Record 與 Service Record，並提供類型篩選、關鍵字搜尋、編輯與刪除互動。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt`
- `app/src/main/java/com/kumo/bubu/feature/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/history/VehicleHistoryScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/history/VehicleHistoryViewModel.kt`
- `app/src/main/java/com/kumo/bubu/domain/model/FuelEconomy.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceRecordDao.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceItemDao.kt`
- `app/src/main/java/com/kumo/bubu/data/repository/OfflineServiceRepository.kt`
- `app/src/main/java/com/kumo/bubu/domain/repository/ServiceRepository.kt`
- 對應單元與 Compose instrumentation 測試

### 完成條件

- 所有未封存車輛都可從 Dashboard 開啟自身歷史；歷史頁不加入 Bottom Navigation。
- Fuel Record 與 Service Record 混合穩定排序，顯示指定摘要；篩選、忽略大小寫且 trim 的搜尋可同時作用。
- Composable 不處理複雜篩選、不持有 Room Entity；點擊或更多選單可編輯，刪除需確認且清單立即更新。
- 本切片不擴充 Room schema、加油站、店家、其他支出、日期範圍、報表、提醒、雲端或帳號；其餘 Phase 4 範圍以後續 ticket 逐一拆解。

### 驗證指令

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
```

---

## P4-02：首頁近期紀錄

**狀態：** 已完成（2026-08-15；Dashboard 最近 Fuel／Service 紀錄混合排序、車輛歷史導向與實機 Compose 測試通過）

**前置任務：** P4-01

### 任務目標

讓首頁在車輛卡片之外，能以統一摘要顯示最近的加油與服務工單，並可直接前往該筆紀錄所屬車輛的歷史頁；不另建唯讀詳情頁，歷史卡片仍直接進既有編輯頁。

### 完成條件

- 最近紀錄混合 Fuel Record 與 Service Record，穩定依日期、選填時間與同日順序由新到舊排序。
- 每筆摘要標示車輛與紀錄類型，點擊後開啟該車歷史頁。
- 空白、載入與失敗狀態有清楚的 Material 3 畫面，篩選與排序在 ViewModel／domain 層處理。
- 不修改 Room schema，不新增提醒、匯出、備份、雲端、帳號或其他 Phase 功能。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

---

## P4-03：單車歷史日期範圍與 Phase 4 品質閘門

> 2026-08-16 驗證註記：Android 16 實機仍有 Compose test host 在 `setContent` 後失去 hierarchy 的裝置相容性限制；改以 API 35 Google APIs x86_64 模擬器完成完整儀器驗收，所有測試均通過（0 failures、0 errors）。

**狀態：** 已完成（2026-08-16；日期範圍與既有類型／搜尋組合的 ViewModel 測試已補齊開放端點與起迄倒置邊界；`clean`、Debug APK、JVM tests、Android test APK、完整 41 項 compatible-device instrumentation 與 lint 均通過。Standards／Spec 雙軸審查沒有未處理的確認缺陷；已移除未使用的 History placeholder，且本任務未變更 Phase 5 功能。）

**前置任務：** P3-07、P4-02

### 任務目標

完成統一時間軸的日期範圍篩選，與既有類型篩選、搜尋同時生效；直接使用既有 Edit Fuel／Edit Maintenance 作為紀錄操作頁，最後以完整建置、實機驗證與審查關閉 Phase 4。

### 完成條件

- 使用者可設定起訖本地日期；空白端點代表不設限，起日不可晚於迄日。
- 日期範圍、類型篩選與關鍵字搜尋同時作用，複雜條件在 ViewModel／domain 層處理。
- 歷史紀錄點擊直接開啟既有編輯頁，不建立唯讀詳情頁。
- `clean`、Debug APK、JVM tests、Android test APK、相容 Android 裝置 instrumentation tests 與 lint 全部通過；完成 Standards／Spec review，且不變更本 ticket 以外的 Phase 5 以後功能。

---

## Dashboard multi-vehicle UI refactor

**狀態：** 已完成（2026-08-13）

### 任務目標

將首頁改為多車輛儀表板：顯示所有啟用車輛的名稱、車牌、可重建最新里程與最近平均油耗，並由各車專屬新增按鈕開始加油或保養／維修紀錄。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/feature/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/dashboard/DashboardViewModel.kt`
- `app/src/main/java/com/kumo/bubu/domain/model/FuelEconomy.kt`
- `app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt`
- `app/src/main/java/com/kumo/bubu/navigation/TopLevelDestination.kt`
- `app/src/main/java/com/kumo/bubu/core/ui/components/BuBuBottomNavigation.kt`
- `app/src/main/java/com/kumo/bubu/feature/fuel/FuelFormViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/settings/SettingsScreen.kt`
- `app/src/main/res/values/strings.xml`
- 對應 dashboard 與滿箱油耗衍生值測試

### 完成條件

- 首頁用 `LazyColumn` 顯示所有未封存車輛；車輛卡只呈現名稱、車牌、目前里程與資料足夠時的最近平均油耗。
- 每張卡的新增按鈕開啟 Bottom Sheet，並在加油或保養／維修表單中預選該車輛；卡片其餘區域開啟車輛編輯。
- 無啟用車輛時顯示可導向新增車輛的 Empty State；底部導覽只保留首頁、報表、設定，既有紀錄與車輛畫面仍可由其他入口使用。
- Dashboard 油耗只從現有加油紀錄以滿箱規則衍生，不保存快取、不改動 Room schema 或建立報表功能。

### 驗證指令

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
```

### 禁止提前實作的內容

- 不新增 Dashboard 以外的統計、提醒、搜尋、統一時間軸、報表頁內容、Room schema、CSV、備份或雲端功能。

## Fuel record UI refinement

**狀態：** 已完成（2026-08-12；兩張 24dp 圓角核心卡片、預設收合的進階設定、油品 chips、加滿 switch、固定底部儲存按鈕與 emulator Compose 回歸測試完成）

### 任務目標

將加油紀錄表單重構為適合單手操作的現代 Material 3 卡片式介面，保留既有加油交易、油價帶入與自動計算行為。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/feature/fuel/FuelFormScreen.kt`
- `app/src/androidTest/java/com/kumo/bubu/feature/fuel/FuelFormScreenTest.kt`
- `app/src/main/res/values/strings.xml`

### 完成條件

- 主畫面僅顯示日期時間、目前里程、總金額、公升數、每公升單價與固定儲存按鈕，並以圓角卡片建立清楚層級。
- 油品、是否加滿與備註在預設收合的進階設定中；油品可用 chips 選取，是否加滿使用 switch。
- 保留自動帶入油價、手動輸入、重新載入、數值自動計算、編輯與防重複儲存。

### 驗證指令

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
```

### 禁止提前實作的內容

- 不變更加油資料模型、Room schema、Repository、油耗、報表、提醒、CSV、備份或其他 Phase 功能。

## Fuel product selection regression fix

**狀態：** 已完成（2026-08-12；以 Material 3 exposed dropdown 修正錨點與點擊事件，新增 emulator Compose 回歸測試）

### 任務目標

修正「油品（選填）」在新版加油表單無法展開並選擇 92／95／98 無鉛、柴油或其他的問題。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/feature/fuel/FuelFormScreen.kt`
- `app/src/androidTest/java/com/kumo/bubu/feature/fuel/FuelFormScreenTest.kt`

### 完成條件

- 點擊油品欄位後會顯示全部油品選項，且可選取。
- 使用 Material 3 標準的 exposed dropdown 錨點；不變更加油資料模型、計算與儲存交易。

### 驗證指令

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

### 禁止提前實作的內容

- 不調整油價、中油資料來源、加油計算、Room schema 或其他 Phase 功能。

## Fuel price retrieval reliability fix

**狀態：** 已完成（2026-08-12；修正 Android XML 中文元素解析、失敗後重試且不快取空結果、清楚呈現失敗／上次價格來源、補齊 JVM 與模擬器回歸驗證）

### 任務目標

讓新增加油表單可穩定取得台灣中油歷史人工牌價；暫時查詢失敗時可重新載入、不快取空結果，並清楚區分「查無該日期牌價」與「中油服務無法取得」。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/data/repository/CpcFuelPriceRepository.kt`
- `app/src/main/java/com/kumo/bubu/feature/fuel/FuelFormState.kt`
- `app/src/main/java/com/kumo/bubu/feature/fuel/FuelFormViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/fuel/FuelFormScreen.kt`
- `app/src/main/res/values/strings.xml`
- 對應單元與 Android instrumentation 測試

### 完成條件

- 成功取得的中油歷史牌價會帶入單價；短暫請求失敗不會被空快取永久阻擋，重新載入會再次查詢。
- 取得失敗但有上次同油品價格時，會明確說明目前使用的是上次價格；沒有上次價格時提示可重試。
- 不變更加油紀錄資料模型、Room schema、金額計算或其他 Phase 功能。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

### 禁止提前實作的內容

- 不新增付款方式、油耗、報表、提醒、CSV、備份或雲端功能。
- 不更改 `FuelRecord`、Room schema 或 Repository 的既有加油交易。

## Fuel UI layout refactor

**狀態：** 已完成（2026-08-11；`assembleDebug`、`testDebugUnitTest` 與 `lintDebug` 均通過，lint 0 errors／0 warnings）

### 任務目標

將新增／編輯加油表單改為緊湊的卡片式橫向資訊分組，同時保留既有加油資料、油價帶入與計算流程。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/feature/fuel/FuelFormScreen.kt`
- `app/src/main/res/values/strings.xml`
- `TASKS.md`

### 完成條件

- 頂部以車輛名稱、返回與儲存動作取代大型表單標題。
- 日期時間／里程、金額，以及油量／單價／油品以同列的響應式欄位呈現。
- 油價來源、目前最新里程、加滿選項與可收合備註均清楚可用。
- 不變更 `FuelRecord`、Repository 或既有金額計算與防重複儲存行為。

### 驗證指令

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

### 禁止提前實作的內容

- 不新增付款方式或「上次忘記紀錄」的持久化資料欄位。
- 不修改 Fuel Repository、Room schema、油價取得機制或其他 Phase 功能。

本清單目前拆解 `Spec.md` 的 Phase 0「專案初始化」、Phase 1「車輛管理」、Phase 2「加油紀錄」與 Phase 3「保養與維修」。所有任務均以 `com.kumo.bubu`、`minSdk 26`、Kotlin、Jetpack Compose、Material 3、Gradle Kotlin DSL 與 Version Catalog 為共同前提；Phase 4 以後尚未拆解。

## 範圍邊界

- 本階段只交付可編譯、可啟動、可切換五個空白主頁籤，並支援系統深色／淺色外觀的 Android 專案骨架。
- Room、DataStore、Navigation Compose、WorkManager 在 Phase 0 只完成相依套件與必要基礎設定；Navigation Compose 可用於五頁切換，其餘不得建立產品資料流。
- 不拆解、也不實作 Phase 1 之後的車輛、加油、保養維修、紀錄、報表、提醒、匯出或備份還原功能。
- 執行順序：`P0-01 → P0-02 → P0-03 → P0-05 → P0-07`；`P0-04` 可在 `P0-01` 後進行，`P0-06` 須等待 `P0-01` 至 `P0-04` 完成。

---

## P0-01：建立可編譯與啟動的 Android 專案骨架

**狀態：** 已完成（2026-08-01；`assembleDebug` 通過，模擬器啟動驗收併入 P0-07）

**前置任務：** 無

### 任務目標

建立單一 `app` module 的 BuBu Android 專案，設定 package、SDK、Kotlin、Compose、Material 3、Version Catalog，以及 Phase 0 所需的 Navigation Compose、Room、DataStore、WorkManager 與測試相依套件。先提供最小可啟動入口，不放入 Android Studio 範例功能。

### 要建立或修改的檔案

- `.gitignore`
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/kumo/bubu/BuBuApplication.kt`
- `app/src/main/java/com/kumo/bubu/MainActivity.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`

### 完成條件

- `namespace` 與 `applicationId` 都是 `com.kumo.bubu`，`minSdk` 為 26，compile/target SDK 採執行當下穩定且彼此相容的版本。
- 所有版本集中於 `gradle/libs.versions.toml`；Gradle、AGP、Kotlin、Compose compiler/plugin 與 KSP 版本彼此相容。
- Compose、Material 3、Navigation Compose、Room（含 compiler）、Preferences DataStore、WorkManager 與必要測試套件可由 Gradle 正常解析。
- Manifest 正確宣告 `BuBuApplication` 與 launcher `MainActivity`。
- Debug APK 可建置、安裝並啟動；畫面只顯示最小 BuBu 入口，不含範例計數器或示範資料。

### 驗證指令

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -W -n com.kumo.bubu/.MainActivity
```

### 禁止提前實作的內容

- 不建立任何 Room Entity、DAO、Database、Relation、Mapper、Repository 或 migration。
- 不建立 DataStore key、偏好資料流、WorkManager Worker 或通知排程。
- 不建立車輛、加油、保養維修、報表等畫面或商業邏輯。
- 不加入 Firebase、登入、雲端同步、廣告、追蹤 SDK 或與 Phase 0 無關的第三方套件。

---

## P0-02：建立 Material 3 深色／淺色主題

**狀態：** 已完成（2026-08-01；`assembleDebug` 通過）

**前置任務：** P0-01

### 任務目標

建立 BuBu 的基本 Compose Material 3 theme，使 App 能跟隨 Android 系統深色／淺色設定正確顯示，並讓後續畫面只透過 theme 取得顏色與字體樣式。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/core/ui/theme/Color.kt`
- `app/src/main/java/com/kumo/bubu/core/ui/theme/Theme.kt`
- `app/src/main/java/com/kumo/bubu/core/ui/theme/Type.kt`
- `app/src/main/java/com/kumo/bubu/MainActivity.kt`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`

### 完成條件

- 根 Composable 套用 BuBu theme，並以系統模式決定深色或淺色配色。
- 兩種模式的背景、文字與導覽元件具有可辨識對比，系統狀態列／導覽列不出現明顯色彩衝突。
- Compose 中沒有散落的硬編碼顏色、尺寸或硬編碼中文字串。
- 切換系統 night mode 並重新啟動 Activity 後不 crash，且能看到對應外觀。

### 驗證指令

```powershell
.\gradlew.bat :app:assembleDebug
adb shell cmd uimode night yes
adb shell am force-stop com.kumo.bubu
adb shell am start -W -n com.kumo.bubu/.MainActivity
adb shell cmd uimode night no
adb shell am force-stop com.kumo.bubu
adb shell am start -W -n com.kumo.bubu/.MainActivity
```

### 禁止提前實作的內容

- 不建立設定頁的手動深色模式選項，也不使用 DataStore 保存外觀偏好。
- 不設計完整品牌視覺、圖表色盤、表單元件庫或 Phase 1 之後才會使用的元件。
- 不把主題或狀態邏輯塞入各個空畫面。

---

## P0-03：建立五頁底部導航骨架

**狀態：** 已完成（2026-08-01；Navigation API 修正後 `assembleDebug` 通過）

**前置任務：** P0-02

### 任務目標

使用 Navigation Compose 與 Material 3 NavigationBar 建立 Dashboard、History、Reports、Vehicles、Settings 五個頂層目的地。每頁只顯示可辨識的頁名，形成可操作但不含產品功能的導航骨架。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/navigation/TopLevelDestination.kt`
- `app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt`
- `app/src/main/java/com/kumo/bubu/core/ui/components/BuBuBottomNavigation.kt`
- `app/src/main/java/com/kumo/bubu/feature/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/history/HistoryScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/reports/ReportsScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehiclesScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/settings/SettingsScreen.kt`
- `app/src/main/java/com/kumo/bubu/MainActivity.kt`
- `app/src/main/res/values/strings.xml`

### 完成條件

- App 預設進入 Dashboard，底部固定顯示五個目的地。
- 點擊每個目的地都會切換到正確空畫面，選取狀態與目前 route 一致。
- 重複點擊目前目的地不會不斷堆疊 destination；返回鍵行為不造成 crash 或重複頁面。
- 五個畫面只含資源化頁名與必要版面，沒有假資料、功能按鈕或不可執行 TODO。
- `MainActivity` 只負責 activity／根 Compose 裝配，不承載各頁邏輯。

### 驗證指令

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -W -n com.kumo.bubu/.MainActivity
```

安裝後人工依序點擊「首頁 → 紀錄 → 報表 → 車輛 → 設定」，確認標題與選取狀態同步，再按返回鍵確認沒有 crash。

### 禁止提前實作的內容

- 不加入新增／編輯車輛、加油、保養、維修、支出或詳情 route。
- 不實作首頁摘要、紀錄時間軸、報表數字或圖表、車輛清單、設定項目與任何 Empty/Loading/Error 資料狀態。
- 不建立 ViewModel、Repository、假資料或預覽用正式資料流。

---

## P0-04：建立 feature-first 目錄邊界

**狀態：** 已完成（2026-08-01；目錄稽核與 `assembleDebug` 通過）

**前置任務：** P0-01

### 任務目標

依 `Spec.md` 建立 Phase 0 尚未有實作檔案的 package 目錄，使後續 Phase 能依 core、data、domain、feature 分層放置程式碼；空目錄只用 `.gitkeep` 保存，不先建立空殼類別。

### 要建立或修改的檔案

- 下列尚無 Phase 0 程式碼之目錄內的 `.gitkeep`：
  - `app/src/main/java/com/kumo/bubu/core/{database,date,currency,units,csv,backup,validation,util}/.gitkeep`
  - `app/src/main/java/com/kumo/bubu/data/local/{entity,dao,relation,converter}/.gitkeep`
  - `app/src/main/java/com/kumo/bubu/data/{mapper,repository,exporter}/.gitkeep`
  - `app/src/main/java/com/kumo/bubu/domain/{model,repository,usecase}/.gitkeep`
  - `app/src/main/java/com/kumo/bubu/feature/{fuel,service,reminder,dataexport}/.gitkeep`

已由 P0-02、P0-03 建立實際檔案的 `core/ui`、`feature/dashboard`、`feature/history`、`feature/reports`、`feature/vehicle`、`feature/settings` 與 `navigation` 不再放置 `.gitkeep`。

### 完成條件

- 目錄結構與 `Spec.md` 第 3 節一致，且沒有為了保留目錄而建立無用途 Kotlin 類別。
- `.gitkeep` 不影響 Kotlin source set、build、lint 或 APK。
- 未出現 `MainActivity` 集中所有功能、UI 直連 DAO，或 data/domain 方向顛倒的結構。

### 驗證指令

```powershell
Get-ChildItem app\src\main\java\com\kumo\bubu -Recurse -Force | Select-Object FullName
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不建立 Entity、DAO、Database、Domain Model、Repository、UseCase、Mapper、Exporter、Worker 或 Attachment 類別。
- 不為未開始的功能加入 interface、stub、TODO、假實作或範例資料。
- 不拆解或實作 Phase 1 以後的細部工作。

---

## P0-05：加入五頁導航儀器測試

**狀態：** 已完成（2026-08-01；測試 APK 無警告編譯，連線執行驗收併入 P0-07）

**前置任務：** P0-03

### 任務目標

從使用者可見介面驗證 App 能啟動並切換五個底部導航目的地，讓 Phase 0 的核心互動具有可重複執行的驗收證據。

### 要建立或修改的檔案

- `app/src/androidTest/java/com/kumo/bubu/navigation/BottomNavigationTest.kt`
- `app/src/main/res/values/strings.xml`（僅在測試發現缺少可存取標籤時修改）

### 完成條件

- 測試啟動 `MainActivity` 後確認 Dashboard 為預設目的地。
- 測試依序點擊 History、Reports、Vehicles、Settings，並以可見頁名或語意標籤確認目的地。
- 測試不依賴固定座標、sleep、網路、資料庫或預先存在的使用者資料。
- 測試在 API 26 以上 emulator 可穩定通過。

### 驗證指令

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

### 禁止提前實作的內容

- 不撰寫 Phase 1 以後的車輛建立、加油、工單、報表、提醒、匯出或備份 UI 測試。
- 不以測試專用假畫面、假 navigation graph 或 production code 特例讓測試通過。
- 不加入 screenshot 測試框架或非 Phase 0 必要的測試工具。

---

## P0-06：補齊專案文件與代理規則

**狀態：** 已完成（2026-08-01；文件稽核與 `assembleDebug` 通過）

**前置任務：** P0-01、P0-02、P0-03、P0-04

### 任務目標

建立能讓開發者與後續代理正確建置、驗證並延續專案的文件，記錄實際採用的技術版本、Phase 0 範圍與禁止越界事項。

### 要建立或修改的檔案

- `README.md`
- `AGENTS.md`
- `CHANGELOG.md`

### 完成條件

- `README.md` 說明專案目標、目前只完成 Phase 0、環境需求、實際技術版本、專案結構、build/test/lint/emulator 指令及離線／無登入／無雲端的產品邊界。
- `AGENTS.md` 要求先閱讀 `Spec.md`、`CONTEXT.md` 與 ADR，遵循分層規則、只執行被指派 Phase、先驗證後回報，且不得預作未授權功能。
- `CHANGELOG.md` 採 Keep a Changelog 風格，於 `Unreleased` 僅列 Phase 0 已完成內容，不宣稱 Phase 1 以後功能已存在。
- 文件中的 package、SDK、版本與指令均和實際 Gradle 設定一致。

### 驗證指令

```powershell
Get-Content README.md -Encoding UTF8
Get-Content AGENTS.md -Encoding UTF8
Get-Content CHANGELOG.md -Encoding UTF8
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不在文件中宣稱車輛管理、油耗、圖表、提醒、CSV 或備份還原已可使用。
- 不加入 Phase 1 以後的 ticket、實作步驟或排程承諾。
- 不把 CSV 描述為可還原格式。

---

## P0-07：執行 Phase 0 最終品質閘門

**狀態：** 已完成（2026-08-01；全部 Phase 0 品質閘門通過）

**驗證紀錄：** `clean`、`assembleDebug`、`testDebugUnitTest`、`assembleDebugAndroidTest`、`lintDebug` 與依賴／原始碼稽核均通過；lint SARIF 為 0 項。`connectedDebugAndroidTest` 已在 Android 16 實機通過首頁與導覽測試。淺色與深色模式皆完成冷啟動，`am start -W` 均回報 `Status: ok`。

**前置任務：** P0-01、P0-02、P0-03、P0-04、P0-05、P0-06

### 任務目標

以乾淨建置、單元測試、儀器測試、lint 與 emulator 操作完成 Phase 0 驗收，清除無關範例、明顯警告與 crash，並整理最後交付回報。

### 要建立或修改的檔案

- 預定不建立新檔案。
- 若驗證失敗，只能修改 P0-01 至 P0-06 已列出的 Phase 0 檔案；所有修正檔案必須在交付回報中逐一列明。
- 若實際版本或驗證指令因修正而改變，同步修改 `README.md` 與 `CHANGELOG.md`。

### 完成條件

- `clean`、debug build、unit test、instrumentation test 與 lint 全部通過，lint 沒有 error。
- App 可在 emulator 冷啟動；五頁切換、返回操作、深色與淺色模式均不 crash。
- 原始碼中沒有 Android Studio 範例頁、計數器、假資料、未使用示範程式碼、空 catch 或不可執行 TODO。
- Gradle dependency tree 不含 Firebase、登入、雲端、廣告或追蹤 SDK。
- 最終回報包含建立／修改檔案、實際技術版本、build 結果、測試結果，以及下一步僅建議進入 Phase 1，不在本任務實作。

### 驗證指令

```powershell
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
rg -n "Firebase|TODO|FIXME|Hello Android|Greeting|Counter" app README.md AGENTS.md CHANGELOG.md
adb shell cmd uimode night yes
adb shell am force-stop com.kumo.bubu
adb shell am start -W -n com.kumo.bubu/.MainActivity
adb shell cmd uimode night no
adb shell am force-stop com.kumo.bubu
adb shell am start -W -n com.kumo.bubu/.MainActivity
```

`rg` 找不到上述禁止字樣時會以 exit code 1 結束，這代表檢查通過，不是失敗。

### 禁止提前實作的內容

- 不藉由修 lint 或測試擴充任何 Phase 1 以後功能。
- 不新增資料庫 schema、產品資料、ViewModel、報表圖表、提醒通知、匯入匯出、備份還原或附件處理。
- 不開始 Phase 1；品質閘門完成後即停止並回報。

---

## Phase 0 完成定義

只有 P0-01 至 P0-07 全部完成且通過驗證，Phase 0 才算完成。任何 Phase 1 以後的程式碼，即使可以編譯，也視為越界而非額外交付。

---

# Phase 1：車輛管理

## Phase 1 範圍邊界

- 本階段只交付車輛 Entity、DAO、Repository、列表、新增、編輯、封存、解除封存、刪除尚無關聯資料的車輛、汽車／機車與機車級別，以及目前車輛持久化。
- 車輛名稱不得為空；日期使用不含時區的本地日曆日期；里程只使用非負整數公里；主要資料同時保存本機 `Long` 主鍵與不可變 UUID。
- 不建立加油、服務工單、支出、提醒、附件、里程修正或儀表更換的 Entity、DAO、Repository 或 UI。
- 不加入 Firebase、登入、雲端、廣告、追蹤、多貨幣、英制單位、電動車、營業車或租賃車。
- 執行順序：`P1-01 → P1-02 → P1-03 → P1-04 → P1-05`。

---

## P1-01：建立第一台車的持久化新增流程

**狀態：** 已完成（2026-08-01；build、6 項 unit tests 與 Android test APK 編譯通過，實機 connected 驗收併入 P1-05）

**前置任務：** P0-07

### 任務目標

從車輛 Empty State 進入新增表單，建立一台具備穩定 UUID、車種、追蹤起始日期與追蹤起始里程的車輛，經 ViewModel、domain Repository、mapper、Room DAO 寫入本機資料庫，返回列表後立即可見，重新建立 Activity 後仍存在。

### 要建立或修改的檔案

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/kumo/bubu/BuBuApplication.kt`
- `app/src/main/java/com/kumo/bubu/core/database/BuBuDatabase.kt`
- `app/src/main/java/com/kumo/bubu/core/database/AppContainer.kt`
- `app/src/main/java/com/kumo/bubu/data/local/entity/VehicleEntity.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/VehicleDao.kt`
- `app/src/main/java/com/kumo/bubu/data/mapper/VehicleMapper.kt`
- `app/src/main/java/com/kumo/bubu/data/repository/OfflineVehicleRepository.kt`
- `app/src/main/java/com/kumo/bubu/domain/model/Vehicle.kt`
- `app/src/main/java/com/kumo/bubu/domain/repository/VehicleRepository.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehicleFormState.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehicleFormViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehicleFormScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehiclesViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehiclesScreen.kt`
- `app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt`
- `app/src/main/res/values/strings.xml`
- 對應 mapper、DAO 與 UI 測試檔案

### 完成條件

- Room schema 只包含 `VehicleEntity`；Entity、domain model 與 UI state 彼此分離。
- 新車建立時產生不可變 UUID，Room 使用自動遞增 `Long` 主鍵，`createdAt`／`updatedAt` 使用 epoch milliseconds。
- Empty State 提供「新增第一台車」操作；名稱空白、負里程或未選必要車種時不可保存，錯誤原因明確顯示。
- 保存成功後回到真實資料列表；Activity 重建後資料仍存在，不使用假資料或 Composable 直連 DAO。
- mapper、DAO 新增／Flow 更新與第一台車 UI 流程有測試證據。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不實作目前車輛 DataStore、編輯、封存、刪除或 Phase 2 加油功能。
- 不建立其他產品 Entity、DAO、資料表或 migration。
- 不把 `VehicleEntity` 傳到 UI，也不把資料存進記憶體假 Repository。

---

## P1-02：支援多台車與目前車輛持久化

**狀態：** 已完成（2026-08-01；多車列表、UUID 選車與 DataStore 持久化已完成，三組 Kotlin source set 編譯通過）

**前置任務：** P1-01

### 任務目標

讓使用者可建立多台車、在列表辨識目前車輛並切換選擇；目前車輛以 Preferences DataStore 持久化，關閉再開啟 App 後保持選擇。刪除或封存造成選擇失效時，Repository 以可預期規則改選第一台使用中車輛或清空。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/core/database/AppContainer.kt`
- `app/src/main/java/com/kumo/bubu/data/repository/OfflineVehicleRepository.kt`
- `app/src/main/java/com/kumo/bubu/domain/repository/VehicleRepository.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehiclesViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehiclesScreen.kt`
- `app/src/main/res/values/strings.xml`
- 對應 Repository 與 UI 測試檔案

### 完成條件

- 可重複使用新增流程建立至少兩台車，列表排序穩定且以 Flow 即時更新。
- 目前車輛以穩定 UUID 保存，不以列表位置或車名識別；重新啟動 App 後選擇不變。
- 列表清楚標示目前車輛，切換操作具有成功或失敗狀態，不會在每次 recomposition 重讀整張表。
- DataStore 與 Room 的組合資料流由 Repository 封裝，ViewModel 只暴露 immutable UI state 與 sealed UI event。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不加入跨裝置同步、帳號或雲端選車狀態。
- 不建立 Dashboard 車輛摘要、VehicleSelector 共用元件或 Phase 2 新增加油入口。
- 不以車牌、車名或可變欄位當作目前車輛的永久識別。

---

## P1-03：完成車輛完整欄位與編輯流程

**狀態：** 已完成（2026-08-01；完整欄位、驗證、編輯與不可變識別保存已完成，三組 Kotlin source set 編譯通過）

**前置任務：** P1-02

### 任務目標

讓使用者從車輛列表編輯 Phase 1 規定的完整車輛資料，包括名稱、汽車／機車、機車級別、廠牌、型號、出廠年份、排氣量、車牌、液體燃料動力類型、追蹤起始日期、追蹤起始里程與備註；保存時保留原 UUID 與建立時間並更新 `updatedAt`。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/data/local/entity/VehicleEntity.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/VehicleDao.kt`
- `app/src/main/java/com/kumo/bubu/data/mapper/VehicleMapper.kt`
- `app/src/main/java/com/kumo/bubu/data/repository/OfflineVehicleRepository.kt`
- `app/src/main/java/com/kumo/bubu/domain/model/Vehicle.kt`
- `app/src/main/java/com/kumo/bubu/domain/repository/VehicleRepository.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehicleFormState.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehicleFormViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehicleFormScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehiclesScreen.kt`
- `app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt`
- `app/src/main/res/values/strings.xml`
- 對應 mapper、DAO、ViewModel 與 UI 測試檔案

### 完成條件

- `Vehicle` 欄位與 `Spec.md` 5.1 一致；汽車的機車級別必為 `null`，機車必須選擇輕型、普通重型或大型重型。
- 動力類型只提供汽油、柴油、油電混合及其他液體燃料；不出現電動車選項。
- 出廠年份、排氣量、日期與里程輸入有明確驗證；不接受未來追蹤起始日期、負里程或無法解析的數字。
- 編輯不改變 `publicId`／`createdAt`，成功後列表立即顯示新名稱、廠牌型號與目前里程。
- 修改追蹤起始里程時，在尚無日常紀錄的 Phase 1 中同步更新可重建快取 `currentOdometerKm`。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不建立里程修正、儀表更換、加油或服務工單來更新目前里程。
- 不加入電動、營業、租賃、特殊用途車種或其他單位／貨幣。
- 不實作附件或車輛照片資料表；列表只使用不持久化的車種圖示佔位。

---

## P1-04：完成封存、解除封存與空白車輛刪除

**狀態：** 已完成（2026-08-01；封存、解除封存、無關聯車輛永久刪除與目前車輛重整已完成，三組 Kotlin source set 編譯通過）

**前置任務：** P1-03

### 任務目標

讓使用者封存不再日常使用的車輛、查看並解除封存，並可在確認後永久刪除目前尚無任何關聯紀錄的車輛；操作完成後目前車輛選擇仍保持有效。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/data/local/dao/VehicleDao.kt`
- `app/src/main/java/com/kumo/bubu/data/repository/OfflineVehicleRepository.kt`
- `app/src/main/java/com/kumo/bubu/domain/repository/VehicleRepository.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehiclesViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/vehicle/VehiclesScreen.kt`
- `app/src/main/java/com/kumo/bubu/core/ui/components/ConfirmDeleteDialog.kt`
- `app/src/main/res/values/strings.xml`
- 對應 DAO、Repository 與 UI 測試檔案

### 完成條件

- 封存車輛仍顯示於清單並有明確標示，可查看、編輯及解除封存；使用中車輛排在封存車輛之前。
- 封存目前車輛後自動選擇其他使用中車輛；沒有其他使用中車輛時目前車輛清空。
- 永久刪除前顯示具體確認對話框；Phase 1 只有在資料庫尚無其他產品關聯表的前提下允許刪除，Repository API 明確命名為刪除無關聯車輛。
- DAO 更新、封存／解除封存、刪除及 Flow 更新有測試；錯誤不被吞掉並以具體 UI 訊息呈現。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不建立 Phase 2 以後的關聯表來模擬歷史資料，也不實作垃圾桶車輛。
- 不讓封存車輛消失到無法查看、編輯或解除封存。
- 不因封存或刪除自動修改任何其他車輛原始資料。

---

## P1-05：執行 Phase 1 最終品質閘門與程式碼審查

**狀態：** 已完成（2026-08-02；產品 APK 與 Android test APK build 成功、unit tests 全數通過、lint SARIF 0、依賴檢查與雙軸 code review 皆為 0 findings；Android 16 實機的 `connectedDebugAndroidTest` 通過，0 failures、0 errors；Debug APK 冷啟動成功）

**前置任務：** P1-01、P1-02、P1-03、P1-04

### 任務目標

以乾淨建置、單元測試、DAO／儀器測試、lint 與 Samsung 實機操作驗收 Phase 1；更新文件，並分別依專案標準與 `Spec.md` Phase 1 做雙軸 code review，修正所有確認問題後才宣告完成。

### 要建立或修改的檔案

- `README.md`
- `CHANGELOG.md`
- `TASKS.md`
- 測試或審查發現問題時，僅修改 P1-01 至 P1-04 列出的 Phase 1 檔案

### 完成條件

- 可建立多台車、切換目前車輛、重新啟動後保持資料與選擇，並可編輯、封存、解除封存及刪除無關聯車輛。
- Room DAO 新增、更新、刪除與 Flow 測試通過；mapper 與表單驗證單元測試通過；核心 UI 流程儀器測試通過。
- `clean`、debug build、unit test、instrumentation test 與 lint 全部通過，lint SARIF 為 0 項。
- 原始碼不含 Phase 2 Entity／DAO／Repository／UI，不含 Firebase、登入、雲端、廣告、追蹤、假資料、空 catch 或不可執行 TODO。
- code review 分開檢查 Standards 與 Spec，所有確認為缺陷的 finding 修正後重跑相關品質閘門。
- README 與 CHANGELOG 只宣告 Phase 0–1 已完成，不宣稱加油或其他後續功能可用。

### 驗證指令

```powershell
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
rg -n "Firebase|TODO|FIXME|GlobalScope|FuelRecord|ServiceRecord|ExpenseRecord|VehicleReminder" app README.md CHANGELOG.md
```

### 禁止提前實作的內容

- 不開始或拆解 Phase 2；不建立加油紀錄模型、資料表、表單、油耗計算或測試。
- 不建立保養維修、報表、提醒、CSV、備份還原或附件功能。
- 不以 code review 名義擴張架構、加入未使用抽象或新增與 Phase 1 無關套件。

---

## Phase 1 完成定義

只有 P1-01 至 P1-05 全部完成、TASKS.md 狀態更新、實機與自動化驗收通過，且雙軸 code review 沒有未處理的確認問題，Phase 1 才算完成。任何 Phase 2 以後的程式碼都視為越界。

---

# Phase 2：加油紀錄

## Phase 2 範圍邊界

- 本階段只交付 `FuelRecord` 的本機持久化、新增、編輯、刪除、精確金額換算、加滿標記、最近紀錄與目前里程重算。
- 加油量以 ml `Long` 保存、每公升單價以 milli-TWD `Long?` 保存、實付總額以 TWD `Long` 保存；不使用 `Double` 儲存或累加金額／油量。
- 只允許使用中的車輛新增加油紀錄；封存車輛可查看、編輯與刪除其舊紀錄。
- 不建立油耗計算、油耗週期重算、服務工單、支出、垃圾桶、附件、提醒、首頁摘要、報表、CSV 或備份還原。
- 執行順序：`P2-01 → P2-02 → P2-03 → P2-04`。

---

## P2-01：建立第一筆可持久化的加油紀錄

**狀態：** 已完成（2026-08-02；主程式、unit tests 與 Android test source set 均已編譯通過；Android 16 實機的 `connectedDebugAndroidTest` 通過，0 failures、0 errors）

**前置任務：** P1-05

### 任務目標

讓使用者為使用中的車輛建立一筆真正寫入本機資料庫的加油紀錄，並在最近紀錄中立即看見它；儲存日期、選填時間、里程、加油量、實付總額、是否加滿、油品與備註，且以穩定 UUID 識別紀錄。

### 影響範圍

- 擴充既有 Room 資料庫並加入 FuelRecord 的 domain model、DAO、mapper、Repository 與資料庫交易。
- 在車輛流程提供新增加油入口、表單、immutable UI state、sealed UI event 與最近紀錄顯示。
- 為 domain／mapper、DAO／Repository 與使用者新增流程建立測試。

### 完成條件

- `FuelRecord` 只保存 Phase 2 所需欄位；油量、單價與總金額全部使用整數最小單位，不以 `Double` 持久化。
- 只能對使用中車輛新增加油；日期不可在未來、里程為非負整數公里、油量大於 0 且不超過 999.999 L、總金額不可為負。
- 儲存成功後返回上一頁並在真實最近紀錄列表可見；快速重複點擊不會新增兩筆。
- 新增里程高於目前里程時，和新增紀錄在同一個資料庫交易中更新車輛目前里程。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不計算 km/L、最近平均油耗、每公里成本或滿箱油耗週期。
- 不建立 ServiceRecord、ExpenseRecord、VehicleReminder、Attachment、垃圾桶或後續資料表。

---

## P2-02：完成精確金額換算與加滿偏好

**狀態：** 已完成（2026-08-02；三種兩欄換算以 `BigDecimal` 與整數最小單位驗證，unit tests 與 Android 16 實機 instrumentation tests 均通過）

**前置任務：** P2-01

### 任務目標

讓使用者在公升數、每公升單價、實付總額任意輸入兩項時，準確取得第三項計算結果；每台車會沿用上一次的「是否加滿」設定。

### 影響範圍

- 擴充加油表單的輸入解析、精確整數／Decimal 換算、欄位錯誤與計算來源提示。
- 在既有本機偏好資料中保存每台車最近一次的加滿選擇。
- 為三種兩欄輸入組合、四捨五入、數值邊界與加滿偏好建立行為測試。

### 完成條件

- 輸入公升數與單價可計算整數新臺幣總金額；輸入公升數與總金額可計算單價；輸入單價與總金額可計算公升數。
- 最後明確輸入的兩欄是來源；自動計算欄位有清楚標示，且使用者可覆寫。
- 保存時油量與實付總額為權威資料，所有換算不用浮點累加。
- 新增下一筆同車加油時，是否加滿預設為該車上次保存的設定。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不依油量、油品或車種猜測是否加滿。
- 不因加滿標記建立油耗計算、錨點、週期重啟或油耗報表。

---

## P2-03：完成近期加油紀錄的編輯、刪除與里程重算

**狀態：** 已完成（2026-08-02；編輯、確認刪除與里程重算的 unit／DAO／Repository／Compose UI 測試通過；Android 16 實機的 `connectedDebugAndroidTest` 通過，0 failures、0 errors）

**前置任務：** P2-01、P2-02

### 任務目標

讓使用者可在最近紀錄中編輯或確認後永久刪除加油紀錄；任何新增、補登、編輯或刪除都會從可用歷史資料重算車輛目前里程，補登舊資料絕不降低目前里程。

### 影響範圍

- 擴充 FuelRecord 的單筆讀取、更新、永久刪除與依車輛／日期穩定排序查詢。
- 提供加油編輯路由、確認刪除互動與 UI 錯誤處理。
- 在 Repository 交易中依追蹤起始里程和未刪除加油紀錄最大里程重建目前里程。
- 為補登、更新、刪除最高里程與 UI 編輯／刪除流程建立測試。

### 完成條件

- 編輯保留 FuelRecord 的 stable identity 與建立時間，更新 `updatedAt`；刪除前顯示明確確認對話框。
- 新增或修改較高里程時目前里程上升；補登較舊或較低里程不會降低目前里程；刪除或降低最高里程時依剩餘資料重新計算。
- 最近紀錄依日期、選填時間與使用者確認順序穩定排列，沒有時間時不虛構時間。
- 所有操作使用真實 Room／Repository 資料流，UI 不直接呼叫 DAO。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不建立里程錯誤修正、儀表更換、垃圾桶、還原或刪除原因功能。
- 不建立歷史篩選、搜尋、跨車統計、油耗或 Dashboard 摘要。

---

## P2-04：執行 Phase 2 最終品質閘門與程式碼審查

**狀態：** 已完成（2026-08-02；從 `clean` 狀態完成 Debug APK、unit tests 與 lint SARIF 0；Android 16 實機的 `connectedDebugAndroidTest` 通過，0 failures、0 errors；runtime dependency 與範圍稽核通過；Standards／Spec 最終 code review 均為 0 findings）

**前置任務：** P2-01、P2-02、P2-03

### 任務目標

以 build、單元／DAO／Repository／UI／Samsung 實機測試、lint 與 Standards／Spec 雙軸 code review 驗收 Phase 2，修正所有確認缺陷後更新專案文件與任務狀態。

### 完成條件

- 公升數、單價、總金額任兩項計算第三項的所有組合皆有行為測試，且儲存值不使用 `Double`。
- 新增、編輯、刪除、加滿偏好、快速重複保存、較高里程更新與補登不降里程皆有自動化驗收證據。
- `clean`、debug build、unit test、instrumentation test、lint、依賴與原始碼範圍稽核全部通過。
- Standards 與 Spec review 均沒有未處理 finding；README、CHANGELOG 只宣告已完成的 Phase 0–2 功能。

### 驗證指令

```powershell
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
rg -n "Firebase|TODO|FIXME|GlobalScope|ServiceRecord|ExpenseRecord|VehicleReminder|Attachment" app README.md CHANGELOG.md
```

### 禁止提前實作的內容

- 不開始或拆解 Phase 3；不建立服務工單、保養維修、支出、提醒、附件、油耗、報表、CSV 或備份還原。
- 不以 code review 名義加入未使用抽象、外部 SDK、網路、帳號、雲端、廣告或追蹤。

---

## Phase 2 完成定義

只有 P2-01 至 P2-04 全部完成、TASKS.md 狀態更新、實機與自動化驗收通過，且雙軸 code review 沒有未處理的確認問題，Phase 2 才算完成。任何 Phase 3 以後的程式碼都視為越界。

---

# Phase 3：保養與維修

## Phase 3 範圍邊界

- 本階段交付本機 `ServiceRecord`、`ServiceItem`、`ServiceType` 與 `ExpenseRecord` 的持久化、管理與服務工單操作，並依 P3-06 增加服務工單所需的多圖片附件及服務項目提醒關聯資料。
- 服務工單新增、編輯與刪除時，工單、項目、提醒、附件關聯與車輛目前里程必須在同一個 Room Transaction 中一致處理；附件實體檔案使用可補償流程保持一致。
- 只交付服務與支出各自的列表／表單操作，以及服務工單內必要的附件圖片與提醒關聯；不建立統一時間軸、搜尋、日期範圍、首頁摘要、完整提醒頁、WorkManager、系統通知、垃圾桶、油耗、報表、CSV、備份或還原。
- P3-01 至 P3-05 保留其完成當時的歷史驗收紀錄；P3-06 是使用者後續明確授權的服務工單重構，僅在其列明範圍內取代舊表單、工資、總額、附件及提醒限制。
- 執行順序：`P3-01 → P3-02 → P3-03 → P3-04`，`P3-05` 可在 `P3-01` 後進行；接著執行 `P3-06`，最後以 `P3-07` 完成品質閘門。

---

## P3-01：建立第一張可持久化的服務工單

**狀態：** 已完成（2026-08-03；Room 工單／項目 transaction、里程重建與 Samsung 實機資料庫測試通過）

**前置任務：** P2-04

### 任務目標

讓使用者可為使用中的車輛建立一張保養、維修或檢驗工單，包含一個自行命名的服務項目；工單、項目與目前里程在同一個 Room Transaction 寫入，並立即顯示於服務紀錄列表。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/core/database/BuBuDatabase.kt`
- `app/src/main/java/com/kumo/bubu/core/database/AppContainer.kt`
- `app/src/main/java/com/kumo/bubu/data/local/entity/ServiceRecordEntity.kt`
- `app/src/main/java/com/kumo/bubu/data/local/entity/ServiceItemEntity.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceRecordDao.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceItemDao.kt`
- `app/src/main/java/com/kumo/bubu/data/mapper/ServiceMapper.kt`
- `app/src/main/java/com/kumo/bubu/data/repository/OfflineServiceRepository.kt`
- `app/src/main/java/com/kumo/bubu/domain/model/ServiceRecord.kt`
- `app/src/main/java/com/kumo/bubu/domain/repository/ServiceRepository.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormState.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceRecordsViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceRecordsScreen.kt`
- `app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt`
- `app/src/main/res/values/strings.xml`
- 對應 domain、Room、Repository、ViewModel 與 Compose 測試

### 完成條件

- 工單與服務項目都有本機 `Long` 主鍵、不可變 UUID 與稽核時間；日期不可為未來、里程只接受非負整數，封存車輛不可建立新工單。
- 成功建立會同時寫入工單、第一個項目並以加油與服務工單最大里程重建車輛目前里程；補登舊紀錄不得降低目前里程。
- 任一步資料庫寫入失敗時，工單、項目及車輛里程都回復到操作前狀態。
- UI 顯示 loading、empty、欄位錯誤與儲存失敗，Composable 不直接呼叫 DAO。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不建立服務提醒、到期週期、附件、垃圾桶、統一時間軸、報表或油耗。
- 不建立自訂服務類型管理、多項目編輯、工單刪除或其他支出。

---

## P3-02：加入服務類型與自訂服務項目

**狀態：** 已完成（2026-08-03；內建服務類型初始化、自訂類型新增、編輯與封存資料流程已完成）

**前置任務：** P3-01

### 任務目標

讓使用者在服務工單項目中選擇內建服務類型，或建立可重複使用的自訂服務類型；歷史項目一律保存名稱快照。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/core/database/BuBuDatabase.kt`
- `app/src/main/java/com/kumo/bubu/data/local/entity/ServiceTypeEntity.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceTypeDao.kt`
- `app/src/main/java/com/kumo/bubu/data/mapper/ServiceMapper.kt`
- `app/src/main/java/com/kumo/bubu/data/repository/OfflineServiceRepository.kt`
- `app/src/main/java/com/kumo/bubu/domain/model/ServiceType.kt`
- `app/src/main/java/com/kumo/bubu/domain/repository/ServiceRepository.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormState.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormScreen.kt`
- `app/src/main/res/values/strings.xml`
- 對應 domain、Room、Repository、ViewModel 與 Compose 測試

### 完成條件

- 內建類型包含規格列出的常用項目，並可在工單中選取；使用者可建立自訂類型並立即使用。
- 服務項目保存選填的類型關聯及必填名稱快照；日後變更或封存類型不改寫歷史名稱。
- 內建類型不可改名；自訂類型可修改或封存，且已被歷史引用的類型不能永久刪除。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不建立提醒週期、到期里程／日期的提醒實體或 WorkManager。
- 不建立服務費用報表、全文搜尋或跨車統計。

---

## P3-03：完成多服務項目、金額合計與工單編輯

**狀態：** 已完成（2026-08-03；多項目小計加上工資的整數總額、穩定項目識別與工單編輯已完成）

**前置任務：** P3-01、P3-02

### 任務目標

讓使用者新增、刪除與調整多個服務項目，正確計算項目小計與建議總額，並可編輯整張工單。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceRecordDao.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceItemDao.kt`
- `app/src/main/java/com/kumo/bubu/data/mapper/ServiceMapper.kt`
- `app/src/main/java/com/kumo/bubu/data/repository/OfflineServiceRepository.kt`
- `app/src/main/java/com/kumo/bubu/domain/model/ServiceRecord.kt`
- `app/src/main/java/com/kumo/bubu/domain/repository/ServiceRepository.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormState.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceRecordsScreen.kt`
- `app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt`
- `app/src/main/res/values/strings.xml`
- 對應 calculation、Room、Repository、ViewModel 與 Compose 測試

### 完成條件

- 一張工單可包含多個項目並保存使用者確認的順序；各項目可有選填數量、單價、小計、下次里程、下次日期與備註。
- 項目小計與工資可計算建議總額；實付總額是權威整數新臺幣，可為零但不可為負，差異可由工單備註說明。
- 編輯在同一 Transaction 中更新工單、同步新增／更新／移除項目並重建車輛里程；保留工單與項目的 stable identity／建立時間。
- 工單沒有服務項目時，必須有非空白備註。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不建立提醒、排程、附件、垃圾桶、服務花費圖表、報表或匯出。
- 不使用 `Double` 儲存或累加金額。

---

## P3-04：刪除服務工單並保持資料一致

**狀態：** 已完成（2026-08-03；刪除工單由 Room 外鍵連動刪除項目並重建車輛里程，實機測試通過）

**前置任務：** P3-03

### 任務目標

讓使用者在確認後永久刪除服務工單；所有隸屬服務項目與車輛目前里程在同一 Transaction 一致更新。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceRecordDao.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceItemDao.kt`
- `app/src/main/java/com/kumo/bubu/data/repository/OfflineServiceRepository.kt`
- `app/src/main/java/com/kumo/bubu/domain/repository/ServiceRepository.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceRecordsViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceRecordsScreen.kt`
- `app/src/main/res/values/strings.xml`
- 對應 Room、Repository、ViewModel 與 Compose 測試

### 完成條件

- 刪除前顯示工單資訊與不可復原的確認；快速重複點擊只執行一次。
- 刪除成功後沒有孤兒服務項目，最近服務紀錄立即更新；刪除最高里程工單後，以剩餘加油與服務紀錄重建目前里程。
- 刪除任一步失敗時整個 Transaction rollback，使用者可看見具體錯誤。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不建立垃圾桶、還原、刪除原因、附件或提醒連動。
- 不建立統一紀錄頁、搜尋或 Phase 4 篩選。

---

## P3-05：建立其他支出紀錄

**狀態：** 已完成（2026-08-03；其他支出新增、編輯、刪除與獨立里程規則已完成）

**前置任務：** P3-01

### 任務目標

讓使用者建立、編輯與確認後刪除不屬於加油或服務工單的其他支出，並和服務工單資料完全分離。

### 要建立或修改的檔案

- `app/src/main/java/com/kumo/bubu/core/database/BuBuDatabase.kt`
- `app/src/main/java/com/kumo/bubu/data/local/entity/ExpenseRecordEntity.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ExpenseRecordDao.kt`
- `app/src/main/java/com/kumo/bubu/data/mapper/ExpenseRecordMapper.kt`
- `app/src/main/java/com/kumo/bubu/data/repository/OfflineExpenseRepository.kt`
- `app/src/main/java/com/kumo/bubu/domain/model/ExpenseRecord.kt`
- `app/src/main/java/com/kumo/bubu/domain/repository/ExpenseRepository.kt`
- `app/src/main/java/com/kumo/bubu/feature/expense/ExpenseFormState.kt`
- `app/src/main/java/com/kumo/bubu/feature/expense/ExpenseFormViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/expense/ExpenseFormScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/expense/ExpenseRecordsViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/expense/ExpenseRecordsScreen.kt`
- `app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt`
- `app/src/main/res/values/strings.xml`
- 對應 domain、Room、Repository、ViewModel 與 Compose 測試

### 完成條件

- 支出保存車輛、UUID、日期、選填時間、固定類別、非負整數新臺幣實付總額與備註；不保存里程且不改變目前里程。
- 可新增、編輯與確認後刪除；封存車輛不可新增但可查看、編輯及刪除既有紀錄。
- 類別限定為規格列出的稅費、保險、停車、通行費、罰款、洗車美容及其他，不提供退款負數。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
```

### 禁止提前實作的內容

- 不建立稅費提醒、保險提醒、年度週期、垃圾桶、CSV、備份或報表。
- 不讓其他支出更新或覆寫車輛目前里程。

---

## P3-06：重構服務工單主卡、多項目、附件與交易流程

**狀態：** 已完成（2026-08-09；服務工單重構、資料庫 v5、提醒與附件交易流程、58 項 JVM 測試、Android 測試 APK 編譯、完整 build、lint 及三向 code review 均通過；實機 instrumentation 驗收併入 P3-07）

**前置任務：** P3-01、P3-02、P3-03、P3-04、P3-05

### 任務目標

把既有單頁長表單重構為「主工單＋多個服務項目」流程，提供精確自動合計、可管理的常用項目、多張私有附件圖片，以及工單、項目、服務提醒、附件關聯與目前里程的一致交易。工資、施工費及檢查費改以一般服務項目保存，不再使用獨立工資、手動小計或工單總額覆寫欄位。

### 要建立或修改的檔案

- `Spec.md`
- `CONTEXT.md`
- `TASKS.md`
- `app/src/main/java/com/kumo/bubu/core/database/BuBuDatabase.kt`
- `app/src/main/java/com/kumo/bubu/core/database/AppContainer.kt`
- `app/src/main/java/com/kumo/bubu/data/local/entity/ServiceRecordEntity.kt`
- `app/src/main/java/com/kumo/bubu/data/local/entity/ServiceItemEntity.kt`
- `app/src/main/java/com/kumo/bubu/data/local/entity/ServiceTypeEntity.kt`
- `app/src/main/java/com/kumo/bubu/data/local/entity/VehicleReminderEntity.kt`
- `app/src/main/java/com/kumo/bubu/data/local/entity/ServiceAttachmentEntity.kt`
- `app/src/main/java/com/kumo/bubu/data/local/entity/PendingAttachmentDeletionEntity.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceRecordDao.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceItemDao.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceTypeDao.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/VehicleReminderDao.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/ServiceAttachmentDao.kt`
- `app/src/main/java/com/kumo/bubu/data/local/dao/PendingAttachmentDeletionDao.kt`
- `app/src/main/java/com/kumo/bubu/data/mapper/ServiceMapper.kt`
- `app/src/main/java/com/kumo/bubu/data/repository/OfflineServiceRepository.kt`
- `app/src/main/java/com/kumo/bubu/data/attachment/PrivateAttachmentStore.kt`
- `app/src/main/java/com/kumo/bubu/domain/model/BuiltInServiceTypeSeed.kt`
- `app/src/main/java/com/kumo/bubu/domain/model/ServiceRecord.kt`
- `app/src/main/java/com/kumo/bubu/domain/model/ServiceType.kt`
- `app/src/main/java/com/kumo/bubu/domain/repository/ServiceRepository.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormState.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormViewModel.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceFormScreen.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceWorkOrderCard.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceItemsSection.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceTypePickerSheet.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceAttachmentsSection.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceNotesCard.kt`
- `app/src/main/java/com/kumo/bubu/feature/service/ServiceLabels.kt`
- `app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt`
- `app/src/main/res/values/strings.xml`
- `app/schemas/com.kumo.bubu.core.database.BuBuDatabase/5.json`
- 對應 domain、calculation、migration、Room transaction、Repository、ViewModel、附件檔案及 Compose UI 測試

### 完成條件

- 主工單獨立卡片顯示車輛、預設為當下的本地日期與時間、目前里程、唯讀工單總金額、付款方式、標題及工單類型；里程欄下方顯示包含加油與服務工單最大值的「目前最新里程：XXXX 公里」。
- 補登較舊或較低里程的工單不降低 `Vehicle.currentOdometerKm`；新增、編輯或刪除任一加油或服務紀錄後，目前里程仍可由追蹤起始里程與兩類有效紀錄的最大值重建。
- 畫面沒有獨立工資、施工費、服務費、檢查費、手動小計或總額覆寫欄位；上述費用只能作為一般 `ServiceItem`。
- 每個項目以卡片顯示名稱、數量、單價、自動小計、下次保養里程、下次保養日期與備註，支援多項目、單項編輯及刪除；數量預設為 1。
- 項目小計等於數量乘以單價，工單總金額等於所有小計總和；數量與金額使用 `Long`、`BigDecimal` 或既有整數縮放規則，不使用 `Double` 累計，也不發生靜默溢位。
- 「＋ 新增保養／維修項目」開啟 `ModalBottomSheet` 或全螢幕選擇頁；第一個選項為「維修／服務項目（自訂輸入）」，並包含 `Spec.md` 指定的完整內建項目清單。
- 點擊常用項目後以數量 1 加入並返回主工單頁；自訂項目可選擇保存為常用類型。自訂類型可編輯、排序、封存及解除封存；內建類型不可改名或刪除，只能排序、隱藏及重新顯示。
- 「附件圖片」支援新增、預覽及刪除多張 JPEG、PNG 或 WebP；選取內容複製到 App 私有目錄，資料庫只保存相對路徑，不保存暫時 `content URI`，也不刪除使用者來源檔案。
- 新增或更新工單時，`ServiceRecord`、全部 `ServiceItem`、相關提醒、附件關聯及車輛目前里程在同一 Room Transaction 完成；任一步失敗時全部 rollback。附件檔案採可補償流程，失敗不得留下孤兒資料或遺失既有檔案。
- 新增、編輯、補登或刪除工單後，依日期、時間及同日順序重建服務提醒完成鏈；後補的中間工單可正確改接前後期提醒。
- 確認刪除工單後同步刪除相關項目、提醒、附件關聯與 BuBu 私有附件檔案並重算里程；快速重複刪除不會執行兩次。
- v4→v5 遷移保留既有實付總額，必要時建立「舊工單金額調整」項目，且在 API 26 外鍵行為下不遺失項目；未引用附件只在寬限期後進入持久化清理佇列。
- 備註使用獨立多行卡片；「儲存工單」清楚可見，支援 `imePadding`、`navigationBarsPadding`、防止快速重複點擊、儲存 loading、成功返回上一頁及具體失敗原因。
- 只建立服務項目提醒所需的資料與交易關聯；沒有提醒頁、WorkManager、系統通知、稅費或驗車提醒的提前實作。

### 驗證指令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

### 禁止提前實作的內容

- 不開始或拆解 Phase 4；不建立 Dashboard 摘要、統一時間軸、搜尋、篩選、詳情頁或跨類型歷史資料流。
- 不建立 Phase 6 的提醒頁、狀態計算、WorkManager、系統通知、稅費提醒、驗車提醒或平均里程通知。
- 不建立服務工單以外的附件管理、車輛封面、PDF 附件、垃圾桶、油耗、報表、CSV 或備份還原。
- 不加入網路、Firebase、登入、帳號、雲端同步、廣告、追蹤或未要求的權限。

---

## P3-07：執行 Phase 3 最終品質閘門與程式碼審查

> 2026-08-16 驗證註記：Android 16 實機仍有 Compose test host 在 `setContent` 後失去 hierarchy 的裝置相容性限制；改以 API 35 Google APIs x86_64 模擬器完成完整儀器驗收，所有測試均通過（0 failures、0 errors）。

**狀態：** 已完成（2026-08-16；`clean`、Debug APK、JVM tests、Android test APK、完整 41 項 compatible-device instrumentation 與 lint 均通過；Debug runtime 相依與原始碼範圍稽核通過。Standards／Spec 雙軸審查沒有未處理 finding，並已將服務項目確認流程、付款方式歷史保留行為與專案文件對齊現行實作。）

**前置任務：** P3-01、P3-02、P3-03、P3-04、P3-05、P3-06

### 任務目標

以 build、單元／Room／Repository／Compose UI／Samsung 實機測試、lint 與 Standards／Spec 雙軸 code review 驗收 Phase 3，修正確認缺陷後才開始 Phase 4。

### 要建立或修改的檔案

- `README.md`
- `CHANGELOG.md`
- `TASKS.md`
- 測試或審查發現問題時，只修改 P3-01 至 P3-06 列出的 Phase 3 檔案

### 完成條件

- 新增、編輯、刪除服務工單與服務項目、常用類型管理、精確自動總額、私有圖片附件、服務提醒關聯、支出紀錄、Transaction rollback、檔案補償與跨加油／服務里程重算均有自動化證據。
- 主工單卡片、多項目卡片、項目選擇流程、附件操作、鍵盤與導覽列 padding、防重複儲存、loading、具體錯誤及成功返回完成實機驗收。
- `clean`、debug build、unit test、instrumentation test、lint、依賴與原始碼範圍稽核全部通過。
- Standards 與 Spec review 均沒有未處理 finding；README、CHANGELOG 只宣告實際完成的功能。

### 驗證指令

```powershell
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
rg -n "Firebase|TODO|FIXME|GlobalScope|OdometerCorrection|OdometerReplacement" app README.md CHANGELOG.md
```

### 禁止提前實作的內容

- 不開始或拆解 Phase 4；不建立 Dashboard 摘要、統一時間軸、搜尋、篩選、詳情頁或跨類型歷史資料流。
- 不建立 P3-06 範圍以外的提醒、附件、垃圾桶、油耗、報表、CSV、備份還原、網路、帳號、雲端、廣告或追蹤。

---

## Phase 3 完成定義

只有 P3-01 至 P3-07 全部完成、TASKS.md 狀態更新、實機與自動化驗收通過，且雙軸 code review 沒有未處理的確認問題，Phase 3 才算完成。Phase 4 只能在此定義滿足後開始。

---

## Phase 6：提醒

**狀態：** 已完成（2026-08-22；Release Readiness 最終建置、相容裝置驗證與 Phase 7 差異式 review 均已關閉）。

### 任務目標

提供服務、手動、臺灣法定稅費／驗車與里程預估提醒，支援完成、延後、狀態顯示與本機通知排程；不增加網路、帳號或雲端同步。

### 已完成範圍

- 提醒中心、服務提醒完成鏈、手動與法定提醒、里程預估提醒。
- Android 13+ 通知權限、WorkManager 排程、狀態去重與停用後清理。
- 使用牌照稅、公路使用養護安全管理費與定期驗車的臺灣規則；不推算稅費金額。

### 驗證狀態

不重做 Phase 6 功能；其既有自動化與實機驗收已由本次 Release Readiness 的 clean build、113 項 unit tests、API 35 完整 59 項 instrumentation 與 lint 重新覆蓋。Phase 7 差異式 review 已完成，Phase 0–7 全數完成。

---

## Phase 7：資料匯出、備份與還原

**狀態：** 已完成；納入正式版本 **v1.0.0**（2026-08-23）。功能、實機驗收、最終品質閘門與 scoped Standards／Spec review 均已關閉。已以匿名測試資料完成還原、CSV ZIP 與完整 `.bubu` 備份驗證；Excel 已正確開啟含 UTF-8 BOM 的繁中資料。最新備份檔名為 `.bubu`，不再被系統附加 `.zip`。先前 `:app:packageDebug` 的 duplicate `app-metadata.properties` 已確認為重疊 Gradle 程序同時寫入 APK 輸出，不是依賴或 packaging rule 重複輸入。

**Release Readiness 驗證（2026-08-22）：** 以 Android Studio embedded JBR 25.0.2 的單一、非平行流程從 `clean` 重新建置，`:app:assembleDebug`、`:app:testDebugUnitTest`、`:app:assembleDebugAndroidTest`、API 35 Google APIs x86_64 AVD 的完整 `:app:connectedDebugAndroidTest` 與 `:app:lintDebug` 全數通過（lint 0 errors；log：`outputs/phase7-acceptance/release-readiness-final-api35.out.log`）。Git baseline 為 `ecb313d392d5ef9b5510fe547106605cbaa9d059`。Android 16 實機的完整套件仍有 isolated Compose screen test 在前序測試後遺失 host hierarchy；同一測試在 API 35 完整通過，且 Dashboard isolated regression 已通過，故列為測試 host lifecycle 相容性風險而非產品功能 blocker。baseline 後 Standards review 無 blocker，最終 Spec re-review 為 0 findings；quality gate 已關閉。

**追蹤 tickets：** `.scratch/phase-7-export-backup/issues/`

---

## Post-v1 維護：油耗統計排除

**狀態：** 已完成（2026-08-24）；JVM tests、debug build、lint、Android test APK、SM-S9280 Android 16 的 v12→v13 Room migration 儀器測試（8 項）與覆蓋安裝驗收均通過。

### 已完成範圍

- 以滿箱油耗區段終點保存未檢查、確認納入或排除的油耗統計判定；不刪除原始加油資料。
- 報表、首頁最近平均與歷史油耗摘要一致忽略已排除區段，同時保留該紀錄作為下一區段錨點。
- 舊有與匯入資料維持未檢查且仍納入統計，設定頁提供疑似異常資料的逐筆確認／排除流程。

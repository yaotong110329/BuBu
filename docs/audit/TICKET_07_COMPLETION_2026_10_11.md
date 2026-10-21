# Ticket 07 — 返回前景與跨日刷新保養日期

完成日期：2026-10-11。01–07 完成；08–16 共 9 張待完成，CB-12 真實 Google 帳號驗收獨立 pending。

## 實作與變更檔案

- `core/state/LocalDateState.kt`：每個 retained ViewModel 共用一個日期 Flow，訂閱及返回前景時立即讀取本地日期；前景計時至午夜，最多 60 秒重新檢查一次以涵蓋時鐘變動。同一 scope 只有一個受 isActive 防護的計時工作，pause／dispose 或 ViewModel 清除即取消。
- `feature/dashboard/DashboardViewModel.kt`、`feature/reminder/RemindersViewModel.kt`：日期改變時使用 flatMapLatest 重新取得既有 repository 的保養衍生資料，日期與計算結果一起传遞。首頁預測、手動提醒狀態亦使用同一日期。未改 calculator／worker 到期規則、正式資料或 schema。
- 兩頁 `Screen.kt` 的 Route 使用 LifecycleResumeEffect 交付生命週期事件；提醒頁亦改用 collectAsStateWithLifecycle。沒有 Composable DAO 呼叫。提醒列表增加既有慣例的測試 tag，供公開 UI seam 實際捲動驗證。
- 新 `DashboardViewModelTest.kt`、`DateRefreshLifecycleTest.kt` 及 `RemindersViewModelTest.kt` 日期回歸：公開 ViewModel／真實 Room repository／Compose lifecycle；DAO 僅建立 fixture，沒有 DAO 或私有方法斷言。
- `TASKS.md`、issue 07 與本完成文件更新；沒有新依賴、權限、網路、精準鬧鐘、資料格式或導航設計變更。

## 實際驗證

所有 logs、preserved XML 與 summary 均在 `outputs/release-1.1.1-audit/`。

| 檢查 | 結果與證據 |
| --- | --- |
| 背景停止訂閱後，同一提醒 ViewModel 重新收集 | 日期型與混合規則在 8/15→8/16 expected DUE、actual UPCOMING；actual red→green：ticket-07-reminders-date-{red,green}.log |
| 同一首頁 ViewModel 重新收集 | expected dueCount 2、actual 0；actual red→green：ticket-07-dashboard-date-{red,green}.log |
| 畫面持續前景跨日，repository 無新 emission | expected DUE、actual UPCOMING；actual red→green：ticket-07-midnight-{red,green}.log。重複前景事件不啟動第二個工作；虛擬時間推進 60 秒 |
| 真實 Room／首頁 Compose 的短暫 pause→resume | 同一 ViewModel、訂閱保持活躍、沒有 Room 寫入；actual red 在刷新條件逾時，green 正確顯示 2 項到期：ticket-07-dashboard-lifecycle-actual-red.log、ticket-07-dashboard-lifecycle-green.log |
| 真實 Room／提醒頁 Compose 的短暫 pause→resume | 日期型與混合規則均正確轉到期，正式工單保持不變，公開 repository 新日期結果一致；actual red→green：ticket-07-reminders-lifecycle-confirmed-red.log、ticket-07-date-lifecycle-green-final.log（2／0／0／0，1m5s） |
| 最終 assembleDebug／完整 JVM | 成功，207／0 failures／0 errors／0 skipped；ticket-07-build-unit-final.log（1m24s）、ticket-07-jvm-summary-final.json |
| 最終相關 Android 裝置回歸 | API35：11／0／0／0，含兩頁生命周期、DashboardScreen、RemindersScreen、既有兩項保養通知整合；ticket-07-connected-final.log（2m26s）、ticket-07-connected-summary-final.json |
| 最終 lintDebug | 0 fatal／0 errors／126 既有 warnings；ticket-07-lint-final.log（5m4s）、ticket-07-lint-summary-final.json、ticket-07-lint-final.xml |
| whitespace／scope | git diff --check 成功；僅既有 CRLF 提醒：ticket-07-diff-check-final.log |

保留測試架構失敗，沒有把它們冒稱產品 red：最初 @After 非 void、摘要字串／LazyColumn 捲動 fixture，以及一次首頁 Compose hierarchy 尚未建立。最後改為 Android resource 文案、實際列表捲動及明確畫面就緒 barrier；完整 11 項回歸已涵蓋最終測試版本。

## Standards

0 項硬性違規、0 項阻塞；1 項低優先判斷性建議：Dashboard 與 Reminders 的已知日期 fixture 可共用以減少重複。目前兩份 worked example 內容一致，沒有產品正確性影響，未擴大本票整理範圍。日期 Flow／單一工作與取消、UI→ViewModel→repository、錯誤邊界、測試 scope 與私有檔案清理皆符合標準。審查代理僅唯讀，不把靜態審查當成測試結果。

## Spec

0 findings、0 阻塞；保留 ViewModel 的新日期、前景／跨日更新、日期與混合規則、單一計時工作及限制範圍均符合 issue 07。兩軸固定點為 Release 1.1.1 HEAD e12379551c1cf9b48fb17ddc18f424ad31504700，只審 Ticket 07 的未提交變更，不納入既有 01–06 修改。

## 版本與環境限制

App 1.1.1（code 3）、DB 15、backup format 1；SDK 37／37／26，AGP 9.3.1、Gradle 9.6.1、Kotlin 2.4.10、KSP 2.3.10、Room 2.8.4、Compose BOM 2026.06.01／Material 3 1.4.0、Activity 1.13.0、Navigation 2.9.8、Lifecycle 2.11.0、DataStore 1.2.1、WorkManager 2.11.2、Coroutines 1.11.0、Serialization 1.8.1、Credentials 1.6.0、Google ID 1.2.0、Play Auth 21.6.0、Core KTX 1.19.0、JBR 25.0.2。版本均未更動。

最終 APK SHA256：827310d8b0185045da2b2fe8e682413faed7936fbccbebc0887b7153def2c466。

只使用専用 BuBu_API_35／emulator-5556、隔離 ADB 5041 與 connected device guard。未操作手機、其他 AVD 或真實 Drive。裝置測試結束後核對 AVD 名稱再關閉自有模擬器，以釋放記憶體供 lint 使用；沒有終止其他 Java 程序。

日期與生命週期為受控實驗，沒有修改 Android 系統日期，也沒有宣稱真正跨夜或真實手機驗證。worker 保持透過同一 ServiceRepository／calculator 取得今日狀態，既有通知整合亦通過；沒有宣稱強制 worker 系統日期切換已執行。Compose 生命周期 API 與清理語意依 [Android 官方文件](https://developer.android.com/topic/libraries/architecture/lifecycle?hl=en)。CB-12 仍待真實帳號驗收。

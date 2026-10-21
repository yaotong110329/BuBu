# Ticket 05 — 外部操作旋轉與私有下載清理

完成日期：2026-10-10。01–05 已完成；06–16 共 11 張尚未結案。CB-12 真實 Google 帳號驗收仍獨立 pending。

## 實作與範圍

- Google 授權與 CSV 選檔使用 ViewModel 保留的操作編號，當前 Activity 的 launcher 按該編號註冊；結果只消耗一次。Google gateway 將延遲 resolution 交給 ViewModel，旋轉後不向舊 launcher 發起操作，也不重複啟動已交付的 resolution。
- 旋轉保留原操作；離開設定頁會失效 pending 操作並清除未確認的預覽。晚到的舊 ActivityResult 與 SDK callback 不會接到新請求。授權取消與失敗可重試；access token 只在此次操作的記憶體中使用。
- 還原 ViewModel 擁有下載檔清理責任；取消、無效預覽、成功還原及 owner 清除釋放自有下載。正式 repository 以每次下載獨立檔名和所有權集合隔離來源檔；在 IO 完成與回呼交付之间取消仍清理，保留 CancellationException。清理失敗有紀錄，可用的 owner 保留失敗參照供重試。
- connected tests 的 Gradle 防護要求明確 serial／AVD 與 ANDROID_SERIAL，ADB 只能有該模擬器。helper 與測試 action 執行前均檢查；configuration cache 儲存和重用均通過。
- 主要變更：app/build.gradle.kts、README.md、CloudBackupRepository／OfflineCloudBackupRepository／PrivateCloudDownloadStore、CloudBackupViewModel／RestoreViewModel／CsvExportViewModel、GoogleDriveAuthorizationCoordinator、BuBuNavHost、SettingsScreen／CloudBackupListDialog，以及相應 JVM、導航 rotation 與 debug-only 外部邊界 fixtures。沒有 schema、backup format、權限、簽署、dependency 版本或正式產品資料變更。

## 驗證

| 檢查 | 實際結果 | 本機證據（outputs/release-1.1.1-audit/） |
| --- | --- | --- |
| 原 Google rotation 回歸 | 修正前失敗 | ticket-05-google-red.log |
| 最終 App／測試 APK 建置與 JVM | 成功；178 tests，0 failures／errors／skipped | ticket-05-build-unit-final.log、ticket-05-cleanup-final.log；JVM XML |
| 完整 ActivityResult／導航回歸 | API 35 AVD 11／0／0／0 | ticket-05-connected-11-green.log、ticket-05-connected-11-green.xml |
| 清除空 super 呼叫後針對性回歸 | 下載取消與確認還原 2／0／0／0 | ticket-05-cleanup-final.log；connected XML |
| 最終 lint | 0 errors／fatal，126 warnings；兩個新增 EmptySuperCall 已移除 | ticket-05-lint-final.log；lint-results-debug.xml |
| 裝置防護 | 未指定、實體裝置同時連線、AVD 名稱不符均拒絕；指定 AVD 通過，cache 重用仍執行檢查 | device-guard-unspecified.log、device-guard-physical-connected.log、device-guard-wrong-avd.log、device-guard-cache-first.log、device-guard-cache-reuse.log |
| whitespace | git diff --check 通過；只有既有 LF／CRLF 提醒 | ticket-05-diff-check.log |

11 項涵蓋 CSV 旋轉、取消與舊選檔結果隔離；Google 成功／取消／失敗旋轉、重試與結果重送；resolution 延遲到重建後、離頁後延遲 resolution 和旧結果；下載預覽旋轉後取消／確認，以及正式 repository 不刪除未擁有檔案。下載測試使用正式 archive reader、備份／還原 repository、Room 與 UI；原檔與另一操作下載檔均保留。JVM 另覆蓋 IO 完成、caller 尚未接到結果時取消的檔案清理。

## Standards

復審 0 項新的確認問題；先前的取消交付遺留與清理例外無紀錄兩項已關閉。分層、資源規則、操作 ID、離頁失效及 launcher 分工符合指定範圍。最終 lint 指出的兩個空 super 呼叫另已移除并重跑驗證。

## Spec

復審沒有未處理的確認 finding。Ticket 05 的操作對應、單次消耗、清理自身下載及設定頁離開／返回都有實作與執行回歸。未擴張到 process death，未持久化 token。

Standards：0 個未處理確認問題；Spec：0 個未處理確認問題。

## 實際版本與環境限制

App1.1.1（code3）、DB15、backup format1；compile／target／min SDK37／37／26；AGP9.3.1、Gradle9.6.1、Kotlin2.4.10、KSP2.3.10、Room2.8.4、Compose BOM2026.06.01／Material3 1.4.0、JBR25.0.2。版本均未變更。

- 使用 BuBu_API_35／emulator-5554，執行前 ADB 只有該模擬器。未安裝／卸載手機 App，也未修改實際 Drive 備份。
- Google SDK／Drive 是 debug-only fixture 邊界；實際 ActivityResult、導航、Activity recreation、檔案與還原流程有執行。實際帳號、Google 選帳號畫面、網路及 CB-12 五份保留等未由本票驗證。
- 初次 Kotlin daemon 無法連線，備援建置停滯已停止；改用同版 Gradle 的 in-process Kotlin compiler 與既有已驗證的 local artifact init 完成。正常 sandbox helper／apply_patch 環境仍異常，僅以核准的工作區內操作接續。一次自動核准審查因額度無法完成，該批未執行；額度恢復後重新執行。
- 中途有測試 fixture 失敗：兩個不相容 scheduler、FileProvider 未開放路徑，以及旋轉後未將重試按鈕捲入畫面。修正 fixture、依 Android 主執行緒契約送出結果後，完整回歸與最終針對性驗證通過。未擴大 FileProvider roots；失敗紀錄保留。

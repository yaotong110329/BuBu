# BuBu 專案優化檢查 — 2026-10-09

本次只檢查目前專案與待完成工作，沒有修改 App、安裝 APK、操作手機或執行 connected tests。加油缺漏補登已於 2026-10-05 完成；資料修復與本次程式審查分開驗收。

## 範圍與證據

- 基準沿用 Release `v1.1.1`；目前 HEAD 為 `e12379551c1cf9b48fb17ddc18f424ad31504700`。
- `git log v1.1.1..HEAD --oneline` 為空。此次檢查包含 `git diff v1.1.1` 的未提交修改及新增檔案，不能把基準之前的歷史 commit 當成本次變更。
- 規則來源：AGENTS.md、Spec.md、CONTEXT.md、docs/adr，以及使用者已核准的 16 張 hardening tickets。
- 以下明確區分既有回歸失敗、靜態程式證據與尚待建立的故障驗證；沒有重新執行的測試不算本次通過。

## Standards

1. **測試流程缺少實機隔離防護。** README.md:90 仍直接示範 `connectedDebugAndroidTest`；app/build.gradle.kts 沒有執行前的實機阻擋檢查，也沒有 scripts／CI 裝置防護。TASKS.md:1888 明確要求「後續 connected 驗收必須隔離實機，只使用明確指定的可拋棄模擬器」。這是已確認的工作流程缺口，應在下一次儀器測試之前落實可執行防護與安全指令，不只是文件提醒。不是 App production bug。

2. **新增通知監聽沒有錯誤邊界。** AppContainer.kt:300–306 的 applicationScope collector 直接執行 `reconcile()`，其 DataStore 狀態讀寫在 DataStoreMaintenanceSummaryStateStore.kt:14／19。AppContainer.kt:135 使用 SupervisorJob，但 SupervisorJob 不會處理未捕捉的例外。普通儲存失敗可逃出 coroutine；應在適當邊界處理可恢復錯誤並保留取消語意，再注入失敗驗證。靜態缺口已確認，閃退尚未重現。Spec.md:978 要求捕捉 Room 等錯誤；DataStore／通知部分是同一可靠性方向的判斷，不是該句原文明列的項目。

沒有另外確認的分層違規或需要單獨重構的 code smell；不重報 01–04 已解決的舊問題。

## Spec

1. **Google 授權旋轉後失效（Ticket 05，已重現）。** 票要求「Google 授權結果對應原操作且只執行一次」。BuBuNavHost.kt:265–266 仍以 `remember` 保存操作及帳號，callback:273–275 在狀態消失時略過操作。既有 `outputs/release-1.1.1-audit/ticket-05-google-red.log:86` 記錄旋轉回歸失敗。CSV 回歸已有綠燈，不能據此將整張票結案；下載預覽的檔案歸屬與清理也尚需完成。

2. **補登未知時間無法留白（新增確認）。** Spec.md:236／478／876 要求選填時間、不得虛構未知時間、CSV 未知時間留白。FuelFormState.kt:24 預設當下時間，domain 可以接受空白；但 FuelFormScreen.kt:255–260 使用唯讀選時欄位，DateTimePickerField.kt:52／101–122 只有選時、確認與取消，沒有清除入口。補登時忘記時間的使用者無法透過 UI 保存真正的未知值。應提供資源化的「時間未知／清除」操作，保存 null，並驗證排序與 CSV 留白；不需要 DB schema 變更。

3. **Drive 清單沒有讀取後續分頁（Ticket 10，靜態確認）。** 票要求「請求並跟隨 nextPageToken」。GoogleDriveBackupDataSource.kt:98 只讀第一頁，fields 未包含 token；較舊備份可能漏列，保留最新五份的清理也無法依完整清單執行。應以多頁 scripted transport 驗證清單與上傳／保留 orchestration；不以真實帳號刪檔作為開發測試。

4. **還原預覽缺少資料語意檢查（Ticket 11，靜態確認）。** 票要求「擁有者一致性、合法 enum、數值範圍」。BackupArchiveReader.kt:65–104 主要檢查識別、數量與參照存在；:89 沒有核對來源服務項目與提醒是否屬於同一車輛，也沒有完整數值範圍檢查。checksum 正確不代表資料語意正確。需先建立跨車關聯／負金額等非法 archive 回歸，再補適用 invariant；保留合法舊 format 1 的相容性。

5. **還原故障情境仍缺驗收證據（Tickets 12／13）。** Spec.md:929 要求失敗時回復復原備份。磁碟不足、取消、程序終止及重新開啟後附件一致性尚未完成完整驗證。Ticket 04 的復原檔發布修正不能代替上述驗收；目前不能推論已存在永久資料遺失，也不能宣稱這些情境全部安全。

## 既有 tickets 的現況

| Ticket | 交付內容 | 目前狀態 |
| --- | --- | --- |
| 01 | 備份／還原大小契約 | 已完成 |
| 02 | 加油表單溢位 | 已完成 |
| 03 | 保養摘要通知生命週期 | 原票完成；本次另發現通知監聽錯誤邊界需補強 |
| 04 | 復原備份可靠發布 | 已完成 |
| 05 | 旋轉後外部操作與下載清理 | 部分完成；Google 授權回歸仍失敗 |
| 06 | 系統回收後恢復表單草稿 | 待完成；先建立 process recreation 證據 |
| 07 | 跨午夜／前景刷新保養日期 | 待完成；DashboardViewModel.kt:57 仍捕捉建立時日期 |
| 08 | 通知定位正確車輛與提醒 | 待完成；需雙車與冷／熱啟動整合回歸 |
| 09 | Google 帳號與連結狀態一致 | 待完成；需多帳號邊界回歸 |
| 10 | Drive 分頁與最新五份保留 | 待完成；分頁缺口已確認 |
| 11 | 還原預覽語意驗證 | 待完成；語意檢查缺口已確認 |
| 12 | 還原磁碟不足與取消安全 | 待完成；故障注入驗證缺口 |
| 13 | 還原途中程序終止／重開安全 | 待完成；程序實驗驗證缺口 |
| 14 | 最近八個區段的油耗異常判斷 | 待完成；FuelEconomy.kt:180–183 仍先排序數值再取八筆，實際取最高八筆 |
| 15 | 保養歷史還原後衍生狀態 | 待完成；正常歷史與非預設週期整合驗證缺口 |
| 16 | 正式版能力與文件對齊 | 待完成；不得以改文件擅自縮減規格 |

總計 4 張完成、1 張部分完成、11 張待完成，即還有 12 張尚未結案。CB-12 的真實單帳號雲端附件／五份保留／解除再連結驗收仍另行追蹤。

## 後續執行與效能候選

- 任何下一次儀器測試之前，先落實可拋棄模擬器防護。
- 延續已授權的 Ticket 05，完成 Google 授權及下載清理的回歸；接續處理既有資料／備份相關 tickets。未知時間與通知錯誤邊界是本次新增的具體修正候選。
- 大量照片備份的記憶體用量值得量測。OfflineBackupRepository.kt:133–137 仍將所有附件讀成 ByteArray 並保留在清單；共同大小上限不等於手機 heap 保證。尚未重現 OOM，應先量測再決定串流改善，沿用 .bubu format 1。
- 共用油耗區段計算、雲端錯誤契約與大型導航檔的責任整理，沿用 TECH_DEBT.md 的最小範圍策略。沒有證據支持全面重構、拆多模組或為優化而升級全部依賴。

## 驗證與版本

- 本次：雙軸靜態審查、既有回歸日誌核對、tickets／文件／實作核對、`git diff --check` 通過；只出現既有 LF／CRLF 提醒。
- 本次沒有執行 build、JVM tests、lint 或儀器測試，因此不提供新的通過數字。未改程式與依賴，文件檢查不需要啟動 App 品質閘門。
- 實際專案版本：App 1.1.1、DB 15、backup format 1；compile／target／min SDK 37／37／26；AGP 9.3.1、Gradle 9.6.1、Kotlin 2.4.10、KSP 2.3.10、Room 2.8.4、Compose BOM 2026.06.01。版本未變更。

Standards：2 項，最優先補齊實機隔離防護；Spec：5 項，已重現的 Google 授權旋轉失效仍未修復，還原語意與故障安全仍需完成驗證。


## 2026-10-10 執行更新

Ticket 05 及 connected 裝置防護已完成；最新狀態為 01–05 完成、06–16 共 11 張待完成。Google 授權旋轉與下載生命週期回歸通過，雙軸復審未留確認問題。JVM 178 項、完整導航回歸 11 項及清除 lint 警告後的 2 項針對性回歸通過；lint 0 errors／126 warnings。見 `TICKET_05_COMPLETION_2026_10_10.md`。通知監聽錯誤邊界、未知時間 UI、其餘 tickets 及 CB-12 尚待處理。

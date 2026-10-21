# 15 — 驗證保養歷史還原後的衍生狀態

**What to build:** 完整備份還原後，使用者看見的保養基準與提醒狀態仍由實際服務工單及每車規則正確導出。

**Blocked by:** None — can start immediately.

**Status:** completed

**Audit reference:** Test Gap / 非預設規則與工單歷史整合；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 以兩台車、汽車／機車、封存資料、內建／自訂服務類型及非預設週期建立真實 repository integration fixture。
- [x] 執行 source DB→正式備份→reader→正式覆蓋還原→target DB，驗證所有規則、UUID、歷史關聯與必要附件 bytes。
- [x] 在同一固定日期／里程下驗證實際工單優先於啟用的 0 km 起始基準；沒有工單或 opt-in 時為 UNKNOWN，不猜測服務日期。
- [x] 涵蓋工單新增、回填日期、編輯與刪除的合理完成鏈及導出結果；停用、封存与不適用類型不納入提醒。
- [x] 區分單純 DTO 欄位守恆與完成鏈語意；先建立真實正常 fixture，只在發現確定缺陷時做最小修正。

**Scope / evidence:** 已通過的 audit round-trip 繼續保留；不得把為參照守恆設計的原 fixture 當成時間順序正確的完成鏈證據。

**Plan (2026-10-18):** 使用真實Vehicle／Fuel／Service repositories與隔離Room／DataStore建立兩台有效CAR／MOTORCYCLE，另以封存車作排除控制；內建／自訂及封存／停用類型、非預設規則、正常工單與實際附件。先驗正式備份還原及固定2025-01-15／5000km的公開衍生狀態，再以獨立垂直案例驗真正新增／回填／編輯／刪除完成鏈。已約定公開Repository／正式Reader／檔案seam延續；沒有新增接口／依賴。每片實際判定、最終品質閘門與雙軸審查分開記錄，未執行不宣稱通過。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

**Completion (2026-10-20):** 兩個正常真實 Repository 案例通過，驗證五種保養狀態、完整正式還原、規則／UUID／附件內容，以及新增回填、改期與刪除後的精確完成鏈。完整 JVM243、相關裝置27、Debug／test APK 與 lint 全部通過；lint0 errors／125既有 warnings，新增0／移除0，雙軸0未解。審查後拆分測試210行／fixture121行；archive assertion 依 publicId 比完整 DTO，保留顯式順序欄位與所有附件 bytes。206個正式來源／設定雜湊未變，沒有需修正的已重現產品缺陷。結案與限制：docs/audit/TICKET_15_COMPLETION_2026_10_20.md；CB-12獨立pending。

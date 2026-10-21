# 14 — 以最近八個區段判斷油耗異常

**What to build:** 疑似異常油耗以同一車輛最近有效區段作比較，不被歷史最高油耗主導。

**Blocked by:** None — can start immediately.

**Status:** completed

**Audit reference:** L01 / Low / 已重現；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 先重現八個舊 30 km/L、八個近期 10 km/L、下一個 10 km/L 的 fixture；比較基準應為近期 10，不是舊 30。
- [x] 先按有效區段終點的時序取最近最多八個，再在該 window 排序計算中位數；測試同日穩定排序與不足八個。
- [x] 排除的歷史區段不污染比較基準，兩台車各自隔離；candidate 不被算入自己的 baseline。
- [x] 修正只影響警告，不自動排除紀錄、不改原始費用／里程或下一個滿箱錨點；既有使用者統計判定保留。

**Scope / evidence:** 不合併全專案的油耗 accumulator；現有重複迴圈屬技術債，非此票必要重構。

**Execution (2026-10-18):** 完成。先取得實際 red（10km/L錯用30基準），再最小修正為先選最近八段、才排序數值。五個 domain 與兩個 ViewModel 回歸案例、完整 JVM243/0、相關裝置8/0、lint0 errors／125 warnings、diff check及兩軸最終審查0未解均通過。10/17 首次命令因自動核准審查額度未執行，10/18重試已核准，未繞過審查。詳細記錄見 docs/audit/TICKET_14_COMPLETION_2026_10_18.md。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

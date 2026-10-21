# 07 — 跨午夜與返回前景刷新保養日期

**What to build:** 跨午夜或長時間背景返回後，首頁與提醒頁以今天日期顯示保養状态，不需要使用者修改資料才刷新。

**Blocked by:** None — can start immediately.

**Status:** completed

**Audit reference:** M03 / Medium / 捕捉固定日期的靜態問題；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 用可控制日期與 lifecycle 的真實 ViewModel seam 重現：到期前一天開啟，日期前進到到期日，Room 未發出新資料。
- [x] 首頁與提醒頁在返回前景／跨日後重新計算；保留同一 ViewModel 也使用新日期。
- [x] 畫面狀態與同一資料下使用新日期的 calculator／worker 一致，日期型與混合里程／日期規則皆涵蓋。
- [x] 日期 signal 不因 recomposition 重複建立 collector，不加入網路同步、精準鬧鐘或額外權限。

**Scope / evidence:** 維持無時區本地事件時間與既有到期語意；不要藉此更改規則。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

**Progress (2026-10-11):** 提醒頁 retained ViewModel 在 8/15 背景停止收集、8/16 到期日重新收集，完全不更動 repository 資料；日期型與混合兩項均 expected DUE、actual UPCOMING，actual red 已取得（1 failed，5m46s）。LocalDateState／提醒頁日期訂閱修正後五項 group green（4m8s），logs 與 XML 已保存。首頁、短暫返回／跨午夜與 Compose lifecycle 接線尚未完成，保持 in-progress。環境變慢時已核對 AVD 名稱後只關閉自有 emulator-5556。前輪一次文件更新因核准審查額度失敗未執行，使用者續作後已恢復，未繞過審查。

**Completion (2026-10-11):** 全部驗收已完成；首頁／提醒頁公開日期與 Room Compose 短暫返回 actual red→green、跨午夜 group green；最終 build／JVM 207／裝置 11 全綠，lint 0 errors／126 既有 warnings、雙軸審查無阻塞。完整證據與受控实验／fixture 限制見 `docs/audit/TICKET_07_COMPLETION_2026_10_11.md`。01–07 完成，08–16 共 9 張待完成，CB-12 保持 pending。

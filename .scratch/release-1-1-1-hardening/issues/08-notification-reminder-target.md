# 08 — 通知定位正確車輛與提醒

**What to build:** 點擊車輛提醒通知時直接看到對應車輛與目標提醒；點擊保養摘要時進入可處理保養的提醒入口。

**Blocked by:** None — can start immediately.

**Status:** completed

**Audit reference:** M06 / Medium / 尚未跑通知入口整合重現；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 先建立通知→Activity→導航→車輛選擇的雙車整合 regression，第二台車目標不能被第一台車篩選遮蔽。
- [x] 普通通知的目標車輛與提醒被選取／標示，捲動定位不依賴假設固定的前置 row 數。
- [x] 保養摘要通知有明確目的入口；保留既有四個底部頁籤，不重新設計導航圖。
- [x] 測試多個維護區塊、冷啟動與 App 已開啟；目標已刪除或車輛封存時安全顯示有效入口，不 crash 或導向錯誤紀錄。
- [x] 導航使用本機身分與有效資料解析，通知重送不建立重複返回堆疊。

**Scope / evidence:** 03 的去重修正不阻塞此票；僅修通知定位所需導航行為。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

**Plan (2026-10-11):** 沿用已授權公開 seam，先從正式 Worker 產生的 Notification PendingIntent→MainActivity→實際導航→雙車選擇取得 regression；再逐片驗證動態保養區塊捲動、目標標示、摘要入口、冷啟動／已開啟／重送，以及刪除或封存目標。每片 actual red→最小 green；最後 build／完整 JVM／相關隔離 AVD／lint 與雙軸 review。只操作可拋棄 AVD，不碰手機或 Drive。

**Progress (2026-10-12):** 正式 Worker→系統 PendingIntent→實際 MainActivity／導航的雙車回歸已取得 actual red（Selected=false；修正 observer 後仍在 5 秒等待下為 false）與最終 green（1／0／0／0，4m14s）。ViewModel 依目前有效提醒資料解析目標車輛；相關 Date Flow 保留。非 void fixture 與自動捲動造成的 selector viewport 失敗均獨立保存。接著執行不手動捲動的多保養區塊定位回歸；本票仍 in-progress。測試已改用專用 workspace AVD BuBu_Ticket08_API35／emulator-5558／隔離 ADB5042，紀錄 launcher 與 child ownership；原 5556 後由不同程序占用，未重用或關閉它。

**Progress (2026-10-13):** 多保養區塊捲動 actual red→green（1 test／0 failures；21.463s）；目標卡片 selected regression actual red→green（1／0；15.78s），新增 Material 3 primary 外框與輔助選取語意。Debug／Android test APK 編譯通過。首次開機 ANR 獨立記錄，不計作產品 assertion。接著驗證通知返回後重送；本票仍 in-progress。


**Completion (2026-10-13):** 五項完成條件均驗證；最終build／完整JVM208／相關Gradleconnected14全綠、lint0 Error／0 Fatal／126 Warning、diff --check通過，雙軸0未解findings。重送摘要保存中斷服務草稿，僅同車同項目回復；連續中斷清除舊導航快照，actual X→summary→Y→summary→Y red→green。所有fixture與啟動失敗分開記錄，沒有冒充產品actual red或最終gate。檔案、版本、完整證據與限制見 `docs/audit/TICKET_08_COMPLETION_2026_10_13.md`。未操作手機／Drive；CB-12獨立pending。

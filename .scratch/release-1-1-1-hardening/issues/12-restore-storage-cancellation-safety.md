# 12 — 驗證磁碟不足與取消時的還原安全

**What to build:** 在儲存空間不足或使用者／協程取消時，還原仍保護完整資料與附件；先建立可重現故障測試，再修必要的安全缺口。

**Blocked by:** None — can start immediately.

**Status:** completed

**Audit reference:** M11 / Medium / Test Gap；未證明永久資料遺失；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 在正式還原流程注入短寫／ENOSPC，涵蓋復原建立、附件 staging、DB 插入／commit 周邊與檔案清理；不能只用非法 enum 代替儲存故障。
- [x] 在對應 suspending boundary 取消，檢查完整舊或完整新 snapshot；不允許混合快照，所有被引用附件 bytes 必須可讀。
- [x] 涵蓋復原補償再次失敗，確認有效復原檔保留並有可理解的結果；不把補償失敗直接宣告為資料毀損。
- [x] 儲存空間預檢考慮此次 candidate、incoming attachments、目前復原快照與必要 SQLite/WAL 開銷；記錄可保證的限制。
- [x] 如有 failing regression，只增加最小 filesystem fault seam／必要補償保護；若現有保護足夠，交付通過測試與證據，不做預防性大改。

**Scope / evidence:** 可獨立以目前實作建立故障測試，故 01／04 不是真正 blocker；不需要先建通用檔案框架。

**Completion (2026-10-17):** 最終build／完整JVM236／test APK、相關裝置21/0/0/0、lint0 errors／125 warnings、diff check與兩軸0未解均完成。新增11項案例涵蓋真實SQLite FULL、實際部分寫入ENOSPC、取消與commit交界補償失敗，全部比較公開快照／引用bytes／有效復原檔。空間預檢heuristic及非reservation限制明載；僅自有AVD，未操作手機或Drive。見docs/audit/TICKET_12_COMPLETION_2026_10_17.md。

**Plan (2026-10-17):** 沿用已確認的公開 Backup／RestoreRepository、正式 Reader／Writer 與真實 Room seam；fixture DAO／SQL 僅建資料或注入實際儲存故障，結果由公開匯出完整資料與附件 bytes 判讀。先以現有 archive output seam 卡住復原寫入後取消，取得 actual red／green；後續每片測試完成才修最小保護，不預建通用 filesystem framework。依序執行 build、JVM、相關裝置、lint，Gradle 不重疊。程序強制終止另屬13。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

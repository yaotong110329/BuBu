# 13 — 驗證還原中 process restart 的恢復能力

**What to build:** 還原期間 App 程序終止後，重新開啟能得到完整可用資料與附件，并保留有效復原途徑。

**Blocked by:** None — can start immediately.

**Status:** completed

**Audit reference:** M11 / Medium / Test Gap；尚未做程序终止實驗；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 在 DB clear 前、交易進行中、commit 後及舊附件清理中建立可控制的 process termination／reopen 實驗。
- [x] 重開真实 Room 與私有附件目錄後，比對完整舊或完整新快照的 UUID、關聯、金額、規則及引用附件 bytes。
- [x] 交易前／中終止不留下混合資料；commit 後不刪新引用檔，遺留的舊／暫存檔由安全歸屬與清理策略處理。
- [x] 最新可供使用的復原備份仍能通過 reader 並恢復；必要的手動復原流程可驗證，不默默重複未確認的覆蓋。
- [x] 僅在實驗揭露缺口時做最小持久化／清理修正，保持普通還原與例外補償測試通過。

**Scope / evidence:** 程序終止與 coroutine cancellation 為不同故障；可獨立建立實驗，不虛構與 12 的阻塞關係。

**Completion (2026-10-18):** 四情境同一最終APK真實kill／freshprocess重播全綠；普通還原22項通過，另兩個host協作方法無參數明確skip；完整JVM243，lint0 errors／125既有warnings，兩軸0未解。審查指出私有copied archive漏驗後，取得實際殘留red→green；再為新清理策略取得active副本並行red→green，以24h／canonical UUID／私有目錄／程序內所有權保護回收。資料、附件、recovery及明確手動復原均由正式公開archive驗證。詳見docs/audit/TICKET_13_COMPLETION_2026_10_18.md；不宣稱裝置斷電或所有可能指令位置都已覆蓋。

**Plan (2026-10-17):** 沿用已確認真實Repository／Room與正式archive seam。先以具名disk-backed WAL fixture在第一次clear前產生可觀察gate，host核對自有AVD／private ADB5042與App PID後真正force-stop；另一次instrumentation重開同一檔案，先比對原快照／引用bytes，再驗證有效recovery與明確手動restore，不自動重做覆蓋。第一片取得實際判定後才加入交易中、commit後及舊檔清理中的終止點。安全歸屬／24h grace與pending清理另由公開維護入口驗證。所有實驗及普通品質閘門分別記錄，不操作手機／實際Drive、不測裝置斷電。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

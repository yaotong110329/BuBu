# 01 — 統一備份與還原大小契約

**What to build:** 使用者看到成功的本機資料備份或復原備份時，該檔案一定符合既有還原契約；超限時顯示可理解的失敗原因。

**Blocked by:** None — can start immediately.

**Status:** done

**Audit reference:** H01 / High / 已重現；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 統一 writer、verifier、reader 與本機／復原備份使用的既有大小限制；維持總內容 512 MiB、data 50 MiB、manifest 1 MiB、單附件 20 MiB 的契約，不直接提高上限。
- [x] 在累積全部附件 bytes 前檢查可取得的檔案大小；序列化後的 data／manifest 大小與實際輸出仍須驗證，不能只相信 metadata。
- [x] 每個回報成功的備份均由正式 restore reader 驗證；任何超限、驗證或寫入失敗都不得回報成功或暴露此次無效輸出。
- [x] 以真實公開備份／reader seam 測試各上限與 +1 byte，以及已重現的 26 張合計 520 MiB 附件情境；拒絕結果不得修改目前資料。
- [x] 既有完整 Room round-trip、附件 bytes 與一般例外復原測試保持通過；不改 schema 15、backup format 1 或合法舊備份語意。

**Scope / evidence:** 此票處理成功與可還原的契約；先前有效復原檔在中斷寫入後的選擇與原子發布由 04 獨立處理，兩票無必要阻塞。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

**Completion (2026-09-13):** 共用既有大小契約、附件 metadata 預檢／有界讀取與正式 reader 成功驗證已完成。第一個公開 writer regression 先失敗後通過；最終 assembleDebug、158 JVM tests、API35 完整 77 instrumentation tests 全部通過，lint 0 errors／137 warnings，無新增 Kotlin compiler warning。Schema15／format1／navigation／依賴保持原值。26 × 20 MiB 額外附件的真實 Room fixture 驗證本機與復原備份拒絕、原快照／圖片 bytes 不變。512 MiB JVM exact-boundary 驗證不等於手機 heap measurement；crash-safe atomic publication、ENOSPC／取消／process restart 與真實 Google 驗收仍依其他票追蹤。

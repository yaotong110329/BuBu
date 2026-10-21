# 04 — 安全發布復原備份

**What to build:** 建立新的復原備份失敗時，使用者仍能匯出或使用先前有效的最新復原備份，不會把不完整檔案當成最新結果。

**Blocked by:** None — can start immediately.

**Status:** done（2026-10-04）

**Audit reference:** M09 / Medium / 靜態問題，尚未注入短寫故障；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 先建立已有有效復原檔、此次 writer 中途短寫／拋錯的 failing regression；不能用 writer 尚未開啟輸出前的缺附件代替此故障。
- [x] 先寫 App 擁有的暫存檔，完成正式 reader 驗證與必要持久化後，再於同一私有目錄安全發布。
- [x] 失敗的暫存或無效最終命名檔不會成為最新可用復原備份；失敗清理不刪除先前有效檔。
- [x] 新的有效復原備份成功發布後，才按既有只保留最新一份的規則清理舊有效檔；匯出／刪除所選最新檔必須有效。
- [x] 復原建立失敗時不進行資料庫替換，原快照與附件 bytes 不變；普通成功與中／後段例外恢復測試保持通過。

**Scope / evidence:** 使用現有 reader 契約即可獨立完成；不以完成 01 為前置條件，不自行增加保留份數。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

**Result:** 公開 `restore()` 回歸在實際 ZIP writer 開啟並寫出部分 bytes、尚未完成時，重現 `getLatestRecoveryBackup()` 選到新建無效 `.bubu` 的紅燈；修正後轉綠。writer 對同一私有目錄 `.part` 寫入、同步並由正式 reader 驗證，改名發布後同步目錄，才清理舊檔。查詢／匯出／刪除跳過無效 `.bubu`；中斷時舊備份可匯出，原資料與附件 bytes 完整。正常還原、既有中／後段例外恢復、成功發布清理及無效最終檔測試通過。

**Evidence:** `outputs/release-1.1.1-audit/ticket-04-red.log`（1 項預期失敗）、`ticket-04-green.log`（1 項通過）、`ticket-04-assemble.log`、`ticket-04-unit.log`（168／0／0／0）、`ticket-04-connected.log`（API 35 AVD 9／0／0／0）、`ticket-04-lint.log`（0 errors／140 warnings）。首次 red 嘗試因 AVD 未連線而未執行測試，重啟後才取得真正紅燈；實體裝置、程序終止及真實 ENOSPC 未執行，分別保留於後續票。

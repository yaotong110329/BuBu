# 03 — 保養摘要通知去重與失效清理

**What to build:** 同一保養狀態只通知一次；保養完成或失去資格後，通知摘要立即反映目前可通知的項目。

**Blocked by:** None — can start immediately.

**Status:** done (2026-10-04)

**Audit reference:** M02 / Medium / 靜態問題，尚未實機重現；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 先以正式 worker／notification boundary 建立回饋迴圈，證明相同狀態連續執行的重複 posting 與失效摘要問題。
- [x] 去重狀態跨 worker 重跑與程序重建有效；有效狀態轉變仍可產生應有通知，不永久壓制新到期事件。
- [x] 封存車輛、停用規則或新增／編輯／刪除實際服務工單後重新核對摘要；沒有可通知項目時取消摘要，有其他項目時保持內容正確。
- [x] 在發送前重新確認全域通知設定與有效性，測試讀取狀態後、post 前停用的交錯情境。
- [x] 普通車輛提醒與本機備份提醒的既有去重、通知識別與清理行為保持通過。

**Scope / evidence:** 本票不處理通知入口的車輛選擇或 route；08 可独立執行。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

**Completion evidence (2026-10-04):** Worker 改由共用的保養摘要協調器發送；AppContainer 訂閱衍生保養提醒及全域設定，資料變化時取消或靜默更新既有摘要。DataStore 原子保存最近響鈴與最近顯示的兩個狀態簽章；同狀態的 worker 重跑與程序重建不重送，新狀態仍可提醒。協調器在 notify 前再次讀取設定、通知權限及保養項目。正式邊界單元測試先 red（包含重建後重複 posting 的斷言失敗）再 green；Room／DataStore／系統通知 Android 測試覆蓋工單新增、編輯、刪除、封存、規則停用與摘要內容／取消。原 worker 的無條件 maintenance notify 與原 AppContainer 未訂閱 maintenance flow 是 M02 的前態證據。

最終依序通過 `:app:assembleDebug`、完整 `:app:testDebugUnitTest`（168／0 failures／0 errors／0 skipped）、API 35 `:app:connectedDebugAndroidTest`（80／0／0／0）及 `:app:lintDebug`（0 errors／140 warnings，沒有 issue 指向本票通知檔案）。Logs：`outputs/release-1.1.1-audit/ticket-03-*-final.log`；前期 red／focused logs 同目錄。`git diff --check` 通過（僅 Windows LF→CRLF 提示）。另一台已連線實機因既有 `com.kumo.bubu` 簽章不同而 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，0 項實機測試執行；AVD 結果獨立通過，未宣稱實機驗證。最後一次 lint 首次因自動核准服務額度限制未啟動，額度恢復後重新執行並通過。未變更 schema、backup format、navigation、權限、依賴或普通／備份通知 ID。

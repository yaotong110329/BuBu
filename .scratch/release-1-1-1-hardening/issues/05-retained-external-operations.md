# 05 — 旋轉後完成外部操作與下載清理

**What to build:** 使用者旋轉手機後，已啟動的 CSV 匯出、Google 授權及雲端下載預覽仍能完成或取消，且正確清除此次操作建立的檔案。

**Blocked by:** None — can start immediately.

**Status:** completed

**Audit reference:** M05 / Medium / 尚未透過 ActivityResult recreation 重現；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 先透過真正 ActivityResult／導航流程重現旋轉，不只測試孤立畫面或直接呼叫 ViewModel callback。
- [x] 旋轉期間的 SAF 匯出與 Google 授權結果對應原操作且只執行一次；取消與失敗也清除待處理狀態。
- [x] 下載預覽旋轉後，完成或取消仍清除自己建立的私有下載檔；不刪除 provider 原檔或其他操作的檔案。
- [x] 重新授權、重試與 callback 重送不會重複上傳、刪除或還原；不將 access token 寫入持久化狀態。
- [x] 測試表單與設定頁離開／返回的操作歸屬，不因 stale callback 自動開始未確認的資料覆蓋。

**Scope / evidence:** 此票處理外部操作的 configuration recreation，不擴展為所有表單的 process-death 保存；06 分開交付。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。


**Result:** 2026-10-10 完成。Google／CSV 的原操作跨旋轉保留、依操作 ID 單次消耗，離頁後失效；授權成功、取消、失敗與重試的公開 ActivityResult 回歸通過。私有下載的取消、確認、owner 清除與交付前取消清理已驗證，來源與其他操作檔案保留。

**Evidence:** 完整導航回歸 API 35 AVD 11／0／0／0；清理 lint 後針對性 2／0／0／0；JVM 178／0／0／0；build 通過，lint 0 errors／126 warnings，git diff --check 通過。裝置隔離防護與 configuration cache 重用通過。詳見 `docs/audit/TICKET_05_COMPLETION_2026_10_10.md`；真實 Google SDK／帳號與 Drive 驗收仍由 CB-12 追蹤，process death 留在 06。

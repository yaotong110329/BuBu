# 09 — Google 操作帳號與連結狀態一致

**What to build:** 雲端操作使用的 Google 帳號與畫面標示、備份清單及連結時間一致，不會以另一帳號的 token 操作卻仍顯示舊帳號。

**Blocked by:** None — can start immediately.

**Status:** completed

**Audit reference:** M07 / Medium / 尚未用真實帳號重現；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 建立 Connected(A) 後 authorize Upload／Load／Download／Delete 選到 B 的真實 ViewModel boundary regression。
- [x] 既有操作鎖定已連結帳號，或明確讓使用者決定切換；不默默把 B 的結果／時間記到 A。
- [x] 切換時清除舊帳號的備份選取與清單狀態；不得以 stale backup identifier 發起刪除或下載。
- [x] 取消、授權失敗與 token 過期不意外改變既有連結；access token 不持久化、不記錄到日誌。
- [x] 單一帳號的既有連結、解除連結與重新連結流程保持通過；真實多帳號驗證獨立記錄，不擅自擴大 CB-12。

**Scope / evidence:** 維持 ADR 的窄 Drive scope、appDataFolder 與無 BuBu 帳號模型。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

**Plan (2026-10-12):** 沿用已授權的真實 ViewModel 公開邊界，先回歸已連結 A 但 Upload 授權 B，拒絕非明確重新連結的帳號切換並保留 A 狀態；再逐片驗證 Load／Download／Delete、取消／失敗與明確重新連結的舊清單失效。先 actual red 再最小 green；最後依票要求 build／JVM／相關隔離 AVD／lint 及雙軸 review。真實 Google／Drive 完全不操作，CB-12 維持獨立 pending。Ticket 08 裝置初始化暫時受限，仍 in-progress。

**Progress (2026-10-12):** Connected(A)／Upload 授權 B 的公開 ViewModel 回歸已取得 actual red（expected false／actual true；1／1／0／0）及最小 green；Debug build與整個 CloudBackupViewModelTest（6／0／0／0）通過。非 Connect 動作在 operation 前比對 repository 現在的 Connected 帳號，不一致回報資源化 AccountMismatch。第一段 Standards／Spec 均0 findings，git diff --check exit0。尚待其他操作、NeedsAuthorization、切換清單／stale identifier、完整JVM／相關AVD／lint，本票仍 in-progress。

**Completion (2026-10-14):** 帳號／過期／stale identifier驗證與切換清單、選取、完成訊息一致性完成；同帳號大小寫／時間更新保留狀態。build、完整JVM217及相關裝置13全綠；lint0 errors／126 warnings，雙軸0未解問題。未操作手機或實際Drive；CB-12真實帳號獨立pending。 詳見docs/audit/TICKET_09_COMPLETION_2026_10_14.md。

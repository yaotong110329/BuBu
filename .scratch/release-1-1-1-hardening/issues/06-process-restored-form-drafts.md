# 06 — 系統回收後恢復表單草稿

**What to build:** 新增或編輯車輛、加油紀錄及服務工單時，系統在背景回收 App 後仍可恢復未儲存草稿及安全的附件參照。

**Blocked by:** None — can start immediately.

**Status:** completed

**Audit reference:** M04 / Medium / 尚未重現系統 process recreation；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 先測試背景後系統終止／重建程序的真實情境；普通旋轉或 force-stop 不當成等價證據。
- [x] 新增與編輯表單均保留已輸入欄位、服務項目順序與穩定草稿識別；編輯草稿不默默退回原始資料。
- [x] 輕量 state 與 App 私有暫存附件歸屬可恢復；不把圖片 bytes 或授權 token 塞入 saved state。
- [x] 草稿尚未儲存時不建立正式工單／加油／車輛資料；明確取消、成功儲存與失效來源有可驗證的清理策略。
- [x] 重建後可繼續正常驗證與儲存，無重複紀錄；正常旋轉的既有行為保持通過。

**Scope / evidence:** 不新增雲端草稿、帳號或額外權限；保存所需的最小實作屬於本票。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

**Progress (2026-10-10):** API 35 BuBu_API_35 已重現真正背景程序回收：cached process 25179 由 `am kill` 終止，原任務以新 PID 25617 返回新增車輛畫面，`stopped=false`，車名 DRAFT06-CAR／廠牌 BRAND06 皆遺失。證據在 `outputs/release-1.1.1-audit/ticket-06-{before-kill,after-kill}.xml` 及 `ticket-06-process-recreation-red.json`。車輛草稿修復進行中，其他驗收尚未完成。


**Progress (2026-10-11):** 三種新增表單真正 cached process kill 的紅／綠 JSON 與 XML 已保存。車輛、加油及服務均使用穩定草稿 UUID 作為正式建立識別，恢復時檢查已提交結果；三個「背景快照先保存、正式資料後提交」公開 ViewModel 回歸先紅後綠。加油里程異常原因恢復與重新驗證回歸先紅後綠。服務草稿私有附件的兩天舊副本不再被啟動清理當成孤兒；取消後私有副本刪除、來源圖片保留，真實 Room 整合通過。`ticket-06-commit-replay-integration-green.log`：assembleDebug、完整 JVM 194/0/0/0、API 35 專用 AVD 5/0/0/0。新增整合測試最初欄位名稱拼錯，修正後才通過；不是產品缺陷。提交後偏好寫入故障的加油回歸另已重現，修復驗證中。草稿大小上限、損壞解碼清理及最終 lint／完整回歸尚未完成，保持 in-progress。

**Progress — continuation (2026-10-11):** 三種表單的長備註／未完成項目採真正私有檔案保存，UTF-8 JSON inline 上限 31 KiB，含 identity 的字串預算回歸 ≤32 KiB（不是完整 Android Parcel 大小量測）。SHA mismatch／first-pending missing file 均阻擋誤恢復／誤存正式資料。Vehicle／Fuel post-commit failure recovery 已綠。私有草稿中斷 `.part` owner 清理、來源失效時附件匯入取消與 stale completion 均公開 red→green；Vehicle／Service group 合計 21 項通過。實際 IO 交付取消在 `ticket-06-attachment-delivery-red.log` 重現私有 copy 殘留，外層 NonCancellable 清理修正等待完整品質閘門。新增 corrupt saved JSON／原 owner 清理的 Room integration 待執行。首輪測試 APK 因測試誤用 private companion 常數而編譯失敗，已修正；不是產品 red。Standards 最終靜態復核 0 blockers；Spec sub-agent 此輪因額度中斷，由主 agent 補規格核對，不冒稱雙 sub-agent 均完成。其他專案使用 BuBuApi35，故啟動專用 BuBu_API_35／emulator-5556，ADB 5041 關閉自動掃描並只註冊本票 AVD；裝置防護通過。保持 in-progress，最終 gates 和新 production APK 的真正 OS process-recreation 仍在驗證。

**Progress — final review (2026-10-11):** 首輪完整建置／JVM 203/0/0/0／API 35 裝置 99/0/0/0 通過（`ticket-06-build-unit-green.log`、`ticket-06-connected-final.log`），包含實際 IO 交付取消與 corrupt JSON／原 owner 清理。六個新增 VisibleForTests 警告已移除，lint 回到 0 errors／126 既有 warnings。Spec 復審提出長稿 A 已保存、背景附件發布 B 覆寫檔案的競態；公開延遲附件＋真實私有檔案回歸已 red→green（`ticket-06-background-completion-{red,green}.log`）。私有 snapshot envelope 驗證 owner／revision／SHA；可恢復較新完整發布版本，缺檔／較舊版本／摘要不符仍 fail-closed，不保留每次長稿按鍵的完整副本。Spec 復審該 P2 已解除。新增最終 gates 執行中，未結案。

最終 OS 驗證另發現初版 harness 的裸 component 啟動與 MAIN／LAUNCHER 返回不相符，建立第二個 MainActivity 而顯示首頁；Back 回原 Activity 後欄位仍在，因此該輸出不是產品 red。已保留失敗的 harness 證據，改以 reorder-to-front 並核對原 task／ActivityRecord：`ticket-06-final-vehicle-same-task-green.json` PID 7510→7783、task 153→153、ActivityRecord 8d85ef2 不變，車名／廠牌均保留。仍待最新 snapshot APK 的三表單最終 OS 驗證。

**Completion (2026-10-11):** 全部驗收完成。最新 build／JVM 204／相關裝置 39、最終 lint 0 errors／126 既有 warnings，首輪完整裝置 99 全綠；最新 APK 三表單真正 OS 回收及未完成工單 editor 恢復均通過。Standards／Spec 0 未處理問題。完整變更、版本、真實與 controlled 證據邊界、環境限制見 `docs/audit/TICKET_06_COMPLETION_2026_10_11.md`。

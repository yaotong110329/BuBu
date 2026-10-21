# 02 — 加油表單安全處理計算溢位

**What to build:** 加油表單遇到可解析但衍生結果超出 Long 的數值時，保留使用者明確輸入並顯示欄位錯誤，讓使用者可修正後繼續。

**Blocked by:** None — can start immediately.

**Status:** done

**Audit reference:** M01 / Medium / 已重現；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 先在真實表單輸入事件／state seam 建立 failing regression，涵蓋 0.001 L 與 Long.MAX_VALUE 總額的兩種輸入順序。
- [x] 衍生單價或油量無法表示時，不讓 ArithmeticException 離開輸入 callback；錯誤狀態可由 UI 顯示。
- [x] 不以截斷、clamp、浮點近似或默默替換權威輸入掩蓋錯誤；錯誤未排除時不可儲存無效結果。
- [x] 普通三欄推算、零除數、精度與輸入優先順序保持原行為；修正輸入後可正常計算與儲存。

**Scope / evidence:** 不要將此票擴展為所有金額型別或全專案計算器重構。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

**Verification status (2026-09-28):** 三個溢位方向與價格來源回歸依序完成 red → green；`:app:assembleDebug` 通過（3m19s），完整 `:app:testDebugUnitTest` 為 163／0 failures／0 errors／0 skipped，API 35 `:app:connectedDebugAndroidTest` 為 78／0／0／0，`:app:lintDebug` 為 0 errors／140 warnings，且沒有 warning 指向本票 Fuel 變更檔案；`git diff --check` 通過。初次驗證因本機缺少 Kotlin 2.4.10 compiler artifacts 且 Gradle 遠端傳輸中斷，最後以官方 Maven Central 檔案、官方 SHA-1 完整比對與一次性本機 repository 完成；沒有修改 dependency 或版本。證據：`outputs/release-1.1.1-audit/ticket-02-*.log`。

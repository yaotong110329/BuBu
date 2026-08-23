# 06 — Phase 7 品質閘門

**What to build:** 使用者可信任 CSV 匯出、完整備份與完全覆蓋還原能在離線裝置重複運作，並在任何失敗或損壞輸入下保護既有資料與附件。

**Blocked by:** 01 — CSV 報表 ZIP 匯出; 02 — `.bubu` 完整備份與格式驗證; 03 — 備份解析、完整性驗證與還原預覽; 04 — 完全覆蓋還原與失敗回復; 05 — 設定頁備份管理與復原備份.

**Status:** done

**Review baseline:** `ecb313d392d5ef9b5510fe547106605cbaa9d059` (`chore: establish BuBu release-readiness baseline`). All subsequent Phase 7 / Release Readiness reviews use this commit as the fixed point.

**Verification note (2026-08-22):** A controlled single-process Android Studio embedded JBR 25.0.2 build rebuilt all Debug tasks successfully after the `duplicate app-metadata.properties` incident. The root cause was overlapping Gradle processes writing the same `app-debug.apk`, not a duplicate dependency or packaging input; the rebuilt APK contains exactly one generated metadata entry. The final serial gate passed `clean`, `:app:assembleDebug`, JVM unit tests, `:app:assembleDebugAndroidTest`, the complete `connectedDebugAndroidTest` suite on an API 35 Google APIs x86_64 AVD, and `:app:lintDebug` (0 errors). The seven isolated Compose screen test classes explicitly opt into the v2 test rule's `UnconfinedTestDispatcher`, which matches their synchronous `setContent` lifecycle; a targeted Android 16 Dashboard regression passes. The Android 16 device run still has a Compose-host lifecycle compatibility risk only when the complete suite follows earlier tests; API 35 runs the same full suite successfully. This is a test-host lifecycle compatibility risk, not a Phase 7 backup/export assertion failure. Baseline-diff Standards review found no blocker (only a non-blocking duplicated-test-setup suggestion deliberately not expanded during release closure); the final Spec re-review found 0 findings. Quality gate closed.

- [x] 從乾淨建置完成 Debug APK、JVM、Room／Repository、Compose、相容裝置儀器測試與 lint，並產出當前 Room schema。（API 35 AVD 完整套件通過；Android 16 實機完整套件仍有 Compose host lifecycle 相容性風險，詳見驗證註記。）
- [x] 在實機驗證 Excel 開啟繁體中文 CSV、CSV 不可反向匯入、備份格式驗證、損壞附件拒絕與失敗還原不破壞原資料。
- [x] 完成 Standards／Spec 雙軸審查與範圍稽核，修正所有確認缺陷且不加入雲端、帳號、廣告、未驗證 Drivvo 匯入或廣泛檔案權限。（範圍掃描無命中；baseline 差異式 Standards 無 blocker、最終 Spec review 0 findings。）
- [x] 更新專案文件與 Phase 7 任務狀態，只宣告實際通過驗收的功能。

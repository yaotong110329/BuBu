# Ticket 15 雙軸審查 — 2026-10-20

依 code-review 技能由兩個獨立 agent 平行審查。固定參考 `e12379551c1cf9b48fb17ddc18f424ad31504700` 已解析；`git diff <ref>...HEAD` 與 `git log <ref>..HEAD --oneline` 為空，因本輪沿用已核准的未提交工作樹流程。實際審查非空的本票差異，不把空 commit diff 當成通過證據。

本票範圍：新增 `MaintenanceHistoryRoundTripTest.kt`、`MaintenanceHistoryFixture.kt`，以及 `BackupArchiveAssertions.kt` 的 UUID 集合排序比較。新檔以 `git diff --no-index -- NUL <path>` 檢查；既有 helper 以 `outputs/release-1.1.1-audit/ticket-13-final-artifacts/app/src/androidTest/java/com/kumo/bubu/data/repository/BackupArchiveAssertions.kt` 對照目前檔案。其他票已存在的正式碼與測試變更不屬本票。

規格來源：`.scratch/release-1-1-1-hardening/issues/15-maintenance-roundtrip-derived-state.md`、Spec 5.7、CONTEXT 的保養基準／週期／狀態／新車起始保養，以及 ADR 0005。標準來源：AGENTS.md、Spec、CONTEXT、TASKS、ADR、適用技能與 code-review 完整 smell baseline；工具已強制檢查的項目不重複當人工 finding。

## Standards

首次審查：0 項強制規則違反；1 項文件化品質準則偏離。新增測試 318 行，超過 Spec.md「避免超過 300 行的單一檔案」建議。已在審查階段將資料建立與 DataStore 生命週期抽至同 package 的測試 fixture，保留兩個案例與全部斷言，沒有壓縮排版或擴張正式架構。

最終複審：0 項硬規違反、0 項未解品質準則偏離、0 項需處理的 smell。測試 210 行、fixture 121 行、archive helper 37 行。fixture 集中真實 repository、歷史建立及 DataStore 生命週期；清理先取消並等待 scope，再移除儲存。公開 seam、完整 DTO 欄位與附件 bytes 比較均保留，沒有正式程式或 API 變更。

## Spec

最終複審：0 findings。真實 repositories 與正式備份／reader／覆蓋還原，涵蓋 CAR／MOTORCYCLE、封存資料、內建／自訂類型、非預設規則及附件內容。固定日期與里程的獨立預期驗證實際工單優先於 0 km 基準、無歷史／無 opt-in 為 UNKNOWN，以及停用、封存、不適用類型排除。

還原後真正新增回填工單、改期並刪除，逐步檢查完成鏈、最新基準及狀態，符合 Spec 5.7 與 ADR 0005。以 publicId 正規化集合順序仍比較全部欄位，包括顯式 sequence／sort、UUID 關聯與附件 bytes，未發現不當放寬。fixture 拆分未改變案例語意。

兩軸均為靜態審查；沒有代替建置、裝置測試或 lint，實際品質閘門見本票結案紀錄。

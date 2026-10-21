# Ticket 14 雙軸審查 — 2026-10-18

依 code-review 技能分別由兩個審查 agent 執行，固定參考 e12379551c1cf9b48fb17ddc18f424ad31504700。HEAD 尚無新增 commit，因此採已核准的非空 scoped worktree 差異：FuelEconomy.kt、FuelEconomyTest.kt，以及 FuelFormViewModelTest.kt 本票兩個新測試／新增 import／共用歷史 helper；其他先前票的變更不屬本票。

規格來源：.scratch/release-1-1-1-hardening/issues/14-fuel-outlier-recent-window.md。標準來源：AGENTS.md、Spec.md、CONTEXT.md、TASKS.md、docs/adr/ 及 .agents/skills/，另使用技能所列 smell baseline。

## Standards

首次審查：硬規違反0；1項低優先度判斷性 Duplicated Code，兩個表單測試重複建立八舊30／八近10歷史。已抽出檔案內 historyWithEightOldHighAndEightRecentNormalSegments()，各測試保留獨立事件及斷言。另加正式碼註解，說明 map 保留區段終點時序，先選 window 再排序數值。

最終複審：硬規違反0、未解 smell 0。單一 app、domain 職責、公開測試 seam、資源及依賴規則均無新增違反。

## Spec

0 findings。先依既有有效區段時序取最多八段，再算中位數／MAD；排除筆、候選筆、滿箱錨點、兩車隔離與警告／明確確認保存語意符合本票。沒有 accumulator 重構或額外產品功能。

兩軸均為靜態審查，沒有代替建置／測試／Lint；實際品質閘門另記錄。

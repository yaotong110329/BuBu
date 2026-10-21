# 16 — 釐清正式版本與文件差異

**What to build:** 後續開發者能分清 Release 1.1.1 的實際能力、權威規格要求、未完成範圍與需要產品決策的差異，不因早期文件誤開功能或全面重構。

**Blocked by:** None — can start immediately.

**Status:** complete

**Audit reference:** L02 / Low / Documentation Drift；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 建立依實際 routes／schema／操作可查證的 release capability inventory，區分已實作、尚未實作、明確核准延期與待決項；未核准者不自行標成延期。
- [x] 列清油耗重啟／資格、里程更正／更換、垃圾桶、封存報表與歷史能力的規格差異，不在本票實作這些功能。
- [x] 對齊 README 的四個底部頁籤與 TASKS 的現有階段標示，保留歷史驗證紀錄及 audit 結果。
- [x] 保持 Spec 最高權威；規格縮減、延期或 domain/ADR 決策需明確決策來源，不以文件修改消除 production 差異。
- [x] CB-12 真實 Google 單帳號驗收保持 pending，沒有新驗證證據就不宣告完成；本票用文件與實作 inventory 核對，不要求無關的 App build。

**Scope / evidence:** 此票不升級依賴、不改 schema／format、不刪 scaffold，也不另開真實驗收重複票。

**Verification:** 核對文件與實際版本 inventory，檢查差異、連結及範圍。

**Completed 2026-10-17:** docs/audit/RELEASE_1_1_1_CAPABILITY_INVENTORY.md 與 README／TASKS 對齊；來源／本機連結／runtime 雜湊未變／權威文件未改／diff check 通過，Standards0／Spec0。詳見 docs/audit/TICKET_16_COMPLETION_2026_10_17.md 及 TICKET_16_REVIEW_2026_10_17.md；沒有新增 App build 或真實 Google 驗收宣稱。

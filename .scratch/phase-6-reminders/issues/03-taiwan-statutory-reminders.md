# 03 — 臺灣法定車輛提醒

**What to build:** BuBu 會為符合條件的臺灣自用車建立使用牌照稅、公路養管費與定期驗車提醒，使用者能在提醒中心看見、調整必要的驗車日期資訊，並收到一次本機通知。

**Blocked by:** 01 — 車輛提醒中心與到期狀態; 02 — 本機提醒通知與權限.

**Status:** completed — implementation and verification completed on 2026-08-21; insufficient-data acceptance scenarios deferred per user instruction

- [x] 使用牌照稅與公路養管費依固定開徵月份建立週期日期提醒，僅提醒日期、不推算金額。
- [x] 自用小客車及大型重型機車依車齡與行車執照指定檢驗日期建立定檢提醒；十年以上的兩個檢驗日期可分別設定。
- [x] 規則變更、車輛封存或資料不足時不產生錯誤或重複的提醒／通知。
- [x] 規則、資料遷移、提醒中心和通知流程都有自動化驗收。

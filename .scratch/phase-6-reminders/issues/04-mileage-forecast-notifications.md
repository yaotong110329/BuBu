# 04 — 平均里程預估提醒

**What to build:** 對具有足夠有效里程紀錄的車輛，BuBu 會預估里程型提醒進入即將到期的日期並安排一次通知；不足資料時明確不預估。

**Blocked by:** 01 — 車輛提醒中心與到期狀態; 02 — 本機提醒通知與權限.

**Status:** completed — implementation and verification completed on 2026-08-21; insufficient-data acceptance scenarios deferred per user instruction

- [x] 以有效里程紀錄計算近期平均行駛量，優先最近 90 天、資料不足時擴至 180 天，仍不足則不預估。
- [x] 預估通知日只影響通知時機，不取代原始到期里程或提醒狀態。
- [x] 多車資料、非正距離與不完整時間序列不會互相混算或產生通知。
- [x] 預估與通知行為有固定時間的 unit／integration 驗收。

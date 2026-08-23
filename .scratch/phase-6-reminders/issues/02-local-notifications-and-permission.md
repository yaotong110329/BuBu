# 02 — 本機提醒通知與權限

**What to build:** 提醒中心的到期與即將到期提醒可由本機 WorkManager 發出一次可辨識通知；使用者可授權、拒絕或停用通知，而提醒資料與提醒中心不受影響。

**Blocked by:** 01 — 車輛提醒中心與到期狀態.

**Status:** completed

## Verification

- [x] Android 13+ notification permission request, persisted notification switch, and daily WorkManager scheduling.
- [x] Status-based deduplication plus stale-notification cleanup for completed, snoozed, disabled, and deleted reminders.
- [x] Stable notification deep link opens the matching reminder in the reminder center.
- [x] Unit tests and Android 15 emulator instrumentation tests passed.

- [x] Android 通知權限、通知開關與 WorkManager 排程依系統版本正確處理。
- [x] 同一提醒在同一到期狀態不重複通知；完成、延後、停用或刪除後不再發出過期通知。
- [x] 通知點擊可安全開啟對應提醒或來源紀錄，且不需要帳號、雲端或網路服務。
- [x] 排程與去重有可重複的 unit／instrumentation 驗收。

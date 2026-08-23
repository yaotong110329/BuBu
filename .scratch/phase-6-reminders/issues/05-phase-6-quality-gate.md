# 05 — Phase 6 品質閘門

**What to build:** 使用者可信任 Phase 6 的提醒中心、通知、法定規則與里程預估在離線裝置上可重複運作，且不引入未要求的雲端、帳號、廣告或資料交換功能。

**Blocked by:** 02 — 本機提醒通知與權限; 03 — 臺灣法定車輛提醒; 04 — 平均里程預估提醒.

**Status:** completed on 2026-08-21 — clean build, JVM tests, lint, Room schema generation, APK installation and device acceptance passed; insufficient-data acceptance scenarios deferred per user instruction

- [x] 從乾淨建置完成 Debug APK、JVM、Room／Repository、Compose、儀器測試與 lint。
- [x] 驗證通知去重、通知停用、完成、延後、封存車輛與權限拒絕情境。
- [x] 完成 Standards／Spec 雙軸審查與範圍稽核，修正所有確認缺陷。
- [x] 更新專案文件與 Phase 6 任務狀態，只宣告實際完成的功能。

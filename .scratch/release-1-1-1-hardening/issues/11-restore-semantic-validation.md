# 11 — 還原預覽驗證資料語意

**What to build:** 自製或異常備份即使 checksum 正確，也必须在預覽階段拒絕無效資料，而不等到正式替換才發現。

**Blocked by:** None — can start immediately.

**Status:** completed

**Audit reference:** M10 / Medium / Test Gap；數值與跨擁有者資料尚未重現；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 先建立結構、checksum 正確但含非法金額、日期／時間、跨車 item／reminder 關聯與重複 vehicle/type policy 的 archive regression。
- [x] 驗證參照存在以外的擁有者一致性、合法 enum、數值範圍與工單小計／總額契約；只檢查真正適用的 domain invariant，不拒絕合法歷史資料。
- [x] 非法資料在建立復原或替換目前 DB 前被拒絕，錯誤可理解且原快照／附件保持不變。
- [x] 明確定義舊 format 1 缺少 additive 欄位時的既有合法預設與未知 enum 策略；保留合法舊備份相容性，不改 format version 或 schema。
- [x] 完整語意 round-trip 與原有 archive guard 測試保持通過；衍生統計與 App 偏好不被加入備份。

**Scope / evidence:** 不要以 checksum 等同語意有效；numeric/cross-owner fixture 的失敗證據是本票開始工作的第一步。

**Completion (2026-10-17):** build／完整 JVM236／test APK、最終相關 connected10/0/0/0、最後 lint0 errors／126既有 warnings、diff check 與兩軸複審皆通過。舊未知 enum 測試已由延後替換故障改為提前拒絕／零新復原檔／公開匯出快照不變；真正晚期交易故障仍由 SQLite fixture trigger 驗證。完整結果及限制見 docs/audit/TICKET_11_COMPLETION_2026_10_17.md；下列時間紀錄保留歷史狀態。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

**Plan (2026-10-14):** 已授權公開archive reader/writer及實際RestoreRepository／Room seam；先以checksum正確的非法金額fixture取得actual red→green，再逐項日期／enum／owner／policy／subtotal驗證，保留合法歷史defaults；最後確認preview/restore失敗在recovery/DB替換前，原快照與附件不變，完整品質閘門及雙軸review。前票01–10已完成。

- 2026-10-14 第一片：checksum／manifest正確的負數工單總額actual red17s→green；reader最小攔截，Debug build／完整JVM219/0/0/0（50s）、diff check通過。日期／跨擁有者／enum／policy／subtotal／舊格式及實際還原不變性仍待驗證；本票connected／lint／雙軸審查尚未執行，維持in-progress。證據outputs/release-1.1.1-audit/TICKET_11_SESSION_2026_10_14.md。

- 2026-10-17：數值／日期／enum／跨車引用／工單金額與唯一性 guards 已完成 actual red→green。合法歷史機車共用 CAR 類型、修改車種後保留 policy、format 1 additive defaults 均有公開正例。最新 Debug build／完整 JVM236/0/0/0／Android test APK 成功，兩軸最終 review 0 未解 findings（docs/audit/TICKET_11_REVIEW_2026_10_17.md）。10/14 實際 Room round-trip／非法預覽資料與附件保存性 6 項曾通過；目前全新自有 API35 模擬器首次初始化中，需重跑最終裝置閘門，另修正一個舊測試期待未知 enum 延至正式替換才失敗的過時斷言。lint 進行中，不提前結案。完整 XML／日誌見 outputs/release-1.1.1-audit/ticket-11-unique-final-unit 與 TICKET_11_SESSION_2026_10_16.md。

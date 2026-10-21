# 10 — 完整 Drive 清單與可靠保留策略

**What to build:** 使用者取得完整的 BuBu 雲端備份清單；成功上傳後可靠保留最新五份，上傳失敗不會觸發刪除舊備份。

**Blocked by:** None — can start immediately.

**Status:** completed

**Audit reference:** M08 / Medium / 靜態分頁缺口；雲端 orchestration coverage 缺口；Release 1.1.1 audit snapshot e123795（2026-09-13）。

- [x] 正式 datasource 請求並跟隨 nextPageToken；第一頁少於 pageSize 但仍有 token 時繼續，保留既有 appDataFolder／BuBu filter。
- [x] 以兩頁以上 scripted transport 測試完整清單與最新排序；不得用單純 retention helper 測試代替 repository 流程。
- [x] 第六份成功上傳、驗證與記錄成功後保留最新五份；舊 inventory 超過一頁時清理不漏列。
- [x] metadata／media 上傳失敗只嘗試清理此次新建檔，不刪除任何先前備份；刪除舊檔失敗保留新有效備份並準確回報清理結果。
- [x] 測試 transport 中途失敗、重試與清單失敗，不以不完整 inventory 宣告清理成功；不增加廣泛 Drive scope。

**Scope / evidence:** 此票以失敗注入驗證本機 orchestration，不授權刪除真實帳號備份；原 CB-12 真實單帳號驗收继续追蹤。

**Verification:** 先在上述真實公開 seam 執行 regression／重現流程並保存結果。程式碼修正後依序執行 :app:assembleDebug、:app:testDebugUnitTest、相關 :app:connectedDebugAndroidTest 與 :app:lintDebug；Gradle 不重疊執行。程序／檔案故障及真實裝置驗證各自記錄，未執行的項目不得宣告通過。

**Plan (2026-10-14):** 沿用已授權公開Repository與外部HTTP transport seam；真實Room/DataStore／正式HTTP datasource配scripted Drive回應，先短頁帶token漏列actual red→green，再逐片驗證已驗證上傳後五份保留與metadata/media/list/delete失敗、重試及UI清理結果。SDK transport只在外部URL connection邊界可注入，不操作真實Drive、不新增scope/schema/deps。最後完整build/JVM、相關connected、lint及雙軸review依序執行。

**Completion (2026-10-14):** 正式Drive清單跨短頁／空页跟隨token，拒絕不完整inventory；成功上傳後才清理保留五份，清理失敗保留新有效備份與時間，畫面明確顯示部分成功。build／完整JVM218／相關裝置28全綠，lint0 errors／126 warnings、diff check通過；雙軸0未解問題。 詳見docs/audit/TICKET_10_COMPLETION_2026_10_14.md；CB-12獨立pending。

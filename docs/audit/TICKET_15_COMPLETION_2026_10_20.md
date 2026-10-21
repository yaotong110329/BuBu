# Ticket 15：保養歷史備份還原與衍生狀態 — 2026-10-20

**狀態：** 已完成（2026-10-20）。本批 Release 1.1.1 audit tickets 01–16 全部結案；CB-12 真實帳號驗收獨立 pending。

本票補齊保養歷史的真實整合驗證。正常資料在正式覆蓋還原後，保養基準、每車規則、提醒狀態、工單完成鏈及附件內容一致；還原後的回填、改期與刪除，會依剩餘有效工單重新導出。沒有重現需修改正式碼的產品缺陷。

## 變更檔案與範圍

- `app/src/androidTest/java/com/kumo/bubu/data/repository/MaintenanceHistoryRoundTripTest.kt`：兩個公開整合案例與獨立預期值，210 行。
- `app/src/androidTest/java/com/kumo/bubu/data/repository/MaintenanceHistoryFixture.kt`：真實 Vehicle／Fuel／Service repositories、隔離 Room／DataStore、正常歷史及受控清理，121 行。
- `app/src/androidTest/java/com/kumo/bubu/data/repository/BackupArchiveAssertions.kt`：依 publicId 對應集合後比較完整 DTO 與每份附件 bytes，37 行；本地 row ID 改變造成的陣列重排不再誤判。sequence／sort、UUID、關聯、所有其他欄位仍完整比較。
- `TASKS.md`、本票 issue、結案與[雙軸審查](TICKET_15_REVIEW_2026_10_20.md)：保存目前進度、驗收與限制。

單一 app、既有資料／領域／UI 職責、權限及依賴維持原狀。206 個正式來源／資源／build 檔案雜湊與本票開始時完全一致；沒有 schema、backup format、正式 API、功能或發布變更。原本為參照守恆設計的 BackupRoundTripAuditTest fixture 保留，不當成正常完成鏈的證據。

## 獨立行為驗證

第一例建立有效汽車、有效普通重型機車，以及一台具有工單的封存汽車；使用三個實際內建 seed、四個自訂類型、五組非預設每車規則、五張正常工單與一份實際 PNG 附件。車輛由真實 Repository 建立，後續加油紀錄固定目前里程為 5,000 km；比較日期固定 2025-01-15。

正式 source DB → 備份 → reader → 覆蓋已存在車輛的 target DB → 再備份，比對全部備份集合與附件內容。公開衍生狀態以車輛／服務類型 publicId 對應，僅移除不可攜的本地 ID。獨立字面預期包含：

| 情境 | 預期 |
| --- | --- |
| 汽車機油最近實際 2025-01-01／4,000 km，週期 1,000 km／2 月 | 基準取實際工單，覆蓋全車及明確規則的 0 km；下次 5,000 km／2025-03-01，剩 0 km／45 天，DUE |
| 汽車自訂，2024-12-15／3,500 km，777 km／3 月 | 下次 4,277 km／2025-03-15，剩 -723 km／59 天，OVERDUE |
| 機車自訂，2024-12-20／4,500 km，750 km／1 月 | 下次 5,250 km／2025-01-20，剩 250 km／5 天，UPCOMING |
| 機車機油沒有工單或基準 opt-in | UNKNOWN，日期／里程基準及下次值皆空 |
| 汽車空濾沒有工單、啟用新車基準 | 僅取 0 km；下次 40,000 km，NORMAL；不猜測保養日期 |

封存車、封存類型、停用規則及不同車種的類型均排除；還原後政策、UUID、歷史關聯、基準與狀態保持一致。

第二例重新建立正常來源並正式還原，透過 ServiceRepository 真正變更工單。每一步都檢查 formal reader 的精確完成邊、completedAt 有無、公開基準／狀態、固定里程、政策與附件內容，其他保養項目結果保持不變：

1. 初始 `first → last`；last 待辦，基準 4,000 km／2025-01-01，DUE。
2. 回填 2024-12-15／2,000 km：`first → middle → last`，基準仍取 last。
3. middle 改為 2025-01-10／4,500 km：`first → last → middle`；工單及項目 publicId 保留，基準改取 middle，下次 5,500 km／2025-03-10、剩 500 km／54 天，NORMAL。
4. 刪除 middle：回到 `first → last`，DUE。
5. 再刪除 last：first 待辦，基準 1,000 km／2024-12-01，下次 2,000 km／2025-02-01、剩 -3,000 km／17 天，OVERDUE。

## 實際品質閘門

Gradle 沒有重疊執行。僅使用自有 `BuBu_Ticket08_API35`／emulator-5558、私有 ADB5042，connected 裝置防護成功；最後已正常關閉此 AVD 並保留資料。

| 檢查 | 實際結果 | 證據（outputs/release-1.1.1-audit/） |
| --- | --- | --- |
| 第一個正常 round-trip 案例 | 1 pass，1.551 秒 | ticket-15-roundtrip-identity-green.log／json |
| 第二個工單變更案例 | 1 pass，1.978 秒 | ticket-15-mutation-first-verdict.log／json |
| 拆分後 Debug／完整 JVM／Android test APK | BUILD SUCCESSFUL，49 秒；243 tests／0 failures／0 errors／0 skipped | ticket-15-final-reviewed-build.log、ticket-15-final-unit/ |
| 拆分後相關 connected regression | 27／0／0／0，19 秒；涵蓋新增兩例及既有 Service、BackupRoundTrip、Restore 測試 | ticket-15-final-connected.log、ticket-15-final-connected/ |
| lintDebug | BUILD SUCCESSFUL，1 分 20 秒；0 Fatal／0 Error／125 既有 Warning；相對 Ticket13 正規化 issue 比較新增0／移除0 | ticket-15-final-lint.log／xml／-counts.json |
| git diff --check | 通過 | 最終工作樹及本票新檔另行檢查 |
| Standards／Spec | 最終各 0 未解；首次檔案長度 finding 已解決 | 本票審查文件 |
| 正式來源／設定未變及最終來源／APK SHA-256 | 206 檔一致；保存三份測試來源與兩份 APK | ticket-15-runtime-before.json、ticket-15-source-apk-sha256.json、ticket-15-final-artifacts/ |
| 最後狀態／連結／證據一致性 | 16 張 issue 均結案、本票 5 項完成條件勾選；本票連結、來源及保存 APK 雜湊一致 | ticket-15-final-check.json |

最終主 APK SHA-256：`f3f201bee91d95b66bc7262ac09929d870ffc595f48226951c671234a1766d96`，與 Ticket13 最終主 APK 相同。最終測試 APK：`fa33636ed73f6b3f5852903fe5c60f016d2e45a64d7fe689b94e5bf694337cf4`。

## 實際版本與限制

App 1.1.1／versionCode3、Room DB15、backup format1；compile／target／min SDK37／37／26。Gradle9.6.1、AGP9.3.1、Kotlin2.4.10、KSP2.3.10、JBR25.0.2、Room2.8.4、DataStore1.2.1、Compose BOM2026.06.01、Coroutines1.11.0、JUnit4.13.2。裝置為 API35／Android15 google_apis x86_64，沒有升級依賴。

中斷前編譯的測試 String／enum 比較錯誤、第一輪 cold-start startup ANR、Repository 建構時漏傳內建 seed，以及 raw list-order assertion 的假失敗均保存並逐項修正；未當成產品缺陷或產品 red→green 宣稱。相同 APK 重跑進入案例；ANR 時尚無 TestRunner 案例事件，Android exit-info 顯示 startup timeout，系統日誌有 CPU99% 及其他系統程序啟動逾時。沒有提高逾時門檻或改正式初始化流程掩蓋問題。

Windows 中文路徑沿用僅測試用 overridePathCheck／指向相同檔案的英文 classpath 別名，離線使用既有 SDK／Gradle 快取。實際模擬器結果不代表所有 Android 版本、真實帳號、裝置斷電或個人資料驗收；本票沒有操作手機、實際 Drive 或估算加油紀錄。CB-12 真實 Google 單帳號附件、保留五份及解除再連結驗收仍獨立 pending；完整 Spec 能力差異見[既有盤點](RELEASE_1_1_1_CAPABILITY_INVENTORY.md)。

詳細逐步記錄見 `outputs/release-1.1.1-audit/TICKET_15_SESSION_2026_10_20.md`。本票未建立 commit、PR、發布或安裝手機 APK。

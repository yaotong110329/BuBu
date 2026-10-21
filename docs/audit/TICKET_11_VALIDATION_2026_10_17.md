# Ticket 11 驗證記錄 — 2026-10-17

狀態：已完成。語意／歷史相容性修正、Debug build、完整 JVM236 項與 Android test APK、相關裝置10項、最後 lint0 errors／126既有 warnings 均通過。此檔僅為 ticket11 的結果。

checksum 正確的備份仍可能含非法業務資料。本票將驗證集中在正式 Reader，讓預覽與還原共用相同入口，在建立復原檔或替換 DB 前拒絕無效資料。

## 變更與相容性

- BackupArchiveReader.kt：驗證 enum、非負金額與里程、加油量、可表示日期／時間、排程值、明細正數數量與 HALF_UP 小計、可安全相加的工單總額；驗證跨車提醒來源／完成關聯與 policy、類型名稱及提醒鍵的實際唯一性。
- OfflineRestoreRepository.kt：移除 Reader 已拒絕的未知 enum fallback；缺少 additive 欄位仍由既有 serializer defaults 處理。
- BackupArchiveSemanticValidationTest.kt、BackupArchiveCompatibilityTest.kt、BackupArchiveTestData.kt：經正式 Writer 產生正確 manifest/checksum，再經公開 Reader 驗證拒絕與合法例外。測試在 review 階段拆分並共用合法 fixture。
- BackupRoundTripAuditTest.kt：經實際 Room／Repository 匯出完整快照與附件 bytes；預覽／還原拒絕非法語意時，原資料、來源 archive、已有復原檔及 RestoreViewModel 狀態保持正確。實際交易故障使用 fixture SQLite trigger，無效 enum 不再假冒晚期 DB 故障。
- OfflineRestoreRepositoryTest.kt：未知 enum 的舊測試改名並驗證提前拒絕、無新復原檔及公開 before／after archive 相等；finally 清除測試匯出檔。

不要求歷史明細或 policy 的類型分類等於目前車種。Migration 8→9 的舊共用類型與車輛編輯 producer 都能產生合法差異；只檢查參照存在及真正的擁有者契約。同名類型可分屬 CAR／MOTORCYCLE，提醒 nullable unique 欄位可有多筆 NULL。名稱採資料庫原有精確比較，不作 trim／case folding／歷史 ID 重寫。

format 1 缺少既有 additive 欄位仍保留 defaults：類型 CAR、加油 FULL_SERVICE、統計 UNREVIEWED、新車基準 false、缺省 policy 欄位與空集合等。明確未知 enum 則拒絕；合法零費用、未知時間、無每公升單價與 decimal HALF_UP 明細均有正例。加油支付金額不強制等於單價乘公升。

## 實測證據

輸出目錄：outputs/release-1.1.1-audit/。固定 audit ref e12379551c1cf9b48fb17ddc18f424ad31504700；ticket-11-before 快照隔離已接受的 01–10，限定 diff／source/APK hashes 另存。未建立 commit、PR 或發布。

| 項目 | 實際狀態 | 證據 |
|---|---|---|
| 語意各片段 actual red→green | 非法金額／日期／enum／owner／policy／金額契約均已重現後修正 | TICKET_11_SESSION_2026_10_14.md |
| 歷史機車類型／車種編輯後 policy | actual red→green；公開相容性正例通過 | historical-motorcycle-direct／edited-vehicle-policy logs/XML |
| 類型名稱／提醒非 NULL key 唯一性 | 各有 actual red；全部修正後全套綠 | ticket-11-unique-service-type-actual-red.*、ticket-11-unique-reminder-actual-red.* |
| assembleDebug／testDebugUnitTest／assembleDebugAndroidTest | 10m48s 成功，236/0/0/0 | ticket-11-unique-final-build-unit.log、ticket-11-unique-final-unit/ |
| 舊裝置測試契約 actual red→green | 舊 expected1/actual0 的復原檔斷言已重現；更新後通過 | ticket-11-old-enum-contract-assertion-red.log/json |
| 契約更新後 assembleDebug／testDebugUnitTest／assembleDebugAndroidTest | 1m05s成功；僅 Android test 變更，main／完整JVM任務 UP-TO-DATE | ticket-11-contract-final-build-unit.log；完整236 XML沿用前一列 |
| 實際 Room round-trip／失敗保存性與原 RestoreRepository | 最終 connected10/0/0/0，20s成功；完整快照／附件、提前拒絕、真實晚期交易失敗保存性 | ticket-11-connected-contract-final.log、ticket-11-connected-contract-final/ XML及counts.json |
| 最後 lintDebug | 1m16s成功；重新分析AndroidTest；0 Fatal／0 Error／126 Warning | ticket-11-lint-after-contract.log/xml/-counts.json；report UP-TO-DATE且逐byte等於前次XML，來源記於-report-provenance.json |
| git diff --check | exit0；既有 LF→CRLF 提示 | 主線執行結果 |

## Standards

獨立最終審查 0 硬規違反／0 可行動 smells；公開 seams、共享 fixture 與 nullable unique 檢查符合原有結構。舊裝置測試契約更新後亦複核為0；完整報告見 TICKET_11_REVIEW_2026_10_17.md。

## Spec

獨立最終審查 0 未解 finding：已修正過度嚴格的歷史分類檢查與兩組 schema unique 漏檢；契約更新後複核亦為0。審查與實測為分別完成的證據。

## 實際版本與限制

App1.1.1/code3、DB15、backup format1、compile/target/min SDK37/37/26；AGP9.3.1、Gradle9.6.1、Kotlin2.4.10、KSP2.3.10、JBR25.0.2／Java bytecode11；Compose BOM2026.06.01、Material3 1.4.0、Activity1.13.0、Core1.19.0、Navigation2.9.8、Room2.8.4、Lifecycle2.11.0、DataStore1.2.1、Work2.11.2、Coroutines1.11.0、Serialization1.8.1、Credentials1.6.0、GoogleID1.2.0、PlayAuth21.6.0；JUnit4.13.2、AndroidxJUnit1.3.0、Espresso3.7.0。本票未升級或新增依賴／permissions。

Windows 專案搬遷至中文路徑後，AGP path check 以命令列 override，Gradle test worker 以驗證同檔案身分的 ASCII junction classpath 執行；來源檔未複製／搬移，未改 production build 設定或停用編譯最佳化。override 有實驗選項提示，未抑制警告。冷啟動編譯／lint 與模擬器首次 dexopt 曾競爭 CPU；暫停自有 AVD 後 lint 完成。JVM heap 約860 MiB、Full GC為0，未因推測調大記憶體。新 AVD resume 後仍在驗證系統 PrebuiltBugle（Google訊息）APK，正常關閉並保留磁碟；改以先前已驗證的自有 Ticket08 AVD 啟動，無 wipe。

重用08啟動後第一輪 instrumentation 發生 startup ANR，不能作語意 actual red；同APK暖啟動重跑正常且重現 expected1/actual0，之後更新測試並完成全部10項。沒有以改 production 啟動設定來掩蓋測試結果。

僅自有 BuBu_Ticket11_API35 與重用的 BuBu_Ticket08_API35／emulator-5558／隔離 ADB5042，Android15/API35 google_apis x86_64 rev9；兩份 AVD 磁碟保留，沒有操作手機或真正 Google Drive。本票不代表 ENOSPC／取消／程序強制終止矩陣完成，這些屬 ticket12／13；CB-12 真實 Google 驗證仍獨立 pending。01–11 與16完成，12–15 尚未結案。

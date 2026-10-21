# Ticket 11 完成 — 備份語意與歷史相容性

checksum 正確但業務內容非法的備份，現在於共用 Reader／預覽入口拒絕，尚未建立復原檔或取代資料。合法舊 format1 defaults、歷史機車共用類型及編輯車種後的 policy 均保留；沒有改 schema、format 或依賴。

變更檔案為 BackupArchiveReader.kt、OfflineRestoreRepository.kt、BackupArchiveSemanticValidationTest.kt、BackupArchiveCompatibilityTest.kt、BackupArchiveTestData.kt、BackupRoundTripAuditTest.kt 與 OfflineRestoreRepositoryTest.kt；詳見[驗證記錄與實際版本](TICKET_11_VALIDATION_2026_10_17.md)。TASKS 與本票檢查表已同步。

- Debug build、完整 JVM236/0/0/0、Android test APK通過；最後僅Android測試契約更新後重新build1m05s成功。
- 最終相關 connected10/0/0/0，20s成功；包含完整 Room 往返、非法資料提前拒絕及晚期真實SQLite交易失敗後完整舊資料／附件保存。
- 最後 lint1m16s成功：0 Fatal、0 Error、126既有 warnings；AndroidTest重新分析，未變的報表由Gradle重用並核對XML逐byte相等。diff check exit0。
- [Standards與Spec雙軸審查](TICKET_11_REVIEW_2026_10_17.md)各0未解；最後測試契約變更亦已複審。

證據位於 outputs/release-1.1.1-audit/；ticket-11-final-scoped.diff 隔離本票與前票，ticket-11-final-source-apk-sha256.json 記錄7份來源與2份APK。完整JVM XML在ticket-11-unique-final-unit/，裝置XML在ticket-11-connected-contract-final/，最後lint及來源證明為ticket-11-lint-after-contract.*。

僅操作自有 API35／Android15 模擬器與隔離 ADB5042；未操作手機或實際Drive。Windows中文路徑以測試命令列override及同檔案ASCII junction處理，未改產品build設定。初次instrumentation startup ANR與後續真正斷言失敗分別記錄；最終裝置閘門通過。儲存不足／取消與程序重啟仍分屬12／13；CB-12真實Google帳號驗收獨立pending。本票未建立commit、發布或PR。

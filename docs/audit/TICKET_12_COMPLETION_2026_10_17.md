# Ticket 12 完成 — 儲存故障與取消還原安全

還原取消現在正確傳遞；清理與補償可以在取消後完成。已修正commit與取消相撞時誤刪新資料仍引用附件的缺口；補償再遇ENOSPC，仍保留完整old或new與有效復原備份。舊附件清理失敗透過既有queue重試，空間預檢涵蓋目前復原快照、incoming內容及SQLite／WAL工作餘裕。

- 最終Debug build／完整JVM236/0/0/0／Android test APK：1m25s成功。
- 最終相關整合21/0/0/0：21s成功；11項新案例＋原10項，涵蓋真正SQLite FULL、實際部分寫入／ENOSPC、staging／交易／commit／清理取消與再次補償失敗。
- 最終lint：1m55s成功，0 Fatal／0 Error／125 Warning；較11少1項，無新增warning。初次127提示已修正，沒有壓制lint。
- Standards硬規0／可行動smells0；Spec0。diff check、來源／APK雜湊與文件連結核對另保存。

變更來源共9份：OfflineRestoreRepository.kt、OfflineBackupRepository.kt、PrivateAttachmentStore.kt、RestoreSafetyFixture.kt、RestoreCancellationGate.kt、RestoreCancellationSafetyTest.kt、RestoreAttachmentSafetyTest.kt、RestoreStorageSafetyTest.kt、RestoreCommitSafetyTest.kt。另同步TASKS、本票issue及本組驗證／審查／結案文件。單一app、DB15、backup format1、依賴與permissions未變。

完整actual red→green、故障fixture修正、版本及環境見[驗證記錄](TICKET_12_VALIDATION_2026_10_17.md)，兩軸見[審查](TICKET_12_REVIEW_2026_10_17.md)。主要證據為outputs/release-1.1.1-audit/ticket-12-statfs-final-*，限定diff與9份來源／2份APK雜湊另存；最後來源／APK副本保存於ticket-12-final-artifacts/。

空間倍數／16MiB屬heuristic而非容量reservation，不能保證任意archive或並行磁碟佔用；實際寫入故障仍由交易與復原保護。只用自有API35／Android15 AVD、隔離ADB5042；Windows中文路徑的測試命令列override／ASCII別名限制保留記錄。沒有操作手機或實際Drive，沒有commit／PR／發布；真正程序終止驗證續由13完成，CB-12真實Google帳號仍獨立pending。

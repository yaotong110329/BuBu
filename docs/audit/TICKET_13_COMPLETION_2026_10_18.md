# Ticket 13 完成記錄 — 2026-10-18

已完成。四個真正程序終止控制點在相同最終APK上重播通過；新程序先觀察完整舊／新快照，再驗安全清理、原recovery及明確手動復原。審查另外找到私有還原副本回收漏驗，已取得實際red後補最小產品修正，並取得使用中副本保護的red→green。

## 來源與行為

- OfflineRestoreRepository.kt：來源完整複製／sync後，在既有私有restore cache回收超過24h、canonical UUID命名的一般副本；保留新檔、未知檔及其他目錄，失敗log後續重試。跨instance程序內active集合在建立前登記canonical path，finally刪除後才釋放，避免時間戳老化時誤刪使用中的副本。沒有自動重做DB覆蓋或改動recovery保留規則。
- RestoreSafetyFixture.kt：具名WAL Room、可回傳target檔名、實際UUID seed mapper；原TRUNCATE預設保持，供既有FULL測試使用。
- RestoreProcessRestartProbeTest.kt／RestoreRestartProbeFiles.kt：僅AndroidTest的prepare／freshverify與同步manifest／gate。主機核對自有AVD、私有ADB、SDK程序及App PID／cmdline後真正force-stop，確認PID消失；下一次instrumentation以不同PID重開同一DB。未帶host參數明確skip。
- BackupArchiveAssertions.kt：正式Reader全DTO及每份被引用附件bytes共同比對，舊fixture入口保留委派。
- RestoreTemporaryFileSafetyTest.kt：真實還原停在交易中，第二instance預覽不得回收仍使用中但被測試設為老化的candidate，還原後仍須完整匯出。

只有一個production檔在本票修改；PrivateAttachmentStore與OfflineBackupRepository與票前snapshot逐byte相同。總計六個來源；沒有依賴、schema、format、permission或UI字串變更。

## 最終真正程序終止重播

每個控制點包含9個集合、10個實際UUID、全部關聯／金額／規則與2份引用附件的完整內容。比較正式重新匯出的BackupData及ZIP內容，沒有以DAO結果當作保存性斷言。

| 控制點 | 被終止PID → 重開PID | 第一個觀察結果 |
|---|---|---|
| 第一次product clear前 | 5462 → 5520 | 完整原快照 |
| clear後、晚期attachment insert前的交易中 | 5572 → 5628 | 完整原快照 |
| transaction commit後、pending清理前 | 5677 → 5734 | 完整incoming快照 |
| 第一份舊附件實際unlink後、delete callback回傳前 | 5783 → 5837 | 完整incoming快照；已刪一份舊檔、另一舊檔仍可讀 |

每次先觀察，再匯出有效原recovery；僅老化本probe擁有的managed檔案，經正式service maintenance後引用附件仍完整、未引用檔安全清理。另記錄真正kill留下的cache candidate，確認它仍是完整incoming archive；只將該副本設為25h舊，下一次公開preview須回收它，並保留fresh UUID副本及aged未知命名控制檔。最後明確呼叫手動restore回到完整原資料，沒有默默重播覆蓋。

各次證據為 outputs/release-1.1.1-audit/ticket-13-final-{before-clear,in-transaction,after-commit,during-cleanup}.json及prepare／verify logs；彙整ticket-13-final-replays.json同時核對所有旗標、PID消失及與最終封存APK一致的SHA-256。Prepare被kill的runner不是JUnit pass；真正判定來自fresh verify的實際OK(1 test)與公開斷言。

## 實際缺口及修正

| 回歸 | Actual red | Green |
|---|---|---|
| 程序終止留下的私有copied archive | ticket-13-temporary-copy-actual-red；完整資料／recovery檢查先通過，但next preview仍留過期副本，0.707s失敗 | ticket-13-temporary-copy-green，PID4848→4904，temp回收及所有資料／附件／手動復原旗標true |
| 第一版回收策略的並行保護 | ticket-13-live-candidate-actual-red；僅按age回收誤刪另一個仍運作中的restore candidate，0.780s失敗 | 加跨instance active ownership後同測試0.846s OK1，ticket-13-live-candidate-green |

第一個是已確認的暫存檔殘留，未觀察到資料毀損；第二個是新增回收策略的安全性回歸，於交付前修正。沒有因模擬器啟動失敗而臆測產品修正。

## 品質閘門及審查

- 最終Debug／完整JVM／test APK：25s成功；243 tests、0 failures/errors/skips，ticket-13-final-build-unit.log、ticket-13-final-unit/。
- 普通connected：19s成功；XML共24 tests、22通過、0 failures/errors、2個無host參數協作方法按設計skip，ticket-13-final-connected.log／同名XML與counts目錄。Console重複計入skip事件顯示Finished26，精確計數以XML為準，不把skip當成通過的process實驗。
- 四次同最終APK真正kill／freshverify皆通過，另含temp回收與partial cleanup證據；不以普通connected的兩個skip取代。
- 最終Lint：2m44s成功；0 Fatal／0 Error／125 Warning，ticket-13-final-lint.log／xml／counts。忽略純行號位移、按id／severity／message／檔案比較，與Ticket14及Ticket12最後報告均0新增／0移除，ticket-13-final-lint-comparison.json。
- [雙軸審查](TICKET_13_REVIEW_2026_10_18.md)：Standards0硬規／0未解smell；Spec0未解，原temp回收P2已解決。scoped diff check exit0。
- 六份來源、主機controller及兩份APK封存在ticket-13-final-artifacts/，ticket-13-source-apk-sha256.json保存雜湊；Ticket12／14的歷史來源與APK封存保持。沒有建立commit、PR或發布。

## 版本與環境界線

App1.1.1/code3，DB15、backup format1；SDK compile/target/min37/37/26；AGP9.3.1、Gradle9.6.1、Kotlin2.4.10、KSP2.3.10、JBR25.0.2／bytecode11；Room2.8.4、Coroutines1.11.0、Serialization1.8.1；Compose BOM2026.06.01／Material3 1.4.0，皆未升級。

只操作專用BuBu_Ticket08_API35（Android15/API35 google_apis x86_64）、emulator-5558／隔離ADB5042；owner launcher20792／qemu20696。先前10/17模擬器啟動ANR與開機逾時、額度自動審查失敗均獨立記錄，沒有算成產品red；10/18核對空inventory／ports／alias後，以相同userdata成功重啟，未wipe。Lint期間pause，不操作其他模擬器。Windows中文路徑只用既有命令列path override與同檔ASCII測試classpath。

本票覆蓋列出的四個程序終止時點，不宣稱裝置斷電或所有機器指令位置皆已驗證。暫存回收是24h grace後的下一次成功preview／restore讀入流程，非手機啟動時立即清空cache；不刪recovery。未操作使用者手機或實際GoogleDrive，CB-12真實帳號驗收仍獨立pending。保養完成鏈的語意整合由Ticket15繼續驗證。

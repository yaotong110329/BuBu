# Ticket 12 驗證記錄 — 2026-10-17

狀態：已完成。最後StatFs讀值修正後build／完整JVM236／test APK、相關裝置21/0/0/0、lint0 errors／125 warnings、diff check均通過；Standards與Spec複審均0未解。

## 變更

- OfflineRestoreRepository.kt：取消保持CancellationException；失敗清理／補償在NonCancellable內完成，補償本體錯誤與返回取消分開。讀取原附件路徑納入清理範圍；多附件staging取消也完成清理。commit後才收到失敗時，先查DB引用，仍被引用或查詢失敗的檔案保留。舊檔與原待清理工作在替換交易內進既有pending-deletion表，提交後盡力清理，失敗保留重試。
- PrivateAttachmentStore.kt：restoreManagedBytes處理寫入後未交付的檔案；與stage共用狹窄失敗清理helper。僅在真實filesystem邊界增加output／delete factory，預設仍FileOutputStream與File.delete。
- OfflineBackupRepository.kt：與實際writer共用readSnapshot，估計當前復原備份所需原始metadata／附件容量，不把壓縮率當作空間保證。
- 可用空間預設以StatFs.availableBytes讀取當下freeblocks；不假設可回收快取已分配，注入seam只用於控制測試讀值。
- RestoreSafetyFixture.kt、RestoreCancellationGate.kt及四份Restore*SafetyTest.kt：真實Room、正式archive、實際檔案；SQL只用於建fixture、限制頁數與控制實際交易交界。斷言公開Repository匯出、正式Reader與全部被引用附件bytes，沒有DAO結果或call-count斷言。

單一app module、DB15、backup format1、依賴、permissions與UI資源均未變；沒有新通用filesystem框架。TASKS及本票issue持續記錄狀態。

## Actual red 與結果

證據根目錄：outputs/release-1.1.1-audit/。ticket-12-before/ 保存開始前3份production及未修改的RestoreViewModel基準；ticket-12-scoped.diff限定9份本票來源，source/APK hashes另存。沒有建立commit、PR或發布。

| 故障 | 實際重現／修正後結果 | 證據標籤 |
|---|---|---|
| 建立復原檔時取消 | 原本誤包成read/validation錯誤；修正後完整原快照／附件與取消傳遞正確 | cancel-recovery-actual-red／green |
| DB insert時取消 | 原本補償沿用cancelled context；修正後完整原資料與有效recovery | cancel-transaction-actual-red／green |
| staging後ownership讀取取消 | 原本漏一檔；修正後整組另發現返回取消誤判補償失敗，調整catch位置後3項連續兩次綠 | cancel-after-staging-actual-red；cancel-staging-diagnostic-all；cancel-staging-delivery-green／confirm |
| 附件寫入與交付取消 | 原本目的檔未交付卻遺留；outer failure cleanup修正 | attachment-cancel-actual-red／green |
| 第二附件寫入取消 | 原本第一份已交付staging檔仍漏一檔；NonCancellable清理修正 | multiple-staging-cancel-actual-red／green |
| 真正SQLite FULL | 自有disk-backed TRUNCATE資料庫max_page_count限制，valid incoming需增頁；整份原資料／附件與復原檔保留 | sqlite-full-system-evidence.log（SQLite code13）；sqlite-full-preservation |
| Recovery部分寫入ENOSPC | 寫16 bytes後故障；舊已發布復原檔仍可匯出，原快照完整、無part殘檔 | recovery-enospc |
| staging及補償再次ENOSPC | 兩次真實partial write；原資料完整，來源未改，有效recovery仍可取回；不稱為資料毀損 | double-enospc |
| commit後舊檔delete=false | 原本清理工作無立即重試；交易內queue及後續維護retry通過，已提交快照完整 | cleanup-retry-assertion-red／green |
| 小incoming／大current recovery空間 | 原本漏算2MiB目前附件；修正後256KiB可用值提前拒絕，不換掉原資料／已有復原檔 | space-budget-actual-red／green |
| commit取消＋補償ENOSPC | 原本SQLite已commit但清理誤刪其引用附件，public export失敗；引用檢查後完整old或new及原recovery可讀 | commit-cancel-actual-red／green |
| commit後刪舊檔時取消 | 完整old或new、取消傳遞與原recovery均保持 | cleanup-cancel |

表中標籤均有ticket-12-前綴，instrumentation log／JSON保存實際APK雜湊及時間。11項@Test中，附件取消案例先驗單檔再擴為多檔，表列分片不相加成不同@Test。初次清理故障fixture因Android私有目錄別名未canonical而未命中，不算產品red；校正後真正重試斷言才算。SQLite首次測試要求錯誤cause一定是SQLiteFullException過嚴，Room的rollback錯誤可覆蓋它；校正公開保存性斷言、保留code13系統證據，沒有為此修改產品。

## 空間預檢的界線

目前計入candidate檔長（保守額外餘裕）、incoming未壓縮內容與附件、目前備份原始metadata及被引用附件的2倍＋2MiB manifest／ZIP餘裕；SQLite採max(目前page_count×page_size, incoming JSON×4)×2，再加目前WAL長度與16MiB餘裕。所有相加／乘法檢查overflow，沿用50MiB metadata、20MiB單附件及512MiB總內容／archive上限。

上述倍數與固定餘裕是保守budget／heuristic，不是對所有合法archive之SQLite/index配置的嚴格上界，也不是檔案系統容量reservation。其他工作仍可搶走空間，DB或附件也可能在量測後改變；遇到實際ENOSPC仍依賴已測交易、引用檢查及有效復原備份。未宣稱512MiB內容能在所有手機heap一次載入；也未宣稱本票已驗證程序強制終止，那屬13。

## 品質閘門

- Review前build／完整JVM236/0/0/0、test APK：30s成功，ticket-12-review-build-unit.log。
- Review整理後首輪build／完整JVM236/0/0/0／test APK：47s成功，ticket-12-final-build-unit.log、ticket-12-final-unit/；21項connected亦成功。初次lint0 errors／127 warnings，多1項constructor File.usableSpace提示，未忽略。
- StatFs修正後最終build／完整JVM236/0/0/0／test APK：1m25s成功，ticket-12-statfs-final-build-unit.log及ticket-12-statfs-final-unit/全套XML。
- 最終connected：21s成功，21/0/0/0（原本10＋新增11），ticket-12-statfs-final-connected.log及同名目錄XML／counts.json。
- 最終lint：1m55s成功，0 Fatal／0 Error／125 Warning，ticket-12-statfs-final-lint.log/xml/-counts.json。與11的126相比無新增issue，移除原UsableSpace一項；先前127報告保留。diff check exit0。
- [雙軸審查](TICKET_12_REVIEW_2026_10_17.md)：Standards硬規0／smells0；Spec0。

## 版本及環境

App1.1.1/code3，DB15／backup format1，SDK compile/target/min37/37/26，AGP9.3.1、Gradle9.6.1、Kotlin2.4.10、KSP2.3.10、JBR25.0.2／bytecode11，Room2.8.4、Coroutines1.11.0、Serialization1.8.1，Compose BOM2026.06.01／Material3 1.4.0；其他未變版本列於[Ticket11驗證](TICKET_11_VALIDATION_2026_10_17.md)。沒有更新／新增依賴。

只操作自有BuBu_Ticket08_API35，Android15/API35 google_apis x86_64 rev9，emulator-5558／隔離ADB5042。SDK控制前核對owner、CIM executable／parent／命令列與AVD身分；AVD於編譯時pause節省CPU，不wipe。Windows中文路徑沿用命令列path override與驗證同檔身分之ASCII classpath junction，沒有改產品build設定或停用最佳化。沒有操作手機、預設ADB或實際GoogleDrive；CB-12真實帳號驗收仍獨立pending。

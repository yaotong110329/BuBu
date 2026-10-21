# Release 1.1.1 能力盤點與規格差異

核對日期：2026-10-17。發布基準為 `e12379551c1cf9b48fb17ddc18f424ad31504700`，另核對目前工作樹。App metadata 仍是 1.1.1/code3；工作樹中的發布後 hardening 尚未另行發布，不能推定 GitHub 上既有 APK 已包含這些修正。

[Spec.md](../../Spec.md) 仍為最高權威；[CONTEXT.md](../../CONTEXT.md) 定義用語，[ADR](../adr/) 記錄已接受決策。本盤點描述可查證現況，不以文件替代產品決策、驗收或實作。

## 判讀方式

- **已實作**：可追溯至實際資料、操作與正常 UI 入口；不等於所有故障／實機情境都已驗收。
- **部分實作**：已有資料層、畫面或部分操作，但完整規格或一般入口尚缺。
- **未實作**：未找到相應 schema、操作與 UI 能力。
- **明確核准延期**：必須有明確決策來源及範圍。本次列出的缺口沒有查得足以核准 Release 永久減項／延期的紀錄，不自行套用此狀態。
- **待決／待安排**：規格要求仍有效；後續實作、分期安排或變更規格需另有明確任務／決策。

## 實際能力

| 能力 | 現況與限制 | 可追溯來源 |
|---|---|---|
| 頂層導覽 | 已實作四頁：首頁、提醒、報表、設定。車輛管理是設定下的子頁；單車歷史由首頁進入。Spec §9 的五頁清單是建議，不應拿早期 scaffold 數量描述目前 UI。 | [TopLevelDestination](../../app/src/main/java/com/kumo/bubu/navigation/TopLevelDestination.kt)、[BuBuNavHost](../../app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt) |
| 多車輛與目前車輛 | 已實作汽車／機車、編輯、封存／解除封存、空車永久刪除、穩定識別與本機選車。 | [VehicleRepository](../../app/src/main/java/com/kumo/bubu/domain/repository/VehicleRepository.kt)、[OfflineVehicleRepository](../../app/src/main/java/com/kumo/bubu/data/repository/OfflineVehicleRepository.kt)、[VehiclesScreen](../../app/src/main/java/com/kumo/bubu/feature/vehicle/VehiclesScreen.kt) |
| 加油與金額 | 已實作新增／編輯／刪除、滿箱、油品、人工／自助、三欄任填兩欄計算與每車偏好。刪除是永久刪除；沒有垃圾桶。 | [FuelRecordEntity](../../app/src/main/java/com/kumo/bubu/data/local/entity/FuelRecordEntity.kt)、[OfflineFuelRepository](../../app/src/main/java/com/kumo/bubu/data/repository/OfflineFuelRepository.kt)、[FuelFormScreen](../../app/src/main/java/com/kumo/bubu/feature/fuel/FuelFormScreen.kt) |
| 油耗、異常檢視 | 已實作連續滿箱區段、中間油量、最近三段加權平均與終點紀錄的 UNREVIEWED／INCLUDED／EXCLUDED 判定。週期重啟及完整資格模型尚缺，詳下表。近期異常基準的修正另由 ticket14 處理，不能視為本票已修。 | [FuelEconomy](../../app/src/main/java/com/kumo/bubu/domain/model/FuelEconomy.kt)、[FuelRecord](../../app/src/main/java/com/kumo/bubu/domain/model/FuelRecord.kt) |
| 服務工單與保養 | 已實作工單／多項目 CRUD、內建與自訂服務類型、提醒關聯及歷史衍生保養狀態。當前簡化表單以一件、項目金額保存；歷史 quantity／price／due 欄位保留，符合 Spec §9.4 的當前表單規定。 | [OfflineServiceRepository](../../app/src/main/java/com/kumo/bubu/data/repository/OfflineServiceRepository.kt)、[ServiceFormScreen](../../app/src/main/java/com/kumo/bubu/feature/service/ServiceFormScreen.kt)、[ADR0005](../adr/0005-derive-maintenance-reminders-from-service-history.md) |
| 其他支出 | 部分實作：schema、Repository CRUD、清單／表單與 routes 已存在，也納入匯出／備份。但 expense/records 沒有一般導航入口；其新增／編輯只由該清單內部連出。不能宣稱使用者已可由正式入口完成全套支出操作。 | [ExpenseScreens](../../app/src/main/java/com/kumo/bubu/feature/expense/ExpenseScreens.kt)、[OfflineExpenseRepository](../../app/src/main/java/com/kumo/bubu/data/repository/OfflineExpenseRepository.kt)、[BuBuNavHost](../../app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt) |
| 單車歷史 | 已實作 Fuel／Service 混合排序、全部／加油／服務篩選、關鍵字及日期範圍、編輯與刪除。不是涵蓋全部支出的統一時間軸；差異見下表。 | [VehicleHistoryViewModel](../../app/src/main/java/com/kumo/bubu/feature/history/VehicleHistoryViewModel.kt)、[VehicleHistoryScreen](../../app/src/main/java/com/kumo/bubu/feature/history/VehicleHistoryScreen.kt) |
| 報表 | 已實作目前單台未封存車輛的摘要、花費／油耗圖表、期間與來源加油／服務操作。車輛選擇器排除封存車。歷史 TASKS 的單車 UX 驗收不等於取消 Spec 的封存資料要求。 | [ReportsViewModel](../../app/src/main/java/com/kumo/bubu/feature/reports/ReportsViewModel.kt)、[OfflineReportRepository](../../app/src/main/java/com/kumo/bubu/data/repository/OfflineReportRepository.kt)、[TASKS 的報表 UX 紀錄](../../TASKS.md) |
| 提醒與通知 | 已實作保養、手動、法定提醒、延後／完成、WorkManager 與通知。新車零公里基準需 opt-in，實際工單優先。資料不足的完整里程預估驗收沒有可查證延期核准，保留待確認。 | [RemindersViewModel](../../app/src/main/java/com/kumo/bubu/feature/reminder/RemindersViewModel.kt)、[OfflineReminderRepository](../../app/src/main/java/com/kumo/bubu/data/repository/OfflineReminderRepository.kt)、[MileageForecast](../../app/src/main/java/com/kumo/bubu/domain/model/MileageForecast.kt) |
| 附件 | 已實作服務工單 App 私有 JPEG／PNG／WebP、預覽與刪除。PDF、車輛封面及其他擁有者的通用附件不是目前完整能力。 | [PrivateAttachmentStore](../../app/src/main/java/com/kumo/bubu/data/attachment/PrivateAttachmentStore.kt)、[ServiceAttachmentEntity](../../app/src/main/java/com/kumo/bubu/data/local/entity/ServiceAttachmentEntity.kt)、Spec §5.8／§9.4 |
| CSV、完整備份與還原 | 已實作可讀 CSV ZIP、format1 .bubu、引用附件、manifest/hash、覆蓋還原及私有復原備份。包含封存車；App 偏好與可重建統計不加入備份。沒有垃圾桶 schema，因此不能宣稱已備份不存在的垃圾桶資料。ticket11–13 的發布後驗證各自追蹤。 | [OfflineCsvExportRepository](../../app/src/main/java/com/kumo/bubu/data/repository/OfflineCsvExportRepository.kt)、[BackupData](../../app/src/main/java/com/kumo/bubu/data/backup/BackupData.kt)、[OfflineRestoreRepository](../../app/src/main/java/com/kumo/bubu/data/repository/OfflineRestoreRepository.kt)、[ADR0003](../adr/0003-separate-readable-csv-from-restorable-backup.md) |
| Google Drive | 已有選用的 appDataFolder 備份／清單／下載／還原／刪除實作，不是同步。真實單帳號附件、五份保留、解除再連結等驗收仍為 CB-12 pending；模擬 HTTP／帳號 fixture 的通過不能代替。 | [ADR0004](../adr/0004-store-cloud-backups-as-existing-bubu-files-in-drive-appdatafolder.md)、[TASKS CB-12](../../TASKS.md) |

## 必須保留的規格差異

| Spec 要求 | 目前缺口／已存在的部分 | 狀態與決策邊界 |
|---|---|---|
| §5.2／§8.2／§9.3：滿箱週期重啟、不可信資料的油耗資格 | schema15 沒有 startsNewConsumptionCycle／excludeFromFuelEconomy。統計終點排除狀態可用，但滿箱仍成為下一錨點；沒有明確重啟或漏記／順序未確認的資格流程。 | 部分實作；未核准延期，需另安排完整資格與週期設計。不得將現有統計排除當成同義實作。 |
| §5.6／§8.1：里程修正保存原值、新值、原因與時間 | 可編輯原紀錄並重算最大里程，但無 OdometerCorrection 表、原因與修正事件歷程；updatedAt 不等於保留原值。 | 未實作稽核歷程；規格仍有效，待安排。 |
| §5.6／§8.1：儀表更換與累積偏移、中斷油耗 | 無 OdometerReplacement 表／DAO／畫面／偏移計算；目前里程採追蹤基準與紀錄最大值。 | 未實作；未核准以最大值模型永久替代。 |
| §5／§6／§8.6：垃圾桶 30 天、還原、期限後永久刪除 | fuel／service／expense 無 deletedAt、垃圾桶 route 或保留期工作；delete 操作永久刪除。pending_attachment_deletions 是附件清理佇列。 | 未實作。Spec §7 明定 Phase3 尚無垃圾桶時的永久刪除過渡行為；這個階段允許不等於核准 Release 永久取消 §8.6。 |
| §6／§9.2／§9.6：封存車歷史可閱／搜／改與統計 | CSV／完整備份保留封存車，Repository 允許原車舊紀錄編輯；報表 selector 明確排除封存車，車輛管理也沒有直接歷史入口。 | 部分實作；封存仍保留統計意義的規格差異待解決，未核准延期。 |
| §9.5：涵蓋稅費／保險等支出、金額範圍、複製成新紀錄 | 歷史 model 只有 Fuel 與 Maintenance，filter 只有 ALL／FUEL／MAINTENANCE；有日期與關鍵字，沒有金額範圍與複製操作。支出 routes 的一般入口也缺少。 | 部分實作；不把單車 Fuel／Service 時間軸稱為完整規格歷史頁。 |
| §5.8／§9.1／§9.3：PDF、車輛圖片、加油收據等 | 目前附件 table 的 owner 是 ServiceRecord，MIME whitelist 只有 JPEG／PNG／WebP。 | 部分實作；Spec §9.4 明定目前工單圖片範圍，但一般附件要求仍須另有明確安排。 |
| 里程預估「資料不足」驗收 | README 曾稱依本輪暫緩；本次在 Spec／ADR／TASKS 找不到明確核准來源。 | 驗收待確認；移除無來源的延期宣稱，不宣告驗收已完成。 |

此票不新建上述功能、不更動 schema／format、不移除現有 scaffold，也不修改 Spec 來消除差異。若要減項、延期或改 domain 契約，必須記錄明確決策來源；本次沒有作這些決策。

## 可重查的基準

- schema15 實際有十張表：vehicles、fuel_records、service_records、service_items、service_types、expense_records、service_attachments、vehicle_reminders、vehicle_service_reminder_preferences、pending_attachment_deletions。依據 [schema15](../../app/schemas/com.kumo.bubu.core.database.BuBuDatabase/15.json)。
- App1.1.1/code3、compile/target/min37/37/26、backup format1；AGP9.3.1、Gradle9.6.1、Kotlin2.4.10、KSP2.3.10、Compose BOM2026.06.01、Room2.8.4、DataStore1.2.1。依據 [app build](../../app/build.gradle.kts)、[catalog](../../gradle/libs.versions.toml)、[wrapper](../../gradle/wrapper/gradle-wrapper.properties)、[manifest model](../../app/src/main/java/com/kumo/bubu/data/backup/BackupManifest.kt)。
- 發布基準可用 `git show e12379551c1cf9b48fb17ddc18f424ad31504700:<相對路徑>` 重查；目前工作樹的 routes、schema、操作入口則依上方連結。原始碼雜湊與欄位盤點另存本機 audit 證據 ticket-16-release-inventory.json。
- 本票以文件、route 引用、schema 欄位與操作實作核對，不新增無關 App build；它不取代 ticket11–15 的實際故障／裝置閘門。CB-12 的真實 Google 驗收持續獨立 pending。

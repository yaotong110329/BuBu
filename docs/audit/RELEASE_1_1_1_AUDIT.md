# BuBu v1.1.1 Repository Deep Audit

Audit date: 2026-09-13 (Asia/Taipei). Reviewed snapshot: `e123795` (`feat: add maintenance reminder center`), clean working tree before audit. This is a full current-repository review, not a last-commit-only diff. Root AGENTS.md, all repository skill files, Spec, CONTEXT, TASKS, README, CHANGELOG and all five ADRs were read. Spec has highest priority.

Source evidence paths omitting the repository prefix are based at `app/src/main/java/com/kumo/bubu/`; JVM and Android tests live under the matching `app/src/test/java/` and `app/src/androidTest/java/` trees. All paths are relative to the BuBu repository root.

## Executive Summary

目前產品的 single app、feature-first、離線優先架構與主要資料交易具備實際保護；沒有證據支持 Critical、資料庫毀損或憑證洩漏的判定。最重要的確認問題是 **本機備份 writer verifier 與還原 reader 的可接受大小不一致**：實際產出的 520 MiB 附件 archive 被 writer verifier 接受，卻無法進入還原。

另有已重現的表單計算溢位與油耗異常比較基準錯誤。通知、日期更新、process death、ActivityResult、雲端帳號／分頁與復原檔發布仍有中等可靠性問題；未實機重現的項目明確標示 `Not reproduced`。大檔案、沒有某個 Test.kt、保留 scaffold 和未完成 Google 真實帳號驗收，均不被當作 High 缺陷。

Baseline snapshot open findings: **Critical 0 / High 1 / Medium 11 / Low 2**. Medium includes nine Code Findings and two Test Gaps; M12 is the additional Medium baseline test gap now closed by the audit; Low includes one Code Finding and one Documentation Drift. TD candidates are not added to these totals. Audit-added tests target the baseline round-trip/historical-path gaps; they are not closed solely from source or compilation. Final runtime status appears below: all four audit additions passed within the complete 74-test instrumentation suite. Subsequent implementation status is recorded separately at the end of this report.

修復建議第一票：讓成功的本機／復原備份必定符合既有 restore reader 契約，建立前檢查資料量，失敗不發布可誤認為有效的檔案。維持 schema 15、backup format 1、navigation 與依賴版本。

## Baseline Verification

正式指令以單一順序執行，沒有重疊 Gradle build；未修改 production code。第一個 sandbox `--stop` 因 distribution 存取被拒，取得執行權限後正常通過。這是環境限制，不是 App build failure。

| Check | Actual result | Evidence |
| --- | --- | --- |
| `--stop` | Passed on authorized retry | outputs/release-1.1.1-audit/stop-retry.log |
| `clean` | Passed, 59s | clean.log |
| `:app:assembleDebug` | Passed, 8m23s | assemble.log |
| `:app:testDebugUnitTest` | Passed, **147 tests / 44 suites / 0 failures / 0 errors / 0 skipped** | unit-baseline.log; app/build/test-results/testDebugUnitTest |
| `:app:lintDebug` (baseline) | Passed, 26m38s; **0 errors / 137 warnings** | lint-baseline.log |
| `:app:connectedDebugAndroidTest` | First attempt failed before APK installation; **0 tests executed**. Emulator activity/package services died; successful final retry is recorded separately | instrumentation-baseline.log |
| `:app:assembleDebugAndroidTest` (audit tests) | Passed, 14m28s; one redundant Unit-expression warning corrected afterward; final recompilation and all four audit tests passed with no new Kotlin warning | audit-test-compile.log |
| `:app:connectedDebugAndroidTest` (final) | **Passed, 74 tests / 0 failures / 0 errors / 0 skipped**, 4m38s; includes four audit methods | instrumentation-final.log; final connected JUnit XML |
| `:app:lintDebug` (final) | **Passed, 0 errors / 137 warnings**, 1m23s; updated Android-test sources analyzed | lint-final.log; lint-results-debug.xml |
| Audit compiled-code probes | **27 checks passed as observations; three defect mechanisms reproduced** | probes.log; AuditProbe.java |

Build warning: native libraries `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so` could not be stripped and were packaged unchanged. No Kotlin production compiler warning was observed in assemble.log. This is not evidence of a runtime defect.

Lint warnings: 112 UnusedResources; 11 dependency/AGP availability notices; 4 ModifierParameter; 4 UseKtx; and one each CredentialManagerMisuse, IconLauncherShape, PluralsCandidate, TypographyDashes, UsableSpace, UseTomlInstead. Credential selection already catches Exception and returns null, so the missing dedicated NoCredentialException branch is an error classification/testing concern rather than an uncaught crash. Availability notices do not authorize dependency upgrades; unused-resource cleanup is deferred and not a High finding.

Final environment resolution: existing API35 AVD initially cold-booted slowly and then lost Android system services (DeadSystemException in non-BuBu processes before APK installation). Reboot alone did not recover readiness. The same AVD with host GPU, Vulkan disabled and no snapshot load/save completed boot and passed the entire suite. No userdata/snapshot wipe, live Google account operation or physical-device data change was performed. The audit-started emulator was gracefully stopped afterward. A final lint launch was temporarily rejected by automatic approval review because account usage was exhausted; after the user confirmed restoration, the original command was retried successfully. No approval rejection remains blocking.

### Actual configured technology versions

App 1.1.1 (code 3); DB 15; backup format 1. compile/target/min SDK 37/37/26; build-tools requested by environment 37.0.0; AGP 9.3.1; wrapper 9.6.1; actual Java invocation JBR/OpenJDK 25.0.2; Kotlin/Compose compiler 2.4.10; KSP 2.3.10; Compose BOM 2026.06.01; Material 3 **1.4.0**, resolved through that BOM; Activity Compose 1.13.0; Navigation 2.9.8; Lifecycle 2.11.0; Room 2.8.4; DataStore 1.2.1; WorkManager 2.11.2; Coroutines 1.11.0; Kotlin Serialization 1.8.1; Credentials 1.6.0; Google ID 1.2.0; Play Services Auth 21.6.0.

Sources: app/build.gradle.kts, gradle/libs.versions.toml, wrapper properties, resolved lint dependency model (material3-android:1.4.0) and actual `java -version`. These are repository-configured/resolved baseline values, not recommendations to upgrade.

## Critical Findings

None demonstrated. Original database is not cleared before recovery creation succeeds; Room replacement is transactional. A restore/cleanup/cancellation failure is not, by itself, proof of permanent data loss.

## High Findings

### H01 — 本機備份成功驗證不保證可以還原

Severity: High. Category: Code Finding. Scope: Small for matching limits; Medium if streaming is subsequently measured and justified.

#### Evidence

File: `data/repository/OfflineBackupRepository.kt:94–105`; class OfflineBackupRepository; function writePrivateBackup:
`BackupArchiveWriter.write(...)` then `if (!BackupArchiveVerifier.isValid(destination)) throw ...`.

File: `data/backup/BackupArchiveWriter.kt`; object BackupArchiveVerifier; function isValid: checks entry set, sizes and SHA using unbounded `input.readBytes()`; no total-content/data/manifest reader limit.

File: `data/backup/BackupArchiveReader.kt:36–38,152–155`; object BackupArchiveReader; function read:
`require(manifest.files.totalSizeBytes() <= MAX_TOTAL_CONTENT_BYTES)`;
total = 512 MiB, data = 50 MiB, manifest = 1 MiB, per attachment = 20 MiB.

#### Why this matters

26 個服務工單各一張 20 MiB 圖片並未違反單筆附件數量與單檔限制；總量已超過 reader 上限。長期使用若本機備份被顯示為成功，使用者會誤以為可以還原。實際 App 的全附件 ByteArray 累積也可能先造成 OOM，這個 OOM 尚未重現。雲端 repository 上傳前額外呼叫 reader，因此會先拒絕這類檔案；不把它描述為已成功上傳不可還原雲端檔。

#### Reproduction

Executed audit-only harness against unchanged app classes. Actual BackupArchiveWriter produced an archive with 26 records/items/images and 520 MiB image payload, using a shared buffer in the harness to avoid imposing App's separate allocation behavior. Actual BackupArchiveVerifier returned true; actual BackupArchiveReader threw `Backup is too large to restore safely.`. This proves contract asymmetry, not a measured end-to-end phone heap allocation success. Log: probes.log.

#### Existing coverage

BackupArchiveWriterTest small content/hash fixture; reader test uses empty data and unsafe path. No success-implies-readability size boundary assertion.

#### Proposed regression test

At public writer/local backup seam, test max accepted payload, +1 byte, data/manifest boundaries; every successful result must be accepted by the same restore reader. Enforce file-metadata preflight before retaining all bytes.

#### Recommended fix

Centralize existing limits, validate emitted archives with restore-grade reader and avoid reporting success for rejected output. Write/publish only validated files. No schema or .bubu format change, no unmeasured increase in caps.

## Medium Findings

### M01 — 加油表單自動計算可在驗證前溢位

Severity: Medium. Category: Code Finding. Scope: Small.

Evidence: `domain/model/FuelCalculation.kt:23,31`, calculateFuelPricePerLiter/calculateFuelVolumeMl end in `longValueExact()`; `feature/fuel/FuelFormState.kt:222,228`, withCalculatedValue invokes them without catch; FuelFormViewModel updateField is a synchronous event path.

Why this matters: 貼上極端但可解析為 Long 的總額時，Decimal 雖無精度損失，結果仍無法表示為 Long；未捕捉的 ArithmeticException 會離開輸入 callback。

Reproduction: **Reproduced twice**, volume 0.001 L + total 9223372036854775807, both input orders. Exact output `java.lang.ArithmeticException: Overflow`, probes.log.

Existing coverage: ordinary three calculations, zero divisors, source priority and fractional precision; no overflowing derived price/volume through form state.

Proposed regression test: real withUserValue / form-event public seam, both orders and each derived field, preserve explicit inputs and display field error. Recommended fix: convert unrepresentable derived values into validation state, no clamping or silent precision loss.

### M02 — 保養摘要通知沒有沿用去重與即時失效清理

Severity: Medium. Category: Code Finding. Scope: Medium.

Evidence: ReminderNotificationWorker.kt:142–150, doWork posts maintenance summary whenever nonzero; postMaintenanceNotification has no persisted trigger tracking. Ordinary reminders use selectReminderNotificationTrigger. ReminderNotifications.kt:24–26 cancelDeleted preserves MAINTENANCE_NOTIFICATION_ID; AppContainer.kt:271–285 reconciles individual IDs, not this summary. Spec §8.4 requires same state once and archived/disabled notification suppression.

Why this matters: 相同保養狀態每天再次通知；封存最後到期車輛、停用規則或新增實際工單後，舊摘要可持續顯示到下次 worker。Summary posting also lacks the ordinary reminder's final recheck, leaving an interleaving global-disable gap.

Reproduction: **Not reproduced** on device. Static branch traces above; repeated notification means repeated posting/alert, not multiple permanent tray IDs.

Existing coverage: ordinary trigger-rule tests and reminder screens, no actual maintenance summary worker/notifier lifecycle test.

Proposed regression test: recording notification boundary, two unchanged runs, state transition, archive/disable/completion and disable-before-post. Recommended fix: track maintenance summary trigger/signature and reconcile summary eligibility promptly, recheck setting before posting.

### M03 — 日期跨午夜或背景返回後保養狀態仍使用舊日期

Severity: Medium. Category: Code Finding. Scope: Small/Medium.

Evidence: RemindersViewModel.kt:106 and DashboardViewModel construct observeMaintenanceReminders(todayProvider()) once; OfflineServiceRepository.observeMaintenanceReminders(today: LocalDate) captures that date in combine and passes `today = today` to calculator. No clock/resume signal changes the captured parameter.

Why this matters: 保留 ViewModel 過夜，即使 Room 再發出資料，日期條件仍使用舊日；worker 新取得 LocalDate.now，畫面与通知可能短暫不同。

Reproduction: **Not reproduced** with actual clock/resume. Concrete trigger: open at day before due, background through midnight, resume without recreating VM.

Existing coverage: calculator fixed-date tests; no ViewModel resume/midnight update. Proposed regression: injectable today-flow/day change without data write, compared with fresh calculator/worker. Recommended fix: date signal refresh on resume/day boundary; no background sync.

### M04 — Fuel / Service / Vehicle 草稿無 process-death 保存

Severity: Medium. Category: Code Finding. Scope: Medium.

Evidence: VehicleFormViewModel.kt:23–25, FuelFormViewModel.kt:37, ServiceFormViewModel.kt:42–49 hold memory-only MutableStateFlow; factories restore route IDs but no SavedStateHandle/draft source.

Why this matters: 系統在背景回收 App 後，新增表單回到空白、編輯表單回到原始資料；已輸入但未儲存的內容與服務附件草稿關聯遺失。旋轉一般保留 VM，不能當作同一情境。

Reproduction: **Not reproduced**. Existing form tests cover input/save/editor states, not system process recreation.

Proposed regression: enter halfway, background, system-initiated process kill/recreate, assert fields/item identities and staged references, including attachment cleanup. Recommended fix: persist lightweight draft and safe staged-file ownership; avoid putting image bytes in saved state. [Android saved-state documentation](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate).

### M05 — Settings 外部操作 callback／下載清理 context 在旋轉後遺失

Severity: Medium. Category: Code Finding. Scope: Small/Medium.

Evidence: BuBuNavHost.kt:234,253,260–261 uses `remember` for pendingCsvExportRequest, cloudDownloadPath, pendingCloudAction/account. Authorization result only proceeds when pending values exist (:263–277); CSV request likewise guarded (:320–326). Completed/cancelled restore cleanup uses cloudDownloadPath (:315,:463).

Why this matters: SAF 已建立檔案卻未匯出、授權成功卻不執行動作；下載預覽旋轉後保留 VM 的 URI，但失去 cleanup path，取消／完成可能留存私有 cache。這不是手機資料被自動覆蓋。

Reproduction: **Not reproduced**. Existing SettingsScreen tests bypass real ActivityResult/NavHost recreation.

Proposed regression: rotate during SAF/auth and downloaded preview; callback runs once and owned temp file is deleted. Recommended fix: retained pending-operation state and explicit download ownership; do not persist access token.

### M06 — 第二台車的通知入口找不到對應提醒

Severity: Medium. Category: Code Finding. Scope: Small/Medium.

Evidence: BuBuNavHost.kt:179–200 constructs ordinary RemindersViewModel without reminder owner; RemindersViewModel.kt:109–114 selects first active vehicle and filters rows; RemindersScreen.kt:179–181 searches highlightedReminderId only in selected rows, scrollToItem(targetIndex + 1) ignores new preceding maintenance sections. Maintenance summary intent at Worker.kt:196–197 has no destination extra, so cold launch opens dashboard.

Why this matters: 點第二台車的提醒通知會進入第一台車列表，无法定位真正待辦；混合維護段落的 ordinal scroll 不可靠。

Reproduction: **Not reproduced**. Existing bottom-nav and reminder isolated screen tests do not execute notification→Activity→route→vehicle selection.

Proposed regression: two vehicles, notified reminder owned by second, multiple maintenance sections. Recommended fix: resolve owner/target and stable scroll mapping when separately authorized navigation fix ticket is implemented. No navigation change during audit.

### M07 — 非連結操作忽略新選擇帳號，畫面可能仍顯示舊帳號

Severity: Medium. Category: Code Finding. Scope: Small/Medium.

Evidence: GoogleDriveAuthorizationCoordinator.authorize selects account every time using filterByAuthorizedAccounts(false), sets request's account. CloudBackupViewModel.onAuthorized uses accountEmail only for Connect; Upload/LoadBackups/Download/Delete use only accessToken. DataStoreCloudBackupConnection timestamp update does not update accountEmail.

Why this matters: 已連結 A、操作時選 B，token 的檔案屬於 B，畫面與 last backup 時間仍標示 A。這是帳號狀態可靠性問題，不是未授權憑證洩漏；使用者選 B 本身是授權。

Reproduction: **Not reproduced** with real accounts. Existing VM tests cover normal connection/expired token, not mismatched selected/stored email.

Proposed regression: Connected(A), onAuthorized(Upload/LoadBackups,B,tokenB), assert explicit account switch/rejection before operation. Recommended fix: authorize stored account for existing operations or explicitly update/confirm switching and reset account-specific list state. Real multi-account acceptance is not silently added to CB-12.

### M08 — Drive 清單只讀單頁，保留策略與完整清單依賴截斷結果

Severity: Medium. Category: Code Finding. Scope: Small/Medium.

Evidence: HttpGoogleDriveBackupDataSource.list, GoogleDriveBackupDataSource.kt:98 requests `fields=files(... )&pageSize=100`; no nextPageToken field or loop. OfflineCloudBackupRepository.retainNewestBackups uses that list.

Why this matters: 連續保留清理失敗留下多於一頁的備份，或服務回傳較短分頁時，舊檔可能永久漏列／漏刪；不是普通第六份備份必然失敗。 [Google files.list contract](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/list) specifies continuation tokens and permits fewer files than requested.

Reproduction: **Not reproduced** against Google. Code has no continuation path. Datasource test only verifies appDataFolder parent JSON.

Proposed regression: two pages, first shorter than requested with nextPageToken; all files listed and successful retention iterates complete inventory. Recommended fix: request/follow token, independently bound total work if needed without discarding files silently.

### M09 — 中断寫入的復原檔可能遮蔽先前有效復原備份

Severity: Medium. Category: Code Finding. Scope: Small.

Evidence: OfflineRestoreRepository.createRecoveryBackup (:134–146) writes directly `recovery-UUID.bubu` via writePrivateBackup; no catch/finally removes incomplete final-named output. latestRecoveryFile (:158–161) chooses greatest lastModified by extension, with no reader validation.

Why this matters: ENOSPC／writer 中途失敗會留下較新的不完整 .bubu，UI export/delete 的「最新」指向它；舊有效檔仍在，正式 DB 尚未被改動，故不是永久資料遺失或 Critical。

Reproduction: **Not reproduced** with actual short write. Missing attachment before writer opens output is **not** a valid repro of this issue.

Existing coverage: recovery created on success and invalid-enum recovery; no writer short-write/newest-file assertion. Proposed regression: prior valid recovery, fail mid-write, assert newest exposed recovery is still valid/prior. Recommended fix: .part + validation + atomic publish, delete failed partial, prune older valid files only after success.

### M10 — Archive preview 缺少數值與跨擁有者關聯的完整語意驗證

Severity: Medium. Category: Test Gap. Scope: Medium.

Evidence: BackupArchiveReader.validateData checks count, distinct public IDs, referenced existence and policy reminderType; not negative amounts/invalid LocalDate/time, service subtotal/total equality, service item owner's vehicle versus reminder vehicle, duplicate policy vehicle/type pairs or enum values other than policy type. OfflineRestoreRepository.insertData inserts entities directly; unknown fuelingMode/statisticsStatus silently falls back.

Why this matters: SHA 證明 bytes 一致，不能證明資料語意有效。數值非法或跨車關聯的自製／舊版本異常檔可能通過 preview；部分錯誤到正式 replacement transaction 才被察覺。這輪不宣稱正常 writer 會自然產生這些資料。

Reproduction: **Not reproduced** for numeric/cross-owner dataset; original and new rollback test sources assert that unknown enums pass reader then fail during conversion; the final run passes both middle and late failures with complete old-snapshot/attachment comparison.

Existing coverage: empty valid archive/path rejection, builder basic public relation checks. Proposed regression: structurally valid/checksummed but negative totals, mismatched subtotal, invalid time/date, cross-vehicle reminder/item/policy, duplicate policy key; preview must refuse before recovery/destruction. Recommended fix after deciding backward compatibility: domain-grade validation and explicit version/default handling.

### M11 — 尚未證明 ENOSPC／取消／process restart 的完整還原可恢復性

Severity: Medium. Category: Test Gap. Scope: Medium.

Evidence: OfflineRestoreRepository.restore catch performs replaceCurrentData(recovery) in current cancellable coroutine; replaceCurrentData catch/final cleanup use suspending store deletes without NonCancellable. ensureSpaceForRestore uses candidate.length + incoming totalByteCount, not current snapshot recovery size or new SQLite/WAL overhead.

Why this matters: 現有 Room rollback 足以防止 transaction 中的混合資料；不等於已證明每個檔案／協程／重新啟動情境的 automatic recovery。若 coroutine 已取消，補償也可能被取消；不因此直接推論原資料被破壞。

Reproduction: **Not reproduced** for cancellation/disk/process death. New middle/late exception tests cover ordinary exceptions with all snapshot fields, not these fault mechanisms.

Proposed regression: real short-write filesystem boundary, cancellation at staging/clear/insert/commit/cleanup, restart after commit, failed automatic recovery followed by later manual recovery. Assertions: complete old or complete new snapshot, all referenced bytes readable and valid recovery remains. Recommended fix only after repro: durable/recoverable file publication and minimal noncancellable compensation where necessary.

### M12 — 原始歷史升級測試的 current 路徑失效且歷史資料 fixture 不完整 [Closed]

Severity: Medium. Category: Test Gap. Scope: Small.

Evidence: BuBuDatabaseMigrationTest.migrationFromVehicleOnlyDatabasePreservesVehicleAndAddsFuelRecords registers through MIGRATION_13_14 only while @Database version=15. Individual tests stop at intermediate versions; v13→14 test is an empty DB/column check. Production AppContainer registers MIGRATION_14_15 correctly.

Why this matters: 測試不能用來支持 release 1.1.1 的歷史升級保證；這是測試缺陷，不是證明 App 缺少 migration。 Audit-added all-start-version test supplies populated UUID/FK/amount/policy fixtures with the actual complete chain.

Reproduction: Original baseline failed at device APK installation (0 tests), so no runtime migration failure is claimed. Static missing final registration is explicit. Audit remediation adds only MIGRATION_14_15 to the original builder and keeps every original assertion; the original baseline failure log remains. Final runtime verification passed: all 14 historical starts reach version15 with Room schema/FK/fixture assertions; the original nine migration tests also pass. M12 is closed as an audit test remediation, not counted among open Medium findings.

Existing coverage: individual versions listed in TEST_GAPS. Proposed regression: new allHistorical test plus keep production validator, preserve stable UUID/nondefault policy/nullable fields. Audit remediation: original registration corrected; populated per-start-version fixtures added. Original environment-failure evidence retained. Final runtime evidence closes this gap; legacy motorcycle/type classification remains an independently listed integration gap.

## Low Findings

### L01 — 疑似異常油耗的比較 window 使用歷史最高八值

Severity: Low. Category: Code Finding. Scope: Small.

Evidence: FuelEconomy.kt:198–201, detectFuelEconomyOutlier uses `.values.sorted().takeLast(MAX_OUTLIER_BASELINE_SEGMENTS)`. CONTEXT defines comparison relative to same vehicle's recent valid segments.

Why this matters: 近期穩定且正常的油耗可能被較早的高值反覆警告；不會自動排除，原始費用／里程不變，因此不升 High。

Reproduction: **Reproduced**: eight old 30 km/L segments, eight recent 10 km/L, next 10 km/L gives candidateMilli=10000 / baselineMedianMilli=30000.

Existing coverage: minimum baseline, low-side deviation, explicit decisions; no recency window fixture. Proposed regression: that chronological fixture and excluded baseline history. Recommended fix: select latest chronological valid endpoints first, sort that window for median.

### L02 — 現行正式產品與權威文件保留早期完整範圍的矛盾

Severity: Low. Category: Documentation Drift. Scope: Small/Medium documentation decision only.

Evidence: Spec §§5.2,5.6,8.6,9.5/9.6 describe restart/eligibility, OdometerCorrection/Replacement, 30-day trash, richer history/archived report capabilities. Current FuelRecord/Entity have no restart/deletedAt fields; no correction/replacement DAO/entity; repositories delete permanently; report selector filters active vehicles. Spec §3/Phase0 package tree and README three top-level pages also retain earlier structure descriptions while four current top-level tabs include reminders. TASKS top heading still Phase 0–2 while contents span releases/cloud enhancement.

Why this matters: 新的修復票容易把未實作範圍當成已完成，或因舊 Phase 限制誤判已實作 Google Drive。按本次指示独立列 Documentation Drift，不把全部早期規格缺項製造成 production bug，也不自行開發垃圾桶／儀表更換。

Reproduction: document↔code comparison; no runtime defect alleged. Existing coverage: actual current entity/package/navigation inventory.

Proposed regression: release capabilities checklist against actual routes/schema and intentionally postponed scope. Recommended fix: preserve Spec authority but explicitly mark release baseline and approved postponed items; align README/TASKS, clarify stable public identifiers created by legacy migrations and null interval means default. No schema change during audit.

## Data Integrity

### Failure / edge-case matrix

“Probe” means executed this audit against unchanged compiled classes. “Existing test” means source coverage found; consult main report for actual baseline execution. “Static” is an inspected guard, not an executed experiment. “Gap” means the indicated outcome remains unverified.

| # | Failure / input | Current handling / evidence | Status and next experiment |
| --- | --- | --- | --- |
| 1 | ZIP central directory truncated | ZipFile refuses archive | Probe: rejected |
| 2 | data SHA mismatch | Reader hashes declared bytes | Probe: rejected |
| 3 | manifest missing | single manifest required | Probe: rejected |
| 4 | declared data entry missing | Exact entry-set equality | Probe: rejected |
| 5 | undeclared extra file | Exact entry-set equality | Probe: rejected |
| 6 | ../ attachment traversal | Safe relative path check | Probe: rejected |
| 7 | absolute archive path | Leading slash rejected | Probe: rejected |
| 8 | backslash archive path | Backslash rejected | Probe: rejected |
| 9 | empty / dot path segment | Path segments rejected | Probe: rejected |
| 10 | manifest negative file size | Nonnegative size required | Probe: rejected |
| 11 | file size differs from declaration | Measured byte count checked | Probe: rejected |
| 12 | future / zero format version | Supported range is 1 only | Probe: rejected |
| 13 | record count mismatch | All declared counts compared | Probe: rejected |
| 14 | directory ZIP entry | Reader refuses directory entries | Probe: rejected |
| 15 | unknown data JSON key | Strict JSON decode | Probe: rejected |
| 16 | duplicate ZIP entry | Reader compares entry names count with distinct count | Static; raw ZIP fixture still needed |
| 17 | duplicate manifest file path | associateBy size vs list size | Static + builder duplicate coverage; direct reader fixture needed |
| 18 | attachment corrupt bytes | SHA/size checks apply to every declared entry | Static; mutate image bytes independently of data |
| 19 | attachment missing | Exact manifest + attachment path sets | Static; explicit image fixture needed |
| 20 | two attachment identities share one archive path | Set comparison does not enforce one-to-one cardinality | Gap: decide alias contract; no automatic corruption claim |
| 21 | attachment wrong / unsupported MIME | Restore store allowlist rejects; reader doesn't check MIME | Gap: reject at preview before replacement |
| 22 | over-20 MiB attachment | Reader bounded read / store limit | Static; exact boundary and +1-byte fixture |
| 23 | total attachment payload >512 MiB | Writer verifier accepts; restore reader rejects | Probe: H01 reproduced at 520 MiB |
| 24 | data.json >50 MiB / manifest >1 MiB | Restore bounded reads; writer verifier unbounded | Static asymmetry; boundary tests |
| 25 | failure immediately after DB clear | Clear + insertData transaction; existing invalid vehicle enum | Existing restore test; compare all old data rather than just name |
| 26 | failure while converting service items | Transaction rollback then recovery replacement | Audit-added test passed; all old data/image bytes equal |
| 27 | failure while converting expense after reminders | Same transaction and compensation | Audit-added test passed; all old data/image bytes equal |
| 28 | recovery archive write runs out of disk | Final .bubu name is written directly; latest selector trusts extension/mtime | M recovery reliability finding; deterministic short-write seam needed |
| 29 | recovery plus second replacement both fail | Exception is reported; recovery file remains if initially valid | Gap: test recoverability after transient environment recovers |
| 30 | process death after DB commit before old file delete | New references use staged random paths; old files may orphan | Static protective design; restart/attachment byte experiment |
| 31 | cancellation while staging / cleaning restore | Restore catches Throwable but cleanup/recovery use cancellable context | Gap: cancellation does not prove data loss; test original/new snapshot and orphan cleanup |
| 32 | current data larger than imported backup | Space estimate uses candidate + incoming uncompressed total, not current recovery size | Gap: small incoming archive + large current attachments + disk limit |
| 33 | upload timeout / partial media PATCH | Datasource deletes newly created metadata file on upload failure | Static; assert old files untouched at repository seam |
| 34 | upload succeeds, retention DELETE fails | New file remains; repository currently reports generic backup failure | Reliability gap: distinguish upload success from cleanup failure |
| 35 | sixth successful backup | Retention constant five; sorting independent of input order | Existing helper tests; repository and real-account acceptance pending |
| 36 | cloud list has nextPageToken / short page | HTTP code requests only files(...) and one page | Medium pagination finding; scripted multi-page HTTP response |
| 37 | cloud download interrupted | Repository deletes destination on exception | Static; partial-file fixture |
| 38 | cloud download consumes disk before validation | HTTP input.copyTo is unbounded | Gap: declared size and streamed byte ceiling; no credential exposure |
| 39 | Drive access expires | 401 traversed through cause chain, NeedsAuthorization persisted | Existing CloudBackupViewModel test; real expiry acceptance separate |
| 40 | unlink / revoke fails offline | UI maps error; connection clears only on successful revoke | Static; single-account real acceptance pending |
| 41 | account chosen differs from stored account | Non-Connect action ignores email but uses selected token | Medium account/state mismatch finding; VM boundary fixture |
| 42 | current vehicle archived / removed | Selection resolver + domain archive filters | Existing current selection/vehicle tests; verify notification summary invalidation separately |
| 43 | deleting vehicle with policy / history | DAO reference-count guard + RESTRICT FKs | Static safe rejection; policy-only vehicle fixture |
| 44 | equal odometer fuel anchor | Nonpositive distance cannot create economy | Existing FuelEconomy tests |
| 45 | backdated fuel / deleted anchor | Full sorted timeline is recomputed | Existing fuel/domain tests; full differential fixture needed |
| 46 | partial fills between full anchors | Volumes accumulated; start anchor volume omitted | Static algorithm handling; explicit intermediate-fill fixture remains a gap |
| 47 | excluded terminal fuel segment | Excludes only segment; terminal remains next anchor | Existing tests + DAO spending/odometer inspection; cost/km unaffected |
| 48 | eight obsolete high segments, eight recent normal, next normal | Baseline sorts values before taking last eight | Probe: Low outlier-recency defect reproduced |
| 49 | VOLUME/PRICE reversed input order | Last two explicit sources retained; Decimal HALF_UP | Probe: both orders yield 135 TWD for 4.270 L ×31.7 |
| 50 | volume 0.001 L + Long.MAX_VALUE total, either order | longValueExact throws synchronously before form validation | Probe: Medium overflow defect reproduced twice |
| 51 | zero divisor / zero cost | Price/volume helpers guard divisors; zero paid total legal | Existing calculation/domain tests; retain zero-vs-absent distinction |
| 52 | service subtotal/total Long overflow | Subtotal runCatching, total addExact; UI catches invalid save | Existing ServiceRecordInputValidation tests; no Double accumulation |
| 53 | report period ends/starts across month | Range query + pre-period odometer anchor; economy endpoint attribution | Existing Report tests; add leap-year/custom-period fixture |
| 54 | Jan 31 + month interval / leap February | LocalDate.plusMonths domain calculation | Static LocalDate.plusMonths handling; exact end-month/leap assertion remains a gap |
| 55 | real work order exists with 0 km opt-in | baselineRow wins configured fallback | Static; integration assertion after latest service deletion/backdate |
| 56 | user override app update / restore | existing.copy + INSERT IGNORE seed update; DTO stores policy fields | Static + new round-trip; old seeded policy migration fixture |
| 57 | old motorcycle item still references global type migrated CAR | 8→9 marks all legacy types CAR; current derivation filters vehicle type | Gap: realistic legacy motorcycle fixture before declaring reminder defect |
| 58 | policy orphan / foreign vehicle item in imported data | Reader checks referenced existence, not matching owner vehicle/type or numeric invariants | Medium validation gap; structurally valid archive containing cross-vehicle links |
| 59 | form background then system process death | Draft fields in memory-only StateFlow | Medium lifecycle finding; use background am kill, not force-stop as equivalent |
| 60 | restore preview process death | URI/preview memory only; no destructive action occurs without preview | Reliability/cleanup gap; safely ask user to reselect, check orphan download |
| 61 | rotate in SAF / Google authorization | pending operation held via remember | Medium lifecycle finding; ActivityResult recreation test |
| 62 | daily worker repeated same maintenance state | Summary posted every run without persisted trigger | Medium notification finding; recording notifier boundary |
| 63 | disable/archive/complete after summary posted | Live reconciler exempts maintenance summary ID | Medium notification finding; immediate cancel observable |
| 64 | repeated scheduler registration | Unique periodic work + KEEP/UPDATE policies | Static; WorkManager enqueue/cancel test |
| 65 | timezone change / reboot / Doze / app update | WorkManager persists periodic work; initial local 9am delay computed at registration | Gap: exact local-time expectation and date recomputation; no background sync required |
| 66 | external attachment unavailable / short copy | stage opens stream; size limit, .part cleanup, safe private path | Existing attachment repository tests + static; provider failure fixture |
| 67 | CSV =,+,-,@, comma, quote, CRLF, Chinese, emoji | All mapped cells pass central writer; BOM and RFC quoting | Probe: requested payloads protected and Unicode retained |
| 68 | rapidly repeated cloud authorization / restore click | Operation busy flags start after authorization/launch; normal UI callbacks main-thread serialized | Gap: suspend authorization twice before operation begins; demonstrate before declaring duplicate work |



### Stable Identity trace

All nine backed-up collections retain publicId: vehicles, fuel, serviceTypes, serviceRecords, serviceItems, expenses, reminders, maintenance policies and attachments. Domain/entity mappers copy identifiers; creation generates UUID, updates use existing.copy. BackupDataBuilder remaps local FK IDs to public IDs. Restore inserts with same publicId and maps newly assigned local IDs back into relationships. Physical attachment paths intentionally change, UUID/displayName/MIME/bytes should not.

Cloud transport sends archive bytes and uses the same reader/restore workflow, so no second identity conversion exists. Legacy v4 migration preserves original record/item identifiers; newly created labor/adjustment/reminder identifiers use deterministic prefixes, not random replacements of existing identities. UUID syntax/cross-table collision validation is not enforced; do not confuse stable public identity with local row ID.

Original source tests only verify a few relation public references. New semantic round-trip compares **every BackupData field** after normalization by publicId plus ZIP image bytes, including created/updated times, nulls, record ordering values, policies/types, archival and 0 km opt-in. The raw DB fixture deliberately exercises serialized completion references, including a self-referenced originating work order; it does not prove chronological completion-chain regeneration or derived baseline correctness. Those remain repository-integration gaps. App preferences/token fields are excluded by the DTO, and restore never writes preferences DataStore; further target preference preservation integration is still worthwhile.

### Integer / measurement model

Entities/DTO use Long for ml, milli-TWD, TWD, odometer, quantityMilli and totals. Fuel Decimal multiplication/division uses HALF_UP; service subtotal catches exact-conversion overflow and service total uses Math.addExact. Report money helpers use integers/BigDecimal; SQL SUM may reject extreme aggregate overflow rather than silently become correct—realistic thousands of ordinary receipts do not approach Long bounds. Double/Float in charts/presentation do not store financial amounts. Zero/divisor and weighted economy conditions avoid NaN/infinity in inspected paths; M01 is the genuine missing error boundary.

## Database / Migration

Schema exports **1.json …15.json** are present. Production registration includes every contiguous Migration edge 1→2→…→15 and no destructive fallback. All intermediate jumps use Room's chained path; testing each *combinatorial hop sequence* is unnecessary because there are no competing direct edges. Populated test **for each historical starting schema** is valuable because earlier rows/null/default states differ.

- 1→2 fuel table; 2→3 service; 3→4 type/expense; 4→5 service normalization/attachment/reminder/deletion queue.
- 5→6 reminder source/manual/nullable service item; 6→7 notification status; 7→8 statutory/inspection/expense completion + status trigger backfill.
- 8→9 service types CAR classification and per-vehicle-type unique name; 9→10 fuel mode FULL_SERVICE.
- 10→11 maintenance preferences + unique vehicle/type/FKs; 11→12 nullable base odometer; 12→13 UNREVIEWED fuel statistics.
- 13→14 nullable months/type defaults and specific inspect/estimate built-in backfill; 14→15 opt-in false.
- v4→5 stages child items without FK before replacing parent, preserves existing IDs/paid total and inserts labor/legacy adjustment as needed. Existing migration test asserts normalization, stable item UUID, total, completion and foreign_key_check.
- Canonical schema createSql/Room entity defaults differ from ALTER TABLE migration SQL defaults where no @ColumnInfo default is defined; nullable/default behavior should be tested through Room validation, not inferred from constructor defaults.
- FKs/indices protect vehicle history, item/attachment parent cascade, reminder completion SET NULL, UUID uniqueness and policy pairs. ServiceItem.serviceTypeId and expense/reminder completion links are application-managed rather than full schema FKs; repos enforce normal transactions, and import semantic gap is M10.
- New allHistorical test invokes runMigrationsAndValidate at version15, foreign_key_check, preserved UUID/timestamps/nondefault money/policies for all 14 sources. Original registration is corrected; M12 is closed by actual runtime evidence.

No production migration defect has been demonstrated. Runtime schema verification passed for all 14 starting-version fixtures; JSON existence/build alone was not used as proof. The fixture scope is CAR data with custom type/policy rows in newer versions, not every possible legacy vehicle/type combination.

## Backup / Restore

Archive guard probes confirm strict manifest version, size/SHA, exact contents, count, safe path and strict JSON validation. Duplicate entry/manifest path guards exist; targeted raw duplicate/image aliases are still gaps. Format remains1 with additive DTO defaults; old format1 backups get documented defaults for mode/statistics/months/type/0 km baseline. Silent enum fallback and unversioned semantic evolution should be tested explicitly (M10), not redesigned.

Restore phase analysis:

| Point | Current protection | What is / is not proven |
| --- | --- | --- |
| candidate copy + preview (~10%) | bounded private copy; reader validates before recovery/DB changes | Bad ZIP probes reject; no destructive side effect |
| recovery creation | snapshot under Room transaction; archive before replacement | Existing success evidence; partial publication risk M09 |
| image staging (~30%) | new random managed paths; old files retained | Ordinary failures discard staged values; cancellation/orphans need injection |
| clear + any insert (~50–90%) | one outer Room withTransaction | Complete DB rollback for exceptions/cancel per Room contract; middle/late audit tests check semantic old snapshot and recovery |
| commit then old cleanup | new image paths are committed before old paths removed | Cannot leave half-old/half-new DB from ordinary transaction failure; cleanup crash can leave orphan old files |
| automatic recovery | same validated archive/replace flow | Existing invalid-enum test + new richer failures; ENOSPC/cancel/both-fail still unverified |

[Room transaction documentation](https://developer.android.com/reference/androidx/room/RoomDatabaseKt) states success is withheld for exceptions/cancellation; this supports the inspected database boundary. It does not make filesystem cleanup transactional.

Full snapshot load is proportional to all attachments; 3–5 years of images may hit heap/reader caps much sooner than record counts. H01 and targeted follow-up measurements matter more than repository aesthetic changes.

## Attachments

PrivateAttachmentStore only manages canonical paths under files/attachments/service. Source content URI is copied to private .part then fsync/rename; it never deletes original provider file. File size/MIME allowlist guards JPEG/PNG/WebP and 20 MiB. Service transaction queues durable deletions; cleanup rechecks reference count, startup waits a grace period before collecting orphans. Existing tests cover private path validation, cascade cleanup, fresh-vs-stale orphan and rollback.

Restore preserves image identity but chooses new physical filename; old references are removed only after replacement commits. New byte comparison verifies this at the backup seam. Unsupported MIME, insufficient storage, cancellation and alias declarations remain separate gaps. Current service image scope is narrower than old general PDF/cover Spec text (L02).

## Google Drive

Scopes exactly match ADR0004: drive.appdata + narrow drive.file Android write flow, no full-Drive scope. Actual writes target appDataFolder; list query also app property BuBu; metadata mapper filters .bubu and required summary. Token is transient, DataStore keeps email/time/needs-auth flag only; unlink revokes then clears local state, keeps remote backups.

Upload creates metadata then media PATCH; failure attempts to delete **newly created file only**. Repository calls retention only after successful upload/mapping/timestamp update. Therefore **failed upload does not call old-file retention** by inspection. Helper tests are insufficient proof of all repository failure interleavings; TEST_GAPS identifies the needed orchestration seam.

Download validates private file then existing preview/recovery/full replace. Failure deletes destination; successful temp lifetime lives in NavHost and M05 covers rotation leakage. HTTP download streams without size cap before validation; add bounded transport test (no credential exposure demonstrated). M07 account state, M08 paging and retention cleanup status are distinct from CB-12 pending real-account acceptance.

## Fuel Economy

Fuel History/Dashboard share domain full-tank calculations/recent-three weighted summary. Reports use endpoint-attributed period segments with another source-row accumulator. Review uses the domain candidates. Normal partial fills, anchor volume exclusion, explicit statistics exclusion and next anchor preservation agree. DAO costs and odometer queries do not filter fuelEconomyStatisticsStatus, so exclusion cannot remove raw cost/distance by inspected dataflow.

The requested cycle restart/eligibility controls are not current model fields; recorded under drift, not fabricated as implemented features. L01 changes warning baseline only. Recent-three/dashboard and selected-period/report summaries intentionally have different windows; identical numbers are required only for identical metric inputs/window.

## Maintenance Reminder

Car oil 10000 km/6 months and motorcycle oil1000/6 exact. Latest actual Service Work Order baseline wins configured 0 km; opt-in derives mileage only without invented service date, then no baseline is UNKNOWN. Saved policies use existing.copy preserving publicId/createdAt; built-in seeds inserted IGNORE then existing metadata refresh does not overwrite saved policy.

Archived vehicles/types and disabled policies are filtered from derived reminders. Historical work-order updates/deletes rebuild completion chains/odometer. Policy FK prevents orphan vehicle/type by normal DB insertion. New round-trip retains policy reminder type/months/disable/base values. Full legacy motorcycle/global-type and nondefault-policy precedence integration remains a high-value gap.

**Withdrawn suspicion:** nullable interval fallback is consistent with strings.xml instruction “留空時使用內建週期”; blank months reusing defaults is not a confirmed user-override defect.

## Reports

ReportDao aggregates spending per vehicle/date in SQL and excludes no economy decision from costs. Repository/domain compute range distance and weighted segment metrics; ViewModel/Composable filter/format graph values rather than introduce another authoritative saved metric. Monetary work-order aggregate uses stored total once, not joined item re-summing.

The two fuel-cycle loops are debt, not demonstrated divergence. Archived report selector versus old Spec is documentation drift; deciding historical access is product scope, not this audit's redesign. Range anchors, endpoint dates, same-day ordering and multi-vehicle isolation have domain tests, with differential source fixture remaining in TEST_GAPS.

## WorkManager / Notifications

Unique periodic scheduling prevents registering a new independent daily/monthly work item on each App startup. Workers re-read settings and ordinary vehicle/reminder validity. WorkManager persistence supports reboot/app update; exact wall-clock9am under Doze/timezone needs tests, not a precision promise inferred from initial delay.

Ordinary notifications track status/trigger and use immutable PendingIntent; backup reminder tracks successful local backup preference separately and is not backed up. New maintenance summary bypasses ordinary de-dup/reconciliation (M02); M06 notification routing and M03 date capture are concrete review concerns. No background synchronization suggested.

## Lifecycle

collectAsStateWithLifecycle is used for route UI state. ViewModel/structured app scope collectors are not database reads on every recomposition. Channels buffer single-use actions within process; external authorization/SAF completion context still requires retained state (M05). No duplicate collector defect was demonstrated.

Rotation, background retention and system process death are explicitly distinct. Forms survive normal rotation through retained VM but have no recoverable draft on system death (M04). Restore preview disappears safely without initiating a replacement after recreation; temp cleanup/retained source ownership remains to verify. Navigation restores local IDs; IDs can change after restore, so stale back-stack entries should be exercised in a scoped lifecycle test rather than redesign graph.

## Security / Privacy

Application-owned source manifest exports launcher MainActivity intentionally; FileProvider is exported=false and path is cache/cloud-backup only. The merged debug manifest also includes exported library components: permission-protected JobService/profile/test services and debug PreviewActivity/ComponentActivity. Debug entries are not evidence of an exposed release data entry point; no release manifest/artifact scan is claimed. GrantUriPermissions is narrow; attachment paths are canonical-checked and restored files are random-named. INTERNET and POST_NOTIFICATIONS are expected current permissions, no storage-manager/auth/backend/tracking SDK was found in current production configuration.

BuildConfig contains public OAuth web client identifier, not client secret. Logs inspected show scope names and operational errors, no access token/key/password logging. Actual local config/signing files/keystore patterns ignored; signing/OAuth release check hooks preReleaseBuild and fails when required inputs absent. This does not claim a cryptographic scan of historical Git object contents or real server authorization acceptance.

Backup is unencrypted by Spec and user must guard exported file; no unsolicited encrypted-format redesign. Minify disabled is deliberate and not a security finding by itself. Actual release artifact signing was not changed or re-published during audit.

## Documentation Drift

L02 records conflicting old/broad Spec scope and current release inventory independently. Spec remains highest authority; no feature reimplementation was performed to reconcile text. ADR0004 accurately matches narrow scopes/transport; ADR0005 matches service-derived maintenance. CONTEXT's Stable Identity and Data Backup distinction are used throughout; maintenance policy is domain data despite “preference” class name, while actual app preferences are excluded.

## Manual Acceptance Pending

TASKS Google Drive enhancement / CB-12 still requires **one Google account**: attachment backup/download/preview/restore byte usability, successful sixth→five retention, unlink/reconnect with remote backups preserved. Existing code upload/list/restore/delete is not marked absent just because this acceptance remains open.

No Google account messages/actions were sent, no live cloud backup deleted, no release uploaded. Emulator tests use local test fixtures, not proof of these real-account cases. Earlier Samsung Android16 test-host hierarchy limitations remain historical context; API35 current-run result is reported separately.

## Dual-axis review

Required code-review skill normally asks for a nonempty diff/fixed point; user's full-repository request overrides diff-only scope, so snapshot HEAD is the fixed baseline and no approval interview is required. Two independent read-only subagents reviewed Standards and Spec while root ran serial verification/data audit. No subagent ran Gradle or edited production.

- **Standards axis:** lifecycle/pending-result/notification entry and maintenance summary concerns; no demonstrated Critical/High in assigned scope. Concrete standards versus heuristic smells are distinguished; no length-based violation/refactor recommendation.
- **Spec axis:** confirmed arithmetic overflow and outlier recency mechanisms; correct baseline/default/exclusion/CSV behavior, policy null suspicion withdrawn. Existing risk gaps/drift/debt kept distinct.
- These axes are reported independently; overall finding register is root's separate evidence synthesis, including data audit H01.

diagnosing-bugs followed deterministic compiled public-seam probes for arithmetic/outlier/size observations; proposed fixes were not applied, so green-after-fix phase is intentionally deferred. TDD discipline governs public seam assertions and audit additions, without speculative implementation. Domain scenarios stress-tested baseline precedence/stable identity/backup-vs-preference boundaries; no new domain term or irreversible ADR decision was introduced.

## Changed files / review scope (audit stage)

Requested artifacts: docs/audit/RELEASE_1_1_1_AUDIT.md, TEST_GAPS.md, TECH_DEBT.md. Audit additions: two Android test files described above and the original migration test registration correction; TASKS gets this audit's status only. Probe sources/logs are under ignored outputs/release-1.1.1-audit. No production, schema, format, dependency, UI/navigation, signing or package changes. Final git diff/scope and test/lint results are checked before completion. The working changes are seven files: the three audit artifacts, `app/src/androidTest/java/com/kumo/bubu/data/repository/BackupRoundTripAuditTest.kt`, `app/src/androidTest/java/com/kumo/bubu/core/database/AllHistoricalMigrationsAuditTest.kt`, `app/src/androidTest/java/com/kumo/bubu/core/database/BuBuDatabaseMigrationTest.kt` (one registration), and `TASKS.md`.

## Recommended first implementation ticket

**Local/recovery backup success must imply compatibility with existing Restore Reader.** Blocked by: none. Acceptance: common current limits; preflight input sizes before all-image allocation; exact boundary and +1 failures; same reader validates all successful outputs; incomplete recovery never replaces newest valid archive; ordinary semantic round-trip remains identical. Keep DB15/format1, no full repository refactor. Follow with maintenance notification lifecycle and retained operation/draft tickets.

## Follow-up implementation — Ticket 01 (2026-09-13)

User approved the 16 local hardening tickets and then assigned execution. Ticket 01 is **done** in the working tree: H01's writer/reader limit asymmetry is addressed. Baseline findings and failed observations above describe snapshot e123795 and are retained; current named open counts after this remediation are **Critical 0 / High 0 / Medium 11 / Low 2**. M09's crash-safe atomic recovery publication, M11 storage/cancellation/restart faults, TD-04 memory measurement and CB-12 real-account acceptance remain open; they are not claimed as solved by size guards.

- Writer, reader, verifier, private attachment store and restore candidate/staging limits use the same existing caps. Actual ZIP size is also bounded by the existing 512 MiB restore-candidate limit.
- Local/recovery backup checks data bytes and every referenced attachment's metadata before loading all attachments. Bounded reads and actual-byte checks cover file growth. Serialized manifest and emitted ZIP sizes are checked independently.
- Every successful private backup passes the formal restore reader before destination copy or recovery success. Ordinary writer/reader failure removes the current invalid output. This does not claim atomic publication or recovery after abrupt process termination.
- A typed domain size failure produces resource-based size-limit feedback for local backup and blocked restore. No schema, format, navigation, signing, permission or dependency change was made.

| Check | Actual result | Evidence |
| --- | --- | --- |
| First regression before fix | **1 test failed**, writer accepted attachment size 20 MiB + 1; correct behavior is rejection before opening destination | outputs/release-1.1.1-audit/ticket-01-red.log |
| First regression after fix | Passed | ticket-01-green-first.log |
| Archive boundary and UI-state slice | Passed; includes the original 26 × 20 MiB writer rejection | ticket-01-boundaries.log |
| `:app:assembleDebug` | Passed, 1m26s | ticket-01-assemble.log |
| `:app:testDebugUnitTest` | **158 tests / 0 failures / 0 errors / 0 skipped**, 58s | ticket-01-unit.log; current JVM JUnit XML |
| `:app:connectedDebugAndroidTest` | **77 tests / 0 failures / 0 errors / 0 skipped**, API35, 4m32s | ticket-01-instrumentation.log; current connected JUnit XML |
| `:app:lintDebug` | **0 errors / 137 warnings**, 3m13s; warning categories/counts unchanged from audit baseline | ticket-01-lint.log; current lint XML |

Nine new public archive tests cover 20 MiB attachment, 50 MiB data, 1 MiB manifest, 512 MiB total content and actual ZIP boundaries, including +1 rejection, untouched destination on preflight failure and verifier/reader agreement. The actual 512 MiB ZIP reader test uses a sparse self-extracting prefix and small payload rather than allocating 512 MiB. These are JVM boundary results, not a phone-heap measurement. The original writer/hash test now uses a complete, referenced BackupData fixture with unchanged hash/entry assertions, because its old minimal JSON was not actually reader-valid.

The new Room test creates 26 separate work orders with one 20 MiB padded valid PNG each, plus the original snapshot. Both public local backup and recovery creation before restore reject the oversized dataset; no invalid output is exposed. Removing only the extra fixture rows and re-exporting confirms the original complete snapshot and attachment bytes remain unchanged. Existing semantic round-trip and both middle/late ordinary exception recovery tests pass. Two new Compose tests verify the actual size-limit messages. Test paths/files are owned and cleaned by their fixtures; the audit-started AVD was gracefully stopped.

Changed production files: BackupArchiveLimits, BackupArchiveReader, BackupArchiveWriter, PrivateAttachmentStore, OfflineBackupRepository, OfflineRestoreRepository, domain BackupRepository, BackupViewModel, RestoreViewModel, SettingsScreen, RestorePreviewDialog and strings.xml. Changed/new test files: BackupArchiveSizeContractTest, BackupArchiveTestData, BackupArchiveWriterTest, BackupViewModelTest, RestoreViewModelTest, BackupRoundTripAuditTest and SettingsScreenTest. Ticket 01, TASKS and these audit follow-up records are updated. Actual technology versions remain those listed above (AGP9.3.1, Gradle9.6.1, Kotlin2.4.10, Room2.8.4, JBR25.0.2; DB15 / format1).

At ticket 01 completion, the next recommended ticket was **02 — 加油表單安全處理計算溢位**. The following section records its later implementation.

## Follow-up implementation — Ticket 02 (2026-09-28)

Ticket 02 production and regression changes are implemented in the working tree. The form now converts an unrepresentable derived price, volume or total into a field-level `CALCULATION_OVERFLOW` state, preserves both explicit inputs, blocks persistence through normal validation and clears the error after valid correction. A failed derived price also clears stale automatic/last-record source metadata. User-visible text remains in Android resources; schema, backup format, navigation, permissions and dependency versions are unchanged.

Public-seam ViewModel tests cover both 0.001 L / `Long.MAX_VALUE` input orders, all three derivation directions, no invalid persistence and recovery after correction. A state test covers stale price-source removal and a Compose test covers immediate error visibility. Each overflow direction and source regression completed red → green; `git diff --check` passes.

Ticket 02 is **done**. Final serial gates pass: `:app:assembleDebug` in 3m19s; complete `:app:testDebugUnitTest` with **163 tests / 0 failures / 0 errors / 0 skipped** in 28s; API 35 `:app:connectedDebugAndroidTest` with **78 / 0 / 0 / 0** in 5m23s; and `:app:lintDebug` with **0 errors / 140 warnings** in 4m18s. No lint issue points to ticket 02 Fuel files, and `git diff --check` passes. Initial gates were blocked by missing Kotlin 2.4.10 compiler artifacts and interrupted remote transfers; verification recovered using complete official Maven Central artifacts whose SHA-1 values matched the official checksums, with SHA-256 values also recorded. The one-time repository and artifacts remain under ignored audit outputs and do not change project dependencies. M01/P2 is closed; current named open counts are **Critical 0 / High 0 / Medium 10 / Low 2**. Tickets 03–16 remain ready-for-agent; next recommended ticket is **03 — 保養摘要通知生命週期**.

## Follow-up implementation — Ticket 03 (2026-10-04)

2026-10-04 device incident addendum: the primary user's `com.kumo.bubu` package and original data directories are now absent. Historical failed-install/cleanup logs and the locally inspected AGP 9.3.1 / UTP 32.3.1 implementation strongly support an inference that test cleanup uninstalled the pre-existing release app without keeping data. No historical main-app uninstall-success response was retained, so this remains an inference. Zero executed phone tests and a signature conflict did not establish device safety. Three phone backup archives were preserved and successfully read by the formal backup reader; details are in ignored `outputs/phone-recovery-2026-10-04/DIAGNOSIS.md`. Future connected validation must isolate real devices and use an explicitly selected disposable emulator.

Ticket 03 is **done** in the working tree. The daily worker now shares a maintenance summary coordinator with an AppContainer observer of derived service reminders and the global notification setting. Persistent DataStore signatures distinguish the last alert from the last displayed summary: unchanged worker runs and reconstructed processes do not repost, while a new eligible state can alert again. Service work orders, archived vehicles and disabled rules promptly cancel or silently refresh an existing summary. The coordinator rereads settings, permission and eligible reminders immediately before posting. Ordinary reminder and local backup notification identifiers and flows are unchanged; notification entry routing remains ticket 08.

Regression tests at the production notification boundary covered repeated runs, reconstruction, state transitions and disabling settings between the first read and posting. A reconstruction duplicate-post assertion was red before the persistent display signature fix and green afterward. API 35 integration tests use Room, DataStore and Android's NotificationManager to verify actual work-order create/edit/delete, vehicle archive, rule disable, summary counts and cancellation. Final serial gates pass: `:app:assembleDebug`; complete `:app:testDebugUnitTest` with **168 / 0 failures / 0 errors / 0 skipped**; API 35 `:app:connectedDebugAndroidTest` with **80 / 0 / 0 / 0**; and `:app:lintDebug` with **0 errors / 140 warnings** and no issue pointing to ticket 03 notification files. `git diff --check` passes; logs are under ignored `outputs/release-1.1.1-audit/ticket-03-*-final.log`. The first final lint attempt did not start because automatic approval review hit a usage limit; it later ran and passed. A separately connected physical device ran **0 tests** because an installed app with a different signature caused `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; this is not reported as device validation. M02/P2 is closed; current named open counts are **Critical 0 / High 0 / Medium 9 / Low 2**. Tickets 04–16 remain ready-for-agent.

# BuBu v1.1.1 — Test Gaps

Audit snapshot: `e123795`, 2026-09-13. Spec is authoritative. This file records high-value remaining gaps, not covered cases or class-by-class coverage. No pending real-account acceptance is classified as a code defect.

## Public seams and audit additions

The user's requested seams are Room historical schema → current schema, and Database A → BackupRepository → archive validation → RestoreRepository → Database B → re-exported semantic comparison. These explicitly authorized seams govern the new audit tests; no production seam or schema is introduced.

- Original `OfflineRestoreRepositoryTest`: vehicle replacement, invalid vehicle enum after clear, disk-backed timeout. It does **not** compare all UUIDs/relations/attachments/policies.
- Original `BuBuDatabaseMigrationTest`: v1→current builder omits 14→15; individual 4→5, 5→6, 6→7, 7→8, 8→9, 9→10, 12→13, 13→14 checks. The audit corrects that one missing registration without weakening assertions. Original source had no per-start-version 1…14→15 fixtures; 13→14 fixture is empty and tests column presence rather than saved policy preservation.
- Audit additions: `BackupRoundTripAuditTest` (semantic replacement, middle and late conversion failures with complete recovery comparison), `AllHistoricalMigrationsAuditTest` (14 starting versions, UUID/audit times/FK/money/policy preservation). Final actual execution: **4/4 audit methods passed** in the complete **74/74 instrumentation suite**, including all 14 historical starting-version fixtures. M12 and the broad serialized-conservation gap are closed; the remaining table distinguishes derived state, storage faults and process restart. Compilation alone was not used as evidence.
- Audit-only `outputs/release-1.1.1-audit/AuditProbe.java`: executes unchanged compiled application functions. 27 checks recorded in `probes.log`; includes archive negatives, calculation order, overflow, outlier recency and writer/reader size asymmetry. A successful harness exit means the documented observations were obtained, including intentionally reproduced defects.

## Ticket 01 follow-up (2026-09-13)

The baseline H01 success-implies-reader-acceptance gap is closed by ticket 01: nine public archive boundary regressions, two ViewModel size-error results, one real Room oversized-snapshot rejection/local-and-recovery preservation test and two Compose size-message tests. Complete execution passes **158 JVM / 77 instrumentation tests**, with final lint **0 errors / 137 warnings**. Baseline 147/74 evidence above remains historical. Disk faults, cancellation, abrupt process termination and within-limit heap measurements are not covered by these size tests and remain separately tracked below.

## Ticket 02 follow-up (2026-09-28)

The arithmetic-overflow gap is closed by ticket 02: ViewModel event/state regressions cover both 0.001 L / `Long.MAX_VALUE` input orders, all three derived-field overflow directions, blocked persistence, correction and stale price-source removal, plus a Compose error-visibility regression. Targeted red → green and complete execution pass: **163 JVM / 78 API 35 instrumentation tests**, assembleDebug and lint with **0 errors / 140 warnings**. Evidence is recorded under `outputs/release-1.1.1-audit/ticket-02-*.log`.

## Highest-value remaining gaps

| Priority | Gap | Required observable assertion | Existing evidence / limitation |
| --- | --- | --- | --- |
| P1 | Real storage failure across recovery creation/staging/commit/cleanup | Old or fully new snapshot; newest recovery remains valid; no missing referenced image | Existing invalid-enum test is not an ENOSPC/process-death injection |
| P1 | Process termination around restore commit | Kill before clear, mid-transaction, just after commit, while old files are removed; reopen and compare UUIDs + image bytes | Room transaction is protective; no durable-operation/restart experiment |
| P1 | Cloud repository upload/retention orchestration | Failed upload deletes no pre-existing files; successful sixth upload retains five; delete failure preserves new file and reports cleanup failure accurately | Retention helper tests are not `OfflineCloudBackupRepository` fault injection; datasource test only checks metadata parent array |
| P1 | Nondefault policy + work-order history round-trip | Preserved fields and derived states, actual work order > 0 km > unknown for two vehicles | New round-trip preserves fields; precedence still needs repository integration fixtures |
| P2 | Imported numeric/cross-owner validation | Preview refuses negative/mismatched totals, invalid time/date and cross-vehicle references before replacement | M10; checksums alone cannot validate semantics |
| P2 | Midnight/resume/timezone recalculation | Date status updates without a data write and matches freshly executed worker | ViewModels pass a date once; no resume/midnight clock fixture |
| P2 | Notification deduplication and invalidation | Same unchanged maintenance state posts once; archive/disable/new service cancels obsolete summary | Existing rules tests largely exercise ordinary reminder trigger helpers |
| P2 | Notification multi-vehicle entry | Second vehicle's reminder is selected/highlighted; scroll accounts for maintenance sections | Isolated screen tests do not exercise notification→Activity→NavHost |
| P2 | Form/process state and pending activity results | Fuel/Service/Vehicle drafts and SAF/auth operation recover once after system recreation | Plain ViewModel state handles rotation, not process death; NavHost pending values use remember |
| P2 | Fuel/report differential fixture | Shared two-vehicle fixture with partial fills, same-day order, exclusion, backdating gives equal applicable metrics | Independent domain loops agree on ordinary cases; keep period and recent-window semantics distinct |
| P2 | Drive transport pagination/partial download | Follow nextPageToken even for short pages; bounded download deletes incomplete file | Current HTTP transport is hardcoded and no transport fault seam is injected |

Full 68-case handling/evidence matrix is in [the main audit](RELEASE_1_1_1_AUDIT.md#failure--edge-case-matrix); covered cases are not listed as remaining gaps here.

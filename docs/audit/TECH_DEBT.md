# BuBu v1.1.1 — Technical Debt

Snapshot `e123795`, 2026-09-13. This file contains non-bug maintenance candidates only. Code defects, gaps and real-account acceptance are in the other audit files. No refactor or scaffold deletion was performed.

## Architecture hotspot decisions

| File | Actual responsibilities | Testing/change coupling | Judgment and future scope |
| --- | --- | --- | --- |
| BuBuDatabase.kt | Entity registration and historical SQL migrations, including v4 monetary normalization | Changing legacy migrations requires populated historical fixtures | Reasonable migration orchestration. Separate old migrations only when a migration ticket benefits; never rewrite already shipped SQL casually |
| AppContainer.kt | Dependency wiring, localized seeds, startup reconciliation, notification scheduling | Runtime boot and repository construction share setup; startup collectors influence tests | Reasonable composition root; move notification reconciliation behind a seam when notification lifecycle ticket lands |
| OfflineRestoreRepository.kt | Candidate copy, archive validation, recovery archive management, staging, replacement and compensation | Filesystem faults/cancellation cannot be injected precisely with current concrete stores | Highest-value orchestration seam candidate; limits/recovery fixes first, extract only what those tests need |
| OfflineServiceRepository.kt | Work-order transactions, item/type operations, derived policies, completion chains, attachment deletion queue | Policy/default rules reused across reminder derivation and stored reminder sync | Large but cohesive around service transactions. Preserve all-or-nothing boundary; narrow policy resolver extraction if a behavior ticket requires it |
| BuBuNavHost.kt | Graph assembly plus SAF and authorization ActivityResult callbacks and temp-file cleanup | Pending operations live alongside graph composition; rotation failure is independently recorded | Possible Divergent Change. Retained operation state is concrete first ticket; graph-wide rewrite not justified |
| RemindersScreen.kt | Vehicle selector, maintenance and ordinary reminder sections, dialogs and highlighting | Mixed list sections make ordinal scrolling fragile | UI orchestration reasonable. Stable lazy-list keys/target mapping belong to notification entry fix; size alone is not evidence |
| FuelFormScreen.kt | Shared form widgets, calculation source labels, input warnings | Calculation is in state/domain, not Composable | Reasonable UI composition; no independent rewrite candidate |
| ServiceFormViewModel.kt | Draft items/editor orchestration, attachment staging/cleanup and save state | Process draft and staged-image ownership need joint lifecycle tests | Persist small fields and recoverable attachment references in draft ticket; avoid generic use-case layer |
| SettingsScreen.kt | Groups of settings and operation status/callbacks | Isolated composable can be tested using callbacks | Reasonable orchestration; do not extract solely to satisfy a line threshold |
| ReportsViewModel.kt | Selected vehicle/period/filter/layout preferences and report state | Domain metrics remain repository/domain-owned, UI performs presentation filtering | Reasonable orchestration. Midnight/resume and archived-report contract are separate audit observations |

## TD-01 — Duplicate fuel-cycle accumulation

Severity: Low. Category: Technical Debt. Evidence: `domain/model/FuelEconomy.kt` fuel-record loop and `domain/model/Report.kt` report source-row loop independently accumulate volumes and full-tank anchors.

Normal exclusion, weighted average and endpoint attribution agree by inspection and existing domain tests. History/dashboard recent-three summaries and period-based reports deliberately have different windows; equal numbers are not required when their windows differ.

Recommended regression before extraction: same vehicle records normalized into both public domain seams; compare all applicable endpoint segments, including partial fills, exclusions, same-day order and two-vehicle isolation. Then share the smallest cycle accumulator if it avoids future divergence. Scope: Small/Medium; no current proven statistics divergence.

## TD-02 — Restore filesystem fault seam

Severity: Medium maintainability candidate, not a production bug by itself. Evidence: concrete `PrivateAttachmentStore`, `OfflineBackupRepository` and direct FileOutputStream usages in `OfflineRestoreRepository`.

New middle/late enum failures test transactional compensation, but do not model short writes, ENOSPC, cleanup failure or process restart. Prefer a narrow filesystem/recovery writer boundary introduced alongside the first relevant regression. No speculative interface should be built during this audit. Scope: Medium.

## TD-03 — Cloud error contract crosses layers

Severity: Low. Evidence: `feature/settings/CloudBackupViewModel.kt` imports `data.repository.CloudBackupException` and `data.cloud.drive.GoogleDriveHttpException` and traverses causes.

Mapping works for inspected 401/network/format failures; coupling means transport exception changes affect feature code. Move a focused error result into the existing domain contract only when pagination/transport reliability work justifies it. Scope: Small. No Hilt, backend or multi-module change needed.

## TD-04 — Snapshot uses memory proportional to all attachments

Severity: Medium maintainability/performance candidate paired with H01's concrete limit defect. `OfflineBackupRepository.buildContents` materializes every image ByteArray, then writer/verifier serializes/reads images. Thousands of ordinary receipts can exceed practical heap before archive size limit. An OOM was **not reproduced**.

First enforce a consistent public success/restore contract; then measure heap for anonymized many-image fixtures before a streaming refactor. Preserve manifest/hash/.bubu format. Scope: Medium; changing format or increasing limit without measurements is not recommended.

Follow-up (2026-09-13): ticket 01 addresses H01 with common limits, metadata preflight, bounded reads and formal reader validation; the original 520 MiB oversized fixture is rejected before all-image loading. It does not replace the all-image ByteArray representation or measure within-limit phone heap. TD-04 therefore remains a measurement-led performance candidate, not a newly reproduced OOM.

## Retained scaffolding

`Spec.md` §3 and Phase 0 explicitly show the original directory tree. Current .gitkeep-only packages:

- `core/backup`, `core/csv`, `core/currency`, `core/date`, `core/units`, `core/util`, `core/validation`
- `domain/usecase`, `feature/dataexport`
- `data/local/relation`, `data/exporter`

Classification: **A — historical Spec requested the directories; C — current implementations reside elsewhere, leaving old scaffold.** There is no executable evidence for B (certain future use). These files are harmless, Low debt. Preserve until the documented package tree is intentionally aligned; do not delete them during an audit or prebuild speculative classes.

## Rejected refactor recommendations

No measured architecture failure supports multi-module conversion, Hilt, Firebase, authentication, backend, package reorganization, navigation redesign, global formatting, dependency upgrades or enabling R8 solely for release. The single app/feature-first arrangement is consistent with the current product. Large orchestration files are reviewed above on behavior and coupling, not length.

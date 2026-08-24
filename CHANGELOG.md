# Changelog

All notable changes to BuBu will be documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [1.1.0] - 2026-08-24

### Added

- Google Drive cloud backup and restore for the existing complete `.bubu` format, scoped to each user's hidden Drive app-data folder.
- A refreshable cloud-backup list with download preview and a confirmed permanent-delete action for a selected backup.

### Security

- Google authorization uses short-lived credentials and narrow Drive scopes; no access token, client secret, or signing material is stored in Git.

### Added

- User-managed fuel-economy statistics decisions: suspected abnormal full-tank segments can be confirmed or excluded without deleting the fuel record.
- A Settings entry for reviewing historical suspected segments, including imported records, and restoring an excluded segment to fuel-economy statistics.

### Changed

- Reports, dashboard recent average, and fuel history now omit only explicitly excluded fuel-economy segments; fuel cost, odometer records, and cost per kilometre remain unchanged.

## [1.0.0] - 2026-08-23

### Added

- First official BuBu release: offline vehicle garage, fuel records, service work orders, expenses, history, reports, reminders, CSV export, complete `.bubu` backup, and validated restore.
- Per-vehicle fuel-service preference with CPC list-price suggestions and a precise self-service discount.
- Vehicle-type-specific common service items, including a compact quick-pick flow and shared date/time entry for fuel and service records.

### Changed

- Reports are organised around one selected vehicle, a consistent period filter, readable monthly spending trends, and fuel-economy data within the selected period.
- Settings are grouped into Vehicles, Reminders, and Data & Backup; internal recovery identifiers are no longer shown as primary user-facing content.

### Security

- All product data remains local to the device. Backups use a versioned manifest and SHA-256 integrity verification before restore.

### Changed

<!-- Keep upcoming release notes here. -->

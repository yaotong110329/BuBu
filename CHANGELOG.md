# Changelog

All notable changes to BuBu will be documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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

# Store cloud backups as existing `.bubu` files in Drive appDataFolder

Google Drive is a private transport and retention location for BuBu's existing, validated `.bubu` Data Backup files, never a second backup format or synchronisation database. Each user authorizes `drive.appdata` plus the narrow `drive.file` scope required by the current Android `AuthorizationClient` write flow; BuBu still writes only to its own `appDataFolder`. All cloud restores download into private temporary storage and then reuse the existing validation, preview, full-replace, and recovery-backup workflow.

package com.kumo.bubu.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.CloudBackupConnection
import com.kumo.bubu.domain.model.CloudBackupError
import com.kumo.bubu.domain.repository.RecoveryBackup
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onManageVehicles: () -> Unit,
    onManageServiceSettings: () -> Unit,
    onReviewFuelEconomy: () -> Unit = {},
    modifier: Modifier = Modifier,
    statutoryReminderSettingsUiState: StatutoryReminderSettingsUiState =
        StatutoryReminderSettingsUiState(),
    onTaxAndFeeEnabledChange: (Boolean) -> Unit = {},
    csvExportUiState: CsvExportUiState = CsvExportUiState(),
    onExportCsv: () -> Unit = {},
    backupUiState: BackupUiState = BackupUiState(),
    onCreateBackup: () -> Unit = {},
    backupReminderUiState: BackupReminderUiState = BackupReminderUiState(),
    onBackupReminderEnabledChange: (Boolean) -> Unit = {},
    restoreUiState: RestoreUiState = RestoreUiState(),
    onRestoreBackup: () -> Unit = {},
    onExportRecoveryBackup: () -> Unit = {},
    onDeleteRecoveryBackup: () -> Unit = {},
    cloudBackupUiState: CloudBackupUiState = CloudBackupUiState(),
    onConnectGoogleDrive: () -> Unit = {},
    onCreateCloudBackup: () -> Unit = {},
    onRestoreCloudBackup: () -> Unit = {},
    onDisconnectGoogleDrive: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp)
                .padding(bottom = 48.dp)
                .testTag("settings-scroll-list"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(stringResource(R.string.settings_section_vehicles)) {
                NavigationSettingRow(
                    title = stringResource(R.string.settings_manage_vehicles),
                    icon = { Icon(Icons.Filled.DirectionsCar, contentDescription = null) },
                    onClick = onManageVehicles,
                )
                GroupDivider()
                NavigationSettingRow(
                    title = stringResource(R.string.settings_service_settings),
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                    onClick = onManageServiceSettings,
                )
                GroupDivider()
                NavigationSettingRow(
                    title = stringResource(R.string.settings_fuel_economy_review),
                    summary = stringResource(R.string.settings_fuel_economy_review_summary),
                    icon = { Icon(Icons.Filled.LocalGasStation, contentDescription = null) },
                    onClick = onReviewFuelEconomy,
                )
            }
            SettingsSection(stringResource(R.string.settings_section_reminders)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_statutory_tax_and_fee)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (statutoryReminderSettingsUiState.hasError) {
                                    R.string.settings_statutory_reminder_error
                                } else {
                                    R.string.settings_statutory_rule_verified_date
                                },
                                statutoryReminderSettingsUiState.verifiedDate,
                            ),
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = statutoryReminderSettingsUiState.taxAndFeeEnabled,
                            onCheckedChange = onTaxAndFeeEnabledChange,
                            enabled = !statutoryReminderSettingsUiState.isLoading &&
                                !statutoryReminderSettingsUiState.isSaving,
                            modifier = Modifier.testTag("settings-tax-fee-switch"),
                        )
                    },
                )
                GroupDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_monthly_backup_reminder)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (backupReminderUiState.hasError) {
                                    R.string.settings_monthly_backup_reminder_error
                                } else if (backupReminderUiState.enabled) {
                                    R.string.settings_monthly_backup_reminder_enabled
                                } else {
                                    R.string.settings_monthly_backup_reminder_disabled
                                },
                            ),
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = backupReminderUiState.enabled,
                            onCheckedChange = onBackupReminderEnabledChange,
                            enabled = !backupReminderUiState.isLoading && !backupReminderUiState.isSaving,
                            modifier = Modifier.testTag("settings-monthly-backup-reminder-switch"),
                        )
                    },
                )
            }
            SettingsSection(stringResource(R.string.settings_section_data_backup)) {
                NavigationSettingRow(
                    title = stringResource(R.string.settings_csv_export),
                    summary = when {
                        csvExportUiState.isExporting -> stringResource(R.string.settings_csv_export_in_progress)
                        csvExportUiState.exportedFileName != null -> stringResource(
                            R.string.settings_csv_export_success,
                            csvExportUiState.exportedFileName,
                        )
                        csvExportUiState.error == CsvExportError.WRITE_FAILED ->
                            stringResource(R.string.settings_csv_export_error)
                        else -> stringResource(R.string.settings_csv_export_summary)
                    },
                    icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    enabled = !csvExportUiState.isExporting,
                    onClick = onExportCsv,
                    modifier = Modifier.testTag("settings-csv-export"),
                )
                GroupDivider()
                NavigationSettingRow(
                    title = stringResource(R.string.settings_create_backup),
                    summary = when {
                        backupUiState.isCreating -> stringResource(R.string.settings_backup_in_progress)
                        backupUiState.createdFileName != null -> stringResource(
                            R.string.settings_backup_success,
                            backupUiState.createdFileName,
                        )
                        backupUiState.hasError -> stringResource(R.string.settings_backup_error)
                        else -> stringResource(R.string.settings_backup_summary)
                    },
                    icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    enabled = !backupUiState.isCreating,
                    onClick = onCreateBackup,
                    modifier = Modifier.testTag("settings-create-backup"),
                )
                GroupDivider()
                NavigationSettingRow(
                    title = stringResource(R.string.settings_restore_backup),
                    summary = when {
                        restoreUiState.isLoadingPreview -> stringResource(R.string.settings_restore_previewing)
                        restoreUiState.isRestoring -> stringResource(R.string.settings_restore_in_progress)
                        restoreUiState.completed -> stringResource(R.string.settings_restore_success)
                        restoreUiState.error != null -> stringResource(R.string.settings_restore_error)
                        else -> stringResource(R.string.settings_restore_summary)
                    },
                    icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    enabled = !restoreUiState.isLoadingPreview && !restoreUiState.isRestoring,
                    onClick = onRestoreBackup,
                    modifier = Modifier.testTag("settings-restore-backup"),
                )
                restoreUiState.recoveryBackup?.let { recovery ->
                    GroupDivider()
                    RecoveryBackupStatus(
                        recovery = recovery,
                        failed = restoreUiState.recoveryActionFailed,
                        enabled = !restoreUiState.isManagingRecovery,
                        onExport = onExportRecoveryBackup,
                        onDelete = onDeleteRecoveryBackup,
                    )
                }
            }
            SettingsSection(stringResource(R.string.settings_section_google_drive)) {
                GoogleDriveBackupSettings(
                    state = cloudBackupUiState,
                    onConnect = onConnectGoogleDrive,
                    onCreateBackup = onCreateCloudBackup,
                    onRestore = onRestoreCloudBackup,
                    onDisconnect = onDisconnectGoogleDrive,
                )
            }
        }
    }
}

@Composable
private fun GoogleDriveBackupSettings(
    state: CloudBackupUiState,
    onConnect: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestore: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val connected = state.connection as? CloudBackupConnection.Connected
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_google_drive_title)) },
        supportingContent = {
            val text = when {
                connected != null -> stringResource(
                    R.string.settings_google_drive_connected_summary,
                    connected.account.email,
                )
                state.connection is CloudBackupConnection.NeedsAuthorization ->
                    stringResource(R.string.settings_google_drive_needs_authorization)
                else -> stringResource(R.string.settings_google_drive_not_connected)
            }
            Text(text)
        },
        leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
        trailingContent = {
            if (connected == null) {
                TextButton(onClick = onConnect, modifier = Modifier.testTag("settings-google-drive-connect")) {
                    Text(stringResource(R.string.settings_google_drive_connect))
                }
            }
        },
    )
    if (connected != null) {
        connected.account.lastCloudBackupAtEpochMillis?.let { lastBackupAt ->
            GroupDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_google_drive_last_backup)) },
                supportingContent = { Text(lastBackupAt.toCloudBackupDateTimeText()) },
            )
        }
        GroupDivider()
        NavigationSettingRow(
            title = stringResource(R.string.settings_google_drive_backup_now),
            summary = when {
                state.isUploading -> stringResource(R.string.settings_google_drive_backup_loading)
                state.backupCompleted -> stringResource(R.string.settings_google_drive_backup_complete)
                else -> stringResource(R.string.settings_google_drive_backup_summary)
            },
            icon = { Icon(Icons.Filled.Download, contentDescription = null) },
            enabled = !state.isUploading && !state.isLoadingBackups && !state.isDownloading,
            onClick = onCreateBackup,
            modifier = Modifier.testTag("settings-google-drive-backup"),
        )
        GroupDivider()
        NavigationSettingRow(
            title = stringResource(R.string.settings_google_drive_restore),
            summary = if (state.isLoadingBackups) {
                stringResource(R.string.settings_google_drive_list_loading)
            } else {
                stringResource(R.string.settings_google_drive_restore_summary)
            },
            icon = { Icon(Icons.Filled.Download, contentDescription = null) },
            enabled = !state.isUploading && !state.isDownloading,
            onClick = onRestore,
            modifier = Modifier.testTag("settings-google-drive-restore"),
        )
        GroupDivider()
        NavigationSettingRow(
            title = stringResource(R.string.settings_google_drive_disconnect),
            summary = stringResource(R.string.settings_google_drive_disconnect_summary),
            icon = { Icon(Icons.Filled.Download, contentDescription = null) },
            enabled = !state.isUploading && !state.isLoadingBackups && !state.isDownloading,
            onClick = onDisconnect,
            modifier = Modifier.testTag("settings-google-drive-disconnect"),
        )
    }
    state.error?.let { error ->
        GroupDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_google_drive_title)) },
            supportingContent = { Text(error.toCloudBackupMessage()) },
        )
    }
}

private fun Long.toCloudBackupDateTimeText(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))

@Composable
private fun CloudBackupError.toCloudBackupMessage(): String = stringResource(
    when (this) {
        CloudBackupError.NotConnected -> R.string.settings_google_drive_error_not_connected
        CloudBackupError.NetworkUnavailable -> R.string.settings_google_drive_error_offline
        CloudBackupError.AuthorizationExpired -> R.string.settings_google_drive_error_authorization_expired
        CloudBackupError.UploadFailed -> R.string.settings_google_drive_error_upload
        CloudBackupError.DownloadFailed -> R.string.settings_google_drive_error_download
        CloudBackupError.DeleteFailed -> R.string.settings_google_drive_error_delete
        CloudBackupError.InvalidBackup -> R.string.settings_google_drive_error_invalid_backup
        CloudBackupError.UnsupportedFormat -> R.string.settings_google_drive_error_unsupported_format
        CloudBackupError.ConfigurationMissing -> R.string.settings_google_drive_error_configuration
        is CloudBackupError.Unknown -> R.string.settings_google_drive_error_unknown
    },
)

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun NavigationSettingRow(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { value -> { Text(value) } },
        leadingContent = icon,
        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp))
}

@Composable
private fun RecoveryBackupStatus(
    recovery: RecoveryBackup,
    failed: Boolean,
    enabled: Boolean,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_recent_backup)) },
        supportingContent = {
            Text(
                text = if (failed) {
                    stringResource(R.string.settings_recovery_backup_error)
                } else {
                    stringResource(
                        R.string.settings_recent_backup_summary,
                        recovery.createdAtText(),
                        recovery.byteCount.displaySize(),
                    )
                },
            )
        },
        leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
        trailingContent = {
            Row {
                TextButton(
                    enabled = enabled,
                    onClick = onExport,
                    modifier = Modifier.testTag("settings-export-recovery-backup"),
                ) { Text(stringResource(R.string.settings_export_recovery_backup)) }
                TextButton(
                    enabled = enabled,
                    onClick = onDelete,
                    modifier = Modifier.testTag("settings-delete-recovery-backup"),
                ) { Text(stringResource(R.string.recovery_backup_delete_confirm)) }
            }
        },
    )
}

private fun RecoveryBackup.createdAtText(): String = Instant.ofEpochMilli(createdAtEpochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))

@Composable
private fun Long.displaySize(): String = when {
    this >= 1_000_000L -> stringResource(
        R.string.settings_backup_size_mb,
        BigDecimal.valueOf(this).divide(BigDecimal.valueOf(1_000_000L), 1, RoundingMode.HALF_UP).toPlainString(),
    )
    else -> stringResource(R.string.settings_backup_size_kb, (this / 1_000L).coerceAtLeast(1L))
}

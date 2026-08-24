package com.kumo.bubu.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.CloudBackup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

@Composable
fun CloudBackupListDialog(
    state: CloudBackupUiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDownload: (String) -> Unit,
    onDelete: (CloudBackup) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_backup_list_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    state.isLoadingBackups -> Text(stringResource(R.string.settings_google_drive_list_loading))
                    state.backups.isEmpty() -> Text(stringResource(R.string.cloud_backup_list_empty))
                    else -> state.backups.forEachIndexed { index, backup ->
                        if (index > 0) HorizontalDivider()
                        CloudBackupRow(
                            backup = backup,
                            enabled = !state.isDownloading && !state.isDeleting,
                            onDownload = onDownload,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onRefresh,
                enabled = !state.isLoadingBackups && !state.isDownloading && !state.isDeleting,
            ) {
                Text(stringResource(R.string.cloud_backup_list_refresh))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.restore_cancel)) }
        },
    )
}

@Composable
private fun CloudBackupRow(
    backup: CloudBackup,
    enabled: Boolean,
    onDownload: (String) -> Unit,
    onDelete: (CloudBackup) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(backup.createdAtText())
        Text(
            stringResource(
                R.string.cloud_backup_list_summary,
                backup.appVersion,
                backup.vehicleCount,
                backup.fuelRecordCount,
                backup.maintenanceRecordCount,
                backup.sizeText(),
            ),
        )
        androidx.compose.foundation.layout.Row {
            TextButton(onClick = { onDownload(backup.id) }, enabled = enabled) {
                Text(stringResource(R.string.cloud_backup_list_select))
            }
            TextButton(onClick = { onDelete(backup) }, enabled = enabled) {
                Text(stringResource(R.string.cloud_backup_list_delete))
            }
        }
    }
}

private fun CloudBackup.createdAtText(): String = Instant.ofEpochMilli(createdAtEpochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))

private fun CloudBackup.sizeText(): String = if (sizeBytes < 1024L * 1024L) {
    "${(sizeBytes / 1024.0).roundToLong()} KB"
} else {
    "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
}

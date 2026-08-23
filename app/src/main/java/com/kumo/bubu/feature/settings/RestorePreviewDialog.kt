package com.kumo.bubu.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kumo.bubu.R
import com.kumo.bubu.domain.repository.RestorePreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RestorePreviewDialog(
    state: RestoreUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.isRestoring) onDismiss() },
        title = { Text(stringResource(R.string.restore_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.preview?.let { preview -> RestorePreviewDetails(preview) }
                Text(
                    text = stringResource(R.string.restore_preview_overwrite_warning),
                    color = MaterialTheme.colorScheme.error,
                )
                if (state.error == RestoreError.RESTORE_FAILED) {
                    Text(
                        text = stringResource(R.string.restore_error_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.isRestoring && state.preview != null) {
                Text(stringResource(R.string.restore_confirm_overwrite))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isRestoring) {
                Text(stringResource(R.string.restore_cancel))
            }
        },
    )
}

@Composable
private fun RestorePreviewDetails(preview: RestorePreview) {
    val backupTime = Instant.ofEpochMilli(preview.createdAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .format(BACKUP_TIME_FORMATTER)
    Text(stringResource(R.string.restore_preview_created_at, backupTime))
    Text(stringResource(R.string.restore_preview_app_version, preview.appVersion))
    Text(
        stringResource(
            R.string.restore_preview_counts,
            preview.vehicleCount,
            preview.fuelRecordCount,
            preview.serviceRecordCount,
            preview.serviceItemCount,
            preview.expenseRecordCount,
            preview.reminderCount,
            preview.attachmentCount,
        ),
    )
    Text(stringResource(R.string.restore_preview_size, preview.totalByteCount))
}

private val BACKUP_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

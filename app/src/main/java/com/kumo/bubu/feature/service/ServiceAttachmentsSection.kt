package com.kumo.bubu.feature.service

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.MAX_SERVICE_ATTACHMENTS_PER_RECORD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ServiceAttachmentsSection(
    state: ServiceFormUiState,
    onEvent: (ServiceFormEvent) -> Unit,
    onPickAttachments: () -> Unit,
    loadAttachmentBytes: suspend (String) -> ByteArray?,
) {
    var previewPath by rememberSaveable { mutableStateOf<String?>(null) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.service_attachments_title),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    AddAttachmentCard(
                        isImporting = state.isImportingAttachments,
                        enabled = !state.isSaving &&
                            state.attachments.size < MAX_SERVICE_ATTACHMENTS_PER_RECORD,
                        onClick = onPickAttachments,
                    )
                }
                items(state.attachments, key = ServiceAttachmentDraft::relativePath) { attachment ->
                    AttachmentThumbnailCard(
                        attachment = attachment,
                        onPreview = { previewPath = attachment.relativePath },
                        onDelete = {
                            onEvent(ServiceFormEvent.RemoveAttachment(attachment.relativePath))
                        },
                        loadAttachmentBytes = loadAttachmentBytes,
                        canDelete = !state.isSaving && !state.isImportingAttachments,
                    )
                }
            }
            if (state.attachments.isEmpty()) {
                Text(
                    text = stringResource(R.string.service_attachments_empty),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    previewPath?.let { path ->
        state.attachments.firstOrNull { it.relativePath == path }?.let { attachment ->
            AttachmentPreviewDialog(
                attachment = attachment,
                loadAttachmentBytes = loadAttachmentBytes,
                onDismiss = { previewPath = null },
            )
        }
    }
}

@Composable
private fun AddAttachmentCard(
    isImporting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        enabled = enabled && !isImporting,
        modifier = Modifier.size(width = 132.dp, height = 150.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.add_symbol), style = MaterialTheme.typography.headlineLarge)
            }
        }
        Text(
            text = stringResource(
                if (isImporting) R.string.importing_service_attachments
                else R.string.add_service_photo,
            ),
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun AttachmentThumbnailCard(
    attachment: ServiceAttachmentDraft,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
    loadAttachmentBytes: suspend (String) -> ByteArray?,
    canDelete: Boolean,
) {
    Card(modifier = Modifier.size(width = 176.dp, height = 210.dp)) {
        PrivateAttachmentImage(
            relativePath = attachment.relativePath,
            contentDescription = stringResource(
                R.string.service_attachment_preview_description,
                attachment.displayName,
            ),
            modifier = Modifier.fillMaxWidth().height(104.dp),
            loadAttachmentBytes = loadAttachmentBytes,
        )
        Text(
            text = attachment.displayName,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.preview_service_photo))
        }
        TextButton(
            onClick = onDelete,
            enabled = canDelete,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.delete_service_photo))
        }
    }
}

@Composable
private fun AttachmentPreviewDialog(
    attachment: ServiceAttachmentDraft,
    loadAttachmentBytes: suspend (String) -> ByteArray?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(attachment.displayName, style = MaterialTheme.typography.titleMedium)
                PrivateAttachmentImage(
                    relativePath = attachment.relativePath,
                    contentDescription = stringResource(
                        R.string.service_attachment_preview_description,
                        attachment.displayName,
                    ),
                    modifier = Modifier.fillMaxWidth().height(480.dp),
                    contentScale = ContentScale.Fit,
                    targetSizePx = PREVIEW_TARGET_SIZE_PX,
                    loadAttachmentBytes = loadAttachmentBytes,
                )
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun PrivateAttachmentImage(
    relativePath: String,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetSizePx: Int = THUMBNAIL_TARGET_SIZE_PX,
    loadAttachmentBytes: suspend (String) -> ByteArray?,
) {
    val previewState by produceState(
        initialValue = AttachmentBitmapState(),
        relativePath,
        targetSizePx,
        loadAttachmentBytes,
    ) {
        value = withContext(Dispatchers.IO) {
            val encodedBytes = runCatching { loadAttachmentBytes(relativePath) }.getOrNull()
            AttachmentBitmapState(
                isComplete = true,
                bitmap = encodedBytes?.let { decodeAttachmentBytes(it, targetSizePx) },
            )
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            previewState.bitmap != null -> Image(
                bitmap = requireNotNull(previewState.bitmap),
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
            )
            previewState.isComplete -> Text(
                text = stringResource(R.string.service_attachment_preview_unavailable),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            else -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}

private fun decodeAttachmentBytes(
    encodedBytes: ByteArray,
    targetSizePx: Int,
): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(encodedBytes, 0, encodedBytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > targetSizePx * 2 ||
        bounds.outHeight / sampleSize > targetSizePx * 2
    ) {
        sampleSize *= 2
    }
    BitmapFactory.decodeByteArray(
        encodedBytes,
        0,
        encodedBytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )?.asImageBitmap()
}.getOrNull()

private data class AttachmentBitmapState(
    val isComplete: Boolean = false,
    val bitmap: ImageBitmap? = null,
)

private const val THUMBNAIL_TARGET_SIZE_PX = 512
private const val PREVIEW_TARGET_SIZE_PX = 2_048

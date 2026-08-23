package com.kumo.bubu.feature.service

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.MAX_SERVICE_ATTACHMENTS_PER_RECORD

@Composable
fun ServiceFormRoute(
    viewModel: ServiceFormViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_SERVICE_ATTACHMENTS_PER_RECORD),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onEvent(ServiceFormEvent.AttachmentsSelected(uris.map { it.toString() }))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.savedEffects.collect { onSaved() }
    }
    ServiceFormScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = { viewModel.discardUnsavedAttachments(onBack) },
        onPickAttachments = {
            attachmentPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        loadAttachmentBytes = viewModel::readAttachmentBytes,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceFormScreen(
    state: ServiceFormUiState,
    onEvent: (ServiceFormEvent) -> Unit,
    onBack: () -> Unit,
    onPickAttachments: () -> Unit = {},
    loadAttachmentBytes: suspend (String) -> ByteArray? = { null },
) {
    BackHandler {
        when {
            state.isItemPickerVisible -> onEvent(ServiceFormEvent.CloseItemPicker)
            !state.isSaving && !state.isImportingAttachments -> onBack()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val itemFeedbackMessage = state.itemFeedback?.let { feedback ->
        stringResource(
            when (feedback) {
                ServiceItemFeedback.ADDED -> R.string.service_item_added
                ServiceItemFeedback.ALREADY_ADDED -> R.string.service_item_already_added
            },
        )
    }
    itemFeedbackMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onEvent(ServiceFormEvent.DismissItemFeedback)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.editingId == null) R.string.add_service_title
                            else R.string.edit_service_title,
                        ),
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        enabled = !state.isSaving && !state.isImportingAttachments,
                    ) {
                        Text(stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = { ServiceSaveBar(state, onEvent) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        if (state.isLoading) {
            ServiceFormLoading(scaffoldPadding)
        } else {
            ServiceFormContent(
                state,
                onEvent,
                onPickAttachments,
                loadAttachmentBytes,
                scaffoldPadding,
            )
        }
    }
    ServiceCustomItemDialog(state, onEvent)
    ServiceItemEditorSheet(state, onEvent)
    if (state.isItemPickerVisible) ServiceItemPickerScreen(state, onEvent)
}

@Composable
private fun ServiceFormContent(
    state: ServiceFormUiState,
    onEvent: (ServiceFormEvent) -> Unit,
    onPickAttachments: () -> Unit,
    loadAttachmentBytes: suspend (String) -> ByteArray?,
    scaffoldPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = scaffoldPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = scaffoldPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ServiceWorkOrderCard(state, onEvent) }
        item { ServiceItemsSection(state, onEvent) }
        item { ServiceWorkOrderTotal(state) }
        item {
            ServiceTextField(
                value = state.title,
                onValueChange = { onEvent(ServiceFormEvent.TitleChanged(it)) },
                labelRes = R.string.service_title,
                error = state.error.takeIf { it == ServiceFormError.TITLE_REQUIRED },
                enabled = !state.isSaving,
            )
        }
        item {
            ServiceNotesCard(state.note, enabled = !state.isSaving) {
                onEvent(ServiceFormEvent.NoteChanged(it))
            }
        }
        item {
            ServiceAttachmentsSection(
                state,
                onEvent,
                onPickAttachments,
                loadAttachmentBytes,
            )
        }
        state.error?.let { error ->
            item { ServiceFormErrorMessage(error) }
        }
    }
}

@Composable
private fun ServiceFormLoading(scaffoldPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.loading_service_form),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ServiceSaveBar(
    state: ServiceFormUiState,
    onEvent: (ServiceFormEvent) -> Unit,
) {
    Surface(modifier = Modifier.imePadding(), tonalElevation = 3.dp) {
        Button(
            onClick = { onEvent(ServiceFormEvent.Save) },
            enabled = !state.isLoading && !state.isSaving &&
                !state.isImportingAttachments && state.vehicles.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).padding(end = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            }
            Text(
                stringResource(
                    if (state.isSaving) R.string.saving_service
                    else R.string.save_service,
                ),
            )
        }
    }
}

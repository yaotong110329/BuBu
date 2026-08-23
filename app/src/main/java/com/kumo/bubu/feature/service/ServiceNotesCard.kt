package com.kumo.bubu.feature.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kumo.bubu.R

@Composable
internal fun ServiceNotesCard(
    note: String,
    enabled: Boolean,
    onNoteChanged: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.service_note),
                style = MaterialTheme.typography.titleMedium,
            )
            ServiceTextField(
                value = note,
                onValueChange = onNoteChanged,
                labelRes = R.string.service_note,
                minLines = 4,
                enabled = enabled,
            )
        }
    }
}

package com.daniloff.justdrop.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.daniloff.justdrop.R
import com.daniloff.justdrop.model.IncomingTransfer
import com.daniloff.justdrop.utils.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingTransferDialog(
    transfer: IncomingTransfer,
    onAction: () -> Unit
) {
    val isCompleted = transfer.isFinished
    val progress = if (transfer.totalBytes > 0) {
        (transfer.receivedBytes.toFloat() / transfer.totalBytes).coerceIn(0f, 1f)
    } else {
        0f
    }

    BasicAlertDialog(
        onDismissRequest = {}
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier
                    .padding(top = 20.dp, bottom = 8.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(
                        if (isCompleted) {
                            R.string.transfer_completed
                        } else {
                            R.string.receiving_files
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = pluralStringResource(
                        id = R.plurals.received_files_count,
                        count = transfer.totalFiles,
                        formatArgs = arrayOf(transfer.totalFiles, transfer.completedFiles)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!isCompleted) {
                    Spacer(Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(
                            R.string.received_bytes,
                            formatFileSize(transfer.receivedBytes),
                            formatFileSize(transfer.totalBytes)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = onAction
                ) {
                    Text(
                        text = if (isCompleted) {
                            stringResource(R.string.close)
                        } else {
                            stringResource(R.string.cancel)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
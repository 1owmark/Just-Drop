package com.daniloff.justdrop.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniloff.justdrop.R
import com.daniloff.justdrop.model.TransferFile
import com.daniloff.justdrop.model.TransferStatus
import com.daniloff.justdrop.ui.theme.Success
import com.daniloff.justdrop.utils.formatFileSize

@Composable
fun TransferFileItem(
    file: TransferFile
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = file.file.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = formatFileSize(file.file.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.height(5.dp))

            when (file.status) {
                TransferStatus.WAITING -> {
                    WaitingTransferState()
                }

                TransferStatus.SENDING -> {
                    SendingTransferState(
                        progress = file.progress
                    )
                }

                TransferStatus.SUCCESS -> {
                    SuccessTransferState()
                }

                TransferStatus.ERROR -> {
                    ErrorTransferState()
                }
            }
        }
    }
}

@Composable
private fun WaitingTransferState() {
    Text(
        text = stringResource(R.string.expectation),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun SendingTransferState(
    progress: Float,
) {
    Column {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SuccessTransferState() {
    Text(
        text = stringResource(R.string.successfully),
        style = MaterialTheme.typography.labelSmall,
        color = Success
    )
}

@Composable
private fun ErrorTransferState() {
    Text(
        text = stringResource(R.string.error),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error
    )
}
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

            when (file.status) {
                TransferStatus.WAITING -> {
                    WaitingTransferState()
                }

                TransferStatus.SENDING -> {
                    SendingTransferState(
                        progress = file.progress,
                        speed = file.speed
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
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun SendingTransferState(
    progress: Float,
    speed: Long
) {
    Column {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "${formatFileSize(speed)}/с",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SuccessTransferState() {
    Text(
        text = stringResource(R.string.done),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ErrorTransferState() {
    Text(
        text = stringResource(R.string.error),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error
    )
}
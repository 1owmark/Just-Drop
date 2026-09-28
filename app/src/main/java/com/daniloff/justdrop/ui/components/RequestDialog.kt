package com.daniloff.justdrop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.daniloff.justdrop.model.TransferRequestFile
import com.daniloff.justdrop.ui.theme.Success
import com.daniloff.justdrop.ui.theme.TextHint
import com.daniloff.justdrop.utils.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDialog(
    files: List<TransferRequestFile>,
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = {}
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            val totalSize = files.sumOf { it.size }
            val filesCount = pluralStringResource(
                R.plurals.files_count,
                files.size,
                files.size
            )

            Column(
                Modifier
                    .padding(top = 20.dp, bottom = 8.dp, start = 20.dp, end = 20.dp),
            ) {
                Text(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    text = stringResource(R.string.accept_files),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(Modifier.height(15.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(files) { file ->
                        TransferRequestFileItem(file)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Разделитель
                HorizontalDivider(
                    color = TextHint.copy(alpha = 0.3f)
                )

                Spacer(Modifier.height(15.dp))

                // Общее количество файлов и размер
                Text(
                    text = stringResource(
                        R.string.files_summary,
                        filesCount,
                        formatFileSize(totalSize)
                    ),
                    style = MaterialTheme.typography.labelMedium
                )

                // Кнопки отмены и принятия
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onCancel
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    TextButton(
                        onClick = onAccept
                    ) {
                        Text(
                            text = stringResource(R.string.apply),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Success
                        )
                    }
                }
            }
        }
    }
}
package com.daniloff.justdrop.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.daniloff.justdrop.R
import com.daniloff.justdrop.model.TransferFile
import com.daniloff.justdrop.model.TransferRequestFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDialog(
    titleRes: Int,
    files: List<TransferFile>,
    onCancel: () -> Unit
) {
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
            ) {
                Text(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(Modifier.height(15.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(files) { file ->
                        TransferFileItem(file)
                    }
                }
                Spacer(Modifier.height(30.dp))
                TextButton(
                    onClick = onCancel
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
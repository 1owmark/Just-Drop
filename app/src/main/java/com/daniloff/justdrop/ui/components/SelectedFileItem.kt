package com.daniloff.justdrop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniloff.justdrop.R
import com.daniloff.justdrop.model.SelectedFile
import com.daniloff.justdrop.utils.formatFileSize

@Composable
fun SelectedFileItem(
    file: SelectedFile,
    onRemove: (SelectedFile) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = file.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatFileSize(file.size),
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium
        )

        IconButton(
            onClick = { onRemove(file) }
        ) {
            Image(
                painter = painterResource(R.drawable.trash),
                contentDescription = null,
                Modifier.size(20.dp),
            )
        }
    }
}
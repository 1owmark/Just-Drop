package com.daniloff.justdrop.ui


import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.daniloff.justdrop.R
import com.daniloff.justdrop.ui.components.SearchIndicator
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.daniloff.justdrop.ui.components.DeviceCard
import com.daniloff.justdrop.ui.theme.TextHint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.daniloff.justdrop.model.Device
import com.daniloff.justdrop.ui.components.SelectedFilesDialog
import androidx.compose.ui.platform.LocalResources
import com.daniloff.justdrop.utils.truncateFileName


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val viewModel: MainViewModel = viewModel()
    val devices by viewModel.devices.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    var selectedDevice by remember {
        mutableStateOf<Device?>(null)
    }
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    var showSelectedFilesDialog by remember {
        mutableStateOf(false)
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val device = selectedDevice

        if (device != null) {
            viewModel.onFilesSelected(uris)
            showSelectedFilesDialog = true
        }
    }

    // Если добавлены одинаковые файлы
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.FileAlreadyAdded -> {
                    Toast.makeText(
                        context,
                        resources.getString(
                            R.string.file_has_already_been_added,
                            truncateFileName(event.fileName, 20)
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Scaffold() { innerPadding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            Row(
                modifier = Modifier
                    .padding(top = 80.dp)
            ) {
                Text(
                    text = "Just ",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Drop",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (devices.isEmpty()) {
                when (searchState) {
                    DeviceSearchState.Searching -> {
                        Spacer(Modifier.height(250.dp))
                        SearchIndicator()

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.searching_devices),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    DeviceSearchState.NotFound -> {
                        Spacer(Modifier.height(160.dp))
                        Image(
                            painter = painterResource(R.drawable.wifi),
                            contentDescription = null
                        )
                        Spacer(Modifier.height(40.dp))
                        Text(
                            text = stringResource(R.string.devices_not_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            text = stringResource(R.string.same_network_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextHint,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(100.dp))
                LazyColumn {
                    items(devices) { device ->
                        DeviceCard(device) {
                            selectedDevice = device
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                    }
                }
            }
        }
    }
    if (showSelectedFilesDialog) {
        SelectedFilesDialog(
            files = selectedFiles,
            onAddFiles = {
                filePickerLauncher.launch(arrayOf("*/*"))
            },
            onSend = {
                selectedDevice?.let { device ->
                    viewModel.sendFiles(device)
                }
            },
            onCancel = {
                showSelectedFilesDialog = false
                viewModel.clearSelectedFiles()
            },
            onRemoveFile = viewModel::deleteFile
        )
    }
}


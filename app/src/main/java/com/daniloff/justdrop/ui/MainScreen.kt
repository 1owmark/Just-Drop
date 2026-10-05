package com.daniloff.justdrop.ui


import android.util.Log
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.daniloff.justdrop.ui.components.DeviceCard
import com.daniloff.justdrop.ui.theme.TextHint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.daniloff.justdrop.model.Device
import com.daniloff.justdrop.ui.components.SelectedFilesDialog
import androidx.compose.ui.platform.LocalResources
import com.daniloff.justdrop.model.TransferRequestState
import com.daniloff.justdrop.model.TransferStatus
import com.daniloff.justdrop.ui.components.DeclinedDialog
import com.daniloff.justdrop.ui.components.IncomingTransferDialog
import com.daniloff.justdrop.ui.components.RequestDialog
import com.daniloff.justdrop.ui.components.TransferDialog
import com.daniloff.justdrop.utils.truncateFileName
import kotlinx.coroutines.launch


@Composable
fun MainScreen() {
    val scope = rememberCoroutineScope()
    val viewModel: MainViewModel = viewModel()
    val devices by viewModel.devices.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val transferRequest by viewModel.transferRequest.collectAsState()
    val transferRequestState by viewModel.transferRequestState.collectAsState()
    val transferFiles by viewModel.transferFiles.collectAsState()
    val incomingTransfer by viewModel.incomingTransfer.collectAsState()
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
    var showTransferDialog by remember {
        mutableStateOf(false)
    }
    val isTransferCompleted = transferFiles.isNotEmpty() &&
            transferFiles.all {
                it.status == TransferStatus.SUCCESS || it.status == TransferStatus.ERROR
            }

    incomingTransfer?.let { transfer ->
        IncomingTransferDialog(transfer = transfer,
            onAction = {
                viewModel.clearIncomingTransfer()
            })
    }

    // Обработка UI событий
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

                is UiEvent.FileOpenError -> {
                    Toast.makeText(
                        context,
                        resources.getString(
                            R.string.error_reading_file,
                            truncateFileName(event.fileName)
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is UiEvent.FileUploadError -> {
                    Toast.makeText(
                        context,
                        resources.getString(
                            R.string.error_sending_file,
                            truncateFileName(event.fileName)
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is UiEvent.DeviceUnavailable -> {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.device_unavailable),
                        Toast.LENGTH_LONG
                    ).show()
                }

                is UiEvent.TransferRequestError -> {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.device_unavailable),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(transferRequestState) {
        when (transferRequestState) {
            TransferRequestState.ACCEPTED -> {
                showSelectedFilesDialog = false
                showTransferDialog = true

                selectedDevice?.let { device ->
                    viewModel.sendFiles(device.info.deviceId)
                }
            }

            TransferRequestState.DECLINED -> {
                showSelectedFilesDialog = false
                viewModel.clearSelectedFiles()
            }

            else -> Unit
        }
    }

    if (showTransferDialog) {
        TransferDialog(
            files = transferFiles,
            isCompleted = isTransferCompleted,
            onAction = {
                if (isTransferCompleted) {
                    showTransferDialog = false
                } else {
                    viewModel.cancelTransfer()
                    showTransferDialog = false
                    Toast.makeText(
                        context,
                        resources.getString(R.string.file_transfer_cancelled),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (devices.isEmpty()) {
                EmptyState(
                    state = searchState,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 72.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    items(devices) { device ->
                        DeviceCard(device) {
                            selectedDevice = device
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                    }
                }
            }
            AppTitle(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            )
        }
    }

    if (showSelectedFilesDialog) {
        SelectedFilesDialog(
            files = selectedFiles,
            isSendButtonEnabled = transferRequestState != TransferRequestState.WAITING,
            onAddFiles = {
                filePickerLauncher.launch(arrayOf("*/*"))
            },
            onSend = {
                selectedDevice?.let { device ->
                    scope.launch {
                        val response = viewModel.requestTransfer(device.info.deviceId)

                        Log.d("HANDSHAKE", "response = $response")
                    }
                }
            },
            onCancel = {
                showSelectedFilesDialog = false
                viewModel.clearSelectedFiles()
            },
            onRemoveFile = viewModel::deleteFile
        )
    }

    if (transferRequest != null) {
        RequestDialog(
            files = transferRequest!!.request.files,
            onAccept = {
                viewModel.acceptTransfer()
            },
            onCancel = {
                viewModel.declineTransfer()
                viewModel.clearSelectedFiles()
            }
        )
    }

    if (transferRequestState == TransferRequestState.DECLINED) {
        DeclinedDialog {
            viewModel.resetTransferRequestState()
        }
    }
}

@Composable
private fun AppTitle(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
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
}

@Composable
private fun EmptyState(
    state: DeviceSearchState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            DeviceSearchState.Searching -> {
                SearchIndicator()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.searching_devices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            DeviceSearchState.NotFound -> {
                Image(
                    painter = painterResource(R.drawable.wifi),
                    contentDescription = null
                )
                Spacer(Modifier.height(16.dp))
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
    }
}

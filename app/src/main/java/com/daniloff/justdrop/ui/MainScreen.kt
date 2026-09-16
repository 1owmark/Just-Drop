package com.daniloff.justdrop.ui.theme



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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daniloff.justdrop.R
import com.daniloff.justdrop.network.discovery.DeviceDiscovery
import com.daniloff.justdrop.network.server.HttpServer
import com.daniloff.justdrop.ui.components.SearchIndicator


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {

    val context = LocalContext.current
    val deviceDiscovery = remember { DeviceDiscovery(context) }
    val httpServer = remember { HttpServer() }

    LaunchedEffect(Unit) {
        httpServer.start()
    }


    Scaffold() { innerPadding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
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

            Spacer(Modifier.height(250.dp))

            SearchIndicator()

            Spacer(Modifier.height(8.dp))

            Text(text = stringResource(R.string.searching_devices),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
@Preview(showBackground = true)
fun MainScreenPreview() {
    JustDropTheme {
        MainScreen()
    }
}


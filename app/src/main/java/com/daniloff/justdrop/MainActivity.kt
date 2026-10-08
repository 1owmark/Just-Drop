package com.daniloff.justdrop

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.daniloff.justdrop.ui.theme.JustDropTheme
import com.daniloff.justdrop.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissions = mutableListOf(
                Manifest.permission.POST_NOTIFICATIONS
            )

            if (Build.VERSION.SDK_INT >= 37) {
                permissions += Manifest.permission.ACCESS_LOCAL_NETWORK
            }

            requestPermissions(permissions.toTypedArray(), 100)
        }

        enableEdgeToEdge()
        setContent {
            JustDropTheme {
                MainScreen()
            }
        }
    }
}
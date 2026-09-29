package com.daniloff.justdrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.daniloff.justdrop.ui.theme.JustDropTheme
import com.daniloff.justdrop.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JustDropTheme {
                MainScreen()
            }
        }
    }
}
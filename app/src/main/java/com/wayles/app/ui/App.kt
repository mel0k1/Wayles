package com.wayles.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wayles.app.td.Auth
import com.wayles.app.td.Td

@Composable
fun App() {
    WaylesTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                Td.auth != Auth.Done -> LoginScreen()
                Td.openChatId != null -> ChatScreen()
                else -> MainScreen()
            }
        }
    }
}

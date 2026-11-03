package com.wayles.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wayles.app.CrashGuard
import com.wayles.app.td.Auth
import com.wayles.app.td.Td

@Composable
fun App() {
    val crash = CrashGuard.report
    val ctx = LocalContext.current
    WaylesTheme(mode = ThemeStore.mode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (crash != null) {
                CrashScreen(crash) { CrashGuard.dismiss(ctx) }
            } else {
                when {
                    Td.auth != Auth.Done -> LoginScreen()
                    Td.mediaChatId != null -> MediaScreen()
                    Td.openChatId != null -> ChatScreen()
                    else -> MainScreen()
                }
            }
        }
    }
}

package com.wayles.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wayles.app.td.Td
import com.wayles.app.ui.App
import com.wayles.app.ui.ThemeStore
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.install(this)
        ThemeStore.init(this)
        Td.appContext = applicationContext
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("messages", "Сообщения", NotificationManager.IMPORTANCE_HIGH)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        Td.mediaDir = File(filesDir, "media").apply { mkdirs() }
        setContent {
            App()
        }
        Td.start(File(filesDir, "tdlib").apply { mkdirs() }, filesDir)
    }

    override fun onStart() {
        super.onStart()
        Td.appOpen = true
    }

    override fun onStop() {
        super.onStop()
        Td.appOpen = false
    }
}

package com.wayles.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wayles.app.td.Td
import com.wayles.app.ui.App
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Td.mediaDir = File(filesDir, "media").apply { mkdirs() }
        setContent {
            App()
        }
    }
}

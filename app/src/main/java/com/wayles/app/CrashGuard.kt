package com.wayles.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashGuard {
    var report by mutableStateOf<String?>(null)
        private set

    fun install(ctx: Context) {
        val file = File(ctx.filesDir, "last_crash.txt")
        report = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()
        val parent = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            val trace = StringWriter()
            e.printStackTrace(PrintWriter(trace))
            runCatching { file.writeText(t.name + "\n" + trace) }
            parent?.uncaughtException(t, e)
        }
    }

    fun dismiss(ctx: Context) {
        report = null
        runCatching { File(ctx.filesDir, "last_crash.txt").delete() }
    }
}

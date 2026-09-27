package com.pixelmagic

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pixelmagic.core.shizuku.ShizukuPrivilegeBridge
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * PixelMagicApp
 * 全局应用入口与崩溃防护兜底 (CrashGuard)
 */
class PixelMagicApp : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        installCrashGuard()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ShizukuPrivilegeBridge.init(this)
    }

    private fun installCrashGuard() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("CrashGuard", "Caught uncaught exception on thread ${thread.name}", throwable)
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()

                val crashFile = File(filesDir, "crash_guard.log")
                crashFile.writeText("Time: ${System.currentTimeMillis()}\nThread: ${thread.name}\n\n$stackTrace")

                val prefs = getSharedPreferences("crash_guard", Context.MODE_PRIVATE)
                val lastCrashTime = prefs.getLong("last_crash_time", 0L)
                val now = System.currentTimeMillis()
                prefs.edit().putLong("last_crash_time", now).commit()

                if (now - lastCrashTime > 5000L) {
                    val restartIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra("crash_recovered", true)
                    }
                    startActivity(restartIntent)
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(10)
                } else {
                    defaultHandler?.uncaughtException(thread, throwable)
                }
            } catch (e: Throwable) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        lateinit var instance: PixelMagicApp
            private set
    }
}

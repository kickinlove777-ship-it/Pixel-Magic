package com.pixelmagic.core.shizuku

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuPrivilegeBridge {

    private val _state = MutableStateFlow(ShizukuState.DEAD)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _state.value = ShizukuState.DEAD
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                _state.value = ShizukuState.AUTHORIZED
            } else {
                _state.value = ShizukuState.DENIED
            }
        }

    fun init(context: Context) {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            updateState()
        } catch (e: Throwable) {
            _state.value = ShizukuState.DEAD
        }
    }

    fun requestPermission(activity: Activity, requestCode: Int = 1001) {
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            } else {
                _state.value = ShizukuState.AUTHORIZED
            }
        } catch (e: Throwable) {
            _state.value = ShizukuState.DEAD
        }
    }

    fun checkPermission(): ShizukuState {
        updateState()
        return _state.value
    }

    private fun updateState() {
        _state.value = try {
            if (!Shizuku.pingBinder()) {
                ShizukuState.DEAD
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuState.AUTHORIZED
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                ShizukuState.DENIED
            } else {
                ShizukuState.UNAUTHORIZED
            }
        } catch (e: Throwable) {
            ShizukuState.DEAD
        }
    }

    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        if (checkPermission() != ShizukuState.AUTHORIZED) {
            return@withContext CommandResult(-1, "", "Shizuku 未授权或未运行")
        }
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val exitCode = process.waitFor()
            CommandResult(exitCode, output.trim(), error.trim())
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "执行异常")
        }
    }

    suspend fun setFreeformGates(enabled: Boolean): Boolean {
        val v = if (enabled) "1" else "0"
        val r1 = executeCommand("settings put global enable_freeform_support $v")
        val r2 = executeCommand("settings put global force_resizable_activities $v")
        return r1.isSuccess && r2.isSuccess
    }

    suspend fun isFreeformSupportEnabled(): Boolean {
        val res = executeCommand("settings get global enable_freeform_support")
        return res.output.trim() == "1"
    }

    suspend fun launchFreeformWindow(componentName: String): Boolean {
        return executeCommand("am start -n $componentName --windowingMode 5").isSuccess
    }

    suspend fun setRefreshRatePolicy(minRate: Float, maxRate: Float): Boolean {
        val r1 = executeCommand("settings put system min_refresh_rate $minRate")
        val r2 = executeCommand("settings put system peak_refresh_rate $maxRate")
        return r1.isSuccess && r2.isSuccess
    }

    suspend fun setBatteryChargeLimit80(enabled: Boolean): Boolean {
        val value = if (enabled) "80" else "0"
        return executeCommand("settings put global charging_limit $value").isSuccess
    }

    suspend fun isBatteryChargeLimit80Enabled(): Boolean {
        val res = executeCommand("settings get global charging_limit")
        return res.output.trim() == "80"
    }
}

enum class ShizukuState {
    AUTHORIZED,
    UNAUTHORIZED,
    DENIED,
    DEAD
}

data class CommandResult(val exitCode: Int, val output: String, val error: String) {
    val isSuccess: Boolean get() = exitCode == 0
}

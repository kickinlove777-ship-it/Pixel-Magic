package com.pixelmagic.modules.hardware

import com.pixelmagic.core.shizuku.ShizukuPrivilegeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PixelHardwareModule {

    data class State(
        val is120HzLocked: Boolean = false,
        val isChargeLimit80Enabled: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun refresh() {
        val charge80 = ShizukuPrivilegeBridge.isBatteryChargeLimit80Enabled()
        _state.value = _state.value.copy(isChargeLimit80Enabled = charge80)
    }

    suspend fun toggleLock120Hz(lock: Boolean): Boolean {
        val min = if (lock) 120.0f else 1.0f
        val max = 120.0f
        val ok = ShizukuPrivilegeBridge.setRefreshRatePolicy(min, max)
        if (ok) _state.value = _state.value.copy(is120HzLocked = lock)
        return ok
    }

    suspend fun toggleBatteryChargeLimit(enabled: Boolean): Boolean {
        val ok = ShizukuPrivilegeBridge.setBatteryChargeLimit80(enabled)
        if (ok) _state.value = _state.value.copy(isChargeLimit80Enabled = enabled)
        return ok
    }
}

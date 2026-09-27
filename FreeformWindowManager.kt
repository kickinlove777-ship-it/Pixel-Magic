package com.pixelmagic.modules.freeform

import com.pixelmagic.core.shizuku.ShizukuPrivilegeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FreeformWindowManager {

    data class State(val isEnabled: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun refresh() {
        val ok = ShizukuPrivilegeBridge.isFreeformSupportEnabled()
        _state.value = State(ok)
    }

    suspend fun enableFreeform(): Boolean {
        val ok = ShizukuPrivilegeBridge.setFreeformGates(true)
        if (ok) _state.value = State(true)
        return ok
    }

    suspend fun launchInFreeform(pkgAndActivity: String): Boolean {
        return ShizukuPrivilegeBridge.launchFreeformWindow(pkgAndActivity)
    }
}

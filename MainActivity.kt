package com.pixelmagic

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pixelmagic.core.shizuku.ShizukuPrivilegeBridge
import com.pixelmagic.core.shizuku.ShizukuState
import com.pixelmagic.modules.freeform.FreeformWindowManager
import com.pixelmagic.modules.haptics.PixelHapticEngine
import com.pixelmagic.modules.hardware.PixelHardwareModule
import com.pixelmagic.modules.notification.NotificationLogService
import com.pixelmagic.modules.pill.PixelFluidPillService
import com.pixelmagic.modules.portal.PixelPortalService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val hardwareModule = PixelHardwareModule()
    private val freeformManager = FreeformWindowManager()
    private val hapticEngine by lazy { PixelHapticEngine(this) }

    private val pillRunningFlow = MutableStateFlow(false)
    private val portalRunningFlow = MutableStateFlow(false)
    private val overlayGrantedFlow = MutableStateFlow(false)
    private val notificationGrantedFlow = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) {
                    dynamicDarkColorScheme(this)
                } else {
                    dynamicLightColorScheme(this)
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    val pillRunning by pillRunningFlow.collectAsState()
                    val portalRunning by portalRunningFlow.collectAsState()
                    val overlayGranted by overlayGrantedFlow.collectAsState()
                    val notificationGranted by notificationGrantedFlow.collectAsState()
                    val shizukuState by ShizukuPrivilegeBridge.state.collectAsState()

                    PixelNativeAppScreen(
                        hardware = hardwareModule,
                        freeform = freeformManager,
                        haptics = hapticEngine,
                        pillRunning = pillRunning,
                        portalRunning = portalRunning,
                        overlayGranted = overlayGranted,
                        notificationGranted = notificationGranted,
                        shizukuState = shizukuState,
                        onRequestShizuku = {
                            ShizukuPrivilegeBridge.requestPermission(this)
                        },
                        onTogglePillService = { enable ->
                            hapticEngine.click()
                            val intent = Intent(this, PixelFluidPillService::class.java)
                            if (enable) {
                                ContextCompat.startForegroundService(this, intent)
                            } else {
                                stopService(intent)
                            }
                            syncRuntimeStates()
                        },
                        onTogglePortalService = { enable ->
                            hapticEngine.click()
                            val intent = Intent(this, PixelPortalService::class.java)
                            if (enable) {
                                ContextCompat.startForegroundService(this, intent)
                            } else {
                                stopService(intent)
                            }
                            syncRuntimeStates()
                        },
                        onOpenOverlaySettings = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        },
                        onOpenNotificationSettings = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncRuntimeStates()
    }

    private fun syncRuntimeStates() {
        pillRunningFlow.value = isServiceRunning(this, PixelFluidPillService::class.java)
        portalRunningFlow.value = isServiceRunning(this, PixelPortalService::class.java)
        overlayGrantedFlow.value = Settings.canDrawOverlays(this)
        notificationGrantedFlow.value =
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

        val state = ShizukuPrivilegeBridge.checkPermission()
        if (state == ShizukuState.AUTHORIZED) {
            hardwareModule.refresh()
            freeformManager.refresh()
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}

enum class PixelNavigationTab(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    INTERFACE("界面个性化", Icons.Filled.Palette, Icons.Outlined.Palette),
    SYSTEM("手势与系统", Icons.Filled.TouchApp, Icons.Outlined.TouchApp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelNativeAppScreen(
    hardware: PixelHardwareModule,
    freeform: FreeformWindowManager,
    haptics: PixelHapticEngine,
    pillRunning: Boolean,
    portalRunning: Boolean,
    overlayGranted: Boolean,
    notificationGranted: Boolean,
    shizukuState: ShizukuState,
    onRequestShizuku: () -> Unit,
    onTogglePillService: (Boolean) -> Unit,
    onTogglePortalService: (Boolean) -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(PixelNavigationTab.INTERFACE) }

    val hardwareState by hardware.state.collectAsState()
    val freeformState by freeform.state.collectAsState()

    var activeDialogMessage by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Pixel Magic",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Google Pixel 9 Pro XL · Android 17",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                PixelNavigationTab.values().forEach { tab ->
                    val selected = selectedTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            haptics.tick()
                            selectedTab = tab
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PixelSystemStatusBanner(
                    shizukuState = shizukuState,
                    overlayGranted = overlayGranted,
                    notificationGranted = notificationGranted,
                    onRequestShizuku = onRequestShizuku,
                    onGrantOverlay = onOpenOverlaySettings,
                    onGrantNotification = onOpenNotificationSettings
                )
            }

            if (selectedTab == PixelNavigationTab.INTERFACE) {
                item {
                    M3GroupCard(title = "锁屏交互与端侧流转") {
                        M3ActionRow(
                            icon = Icons.Outlined.Layers,
                            title = "锁屏流体药丸 (灵犀一瞥 3.0)",
                            subtitle = if (pillRunning) "手电筒与相机之间固定 · 轻触展开大圆角卡片" else "在锁屏底部手电筒与相机正中间渲染实时气象与设备状态",
                            checked = pillRunning,
                            badge = if (pillRunning) "运行中" else "已就绪",
                            badgeColor = if (pillRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            onCheckedChange = { checked ->
                                if (!overlayGranted && checked) {
                                    onOpenOverlaySettings()
                                } else {
                                    onTogglePillService(checked)
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        M3ActionRow(
                            icon = Icons.Outlined.NearMe,
                            title = "端侧意图任意门 (44dp 宽热区)",
                            subtitle = if (portalRunning) "边缘把手就绪 · 豁免系统返回手势" else "屏幕右侧向内滑出半月弧形磁吸面板",
                            checked = portalRunning,
                            badge = if (portalRunning) "已待命" else "已就绪",
                            badgeColor = if (portalRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            onCheckedChange = { checked ->
                                if (!overlayGranted && checked) {
                                    onOpenOverlaySettings()
                                } else {
                                    onTogglePortalService(checked)
                                }
                            }
                        )
                    }
                }

                item {
                    M3GroupCard(title = "原生系统生态适配 (官方体系扩展)") {
                        M3ActionRow(
                            icon = Icons.Outlined.AutoAwesome,
                            title = "官方灵犀一瞥生态联动 (Smartspace)",
                            subtitle = "支持 Smartspace 目标扩展协议与官方 At a Glance 协同",
                            badge = "原生扩展",
                            badgeColor = MaterialTheme.colorScheme.tertiary,
                            showArrow = true,
                            onClick = {
                                activeDialogMessage = Pair("灵犀一瞥扩展说明", "我们严格拒绝在桌面绘制丑陋的伪劣小组件！而是采用 Android / Pixel 的原生 Smartspace Target 注入协议，为 Pixel 自带的 At a Glance 输送动态信息卡片。")
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        M3ActionRow(
                            icon = Icons.Outlined.Schedule,
                            title = "ClockFace 艺术时钟工坊",
                            subtitle = "自定义锁屏极简数字与流体指针样式",
                            badge = "规划中",
                            badgeColor = MaterialTheme.colorScheme.outline,
                            showArrow = true,
                            onClick = {
                                activeDialogMessage = Pair("ClockFace 规划中", "正在适配 Android 17 SystemUI 动态时钟扩展接口。")
                            }
                        )
                    }
                }
            } else {
                item {
                    M3GroupCard(title = "Tensor 硬件调度与特权控制") {
                        M3ActionRow(
                            icon = Icons.Outlined.Speed,
                            title = "强制 120Hz 极速锁频",
                            subtitle = if (hardwareState.is120HzLocked) "已锁定 peak=120, min=120" else "解除 Tensor G4 动态温控降频",
                            checked = hardwareState.is120HzLocked,
                            badge = if (shizukuState == ShizukuState.AUTHORIZED) "特权" else "需Shizuku",
                            badgeColor = if (shizukuState == ShizukuState.AUTHORIZED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            onCheckedChange = { checked ->
                                haptics.mechanicalRatchet()
                                coroutineScope.launch {
                                    hardware.toggleLock120Hz(checked)
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        M3ActionRow(
                            icon = Icons.Outlined.BatteryChargingFull,
                            title = "电池 80% 充电健康上限",
                            subtitle = if (hardwareState.isChargeLimit80Enabled) "已启用 charging_limit=80 保护" else "写入 global charging_limit 保护电池寿命",
                            checked = hardwareState.isChargeLimit80Enabled,
                            badge = "特权",
                            badgeColor = MaterialTheme.colorScheme.primary,
                            onCheckedChange = { checked ->
                                haptics.click()
                                coroutineScope.launch {
                                    hardware.toggleBatteryChargeLimit(checked)
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        M3ActionRow(
                            icon = Icons.Outlined.PictureInPicture,
                            title = "原生自由多小窗支持",
                            subtitle = if (freeformState.isEnabled) "系统级自由窗口支持已开启" else "开启 enable_freeform_support",
                            checked = freeformState.isEnabled,
                            badge = "特权",
                            badgeColor = MaterialTheme.colorScheme.primary,
                            onCheckedChange = { checked ->
                                haptics.click()
                                coroutineScope.launch {
                                    if (checked) freeform.enableFreeform()
                                }
                            }
                        )
                    }
                }

                item {
                    M3GroupCard(title = "硬件触觉与系统记录") {
                        M3ActionRow(
                            icon = Icons.Outlined.Vibration,
                            title = "机械触觉工坊 (Haptic Lab)",
                            subtitle = "轻触测试 Pixel 9 Pro XL 线性马达质感",
                            badge = "已接入",
                            badgeColor = MaterialTheme.colorScheme.primary,
                            showArrow = true,
                            onClick = {
                                haptics.mechanicalRatchet()
                                activeDialogMessage = Pair("触觉工坊反馈触发", "已通过 Android 12+ Composition 接口向 X 轴线性马达发送机械棘轮震动反馈。")
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        M3ActionRow(
                            icon = Icons.Outlined.VolumeUp,
                            title = "Sound Focus 多应用独立音量",
                            subtitle = "受限系统机制 (非公开 AOSP API)",
                            badge = "系统限制",
                            badgeColor = MaterialTheme.colorScheme.outline,
                            showArrow = true,
                            onClick = {
                                activeDialogMessage = Pair("Sound Focus 架构限制说明", "Android 音频服务 (AudioService/AudioTrack) 对第三方应用实行沙盒隔离，免 Root 环境下无法实现单应用分轨衰减。本应用坚持原则，不提供假开关。")
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    activeDialogMessage?.let { (title, msg) ->
        AlertDialog(
            onDismissRequest = { activeDialogMessage = null },
            title = { Text(text = title, fontWeight = FontWeight.Bold) },
            text = { Text(text = msg) },
            confirmButton = {
                TextButton(onClick = { activeDialogMessage = null }) {
                    Text("了解")
                }
            }
        )
    }
}

@Composable
fun PixelSystemStatusBanner(
    shizukuState: ShizukuState,
    overlayGranted: Boolean,
    notificationGranted: Boolean,
    onRequestShizuku: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantNotification: () -> Unit
) {
    val isShizukuReady = shizukuState == ShizukuState.AUTHORIZED
    val isOverlayReady = overlayGranted
    val isAllReady = isShizukuReady && isOverlayReady

    val containerColor = if (isAllReady) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
    }

    val contentColor = if (isAllReady) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isAllReady) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = if (isAllReady) "Android 17 特权环境已就绪" else "特权与悬浮窗状态待激活",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = buildString {
                            append("Shizuku: ${if (isShizukuReady) "已连接授权" else "未连接/未授权"}  |  ")
                            append("悬浮窗: ${if (isOverlayReady) "已就绪" else "未开启"}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.85f)
                    )
                }
            }

            if (!isAllReady) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!isShizukuReady) {
                        Button(
                            onClick = onRequestShizuku,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = contentColor,
                                contentColor = containerColor
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("激活 Shizuku", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    if (!isOverlayReady) {
                        OutlinedButton(onClick = onGrantOverlay) {
                            Text("开启悬浮窗", color = contentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M3GroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
        )
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun M3ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean? = null,
    badge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.secondary,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    badge?.let {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (checked != null && onCheckedChange != null) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else null
            )
        } else if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

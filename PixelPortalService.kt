package com.pixelmagic.modules.portal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.pixelmagic.core.theme.MonetColorProvider
import java.util.Collections

/**
 * PixelPortalService (端侧任意门 3.0 莫奈纯正版)
 * 1. 44dp 宽隐形触摸热区：调用 setSystemGestureExclusionRects，豁免系统侧滑返回拦截。
 * 2. M3E 半月弧形磁吸抽屉：完全贴合屏幕右边缘，绝不是居中小黑块！自动汲取壁纸莫奈色彩。
 * 3. 聚焦剪贴板突破：展开瞬间获得窗口焦点，合法读取剪贴板，支持地图导航、微信好友与系统分享。
 */
class PixelPortalService : Service() {

    private var windowManager: WindowManager? = null
    private var edgeHotspotView: FrameLayout? = null
    private var portalArcDrawer: FrameLayout? = null
    private var clipboardManager: ClipboardManager? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        setupEdgeHotspot()
    }

    private fun startAsForeground() {
        val channelId = "pixel_portal_fgs"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm != null && nm.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "Pixel 任意门服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "屏幕边缘智能意图路由与流转服务"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("Pixel 任意门已待命")
            .setContentText("屏幕右侧轻触或向内滑出即可流转意图")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1002, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupEdgeHotspot() {
        if (edgeHotspotView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density

        val params = WindowManager.LayoutParams(
            (44 * density).toInt(),
            (160 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        val primaryColor = MonetColorProvider.getPrimary(this)

        edgeHotspotView = FrameLayout(this).apply {
            val visibleHandle = View(context).apply {
                val lp = FrameLayout.LayoutParams((6 * density).toInt(), (80 * density).toInt()).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(
                        999f, 999f,
                        0f, 0f,
                        0f, 0f,
                        999f, 999f
                    )
                    setColor(primaryColor)
                }
            }
            addView(visibleHandle)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
                    val rect = Rect(0, 0, right - left, bottom - top)
                    v.systemGestureExclusionRects = Collections.singletonList(rect)
                }
            }

            setOnTouchListener(object : View.OnTouchListener {
                private var startX = 0f
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.rawX
                            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            val diffX = startX - event.rawX
                            if (diffX >= -5) {
                                openM3EArcDrawer()
                            }
                            return true
                        }
                    }
                    return false
                }
            })
        }

        try {
            windowManager?.addView(edgeHotspotView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openM3EArcDrawer() {
        if (portalArcDrawer != null) return

        val density = resources.displayMetrics.density
        val wm = windowManager ?: return

        val params = WindowManager.LayoutParams(
            (290 * density).toInt(),
            (380 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        val surfaceColor = MonetColorProvider.getSurfaceContainer(this)
        val outlineColor = MonetColorProvider.getOutlineColor(this, 120)

        portalArcDrawer = FrameLayout(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    42 * density, 42 * density,
                    0f, 0f,
                    0f, 0f,
                    42 * density, 42 * density
                )
                setColor(surfaceColor)
                setStroke(2, outlineColor)
            }
            setPadding((16 * density).toInt(), (20 * density).toInt(), (14 * density).toInt(), (20 * density).toInt())
        }

        try {
            wm.addView(portalArcDrawer, params)
            portalArcDrawer?.requestFocus()

            handler.postDelayed({
                var clipText = ""
                try {
                    val clip: ClipData? = clipboardManager?.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        clipText = clip.getItemAt(0).text?.toString() ?: ""
                    }
                } catch (_: Exception) {}

                buildArcDrawerItems(clipText)
            }, 60L)

            handler.postDelayed({ closeArcDrawer() }, 6500L)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildArcDrawerItems(text: String) {
        val drawer = portalArcDrawer ?: return
        val density = resources.displayMetrics.density
        drawer.removeAllViews()

        val rootLinear = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val primaryColor = MonetColorProvider.getPrimary(this)
        val cardInnerColor = MonetColorProvider.getSurfaceCard(this)

        val header = TextView(this).apply {
            this.text = "✨ 端侧意图任意门"
            setTextColor(primaryColor)
            textSize = 13f
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, (10 * density).toInt())
        }
        rootLinear.addView(header)

        if (text.isNotBlank()) {
            val previewBox = TextView(this).apply {
                this.text = "“${text.take(24)}${if (text.length > 24) "..." else ""}”"
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 14 * density
                    setColor(cardInnerColor)
                }
            }
            rootLinear.addView(previewBox)
        }

        val isAddress = text.contains("路") || text.contains("街") || text.contains("号") || text.contains("市")
        if (isAddress) {
            rootLinear.addView(createActionTile("地图路线导航", android.R.drawable.ic_dialog_map) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(text)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                closeArcDrawer()
            })
        }

        val isUrl = text.startsWith("http://") || text.startsWith("https://")
        if (isUrl) {
            rootLinear.addView(createActionTile("浏览器直达", android.R.drawable.ic_menu_search) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(text)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                closeArcDrawer()
            })
        }

        rootLinear.addView(createActionTile("微信好友流转", android.R.drawable.ic_menu_share) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text.ifBlank { "来自 Pixel Magic 的意图流转" })
                setPackage("com.tencent.mm")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { startActivity(intent) } catch (_: Exception) {}
            closeArcDrawer()
        })

        rootLinear.addView(createActionTile("系统分享面板", android.R.drawable.ic_menu_share) {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text.ifBlank { "来自 Pixel Magic" })
                type = "text/plain"
            }
            val chooser = Intent.createChooser(sendIntent, "任意门流转").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { startActivity(chooser) } catch (_: Exception) {}
            closeArcDrawer()
        })

        drawer.addView(rootLinear)
    }

    private fun createActionTile(label: String, iconRes: Int, onClick: () -> Unit): View {
        val density = resources.displayMetrics.density
        val cardInnerColor = MonetColorProvider.getSurfaceCard(this)
        val primaryColor = MonetColorProvider.getPrimary(this)

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, (8 * density).toInt(), 0, 0)
            }
            layoutParams = lp
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * density
                setColor(cardInnerColor)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }

            val iv = ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(primaryColor)
                layoutParams = LinearLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt())
            }
            val tv = TextView(context).apply {
                this.text = label
                setTextColor(Color.WHITE)
                textSize = 13f
                paint.isFakeBoldText = true
                setPadding((10 * density).toInt(), 0, 0, 0)
            }
            addView(iv)
            addView(tv)
        }
    }

    private fun closeArcDrawer() {
        portalArcDrawer?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            portalArcDrawer = null
        }
    }

    override fun onDestroy() {
        edgeHotspotView?.let { windowManager?.removeView(it) }
        closeArcDrawer()
        super.onDestroy()
    }
}

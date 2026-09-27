package com.pixelmagic.core.theme

import android.content.Context
import android.graphics.Color
import android.os.Build

object MonetColorProvider {

    fun getSurfaceContainer(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                context.getColor(android.R.color.system_neutral1_900)
            } catch (_: Exception) {
                Color.parseColor("#241421")
            }
        } else {
            Color.parseColor("#241421")
        }
    }

    fun getSurfaceCard(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                context.getColor(android.R.color.system_neutral1_800)
            } catch (_: Exception) {
                Color.parseColor("#361E32")
            }
        } else {
            Color.parseColor("#361E32")
        }
    }

    fun getPrimary(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                context.getColor(android.R.color.system_accent1_300)
            } catch (_: Exception) {
                Color.parseColor("#FF7BD7")
            }
        } else {
            Color.parseColor("#FF7BD7")
        }
    }

    fun getSecondary(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                context.getColor(android.R.color.system_accent2_200)
            } catch (_: Exception) {
                Color.parseColor("#FFD7F3")
            }
        } else {
            Color.parseColor("#FFD7F3")
        }
    }

    fun getOutlineColor(context: Context, alpha: Int = 120): Int {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                context.getColor(android.R.color.system_neutral2_300)
            } catch (_: Exception) {
                Color.parseColor("#FFD7F3")
            }
        } else {
            Color.parseColor("#FFD7F3")
        }
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }
}

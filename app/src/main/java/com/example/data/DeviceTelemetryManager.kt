package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs

data class DeviceTelemetryInfo(
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val availableRamMb: Long,
    val totalRamMb: Long,
    val freeStorageGb: Double,
    val totalStorageGb: Double,
    val networkType: String
)

object DeviceTelemetryManager {

    fun getDeviceTelemetry(context: Context): DeviceTelemetryInfo {
        // Battery info
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // Memory info
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)

        // Storage info
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        val totalBlocks = stat.blockCountLong

        val freeStorageGb = (availableBlocks * blockSize).toDouble() / (1024 * 1024 * 1024)
        val totalStorageGb = (totalBlocks * blockSize).toDouble() / (1024 * 1024 * 1024)

        // Network info
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connMgr.activeNetwork
        val caps = connMgr.getNetworkCapabilities(network)
        val netType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Disconnected"
        }

        return DeviceTelemetryInfo(
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            availableRamMb = availRamMb,
            totalRamMb = totalRamMb,
            freeStorageGb = String.format("%.2f", freeStorageGb).toDouble(),
            totalStorageGb = String.format("%.2f", totalStorageGb).toDouble(),
            networkType = netType
        )
    }
}

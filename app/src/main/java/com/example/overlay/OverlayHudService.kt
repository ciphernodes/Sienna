package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.theme.ClaudeAccentClay

class OverlayHudService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        startForegroundServiceNotification()
        setupOverlayView()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "sienna_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sienna Live Overlay HUD",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sienna Action Trace HUD")
            .setContentText("Overlay HUD active on top of applications.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1001, notification)
    }

    private fun setupOverlayView() {
        if (!OverlayHudManager.canDrawOverlay(this)) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayHudService)
            setViewTreeSavedStateRegistryOwner(this@OverlayHudService)
            setContent {
                val state by OverlayHudManager.hudState.collectAsState()
                OverlayHudContent(
                    state = state,
                    onClose = { stopSelf() },
                    onToggleMinimize = { OverlayHudManager.setMinimized(!state.isMinimized) }
                )
            }
        }

        // Enable touch dragging of floating HUD window
        composeView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(composeView, params)
                        return true
                    }
                }
                return false
            }
        })

        overlayView = composeView
        windowManager.addView(composeView, params)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayView?.let { windowManager.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun OverlayHudContent(
    state: OverlayState,
    onClose: () -> Unit,
    onToggleMinimize: () -> Unit
) {
    MaterialTheme {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1C1C1E).copy(alpha = 0.92f),
            shadowElevation = 8.dp,
            modifier = Modifier
                .width(if (state.isMinimized) 180.dp else 300.dp)
                .border(1.dp, ClaudeAccentClay.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (state.isExecuting) ClaudeAccentClay else Color.Gray,
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SIENNA LIVE TRACE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }

                    Row {
                        IconButton(onClick = onToggleMinimize, modifier = Modifier.size(22.dp)) {
                            Icon(
                                imageVector = if (state.isMinimized) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = "Minimize",
                                tint = Color.LightGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(22.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.LightGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Current Action Pill
                Text(
                    text = state.currentAction,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ClaudeAccentClay,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Expanded Trace Logs View
                AnimatedVisibility(visible = !state.isMinimized) {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 140.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(state.traceSteps.takeLast(6)) { step ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "> ",
                                        color = ClaudeAccentClay,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = step,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

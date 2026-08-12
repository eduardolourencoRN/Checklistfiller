package com.checklistfiller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingButtonService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var menu: View? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        bubble?.let { runCatching { wm.removeView(it) } }
        menu?.let { runCatching { wm.removeView(it) } }
        bubble = null; menu = null
        super.onDestroy()
    }

    private fun addBubble() {
        val btn = Button(this).apply {
            text = "OK"
            setTextColor(Color.WHITE)
            textSize = 16f
            setBackgroundColor(Color.parseColor("#0A7E3E"))
        }
        params = WindowManager.LayoutParams(
            dp(56), dp(56), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12); y = dp(200)
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        btn.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    params.x = startX + dx; params.y = startY + dy
                    bubble?.let { wm.updateViewLayout(it, params) }; true
                }
                MotionEvent.ACTION_UP -> { if (!moved) toggleMenu(); true }
                else -> false
            }
        }
        bubble = btn
        wm.addView(btn, params)
    }

    private fun toggleMenu() {
        if (menu != null) { removeMenu(); return }
        val svc = FillAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(this, "Ative a Acessibilidade primeiro", Toast.LENGTH_LONG).show(); return
        }
        val templates = TemplateStore.load(this)
        if (templates.isEmpty()) {
            Toast.makeText(this, "Nenhum template salvo", Toast.LENGTH_LONG).show(); return
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        container.addView(TextView(this).apply {
            text = "Preencher com:"
            setTextColor(Color.parseColor("#333333"))
            setPadding(dp(8), dp(4), dp(8), dp(8))
        })
        val currentPkg = svc.currentPackage()
        templates.forEach { tpl ->
            container.addView(Button(this).apply {
                text = tpl.name
                isAllCaps = false
                setOnClickListener {
                    removeMenu()
                    if (tpl.targetPackage.isNotBlank() && currentPkg.isNotBlank()
                        && tpl.targetPackage != currentPkg) {
                        Toast.makeText(this@FloatingButtonService,
                            "Aviso: template de ${tpl.targetPackage}, tela atual $currentPkg",
                            Toast.LENGTH_SHORT).show()
                    }
                    FillAccessibilityService.instance?.fill(tpl)
                }
            })
        }
        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = params.x; y = params.y + dp(60)
        }
        menu = container
        wm.addView(container, menuParams)
    }

    private fun removeMenu() {
        menu?.let { runCatching { wm.removeView(it) } }
        menu = null
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun startInForeground() {
        val channelId = "checklist_filler"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Checklist Filler", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notif: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Botao de preenchimento ativo")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .build()
        startForeground(1, notif)
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
        ).toInt()
}

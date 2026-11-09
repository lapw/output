package com.example.floatbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * 核心無障礙服務：
 * - 啟動時在 TYPE_ACCESSIBILITY_OVERLAY 層掛一條可拖動浮動條（免 SYSTEM_ALERT_WINDOW 權限）
 * - 按鈕：▶ Play / ⏸ Pause / ⏹ Stop / 📷 截圖 / 📜 選腳本
 * - 截圖：Android 11+ 用 takeScreenshot()（無需 MediaProjection）
 */
class BotAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "FloatBot"
        @Volatile var instance: BotAccessibilityService? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var bar: LinearLayout
    private lateinit var wm: WindowManager
    private var barAdded = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addFloatingBar()
        Log.i(TAG, "FloatBot service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        removeBar()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeBar()
    }

    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    /** 所有 toast 統一 post 回主線程（腳本執行緒沒有 Looper，直接 Toast 會崩） */
    fun toast(msg: String) = mainHandler.post {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---------------- 浮動條 ----------------

    private fun addFloatingBar() {
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xE622222222.toInt())
            setPadding(12, 6, 12, 6)
            elevation = 12f
        }

        fun btn(label: String, onClick: () -> Unit): Button =
            Button(this).apply {
                text = label
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0x00000000)
                setOnClickListener { onClick() }
            }

        bar.addView(btn("▶") { ScriptRunner.start(this) })
        bar.addView(btn("⏸") { ScriptRunner.pause(this) })
        bar.addView(btn("⏹") { ScriptRunner.stop(this) })
        bar.addView(btn("📷") { takeShot() })
        bar.addView(btn("📜") { pickScript() })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        // 拖動浮條本體空白處；按鈕自身的事件不經過這裡，互不干擾
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        bar.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startX = params.x; startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(ev.rawX - downX) > 4 || abs(ev.rawY - downY) > 4) moved = true
                    if (moved) {
                        params.x = startX + (ev.rawX - downX).toInt()
                        params.y = startY + (ev.rawY - downY).toInt()
                        wm.updateViewLayout(bar, params)
                    }
                    true
                }
                else -> false
            }
        }

        wm.addView(bar, params)
        barAdded = true
    }

    private fun removeBar() {
        if (barAdded) {
            runCatching { wm.removeView(bar) }
            barAdded = false
        }
    }

    // ---------------- 截圖（Android 11+，無障礙自帶能力） ----------------

    private fun takeShot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        val copy = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        screenshot.hardwareBuffer.close()
                        saveShot(copy)
                    }
                    override fun onFailure(errorCode: Int) {
                        toast("截圖失敗 code=$errorCode")
                    }
                })
        } else {
            toast("需 Android 11+；舊版截圖要另接 MediaProjection（下一步再加）")
        }
    }

    private fun saveShot(bmp: Bitmap?) {
        if (bmp == null) return
        // App 專屬目錄免儲存權限；要用檔案管理員看：/sdcard/Android/data/com.example.floatbot/files/Pictures/FloatBot
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "FloatBot")
        dir.mkdirs()
        val f = File(dir, "shot_${System.currentTimeMillis()}.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        toast("已截圖：${f.name}")
    }

    // ---------------- 腳本選擇（浮動 Dialog 掛在無障礙層） ----------------

    private fun pickScript() {
        val list = assets.list("scripts")?.sorted() ?: emptyList()
        if (list.isEmpty()) {
            toast("assets/scripts 內沒有腳本")
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("選擇腳本")
            .setItems(list.toTypedArray()) { _, i -> ScriptRunner.start(this, list[i]) }
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        dialog.show()
    }

    // ---------------- 給腳本 Bridge 用的底層能力 ----------------

    fun tap(x: Float, y: Float) = gesture(60, x, y, x, y)

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long = 300) = gesture(ms, x1, y1, x2, y2)

    private fun gesture(ms: Long, x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, ms))
                .build(),
            null, null
        )
    }

    /** 回傳第一個包含 text 的控件中心點（基於控件比硬編座標穩，跨解析度不會跑位） */
    fun findNodeCenter(text: String): Pair<Float, Float>? {
        val nodes: List<AccessibilityNodeInfo> =
            rootInActiveWindow?.findAccessibilityNodeInfosByText(text) ?: return null
        for (n in nodes) {
            val r = Rect()
            n.getBoundsInScreen(r)
            if (!r.isEmpty) return r.centerX().toFloat() to r.centerY().toFloat()
        }
        return null
    }
}

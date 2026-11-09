package com.example.floatbot

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 主頁：模式如同一般 App。三步：開無障礙 → (備用)懸浮窗權限 → 啟動浮動條。
 * 浮動條本身由 BotAccessibilityService 掛在無障礙 overlay 層，免 SYSTEM_ALERT_WINDOW。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            // 先確認無障礙服務已開，才啟動浮動條
            if (!isAccessibilityOn()) {
                statusText.text = "狀態：請先開啟無障礙服務"
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                statusText.text = "狀態：服務運行中，浮動條已顯示"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        statusText.text = if (isAccessibilityOn()) "狀態：服務運行中" else "狀態：未啟動"
    }

    private fun isAccessibilityOn(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}

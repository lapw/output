package com.example.floatbot

import android.util.Log
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rhino 腳本執行器：單一背景執行緒跑腳本；狀態機 IDLE / RUNNING / PAUSED。
 * 腳本內透過全域物件 bot 呼叫原生能力（Rhino LiveConnect 自動包裝 Kotlin 物件）。
 */
object ScriptRunner {

    enum class State { IDLE, RUNNING, PAUSED }

    @Volatile var state: State = State.IDLE
        private set

    private val executor = Executors.newSingleThreadExecutor()
    private val paused = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null

    fun start(svc: BotAccessibilityService, scriptName: String = "demo_script.js") {
        if (state == State.RUNNING) {
            svc.toast("已在運行，請先 ⏹ 停止")
            return
        }
        paused.set(false)
        state = State.RUNNING
        executor.execute {
            worker = Thread.currentThread()
            val cx = RhinoContext.enter()
            try {
                cx.optimizationLevel = -1  // Android（無 JIT class loader）必須 -1 直譯模式
                val src = loadScript(svc, scriptName)
                val scope = cx.initStandardObjects()
                ScriptableObject.putProperty(scope, "bot", RhinoContext.javaToJS(Bridge(svc), scope))
                cx.evaluateString(scope, src, scriptName, 1, null)
                svc.toast("腳本完成")
            } catch (e: InterruptedException) {
                svc.toast("腳本已停止")
            } catch (e: Throwable) {
                Log.e(BotAccessibilityService.TAG, "script error", e)
                svc.toast("腳本錯誤：${e.message}")
            } finally {
                RhinoContext.exit()
                worker = null
                state = State.IDLE
            }
        }
    }

    /** ⏸：暫停 / 繼續 toggle。腳本每次呼叫 bot.* 前都會在 gate() 檢查，長迴圈也有效 */
    fun pause(svc: BotAccessibilityService) {
        paused.set(!paused.get())
        svc.toast(if (paused.get()) "已暫停" else "繼續")
    }

    /** ⏹：中斷執行緒；卡在 bot.sleep() 會立刻丟 InterruptedException */
    fun stop(svc: BotAccessibilityService) {
        paused.set(false)
        worker?.interrupt()
        if (state == State.PAUSED) state = State.IDLE
        svc.toast("已停止")
    }

    private fun gate() {
        while (paused.get()) Thread.sleep(100)
        if (Thread.interrupted()) throw InterruptedException("stopped")
    }

    /** 腳本載入順序：App 專屬 scripts 目錄（adb push 可寫、免權限）→ assets/scripts */
    private fun loadScript(ctx: android.content.Context, name: String): String {
        val alt = java.io.File(ctx.getExternalFilesDir(null), "scripts/$name")
        if (alt.exists()) return alt.readText()
        return ctx.assets.open("scripts/$name").bufferedReader().use { it.readText() }
    }

    /**
     * Bridge：腳本裡的 `bot` 物件。
     * JS 範例：bot.toast("hi"); bot.click(500, 1200); bot.sleep(800);
     *          if (bot.existsText("下一步")) bot.tapText("下一步");
     */
    class Bridge(private val svc: BotAccessibilityService) {

        fun click(x: Double, y: Double) {
            gate()
            svc.tap(x.toFloat(), y.toFloat())
        }

        fun swipe(x1: Double, y1: Double, x2: Double, y2: Double, ms: Double = 300.0) {
            gate()
            svc.swipe(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), ms.toLong())
        }

        /** 依畫面上可見文字找控件並點擊；回傳是否找到 */
        fun tapText(text: String): Boolean {
            gate()
            val c = svc.findNodeCenter(text) ?: return false
            svc.tap(c.first, c.second)
            return true
        }

        fun existsText(text: String): Boolean {
            gate()
            return svc.findNodeCenter(text) != null
        }

        fun sleep(ms: Double) {
            gate()
            Thread.sleep(ms.toLong())
        }

        fun toast(msg: String) = svc.toast(msg)

        fun log(msg: String) = Log.d(BotAccessibilityService.TAG, msg)

        fun screenW(): Int = svc.resources.displayMetrics.widthPixels
        fun screenH(): Int = svc.resources.displayMetrics.heightPixels
    }
}

package com.backrooms.cardjourney

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import kotlin.math.min

/**
 * 主菜单 / 选项页：与 PC 端同一套手绘风格（走廊背景 + 暗色面板 + 琥珀描边按钮）。
 * 不再使用原生控件，因此不会出现原生菜单与 PC 端风格不一致的问题。
 */
class CanvasMenuView(ctx: Context, private val host: Host) : BackroomsBackgroundView(ctx) {

    interface Host {
        fun menuStartGame()
        fun menuContinueGame()
        fun menuOptions()
        fun menuExitApp()
        fun musicVolume(): Int
        fun setMusicVolume(v: Int)
        fun soundVolume(): Int
        fun setSoundVolume(v: Int)
        fun brightness(): Int
        fun setBrightness(v: Int)
    }

    var optionsOpen = false
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
    }

    private val amber = Color.rgb(232, 197, 71)
    private val txt = Color.rgb(237, 237, 237)
    private val dim = Color.rgb(143, 143, 143)

    private var dragging = -1

    init {
        setScene(0, 0)
    }

    private fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity

    private fun panel(): RectF {
        val pw = min(width - sp(28f), sp(330f))
        val ph = min(height - sp(60f), sp(330f))
        return RectF((width - pw) / 2f, (height - ph) / 2f, (width + pw) / 2f, (height + ph) / 2f)
    }

    private fun sliderTrack(i: Int): RectF {
        val r = panel()
        val top = r.top + sp(66f) + i * sp(52f)
        return RectF(r.left + sp(26f), top + sp(14f), r.right - sp(26f), top + sp(26f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 主菜单：压暗场景，标题 + 按钮列
        paint.color = Color.argb(150, 8, 8, 8)
        canvas.drawRect(0f, 0f, w, h, paint)

        bold.textSize = sp(30f)
        bold.color = amber
        val title = "后室卡牌之旅"
        canvas.drawText(title, sp(28f), sp(86f), bold)

        text.textSize = sp(10.5f)
        text.color = dim
        canvas.drawText("BACKROOMS CARD JOURNEY", sp(30f), sp(104f), text)

        val items = listOf("开始", "继续", "选项", "退出")
        var y = h * 0.5f - sp(52f)
        for (i in items.indices) {
            val br = RectF(sp(24f), y, sp(24f) + sp(180f), y + sp(40f))
            paint.color = Color.argb(210, 20, 20, 20)
            canvas.drawRoundRect(br, sp(6f), sp(6f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = sp(1.2f)
            paint.color = Color.argb(170, 232, 197, 71)
            canvas.drawRoundRect(br, sp(6f), sp(6f), paint)
            paint.style = Paint.Style.FILL
            bold.textSize = sp(13f)
            bold.color = txt
            canvas.drawText(items[i], br.left + sp(18f), br.centerY() + sp(4.5f), bold)
            y += sp(50f)
        }

        text.textSize = sp(9.5f)
        text.color = dim
        canvas.drawText("版本 v" + APP_VERSION + "  ·  测试版", sp(26f), h - sp(14f), text)

        if (optionsOpen) drawOptions(canvas, w, h)
    }

    private fun drawOptions(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(185, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paint)
        val r = panel()
        paint.color = Color.argb(246, 18, 18, 18)
        canvas.drawRoundRect(r, sp(10f), sp(10f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sp(1.3f)
        paint.color = Color.argb(180, 232, 197, 71)
        canvas.drawRoundRect(r, sp(10f), sp(10f), paint)
        paint.style = Paint.Style.FILL

        bold.textSize = sp(17f)
        bold.color = amber
        canvas.drawText("选项", r.left + sp(20f), r.top + sp(34f), bold)

        val labels = listOf("音乐音量", "音效音量", "界面亮度")
        val values = listOf(host.musicVolume(), host.soundVolume(), host.brightness())
        for (i in 0 until 3) {
            text.textSize = sp(10.5f)
            text.color = txt
            val tr = sliderTrack(i)
            canvas.drawText(labels[i], tr.left, tr.top - sp(7f), text)
            bold.textSize = sp(10.5f)
            bold.color = amber
            canvas.drawText(values[i].toString(), tr.right - bold.measureText(values[i].toString()), tr.top - sp(7f), bold)
            paint.color = Color.argb(255, 46, 46, 46)
            canvas.drawRoundRect(tr, sp(6f), sp(6f), paint)
            paint.color = Color.argb(255, 232, 197, 71)
            canvas.drawRoundRect(RectF(tr.left, tr.top, tr.left + tr.width() * values[i] / 100f, tr.bottom), sp(6f), sp(6f), paint)
            val kx = tr.left + tr.width() * values[i] / 100f
            paint.color = Color.rgb(255, 236, 160)
            canvas.drawCircle(kx, tr.centerY(), sp(6f), paint)
        }

        val back = RectF(r.right - sp(104f), r.bottom - sp(46f), r.right - sp(18f), r.bottom - sp(14f))
        paint.color = Color.argb(220, 26, 26, 26)
        canvas.drawRoundRect(back, sp(6f), sp(6f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sp(1.2f)
        paint.color = Color.argb(170, 232, 197, 71)
        canvas.drawRoundRect(back, sp(6f), sp(6f), paint)
        paint.style = Paint.Style.FILL
        bold.textSize = sp(11f)
        bold.color = txt
        canvas.drawText("返回", back.centerX() - bold.measureText("返回") / 2f, back.centerY() + sp(4f), bold)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y
        if (optionsOpen) {
            val r = panel()
            if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                for (i in 0 until 3) {
                    val tr = sliderTrack(i)
                    if (y >= tr.top - sp(14f) && y <= tr.bottom + sp(16f) && x >= tr.left - sp(12f) && x <= tr.right + sp(12f)) {
                        if (e.action == MotionEvent.ACTION_DOWN) dragging = i
                        if (dragging == i) {
                            val v = ((x - tr.left) / tr.width() * 100f).toInt().coerceIn(0, 100)
                            when (i) {
                                0 -> host.setMusicVolume(v)
                                1 -> host.setSoundVolume(v)
                                else -> host.setBrightness(v)
                            }
                            invalidate()
                        }
                        return true
                    }
                }
            }
            if (e.action == MotionEvent.ACTION_UP) dragging = -1
            if (e.action == MotionEvent.ACTION_DOWN) {
                val back = RectF(r.right - sp(104f), r.bottom - sp(46f), r.right - sp(18f), r.bottom - sp(14f))
                if (x >= back.left && x <= back.right && y >= back.top && y <= back.bottom) {
                    optionsOpen = false
                    invalidate()
                }
            }
            return true
        }

        if (e.action != MotionEvent.ACTION_DOWN) return true
        var top = height.toFloat() * 0.5f - sp(52f)
        val order = listOf("start", "cont", "opts", "exit")
        for (k in order) {
            val br = RectF(sp(24f), top, sp(24f) + sp(180f), top + sp(40f))
            if (x >= br.left && x <= br.right && y >= br.top && y <= br.bottom) {
                when (k) {
                    "start" -> host.menuStartGame()
                    "cont" -> host.menuContinueGame()
                    "opts" -> optionsOpen = true
                    "exit" -> host.menuExitApp()
                }
                invalidate()
                return true
            }
            top += sp(50f)
        }
        return true
    }
}

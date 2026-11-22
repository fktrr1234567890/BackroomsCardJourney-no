package com.backrooms.cardjourney

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.abs

/**
 * 手绘的后室场景：透视走廊、墙面拼板、天花板灯组（会闪）、地砖、管道、
 * 裂缝、水渍、门框与出口指示牌，再叠一层颗粒、扫描线和暗角。
 * 按 Stage.theme 换配色，不再是一张两色渐变。
 */
class BackroomsBackgroundView(context: Context) : View(context) {

    private var theme = 0
    private var sceneSeed = 0L
    private var stageIndex = 0

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private data class Stain(val x: Float, val y: Float, val r: Float, val a: Int)
    private data class Lamp(val y: Float, val w: Float, val flicker: Float)
    private data class Scratch(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val a: Int)

    private val stains = ArrayList<Stain>()
    private val lamps = ArrayList<Lamp>()
    private val scratches = ArrayList<Scratch>()

    private var wallTop = 0xFFE8DCB0.toInt()
    private var wallBottom = 0xFFBFAE79.toInt()
    private var floorColor = 0xFF7A6A4A.toInt()
    private var ceilColor = 0xFFDCD2A6.toInt()
    private var lightColor = 0xFFFFF6D0.toInt()
    private var accent = 0xFFE8C547.toInt()

    fun setScene(themeIdx: Int, stageIdx: Int) {
        theme = themeIdx
        stageIndex = stageIdx
        sceneSeed = (themeIdx * 1000L + stageIdx * 7L + 17L)
        applyPalette()
        rebuildProps()
        invalidate()
    }

    private fun applyPalette() {
        when (theme) {
            0 -> { // 黄色走廊
                wallTop = 0xFFE9DFB4.toInt(); wallBottom = 0xFFB8A771.toInt()
                floorColor = 0xFF6E5F42.toInt(); ceilColor = 0xFFDED19E.toInt()
                lightColor = 0xFFFFF7D6.toInt(); accent = 0xFFE8C547.toInt()
            }
            1 -> { // 工业灰
                wallTop = 0xFF9AA0A3.toInt(); wallBottom = 0xFF5E6366.toInt()
                floorColor = 0xFF42474A.toInt(); ceilColor = 0xFF7C8285.toInt()
                lightColor = 0xFFDDF3FF.toInt(); accent = 0xFF5AA8E0.toInt()
            }
            2 -> { // 黑暗
                wallTop = 0xFF1A1A1C.toInt(); wallBottom = 0xFF0A0A0B.toInt()
                floorColor = 0xFF101011.toInt(); ceilColor = 0xFF141416.toInt()
                lightColor = 0xFF6E7A86.toInt(); accent = 0xFF8A2BE2.toInt()
            }
            3 -> { // 冷色办公
                wallTop = 0xFFD7E0E3.toInt(); wallBottom = 0xFF9AA7AC.toInt()
                floorColor = 0xFF5D6B70.toInt(); ceilColor = 0xFFC6D2D6.toInt()
                lightColor = 0xFFEFFBFF.toInt(); accent = 0xFF7FD1C7.toInt()
            }
            4 -> { // 血肉有机
                wallTop = 0xFF7E4A3F.toInt(); wallBottom = 0xFF452520.toInt()
                floorColor = 0xFF33191A.toInt(); ceilColor = 0xFF5E2C28.toInt()
                lightColor = 0xFFFF9C8A.toInt(); accent = 0xFFE05252.toInt()
            }
            else -> { // 紫色虚空
                wallTop = 0xFF3A2550.toInt(); wallBottom = 0xFF1A0F26.toInt()
                floorColor = 0xFF120A1C.toInt(); ceilColor = 0xFF241535.toInt()
                lightColor = 0xFFD8A8FF.toInt(); accent = 0xFFB06BE0.toInt()
            }
        }
    }

    private fun rebuildProps() {
        stains.clear(); lamps.clear(); scratches.clear()
        val rand = java.util.Random(sceneSeed)
        // 墙面污渍
        repeat(9) {
            stains.add(Stain(rand.nextFloat(), 0.18f + rand.nextFloat() * 0.55f, 0.02f + rand.nextFloat() * 0.07f,
                20 + rand.nextInt(50)))
        }
        // 天花板灯组
        val lampCount = if (theme == 2) 2 else 4
        for (i in 0 until lampCount) {
            lamps.add(Lamp(0.10f + i * 0.12f, 0.10f + rand.nextFloat() * 0.10f, rand.nextFloat()))
        }
        // 划痕
        repeat(14) {
            scratches.add(Scratch(rand.nextFloat(), rand.nextFloat() * 0.55f,
                rand.nextFloat(), rand.nextFloat() * 0.65f, 18 + rand.nextInt(40)))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildProps()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val horizon = h * 0.58f
        val vx = w * 0.5f
        val t = System.currentTimeMillis()

        // 1. 整体底色
        p.shader = LinearGradient(0f, 0f, 0f, h, wallTop, wallBottom, Shader.TileMode.CLAMP)
        p.alpha = 255
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // 2. 天花板（向灭点收窄）
        path.reset()
        path.moveTo(0f, 0f)
        path.lineTo(w, 0f)
        path.lineTo(vx + w * 0.10f, horizon)
        path.lineTo(vx - w * 0.10f, horizon)
        path.close()
        p.color = ceilColor
        canvas.drawPath(path, p)

        // 3. 地板
        path.reset()
        path.moveTo(0f, h)
        path.lineTo(w, h)
        path.lineTo(vx + w * 0.13f, horizon)
        path.lineTo(vx - w * 0.13f, horizon)
        path.close()
        p.color = floorColor
        canvas.drawPath(path, p)

        // 4. 两侧墙
        path.reset()
        path.moveTo(0f, 0f); path.lineTo(vx - w * 0.10f, horizon); path.lineTo(vx - w * 0.13f, horizon); path.lineTo(0f, h); path.close()
        p.color = wallBottom
        canvas.drawPath(path, p)
        path.reset()
        path.moveTo(w, 0f); path.lineTo(vx + w * 0.10f, horizon); path.lineTo(vx + w * 0.13f, horizon); path.lineTo(w, h); path.close()
        canvas.drawPath(path, p)

        // 5. 墙面拼板（竖向接缝 + 横向腰线）
        p.color = darken(wallBottom, 0.35f)
        p.strokeWidth = 1.5f
        p.alpha = 130
        for (i in 1..5) {
            val fx = i / 6f
            canvas.drawLine(0f, 0f, vx - w * 0.10f + (vx - w * 0.10f) * (1f - fx) * 0.0f, horizon, p)
        }
        // 用透视线替代：从走廊两侧边沿射向灭点
        for (i in 1..6) {
            val yy = horizon - (horizon / 7f) * i
            val lx = (w * 0.5f - w * 0.5f * (i / 7f))
            canvas.drawLine(lx, yy, vx, horizon, p)
            canvas.drawLine(w - lx, yy, vx, horizon, p)
        }
        p.alpha = 255

        // 6. 地砖透视线
        p.color = darken(floorColor, 0.5f)
        p.alpha = 90
        for (i in 1..9) {
            val k = (i / 10f)
            val yy = horizon + (h - horizon) * (k * k)
            canvas.drawLine(0f, yy, w, yy, p)
        }
        val floorBottomW = w * 0.26f
        for (i in -4..4) {
            val xBottom = vx + floorBottomW * i / 4f
            val xTop = vx + (w * 0.13f) * i / 4f
            canvas.drawLine(xBottom, h, xTop, horizon, p)
        }
        p.alpha = 255

        // 7. 天花板灯组 + 光晕（带闪烁）
        for (lamp in lamps) {
            val ly = h * lamp.y
            val lw = w * lamp.w
            val flick = 0.55f + 0.45f * abs(sin((t / 380.0) + lamp.flicker * 6.0))
            // 灯管
            p.color = lightColor
            p.alpha = (140 + 100 * flick).toInt().coerceIn(0, 255)
            val r = RectF(vx - lw / 2f, ly, vx + lw / 2f, ly + h * 0.018f)
            canvas.drawRoundRect(r, 4f, 4f, p)
            // 光锥
            p.color = lightColor
            p.alpha = (34 * flick).toInt().coerceIn(0, 255)
            path.reset()
            path.moveTo(vx - lw / 2f, ly + h * 0.018f)
            path.lineTo(vx + lw / 2f, ly + h * 0.018f)
            path.lineTo(vx + lw, horizon + h * 0.18f)
            path.lineTo(vx - lw, horizon + h * 0.18f)
            path.close()
            canvas.drawPath(path, p)
        }
        p.alpha = 255

        // 8. 墙面污渍
        for (s in stains) {
            p.color = darken(wallBottom, 0.15f)
            p.alpha = s.a
            val cx = s.x * w
            val cy = s.y * h
            canvas.drawOval(RectF(cx - s.r * w, cy - s.r * w * 1.8f, cx + s.r * w, cy + s.r * w * 1.8f), p)
        }
        p.alpha = 255

        // 9. 管道与线缆（工业/虚空主题更明显）
        if (theme == 1 || theme == 5) {
            p.color = darken(wallBottom, 0.6f)
            p.alpha = 170
            p.strokeWidth = w * 0.012f
            canvas.drawLine(w * 0.06f, 0f, w * 0.06f, horizon + h * 0.1f, p)
            canvas.drawLine(w * 0.10f, 0f, w * 0.10f, horizon + h * 0.05f, p)
            p.strokeWidth = w * 0.005f
            canvas.drawLine(w * 0.94f, h * 0.04f, w * 0.86f, horizon - h * 0.06f, p)
            p.alpha = 255
        }

        // 10. 尽头的门 / 出口牌
        val doorW = w * 0.07f
        val doorH = h * 0.13f
        p.color = darken(ceilColor, 0.75f)
        canvas.drawRoundRect(RectF(vx - doorW / 2f, horizon - doorH, vx + doorW / 2f, horizon), 2f, 2f, p)
        p.color = lightColor
        p.alpha = 90 + (60 * abs(sin(t / 900.0))).toInt()
        canvas.drawRect(vx - doorW * 0.3f, horizon - doorH * 0.9f, vx + doorW * 0.3f, horizon - doorH * 0.55f, p)
        p.alpha = 255

        // 11. 划痕与裂缝
        for (s in scratches) {
            p.color = darken(wallBottom, 0.7f)
            p.alpha = s.a
            p.strokeWidth = 1f
            canvas.drawLine(s.x1 * w, s.y1 * h, s.x2 * w, s.y2 * h, p)
        }
        p.alpha = 255

        // 12. 颗粒噪点（固定种子，不会每帧跳）
        val rand = java.util.Random(sceneSeed + 99)
        p.color = Color.WHITE
        p.alpha = 14
        repeat(260) {
            val gx = rand.nextFloat() * w
            val gy = rand.nextFloat() * h
            canvas.drawCircle(gx, gy, 0.8f, p)
        }
        p.alpha = 255

        // 13. 扫描线
        p.color = Color.BLACK
        p.alpha = 10
        var y = 0f
        while (y < h) {
            canvas.drawRect(0f, y, w, y + 1f, p)
            y += 4f
        }
        p.alpha = 255

        // 14. 暗角
        p.shader = RadialGradient(vx, h * 0.45f, w * 0.85f,
            intArrayOf(0x00000000, 0x22000000, 0x99000000.toInt()), floatArrayOf(0.55f, 0.82f, 1.0f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // 15. 走廊尽头的一层薄雾
        p.color = lightColor
        p.alpha = if (theme == 2) 12 else 26
        path.reset()
        path.moveTo(vx - w * 0.13f, horizon)
        path.lineTo(vx + w * 0.13f, horizon)
        path.lineTo(vx + w * 0.22f, horizon + h * 0.10f)
        path.lineTo(vx - w * 0.22f, horizon + h * 0.10f)
        path.close()
        canvas.drawPath(path, p)
        p.alpha = 255
    }

    private fun sin(v: Double): Double = Math.sin(v)

    private fun darken(color: Int, factor: Float): Int {
        val r = ((color shr 16 and 0xFF) * (1f - factor)).toInt().coerceIn(0, 255)
        val g = ((color shr 8 and 0xFF) * (1f - factor)).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * (1f - factor)).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}

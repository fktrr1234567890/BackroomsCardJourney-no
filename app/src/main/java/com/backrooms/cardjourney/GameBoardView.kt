package com.backrooms.cardjourney

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/**
 * 安卓端对局画面：与桌面版同一套手绘语言——透视走廊背景、顶部状态 chips、
 * 中部怪物剪影、事件条、底部扇形手牌、左下背包图标、右下结束回合。
 * 全部自绘，不再使用原生控件，从而与 PC 端布局保持一致。
 */
class GameBoardView(
    context: Context,
    private val engine: GameEngine,
    private val host: Host
) : BackroomsBackgroundView(context) {

    interface Host {
        fun boardAfterAction()
        fun boardEndTurn()
        fun boardRestart()
        fun boardOptions()
        fun boardExitToMenu()
        fun boardAdminPassword()
        fun boardUseItem(index: Int)
        fun isAdmin(): Boolean
        fun musicVolume(): Int
        fun setMusicVolume(v: Int)
        fun soundVolume(): Int
        fun setSoundVolume(v: Int)
        fun brightnessValue(): Int
        fun setBrightness(v: Int)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans", Typeface.NORMAL)
    }
    private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans", Typeface.BOLD)
    }

    private var menuOpen = false
    private var optionsOpen = false
    private var optDrag = -1
    private var bagOpen = false
    private var verTaps = 0
    private var hint = ""

    private val amber = Color.parseColor("#E8C547")
    private val dim = Color.parseColor("#8F8F8F")
    private val txt = Color.parseColor("#EDEDED")
    private val red = Color.parseColor("#E05252")
    private val purple = Color.parseColor("#B06BE0")
    private val blue = Color.parseColor("#5AA8E0")
    private val green = Color.parseColor("#66BB6A")

    init {
        isClickable = true
        isFocusable = true
    }

    fun refresh() {
        invalidate()
    }

    fun closeOverlays() {
        menuOpen = false
        bagOpen = false
        optionsOpen = false
        invalidate()
    }

    fun openMenu() {
        menuOpen = true
        invalidate()
    }

    private fun sp(v: Float): Float = v * resources.displayMetrics.density

    // ---------- 绘制 ----------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 顶部极淡渐变，压住状态字；底部完全透明（不再有黑边）
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, sp(64f),
            intArrayOf(Color.argb(70, 0, 0, 0), Color.argb(0, 0, 0, 0)), null,
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, sp(64f), paint)
        paint.shader = null

        drawHud(canvas, w, h)
        drawMonsters(canvas, w, h)
        drawEventBar(canvas, w, h)
        drawHand(canvas, w, h)
        drawBottomBar(canvas, w, h)

        if (bagOpen) drawBag(canvas, w, h)
        if (menuOpen) drawMenu(canvas, w, h)
        if (optionsOpen) drawOptions(canvas, w, h)
    }

    private fun chip(canvas: Canvas, x: Float, y: Float, label: String, color: Int): Float {
        text.textSize = sp(11f)
        text.color = color
        val tw = text.measureText(label)
        val r = RectF(x, y, x + tw + sp(18f), y + sp(20f))
        paint.color = Color.argb(135, 12, 12, 12)
        canvas.drawRoundRect(r, sp(10f), sp(10f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sp(1f)
        paint.color = color
        canvas.drawRoundRect(r, sp(10f), sp(10f), paint)
        paint.style = Paint.Style.FILL
        canvas.drawText(label, x + sp(9f), y + sp(14f), text)
        return r.right
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        // 左上菜单
        val mr = RectF(sp(12f), sp(12f), sp(78f), sp(38f))
        paint.color = Color.argb(200, 24, 24, 24)
        canvas.drawRoundRect(mr, sp(8f), sp(8f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sp(1.2f)
        paint.color = Color.argb(170, 232, 197, 71)
        canvas.drawRoundRect(mr, sp(8f), sp(8f), paint)
        paint.style = Paint.Style.FILL
        text.textSize = sp(12f)
        text.color = txt
        canvas.drawText("菜单", mr.left + sp(20f), mr.centerY() + sp(4f), text)

        // 右上轮回/层级
        bold.textSize = sp(13f)
        bold.color = amber
        val round = if (engine.infiniteMode) "第 ${engine.score + 1} 轮回" else "第 ${engine.levelIndex + 1}/30 层"
        canvas.drawText(round, w - sp(16f) - bold.measureText(round), sp(28f), bold)
        text.textSize = sp(9f)
        text.color = Color.parseColor("#B0B0B0")
        val sub = if (engine.stage.isSpecialStage) "特殊层级 " + engine.stage.name
        else if (engine.infiniteMode) "最高通 ${engine.completed}" else engine.stage.name
        canvas.drawText(sub, w - sp(16f) - text.measureText(sub), sp(42f), text)

        // 管理员圆形入口（仅登录后出现）
        if (host.isAdmin()) {
            val cx = w - sp(34f)
            val cy = sp(70f)
            paint.color = Color.argb(120, 58, 42, 18)
            canvas.drawCircle(cx, cy, sp(18f), paint)
            paint.style = Paint.Style.STROKE
            paint.color = amber
            canvas.drawCircle(cx, cy, sp(18f), paint)
            paint.style = Paint.Style.FILL
            text.textSize = sp(9f)
            text.color = amber
            canvas.drawText("管理", cx - text.measureText("管理") / 2f, cy - sp(1f), text)
            canvas.drawText("菜单", cx - text.measureText("菜单") / 2f, cy + sp(10f), text)
        }

        // 状态 chips（两行，避免窄屏溢出）
        var x = sp(12f)
        var y = sp(44f)
        val chips = ArrayList<Pair<String, Int>>()
        chips.add("生命 ${engine.hp}/${engine.maxHp}" to red)
        chips.add("理智 ${engine.sanity}" to purple)
        chips.add("金币 ${engine.coins}" to amber)
        chips.add("进度 ${progressText()}" to blue)
        chips.add("行动 ${engine.ap}" to green)
        if (engine.luck > 0) chips.add("幸运 ${engine.luck}" to Color.parseColor("#F3D35E"))
        if (engine.shield > 0) chips.add("盾 ${engine.shield}" to Color.parseColor("#90CAF9"))
        for ((label, c) in chips) {
            val right = x + sp(9f) * 2 + (text.apply { textSize = sp(11f) }.measureText(label))
            if (right > w - sp(8f)) {
                x = sp(12f)
                y += sp(24f)
            }
            x = chip(canvas, x, y, label, c) + sp(5f)
        }
    }

    private fun progressText(): String {
        val s = engine.stage
        return when (s.goal) {
            GoalType.KILL_ALL, GoalType.BOSS -> "剩 ${engine.monsters.size}"
            GoalType.SURVIVE -> "${engine.turn}/${s.goalValue}"
            else -> "${engine.progress}/${s.goalValue}"
        }
    }

    private fun monsterRect(i: Int, n: Int, w: Float, h: Float): RectF {
        val top = sp(96f)
        val bottom = h - sp(232f)
        val spacing = min(w / (n + 1), sp(120f))
        val startX = w / 2f - (n - 1) * spacing / 2f
        val cx = startX + i * spacing
        val ch = min(bottom - top, sp(150f))
        val half = sp(40f)
        return RectF(cx - half, top + (bottom - top - ch) / 2f, cx + half, top + (bottom - top - ch) / 2f + ch)
    }

    private fun drawMonsters(canvas: Canvas, w: Float, h: Float) {
        val ms = engine.monsters
        if (ms.isEmpty()) {
            text.textSize = sp(12f)
            text.color = Color.parseColor("#888888")
            val t = "这一层暂时很安静"
            canvas.drawText(t, w / 2f - text.measureText(t) / 2f, (h * 0.45f), text)
            return
        }
        val n = min(ms.size, 3)
        for (i in 0 until n) {
            val m = ms[i]
            val r = monsterRect(i, n, w, h)
            val selected = engine.target == i
            paint.color = Color.argb(if (selected) 60 else 24, 0, 0, 0)
            canvas.drawRoundRect(RectF(r.left - sp(8f), r.top - sp(8f), r.right + sp(8f), r.bottom + sp(8f)), sp(10f), sp(10f), paint)
            // 人形剪影：头/肩/躯干/手臂按同一比例缩放，避免被拉扁
            val headR = r.width() * 0.27f
            val hx = r.centerX()
            val hy = r.top + headR + sp(2f)
            paint.color = Color.argb(225, 16, 16, 18)
            canvas.drawCircle(hx, hy, headR, paint)
            val bodyTop = hy + headR * 0.82f
            val shoulder = r.width() * 0.86f
            val hipY = r.bottom - (r.bottom - bodyTop) * 0.24f
            val bodyPath = Path()
            bodyPath.moveTo(hx - shoulder / 2f, bodyTop)
            bodyPath.quadTo(hx, bodyTop - headR * 0.28f, hx + shoulder / 2f, bodyTop)
            bodyPath.lineTo(hx + shoulder * 0.40f, hipY)
            bodyPath.lineTo(hx - shoulder * 0.40f, hipY)
            bodyPath.close()
            canvas.drawPath(bodyPath, paint)
            // 手臂
            paint.strokeWidth = r.width() * 0.14f
            paint.color = Color.argb(210, 16, 16, 18)
            canvas.drawLine(hx - shoulder / 2f, bodyTop + headR * 0.15f, hx - shoulder * 0.62f, hipY, paint)
            canvas.drawLine(hx + shoulder / 2f, bodyTop + headR * 0.15f, hx + shoulder * 0.62f, hipY, paint)
            paint.strokeWidth = sp(1.4f)
            paint.style = Paint.Style.STROKE
            paint.color = if (selected) amber else Color.argb(150, 90, 90, 90)
            canvas.drawCircle(hx, hy, headR, paint)
            canvas.drawPath(bodyPath, paint)
            paint.style = Paint.Style.FILL
            // 眼睛
            paint.color = if (selected) amber else Color.argb(230, 224, 82, 82)
            canvas.drawCircle(hx - headR * 0.36f, hy - headR * 0.06f, headR * 0.17f, paint)
            canvas.drawCircle(hx + headR * 0.36f, hy - headR * 0.06f, headR * 0.17f, paint)
            // 名字与血条
            text.textSize = sp(10f)
            text.color = txt
            canvas.drawText(m.name, r.centerX() - text.measureText(m.name) / 2f, r.bottom + sp(14f), text)
            val barW = r.width() * 0.9f
            val barY = r.bottom + sp(20f)
            paint.color = Color.argb(200, 40, 40, 40)
            canvas.drawRect(r.centerX() - barW / 2f, barY, r.centerX() + barW / 2f, barY + sp(4f), paint)
            paint.color = red
            val frac = m.hp.toFloat() / m.maxHp.toFloat()
            canvas.drawRect(r.centerX() - barW / 2f, barY, r.centerX() - barW / 2f + barW * frac, barY + sp(4f), paint)
            text.textSize = sp(8f)
            text.color = Color.parseColor("#AAAAAA")
            val stat = "${m.hp}/${m.maxHp} · 攻${m.atk} · ${m.skill}"
            canvas.drawText(stat, r.centerX() - text.measureText(stat) / 2f, barY + sp(15f), text)
        }
    }

    private fun drawEventBar(canvas: Canvas, w: Float, h: Float) {
        val bw = min(w - sp(40f), sp(560f))
        val cardTop = h - sp(150f)
        val r = RectF(w / 2f - bw / 2f, cardTop - sp(50f), w / 2f + bw / 2f, cardTop - sp(24f))
        paint.color = Color.argb(190, 16, 16, 16)
        canvas.drawRoundRect(r, sp(8f), sp(8f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sp(1.2f)
        paint.color = Color.argb(170, 232, 197, 71)
        canvas.drawRoundRect(r, sp(8f), sp(8f), paint)
        paint.style = Paint.Style.FILL
        text.textSize = sp(10f)
        val ev = engine.activeEvent
        val msg = if (ev != null) {
            val tag = if (ev.tag == "good") "【好事】" else if (ev.tag == "bad") "【危险】" else "【异象】"
            tag + ev.title + " —— 点击查看"
        } else if (engine.message.isNotEmpty()) engine.message else hint
        text.color = when (ev?.tag) {
            "good" -> Color.parseColor("#9CE09C")
            "bad" -> Color.parseColor("#E08A8A")
            else -> Color.parseColor("#E6E6E6")
        }
        var t = msg
        while (text.measureText(t) > r.width() - sp(16f) && t.length > 4) t = t.substring(0, t.length - 2)
        canvas.drawText(t, r.left + sp(8f), r.centerY() + sp(4f), text)
    }

    private fun cardRect(i: Int, n: Int, w: Float, h: Float): RectF {
        val cw = sp(62f)
        val ch = sp(96f)
        val mid = (n - 1) / 2f
        val t = if (n <= 1) 0f else (i - mid) / mid
        val spacing = sp(46f)
        val cx = w / 2f + t * (n - 1) * spacing / 2f
        val top = h - sp(150f)
        return RectF(cx - cw / 2f, top, cx + cw / 2f, top + ch)
    }

    private fun drawHand(canvas: Canvas, w: Float, h: Float) {
        val n = engine.hand.size
        if (n == 0) {
            text.textSize = sp(11f)
            text.color = dim
            val t = "手牌为空 · 结束回合会补满"
            canvas.drawText(t, w / 2f - text.measureText(t) / 2f, h - sp(120f), text)
            return
        }
        for (i in n - 1 downTo 0) {
            val c = engine.hand[i]
            val r = cardRect(i, n, w, h)
            paint.color = Color.argb(235, 20, 20, 22)
            canvas.drawRoundRect(r, sp(8f), sp(8f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = sp(1.6f)
            paint.color = c.category.color
            canvas.drawRoundRect(r, sp(8f), sp(8f), paint)
            paint.style = Paint.Style.FILL
            // 顶部类别色条
            paint.color = Color.argb(120, Color.red(c.category.color), Color.green(c.category.color), Color.blue(c.category.color))
            canvas.drawRect(r.left + sp(4f), r.top + sp(4f), r.right - sp(4f), r.top + sp(10f), paint)
            // 名称
            bold.textSize = sp(10f)
            bold.color = txt
            var name = c.name
            while (bold.measureText(name) > r.width() - sp(8f) && name.length > 2) name = name.substring(0, name.length - 1)
            canvas.drawText(name, r.left + sp(6f), r.top + sp(28f), bold)
            // 数值
            bold.textSize = sp(15f)
            bold.color = amber
            canvas.drawText(c.v1.toString(), r.left + sp(6f), r.top + sp(52f), bold)
            text.textSize = sp(8f)
            text.color = dim
            canvas.drawText(c.v2.toString(), r.left + sp(6f), r.top + sp(64f), text)
            // 幸运
            if (c.luck > 0) {
                text.textSize = sp(8f)
                text.color = Color.parseColor("#F3D35E")
                canvas.drawText("幸${c.luck}", r.right - sp(22f), r.top + sp(28f), text)
            }
            // 描述
            text.textSize = sp(7.5f)
            text.color = Color.parseColor("#9A9A9A")
            val d = c.desc
            var d1 = d
            while (text.measureText(d1) > r.width() - sp(10f) && d1.length > 3) d1 = d1.substring(0, d1.length - 1)
            canvas.drawText(d1, r.left + sp(6f), r.bottom - sp(24f), text)
            // 价格
            bold.textSize = sp(9f)
            bold.color = amber
            canvas.drawText(c.cost.toString() + "金", r.left + sp(6f), r.bottom - sp(8f), bold)
        }
    }

    private val bagRect: RectF get() = RectF(sp(12f), height - sp(78f), sp(60f), height - sp(30f))
    private val endRect: RectF get() = RectF(width - sp(112f), height - sp(74f), width - sp(12f), height - sp(30f))

    private fun drawBottomBar(canvas: Canvas, w: Float, h: Float) {
        // 背包图标（数量角标）
        val r = bagRect
        paint.color = Color.argb(190, 18, 18, 18)
        canvas.drawRoundRect(r, sp(10f), sp(10f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sp(1.2f)
        paint.color = Color.argb(180, 232, 197, 71)
        canvas.drawRoundRect(r, sp(10f), sp(10f), paint)
        paint.style = Paint.Style.FILL
        val cx = r.centerX()
        paint.color = amber
        val bodyTop = r.top + sp(13f)
        canvas.drawRoundRect(RectF(cx - sp(13f), bodyTop, cx + sp(13f), r.bottom - sp(7f)), sp(6f), sp(6f), paint)
        // 提手
        canvas.drawArc(RectF(cx - sp(7f), bodyTop - sp(8f), cx + sp(7f), bodyTop + sp(6f)), 180f, 180f, false, paint)
        // 包盖与前袋
        paint.color = Color.argb(255, 25, 25, 27)
        canvas.drawRoundRect(RectF(cx - sp(13f), bodyTop + sp(2f), cx + sp(13f), bodyTop + sp(7f)), sp(2.5f), sp(2.5f), paint)
        canvas.drawRoundRect(RectF(cx - sp(9f), r.bottom - sp(16f), cx + sp(9f), r.bottom - sp(8f)), sp(3f), sp(3f), paint)
        // 肩带
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sp(1.4f)
        paint.color = amber
        canvas.drawArc(RectF(cx - sp(18f), bodyTop + sp(3f), cx - sp(8f), bodyTop + sp(21f)), 90f, 120f, false, paint)
        canvas.drawArc(RectF(cx + sp(8f), bodyTop + sp(3f), cx + sp(18f), bodyTop + sp(21f)), -30f, 120f, false, paint)
        paint.style = Paint.Style.FILL
        if (engine.inventory.size > 0) {
            paint.color = red
            canvas.drawCircle(r.right - sp(3f), r.top + sp(3f), sp(8f), paint)
            text.textSize = sp(8f)
            text.color = Color.WHITE
            val n = if (engine.inventory.size > 9) "9+" else engine.inventory.size.toString()
            canvas.drawText(n, r.right - sp(3f) - text.measureText(n) / 2f, r.top + sp(6f), text)
        }

        // 结束回合
        val er = endRect
        paint.color = amber
        canvas.drawRoundRect(er, sp(10f), sp(10f), paint)
        paint.color = Color.argb(80, 0, 0, 0)
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(er, sp(10f), sp(10f), paint)
        paint.style = Paint.Style.FILL
        bold.textSize = sp(12f)
        bold.color = Color.parseColor("#141414")
        val label = "结束回合"
        canvas.drawText(label, er.centerX() - bold.measureText(label) / 2f, er.centerY() + sp(4f), bold)

        // 装备/道具提示
        text.textSize = sp(9f)
        text.color = Color.argb(150, 170, 170, 170)
        val tip = "装备 ${engine.equipment.size}/${GameEngine.MAX_EQUIP} · 道具 ${engine.inventory.size}"
        canvas.drawText(tip, r.right + sp(8f), r.top + sp(14f), text)
        if (hint.isNotEmpty()) {
            text.color = Color.argb(200, 232, 197, 71)
            canvas.drawText(hint, r.right + sp(8f), r.top + sp(28f), text)
        }
    }

    // ---------- 覆盖层 ----------

    private fun panelRect(): RectF {
        val pw = min(width - sp(24f), sp(360f))
        val ph = min(height - sp(40f), sp(420f))
        return RectF(width / 2f - pw / 2f, height / 2f - ph / 2f, width / 2f + pw / 2f, height / 2f + ph / 2f)
    }

    private fun optTrack(i: Int): RectF {
        val r = panelRect()
        val top = r.top + sp(60f) + i * sp(50f)
        return RectF(r.left + sp(26f), top + sp(12f), r.right - sp(26f), top + sp(23f))
    }

    private fun drawOptions(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(200, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paint)
        val r = panelRect()
        paint.color = Color.argb(246, 18, 18, 18)
        canvas.drawRoundRect(r, sp(10f), sp(10f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sp(1.3f)
        paint.color = Color.argb(180, 232, 197, 71)
        canvas.drawRoundRect(r, sp(10f), sp(10f), paint)
        paint.style = Paint.Style.FILL

        bold.textSize = sp(16f)
        bold.color = amber
        canvas.drawText("选项", r.left + sp(20f), r.top + sp(32f), bold)

        val labels = listOf("音乐音量", "音效音量", "界面亮度")
        val values = listOf(host.musicVolume(), host.soundVolume(), host.brightnessValue())
        for (i in 0 until 3) {
            val tr = optTrack(i)
            text.textSize = sp(10f)
            text.color = txt
            canvas.drawText(labels[i], tr.left, tr.top - sp(7f), text)
            bold.textSize = sp(10f)
            bold.color = amber
            canvas.drawText(values[i].toString(), tr.right - bold.measureText(values[i].toString()), tr.top - sp(7f), bold)
            paint.color = Color.argb(255, 46, 46, 46)
            canvas.drawRoundRect(tr, sp(5f), sp(5f), paint)
            paint.color = amber
            canvas.drawRoundRect(RectF(tr.left, tr.top, tr.left + tr.width() * values[i] / 100f, tr.bottom), sp(5f), sp(5f), paint)
            paint.color = Color.rgb(255, 236, 160)
            canvas.drawCircle(tr.left + tr.width() * values[i] / 100f, tr.centerY(), sp(6f), paint)
        }

        val back = RectF(r.right - sp(104f), r.bottom - sp(44f), r.right - sp(16f), r.bottom - sp(14f))
        paint.color = Color.argb(220, 26, 26, 26)
        canvas.drawRoundRect(back, sp(6f), sp(6f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sp(1.2f)
        paint.color = Color.argb(170, 232, 197, 71)
        canvas.drawRoundRect(back, sp(6f), sp(6f), paint)
        paint.style = Paint.Style.FILL
        bold.textSize = sp(11f)
        bold.color = txt
        canvas.drawText("返回对局", back.centerX() - bold.measureText("返回对局") / 2f, back.centerY() + sp(4f), bold)
    }

    private fun drawMenu(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(190, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paint)
        val r = panelRect()
        paint.color = Color.argb(245, 18, 18, 18)
        canvas.drawRoundRect(r, sp(12f), sp(12f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sp(1.4f)
        paint.color = Color.argb(180, 232, 197, 71)
        canvas.drawRoundRect(r, sp(12f), sp(12f), paint)
        paint.style = Paint.Style.FILL
        bold.textSize = sp(18f)
        bold.color = amber
        canvas.drawText("菜单", r.centerX() - bold.measureText("菜单") / 2f, r.top + sp(36f), bold)

        val items = listOf("继续", "重来一次", "选项", "退出至主界面")
        var y = r.top + sp(58f)
        for (i in items.indices) {
            val br = RectF(r.left + sp(24f), y, r.right - sp(24f), y + sp(40f))
            paint.color = Color.argb(220, 24, 24, 24)
            canvas.drawRoundRect(br, sp(8f), sp(8f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = sp(1.2f)
            paint.color = Color.argb(150, 232, 197, 71)
            canvas.drawRoundRect(br, sp(8f), sp(8f), paint)
            paint.style = Paint.Style.FILL
            bold.textSize = sp(12f)
            bold.color = txt
            canvas.drawText(items[i], br.centerX() - bold.measureText(items[i]) / 2f, br.centerY() + sp(4f), bold)
            y += sp(48f)
        }
        text.textSize = sp(9f)
        text.color = dim
        canvas.drawText("v$APP_VERSION", r.left + sp(12f), r.bottom - sp(8f), text)
    }

    private fun drawBag(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(205, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paint)
        val r = panelRect()
        paint.color = Color.argb(245, 16, 16, 16)
        canvas.drawRoundRect(r, sp(12f), sp(12f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sp(1.4f)
        paint.color = Color.argb(180, 232, 197, 71)
        canvas.drawRoundRect(r, sp(12f), sp(12f), paint)
        paint.style = Paint.Style.FILL
        bold.textSize = sp(16f)
        bold.color = amber
        canvas.drawText("背包", r.left + sp(18f), r.top + sp(30f), bold)
        text.textSize = sp(9f)
        text.color = dim
        canvas.drawText("装备 ${engine.equipment.size}/${GameEngine.MAX_EQUIP} · 道具 ${engine.inventory.size}", r.left + sp(62f), r.top + sp(30f), text)

        // 装备槽
        val tileW = (r.width() - sp(36f)) / 2f
        for (i in 0 until GameEngine.MAX_EQUIP) {
            val col = i % 2
            val row = i / 2
            val tr = RectF(r.left + sp(12f) + col * (tileW + sp(12f)), r.top + sp(44f) + row * sp(40f),
                r.left + sp(12f) + col * (tileW + sp(12f)) + tileW, r.top + sp(44f) + row * sp(40f) + sp(34f))
            paint.color = Color.argb(235, 28, 28, 28)
            canvas.drawRoundRect(tr, sp(6f), sp(6f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = sp(0.8f)
            paint.color = Color.argb(120, 90, 90, 90)
            canvas.drawRoundRect(tr, sp(6f), sp(6f), paint)
            paint.style = Paint.Style.FILL
            val e = engine.equipment.getOrNull(i)
            text.textSize = sp(9f)
            if (e == null) {
                text.color = Color.parseColor("#666666")
                canvas.drawText("空槽", tr.left + sp(6f), tr.centerY() + sp(3f), text)
            } else {
                text.color = when (e.slot) {
                    EquipSlot.WEAPON -> Color.parseColor("#E88A5A")
                    EquipSlot.ARMOR -> Color.parseColor("#5AA8E0")
                    else -> Color.parseColor("#8BC34A")
                }
                var n = e.name
                while (text.measureText(n) > tr.width() - sp(10f) && n.length > 2) n = n.substring(0, n.length - 1)
                canvas.drawText(n, tr.left + sp(6f), tr.top + sp(14f), text)
                text.color = Color.parseColor("#9A9A9A")
                text.textSize = sp(8f)
                canvas.drawText("攻+${e.atk} 甲+${e.armor}", tr.left + sp(6f), tr.top + sp(27f), text)
            }
        }

        // 道具列表（点击使用）
        text.textSize = sp(10f)
        text.color = Color.parseColor("#CCCCCC")
        canvas.drawText("道具（点击使用）", r.left + sp(12f), r.top + sp(140f), text)
        if (engine.inventory.isEmpty()) {
            text.color = Color.parseColor("#777777")
            canvas.drawText("背包里还没有东西", r.left + sp(12f), r.top + sp(158f), text)
        }
        val shown = min(engine.inventory.size, 6)
        for (i in 0 until shown) {
            val ir = itemRowRect(i, r)
            val it = engine.inventory[i]
            paint.color = Color.argb(230, 26, 26, 26)
            canvas.drawRoundRect(ir, sp(5f), sp(5f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = sp(0.8f)
            paint.color = Color.argb(160, Color.red(it.category.color), Color.green(it.category.color), Color.blue(it.category.color))
            canvas.drawRoundRect(ir, sp(5f), sp(5f), paint)
            paint.style = Paint.Style.FILL
            text.textSize = sp(9f)
            text.color = it.category.color
            canvas.drawText(it.name, ir.left + sp(6f), ir.top + sp(12f), text)
            text.textSize = sp(8f)
            text.color = Color.parseColor("#999999")
            var d = it.desc
            while (text.measureText(d) > ir.width() - sp(10f) && d.length > 3) d = d.substring(0, d.length - 1)
            canvas.drawText(d, ir.left + sp(6f), ir.top + sp(24f), text)
        }

        // 关闭按钮
        val cr = RectF(r.right - sp(80f), r.bottom - sp(38f), r.right - sp(14f), r.bottom - sp(10f))
        paint.color = Color.argb(220, 30, 30, 30)
        canvas.drawRoundRect(cr, sp(6f), sp(6f), paint)
        bold.textSize = sp(11f)
        bold.color = txt
        canvas.drawText("关闭", cr.centerX() - bold.measureText("关闭") / 2f, cr.centerY() + sp(4f), bold)
    }

    private fun itemRowRect(i: Int, panel: RectF): RectF {
        val top = panel.top + sp(146f) + i * sp(40f)
        return RectF(panel.left + sp(12f), top, panel.right - sp(12f), top + sp(34f))
    }

    // ---------- 触摸 ----------

    private fun inR(x: Float, y: Float, r: RectF): Boolean = x >= r.left && x <= r.right && y >= r.top && y <= r.bottom

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        val x = e.x
        val y = e.y

        if (optionsOpen) {
            val r = panelRect()
            if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                for (i in 0 until 3) {
                    val tr = optTrack(i)
                    if (y >= tr.top - sp(14f) && y <= tr.bottom + sp(16f) && x >= tr.left - sp(12f) && x <= tr.right + sp(12f)) {
                        if (e.action == MotionEvent.ACTION_DOWN) optDrag = i
                        if (optDrag == i) {
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
            if (e.action == MotionEvent.ACTION_UP) optDrag = -1
            if (e.action == MotionEvent.ACTION_DOWN) {
                val back = RectF(r.right - sp(104f), r.bottom - sp(44f), r.right - sp(16f), r.bottom - sp(14f))
                if (inR(x, y, back)) { optionsOpen = false; invalidate() }
            }
            return true
        }

        if (menuOpen) {
            val r = panelRect()
            val verRect = RectF(r.left + sp(6f), r.bottom - sp(22f), r.left + sp(90f), r.bottom)
            if (inR(x, y, verRect)) {
                verTaps++
                if (verTaps >= 6) {
                    verTaps = 0
                    menuOpen = false
                    host.boardAdminPassword()
                    invalidate()
                }
                return true
            }
            val keys = listOf("resume", "restart", "opts", "exit")
            var top = r.top + sp(58f)
            for (k in keys) {
                val br = RectF(r.left + sp(24f), top, r.right - sp(24f), top + sp(40f))
                if (inR(x, y, br)) {
                    menuOpen = false
                    when (k) {
                        "resume" -> invalidate()
                        "restart" -> host.boardRestart()
                        "opts" -> { optionsOpen = true; menuOpen = false; invalidate() }
                        "exit" -> host.boardExitToMenu()
                    }
                    return true
                }
                top += sp(48f)
            }
            return true
        }

        if (bagOpen) {
            val r = panelRect()
            val closeRect = RectF(r.right - sp(80f), r.bottom - sp(38f), r.right - sp(14f), r.bottom - sp(10f))
            if (inR(x, y, closeRect)) {
                bagOpen = false
                invalidate()
                return true
            }
            val shown = min(engine.inventory.size, 6)
            for (i in 0 until shown) {
                val ir = itemRowRect(i, r)
                if (inR(x, y, ir)) {
                    host.boardUseItem(i)
                    hint = "已使用道具"
                    return true
                }
            }
            return true
        }

        val menuRect = RectF(sp(12f), sp(12f), sp(78f), sp(38f))
        if (inR(x, y, menuRect)) {
            openMenu()
            return true
        }
        if (host.isAdmin()) {
            val cx = width - sp(34f)
            val cy = sp(70f)
            val rr = sp(18f)
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= rr * rr) {
                host.boardAdminPassword()
                return true
            }
        }
        if (inR(x, y, bagRect)) {
            bagOpen = true
            hint = ""
            invalidate()
            return true
        }
        if (inR(x, y, endRect)) {
            host.boardEndTurn()
            return true
        }

        val n = min(engine.monsters.size, 3)
        for (i in 0 until n) {
            if (inR(x, y, monsterRect(i, n, width.toFloat(), height.toFloat()))) {
                engine.target = i
                invalidate()
                return true
            }
        }
        val bw = min(width - sp(40f), sp(560f))
        val evRect = RectF(width / 2f - bw / 2f, height - sp(212f), width / 2f + bw / 2f, height - sp(186f))
        if (inR(x, y, evRect)) {
            host.boardAfterAction()
            return true
        }
        val hn = engine.hand.size
        for (i in hn - 1 downTo 0) {
            if (inR(x, y, cardRect(i, hn, width.toFloat(), height.toFloat()))) {
                if (engine.playCard(i)) host.boardAfterAction() else invalidate()
                return true
            }
        }
        return true
    }
}

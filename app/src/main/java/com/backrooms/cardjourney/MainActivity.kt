package com.backrooms.cardjourney

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private val engine = GameEngine()
    private var musicVol = 70
    private var soundVol = 80
    private var bright = 100
    private var overDialogShown = false

    private val C_BG = 0xFF0D0D0D.toInt()
    private val C_PANEL = 0xFF1B1B1B.toInt()
    private val C_PANEL2 = 0xFF262626.toInt()
    private val C_ACCENT = 0xFFE8C547.toInt()
    private val C_TEXT = 0xFFEDEDED.toInt()
    private val C_DIM = 0xFF8F8F8F.toInt()
    private val C_RED = 0xFFE05252.toInt()
    private val C_GREEN = 0xFF63C46B.toInt()
    private val C_PURPLE = 0xFFB06BE0.toInt()
    private val C_BLUE = 0xFF5AA8E0.toInt()

    private val prefs by lazy { getSharedPreferences("backrooms", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        root.setBackgroundColor(C_BG)
        setContentView(root)
        loadSettings()
        applyBrightness()
        showMainMenu()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Float): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = radius
        return g
    }

    private fun roundedStroke(fill: Int, radius: Float, stroke: Int, strokeColor: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = radius
        g.setStroke(stroke, strokeColor)
        return g
    }

    private fun tv(t: String, size: Float, color: Int = C_TEXT, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextColor(color)
        v.textSize = size
        if (bold) v.setTypeface(null, Typeface.BOLD)
        return v
    }

    private fun lpFull(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    private fun lpWeight(weight: Float, margin: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.weight = weight
        p.setMargins(margin, margin, margin, margin)
        return p
    }

    private fun spacer(h: Int): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(1, dp(h))
        return v
    }

    private fun actionBtn(text: String, bg: Int = C_ACCENT, fg: Int = Color.BLACK, onClick: () -> Unit): TextView {
        val v = TextView(this)
        v.text = text
        v.gravity = Gravity.CENTER
        v.setTextColor(fg)
        v.textSize = 14f
        v.setTypeface(null, Typeface.BOLD)
        v.background = rounded(bg, dp(10).toFloat())
        v.setPadding(dp(12), dp(12), dp(12), dp(12))
        v.isClickable = true
        v.setOnClickListener {
            playClick()
            onClick()
        }
        return v
    }

    private fun chip(text: String, color: Int): TextView {
        val v = tv(text, 12f, Color.WHITE, true)
        v.gravity = Gravity.CENTER
        v.background = rounded(color, dp(8).toFloat())
        v.setPadding(dp(6), dp(8), dp(6), dp(8))
        return v
    }

    private fun menuButton(text: String, onClick: () -> Unit): TextView {
        val v = actionBtn(text, C_PANEL2, C_TEXT) { onClick() }
        v.textSize = 17f
        v.setPadding(dp(20), dp(18), dp(20), dp(18))
        val p = lpFull()
        p.topMargin = dp(12)
        v.layoutParams = p
        return v
    }

    private fun playClick() {
        if (soundVol > 0) {
            try {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, soundVol * 40 / 100)
                tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 40)
                tg.release()
            } catch (e: Exception) {
            }
        }
    }

    private fun loadSettings() {
        musicVol = prefs.getInt("music", 70)
        soundVol = prefs.getInt("sound", 80)
        bright = prefs.getInt("bright", 100)
    }

    private fun saveSettings() {
        prefs.edit()
            .putInt("music", musicVol)
            .putInt("sound", soundVol)
            .putInt("bright", bright)
            .apply()
    }

    private fun applyBrightness() {
        val attr = window.attributes
        attr.screenBrightness = bright.coerceIn(10, 100) / 100f
        window.attributes = attr
    }

    private fun saveGame() {
        prefs.edit()
            .putBoolean("has_save", true)
            .putInt("level", engine.levelIndex)
            .putInt("coins", engine.coins)
            .putInt("score", engine.score)
            .putBoolean("infinite", engine.infiniteMode)
            .putInt("completed", engine.completed)
            .apply()
    }

    private fun hasSave(): Boolean = prefs.getBoolean("has_save", false)

    private fun continueGame() {
        if (!hasSave()) {
            Toast.makeText(this, "还没有存档，请先开始游戏", Toast.LENGTH_SHORT).show()
            return
        }
        engine.levelIndex = prefs.getInt("level", 0)
        engine.coins = prefs.getInt("coins", 12)
        engine.score = prefs.getInt("score", 0)
        engine.infiniteMode = prefs.getBoolean("infinite", false)
        engine.completed = prefs.getInt("completed", 0)
        engine.startStage(engine.levelIndex)
        overDialogShown = false
        showGame()
    }

    private fun showMainMenu() {
        root.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.gravity = Gravity.CENTER_HORIZONTAL
        ll.setPadding(dp(24), dp(70), dp(24), dp(40))
        sv.addView(ll, lpFull())
        root.addView(sv, lpFull())

        val title = tv("后室卡牌之旅", 34f, C_ACCENT, true)
        title.gravity = Gravity.CENTER
        ll.addView(title, lpFull())

        val sub = tv("你不小心掉进了后室，而你得要依靠你手中的卡牌来逃离这里", 13f, C_DIM)
        sub.gravity = Gravity.CENTER
        val sp = lpFull()
        sp.topMargin = dp(10)
        ll.addView(sub, sp)

        ll.addView(spacer(40))

        ll.addView(menuButton("开始游戏") {
            engine.newGame()
            overDialogShown = false
            saveGame()
            showGame()
        })

        ll.addView(menuButton("继续游戏") { continueGame() })

        ll.addView(menuButton("设置") { showSettings() })

        ll.addView(menuButton("关于详情") { showAbout() })

        ll.addView(spacer(30))
        val ver = tv("v1.0.0  ·  2000 张卡牌  ·  1000 种道具  ·  30 个关卡", 11f, C_DIM)
        ver.gravity = Gravity.CENTER
        ll.addView(ver, lpFull())
    }

    private fun update() {
        if (engine.gameOver) {
            saveGame()
            showGameOver()
            return
        }
        if (engine.cleared) {
            saveGame()
            onStageCleared()
            return
        }
        saveGame()
        showGame()
    }

    private fun showGame() {
        root.removeAllViews()
        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.setPadding(dp(10), dp(10), dp(10), dp(10))

        val status = LinearLayout(this)
        status.orientation = LinearLayout.HORIZONTAL
        main.addView(status, lpFull())

        status.addView(chip("生命 " + engine.hp + "/" + engine.maxHp, C_RED), lpWeight(1f, dp(3)))
        status.addView(chip("理智 " + engine.sanity, C_PURPLE), lpWeight(1f, dp(3)))
        status.addView(chip("金币 " + engine.coins, C_ACCENT), lpWeight(1f, dp(3)))

        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        info.background = rounded(C_PANEL, dp(12).toFloat())
        info.setPadding(dp(14), dp(12), dp(14), dp(12))
        val infoLp = lpFull()
        infoLp.topMargin = dp(8)
        main.addView(info, infoLp)

        val s = engine.stage
        info.addView(tv(s.name, 16f, C_ACCENT, true), lpFull())
        info.addView(tv(s.subtitle, 11f, C_DIM), lpFull())

        val goalLp = lpFull()
        goalLp.topMargin = dp(6)
        info.addView(tv("目标：" + s.goal.label + "  ·  " + goalText(), 13f, C_TEXT, true), goalLp)
        info.addView(tv("回合 " + engine.turn + "   行动力 " + engine.ap + "   护盾 " + engine.shield, 11f, C_DIM), lpFull())

        val monBox = LinearLayout(this)
        monBox.orientation = LinearLayout.VERTICAL
        val monLp = lpFull()
        monLp.topMargin = dp(8)
        main.addView(monBox, monLp)

        if (engine.monsters.isEmpty()) {
            val none = tv("周围暂时安全……", 12f, C_DIM)
            none.setPadding(dp(6), dp(6), dp(6), dp(6))
            monBox.addView(none, lpFull())
        } else {
            for (m in engine.monsters) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.background = roundedStroke(C_PANEL2, dp(8).toFloat(), dp(1), C_RED)
                row.setPadding(dp(10), dp(8), dp(10), dp(8))
                val rlp = lpFull()
                rlp.topMargin = dp(4)
                monBox.addView(row, rlp)

                val nameTv = tv(m.name, 13f, C_RED, true)
                row.addView(nameTv, lpWeight(1f, dp(2)))

                val statTv = tv("HP " + m.hp + "/" + m.maxHp + "   ATK " + m.atk, 11f, C_DIM)
                row.addView(statTv, lpWeight(1f, dp(2)))
            }
        }

        val msg = tv(engine.message, 11f, C_DIM)
        msg.setPadding(dp(4), dp(6), dp(4), dp(6))
        main.addView(msg, lpFull())

        val handScroll = HorizontalScrollView(this)
        handScroll.isHorizontalScrollBarEnabled = false
        val handLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(158))
        handLp.topMargin = dp(2)
        main.addView(handScroll, handLp)

        val handRow = LinearLayout(this)
        handRow.orientation = LinearLayout.HORIZONTAL
        handScroll.addView(handRow, lpFull())

        for (i in engine.hand.indices) {
            val card = engine.hand[i]
            handRow.addView(makeCardView(card) {
                engine.playCard(i)
                update()
            })
        }

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        val r1lp = lpFull()
        r1lp.topMargin = dp(8)
        main.addView(row1, r1lp)

        row1.addView(actionBtn("结束回合", C_ACCENT) {
            engine.endTurn()
            update()
        }, lpWeight(1f, dp(4)))

        row1.addView(actionBtn("买卡 (2金币)", C_BLUE) { buyCardDialog() }, lpWeight(1f, dp(4)))

        row1.addView(actionBtn("商人", C_PURPLE) { openShop() }, lpWeight(1f, dp(4)))

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        val r2lp = lpFull()
        r2lp.topMargin = dp(6)
        main.addView(row2, r2lp)

        row2.addView(actionBtn("道具 (" + engine.inventory.size + ")", C_PANEL2, C_TEXT) {
            openInventory()
        }, lpWeight(1f, dp(4)))

        row2.addView(actionBtn("菜单", C_PANEL2, C_TEXT) {
            saveGame()
            showMainMenu()
        }, lpWeight(1f, dp(4)))

        root.addView(main, lpFull())
    }

    private fun goalText(): String {
        val s = engine.stage
        return when (s.goal) {
            GoalType.KILL_ALL, GoalType.BOSS -> "剩余怪物 " + engine.monsters.size
            GoalType.SURVIVE -> "已存活 " + engine.turn + " / " + s.goalValue + " 回合"
            else -> "进度 " + engine.progress + " / " + s.goalValue
        }
    }

    private fun makeCardView(card: Card, onClick: () -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = roundedStroke(C_PANEL2, dp(10).toFloat(), dp(2), card.category.color)
        box.setPadding(dp(8), dp(8), dp(8), dp(8))
        box.isClickable = true
        box.setOnClickListener {
            playClick()
            onClick()
        }

        val p = LinearLayout.LayoutParams(dp(112), dp(152))
        p.setMargins(dp(4), 0, dp(4), 0)
        box.layoutParams = p

        val nameTv = tv(card.name, 12f, C_TEXT, true)
        nameTv.maxLines = 2
        box.addView(nameTv, lpFull())

        val catTv = tv(card.category.label, 10f, card.category.color, true)
        val cLp = lpFull()
        cLp.topMargin = dp(2)
        box.addView(catTv, cLp)

        val descTv = tv(card.desc, 10f, C_DIM)
        descTv.maxLines = 4
        val dLp = lpFull()
        dLp.topMargin = dp(4)
        box.addView(descTv, dLp)

        return box
    }

    private fun buyCardDialog() {
        if (engine.boughtThisTurn) {
            Toast.makeText(this, "本回合已经购买过卡牌了", Toast.LENGTH_SHORT).show()
            return
        }
        if (engine.coins < 2) {
            Toast.makeText(this, "金币不足（需要 2 金币）", Toast.LENGTH_SHORT).show()
            return
        }
        val picks = CardLibrary.random(3)
        val labels = Array<CharSequence>(picks.size) { i ->
            picks[i].name + " [" + picks[i].category.label + "]\n" + picks[i].desc
        }
        val b = AlertDialog.Builder(this)
        b.setTitle("花 2 金币购买一张卡牌")
        b.setItems(labels) { _, which ->
            engine.coins -= 2
            engine.hand.add(picks[which])
            engine.boughtThisTurn = true
            engine.message = "购买了「" + picks[which].name + "」"
            update()
        }
        b.setNegativeButton("取消", null)
        b.show()
    }

    private fun openShop() {
        val picks = ItemLibrary.random(3)
        val labels = Array<CharSequence>(picks.size) { i ->
            picks[i].name + " [" + picks[i].category.label + "]\n" +
                picks[i].desc + "   价格 " + picks[i].price + " 金币"
        }
        val b = AlertDialog.Builder(this)
        b.setTitle("流浪商人  ·  你有 " + engine.coins + " 金币")
        b.setItems(labels) { _, which ->
            val item = picks[which]
            if (engine.coins >= item.price) {
                engine.coins -= item.price
                engine.inventory.add(item)
                Toast.makeText(this, "获得了「" + item.name + "」", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "金币不足", Toast.LENGTH_SHORT).show()
            }
            update()
        }
        b.setNegativeButton("离开", null)
        b.show()
    }

    private fun openInventory() {
        if (engine.inventory.isEmpty()) {
            Toast.makeText(this, "背包里空空的", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = Array<CharSequence>(engine.inventory.size) { i ->
            val it = engine.inventory[i]
            it.name + " [" + it.category.label + "]\n" + it.desc
        }
        val b = AlertDialog.Builder(this)
        b.setTitle("背包")
        b.setItems(labels) { _, which ->
            engine.useItem(which)
            update()
        }
        b.setNegativeButton("关闭", null)
        b.show()
    }

    private fun onStageCleared() {
        engine.cleared = false
        val force = engine.completed % 5 == 0
        val roll = Math.random()
        if (force || roll < 0.06) {
            showSpecialDialog()
        } else {
            showClearedDialog()
        }
    }

    private fun showClearedDialog() {
        val s = engine.stage
        val sb = StringBuilder()
        sb.append("你成功通过了「").append(s.name).append("」\n")
        sb.append("获得 ").append(s.reward).append(" 金币\n")
        if (engine.infiniteMode) {
            sb.append("当前无限模式得分：").append(engine.score)
        }
        val b = AlertDialog.Builder(this)
        b.setTitle("通关！")
        b.setMessage(sb.toString())
        b.setCancelable(false)
        b.setPositiveButton("继续") { _, _ ->
            engine.advance()
            if (engine.finishedAll) {
                showAllClearedDialog()
            } else {
                update()
            }
        }
        b.show()
    }

    private fun showAllClearedDialog() {
        val b = AlertDialog.Builder(this)
        b.setTitle("恭喜！")
        b.setMessage("你已经通过了全部 30 个关卡！\n\n后室的尽头之外，是无尽的回廊。\n是否进入无限模式，看看你能拿到多少分？")
        b.setCancelable(false)
        b.setPositiveButton("进入无限模式") { _, _ ->
            engine.infiniteMode = true
            engine.score = 0
            engine.startStage(0)
            update()
        }
        b.setNegativeButton("返回主菜单") { _, _ -> showMainMenu() }
        b.show()
    }

    private fun showSpecialDialog() {
        val r = Math.random()
        val type = when {
            r < 0.7 -> "good"
            r < 0.8 -> "bad"
            else -> "neutral"
        }
        when (type) {
            "good" -> showGoodSpecial()
            "bad" -> showBadSpecial()
            else -> showNeutralSpecial()
        }
    }

    private fun showGoodSpecial() {
        val idx = (Math.random() * 4).toInt()
        val b = AlertDialog.Builder(this)
        when (idx) {
            0 -> {
                b.setTitle("特殊事件 · 补给箱")
                b.setMessage("墙角堆着几个未开封的补给箱。\n\n获得 18 金币。")
                b.setPositiveButton("好耶") { _, _ ->
                    engine.coins += 18
                    engine.message = "捡到了补给箱！"
                    update()
                }
            }
            1 -> {
                b.setTitle("特殊事件 · 安全屋")
                b.setMessage("你找到了一间干净的房间，门可以从里面锁上。\n\n生命与理智全部恢复。")
                b.setPositiveButton("休息一下") { _, _ ->
                    engine.hp = engine.maxHp
                    engine.sanity = 100
                    engine.message = "在安全屋里恢复了状态。"
                    update()
                }
            }
            2 -> {
                b.setTitle("特殊事件 · 神秘商人")
                b.setMessage("一个披着斗篷的身影递给你一件东西，然后消失了。\n\n获得 1 件特殊道具。")
                b.setPositiveButton("收下") { _, _ ->
                    engine.inventory.add(ItemLibrary.random(1)[0])
                    update()
                }
            }
            else -> {
                b.setTitle("特殊事件 · 希望的灯火")
                b.setMessage("远处的灯光让你重新找回了一点点理智。\n\n理智 +50，获得 10 金币。")
                b.setPositiveButton("谢谢") { _, _ ->
                    engine.sanity = minOf(100, engine.sanity + 50)
                    engine.coins += 10
                    update()
                }
            }
        }
        b.setCancelable(false)
        b.show()
    }

    private fun showBadSpecial() {
        val idx = (Math.random() * 3).toInt()
        val b = AlertDialog.Builder(this)
        when (idx) {
            0 -> {
                b.setTitle("特殊事件 · 怪物突袭")
                b.setMessage("黑暗中有东西扑向了你。\n\n失去 30 点生命。")
                b.setPositiveButton("……") { _, _ ->
                    engine.hp -= 30
                    engine.checkState()
                    update()
                }
            }
            1 -> {
                b.setTitle("特殊事件 · 陷阱")
                b.setMessage("脚下的地毯塌陷，金币撒了一地。\n\n失去 12 金币。")
                b.setPositiveButton("该死") { _, _ ->
                    engine.coins = maxOf(0, engine.coins - 12)
                    update()
                }
            }
            else -> {
                b.setTitle("特殊事件 · 精神污染")
                b.setMessage("你看见了不该看见的东西。\n\n失去 30 点理智。")
                b.setPositiveButton("闭眼") { _, _ ->
                    engine.sanity -= 30
                    engine.checkState()
                    update()
                }
            }
        }
        b.setCancelable(false)
        b.show()
    }

    private fun showNeutralSpecial() {
        val b = AlertDialog.Builder(this)
        b.setTitle("特殊事件 · 岔路口")
        b.setMessage("前方分出两条路，你必须选择一条。\n\n左边：拿走地上的 12 金币\n右边：在长椅上休息，恢复 25 点生命")
        b.setCancelable(false)
        b.setPositiveButton("左边（金币）") { _, _ ->
            engine.coins += 12
            update()
        }
        b.setNegativeButton("右边（生命）") { _, _ ->
            engine.hp = minOf(engine.maxHp, engine.hp + 25)
            update()
        }
        b.show()
    }

    private fun showGameOver() {
        if (overDialogShown) return
        overDialogShown = true
        val sb = StringBuilder()
        sb.append("后室吞没了你……\n\n")
        sb.append("到达关卡：").append(engine.stage.name).append("\n")
        sb.append("通关数量：").append(engine.completed).append("\n")
        if (engine.infiniteMode) sb.append("无限模式得分：").append(engine.score).append("\n")

        val b = AlertDialog.Builder(this)
        b.setTitle("你倒下了")
        b.setMessage(sb.toString())
        b.setCancelable(false)
        b.setPositiveButton("重新开始") { _, _ ->
            overDialogShown = false
            engine.newGame()
            update()
        }
        b.setNegativeButton("返回主菜单") { _, _ ->
            overDialogShown = false
            showMainMenu()
        }
        b.show()
    }

    private fun showSettings() {
        root.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(dp(24), dp(50), dp(24), dp(40))
        sv.addView(ll, lpFull())
        root.addView(sv, lpFull())

        ll.addView(tv("设置", 28f, C_ACCENT, true))
        ll.addView(spacer(24))

        ll.addView(sliderRow("音乐音量", musicVol) {
            musicVol = it
            saveSettings()
        })
        ll.addView(sliderRow("音效音量", soundVol) {
            soundVol = it
            saveSettings()
        })
        ll.addView(sliderRow("屏幕亮度", bright) {
            bright = it
            applyBrightness()
            saveSettings()
        })

        ll.addView(spacer(30))
        ll.addView(actionBtn("返回主菜单") { showMainMenu() })
    }

    private fun sliderRow(title: String, value: Int, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val p = lpFull()
        p.topMargin = dp(18)
        box.layoutParams = p

        val label = tv(title + "：" + value + "%", 14f, C_TEXT, true)
        box.addView(label, lpFull())

        val sb = SeekBar(this)
        sb.max = 100
        sb.progress = value
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = title + "：" + progress + "%"
                onChange(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        box.addView(sb, lpFull())
        return box
    }

    private fun showAbout() {
        root.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(dp(24), dp(50), dp(24), dp(40))
        sv.addView(ll, lpFull())
        root.addView(sv, lpFull())

        ll.addView(tv("关于详情", 28f, C_ACCENT, true))
        ll.addView(spacer(16))

        ll.addView(tv("版本 1.0.0", 14f, C_TEXT, true))
        ll.addView(tv("后室卡牌之旅 · Backrooms Card Journey", 12f, C_DIM))
        ll.addView(spacer(20))

        ll.addView(tv("【攻略】", 16f, C_ACCENT, true))
        val guide = """
1. 每回合有 3 点行动力，每张卡牌消耗 1 点。
2. 出牌后按「结束回合」推进时间，怪物会在此时发动攻击。
3. 每到一个关卡的刷新间隔（1~10 回合，各关不同），就会有怪物出现。
4. 每回合可以花 2 金币从 3 张卡牌中购买 1 张。
5. 金币还可以找流浪商人购买特殊道具，道具随时可用。
6. 注意理智值：每 2 回合会自然下降 1 点，理智归零同样会失败。
7. 不同关卡的胜利条件不同：寻找线索、逃脱出口、破解谜题、清剿怪物、生存到底、击杀首领。
8. 通关 30 个关卡后可进入无限模式，每完成一轮 +1 分。
9. 每次通关后有极低概率（约 6%）进入特殊关卡；每通关 5 个关卡必定触发一次。
10. 特殊关卡不计入总关卡数，且好事件 : 坏事件 : 中立事件 ≈ 7 : 1 : 2。
        """.trimIndent()
        ll.addView(tv(guide, 13f, C_DIM))
        ll.addView(spacer(20))

        ll.addView(tv("【内容规模】", 16f, C_ACCENT, true))
        val content = """
· 卡牌：2000 张，分为食用牌、休息牌、功能牌、战斗牌、防御牌、移动牌、精神牌、交易牌 8 大类
· 特殊道具：1000 种，分为怪物类、命运类、希望类、毁灭类、治愈类、探索类、交易类、时间类、防御类、神秘类 10 大类
· 关卡：30 个主线关卡 + 无限关卡 + 特殊关卡
        """.trimIndent()
        ll.addView(tv(content, 13f, C_DIM))
        ll.addView(spacer(20))

        ll.addView(tv("【关于后室】", 16f, C_ACCENT, true))
        val lore = """
后室（The Backrooms）是起源于网络都市传说的一处异空间。
当你从现实的缝隙中「卡」出去，就会掉进这片无穷无尽的黄色房间——
潮湿地毯的味道、永不停歇的荧光灯嗡鸣，还有……别的东西。
        """.trimIndent()
        ll.addView(tv(lore, 13f, C_DIM))

        ll.addView(spacer(30))
        ll.addView(actionBtn("返回主菜单") { showMainMenu() })
    }
}

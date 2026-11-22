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
    private val C_PURPLE = 0xFFB06BE0.toInt()
    private val C_BLUE = 0xFF5AA8E0.toInt()

    private val prefs by lazy { getSharedPreferences("backrooms", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
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

    private fun lpFull(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun lpWeight(w: Float, m: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.weight = w
        p.setMargins(m, m, m, m)
        return p
    }

    private fun spacer(h: Int): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(1, dp(h))
        return v
    }

    private fun actionBtn(text: String, bg: Int = C_ACCENT, fg: Int = Color.BLACK, onClick: () -> Unit): TextView {
        val v = tv(text, 14f, fg, true)
        v.gravity = Gravity.CENTER
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
            } catch (e: Exception) {}
        }
    }

    private fun loadSettings() {
        soundVol = prefs.getInt("sound", 80)
        bright = prefs.getInt("bright", 100)
    }

    private fun saveSettings() {
        prefs.edit().putInt("sound", soundVol).putInt("bright", bright).apply()
    }

    private fun applyBrightness() {
        val attr = window.attributes
        attr.screenBrightness = bright.coerceIn(10, 100) / 100f
        window.attributes = attr
    }

    private fun saveGame() {
        val cid = engine.currentChar?.id ?: 0
        prefs.edit()
            .putBoolean("has_save", true)
            .putInt("level", engine.levelIndex)
            .putInt("coins", engine.coins)
            .putInt("score", engine.score)
            .putBoolean("infinite", engine.infiniteMode)
            .putInt("completed", engine.completed)
            .putInt("charId", cid)
            .apply()
    }

    private fun hasSave(): Boolean = prefs.getBoolean("has_save", false)

    private fun continueGame() {
        if (!hasSave()) {
            Toast.makeText(this, "还没有存档", Toast.LENGTH_SHORT).show()
            return
        }
        val charId = prefs.getInt("charId", 0)
        engine.currentChar = engine.characters.find { it.id == charId } ?: engine.characters[0]
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
        ll.setPadding(dp(24), dp(40), dp(24), dp(40))
        sv.addView(ll, lpFull())
        root.addView(sv, lpFull())

        val title = tv("后室卡牌之旅", 34f, C_ACCENT, true)
        title.gravity = Gravity.CENTER
        ll.addView(title, lpFull())
        ll.addView(spacer(20))

        ll.addView(menuButton("开始游戏") { showCharacterSelect() })
        ll.addView(menuButton("继续游戏") { continueGame() })
        ll.addView(menuButton("设置") { showSettings() })
        ll.addView(menuButton("关于详情") { showAbout() })
    }

    private fun showCharacterSelect() {
        root.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(dp(20), dp(20), dp(20), dp(20))
        sv.addView(ll, lpFull())
        root.addView(sv, lpFull())
        ll.addView(tv("选择角色", 24f, C_ACCENT, true))

        val maxUnlock = prefs.getInt("max_level", 0)
        val infScore = prefs.getInt("best_inf_score", 0)

        for (c in engine.characters) {
            val unlocked = when {
                c.unlockAt == -1 -> true
                c.unlockAt == -2 -> infScore >= 3
                else -> maxUnlock >= c.unlockAt
            }
            val txt = c.name + " (" + (if (unlocked) c.desc else "需通关第${c.unlockAt}关") + ")"
            val btn = actionBtn(txt, if (unlocked) C_PANEL2 else C_BG, C_TEXT) {
                if (unlocked) {
                    engine.newGame(c)
                    saveGame()
                    overDialogShown = false
                    showGame()
                } else {
                    Toast.makeText(this, "未解锁", Toast.LENGTH_SHORT).show()
                }
            }
            val p = lpFull()
            p.topMargin = dp(8)
            btn.layoutParams = p
            ll.addView(btn)
        }
        ll.addView(spacer(20))
        ll.addView(actionBtn("返回", C_PANEL2, C_TEXT) { showMainMenu() })
    }

    private fun update() {
        if (engine.gameOver) {
            saveGame()
            showGameOver()
            return
        }
        if (engine.activeEvent != null) {
            showEventDialog()
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

    private fun showEventDialog() {
        val evt = engine.activeEvent ?: return
        val b = AlertDialog.Builder(this)
        b.setTitle(evt.title)
        b.setMessage(evt.desc)
        b.setCancelable(false)
        b.setPositiveButton("确认") { _, _ ->
            engine.resolveEvent()
            update()
        }
        b.show()
    }

    private fun showGame() {
        root.removeAllViews()
        val s = engine.stage
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(s.bgStart, s.bgEnd))
        root.background = bg

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.setPadding(dp(10), dp(10), dp(10), dp(10))

        val status = LinearLayout(this)
        status.orientation = LinearLayout.HORIZONTAL
        main.addView(status, lpFull())
        status.addView(chip("生命 " + engine.hp + "/" + engine.maxHp, C_RED), lpWeight(1f, dp(3)))
        status.addView(chip("理智 " + engine.sanity, C_PURPLE), lpWeight(1f, dp(3)))
        status.addView(chip("金币 " + engine.coins, C_ACCENT), lpWeight(1f, dp(3)))
        status.addView(chip("进度 " + engine.progress, C_BLUE), lpWeight(1f, dp(3)))
        status.addView(chip("行动力 " + engine.ap, C_PANEL2), lpWeight(1f, dp(3)))

        val midLayout = LinearLayout(this)
        midLayout.orientation = LinearLayout.HORIZONTAL
        val midLp = lpFull()
        midLp.topMargin = dp(8)
        main.addView(midLayout, midLp)

        val leftBox = LinearLayout(this)
        leftBox.orientation = LinearLayout.VERTICAL
        leftBox.background = rounded(C_PANEL, dp(12).toFloat())
        leftBox.setPadding(dp(10), dp(10), dp(10), dp(10))
        midLayout.addView(leftBox, lpWeight(1f, dp(4)))

        leftBox.addView(tv(s.name, 16f, C_ACCENT, true), lpFull())
        leftBox.addView(tv("目标: " + s.goal.label + "  " + goalText(), 12f, C_TEXT, true), lpFull())

        val monScroll = ScrollView(this)
        leftBox.addView(monScroll, lpWeight(1f, 0))
        val monBox = LinearLayout(this)
        monBox.orientation = LinearLayout.VERTICAL
        monScroll.addView(monBox, lpFull())

        if (engine.monsters.isEmpty()) {
            monBox.addView(tv("周围暂时安全……", 12f, C_DIM), lpFull())
        } else {
            for (m in engine.monsters) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.VERTICAL
                row.background = roundedStroke(C_PANEL2, dp(8).toFloat(), dp(1), C_RED)
                row.setPadding(dp(8), dp(8), dp(8), dp(8))
                val rlp = lpFull()
                rlp.topMargin = dp(4)
                monBox.addView(row, rlp)
                row.addView(tv(m.name + " [" + m.skill + "]", 13f, C_RED, true), lpFull())
                row.addView(tv("HP " + m.hp + "/" + m.maxHp + "   ATK " + m.atk, 11f, C_DIM), lpFull())
            }
        }

        val rightBox = LinearLayout(this)
        rightBox.orientation = LinearLayout.VERTICAL
        midLayout.addView(rightBox, lpWeight(2f, dp(4)))

        val handScroll = HorizontalScrollView(this)
        handScroll.isHorizontalScrollBarEnabled = false
        rightBox.addView(handScroll, lpWeight(1f, 0))
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
        row1.addView(actionBtn("买卡", C_BLUE) { buyCardDialog() }, lpWeight(1f, dp(4)))
        row1.addView(actionBtn("商人", C_PURPLE) { openShop() }, lpWeight(1f, dp(4)))
        row1.addView(actionBtn("道具", C_PANEL2, C_TEXT) { openInventory() }, lpWeight(1f, dp(4)))
        row1.addView(actionBtn("菜单", C_PANEL2, C_TEXT) {
            saveGame()
            showMainMenu()
        }, lpWeight(1f, dp(4)))

        root.addView(main, lpFull())
    }

    private fun goalText(): String {
        val s = engine.stage
        return when (s.goal) {
            GoalType.KILL_ALL, GoalType.BOSS -> "剩余 " + engine.monsters.size + " 只"
            GoalType.SURVIVE -> "存活 " + engine.turn + "/" + s.goalValue
            else -> engine.progress.toString() + "/" + s.goalValue
        }
    }

    private fun makeCardView(card: Card, onClick: () -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = roundedStroke(C_PANEL2, dp(8).toFloat(), dp(2), card.category.color)
        box.setPadding(dp(6), dp(6), dp(6), dp(6))
        box.isClickable = true
        box.setOnClickListener {
            playClick()
            onClick()
        }
        val p = LinearLayout.LayoutParams(dp(100), dp(140))
        p.setMargins(dp(3), 0, dp(3), 0)
        box.layoutParams = p
        val nameTv = tv(card.name, 12f, C_TEXT, true)
        nameTv.maxLines = 2
        box.addView(nameTv, lpFull())
        box.addView(tv(card.category.label, 10f, card.category.color, true), lpFull())
        val descTv = tv(card.desc, 10f, C_DIM)
        descTv.maxLines = 3
        box.addView(descTv, lpFull())
        return box
    }

    private fun buyCardDialog() {
        if (engine.boughtThisTurn || engine.coins < 2) {
            Toast.makeText(this, "无法购买", Toast.LENGTH_SHORT).show()
            return
        }
        val picks = CardLibrary.random(3)
        val labels = Array<CharSequence>(picks.size) { i -> picks[i].name + "\n" + picks[i].desc }
        AlertDialog.Builder(this).setTitle("花 2 金币购买").setItems(labels) { _, which ->
            engine.coins -= 2
            engine.hand.add(picks[which])
            engine.boughtThisTurn = true
            update()
        }.setNegativeButton("取消", null).show()
    }

    private fun openShop() {
        val picks = ItemLibrary.random(3)
        val labels = Array<CharSequence>(picks.size) { i -> picks[i].name + " (" + picks[i].price + "金)\n" + picks[i].desc }
        AlertDialog.Builder(this).setTitle("流浪商人 (你有 " + engine.coins + " 金币)").setItems(labels) { _, which ->
            val item = picks[which]
            if (engine.coins >= item.price) {
                engine.coins -= item.price
                engine.inventory.add(item)
            } else {
                Toast.makeText(this, "金币不足", Toast.LENGTH_SHORT).show()
            }
            update()
        }.setNegativeButton("离开", null).show()
    }

    private fun openInventory() {
        if (engine.inventory.isEmpty()) {
            Toast.makeText(this, "背包为空", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = Array<CharSequence>(engine.inventory.size) { i -> engine.inventory[i].name + "\n" + engine.inventory[i].desc }
        AlertDialog.Builder(this).setTitle("背包").setItems(labels) { _, which ->
            engine.useItem(which)
            update()
        }.setNegativeButton("关闭", null).show()
    }

    private fun onStageCleared() {
        engine.cleared = false
        if (!engine.infiniteMode) {
            prefs.edit().putInt("max_level", max(prefs.getInt("max_level", 0), engine.levelIndex + 1)).apply()
        } else {
            prefs.edit().putInt("best_inf_score", max(prefs.getInt("best_inf_score", 0), engine.score)).apply()
        }

        val force = engine.completed % 3 == 0
        val roll = Math.random()
        if (force || roll < 0.12) {
            engine.isSpecial = true
            engine.specialStageName = engine.generateSpecialStageName()
            showSpecialDialog()
        } else {
            showClearedDialog()
        }
    }

    private fun showClearedDialog() {
        val s = engine.stage
        AlertDialog.Builder(this).setTitle("通关！").setMessage("通过了「" + s.name + "」\n获得 " + s.reward + " 金币").setCancelable(false)
            .setPositiveButton("继续") { _, _ ->
                engine.advance()
                if (engine.finishedAll) showAllClearedDialog() else update()
            }.show()
    }

    private fun showAllClearedDialog() {
        AlertDialog.Builder(this).setTitle("恭喜！").setMessage("你通过了全部 30 个关卡！\n进入无限模式冲刺高分吧！").setCancelable(false)
            .setPositiveButton("进入无限") { _, _ ->
                engine.infiniteMode = true
                engine.score = 0
                engine.startStage(0)
                update()
            }
            .setNegativeButton("主菜单") { _, _ -> showMainMenu() }.show()
    }

    private fun showSpecialDialog() {
        val r = Math.random()
        val type = if (r < 0.7) "good" else if (r < 0.8) "bad" else "neutral"
        val b = AlertDialog.Builder(this).setTitle(engine.specialStageName).setCancelable(false)

        when (type) {
            "good" -> {
                b.setMessage("这里是安全的避风港。\n生命与理智完全恢复，获得道具。")
                b.setPositiveButton("好耶") { _, _ ->
                    engine.hp = engine.maxHp
                    engine.sanity = 100
                    engine.inventory.add(ItemLibrary.random(1)[0])
                    engine.isSpecial = false
                    engine.advance()
                    if (engine.finishedAll) showAllClearedDialog() else update()
                }
            }
            "bad" -> {
                b.setMessage("你掉进了陷阱！\n失去 40 点生命。")
                b.setPositiveButton("该死") { _, _ ->
                    engine.hp -= 40
                    engine.checkState()
                    engine.isSpecial = false
                    engine.advance()
                    if (engine.finishedAll) showAllClearedDialog() else update()
                }
            }
            else -> {
                b.setMessage("前方有两条路：\n左路(金币+20)，右路(进度+50)")
                b.setPositiveButton("左路") { _, _ ->
                    engine.coins += 20
                    engine.isSpecial = false
                    engine.advance()
                    if (engine.finishedAll) showAllClearedDialog() else update()
                }
                b.setNegativeButton("右路") { _, _ ->
                    engine.progress += 50
                    engine.isSpecial = false
                    engine.advance()
                    if (engine.finishedAll) showAllClearedDialog() else update()
                }
            }
        }
        b.show()
    }

    private fun showGameOver() {
        if (overDialogShown) return
        overDialogShown = true
        val sb = "到达关卡：" + engine.stage.name + "\n通关数量：" + engine.completed
        AlertDialog.Builder(this).setTitle("你倒下了").setMessage(sb).setCancelable(false)
            .setPositiveButton("重新开始") { _, _ ->
                overDialogShown = false
                showCharacterSelect()
            }
            .setNegativeButton("主菜单") { _, _ ->
                overDialogShown = false
                showMainMenu()
            }.show()
    }

    private fun showSettings() {
        root.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(dp(24), dp(24), dp(24), dp(24))
        sv.addView(ll, lpFull())
        root.addView(sv, lpFull())
        ll.addView(tv("设置", 28f, C_ACCENT, true))
        ll.addView(spacer(24))

        val label1 = tv("音效音量：" + soundVol + "%", 14f, C_TEXT, true)
        ll.addView(label1)
        val sb1 = SeekBar(this)
        sb1.max = 100
        sb1.progress = soundVol
        sb1.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                label1.text = "音效音量：" + p + "%"
                soundVol = p
                saveSettings()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        ll.addView(sb1)

        val label2 = tv("屏幕亮度：" + bright + "%", 14f, C_TEXT, true)
        ll.addView(label2)
        val sb2 = SeekBar(this)
        sb2.max = 100
        sb2.progress = bright
        sb2.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                label2.text = "屏幕亮度：" + p + "%"
                bright = p
                applyBrightness()
                saveSettings()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        ll.addView(sb2)

        ll.addView(spacer(30))
        ll.addView(actionBtn("返回主菜单") { showMainMenu() })
    }

    private fun showAbout() {
        root.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(dp(24), dp(24), dp(24), dp(24))
        sv.addView(ll, lpFull())
        root.addView(sv, lpFull())
        ll.addView(tv("关于详情 v2.0", 28f, C_ACCENT, true))
        ll.addView(spacer(16))
        ll.addView(tv("已更新：\n1. 特殊关卡增加至 100+ 种\n2. 怪物加入了技能\n3. 新增 12 个角色\n4. 新增 200 个事件\n5. 改为横屏", 14f, C_TEXT))
        ll.addView(spacer(30))
        ll.addView(actionBtn("返回主菜单") { showMainMenu() })
    }
}

package com.backrooms.cardjourney

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max

/**
 * 显示用版本号：启动时从 APK 的 versionName 读取，
 * 避免再出现 build.gradle 已升到 0.0.9、界面还写着 0.0.7 的情况。
 */
internal var APP_VERSION = "0.1.0-beta2"
private const val ADMIN_PASSWORD = "15807050240"
private const val ADMIN_TAP_COUNT = 7

private data class AdminMonster(val name: String, val hp: Int, val atk: Int, val skill: String)

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var bg: BackroomsBackgroundView
    private lateinit var content: FrameLayout
    private var board: GameBoardView? = null
    private val engine = GameEngine()
    private val bgm = BgmPlayer()

    private var musicVol = 70
    private var soundVol = 80
    private var bright = 100
    private var overDialogShown = false
    private var toneGen: ToneGenerator? = null
    private var screen = "menu"

    // 管理员：版本号连点打开入口，开始游戏时输入密码后生效
    private var adminGate = false
    private var adminActive = false
    private var versionTaps = 0

    private var currentTheme = -1

    private val C_BG = 0xFF0D0D0D.toInt()
    private val C_PANEL = 0xFF1B1B1B.toInt()
    private val C_PANEL2 = 0xFF262626.toInt()
    private val C_ACCENT = 0xFFE8C547.toInt()
    private val C_TEXT = 0xFFEDEDED.toInt()
    private val C_DIM = 0xFF8F8F8F.toInt()
    private val C_RED = 0xFFE05252.toInt()
    private val C_PURPLE = 0xFFB06BE0.toInt()
    private val C_BLUE = 0xFF5AA8E0.toInt()
    private val C_GREEN = 0xFF66BB6A.toInt()

    private val prefs by lazy { getSharedPreferences("backrooms", Context.MODE_PRIVATE) }

    private val flicker = object : Runnable {
        override fun run() {
            if (screen == "game") {
                board?.refresh()
                bg.invalidate()
                root.postDelayed(this, 800)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        bg = BackroomsBackgroundView(this)
        root.addView(bg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content = FrameLayout(this)
        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        APP_VERSION = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: APP_VERSION
        } catch (e: Exception) {
            APP_VERSION
        }
        loadSettings()
        applyBrightness()
        showMainMenu()
    }

    override fun onResume() {
        super.onResume()
        syncMusic(true)
    }

    override fun onPause() {
        super.onPause()
        bgm.stop()
        currentTheme = -1
        root.removeCallbacks(flicker)
    }

    override fun onDestroy() {
        super.onDestroy()
        bgm.stop()
        toneGen?.release()
        toneGen = null
        root.removeCallbacks(flicker)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (screen) {
            "game" -> { saveGame(); showMainMenu() }
            "menu" -> super.onBackPressed()
            else -> showMainMenu()
        }
    }

    // ---------- 基础 UI ----------

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
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun lpMatch(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

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
        val v = tv(text, 13f, fg, true)
        v.gravity = Gravity.CENTER
        v.background = rounded(bg, dp(8).toFloat())
        v.setPadding(dp(6), dp(10), dp(6), dp(10))
        v.isClickable = true
        v.setOnClickListener {
            playClick()
            onClick()
        }
        return v
    }

    private fun chip(text: String, color: Int): TextView {
        val v = tv(text, 11f, Color.WHITE, true)
        v.gravity = Gravity.CENTER
        v.background = rounded(color, dp(6).toFloat())
        v.setPadding(dp(3), dp(5), dp(3), dp(5))
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
        if (soundVol <= 0) return
        try {
            // 每次点击都 new ToneGenerator 会造成明显延迟和 GC 抖动，这里复用一个实例
            val tg = toneGen ?: ToneGenerator(AudioManager.STREAM_MUSIC, soundVol * 40 / 100).also { toneGen = it }
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
        } catch (e: Exception) { }
    }

    private fun loadSettings() {
        musicVol = prefs.getInt("music", 70)
        soundVol = prefs.getInt("sound", 80)
        bright = prefs.getInt("bright", 100)
    }

    private fun saveSettings() {
        prefs.edit().putInt("music", musicVol).putInt("sound", soundVol).putInt("bright", bright).apply()
    }

    private fun applyBrightness() {
        val attr = window.attributes
        attr.screenBrightness = bright.coerceIn(10, 100) / 100f
        window.attributes = attr
    }

    /** 切换界面时统一复位背景：只有对局画面才露出透视走廊 */
    private fun beginScreen(name: String, showScene: Boolean) {
        screen = name
        content.removeAllViews()
        if (showScene) {
            bg.visibility = View.VISIBLE
            content.background = null
        } else {
            bg.visibility = View.GONE
            content.background = ColorDrawable(C_BG)
        }
        if (!showScene) {
            root.removeCallbacks(flicker)
            bgm.stop()
            currentTheme = -1
        }
    }

    private fun addScreen(v: View) {
        content.addView(v, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // ---------- 音乐 ----------

    private fun syncMusic(force: Boolean = false) {
        if (screen != "game" || musicVol <= 0) {
            bgm.stop()
            currentTheme = -1
            return
        }
        val s = engine.stage
        if (force || currentTheme != s.theme) {
            currentTheme = s.theme
            bgm.start(BgmThemes.forStage(engine.levelIndex, s.theme), musicVol)
        } else if (!bgm.isPlaying()) {
            bgm.start(BgmThemes.forStage(engine.levelIndex, s.theme), musicVol)
        }
    }

    // ---------- 存档 ----------

    private fun saveGame() {
        // 死亡存档没有意义；NPC 未落地时不存，避免半状态
        if (engine.gameOver || engine.pendingNpc != null) return
        val cid = engine.currentChar?.id ?: 0
        prefs.edit()
            .putBoolean("has_save", true)
            .putInt("level", engine.levelIndex)
            .putInt("coins", engine.coins)
            .putInt("score", engine.score)
            .putBoolean("infinite", engine.infiniteMode)
            .putInt("completed", engine.completed)
            .putInt("charId", cid)
            .putInt("hp", engine.hp)
            .putInt("maxHp", engine.maxHp)
            .putInt("sanity", engine.sanity)
            .putInt("turn", engine.turn)
            .putInt("ap", engine.ap)
            .putInt("shield", engine.shield)
            .putInt("progress", engine.progress)
            .putInt("target", engine.target)
            .putString("hand", encodeCards(engine.hand))
            .putString("inv", encodeItems(engine.inventory))
            .putString("mon", encodeMonsters(engine.monsters))
            .apply()
    }

    private fun encodeCards(cards: List<Card>): String = cards.joinToString("|") { it.id.toString() }

    private fun decodeCards(s: String): List<Card> =
        s.split("|").mapNotNull { id -> id.toIntOrNull()?.let { CardLibrary.byId(it) } }

    private fun encodeItems(items: List<Item>): String = items.joinToString("|") { it.id.toString() }

    private fun decodeItems(s: String): List<Item> =
        s.split("|").mapNotNull { id -> id.toIntOrNull()?.let { ItemLibrary.byId(it) } }

    private fun encodeMonsters(list: List<Monster>): String =
        list.joinToString("|") { m -> m.name + "," + m.hp + "," + m.maxHp + "," + m.atk + "," + m.skill }

    private fun decodeMonsters(s: String): List<Monster> =
        s.split("|").mapNotNull { rec ->
            val p = rec.split(",")
            if (p.size < 5) return@mapNotNull null
            val hp = p[1].toIntOrNull() ?: return@mapNotNull null
            val maxHp = p[2].toIntOrNull() ?: hp
            val atk = p[3].toIntOrNull() ?: return@mapNotNull null
            Monster(p[0], hp, maxHp, atk, p[4])
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
        engine.maxHp = prefs.getInt("maxHp", 100)
        engine.hp = prefs.getInt("hp", engine.maxHp).coerceIn(1, engine.maxHp)
        engine.sanity = prefs.getInt("sanity", 100).coerceIn(1, 100)
        engine.turn = prefs.getInt("turn", 0)
        engine.ap = prefs.getInt("ap", 3)
        engine.shield = prefs.getInt("shield", 0)
        engine.progress = prefs.getInt("progress", 0)
        engine.target = prefs.getInt("target", 0)
        engine.cleared = false
        engine.gameOver = false
        engine.isSpecial = false
        engine.activeEvent = null
        engine.pendingNpc = null
        engine.message = "从存档继续"
        engine.monsters.clear()
        engine.monsters.addAll(decodeMonsters(prefs.getString("mon", "") ?: ""))
        engine.hand.clear()
        engine.hand.addAll(decodeCards(prefs.getString("hand", "") ?: ""))
        if (engine.hand.isEmpty()) engine.hand.addAll(CardLibrary.startingHand(GameEngine.HAND_TARGET))
        engine.inventory.clear()
        engine.inventory.addAll(decodeItems(prefs.getString("inv", "") ?: ""))
        engine.offerCards.clear()
        engine.offerRerolls = 0
        engine.ensureOffers()
        overDialogShown = false
        showGame()
    }

    // ---------- 主菜单 ----------

    private fun showMainMenu() {
        beginScreen("menu", true)
        val v = CanvasMenuView(this, menuHost)
        v.setScene(0, 0)
        addScreen(v)
        syncMenuMusic()
    }

    private fun syncMenuMusic() {
        if (screen != "menu" || musicVol <= 0) return
        currentTheme = -1
        bgm.start(BgmThemes.forStage(0, 0), musicVol)
        currentTheme = 0
    }

    private val menuHost = object : CanvasMenuView.Host {
        override fun menuStartGame() { beginGameFlow() }

        override fun menuContinueGame() { continueGame() }

        override fun menuOptions() {}

        override fun menuExitApp() { finish() }

        override fun musicVolume(): Int = musicVol

        override fun setMusicVolume(v: Int) {
            musicVol = v
            saveSettings()
            if (v <= 0) bgm.stop() else if (screen == "menu") syncMenuMusic() else syncMusic(true)
        }

        override fun soundVolume(): Int = soundVol

        override fun setSoundVolume(v: Int) { soundVol = v; saveSettings() }

        override fun brightness(): Int = bright

        override fun setBrightness(v: Int) { bright = v; saveSettings(); applyBrightness() }
    }

    private fun beginGameFlow() {
        showCharacterSelect()
    }

    private fun showAdminPassword() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "输入管理员密码"
        input.setTextColor(C_TEXT)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(10), dp(20), dp(10))
        box.addView(input, lpFull())
        AlertDialog.Builder(this)
            .setTitle("管理员验证")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("确认") { _, _ ->
                if (input.text.toString().trim() == ADMIN_PASSWORD) {
                    adminActive = true
                    Toast.makeText(this, "管理员已登录", Toast.LENGTH_SHORT).show()
                } else {
                    adminActive = false
                    Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show()
                }
                showCharacterSelect()
            }
            .setNegativeButton("取消") { _, _ ->
                adminActive = false
                showCharacterSelect()
            }
            .show()
    }

    private fun showCharacterSelect() {
        beginScreen("char", false)
        val sv = ScrollView(this)
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(dp(20), dp(20), dp(20), dp(20))
        sv.addView(ll, lpFull())
        addScreen(sv)
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

    // ---------- 管理员面板 ----------

    private fun ensureGameRunning() {
        if (engine.currentChar == null) {
            engine.newGame(engine.characters[0])
        }
    }

    private fun showAdminPanel() {
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(dp(16), dp(10), dp(16), dp(10))
        val sv = ScrollView(this)
        sv.addView(ll, lpFull())
        var panel: AlertDialog? = null

        fun addBtn(text: String, color: Int, fg: Int, action: () -> Unit) {
            val b = actionBtn(text, color, fg) {
                // 先收起面板，避免二级菜单叠在面板上
                panel?.dismiss()
                action()
            }
            val p = lpFull()
            p.topMargin = dp(6)
            b.layoutParams = p
            ll.addView(b)
        }

        ll.addView(tv("管理员面板", 16f, C_ACCENT, true), lpFull())
        ll.addView(tv("测试用，改动只影响当前运行，不会写入存档", 11f, C_DIM), lpFull())
        ll.addView(spacer(8))

        addBtn("创造怪物", C_PANEL2, C_TEXT) {
            ensureGameRunning()
            adminSpawnMenu()
        }
        addBtn("销毁怪物", C_PANEL2, C_TEXT) {
            ensureGameRunning()
            adminKillMenu()
        }
        addBtn("跳关", C_BLUE, Color.BLACK) {
            ensureGameRunning()
            adminJumpMenu()
        }
        addBtn("召唤 NPC", C_PURPLE, Color.WHITE) {
            ensureGameRunning()
            adminSummonNpcMenu()
        }
        addBtn("直接通关本关", C_GREEN, Color.BLACK) {
            ensureGameRunning()
            engine.adminEndStage()
            if (engine.cleared) {
                saveGame()
                onStageCleared()
            } else {
                update()
            }
        }
        addBtn("回满生命 / 理智", C_PANEL2, C_TEXT) {
            ensureGameRunning()
            engine.adminHeal()
            update()
        }
        addBtn("金币 +100", C_PANEL2, C_TEXT) {
            ensureGameRunning()
            engine.adminCoins(100)
            update()
        }
        addBtn("退出管理员", C_RED, Color.WHITE) {
            adminActive = false
            Toast.makeText(this, "管理员已退出", Toast.LENGTH_SHORT).show()
            if (screen == "game") update() else showMainMenu()
        }

        panel = AlertDialog.Builder(this)
            .setView(sv)
            .setNegativeButton("收起", null)
            .create()
        panel.show()
    }

    private fun adminSpawnMenu() {
        val templates = listOf(
            AdminMonster("窃皮者 · 弱", 30, 6, "无"),
            AdminMonster("猎犬 · 中", 60, 10, "无"),
            AdminMonster("笑魇 · 强", 120, 14, "狂暴"),
            AdminMonster("自爆体", 40, 18, "自爆"),
            AdminMonster("破甲者", 90, 12, "破甲"),
            AdminMonster("噬魂者", 80, 10, "噬魂"),
            AdminMonster("深渊首领", 220, 22, "无")
        )
        val labels = Array<CharSequence>(templates.size) { i ->
            val t = templates[i]
            t.name + "   HP " + t.hp + " / ATK " + t.atk + " / " + t.skill
        }
        AlertDialog.Builder(this)
            .setTitle("创造怪物")
            .setItems(labels) { _, which ->
                val t = templates[which]
                engine.adminSpawn(t.name, t.hp, t.atk, t.skill)
                if (screen == "game") update() else showGame()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun adminKillMenu() {
        if (engine.monsters.isEmpty()) {
            Toast.makeText(this, "场上没有怪物", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = ArrayList<CharSequence>()
        labels.add("全部销毁")
        for (m in engine.monsters) {
            labels.add(m.name + "  HP " + m.hp + "/" + m.maxHp + "  [" + m.skill + "]")
        }
        AlertDialog.Builder(this)
            .setTitle("销毁怪物")
            .setItems(Array(labels.size) { labels[it] }) { _, which ->
                if (which == 0) engine.adminKillAll() else engine.adminKill(which - 1)
                update()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun adminJumpMenu() {
        val labels = ArrayList<CharSequence>()
        labels.add("无限模式 · 第 1 轮")
        for (s in StageLibrary.stages) labels.add(s.name + "   " + s.goal.label)
        AlertDialog.Builder(this)
            .setTitle("跳关")
            .setItems(Array(labels.size) { labels[it] }) { _, which ->
                if (which == 0) engine.adminJumpInfinite(0) else engine.adminJumpTo(which - 1)
                showGame()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun adminSummonNpcMenu() {
        val kinds = NpcKind.values()
        val labels = Array<CharSequence>(kinds.size) { i ->
            kinds[i].label + (if (kinds[i].friendly) "（友善）" else "（敌对）")
        }
        AlertDialog.Builder(this)
            .setTitle("召唤 NPC")
            .setItems(labels) { _, which ->
                engine.adminSummonNpc(kinds[which])
                showGame()
                showNpcDialog()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- 主循环 ----------

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
        if (engine.pendingNpc != null) {
            showNpcDialog()
            return
        }
        if (engine.pendingMerchant && engine.merchantStock.isNotEmpty()) {
            showMerchantDialog()
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

        val effectMsg = StringBuilder()
        effectMsg.append(evt.desc).append("\n\n【结算结果】\n")
        if (evt.hpChange != 0) effectMsg.append("生命 ").append(if (evt.hpChange > 0) "+" else "").append(evt.hpChange).append("\n")
        if (evt.coinChange != 0) effectMsg.append("金币 ").append(if (evt.coinChange > 0) "+" else "").append(evt.coinChange).append("\n")
        if (evt.progChange != 0) effectMsg.append("进度 ").append(if (evt.progChange > 0) "+" else "").append(evt.progChange).append("\n")
        if (evt.sanityChange != 0) effectMsg.append("理智 ").append(if (evt.sanityChange > 0) "+" else "").append(evt.sanityChange).append("\n")
        if (evt.hpChange == 0 && evt.coinChange == 0 && evt.progChange == 0 && evt.sanityChange == 0) {
            effectMsg.append("无事发生……\n")
        }

        AlertDialog.Builder(this)
            .setTitle(evt.title)
            .setMessage(effectMsg.toString())
            .setCancelable(false)
            .setPositiveButton("确认") { dialog, _ ->
                dialog.dismiss()
                engine.resolveEvent()
                // 使用 post 确保关闭弹窗后再重绘界面，解决卡黑屏问题
                root.post { update() }
            }
            .show()
    }

    // ---------- NPC ----------

    private fun showNpcDialog() {
        val npc = engine.pendingNpc ?: return
        val msg = npc.greeting + "\n\n" + if (npc.kind.friendly) "他看起来可以交谈，也愿意卖点东西。" else "他看起来不太友善，最好小心。"
        AlertDialog.Builder(this)
            .setTitle(npc.name + " · " + npc.kind.label)
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton(if (engine.npcTalked) "继续前进" else "交谈") { dialog, _ ->
                if (engine.npcTalked) {
                    dialog.dismiss()
                    engine.resolveNpc()
                    update()
                } else {
                    val result = engine.npcTalk()
                    dialog.dismiss()
                    AlertDialog.Builder(this)
                        .setTitle("交谈")
                        .setMessage(result)
                        .setPositiveButton("好") { d2, _ ->
                            d2.dismiss()
                            if (engine.pendingNpc != null) showNpcDialog() else update()
                        }
                        .show()
                }
            }
            .setNeutralButton("交易") { dialog, _ ->
                dialog.dismiss()
                showNpcTradeDialog()
            }
            .setNegativeButton("离开") { dialog, _ ->
                dialog.dismiss()
                engine.resolveNpc()
                update()
            }
            .show()
    }

    private fun showNpcTradeDialog() {
        val npc = engine.pendingNpc ?: return
        if (engine.npcStock.isEmpty()) {
            Toast.makeText(this, "他没有东西可卖了", Toast.LENGTH_SHORT).show()
            showNpcDialog()
            return
        }
        val labels = Array<CharSequence>(engine.npcStock.size) { i ->
            val st = engine.npcStock[i]
            st.name + "  (" + st.price + "金 · " + st.label + ")\n" + st.desc
        }
        AlertDialog.Builder(this)
            .setTitle(npc.name + " 的小摊（你有 " + engine.coins + " 金）")
            .setItems(labels) { _, which ->
                val result = engine.npcBuy(which)
                Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                if (engine.pendingNpc != null) showNpcDialog() else update()
            }
            .setNegativeButton("不买了", null)
            .show()
    }

    // ---------- 对局界面 ----------

    private fun showGame() {
        // 整个对局画面交给自绘的 GameBoardView：与 PC 端同一套手绘布局
        beginScreen("game", false)
        val s = engine.stage
        val v = GameBoardView(this, engine, boardHost)
        v.setScene(s.theme, if (engine.infiniteMode) 1000 + engine.score else engine.levelIndex)
        board = v
        addScreen(v)
        syncMusic()
        root.removeCallbacks(flicker)
        root.postDelayed(flicker, 800)
    }

    private val boardHost = object : GameBoardView.Host {
        override fun boardAfterAction() {
            update()
        }

        override fun boardEndTurn() {
            when (engine.endTurn()) {
                1 -> showEventDialog()
                2 -> showNpcDialog()
                3 -> showMerchantDialog()
                else -> update()
            }
        }

        override fun boardRestart() {
            val c = engine.currentChar ?: engine.characters[0]
            engine.newGame(c)
            update()
        }

        override fun boardOptions() {
            showBoardOptions()
        }

        override fun boardExitToMenu() {
            saveGame()
            showMainMenu()
        }

        override fun boardAdminPassword() {
            showAdminPasswordInline()
        }

        override fun boardUseItem(index: Int) {
            engine.useItem(index)
            update()
        }

        override fun isAdmin(): Boolean = adminActive

        override fun musicVolume(): Int = musicVol

        override fun setMusicVolume(v: Int) {
            musicVol = v
            saveSettings()
            if (v <= 0) bgm.stop() else syncMusic(true)
        }

        override fun soundVolume(): Int = soundVol

        override fun setSoundVolume(v: Int) { soundVol = v; saveSettings() }

        override fun brightnessValue(): Int = bright

        override fun setBrightness(v: Int) { bright = v; saveSettings(); applyBrightness() }
    }

    /** 对局内的选项：只调音量与亮度，关闭后回到对局 */
    private fun showBoardOptions() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(18), dp(10), dp(18), dp(6))

        fun slider(title: String, value: Int, apply: (Int) -> Unit) {
            box.addView(tv(title + "  " + value, 12f, C_TEXT, true))
            val bar = SeekBar(this)
            bar.max = 100
            bar.progress = value
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?, p: Int, fromUser: Boolean) {
                    apply(p)
                    (box.getChildAt(box.childCount - 2) as android.widget.TextView).text = title + "  " + p
                }

                override fun onStartTrackingTouch(b: SeekBar?) {}
                override fun onStopTrackingTouch(b: SeekBar?) {}
            })
            box.addView(bar)
        }
        slider("音乐音量", musicVol) { v ->
            musicVol = v
            saveSettings()
            syncMusic(force = true)
        }
        slider("音效音量", soundVol) { v ->
            soundVol = v
            saveSettings()
        }
        slider("界面亮度", bright) { v ->
            bright = v
            saveSettings()
            applyBrightness()
        }

        AlertDialog.Builder(this)
            .setTitle("选项")
            .setView(box)
            .setPositiveButton("返回对局") { _, _ -> showGame() }
            .show()
    }

    /** 对局菜单左下角版本号连点 6 次的入口 */
    private fun showAdminPasswordInline() {
        if (adminActive) {
            Toast.makeText(this, "管理员已登录", Toast.LENGTH_SHORT).show()
            return
        }
        val field = EditText(this)
        field.inputType = InputType.TYPE_CLASS_NUMBER
        field.hint = "管理员密码"
        AlertDialog.Builder(this)
            .setTitle("管理员验证")
            .setView(field)
            .setPositiveButton("确定") { _, _ ->
                if (field.text.toString().trim() == ADMIN_PASSWORD) {
                    adminActive = true
                    Toast.makeText(this, "管理员已登录", Toast.LENGTH_SHORT).show()
                } else {
                    adminActive = false
                    Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show()
                }
                showGame()
            }
            .setNegativeButton("取消") { _, _ -> showGame() }
            .show()
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

        val p = LinearLayout.LayoutParams(dp(85), ViewGroup.LayoutParams.MATCH_PARENT)
        p.setMargins(dp(4), 0, dp(4), 0)
        box.layoutParams = p

        val nameTv = tv(card.name, 12f, C_TEXT, true)
        nameTv.maxLines = 2
        box.addView(nameTv, lpFull())

        box.addView(tv(card.category.label, 10f, card.category.color, true), lpFull())
        box.addView(tv(card.rarity + " · " + card.cost + "金", 10f, card.rarityColor, true), lpFull())

        val descTv = tv(card.desc, 10f, C_DIM)
        descTv.maxLines = 3
        box.addView(descTv, lpFull())
        return box
    }

    // ---------- 商店 / 买卡 ----------

    private fun rerollWithUi() {
        val cost = engine.offerRerollCost()
        when (engine.rerollOffers()) {
            0 -> Toast.makeText(this, "已刷新报价（-" + cost + " 金）", Toast.LENGTH_SHORT).show()
            1 -> Toast.makeText(this, "金币不足", Toast.LENGTH_SHORT).show()
            else -> Toast.makeText(this, "本回合最多刷 4 次", Toast.LENGTH_SHORT).show()
        }
        update()
    }

    /** 回合结束时随机出现的商人：买装备，装备直接加攻/甲 */
    private fun showMerchantDialog() {
        val stock = engine.merchantStock
        if (stock.isEmpty()) {
            engine.pendingMerchant = false
            showGame()
            return
        }
        val labels = Array<CharSequence>(stock.size) { i ->
            val e = stock[i]
            e.name + "  (" + e.price + "金 · " + e.slot.label + " · " + e.tier + ")\n" +
                (if (e.atk > 0) "攻击 +" + e.atk + "  " else "") +
                (if (e.armor > 0) "护甲 +" + e.armor + "  " else "") + e.desc
        }
        AlertDialog.Builder(this)
            .setTitle("流浪商人（你有 " + engine.coins + " 金 · 装备 " + engine.equipment.size + "/" + GameEngine.MAX_EQUIP + "）")
            .setItems(labels) { _, which ->
                val msg = when (engine.buyEquipment(which)) {
                    0 -> "买下并装备了 " + stock[which].name
                    1 -> "金币不足"
                    2 -> "装备位已满"
                    else -> "这件已经卖掉了"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (engine.merchantStock.isNotEmpty()) showMerchantDialog() else { engine.pendingMerchant = false; update() }
            }
            .setNegativeButton("离开") { _, _ -> engine.pendingMerchant = false; showGame() }
            .setCancelable(false)
            .show()
    }

    /** 查看已装备的东西 */
    private fun showEquipmentDialog() {
        if (engine.equipment.isEmpty()) {
            Toast.makeText(this, "还没装备任何东西 · 回合结束时商人可能出现", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = Array<CharSequence>(engine.equipment.size) { i ->
            val e = engine.equipment[i]
            e.name + " (" + e.slot.label + ")\n" +
                (if (e.atk > 0) "攻击 +" + e.atk + "  " else "") +
                (if (e.armor > 0) "护甲 +" + e.armor + "  " else "") + e.desc
        }
        AlertDialog.Builder(this)
            .setTitle("装备 " + engine.equipment.size + "/" + GameEngine.MAX_EQUIP +
                "（攻 +" + engine.equipAtk() + " · 甲 +" + engine.equipArmor() + "）")
            .setItems(labels, null)
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun buyCardDialog() {
        engine.ensureOffers()
        if (engine.offerCards.isEmpty()) {
            Toast.makeText(this, "本回合的报价都买完了，可以刷牌", Toast.LENGTH_SHORT).show()
            return
        }
        if (engine.hand.size >= GameEngine.HAND_MAX) {
            Toast.makeText(this, "手牌已满", Toast.LENGTH_SHORT).show()
            return
        }
        // 同一回合内取消再点开，报价不变：不会再被用来免费刷牌
        val labels = Array<CharSequence>(engine.offerCards.size) { i ->
            val c = engine.offerCards[i]
            c.name + "  (" + c.cost + "金 · " + c.rarity + ")\n" + c.desc
        }
        AlertDialog.Builder(this)
            .setTitle("买卡（你有 " + engine.coins + " 金币）")
            .setItems(labels) { _, which ->
                val msg = when (engine.buyOffer(which)) {
                    0 -> "买入成功"
                    1 -> "金币不足"
                    2 -> "手牌已满"
                    else -> "这件已经卖掉了"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                update()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openShop() {
        val picks = ItemLibrary.random(3)
        val labels = Array<CharSequence>(picks.size) { i ->
            picks[i].name + " (" + picks[i].price + "金)\n" + picks[i].desc
        }
        AlertDialog.Builder(this).setTitle("流浪商人（你有 " + engine.coins + " 金币)").setItems(labels) { _, which ->
            val item = picks[which]
            if (engine.coins >= item.price) {
                engine.coins -= item.price
                engine.inventory.add(item)
                Toast.makeText(this, "买下 " + item.name, Toast.LENGTH_SHORT).show()
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
        val labels = Array<CharSequence>(engine.inventory.size) { i ->
            engine.inventory[i].name + "\n" + engine.inventory[i].desc
        }
        AlertDialog.Builder(this).setTitle("背包").setItems(labels) { _, which ->
            engine.useItem(which)
            update()
        }.setNegativeButton("关闭", null).show()
    }

    // ---------- 通关 / 特殊关 ----------

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
        AlertDialog.Builder(this)
            .setTitle("通关！")
            .setMessage("通过了「" + s.name + "」\n获得 " + s.reward + " 金币")
            .setCancelable(false)
            .setPositiveButton("继续") { _, _ ->
                engine.advance()
                if (engine.finishedAll && !engine.infiniteMode) showAllClearedDialog() else update()
            }
            .show()
    }

    private fun showAllClearedDialog() {
        AlertDialog.Builder(this)
            .setTitle("恭喜！")
            .setMessage("你通过了全部 30 个关卡！\n进入无限模式冲刺高分吧！")
            .setCancelable(false)
            .setPositiveButton("进入无限") { _, _ ->
                engine.enterInfiniteMode()
                update()
            }
            .setNegativeButton("主菜单") { _, _ -> showMainMenu() }
            .show()
    }

    private fun showSpecialDialog() {
        val r = Math.random()
        val n = if (r < 0.7) "good" else if (r < 0.8) "bad" else "neutral"
        val b = AlertDialog.Builder(this).setTitle(engine.specialStageName).setCancelable(false)

        when (n) {
            "good" -> {
                b.setMessage("这里是安全的避风港。\n生命与理智完全恢复，获得道具。")
                b.setPositiveButton("好耶") { dialog, _ ->
                    dialog.dismiss()
                    engine.hp = engine.maxHp
                    engine.sanity = 100
                    engine.inventory.add(ItemLibrary.random(1)[0])
                    engine.isSpecial = false
                    engine.advance()
                    if (engine.finishedAll && !engine.infiniteMode) showAllClearedDialog() else root.post { update() }
                }
            }
            "bad" -> {
                b.setMessage("你掉进了陷阱！\n失去 40 点生命。")
                b.setPositiveButton("该死") { dialog, _ ->
                    dialog.dismiss()
                    engine.hp -= 40
                    engine.checkState()
                    engine.isSpecial = false
                    engine.advance()
                    if (engine.finishedAll && !engine.infiniteMode) showAllClearedDialog() else root.post { update() }
                }
            }
            else -> {
                b.setMessage("前方有两条路：\n左路(金币+20)，右路(进度+50)")
                b.setPositiveButton("左路") { dialog, _ ->
                    dialog.dismiss()
                    engine.coins += 20
                    engine.isSpecial = false
                    engine.advance()
                    if (engine.finishedAll && !engine.infiniteMode) showAllClearedDialog() else root.post { update() }
                }
                b.setNegativeButton("右路") { dialog, _ ->
                    dialog.dismiss()
                    engine.progress += 50
                    engine.isSpecial = false
                    engine.advance()
                    if (engine.finishedAll && !engine.infiniteMode) showAllClearedDialog() else root.post { update() }
                }
            }
        }
        b.show()
    }

    private fun showGameOver() {
        if (overDialogShown) return
        overDialogShown = true
        val sb = "到达关卡：" + engine.stage.name + "\n通关数量：" + engine.completed +
            "\n遇到 NPC：" + engine.npcMeetings + " 次"
        AlertDialog.Builder(this)
            .setTitle("你倒下了")
            .setMessage(sb)
            .setCancelable(false)
            .setPositiveButton("重新开始") { _, _ ->
                overDialogShown = false
                showCharacterSelect()
            }
            .setNegativeButton("主菜单") { _, _ ->
                overDialogShown = false
                showMainMenu()
            }
            .show()
    }

    // ---------- 设置 / 关于 ----------

    private fun showSettings() {
        beginScreen("settings", false)
        val sv = ScrollView(this)
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(dp(24), dp(24), dp(24), dp(24))
        sv.addView(ll, lpFull())
        addScreen(sv)
        ll.addView(tv("设置", 28f, C_ACCENT, true))
        ll.addView(spacer(24))

        val labelM = tv("背景音乐：" + musicVol + "%", 14f, C_TEXT, true)
        ll.addView(labelM)
        val sbM = SeekBar(this)
        sbM.max = 100
        sbM.progress = musicVol
        sbM.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                labelM.text = "背景音乐：" + p + "%"
                musicVol = p
                saveSettings()
                if (musicVol == 0) {
                    bgm.stop()
                    currentTheme = -1
                } else if (screen == "game") {
                    currentTheme = -1
                    syncMusic(true)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) { }
            override fun onStopTrackingTouch(s: SeekBar?) { }
        })
        ll.addView(sbM)

        val labelS = tv("音效音量：" + soundVol + "%", 14f, C_TEXT, true)
        val pS = lpFull(); pS.topMargin = dp(16); ll.addView(labelS, pS)
        val sbS = SeekBar(this)
        sbS.max = 100
        sbS.progress = soundVol
        sbS.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                labelS.text = "音效音量：" + p + "%"
                soundVol = p
                saveSettings()
            }
            override fun onStartTrackingTouch(s: SeekBar?) { }
            override fun onStopTrackingTouch(s: SeekBar?) { }
        })
        ll.addView(sbS)

        val labelB = tv("屏幕亮度：" + bright + "%", 14f, C_TEXT, true)
        val pB = lpFull(); pB.topMargin = dp(16); ll.addView(labelB, pB)
        val sbB = SeekBar(this)
        sbB.max = 100
        sbB.progress = bright
        sbB.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                labelB.text = "屏幕亮度：" + p + "%"
                bright = p
                applyBrightness()
                saveSettings()
            }
            override fun onStartTrackingTouch(s: SeekBar?) { }
            override fun onStopTrackingTouch(s: SeekBar?) { }
        })
        ll.addView(sbB)

        ll.addView(spacer(30))
        ll.addView(actionBtn("返回主菜单") { showMainMenu() })
    }

    private fun showAbout() {
        beginScreen("about", false)
        val sv = ScrollView(this)
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(dp(24), dp(24), dp(24), dp(24))
        sv.addView(ll, lpFull())
        addScreen(sv)

        val ver = tv("关于详情 v" + APP_VERSION, 28f, C_ACCENT, true)
        ver.setOnClickListener {
            // 连续点击版本号打开管理员入口
            versionTaps++
            if (versionTaps >= ADMIN_TAP_COUNT) {
                versionTaps = 0
                adminGate = true
                Toast.makeText(this, "管理员入口已打开：下次开始游戏会要求密码", Toast.LENGTH_LONG).show()
            } else if (versionTaps >= 4) {
                Toast.makeText(this, "再点 " + (ADMIN_TAP_COUNT - versionTaps) + " 次…", Toast.LENGTH_SHORT).show()
            }
        }
        ll.addView(ver)
        ll.addView(spacer(16))
        ll.addView(
            tv(
                "已更新：\n1. 背景音乐按层级主题分轨（6 套音色 + 每关移调）\n" +
                    "2. 手绘透视走廊背景：墙面拼板、灯组闪烁、地砖、管道、污渍、颗粒与暗角\n" +
                    "3. 卡牌按稀有度加权刷新，强力牌不再满地\n" +
                    "4. 修复取消买卡可免费刷牌的漏洞，新增刷牌按钮（花金币、本回合限次）\n" +
                    "5. 新增 NPC：幸存者 / 医生 / 士兵 / 商人 / 邪教徒 / 掠夺者，可交谈、可交易，孤立层级不刷新\n" +
                    "6. 假随机：手牌越多怪越强越勤，手牌少时整关可能不出怪",
                13f, C_TEXT
            )
        )
        ll.addView(spacer(30))
        ll.addView(actionBtn("返回主菜单") { showMainMenu() })
    }
}

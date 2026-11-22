package com.backrooms.cardjourney

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
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
import android.media.ToneGenerator
import kotlin.math.max
import kotlin.math.sin

private const val APP_VERSION = "0.0.4"

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private val engine = GameEngine()
    private var musicVol = 70
    private var soundVol = 80
    private var bright = 100
    private var overDialogShown = false

    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null
    private var bgmStarted = false
    private var toneGen: ToneGenerator? = null
    private var screen = "menu"

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

    override fun onResume() {
        super.onResume()
        if (musicVol > 0) startBgm()
    }

    override fun onPause() {
        super.onPause()
        // 切到后台/锁屏时停掉底噪，避免后台持续占用音频焦点
        stopBgm()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBgm()
        toneGen?.release()
        toneGen = null
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (screen) {
            "game" -> { saveGame(); showMainMenu() }
            "menu" -> super.onBackPressed()
            else -> showMainMenu()
        }
    }

    private fun startBgm() {
        if (musicVol <= 0 || bgmStarted) return
        bgmStarted = true
        audioThread = Thread {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            try {
                audioTrack = buildTrack(sampleRate, bufferSize)

                audioTrack?.play()
                val buffer = ShortArray(bufferSize)
                var phase = 0.0
                while (!Thread.currentThread().isInterrupted && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    for (i in buffer.indices) {
                        val t = phase / sampleRate.toDouble()
                        val sine = sin(2.0 * Math.PI * 50.0 * t) * 0.1 + sin(2.0 * Math.PI * 120.0 * t) * 0.05
                        val noise = (Math.random() * 2 - 1) * 0.015
                        buffer[i] = ((sine + noise) * Short.MAX_VALUE * (musicVol / 100.0)).toInt().toShort()
                        phase += 1.0
                    }
                    audioTrack?.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        audioThread?.start()
    }

    private fun stopBgm() {
        audioThread?.interrupt()
        audioThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
        bgmStarted = false
    }

    // AudioTrack.Builder 是 API 23 才提供的，minSdk 21 的机器必须走老构造函数，否则闪退
    private fun buildTrack(sampleRate: Int, bufferSize: Int): AudioTrack =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
                AudioTrack.MODE_STREAM
            )
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
        v.background = rounded(bg, dp(8).toFloat())
        v.setPadding(dp(8), dp(10), dp(8), dp(10))
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
        v.background = rounded(color, dp(6).toFloat())
        v.setPadding(dp(4), dp(6), dp(4), dp(6))
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
        } catch (e: Exception) {}
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

    private fun saveGame() {
        // 死亡存档没有意义，直接用继续游戏会把死人复活
        if (engine.gameOver) return
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
            .putBoolean("cleared", engine.cleared)
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
        engine.message = "从存档继续"
        engine.monsters.clear()
        engine.monsters.addAll(decodeMonsters(prefs.getString("mon", "") ?: ""))
        engine.hand.clear()
        engine.hand.addAll(decodeCards(prefs.getString("hand", "") ?: ""))
        if (engine.hand.isEmpty()) engine.hand.addAll(CardLibrary.random(GameEngine.HAND_TARGET))
        engine.inventory.clear()
        engine.inventory.addAll(decodeItems(prefs.getString("inv", "") ?: ""))
        overDialogShown = false
        showGame()
    }

    private fun showMainMenu() {
        screen = "menu"
        // 关卡会给 root 设置渐变背景，这三个界面必须显式复位，否则浅色关卡会让浅灰文字看不清
        root.background = ColorDrawable(C_BG)
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
        screen = "char"
        // 关卡会给 root 设置渐变背景，这三个界面必须显式复位，否则浅色关卡会让浅灰文字看不清
        root.background = ColorDrawable(C_BG)
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

        val effectMsg = StringBuilder()
        effectMsg.append(evt.desc).append("\n\n【结算结果】\n")
        if (evt.hpChange != 0) effectMsg.append("生命 ").append(if (evt.hpChange > 0) "+" else "").append(evt.hpChange).append("\n")
        if (evt.coinChange != 0) effectMsg.append("金币 ").append(if (evt.coinChange > 0) "+" else "").append(evt.coinChange).append("\n")
        if (evt.progChange != 0) effectMsg.append("进度 ").append(if (evt.progChange > 0) "+" else "").append(evt.progChange).append("\n")
        if (evt.hpChange == 0 && evt.coinChange == 0 && evt.progChange == 0) effectMsg.append("无事发生……\n")

        val b = AlertDialog.Builder(this)
        b.setTitle(evt.title)
        b.setMessage(effectMsg.toString())
        b.setCancelable(false)
        b.setPositiveButton("确认") { dialog, _ ->
            dialog.dismiss()
            engine.resolveEvent()
            // 使用 post 确保关闭弹窗后再重绘界面，解决卡黑屏问题
            root.post { update() }
        }
        b.show()
    }

    private fun showGame() {
        screen = "game"
        root.removeAllViews()
        val s = engine.stage
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(s.bgStart, s.bgEnd))
        root.background = bg

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.setPadding(dp(6), dp(6), dp(6), dp(6))

        // 1. 顶部状态栏
        val status = LinearLayout(this)
        status.orientation = LinearLayout.HORIZONTAL
        main.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        status.addView(chip("生命 " + engine.hp + "/" + engine.maxHp, C_RED), lpWeight(1f, dp(2)))
        status.addView(chip("理智 " + engine.sanity, C_PURPLE), lpWeight(1f, dp(2)))
        status.addView(chip("金币 " + engine.coins, C_ACCENT), lpWeight(1f, dp(2)))
        status.addView(chip("进度 " + engine.progress, C_BLUE), lpWeight(1f, dp(2)))
        status.addView(chip("行动力 " + engine.ap, C_PANEL2), lpWeight(1f, dp(2)))

        // 2. 中间核心区 (重要：权重分配，占满剩余空间)
        val midLayout = LinearLayout(this)
        midLayout.orientation = LinearLayout.HORIZONTAL
        val midLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
        midLp.weight = 1f
        midLp.topMargin = dp(6)
        main.addView(midLayout, midLp)

        // 左侧：信息与怪物
        val leftBox = LinearLayout(this)
        leftBox.orientation = LinearLayout.VERTICAL
        leftBox.background = rounded(C_PANEL, dp(8).toFloat())
        leftBox.setPadding(dp(8), dp(8), dp(8), dp(8))
        val leftLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
        leftLp.weight = 1f
        leftLp.setMargins(0, 0, dp(4), 0)
        midLayout.addView(leftBox, leftLp)

        leftBox.addView(tv(s.name, 15f, C_ACCENT, true), lpFull())
        leftBox.addView(tv(s.subtitle, 11f, C_DIM), lpFull())
        leftBox.addView(tv("目标: " + s.goal.label + "  " + goalText(), 12f, C_TEXT, true), lpFull())
        // engine.message 之前全程没有出口，击倒/自爆/事件提示全部被丢弃
        leftBox.addView(tv("» " + engine.message, 11f, C_BLUE), lpFull())

        val monScroll = ScrollView(this)
        leftBox.addView(monScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
        val monBox = LinearLayout(this)
        monBox.orientation = LinearLayout.VERTICAL
        monScroll.addView(monBox, lpFull())

        if (engine.monsters.isEmpty()) {
            monBox.addView(tv("周围暂时安全……", 12f, C_DIM), lpFull())
        } else {
            for ((mi, m) in engine.monsters.withIndex()) {
                val isTarget = mi == engine.target.coerceIn(0, engine.monsters.size - 1)
                val row = LinearLayout(this)
                row.orientation = LinearLayout.VERTICAL
                // 可点击选择攻击目标，被选中的怪用金色描边
                row.background = roundedStroke(
                    if (isTarget) C_PANEL2 else C_PANEL,
                    dp(6).toFloat(),
                    dp(if (isTarget) 2 else 1),
                    if (isTarget) C_ACCENT else C_RED
                )
                row.isClickable = true
                row.setOnClickListener {
                    playClick()
                    engine.target = mi
                    update()
                }
                row.setPadding(dp(6), dp(6), dp(6), dp(6))
                val rlp = lpFull()
                rlp.topMargin = dp(4)
                monBox.addView(row, rlp)
                row.addView(tv((if (isTarget) "▶ " else "") + m.name + " [" + m.skill + "]", 13f, C_RED, true), lpFull())
                row.addView(tv("HP " + m.hp + "/" + m.maxHp + " ATK " + m.atk, 11f, C_DIM), lpFull())
            }
        }

        // 右侧：手牌区 (分配更多权重)
        val rightBox = LinearLayout(this)
        rightBox.orientation = LinearLayout.VERTICAL
        rightBox.background = rounded(C_PANEL, dp(8).toFloat())
        rightBox.setPadding(dp(4), dp(4), dp(4), dp(4))
        val rightLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
        rightLp.weight = 2f
        rightLp.setMargins(dp(4), 0, 0, 0)
        midLayout.addView(rightBox, rightLp)

        rightBox.addView(tv("手牌 (点击打出)", 12f, C_DIM, true), lpFull())

        val handScroll = HorizontalScrollView(this)
        handScroll.isHorizontalScrollBarEnabled = false
        rightBox.addView(handScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })

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

        // 3. 底部操作栏
        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        val r1lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        r1lp.topMargin = dp(6)
        main.addView(row1, r1lp)

        row1.addView(actionBtn("结束回合", C_ACCENT) { engine.endTurn(); update() }, lpWeight(1f, dp(2)))
        row1.addView(actionBtn("买卡", C_BLUE) { buyCardDialog() }, lpWeight(1f, dp(2)))
        row1.addView(actionBtn("商人", C_PURPLE) { openShop() }, lpWeight(1f, dp(2)))
        row1.addView(actionBtn("道具", C_PANEL2, C_TEXT) { openInventory() }, lpWeight(1f, dp(2)))
        row1.addView(actionBtn("菜单", C_PANEL2, C_TEXT) { saveGame(); showMainMenu() }, lpWeight(1f, dp(2)))

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

        // 固定卡牌宽度，高度填满，确保能看见
        val p = LinearLayout.LayoutParams(dp(85), ViewGroup.LayoutParams.MATCH_PARENT)
        p.setMargins(dp(4), 0, dp(4), 0)
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
        if (engine.boughtThisTurn) {
            Toast.makeText(this, "本回合已经买过了", Toast.LENGTH_SHORT).show()
            return
        }
        if (engine.hand.size >= GameEngine.HAND_MAX) {
            Toast.makeText(this, "手牌已满", Toast.LENGTH_SHORT).show()
            return
        }
        val picks = CardLibrary.random(3)
        // 卡牌自带 cost，之前算出来却没用，买卡永远 2 金
        val labels = Array<CharSequence>(picks.size) { i ->
            picks[i].name + "  (" + picks[i].cost + "金)\n" + picks[i].desc
        }
        AlertDialog.Builder(this)
            .setTitle("买卡 (你有 " + engine.coins + " 金币)")
            .setItems(labels) { _, which ->
                val card = picks[which]
                if (engine.coins < card.cost) {
                    Toast.makeText(this, "金币不足", Toast.LENGTH_SHORT).show()
                } else if (engine.hand.size >= GameEngine.HAND_MAX) {
                    Toast.makeText(this, "手牌已满", Toast.LENGTH_SHORT).show()
                } else {
                    engine.coins -= card.cost
                    engine.hand.add(card)
                    engine.boughtThisTurn = true
                }
                update()
            }
            .setNegativeButton("取消", null)
            .show()
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
                b.setPositiveButton("好耶") { dialog, _ ->
                    dialog.dismiss()
                    engine.hp = engine.maxHp
                    engine.sanity = 100
                    engine.inventory.add(ItemLibrary.random(1)[0])
                    engine.isSpecial = false
                    engine.advance()
                    if (engine.finishedAll) showAllClearedDialog() else root.post { update() }
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
                    if (engine.finishedAll) showAllClearedDialog() else root.post { update() }
                }
            }
            else -> {
                b.setMessage("前方有两条路：\n左路(金币+20)，右路(进度+50)")
                b.setPositiveButton("左路") { dialog, _ ->
                    dialog.dismiss()
                    engine.coins += 20
                    engine.isSpecial = false
                    engine.advance()
                    if (engine.finishedAll) showAllClearedDialog() else root.post { update() }
                }
                b.setNegativeButton("右路") { dialog, _ ->
                    dialog.dismiss()
                    engine.progress += 50
                    engine.isSpecial = false
                    engine.advance()
                    if (engine.finishedAll) showAllClearedDialog() else root.post { update() }
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
        screen = "settings"
        // 关卡会给 root 设置渐变背景，这三个界面必须显式复位，否则浅色关卡会让浅灰文字看不清
        root.background = ColorDrawable(C_BG)
        root.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(dp(24), dp(24), dp(24), dp(24))
        sv.addView(ll, lpFull())
        root.addView(sv, lpFull())
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
                if (musicVol == 0) stopBgm() else if (!bgmStarted) startBgm()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
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
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
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
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        ll.addView(sbB)

        ll.addView(spacer(30))
        ll.addView(actionBtn("返回主菜单") { showMainMenu() })
    }

    private fun showAbout() {
        screen = "about"
        // 关卡会给 root 设置渐变背景，这三个界面必须显式复位，否则浅色关卡会让浅灰文字看不清
        root.background = ColorDrawable(C_BG)
        root.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(dp(24), dp(24), dp(24), dp(24))
        sv.addView(ll, lpFull())
        root.addView(sv, lpFull())
        ll.addView(tv("关于详情 v" + APP_VERSION, 28f, C_ACCENT, true))
        ll.addView(spacer(16))
        ll.addView(tv("已更新：\n1. 修复事件完成后的卡黑屏Bug\n2. 修复卡牌在横屏下被挤压不显示的问题\n3. 加入纯代码生成的后室底噪背景音乐（可在设置里调节）\n4. 新增 12 个角色和特殊关卡系统", 14f, C_TEXT))
        ll.addView(spacer(30))
        ll.addView(actionBtn("返回主菜单") { showMainMenu() })
    }
}

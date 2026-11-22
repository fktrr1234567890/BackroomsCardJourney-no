package com.backrooms.cardjourney

import kotlin.math.max
import kotlin.math.min

data class Monster(val name: String, var hp: Int, val maxHp: Int, var atk: Int, val skill: String)

data class PlayerChar(
    val id: Int,
    val name: String,
    val desc: String,
    val maxHpBonus: Int,
    val coinBonus: Int,
    val startItem: Boolean,
    val unlockAt: Int
)

data class GameEvent(
    val title: String,
    val desc: String,
    val hpChange: Int,
    val coinChange: Int,
    val progChange: Int,
    val sanityChange: Int = 0
)

class GameEngine {
    companion object {
        // 每回合补牌目标张数：只补 1 张会让 3 点行动力长期空转
        const val HAND_TARGET = 6
        const val HAND_MAX = 10
        const val MAX_MONSTERS = 8
    }

    var levelIndex = 0
    var hp = 100
    var maxHp = 100
    var sanity = 100
    var coins = 12
    var turn = 0
    var ap = 3
    var shield = 0
    var target = 0
    var progress = 0
    var monsters = mutableListOf<Monster>()
    var hand = mutableListOf<Card>()
    var inventory = mutableListOf<Item>()
    var boughtThisTurn = false
    var infiniteMode = false
    var score = 0
    var completed = 0
    var cleared = false
    var gameOver = false
    var finishedAll = false
    var message = "欢迎来到后室。"

    var currentChar: PlayerChar? = null
    var activeEvent: GameEvent? = null
    var specialStageName = ""
    var isSpecial = false

    val characters = listOf(
        PlayerChar(0, "流浪者", "默认解锁", 0, 0, false, -1),
        PlayerChar(1, "幸存者", "第3关解锁", 50, 0, false, 3),
        PlayerChar(2, "富商", "第6关解锁", 0, 20, false, 6),
        PlayerChar(3, "探险家", "第9关解锁", 0, 0, true, 9),
        PlayerChar(4, "战士", "第12关解锁", 100, 0, false, 12),
        PlayerChar(5, "黑客", "第13关解锁", 0, 30, false, 15),
        PlayerChar(6, "医生", "第18关解锁", 80, 0, false, 18),
        PlayerChar(7, "小偷", "第21关解锁", 0, 40, false, 21),
        PlayerChar(8, "狂战士", "第24关解锁", 120, 0, false, 24),
        PlayerChar(9, "学者", "第27关解锁", 0, 0, true, 27),
        PlayerChar(10, "神明", "第29关解锁", 200, 0, false, 29),
        PlayerChar(11, "无限行者", "无限模式解锁", 100, 20, true, -2)
    )

    private val monsterNames = listOf("窃皮者", "猎犬", "无面者", "笑魇", "蠕虫", "派对客", "死亡飞蛾", "巢蛛", "爬行者", "盲者")
    private val monsterSkills = listOf("无", "无", "无", "吸血", "反伤", "狂暴", "连击", "破甲", "回血", "自爆", "噬魂")

    private val evtPlaces = listOf("潮湿的角落", "废弃的箱子", "昏暗的走廊", "破裂的镜子", "生锈的管道")
    private val evtActions = listOf("你发现了一个背包", "你听到了诡异的低语", "一阵阴风吹过", "你踢到了一个硬物", "一扇门突然打开")

    // 事件结果同时包含正负两条分支，不再用 r[1]>0 猜正负、也不再对 hp 二次取反
    private class EvtResult(val hp: Int, val coin: Int, val prog: Int, val sanity: Int, val text: String)

    private val evtResults = listOf(
        EvtResult(15, 5, 0, 0, "背包里是可用的补给。"),
        EvtResult(0, 10, 15, 0, "你摸到一条通往下层的捷径。"),
        EvtResult(8, 3, 20, 0, "笔记上记着前人走过的路线。"),
        EvtResult(-15, 0, 0, -5, "有什么东西从背后扑了上来。"),
        EvtResult(-8, -4, 10, 0, "背包被撕开，东西洒了一地。"),
        EvtResult(0, 0, -15, -10, "走廊开始扭曲，你退了回来。")
    )

    val allEvents = mutableListOf<GameEvent>()

    init {
        for (p in evtPlaces) {
            for (a in evtActions) {
                for (r in evtResults) {
                    val title = "事件: " + p
                    val desc = a + "。" + r.text
                    allEvents.add(GameEvent(title, desc, r.hp, r.coin, r.prog, r.sanity))
                }
            }
        }
    }

    val stage: Stage
        get() = if (infiniteMode) StageLibrary.infinite(score) else StageLibrary.stages[levelIndex.coerceIn(0, StageLibrary.stages.size - 1)]

    fun newGame(char: PlayerChar) {
        currentChar = char
        levelIndex = 0
        coins = 12 + char.coinBonus
        score = 0
        infiniteMode = false
        completed = 0
        finishedAll = false
        gameOver = false
        cleared = false
        inventory.clear()
        if (char.startItem) {
            inventory.add(ItemLibrary.random(1)[0])
        }
        startStage(0)
    }

    fun startStage(idx: Int) {
        levelIndex = idx
        val s = stage
        val base = if (infiniteMode) 30 + score else idx
        val charHp = currentChar?.maxHpBonus ?: 0
        maxHp = 100 + base * 4 + charHp
        hp = maxHp
        sanity = 100
        turn = 0
        ap = 3
        shield = 0
        target = 0
        progress = 0
        boughtThisTurn = false
        cleared = false
        gameOver = false
        isSpecial = false
        activeEvent = null
        monsters.clear()
        hand.clear()
        hand.addAll(CardLibrary.random(HAND_TARGET))
        if (s.goal == GoalType.KILL_ALL || s.goal == GoalType.BOSS) {
            for (i in 0 until s.monsterCount) {
                spawnMonster()
            }
        }
        message = "进入 " + s.name
    }

    fun spawnMonster() {
        if (monsters.size >= MAX_MONSTERS) return
        val s = stage
        val nm = monsterNames.random()
        val sk = monsterSkills.random()
        monsters.add(Monster(nm, s.monsterHp, s.monsterHp, s.monsterAtk, sk))
    }

    fun playCard(index: Int): Boolean {
        if (gameOver || cleared || ap <= 0 || index < 0 || index >= hand.size) return false
        val c = hand[index]
        ap--
        hand.removeAt(index)
        progress += c.progress
        message = "使用了 " + c.name

        when (c.category) {
            CardCategory.FOOD -> {
                hp = min(maxHp, hp + c.v1)
                sanity = min(100, sanity + c.v2)
            }
            CardCategory.REST -> {
                hp = min(maxHp, hp + c.v1)
                ap += c.v2
            }
            CardCategory.UTILITY -> {
                progress += c.v1
                coins += c.v2
            }
            CardCategory.COMBAT -> {
                if (monsters.isNotEmpty()) {
                    hitMonster(target.coerceIn(0, monsters.size - 1), c.v1)
                } else {
                    progress += c.v2
                }
            }
            CardCategory.DEFENSE -> {
                shield += c.v1
                hp = min(maxHp, hp + c.v2)
            }
            CardCategory.MOVEMENT -> {
                progress += c.v1
                shield += c.v2 * 3
            }
            CardCategory.MENTAL -> {
                sanity = min(100, sanity + c.v1)
                progress += c.v2
            }
            CardCategory.TRADE -> {
                coins += c.v1
                progress += c.v2
            }
        }
        checkState()
        return true
    }

    // 统一处理反伤、死亡与自爆，避免各调用点漏实现
    private fun hitMonster(index: Int, damage: Int) {
        if (index < 0 || index >= monsters.size) return
        val m = monsters[index]
        m.hp -= damage
        if (m.skill == "反伤") hp -= (damage * 0.2).toInt()
        if (m.hp <= 0) removeMonster(index)
    }

    private fun removeMonster(index: Int) {
        if (index < 0 || index >= monsters.size) return
        val m = monsters.removeAt(index)
        if (m.skill == "自爆") {
            hp -= m.atk * 2
            message = m.name + " 自爆了！"
        } else {
            message = "击倒了 " + m.name + "！"
        }
        if (monsters.isEmpty()) target = 0 else target = target.coerceIn(0, monsters.size - 1)
    }

    fun endTurn() {
        if (gameOver || cleared || isSpecial) return
        var dmg = 0      // 可被护盾抵挡
        var pierce = 0   // 破甲：直接穿透护盾
        for (m in monsters) {
            var atk = m.atk
            if (m.skill == "狂暴" && m.hp < m.maxHp / 2) atk *= 2
            if (m.skill == "连击") atk += m.atk / 2
            if (m.skill == "破甲") pierce += atk else dmg += atk
            if (m.skill == "吸血") m.hp = min(m.maxHp, m.hp + m.atk / 2)
            if (m.skill == "回血") m.hp = min(m.maxHp, m.hp + 10)
            if (m.skill == "噬魂") sanity = max(0, sanity - 3)
        }
        if (shield > 0) {
            val a = min(shield, dmg)
            shield -= a
            dmg -= a
        }
        if (dmg + pierce > 0) hp -= dmg + pierce

        turn++
        ap = 3
        boughtThisTurn = false

        if (turn % 2 == 0) sanity = max(0, sanity - 1)

        if (turn > 1 && Math.random() < 0.15 && activeEvent == null) {
            activeEvent = allEvents.random()
            // 事件可能在结算前就已经把人打死（扣血/扣理智），这里先判定一次状态
            checkState()
            return
        }

        val s = stage
        if (s.goal != GoalType.KILL_ALL && s.goal != GoalType.BOSS && turn % s.spawnEvery == 0) {
            spawnMonster()
        }
        refillHand()
        checkState()
    }

    fun resolveEvent() {
        activeEvent?.let {
            hp = min(maxHp, hp + it.hpChange)
            coins = max(0, coins + it.coinChange)
            progress = max(0, progress + it.progChange)
            sanity = max(0, min(100, sanity + it.sanityChange))
            message = "事件结算完成"
        }
        activeEvent = null
        val s = stage
        if (s.goal != GoalType.KILL_ALL && s.goal != GoalType.BOSS && turn % s.spawnEvery == 0) {
            spawnMonster()
        }
        refillHand()
        checkState()
    }

    // 每回合补到 HAND_TARGET 张，而不是只补 1 张
    private fun refillHand() {
        while (hand.size < HAND_TARGET) hand.add(CardLibrary.randomOne())
    }

    fun checkState() {
        if (hp <= 0 || sanity <= 0) {
            hp = max(0, hp)
            gameOver = true
            message = "你倒下了……"
            return
        }
        if (cleared || isSpecial) return
        val s = stage
        val won = when (s.goal) {
            GoalType.KILL_ALL, GoalType.BOSS -> monsters.isEmpty()
            GoalType.SURVIVE -> turn >= s.goalValue
            else -> progress >= s.goalValue
        }
        if (won) {
            cleared = true
            completed++
            coins += s.reward
            message = "通关！"
        }
    }

    fun advance() {
        cleared = false
        if (infiniteMode) {
            score++
            startStage(levelIndex)
            return
        }
        if (levelIndex >= StageLibrary.stages.size - 1) {
            finishedAll = true
            return
        }
        startStage(levelIndex + 1)
    }

    fun useItem(index: Int) {
        if (gameOver || cleared || index < 0 || index >= inventory.size) return
        val it = inventory.removeAt(index)
        message = "使用了道具 " + it.name
        when (it.category) {
            ItemCategory.HEAL -> hp = min(maxHp, hp + it.power)
            ItemCategory.HOPE -> {
                hp = min(maxHp, hp + it.power / 2)
                sanity = min(100, sanity + it.power / 2)
            }
            ItemCategory.EXPLORE -> progress += it.power
            ItemCategory.GUARD -> shield += it.power
            ItemCategory.TRADE -> coins += it.power / 2
            ItemCategory.DOOM -> {
                val snapshot = monsters.toList()
                for (m in snapshot) m.hp -= it.power
                // 倒序删除，保证下标在删除过程中依然有效，同时触发自爆等死亡效果
                for (i in monsters.indices.reversed()) {
                    if (monsters[i].hp <= 0) removeMonster(i)
                }
                hp = max(1, hp - it.power / 4)
            }
            ItemCategory.MONSTER -> {
                if (monsters.isNotEmpty()) hitMonster(target.coerceIn(0, monsters.size - 1), it.power)
            }
            ItemCategory.TIME -> ap += 2
            ItemCategory.FATE -> {
                progress += it.power
                coins += it.power / 5
            }
            ItemCategory.MYSTIC -> {
                hp = min(maxHp, hp + it.power / 3)
                sanity = min(100, sanity + it.power / 3)
                progress += it.power / 3
            }
        }
        checkState()
    }

    fun generateSpecialStageName(): String {
        val adj = listOf("扭曲的", "血红的", "耀眼的", "崩坏的", "寂静的")
        val noun = listOf("迷宫", "休息室", "虚空", "祭坛", "捷径")
        return "特殊关卡 " + adj.random() + noun.random()
    }
}

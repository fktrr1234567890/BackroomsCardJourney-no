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
    val progChange: Int
)

class GameEngine {
    var levelIndex = 0
    var hp = 100
    var maxHp = 100
    var sanity = 100
    var coins = 12
    var turn = 0
    var ap = 3
    var shield = 0
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
        PlayerChar(5, "黑客", "第15关解锁", 0, 30, false, 15),
        PlayerChar(6, "医生", "第18关解锁", 80, 0, false, 18),
        PlayerChar(7, "小偷", "第21关解锁", 0, 40, false, 21),
        PlayerChar(8, "狂战士", "第24关解锁", 120, 0, false, 24),
        PlayerChar(9, "学者", "第27关解锁", 0, 0, true, 27),
        PlayerChar(10, "神明", "第29关解锁", 200, 0, false, 29),
        PlayerChar(11, "无限行者", "无限模式解锁", 100, 20, true, -2)
    )

    private val monsterNames = listOf("窃皮者", "猎犬", "无面者", "笑魇", "蠕虫", "派对客", "死亡飞蛾", "巢蛛", "爬行者", "盲者")
    private val monsterSkills = listOf("无", "无", "无", "吸血", "反伤", "狂暴", "连击", "破甲", "回血", "自爆")

    private val evtPlaces = listOf("潮湿的角落", "废弃的箱子", "昏暗的走廊", "破裂的镜子", "生锈的管道")
    private val evtActions = listOf("你发现了一个背包", "你听到了诡异的低语", "一阵阴风吹过", "你踢到了一个硬物", "一扇门突然打开")
    private val evtResults = listOf(listOf(-15, 5, 0), listOf(0, 10, 15))

    val allEvents = mutableListOf<GameEvent>()

    init {
        for (p in evtPlaces) {
            for (a in evtActions) {
                for (r in evtResults) {
                    val isGood = r[1] > 0
                    val title = "事件: " + p
                    val desc = a + "，你获得了" + (if (isGood) "好处" else "代价") + "。"
                    allEvents.add(GameEvent(title, desc, -r[0], r[1], r[2]))
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
        progress = 0
        boughtThisTurn = false
        cleared = false
        gameOver = false
        isSpecial = false
        activeEvent = null
        monsters.clear()
        hand.clear()
        hand.addAll(CardLibrary.random(10))
        if (s.goal == GoalType.KILL_ALL || s.goal == GoalType.BOSS) {
            for (i in 0 until s.goalValue) {
                spawnMonster()
            }
        }
        message = "进入 " + s.name
    }

    fun spawnMonster() {
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
                    val m = monsters[0]
                    m.hp -= c.v1
                    if (m.skill == "反伤") {
                        hp -= (c.v1 * 0.2).toInt()
                    }
                    if (m.hp <= 0) {
                        monsters.removeAt(0)
                        message = "击倒了 " + m.name + "！"
                    }
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
        message = "使用了 " + c.name
        checkState()
        return true
    }

    fun endTurn() {
        if (gameOver || cleared || isSpecial) return
        var dmg = 0
        for (m in monsters) {
            if (m.skill == "狂暴" && m.hp < m.maxHp / 2) dmg += m.atk * 2 else dmg += m.atk
            if (m.skill == "吸血") m.hp = min(m.maxHp, m.hp + m.atk / 2)
            if (m.skill == "连击") dmg += m.atk / 2
            if (m.skill == "回血") m.hp = min(m.maxHp, m.hp + 10)
        }
        if (shield > 0) {
            val a = min(shield, dmg)
            shield -= a
            dmg -= a
        }
        if (dmg > 0) hp -= dmg

        turn++
        ap = 3
        boughtThisTurn = false

        if (turn % 2 == 0) sanity = max(0, sanity - 1)

        if (turn > 1 && Math.random() < 0.15 && activeEvent == null) {
            activeEvent = allEvents.random()
            return
        }

        val s = stage
        if (s.goal != GoalType.KILL_ALL && s.goal != GoalType.BOSS && turn % s.spawnEvery == 0) {
            spawnMonster()
        }
        if (hand.size < 10) hand.add(CardLibrary.randomOne())
        checkState()
    }

    fun resolveEvent() {
        activeEvent?.let {
            hp = min(maxHp, hp + it.hpChange)
            coins = max(0, coins + it.coinChange)
            progress += it.progChange
            message = "事件结算完成"
        }
        activeEvent = null
        val s = stage
        if (s.goal != GoalType.KILL_ALL && s.goal != GoalType.BOSS && turn % s.spawnEvery == 0) {
            spawnMonster()
        }
        if (hand.size < 10) hand.add(CardLibrary.randomOne())
        checkState()
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
        if (index < 0 || index >= inventory.size) return
        val it = inventory.removeAt(index)
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
                for (m in monsters) m.hp -= it.power
                monsters.removeAll { m -> m.hp <= 0 }
                hp = max(1, hp - it.power / 4)
            }
            ItemCategory.MONSTER -> {
                if (monsters.isNotEmpty()) {
                    monsters[0].hp -= it.power
                    if (monsters[0].hp <= 0) monsters.removeAt(0)
                }
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
        message = "使用了道具 " + it.name
        checkState()
    }

    fun generateSpecialStageName(): String {
        val adj = listOf("扭曲的", "血红的", "耀眼的", "崩坏的", "寂静的")
        val noun = listOf("迷宫", "休息室", "虚空", "祭坛", "捷径")
        return "特殊关卡 " + adj.random() + noun.random()
    }
}

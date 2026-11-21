package com.backrooms.cardjourney

import kotlin.math.max
import kotlin.math.min

data class Monster(val name: String, var hp: Int, val maxHp: Int, val atk: Int)

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
    var killDone = 0
    var completed = 0
    var cleared = false
    var gameOver = false
    var finishedAll = false
    var message = "欢迎来到后室。"

    private val monsterNames = listOf(
        "窃皮者", "猎犬", "无面者", "笑魇", "蠕虫",
        "派对客", "死亡飞蛾", "巢蛛", "爬行者", "盲者",
        "缝合者", "拾荒者", "隧道爬虫", "低语者"
    )

    val stage: Stage
        get() = if (infiniteMode)
            StageLibrary.infinite(score)
        else
            StageLibrary.stages[levelIndex.coerceIn(0, StageLibrary.stages.size - 1)]

    fun newGame() {
        levelIndex = 0
        coins = 12
        score = 0
        infiniteMode = false
        completed = 0
        finishedAll = false
        gameOver = false
        cleared = false
        inventory.clear()
        startStage(0)
    }

    fun startStage(idx: Int) {
        levelIndex = idx
        val s = stage
        val base = if (infiniteMode) 30 + score else idx
        maxHp = 100 + base * 4
        hp = maxHp
        sanity = 100
        turn = 0
        ap = 3
        shield = 0
        progress = 0
        killDone = 0
        boughtThisTurn = false
        cleared = false
        gameOver = false
        monsters.clear()
        hand.clear()
        hand.addAll(CardLibrary.random(10))
        if (s.goal == GoalType.KILL_ALL || s.goal == GoalType.BOSS) {
            for (i in 0 until s.goalValue) spawnMonster()
        }
        message = "进入 " + s.name
    }

    fun spawnMonster() {
        val s = stage
        val nm = monsterNames[(Math.random() * monsterNames.size).toInt()
            .coerceIn(0, monsterNames.size - 1)]
        monsters.add(Monster(nm, s.monsterHp, s.monsterHp, s.monsterAtk))
    }

    fun playCard(index: Int): Boolean {
        if (gameOver || cleared) return false
        if (ap <= 0) {
            message = "行动力不足，请结束回合。"
            return false
        }
        if (index < 0 || index >= hand.size) return false
        val c = hand[index]
        ap--
        hand.removeAt(index)
        applyCard(c)
        checkState()
        return true
    }

    private fun applyCard(c: Card) {
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
                    if (m.hp <= 0) {
                        monsters.removeAt(0)
                        killDone++
                        message = "击倒了 " + m.name + "！"
                    } else {
                        message = "对 " + m.name + " 造成 " + c.v1 + " 点伤害。"
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
        message = "使用了「" + c.name + "」"
    }

    fun endTurn() {
        if (gameOver || cleared) return

        var dmg = 0
        for (m in monsters) dmg += m.atk
        if (shield > 0) {
            val absorb = min(shield, dmg)
            shield -= absorb
            dmg -= absorb
        }
        if (dmg > 0) hp -= dmg

        turn++
        ap = 3
        boughtThisTurn = false
        if (turn % 2 == 0) sanity = max(0, sanity - 1)

        val s = stage
        if (s.goal != GoalType.KILL_ALL && s.goal != GoalType.BOSS) {
            if (turn % s.spawnEvery == 0) {
                spawnMonster()
                message = "有东西从阴影中爬了出来……"
            }
        }
        if (hand.size < 10) hand.add(CardLibrary.randomOne())
        checkState()
    }

    fun checkState() {
        if (hp <= 0 || sanity <= 0) {
            hp = max(0, hp)
            gameOver = true
            message = "你倒下了……后室吞没了你。"
            return
        }
        if (cleared) return
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
            message = "通关！获得 " + s.reward + " 金币"
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
                    val m = monsters[0]
                    m.hp -= it.power
                    if (m.hp <= 0) {
                        monsters.removeAt(0)
                        killDone++
                    }
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
        message = "使用了道具「" + it.name + "」"
        checkState()
    }
}

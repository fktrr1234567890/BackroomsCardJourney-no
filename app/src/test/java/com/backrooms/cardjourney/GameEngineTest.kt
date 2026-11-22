package com.backrooms.cardjourney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GameEngine 是纯 Kotlin、无 Android 依赖，可以直接用 JVM 单元测试跑。
 * 这些用例分别锁住本次修复的几个回归点。
 */
class GameEngineTest {

    private fun newEngine(): GameEngine = GameEngine().apply { newGame(characters[0]) }

    @Test
    fun `无限模式第一轮不会刷出几十只怪`() {
        val e = newEngine()
        e.infiniteMode = true
        e.score = 0
        e.startStage(0)

        assertEquals(GoalType.KILL_ALL, e.stage.goal)
        assertTrue("怪物数量=${e.monsters.size}", e.monsters.size in 1..6)
        // 进度目标不应再被当成怪物数量
        assertTrue(e.stage.goalValue <= 6)
    }

    @Test
    fun `首领关只有一只怪`() {
        assertEquals(1, StageLibrary.stages[29].monsterCount)
    }

    @Test
    fun `事件表里必须同时存在正负结果`() {
        val e = GameEngine()
        assertTrue("缺少扣血事件", e.allEvents.any { it.hpChange < 0 })
        assertTrue("缺少扣理智事件", e.allEvents.any { it.sanityChange < 0 })
        assertTrue("缺少回血事件", e.allEvents.any { it.hpChange > 0 })
    }

    @Test
    fun `事件结算按声明的正负号生效`() {
        val e = newEngine()
        val bad = e.allEvents.first { it.hpChange < 0 }
        e.activeEvent = bad
        val before = e.hp
        e.resolveEvent()
        assertTrue("hp 应下降：before=$before after=${e.hp}", e.hp < before)
        assertTrue("结算后应清空事件", e.activeEvent == null)
    }

    @Test
    fun `自爆怪死亡时对玩家造成两倍攻击伤害`() {
        val e = newEngine()
        e.monsters.clear()
        e.monsters.add(Monster("测试自爆怪", 5, 5, 10, "自爆"))
        e.hand.clear()
        e.hand.add(Card(1, "测试攻击", CardCategory.COMBAT, 100, 0, 0, "测试"))
        e.ap = 3
        val before = e.hp
        e.playCard(0)
        assertEquals("自爆应扣 20 血", before - 20, e.hp)
        assertTrue(e.monsters.isEmpty())
    }

    @Test
    fun `破甲怪穿透护盾`() {
        val e = newEngine()
        e.monsters.clear()
        e.monsters.add(Monster("测试破甲怪", 50, 50, 10, "破甲"))
        e.shield = 30
        val before = e.hp
        e.endTurn()
        assertEquals("护盾不应抵扣破甲伤害", 30, e.shield)
        assertEquals(before - 10, e.hp)
    }

    @Test
    fun `每回合补牌到目标张数`() {
        val e = newEngine()
        assertEquals(GameEngine.HAND_TARGET, e.hand.size)

        e.hand.clear()
        e.activeEvent = GameEvent("测试事件", "测试", 0, 0, 0)
        e.resolveEvent()
        assertTrue("手牌应补到 ${GameEngine.HAND_TARGET}，实际 ${e.hand.size}",
            e.hand.size >= GameEngine.HAND_TARGET)
    }
}

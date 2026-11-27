package com.backrooms.cardjourney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GameEngine 是纯 Kotlin、无 Android 依赖，可以直接用 JVM 单元测试跑。
 * 这些用例分别锁住修复与新功能的回归点。
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

    // ---------- 商店刷牌漏洞 ----------

    @Test
    fun `同一回合取消买卡再打开不会刷新报价`() {
        val e = newEngine()
        val first = e.ensureOffers().map { it.id }
        // 打开对话框（ensureOffers）不会重新生成
        val second = e.ensureOffers().map { it.id }
        assertEquals(first, second)
        assertEquals(GameEngine.OFFER_SIZE, first.size)
    }

    @Test
    fun `买掉一件后其余报价仍然保留`() {
        val e = newEngine()
        e.coins = 100
        val before = e.ensureOffers().map { it.id }
        val total = e.offerCards.size
        val boughtId = before[0]
        assertEquals(0, e.buyOffer(0))
        assertEquals(total - 1, e.offerCards.size)
        assertTrue(e.offerCards.none { it.id == boughtId })
    }

    @Test
    fun `主动刷牌要扣金币并且有次数上限`() {
        val e = newEngine()
        e.coins = 100
        e.ensureOffers()
        val before = e.coins
        assertEquals(0, e.rerollOffers())
        assertEquals("首次刷牌应扣 1 金", before - 1, e.coins)
        assertEquals(1, e.offerRerolls)

        // 刷满次数后不再允许
        while (e.rerollOffers() == 0) { }
        assertEquals(GameEngine.REROLL_MAX_PER_TURN, e.offerRerolls)
    }

    @Test
    fun `买完报价后同一回合不会免费补货`() {
        val e = newEngine()
        e.coins = 1000
        e.ensureOffers()
        while (e.offerCards.isNotEmpty()) e.buyOffer(0)
        assertTrue(e.offerCards.isEmpty())
        assertEquals("同回合不应再生成免费报价", 0, e.ensureOffers().size)
    }

    @Test
    fun `结束回合后报价重新生成`() {
        val e = newEngine()
        e.ensureOffers()
        e.endTurn()
        assertEquals(0, e.offerRerolls)
        assertEquals(0, e.offerCards.size)
        e.ensureOffers()
        assertEquals(GameEngine.OFFER_SIZE, e.offerCards.size)
    }

    // ---------- 卡池概率 ----------

    @Test
    fun `卡池按权重分层且强力牌更稀少`() {
        val all = CardLibrary.all
        val common = all.count { it.weight >= 24 }
        val legendary = all.count { it.weight <= 1 }
        assertTrue("常见牌应明显多于传说牌", common > legendary * 2)
        assertTrue("应存在四种稀有度", all.map { it.rarity }.distinct().size >= 4)

        // 抽样验证权重真的生效：常见牌命中次数应远多于传说牌
        var hitCommon = 0
        var hitLegendary = 0
        repeat(400) {
            when (CardLibrary.randomOne().rarity) {
                "常见" -> hitCommon++
                "传说" -> hitLegendary++
            }
        }
        assertTrue("常见牌抽取次数应远多于传说：$hitCommon vs $hitLegendary", hitCommon > hitLegendary * 3)
    }

    @Test
    fun `起手牌保证有攻击与防御`() {
        val h = CardLibrary.startingHand(GameEngine.HAND_TARGET)
        assertEquals(GameEngine.HAND_TARGET, h.size)
        assertTrue("至少两张战斗牌", h.count { it.category == CardCategory.COMBAT } >= 2)
        assertTrue("至少一张防御牌", h.any { it.category == CardCategory.DEFENSE })
    }

    // ---------- 动态难度 ----------

    @Test
    fun `手牌越多刷怪概率越高`() {
        val e = newEngine()
        val withFew = e.spawnChance()
        while (e.hand.size < GameEngine.HAND_MAX) e.hand.add(CardLibrary.randomOne())
        val withMany = e.spawnChance()
        assertTrue("牌多时应更容易刷怪：$withFew -> $withMany", withMany > withFew)
        assertTrue("牌多时怪物更强", e.threatScale() > 1.0)
    }

    @Test
    fun `手牌很少时刷怪概率显著降低`() {
        val e = newEngine()
        while (e.hand.isNotEmpty()) e.hand.removeAt(0)
        assertTrue("空手时刷怪概率应低于 0.2，实际 ${e.spawnChance()}", e.spawnChance() < 0.2)
        assertTrue("空手时怪物更弱", e.threatScale() < 1.0)
    }

    // ---------- NPC ----------

    @Test
    fun `孤立层级不会遇到NPC`() {
        assertTrue(StageLibrary.stages[6].isolation)
        val e = newEngine()
        e.startStage(5)
        e.advance()
        assertNull("孤立层级不应出现 NPC", e.pendingNpc)
        assertEquals(6, e.levelIndex)
    }

    @Test
    fun `无限模式深处进入孤立区`() {
        assertTrue(StageLibrary.infinite(3).isolation)
    }

    @Test
    fun `士兵只卖武器`() {
        val npc = NpcLibrary.byKind(NpcKind.SOLDIER)
        assertTrue(npc.stock.isNotEmpty())
        assertTrue(npc.stock.all { it.card?.category == CardCategory.COMBAT || it.item?.category == ItemCategory.GUARD })
    }

    @Test
    fun `幸存者只卖吃的和药`() {
        val npc = NpcLibrary.byKind(NpcKind.SURVIVOR)
        assertTrue(npc.stock.isNotEmpty())
        assertTrue(npc.stock.all { it.card?.category == CardCategory.FOOD || it.item?.category == ItemCategory.HEAL })
    }

    @Test
    fun `在NPC处购买会扣钱并拿到东西`() {
        val e = newEngine()
        e.pendingNpc = NpcLibrary.byKind(NpcKind.SOLDIER)
        e.npcStock.clear()
        e.npcStock.addAll(e.pendingNpc!!.stock)
        e.coins = 200
        val price = e.npcStock[0].price
        val invBefore = e.inventory.size
        val handBefore = e.hand.size
        val msg = e.npcBuy(0)
        assertTrue(msg.contains("获得"))
        assertEquals(200 - price, e.coins)
        assertTrue("应获得卡牌或道具", e.inventory.size > invBefore || e.hand.size > handBefore)
    }

    @Test
    fun `管理员可以定向召唤NPC`() {
        val e = newEngine()
        e.adminSummonNpc(NpcKind.CULTIST)
        assertNotNull(e.pendingNpc)
        assertEquals(NpcKind.CULTIST, e.pendingNpc!!.kind)
        assertTrue(e.npcStock.isNotEmpty())
    }

    // ---------- 管理员面板 ----------

    @Test
    fun `管理员可以创造和销毁怪物`() {
        val e = newEngine()
        e.monsters.clear()
        e.adminSpawn("测试怪", 50, 10, "无")
        assertEquals(1, e.monsters.size)
        assertEquals(50, e.monsters[0].hp)
        e.adminKill(0)
        assertTrue(e.monsters.isEmpty())
        e.adminSpawn("测试怪2", 80, 12, "破甲")
        e.adminKillAll()
        assertTrue(e.monsters.isEmpty())
    }

    @Test
    fun `管理员可以跳关`() {
        val e = newEngine()
        e.adminJumpTo(12)
        assertEquals(12, e.levelIndex)
        assertEquals(StageLibrary.stages[12].name, e.stage.name)
        e.adminJumpInfinite(3)
        assertTrue(e.infiniteMode)
        assertEquals(3, e.score)
    }

    @Test
    fun `管理员可以直接通关当前关卡`() {
        val e = newEngine()
        e.adminEndStage()
        assertTrue(e.cleared)
        assertEquals(1, e.completed)
    }

    @Test
    fun `进入无限模式会清掉全部通关标记`() {
        val e = newEngine()
        e.adminJumpTo(29)
        e.adminEndStage()
        e.advance()
        assertTrue(e.finishedAll)

        e.enterInfiniteMode()
        assertFalse(e.finishedAll)
        assertEquals(0, e.score)
        assertTrue(e.infiniteMode)
        assertTrue(e.stage.name.contains("第 1 轮"))
    }

    @Test
    fun `无限模式通关后轮次递增而不会回到第一轮`() {
        val e = newEngine()
        e.enterInfiniteMode()

        repeat(5) {
            e.adminEndStage()
            e.advance()
            if (e.pendingNpc != null) e.resolveNpc()
            assertFalse("无限模式不应再出现全部通关标记", e.finishedAll)
        }

        assertEquals(5, e.score)
        assertTrue(e.stage.name.contains("第 6 轮"))
        assertFalse(e.finishedAll)
    }

    @Test
    fun `从存档恢复后不会残留全部通关标记`() {
        val e = newEngine()
        e.adminJumpTo(29)
        e.adminEndStage()
        e.advance()
        assertTrue(e.finishedAll)

        // 模拟用户在弹窗前退出、下次继续游戏
        e.startStage(29)
        assertFalse(e.finishedAll)
    }
}

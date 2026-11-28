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
    val sanityChange: Int = 0,
    val shieldChange: Int = 0,
    val apChange: Int = 0,
    /** 标签：good / bad / weird，供界面配色和“必出好事件”判定 */
    val tag: String = "weird"
)

private const val MERCHANT_CHANCE = 0.55
const val MAX_EQUIP = 4

class GameEngine {
    companion object {
        // 每回合补牌目标张数：只补 1 张会让 3 点行动力长期空转
        const val HAND_TARGET = 6
        const val HAND_MAX = 10
        const val MAX_MONSTERS = 8
        // 商店报价数量与刷牌规则
        const val OFFER_SIZE = 3
        const val REROLL_BASE = 1
        const val REROLL_MAX_PER_TURN = 4
        // 每次进入新关卡前遇到 NPC 的概率
        const val NPC_CHANCE = 0.45
        const val MAX_EQUIP = 4
        const val MERCHANT_CHANCE = 0.55
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

    /** 已装备的物品（武器/护甲/随身），最多 MAX_EQUIP 件 */
    var equipment = mutableListOf<Equipment>()

    /** 回合结束时商人是否出现；出现时 merchantStock 是他的货 */
    var pendingMerchant = false
    var merchantStock = mutableListOf<Equipment>()
    var boughtThisTurn = false
    var infiniteMode = false
    var score = 0
    var completed = 0
    var cleared = false
    var gameOver = false
    var finishedAll = false
    var message = "欢迎来到后室。"

    /** 玩家幸运：影响暴击率、金币掉落与事件走向 */
    var luck = 0
    /** 闪避回合数，本回合内免疫怪物伤害 */
    var dodge = 0
    /** 静默：下一回合行动力 -1 */
    var silenced = false

    var currentChar: PlayerChar? = null
    var activeEvent: GameEvent? = null
    var specialStageName = ""
    var isSpecial = false

    // ---- 商店报价：本回合固定，取消对话框不会重刷 ----
    var offerCards = mutableListOf<Card>()
    var offerRerolls = 0
    var offersTurn = -1

    // ---- NPC：进入下一关之前的偶遇 ----
    var pendingNpc: Npc? = null
    var pendingStageIndex = 0
    var npcStock = mutableListOf<NpcStock>()
    var npcTalked = false
    var npcMeetings = 0

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
    private val monsterSkills = listOf(
        "无", "无", "无", "吸血", "反伤", "狂暴", "连击", "破甲", "回血", "自爆", "噬魂",
        "腐蚀", "恐惧", "护盾", "分裂", "召唤", "静默"
    )

    private val evtPlaces = listOf(
        "潮湿的角落", "废弃的箱子", "昏暗的走廊", "破裂的镜子", "生锈的管道",
        "停摆的电梯", "长满霉斑的储物间", "被水淹的半层", "挂满旧照片的过道", "没有出口的楼梯间",
        "嗡嗡作响的配电房", "铺满地毯的放映厅"
    )
    private val evtActions = listOf(
        "你发现了一个背包", "你听到了诡异的低语", "一阵阴风吹过", "你踢到了一个硬物", "一扇门突然打开",
        "墙上的钟倒着走", "地上的水洼映出另一个你", "远处传来拖拽声", "有人在你背后轻声数数", "灯忽然全灭了",
        "一张纸条从门缝塞进来", "空气突然变得很甜"
    )

    // 事件结果同时包含正负两条分支，不再用 r[1]>0 猜正负、也不再对 hp 二次取反
    private class EvtResult(
        val hp: Int, val coin: Int, val prog: Int, val sanity: Int,
        val text: String, val shield: Int = 0, val ap: Int = 0, val tag: String = "weird"
    )

    private val evtResults = listOf(
        EvtResult(15, 5, 0, 0, "背包里是可用的补给。", 0, 0, "good"),
        EvtResult(0, 10, 15, 0, "你摸到一条通往下层的捷径。", 6, 0, "good"),
        EvtResult(8, 3, 20, 0, "笔记上记着前人走过的路线。", 0, 1, "good"),
        EvtResult(-15, 0, 0, -5, "有什么东西从背后扑了上来。", 0, 0, "bad"),
        EvtResult(-8, -4, 10, 0, "背包被撕开，东西洒了一地。", 0, 0, "bad"),
        EvtResult(0, 0, -15, -10, "走廊开始扭曲，你退了回来。", 0, 0, "bad"),
        EvtResult(6, 8, 6, 0, "你捡到一枚还在发热的硬币。", 4, 0, "good"),
        EvtResult(0, 14, 0, 6, "墙里的声音告诉你一个名字。", 0, 0, "weird"),
        EvtResult(-6, 12, 8, -6, "你从一具尸体上翻出了东西。", 0, 0, "weird"),
        EvtResult(0, 0, 0, -14, "那面镜子里的你没有跟着你走。", 0, 0, "bad"),
        EvtResult(10, 0, 0, 8, "有人留下了半壶还温的水。", 3, 0, "good"),
        EvtResult(-10, 0, 12, 0, "门在你身后关上，前进是唯一的路。", 0, 0, "weird")
    )

    val allEvents = mutableListOf<GameEvent>()

    init {
        for (p in evtPlaces) {
            for (a in evtActions) {
                for (r in evtResults) {
                    val title = "事件: " + p
                    val desc = a + "。" + r.text
                    allEvents.add(
                        GameEvent(title, desc, r.hp, r.coin, r.prog, r.sanity, r.shield, r.ap, r.tag)
                    )
                }
            }
        }
    }

    val stage: Stage
        get() = if (infiniteMode) StageLibrary.infinite(score) else StageLibrary.stages[levelIndex.coerceIn(0, StageLibrary.stages.size - 1)]

    fun newGame(char: PlayerChar) {
        equipment.clear()
        pendingMerchant = false
        merchantStock.clear()
        currentChar = char
        levelIndex = 0
        coins = 12 + char.coinBonus
        score = 0
        infiniteMode = false
        completed = 0
        finishedAll = false
        gameOver = false
        cleared = false
        npcMeetings = 0
        inventory.clear()
        if (char.startItem) {
            inventory.add(ItemLibrary.random(1)[0])
        }
        pendingNpc = null
        startStage(0)
    }

    fun startStage(idx: Int) {
        levelIndex = idx
        val s = stage
        // 只要进了新关，就说明这一局还在继续，通关标记必须清掉
        finishedAll = false
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
        pendingNpc = null
        npcStock.clear()
        npcTalked = false
        offerCards.clear()
        offerRerolls = 0
        offersTurn = -1
        monsters.clear()
        hand.clear()
        hand.addAll(CardLibrary.startingHand(HAND_TARGET))
        ensureOffers()
        if (s.goal == GoalType.KILL_ALL || s.goal == GoalType.BOSS) {
            for (i in 0 until s.monsterCount) {
                spawnMonster()
            }
        }
        message = "进入 " + s.name
    }

    // ---------- 商店 ----------

    fun ensureOffers(): List<Card> {
        // 只在"跨回合"时补货：同一回合把三件都买光后不会再免费生成一批
        if (offerCards.isEmpty() && offersTurn != turn) {
            offerCards.clear()
            offerCards.addAll(CardLibrary.offers(OFFER_SIZE))
            offersTurn = turn
        }
        return offerCards
    }

    /** 主动刷牌：花金币换一批报价，同一回合每刷一次更贵 */
    fun offerRerollCost(): Int = REROLL_BASE + offerRerolls

    fun rerollOffers(): Int {
        if (offerRerolls >= REROLL_MAX_PER_TURN) return 2
        val cost = offerRerollCost()
        if (coins < cost) return 1
        coins -= cost
        offerRerolls++
        offerCards.clear()
        offerCards.addAll(CardLibrary.offers(OFFER_SIZE))
        message = "花 " + cost + " 金币刷新了报价"
        return 0
    }

    fun buyOffer(index: Int): Int {
        val c = offerCards.getOrNull(index) ?: return 3
        if (hand.size >= HAND_MAX) return 2
        if (coins < c.cost) return 1
        coins -= c.cost
        hand.add(c)
        offerCards.removeAt(index)
        boughtThisTurn = true
        message = "买入 " + c.name
        return 0
    }

    // ---------- 假随机（动态难度） ----------

    /** 手牌越多压力越大：怪更强、刷新更频繁；手牌少时可能整关都不刷怪 */
    fun handPressure(): Double = ((hand.size - HAND_TARGET) / 4.0).coerceIn(-1.2, 1.5)

    fun threatScale(): Double = (1.0 + handPressure() * 0.18).coerceIn(0.65, 1.35)

    fun spawnChance(): Double = (0.42 + handPressure() * 0.30).coerceIn(0.05, 0.95)

    fun spawnMonster() {
        if (monsters.size >= MAX_MONSTERS) return
        val s = stage
        val nm = monsterNames.random()
        val sk = monsterSkills.random()
        val hp = (s.monsterHp * threatScale()).toInt().coerceAtLeast(1)
        val atk = (s.monsterAtk * threatScale()).toInt().coerceAtLeast(1)
        monsters.add(Monster(nm, hp, hp, atk, sk))
        message = if (hp > s.monsterHp) "有东西从黑暗里挤了出来" else "有什么东西在靠近"
    }

    /** 按当前手牌压力决定是否额外刷怪（不是每回合必刷） */
    fun trySpawn() {
        if (monsters.size >= MAX_MONSTERS) return
        if (Math.random() > spawnChance()) return
        spawnMonster()
        // 手牌特别多时，暗中一次刷两只
        if (handPressure() >= 0.5 && monsters.size < MAX_MONSTERS && Math.random() < 0.5) {
            spawnMonster()
        }
    }

    fun playCard(index: Int): Boolean {
        if (gameOver || cleared || ap <= 0 || index < 0 || index >= hand.size) return false
        // 有未结算的事件/NPC/商人时不允许出牌，避免弹窗与棋盘状态互相打架
        if (activeEvent != null || pendingNpc != null || (pendingMerchant && merchantStock.isNotEmpty())) return false
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
                if (c.skill == "巧手") progress += c.luck * 3
                if (c.skill == "藏匿") shield += c.luck * 3
            }
            CardCategory.COMBAT -> {
                if (monsters.isNotEmpty()) {
                    val idx = target.coerceIn(0, monsters.size - 1)
                    var d = c.v1 + equipAtk()
                    if (c.skill == "撕裂") d += c.luck * 2
                    val crit = Math.random() < (c.luck + luck) * 0.04
                    if (crit) d *= 2
                    if (c.skill == "连击") {
                        hitMonster(idx, d / 2)
                        if (idx < monsters.size) hitMonster(idx, d / 2)
                    } else {
                        hitMonster(idx, d)
                    }
                    if (c.skill == "吸血") hp = min(maxHp, hp + d / 4)
                    if (crit) message = "暴击！" + c.name
                } else {
                    progress += c.v2
                }
            }
            CardCategory.DEFENSE -> {
                var sh = c.v1 + equipArmor()
                if (c.skill == "坚壁") sh += c.luck * 4
                shield += sh
                hp = min(maxHp, hp + c.v2)
                if (c.skill == "反震" && monsters.isNotEmpty()) {
                    for (m in monsters) m.hp -= sh / 4
                    for (i in monsters.indices.reversed()) if (monsters[i].hp <= 0) removeMonster(i)
                }
            }
            CardCategory.MOVEMENT -> {
                progress += c.v1
                shield += c.v2 * 3
                if (c.skill == "疾行") ap += 1
                if (c.skill == "闪避") dodge += 1
            }
            CardCategory.MENTAL -> {
                sanity = min(100, sanity + c.v1 + if (c.skill == "净化") c.luck * 5 else 0)
                progress += c.v2
                if (c.skill == "回响" && hand.size < HAND_MAX) hand.add(CardLibrary.randomOne())
            }
            CardCategory.TRADE -> {
                var gain = c.v1
                if (c.skill == "掠夺") gain += c.luck * 3
                if (c.skill == "议价") gain += c.luck
                coins += gain
                progress += c.v2
            }
        }
        // 幸运掉落：卡牌自带的幸运度会影响额外金币
        if (c.luck > 0 && Math.random() < c.luck * 0.06) coins += 1
        if (luck > 0 && Math.random() < luck * 0.05) coins += 1
        checkState()
        return true
    }

    // 统一处理反伤、死亡与自爆，避免各调用点漏实现
    private fun hitMonster(index: Int, damage: Int) {
        if (index < 0 || index >= monsters.size) return
        val m = monsters[index]
        val real = if (m.skill == "护盾") (damage * 0.7).toInt().coerceAtLeast(1) else damage
        m.hp -= real
        if (m.skill == "反伤") hp -= (real * 0.2).toInt()
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
        // 分裂：死亡时裂成两只更弱的个体
        if (m.skill == "分裂" && monsters.size < MAX_MONSTERS && m.maxHp > 12) {
            repeat(2) {
                if (monsters.size < MAX_MONSTERS) {
                    val childHp = (m.maxHp / 3).coerceAtLeast(6)
                    monsters.add(Monster(m.name + "·碎片", childHp, childHp, (m.atk * 2 / 3).coerceAtLeast(1), "无"))
                }
            }
            message = m.name + " 裂成了碎片！"
        }
        if (monsters.isEmpty()) target = 0 else target = target.coerceIn(0, monsters.size - 1)
    }

    /** 结束回合；返回 0 成功，1 事件待结算，2 NPC 待处理，3 商人待处理（不会静默推进回合） */
    fun endTurn(): Int {
        if (gameOver || cleared || isSpecial) return 0
        if (activeEvent != null) return 1
        if (pendingNpc != null) return 2
        if (pendingMerchant && merchantStock.isNotEmpty()) return 3
        var dmg = 0      // 可被护盾抵挡
        var pierce = 0   // 破甲：直接穿透护盾
        val spawner = monsters.any { it.skill == "召唤" }
        var corrosive = false
        var fear = false
        for (m in monsters) {
            var atk = m.atk
            if (m.skill == "狂暴" && m.hp < m.maxHp / 2) atk *= 2
            if (m.skill == "连击") atk += m.atk / 2
            if (m.skill == "破甲") pierce += atk else dmg += atk
            if (m.skill == "吸血") m.hp = min(m.maxHp, m.hp + m.atk / 2)
            if (m.skill == "回血") m.hp = min(m.maxHp, m.hp + 10)
            if (m.skill == "噬魂") sanity = max(0, sanity - 3)
            if (m.skill == "腐蚀") corrosive = true
            if (m.skill == "恐惧") fear = true
            if (m.skill == "静默" && Math.random() < 0.4) silenced = true
        }
        if (corrosive) {
            maxHp = max(20, maxHp - 2)
            hp = min(hp, maxHp)
            message = "有东西在腐蚀你的身体"
        }
        if (fear) sanity = max(0, sanity - 4)
        if (dodge > 0) {
            dmg = 0
            pierce = 0
            dodge--
            message = "你闪开了这一轮攻击"
        }
        if (spawner && monsters.size < MAX_MONSTERS && Math.random() < 0.3) spawnMonster()
        if (shield > 0) {
            val a = min(shield, dmg)
            shield -= a
            dmg -= a
        }
        if (dmg + pierce > 0) hp -= dmg + pierce

        turn++
        ap = (if (silenced) 2 else 3) + stage.bonusAp
        silenced = false
        if (stage.sanityDrain > 0) sanity = max(0, sanity - stage.sanityDrain)
        boughtThisTurn = false
        // 每回合报价过期，下一回合重新报价（同回合取消对话框不会重刷）
        offerCards.clear()
        offerRerolls = 0

        if (turn % 2 == 0) sanity = max(0, sanity - 1)

        if (turn > 1 && Math.random() < 0.15 && activeEvent == null) {
            activeEvent = allEvents.random()
            // 事件可能在结算前就已经把人打死（扣血/扣理智），这里先判定一次状态
            checkState()
            return 0
        }

        val s = stage
        if (s.goal != GoalType.KILL_ALL && s.goal != GoalType.BOSS && turn % s.spawnEvery == 0) {
            trySpawn()
        }
        refillHand()
        rollMerchant()
        checkState()
        return 0
    }

    /** 回满手牌（回合结束给牌，取代原来的手动刷牌） */
    private fun rollMerchant() {
        pendingMerchant = false
        merchantStock.clear()
        if (Math.random() < MERCHANT_CHANCE) {
            merchantStock.addAll(EquipmentLibrary.random(3))
            pendingMerchant = true
        }
    }

    /** 结算商人的一件装备 */
    fun buyEquipment(index: Int): Int {
        val e = merchantStock.getOrNull(index) ?: return 3
        if (coins < e.price) return 1
        if (equipment.size >= MAX_EQUIP) return 2
        coins -= e.price
        equipment.add(e)
        merchantStock.removeAt(index)
        message = "装备了 " + e.name
        if (merchantStock.isEmpty()) pendingMerchant = false
        return 0
    }

    fun equipAtk(): Int = equipment.sumOf { it.atk }

    fun equipArmor(): Int = equipment.sumOf { it.armor }

    fun equipSlotsLeft(): Int = (MAX_EQUIP - equipment.size).coerceAtLeast(0)

    fun resolveEvent() {
        activeEvent?.let {
            hp = min(maxHp, hp + it.hpChange)
            coins = max(0, coins + it.coinChange)
            progress = max(0, progress + it.progChange)
            sanity = max(0, min(100, sanity + it.sanityChange))
            shield += it.shieldChange
            ap += it.apChange
            message = "事件结算完成"
        }
        activeEvent = null
        val s = stage
        if (s.goal != GoalType.KILL_ALL && s.goal != GoalType.BOSS && turn % s.spawnEvery == 0) {
            trySpawn()
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
            sanity = max(0, sanity)
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
            coins += s.reward + s.bonusCoins
            message = "通关！"
        }
    }

    // ---------- 关卡推进 + NPC ----------

    /** 通关后推进：先掷 NPC，命中则停在 NPC 前，UI 交互后再进关 */
    fun advance() {
        cleared = false
        finishedAll = false
        if (infiniteMode) {
            score++
            pendingStageIndex = levelIndex
            val next = StageLibrary.infinite(score)
            if (next.isSpecialStage) {
                // 进入「!」或「!-!」之前，先 100% 触发一个必定为好的事件
                startStage(levelIndex)
                activeEvent = goodEvent()
                message = "在踏进「" + next.name + "」之前，你先遇到了一件好事"
                return
            }
            if (!rollNpc(next)) startStage(levelIndex)
            return
        }
        if (levelIndex >= StageLibrary.stages.size - 1) {
            finishedAll = true
            return
        }
        val next = levelIndex + 1
        pendingStageIndex = next
        if (!rollNpc(StageLibrary.stages[next])) startStage(next)
    }

    /** 30 层通关后进入无限模式：轮次归零、清掉通关标记，避免重复弹“全部通关” */
    fun enterInfiniteMode() {
        infiniteMode = true
        score = 0
        finishedAll = false
        cleared = false
        gameOver = false
        pendingNpc = null
        activeEvent = null
        startStage(0)
    }

    private fun rollNpc(nextStage: Stage): Boolean {
        // 孤立效应层级没有活人，只会刷怪
        if (nextStage.isolation) return false
        if (Math.random() >= NPC_CHANCE) return false
        val npc = NpcLibrary.random()
        pendingNpc = npc
        npcStock.clear()
        npcStock.addAll(npc.stock)
        npcTalked = false
        npcMeetings++
        message = "在通往「" + nextStage.name + "」的路上遇到了 " + npc.name
        return true
    }

    fun npcAllowed(): Boolean = !stage.isolation

    fun npcTalk(): String {
        val n = pendingNpc ?: return "没有人可以交谈。"
        if (npcTalked) return "你们已经聊过了。"
        npcTalked = true
        return if (n.friendly) {
            val line = n.talkLines.random()
            when ((0..2).random()) {
                0 -> {
                    val heal = min(12, maxHp - hp)
                    hp = min(maxHp, hp + 12)
                    line + "（生命 +" + heal + "）"
                }
                1 -> {
                    coins += 8
                    line + "（金币 +8）"
                }
                else -> {
                    sanity = min(100, sanity + 10)
                    line + "（理智 +10）"
                }
            }
        } else {
            if (Math.random() < 0.45) {
                val lost = min(coins, 12)
                coins -= lost
                hp = max(0, hp - 8)
                checkState()
                "他忽然伸手抢走了你的东西！" + n.talkLines.random() + "（金币 -" + lost + "，生命 -8）"
            } else {
                sanity = max(0, sanity - 8)
                checkState()
                n.talkLines.random() + "（理智 -8）"
            }
        }
    }

    fun npcBuy(index: Int): String {
        val n = pendingNpc ?: return "这里没有商人。"
        val st = npcStock.getOrNull(index) ?: return "这件东西已经不在了。"
        if (coins < st.price) return "金币不足。"
        if (st.card != null && hand.size >= HAND_MAX) return "手牌已满。"
        coins -= st.price
        // 坏 NPC 会卖假货
        if (!n.friendly && Math.random() < 0.35) {
            npcStock.removeAt(index)
            sanity = max(0, sanity - 5)
            checkState()
            return "付了 " + st.price + " 金，盒子是空的……（理智 -5）"
        }
        if (st.card != null) hand.add(st.card)
        if (st.item != null) inventory.add(st.item)
        npcStock.removeAt(index)
        message = "从 " + n.name + " 处买入 " + st.name
        checkState()
        return "获得「" + st.name + "」（-" + st.price + " 金）"
    }

    fun resolveNpc() {
        pendingNpc = null
        npcStock.clear()
        npcTalked = false
        startStage(pendingStageIndex)
    }

    // ---------- 管理员调试接口 ----------

    fun adminSpawn(name: String, hp: Int, atk: Int, skill: String) {
        if (monsters.size >= MAX_MONSTERS) return
        val h = hp.coerceAtLeast(1)
        monsters.add(Monster(name, h, h, atk.coerceAtLeast(0), skill))
        message = "管理员召唤了 " + name
    }

    fun adminKillAll() {
        monsters.clear()
        target = 0
        message = "管理员清空了怪物"
        checkState()
    }

    fun adminKill(index: Int) {
        if (index < 0 || index >= monsters.size) return
        monsters.removeAt(index)
        if (monsters.isEmpty()) target = 0 else target = target.coerceIn(0, monsters.size - 1)
        message = "管理员销毁了一只怪物"
        checkState()
    }

    fun adminJumpTo(idx: Int) {
        infiniteMode = false
        finishedAll = false
        gameOver = false
        startStage(idx.coerceIn(0, StageLibrary.stages.size - 1))
    }

    fun adminJumpInfinite(round: Int) {
        infiniteMode = true
        score = round.coerceAtLeast(0)
        gameOver = false
        startStage(levelIndex)
    }

    fun adminSummonNpc(kind: NpcKind? = null) {
        val npc = if (kind == null) NpcLibrary.random() else NpcLibrary.byKind(kind)
        pendingNpc = npc
        npcStock.clear()
        npcStock.addAll(npc.stock)
        npcTalked = false
        npcMeetings++
        message = "管理员召来了 " + npc.name
    }

    fun adminEndStage() {
        val s = stage
        cleared = false
        when (s.goal) {
            GoalType.KILL_ALL, GoalType.BOSS -> monsters.clear()
            GoalType.SURVIVE -> turn = max(turn, s.goalValue)
            else -> progress = max(progress, s.goalValue)
        }
        checkState()
    }

    fun adminHeal() {
        hp = maxHp
        sanity = 100
        gameOver = false
        message = "管理员恢复了你的状态"
    }

    fun adminCoins(n: Int) {
        coins = max(0, coins + n)
    }

    fun useItem(index: Int) {
        if (gameOver || cleared || index < 0 || index >= inventory.size) return
        val it = inventory.removeAt(index)
        message = "使用了道具 " + it.name
        if (it.luckBonus > 0) luck = min(30, luck + it.luckBonus)
        when (it.category) {
            ItemCategory.HEAL -> {
                hp = min(maxHp, hp + it.power)
                shield += 2
            }
            ItemCategory.HOPE -> {
                hp = min(maxHp, hp + it.power / 2)
                sanity = min(100, sanity + it.power / 2)
            }
            ItemCategory.EXPLORE -> progress += it.power
            ItemCategory.GUARD -> {
                shield += it.power
                if (it.tag == "反震") {
                    for (m in monsters) m.hp -= it.power / 4
                    for (i in monsters.indices.reversed()) if (monsters[i].hp <= 0) removeMonster(i)
                }
            }
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
                if (monsters.isNotEmpty()) {
                    val idx = target.coerceIn(0, monsters.size - 1)
                    val strong = it.tag == "处决" && monsters[idx].hp < monsters[idx].maxHp / 2
                    hitMonster(idx, if (strong) it.power * 2 else it.power)
                }
            }
            ItemCategory.TIME -> {
                ap += 2
                if (it.tag == "静滞") dodge += 1
            }
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

    /** 必定为正收益的事件，用于特殊层级前的保底 */
    fun goodEvent(): GameEvent {
        val titles = listOf("遗落的补给箱", "陌生人的回礼", "墙缝里的捷径", "还亮着的手电")
        val descs = listOf(
            "箱子里整整齐齐码着药品和干粮。",
            "有人把东西放在门口，像是专门留给你的。",
            "墙缝比看起来宽得多，你能直接穿过去。",
            "光束照到的墙面上没有任何划痕。"
        )
        return GameEvent(
            titles.random(), descs.random(),
            hpChange = maxHp / 4, coinChange = 18 + score * 2, progChange = 20,
            sanityChange = 10, shieldChange = 12, tag = "good"
        )
    }

    fun generateSpecialStageName(): String {
        val adj = listOf("扭曲的", "血红的", "耀眼的", "崩坏的", "寂静的")
        val noun = listOf("迷宫", "休息室", "虚空", "祭坛", "捷径")
        return "特殊关卡 " + adj.random() + noun.random()
    }
}

package com.backrooms.cardjourney

enum class CardCategory(val label: String, val color: Int) {
    FOOD("食用牌", 0xFF8BC34A.toInt()),
    REST("休息牌", 0xFF7986CB.toInt()),
    UTILITY("功能牌", 0xFF26A69A.toInt()),
    COMBAT("战斗牌", 0xFFEF5350.toInt()),
    DEFENSE("防御牌", 0xFF42A5F5.toInt()),
    MOVEMENT("移动牌", 0xFFA1887F.toInt()),
    MENTAL("精神牌", 0xFFAB47BC.toInt()),
    TRADE("交易牌", 0xFFFFCA28.toInt())
}

/**
 * weight 是抽卡权重（越大越常见）。数值越高、越少见的牌权重越低，
 * 这样卡池不再等概率刷，强力牌不会满地都是。
 */
data class Card(
    val id: Int,
    val name: String,
    val category: CardCategory,
    val v1: Int,
    val v2: Int,
    val cost: Int,
    val desc: String,
    val weight: Int = 10
) {
    val progress: Int get() = (v1 / 2).coerceAtLeast(1)

    val rarity: String
        get() = when {
            weight >= 24 -> "常见"
            weight >= 10 -> "少见"
            weight >= 4 -> "稀有"
            else -> "传说"
        }

    val rarityColor: Int
        get() = when {
            weight >= 24 -> 0xFF9E9E9E.toInt()
            weight >= 10 -> 0xFF66BB6A.toInt()
            weight >= 4 -> 0xFF42A5F5.toInt()
            else -> 0xFFFFB300.toInt()
        }
}

object CardLibrary {

    private val adjectives = listOf(
        "微弱", "普通", "精良", "优秀", "稀有",
        "史诗", "传说", "神秘", "陈旧", "崭新",
        "破损", "精巧", "原始", "强化", "腐蚀",
        "闪光", "寂静", "温暖", "冰冷", "狂热",
        "冷静", "贪婪", "希望", "绝望", "虚空"
    )

    private val groups: List<Pair<CardCategory, List<String>>> = listOf(
        CardCategory.FOOD to listOf(
            "罐头", "面包", "饼干", "牛奶", "肉干",
            "蘑菇", "糖果", "热汤", "苹果", "巧克力"
        ),
        CardCategory.REST to listOf(
            "睡袋", "折叠床", "旧沙发", "毛毯", "帐篷",
            "吊床", "靠椅", "枕头", "地铺", "休息室"
        ),
        CardCategory.UTILITY to listOf(
            "钥匙", "手电筒", "地图", "扳手", "电池",
            "绳索", "打火机", "胶带", "撬棍", "探测器"
        ),
        CardCategory.COMBAT to listOf(
            "铁棍", "匕首", "斧头", "棒球棍", "钢管",
            "电击器", "长矛", "锤子", "菜刀", "弩"
        ),
        CardCategory.DEFENSE to listOf(
            "护甲", "盾牌", "头盔", "掩体", "绷带",
            "防毒面具", "护目镜", "铁板", "护腕", "防护服"
        ),
        CardCategory.MOVEMENT to listOf(
            "跑鞋", "滑铲", "翻滚", "冲刺", "钩索",
            "闪现", "电梯卡", "通风管", "传送符", "跳板"
        ),
        CardCategory.MENTAL to listOf(
            "冥想", "祈祷", "回忆", "音乐", "日记",
            "照片", "安慰", "催眠", "信念", "微笑"
        ),
        CardCategory.TRADE to listOf(
            "硬币", "钱包", "贩卖机", "以物易物", "折扣券",
            "走私货", "黑市券", "典当票", "小费", "保险箱"
        )
    )

    val all: List<Card> = run {
        val out = ArrayList<Card>(2000)
        var id = 0
        for (g in groups) {
            val cat = g.first
            val nouns = g.second
            for (i in 0 until 250) {
                val adjIdx = i % 25
                val name = adjectives[adjIdx] + nouns[i / 25]
                var v1 = 0
                var v2 = 0
                var d = ""
                when (cat) {
                    CardCategory.FOOD -> {
                        v1 = 5 + i % 30
                        v2 = 1 + i % 12
                        d = "恢复 $v1 生命，$v2 理智"
                    }
                    CardCategory.REST -> {
                        v1 = 3 + i % 14
                        v2 = 1 + i % 3
                        d = "恢复 $v1 生命，+$v2 行动力"
                    }
                    CardCategory.UTILITY -> {
                        v1 = 6 + i % 26
                        v2 = 1 + i % 6
                        d = "进度 +$v1，+$v2 金币"
                    }
                    CardCategory.COMBAT -> {
                        v1 = 4 + i % 30
                        v2 = 2 + i % 12
                        d = "造成 $v1 伤害，进度 +$v2"
                    }
                    CardCategory.DEFENSE -> {
                        v1 = 4 + i % 22
                        v2 = 1 + i % 6
                        d = "+$v1 护盾，恢复 $v2 生命"
                    }
                    CardCategory.MOVEMENT -> {
                        v1 = 5 + i % 22
                        v2 = 1 + i % 4
                        d = "进度 +$v1，闪避 $v2 回合"
                    }
                    CardCategory.MENTAL -> {
                        v1 = 4 + i % 20
                        v2 = 2 + i % 14
                        d = "恢复 $v1 理智，进度 +$v2"
                    }
                    CardCategory.TRADE -> {
                        v1 = 2 + i % 9
                        v2 = 1 + i % 8
                        d = "+$v1 金币，进度 +$v2"
                    }
                }
                // 词条越靠后（越稀有）权重越低，价格越贵
                val w = when {
                    adjIdx < 9 -> 26
                    adjIdx < 17 -> 12
                    adjIdx < 22 -> 4
                    else -> 1
                }
                val price = 2 + when {
                    adjIdx < 9 -> 0
                    adjIdx < 17 -> 1
                    adjIdx < 22 -> 3
                    else -> 5
                }
                out.add(Card(id, name, cat, v1, v2, price, d, w))
                id++
            }
        }
        out
    }

    private fun weightedPick(pool: List<Card>, rand: java.util.Random): Card {
        var total = 0
        for (c in pool) total += c.weight
        var r = rand.nextInt(total.coerceAtLeast(1))
        for (c in pool) {
            r -= c.weight
            if (r < 0) return c
        }
        return pool.last()
    }

    /** 按权重随机 n 张，不重复 */
    fun random(n: Int): List<Card> {
        if (n <= 0) return emptyList()
        val rand = java.util.Random()
        val out = ArrayList<Card>(n)
        val pool = all.toMutableList()
        repeat(n) {
            if (pool.isEmpty()) return@repeat
            val c = weightedPick(pool, rand)
            out.add(c)
            pool.removeAll { it.id == c.id }
        }
        return out
    }

    fun randomOne(): Card = weightedPick(all, java.util.Random())

    /** 起手牌：保证有牌可打（2 张攻击、1 张防御），其余按权重 */
    fun startingHand(target: Int = 6): List<Card> {
        val rand = java.util.Random()
        val out = ArrayList<Card>(target)
        val combatPool = all.filter { it.category == CardCategory.COMBAT }
        val defensePool = all.filter { it.category == CardCategory.DEFENSE }

        fun takeFrom(pool: List<Card>) {
            repeat(30) {
                val c = weightedPick(pool, rand)
                if (out.none { it.id == c.id }) {
                    out.add(c)
                    return
                }
            }
        }

        repeat(2) { takeFrom(combatPool) }
        takeFrom(defensePool)
        while (out.size < target) {
            val c = randomOne()
            if (out.none { it.id == c.id }) out.add(c)
        }
        return out
    }

    /** 商店/买卡界面的报价，权重抽卡且不重复 */
    fun offers(n: Int = 3): List<Card> = random(n)

    // 只抽某一类（NPC 商店按身份售卖）
    fun byCategory(cat: CardCategory, n: Int): List<Card> {
        val rand = java.util.Random()
        val pool = all.filter { it.category == cat }
        val out = ArrayList<Card>(n)
        val rest = pool.toMutableList()
        repeat(n) {
            if (rest.isEmpty()) return@repeat
            val c = weightedPick(rest, rand)
            out.add(c)
            rest.removeAll { it.id == c.id }
        }
        return out
    }

    // id 与下标一一对应，存档只需要保存 id
    fun byId(id: Int): Card? = all.getOrNull(id)
}

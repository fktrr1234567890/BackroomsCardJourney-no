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

data class Card(
    val id: Int,
    val name: String,
    val category: CardCategory,
    val v1: Int,
    val v2: Int,
    val cost: Int,
    val desc: String
) {
    val progress: Int get() = (v1 / 2).coerceAtLeast(1)
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
                val name = adjectives[i % 25] + nouns[i / 25]
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
                out.add(Card(id, name, cat, v1, v2, 2 + (i / 25) / 3, d))
                id++
            }
        }
        out
    }

    fun random(n: Int): List<Card> = all.shuffled().take(n)

    fun randomOne(): Card = all[(Math.random() * all.size).toInt().coerceIn(0, all.size - 1)]

    // id 与下标一一对应，存档只需要保存 id
    fun byId(id: Int): Card? = all.getOrNull(id)
}

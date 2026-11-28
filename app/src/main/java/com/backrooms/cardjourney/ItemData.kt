package com.backrooms.cardjourney

enum class ItemCategory(val label: String, val color: Int) {
    MONSTER("怪物类", 0xFFB71C1C.toInt()),
    FATE("命运类", 0xFF6A1B9A.toInt()),
    HOPE("希望类", 0xFFFFD54F.toInt()),
    DOOM("毁灭类", 0xFFD84315.toInt()),
    HEAL("治愈类", 0xFF66BB6A.toInt()),
    EXPLORE("探索类", 0xFF29B6F6.toInt()),
    TRADE("交易类", 0xFFFFB300.toInt()),
    TIME("时间类", 0xFF26C6DA.toInt()),
    GUARD("防御类", 0xFF5C6BC0.toInt()),
    MYSTIC("神秘类", 0xFF8E24AA.toInt())
}

data class Item(
    val id: Int,
    val name: String,
    val category: ItemCategory,
    val desc: String,
    val price: Int,
    val power: Int,
    /** 携带时的幸运加成（部分道具没有这项机制） */
    val luckBonus: Int = 0,
    /** 额外机制标签：反震 / 处决 / 回响 / 静滞 */
    val tag: String = ""
)

object ItemLibrary {

    private val adjectives = listOf(
        "古老", "破碎", "闪耀", "幽暗", "血色",
        "银白", "腐化", "幸运", "寂静", "燃烧"
    )

    private val groups: List<Pair<ItemCategory, List<String>>> = listOf(
        ItemCategory.MONSTER to listOf(
            "巢穴", "牙齿", "爪痕", "眼睛", "触须",
            "低语", "面具", "脚印", "黏液", "嚎叫"
        ),
        ItemCategory.FATE to listOf(
            "骰子", "塔罗", "硬币", "指针", "红线",
            "星图", "签筒", "罗盘", "天平", "水晶球"
        ),
        ItemCategory.HOPE to listOf(
            "蜡烛", "灯火", "种子", "彩虹", "歌声",
            "羽毛", "晨光", "誓言", "微笑", "信标"
        ),
        ItemCategory.DOOM to listOf(
            "炸弹", "烈焰", "毒雾", "裂痕", "陨石",
            "诅咒", "灰烬", "雷暴", "黑洞", "尖啸"
        ),
        ItemCategory.HEAL to listOf(
            "绷带", "药草", "血清", "温泉", "呼吸",
            "药剂", "糖浆", "圣水", "暖石", "康复符"
        ),
        ItemCategory.EXPLORE to listOf(
            "地图册", "望远镜", "探测仪", "钥匙串", "指南针",
            "绳索", "背包", "手电筒", "相机", "撬锁器"
        ),
        ItemCategory.TRADE to listOf(
            "金币袋", "商会纹章", "契约", "宝石", "账本",
            "拍卖锤", "折扣符", "走私箱", "当票", "钱匣"
        ),
        ItemCategory.TIME to listOf(
            "沙漏", "怀表", "时钟", "延时符", "回溯石",
            "加速器", "静滞环", "日历", "秒针", "钟摆"
        ),
        ItemCategory.GUARD to listOf(
            "壁垒", "护身符", "结界", "铁壁", "护盾核心",
            "荆棘甲", "圣印", "反制阵", "屏障符", "守夜灯"
        ),
        ItemCategory.MYSTIC to listOf(
            "图腾", "符文", "咒语", "符箓", "幻象",
            "幽灵", "灵魂石", "咒印", "祭坛", "异象"
        )
    )

    val all: List<Item> = run {
        val out = ArrayList<Item>(1000)
        var id = 0
        for (g in groups) {
            val cat = g.first
            val nouns = g.second
            for (i in 0 until 100) {
                val name = adjectives[i % 10] + nouns[i / 10]
                // 每三件里有两件带额外机制，剩下一件维持原始效果
                val power = 5 + (i * 7) % 60
                var luckBonus = 0
                var tag = ""
                if (i % 3 != 0) {
                    when (cat) {
                        ItemCategory.FATE -> luckBonus = (i % 4) + 1
                        ItemCategory.HOPE -> luckBonus = 1
                        ItemCategory.TIME -> tag = "静滞"
                        ItemCategory.GUARD -> tag = "反震"
                        ItemCategory.MONSTER -> tag = "处决"
                        ItemCategory.MYSTIC -> luckBonus = 2
                        else -> {}
                    }
                }
                var d = ""
                when (cat) {
                    ItemCategory.MONSTER -> d = "对一只怪物造成 $power 点伤害"
                    ItemCategory.FATE -> d = "进度 +$power，金币 +${power / 5}"
                    ItemCategory.HOPE -> d = "恢复 ${power / 2} 生命与理智"
                    ItemCategory.DOOM -> d = "全体怪物 -$power 生命，自损 ${power / 4}"
                    ItemCategory.HEAL -> d = "恢复 $power 点生命"
                    ItemCategory.EXPLORE -> d = "关卡进度 +$power"
                    ItemCategory.TRADE -> d = "获得 ${power / 2} 金币"
                    ItemCategory.TIME -> d = "立即获得 2 点行动力"
                    ItemCategory.GUARD -> d = "获得 $power 点护盾"
                    ItemCategory.MYSTIC -> d = "恢复 ${power / 3} 生命与理智，进度 +${power / 3}"
                }
                if (luckBonus > 0) d += " · 幸运 +$luckBonus"
                if (tag.isNotEmpty()) d += " · $tag"
                out.add(Item(id, name, cat, d, 5 + (i % 20) + (i / 10), power, luckBonus, tag))
                id++
            }
        }
        out
    }

    fun random(n: Int): List<Item> = all.shuffled().take(n)

    /** NPC 商店按身份售卖固定类别，这里是同类随机取 n 件 */
    fun byCategory(cat: ItemCategory, n: Int): List<Item> =
        all.filter { it.category == cat }.shuffled().take(n)

    fun byId(id: Int): Item? = all.getOrNull(id)
}

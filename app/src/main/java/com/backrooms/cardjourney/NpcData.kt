package com.backrooms.cardjourney

enum class NpcKind(val label: String, val color: Int, val friendly: Boolean) {
    SURVIVOR("幸存者", 0xFF8BC34A.toInt(), true),
    MEDIC("医生", 0xFF4DD0E1.toInt(), true),
    SOLDIER("士兵", 0xFFEF5350.toInt(), true),
    MERCHANT("流浪商人", 0xFFFFCA28.toInt(), true),
    CULTIST("邪教徒", 0xFFAB47BC.toInt(), false),
    RAIDER("掠夺者", 0xFF8D6E63.toInt(), false)
}

data class NpcStock(
    val name: String,
    val desc: String,
    val price: Int,
    val card: Card? = null,
    val item: Item? = null
) {
    val label: String get() = if (card != null) "卡牌" else "道具"
}

data class Npc(
    val name: String,
    val kind: NpcKind,
    val greeting: String,
    val talkLines: List<String>,
    val stock: List<NpcStock>
) {
    val friendly: Boolean get() = kind.friendly
}

object NpcLibrary {

    private val survivorNames = listOf("拾荒者 老K", "躲雨的旅人", "抱纸箱的女孩", "沉默的守夜人")
    private val medicNames = listOf("游医 白手套", "戴口罩的护士", "自称医生的男人")
    private val soldierNames = listOf("掉队士兵 陈", "守备队 07", "独眼的军士")
    private val merchantNames = listOf("灰帽商人", "推车的老贩", "戴礼帽的家伙")
    private val cultistNames = listOf("低语的信徒", "戴骨面具的祭司", "唱赞歌的女人")
    private val raiderNames = listOf("掠夺者 疤脸", "三人团伙的头目", "拎着铁钩的人")

    private fun cards(cat: CardCategory, n: Int, markup: Int = 3): List<NpcStock> =
        CardLibrary.byCategory(cat, n).map { c ->
            NpcStock(c.name, c.desc + "（" + c.rarity + "）", (c.cost * markup).coerceAtLeast(3), card = c)
        }

    private fun items(cat: ItemCategory, n: Int, markup: Int = 2): List<NpcStock> =
        ItemLibrary.byCategory(cat, n).map { it ->
            NpcStock(it.name, it.desc, (it.price * markup).coerceAtLeast(4), item = it)
        }

    /** 生成一个 NPC，库存按其身份决定：幸存者卖吃的，士兵卖武器，邪教徒卖神秘货 */
    fun random(): Npc {
        val kind = when (Math.random()) {
            in 0.0..0.34 -> NpcKind.SURVIVOR
            in 0.34..0.50 -> NpcKind.MEDIC
            in 0.50..0.68 -> NpcKind.SOLDIER
            in 0.68..0.82 -> NpcKind.MERCHANT
            in 0.82..0.92 -> NpcKind.CULTIST
            else -> NpcKind.RAIDER
        }
        return byKind(kind)
    }

    /** 按身份构造 NPC（管理员面板也用它定向召唤） */
    fun byKind(kind: NpcKind): Npc = when (kind) {
            NpcKind.SURVIVOR -> Npc(
                survivorNames.random(),
                kind,
                "「别紧张……我只是在找能吃的。你要不要也来点？」",
                listOf("他告诉你前面哪条走廊塌了。", "他分给你半块压缩饼干。", "他小声说：别信戴骨面具的人。"),
                cards(CardCategory.FOOD, 2) + items(ItemCategory.HEAL, 2)
            )
            NpcKind.MEDIC -> Npc(
                medicNames.random(),
                kind,
                "「伤口处理一下再走，不然你会死在第 6 层。」",
                listOf("他给你缠了绷带。", "他说你的瞳孔不太对劲，塞给你一管药剂。", "他劝你别再往深处走了。"),
                items(ItemCategory.HEAL, 3) + cards(CardCategory.MENTAL, 2)
            )
            NpcKind.SOLDIER -> Npc(
                soldierNames.random(),
                kind,
                "「武器？有。钱？也要。别问我是哪个部队的。」",
                listOf("他教你握棍的姿势。", "他骂骂咧咧地给了你一块巧克力。", "他说他见过第 29 层，然后就闭嘴了。"),
                cards(CardCategory.COMBAT, 3, 3) + items(ItemCategory.GUARD, 1)
            )
            NpcKind.MERCHANT -> Npc(
                merchantNames.random(),
                kind,
                "「什么都卖，什么都不保证。」",
                listOf("他跟你换了点零钱。", "他神秘地说：这层楼里还有别人。", "他抱怨生意越来越难做。"),
                cards(CardCategory.TRADE, 1) + items(ItemCategory.FATE, 2) + items(ItemCategory.EXPLORE, 1)
            )
            NpcKind.CULTIST -> Npc(
                cultistNames.random(),
                kind,
                "「你听见了吗？墙在唱歌。买点东西，它就安静了。」",
                listOf("他的低语让你头疼。", "他称你为「候选人」。", "他突然开始唱一段不成调的歌。"),
                items(ItemCategory.MYSTIC, 3, 3) + cards(CardCategory.MENTAL, 2)
            )
            NpcKind.RAIDER -> Npc(
                raiderNames.random(),
                kind,
                "「把值钱的都放下。……开玩笑的。要买吗？」",
                listOf("他掂了掂手里的铁钩。", "他嘲笑你活不过今晚。", "他问你身上还有多少金币。"),
                cards(CardCategory.COMBAT, 2, 2) + items(ItemCategory.DOOM, 1)
            )
    }
}

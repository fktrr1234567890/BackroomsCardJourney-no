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

    /** 15 个额外 NPC：各有自己的名字、对白与货架，不会与常规 NPC 重名 */
    private val specials: List<Npc> by lazy {
        listOf(
            Npc("提灯的守夜人", NpcKind.SURVIVOR, "「跟着灯走，别跟着声音走。」",
                listOf("他把灯借你照了三秒。", "他说第 6 层的黑暗会吃人。", "他数着走廊里的灯，数到第七盏就停了。"),
                cards(CardCategory.FOOD, 2) + items(ItemCategory.HOPE, 2)),
            Npc("缝合匠 艾拉", NpcKind.MEDIC, "「别动，我把你缝回去。」",
                listOf("她给你缝合了一道旧伤。", "她说你的心跳太快了。", "她问你要不要换一双眼。"),
                items(ItemCategory.HEAL, 2) + items(ItemCategory.MYSTIC, 1, 3)),
            Npc("测绘员 何", NpcKind.SURVIVOR, "「这张图我画了十一年，还没画完。」",
                listOf("他在你的地图上补了一条线。", "他说尽头那扇门不该存在。", "他劝你原路返回。"),
                cards(CardCategory.MOVEMENT, 2) + items(ItemCategory.EXPLORE, 2)),
            Npc("弹药商 卡门", NpcKind.SOLDIER, "「一发换三个金币，不讲价。」",
                listOf("他给了你两发子弹。", "他教你辨别枪声的方向。", "他说他打光过一整箱。"),
                cards(CardCategory.COMBAT, 2) + items(ItemCategory.MONSTER, 2)),
            Npc("旧书店主", NpcKind.MERCHANT, "「书比枪有用，至少它们不会走火。」",
                listOf("他卖给你一本没有结尾的日记。", "他说书里有人写过你的名字。", "他叮嘱你不要读完最后一页。"),
                cards(CardCategory.MENTAL, 2) + cards(CardCategory.UTILITY, 1) + items(ItemCategory.FATE, 1)),
            Npc("戴耳塞的少女", NpcKind.SURVIVOR, "「……你能听见我说话吗？」",
                listOf("她递给你一副耳塞。", "她说墙里有人在唱歌。", "她突然安静下来，指向你身后。"),
                items(ItemCategory.HOPE, 2) + cards(CardCategory.MENTAL, 1)),
            Npc("拾骨人", NpcKind.CULTIST, "「骨头会告诉你路怎么走。」",
                listOf("他把一根指骨放进你口袋。", "他念了一段你听不懂的祷词。", "他说你的骨头很干净。"),
                items(ItemCategory.MYSTIC, 2, 3) + items(ItemCategory.DOOM, 1)),
            Npc("倒退行走的人", NpcKind.CULTIST, "「往前走就是往后走。」",
                listOf("他倒退着给你指了指方向。", "他笑你很勇敢。", "他说他记不起自己的脸。"),
                items(ItemCategory.FATE, 2) + cards(CardCategory.MOVEMENT, 1)),
            Npc("维修工 老周", NpcKind.SURVIVOR, "「灯管坏了？我这儿有备的。」",
                listOf("他帮你修好了手电。", "他抱怨地下配电房没人管。", "他给你留了一把螺丝刀。"),
                items(ItemCategory.GUARD, 2) + items(ItemCategory.TIME, 1)),
            Npc("穿西装的推销员", NpcKind.MERCHANT, "「先生，考虑一下后室置业吗？」",
                listOf("他塞给你一份没人签过的合同。", "他说这里的房价只涨不跌。", "他临走前问你有没有多余的命。"),
                cards(CardCategory.TRADE, 2) + items(ItemCategory.TRADE, 2)),
            Npc("盲眼哨兵", NpcKind.SOLDIER, "「我看不见你，但我知道你在。」",
                listOf("他准确说出了你身上的伤口数。", "他守的那扇门后什么也没有。", "他把刀递给你，说别用在人身上。"),
                cards(CardCategory.COMBAT, 2, 2) + items(ItemCategory.GUARD, 1)),
            Npc("数数的孩子", NpcKind.SURVIVOR, "「一、二、三……你排第七个。」",
                listOf("他数到你的编号就停了。", "他把一颗弹珠放进你手里。", "他问你有没有见过他妈妈。"),
                items(ItemCategory.HOPE, 1) + cards(CardCategory.REST, 1)),
            Npc("雨衣下的医生", NpcKind.MEDIC, "「别问我在滴什么。」",
                listOf("他给你打了一针，你说不清是冷还是暖。", "他说他治好过比你更糟的人。", "他问你要不要保留痛觉。"),
                items(ItemCategory.HEAL, 1) + items(ItemCategory.MYSTIC, 2, 2)),
            Npc("吞币的雕像", NpcKind.CULTIST, "「投一枚，换一个愿望。」",
                listOf("雕像的眼睛动了一下。", "你听见石头肚子里有硬币在响。", "它希望你再多投一点。"),
                items(ItemCategory.FATE, 3, 3) + cards(CardCategory.TRADE, 1)),
            Npc("收尸队的幸存者", NpcKind.RAIDER, "「我不抢活人，只收死人东西。」",
                listOf("他翻出一件还算完整的护具给你。", "他说这层昨天死了九个。", "他让你别死在太显眼的地方。"),
                items(ItemCategory.GUARD, 2) + cards(CardCategory.COMBAT, 1, 2))
        )
    }

    /** 生成一个 NPC，库存按其身份决定：幸存者卖吃的，士兵卖武器，邪教徒卖神秘货 */
    fun random(): Npc {
        // 额外 NPC 出现概率更高，让每次相遇都不太一样
        if (Math.random() < 0.45) return specials.random()
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

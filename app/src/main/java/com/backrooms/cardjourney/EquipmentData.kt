package com.backrooms.cardjourney

/**
 * 装备（商人处出售）。20 件，价格严格跟强度挂钩：
 * 攻击/护甲值越高越贵，避免出现“32 攻击 5 金、4 攻击 45 金”这种倒挂。
 */
enum class EquipSlot(val label: String) {
    WEAPON("武器"), ARMOR("护甲"), GEAR("随身装备")
}

data class Equipment(
    val id: Int,
    val name: String,
    val slot: EquipSlot,
    val atk: Int,
    val armor: Int,
    val desc: String,
    val price: Int
) {
    val tier: String
        get() {
            val p = power()
            return when {
                p >= 26 -> "传说"
                p >= 18 -> "稀有"
                p >= 10 -> "精良"
                else -> "普通"
            }
        }

    fun power(): Int = maxOf(atk, armor)

    /** 强度分：攻击按 1.0 计，护甲按 1.2 计（护甲更稀缺） */
    fun score(): Double = atk + armor * 1.2
}

object EquipmentLibrary {

    /** 价格公式：基础价 + 强度×系数，且强度越高涨幅越快（二次项保证 32 攻击不会便宜） */
    fun priceOf(atk: Int, armor: Int): Int {
        val s = atk + armor * 1.2
        val p = 3 + s * 1.15 + (s * s) / 90.0
        return p.toInt().coerceAtLeast(3)
    }

    val all: List<Equipment> = run {
        fun e(name: String, slot: EquipSlot, atk: Int, armor: Int, desc: String): Equipment =
            Equipment(0, name, slot, atk, armor, desc, priceOf(atk, armor))

        listOf(
            e("生锈的撬棍", EquipSlot.WEAPON, 4, 0, "攻击 +4，轻便好上手"),
            e("木质球棒", EquipSlot.WEAPON, 7, 0, "攻击 +7，挥起来有风声"),
            e("消防斧", EquipSlot.WEAPON, 11, 0, "攻击 +11，破门也行"),
            e("钢筋长矛", EquipSlot.WEAPON, 15, 0, "攻击 +15，能隔着距离捅"),
            e("改装电击器", EquipSlot.WEAPON, 20, 0, "攻击 +20，附带麻痹"),
            e("链锯", EquipSlot.WEAPON, 26, 0, "攻击 +26，轰鸣不止"),
            e("深渊巨刃", EquipSlot.WEAPON, 32, 0, "攻击 +32，来自更深的层级"),
            e("破布护腕", EquipSlot.ARMOR, 0, 3, "护甲 +3，聊胜于无"),
            e("防弹背心", EquipSlot.ARMOR, 0, 7, "护甲 +7"),
            e("焊接护甲", EquipSlot.ARMOR, 0, 11, "护甲 +11，移动略沉"),
            e("防毒面具套装", EquipSlot.ARMOR, 0, 14, "护甲 +14，可过滤孢子"),
            e("工程护盾", EquipSlot.ARMOR, 0, 18, "护甲 +18，能架起来挡"),
            e("装甲板", EquipSlot.ARMOR, 0, 23, "护甲 +23，重型"),
            e("隔绝力场", EquipSlot.ARMOR, 0, 28, "护甲 +28，超自然造物"),
            e("夜视镜", EquipSlot.GEAR, 2, 2, "攻击 +2，护甲 +2，黑暗里能看清"),
            e("拾荒者背包", EquipSlot.GEAR, 3, 3, "攻击 +3，护甲 +3，能多塞点东西"),
            e("应急医疗包", EquipSlot.GEAR, 1, 5, "攻击 +1，护甲 +5，附带绷带"),
            e("诱饵发生器", EquipSlot.GEAR, 5, 0, "攻击 +5，能引开实体"),
            e("信号增幅器", EquipSlot.GEAR, 8, 4, "攻击 +8，护甲 +4，闪烁提示危险"),
            e("后室之匙", EquipSlot.GEAR, 12, 8, "攻击 +12，护甲 +8，据说能开任何门")
        ).mapIndexed { i, it -> it.copy(id = i) }
    }

    fun random(n: Int): List<Equipment> {
        val pool = all.toMutableList()
        val out = ArrayList<Equipment>(n)
        repeat(n) {
            if (pool.isEmpty()) return@repeat
            // 越强越少见
            val weights = pool.map { 1.0 / (1.0 + it.score() / 8.0) }
            val total = weights.sum()
            var r = Math.random() * total
            var idx = 0
            for (i in pool.indices) {
                r -= weights[i]
                if (r <= 0) { idx = i; break }
            }
            out.add(pool[idx])
            pool.removeAt(idx)
        }
        return out
    }

    fun byId(id: Int): Equipment? = all.getOrNull(id)
}

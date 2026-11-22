package com.backrooms.cardjourney

enum class GoalType(val label: String) {
    SEARCH("寻找线索"), ESCAPE("逃脱出口"), PUZZLE("破解谜题"),
    KILL_ALL("清剿怪物"), SURVIVE("生存到底"), BOSS("击杀首领")
}

/**
 * theme 决定背景绘制方案与背景音乐主题，取值 0..5：
 * 0 黄色走廊 / 1 工业灰 / 2 完全黑暗 / 3 冷色办公 / 4 血肉有机 / 5 紫色虚空
 *
 * isolation = 孤立效应：该层级没有任何活人，不会遇到 NPC，只有怪物。
 */
data class Stage(
    val index: Int, val name: String, val subtitle: String,
    val goal: GoalType, val goalValue: Int, val spawnEvery: Int,
    val monsterHp: Int, val monsterAtk: Int, val reward: Int,
    val bgStart: Int, val bgEnd: Int,
    // 清剿类关卡一次刷出的怪物数量，与 goalValue(进度) 解耦，默认沿用旧行为
    val monsterCount: Int = goalValue,
    val isolation: Boolean = false,
    val theme: Int = when {
        index >= 1000 -> 5
        else -> index % 6
    }
)

object StageLibrary {
    val stages: List<Stage> = listOf(
        Stage(0, "Level 0 · 大厅", "潮湿的地毯与永不停歇的荧光灯", GoalType.SEARCH, 40, 3, 16, 5, 8, 0xFFFFFFCC.toInt(), 0xFFD4C485.toInt(), theme = 0),
        Stage(1, "Level 1 · 仓库", "无尽的货架与堆叠的纸箱", GoalType.SEARCH, 55, 3, 20, 6, 9, 0xFFBDBDBD.toInt(), 0xFF757575.toInt(), theme = 1),
        Stage(2, "Level 2 · 管道", "狭窄的通风管道充满回音", GoalType.KILL_ALL, 3, 2, 18, 6, 10, 0xFF424242.toInt(), 0xFF8D6E63.toInt(), theme = 1),
        Stage(3, "Level 3 · 电力站", "电弧在头顶噼啪作响", GoalType.SURVIVE, 12, 2, 20, 7, 11, 0xFF263238.toInt(), 0xFFFFCC80.toInt(), theme = 1),
        Stage(4, "Level 4 · 办公室", "废弃的开放式办公区", GoalType.SEARCH, 75, 4, 24, 8, 12, 0xFFCFD8DC.toInt(), 0xFF90A4AE.toInt(), theme = 3),
        Stage(5, "Level 5 · 旅馆", "华丽却空无一人的走廊", GoalType.ESCAPE, 90, 3, 26, 8, 13, 0xFF800000.toInt(), 0xFF3E2723.toInt(), theme = 4),
        Stage(6, "Level 6 · 黑暗", "完全无光，只能靠听觉", GoalType.SURVIVE, 14, 2, 28, 9, 14, 0xFF000000.toInt(), 0xFF212121.toInt(), isolation = true, theme = 2),
        Stage(7, "Level 7 · 海洋", "冰冷的海水漫过脚踝", GoalType.SEARCH, 100, 3, 30, 9, 15, 0xFF0277BD.toInt(), 0xFF004D40.toInt(), theme = 5),
        Stage(8, "Level 8 · 洞穴", "钟乳石滴落着不明液体", GoalType.KILL_ALL, 4, 2, 32, 10, 16, 0xFF3E2723.toInt(), 0xFF1B0000.toInt(), theme = 4),
        Stage(9, "Level 9 · 郊区", "永远黄昏的住宅区", GoalType.PUZZLE, 110, 3, 34, 10, 17, 0xFFFF8A65.toInt(), 0xFF4E342E.toInt(), theme = 0),
        Stage(10, "Level 10 · 农田", "一人高的麦浪随风晃动", GoalType.SURVIVE, 16, 3, 36, 11, 18, 0xFFFBC02D.toInt(), 0xFF33691E.toInt(), theme = 0),
        Stage(11, "Level 11 · 城市", "无限延伸的水泥森林", GoalType.SEARCH, 130, 4, 38, 11, 19, 0xFF607D8B.toInt(), 0xFF263238.toInt(), theme = 1),
        Stage(12, "Level 12 · 矩阵", "绿色的数据流在墙壁上流淌", GoalType.PUZZLE, 140, 3, 40, 12, 20, 0xFF000000.toInt(), 0xFF00C853.toInt(), isolation = true, theme = 5),
        Stage(13, "Level 13 · 公寓", "一层又一层的单元房", GoalType.ESCAPE, 150, 4, 42, 12, 21, 0xFF8D6E63.toInt(), 0xFF4E342E.toInt(), theme = 3),
        Stage(14, "Level 14 · 天堂", "过于美好，反而令人不安", GoalType.SURVIVE, 18, 5, 44, 13, 22, 0xFFE1F5FE.toInt(), 0xFFB3E5FC.toInt(), theme = 3),
        Stage(15, "Level 15 · 未来", "冰冷的金属与蓝光", GoalType.SEARCH, 160, 3, 46, 13, 23, 0xFF00B0FF.toInt(), 0xFF1A237E.toInt(), theme = 1),
        Stage(16, "Level 16 · 医院", "消毒水味与轮椅的吱呀声", GoalType.KILL_ALL, 5, 2, 48, 14, 24, 0xFFE0E0E0.toInt(), 0xFF9E9E9E.toInt(), theme = 3),
        Stage(17, "Level 17 · 博物馆", "展柜里陈列着不属于人类的东西", GoalType.PUZZLE, 170, 4, 50, 14, 25, 0xFFD7CCC8.toInt(), 0xFF3E2723.toInt(), theme = 3),
        Stage(18, "Level 18 · 记忆", "这里的每一件物品你都似曾相识", GoalType.SURVIVE, 20, 4, 52, 15, 26, 0xFF9FA8DA.toInt(), 0xFF283593.toInt(), isolation = true, theme = 5),
        Stage(19, "Level 19 · 阁楼", "积满灰尘的木梁与旧箱子", GoalType.SEARCH, 180, 3, 54, 15, 27, 0xFF795548.toInt(), 0xFF3E2723.toInt(), theme = 4),
        Stage(20, "Level 20 · 仓库 II", "比一层更深、更暗的仓储区", GoalType.ESCAPE, 190, 4, 56, 16, 28, 0xFF424242.toInt(), 0xFF000000.toInt(), theme = 1),
        Stage(21, "Level 21 · 教堂", "彩窗透进的不是阳光", GoalType.KILL_ALL, 6, 2, 58, 16, 29, 0xFF673AB7.toInt(), 0xFF1A237E.toInt(), theme = 5),
        Stage(22, "Level 22 · 地铁", "列车永远到不了站", GoalType.SURVIVE, 22, 3, 60, 17, 30, 0xFF37474F.toInt(), 0xFF000000.toInt(), isolation = true, theme = 2),
        Stage(23, "Level 23 · 学校", "黑板上写着你没学过的知识", GoalType.PUZZLE, 200, 4, 62, 17, 31, 0xFFAED581.toInt(), 0xFF33691E.toInt(), theme = 3),
        Stage(24, "Level 24 · 游乐场", "旋转木马自己转个不停", GoalType.SURVIVE, 24, 3, 64, 18, 32, 0xFFFF4081.toInt(), 0xFF880E4F.toInt(), theme = 4),
        Stage(25, "Level 25 · 工厂", "传送带仍在运转，却无人操作", GoalType.KILL_ALL, 6, 2, 66, 18, 33, 0xFF795548.toInt(), 0xFF212121.toInt(), theme = 1),
        Stage(26, "Level 26 · 实验室", "培养舱里泡着熟悉的面孔", GoalType.SEARCH, 220, 4, 68, 19, 34, 0xFF80DEEA.toInt(), 0xFF006064.toInt(), theme = 3),
        Stage(27, "Level 27 · 金字塔", "墙壁上的象形文字会移动", GoalType.PUZZLE, 230, 4, 70, 19, 35, 0xFFFFB300.toInt(), 0xFF3E2723.toInt(), theme = 4),
        Stage(28, "Level 28 · 梦境", "你分不清这是梦还是现实", GoalType.SURVIVE, 26, 3, 72, 20, 36, 0xFFE1BEE7.toInt(), 0xFF4A148C.toInt(), isolation = true, theme = 5),
        Stage(29, "Level 29 · 深渊", "后室的最底层，也是最后的机会", GoalType.BOSS, 1, 5, 220, 22, 50, 0xFF000000.toInt(), 0xFFB71C1C.toInt(), isolation = true, theme = 2)
    )

    fun infinite(round: Int): Stage {
        val r = round + 1
        val types = listOf(GoalType.KILL_ALL, GoalType.SEARCH, GoalType.SURVIVE, GoalType.ESCAPE, GoalType.PUZZLE)
        val g = types[(r - 1) % types.size]
        // 不同目标类型的数值含义不同，不能共用一个 60+r*20
        val count = minOf(6, 1 + r / 2)
        val goalValue = when (g) {
            GoalType.SURVIVE -> 8 + r * 2
            GoalType.KILL_ALL -> count
            else -> 60 + r * 20
        }
        return Stage(
            1000 + r, "无限回廊 · 第 $r 轮", "没有尽头的后室", g, goalValue, 3,
            40 + r * 10, 8 + r * 2, 20 + r * 5,
            0xFF1B1B1B.toInt(), 0xFF4A148C.toInt(), count,
            // 深入 4 轮之后进入孤立区：只有怪物，没有 NPC
            isolation = r >= 4,
            theme = 5
        )
    }
}

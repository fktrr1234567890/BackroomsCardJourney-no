# 后室卡牌之旅 / Backrooms Card Journey

一款单机 Android 卡牌 Roguelike 原型：在后室（Backrooms）界面里用卡牌推进进度、对抗实体、管理理智，共 30 个关卡 + 无限模式 + 特殊关卡。

当前版本：**0.0.4（测试版，不推荐直接分发）**

## 玩法

- 每回合 3 点行动力，打出手牌推进进度 / 造成伤害 / 回血回理智。
- 每回合结束补牌到 6 张，怪物在回合结束时攻击，护盾优先抵扣（破甲怪穿透护盾）。
- 卡牌伤害可点击左侧怪物指定目标，被选中的怪金色描边。
- 6 种关卡目标：寻找线索、逃脱出口、破解谜题、清剿怪物、生存到底、击杀首领。
- 8 类卡牌（食用 / 休息 / 功能 / 战斗 / 防御 / 移动 / 精神 / 交易）、10 类道具；卡池与道具由模板 + 数值生成。
- 12 个角色按通关进度解锁，通关 30 关后进入无限模式；每 3 关必出一次特殊关卡。
- 生命 / 理智任意归零即失败；生命、理智、回合、手牌、背包、怪物状态都会存档。

## 构建

需要 JDK 17 与 Android SDK（compileSdk 34）。

```bash
# 首次用 Android Studio 打开，或用本机 gradle 生成 wrapper
gradle wrapper --gradle-version 8.2

./gradlew assembleDebug          # 输出 app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 单元测试（纯 Kotlin 逻辑，无需模拟器）
./gradlew lintDebug             # 静态检查
```

Release 包需要自备签名：

```bash
./gradlew assembleRelease   # 未配置 signingConfig 时产出未签名包
```

## 目录

```
app/src/main/java/com/backrooms/cardjourney/
├── CardData.kt      卡牌与卡池生成
├── ItemData.kt      道具与道具池生成
├── StageData.kt     30 关 + 无限模式关卡表
├── GameEngine.kt    纯 Kotlin 游戏逻辑（无 Android 依赖，可直接跑单元测试）
└── MainActivity.kt  全部界面（手写 View）
```

## 已知问题

- 界面层仍集中在单个 `MainActivity`（约 800 行），后续拆分再做。
- 角色 / 卡牌 / 道具文案硬编码在 Kotlin 中，`strings.xml` 只有应用名，暂不支持多语言。
- 无限模式数值只做了粗调，尚未做长时间压测。
- 卡牌没有稀有度、特殊效果与协同，2000 张本质是 8 个模板的数值变体。

## 许可证

MIT，见 [LICENSE](LICENSE)。

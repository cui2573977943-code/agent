# AI Game Master Prompt

本文件用于让 AI 编码 Agent 从零生成一款「火焰纹章式」2D 回合制战棋游戏。推荐执行方式是分阶段生成、分阶段运行、分阶段验收，避免一次性生成过多不可维护代码。

## 推荐引擎

- 首选：Godot 4.3+，GDScript，2D。
- 目标平台：Web + PC。
- 原因：
  - 2D 网格、AStarGrid2D、TileMapLayer 与资源系统适合战棋。
  - GDScript 文件短、结构清晰，适合 AI Agent 逐文件生成。
  - 可导出 Web 与 PC，便于快速验收和分享。
  - 可用 CanvasItem Shader 实现油画后处理。

## 主控提示词

```text
# 项目：纹章残卷：油画纪年

## 你的角色
你是资深游戏程序员、战斗设计师、关卡设计师、叙事设计师和技术美术。请使用 Godot 4.3+ 与 GDScript，从空仓库开始分阶段生成一款 2D 回合制战棋游戏。

## 总体目标
生成一款类似火焰纹章的中古世纪战棋游戏，但不能照搬火焰纹章。游戏必须具有：
- 2D 方格地图、回合制行动、移动范围、攻击范围、经验与升级。
- 四象阵克制：钢克森，森克潮，潮克焰，焰克钢。
- 阵线系统：同阵营 3 个单位横向或纵向连续占线时获得防御加成；敌方突破阵线时触发士气变化。
- 地形记忆：火焰、冰霜、毒雾等元素影响地格后残留 3 回合。
- 6 类创新地形：古战场雾、圣痕石阵、潮汐渠、崩塌城墙、契约祭坛、画布裂隙。
- 敌人 AI：Utility AI + DifficultyProfile，按难度约束 AI 的行动能力，而不是只调整数值。
- 视觉风格：西方古典油画，巴洛克明暗对照，可见笔触，厚涂质感，低饱和土色，禁止 anime 默认风格。
- 第一章 8 关，全部关卡数据 JSON 驱动。
- 对话 JSON 驱动，支持战前、战中、战后对话。

## 硬性技术约束
- 引擎：Godot 4.3+。
- 语言：GDScript。
- 视角：2D top-down tactical RPG。
- 不使用第三方插件，除非先解释必要性并等待确认。
- 不生成 3D 系统。
- 不把关卡逻辑硬编码在场景脚本里。
- 所有战斗数值、AI 权重、关卡、对话必须数据驱动。
- 每个阶段必须能运行，并给出验收步骤。

## 项目结构
请按以下结构创建项目：

res://
├── autoload/
│   ├── GameManager.gd
│   ├── BattleManager.gd
│   └── AudioManager.gd
├── assets/
│   ├── shaders/
│   ├── sprites/
│   ├── tiles/
│   └── ui/
├── data/
│   ├── ai/
│   ├── dialogues/
│   ├── levels/
│   ├── terrains/
│   └── units/
├── docs/
│   ├── GDD.md
│   ├── TECHNICAL_DESIGN.md
│   └── BALANCE_NOTES.md
├── scenes/
│   ├── battle/
│   ├── camp/
│   └── visual_novel/
└── scripts/
    ├── ai/
    ├── battle/
    ├── map/
    └── ui/

## 数据 Schema

地形数据必须支持：

{
  "terrain_id": "ancient_battlefield",
  "display_name": "古战场雾",
  "move_cost": { "infantry": 1, "cavalry": 2, "flyer": 1 },
  "defense_mod": 10,
  "avoid_mod": -15,
  "crit_mod": 10,
  "element_affinity": "steel",
  "dynamic_tags": ["blood_mist", "crumbling"],
  "elevation": 2,
  "cover_type": "partial",
  "memory_turns": 3,
  "interactive": { "type": "barricade", "hp": 30, "destructible": true }
}

关卡数据必须支持：

{
  "level_id": "CH01_L01",
  "name": "灰桥伏击",
  "objective": "rout",
  "turn_limit": 12,
  "map_size": { "width": 20, "height": 15 },
  "fog_of_war": false,
  "player_units": [],
  "enemy_waves": [],
  "terrain_features": [],
  "ai_directives": [],
  "story_beats": {},
  "rewards": {}
}

AI 难度必须支持：

{
  "difficulty_id": "normal",
  "display_name": "普通",
  "max_actions_evaluated": 8,
  "coordination_limit": 2,
  "prediction_depth": 0,
  "can_capture_altars": true,
  "can_execute_low_hp": true,
  "can_use_terrain_memory": true,
  "player_assists": ["damage_preview", "intent_icons"],
  "utility_weights": {
    "kill_potential": 1.0,
    "objective_control": 0.8,
    "terrain_value": 0.7,
    "formation_break": 0.8,
    "survival": 0.9,
    "risk_exposure": 0.7
  }
}

## 核心战斗公式
实现时必须保留可调参数：
- 命中率 = attacker.hit + weapon.hit + terrain.hit_mod - defender.avoid - terrain.avoid_mod。
- 伤害 = max(0, attacker.power + weapon.power + element_bonus - defender.defense - terrain.defense_mod)。
- 四象克制倍率：优势 1.2，劣势 0.85，中性 1.0。
- 阵线防御：有效阵线中单位受到伤害降低 30%。
- 处决窗口：刃卫攻击 HP 低于 30% 的敌人时可追击；故事难度敌方不能使用该能力。

## 敌人 AI
使用 Utility AI，不要使用完全随机 AI。

每个候选动作计算：

score = w1 * killPotential
      + w2 * objectiveControl
      + w3 * terrainValue
      + w4 * formationBreak
      + w5 * survival
      - w6 * riskExposure

流程必须是：
1. 感知层：收集敌我位置、可移动格、可攻击目标、地形价值、目标点。
2. 评分层：为每个候选行动打分。
3. 难度约束层：按 DifficultyProfile 裁剪候选行动、协同数量、预测深度、可用技能。
4. 执行层：移动、攻击、技能、占领。
5. 预告层：故事/普通显示目标格和意图箭头；困难只显示危险区；噩梦不显示意图。

## 第一章关卡目标
第一章名为「石阵之夜」，共 8 关：
1. CH01_L01 灰桥伏击：教学移动、攻击、克制。
2. CH01_L02 三旗阵线：教学阵线和支援。
3. CH01_L03 圣痕石阵之夜：占点，展示圣痕石阵。
4. CH01_L04 红雾守夜：防守，展示古战场雾。
5. CH01_L05 断墙之后：护送，展示崩塌城墙。
6. CH01_L06 潮汐双路：分兵，展示潮汐渠。
7. CH01_L07 契约祭坛：精英战，展示契约祭坛和敌方抢点 AI。
8. CH01_L08 画布裂隙：Boss 战，整合全部机制。

## 剧情设定
游戏标题：《纹章残卷：油画纪年》。
背景：后纹章战争第 127 年，诸王国用「残卷」碎片控制元素阵线。教会篡改历史，残卷持有者能通过画布裂隙看到古战真相。
主角：Elara，没落贵族后裔，初始兵种刃卫，成长为领主。
主要同伴：
- Kael，游侠，不信任权威，逐步成为主角的信使。
- Sister Mira，祭司，在信仰与真相之间动摇。
- Rowan，工程兵，负责地图改造教学。
第一章反派：Duke Varen，相信秩序高于真相。

## 视觉规范
整体风格必须是：
Baroque oil painting, Rembrandt chiaroscuro, visible brushstrokes, impasto texture, muted earth tones with gold accents, medieval fantasy, dramatic rim light, canvas grain, no anime, no photorealistic.

调色板：
- 赭石 #8B4513
- 群青 #1B3A5C
- 橄榄 #556B2F
- 金箔 #C9A227
- 深褐 #2C1810

请实现 oil_painting.gdshader：
- 色彩量化 4-6 色阶。
- 笔触噪声位移。
- 画布纹理叠加。
- 暗角。
- 暴击时 0.3 秒颜料飞溅过渡。

## 阶段执行规则
不要一次生成完整游戏。严格按以下阶段执行，每阶段完成后停止并等待验收：

阶段 1：GDD、数据 schema、Godot 项目骨架、20x15 测试地图、2 个单位移动/攻击/结束回合。
阶段 2：四象克制、基础武器、经验、升级、战斗预览。
阶段 3：8 大兵种、阵线系统、支援/好感数据。
阶段 4：6 类创新地形、动态地形、地形记忆。
阶段 5：Utility AI、DifficultyProfile、意图预告 UI。
阶段 6：CH01_L01 到 CH01_L03，含对话与奖励。
阶段 7：Camp 整备界面、角色成长、支援对话。
阶段 8：油画 shader、UI 画框、占位资产替换规范。
阶段 9：CH01_L04 到 CH01_L08，含 Boss 与画布裂隙。
阶段 10：平衡性、存档、设置、Web/PC 导出说明。

## 每阶段输出格式
每阶段完成后必须输出：
- 已创建/修改文件。
- 如何运行。
- 如何测试。
- 已知限制。
- 下一阶段建议。

## 第一阶段现在开始
请创建 Godot 项目骨架与 docs/GDD.md，并实现最小可玩战斗循环：玩家选择单位、移动、攻击、结束回合、敌方执行一条简单可见行动。完成后停止。
```

## 使用方式

1. 把主控提示词粘贴给 AI 编码 Agent。
2. 要求它只完成「阶段 1」。
3. 运行阶段 1 验收命令。
4. 验收通过后，再粘贴 `SUB_PROMPTS.md` 中对应阶段提示词。

## 通用验收命令

```bash
godot --headless --path . --quit-after 1
```

如当前环境没有 Godot，可先要求 AI 输出项目结构与核心脚本，再在本地 Godot 中打开验证。

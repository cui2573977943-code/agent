# Sub Prompts: 分阶段生成提示词

每个提示词都应在一个独立阶段使用。不要让 AI 一次完成所有阶段。

## P1-GDD 与项目骨架

```text
请基于 docs/GDD_TEMPLATE.md 创建 Godot 4.3 项目骨架。

输入：
- docs/GDD_TEMPLATE.md
- docs/ART_BIBLE.md

输出：
- project.godot
- docs/GDD.md
- docs/TECHNICAL_DESIGN.md
- autoload/GameManager.gd
- autoload/BattleManager.gd
- scenes/battle/BattleScene.tscn
- scripts/battle/TurnManager.gd
- scripts/battle/Unit.gd
- scripts/map/GridMap.gd

要求：
1. 实现 20x15 测试地图。
2. 实现 2 个玩家单位和 2 个敌方单位。
3. 玩家可以选择单位、移动、攻击、结束回合。
4. 敌方回合先使用最简单的可见行动：向最近玩家单位移动。
5. 关卡数据从 JSON 加载，不硬编码在场景脚本中。

验收：
1. Godot 项目可打开。
2. 玩家单位可移动和攻击。
3. 回合可以在 player/enemy 之间切换。
```

## P2-Combat 战斗公式

```text
请实现四象阵克制、基础武器、战斗预览、经验与升级。

输入：
- docs/GDD.md
- data/levels/CH01_L01.json

输出：
- scripts/battle/CombatFormula.gd
- scripts/battle/WeaponData.gd
- scripts/battle/ElementAffinity.gd
- scripts/ui/CombatPreviewPanel.gd
- data/units/classes.json
- data/units/weapons.json

要求：
1. 四象阵：钢克森、森克潮、潮克焰、焰克钢。
2. 优势倍率 1.2，劣势倍率 0.85，中性 1.0。
3. 战斗预览显示命中、伤害、暴击、克制状态。
4. 击败敌人获得经验，100 经验升级。
5. 升级使用可配置成长率。

验收：
1. Elara 攻击钢系/森系目标时伤害倍率正确。
2. UI 显示克制状态。
3. 击败敌人后经验增加。
```

## P3-ClassFormation 兵种与阵线

```text
请实现 8 大兵种、阵线系统、支援/好感数据。

输入：
- docs/GDD.md
- data/units/classes.json

输出：
- scripts/battle/ClassAbility.gd
- scripts/battle/FormationSystem.gd
- scripts/battle/MoraleSystem.gd
- data/units/supports.json

要求：
1. 实现枪阵军、刃卫、魔导士、游侠、德鲁伊、祭司、工程兵、斥候。
2. 横向或纵向 3 个同阵营连续单位形成阵线。
3. 阵线内单位受到伤害降低 30%。
4. 敌人突破阵线时被突破阵营士气 -1。
5. 好感达到 2 的相邻主将可发动一次将领合击。

验收：
1. 3 个单位连续站位时防御加成生效。
2. 敌方穿过阵线后士气变化。
3. 刃卫处决窗口在目标 HP<30% 时触发。
```

## P4-Terrain 动态地形

```text
请实现 6 类创新地形和地形记忆。

输入：
- docs/GDD.md
- data/levels/CH01_L01.json

输出：
- scripts/map/TerrainSystem.gd
- scripts/map/DynamicTerrain.gd
- scripts/map/TerrainMemory.gd
- data/terrains/base_terrains.json

要求：
1. 古战场雾每 3 回合扩散 1 格。
2. 圣痕石阵回合开始恢复 5% HP。
3. 潮汐渠奇数回合可走，偶数回合不可通行。
4. 崩塌城墙受到范围伤害后变为碎石。
5. 契约祭坛占领后每关可召唤 1 次援军。
6. 画布裂隙触发剧情事件或伏兵。
7. 元素效果残留 3 回合，并影响命中、移动或伤害。

验收：
1. 第 3 回合古战场雾扩散。
2. 潮汐渠随回合改变通行状态。
3. 地形记忆持续 3 回合后消失。
```

## P5-EnemyAI 敌人 AI 与难度

```text
请实现 Utility AI、DifficultyProfile、敌方意图预告 UI。

输入：
- docs/GDD.md
- data/ai/difficulty_profiles.json
- scripts/map/TerrainSystem.gd
- scripts/battle/FormationSystem.gd

输出：
- scripts/ai/UtilityAI.gd
- scripts/ai/ActionScorer.gd
- scripts/ai/DifficultyClipper.gd
- scripts/ai/EnemyIntent.gd
- scripts/ui/EnemyIntentOverlay.gd

要求：
1. 每个敌人先枚举候选移动、攻击、占领、技能动作。
2. 对每个候选动作计算 killPotential、objectiveControl、terrainValue、formationBreak、survival、riskExposure。
3. 按 difficulty_profiles.json 的权重计算 score。
4. 先计算完整分数，再按难度裁剪候选行动。
5. 故事难度不追击残血、不抢祭坛、不协同。
6. 普通难度显示意图图标，困难只显示危险区，噩梦不显示意图。

验收：
1. 故事难度敌人不使用处决窗口。
2. 普通难度敌人会占领圣痕石阵或契约祭坛。
3. 困难难度敌人会优先破阵线。
```

## P6-Level 关卡生成

```text
请根据 GDD 生成第一章 CH01_L01 到 CH01_L03 的完整可玩关卡。

输入：
- docs/GDD.md
- data/ai/difficulty_profiles.json
- data/terrains/base_terrains.json

输出：
- data/levels/CH01_L01.json
- data/levels/CH01_L02.json
- data/levels/CH01_L03.json
- data/dialogues/ch01_l01.json
- data/dialogues/ch01_l02.json
- data/dialogues/ch01_l03.json

要求：
1. 每关都有明确目标、部署点、敌方波次、奖励、剧情事件。
2. L1 教学移动、攻击、四象克制。
3. L2 教学阵线和支援。
4. L3 教学圣痕石阵和抢点 AI。
5. 对话包括 pre_battle、mid_battle、victory。

验收：
1. 三个关卡均可从菜单加载。
2. 每关目标可以完成并进入结算。
3. 对话事件按回合或目标触发。
```

## P7-Camp 整备与剧情

```text
请实现 Camp 整备界面、角色成长、支援对话。

输入：
- docs/GDD.md
- data/units/classes.json
- data/units/supports.json

输出：
- scenes/camp/CampScene.tscn
- scripts/camp/CampManager.gd
- scripts/camp/SupportManager.gd
- scripts/ui/UnitRosterPanel.gd

要求：
1. 战斗间可查看单位、装备、等级、好感。
2. 好感达到阈值后解锁支援对话。
3. Camp 分支只影响好感、下一关增援或奖励，不改变主线结构。
4. 支援对话风格短句、中古口吻、无现代梗。

验收：
1. 完成 L1 后进入 Camp。
2. 可以查看 Elara 和 Kael 的状态。
3. 触发一次支援对话并改变好感。
```

## P8-Art 油画视觉

```text
请根据 docs/ART_BIBLE.md 实现油画风格 Shader、UI 画框和资产生成清单。

输入：
- docs/ART_BIBLE.md

输出：
- assets/shaders/oil_painting.gdshader
- scripts/ui/OilPaintingPostProcess.gd
- assets/ui/README.md
- assets/sprites/README.md
- assets/tiles/README.md

要求：
1. Shader 包含色彩量化、笔触噪声位移、画布纹理叠加、暗角。
2. UI 使用旧木画框、金边、羊皮纸底。
3. README 中列出角色、tile、UI、特效的图像生成 prompt。
4. 暴击时触发 0.3 秒颜料飞溅参数动画。

验收：
1. BattleScene 启用全局油画后处理。
2. 移动范围和攻击范围仍然清晰。
3. UI 不使用 anime 或科幻视觉元素。
```

## P9-FullChapter 第一章扩展

```text
请生成第一章 CH01_L04 到 CH01_L08，并补齐 Boss 战。

输入：
- docs/GDD.md
- 已完成的 CH01_L01 到 CH01_L03

输出：
- data/levels/CH01_L04.json 到 data/levels/CH01_L08.json
- data/dialogues/ch01_l04.json 到 data/dialogues/ch01_l08.json
- data/units/bosses.json

要求：
1. L4 防守红雾。
2. L5 护送并使用崩塌城墙。
3. L6 分兵并使用潮汐渠。
4. L7 契约祭坛精英战。
5. L8 Duke Varen Boss 战，使用画布裂隙和全机制。
6. 第一章结尾获得第一块残卷。

验收：
1. 8 关均可加载并通关。
2. Boss 战至少触发一次画布裂隙。
3. 结尾剧情进入下一章钩子。
```

## P10-Balance 平衡与导出

```text
请进行第一章平衡性 pass、存档、设置与导出说明。

输入：
- docs/GDD.md
- data/levels/*.json
- data/ai/difficulty_profiles.json

输出：
- docs/BALANCE_NOTES.md
- docs/EXPORT_GUIDE.md
- scripts/save/SaveManager.gd
- scenes/settings/SettingsScene.tscn

要求：
1. 每关记录推荐等级、推荐回合数、关键风险。
2. 调整敌方数量、装备和 AI 权重。
3. 实现自动存档和手动存档。
4. 设置界面支持音量、文字速度、难度选择、永久死亡开关。
5. 输出 Web 和 PC 导出步骤。

验收：
1. 从 L1 连续游玩到 L3 不需要手动改数据。
2. 存档后重启可以继续。
3. 导出说明足够让非程序用户执行。
```

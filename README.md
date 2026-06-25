# AI 战棋游戏生成提示词套件

这个仓库提供一套可交给 AI 编码 Agent 使用的完整提示词与数据样例，用于从零生成一款类似火焰纹章、但包含原创兵种/地图/AI/美术方向的 2D 回合制战棋游戏。

## 推荐起点

1. 阅读 `docs/AI_GAME_MASTER_PROMPT.md`。
2. 将主控提示词粘贴给 AI Agent。
3. 让 AI 只执行阶段 1，验收通过后再使用 `docs/SUB_PROMPTS.md` 逐阶段推进。

## 当前进度

- 已完成提示词审查：`docs/PROMPT_AUDIT.md`。
- 已完成阶段 1 Godot 项目骨架：`project.godot`。
- 当前主场景：`scenes/battle/BattleScene.tscn`。
- 当前玩法：选择单位、移动、攻击、等待、结束回合、敌方占位行动。

## 文件说明

- `docs/AI_GAME_MASTER_PROMPT.md`：主控提示词，包含引擎选择、技术约束、核心系统和 10 阶段执行规则。
- `docs/GDD_TEMPLATE.md`：游戏设计文档模板，锁定世界观、兵种、地形、AI、关卡和剧情。
- `docs/GDD.md`：阶段 1 已实现范围。
- `docs/TECHNICAL_DESIGN.md`：阶段 1 技术结构和已知限制。
- `docs/PROMPT_AUDIT.md`：提示词审查与修正记录。
- `docs/ART_BIBLE.md`：西方油画视觉规范和图像生成提示词。
- `docs/SUB_PROMPTS.md`：分模块提示词，用于战斗、地形、AI、关卡、剧情、美术和平衡迭代。
- `data/ai/difficulty_profiles.json`：故事/普通/困难/噩梦四档 AI 能力约束样例。
- `data/levels/CH01_L01.json`：第一关「灰桥伏击」样例关卡数据。

## 运行方式

安装 Godot 4.3+ 后，在仓库根目录运行：

```bash
godot --path .
```

当前云端环境没有安装 Godot，因此本仓库提供静态校验脚本作为阶段 1 的基础验证。

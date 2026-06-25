# AI 战棋游戏生成提示词套件

这个仓库提供一套可交给 AI 编码 Agent 使用的完整提示词与数据样例，用于从零生成一款类似火焰纹章、但包含原创兵种/地图/AI/美术方向的 2D 回合制战棋游戏。

## 推荐起点

1. 阅读 `docs/AI_GAME_MASTER_PROMPT.md`。
2. 将主控提示词粘贴给 AI Agent。
3. 让 AI 只执行阶段 1，验收通过后再使用 `docs/SUB_PROMPTS.md` 逐阶段推进。

## 文件说明

- `docs/AI_GAME_MASTER_PROMPT.md`：主控提示词，包含引擎选择、技术约束、核心系统和 10 阶段执行规则。
- `docs/GDD_TEMPLATE.md`：游戏设计文档模板，锁定世界观、兵种、地形、AI、关卡和剧情。
- `docs/ART_BIBLE.md`：西方油画视觉规范和图像生成提示词。
- `docs/SUB_PROMPTS.md`：分模块提示词，用于战斗、地形、AI、关卡、剧情、美术和平衡迭代。
- `data/ai/difficulty_profiles.json`：故事/普通/困难/噩梦四档 AI 能力约束样例。
- `data/levels/CH01_L01.json`：第一关「灰桥伏击」样例关卡数据。

# Prompt Audit

本文件记录提示词套件在开始生成游戏前的审查结论。

## 审查结论

整体方向可用：Godot 4.3+、2D 战棋、JSON 数据驱动、分阶段生成、Utility AI 与油画视觉的边界清晰，适合让 AI Agent 逐批次生成游戏内容。

## 已修正问题

1. 命中公式引用了 `terrain.hit_mod`，但地形 schema 未声明该字段。
   - 已在 `docs/GDD_TEMPLATE.md` 的地形 schema 中加入 `hit_mod`。
2. 原命中公式对 `terrain.avoid_mod` 的符号容易误解。
   - 已改为 `attacker.hit + weapon.hit - defender.avoid + terrain.hit_mod + terrain.avoid_mod`。
   - 约定古战场雾使用 `avoid_mod = -15` 表示最终命中降低 15。
3. 主控提示词中的 AI 难度 schema 少于实际 JSON 配置字段。
   - 已补充 `can_use_overdraw_magic`、`can_modify_terrain`、`intent_visibility`。
4. 阶段 1 的敌方行动描述可能被误解为提前实现完整 AI。
   - 已明确阶段 1 只实现占位敌方行动，完整 Utility AI 留到阶段 5。

## 保留设计

- Godot 4.3+ 仍为首选引擎。
- 第一章 8 关仍作为长期目标。
- 本批次只执行阶段 1，不提前实现阶段 2-10 的完整系统。
- 现有 `data/ai/difficulty_profiles.json` 与 `data/levels/CH01_L01.json` 可继续作为后续阶段输入。

## 阶段 1 成功标准

- Godot 项目骨架存在。
- `docs/GDD.md` 与 `docs/TECHNICAL_DESIGN.md` 存在。
- `CH01_L01` 可以作为数据源加载。
- 战斗场景包含 20x15 网格、玩家单位、敌方单位。
- 玩家可选择单位、移动、攻击、结束回合。
- 敌方回合执行占位行动：接近最近玩家，若在攻击范围内则攻击。

# Art Bible: 西方油画风战棋视觉规范

本文件用于统一角色、地图、UI、特效和 Shader 的视觉生成方向。

## 1. 核心风格

目标风格：

```text
Baroque oil painting, Rembrandt chiaroscuro, visible brushstrokes, impasto texture, muted earth tones with gold accents, medieval fantasy, dramatic rim light, canvas grain, no anime, no photorealistic
```

中文描述：
- 巴洛克油画。
- 伦勃朗式明暗对照。
- 可见笔触和厚涂质感。
- 低饱和土色，少量金箔高光。
- 中古世纪西方奇幻。
- 避免日系二次元、现代摄影感、塑料质感。

## 2. 调色板

| 用途 | 色名 | Hex | 使用规则 |
| --- | --- | --- | --- |
| 主暖色 | 赭石 | `#8B4513` | 地面、皮革、旧木 |
| 主冷色 | 群青 | `#1B3A5C` | 夜景、贵族布料、阴影 |
| 自然色 | 橄榄 | `#556B2F` | 森林、披风、腐蚀地形 |
| 高光色 | 金箔 | `#C9A227` | 圣物、按钮边缘、残卷 |
| 阴影色 | 深褐 | `#2C1810` | 暗部，禁止纯黑 |
| 血雾色 | 暗红 | `#5C1E1E` | 古战场雾、暴击过渡 |

## 3. 构图规则

- 角色立绘采用半身像，三分之二侧脸或正面。
- 地图资产采用 top-down tactical tile。
- UI 使用旧木画框、金边、羊皮纸底纹。
- 战斗特效像颜料扩散，不像霓虹粒子。
- 暴击和合击可以短暂切换到「油画闪回」构图：强侧光、大阴影、背景简化。

## 4. 资产规格

| 资产类型 | 推荐尺寸 | 格式 | 说明 |
| --- | --- | --- | --- |
| 角色立绘 | 512x512 | PNG/WebP | 透明背景，半身 |
| 地图 tile | 64x64 或 128x128 | PNG/WebP | 无缝拼接，笔触方向一致 |
| 单位 sprite | 64x64 | PNG/WebP | 小比例可读，阵营色明显 |
| 战斗特效 | 8-12 帧 | PNG 序列 | 颜料飞溅、火焰、雾 |
| UI 框 | 9-slice | PNG | 旧木框 + 金边 |
| 画布纹理 | 512x512 | PNG | 可平铺，低对比 |

## 5. 通用负面提示词

```text
anime, manga, chibi, photorealistic, sci-fi armor, plastic material, neon cyberpunk, modern clothing, clean vector art, flat icon, 3d render, glossy, high saturation, cartoon, comic book
```

## 6. 风格锚点提示词

用于先生成一张全局风格锚点图，后续角色和地图提示词都引用它。

```text
A medieval tactical battlefield at dusk, ruined stone bridge, banners torn by wind, armored soldiers seen from a painterly distance, Baroque oil painting, Rembrandt chiaroscuro, visible brushstrokes, impasto texture, muted earth tones, gold accents on sacred relics, dramatic rim light, canvas grain, no anime, no photorealistic
```

## 7. 角色生成提示词模板

```text
Character sheet for [CHARACTER_NAME], [CLASS_NAME] in a medieval fantasy tactical RPG, three poses front side back, consistent outfit, readable silhouette for 2D tactical game, [PERSONALITY_TRAITS], Baroque oil painting, Rembrandt chiaroscuro, visible brushstrokes, impasto texture, muted earth tones with gold accents, canvas grain, transparent background, no anime, no photorealistic
```

示例：

```text
Character sheet for Elara, fallen noble bladeguard and future lord, crimson cloak, worn steel cuirass, old family crest, determined but haunted expression, three poses front side back, consistent outfit, readable silhouette for 2D tactical game, Baroque oil painting, Rembrandt chiaroscuro, visible brushstrokes, impasto texture, muted earth tones with gold accents, canvas grain, transparent background, no anime, no photorealistic
```

## 8. 地图 tile 提示词模板

```text
Seamless top-down tactical RPG tile, [TERRAIN_NAME], 64x64, readable grid, painterly medieval fantasy, Baroque oil painting, visible brushstrokes, muted earth tones, canvas grain, no anime, no photorealistic, no UI, no characters
```

示例：

```text
Seamless top-down tactical RPG tile, ancient battlefield mist over cracked earth and old bones, dark red brushstroke fog, 64x64, readable grid, painterly medieval fantasy, Baroque oil painting, visible brushstrokes, muted earth tones, canvas grain, no anime, no photorealistic, no UI, no characters
```

## 9. UI 提示词模板

```text
Medieval tactical RPG UI frame, old dark wood, subtle gold leaf border, parchment inner panel, oil painting texture, Rembrandt inspired shadows, readable empty center, 9-slice friendly, no text, no icons, no anime, no photorealistic
```

## 10. 过场插图提示词模板

```text
Cinematic story illustration, [SCENE_DESCRIPTION], medieval fantasy, dramatic side lighting, Baroque oil painting, Rembrandt chiaroscuro, visible brushstrokes, impasto texture, muted earth tones, gold accents, canvas grain, no anime, no photorealistic
```

## 11. Godot Shader 生成提示词

```text
请为 Godot 4.3 编写一个 canvas_item shader，文件名为 assets/shaders/oil_painting.gdshader。效果包括：
1. 对屏幕颜色做 4-6 级色彩量化。
2. 使用噪声纹理对 UV 做轻微笔触位移。
3. 叠加可平铺画布纹理，multiply 强度 0.08。
4. 添加暗角，不使用纯黑。
5. 暴击时通过 uniform paint_splash_amount 在 0.3 秒内增强红褐色颜料飞溅效果。
请暴露 uniforms：palette_steps, brush_strength, canvas_strength, vignette_strength, paint_splash_amount。
```

## 12. 可读性规则

- 地图格必须可读，油画笔触不能遮挡移动范围。
- 阵营色必须明显：玩家偏群青，敌人偏暗红，中立偏橄榄。
- 金箔色只用于可交互重点：目标点、圣物、剧情物件、确认按钮。
- 禁止纯黑描边；使用深褐作为暗部。
- 移动范围与攻击范围 UI 要用半透明色块，不要用复杂纹理。

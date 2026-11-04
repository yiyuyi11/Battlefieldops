# 656KillIcon 绘制层移植规格

本文件记录从 `.ref-killicon` 提取的**精确数学**，用于实现 `cn.bfops.KillFx*`。
所有数值均为参考端原值，不是估算。

## 1. HudElementManager（`HudElementManager.java`）

- 注册表：`Map<category, Map<name, IHudRenderer>>`，元素 id = `category + "/" + name`
- 触发：`trigger(category, name, TriggerContext)`
- 绘制条件：`mc.screen == null || mc.screen instanceof ChatScreen`
- 每帧：`enableBlend` / `defaultBlendFunc` / `setShaderColor(1,1,1,1)`，末尾 `finally` 复位
- 基色：每个元素独立读取 `rotation_angle`（默认 0）
  - `|angle| <= 0.001` → 直接 `render`
  - 否则绕 `ScreenAnchor.resolveCenter(screen_anchor, x_offset, y_offset)` 旋转后 `render`
- **alpha 契约**：渲染器不得依赖外部 shader color，必须自行设置并复位

`TriggerContext(type, entityId, comboCount, extraData, distance, bonusMultiplier, bonusScale)`
- `type` 是 `KillType` 数字（0..10）

## 2. IconRingEffect（`IconRingEffect.java`）

常量：
- `EFFECT_DELAY_MS = 100`
- `EFFECT_DURATION_MS = 300`，爆炸 `maxEffectDuration = 400`
- `BASE_RADIUS_PX = 10`，`RADIUS_GROWTH_PX = 32`，`BASE_THICKNESS_PX = 3`
- `SEGMENTS = 72`
- 爆炸第二环延迟 `100ms`，厚度倍率 `1.8`
- 暴击厚度倍率 `0.6`

默认环参数（`normal` / `headshot` / `explosion`）：
- maxRadius 均 `42.0`
- thickness `1.8000001` / `3.0` / `5.3999996`

触发：仅 `killType ∈ {1 headshot, 2 explosion, 3 crit}` 且 `enabled`；
`effectStartTimeMs = triggerTime + 100`

绘制（`t = clamp(elapsed / 300, 0, 1)`）：
- `eased = 1 - (1-t)^3`
- `alpha = (1-t)^2`
- 半径：`baseRatio = 0.23809524`，`min = maxRadius * ratio`，`radius = (min + (max-min)*eased) * scale`
- 厚度：`thickness * (1-t) * scale`
- type=1：单环用 `headshotRgb`
- type=2：主环同 headshot 参数；第二环 `ring2Elapsed = elapsed - 100`，
  `t2 = ring2Elapsed/300`，`eased2 = 1-(1-t2)^3`，`alpha2 = (1-t2)^2`，
  半径用 `explosionMaxRadius`，厚度 `explosionThickness * (1-t2) * scale`，色 `explosionRgb`
- type=3：半径用 `normalMaxRadius`，厚度 `normalThickness`，色 `critRgb`

环绘制：TRIANGLE_STRIP，72 段，`rOuter = radius + thickness/2`，
`rInner = max(0, radius - thickness/2)`，`a = alpha*255`，纯色无纹理（`GameRenderer::getPositionColorShader`）

## 3. IconGlowProcessor（`IconGlowProcessor.java`）

缓存键：`"%s:%s:%b:%06X:%.3f:%.3f"` = `presetId : texturePath : accentTintEnabled : accentColor&0xFFFFFF : brightness : contrast`

逐像素（`applyBrightnessContrastToRgb` 同源）：
1. `alpha <= 0` → 返回 0（保持全透明）
2. accent 重染（`accentTintEnabled && isAccentPixel(argb)`）：
   - `luminance = max(r,g,b) / 255`
   - `r = accent.r * luminance`，`g/g/b` 同理（四舍五入）
3. 亮度/对比度（每通道）：
   - `normalized = channel / 255`
   - `contrasted = (normalized - 0.5) * contrast + 0.5`
   - `adjusted = contrasted * brightness`
   - `round(adjusted * 255)` 并 clamp 到 `[0,255]`

`isAccentPixel`：`alpha >= 16` 且用 `RGBtoHSB` 判定
`brightness ∈ [0.18, 0.98]` 且 `saturation >= 0.18`

## 4. IconGlowRenderEffect（`IconGlowRenderEffect.java`）

8 方向偏移：
`(-1,-1) (0,-1) (1,-1) (-1,0) (1,0) (-1,1) (0,1) (1,1)`

配置默认：
- `enable_icon_glow`（bool，无默认=关闭）
- `color_icon_glow` 默认 `0xFFFFFF`
- `icon_glow_intensity` 默认 `0.45`（clamp 0..1）
- `icon_glow_size` 默认 `4.0`（`max(0, …)`）

绘制（混合 `SRC_ALPHA, ONE` 加法）：
- `glowAlpha = clamp(alpha * intensity, 0, 1)`
- `outerSpread = max(0.5, size)`
- `innerSpread = outerSpread * 0.55`
- 两轮 8 方向，偏移量分别用 `outerSpread` / `innerSpread`
- alpha 依次 `glowAlpha * 0.16`（外圈）、`glowAlpha * 0.11`（内圈）
- 最后正中一次 `glowAlpha * 0.09`
- 结束 `defaultBlendFunc()` + `setShaderColor(1,1,1,1)`

## 5. IconEntranceBackground（`IconEntranceBackground.java`）

`peakAlpha = clamp(1 - peakTransparency, 0, 1)`（注意是**反转**的）

方块：
- `elapsed < fadeIn`：`alpha = peakAlpha * elapsed/fadeIn`，`scale = 1`
- 否则：`progress = (elapsed - fadeIn)/fadeOut`，`alpha = peakAlpha*(1-progress)`，`scale = 1-progress`
- 跳过阈值 `alpha <= 0.001 || scale <= 0.001`
- 以 `(centerX, centerY)` 为锚，`translate + scale(scale,scale) + translate(-size/2,-size/2)`

边框（四边 quad + 对角线射线）：
- `half = size*scale/2`，`maxDiag = half * 1.4142135`
- `quarterX = fadeIn / 4`
- `elapsed < quarterX`：`diagLen = maxDiag * (1 - elapsed/quarterX)`
- `elapsed < fadeIn`：`diagLen = maxDiag * ((elapsed - quarterX)/(fadeIn - quarterX))`
- 否则 `twoThirdsZ = fadeOut*2/3`，`outProgress = elapsed - fadeIn`，
  `diagLen = outProgress < twoThirdsZ ? maxDiag*(1 - outProgress/twoThirdsZ) : 0`
- 四个边框 quad 顺序：上、下、左、右
- 射线投影 `rayProj = diagLen * 0.70710677`，线宽 `halfThickness = borderWidth/2`

`parseColor(hex, fallback)`：去 `#`，长度必须 6，`parseInt(hex,16) & 0xFFFFFF`

## 6. TextScrambleEffect（`TextScrambleEffect.java`）

- 三段字符池：拉丁、数字、CJK（`的一是在不了有和人这中大为上个国我以要他时来用们生到作地于出就分对成会可主发年样能下部动方出进面家种已心`，41 字）
- `isScrambleable`：非空白、非数字、且不是 `+ - . , ( ) : |`
- 每字符揭示窗口：`charRevealStart = i/len * 0.4`，`charRevealDuration = 0.6`
  - `charProgress = clamp((progress - start)/0.6, 0, 1)`
  - `>= 1` 显示真字符，否则显示缓存随机字符
- 刷新按 `refreshRate`（毫秒）批刷新
- `isFinished`：`!active || elapsed >= duration`

## 7. DigitalScrollEffect（`DigitalScrollEffect.java`）

默认 `animationDuration = 1.25s`，`animationRefreshRate = 0.01s`，`easing = QUINTIC_OUT`

- `refreshRate` 未到（`< refreshRate*1000` ms）→ 返回当前值，不重算
- `progress = min(elapsedSeconds / duration, 1)`
- easing：
  - `QUINTIC_OUT`：`1-(1-p)^5`
  - `CUBIC_OUT`：`1-(1-p)^3`
  - `LINEAR`：`p`
- `currentValue = lerp(eased, start, target)`；`progress >= 1` 时锁定为 target 并停止

## 8. TextFadeEffect（`TextFadeEffect.java`）

- `MIN_ALPHA = 0.1`（永远不消失到 0）
- 非闪烁：`alpha = 1 - fadeProgress`
- 闪烁：`p < 1/3` → `1 - 2.4p`；`p < 2/3` → `2.4p - 0.6`；否则 `3(1-p)`
- 返回 `max(0.1, clamp(alpha, 0, 1))`

## 9. SubtitleEntranceBackground（`SubtitleEntranceBackground.java`）

- `INITIAL_WIDTH = 2`，`EDGE_GAP = 2`
- `maxWidth = (textRight - textLeft) + 4`
- `peakAlpha = clamp(1 - peakTransparency, 0, 1)`
- 三段：
  - `elapsed < fadeIn`：`alpha = peakAlpha*elapsed/fadeIn`，`width = 2`
  - `elapsed < fadeIn + sweepMs`：`t = (elapsed-fadeIn)/sweepMs`，
    `eased = 1-(1-clamp(t))^3`，`alpha = peakAlpha`，`width = lerp(eased, 2, maxWidth)`
  - 否则：`p = (elapsed-fadeIn-sweepMs)/fadeOut`，`alpha = peakAlpha*(1-clamp(p))`，`width = maxWidth`
- `mirror`：`right = textRight + 2`，`left = right - width`；否则 `left = textLeft - 2`
- 高度：`midY ± (textHeight + 2)/2`

## 10. BonusTextBox（`BonusTextBox.java`）

见源码（3.0 KB），待移植时逐行对照。

---

## 与我方现状的差距

| 方面 | 参考端 | 我方（移植前） |
| --- | --- | --- |
| 元素注册 | 注册表 + category/name | 硬编码 switch |
| 旋转 | `rotation_angle` 绕锚点 | 无 |
| 环形特效 | 72 段三角带 + 三段缓动 | 无（只画圆点） |
| 辉光 | 8 方向 × 两级扩散 × 加法混合 | 无 |
| 贴图重染 | HSB accent 判定 + 亮度对比度 | 无（只做 tint 乘法） |
| 入场背景 | 四边 + 对角线射线 | 无 |
| 文字乱码 | 三字符池 + 逐字揭示 | 无 |
| 数字滚动 | 五段缓出 + 刷新率节流 | 无（直接跳变） |
| 文字淡出 | 闪烁曲线 + 最低 alpha 0.1 | 线性淡出到 0 |
| 字幕背景 | 三段扫光 + mirror | 无 |

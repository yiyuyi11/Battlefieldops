# Battlefield Ops — 参考实现笔记

本文件记录本次工作中确认的环境事实、从参考整合包里提取的写法，以及已经验证过的行为。
凡是没有实际运行验证的内容都明确标注，不写成「已完成」。

## 1. 环境事实（已实测）

| 项 | 值 | 来源 |
|---|---|---|
| JDK | 21.0.9 LTS (Oracle) | `java -version` |
| 开发目标 | NeoForge 21.1.233 / MC 1.21.1 | `BattlefieldOps/build.gradle` |
| 整合包实际 NeoForge | 21.1.242 | `E:\...\Battlefield656\logs` / `latest.log` 的 `neoforge-21.1.242-universal.jar` |
| TaCZ | 1.1.8 (`tacz-neoforge-1.21.1-1.1.8-hotfix-r1.jar`) | 服务器 mods 列表 + `javap` 校验 API |
| 卓越前线 | 0.8.9.1 / 0.8.9.2 (`superbwarfare-*-mc1.21.1-*.jar`) | 服务器 mods 列表 |
| 客户端整合包 | `E:\QQ文件\Battlefield656-六五六大战场服务器客户端\...\versions\Battlefield656` | 目录存在，含 TaCZ 枪包 12 个、载具包 `limitless_vehicle` |
| `manual_update_package.zip` | 只有更新器（UpdateAgent + mc-update.properties），无游戏内容 | 解包确认 |

结论：**参考对象是 1.20.1 的 Battlefield656 私服整合包**，其中的 `gd656conquest`（征服/突破对局）、
`gd656killicon`、`gd656frontvoice` 等是别人写的 1.20.1 Forge mod。它们不能直接用于 1.21.1 NeoForge，
但逻辑写法可移植。`Battlefield656.jar` 是混淆过的客户端（7436 个 `a.class` 形式类），不可作参考。

## 2. 从参考模组提取并已移植的写法

反编译对象：`gd656conquest-0.10.6-normal.jar`（272 个类反编译成功，输出在 `.ref/gdc-src/`）。

### 2.1 占领控制条（替代布尔旗标）
参考：`server/room/gameplay/conquest/ConquestCaptureRuntimeService.java`、
`server/room/gameplay/breakthrough/BreakthroughCaptureRuntimeService.java`

参考实现统计每个据点内双方人数，按 `ratio = (a-b)/(a+b)` 推进一个 **0~100 的 progress**，
再用 `Mth.clamp(progress, 0.0, 100.0)` 夹住，最后由 progress 反推归属。

移植到 `Rules.java`：
- `Flag.progress` 0~100，50 为中立；`owner()` 从 progress 推导，跨越中点先中立再易手。
- 3 攻 1 守仍会推进（只是变慢），人数相等时停滞——这修掉了原来「一个防守方冻结整个占领」的问题。
- 归属变化以 `Capture(point, team, neutralised)` 记录，中立化不给个人分。
- **一次 `tick()` 即一秒**（引擎每秒调用一次），所以 `delta = 100.0 / captureSeconds`。

### 2.2 硬边界多边形判定
参考：`server/room/runtime/RoomBoundaryPolygonSupport.java`、`RoomBoundaryRuleEvaluator.java`

参考实现用奇偶射线法 `isPointInsideBoundary(x, z, polygon)`，并对「未配置边界」返回 `true`（不惩罚）。
移植为 `Model.contains(boundary, x, z)`，并在 `Engine.tick()` 中用于越界判定：
越界先给宽限，超时按阵亡处理（对应参考的 `boundaryPenaltySeconds` 机制）。

### 2.3 载具自动发现（不写死附属）
参考：`server/compat/superbwarfare/SuperbWarfareVehicleCompatBridge.java`

参考实现**不读硬编码 ID 列表**，而是遍历 `BuiltInRegistries.ENTITY_TYPE`，对每个类型
`type.create(level)` 造一个探针实体，用 `VehicleEntity.class.isInstance(probe)` 判断
（`com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity`），随后 `probe.discard()`。

移植到 `Catalog.scanVehicles()`：同样遍历注册表 + 探针 + `discard()`，因此任何注册了
`VehicleEntity` 子类的附属载具包（`ywzj_vehicle`、vehicle packs）都会自动出现，无需改代码。
`ModList.get().isLoaded(...)` 用于可选依赖探测。

### 2.4 TaCZ 名称解析
参考：`server/loadout/TaczDisplayNameResolver.java`

参考实现从 `TimelessAPI` 取 index，再逐层回落到语言文件与 `prettify`。移植到
`Catalog.gunName()`：`CommonGunIndex.getPojo().getName()` 得到翻译键，查 `lang/zh_cn.json` → `lang/en_us.json`，
最后回落到 ID 美化。服务端扫描时一次性加载全部语言的 lang 表，避免客户端查询。

### 2.5 部署即改配装、离开即恢复
参考：`server/room/runtime/RoomLoadoutGrantService.java`、`RoomPlayerRespawnService.java`

参考实现只在部署时发放配装，并把原背包备份。移植为 `Engine.backup()/recover()`：
加入对局时把背包 + 位置 + 模式 + 血量 + 饥饿值写入
`<world>/battlefield_ops/players/<uuid>.nbt`，退出 / 对局结束 / 服务器关闭时恢复并删除备份。
备份文件存在时**拒绝再次备份**，防止覆盖丢失。

## 3. 服务端校验（防越权）

客户端只能「请求」，所有判定在 `Engine`：

- 保存地图：`MapRequire OP 2` + `Model.Arena.validate()`（边界不自交、面积下限、据点/出生点必须在边界内、突破阶段连续、双方出生点齐全）+ `Catalog.check()`（武器/载具必须真实存在于注册表）。
- 部署：配装必须在 `map.kits` 内、出生点必须属于自己阵营且阶段匹配、重生倒计时结束、出生点无碰撞。
- 载具：必须在 `map.vehicles` 白名单内、`credits >= vehicleCost`、冷却结束、局内数量上限、实体必须是卓越前线载具。
- 计分：tickets / score / credits 只在服务端变更，客户端 `state` 仅回读。
- 伤害：`blockDamage` 拦截友军伤害、跨对局伤害、未部署玩家的伤害。

## 4. 已验证 / 未验证

**已验证**
- 编译通过：`python build.py` → `build/libs/battlefield-ops-0.1.0-neoforge-1.21.1.jar`（0 error）。
- 规则仿真全绿：`sim/RulesSim.java`，23 条断言覆盖征服扣票、僵持不掉票、争夺中推进、突破满阶段推进、
  阶段奖励、超时防守方胜、突破仅攻方扣票、中立化不计分。

复跑方式：
```
javac --release 21 -encoding UTF-8 -proc:none -d build/sim sim/RulesSim.java \
      src/main/java/cn/bfops/Model.java src/main/java/cn/bfops/Rules.java
java "-Dfile.encoding=UTF-8" -cp build/sim RulesSim
```

**未验证（需你有环境才能确认）**
- 未在真实服务器启动：工作区没有 1.21.1 服务端运行目录，`E:\server_packed` 当前不可读。
  因此 `@Mod` 加载、`RegisterPayloadHandlersEvent` 包注册、TaCZ/SBW 反射调用**仅编译期确认**。
- 未实机测试 GUI 交互与粒子圈显示。
- TaCZ 枪械构建用的 `GunItemBuilder.build(HolderLookup.Provider)` 与 `AmmoItemBuilder.build()`
  已按 1.1.8 的 `javap` 签名编写，但未实际生成物品验证。

首次实机建议顺序：单人创造 → 按 `B` → 「地图划线」画边界 → 「据点/出生」摆点 →
「规则」调数值 → 「武器/载具库」→「重扫整合包」确认目录非空 → 「保存」→「开始本图对局」→「加入蓝方」→「部署」。
若「重扫整合包」显示 0 把武器，看 `message` 行的 `diagnostics`，它会告诉你缺哪个 mod 或哪个反射失败。

## 5. 部署界面与小地图

### 5.1 部署页
`OpsScreen.deployment()` 重排为**单列竖排 + 右侧小地图**：

- 左列自上而下：加入蓝 / 红 → 退出恢复背包 → 配装（下方显示主武器 / 副武器）→
  出生点 → 载具（下方显示费用与冷却剩余）→ 部署 → 重新部署 → 管理员区（开始 / 结束 / 删除）。
- 右侧小地图，尺寸 `min(窗口宽-232, 窗口高-150)`；窗口太小时自动隐藏（阈值 96px），
  这时退回纯按钮布局，不会挤成一团。
- 小地图上**左键点出生点标记即可切换出生点**（`pickSpawn()` 取 12px 内最近的一个，
  并再次校验它属于自己阵营且符合当前突破阶段）。
- `deployBottom` 记录按钮区结束位置，状态文本自动下移，避免与按钮重叠。

### 5.2 小地图标识
新增 `Minimap.java`，纯绘制几何类（不读写对局状态），由 **HUD 与部署页共用**，两者不会画得不一样。

| 元素 | 表现 |
|---|---|
| 网格 | 8×8 参考线 |
| 全图边界 | 蓝色多边形 |
| 突破阶段边界 | 暗黄多边形 |
| 据点 | 半径圈（归属色 40% 透明）+ 归属色方块 + 点名 + **0~100 控制条**（蓝左红右）+ 争夺中显示 `!` |
| 出生点 | 十字标记按阵营着色；部署页只显示己方，选中的加白框并标名字 |
| 队友 | 青色小方块 |
| 自己 | 白色三角箭头，按 `yaw` 旋转 |

- 投影等比正方形（同样格数在 X / Z 上像素量相同）；`fit()` 自动包围整个边界并留 15% 边距，
  无边界时退回 256 格窗口。
- HUD 小地图固定右上角，尺寸 `min(120, 窗口宽-268)`，小于 72px 不画。
- 队友位置来自 `Engine.state()` 新增的 `mates` 字段：**只返回同一对局、且存活的玩家**；
  同队全部返回，敌方**只在服务端判定为「已被发现」时**才返回（见 6.2），避免做成雷达。
  骑乘载具的玩家额外带 `vehicle` 字段。

> 后续参考 `RoomMarkerSyncPacket` / `RoomMarkerRuntimeService` 的做法：标记由服务端计算并下发，
> 客户端只负责画。本轮沿用了这个分层。

## 6. 本轮重点：边界 / 高亮 / 重生与队伍 / 计分板

参考文件（`D:\GTMchange\.ref\gdc-src\`）：

| 参考实现 | 借鉴点 |
|---|---|
| `RoomBoundaryRuleEvaluator` + `RoomBoundaryPolygonSupport` | 奇偶射线判定；**未配置边界视为「不算越界」**，不惩罚 |
| `RoomRuleRuntimeService.processBoundaryRule` | 5 tick 检查一次；每秒重发倒计时；到点才杀人；重生流程中免疫 |
| `RoomBoundaryPenaltyHudService` | 大字 title + 副标题倒计时，颜色随时间从蓝→绿→黄→橙→红 |
| `RoomNametagVisibilityService` | 用原版 scoreboard team 的 `HIDE_FOR_OTHER_TEAMS` 实现「敌人看不到我名牌」 |
| `RoomTeamPresentationSupport` | 队伍显示名与颜色集中解析，不散落在各处 |
| `RoomSpawnCandidateSelectionService` | **选离敌人最远的出生点**，8 格内视为危险；全危险时取最远者而非拒绝部署 |
| `RoomResultRuntimeService` | 结算时序：黑屏 → 视角转移 → 关闭倒计时 |
| `RoomKilliconService` | 计分板条目含队名、击杀/死亡/助攻，按分数排序 |

### 6.1 地图边界（第一优先）
`Engine.tick()` 每秒检查一次，任一条件不满足即算「脱离战区」：

```
维度不符 || 不在地图多边形内 || Y 不在 [minY,maxY] || (突破模式且不在当前阶段多边形内)
```

- 每秒发一次 title（不是每 tick），颜色按剩余秒数变化；同时写 actionbar。
- `member.outside` 累加秒数，达到 `outsideSeconds` 才阵亡；回到区内立即清零。
- 用 `member.warnedSecond` 记住上次播报的秒数，避免重复发包。
- 死亡流程、旁观、重生中不判越界（与参考的 `isInRespawnFlow` 免疫一致）。
- 客户端 HUD 显示 `⚠ 脱离战区 Ns 后阵亡`，≤3 秒变红。

### 6.2 附近玩家高亮（第二优先）
新增 `Tactics.java`（**不依赖任何 Minecraft 类型**，所以能离线跑测试）。

**关键限制**：原版 `Entity.setGlowingTag` 是**全局共享标志**，所有客户端看到同一个效果，
无法做到「A 看得见、B 看不见」。因此判定必须是对每个实体做全局决策：

- 队友：任一队友在 64 格内 → 发光（小队标识，可穿墙）。
- 敌人：任一敌人在 32 格内**且有视线**（`hasLineOfSight`）→ 发光（被点亮）。
- 只在状态变化时写标志，避免每 tick 一个 entity data 包。
- 两个开关进 GUI：`glowTeammates`、`glowEnemies`。

我在代码注释里写明了这个限制：真正「只对某玩家可见」的描边需要自绘 RenderType + 客户端 mod，
不是原版标志能做到的。

**名牌**：这部分反而能按观察者区分——用 scoreboard team 的
`setNameTagVisibility(HIDE_FOR_OTHER_TEAMS)`，敌人看不到、队友看得到。开关 `hideEnemyNametags`。

### 6.3 重生与队伍（第三优先）
- **队伍身份**（`Board.java`）：每张地图生成 `bfops_<map>_blue` / `_red` 两个 team，
  设颜色、`[蓝方] ` 前缀、友伤规则、名牌可见性。玩家列表和名牌自动带队伍颜色，
  **不需要客户端装 mod**。
- **安全出生点**（`Tactics.chooseSpawn`）：选中的出生点 16 格内有敌人 → 自动换成最远的安全点，
  并用 title 告知「出生点被敌人压制，已改到 X」。所有候选仍由服务端从白名单里取，
  客户端无法指定未授权的点。
- **重生**：`readyAt` 倒计时，`deploy()` 校验 `ticks >= readyAt`；死亡时清空背包、转旁观、
  显示倒计时 title 与 HUD 提示。
- **平衡**：加入时校验 `本方人数 <= 对方人数`，防止一边倒。

### 6.4 计分板与胜利条件（第四优先）
- **侧边栏**（`Board.sidebar`）：兵力 / 据点比 / 阶段或剩余时间 / 每个据点的归属与控制条 /
  前 6 名玩家 K-D-分。行文本预渲染后整体下发。
- **胜利判定**（`Rules.checkEnd`，已有离线测试）：征服按兵力清零或时间到比兵力；
  突破按阶段推完或时间到防守方胜。
- **结算**：`finish()` 给每人发大字 title（胜负）+ 副标题（个人数据）+ 聊天里的完整排行榜，
  然后恢复背包、清理队伍与载具。

### 6.5 一个必须说明的设计冲突
原版只有**一个** `DisplaySlot.SIDEBAR`，是**全服共享**的，不是每人一份。
所以两张地图同时开时，侧边栏会互相覆盖。我的处理是：`matches.size() > 1` 时**不写侧边栏**，
改由每人独立的 HUD 承担显示。单地图运行时侧边栏正常。

### 6.6 显示他人载具（低优先级）
`Engine.blip()` 读 `player.getVehicle()`，把实体类型 id 美化后作为 `vehicle` 字段下发；
小地图上骑乘者多一条短横线。只发显示名，不发实体 UUID 列表。


### 6.7 群组服兼容：TAB（Velocity）与 Xaero（新增）

目标运行环境不是单机：后端挂在 **Velocity 群组**后面，代理上跑 **TAB**，客户端装 **Xaero 小地图**。
这两者都与本模组抢同一块地方。

**冲突的本质（不是猜的，是查证的）**

| 冲突面 | 原因 | 后果 |
|---|---|---|
| 侧边栏 | 原版 `DisplaySlot.SIDEBAR` **全服只有一个槽**，TAB 和本模组都写它 | 每秒互抢，玩家看到闪烁或布局回弹 |
| 队伍 | 原版**一个玩家只能在一个 team** 里 | 本模组 `addPlayerToTeam` 会把玩家从 TAB 的 team 里踢出去，TAB 下次刷新又抢回来 |
| 小地图 | Xaero 默认画在右上角 | 两个小地图叠在同一处 |

**做法：观察所有权，然后让出**（`CompatPolicy.java`，纯决策核心，42 条离线断言）

- TAB 是 **Velocity 插件**，后端看不到任何痕迹，所以「查 mod 列表判断有没有 TAB」这条路根本不通。
  唯一可靠的办法是**看谁在真正占用这个面**：
  - `scoreboard.getDisplayObjective(SIDEBAR)` 显示的 objective 名字不是 `bfops` → 让出侧边栏；
  - 玩家已经在某个 team 里，且名字不以 `bfops_` 开头 → 停止接管队伍。
- **让出是粘性的**：一旦观察到被占用，整局都不再抢回。否则两边每秒互换，正是闪烁的来源。
- 三种模式，每项独立：`自动`（观察后让出）/ `总是`（强制接管）/ `从不`（完全不碰）。
- 让出后**信息不丢**：侧边栏内容改由每人独立的 HUD 显示（TAB 的侧边栏我们也读不到，所以必须自绘）。

**为什么不用「装了就禁用」**：`Compat.Settings` 里没有「检测 TAB 是否安装」这种字段，因为做不到。
只有一个例外是诚实的：`xaeroMinimap` 是**服主声明**（客户端 mod，服务端探不到），默认 `true`,
因为两个已知整合包都装了它；假设它存在只是少用一个角落，假设它不存在就会两个地图重叠。

**代码落点**

| 文件 | 职责 |
|---|---|
| `CompatPolicy.java` | 纯决策：`mayUseSidebar` / `mayUseTeams` / `mayUseMinimap` / `anchor`，**不引用任何 MC 类型**，可离线测 |
| `Compat.java` | 薄适配层：读 scoreboard 实际状态喂给 Policy，写 `world/battlefield_ops/compat.json` |
| `Board.java` | `apply()` 前先问 `useTeams`；`sidebar()` 前先问 `useSidebar`；`hideSidebar()`/`clearAll()` **只在槽是我们的**时候才清空 |
| `Engine.java` | 构造时 `Compat.load`；`state()` 下发 `hud`{sidebar,teams,minimap,xaero} 与 `compat` 报告；`start` 时 `reset()` 重新检测 |
| `Client.java` | 按 `hud` 决定是否画小地图、Xaero 在场就换到左上角；侧边栏被让出时 HUD 补一份比分榜 |
| `OpsScreen.java` | 新增「兼容」标签页：三项模式按钮 + 谁占用了哪个面的中文说明 + 已作出的让步列表 |

**一个容易被忽略的细节**：`hideSidebar()` 和 `clearAll()` 原本无条件
`setDisplayObjective(SIDEBAR, null)`。那会把 **TAB 的侧边栏一起清空** —— 正是这个类要避免的跨插件破坏。
现在改成先比对槽里是不是我们自己的 objective 再清。

### 6.8 全部计分板（战地式长按查看）

需求是「像战地那样按一下开全部计分板」。实现上最容易做错的一点是**做成 Screen**：

- `Screen` 会抢鼠标、吞掉点击、必须关掉菜单才能继续射击。
- 战地的计分板是**按住看着走**的浮层，不是菜单。所以这里用 `KeyMapping.isDown()`
  （按住判定，不是 `consumeClick()` 的锁存判定），在 `RenderGuiEvent.Post` 里画，
  且 `mc.screen != null` 时不画（玩家真开了菜单就让位）。

**键位**：`F6`。原方案用 `B` 是错的 —— 查 `options.txt` 发现 `B` 在目标整合包里被
Xaero「新建路径点」、fxntstorage 背包、vsie force assist 三方同时占用。
`F4`~`F10`、`F12` 全部空闲，最后选 `F4` 开编辑器、`F6` 看计分板。

**布局**（`Scoreboard.java` 纯逻辑，34 条离线断言）：

| 部分 | 内容 |
|---|---|
| 标题 | 地图名 + 双方兵力（征服）或 进攻方兵力 + 阶段（突破） |
| 左/右列 | 蓝 / 红各自按个人分排序；队头显示存活人数与队伍总分 |
| 每行 | `▶ 名字`（自己带箭头）+ 右对齐 `击杀/阵亡  得分` |
| 阵亡 | 保留在列表里但变暗 —— 看得见谁倒了本身就是信息 |
| 目标条 | 每个据点 id + 归属色 + 控制百分比 / 「争夺」；突破模式只显示当前阶段 |
| 溢出 | 显示「还有 N 人」而不是静默截断 |

**排序为什么要三级**：`分数 desc → 击杀 desc → 名字`。开局时大量玩家都是 0 分 0 杀，
只有两级的话同一份数据每次刷新顺序都可能不同，看起来像 bug 但数字全对。第三级名字
让顺序变成全序，刷新稳定。这条专门写了断言。

**为什么要截断而不是分页**：需要滚动的计分板在交火中读不了。超出的用「还有 N 人」告知。

### 6.9 载具 HUD（限定为敌友 + 能否部署）

需求明确只做两件事：**显示敌军/友军**、**显示能否部署**。血量百分比、低血警告、驾驶员名字
参考包都有，本轮**故意不做** —— 载具 HUD 报出玩家无法据以行动的信息只是挡准星的杂物。

**敌友判定**（`VehicleHud.java`，纯逻辑，45 条离线断言）

归属在**生成时**记下来（`Match.vehicleTeams`），不从"谁正坐在里面"反推：

- 空车仍属生成它的那一方。若按驾驶员判断，敌人下车瞬间这辆坦克就会变回"友军色"。
- 归属未知（`-1`）**不算友军** —— 否则一辆无主的敌方车会被画成安全色而被无视。
- 旁观者（team `-1`）看谁都不是友军：他没有阵营，按别人的阵营上色是撒谎。

**敌军载具一律显示**，这与敌方玩家不同（玩家要等"被发现"才下发）。理由：载具是又大又响、
本来就看得见的东西，从 HUD 上藏起来不是隐蔽而是不一致。所以这里**不需要视线判定**，
也就不会在不可信客户端上泄漏位置信息。

**能否部署**：`VehicleHud.availability` 返回**具体卡在哪一步**，不是一个光秃秃的布尔值：

```
本图未授权 → 非本方 → 分不足 → 冷却中 → 已达上限 → 可部署
```

顺序是有意的：信用点和冷却玩家自己能解决，本局上限解决不了，所以先报能解决的那个。
GUI 里显示为「载具状态：分不足 100/300」这种带数字的文案，绿色=能用，琥珀=会被拒并说明原因。

**小地图表现**：5×5 空心方块（友军青、敌军红），被占用时中心填实。
空心的方块不用图标资源就能读成"载具"而不是"玩家点"，与其余覆盖层一样保持零资源依赖。
空心/实心一眼区分「有车可搭」与「开走别管」，这是运输载具最关键的差别。

### 6.10 全套离线验证

六套测试，共 188 条断言，全部通过：

| 测试 | 断言数 | 覆盖 |
|---|---|---|
| `sim/RulesSim.java` | 24 | 征服扣票、僵持不掉票、争夺中推进、突破满阶段推进、阶段奖励、超时防守方胜、仅攻方扣票、中立化不计分 |
| `sim/MinimapSim.java` | 19 | 包围盒居中、边界顶点落在面板内、屏幕↔世界往返、X/Z 等比、空地图除零、配色 |
| `sim/TacticsSim.java` | 24 | 最远出生点选择、危险半径（含边界 off-by-one）、压制回退、安全选择不被覆盖、威胁排序、队伍人数、高亮距离/视线门槛、越界倒计时 |
| `sim/CompatSim.java` | 42 | 三种模式的侧边栏/队伍/小地图判定、**粘性不被空闲 tick 撤销**、`reset()` 恢复、锚点夹取、前缀误判 |
| `sim/ScoreboardSim.java` | 34 | 分队排序、三级 tiebreak 的全序性（输入顺序无关）、截断与溢出计数、退化 limit 不抛异常、阵亡变暗、自己标记、队伍总分与存活数 |
| `sim/VehicleHudSim.java` | 45 | 敌友判定（含未知归属不冒充友军、旁观者无友军）、摧毁不显示、可用性优先级链、每种状态都有文案、占位与可搭车 |

```
javac --release 21 -encoding UTF-8 -proc:none -cp <compile-classpath> -d build/sim \
      sim/{VehicleHudSim,ScoreboardSim,CompatSim,TacticsSim,MinimapSim,RulesSim}.java \
      src/main/java/cn/bfops/{VehicleHud,Scoreboard,CompatPolicy,Tactics,Minimap,Model,Rules}.java
java "-Dfile.encoding=UTF-8" -cp build/sim cn.bfops.VehicleHudSim

**已修的一个真 bug**：HUD 第 274 行原来硬编码「按 B 选择配装」，键位改成 F4 后就成了错的，
玩家自己改键后更是必错。现在读实时绑定 `OPEN.getTranslatedKeyMessage()`。
java "-Dfile.encoding=UTF-8" -cp build/sim cn.bfops.MinimapSim
java "-Dfile.encoding=UTF-8" -cp build/sim RulesSim
```

`Tactics.java` 特意不引用任何 Minecraft 类型（用自带的 `At` record 代替 `Vec3`），
这样出生点与高亮判定才能脱离服务端验证。这是本轮踩到的实际教训：
第一版直接用 `Vec3` 导致离线测试 `NoClassDefFoundError`。

**仍未验证（必须有真实环境）**：
- 小地图、部署页、侧边栏、title 的实际渲染效果没在游戏里看过，像素级排版需实机确认。
- scoreboard team / 发光标志 / title 包只经过编译期与 API 签名确认，没有实机发包验证。
- 出生点碰撞检查、载具生成、TaCZ 枪械构建同理。
- **TAB 的实际行为没验证过**：本机没有 Velocity + TAB 环境，"侧边栏被占用"这个信号是设计推演，
  不是实测。上线时第一步应该看 `logs/latest.log` 里有没有出现
  `[battlefield_ops] 兼容：侧边栏由其它插件占用`。如果出现了，说明让出逻辑生效；
  如果 TAB 占着侧边栏但日志没动静，说明它的 objective 名恰好也叫 `bfops`（几乎不可能）
  或者它走的是别的方式（例如 Velocity 端同步包），那就需要改成显式配置 `teams=NEVER`。
- **Xaero 与本模组没有代码级冲突**：查了两个包的实际日志与崩溃报告，
  Xaero 的报错只有贴图缺失和版本检查超时，与本模组无关。
  真正的冲突只是"两个小地图画在同一个角落"，纯显示层，靠让位与换角落解决。

**环境事实（查证过，不是假设）**：
- 实际运行实例 `D:\USTBL\.minecraft\versions\Mechanomania-航空学`，1.21.1 NeoForge，约 400 个 mod。
- TaCZ：`tacz-neoforge-1.21.1-1.1.8-hotfix-r1.jar`；卓越前线：0.8.9.2。
- Xaero：`xaerominimap 26.4.2` + `xaeroworldmap 1.44.2`。
- mods 目录里存在**两对重复的 Xaero jar**（26.1.0/1.41.0 与 26.4.2/1.44.2 并存）。
  查 `latest.log` 与 `debug.log` 后确认 **FML 按 modid 做了去重**，没有 "Duplicate mod" 报错，
  所以这不是故障，只是磁盘上多占了 3.5MB。清理与否不影响运行。
  （我一开始把它当成问题，是错的，这里记下来免得再判断错。）


# 战地风云 · Battlefield Ops — Forge 1.20.1

**Minecraft 1.20.1 ｜ Forge 47.4.16+ ｜ 版本 0.8.34-forge1201 ｜ MIT**

`mod id: battlefield_ops` ｜ `package: cn.bfops`

本分支是 1.21.1 NeoForge 版本的 **1.20.1 Forge 移植**。1.21.1 版本在 `main` 分支。

## 构建状态

`clean build` 通过（ForgeGradle 6.0.54 / Java 17 / Gradle 8.8），产物：

```
build/libs/battlefield_ops-0.8.34-forge1201.jar
```

- mixin refmap 已生成并打进包里；
- 依赖范围：forge `[47,)`、minecraft `[1.20.1,1.20.2)`。

## 构建

```bash
# 需要 JDK 17 与 Gradle 8.8（ForgeGradle 6 不支持 Gradle 9）
gradle build
```

## 1.20.1 适配要点

- 网络层：NeoForge payload → Forge `SimpleChannel`（保留消息方向、长度上限、房间受众、机器人过滤）；
- 事件：`@EventBusSubscriber` 的 **mod 总线**监听（按键、客户端 setup、reload）单独成类，Forge 1.20.1 不会跨总线；
- 绘制：`Tesselator.getBuilder()/end()`；没有 `blitSprite`，改为按 `.mcmeta` 九宫格绘制的 `Ui.sprite`；
- 字体：移除 1.20.2+ 才有的 `legacy_unicode` provider；
- TaCZ：`GunItemBuilder.build()` / `AttachmentItemBuilder.setAttachmentId` / `installAttachment(gun, attachment)` 等 1.20.1 短签名优先，失败再退回 1.21 长签名；
- 物品数据：`DataComponents` → NBT；
- 服务端目录：集成服务器房主视为管理员，单人不开作弊也能编辑地图与看到保存栏。

## 已知限制

- 尚未完成全流程实机测试；
- Sable 布娃娃尸体不可用（Sable 无 1.20.1 版本），死亡不生成物理尸体；
- 详细移植记录见 `PORTING.md`。

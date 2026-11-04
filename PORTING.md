# Battlefield Ops — 1.20.1 Forge 移植分支

**构建状态：通过。** `clean build` 成功，Forge 47.4.16 / Java 17 / Minecraft 1.20.1。

| 项目 | 值 |
| --- | --- |
| 产物 | `battlefield_ops-0.8.35-forge1201.jar` |
| 版本 | `0.8.35-forge1201` |
| SHA-256 | `bdbf01ded11fa0754f6f70c8845e0c6a5d858e4dd5bf0e311417a074d093b3dd` |
| mixin refmap | 已生成 |
| 依赖版本范围 | forge `[47,)`，minecraft `[1.20.1,1.20.2)` |

## 构建

```bash
# 需要 JDK 17 与 Gradle 8.8（ForgeGradle 6 不支持 Gradle 9）
gradle build
```

产物在 `build/libs/`。

## 1.20.1 与 1.21.1 的差异

见本文件下方的逐项说明与提交历史。分支按 1.20.1 的 API 单独维护：

- 网络层用 Forge `SimpleChannel`（无 NeoForge payload 机制）；
- 事件用 Forge 总线名字，`@EventBusSubscriber` 的 mod 总线监听单独成类；
- 绘制用 1.20.1 的 `Tesselator.getBuilder()/end()`，没有 `blitSprite`（改为九宫格 `Ui.sprite`）；
- 枪械/配件数据通过反射读取，先试 1.20.1 的短签名再退回 1.21 的长签名。

## 限制

- 尚未进游戏实测全部功能；分支按“编译通过 + 静态核对”发布；
- Sable 布娃娃尸体在 1.20.1 不可用（Sable 无 1.20.1 版本），死亡不生成物理尸体。

# Battlefield Ops — 1.20.1 Forge

这是 Battlefield Ops 的 1.20.1 Forge 移植版。

## 环境

- Minecraft 1.20.1
- Forge 47.4.16（依赖范围 `[47,)`）
- Java 17

## 安装

把 `battlefield_ops-0.8.35-forge1201.jar` 放进 `mods/`。

## 构建

```bash
gradle build     # 需要 Gradle 8.8 + JDK 17
```

## 说明

- 本分支只针对 1.20.1；1.21.1 NeoForge 版本在 `main` 分支。
- 移植细节与已知差异见 `PORTING.md`。

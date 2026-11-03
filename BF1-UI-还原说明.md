# Battlefield 1 UI 本地还原版

日期：2026-11-03。基于 Battlefield Ops 0.8.30 / Minecraft 1.21.1 / NeoForge 21.1.233。

## 本次实现

- 大厅：原版 Forest_Background 场景背景、横向导航、半透明房间列表与详情双栏、白色主要操作按钮；继续使用真实房间目录、搜索、加入与管理请求。
- 部署：保留实时俯视与地图部署目标。底部八个配装入口在宽屏排列为一行、窄屏排列为两行；小队区在左、可滚动部署选项在右、部署按钮在右下、退出在左下。缩小窗口时限制名单显示行数，避免覆盖配装；窄屏比分另起一行。
- 小队：左侧选择，右侧成员与状态，底部明确加入按钮；满员的小队可预览但不能加入。空间足够时展示四个角色位置，使用客户端已加载的真实玩家模型，未加载的玩家使用人员图标。
- 原版图标：兵种、小队长、友军/敌军标记、通用地面/航空载具、医疗/弹药补给标记已接入。171 个原版图集状态均作为可复用资源保留。
- 保留模组的四兵种规则：突击/支援/工程/侦察分别映射 BF1 assault/medic/support/scout 图标；支援兵使用医疗十字，工程兵使用支援弹药图标。

## 提取结果

`D:/GTMchange/bf1-ui/`：

- `png/`：2,250 张解码后的原版纹理 PNG。另有 2 个非二维纹理保留在 raw 中，未强行转换成普通 UI 图片。
- `icons/`：按原版 SharedIcons 元数据 UV 坐标裁切的 171 个图标状态。
- `frontend/`：Sparta 内置前端包的 489 个文件，包括图片、字体与 bundle.js。
- `layouts/`：111 份 UI 元数据文本。
- `texture-manifest.json`、`icon-manifest.json`、`frontend-manifest.json`：来源、尺寸、裁切区域或偏移、哈希。
- `icons-contact-sheet.png`、`backgrounds-contact-sheet.png`：实际提取素材的预览，不是游戏内 UI 截图。
- `before/`：本次修改前涉及的 Java 文件、兵种图标和资源检查脚本备份。

## 工具与来源

- Frosty Editor 1.0.6.3：[官方发布页](https://github.com/CadeEvs/FrostyToolsuite/releases/tag/v1.0.6.3)。程序位于 `D:/GTMchange/tools/FrostyEditor-1.0.6.3/FrostyEditor/FrostyEditor.exe`。
- 编辑器 ZIP SHA-256：`6ECA779AAC4A150F1C46934C0455522269A1820B6D3E0B8727C13A81CA9106F8`。
- [Frostbite-Scripts](https://github.com/NicknineTheEagle/Frostbite-Scripts)，提交 `a07ee6d38ecc62598dfc9fdf990be067d9f21929`，用于读取 TOC/SB/CAT/CAS。
- [FrostyToolsuite](https://github.com/CadeEvs/FrostyToolsuite)，提交 `e400dcb283ad743071263ea7b34ff86780ffe910`。纹理头依据 FrostySdk/Resources/Texture.cs，RenderFormat 数值由下载编辑器自带 BF1SDK.dll 验证。
- 游戏来源：本机 `D:/steam1/steamapps/common/Battlefield 1`，优先提取 Patch，再补充 Data。没有修改游戏安装文件。

## 验证与范围

Java 21 编译、77 组部署窗口尺寸共 5,159 条几何检查、原有布局 881 条、小队部署 295 条、大厅 291 条、部署权限 38 条、配装布局 19 条、静态 PNG/语言资源 33 条检查通过。原资源检查脚本固定检查 0.8.10 老包，已改为默认检查当前 0.8.30 包并支持传入路径。

已验证提取 PNG 可读取、动态引用图标存在且被打入 JAR。尚未进行 Minecraft 实际启动、不同 GUI 缩放下的截图验收和多客户端联机测试。

这是视觉还原测试版。BF1 前端脚本保存在参考目录，没有直接运行在 Minecraft 内；大厅/部署/小队交互使用模组自身实现。小队人物展示使用 Minecraft 玩家模型，不包含 BF1 士兵模型、骨骼动画或完整前端动效。原有俯视相机保留，未新增跟随队友的自由观战模式。

测试包与原模组使用相同 mod id，替换原包使用，不能同时放入两个 battlefield_ops 包。此次未更改实际游戏实例的 mods 目录。

## 资源归属

从 BF1 安装文件提取的图片、图标、字体和脚本为 EA/DICE 原作资源；模组代码的 MIT 许可不覆盖这些原作素材。来源详见包内 BF1-RESOURCE-NOTICE.txt 和 assets/battlefield_ops/bf1_asset_manifest.json。

# BF1 点位、小地图、阵营与音频资源包

## 安装

1. 用本目录的 battlefield-ops-0.8.30-bf1-markers-neoforge-1.21.1.jar 替换旧模组。
2. 两个 ZIP 原样放到所用 Minecraft 实例的 resourcepacks 文件夹，在「选项 → 资源包」启用。适用 Minecraft 1.21.1，pack_format=34。
3. 图标包和音频包可以分别开关。它们需要 Battlefield Ops 提供界面和声音事件；仅安装资源包不会产生部署界面或自动播报。

## 本轮改动

- 据点 A–H 使用 BF1 原版字母素材，保留现有占领进度、友敌关系和选中反馈。自定义点位 ID 继续使用文本。
- 大厅/部署阵营区与出生基地支持原版国家徽章。主模组默认中性图标；可选图标包使用「队伍 0 德意志、队伍 1 英国」外观预设。
- 六种徽章均已包含：德意志、奥匈、意大利、奥斯曼、英国、美国。徽章是 UI 贴图，不是三维飘动旗帜。预设不修改机械轴心/格雷同盟名称、队伍规则或语音国籍。
- 小地图队员兵种、救援、医疗/弹药补给、载具、已标记敌情接入 BF1 图集。支援兵=medic，工程兵=support。
- 修复兵种图片按错误尺寸采样的问题，改成按实际图片尺寸等比绘制。
- 敌情残影改为记录世界坐标，随着小地图平移、缩放和旋转重新投影，保持原有 2.5 秒衰减。
- 小地图图标继续保持竖直，角色与地形旋转逻辑保留；标记来源仍由服务端的房间、阵营、情报和有效期控制。

## 调整阵营徽章

编辑图标 ZIP 内的 assets/battlefield_ops/bf1_factions.json：

{"0":"bases_germanempire_deploy_default","1":"bases_unitedkingdom_deploy_default"}

可将国家部分换为 austrohungarianempire、kingdomofitaly、ottomanempire、unitedstatesofamerica；也可以使用 base_map_default 中性图标。重新加载资源时配置与图片缓存会刷新。

## 音频包内容与后续替换方法

音频 ZIP 是模组已有音频库的独立导出：198 个声音事件、1,433 个 OGG 文件，包括 music、loop、voice、fp、tp、downed。这些既有文件来自工程现有参考音频库，不将其统一声称为本轮从 BF1 新提取的原版音轨。

用 sounds.json 中相同事件 ID 替换 sounds 数组即可替换播报；replace=true 防止与下层包的声音列表混合。长音乐条目保留 stream=true。占点、失点、阶段转换、部署和胜负的触发由模组现有 SoundBank/MusicDirector/语音子系统负责，资源包只负责声音素材。改变触发规则或按地图选择语种需要继续修改模组逻辑。

已从本机 BF1 另外提取 11 条前端音乐的 SPS 原始流，位于 D:/GTMchange/bf1-ui/audio-raw，元数据与时长位于 audio-metadata。SPS 尚未解码为 Minecraft 可用的 OGG，所以没有放入可播放资源包。新原版语音仍需按国籍、语言、事件类型继续定位本地化音频包，再提取、解码和匹配事件，不能将界面包中的提示音当成战场语音。

## 验证范围

Java 21 编译通过；现有布局、小队、大厅、部署权限、配装与静态资源检查通过。两个 ZIP 结构、音频引用与 OGG 文件头已校验。未进行游戏内画面、声音试听与多客户端联机验收。通用载具图标目前使用坦克剪影，尚未按每个载具车型细分；三维旗帜与国家语音自动切换尚未实现。

资源归属沿用上轮 BF1-RESOURCE-NOTICE.txt；代码的 MIT 许可不覆盖 EA/DICE 的原作素材。

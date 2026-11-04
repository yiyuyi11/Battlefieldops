# BattlefieldOps NeoForge 1.21.1 群组服房间

每个战地子服独立运行自己的房间、地图、存档和战绩。大厅列出配置的子服当前已创建的房间，并显示人数、状态和阵营。玩家选择远程房间后，Velocity 负责切服，目标服在玩家登录后验证并加入该房间。

1. 在参与群组的 NeoForge 1.21.1 后端及客户端安装 BattlefieldOps 0.8.34。专门作为入口的 NeoForge 大厅后端也可参加，允许它没有本地地图。
2. 确认玩家已能通过代理登录和切换这些 NeoForge 1.21.1 后端。代理与后端应使用相互兼容的版本。玩家信息转发也需匹配后端配置，并确保同一个玩家在所有后端的 UUID 一致。参见 [Velocity 服务端兼容文档](https://docs.papermc.io/velocity/server-compatibility/) 和 [玩家信息转发](https://docs.papermc.io/velocity/player-information-forwarding/)。
3. 在 Velocity 的 `velocity.toml` 中启用 `[advanced]` 下的 `bungee-plugin-message-channel = true`。本模组使用 Velocity 内置的 BungeeCord `Connect` 消息切服。参见 [Velocity 插件消息文档](https://docs.papermc.io/velocity/dev/plugin-messaging/)。
4. 在每个后端的 `config/battlefield_ops-server.toml` 配置群组。下面以 Velocity 中名为 `hub` 的后端为例。

```toml
battlefieldMode = true

[groupRooms]
backend = "hub"
directory = "/srv/minecraft/battlefield-directory"
peers = "front-a,front-b"
```

在 `front-a` 上将 `backend` 改成 `front-a`，将 `peers` 改为 `hub,front-b`；`front-b` 同理。每个名称须与 Velocity 注册的服务器名一致，使用 1–48 个英文字母、数字、下划线或连字符。每个后端最多配置 16 个对等子服。修改配置后重启各后端。

目录应指向同一个共享位置。同机可使用同一个绝对路径，例如 `D:/Minecraft/battlefield-directory`；分机可以将同一个共享文件系统挂载到各后端。要求文件系统支持同目录原子重命名。只让受信的后端进程访问此目录，因为这里存放房间目录和跨服加入凭证。不要指向任何子服的地图或玩家存档目录。

后端每约三秒发布一次状态，远程状态超过十五秒会从列表移除。加入凭证绑定目标服和玩家 UUID，三十秒过期。目标服用原有加入规则复核房间是否仍开放、有空位及玩家状态，校验失败时打开目标服大厅并显示原因。玩家须先离开当前房间、载具和编辑模式，才能跨服加入。

同一地图 ID 可以在不同子服使用。远程房间只供浏览和加入，创建、开局、关闭和地图管理在目标后端操作。单机或未填写群组配置时使用本服大厅。

当前构建已核对房间状态与一次性凭证的读写，以及切服消息编码。真实代理双后端联调需要使用实际部署的 Velocity、NeoForge 和转发配置。

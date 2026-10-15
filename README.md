# BedCast

BedCast 是面向 Paper 1.8.8 起床战争赛事服的实时数据采集插件。它适配 HMO-Bedwars，为每个竞技场提供比赛、选手、队伍和事件数据，并通过 HTTP REST 和 WebSocket 向直播网页、OBS Browser Source 或导播工具发布。

BedCast 提供数据服务，不包含游戏大厅、BedWars 玩法或直播叠加网页。

## 功能

- 多竞技场隔离；每次新局使用新的 `gameId`。
- 主线程采集 Bukkit/HMO 状态，按 2–5 Tick 发布不可变快照。默认 5 Tick。
- REST 查询竞技场、整场快照、队伍、选手和有界比赛事件历史。
- WebSocket 连接后先发快照，再推送状态变化与比赛事件。
- HMO 事件适配覆盖开局、加入/离开、断线/重连、伤害、死亡/击杀、重生、破床、淘汰、购买、队伍升级和生成器升级。
- 可选 Token 与精确 Origin 白名单；默认仅监听本机地址。

## 兼容与依赖

| 组件 | 要求 |
| --- | --- |
| Minecraft 服务端 | Paper 1.8.8 |
| BedWars 核心 | HMO-Bedwars，以及对应的 `bedwars-api:0.2.4-beta` |
| Java | 构建使用 JDK 17，产物面向 Java 11；服务端使用 Java 11 或更高版本 |
| 构建 | Maven |

当前 HMO 适配器依赖 HMO 公共 API。服务器未安装兼容的 HMO-Bedwars 时，BedCast 不会启用为空数据的服务。

## 构建

在项目根目录运行：

```sh
java -version
mvn -version
mvn verify
```

如果本地 Maven 仓库尚无 `com.andrei1058.bedwars:bedwars-api:0.2.4-beta`，并且 HMO-Bedwars 源码检出在 BedCast 的相邻目录 `../HMO-Bedwars`，先安装匹配版本的 API：

```sh
env -u BED_WARS_SERVER_DIR mvn -f ../HMO-Bedwars/pom.xml -pl bedwars-api -am install -DskipTests
mvn verify
```

构建产物：

```text
bedcast-plugin/target/BedCast-1.0.0-SNAPSHOT.jar
```

常用验证命令：

```sh
mvn -pl bedcast-core test
mvn -pl bedcast-plugin -am test -Dtest=WebSocketNetworkTest -Dsurefire.failIfNoSpecifiedTests=false
```

## 安装

1. 确认 Paper 1.8.8 和兼容的 HMO-Bedwars 已安装，并且 Java 版本满足上表要求。
2. 将 `BedCast-1.0.0-SNAPSHOT.jar` 放入服务器的 `plugins/` 目录。
3. 启动服务器。BedCast 首次启动会生成 `plugins/BedCast/config.yml`。
4. 按需编辑配置，然后重启服务器使配置生效。
5. 请求 `/api/v1/arenas` 获取实际竞技场 ID，再用该 ID 请求单场状态或订阅 WebSocket。

BedCast 没有独立命令行入口；应通过 Paper 启动服务器。

## 配置

默认配置：

```yaml
api:
  enabled: true
  host: 127.0.0.1
  port: 8080
  token: ""
  allowed-origins: []
websocket:
  enabled: true
  host: 127.0.0.1
  port: 8081
snapshot:
  interval-ticks: 5
events:
  capacity-per-arena: 2048
provider:
  type: hmo
matches: {}
debug: false
```

- `api.host`、`websocket.host` 默认绑定 `127.0.0.1`。这适合服务器本机上的直播网页或工具。
- 本机调试时可以让 `token` 保持空字符串，此时服务不要求鉴权；不要把未鉴权服务暴露到其他主机。
- 局域网访问时，显式设置监听地址（例如 `0.0.0.0`）、配置强 Token，并按网络需要开放防火墙端口。插件会在非回环监听且没有 Token 时记录警告。
- 浏览器跨域请求需要在 `api.allowed-origins` 中列出网页的完整 Origin，例如 `http://127.0.0.1:3000`。协议、主机和端口须与浏览器页面来源完全一致。命令行 `curl` 不需要设置 Origin。
- `snapshot.interval-ticks` 必须在 `2` 到 `5` 之间；`events.capacity-per-arena` 必须大于 `0`。
- 可按竞技场 ID 配置比赛系列信息：

```yaml
matches:
  finals_map:
    match-id: finals-bo5
    current-round: 2
    total-rounds: 5
```

省略该配置时，`matchId` 默认使用当前 `gameId`，轮次默认为 `1/1`。竞技场 ID 须与 `/api/v1/arenas` 返回值一致。

## 本机快速调试

保持默认回环地址，并让 Token 为空；重启服务器后，在运行服务器的本机执行：

```sh
curl http://127.0.0.1:8080/api/v1/arenas
```

从返回的竞技场列表中复制 `arenaId`，查询完整快照：

```sh
curl 'http://127.0.0.1:8080/api/v1/snapshot?arena=finals_map'
```

替换示例中的 `finals_map`。无 Token 时不要发送 `Authorization` 头。

## REST API

成功响应包含 `success`、`timestamp` 和 `data`。错误响应还包含稳定错误码与说明。除了竞技场列表，所有路由都必须通过 `arena` query 参数指定竞技场。

| 请求 | 说明 |
| --- | --- |
| `GET /api/v1/arenas` | 列出竞技场 ID、局 ID、地图和比赛状态 |
| `GET /api/v1/snapshot?arena=<id>` | 获取单场完整快照 |
| `GET /api/v1/game?arena=<id>` | 获取比赛状态 |
| `GET /api/v1/teams?arena=<id>` | 获取该局所有队伍 |
| `GET /api/v1/teams/<teamId>?arena=<id>` | 按队伍 ID 获取队伍状态 |
| `GET /api/v1/players?arena=<id>` | 获取该局所有选手 |
| `GET /api/v1/players/<uuid>?arena=<id>` | 按 UUID 获取选手状态 |
| `GET /api/v1/events?arena=<id>&after=0&limit=100` | 从事件游标之后分页读取事件 |
| `GET /api/v1/events/recent?arena=<id>&limit=20` | 读取最近事件 |

若配置了 Token，REST 请求使用：

```http
Authorization: Bearer <token>
```

事件游标按单局递增。响应中的 `nextCursor` 可用于下一次 `/events` 请求；查询已结束的局或已从有界事件窗口淘汰的游标时会返回 `409`，此时重新获取快照并从新游标继续。

## WebSocket

单条连接订阅一场竞技场：

```text
ws://127.0.0.1:8081/api/v1/ws?arena=<arenaId>
```

服务先发 `SNAPSHOT`，随后按序发送 `GAME_STATE_UPDATE`、`PLAYER_STATE_UPDATE`、`TEAM_STATE_UPDATE` 和赛事事件。消息包含 `type`、`arenaId`、`gameId`、`sequence`、`timestamp`、`gameTime` 和 `data`。WebSocket 消息 `sequence` 是连接内消息序号；事件数据中的 `eventSequence` 与 REST 事件游标对应。

Token 为空时，WebSocket 只需协商 `bedcast.v1` 子协议：

```js
const ws = new WebSocket(
  "ws://127.0.0.1:8081/api/v1/ws?arena=finals_map",
  "bedcast.v1"
);
ws.onmessage = event => {
  const message = JSON.parse(event.data);
  console.log(message.type, message.data);
};
```

配置了 Token 时，WebSocket 可以使用 Bearer 请求头；浏览器不支持为 WebSocket 设置任意请求头时，通过第二个子协议传递 Base64URL 编码的 UTF-8 Token：

```js
const token = "替换成配置中的token";
const bytes = new TextEncoder().encode(token);
const encoded = btoa(String.fromCharCode(...bytes))
  .replace(/\+/g, "-")
  .replace(/\//g, "_")
  .replace(/=+$/, "");

const ws = new WebSocket(
  "ws://127.0.0.1:8081/api/v1/ws?arena=finals_map",
  ["bedcast.v1", `bedcast.token.${encoded}`]
);
```

连接只订阅一个竞技场。网络拥塞或客户端读取过慢时，服务会关闭该连接；客户端应重新连接并以新快照同步状态。HTTP 和 WebSocket 都是只读接口。

## 数据范围

- Bukkit 和 HMO 对象只在主线程访问。网络线程读取已发布的不可变 DTO。
- 选手和队伍状态会保留比赛中离线与结算数据。实时事件按竞技场保存在有界内存窗口中；插件不提供数据库持久化或历史比赛查询。
- 位置区域首版只报告可判定的 `BASE` 或 `UNKNOWN`；团队 `activeTrap` 表示待触发陷阱队列的队首。
- 适配器只根据 HMO 公共 API 和可观测事件发布数据；无法确认的历史信息不会被当作实时统计补造。

## 许可证

本项目采用 Apache License 2.0，详见 [LICENSE](LICENSE)。

## 项目模块

- `bedcast-core`：纯 Java 模型、Provider 契约、比赛会话、快照和事件。
- `bedcast-provider-hmo`：HMO-Bedwars 公共 API 适配和 1.8.8 Bukkit 状态采集。
- `bedcast-plugin`：Paper 插件装配、HTTP/REST、WebSocket、配置和鉴权。

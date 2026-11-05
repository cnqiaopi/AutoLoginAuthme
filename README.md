# AutoLoginAuthme 3.0.0

由原插件业务代码反编译、审查并重新整理的源码工程。插件名为 `AutoLoginAuthme`，根包为 `Mistral`。外置依赖方案参考 MistralLobby，使用固定库清单、SHA-256 校验、自动下载和离线部署；为保留 1.8.x 兼容性，加载器采用 Java 8 可用的独立 URLClassLoader。

GitHub 仓库的 Releases 提供主插件 JAR 和包含完整 libs 的离线部署 ZIP。源码通过下方构建命令生成相同结构的产物。

## 文件位置

| 路径 | 内容 |
| --- | --- |
| `src/main/java/Mistral/` | 整理、修复后的正式源码 |
| `src/main/resources/` | 插件描述与默认配置 |
| `src/test/java/Mistral/` | 登录流程、缓存、数据库、依赖下载与成品回归测试 |
| `libs/` | 构建生成的 5 个运行库，部署到插件数据目录的 libs |
| `build-tools/JdbcIsolationProbe.java` | 可选的驱动版本隔离对照工具源码；所需服务端缓存由本地提供 |
| `build.ps1` | Windows 一键验证、打包并复制成品 |
| `docs/BUGFIXES.md` | 发现的问题、修复方法与行为变化 |
| `docs/VALIDATION.md` | 已执行检查及兼容性验证边界 |
| `docs/INSTALL.txt` | 部署 ZIP 内的安装说明 |
| `AutoLoginAuthme-3.0.0.jar` | 主插件，仅包含插件代码与配置资源 |
| `AutoLoginAuthme-3.0.0-distribution.zip` | 主插件 + 完整 libs 的离线部署包 |
| `target/distribution/` | Maven 生成的部署目录 |

## 外置 libs

服务端安装结构：

```text
plugins/
├─ AutoLoginAuthme-3.0.0.jar
└─ AutoLoginAuthme/
   ├─ config.yml
   └─ libs/
      ├─ HikariCP-4.0.3.jar
      ├─ slf4j-api-1.7.36.jar
      ├─ slf4j-jdk14-1.7.36.jar
      ├─ mysql-connector-j-8.4.0.jar
      └─ protobuf-java-3.25.1.jar
```

主 JAR 不合并运行库。启动时逐个校验上述文件的 SHA-256；缺失或损坏时从固定的 Maven Central 路径下载，下载失败则停止启用并输出手动放置路径。已校验的本地文件会直接复用，完整部署 ZIP 可在断网环境使用。程序仅加载清单中的固定文件，不会自动加载 libs 中其他 JAR。

运行库版本、下载路径和哈希集中在 `Mistral.loader.ExternalLibraries`。加载器隔离本插件的 HikariCP、MySQL、SLF4J 和 protobuf，Bukkit/AuthMe 类型由服务端提供。构建依赖由 Maven 管理；本地 CFR、API 参考与服务端缓存放在 `build-tools/`，该目录仅将诊断工具的 Java 源码纳入 Git。这些开发文件无需复制到服务端。

| 运行库 | 作用 |
| --- | --- |
| HikariCP 4.0.3 | 数据库连接池 |
| SLF4J API / JDK14 1.7.36 | 匹配版本的日志接口和 JUL 绑定 |
| MySQL Connector/J 8.4.0 | MySQL JDBC 驱动 |
| protobuf-java 3.25.1 | MySQL 驱动依赖 |

## 版本适配

目标为 Bukkit/Spigot/Paper **1.8.x（包含 1.8.9 客户端常用的 1.8.8 服务端）至 26.3**。最低编译 API 为 Spigot 1.8.8，全部成品类使用 Java 8 字节码；运行 Java 版本仍须满足实际服务端和 AuthMe 的要求。

兼容实现包括：

- 不引用 NMS 或 CraftBukkit 内部类。
- 1.8–1.12 使用 `MC|Brand`，1.13 及以后使用 `minecraft:brand`。
- 品牌按 VarInt 长度和 UTF-8 解码；Paper 有公开品牌 API 时优先读取，适应品牌包早于加入事件的情况。
- 标题消息运行时选择双参数/五参数 API，不支持时回退聊天消息。
- 不声明 `api-version`，保留旧 Bukkit 插件加载方式；新 Paper 可能输出 legacy 插件提示。

本项目没有声明 Folia 支持。AuthMe 必须安装适合具体服务端版本的版本，提供 `fr.xephi.authme.api.v3.AuthMeApi` 和本项目使用的 AuthMe 5.6 事件 API。编译与接口检查不能替代每一种服务端分支的实际玩家测试。

## 安装和升级

1. 停服并备份原插件、`plugins/AutoLoginAuthme/` 和 MySQL 的 `login_sessions` 表。
2. 解压 `AutoLoginAuthme-3.0.0-distribution.zip`，将其中的主 JAR 放入服务端 `plugins/`，将其中的 `AutoLoginAuthme/` 文件夹合并到 `plugins/AutoLoginAuthme/`，保留已有配置。
3. 移除旧的自动登录插件 JAR，保留兼容服务端的 AuthMe。若只复制新主 JAR，首次启用需联网下载运行库；也可手动将工程 `libs/` 复制到 `plugins/AutoLoginAuthme/libs/`。
4. 编辑 `plugins/AutoLoginAuthme/config.yml` 的 MySQL 连接参数。此插件不自动创建 MySQL 数据库，连接后会创建 `login_sessions` 表。
5. 启动服务端，用 `/autologin status` 查看状态；使用测试账号手动登录、退出连接并重新加入，验证自动登录。

主要旧配置键和数据库表结构继续使用。已有配置不会被覆盖，新键缺失时使用代码默认值。旧记录品牌为 `unknown`、IP 为空、时间无效时要求重新手动认证。数据库连接/建表失败时停止启用；运行时数据库操作失败时暂停本实例自动登录，修复连接后使用 `/autologin reload`。

`database.jdbc-url` 可覆盖主机、端口等生成的 URL，必须以 `jdbc:mysql://` 开头。原版错误的 `characterEncoding=utf8mb4` 已改为 Java 支持的 `UTF-8`，表字符集仍为 `utf8mb4`。池、驱动和日志依赖由本插件的外置 libs 独立提供。

如果出现 `Access denied for user 'root'@'localhost' (using password: NO)`，说明 MySQL 拒绝了本次无密码认证。编辑**服务端**的 `plugins/AutoLoginAuthme/config.yml`，将 `database.host`、`database.port`、`database.database`、`database.username`、`database.password` 填为实际可用的 MySQL 连接参数。修改源码模板不会覆盖服务端已有配置；此时插件已停用，修正后重启服务端。插件不会自动读取 AuthMe 的数据库密码。

连接池直接接收本插件 libs 中创建的 `MysqlDataSource` 实例，避免线程上下文或全局 JDBC 注册表中其他版本的同名驱动参与选择。

## 会话规则

- 手动认证成功、IP 有效且收到有效客户端品牌后才保存凭证。
- 自动登录要求账号、IP、品牌都匹配；空 IP、未知/损坏品牌、回环地址不参与自动登录。
- 自动登录不会刷新凭证认证时间。`max_time: 0` 仅关闭时长限制；自然日变化仍使凭证失效。
- 默认不允许 OP 自动登录。自然日默认使用服务端时区，`timezone: Asia/Shanghai` 可固定时区。
- 主动 `/logout`、注销账号、管理员删除凭证会撤销会话。改密命令在执行前撤销旧凭证，即使该次改密后来失败，也需重新手动认证。
- AuthMe 内置 session restore、其他插件的强制登录可能绕过本插件的尝试流程；本插件不会把未知来源的已登录状态自动当成新的凭证。

品牌是客户端自行声明的标识，并非设备密钥。IP 与品牌相同的客户端不能靠这两个字段可靠地区分。本插件保留原方案的便利性，无法承诺防止同 IP、同品牌的账号冒用。使用代理时，后端必须正确接收并可信验证真实玩家地址；多节点共享同一表时，各实例缓存不提供跨节点即时撤销，需设置 `performance.enable_cache: false` 并通过各节点删除/下线流程同步处理。

AuthMe 5.6 没有通用改密成功事件，本插件覆盖 AuthMe 的 `changepassword/changepass/cp`、`email setpassword` 与管理员 `authme password` 常用命令。其他插件直接调用 AuthMe 改密 API、直接改数据库、自定义别名或账户管理接口，需要在其流程中同步撤销本插件的凭证。

## 管理命令

权限均为 `autologin.admin`，默认 OP；别名 `/al`。

| 命令 | 用途 |
| --- | --- |
| `/autologin reload` | 校验配置、异步检查新连接池，切换监听、缓存和监控 |
| `/autologin status` | 查看存储健康、缓存数量和调试设置 |
| `/autologin lookup <玩家>` | 异步查询凭证，IP 脱敏显示 |
| `/autologin remove <玩家>` | 取消待执行的自动登录并删除凭证 |
| `/autologin cleanup` | 清空内存缓存，保留持久化记录 |
| `/autologin debug` | 切换并保存调试配置，再重载 |

## 源码分类

| 包 | 职责 |
| --- | --- |
| `Mistral` | 仅依赖 Java/Bukkit 的最小 JavaPlugin 入口 |
| `Mistral.loader` | 固定依赖清单、校验下载、隔离类加载与共享生命周期接口 |
| `Mistral.bootstrap` | PluginApplication 业务入口、运行组件装配、注册及释放 |
| `Mistral.auth` | AuthMe API 桥接与连接会话状态机 |
| `Mistral.listener` | 玩家、品牌、AuthMe、改密命令监听 |
| `Mistral.command` | 管理命令、权限及补全 |
| `Mistral.config` | 不可变配置、边界检查 |
| `Mistral.security` | 过期/IP/品牌判断、改密目标提取 |
| `Mistral.storage` | JDBC、连接池、有界串行异步任务 |
| `Mistral.cache` | 有过期时间的容量受限 LRU |
| `Mistral.compat` | 任务调度、品牌协议与跨版本 API 适配 |
| `Mistral.message` | 玩家消息、标题兼容 |
| `Mistral.monitor` | 周期缓存维护 |
| `Mistral.model` | 不可变持久化数据 |
| `Mistral.util` | 玩家名与 IP 字面值工具 |

## 构建

默认构建建议 JDK 17/21/25 和 Maven 3.9；编译目标固定为 Java 8。Windows 可直接使用附带的 Maven Wrapper：

```powershell
.\build.ps1

# 依赖已缓存时离线构建
.\build.ps1 -Offline

# 也可直接使用 Maven，产物位于 target/
.\mvnw.cmd -B -ntp clean verify
```

验证 Paper 26.3 API 时使用 JDK 25，并在 PowerShell 中为 profile 参数加引号：

```powershell
.\mvnw.cmd -B -ntp '-Ppaper-26.3' clean verify
```

也可执行 `.\build.ps1 -Paper26` 做 Paper API 验证；发布通用包时再执行默认 `.\build.ps1`。脚本会将主 JAR 和部署 ZIP 复制到工程根目录，同时生成 `libs/`。首次构建需联网获取 Maven 与依赖。

正式通用包以默认最低 API 构建。`verify` 包含 66 项单元测试与 6 项最终成品检查，覆盖类名、Java 8 字节码、外置库哈希与隔离加载、其他线程上下文/驱动已存在时的真实连接池数据源选择、Bukkit/AuthMe 方法描述符。两端 API 的验证记录与实机验收边界见 `docs/VALIDATION.md`。

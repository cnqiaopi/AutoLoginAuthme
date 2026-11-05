# 验证记录

验证日期：2026-11-05。插件版本：3.0.0；主类：`Mistral.AutoLoginAuthme`。

## 已执行验证

在本机 JDK 25 下，分别使用真实的两端 API 完成 `clean verify`：

| 构建配置 | 服务端 API | 单元测试 | 成品集成检查 |
| --- | --- | --- | --- |
| 默认 | Spigot `1.8.8-R0.1-SNAPSHOT` | 66 项通过 | 6 项通过 |
| `paper-26.3` | Paper `26.3.build.242-stable` | 66 项通过 | 6 项通过 |

每组共 72 项，失败、错误、跳过均为 0。最终交付通用 JAR 以默认最低 API 构建。详细结果摘要保存在 `docs/verification/`。

单元测试覆盖品牌协议和未知品牌拒绝、IP 字面值、跨日与超时策略、重连连接身份、手动与自动认证区分、注销/改密/管理员撤销、保存失败与缓存处理、数据库串行队列、配置边界，以及外置库下载缓存、校验失败、重试、重定向和临时文件清理。

成品检查直接读取最终 JAR，验证：

- 插件名称、主类、`Mistral` 根包及 Java 8 字节码。
- 主 JAR 不含第三方依赖类；工程和部署目录的 5 个库均匹配固定 SHA-256。
- 在独立类加载器中实际加载 MySQL 驱动、HikariCP、SLF4J binding、protobuf 及 MySQL 资源包。
- 业务类由独立加载器加载，Bukkit 类型与生命周期接口保持父加载器类型身份，关闭生命周期可完成。
- 父环境已注册同名 JDBC 驱动、线程上下文也指向父环境时，实际 Hikari 连接池仍使用本插件的 MysqlDataSource 和驱动，并正确传递账号、密码。
- 对最终 JAR 的每个 Bukkit/AuthMe 方法调用描述符逐项核对当前构建所选的真实 API。

## 兼容范围与验证边界

目标范围是 Bukkit/Spigot/Paper 1.8.x 至 26.3，包含常见的 1.8.9 客户端搭配 1.8.8 服务端场景。插件代码编译为 Java 8，实际启动 Java 版本由具体服务端、AuthMe 和运行库要求共同决定。AuthMe 编译接口为 5.6.0；服主需要安装适合自己服务端且提供所需 API 的 AuthMe 版本。

数据库回归测试使用 H2 的 MySQL 兼容模式，尚未在真实 MySQL + AuthMe 服务端完成玩家手动登录、重连自动登录、改密、停服等整套实机验收。两端 API 检查也不代表逐个验证了所有中间版本和第三方分支。没有声明 Folia 支持。

Paper 26.3 服务端启动器只执行过 `--help`，用于确认下载产物及启动器可运行；这不计为完整开服或玩家登录测试。

针对用户提供的 Leaf 1.21.11 日志，直接报错为 MySQL `root` 无密码连接被拒绝，需要填写实际服务端配置。本地另用服务端缓存中的 MySQL 9.2.0 做独立进程对照：旧驱动类名查找路径可选中 9.2；显式数据源方式保持 libs 中的 8.4，Hikari 实际使用该数据源。工具源码为 `build-tools/JdbcIsolationProbe.java`，此检查不连接数据库，也不计入上述 72 项。

## 复现

```powershell
# 两端 API 检查；PowerShell 中 profile 参数加引号
.\mvnw.cmd -B -ntp '-Ppaper-26.3' clean verify
.\mvnw.cmd -B -ntp clean verify

# 一键构建并复制 JAR、离线部署 ZIP 到项目根目录
.\build.ps1
```

Maven 和依赖均已缓存时可加 `-o`，或执行 `./build.ps1 -Offline`。使用 Paper 26.3 API 检查时需要 JDK 25。构建输出中的三方依赖警告不会改变插件的 Java 8 编译目标。

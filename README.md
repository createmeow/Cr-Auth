# crskin-all - Minecraft 多认证源一体化解决方案

crskin-all 是一个基于 [authlib-injector](https://github.com/yushijinhun/authlib-injector) 的一体化 Minecraft 认证解决方案，内置 HTTP 认证服务器，支持正版与第三方认证源（如 LittleSkin）共存。

## 功能特性

- ✅ **一体化 JAR**：内置 authlib-injector，无需额外下载
- ✅ **多认证源支持**：同时支持 Mojang 正版和 LittleSkin 等第三方认证
- ✅ **统一玩家身份**：同一玩家可通过不同认证源登录，使用相同的 UUID 和玩家名
- ✅ **自动玩家名修正**：自动添加认证源前缀（如 `Mo-createmeow`、`Li-createmeow`）
- ✅ **Bukkit/Spigot/Paper 插件自动部署**：自动检测并部署显示名修正插件
- ✅ **配置文件支持**：使用 `crauth-config.json` 配置，避免被服务端覆盖
- ✅ **支持 authlib-injector 参数**：完全兼容 authlib-injector 的所有 JVM 参数

## 快速开始

### 1. 下载 crskin-all

从 [Releases](https://github.com/createmeow/Cr-Auth/releases) 下载最新的 `crskin-all-1.0.0.jar`

### 2. 配置 Minecraft 服务器

在 Minecraft 服务器启动脚本中添加 JVM 参数：

**Windows (start.bat)**
```batch
java -server -XX:+UseG1GC -Xmx4096M -Xms1024M ^
     -Dfile.encoding=UTF-8 ^
     -javaagent:crskin-all-1.0.0.jar=http://127.0.0.1:25578/ ^
     -jar minecraft_server.jar nogui
```

**Linux (start.sh)**
```bash
java -server -XX:+UseG1GC -Xmx4096M -Xms1024M \
     -Dfile.encoding=UTF-8 \
     -javaagent:crskin-all-1.0.0.jar=http://127.0.0.1:25578/ \
     -jar minecraft_server.jar nogui
```

### 3. 配置文件（可选）

首次启动时会自动创建 `crauth-config.json`，支持以下配置：

```json
{
  "host": "0.0.0.0",
  "port": 25578,
  "reject_offline_players": true,
  "max_username_length": 16,
  "authlib": {
    "Mojang": {
      "serverlink": "https://sessionserver.mojang.com/session/minecraft/hasJoined",
      "Abbreviation": "Mo"
    },
    "LittleSkin": {
      "serverlink": "https://littleskin.cn/api/yggdrasil",
      "Abbreviation": "Li"
    }
  }
}
```

### 4. 系统属性覆盖（可选）

可以通过系统属性覆盖配置：

```bash
java -Dcrskin.host=0.0.0.0 \
     -Dcrskin.port=25578 \
     -javaagent:crskin-all-1.0.0.jar=http://127.0.0.1:25578/ \
     -jar minecraft_server.jar nogui
```

## 参数说明

crskin-all 完全兼容 authlib-injector 的所有参数，可以直接使用：

```
-Dauthlibinjector.noLogFile
    不要将日志输出到文件.
    默认情况下, crskin-all 会将日志输出到控制台以及当前目录下的 authlib-injector.log 文件.
    开启此选项后, 日志仅会输出到控制台.

-Dauthlibinjector.mojangNamespace={default|enabled|disabled}
    设置是否启用 Mojang 命名空间 (@mojang 后缀).
    若验证服务器未设置 feature.no_mojang_namespace 选项, 则该功能默认启用.

    启用后, 则可以使用名为 <username>@mojang 的虚拟角色来调用对应正版角色的皮肤.
    例如,
     - /give @p minecraft:skull 1 3 {SkullOwner:"Notch@mojang"}
     - /npc skin Notch@mojang
    显示的将会是 Notch 的皮肤.

-Dauthlibinjector.mojangProxy={代理服务器 URL}
    设置访问 Mojang 验证服务时使用的代理, 目前仅支持 SOCKS 协议.
    URL 格式: socks://<host>:<port>

-Dauthlibinjector.legacySkinPolyfill={default|enabled|disabled}
    是否启用旧式皮肤 API polyfill, 即 'GET /skins/MinecraftSkins/{username}.png'.
    若验证服务器未设置 feature.legacy_skin_api 选项, 则该功能默认启用.

-Dauthlibinjector.debug (等价于 -Dauthlibinjector.debug=verbose,authlib)
 或 -Dauthlibinjector.debug={调试选项; 逗号分隔}
    可用的调试选项:
     - verbose             详细日志
     - authlib             开启 Mojang authlib 的调试输出
     - dumpClass           转储修改过的类
     - printUntransformed  打印已分析但未修改的类; 隐含 verbose

-Dauthlibinjector.ignoredPackages={包列表; 逗号分隔}
    忽略指定的包, 其中的类将不会被分析或修改.

-Dauthlibinjector.disableHttpd
    禁用内建的 HTTP 服务器.

-Dauthlibinjector.httpdPort={端口号}
    设置内置 HTTP 服务器使用的端口号, 默认为 0 (随机分配).

-Dauthlibinjector.noShowServerName
    不要在 Minecraft 主界面展示验证服务器名称.

-Dauthlibinjector.mojangAntiFeatures={default|enabled|disabled}
    设置是否开启 Minecraft 的部分 anti-feature.
    若验证服务器未设置 feature.enable_mojang_anti_features 选项, 则默认禁用.

-Dauthlibinjector.profileKey={default|enabled|disabled}
    是否启用消息签名密钥对功能.
    此功能需要验证服务器支持, 若验证服务器未设置 feature.enable_profile_key 选项, 则该功能默认禁用.

-Dauthlibinjector.usernameCheck={default|enabled|disabled}
    是否启用玩家用户名检查.
    若验证服务器未设置 feature.usernameCheck 选项, 则默认禁用.
    注意, 开启此功能将导致用户名包含非英文字符的玩家无法进入服务器.
```

## 与 MultiYggdrasil 的区别

| 特性 | MultiYggdrasil | crskin-all |
|------|----------------|------------|
| **多认证源共存** | ✅ 支持 | ✅ 支持 |
| **玩家名处理** | 添加后缀（如 `Notch.cust`） | 添加前缀（如 `Mo-Notch`、`Li-Notch`） |
| **统一玩家身份** | ❌ 不同认证源使用不同 UUID | ✅ 同一玩家可通过不同认证源登录，使用相同 UUID |
| **账户合并** | ❌ 不支持 | ✅ 支持合并不同认证源的账户 |
| **内置 HTTP 服务器** | ❌ 需要外部认证服务器 | ✅ 内置完整的认证服务器 |
| **数据库支持** | ❌ 无 | ✅ SQLite 数据库存储玩家映射 |
| **配置文件** | ❌ 无 | ✅ JSON 配置文件 |
| **自动插件部署** | ❌ 无 | ✅ 自动部署 Bukkit 显示名修正插件 |

**核心优势**：crskin-all 允许同一玩家通过 Mojang 正版或 LittleSkin 登录，在服务端被视为同一个玩家（相同的 UUID 和玩家名），实现真正的多认证源统一身份。

## 构建

构建依赖：Gradle、JDK 17+。

```bash
cd crskin-all
gradle build
```

生成的 JAR 位于 `build/libs/crskin-all-1.0.0.jar`

## 技术细节

- **Java 版本**: JDK 17+
- **语言**: Java 17
- **数据库**: SQLite
- **HTTP 服务器**: Java 内置 HTTP Server
- **字节码操作**: ASM 9.7.1
- **基础项目**: authlib-injector 1.2.5

## 许可证

GNU Affero General Public License v3.0 or later (AGPL-3.0)

本程序基于 [authlib-injector](https://github.com/yushijinhun/authlib-injector)（AGPL-3.0 许可证）开发。

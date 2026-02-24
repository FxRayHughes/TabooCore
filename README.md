# TabooCore

枫溪为了和对象敢敢一起玩高版本 Minecraft，制作了这个核心。

TabooCore 是一个 Java Agent，让 [TabooLib](https://github.com/TabooLib/TabooLib) 能在**原版 Minecraft 服务端**（无需 Bukkit/Spigot/Paper）上运行。通过 [SpongePowered Mixin](https://github.com/SpongePowered/Mixin) 钩入服务端生命周期，实现插件加载、事件系统和任务调度。

## 快速开始

### 环境要求

- Java 25
- Minecraft 服务端 26.1

### 使用

```bash
java -javaagent:TabooCore.jar -jar minecraft_server.jar
```

TabooCore 首次启动时会自动从 Maven 仓库下载 TabooLib 模块和 Kotlin 运行时到 `libraries/` 目录。

### 构建

```bash
./gradlew shadowJar
```

产物位于 `build/libs/TabooCore-1.0.0.jar`。

## 架构

```
premain (TabooCoreAgent)
  ├── TabooLibLoader       下载并加载 TabooLib 模块到 classpath
  ├── LifeCycle.CONST      TabooLib 静态初始化
  ├── PluginScanner        扫描 plugins/ 目录，收集插件 Mixin 配置
  ├── MixinBootstrap       初始化 Mixin，注入字节码转换器
  └── LifeCycle.LOAD       Mixin 注入完成

服务端初始化完成 (MixinDedicatedServer TAIL)
  ├── LifeCycle.ENABLE     事件系统就绪
  └── PluginLoader         加载插件 (onLoad -> onEnable)

首次 tick (MixinMinecraftServer HEAD)
  ├── PluginLoader         调用 onActive
  └── LifeCycle.ACTIVE     玩家可加入

每次 tick
  └── EventBridge          触发 ServerTickEvent，执行同步任务

服务端关闭 (MixinMinecraftServer HEAD)
  ├── PluginLoader         调用 onDisable
  └── LifeCycle.DISABLE
```

## 模块说明

| 包 | 职责 |
|---|---|
| `agent/` | Java Agent 入口，协调启动流程 |
| `bootstrap/` | 动态下载 TabooLib 依赖；初始化 Mixin 框架 |
| `mixin/` | 通过 Mixin 钩入服务端生命周期和游戏事件 |
| `loader/` | 插件加载器，扫描 `@SubscribeEvent` 并注册到 InternalEventBus |
| `platform/` | TabooLib `PlatformIO`（日志）和 `PlatformExecutor`（任务调度） |
| `bridge/` | 将 Minecraft 原生事件转换为 TabooLib 事件 |
| `player/` | `Player`：直接调用 NMS API 实现 `ProxyPlayer` |
| `event/` | 原版事件（玩家加入/退出、Tick、方块破坏等） |

## 插件开发

### 1. 配置 build.gradle.kts

```kotlin
plugins {
    java
    id("io.izzel.taboolib") version "2.0.31"
    kotlin("jvm") version "2.3.10"
}

taboolib {
    subproject = true
    env {
        repoTabooLib = project.repositories.mavenLocal().url.toString()
    }
    version {
        taboolib = "6.2.4-local-dev"
    }
}

dependencies {
    compileOnly("taboocore:TabooCore:1.0.0")
    compileOnly(fileTree("libs"))
}
```

> 需要在 `settings.gradle.kts` 的 `pluginManagement.repositories` 中添加 `mavenLocal()`。

### 2. 创建主类

```kotlin
object MyPlugin : Plugin() {

    override fun onEnable() {
        info("MyPlugin enabled!")
    }
}
```

### 3. 监听事件

```kotlin
object MyListener {

    @SubscribeEvent
    fun onBlockBreak(e: BlockBreakEvent.Post) {
        e.player.sendMessage("You broke: ${e.blockId}")
    }
}
```

### 4. 插件描述文件

在 `src/main/resources/taboocore.plugin.json` 中声明：

```json
{
  "name": "MyPlugin",
  "main": "com.example.MyPlugin",
  "version": "1.0.0",
  "isolate": false
}
```

如果插件使用了 Mixin，还需要在 JAR Manifest 中声明 `TabooLib-Mixins` 指向 mixin 配置文件。

### 5. 部署

将插件 JAR 放入服务端的 `plugins/` 目录。

## 配置

服务端根目录下的 `taboocore.json` 可覆盖默认配置：

```json
{
  "taboolib": {
    "version": "6.2.4-local-dev",
    "modules": []
  },
  "kotlin": {
    "version": "2.3.10",
    "coroutines": "1.10.2"
  },
  "repo": {
    "central": "https://maven.aliyun.com/repository/central",
    "taboolib": "https://repo.tabooproject.org/repository/releases"
  },
  "libs-dir": "libraries",
  "dev": {
    "enabled": false,
    "local-repo": ""
  }
}
```

| 字段 | 说明 |
|------|------|
| `taboolib.version` | TabooLib 版本 |
| `taboolib.modules` | 额外加载的 TabooLib 模块（common 系列自动加载） |
| `kotlin.version` | Kotlin stdlib 版本 |
| `kotlin.coroutines` | kotlinx-coroutines 版本 |
| `repo.central` | Maven Central 镜像 |
| `repo.taboolib` | TabooLib 仓库地址 |
| `libs-dir` | 依赖下载目录 |
| `dev.enabled` | 启用本地开发模式（优先从本地 Maven 仓库加载） |
| `dev.local-repo` | 本地 Maven 仓库路径（默认 `~/.m2/repository`） |

## 依赖

- **SpongePowered Mixin 0.8.7** — 打包进 JAR
- **Kotlinx Coroutines 1.10.2** — 打包进 JAR
- **TabooLib / Kotlin 运行时** — 启动时动态下载，所有插件共享
- **Minecraft Server JAR** — 仅编译期使用（`libs/` 目录）

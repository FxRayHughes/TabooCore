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
| `event/` | 原版事件（玩家、实体、方块、背包、载具等，共 60+ 个） |
| `packet/` | 数据包拦截框架（PacketSendEvent / PacketReceiveEvent） |
| `proxy/` | 代理对象（EntityProxy / WorldProxy / ItemProxy / ContainerProxy 等） |
| `ui/` | 原版箱子 UI（Chest / PageableChest / StorableChest） |
| `data/` | DataComponent 工具（DataComponentUtils / EntityDataUtils） |
| `util/` | 工具类（EntityUtils / ItemUtils / NbtUtils / LocationUtils / MathUtils） |
| `reflect/` | NMS 私有字段反射工具（getPrivate / setPrivate） |
| `scheduler/` | 全局协程 Scope（SupervisorJob + Dispatchers.Default） |
| `world/` | 运行时世界管理（创建/加载/卸载/删除/导入/导出） |

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

    @SubscribeEvent
    fun onEntityDamage(e: EntityDamageEvent.Pre) {
        if (e.damage > 10.0) e.cancel()
    }

    @SubscribeEvent
    fun onPacketSend(e: PacketSendEvent.Pre) {
        // 拦截发往客户端的数据包
    }
}
```

### 4. 使用代理对象

```kotlin
// 物品构建
val sword = item(Items.DIAMOND_SWORD) {
    name("§6神剑")
    lore("§7攻击力 +100", "§7传说品质")
    enchant(Enchantments.SHARPNESS, 5)
    isUnbreakable = true
}

// 实体操作
val proxy = entity.proxy()
proxy.teleport(x, y, z)
proxy.addEffect(MobEffects.SPEED, 200, 1)
proxy.addTag("boss")

// 世界操作
val world = serverLevel.proxy()
world.difficulty = Difficulty.HARD
world.gameRules.keepInventory = true
world.time = 6000

// 背包操作
val container = player.containerMenu.proxy()
container.setItem(0, itemStack)
val contents = container.contents
```

### 5. 箱子 UI

```kotlin
buildChest("§8商店", rows = 3) {
    slot(13) {
        item = item(Items.DIAMOND) { name("§b钻石") }
        onClick { ctx ->
            ctx.player.sendMessage("购买成功！")
        }
    }
    onClose { player ->
        player.sendMessage("关闭了商店")
    }
}.open(player)
```

翻页 UI：

```kotlin
buildPageableChest("§8物品列表", itemList) { item ->
    // 将数据映射为 ItemStack
    itemStack(item)
}.open(player)
```

### 6. 数据包拦截

```kotlin
@SubscribeEvent
fun onPacketSend(e: PacketSendEvent.Pre) {
    if (e.packet is ClientboundSetEntityMotionPacket) {
        e.cancel() // 阻止发送
    }
}

@SubscribeEvent
fun onPacketReceive(e: PacketReceiveEvent.Post) {
    // 注意：PacketReceiveEvent 在 Netty IO 线程触发
    val packet = e.packet
}
```

也可以通过 PacketManager 注册持久监听：

```kotlin
PacketManager.addListener(object : PacketListener {
    override fun onSend(player: ServerPlayer, packet: Packet<*>): Boolean {
        return false // 返回 true 取消发送
    }
})
```

### 7. 反射工具

```kotlin
// 读取私有字段
val field = entity.getPrivate<String>("somePrivateField")

// 写入私有字段
entity.setPrivate("somePrivateField", "newValue")

// 静态属性
val value = getStaticProperty<Int>(MyClass::class, "CONSTANT")

// ServerPlayer 语言
val lang = player.getLanguage() // "zh_cn"
```

### 8. 插件描述文件

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

### 9. 世界管理系统

```kotlin
// 在 onEnable() 中初始化（读取配置、自动加载世界）
WorldManager.initialize()

// 创建新世界
val level = WorldManager.create("myworld", WorldTemplate.NORMAL, seed = 12345L)

// 加载已注册的世界
val level = WorldManager.load("myworld")

// 卸载世界（默认保存）
WorldManager.unload("myworld")

// 删除世界（卸载 + 删除磁盘文件 + 移除注册）
WorldManager.delete("myworld")

// 导入外部世界文件夹
WorldManager.import("myworld", File("external/myworld"))

// 导出世界到指定目录
WorldManager.export("myworld", File("backup/myworld"))

// 获取已加载的世界
val level = WorldManager.getWorld("myworld")

// 在 onDisable() 中保存所有世界
WorldManager.saveAll()
```

世界配置自动持久化到服务端根目录的 `worldmanager.json`，`autoLoad = true` 的世界在下次启动时自动恢复。

世界文件存储结构：
```
serverRoot/
  worldmanager.json
  worlds/
    myworld/
      level.dat
      dimensions/worlds/myworld/region/
```

### 10. 部署

将插件 JAR 放入服务端的 `plugins/` 目录。

## 事件列表

### 玩家事件（`event.player`）

| 事件 | 说明 |
|------|------|
| `PlayerJoinEvent` | 玩家加入 |
| `PlayerQuitEvent` | 玩家退出 |
| `PlayerChatEvent` | 玩家聊天 |
| `PlayerCommandEvent` | 玩家执行命令 |
| `PlayerMoveEvent` | 玩家移动 |
| `PlayerTeleportEvent` | 玩家传送 |
| `PlayerRespawnEvent` | 玩家重生 |
| `PlayerGameModeChangeEvent` | 游戏模式变更 |
| `PlayerInteractEvent` | 玩家交互方块/空气 |
| `PlayerInteractEntityEvent` | 玩家交互实体 |
| `PlayerDropItemEvent` | 玩家丢弃物品 |
| `PlayerPickupItemEvent` | 玩家拾取物品 |
| `PlayerItemHeldEvent` | 玩家切换手持物品 |
| `PlayerItemConsumeEvent` | 玩家使用物品 |
| `PlayerToggleSneakEvent` | 切换潜行 |
| `PlayerToggleSprintEvent` | 切换疾跑 |
| `PlayerToggleFlightEvent` | 切换飞行 |
| `PlayerSwapHandItemsEvent` | 交换双手物品 |
| `PlayerBedEnterEvent` | 进入床 |
| `PlayerBedLeaveEvent` | 离开床 |
| `PlayerExpChangeEvent` | 经验变化 |
| `PlayerLevelChangeEvent` | 等级变化 |
| `FoodLevelChangeEvent` | 饥饿度变化 |
| `PlayerKickEvent` | 玩家被踢出 |
| `PlayerChangedWorldEvent` | 玩家切换世界 |
| `PlayerAnimationEvent` | 玩家动画（挥手等） |
| `PlayerBucketFillEvent` | 玩家装桶 |
| `PlayerBucketEmptyEvent` | 玩家倒桶 |
| `PlayerShearEntityEvent` | 玩家剪羊毛 |
| `PlayerArmorStandManipulateEvent` | 操作盔甲架 |
| `PlayerFishEvent` | 玩家钓鱼 |
| `PlayerAdvancementDoneEvent` | 完成进度 |

### 实体事件（`event.entity`）

| 事件 | 说明 |
|------|------|
| `EntitySpawnEvent` | 实体生成 |
| `EntityDeathEvent` | 实体死亡 |
| `EntityDamageEvent` | 实体受伤 |
| `EntityRegainHealthEvent` | 实体回血 |
| `EntityTargetEvent` | 实体锁定目标 |
| `EntityExplodeEvent` | 实体爆炸 |
| `EntityTeleportEvent` | 实体传送 |
| `EntityKnockbackEvent` | 实体击退 |
| `EntityCombustEvent` | 实体着火 |
| `EntityMountEvent` | 实体骑乘 |
| `EntityDismountEvent` | 实体下马 |
| `EntityPotionEffectEvent` | 药水效果变化 |
| `EntityTameEvent` | 驯服实体 |
| `EntityBreedEvent` | 实体繁殖 |
| `EntityEnterLoveModeEvent` | 实体进入繁殖模式 |
| `EntityChangeBlockEvent` | 实体改变方块（雪人/末影人等） |
| `EntityEnterPortalEvent` | 实体进入传送门 |
| `EntityResurrectEvent` | 实体复活（图腾） |
| `EntityAirChangeEvent` | 实体氧气值变化 |
| `ProjectileLaunchEvent` | 投射物发射 |
| `ProjectileHitEvent` | 投射物命中 |

### 世界事件（`event.world`）

| 事件 | 说明 |
|------|------|
| `BlockBreakEvent` | 方块被破坏 |
| `BlockPlaceEvent` | 方块被放置 |
| `BlockDamageEvent` | 方块被挖掘（进度） |
| `BlockIgniteEvent` | 方块被点燃 |
| `BlockBurnEvent` | 方块被烧毁 |
| `BlockFadeEvent` | 方块消退（冰融化等） |
| `BlockFormEvent` | 方块形成（雪/冰） |
| `BlockGrowEvent` | 方块生长（农作物等） |
| `BlockFromToEvent` | 流体流动 |
| `BlockDispenseEvent` | 发射器发射 |
| `BlockExplodeEvent` | 方块爆炸（床/重生锚） |
| `BlockPistonExtendEvent` | 活塞伸出 |
| `BlockPistonRetractEvent` | 活塞收回 |
| `SignChangeEvent` | 告示牌文字变更 |
| `LeavesDecayEvent` | 树叶消失 |
| `LightningStrikeEvent` | 闪电落地 |
| `WeatherChangeEvent` | 天气变化 |
| `ChunkLoadEvent` | 区块加载 |
| `ChunkUnloadEvent` | 区块卸载 |
| `PortalCreateEvent` | 传送门创建 |
| `EntityExplodeEvent` | 实体爆炸（TNT/苦力怕） |

### 背包事件（`event.inventory`）

| 事件 | 说明 |
|------|------|
| `InventoryClickEvent` | 背包点击 |
| `InventoryOpenEvent` | 背包打开 |
| `InventoryCloseEvent` | 背包关闭 |

### 载具事件（`event.vehicle`）

| 事件 | 说明 |
|------|------|
| `VehicleEnterEvent` | 进入载具 |
| `VehicleExitEvent` | 离开载具 |
| `VehicleDamageEvent` | 载具受伤 |

### 数据包事件（`packet`）

| 事件 | 触发线程 | 说明 |
|------|---------|------|
| `PacketSendEvent.Pre/Post` | 游戏线程 | 服务端向客户端发包 |
| `PacketReceiveEvent.Pre/Post` | Netty IO 线程 | 客户端向服务端发包 |

> 所有事件均有 `Pre`（可取消）和 `Post` 两个阶段，除非特殊说明。

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

## 感谢

- [TabooLib](https://github.com/TabooLib/TabooLib)
- [SpongePowered Mixin](https://github.com/SpongePowered/Mixin)
- [Kotlin](https://kotlinlang.org/)
- [Kotlinx Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- @BingZi-233 赞助的AI开发Token

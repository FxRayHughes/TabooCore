package taboocore.bridge

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import taboocore.player.TabooCorePlayer
import taboocore.event.PlayerJoinEvent
import taboocore.event.PlayerQuitEvent
import taboocore.event.ServerTickEvent
import taboocore.console.TabooCoreConsole
import taboocore.internal.InternalCommands
import taboocore.internal.TpsTracker
import taboocore.util.ServerUtils
import taboocore.util.TabooCoreLogger
import taboolib.common.LifeCycle
import taboolib.common.TabooLib

object EventBridge {

    private var firstTick = true

    fun firePlayerJoin(player: ServerPlayer) {
        PlayerJoinEvent(TabooCorePlayer.of(player)).call()
    }

    fun firePlayerQuit(player: ServerPlayer) {
        PlayerQuitEvent(TabooCorePlayer.of(player)).call()
        TabooCorePlayer.remove(player)
    }

    private var tickCount = 0

    /**
     * 服务器初始化完成后调用（initServer RETURN）
     * 注册内置命令，触发 ENABLE 生命周期，加载并启用插件
     */
    fun fireServerStarted(server: MinecraftServer) {
        TabooCoreLogger.markServerStarted()
        ServerUtils.serverInstance = server
        InternalCommands.register(server)
        TabooCoreConsole.setupCompleter(server)
        TabooCoreConsole.setupLog4j2Appender()
        TabooLib.lifeCycle(LifeCycle.ENABLE)
        taboocore.loader.PluginLoader.loadAll()
    }

    /**
     * 每个 tick 调用（tickServer HEAD）
     * 首次 tick 触发 ACTIVE 生命周期；每 tick 记录 TPS
     */
    fun fireTick() {
        if (firstTick) {
            firstTick = false
            taboocore.loader.PluginLoader.activeAll()
            TabooLib.lifeCycle(LifeCycle.ACTIVE)
        }
        TpsTracker.record()
        ServerTickEvent(++tickCount).call()
    }

    fun fireServerStopping() {
        TabooLib.lifeCycle(LifeCycle.DISABLE)
    }
}

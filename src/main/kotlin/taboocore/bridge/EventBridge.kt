package taboocore.bridge

import net.minecraft.server.level.ServerPlayer
import taboocore.player.Player
import taboocore.event.PlayerJoinEvent
import taboocore.event.PlayerQuitEvent
import taboocore.event.ServerTickEvent
import taboocore.platform.TabooCoreExecutor
import taboolib.common.LifeCycle
import taboolib.common.TabooLib

object EventBridge {

    private var firstTick = true

    fun firePlayerJoin(player: ServerPlayer) {
        PlayerJoinEvent(Player.of(player)).call()
    }

    fun firePlayerQuit(player: ServerPlayer) {
        PlayerQuitEvent(Player.of(player)).call()
        Player.remove(player)
    }

    private var tickCount = 0

    /**
     * 服务器初始化完成后调用（initServer RETURN）
     * 触发 ENABLE 生命周期，加载并启用插件
     */
    fun fireServerStarted() {
        // ENABLE: 插件可以监听事件
        TabooLib.lifeCycle(LifeCycle.ENABLE)
        taboocore.loader.PluginLoader.loadAll()
    }

    /**
     * 每个 tick 调用（tickServer HEAD）
     * 首次 tick 触发 ACTIVE 生命周期
     */
    fun fireTick() {
        if (firstTick) {
            firstTick = false
            // ACTIVE: 首次 tick，玩家可以加入游戏
            taboocore.loader.PluginLoader.activeAll()
            TabooLib.lifeCycle(LifeCycle.ACTIVE)
        }
        drainSyncQueue()
        ServerTickEvent(++tickCount).call()
    }

    fun fireServerStopping() {
        TabooLib.lifeCycle(LifeCycle.DISABLE)
    }

    @Suppress("UNCHECKED_CAST")
    private fun drainSyncQueue() {
        val executor = TabooCoreExecutor.instance ?: return
        synchronized(executor.syncQueue) {
            val iter = executor.syncQueue.iterator()
            while (iter.hasNext()) {
                val (runnable, task) = iter.next()
                iter.remove()
                runnable.executor(task)
            }
        }
    }
}

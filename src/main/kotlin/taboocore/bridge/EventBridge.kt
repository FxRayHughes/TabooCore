package taboocore.bridge

import net.minecraft.server.level.ServerPlayer
import taboocore.player.VanillaProxyPlayer
import taboocore.event.VanillaPlayerJoinEvent
import taboocore.event.VanillaPlayerQuitEvent
import taboocore.event.VanillaServerTickEvent
import taboocore.platform.TabooCoreExecutor

/**
 * 桥接 TabooLib 事件总线
 * 直接构造事件实例，不使用反射
 * 编译时依赖 platform-taboocore（compileOnly），运行时由 TabooLibLoader 动态加载
 */
object EventBridge {

    fun firePlayerJoin(player: ServerPlayer) {
        VanillaPlayerJoinEvent(VanillaProxyPlayer(player)).call()
    }

    fun firePlayerQuit(player: ServerPlayer) {
        VanillaPlayerQuitEvent(VanillaProxyPlayer(player)).call()
    }

    private var tickCount = 0

    fun fireTick() {
        drainSyncQueue()
        VanillaServerTickEvent(++tickCount).call()
    }

    /**
     * 驱动 TabooCoreExecutor 的同步任务队列
     * 直接访问 Kotlin object 单例，不使用反射
     */
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

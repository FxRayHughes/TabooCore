package taboocore.platform

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import taboocore.scheduler.TabooCoreScope
import taboolib.common.Inject
import taboolib.common.platform.Awake
import taboolib.common.platform.Platform
import taboolib.common.platform.PlatformSide
import taboolib.common.platform.service.PlatformExecutor

@Awake
@Inject
@PlatformSide(Platform.TABOOCORE)
class TabooCoreExecutor : PlatformExecutor {

    companion object {
        /** 由 TabooLib 注入后持有单例引用，供 TabooCore 的 EventBridge 直接访问 */
        var instance: TabooCoreExecutor? = null
            internal set
    }

    init {
        instance = this
    }

    // 同步任务队列，由 MixinMinecraftServer 的 tick 钩子驱动
    val syncQueue = ArrayDeque<Pair<PlatformExecutor.PlatformRunnable, TabooCorePlatformTask>>()

    override fun start() {}

    override fun submit(runnable: PlatformExecutor.PlatformRunnable): PlatformExecutor.PlatformTask {
        val task = TabooCorePlatformTask()
        when {
            runnable.now -> runnable.executor(task)
            runnable.async -> task.job = scheduleAsync(runnable, task)
            else -> enqueueSync(runnable, task)
        }
        return task
    }

    private fun scheduleAsync(
        runnable: PlatformExecutor.PlatformRunnable,
        task: TabooCorePlatformTask
    ): Job = TabooCoreScope.launch {
        if (runnable.delay > 0) delay(runnable.delay * 50L)
        if (runnable.period > 0) {
            while (isActive) {
                runnable.executor(task)
                delay(runnable.period * 50L)
            }
        } else {
            runnable.executor(task)
        }
    }

    private fun enqueueSync(runnable: PlatformExecutor.PlatformRunnable, task: TabooCorePlatformTask) {
        synchronized(syncQueue) { syncQueue.addLast(runnable to task) }
    }

    class TabooCorePlatformTask : PlatformExecutor.PlatformTask {
        var job: Job? = null
        override fun cancel() { job?.cancel() }
    }
}

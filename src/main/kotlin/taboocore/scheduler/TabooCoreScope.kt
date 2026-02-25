package taboocore.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * TabooCore 全局协程作用域
 * 使用虚拟线程调度器，充分利用 JDK 21+ 的虚拟线程特性
 * SupervisorJob 保证子协程异常不会取消整个 scope
 */
object Scope : CoroutineScope by CoroutineScope(
    SupervisorJob() + Dispatchers.Default
)

object VirtualScope : CoroutineScope by CoroutineScope(
    SupervisorJob() + Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
)

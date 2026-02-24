package taboocore.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * TabooCore 全局协程作用域
 * 使用 SupervisorJob 保证子协程异常不会取消整个 scope
 * 使用 Dispatchers.Default 线程池执行 CPU 密集型任务
 */
object TabooCoreScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

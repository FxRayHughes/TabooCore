package taboocore.loader

import com.google.gson.Gson
import taboocore.agent.PluginInfo
import taboocore.util.TabooCoreLogger
import java.util.jar.JarFile

/**
 * Bootstrap 阶段执行器
 *
 * 在 premain 末尾（LOAD 之后、服务端启动前）执行，
 * 扫描插件 JAR 的 taboocore.plugin.json 中的 bootstrap 字段，
 * 实例化并调用 [TabooCoreBootstrap.onBootstrap]。
 */
object BootstrapPhase {

    private val gson = Gson()

    fun execute(plugins: List<PluginInfo>) {
        for (plugin in plugins) {
            runCatching {
                val bootstrapClass = findBootstrapClass(plugin) ?: return@runCatching
                val clazz = Class.forName(bootstrapClass)
                val constructor = clazz.getDeclaredConstructor()
                constructor.isAccessible = true
                val instance = constructor.newInstance()
                if (instance is TabooCoreBootstrap) {
                    instance.onBootstrap()
                    TabooCoreLogger.info("Bootstrap: ${clazz.simpleName}")
                } else {
                    TabooCoreLogger.error("Bootstrap class $bootstrapClass does not implement TabooCoreBootstrap")
                }
            }.onFailure { e ->
                TabooCoreLogger.error("Failed to execute bootstrap for ${plugin.jar.name}: ${e.message}", e)
            }
        }
    }

    private fun findBootstrapClass(plugin: PluginInfo): String? {
        return JarFile(plugin.jar).use { jf ->
            val entry = jf.getJarEntry("taboocore.plugin.json") ?: return@use null
            val json = jf.getInputStream(entry).bufferedReader().readText()
            val meta = gson.fromJson(json, BootstrapMeta::class.java)
            meta.bootstrap?.takeIf { it.isNotBlank() }
        }
    }

    private data class BootstrapMeta(
        val bootstrap: String? = null
    )
}

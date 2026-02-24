package taboocore.loader

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarFile

/**
 * 插件加载器
 * 在服务端启动完成后（由 MixinMinecraftServer 触发）扫描并加载插件
 */
object PluginLoader {

    private val loaded = mutableListOf<LoadedPlugin>()
    private val gson = Gson()

    /** 共享 ClassLoader，所有 isolate=false 的插件共用 */
    private var sharedLoader: SharedPluginClassLoader? = null

    fun loadAll() {
        val pluginsDir = File("plugins")
        if (!pluginsDir.exists()) return

        // 读取元数据并按 priority 排序
        val entries = pluginsDir.listFiles { f -> f.extension == "jar" }
            ?.mapNotNull { jar -> readMeta(jar)?.let { jar to it } }
            ?.sortedBy { it.second.priority }
            ?: return

        // 先收集所有非隔离插件的 URL，构建共享 ClassLoader
        val sharedUrls = entries
            .filter { !it.second.isolate }
            .map { it.first.toURI().toURL() }
        if (sharedUrls.isNotEmpty()) {
            sharedLoader = SharedPluginClassLoader(sharedUrls.toTypedArray(), javaClass.classLoader)
        }

        // 依次加载
        entries.forEach { (jar, meta) ->
            runCatching { load(jar, meta) }.onFailure { e ->
                System.err.println("[TabooCore] 加载插件失败: ${jar.name} - ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun load(jar: File, meta: PluginMeta) {
        val loader = if (meta.isolate) {
            URLClassLoader(arrayOf(jar.toURI().toURL()), javaClass.classLoader)
        } else {
            sharedLoader!!
        }
        val mainClass = loader.loadClass(meta.main)
        val plugin = mainClass.getDeclaredConstructor().newInstance() as TabooCorePlugin
        plugin.onEnable()
        loaded += LoadedPlugin(meta, plugin, loader)
        println("[TabooCore] 已加载插件: ${meta.name} v${meta.version}" +
            if (meta.isolate) " (隔离)" else "")
    }

    fun disableAll() {
        loaded.reversed().forEach { runCatching { it.plugin.onDisable() } }
        loaded.clear()
        sharedLoader = null
    }

    private fun readMeta(jar: File): PluginMeta? = runCatching {
        JarFile(jar).use { jf ->
            val entry = jf.getJarEntry("taboocore.plugin.json") ?: return@use null
            val json = jf.getInputStream(entry).bufferedReader().readText()
            val meta = gson.fromJson(json, PluginMeta::class.java)
            if (meta.main.isEmpty()) return@use null
            if (meta.name.isEmpty()) meta.name = jar.nameWithoutExtension
            if (meta.version.isEmpty()) meta.version = "1.0.0"
            meta
        }
    }.getOrNull()
}

/**
 * 共享 ClassLoader：持有多个插件 JAR 的 URL，所有非隔离插件共用同一个实例
 */
private class SharedPluginClassLoader(
    urls: Array<URL>,
    parent: ClassLoader
) : URLClassLoader(urls, parent)

private data class LoadedPlugin(
    val meta: PluginMeta,
    val plugin: TabooCorePlugin,
    val loader: ClassLoader
)

/**
 * 插件元数据，对应 taboocore.plugin.json
 *
 * ```json
 * {
 *   "name": "MyPlugin",
 *   "main": "com.example.MyPlugin",
 *   "version": "1.0.0",
 *   "priority": 0,
 *   "isolate": false
 * }
 * ```
 *
 * isolate: true 则使用独立 ClassLoader，false 则与其他非隔离插件共享（默认 false）
 */
data class PluginMeta(
    var name: String = "",
    var main: String = "",
    var version: String = "1.0.0",
    var priority: Int = 0,
    var isolate: Boolean = false
)

interface TabooCorePlugin {
    fun onEnable() {}
    fun onDisable() {}
}

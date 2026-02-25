package taboocore.loader

import com.google.gson.Gson
import taboolib.common.event.InternalEvent
import taboolib.common.event.InternalEventBus
import taboolib.common.platform.Plugin
import taboolib.common.platform.event.SubscribeEvent
import taboocore.util.TabooCoreLogger
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarFile

/**
 * 插件加载器
 * 在服务端启动完成后（由 MixinDedicatedServer 触发）扫描并加载插件
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

        // 依次加载并启用
        entries.forEach { (jar, meta) ->
            runCatching { load(jar, meta) }.onFailure { e ->
                TabooCoreLogger.error("Failed to load plugin: ${jar.name} - ${e.message}", e)
            }
        }
    }

    private fun load(jar: File, meta: PluginMeta) {
        val loader = if (meta.isolate) {
            URLClassLoader(arrayOf(jar.toURI().toURL()), javaClass.classLoader)
        } else {
            sharedLoader!!
        }
        // 扫描插件 JAR 中的所有类，注册 @SubscribeEvent 处理器（带禁用代理）
        val proxies = mutableListOf<DisableProxy>()
        injectPluginClasses(jar, loader, proxies)
        // 实例化插件主类并触发生命周期
        val mainClass = loader.loadClass(meta.main)
        val constructor = mainClass.getDeclaredConstructor()
        constructor.isAccessible = true
        val plugin = constructor.newInstance() as Plugin
        plugin.onLoad()
        plugin.onEnable()
        loaded += LoadedPlugin(meta, plugin, loader, proxies)
        TabooCoreLogger.info(
            "Loaded plugin: ${meta.name} v${meta.version}" +
                if (meta.isolate) " (isolated)" else ""
        )
    }

    /**
     * 扫描插件 JAR 中的所有 .class 文件，
     * 对每个有 @SubscribeEvent 且参数为 InternalEvent 子类的方法，
     * 通过 [DisableProxy] 包装后注册到 InternalEventBus。
     *
     * 使用代理的好处：重载时只需禁用代理，无需从 EventBus 移除（避免 API 兼容问题），
     * 被禁用的代理在事件触发时静默跳过，不会双重执行。
     */
    private fun injectPluginClasses(jar: File, loader: ClassLoader, proxies: MutableList<DisableProxy>) {
        JarFile(jar).use { jf ->
            jf.stream()
                .filter { it.name.endsWith(".class") && !it.name.contains('$') }
                .forEach { entry ->
                    val className = entry.name.replace('/', '.').removeSuffix(".class")
                    runCatching {
                        val clazz = Class.forName(className, true, loader)
                        val instance = findKotlinInstance(clazz)

                        for (method in clazz.declaredMethods) {
                            val anno = method.getAnnotation(SubscribeEvent::class.java) ?: continue
                            if (method.parameterCount == 0) continue
                            val eventType = method.parameterTypes[0]
                            if (!InternalEvent::class.java.isAssignableFrom(eventType)) continue

                            method.isAccessible = true

                            // 用 DisableProxy 包装，以便在插件卸载/重载时禁用
                            val proxy = DisableProxy { event ->
                                if (instance != null) {
                                    method.invoke(instance, event)
                                } else {
                                    method.invoke(null, event)
                                }
                            }
                            proxies += proxy

                            @Suppress("UNCHECKED_CAST")
                            InternalEventBus.listen(
                                eventType as Class<InternalEvent>,
                                anno.priority.level,
                                anno.ignoreCancelled,
                                proxy
                            )
                            TabooCoreLogger.info("  @SubscribeEvent: ${clazz.simpleName}.${method.name}(${eventType.simpleName})")
                        }
                    }.onFailure { e ->
                        TabooCoreLogger.error("Failed to inject class: $className - ${e.message}", e)
                    }
                }
        }
    }

    /**
     * 查找 Kotlin object 的 INSTANCE 单例。
     * 对于 Kotlin `object` 声明，编译器生成静态字段 INSTANCE。
     * 普通类返回 null（@SubscribeEvent 方法将以 invokeStatic 方式调用）。
     */
    private fun findKotlinInstance(clazz: Class<*>): Any? {
        return try {
            val field = clazz.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.get(null)
        } catch (_: NoSuchFieldException) {
            null
        }
    }

    /**
     * 触发所有已加载插件的 onActive 回调
     * 在首次 tick 时调用，此时玩家可以加入游戏
     */
    fun activeAll() {
        loaded.forEach { runCatching { it.plugin.onActive() } }
    }

    fun disableAll() {
        loaded.reversed().forEach { lp ->
            runCatching { lp.plugin.onDisable() }
            // 禁用该插件注册的所有事件代理，防止重载后双重触发
            lp.proxies.forEach { it.enabled = false }
        }
        loaded.clear()
        sharedLoader = null
    }

    /**
     * 重新加载所有插件。
     *
     * 流程：禁用所有插件 → 清理资源 → 重新扫描并加载。
     * 已注册的 EventBus 监听器通过 [DisableProxy] 静默禁用，不会被双重执行。
     */
    fun reloadAll() {
        TabooCoreLogger.info("开始重载所有插件...")
        disableAll()
        loadAll()
        TabooCoreLogger.info("插件重载完成。")
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
 * 可禁用的事件监听器代理。
 *
 * 当插件卸载/重载时，将 [enabled] 置为 false，
 * 此后事件触发时该代理静默跳过，不执行真实逻辑。
 */
class DisableProxy(private val delegate: (InternalEvent) -> Unit) : (InternalEvent) -> Unit {
    @Volatile
    var enabled = true

    override fun invoke(event: InternalEvent) {
        if (enabled) delegate(event)
    }
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
    val plugin: Plugin,
    val loader: ClassLoader,
    val proxies: MutableList<DisableProxy> = mutableListOf()
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

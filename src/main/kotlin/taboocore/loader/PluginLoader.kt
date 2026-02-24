package taboocore.loader

import com.google.gson.Gson
import taboolib.common.event.InternalEvent
import taboolib.common.event.InternalEventBus
import taboolib.common.platform.Plugin
import taboolib.common.platform.event.SubscribeEvent
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
                System.err.println("[TabooCore] Failed to load plugin: ${jar.name} - ${e.message}")
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
        // Scan all classes in plugin JAR and register @SubscribeEvent handlers
        injectPluginClasses(jar, loader)
        // Instantiate and start the plugin main class
        val mainClass = loader.loadClass(meta.main)
        val constructor = mainClass.getDeclaredConstructor()
        constructor.isAccessible = true
        val plugin = constructor.newInstance() as Plugin
        plugin.onLoad()
        plugin.onEnable()
        loaded += LoadedPlugin(meta, plugin, loader)
        println(
            "[TabooCore] Loaded plugin: ${meta.name} v${meta.version}" +
                if (meta.isolate) " (isolated)" else ""
        )
    }

    /**
     * 扫描插件 JAR 中的所有 .class 文件，
     * 对每个有 @SubscribeEvent 且参数为 InternalEvent 子类的方法，
     * 直接通过 InternalEventBus.listen() 注册监听器。
     *
     * 绕过 EventBus/ClassVisitor/Reflex 链条，避免 findInstance/optional 等环节的静默失败。
     */
    private fun injectPluginClasses(jar: File, loader: ClassLoader) {
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
                            @Suppress("UNCHECKED_CAST")
                            InternalEventBus.listen(
                                eventType as Class<InternalEvent>,
                                anno.priority.level,
                                anno.ignoreCancelled
                            ) { event ->
                                if (instance != null) {
                                    method.invoke(instance, event)
                                } else {
                                    method.invoke(null, event)
                                }
                            }
                            println("[TabooCore]   @SubscribeEvent: ${clazz.simpleName}.${method.name}(${eventType.simpleName})")
                        }
                    }.onFailure { e ->
                        System.err.println("[TabooCore] Failed to inject class: $className - ${e.message}")
                        e.printStackTrace()
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
    val plugin: Plugin,
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

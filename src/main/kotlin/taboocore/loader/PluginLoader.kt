package taboocore.loader

import com.google.gson.Gson
import taboolib.common.LifeCycle
import taboolib.common.TabooLib
import taboolib.common.event.InternalEvent
import taboolib.common.event.InternalEventBus
import taboolib.common.platform.Plugin
import taboolib.common.platform.command.*
import taboolib.common.platform.command.component.CommandBase
import taboolib.common.platform.command.component.CommandComponent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.ProxyListener
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.registerBukkitListener
import taboolib.common.platform.function.submit
import taboocore.util.TabooCoreLogger
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarFile

/**
 * 插件加载器
 * 模仿 TabooLib ClassVisitorHandler 的 visitor 模式扫描插件类，
 * 处理 @Awake、@Config、@SubscribeEvent、@CommandHeader、@Schedule 等注解。
 */
object PluginLoader {

    private val loaded = mutableListOf<LoadedPlugin>()
    private val gson = Gson()

    /** 共享 ClassLoader，所有 isolate=false 的插件共用 */
    private var sharedLoader: SharedPluginClassLoader? = null

    // 缓存注解类（可能不存在则为 null）
    private val awakeAnno = loadAnnotation("taboolib.common.platform.Awake")
    private val ghostAnno = loadAnnotation("taboolib.common.platform.Ghost")
    private val scheduleAnno = loadAnnotation("taboolib.common.platform.Schedule")
    private val configAnno = loadAnnotation("taboolib.module.configuration.Config")

    // ==================== Visitor 抽象 ====================

    /**
     * 插件类访问器，模仿 TabooLib 的 ClassVisitor。
     * priority 越小越先执行。
     */
    private abstract class PluginClassVisitor(val priority: Int = 0) {
        open fun visitStart(clazz: Class<*>, instance: Any?) {}
        open fun visitField(field: Field, clazz: Class<*>, instance: Any?) {}
        open fun visitMethod(method: Method, clazz: Class<*>, instance: Any?) {}
        open fun visitEnd(clazz: Class<*>, instance: Any?) {}
    }

    // ==================== 内置 Visitor 实现 ====================

    /**
     * 处理 @Awake 方法级别：补偿执行所有已过的生命周期 + ENABLE。
     * 同时收集 ACTIVE/DISABLE 方法供后续调用。
     */
    private class AwakeMethodVisitor(
        private val awakeAnno: Class<out Annotation>,
        private val activeMethods: MutableList<Pair<Method, Any?>>,
        private val disableMethods: MutableList<Pair<Method, Any?>>
    ) : PluginClassVisitor(0) {

        private val invokeNow = setOf(
            LifeCycle.CONST.name, LifeCycle.INIT.name, LifeCycle.LOAD.name, LifeCycle.ENABLE.name
        )

        override fun visitMethod(method: Method, clazz: Class<*>, instance: Any?) {
            val anno = method.getAnnotation(awakeAnno) ?: return
            val lcName = getAwakeLifecycleName(anno)
            method.isAccessible = true
            when {
                lcName in invokeNow -> {
                    if (instance != null) method.invoke(instance) else method.invoke(null)
                    TabooCoreLogger.info("  @Awake($lcName): ${clazz.simpleName}.${method.name}()")
                }
                lcName == LifeCycle.ACTIVE.name -> activeMethods += method to instance
                lcName == LifeCycle.DISABLE.name -> disableMethods += method to instance
            }
        }
    }

    /**
     * 处理 @SubscribeEvent：支持 InternalEvent 和 Bukkit 平台事件。
     */
    private class EventVisitor(
        private val proxies: MutableList<DisableProxy>,
        private val listeners: MutableList<ProxyListener>
    ) : PluginClassVisitor(-1) {

        @Suppress("UNCHECKED_CAST")
        override fun visitMethod(method: Method, clazz: Class<*>, instance: Any?) {
            val anno = method.getAnnotation(SubscribeEvent::class.java) ?: return
            if (method.parameterCount == 0) return
            val eventType = method.parameterTypes[0]
            method.isAccessible = true

            if (InternalEvent::class.java.isAssignableFrom(eventType)) {
                // InternalEvent
                val proxy = DisableProxy { event ->
                    if (instance != null) method.invoke(instance, event) else method.invoke(null, event)
                }
                proxies += proxy
                InternalEventBus.listen(
                    eventType as Class<InternalEvent>,
                    anno.priority.level,
                    anno.ignoreCancelled,
                    proxy
                )
                TabooCoreLogger.info("  @SubscribeEvent: ${clazz.simpleName}.${method.name}(${eventType.simpleName})")
            } else {
                // Bukkit 平台事件
                runCatching {
                    val listener = registerBukkitListener(
                        eventType,
                        anno.priority,
                        anno.ignoreCancelled
                    ) {
                        if (instance != null) method.invoke(instance, it) else method.invoke(null, it)
                    }
                    listeners += listener
                    TabooCoreLogger.info("  @SubscribeEvent(Bukkit): ${clazz.simpleName}.${method.name}(${eventType.simpleName})")
                }.onFailure { e ->
                    TabooCoreLogger.error("  Failed Bukkit event: ${clazz.simpleName}.${method.name}(${eventType.simpleName}) - ${e.message}")
                }
            }
        }
    }

    /**
     * 处理 @CommandHeader / @CommandBody
     */
    private class CommandVisitor : PluginClassVisitor(0) {

        override fun visitEnd(clazz: Class<*>, instance: Any?) {
            val header = clazz.getAnnotation(CommandHeader::class.java) ?: return
            registerPluginCommand(clazz, instance, header)
        }
    }

    /**
     * 处理 @Config：释放资源文件 + 加载配置
     */
    private class ConfigVisitor(
        private val configAnno: Class<out Annotation>
    ) : PluginClassVisitor(1) {

        private val configurationClass = runCatching { Class.forName("taboolib.module.configuration.Configuration") }.getOrNull()
        private val releaseMethod = runCatching {
            val funcClass = Class.forName("taboolib.common.platform.function.IOKt")
            funcClass.methods.firstOrNull { it.name == "releaseResourceFile" }
        }.getOrNull()

        override fun visitField(field: Field, clazz: Class<*>, instance: Any?) {
            val anno = field.getAnnotation(configAnno) ?: return
            field.isAccessible = true
            val name = configAnno.getMethod("value").invoke(anno) as String
            val target = (configAnno.getMethod("target").invoke(anno) as String).ifEmpty { name }
            val concurrent = configAnno.getMethod("concurrent").invoke(anno) as Boolean

            val file = if (releaseMethod != null) {
                releaseMethod.invoke(null, name, target, false) as File
            } else {
                File("plugins", target).also {
                    if (!it.exists()) TabooCoreLogger.error("Config file not found: $target")
                }
            }

            if (configurationClass != null && file.exists()) {
                val loadMethod = configurationClass.methods.firstOrNull {
                    it.name == "loadFromFile" && it.parameterCount >= 1
                }
                if (loadMethod != null) {
                    val conf = when (loadMethod.parameterCount) {
                        1 -> loadMethod.invoke(null, file)
                        else -> {
                            val defaultMethod = configurationClass.methods.firstOrNull {
                                it.name == $$"loadFromFile$default" || (it.name == "loadFromFile" && it.parameterCount >= 2)
                            }
                            defaultMethod?.invoke(null, file, concurrent) ?: loadMethod.invoke(null, file)
                        }
                    }
                    if (conf != null) {
                        field.set(instance, conf)
                        TabooCoreLogger.info("  @Config: ${clazz.simpleName}.${field.name} = $name")
                    }
                }
            }
        }
    }

    /**
     * 处理 @Schedule：注册定时任务
     */
    private class ScheduleVisitor(
        private val scheduleAnno: Class<out Annotation>
    ) : PluginClassVisitor(1) {

        override fun visitMethod(method: Method, clazz: Class<*>, instance: Any?) {
            val anno = method.getAnnotation(scheduleAnno) ?: return
            val async = scheduleAnno.getMethod("async").invoke(anno) as Boolean
            val delay = scheduleAnno.getMethod("delay").invoke(anno) as Long
            val period = scheduleAnno.getMethod("period").invoke(anno) as Long
            method.isAccessible = true
            submit(async = async, delay = delay, period = period) {
                runCatching {
                    if (instance != null) method.invoke(instance) else method.invoke(null)
                }.onFailure { e ->
                    TabooCoreLogger.error("@Schedule error: ${clazz.simpleName}.${method.name} - ${e.message}")
                }
            }
            TabooCoreLogger.info("  @Schedule: ${clazz.simpleName}.${method.name}(async=$async, delay=$delay, period=$period)")
        }
    }

    // ==================== 主扫描逻辑 ====================

    /**
     * 扫描并加载所有插件：在 ENABLE 阶段调用（MC 已启动）。
     */
    @Suppress("UNCHECKED_CAST")
    fun loadAll() {
        val pluginsDir = File("plugins")
        if (!pluginsDir.exists()) return

        val entries = pluginsDir.listFiles { f -> f.extension == "jar" }
            ?.mapNotNull { jar -> readMeta(jar)?.let { jar to it } }
            ?.sortedBy { it.second.priority }
            ?: return

        // 确保关键平台服务已注册（PlatformCommand、PlatformListener、PlatformExecutor）
        ensurePlatformServices()

        // 构建共享 ClassLoader
        val sharedUrls = entries
            .filter { !it.second.isolate }
            .map { it.first.toURI().toURL() }
        if (sharedUrls.isNotEmpty()) {
            sharedLoader = SharedPluginClassLoader(sharedUrls.toTypedArray(), javaClass.classLoader)
        }

        val awokenMap = TabooLib.getAwakenedClasses() as MutableMap<String, Any>
        val classVisitorClass = runCatching { Class.forName("taboolib.common.inject.ClassVisitor") }.getOrNull()
        val platformServiceAnno = loadAnnotation("taboolib.common.platform.PlatformService")
        val serviceMap = getServiceMap()

        for ((jar, meta) in entries) {
            runCatching {
                loadPlugin(jar, meta, awokenMap, classVisitorClass, platformServiceAnno, serviceMap)
            }.onFailure { e ->
                TabooCoreLogger.error("Failed to load plugin: ${meta.name} - ${e.message}", e)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadPlugin(
        jar: File,
        meta: PluginMeta,
        awokenMap: MutableMap<String, Any>,
        classVisitorClass: Class<*>?,
        platformServiceAnno: Class<out Annotation>?,
        serviceMap: MutableMap<String, Any>?
    ) {
        val loader = if (meta.isolate) {
            URLClassLoader(arrayOf(jar.toURI().toURL()), javaClass.classLoader)
        } else {
            sharedLoader!!
        }

        // 扫描 JAR 中的所有类（包括内部类，但排除匿名类如 $1, $2）
        val classes = mutableListOf<Class<*>>()
        JarFile(jar).use { jf ->
            jf.stream()
                .filter { it.name.endsWith(".class") && !isAnonymousClass(it.name) }
                .forEach { entry ->
                    val className = entry.name.replace('/', '.').removeSuffix(".class")
                    runCatching {
                        classes += Class.forName(className, true, loader)
                    }.onFailure { e ->
                        TabooCoreLogger.error("Failed to load class: $className - ${e.message}")
                    }
                }
        }

        val proxies = mutableListOf<DisableProxy>()
        val listeners = mutableListOf<ProxyListener>()
        val activeMethods = mutableListOf<Pair<Method, Any?>>()
        val disableMethods = mutableListOf<Pair<Method, Any?>>()

        // --- @Awake 类级别（实例化 + 注册 awokenMap/serviceMap/ClassVisitor）---
        for (clazz in classes) {
            runCatching {
                if (ghostAnno != null && clazz.isAnnotationPresent(ghostAnno)) return@runCatching
                if (awakeAnno != null && clazz.isAnnotationPresent(awakeAnno)) {
                    val instance = findKotlinInstance(clazz) ?: runCatching {
                        clazz.getDeclaredConstructor().let { it.isAccessible = true; it.newInstance() }
                    }.getOrNull()
                    if (instance != null) {
                        if (classVisitorClass != null && classVisitorClass.isInstance(instance)) {
                            registerClassVisitor(instance)
                        }
                        if (serviceMap != null && platformServiceAnno != null) {
                            for (iface in clazz.interfaces) {
                                if (iface.isAnnotationPresent(platformServiceAnno)) {
                                    serviceMap[iface.name] = instance
                                }
                            }
                        }
                        awokenMap[clazz.name] = instance
                        TabooCoreLogger.info("  @Awake: ${clazz.simpleName}")
                    }
                }
            }.onFailure { e ->
                TabooCoreLogger.error("Failed @Awake class: ${clazz.name} - ${e.message}", e)
            }
        }

        // --- 构建 Visitor 列表 ---
        val visitors = mutableListOf<PluginClassVisitor>()
        if (awakeAnno != null) visitors += AwakeMethodVisitor(awakeAnno, activeMethods, disableMethods)
        visitors += EventVisitor(proxies, listeners)
        visitors += CommandVisitor()
        if (configAnno != null) visitors += ConfigVisitor(configAnno)
        if (scheduleAnno != null) visitors += ScheduleVisitor(scheduleAnno)
        visitors.sortBy { it.priority }

        // --- 执行 Visitor 扫描 ---
        for (clazz in classes) {
            if (ghostAnno != null && clazz.isAnnotationPresent(ghostAnno)) continue
            val instance = findKotlinInstance(clazz)
            for (visitor in visitors) {
                runCatching { visitor.visitStart(clazz, instance) }
                    .onFailure { TabooCoreLogger.error("visitStart error: ${clazz.simpleName} - ${it.message}", it) }
            }
            for (field in clazz.declaredFields) {
                for (visitor in visitors) {
                    runCatching { visitor.visitField(field, clazz, instance) }
                        .onFailure { TabooCoreLogger.error("visitField error: ${clazz.simpleName}.${field.name} - ${it.message}", it) }
                }
            }
            for (method in clazz.declaredMethods) {
                for (visitor in visitors) {
                    runCatching { visitor.visitMethod(method, clazz, instance) }
                        .onFailure { TabooCoreLogger.error("visitMethod error: ${clazz.simpleName}.${method.name} - ${it.message}", it) }
                }
            }
            for (visitor in visitors) {
                runCatching { visitor.visitEnd(clazz, instance) }
                    .onFailure { TabooCoreLogger.error("visitEnd error: ${clazz.simpleName} - ${it.message}", it) }
            }
        }

        // --- 实例化插件主类并触发生命周期 ---
        val mainClass = loader.loadClass(meta.main)
        val constructor = mainClass.getDeclaredConstructor()
        constructor.isAccessible = true
        val plugin = constructor.newInstance() as Plugin
        plugin.onLoad()
        plugin.onEnable()
        loaded += LoadedPlugin(meta, plugin, loader, proxies, listeners, activeMethods, disableMethods)
        TabooCoreLogger.info(
            "Loaded plugin: ${meta.name} v${meta.version}" +
                if (meta.isolate) " (isolated)" else ""
        )
    }

    // ==================== 命令注册 ====================

    private fun registerPluginCommand(clazz: Class<*>, instance: Any?, header: CommandHeader) {
        var mainFunc: (CommandBase.() -> Unit)? = null
        val bodies = mutableListOf<SimpleCommandBody>()

        for (field in clazz.declaredFields) {
            field.isAccessible = true
            val bodyAnno = field.getAnnotation(CommandBody::class.java) ?: continue
            val value = field.get(instance) ?: continue
            when (value) {
                is SimpleCommandMain -> mainFunc = value.func
                is SimpleCommandBody -> {
                    value.name = field.name
                    value.aliases = bodyAnno.aliases
                    value.optional = bodyAnno.optional
                    value.permission = bodyAnno.permission
                    value.permissionDefault = bodyAnno.permissionDefault
                    value.hidden = bodyAnno.hidden
                    bodies += value
                }
            }
        }

        val permissionChildren = bodies
            .filter { it.permission.isNotEmpty() }
            .associate { it.permission to it.permissionDefault }

        command(
            name = header.name,
            aliases = header.aliases.toList(),
            description = header.description,
            usage = header.usage,
            permission = header.permission,
            permissionMessage = header.permissionMessage,
            permissionDefault = header.permissionDefault,
            permissionChildren = permissionChildren,
            newParser = header.newParser,
        ) {
            mainFunc?.invoke(this)
            for (body in bodies) {
                fun register(body: SimpleCommandBody, component: CommandComponent) {
                    component.literal(
                        body.name, *body.aliases,
                        optional = body.optional,
                        permission = body.permission,
                        hidden = body.hidden
                    ) {
                        if (body.children.isEmpty()) body.func(this)
                        else body.children.forEach { child -> register(child, this) }
                    }
                }
                register(body, this)
            }
        }
        TabooCoreLogger.info("  @CommandHeader: /${header.name} (${bodies.size} sub-commands)")
    }

    // ==================== 工具方法 ====================

    private fun findKotlinInstance(clazz: Class<*>): Any? {
        return try {
            val field = clazz.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.get(null)
        } catch (_: NoSuchFieldException) {
            null
        }
    }

    private fun loadAnnotation(name: String): Class<out Annotation>? {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            Class.forName(name) as Class<out Annotation>
        }.getOrNull()
    }

    private fun getServiceMap(): MutableMap<String, Any>? {
        return try {
            val pf = Class.forName("taboolib.common.platform.PlatformFactory")
            val field = pf.getDeclaredField("serviceMap")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field.get(null) as MutableMap<String, Any>
        } catch (_: Throwable) {
            null
        }
    }

    private fun getAwakeLifecycleName(anno: Annotation): String {
        return try {
            val method = anno.javaClass.getMethod("value")
            val result = method.invoke(anno)
            if (result is Enum<*>) result.name else result.toString()
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * 确保关键平台服务已注册到 serviceMap。
     * PlatformFactory 在 CONST 阶段可能因 NoClassDefFoundError 等原因静默失败，
     * 导致 TabooCore 平台服务未注册。
     */
    private fun ensurePlatformServices() {
        val serviceMap = getServiceMap() ?: run {
            TabooCoreLogger.error("ensurePlatformServices: serviceMap is null")
            return
        }

        val services = mapOf(
            "taboolib.common.platform.service.PlatformCommand" to "taboocore.platform.TabooCoreCommand",
            "taboolib.common.platform.service.PlatformIO" to "taboocore.platform.TabooCoreIO",
        )
        for ((serviceInterface, implClass) in services) {
            if (serviceMap.containsKey(serviceInterface)) continue
            runCatching {
                val clazz = Class.forName(implClass)
                val instance = findKotlinInstance(clazz)
                    ?: clazz.getDeclaredConstructor().let { it.isAccessible = true; it.newInstance() }
                serviceMap[serviceInterface] = instance
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val awokenMap = TabooLib.getAwakenedClasses() as MutableMap<String, Any>
                    awokenMap[implClass] = instance
                }
                TabooCoreLogger.info("Registered platform service: ${serviceInterface.substringAfterLast('.')} -> ${implClass.substringAfterLast('.')}")
            }.onFailure { e ->
                TabooCoreLogger.error("Failed to register $serviceInterface: [${e.javaClass.simpleName}] ${e.message}", e)
            }
        }
    }

    private fun registerClassVisitor(instance: Any) {
        try {
            val cvhClass = Class.forName("taboolib.common.inject.ClassVisitorHandler")
            val cvClass = Class.forName("taboolib.common.inject.ClassVisitor")
            val registerMethod = cvhClass.getMethod("register", cvClass)
            registerMethod.invoke(null, instance)
        } catch (e: Throwable) {
            TabooCoreLogger.error("Failed to register ClassVisitor: ${e.message}")
        }
    }

    /** 判断是否为匿名内部类（$1, $2 等），保留命名内部类（$Companion, $SubCommand 等） */
    private fun isAnonymousClass(entryName: String): Boolean {
        val name = entryName.removeSuffix(".class")
        val lastDollar = name.lastIndexOf('$')
        if (lastDollar < 0) return false
        val suffix = name.substring(lastDollar + 1)
        return suffix.all { it.isDigit() }
    }

    // ==================== 生命周期 ====================

    fun activeAll() {
        for (lp in loaded) {
            for ((method, instance) in lp.activeMethods) {
                runCatching {
                    if (instance != null) method.invoke(instance) else method.invoke(null)
                }.onFailure { e ->
                    TabooCoreLogger.error("@Awake(ACTIVE) error: ${method.name} - ${e.message}")
                }
            }
            runCatching { lp.plugin.onActive() }
        }
    }

    fun disableAll() {
        loaded.reversed().forEach { lp ->
            for ((method, instance) in lp.disableMethods) {
                runCatching {
                    if (instance != null) method.invoke(instance) else method.invoke(null)
                }.onFailure { e ->
                    TabooCoreLogger.error("@Awake(DISABLE) error: ${method.name} - ${e.message}")
                }
            }
            runCatching { lp.plugin.onDisable() }
            lp.proxies.forEach { it.enabled = false }
            lp.listeners.forEach { listener ->
                runCatching { taboolib.common.platform.function.unregisterListener(listener) }
            }
        }
        loaded.clear()
        sharedLoader = null
    }

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

class DisableProxy(private val delegate: (InternalEvent) -> Unit) : (InternalEvent) -> Unit {
    @Volatile
    var enabled = true
    override fun invoke(event: InternalEvent) {
        if (enabled) delegate(event)
    }
}

private class SharedPluginClassLoader(
    urls: Array<URL>,
    parent: ClassLoader
) : URLClassLoader(urls, parent)

private data class LoadedPlugin(
    val meta: PluginMeta,
    val plugin: Plugin,
    val loader: ClassLoader,
    val proxies: MutableList<DisableProxy> = mutableListOf(),
    val listeners: MutableList<ProxyListener> = mutableListOf(),
    val activeMethods: MutableList<Pair<Method, Any?>> = mutableListOf(),
    val disableMethods: MutableList<Pair<Method, Any?>> = mutableListOf()
)

data class PluginMeta(
    var name: String = "",
    var main: String = "",
    var version: String = "1.0.0",
    var priority: Int = 0,
    var isolate: Boolean = false
)

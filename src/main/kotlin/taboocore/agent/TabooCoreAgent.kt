package taboocore.agent

import taboocore.bootstrap.MixinBootstrap
import taboocore.bootstrap.TabooLibLoader
import taboolib.common.ClassAppender
import taboolib.common.LifeCycle
import taboolib.common.TabooLib
import java.io.File
import java.lang.instrument.Instrumentation
import java.util.jar.JarFile

/**
 * Java Agent 入口
 * 启动命令：java -javaagent:TabooCore.jar -jar minecraft_server.jar
 */
object TabooCoreAgent {

    @Volatile
    var instrumentation: Instrumentation? = null
        private set

    /** Agent JAR 文件路径，供 MixinClassTransformer 注入到服务端 ClassLoader */
    lateinit var agentJarFile: File
        private set

    /** TabooLibLoader 加载的所有 JAR，需要注入到服务端 ClassLoader */
    val loadedJars = mutableListOf<File>()

    @JvmStatic
    fun premain(args: String?, inst: Instrumentation) {
        instrumentation = inst
        agentJarFile = File(TabooCoreAgent::class.java.protectionDomain.codeSource.location.toURI())

        // 1. 下载并加载 TabooLib 模块
        TabooLibLoader.init()

        // 1.5 通知 ClassAppender 已加载的模块 JAR
        //     TabooLibLoader 使用 Instrumentation 加载模块，不会触发 ClassAppender 回调。
        //     ProjectScanner 依赖 ClassAppender 回调来发现模块中的类（如 EventBus @Awake），
        //     必须在 CONST 生命周期之前完成注册。
        for (jar in loadedJars) {
            runCatching { ClassAppender.addPath(jar.toPath(), false, false) }.onFailure { e ->
                System.err.println("[TabooCore] Failed to register ${jar.name} with ClassAppender: ${e.message}")
            }
        }

        TabooLib.lifeCycle(LifeCycle.CONST)

        // 1.6 确保关键 ClassVisitor 已注册
        //     PlatformFactory 的 CONST 任务可能因 NoClassDefFoundError 等原因静默失败
        ensureClassVisitors()

        // 2. 将 Minecraft 服务端 JAR 加入系统 ClassPath
        loadServerJar(inst)

        // 3. 收集插件 Mixin 配置 + JAR 路径
        val plugins = PluginScanner.scan()

        // 4. INIT 生命周期：Mixin 注入开始前，此阶段可注册额外的 Mixin 配置
        TabooLib.lifeCycle(LifeCycle.INIT)

        // 5. 初始化 Mixin
        MixinBootstrap.init(plugins, inst)

        // 6. ClassAppender
        ClassAppender.addPath(agentJarFile.toPath(), false, false)
        plugins.forEach { ClassAppender.addPath(it.jar.toPath(), false, false) }

        // 7. LOAD 生命周期：Mixin 注入完成，服务器尚未启动
        TabooLib.lifeCycle(LifeCycle.LOAD)

        println("[TabooCore] Agent 启动完成，插件数: ${plugins.size}")
    }

    /**
     * 从 Minecraft 服务端 bundler JAR 中找到实际的服务端代码 JAR，
     * 并通过 Instrumentation 加入系统 ClassPath。
     *
     * 现代 Minecraft 服务端（1.18+）使用 bundler 打包：
     * - server.jar 内含 META-INF/versions.list（格式：hash\tid\tpath）
     * - 首次运行后 bundler 会将服务端 JAR 解压到 versions/ 目录
     */
    private fun loadServerJar(inst: Instrumentation) {
        try {
            val workDir = File(".").canonicalFile
            val serverJar = findServerJar(workDir) ?: return

            // 读取 versions.list 获取服务端代码 JAR 路径
            val jarFile = JarFile(serverJar)
            val versionsList = jarFile.getEntry("META-INF/versions.list")
            if (versionsList == null) {
                // 非 bundler 格式，直接加入 classpath
                inst.appendToSystemClassLoaderSearch(jarFile)
                println("[TabooCore] server JAR added to classpath: ${serverJar.name}")
                return
            }

            val lines = jarFile.getInputStream(versionsList).bufferedReader().readLines()
                .filter { it.isNotBlank() }
            jarFile.close()

            for (line in lines) {
                val parts = line.split("\t")
                if (parts.size < 3) continue
                val extractedPath = File(workDir, "versions/${parts[2]}")
                if (extractedPath.exists()) {
                    inst.appendToSystemClassLoaderSearch(JarFile(extractedPath))
                    println("[TabooCore] server code JAR added to classpath: ${extractedPath.name}")
                } else {
                    System.err.println("[TabooCore] server code JAR not found: $extractedPath")
                }
            }

            // 同时加入 libraries/ 下的所有 JAR
            val libDir = File(workDir, "libraries")
            if (libDir.isDirectory) {
                libDir.walkTopDown()
                    .filter { it.extension == "jar" }
                    .forEach { inst.appendToSystemClassLoaderSearch(JarFile(it)) }
                println("[TabooCore] ${libDir.walkTopDown().filter { it.extension == "jar" }.count()} library JARs added to classpath")
            }
        } catch (e: Exception) {
            System.err.println("[TabooCore] failed to load server JAR: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun findServerJar(workDir: File): File? {
        // 优先从 -jar 参数找
        val cmd = System.getProperty("sun.java.command") ?: ""
        val jarArg = cmd.split(" ").firstOrNull { it.endsWith(".jar") && !it.contains("TabooCore") }
        if (jarArg != null) {
            val f = File(workDir, jarArg)
            if (f.exists()) return f
        }
        // 回退：工作目录下的 server.jar
        val fallback = File(workDir, "server.jar")
        return if (fallback.exists()) fallback else null
    }

    // 支持通过 Attach API 动态附加
    @JvmStatic
    fun agentmain(args: String?, inst: Instrumentation) = premain(args, inst)

    /**
     * 确保 ClassVisitor（尤其是 EventBus）在 CONST 生命周期后已注册。
     * PlatformFactory 的 try-catch 可能静默吞掉错误，导致 propertyMap 为空。
     * 此方法检测并在必要时手动补注册。
     */
    private fun ensureClassVisitors() {
        try {
            val cvh = Class.forName("taboolib.common.inject.ClassVisitorHandler")
            val pmField = cvh.getDeclaredField("propertyMap")
            pmField.isAccessible = true
            val pm = pmField.get(null) as java.util.NavigableMap<*, *>

            var total = 0
            for (group in pm.values) {
                val all = group!!.javaClass.getMethod("getAll").invoke(group) as List<*>
                total += all.size
            }

            if (total > 0) {
                println("[TabooCore] ClassVisitors: $total visitors in ${pm.size} groups")
                return
            }

            println("[TabooCore] WARNING: 0 ClassVisitors after CONST, registering manually")
            val cvClass = Class.forName("taboolib.common.inject.ClassVisitor")
            val registerMethod = cvh.getMethod("register", cvClass)

            // 注册 ClassVisitorAwake（处理 @Awake 方法）
            try {
                val cvaClass = Class.forName("taboolib.common.platform.ClassVisitorAwake")
                val cvaConstructor = cvaClass.getConstructor(LifeCycle::class.java)
                for (lc in LifeCycle.values()) {
                    registerMethod.invoke(null, cvaConstructor.newInstance(lc))
                }
                println("[TabooCore] Registered ClassVisitorAwake x${LifeCycle.values().size}")
            } catch (e: Throwable) {
                System.err.println("[TabooCore] Failed to register ClassVisitorAwake: ${e.message}")
                e.printStackTrace()
            }

            // 注册 EventBus（处理 @SubscribeEvent 方法）
            try {
                val ebClass = Class.forName("taboolib.common.platform.event.EventBus")
                val eb = ebClass.getDeclaredConstructor().newInstance()
                registerMethod.invoke(null, eb)
                println("[TabooCore] Registered EventBus")
            } catch (e: Throwable) {
                System.err.println("[TabooCore] Failed to register EventBus: ${e.message}")
                e.printStackTrace()
            }
        } catch (e: Throwable) {
            System.err.println("[TabooCore] ensureClassVisitors failed: ${e.message}")
            e.printStackTrace()
        }
    }
}

package taboocore.bootstrap

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import taboocore.agent.TabooCoreAgent
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.jar.JarFile

/**
 * 负责下载并加载 TabooLib 模块
 * 完全独立实现，不依赖 TabooLib 任何编译时内容
 *
 * 原生 Kotlin 策略：
 *   在加载 common.jar 之前设置 taboolib.skip-relocate.kotlin=true，
 *   使 PrimitiveSettings.SKIP_KOTLIN_RELOCATE 静态初始化时即为 true。
 *   Kotlin 运行时以原生包名（kotlin.*）加入系统 ClassPath，
 *   所有插件通过父 ClassLoader 委托共享同一份运行时，无需打包或重定向。
 */
object TabooLibLoader {

    private val gson = Gson()
    private val config: TabooCoreConfig by lazy { loadConfig() }

    val repoCentral: String get() = config.repo.central
    val repoTabooLib: String get() = config.repo.taboolib
    val repoReflex: String get() = config.repo.reflex
    val libsDir: File get() = File(config.libsDir)

    val tabooLibVersion: String get() = config.taboolib.version
    val kotlinVersion: String get() = config.kotlin.version
    val coroutinesVersion: String get() = config.kotlin.coroutines

    val installModules: List<String>
        get() = config.taboolib.modules

    val devEnabled: Boolean get() = config.dev.enabled
    val devLocalRepo: String
        get() = config.dev.localRepo.ifBlank {
            // 默认使用标准 Maven 本地仓库
            File(System.getProperty("user.home"), ".m2/repository").absolutePath
        }

    fun init() {
        // ★ 关键：在 common.jar 被加载（PrimitiveSettings 静态初始化）之前设置
        System.setProperty("taboolib.skip-relocate.kotlin", "true")
        System.setProperty("taboolib.skip-relocate.self", "false")
        System.setProperty("taboolib.kotlin.stdlib", config.kotlin.version)
        System.setProperty("taboolib.kotlin.coroutines", config.kotlin.coroutines)
        System.setProperty("taboolib.version", config.taboolib.version)
        System.setProperty("taboolib.repo.central", config.repo.central)
        System.setProperty("taboolib.repo.self", config.repo.taboolib)
        System.setProperty("taboolib.repo.reflex", config.repo.reflex)
        System.setProperty("taboolib.module", config.taboolib.modules.joinToString(","))
        // 让 ClassVisitorHandler 扫描 taboocore.* 包下的类（默认只扫描 taboolib.*）
        System.setProperty("taboolib.group", "taboo")
        if (devEnabled) {
            System.setProperty("taboolib.dev", "true")
        }

        println("开始加载 TabooLib $tabooLibVersion（原生 Kotlin $kotlinVersion）")
        if (devEnabled) {
            println("本地调试模式已启用，本地仓库: $devLocalRepo")
        }

        // 1. 基础依赖：jar-relocator + asm（TabooLib 内部重定向工具链）
        loadBaseDeps()
        // 2. Kotlin 运行时（原生包名，直接加入系统 ClassPath）
        loadKotlin()
        // 3. Reflex 反射库
        loadReflex()
        // 4. TabooLib 核心模块（common 必须最先加载，PrimitiveSettings 在此初始化）
        loadTabooLibCore()
        // 5. 处理各模块的 extra.properties（调用 init 方法）
        //    这一步会触发 ClassVisitorHandler.init()、ProjectScannerKt.init()、
        //    PlatformFactory.init()、RuntimeEnv.init() 等
        processExtraProperties()

        println("TabooLib loaded")
    }

    private fun loadBaseDeps() {
        download(repoCentral, "me.lucko", "jar-relocator", "1.7")
        download(repoCentral, "org.ow2.asm", "asm", "9.8")
        download(repoCentral, "org.ow2.asm", "asm-util", "9.8")
        download(repoCentral, "org.ow2.asm", "asm-commons", "9.8")
    }

    private fun loadKotlin() {
        download(repoCentral, "org.jetbrains.kotlin", "kotlin-stdlib", kotlinVersion)
        download(repoCentral, "org.jetbrains.kotlin", "kotlin-stdlib-jdk8", kotlinVersion)
        download(repoCentral, "org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", coroutinesVersion)
    }

    private fun loadReflex() {
        val reflexJar = resolve(repoReflex, "org.tabooproject.reflex", "reflex", "1.2.2")
        val analyserJar = resolve(repoReflex, "org.tabooproject.reflex", "analyser", "1.2.2")
        // TabooLib 期望 Reflex 在 taboolib.library.reflex 包下，需要重定向
        appendToClassPath(relocate(reflexJar, "org.tabooproject.reflex", "taboolib.library.reflex"))
        appendToClassPath(relocate(analyserJar, "org.tabooproject.reflex", "taboolib.library.reflex"))
    }

    private fun loadTabooLibCore() {
        val g = "io.izzel.taboolib"
        val v = tabooLibVersion
        listOf("common", "common-env", "common-util", "common-legacy-api", "common-platform-api")
            .forEach { download(repoTabooLib, g, it, v) }
        installModules.forEach { download(repoTabooLib, g, it, v) }
    }

    /**
     * 扫描所有已加载模块 JAR 中的 META-INF/taboolib/extra.properties，
     * 按声明调用各模块的 init 方法。
     *
     * 这是 PrimitiveLoader 在正常 TabooLib 启动流程中自动完成的步骤，
     * 但 TabooCore 绕过了 PrimitiveLoader，需要手动补齐。
     *
     * 关键调用：
     *   common-env:          RuntimeEnv.init()
     *   common-util:         ClassVisitorHandler.init(), ProjectScannerKt.init()
     *   common-platform-api: PlatformFactory.init()
     */
    private fun processExtraProperties() {
        val cl = ClassLoader.getSystemClassLoader()
        // Instrumentation.appendToSystemClassLoaderSearch may not make resources
        // discoverable via ClassLoader.getResources(), so iterate the actual JAR files.
        for (jar in TabooCoreAgent.loadedJars) {
            runCatching {
                JarFile(jar).use { jf ->
                    val entry = jf.getJarEntry("META-INF/taboolib/extra.properties") ?: return@use
                    val props = java.util.Properties()
                    jf.getInputStream(entry).use { props.load(it) }
                    val main = props.getProperty("main") ?: return@use
                    val mainMethod = props.getProperty("main-method") ?: return@use
                    for (cls in main.split(",")) {
                        val className = "taboolib.${cls.trim()}"
                        val clazz = Class.forName(className, true, cl)
                        val method = clazz.getDeclaredMethod(mainMethod)
                        method.isAccessible = true
                        method.invoke(null)
                        println("extra.properties -> $className.$mainMethod()")
                    }
                }
            }.onFailure { e ->
                System.err.println("Failed to process extra.properties from ${jar.name}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // ---- 下载与加载 ----

    /** 下载依赖并加载到 classpath */
    fun download(repo: String, group: String, artifact: String, version: String) {
        appendToClassPath(resolve(repo, group, artifact, version))
    }

    /** 仅下载/定位依赖，返回 JAR 文件（不加载到 classpath） */
    fun resolve(repo: String, group: String, artifact: String, version: String): File {
        val path = "${group.replace('.', '/')}/$artifact/$version/$artifact-$version.jar"
        val jar = File(libsDir, path)
        val sha = File(libsDir, "$path.sha1")

        // 本地调试模式：优先从本地 Maven 仓库查找
        if (devEnabled) {
            val localJar = File(devLocalRepo, path)
            if (localJar.exists() && localJar.length() > 0) {
                println("从本地仓库加载 $group:$artifact:$version")
                return localJar
            }
        }

        if (!validate(jar, sha)) {
            println("正在下载 $group:$artifact:$version")
            jar.parentFile.mkdirs()
            var lastError: Throwable? = null
            for (attempt in 1..3) {
                runCatching {
                    fetch(URL("$repo/$path"), jar)
                    fetch(URL("$repo/$path.sha1"), sha)
                }.onSuccess {
                    lastError = null
                }.onFailure { e ->
                    lastError = e
                    if (attempt < 3) {
                        System.err.println("下载失败（第 $attempt 次），重试中... $group:$artifact:$version - ${e.message}")
                    }
                }
                if (lastError == null) break
            }
            if (lastError != null) {
                throw RuntimeException("下载失败 $group:$artifact:$version（已重试 3 次）", lastError)
            }
            if (!validate(jar, sha)) {
                throw RuntimeException("校验失败 $group:$artifact:$version，文件可能损坏")
            }
        }

        return jar
    }

    /**
     * 使用 jar-relocator 重定向 JAR 中的包名
     * jar-relocator 是运行时加载的，通过反射调用
     */
    private fun relocate(input: File, pattern: String, relocatedPattern: String): File {
        val output = File(input.parentFile, "${input.nameWithoutExtension}-relocated.jar")
        if (output.exists() && output.length() > 0 && output.lastModified() >= input.lastModified()) {
            return output
        }
        val cl = ClassLoader.getSystemClassLoader()
        val relocationClass = cl.loadClass("me.lucko.jarrelocator.Relocation")
        val relocatorClass = cl.loadClass("me.lucko.jarrelocator.JarRelocator")
        val relocation = relocationClass.getConstructor(String::class.java, String::class.java)
            .newInstance(pattern, relocatedPattern)
        val relocator = relocatorClass.getConstructor(File::class.java, File::class.java, java.util.Collection::class.java)
            .newInstance(input, output, listOf(relocation))
        relocatorClass.getMethod("run").invoke(relocator)
        return output
    }

    private fun fetch(url: URL, dest: File) {
        val conn = url.openConnection()
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.getInputStream().use { Files.copy(it, dest.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }

    private fun validate(jar: File, sha1: File): Boolean {
        if (!jar.exists() || !sha1.exists() || jar.length() == 0L) return false
        val expected = sha1.readText().trim().lowercase().substringBefore(" ")
        val actual = MessageDigest.getInstance("SHA-1")
            .digest(jar.readBytes())
            .joinToString("") { "%02x".format(it) }
        return expected == actual
    }

    fun appendToClassPath(jar: File) {
        runCatching {
            TabooCoreAgent.instrumentation?.appendToSystemClassLoaderSearch(JarFile(jar))
                ?: error("Instrumentation 未初始化")
            TabooCoreAgent.loadedJars += jar
        }.onFailure { e ->
            System.err.println("无法加载 ${jar.name}: ${e.message}")
        }
    }

    // ---- 配置加载 ----

    private fun loadConfig(): TabooCoreConfig {
        // 1. 从 JAR 资源读取默认配置
        val defaultJson = TabooLibLoader::class.java.getResourceAsStream("/taboocore.json")?.use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { gson.fromJson(it, JsonObject::class.java) }
        } ?: JsonObject()

        // 2. 从服务端根目录读取覆盖配置（字段级合并）
        val overrideFile = File("taboocore.json")
        if (overrideFile.exists()) {
            val overrideJson = overrideFile.reader(Charsets.UTF_8).use { gson.fromJson(it, JsonObject::class.java) }
            deepMerge(defaultJson, overrideJson)
        }

        return gson.fromJson(defaultJson, TabooCoreConfig::class.java)
    }

    /** 将 override 的字段递归合并到 base 中 */
    private fun deepMerge(base: JsonObject, override: JsonObject) {
        for ((key, value) in override.entrySet()) {
            if (value.isJsonObject && base.has(key) && base[key].isJsonObject) {
                deepMerge(base[key].asJsonObject, value.asJsonObject)
            } else {
                base.add(key, value)
            }
        }
    }

    // ---- 配置数据类 ----

    data class TabooCoreConfig(
        val taboolib: TabooLibConfig = TabooLibConfig(),
        val kotlin: KotlinConfig = KotlinConfig(),
        val repo: RepoConfig = RepoConfig(),
        @SerializedName("libs-dir") val libsDir: String = "libraries",
        val dev: DevConfig = DevConfig()
    )

    data class TabooLibConfig(
        val version: String = "6.2.4-local-dev",
        val modules: List<String> = emptyList()
    )

    data class KotlinConfig(
        val version: String = "2.3.10",
        val coroutines: String = "1.10.2"
    )

    data class RepoConfig(
        val central: String = "https://maven.aliyun.com/repository/central",
        val taboolib: String = "https://repo.tabooproject.org/repository/releases",
        val reflex: String = "https://repo.tabooproject.org/repository/releases"
    )

    data class DevConfig(
        val enabled: Boolean = false,
        @SerializedName("local-repo") val localRepo: String = ""
    )
}

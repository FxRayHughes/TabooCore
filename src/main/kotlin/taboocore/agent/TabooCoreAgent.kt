package taboocore.agent

import taboocore.bootstrap.MixinBootstrap
import taboocore.bootstrap.TabooLibLoader
import taboolib.common.ClassAppender
import java.io.File
import java.lang.instrument.Instrumentation

/**
 * Java Agent 入口
 * 启动命令：java -javaagent:TabooCore.jar -jar minecraft_server.jar
 *
 * 启动顺序：
 * 1. 下载并加载 TabooLib 模块（加入系统 ClassPath）
 * 2. 扫描 plugins/ 收集 Mixin 配置 + JAR 路径
 * 3. 初始化 Mixin（TabooCore 先注册，插件后注册）
 * 4. ClassAppender.addPath：TabooCore JAR 先扫描，插件 JAR 后扫描
 */
object TabooCoreAgent {

    /** 保存 Instrumentation 引用，供 TabooLibLoader 使用 */
    @Volatile
    var instrumentation: Instrumentation? = null
        private set

    @JvmStatic
    fun premain(args: String?, inst: Instrumentation) {
        instrumentation = inst

        // 1. 下载并加载 TabooLib 模块
        TabooLibLoader.init()

        // 2. 收集插件 Mixin 配置 + JAR 路径
        val plugins = PluginScanner.scan()

        // 3. 初始化 Mixin：TabooCore 先注册，插件后注册
        MixinBootstrap.init(plugins, inst)

        // 4. ClassAppender：TabooCore JAR 先扫描，触发 @Awake 类注册
        val agentJar = File(TabooCoreAgent::class.java.protectionDomain.codeSource.location.toURI())
        ClassAppender.addPath(agentJar.toPath(), false, false)
        plugins.forEach { ClassAppender.addPath(it.jar.toPath(), false, false) }

        println("[TabooCore] Agent 启动完成，插件数: ${plugins.size}")
    }

    // 支持通过 Attach API 动态附加
    @JvmStatic
    fun agentmain(args: String?, inst: Instrumentation) = premain(args, inst)
}

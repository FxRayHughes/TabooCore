package taboocore.bootstrap

import org.spongepowered.asm.launch.MixinBootstrap as SpongeMixinBootstrap
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.Mixins
import taboocore.agent.TabooCoreAgent
import taboocore.agent.PluginInfo
import org.spongepowered.asm.mixin.extensibility.IMixinConfigSource
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.invoke.VarHandle
import java.security.ProtectionDomain

object MixinBootstrap {

    fun init(plugins: List<PluginInfo>, inst: Instrumentation) {
        // 注入缺失的兼容级别枚举（Mixin 0.8.x 最高只到 JAVA_18）
        injectCompatibilityLevels()

        // 初始化 Mixin 框架
        SpongeMixinBootstrap.init()

        // TabooCore 自身的核心 Mixin 先注册
        Mixins.addConfiguration("taboocore.mixins.json", null as IMixinConfigSource?)

        // 再注册各插件声明的 Mixin 配置（跳过空配置）
        plugins.forEach { plugin ->
            plugin.mixinConfigs.forEach { config ->
                // 检查配置文件是否包含有效的 mixin 类
                val resource = Thread.currentThread().contextClassLoader?.getResourceAsStream(config)
                if (resource != null) {
                    val content = resource.bufferedReader().use { it.readText() }
                    val json = com.google.gson.JsonParser.parseString(content).asJsonObject
                    val mixins = json.getAsJsonArray("mixins")
                    if (mixins != null && mixins.size() > 0) {
                        Mixins.addConfiguration(config, null as IMixinConfigSource?)
                        println("[TabooCore] 注册 Mixin 配置: $config")
                    } else {
                        println("[TabooCore] 跳过空 Mixin 配置: $config")
                    }
                } else {
                    System.err.println("[TabooCore] 未找到 Mixin 配置: $config")
                }
            }
        }

        // 完成 Mixin 引导：从 PREINIT → INIT → DEFAULT
        completeMixinBootstrap()

        // 设置为服务端环境
        MixinEnvironment.getDefaultEnvironment().side = MixinEnvironment.Side.SERVER

        // 将 Mixin 字节码转换器注入到 Instrumentation
        inst.addTransformer(MixinClassTransformer(), true)
        println("[TabooCore/Mixin] ClassFileTransformer registered")
    }

    /**
     * 通过反射将 Mixin 环境从 PREINIT 推进到 DEFAULT 阶段
     * 只有在 DEFAULT 阶段，Mixin 才会解析配置中的 targets 并应用字节码转换
     * 参考 Ignite: space.vectrix.ignite.launch.ember.Ember#completeMixinBootstrap
     */
    private fun completeMixinBootstrap() {
        try {
            val gotoPhase = MixinEnvironment::class.java.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase::class.java)
            gotoPhase.isAccessible = true
            gotoPhase.invoke(null, MixinEnvironment.Phase.INIT)
            gotoPhase.invoke(null, MixinEnvironment.Phase.DEFAULT)
            println("[TabooCore/Mixin] phase transition complete: DEFAULT")
        } catch (e: Exception) {
            System.err.println("[TabooCore/Mixin] phase transition failed: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 通过 MethodHandles + jdk.internal.misc.Unsafe 向 CompatibilityLevel 枚举注入高版本 Java 支持
     * Mixin 0.8.x 最高只定义到 JAVA_18，需要动态添加 JAVA_19 ~ JAVA_25
     *
     * MethodHandles 用于：构造枚举实例、读取 $VALUES
     * jdk.internal.misc.Unsafe 用于：写入 final static 字段（VarHandle 无法做到）
     */
    private fun injectCompatibilityLevels() {
        runCatching {
            val enumClass = MixinEnvironment.CompatibilityLevel::class.java

            // 通过 Instrumentation 开放 java.base 模块
            val inst = TabooCoreAgent.instrumentation!!
            val javaBase = Class::class.java.module
            val thisModule = MixinBootstrap::class.java.module
            inst.redefineModule(javaBase, emptySet(), emptyMap(),
                mapOf("java.lang" to setOf(thisModule), "jdk.internal.misc" to setOf(thisModule)),
                emptySet(), emptyMap())

            // 获取 jdk.internal.misc.Unsafe 实例（用于写入 final 字段）
            val internalUnsafeClass = Class.forName("jdk.internal.misc.Unsafe")
            val getUnsafe = internalUnsafeClass.getMethod("getUnsafe")
            val unsafe = getUnsafe.invoke(null)
            val objectFieldOffset = internalUnsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field::class.java)
            val staticFieldOffset = internalUnsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
            val staticFieldBase = internalUnsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java)
            val putReference = internalUnsafeClass.getMethod("putReference", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)

            // 获取 CompatibilityLevel 的 private lookup
            val lookup = MethodHandles.privateLookupIn(enumClass, MethodHandles.lookup())

            // 枚举构造器: (String name, int ordinal, int ver, int classVersion, int languageFeatures)
            val ctorType = MethodType.methodType(
                Void.TYPE,
                String::class.java, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            val ctor = lookup.findConstructor(enumClass, ctorType)

            // 读取当前 $VALUES
            val valuesArrayType = java.lang.reflect.Array.newInstance(enumClass, 0).javaClass
            val valuesHandle: VarHandle = lookup.findStaticVarHandle(enumClass, "\$VALUES", valuesArrayType)
            val currentValues = valuesHandle.get() as Array<*>

            // 收集需要注入的版本
            val existingNames = currentValues.map { (it as Enum<*>).name }.toSet()
            val newEntries = mutableListOf<Any>()
            for (ver in 19..25) {
                val name = "JAVA_$ver"
                if (name in existingNames) continue
                val ordinal = currentValues.size + newEntries.size
                val classVersion = 44 + ver
                newEntries += ctor.invoke(name, ordinal, ver, classVersion, 127)
            }

            if (newEntries.isEmpty()) return@runCatching

            // 构建新的 $VALUES 数组
            val newValues = java.lang.reflect.Array.newInstance(enumClass, currentValues.size + newEntries.size)
            System.arraycopy(currentValues, 0, newValues, 0, currentValues.size)
            for (i in newEntries.indices) {
                java.lang.reflect.Array.set(newValues, currentValues.size + i, newEntries[i])
            }

            // 用 jdk.internal.misc.Unsafe 写入 final static $VALUES 字段
            val valuesField = enumClass.getDeclaredField("\$VALUES")
            val base = staticFieldBase.invoke(unsafe, valuesField)
            val offset = staticFieldOffset.invoke(unsafe, valuesField) as Long
            putReference.invoke(unsafe, base, offset, newValues)

            // 清除 Class 内部的枚举缓存（实例字段），使 Enum.valueOf() 能找到新值
            runCatching {
                val field = Class::class.java.getDeclaredField("enumConstantDirectory")
                val o = objectFieldOffset.invoke(unsafe, field) as Long
                putReference.invoke(unsafe, enumClass, o, null)
            }
            runCatching {
                val field = Class::class.java.getDeclaredField("enumConstants")
                val o = objectFieldOffset.invoke(unsafe, field) as Long
                putReference.invoke(unsafe, enumClass, o, null)
            }

            println("[TabooCore] 已注入兼容级别: ${newEntries.joinToString { (it as Enum<*>).name }}")
        }.onFailure { e ->
            System.err.println("[TabooCore] 注入 CompatibilityLevel 失败: ${e.message}")
            e.printStackTrace()
        }
    }

    private class MixinClassTransformer : ClassFileTransformer {
        @Volatile private var injected = false

        override fun transform(
            loader: ClassLoader?,
            className: String,
            classBeingRedefined: Class<*>?,
            protectionDomain: ProtectionDomain?,
            classfileBuffer: ByteArray
        ): ByteArray? {
            // Minecraft bundler 创建 URLClassLoader(urls, PlatformClassLoader)，
            // 绕过 SystemClassLoader，导致 agent JAR 上的类不可见。
            // 首次遇到 URLClassLoader 时，通过反射将 agent JAR + 插件 JAR 注入进去。
            if (!injected && loader is java.net.URLClassLoader) {
                injected = true
                injectToServerClassLoader(loader)
            }
            val transformer = MixinServiceTabooCore.transformer ?: return null
            val canonicalName = className.replace('/', '.')
            return try {
                transformer.transformClassBytes(null, canonicalName, classfileBuffer)
            } catch (e: Throwable) {
                println("[TabooCore/Mixin] transform error: $canonicalName - ${e.javaClass.name}: ${e.message}")
                e.printStackTrace()
                null
            }
        }

        private fun injectToServerClassLoader(loader: java.net.URLClassLoader) {
            try {
                val inst = taboocore.agent.TabooCoreAgent.instrumentation!!
                // 打开 java.net 模块，允许反射访问 URLClassLoader.addURL
                val javaBase = java.net.URLClassLoader::class.java.module
                val thisModule = MixinBootstrap::class.java.module
                inst.redefineModule(javaBase, emptySet(), emptyMap(),
                    mapOf("java.net" to setOf(thisModule)), emptySet(), emptyMap())

                val addURL = java.net.URLClassLoader::class.java.getDeclaredMethod("addURL", java.net.URL::class.java)
                addURL.isAccessible = true
                // 注入 agent JAR（含 Mixin 运行时 + TabooCore 类）
                addURL.invoke(loader, taboocore.agent.TabooCoreAgent.agentJarFile.toURI().toURL())
                // 注入 TabooLibLoader 加载的所有 JAR（TabooLib 模块、Kotlin、Reflex 等）
                taboocore.agent.TabooCoreAgent.loadedJars.forEach {
                    addURL.invoke(loader, it.toURI().toURL())
                }
                // 注入 TabooLib 运行时 JAR（libraries/ 下已下载的模块）
                val libDir = java.io.File("libraries")
                if (libDir.isDirectory) {
                    libDir.walkTopDown().filter { it.extension == "jar" }.forEach {
                        addURL.invoke(loader, it.toURI().toURL())
                    }
                }
                println("[TabooCore/Mixin] injected ${taboocore.agent.TabooCoreAgent.loadedJars.size + 1} JARs into server classloader")
            } catch (e: Exception) {
                System.err.println("[TabooCore/Mixin] failed to inject into classloader: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

package taboocore.bootstrap

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual
import org.spongepowered.asm.launch.platform.container.IContainerHandle
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.transformer.IMixinTransformer
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory
import org.spongepowered.asm.service.*
import org.spongepowered.asm.util.ReEntranceLock
import java.io.InputStream
import java.net.URL

/**
 * 为 TabooCore（Java Agent 环境）提供的 Mixin 服务实现
 * 使用系统 ClassLoader 进行类加载和字节码读取
 */
class MixinServiceTabooCore :
    IMixinService,
    IClassProvider,
    IClassBytecodeProvider,
    ITransformerProvider,
    IClassTracker {

    private val lock = ReEntranceLock(1)
    private val container = ContainerHandleVirtual("TabooCore")

    companion object {
        /** Mixin transformer 实例，供 MixinClassTransformer 使用 */
        @JvmStatic
        var transformer: IMixinTransformer? = null
            private set

        @JvmStatic
        fun hasFactory(): Boolean = transformer != null
    }

    // ---- IMixinService ----

    override fun getName(): String = "TabooCore"
    override fun isValid(): Boolean = true
    override fun prepare() {}
    override fun getInitialPhase(): MixinEnvironment.Phase = MixinEnvironment.Phase.PREINIT

    override fun offer(internal: IMixinInternal) {
        if (internal is IMixinTransformerFactory) {
            transformer = internal.createTransformer()
            println("[TabooCore/Mixin] transformer created: ${transformer!!.javaClass.name}")
        }
    }

    override fun init() {}
    override fun beginPhase() {}
    override fun checkEnv(bootSource: Any) {}

    override fun getSideName(): String = "SERVER"

    override fun getReEntranceLock(): ReEntranceLock = lock

    override fun getClassProvider(): IClassProvider = this
    override fun getBytecodeProvider(): IClassBytecodeProvider = this
    override fun getTransformerProvider(): ITransformerProvider = this
    override fun getClassTracker(): IClassTracker = this
    override fun getAuditTrail(): IMixinAuditTrail? = null

    override fun getPlatformAgents(): Collection<String> = emptyList()
    override fun getPrimaryContainer(): IContainerHandle = container
    override fun getMixinContainers(): Collection<IContainerHandle> = emptyList()

    override fun getResourceAsStream(name: String): InputStream? =
        ClassLoader.getSystemClassLoader().getResourceAsStream(name)

    override fun getMinCompatibilityLevel(): MixinEnvironment.CompatibilityLevel =
        MixinEnvironment.CompatibilityLevel.JAVA_8

    override fun getMaxCompatibilityLevel(): MixinEnvironment.CompatibilityLevel? {
        return runCatching {
            MixinEnvironment.CompatibilityLevel.valueOf("JAVA_25")
        }.getOrNull()
    }

    override fun getLogger(name: String): org.spongepowered.asm.logging.ILogger =
        org.spongepowered.asm.logging.LoggerAdapterDefault(name)

    // ---- IClassProvider ----

    @Deprecated("Deprecated in Mixin API")
    override fun getClassPath(): Array<URL> = arrayOf()

    override fun findClass(name: String): Class<*> =
        Class.forName(name, true, ClassLoader.getSystemClassLoader())

    override fun findClass(name: String, initialize: Boolean): Class<*> =
        Class.forName(name, initialize, ClassLoader.getSystemClassLoader())

    override fun findAgentClass(name: String, initialize: Boolean): Class<*> =
        Class.forName(name, initialize, MixinServiceTabooCore::class.java.classLoader)

    // ---- IClassBytecodeProvider ----

    override fun getClassNode(name: String): ClassNode = getClassNode(name, true)

    override fun getClassNode(name: String, runTransformers: Boolean): ClassNode =
        getClassNode(name, runTransformers, 0)

    override fun getClassNode(name: String, runTransformers: Boolean, readerFlags: Int): ClassNode {
        val resourcePath = name.replace('.', '/') + ".class"
        val stream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourcePath)
        if (stream == null) {
            println("[TabooCore/Mixin] getClassNode FAILED: $name")
            throw ClassNotFoundException(name)
        }
        val bytes = stream.use { it.readBytes() }
        val node = ClassNode()
        ClassReader(bytes).accept(node, readerFlags)
        return node
    }

    // ---- ITransformerProvider ----

    override fun getTransformers(): Collection<ITransformer> = emptyList()
    override fun getDelegatedTransformers(): Collection<ITransformer> = emptyList()
    override fun addTransformerExclusion(name: String) {}

    // ---- IClassTracker ----

    override fun registerInvalidClass(name: String) {}
    override fun isClassLoaded(name: String): Boolean = false
    override fun getClassRestrictions(name: String): String = ""
}

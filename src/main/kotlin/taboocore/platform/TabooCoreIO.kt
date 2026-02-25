package taboocore.platform

import taboocore.util.TabooCoreLogger
import taboolib.common.Inject
import taboolib.common.platform.Awake
import taboolib.common.platform.Platform
import taboolib.common.platform.PlatformSide
import taboolib.common.platform.service.PlatformIO
import java.io.File

@Awake
@Inject
@PlatformSide(Platform.TABOOCORE)
class TabooCoreIO : PlatformIO {

    override val pluginId: String get() = "TabooCore"
    override val pluginVersion: String get() = "1.0.0"
    override val isPrimaryThread: Boolean
        get() = Thread.currentThread().name.startsWith("Server thread")

    override fun <T> server(): T {
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    override fun info(vararg message: Any?) {
        message.forEach { TabooCoreLogger.info(it.toString()) }
    }

    override fun severe(vararg message: Any?) {
        message.forEach { TabooCoreLogger.error(it.toString()) }
    }

    override fun warning(vararg message: Any?) {
        message.forEach { TabooCoreLogger.warn(it.toString()) }
    }

    override fun releaseResourceFile(source: String, target: String, replace: Boolean): File {
        error("TabooCore does not support releaseResourceFile")
    }

    override fun getJarFile(): File {
        return File(TabooCoreIO::class.java.protectionDomain.codeSource.location.toURI())
    }

    override fun getDataFolder(): File {
        return File("plugins/TabooCore").also { it.mkdirs() }
    }

    override fun getPlatformData(): Map<String, Any> {
        return mapOf("platformType" to "TABOOCORE")
    }
}

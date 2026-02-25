package taboocore.util

import com.mojang.logging.LogUtils
import taboocore.console.MinecraftColor
import taboocore.console.TabooCoreConsole

/**
 * TabooCore 统一日志工具
 *
 * - 服务端启动前：通过 JLine printAbove 输出（如果可用），否则 println
 * - 服务端启动后：切换到 SLF4J logger
 * - 支持 Minecraft §/& 颜色代码
 */
object TabooCoreLogger {

    @Volatile
    private var serverStarted = false

    private val logger by lazy { LogUtils.getLogger() }

    fun markServerStarted() {
        serverStarted = true
    }

    fun info(message: String) {
        if (serverStarted) {
            logger.info(message)
        } else {
            printToConsole("[TabooCore] $message")
        }
    }

    fun warn(message: String) {
        if (serverStarted) {
            logger.warn(message)
        } else {
            printToConsole("[TabooCore] [WARN] $message", MinecraftColor.YELLOW)
        }
    }

    fun error(message: String, cause: Throwable? = null) {
        if (serverStarted) {
            if (cause != null) logger.error(message, cause)
            else logger.error(message)
        } else {
            printToConsole("[TabooCore] [ERROR] $message", MinecraftColor.RED)
            cause?.printStackTrace()
        }
    }

    private fun printToConsole(text: String, levelColor: String? = null) {
        val ansi = MinecraftColor.toAnsi(text)
        val colored = if (levelColor != null) "$levelColor$ansi${MinecraftColor.RESET}" else ansi
        val reader = TabooCoreConsole.lineReader
        if (reader != null) {
            reader.printAbove(colored)
        } else {
            TabooCoreConsole.originalOut.println(text)
        }
    }
}

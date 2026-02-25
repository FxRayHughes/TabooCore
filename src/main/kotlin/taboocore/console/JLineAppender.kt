package taboocore.console

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginAttribute
import org.apache.logging.log4j.core.config.plugins.PluginFactory
import org.apache.logging.log4j.core.layout.PatternLayout

@Plugin(name = "JLineAppender", category = "Core", elementType = "appender")
class JLineAppender private constructor(name: String, layout: PatternLayout) :
    AbstractAppender(name, null, layout, true, Property.EMPTY_ARRAY) {

    /** 命令错误相关的消息模式（MC 以 INFO 级别记录，但应显示为红色） */
    private val ERROR_PATTERNS = listOf(
        "Unknown or incomplete command",
        "<--[HERE]",
    )

    override fun append(event: LogEvent) {
        val msg = (layout as PatternLayout).toSerializable(event)
        val ansi = MinecraftColor.toAnsi(msg.trimEnd())
        val rawMessage = event.message.formattedMessage
        // 按日志级别上色，同时检测命令错误消息
        val colored = when {
            event.level.isMoreSpecificThan(Level.ERROR) ->
                "${MinecraftColor.RED}$ansi${MinecraftColor.RESET}"
            event.level.isMoreSpecificThan(Level.WARN) ->
                "${MinecraftColor.YELLOW}$ansi${MinecraftColor.RESET}"
            ERROR_PATTERNS.any { rawMessage.contains(it) } ->
                "${MinecraftColor.RED}$ansi${MinecraftColor.RESET}"
            else -> ansi
        }
        val reader = TabooCoreConsole.lineReader
        if (reader != null) {
            reader.printAbove(colored)
        } else {
            TabooCoreConsole.originalOut.print(msg)
        }
    }

    companion object {
        @JvmStatic
        @PluginFactory
        fun createAppender(
            @PluginAttribute("name") name: String,
        ): JLineAppender {
            val layout = PatternLayout.createDefaultLayout()
            return JLineAppender(name, layout)
        }

        fun install() {
            val ctx = LogManager.getContext(false) as org.apache.logging.log4j.core.LoggerContext
            val config = ctx.configuration
            val rootLoggerConfig = config.rootLogger

            // Remove ALL existing appenders from root logger, then only add ours + File
            val toRemove = rootLoggerConfig.appenders.entries
                .filter { it.value is ConsoleAppender }
                .map { it.key }
            toRemove.forEach { rootLoggerConfig.removeAppender(it) }

            // Create and start JLineAppender
            val layout = PatternLayout.newBuilder()
                .withPattern("[%d{HH:mm:ss}] [%t/%level]: %msg{nolookups}%n")
                .build()
            val appender = JLineAppender("JLine", layout)
            appender.start()
            config.addAppender(appender)
            rootLoggerConfig.addAppender(appender, Level.ALL, null)
            ctx.updateLoggers()
        }
    }
}

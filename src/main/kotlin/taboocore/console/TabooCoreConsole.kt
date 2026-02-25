package taboocore.console

import net.minecraft.server.MinecraftServer
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.EndOfFileException
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

object TabooCoreConsole {

    @Volatile
    var lineReader: LineReader? = null
        private set

    @Volatile
    var terminal: Terminal? = null
        private set

    private var pipedOut: PipedOutputStream? = null

    /** 保存原始 stdout，供 fallback 使用 */
    @Volatile
    var originalOut: PrintStream = System.out
        private set

    /**
     * 补全回调：输入 "fullInput" → 返回 [rangeStart, text1, text2, ...]
     * 第一个元素是 Brigadier suggestion range 的起始位置（字符串形式），
     * 后续元素是补全候选文本。
     */
    @Volatile
    var completionProvider: java.util.function.Function<String, List<String>>? = null

    fun init() {
        try {
            originalOut = System.out

            // Windows: 通过 kernel32 API 设置控制台代码页为 UTF-8
            // 需要在 JVM 启动参数中添加 --enable-native-access=ALL-UNNAMED 以消除 WARNING
            if (System.getProperty("os.name", "").contains("Windows", ignoreCase = true)) {
                setWindowsConsoleUtf8()
                // 控制台代码页改为 UTF-8 后，重建 System.out/err 使其编码匹配
                val utf8Out = PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8)
                val utf8Err = PrintStream(FileOutputStream(FileDescriptor.err), true, Charsets.UTF_8)
                System.setOut(utf8Out)
                System.setErr(utf8Err)
                originalOut = utf8Out
            }

            val term = TerminalBuilder.builder()
                .system(true)
                .encoding(Charsets.UTF_8)
                .build()
            terminal = term

            // 不替换 System.out/System.err！
            // MC Bootstrap 会将它们替换为 LoggedPrintStream → Log4j2
            // 我们的 JLineAppender 替换 Log4j2 ConsoleAppender 即可

            val pipedIn = PipedInputStream()
            val out = PipedOutputStream(pipedIn)
            pipedOut = out

            System.setIn(pipedIn)

            val reader = LineReaderBuilder.builder()
                .terminal(term)
                .completer(ConsoleCompleter())
                .build()
            // 禁止 Tab 键插入制表符，仅用于补全
            reader.option(LineReader.Option.INSERT_TAB, false)
            lineReader = reader

            // 替换 JUL handler
            setupJulHandler()

            // Input thread
            val thread = Thread({
                try {
                    while (true) {
                        val currentReader = lineReader ?: break
                        val line = try {
                            currentReader.readLine("> ")
                        } catch (_: UserInterruptException) {
                            continue
                        } catch (_: EndOfFileException) {
                            break
                        }
                        if (line.isNullOrBlank()) continue
                        out.write((line + "\n").toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                } catch (e: Exception) {
                    originalOut.println("Console input thread error: ${e.message}")
                }
            }, "TabooCore Console Input")
            thread.isDaemon = true
            thread.start()

            printAbove("JLine console initialized")
        } catch (e: Exception) {
            originalOut.println("Failed to initialize JLine console: ${e.message}")
            e.printStackTrace(originalOut)
        }
    }

    fun setupCompleter(server: MinecraftServer) {
        val provider = java.util.function.Function<String, List<String>> { input ->
            try {
                val source = server.createCommandSourceStack()
                val dispatcher = server.commands.dispatcher
                val parseResults = dispatcher.parse(input, source)
                val suggestions = dispatcher.getCompletionSuggestions(parseResults, input.length).join()
                if (suggestions.list.isEmpty()) {
                    emptyList()
                } else {
                    // 第一个元素: range start 位置
                    val rangeStart = suggestions.range.start.toString()
                    val result = mutableListOf(rangeStart)
                    suggestions.list.forEach { result.add(it.text) }
                    result
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        // 设置当前 classloader 的实例
        this.completionProvider = provider
        // 同时通过反射设置 system classloader 的实例（跨 classloader 场景）
        try {
            val systemClass = ClassLoader.getSystemClassLoader()
                .loadClass("taboocore.console.TabooCoreConsole")
            if (systemClass !== this::class.java) {
                val instance = systemClass.getField("INSTANCE").get(null)
                val field = systemClass.getDeclaredField("completionProvider")
                field.isAccessible = true
                field.set(instance, provider)
            }
        } catch (_: Exception) {
        }
        printAbove("Console completer registered")
    }

    fun setupLog4j2Appender() {
        try {
            JLineAppender.install()
            printAbove("JLine Log4j2 appender installed")
        } catch (e: Exception) {
            printAbove("Failed to install JLine appender: ${e.message}")
        }
    }

    /** 直接通过 JLine 输出一行，不经过 System.out */
    fun printAbove(text: String) {
        val reader = lineReader
        if (reader != null) {
            reader.printAbove(MinecraftColor.toAnsi(text))
        } else {
            originalOut.println(text)
        }
    }

    /**
     * 通过 FFM API 调用 kernel32 SetConsoleOutputCP(65001) / SetConsoleCP(65001)
     * 直接修改当前进程的控制台代码页为 UTF-8
     */
    private fun setWindowsConsoleUtf8() {
        try {
            val linker = Linker.nativeLinker()
            val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
            val descriptor = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
            val setOutputCP = linker.downcallHandle(
                kernel32.find("SetConsoleOutputCP").orElseThrow(), descriptor
            )
            val setInputCP = linker.downcallHandle(
                kernel32.find("SetConsoleCP").orElseThrow(), descriptor
            )
            setOutputCP.invoke(65001)
            setInputCP.invoke(65001)
        } catch (e: Exception) {
            originalOut.println("Failed to set Windows console UTF-8: ${e.message}")
        }
    }

    private fun setupJulHandler() {
        val rootLogger = Logger.getLogger("")
        for (handler in rootLogger.handlers) {
            rootLogger.removeHandler(handler)
        }
        val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        rootLogger.addHandler(object : Handler() {
            override fun publish(record: LogRecord) {
                if (!isLoggable(record)) return
                val time = java.time.LocalTime.now().format(fmt)
                val level = record.level.name
                val msg = record.message ?: return
                val formatted = "[$time] [${record.loggerName}/$level]: $msg"
                val ansi = MinecraftColor.toAnsi(formatted)
                val colored = when {
                    record.level.intValue() >= java.util.logging.Level.SEVERE.intValue() ->
                        "${MinecraftColor.RED}$ansi${MinecraftColor.RESET}"
                    record.level.intValue() >= java.util.logging.Level.WARNING.intValue() ->
                        "${MinecraftColor.YELLOW}$ansi${MinecraftColor.RESET}"
                    else -> ansi
                }
                val reader = lineReader
                if (reader != null) {
                    reader.printAbove(colored)
                } else {
                    originalOut.println(formatted)
                }
            }

            override fun flush() {}
            override fun close() {}
        })
    }

}

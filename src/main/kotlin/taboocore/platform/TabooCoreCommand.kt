package taboocore.platform

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import taboocore.util.ServerUtils
import taboolib.common.Inject
import taboolib.common.platform.Awake
import taboolib.common.platform.Platform
import taboolib.common.platform.PlatformSide
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandCompleter
import taboolib.common.platform.command.CommandExecutor
import taboolib.common.platform.command.CommandStructure
import taboolib.common.platform.command.component.CommandBase
import taboolib.common.platform.service.PlatformCommand

/**
 * TabooCore 命令服务实现
 *
 * 通过原版 Brigadier 命令调度器注册命令，将 TabooLib 的命令结构
 * 映射到原版的 [com.mojang.brigadier.CommandDispatcher]。
 *
 * 每个 TabooLib 命令注册为一个 Brigadier literal 节点，并附带
 * 一个 greedy string 参数节点用于捕获所有后续参数，然后转发到
 * TabooLib 的 [CommandExecutor] 和 [CommandCompleter]。
 */
@Awake
@Inject
@PlatformSide(Platform.TABOOCORE)
class TabooCoreCommand : PlatformCommand {

    /** 已注册的命令名称列表 */
    private val registeredCommands = mutableListOf<String>()

    override fun registerCommand(
        command: CommandStructure,
        executor: CommandExecutor,
        completer: CommandCompleter,
        commandBuilder: CommandBase.() -> Unit
    ) {
        val server = ServerUtils.serverInstance ?: run {
            System.err.println("服务器尚未初始化，命令 ${command.name} 注册失败")
            return
        }
        val dispatcher = server.commands.dispatcher

        // 构建 Brigadier 命令节点
        val literalBuilder = LiteralArgumentBuilder.literal<CommandSourceStack>(command.name)

        // 无参数时的执行器（例如 /test）
        literalBuilder.executes { ctx ->
            val sender = wrapSender(ctx.source)
            val result = executor.execute(sender, command, command.name, emptyArray())
            if (result) 1 else 0
        }

        // 带参数时的执行器（例如 /test arg1 arg2 ...）
        val argsBuilder = RequiredArgumentBuilder.argument<CommandSourceStack, String>(
            "args", StringArgumentType.greedyString()
        )

        // Tab 补全
        argsBuilder.suggests { ctx, suggestionsBuilder ->
            val input = runCatching { StringArgumentType.getString(ctx, "args") }.getOrElse { "" }
            val args = splitArgs(input)
            val sender = wrapSender(ctx.source)
            val completions = completer.execute(sender, command, command.name, args)
            completions?.forEach { suggestionsBuilder.suggest(it) }
            suggestionsBuilder.buildFuture()
        }

        // 命令执行
        argsBuilder.executes { ctx ->
            val input = StringArgumentType.getString(ctx, "args")
            val args = splitArgs(input)
            val sender = wrapSender(ctx.source)
            val result = executor.execute(sender, command, command.name, args)
            if (result) 1 else 0
        }

        literalBuilder.then(argsBuilder)

        // 注册到调度器
        dispatcher.register(literalBuilder)

        // 注册别名
        for (alias in command.aliases) {
            val aliasBuilder = LiteralArgumentBuilder.literal<CommandSourceStack>(alias)
                .redirect(dispatcher.root.getChild(command.name))
            dispatcher.register(aliasBuilder)
        }

        registeredCommands.add(command.name)
        registeredCommands.addAll(command.aliases)

        // 同步命令树到所有在线玩家
        syncCommands(server)
    }

    override fun unregisterCommand(command: String) {
        val server = ServerUtils.serverInstance ?: return
        removeCommandNode(server, command)
        registeredCommands.remove(command)
        syncCommands(server)
    }

    override fun unregisterCommands() {
        val server = ServerUtils.serverInstance ?: return
        val commands = registeredCommands.toList()
        commands.forEach { removeCommandNode(server, it) }
        registeredCommands.clear()
        syncCommands(server)
    }

    override fun unknownCommand(sender: ProxyCommandSender, command: String, state: Int) {
        // 原版 Brigadier 自带未知命令提示，此处不需要额外处理
    }

    /**
     * 将命令源包装为 [ProxyCommandSender]
     *
     * 如果命令源的实体是 ServerPlayer，则返回对应的 [taboocore.player.Player]，
     * 否则返回 [TabooCoreCommandSender]（控制台等）。
     *
     * @param source 原版命令源
     * @return 包装后的 ProxyCommandSender
     */
    private fun wrapSender(source: CommandSourceStack): ProxyCommandSender {
        val player = source.player
        return if (player != null) {
            taboocore.player.Player.of(player)
        } else {
            TabooCoreCommandSender(source)
        }
    }

    /**
     * 将输入字符串分割为参数数组
     *
     * @param input 输入字符串
     * @return 参数数组
     */
    private fun splitArgs(input: String): Array<String> {
        if (input.isEmpty()) return arrayOf("")
        val args = input.split(" ").toTypedArray()
        // 如果输入以空格结尾，添加一个空字符串表示新参数
        if (input.endsWith(" ")) {
            return args + ""
        }
        return args
    }

    /**
     * 从 Brigadier 调度器中移除指定命令节点
     *
     * Brigadier 不提供原生的 remove 方法，需通过反射操作内部数据结构。
     *
     * @param server 服务器实例
     * @param name 要移除的命令名称
     */
    private fun removeCommandNode(server: MinecraftServer, name: String) {
        runCatching {
            val rootNode = server.commands.dispatcher.root
            // children、literals、arguments 字段定义在 CommandNode 中（RootCommandNode 的父类）
            var clazz: Class<*>? = rootNode.javaClass
            while (clazz != null) {
                for (fieldName in listOf("children", "literals", "arguments")) {
                    runCatching {
                        val field = clazz!!.getDeclaredField(fieldName)
                        field.isAccessible = true
                        @Suppress("UNCHECKED_CAST")
                        val map = field.get(rootNode) as MutableMap<String, *>
                        map.remove(name)
                    }
                }
                clazz = clazz.superclass
            }
        }
    }

    /**
     * 同步命令树到所有在线玩家
     *
     * @param server 服务器实例
     */
    private fun syncCommands(server: MinecraftServer) {
        for (player in server.playerList.players) {
            server.commands.sendCommands(player)
        }
    }
}

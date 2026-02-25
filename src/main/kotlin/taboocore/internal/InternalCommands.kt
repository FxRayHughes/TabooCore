package taboocore.internal

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.Level
import taboocore.loader.PluginLoader
import taboocore.permission.PermissionCheckers

/**
 * TabooCore 内置管理命令
 *
 * - `/tps`      — TPS 报告（1s / 5s / 1m 三档平均值）
 * - `/perf`     — 性能监控（TPS、Tick 时间、内存、玩家数、实体/掉落物/方块实体/区块 等）
 * - `/tbreload` — 重载所有 TabooCore 插件（需 OP 4 级）
 */
object InternalCommands {

    fun register(server: MinecraftServer) {
        val dispatcher = server.commands.dispatcher
        dispatcher.register(buildTpsCommand())
        dispatcher.register(buildPerfCommand(server))
        dispatcher.register(buildReloadCommand())
    }

    // ──────────────────────────────────────────────────────────────
    //  /tps
    // ──────────────────────────────────────────────────────────────

    private fun buildTpsCommand() =
        LiteralArgumentBuilder.literal<CommandSourceStack>("tps")
            .requires { PermissionCheckers.isOp().check(it) }
            .executes { ctx ->
                val tps1s = TpsTracker.getTps(1)
                val tps5s = TpsTracker.getTps(5)
                val tps1m = TpsTracker.getTps(60)
                ctx.source.sendSystemMessage(
                    Component.literal("TPS ").withStyle(ChatFormatting.GOLD)
                        .append(gray("(1s/5s/1m): "))
                        .append(colorTps(tps1s))
                        .append(gray(" / "))
                        .append(colorTps(tps5s))
                        .append(gray(" / "))
                        .append(colorTps(tps1m))
                )
                1
            }

    // ──────────────────────────────────────────────────────────────
    //  /perf
    // ──────────────────────────────────────────────────────────────

    private fun buildPerfCommand(server: MinecraftServer) =
        LiteralArgumentBuilder.literal<CommandSourceStack>("perf")
            .requires { PermissionCheckers.isOp().check(it) }
            .executes { ctx ->
                val src = ctx.source

                // ── 基础指标 ──
                val tps1s    = TpsTracker.getTps(1)
                val tps5s    = TpsTracker.getTps(5)
                val tps1m    = TpsTracker.getTps(60)
                val tickMs   = server.getAverageTickTimeNanos() / 1_000_000.0
                val rt       = Runtime.getRuntime()
                val usedMb   = (rt.totalMemory() - rt.freeMemory()) / 1048576L
                val maxMb    = rt.maxMemory() / 1048576L
                val memRatio = usedMb.toDouble() / maxMb
                val players  = server.playerList.playerCount
                val maxPlayers = server.playerList.maxPlayers

                // ── 世界统计 ──
                val levels = server.allLevels.toList()
                var totalEntities   = 0
                var totalItems      = 0
                var totalBlockEnts  = 0
                var totalChunks     = 0
                data class LevelStats(
                    val name: String,
                    val entities: Int,
                    val items: Int,
                    val blockEntities: Int,
                    val chunks: Int
                )
                val levelStats = levels.map { level ->
                    var ents = 0; var items = 0
                    for (e in level.getAllEntities()) {
                        ents++
                        if (e is ItemEntity) items++
                    }
                    val be     = try {
                        val f = Level::class.java.getDeclaredField("blockEntityTickers")
                        f.isAccessible = true
                        @Suppress("UNCHECKED_CAST")
                        (f.get(level) as List<*>).size
                    } catch (_: Exception) { 0 }
                    val chunks = level.chunkSource.loadedChunksCount
                    totalEntities  += ents
                    totalItems     += items
                    totalBlockEnts += be
                    totalChunks    += chunks
                    LevelStats(
                        name          = level.dimension().identifier().getPath(),
                        entities      = ents,
                        items         = items,
                        blockEntities = be,
                        chunks        = chunks
                    )
                }

                val sep = gray("─────────────────────────────")
                src.sendSystemMessage(sep)
                src.sendSystemMessage(
                    Component.literal("  性能监控").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                )
                src.sendSystemMessage(sep)

                // TPS
                src.sendSystemMessage(
                    label("  TPS        ").append(gray("(1s/5s/1m): "))
                        .append(colorTps(tps1s)).append(gray(" / "))
                        .append(colorTps(tps5s)).append(gray(" / "))
                        .append(colorTps(tps1m))
                )

                // Tick 时间
                val tickColor = when {
                    tickMs <= 50f  -> ChatFormatting.GREEN
                    tickMs <= 100f -> ChatFormatting.YELLOW
                    else           -> ChatFormatting.RED
                }
                src.sendSystemMessage(
                    label("  Tick 时间  ")
                        .append(Component.literal("${"%.2f".format(tickMs)} ms").withStyle(tickColor))
                        .append(gray("  (目标 ≤50 ms)"))
                )

                // 内存
                val memColor = when {
                    memRatio < 0.7 -> ChatFormatting.GREEN
                    memRatio < 0.9 -> ChatFormatting.YELLOW
                    else           -> ChatFormatting.RED
                }
                src.sendSystemMessage(
                    label("  内存       ")
                        .append(Component.literal("${usedMb} MB").withStyle(memColor))
                        .append(gray(" / ${maxMb} MB  (${"%.1f".format(memRatio * 100)}%)"))
                )

                // 玩家
                src.sendSystemMessage(
                    label("  玩家       ")
                        .append(Component.literal("$players").withStyle(ChatFormatting.WHITE))
                        .append(gray(" / $maxPlayers"))
                )

                src.sendSystemMessage(sep)

                // 全局汇总
                src.sendSystemMessage(
                    label("  世界数     ")
                        .append(Component.literal("${levels.size}").withStyle(ChatFormatting.WHITE))
                        .append(gray("  总区块: "))
                        .append(Component.literal("$totalChunks").withStyle(ChatFormatting.WHITE))
                )
                src.sendSystemMessage(
                    label("  实体       ")
                        .append(Component.literal("$totalEntities").withStyle(ChatFormatting.WHITE))
                        .append(gray("  掉落物: "))
                        .append(Component.literal("$totalItems").withStyle(ChatFormatting.YELLOW))
                        .append(gray("  方块实体: "))
                        .append(Component.literal("$totalBlockEnts").withStyle(ChatFormatting.WHITE))
                )

                // 逐世界明细
                src.sendSystemMessage(sep)
                src.sendSystemMessage(
                    Component.literal("  世界明细").withStyle(ChatFormatting.GOLD)
                )
                for (ls in levelStats) {
                    src.sendSystemMessage(
                        gray("  [").append(
                            Component.literal(ls.name).withStyle(ChatFormatting.AQUA)
                        ).append(gray("]"))
                            .append(gray("  区块:"))
                            .append(Component.literal(" ${ls.chunks}").withStyle(ChatFormatting.WHITE))
                            .append(gray("  实体:"))
                            .append(Component.literal(" ${ls.entities}").withStyle(ChatFormatting.WHITE))
                            .append(gray("  掉落物:"))
                            .append(Component.literal(" ${ls.items}").withStyle(ChatFormatting.YELLOW))
                            .append(gray("  方块实体:"))
                            .append(Component.literal(" ${ls.blockEntities}").withStyle(ChatFormatting.WHITE))
                    )
                }

                src.sendSystemMessage(sep)
                1
            }

    // ──────────────────────────────────────────────────────────────
    //  /tbreload
    // ──────────────────────────────────────────────────────────────

    private fun buildReloadCommand() =
        LiteralArgumentBuilder.literal<CommandSourceStack>("tbreload")
            .requires { PermissionCheckers.isOp().check(it) }
            .executes { ctx ->
                ctx.source.sendSystemMessage(
                    Component.literal("正在重载插件...").withStyle(ChatFormatting.YELLOW)
                )
                runCatching {
                    PluginLoader.reloadAll()
                    ctx.source.sendSystemMessage(
                        Component.literal("插件重载完成。").withStyle(ChatFormatting.GREEN)
                    )
                }.onFailure { e ->
                    ctx.source.sendSystemMessage(
                        Component.literal("重载失败: ${e.message}").withStyle(ChatFormatting.RED)
                    )
                    e.printStackTrace()
                }
                1
            }

    // ──────────────────────────────────────────────────────────────
    //  工具方法
    // ──────────────────────────────────────────────────────────────

    private fun colorTps(tps: Double): MutableComponent {
        val color = when {
            tps >= 18.0 -> ChatFormatting.GREEN
            tps >= 15.0 -> ChatFormatting.YELLOW
            else        -> ChatFormatting.RED
        }
        return Component.literal("${"%.2f".format(tps)}").withStyle(color)
    }

    private fun gray(text: String): MutableComponent =
        Component.literal(text).withStyle(ChatFormatting.DARK_GRAY)

    private fun label(text: String): MutableComponent =
        Component.literal(text).withStyle(ChatFormatting.GRAY)
}

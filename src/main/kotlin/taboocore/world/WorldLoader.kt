package taboocore.world

import com.mojang.serialization.Lifecycle
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.storage.LevelStorageSource
import net.minecraft.world.level.storage.PrimaryLevelData
import taboocore.util.ServerUtils
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executor

/**
 * 内部 NMS 操作层，负责世界的创建与卸载
 */
internal object WorldLoader {

    // ======================== 维度 Key ========================

    fun dimensionKey(config: WorldConfig): ResourceKey<Level> {
        return ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(config.dimensionNamespace, config.folder)
        )
    }

    // ======================== 文件夹准备 ========================

    fun ensureWorldFolder(config: WorldConfig, server: MinecraftServer) {
        val worldDir = server.getServerDirectory().resolve("worlds").resolve(config.folder)
        Files.createDirectories(worldDir)
        // 若 level.dat 不存在，从主世界复制一份作为初始数据
        val levelDat = worldDir.resolve("level.dat")
        if (!Files.exists(levelDat)) {
            runCatching {
                val storageAccess = getField<LevelStorageSource.LevelStorageAccess>(server, "storageSource")
                val mainLevelDat = storageAccess.getLevelDirectory().path().resolve("level.dat")
                if (Files.exists(mainLevelDat)) {
                    Files.copy(mainLevelDat, levelDat, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    // ======================== LevelStem ========================

    fun getLevelStem(config: WorldConfig, server: MinecraftServer): LevelStem {
        return when (config.template) {
            WorldTemplate.NETHER -> {
                val nether = server.getLevel(Level.NETHER)
                if (nether != null) LevelStem(nether.dimensionTypeRegistration(), nether.chunkSource.getGenerator())
                else overworldStem(server)
            }
            WorldTemplate.THE_END -> {
                val end = server.getLevel(Level.END)
                if (end != null) LevelStem(end.dimensionTypeRegistration(), end.chunkSource.getGenerator())
                else overworldStem(server)
            }
            else -> overworldStem(server)
        }
    }

    private fun overworldStem(server: MinecraftServer): LevelStem {
        val overworld = server.overworld()
        return LevelStem(overworld.dimensionTypeRegistration(), overworld.chunkSource.getGenerator())
    }

    // ======================== LevelData ========================

    fun createLevelData(config: WorldConfig): PrimaryLevelData {
        val settings = LevelSettings(
            config.name,
            GameType.SURVIVAL,
            LevelSettings.DifficultySettings.DEFAULT,
            false,
            WorldDataConfiguration.DEFAULT
        )
        val specialProp = if (config.template == WorldTemplate.FLAT)
            PrimaryLevelData.SpecialWorldProperty.FLAT
        else
            PrimaryLevelData.SpecialWorldProperty.NONE
        return PrimaryLevelData(settings, specialProp, Lifecycle.stable())
    }

    // ======================== 加载世界 ========================

    fun loadLevel(config: WorldConfig): ServerLevel {
        val server = ServerUtils.server
        val key = dimensionKey(config)

        // 已加载则直接返回
        server.getLevel(key)?.let { return it }

        ensureWorldFolder(config, server)

        val worldsDir = server.getServerDirectory().resolve("worlds")
        val storageSource = LevelStorageSource.createDefault(worldsDir)
        val access = try {
            storageSource.createAccess(config.folder)
        } catch (e: IOException) {
            throw RuntimeException("无法创建世界存储访问: ${config.name}", e)
        }

        val executor = getField<Executor>(server, "executor")
        val levelData = createLevelData(config)
        val stem = getLevelStem(config, server)

        val level = ServerLevel(
            server, executor, access, levelData, key, stem,
            false, config.seed, emptyList(), false
        )

        // 注入到服务端 levels 注册表
        val levelsMap = getField<MutableMap<ResourceKey<Level>, ServerLevel>>(server, "levels")
        levelsMap[key] = level

        return level
    }

    // ======================== 卸载世界 ========================

    fun unloadLevel(config: WorldConfig, level: ServerLevel, save: Boolean) {
        val server = ServerUtils.server
        val key = dimensionKey(config)

        if (save) {
            runCatching { level.save(null, true, false) }
        }

        // 将该世界所有玩家传送至主世界出生点
        val overworld = server.overworld()
        val spawnPos = server.worldData.overworldData().getRespawnData().pos()
        level.players().toList().forEach { player ->
            runCatching {
                player.teleportTo(
                    overworld,
                    spawnPos.x.toDouble() + 0.5,
                    spawnPos.y.toDouble(),
                    spawnPos.z.toDouble() + 0.5,
                    emptySet(),
                    player.yRot,
                    player.xRot,
                    false
                )
            }
        }

        // 从服务端 levels 注册表移除
        val levelsMap = getField<MutableMap<ResourceKey<Level>, ServerLevel>>(server, "levels")
        levelsMap.remove(key)

        runCatching { level.close() }
    }

    // ======================== Java 反射工具 ========================

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(obj: Any, name: String): T {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(name)
                field.isAccessible = true
                return field.get(obj) as T
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        throw NoSuchFieldException("字段 '$name' 在 ${obj.javaClass.name} 及其父类中均未找到")
    }
}

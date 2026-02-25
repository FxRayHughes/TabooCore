package taboocore.world

import com.google.gson.GsonBuilder
import net.minecraft.server.level.ServerLevel
import taboocore.util.ServerUtils
import java.io.File
import java.nio.file.Files

/**
 * 世界管理器，对外公开的单例 API
 *
 * ## 使用示例
 * ```kotlin
 * // 在 onEnable() 中初始化
 * WorldManager.initialize()
 *
 * // 创建世界
 * val level = WorldManager.create("myworld", WorldTemplate.NORMAL, 12345L)
 *
 * // 在 onDisable() 中保存
 * WorldManager.saveAll()
 * ```
 */
object WorldManager {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val loadedWorlds = mutableMapOf<String, ServerLevel>()
    private val configs = mutableMapOf<String, WorldConfig>()

    // ======================== 初始化 ========================

    /**
     * 初始化世界管理器：读取配置文件，自动加载 [WorldConfig.autoLoad] 为 true 的世界。
     * 建议在插件 onEnable() 中调用。
     */
    fun initialize() {
        loadConfigs()
        configs.values.filter { it.autoLoad }.forEach { config ->
            runCatching { doLoad(config) }.onFailure { e ->
                println("[WorldManager] 自动加载世界 '${config.name}' 失败: ${e.message}")
            }
        }
    }

    // ======================== 创建 / 加载 / 卸载 ========================

    /**
     * 创建并加载一个新世界。
     *
     * @param name      世界名称（同时作为文件夹名）
     * @param template  生成模板
     * @param seed      世界种子
     * @return 已加载的 [ServerLevel]
     * @throws IllegalStateException 同名世界已存在
     */
    fun create(name: String, template: WorldTemplate = WorldTemplate.NORMAL, seed: Long = System.currentTimeMillis()): ServerLevel {
        check(name !in configs) { "世界 '$name' 已存在" }
        val config = WorldConfig(name = name, folder = name, template = template, seed = seed)
        configs[name] = config
        val level = doLoad(config)
        saveConfigs()
        return level
    }

    /**
     * 加载一个已注册的世界（若已加载则直接返回）。
     *
     * @param name 世界名称
     * @return 已加载的 [ServerLevel]
     * @throws IllegalArgumentException 世界未注册
     */
    fun load(name: String): ServerLevel {
        val config = configs[name] ?: throw IllegalArgumentException("世界 '$name' 未注册")
        loadedWorlds[name]?.let { return it }
        return doLoad(config)
    }

    /**
     * 卸载一个已加载的世界。对未加载的世界调用为空操作。
     *
     * @param name 世界名称
     * @param save 卸载前是否保存，默认 true
     */
    fun unload(name: String, save: Boolean = true) {
        val level = loadedWorlds[name] ?: return
        val config = configs[name] ?: return
        WorldLoader.unloadLevel(config, level, save)
        loadedWorlds.remove(name)
    }

    /**
     * 删除世界：卸载 → 删除磁盘文件夹 → 从注册表移除。
     *
     * @param name 世界名称
     */
    fun delete(name: String) {
        unload(name, save = false)
        configs.remove(name)
        val server = ServerUtils.server
        val worldDir = server.getServerDirectory().resolve("worlds").resolve(name).toFile()
        if (worldDir.exists()) {
            worldDir.deleteRecursively()
        }
        saveConfigs()
    }

    // ======================== 保存 ========================

    /**
     * 保存指定世界。
     *
     * @param name 世界名称
     */
    fun save(name: String) {
        loadedWorlds[name]?.let { level ->
            runCatching { level.save(null, true, false) }
        }
    }

    /**
     * 保存所有已加载的世界。建议在插件 onDisable() 中调用。
     */
    fun saveAll() {
        loadedWorlds.values.forEach { level ->
            runCatching { level.save(null, true, false) }
        }
    }

    // ======================== 导入 / 导出 ========================

    /**
     * 导入外部世界文件夹到 worlds/ 目录，注册并加载。
     *
     * @param name         世界名称（同时作为目标文件夹名）
     * @param sourceFolder 源世界文件夹
     * @param template     生成模板（用于加载时的 LevelStem 选择）
     * @param autoLoad     是否自动加载
     * @return 加载成功的 [ServerLevel]，失败返回 null
     */
    fun import(
        name: String,
        sourceFolder: File,
        template: WorldTemplate = WorldTemplate.NORMAL,
        autoLoad: Boolean = true
    ): ServerLevel? {
        check(name !in configs) { "世界 '$name' 已存在" }
        val server = ServerUtils.server
        val destDir = server.getServerDirectory().resolve("worlds").resolve(name).toFile()
        if (!destDir.exists()) {
            sourceFolder.copyRecursively(destDir, overwrite = true)
        }
        val config = WorldConfig(name = name, folder = name, template = template, autoLoad = autoLoad)
        configs[name] = config
        saveConfigs()
        return runCatching { doLoad(config) }.onFailure { e ->
            println("[WorldManager] 导入世界 '$name' 失败: ${e.message}")
        }.getOrNull()
    }

    /**
     * 导出世界文件夹到指定位置（先保存，再复制）。
     *
     * @param name       世界名称
     * @param destFolder 目标文件夹
     */
    fun export(name: String, destFolder: File) {
        save(name)
        val server = ServerUtils.server
        val srcDir = server.getServerDirectory().resolve("worlds").resolve(
            configs[name]?.folder ?: name
        ).toFile()
        if (srcDir.exists()) {
            srcDir.copyRecursively(destFolder, overwrite = true)
        }
    }

    // ======================== 查询 ========================

    /**
     * 按名称获取已加载的世界，不存在返回 null。
     */
    fun getWorld(name: String): ServerLevel? = loadedWorlds[name]

    /**
     * 所有已加载的世界（name → ServerLevel）。
     */
    val allWorlds: Map<String, ServerLevel>
        get() = loadedWorlds.toMap()

    /**
     * 所有已注册的世界配置（name → WorldConfig），包含未加载的世界。
     */
    val registeredWorlds: Map<String, WorldConfig>
        get() = configs.toMap()

    // ======================== 扩展属性 ========================

    /**
     * 通过反向查找获取 ServerLevel 对应的世界名称，不存在返回 null。
     */
    val ServerLevel.worldName: String?
        get() = loadedWorlds.entries.find { it.value === this }?.key

    // ======================== 内部实现 ========================

    private fun doLoad(config: WorldConfig): ServerLevel {
        val level = WorldLoader.loadLevel(config)
        loadedWorlds[config.name] = level
        return level
    }

    private fun configFile(): File {
        return ServerUtils.server.getServerDirectory().resolve("worldmanager.json").toFile()
    }

    private fun loadConfigs() {
        val file = configFile()
        if (!file.exists()) return
        runCatching {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, WorldConfig>>() {}.type
            val map: Map<String, WorldConfig> = gson.fromJson(file.readText(), type)
            configs.clear()
            configs.putAll(map)
        }.onFailure { e ->
            println("[WorldManager] 读取配置文件失败: ${e.message}")
        }
    }

    private fun saveConfigs() {
        runCatching {
            val file = configFile()
            file.writeText(gson.toJson(configs))
        }.onFailure { e ->
            println("[WorldManager] 保存配置文件失败: ${e.message}")
        }
    }
}

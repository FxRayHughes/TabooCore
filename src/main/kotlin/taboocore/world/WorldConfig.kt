package taboocore.world

/**
 * 世界配置数据类，Gson 可序列化
 *
 * @param name         世界显示名称（也用作注册表 key）
 * @param folder       磁盘文件夹名称（worlds/ 下的子目录）
 * @param template     世界生成模板
 * @param seed         世界种子
 * @param autoLoad     服务端启动时是否自动加载
 * @param dimensionNamespace 自定义维度命名空间（默认 "worlds"）
 */
data class WorldConfig(
    val name: String,
    val folder: String,
    val template: WorldTemplate = WorldTemplate.NORMAL,
    val seed: Long = System.currentTimeMillis(),
    val autoLoad: Boolean = true,
    val dimensionNamespace: String = "worlds"
)

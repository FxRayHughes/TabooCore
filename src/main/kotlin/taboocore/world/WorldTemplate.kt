package taboocore.world

/**
 * 世界生成模板
 */
enum class WorldTemplate {
    /** 普通主世界地形 */
    NORMAL,
    /** 超平坦世界 */
    FLAT,
    /** 虚空世界（与 NORMAL 地形相同，但不生成地物） */
    VOID,
    /** 地狱维度地形（若地狱未加载则回退到主世界） */
    NETHER,
    /** 末地维度地形（若末地未加载则回退到主世界） */
    THE_END
}

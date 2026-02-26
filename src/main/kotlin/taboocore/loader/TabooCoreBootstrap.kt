package taboocore.loader

/**
 * TabooCore Bootstrap 接口
 *
 * 插件在 taboocore.plugin.json 中声明 "bootstrap" 字段指定实现此接口的类名。
 * 该类会在服务端启动前（Registry freeze 前）被实例化并调用 [onBootstrap]，
 * 允许插件注册需要在 Registry 冻结前完成的内容（如附魔、未来的物品/方块等）。
 *
 * 生命周期时序：
 * ```
 * premain
 *   ├── CONST / INIT / LOAD
 *   └── BootstrapPhase.execute() → onBootstrap()  ← 这里
 * 服务端启动
 *   ├── Registry bootstrap（附魔等注册并冻结）
 *   ├── 资源加载（配方、成就等）
 *   └── ENABLE → PluginLoader → onLoad / onEnable
 * ```
 *
 * 使用示例：
 * ```kotlin
 * class MyBootstrap : TabooCoreBootstrap {
 *     override fun onBootstrap() {
 *         EnchantmentRegistry.register("mymod", "fire_aspect_plus") { context ->
 *             // 构建附魔...
 *         }
 *     }
 * }
 * ```
 *
 * taboocore.plugin.json:
 * ```json
 * {
 *   "name": "MyPlugin",
 *   "main": "com.example.MyPlugin",
 *   "bootstrap": "com.example.MyBootstrap"
 * }
 * ```
 */
interface TabooCoreBootstrap {

    /**
     * 在服务端 Registry 冻结前调用。
     * 此时可以调用 [taboocore.registry.enchantment.EnchantmentRegistry.register] 等方法
     * 注册需要在 freeze 前完成的内容。
     */
    fun onBootstrap()
}

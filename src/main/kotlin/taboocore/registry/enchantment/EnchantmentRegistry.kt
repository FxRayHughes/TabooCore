package taboocore.registry.enchantment

import net.minecraft.core.HolderGetter
import net.minecraft.core.HolderSet
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.registries.Registries
import net.minecraft.data.worldgen.BootstrapContext
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.item.Item
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantment.Cost
import java.util.Optional

/**
 * 全局附魔注册表管理器
 *
 * 插件在 [taboocore.loader.TabooCoreBootstrap.onBootstrap] 阶段通过此注册表注册自定义附魔。
 * 附魔的构建延迟到 Registry bootstrap 阶段执行，届时 [BootstrapContext] 可用，
 * 可以通过 `context.lookup(Registries.ITEM)` 获取 tag-based HolderSet。
 *
 * 使用示例：
 * ```kotlin
 * // 在 TabooCoreBootstrap.onBootstrap() 中：
 * EnchantmentRegistry.register("mymod", "fire_aspect_plus") { context ->
 *     val items = context.lookup(Registries.ITEM)
 *     Enchantment.enchantment(
 *         Enchantment.definition(
 *             items.getOrThrow(ItemTags.FIRE_ASPECT_ENCHANTABLE),
 *             2, 3,
 *             Enchantment.dynamicCost(10, 8),
 *             Enchantment.dynamicCost(60, 8),
 *             4,
 *             EquipmentSlotGroup.MAINHAND
 *         )
 *     ).build(Identifier.withDefaultNamespace("mymod.fire_aspect_plus"))
 * }
 * ```
 */
object EnchantmentRegistry {

    private val pendingRegistrations = mutableListOf<PendingEnchantment>()

    /**
     * 注册一个自定义附魔（延迟构建）
     *
     * builder lambda 在 Enchantments.bootstrap() 时调用，此时 [BootstrapContext] 可用，
     * 可以通过 `context.lookup(Registries.ITEM)` 等方法获取 HolderGetter。
     *
     * @param key 附魔的 ResourceKey
     * @param builder 接收 BootstrapContext，返回构建好的 Enchantment
     */
    fun register(key: ResourceKey<Enchantment>, builder: (BootstrapContext<Enchantment>) -> Enchantment) {
        pendingRegistrations.add(PendingEnchantment(key, builder))
    }

    /**
     * 注册一个自定义附魔（便捷方法）
     *
     * @param namespace 命名空间（e.g., "mymod"）
     * @param path 附魔路径（e.g., "custom_sharpness"）
     * @param builder 接收 BootstrapContext，返回构建好的 Enchantment
     */
    fun register(namespace: String, path: String, builder: (BootstrapContext<Enchantment>) -> Enchantment) {
        val key = ResourceKey.create(
            Registries.ENCHANTMENT,
            Identifier.parse("$namespace:$path")
        )
        register(key, builder)
    }

    /**
     * 在 bootstrap 中注册所有待注册的附魔
     * 由 [taboocore.mixin.MixinEnchantments] 调用
     */
    internal fun registerAll(context: BootstrapContext<Enchantment>) {
        for (pending in pendingRegistrations) {
            runCatching {
                val enchantment = pending.builder(context)
                context.register(pending.key, enchantment)
            }.onFailure { e ->
                System.err.println("Failed to register enchantment ${pending.key.identifier()}: ${e.message}")
                e.printStackTrace()
            }
        }
        if (pendingRegistrations.isNotEmpty()) {
            println("Registered ${pendingRegistrations.size} custom enchantment(s)")
        }
    }

    private data class PendingEnchantment(
        val key: ResourceKey<Enchantment>,
        val builder: (BootstrapContext<Enchantment>) -> Enchantment
    )
}

/**
 * 自定义附魔注册示例
 *
 * 这个文件展示了如何在 TabooCore 插件中注册自定义附魔。
 * 附魔注册使用延迟构建模式：在 onBootstrap() 中声明，在 Registry bootstrap 时构建。
 */

package taboocore.example

import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.item.enchantment.Enchantment
import taboocore.registry.enchantment.EnchantmentRegistry

/**
 * 示例：如何注册自定义附魔
 *
 * 这些方法应在 TabooCoreBootstrap.onBootstrap() 中调用
 */
object CustomEnchantmentExamples {

    /**
     * 示例 1：注册一个简单的伤害附魔
     */
    fun registerSharpnessPlus() {
        EnchantmentRegistry.register("example", "sharpness_plus") { context ->
            val items = context.lookup(Registries.ITEM)
            Enchantment.enchantment(
                Enchantment.definition(
                    items.getOrThrow(ItemTags.SHARP_WEAPON_ENCHANTABLE),
                    items.getOrThrow(ItemTags.MELEE_WEAPON_ENCHANTABLE),
                    10, 5,
                    Enchantment.dynamicCost(1, 11),
                    Enchantment.dynamicCost(21, 11),
                    2,
                    EquipmentSlotGroup.MAINHAND
                )
            ).build(Identifier.withDefaultNamespace("example.sharpness_plus"))
        }
    }

    /**
     * 示例 2：使用 ResourceKey 注册
     */
    fun registerCustomEnchantment() {
        val key = ResourceKey.create(
            Registries.ENCHANTMENT,
            Identifier.parse("example:custom_velocity")
        )

        EnchantmentRegistry.register(key) { context ->
            val items = context.lookup(Registries.ITEM)
            Enchantment.enchantment(
                Enchantment.definition(
                    items.getOrThrow(ItemTags.FOOT_ARMOR_ENCHANTABLE),
                    5, 3,
                    Enchantment.dynamicCost(5, 8),
                    Enchantment.dynamicCost(25, 8),
                    4,
                    EquipmentSlotGroup.FEET
                )
            ).build(Identifier.withDefaultNamespace("example.custom_velocity"))
        }
    }

    /**
     * 示例 3：多槽位护甲附魔
     */
    fun registerMultiSlotEnchantment() {
        EnchantmentRegistry.register("example", "protection_plus") { context ->
            val items = context.lookup(Registries.ITEM)
            Enchantment.enchantment(
                Enchantment.definition(
                    items.getOrThrow(ItemTags.ARMOR_ENCHANTABLE),
                    10, 5,
                    Enchantment.dynamicCost(1, 11),
                    Enchantment.dynamicCost(21, 11),
                    2,
                    EquipmentSlotGroup.HEAD,
                    EquipmentSlotGroup.CHEST,
                    EquipmentSlotGroup.LEGS,
                    EquipmentSlotGroup.FEET
                )
            ).build(Identifier.withDefaultNamespace("example.protection_plus"))
        }
    }

    /**
     * 示例 4：稀有附魔（低权重、高花费）
     */
    fun registerRareEnchantment() {
        EnchantmentRegistry.register("example", "legendary_sharpness") { context ->
            val items = context.lookup(Registries.ITEM)
            Enchantment.enchantment(
                Enchantment.definition(
                    items.getOrThrow(ItemTags.MELEE_WEAPON_ENCHANTABLE),
                    2, 10,
                    Enchantment.dynamicCost(30, 15),
                    Enchantment.dynamicCost(100, 15),
                    8,
                    EquipmentSlotGroup.MAINHAND
                )
            ).build(Identifier.withDefaultNamespace("example.legendary_sharpness"))
        }
    }
}

/**
 * 在你的插件中使用：
 *
 * // taboocore.plugin.json:
 * // { "name": "MyPlugin", "main": "com.example.MyPlugin", "bootstrap": "com.example.MyBootstrap" }
 *
 * class MyBootstrap : TabooCoreBootstrap {
 *     override fun onBootstrap() {
 *         CustomEnchantmentExamples.registerSharpnessPlus()
 *         CustomEnchantmentExamples.registerCustomEnchantment()
 *     }
 * }
 */

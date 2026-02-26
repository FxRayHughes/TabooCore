package taboocore.proxy

import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.enchantment.Enchantment

/**
 * 附魔工具，提供附魔的查询功能
 *
 * 注：这些函数在运行时使用，通过反射调用 Registry API
 */
object EnchantmentUtils {

    /**
     * 检查特定附魔是否存在
     *
     * @param enchantmentId 附魔 ID
     * @return 是否存在
     */
    fun MinecraftServer.hasEnchantment(enchantmentId: String): Boolean {
        return try {
            val registryAccessProvider = this.registryAccess()
            val registryOpt = registryAccessProvider::class.java.getMethod("registry", net.minecraft.resources.ResourceKey::class.java)
                .invoke(registryAccessProvider, Registries.ENCHANTMENT) as java.util.Optional<*>
            if (!registryOpt.isPresent) return false

            @Suppress("UNCHECKED_CAST")
            val registry = registryOpt.get() as net.minecraft.core.Registry<Enchantment>
            registry.get(Identifier.parse(enchantmentId)) != null
        } catch (e: Exception) {
            false
        }
    }
}

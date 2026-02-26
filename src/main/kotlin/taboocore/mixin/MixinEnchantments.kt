package taboocore.mixin

import net.minecraft.data.worldgen.BootstrapContext
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import taboocore.registry.enchantment.EnchantmentRegistry

/**
 * 附魔注册 Mixin
 *
 * 在 Enchantments.bootstrap() 的 TAIL 处注入，
 * 此时 BootstrapContext 仍然可用且 Registry 尚未冻结，
 * 允许注册插件通过 [EnchantmentRegistry] 排队的自定义附魔。
 */
@Mixin(Enchantments::class)
abstract class MixinEnchantments {

    @Inject(method = ["bootstrap"], at = [At("TAIL")])
    private fun onBootstrap(context: BootstrapContext<Enchantment>, ci: CallbackInfo) {
        EnchantmentRegistry.registerAll(context)
    }
}

package taboocore.bootstrap

import org.spongepowered.asm.service.IMixinServiceBootstrap

class MixinServiceTabooCoreBootstrap : IMixinServiceBootstrap {
    override fun getName(): String = "TabooCore"
    override fun getServiceClassName(): String = "taboocore.bootstrap.MixinServiceTabooCore"
    override fun bootstrap() {}
}

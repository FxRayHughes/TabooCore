package taboocore.mixin

import net.minecraft.server.MinecraftServer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import taboocore.bridge.EventBridge

@Mixin(MinecraftServer::class)
abstract class MixinMinecraftServer {

    /**
     * 每个 tick 开始时触发
     * 首次 tick 时触发 ACTIVE 生命周期
     */
    @Inject(method = ["tickServer"], at = [At("HEAD")])
    private fun onTick(ci: CallbackInfo) {
        EventBridge.fireTick()
    }

    @Inject(method = ["stopServer"], at = [At("HEAD")])
    private fun onServerStopping(ci: CallbackInfo) {
        taboocore.loader.PluginLoader.disableAll()
        EventBridge.fireServerStopping()
    }
}

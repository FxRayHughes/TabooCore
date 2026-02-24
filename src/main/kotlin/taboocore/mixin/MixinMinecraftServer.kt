package taboocore.mixin

import net.minecraft.server.MinecraftServer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import taboocore.bridge.EventBridge
import taboocore.loader.PluginLoader

@Mixin(MinecraftServer::class)
abstract class MixinMinecraftServer {

    @Inject(method = ["runServer"], at = [At("TAIL")])
    private fun onServerStarted(ci: CallbackInfo) {
        PluginLoader.loadAll()
    }

    @Inject(method = ["tickServer"], at = [At("HEAD")])
    private fun onTick(ci: CallbackInfo) {
        EventBridge.fireTick()
    }

    @Inject(method = ["stopServer"], at = [At("HEAD")])
    private fun onServerStopping(ci: CallbackInfo) {
        PluginLoader.disableAll()
    }
}

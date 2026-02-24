package taboocore.mixin

import net.minecraft.server.dedicated.DedicatedServer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import taboocore.bridge.EventBridge

@Mixin(DedicatedServer::class)
abstract class MixinDedicatedServer {

    /**
     * 服务器初始化完成后（initServer TAIL）
     * 触发 ENABLE 生命周期：加载插件、注册事件监听
     */
    @Inject(method = ["initServer"], at = [At("TAIL")])
    private fun onServerInitialized(cir: CallbackInfoReturnable<Boolean>) {
        EventBridge.fireServerStarted()
    }
}

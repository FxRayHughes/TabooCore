package taboocore.mixin

import net.minecraft.network.Connection
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.PlayerList
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import taboocore.bridge.EventBridge

@Mixin(PlayerList::class)
abstract class MixinPlayerList {

    @Inject(method = ["placeNewPlayer"], at = [At("TAIL")])
    private fun onPlayerJoin(connection: Connection, player: ServerPlayer, ci: CallbackInfo) {
        EventBridge.firePlayerJoin(player)
    }

    @Inject(method = ["remove"], at = [At("HEAD")])
    private fun onPlayerQuit(player: ServerPlayer, ci: CallbackInfo) {
        EventBridge.firePlayerQuit(player)
    }
}

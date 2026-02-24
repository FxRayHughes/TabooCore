package taboocore.mixin

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerPlayerGameMode
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import taboocore.event.world.BlockBreakEvent

@Mixin(ServerPlayerGameMode::class)
abstract class MixinServerPlayerGameMode {

    @Shadow
    lateinit var level: ServerLevel

    @Shadow
    lateinit var player: ServerPlayer

    @Unique
    private var cachedBlockId: String? = null

    @Inject(method = ["destroyBlock"], at = [At("HEAD")], cancellable = true)
    private fun onDestroyBlock(pos: BlockPos, cir: CallbackInfoReturnable<Boolean>) {
        val state = level.getBlockState(pos)
        val blockId = state.block.descriptionId
        cachedBlockId = blockId
        if (BlockBreakEvent.fireBlockBreakPre(player, pos, state)) {
            cachedBlockId = null
            cir.returnValue = false
        }
    }

    @Inject(method = ["destroyBlock"], at = [At("RETURN")])
    private fun onDestroyBlockPost(pos: BlockPos, cir: CallbackInfoReturnable<Boolean>) {
        val blockId = cachedBlockId ?: return
        cachedBlockId = null
        if (cir.returnValue == true) {
            BlockBreakEvent.fireBlockBreakPost(player, pos, blockId)
        }
    }
}

package taboocore.mixin

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import taboocore.event.player.PlayerArmorStandManipulateEvent

@Mixin(ArmorStand::class)
abstract class MixinArmorStand {

    @Unique
    private var manipulateFired: Boolean = false

    // ========== PlayerArmorStandManipulateEvent ==========

    @Inject(method = ["interact"], at = [At("HEAD")], cancellable = true)
    private fun onInteractPre(player: Player, hand: InteractionHand, location: Vec3, cir: CallbackInfoReturnable<InteractionResult>) {
        if (player !is ServerPlayer) return
        val item = player.getItemInHand(hand)
        val slot = (this as ArmorStand).getEquipmentSlotForItem(item)
        manipulateFired = false
        if (PlayerArmorStandManipulateEvent.firePre(player, this as ArmorStand, hand, slot)) {
            cir.returnValue = InteractionResult.FAIL
            return
        }
        manipulateFired = true
    }

    @Inject(method = ["interact"], at = [At("RETURN")])
    private fun onInteractPost(player: Player, hand: InteractionHand, location: Vec3, cir: CallbackInfoReturnable<InteractionResult>) {
        if (!manipulateFired) return
        manipulateFired = false
        if (player !is ServerPlayer) return
        val item = player.getItemInHand(hand)
        val slot = (this as ArmorStand).getEquipmentSlotForItem(item)
        PlayerArmorStandManipulateEvent.firePost(player, this as ArmorStand, hand, slot)
    }
}

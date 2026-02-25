package taboocore.mixin

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.Relative
import net.minecraft.world.entity.player.Player
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import taboocore.event.inventory.InventoryOpenEvent
import taboocore.event.player.*
import java.util.OptionalInt

@Mixin(ServerPlayer::class)
abstract class MixinServerPlayer {

    @Unique
    private var openMenuFired: Boolean = false

    // ========== Inventory Open ==========

    @Inject(method = ["openMenu"], at = [At("HEAD")], cancellable = true)
    private fun onOpenMenuPre(provider: MenuProvider, cir: CallbackInfoReturnable<OptionalInt>) {
        openMenuFired = false
    }

    @Inject(
        method = ["openMenu"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V",
            shift = At.Shift.BEFORE
        )],
        cancellable = true
    )
    private fun onOpenMenuBeforeSend(provider: MenuProvider, cir: CallbackInfoReturnable<OptionalInt>) {
        val container = (this as Player).containerMenu
        if (InventoryOpenEvent.firePre(this as ServerPlayer, container)) {
            cir.returnValue = OptionalInt.empty()
            return
        }
        openMenuFired = true
    }

    @Inject(method = ["openMenu"], at = [At("RETURN")])
    private fun onOpenMenuPost(provider: MenuProvider, cir: CallbackInfoReturnable<OptionalInt>) {
        if (!openMenuFired) return
        openMenuFired = false
        val container = (this as Player).containerMenu
        InventoryOpenEvent.firePost(this as ServerPlayer, container)
    }

    // ========== Bed Enter ==========

    @Inject(method = ["startSleeping"], at = [At("HEAD")], cancellable = true)
    private fun onStartSleepingPre(bedPosition: BlockPos, ci: CallbackInfo) {
        val player = this as ServerPlayer
        val bed = player.level().getBlockState(bedPosition)
        if (PlayerBedEnterEvent.firePre(player, bed, bedPosition) == null) {
            ci.cancel()
        }
    }

    @Inject(method = ["startSleeping"], at = [At("RETURN")])
    private fun onStartSleepingPost(bedPosition: BlockPos, ci: CallbackInfo) {
        val player = this as ServerPlayer
        val bed = player.level().getBlockState(bedPosition)
        PlayerBedEnterEvent.firePost(player, bed, bedPosition)
    }

    // ========== Bed Leave ==========

    @Unique
    private var bedLeavePos: BlockPos? = null

    @Unique
    private var bedLeaveState: net.minecraft.world.level.block.state.BlockState? = null

    @Inject(method = ["stopSleepInBed"], at = [At("HEAD")], cancellable = true)
    private fun onStopSleepInBedPre(forcefulWakeUp: Boolean, updateLevelList: Boolean, ci: CallbackInfo) {
        val player = this as ServerPlayer
        val pos = player.sleepingPos.orElse(null) ?: return
        val bed = player.level().getBlockState(pos)
        bedLeavePos = pos
        bedLeaveState = bed
        if (PlayerBedLeaveEvent.firePre(player, bed, pos) == null) {
            bedLeavePos = null
            bedLeaveState = null
            ci.cancel()
        }
    }

    @Inject(method = ["stopSleepInBed"], at = [At("RETURN")])
    private fun onStopSleepInBedPost(forcefulWakeUp: Boolean, updateLevelList: Boolean, ci: CallbackInfo) {
        val pos = bedLeavePos ?: return
        val bed = bedLeaveState ?: return
        bedLeavePos = null
        bedLeaveState = null
        PlayerBedLeaveEvent.firePost(this as ServerPlayer, bed, pos)
    }

    // ========== Teleport ==========

    @Unique
    private var teleportFromLevel: ServerLevel? = null

    @Unique
    private var teleportFromX: Double = 0.0

    @Unique
    private var teleportFromY: Double = 0.0

    @Unique
    private var teleportFromZ: Double = 0.0

    @Unique
    private var teleportFired: Boolean = false

    @Inject(method = ["teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z"], at = [At("HEAD")], cancellable = true)
    private fun onTeleportToPre(
        level: ServerLevel, x: Double, y: Double, z: Double,
        relatives: Set<Relative>, newYRot: Float, newXRot: Float, resetCamera: Boolean,
        cir: CallbackInfoReturnable<Boolean>
    ) {
        val player = this as ServerPlayer
        teleportFromLevel = player.level()
        teleportFromX = player.x
        teleportFromY = player.y
        teleportFromZ = player.z
        val event = PlayerTeleportEvent.firePre(
            player,
            player.level(), player.x, player.y, player.z,
            level, x, y, z
        )
        if (event == null) {
            teleportFired = false
            cir.returnValue = false
        } else {
            teleportFired = true
        }
    }

    @Inject(method = ["teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z"], at = [At("RETURN")])
    private fun onTeleportToPost(
        level: ServerLevel, x: Double, y: Double, z: Double,
        relatives: Set<Relative>, newYRot: Float, newXRot: Float, resetCamera: Boolean,
        cir: CallbackInfoReturnable<Boolean>
    ) {
        if (!teleportFired) return
        teleportFired = false
        val fromLevel = teleportFromLevel ?: return
        teleportFromLevel = null
        val player = this as ServerPlayer
        PlayerTeleportEvent.firePost(
            player,
            fromLevel, teleportFromX, teleportFromY, teleportFromZ,
            player.level(), player.x, player.y, player.z
        )
    }

    // ========== Experience Points ==========

    @Inject(method = ["giveExperiencePoints"], at = [At("HEAD")], cancellable = true)
    private fun onGiveExperiencePointsPre(i: Int, ci: CallbackInfo) {
        val player = this as ServerPlayer
        val event = PlayerExpChangeEvent.firePre(player, i)
        if (event == null) {
            ci.cancel()
            return
        }
        // 如果事件处理器修改了 amount，使用修改后的值重新调用（跳过原始调用）
        if (event.amount != i) {
            ci.cancel()
            @Suppress("CAST_NEVER_SUCCEEDS")
            (player as net.minecraft.world.entity.player.Player).giveExperiencePoints(event.amount)
        }
    }

    @Inject(method = ["giveExperiencePoints"], at = [At("RETURN")])
    private fun onGiveExperiencePointsPost(i: Int, ci: CallbackInfo) {
        PlayerExpChangeEvent.firePost(this as ServerPlayer, i)
    }

    // ========== Experience Levels ==========

    @Unique
    private var levelChangeFired: Boolean = false

    @Unique
    private var levelChangeOldLevel: Int = 0

    @Inject(method = ["setExperienceLevels"], at = [At("HEAD")], cancellable = true)
    private fun onSetExperienceLevelsPre(amount: Int, ci: CallbackInfo) {
        val player = this as ServerPlayer
        val oldLevel = (this as Player).experienceLevel
        levelChangeOldLevel = oldLevel
        val event = PlayerLevelChangeEvent.firePre(player, oldLevel, amount)
        if (event == null) {
            levelChangeFired = false
            ci.cancel()
            return
        }
        levelChangeFired = true
        // 如果事件处理器修改了 newLevel，使用修改后的值重新调用（跳过原始调用）
        if (event.newLevel != amount) {
            ci.cancel()
            levelChangeFired = false
            player.setExperienceLevels(event.newLevel)
        }
    }

    @Inject(method = ["setExperienceLevels"], at = [At("RETURN")])
    private fun onSetExperienceLevelsPost(amount: Int, ci: CallbackInfo) {
        if (!levelChangeFired) return
        levelChangeFired = false
        PlayerLevelChangeEvent.firePost(this as ServerPlayer, levelChangeOldLevel, amount)
    }

    // ========== PlayerChangedWorldEvent ==========

    @Inject(method = ["triggerDimensionChangeTriggers"], at = [At("HEAD")])
    private fun onTriggerDimensionChangeTriggers(oldLevel: ServerLevel, ci: CallbackInfo) {
        val player = this as ServerPlayer
        val newLevel = player.level() as ServerLevel
        if (oldLevel !== newLevel) {
            PlayerChangedWorldEvent.firePost(player, oldLevel, newLevel)
        }
    }
}

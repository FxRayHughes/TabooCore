package taboocore.event.vehicle

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.vehicle.VehicleEntity
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 载具受伤事件
 */
class VehicleDamageEvent {

    /**
     * 载具受伤前触发
     *
     * @property vehicle 受伤的载具实体
     * @property attacker 攻击者实体（如果存在，否则为 null）
     * @property player 攻击者玩家（如果攻击者是玩家，否则为 null）
     * @property damage 伤害数值（可修改）
     * @property damageSource 伤害来源
     */
    class Pre(
        val vehicle: VehicleEntity,
        val attacker: Entity?,
        val player: TabooCorePlayer?,
        var damage: Float,
        val damageSource: DamageSource
    ) : CancelableInternalEvent()

    /**
     * 载具受伤后触发
     *
     * @property vehicle 受伤的载具实体
     * @property attacker 攻击者实体（如果存在，否则为 null）
     * @property player 攻击者玩家（如果攻击者是玩家，否则为 null）
     * @property damage 伤害数值
     * @property damageSource 伤害来源
     */
    class Post(
        val vehicle: VehicleEntity,
        val attacker: Entity?,
        val player: TabooCorePlayer?,
        val damage: Float,
        val damageSource: DamageSource
    ) : InternalEvent()

    companion object {
        /**
         * 载具受伤前触发，返回事件对象（如为 null 则事件被取消）
         */
        fun firePre(vehicle: VehicleEntity, level: ServerLevel, source: DamageSource, damage: Float): Pre? {
            val attacker = source.entity
            val player = if (attacker is ServerPlayer) TabooCorePlayer.of(attacker) else null
            val event = Pre(vehicle, attacker, player, damage, source)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 载具受伤后触发
         */
        fun firePost(vehicle: VehicleEntity, level: ServerLevel, source: DamageSource, damage: Float) {
            val attacker = source.entity
            val player = if (attacker is ServerPlayer) TabooCorePlayer.of(attacker) else null
            Post(vehicle, attacker, player, damage, source).call()
        }
    }
}

package taboocore.event.entity

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 实体受伤事件
 */
class EntityDamageEvent {

    /**
     * 实体受伤前触发
     *
     * @property entity 受伤的实体
     * @property player 受伤的玩家（如果实体是玩家，否则为 null）
     * @property damageSource 伤害来源
     * @property damage 伤害数值
     */
    class Pre(
        val entity: LivingEntity,
        val player: TabooCorePlayer?,
        val damageSource: DamageSource,
        val damage: Float
    ) : CancelableInternalEvent()

    /**
     * 实体受伤后触发
     *
     * @property entity 受伤的实体
     * @property player 受伤的玩家（如果实体是玩家，否则为 null）
     * @property damageSource 伤害来源
     * @property damage 伤害数值
     */
    class Post(
        val entity: LivingEntity,
        val player: TabooCorePlayer?,
        val damageSource: DamageSource,
        val damage: Float
    ) : InternalEvent()

    companion object {
        /**
         * 实体受伤前触发，返回 true 表示事件被取消
         */
        fun firePre(entity: LivingEntity, source: DamageSource, damage: Float): Boolean {
            val player = if (entity is ServerPlayer) TabooCorePlayer.of(entity) else null
            val event = Pre(entity, player, source, damage)
            event.call()
            return event.isCancelled
        }

        /**
         * 实体受伤后触发
         */
        fun firePost(entity: LivingEntity, source: DamageSource, damage: Float) {
            val player = if (entity is ServerPlayer) TabooCorePlayer.of(entity) else null
            Post(entity, player, source, damage).call()
        }
    }
}

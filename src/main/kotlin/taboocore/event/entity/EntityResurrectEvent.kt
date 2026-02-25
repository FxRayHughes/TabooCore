package taboocore.event.entity

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 实体复活（不死图腾触发）事件
 */
class EntityResurrectEvent {

    /**
     * 实体复活前触发
     *
     * @property entity 触发复活的实体
     * @property player 触发复活的玩家（如果实体是玩家，否则为 null）
     * @property hand 持有不死图腾的手（可能为 null）
     * @property damageSource 致死的伤害来源
     */
    class Pre(
        val entity: LivingEntity,
        val player: TabooCorePlayer?,
        val hand: InteractionHand?,
        val damageSource: DamageSource
    ) : CancelableInternalEvent()

    /**
     * 实体复活后触发
     *
     * @property entity 触发复活的实体
     * @property player 触发复活的玩家（如果实体是玩家，否则为 null）
     * @property hand 持有不死图腾的手（可能为 null）
     * @property damageSource 致死的伤害来源
     */
    class Post(
        val entity: LivingEntity,
        val player: TabooCorePlayer?,
        val hand: InteractionHand?,
        val damageSource: DamageSource
    ) : InternalEvent()

    companion object {
        /**
         * 实体复活前触发，返回 null 表示事件被取消
         */
        fun firePre(entity: LivingEntity, hand: InteractionHand?, damageSource: DamageSource): Pre? {
            val player = if (entity is ServerPlayer) TabooCorePlayer.of(entity) else null
            val event = Pre(entity, player, hand, damageSource)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 实体复活后触发
         */
        fun firePost(entity: LivingEntity, hand: InteractionHand?, damageSource: DamageSource) {
            val player = if (entity is ServerPlayer) TabooCorePlayer.of(entity) else null
            Post(entity, player, hand, damageSource).call()
        }
    }
}

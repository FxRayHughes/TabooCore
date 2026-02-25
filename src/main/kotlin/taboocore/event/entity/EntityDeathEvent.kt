package taboocore.event.entity

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 实体死亡事件
 */
class EntityDeathEvent {

    /**
     * 实体死亡前触发
     *
     * @property entity 死亡的实体
     * @property player 死亡的玩家（如果实体是玩家，否则为 null）
     * @property damageSource 致死伤害来源
     */
    class Pre(
        val entity: LivingEntity,
        val player: TabooCorePlayer?,
        val damageSource: DamageSource
    ) : CancelableInternalEvent()

    /**
     * 实体死亡后触发
     *
     * @property entity 死亡的实体
     * @property player 死亡的玩家（如果实体是玩家，否则为 null）
     * @property damageSource 致死伤害来源
     */
    class Post(
        val entity: LivingEntity,
        val player: TabooCorePlayer?,
        val damageSource: DamageSource
    ) : InternalEvent()

    companion object {
        /**
         * 实体死亡前触发，返回 true 表示事件被取消
         */
        fun firePre(entity: LivingEntity, source: DamageSource): Boolean {
            val player = if (entity is ServerPlayer) TabooCorePlayer.of(entity) else null
            val event = Pre(entity, player, source)
            event.call()
            return event.isCancelled
        }

        /**
         * 实体死亡后触发
         */
        fun firePost(entity: LivingEntity, source: DamageSource) {
            val player = if (entity is ServerPlayer) TabooCorePlayer.of(entity) else null
            Post(entity, player, source).call()
        }
    }
}

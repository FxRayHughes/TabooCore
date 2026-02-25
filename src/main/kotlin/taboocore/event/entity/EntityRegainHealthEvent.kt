package taboocore.event.entity

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 实体恢复生命值事件
 */
class EntityRegainHealthEvent {

    /**
     * 实体恢复生命值前触发
     *
     * @property entity 恢复生命值的实体
     * @property player 恢复生命值的玩家（如果实体是玩家，否则为 null）
     * @property amount 恢复的生命值数量（可修改）
     */
    class Pre(
        val entity: LivingEntity,
        val player: TabooCorePlayer?,
        var amount: Float
    ) : CancelableInternalEvent()

    /**
     * 实体恢复生命值后触发
     *
     * @property entity 恢复生命值的实体
     * @property player 恢复生命值的玩家（如果实体是玩家，否则为 null）
     * @property amount 恢复的生命值数量
     */
    class Post(
        val entity: LivingEntity,
        val player: TabooCorePlayer?,
        val amount: Float
    ) : InternalEvent()

    companion object {
        /**
         * 实体恢复生命值前触发，返回事件对象（可能已被修改），返回 null 表示事件被取消
         */
        fun firePre(entity: LivingEntity, amount: Float): Pre? {
            val player = if (entity is ServerPlayer) TabooCorePlayer.of(entity) else null
            val event = Pre(entity, player, amount)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 实体恢复生命值后触发
         */
        fun firePost(entity: LivingEntity, amount: Float) {
            val player = if (entity is ServerPlayer) TabooCorePlayer.of(entity) else null
            Post(entity, player, amount).call()
        }
    }
}

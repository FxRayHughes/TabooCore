package taboocore.event.entity

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 实体击退事件
 */
class EntityKnockbackEvent {

    /**
     * 实体被击退前触发
     *
     * @property entity 被击退的实体
     * @property player 被击退的玩家（如果实体是玩家，否则为 null）
     * @property strength 击退力度（可修改）
     * @property ratioX 击退方向 X 分量（可修改）
     * @property ratioZ 击退方向 Z 分量（可修改）
     */
    class Pre(
        val entity: LivingEntity,
        val player: TabooCorePlayer?,
        var strength: Double,
        var ratioX: Double,
        var ratioZ: Double
    ) : CancelableInternalEvent()

    /**
     * 实体被击退后触发
     *
     * @property entity 被击退的实体
     * @property player 被击退的玩家（如果实体是玩家，否则为 null）
     * @property strength 击退力度
     * @property ratioX 击退方向 X 分量
     * @property ratioZ 击退方向 Z 分量
     */
    class Post(
        val entity: LivingEntity,
        val player: TabooCorePlayer?,
        val strength: Double,
        val ratioX: Double,
        val ratioZ: Double
    ) : InternalEvent()

    companion object {
        /**
         * 实体被击退前触发，返回事件对象（可能已被修改），返回 null 表示事件被取消
         */
        fun firePre(entity: LivingEntity, strength: Double, ratioX: Double, ratioZ: Double): Pre? {
            val player = if (entity is ServerPlayer) TabooCorePlayer.of(entity) else null
            val event = Pre(entity, player, strength, ratioX, ratioZ)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 实体被击退后触发
         */
        fun firePost(entity: LivingEntity, strength: Double, ratioX: Double, ratioZ: Double) {
            val player = if (entity is ServerPlayer) TabooCorePlayer.of(entity) else null
            Post(entity, player, strength, ratioX, ratioZ).call()
        }
    }
}

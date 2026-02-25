package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家与实体交互事件
 */
class PlayerInteractEntityEvent {

    /**
     * 玩家与实体交互前触发
     *
     * @property player 交互的玩家
     * @property entity 被交互的实体
     * @property hand 使用的手（可能为 null，表示未指定）
     */
    class Pre(
        val player: TabooCorePlayer,
        val entity: Entity,
        val hand: InteractionHand?
    ) : CancelableInternalEvent()

    /**
     * 玩家与实体交互后触发
     *
     * @property player 交互的玩家
     * @property entity 被交互的实体
     * @property hand 使用的手（可能为 null，表示未指定）
     */
    class Post(
        val player: TabooCorePlayer,
        val entity: Entity,
        val hand: InteractionHand?
    ) : InternalEvent()

    companion object {
        /**
         * 玩家与实体交互前触发，返回事件对象，null 表示事件被取消
         */
        fun firePre(player: ServerPlayer, entity: Entity, hand: InteractionHand?): Pre? {
            val event = Pre(TabooCorePlayer.of(player), entity, hand)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 玩家与实体交互后触发
         */
        fun firePost(player: ServerPlayer, entity: Entity, hand: InteractionHand?) {
            Post(TabooCorePlayer.of(player), entity, hand).call()
        }
    }
}

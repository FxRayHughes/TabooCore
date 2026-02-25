package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家交换主副手物品事件
 */
class PlayerSwapHandItemsEvent {

    /**
     * 玩家交换主副手物品前触发
     *
     * @property player 交换物品的玩家
     */
    class Pre(
        val player: TabooCorePlayer
    ) : CancelableInternalEvent()

    /**
     * 玩家交换主副手物品后触发
     *
     * @property player 交换物品的玩家
     */
    class Post(
        val player: TabooCorePlayer
    ) : InternalEvent()

    companion object {
        /**
         * 玩家交换主副手物品前触发，返回 true 表示事件被取消
         */
        fun firePre(player: ServerPlayer): Boolean {
            val event = Pre(TabooCorePlayer.of(player))
            event.call()
            return event.isCancelled
        }

        /**
         * 玩家交换主副手物品后触发
         */
        fun firePost(player: ServerPlayer) {
            Post(TabooCorePlayer.of(player)).call()
        }
    }
}

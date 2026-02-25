package taboocore.event.player

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家被踢出服务器事件
 */
class PlayerKickEvent {

    /**
     * 玩家被踢出前触发
     *
     * @property player 被踢出的玩家
     * @property reason 踢出原因（可修改）
     */
    class Pre(
        val player: TabooCorePlayer,
        var reason: Component
    ) : CancelableInternalEvent()

    /**
     * 玩家被踢出后触发
     *
     * @property player 被踢出的玩家
     * @property reason 踢出原因
     */
    class Post(
        val player: TabooCorePlayer,
        val reason: Component
    ) : InternalEvent()

    companion object {
        /**
         * 玩家被踢出前触发，返回事件对象，null 表示事件被取消
         */
        fun firePre(player: ServerPlayer, reason: Component): Pre? {
            val event = Pre(TabooCorePlayer.of(player), reason)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 玩家被踢出后触发
         */
        fun firePost(player: ServerPlayer, reason: Component) {
            Post(TabooCorePlayer.of(player), reason).call()
        }
    }
}

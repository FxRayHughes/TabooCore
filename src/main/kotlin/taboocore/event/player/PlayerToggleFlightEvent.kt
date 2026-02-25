package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家切换飞行状态事件
 */
class PlayerToggleFlightEvent {

    /**
     * 玩家切换飞行状态前触发
     *
     * @property player 切换飞行状态的玩家
     * @property isFlying 是否正在飞行
     */
    class Pre(
        val player: TabooCorePlayer,
        val isFlying: Boolean
    ) : CancelableInternalEvent()

    /**
     * 玩家切换飞行状态后触发
     *
     * @property player 切换飞行状态的玩家
     * @property isFlying 是否正在飞行
     */
    class Post(
        val player: TabooCorePlayer,
        val isFlying: Boolean
    ) : InternalEvent()

    companion object {
        /**
         * 玩家切换飞行状态前触发，返回事件对象，null 表示事件被取消
         */
        fun firePre(player: ServerPlayer, isFlying: Boolean): Pre? {
            val event = Pre(TabooCorePlayer.of(player), isFlying)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 玩家切换飞行状态后触发
         */
        fun firePost(player: ServerPlayer, isFlying: Boolean) {
            Post(TabooCorePlayer.of(player), isFlying).call()
        }
    }
}

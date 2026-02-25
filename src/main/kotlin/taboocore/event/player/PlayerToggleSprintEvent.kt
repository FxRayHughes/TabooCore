package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家切换疾跑状态事件
 */
class PlayerToggleSprintEvent {

    /**
     * 玩家切换疾跑状态前触发
     *
     * @property player 切换疾跑的玩家
     * @property isSprinting 是否正在疾跑
     */
    class Pre(
        val player: TabooCorePlayer,
        val isSprinting: Boolean
    ) : CancelableInternalEvent()

    /**
     * 玩家切换疾跑状态后触发
     *
     * @property player 切换疾跑的玩家
     * @property isSprinting 是否正在疾跑
     */
    class Post(
        val player: TabooCorePlayer,
        val isSprinting: Boolean
    ) : InternalEvent()

    companion object {
        /**
         * 玩家切换疾跑状态前触发，返回 true 表示事件被取消
         */
        fun firePre(player: ServerPlayer, isSprinting: Boolean): Boolean {
            val event = Pre(TabooCorePlayer.of(player), isSprinting)
            event.call()
            return event.isCancelled
        }

        /**
         * 玩家切换疾跑状态后触发
         */
        fun firePost(player: ServerPlayer, isSprinting: Boolean) {
            Post(TabooCorePlayer.of(player), isSprinting).call()
        }
    }
}

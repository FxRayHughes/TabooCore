package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家经验等级变化事件
 */
class PlayerLevelChangeEvent {

    /**
     * 玩家经验等级变化前触发
     *
     * @property player 等级变化的玩家
     * @property oldLevel 变化前的等级
     * @property newLevel 变化后的等级（可被修改）
     */
    class Pre(
        val player: TabooCorePlayer,
        val oldLevel: Int,
        var newLevel: Int
    ) : CancelableInternalEvent()

    /**
     * 玩家经验等级变化后触发
     *
     * @property player 等级变化的玩家
     * @property oldLevel 变化前的等级
     * @property newLevel 变化后的等级
     */
    class Post(
        val player: TabooCorePlayer,
        val oldLevel: Int,
        val newLevel: Int
    ) : InternalEvent()

    companion object {
        /**
         * 玩家经验等级变化前触发，返回事件对象，null 表示事件被取消
         */
        fun firePre(player: ServerPlayer, oldLevel: Int, newLevel: Int): Pre? {
            val event = Pre(TabooCorePlayer.of(player), oldLevel, newLevel)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 玩家经验等级变化后触发
         */
        fun firePost(player: ServerPlayer, oldLevel: Int, newLevel: Int) {
            Post(TabooCorePlayer.of(player), oldLevel, newLevel).call()
        }
    }
}

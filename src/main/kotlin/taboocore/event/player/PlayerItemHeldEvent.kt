package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家切换手持物品栏事件
 */
class PlayerItemHeldEvent {

    /**
     * 玩家切换手持物品栏前触发
     *
     * @property player 切换物品栏的玩家
     * @property previousSlot 切换前的物品栏索引
     * @property newSlot 切换后的物品栏索引
     */
    class Pre(
        val player: TabooCorePlayer,
        val previousSlot: Int,
        var newSlot: Int
    ) : CancelableInternalEvent()

    /**
     * 玩家切换手持物品栏后触发
     *
     * @property player 切换物品栏的玩家
     * @property previousSlot 切换前的物品栏索引
     * @property newSlot 切换后的物品栏索引
     */
    class Post(
        val player: TabooCorePlayer,
        val previousSlot: Int,
        val newSlot: Int
    ) : InternalEvent()

    companion object {
        /**
         * 玩家切换手持物品栏前触发，返回事件对象，null 表示事件被取消
         */
        fun firePre(player: ServerPlayer, previousSlot: Int, newSlot: Int): Pre? {
            val event = Pre(TabooCorePlayer.of(player), previousSlot, newSlot)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 玩家切换手持物品栏后触发
         */
        fun firePost(player: ServerPlayer, previousSlot: Int, newSlot: Int) {
            Post(TabooCorePlayer.of(player), previousSlot, newSlot).call()
        }
    }
}

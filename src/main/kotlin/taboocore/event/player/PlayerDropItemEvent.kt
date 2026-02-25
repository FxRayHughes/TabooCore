package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家丢弃物品事件
 */
class PlayerDropItemEvent {

    /**
     * 玩家丢弃物品前触发
     *
     * @property player 丢弃物品的玩家
     * @property item 被丢弃的物品
     * @property dropAll 是否丢弃整组物品
     */
    class Pre(
        val player: TabooCorePlayer,
        val item: ItemStack,
        val dropAll: Boolean
    ) : CancelableInternalEvent()

    /**
     * 玩家丢弃物品后触发
     *
     * @property player 丢弃物品的玩家
     * @property item 被丢弃的物品
     * @property dropAll 是否丢弃整组物品
     */
    class Post(
        val player: TabooCorePlayer,
        val item: ItemStack,
        val dropAll: Boolean
    ) : InternalEvent()

    companion object {
        /**
         * 玩家丢弃物品前触发，返回 true 表示事件被取消
         */
        fun firePre(player: ServerPlayer, item: ItemStack, dropAll: Boolean): Boolean {
            val event = Pre(TabooCorePlayer.of(player), item, dropAll)
            event.call()
            return event.isCancelled
        }

        /**
         * 玩家丢弃物品后触发
         */
        fun firePost(player: ServerPlayer, item: ItemStack, dropAll: Boolean) {
            Post(TabooCorePlayer.of(player), item, dropAll).call()
        }
    }
}

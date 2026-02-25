package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家消耗物品事件（如食物、药水等）
 */
class PlayerItemConsumeEvent {

    /**
     * 玩家消耗物品前触发
     *
     * @property player 消耗物品的玩家
     * @property item 被消耗的物品（消耗前的副本）
     */
    class Pre(
        val player: TabooCorePlayer,
        var item: ItemStack
    ) : CancelableInternalEvent()

    /**
     * 玩家消耗物品后触发
     *
     * @property player 消耗物品的玩家
     * @property item 被消耗的物品（消耗前的副本）
     */
    class Post(
        val player: TabooCorePlayer,
        val item: ItemStack
    ) : InternalEvent()

    companion object {
        /**
         * 玩家消耗物品前触发，返回事件对象，null 表示事件被取消
         */
        fun firePre(player: ServerPlayer, item: ItemStack): Pre? {
            val event = Pre(TabooCorePlayer.of(player), item)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 玩家消耗物品后触发
         */
        fun firePost(player: ServerPlayer, item: ItemStack) {
            Post(TabooCorePlayer.of(player), item).call()
        }
    }
}

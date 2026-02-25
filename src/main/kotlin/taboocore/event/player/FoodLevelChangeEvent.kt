package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家饥饿值变化事件
 */
class FoodLevelChangeEvent {

    /**
     * 玩家饥饿值变化前触发
     *
     * @property player 饥饿值变化的玩家
     * @property foodLevel 变化后的饥饿值（可被修改）
     * @property item 导致饥饿值变化的食物物品（可能为 null）
     */
    class Pre(
        val player: TabooCorePlayer,
        var foodLevel: Int,
        val item: ItemStack?
    ) : CancelableInternalEvent()

    /**
     * 玩家饥饿值变化后触发
     *
     * @property player 饥饿值变化的玩家
     * @property foodLevel 变化后的饥饿值
     * @property item 导致饥饿值变化的食物物品（可能为 null）
     */
    class Post(
        val player: TabooCorePlayer,
        val foodLevel: Int,
        val item: ItemStack?
    ) : InternalEvent()

    companion object {
        /**
         * 玩家饥饿值变化前触发，返回事件对象，null 表示事件被取消
         */
        fun firePre(player: ServerPlayer, foodLevel: Int, item: ItemStack?): Pre? {
            val event = Pre(TabooCorePlayer.of(player), foodLevel, item)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 玩家饥饿值变化后触发
         */
        fun firePost(player: ServerPlayer, foodLevel: Int, item: ItemStack?) {
            Post(TabooCorePlayer.of(player), foodLevel, item).call()
        }
    }
}

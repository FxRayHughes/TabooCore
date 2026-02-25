package taboocore.event.inventory

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.AbstractContainerMenu
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家关闭容器事件
 */
class InventoryCloseEvent {

    /**
     * 玩家关闭容器前触发
     *
     * @property player 关闭容器的玩家
     * @property containerId 容器 ID
     * @property container 被关闭的容器菜单
     */
    class Pre(
        val player: TabooCorePlayer,
        val containerId: Int,
        val container: AbstractContainerMenu
    ) : CancelableInternalEvent()

    /**
     * 玩家关闭容器后触发
     *
     * @property player 关闭容器的玩家
     * @property containerId 容器 ID
     * @property container 被关闭的容器菜单
     */
    class Post(
        val player: TabooCorePlayer,
        val containerId: Int,
        val container: AbstractContainerMenu
    ) : InternalEvent()

    companion object {
        /**
         * 玩家关闭容器前触发，返回 true 表示事件被取消
         */
        fun firePre(player: ServerPlayer, containerId: Int, container: AbstractContainerMenu): Boolean {
            val event = Pre(TabooCorePlayer.of(player), containerId, container)
            event.call()
            return event.isCancelled
        }

        /**
         * 玩家关闭容器后触发
         */
        fun firePost(player: ServerPlayer, containerId: Int, container: AbstractContainerMenu) {
            Post(TabooCorePlayer.of(player), containerId, container).call()
        }
    }
}

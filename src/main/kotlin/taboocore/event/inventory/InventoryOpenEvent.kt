package taboocore.event.inventory

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.AbstractContainerMenu
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家打开容器事件
 */
class InventoryOpenEvent {

    /**
     * 玩家打开容器前触发
     *
     * @property player 打开容器的玩家
     * @property container 将要打开的容器菜单
     */
    class Pre(
        val player: TabooCorePlayer,
        val container: AbstractContainerMenu
    ) : CancelableInternalEvent()

    /**
     * 玩家打开容器后触发
     *
     * @property player 打开容器的玩家
     * @property container 已打开的容器菜单
     */
    class Post(
        val player: TabooCorePlayer,
        val container: AbstractContainerMenu
    ) : InternalEvent()

    companion object {
        /**
         * 玩家打开容器前触发，返回 true 表示事件被取消
         */
        fun firePre(player: ServerPlayer, container: AbstractContainerMenu): Boolean {
            val event = Pre(TabooCorePlayer.of(player), container)
            event.call()
            return event.isCancelled
        }

        /**
         * 玩家打开容器后触发
         */
        fun firePost(player: ServerPlayer, container: AbstractContainerMenu) {
            Post(TabooCorePlayer.of(player), container).call()
        }
    }
}

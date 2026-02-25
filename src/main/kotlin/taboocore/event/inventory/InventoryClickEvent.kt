package taboocore.event.inventory

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家点击容器槽位事件
 */
class InventoryClickEvent {

    /**
     * 玩家点击容器槽位前触发
     *
     * @property player 点击容器的玩家
     * @property containerId 容器 ID
     * @property slotIndex 点击的槽位索引
     * @property buttonNum 点击的按钮编号
     * @property containerInput 容器输入类型（PICKUP、QUICK_MOVE、SWAP 等）
     * @property carriedItem 光标上持有的物品
     * @property container 被点击的容器菜单
     */
    class Pre(
        val player: TabooCorePlayer,
        val containerId: Int,
        var slotIndex: Int,
        var buttonNum: Int,
        val containerInput: ContainerInput,
        var carriedItem: ItemStack,
        val container: AbstractContainerMenu
    ) : CancelableInternalEvent()

    /**
     * 玩家点击容器槽位后触发
     *
     * @property player 点击容器的玩家
     * @property containerId 容器 ID
     * @property slotIndex 点击的槽位索引
     * @property buttonNum 点击的按钮编号
     * @property containerInput 容器输入类型
     * @property carriedItem 光标上持有的物品
     * @property container 被点击的容器菜单
     */
    class Post(
        val player: TabooCorePlayer,
        val containerId: Int,
        val slotIndex: Int,
        val buttonNum: Int,
        val containerInput: ContainerInput,
        val carriedItem: ItemStack,
        val container: AbstractContainerMenu
    ) : InternalEvent()

    companion object {
        /**
         * 玩家点击容器槽位前触发，返回事件对象（如为 null 则事件被取消）
         */
        fun firePre(
            player: ServerPlayer,
            containerId: Int,
            slotIndex: Int,
            buttonNum: Int,
            containerInput: ContainerInput,
            carriedItem: ItemStack,
            container: AbstractContainerMenu
        ): Pre? {
            val event = Pre(TabooCorePlayer.of(player), containerId, slotIndex, buttonNum, containerInput, carriedItem, container)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 玩家点击容器槽位后触发
         */
        fun firePost(
            player: ServerPlayer,
            containerId: Int,
            slotIndex: Int,
            buttonNum: Int,
            containerInput: ContainerInput,
            carriedItem: ItemStack,
            container: AbstractContainerMenu
        ) {
            Post(TabooCorePlayer.of(player), containerId, slotIndex, buttonNum, containerInput, carriedItem, container).call()
        }
    }
}

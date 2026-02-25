package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家剪切实体事件（如剪羊毛）
 */
class PlayerShearEntityEvent {

    /**
     * 玩家剪切实体前触发
     *
     * @property player 剪切的玩家
     * @property entity 被剪切的实体
     * @property hand 使用的手
     * @property item 使用的剪刀物品
     */
    class Pre(
        val player: TabooCorePlayer,
        val entity: LivingEntity,
        val hand: InteractionHand,
        val item: ItemStack
    ) : CancelableInternalEvent()

    /**
     * 玩家剪切实体后触发
     *
     * @property player 剪切的玩家
     * @property entity 被剪切的实体
     * @property hand 使用的手
     * @property item 使用的剪刀物品
     */
    class Post(
        val player: TabooCorePlayer,
        val entity: LivingEntity,
        val hand: InteractionHand,
        val item: ItemStack
    ) : InternalEvent()

    companion object {
        /**
         * 玩家剪切实体前触发，返回 true 表示事件被取消
         */
        fun firePre(player: ServerPlayer, entity: LivingEntity, hand: InteractionHand, item: ItemStack): Boolean {
            val event = Pre(TabooCorePlayer.of(player), entity, hand, item)
            event.call()
            return event.isCancelled
        }

        /**
         * 玩家剪切实体后触发
         */
        fun firePost(player: ServerPlayer, entity: LivingEntity, hand: InteractionHand, item: ItemStack) {
            Post(TabooCorePlayer.of(player), entity, hand, item).call()
        }
    }
}

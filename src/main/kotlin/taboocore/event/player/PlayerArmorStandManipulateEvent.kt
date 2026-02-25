package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家操作盔甲架事件
 */
class PlayerArmorStandManipulateEvent {

    /**
     * 玩家操作盔甲架前触发
     *
     * @property player 操作的玩家
     * @property armorStand 被操作的盔甲架
     * @property hand 使用的手
     * @property slot 操作的装备槽位
     */
    class Pre(
        val player: TabooCorePlayer,
        val armorStand: ArmorStand,
        val hand: InteractionHand,
        val slot: EquipmentSlot
    ) : CancelableInternalEvent()

    /**
     * 玩家操作盔甲架后触发
     *
     * @property player 操作的玩家
     * @property armorStand 被操作的盔甲架
     * @property hand 使用的手
     * @property slot 操作的装备槽位
     */
    class Post(
        val player: TabooCorePlayer,
        val armorStand: ArmorStand,
        val hand: InteractionHand,
        val slot: EquipmentSlot
    ) : InternalEvent()

    companion object {
        /**
         * 玩家操作盔甲架前触发，返回 true 表示事件被取消
         */
        fun firePre(player: ServerPlayer, armorStand: ArmorStand, hand: InteractionHand, slot: EquipmentSlot): Boolean {
            val event = Pre(TabooCorePlayer.of(player), armorStand, hand, slot)
            event.call()
            return event.isCancelled
        }

        /**
         * 玩家操作盔甲架后触发
         */
        fun firePost(player: ServerPlayer, armorStand: ArmorStand, hand: InteractionHand, slot: EquipmentSlot) {
            Post(TabooCorePlayer.of(player), armorStand, hand, slot).call()
        }
    }
}

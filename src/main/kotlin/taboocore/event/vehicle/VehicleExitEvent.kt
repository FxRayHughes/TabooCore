package taboocore.event.vehicle

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 实体离开载具事件
 */
class VehicleExitEvent {

    /**
     * 实体离开载具前触发
     *
     * @property vehicle 被离开的载具实体
     * @property passenger 离开载具的实体
     * @property player 离开载具的玩家（如果实体是玩家，否则为 null）
     */
    class Pre(
        val vehicle: Entity,
        val passenger: Entity,
        val player: TabooCorePlayer?
    ) : CancelableInternalEvent()

    /**
     * 实体离开载具后触发
     *
     * @property vehicle 被离开的载具实体
     * @property passenger 离开载具的实体
     * @property player 离开载具的玩家（如果实体是玩家，否则为 null）
     */
    class Post(
        val vehicle: Entity,
        val passenger: Entity,
        val player: TabooCorePlayer?
    ) : InternalEvent()

    companion object {
        /**
         * 实体离开载具前触发，返回 true 表示事件被取消
         */
        fun firePre(passenger: Entity, vehicle: Entity): Boolean {
            val player = if (passenger is ServerPlayer) TabooCorePlayer.of(passenger) else null
            val event = Pre(vehicle, passenger, player)
            event.call()
            return event.isCancelled
        }

        /**
         * 实体离开载具后触发
         */
        fun firePost(passenger: Entity, vehicle: Entity) {
            val player = if (passenger is ServerPlayer) TabooCorePlayer.of(passenger) else null
            Post(vehicle, passenger, player).call()
        }
    }
}

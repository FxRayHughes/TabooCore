package taboocore.event.entity

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.player.Player as NmsPlayer
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 实体驯服事件
 */
class EntityTameEvent {

    /**
     * 实体驯服前触发
     *
     * @property entity 被驯服的动物
     * @property owner 驯服者（NMS 玩家实体）
     * @property player 驯服者的 Player 包装（如果是 ServerPlayer，否则为 null）
     */
    class Pre(
        val entity: TamableAnimal,
        val owner: NmsPlayer,
        val player: TabooCorePlayer?
    ) : CancelableInternalEvent()

    /**
     * 实体驯服后触发
     *
     * @property entity 被驯服的动物
     * @property owner 驯服者（NMS 玩家实体）
     * @property player 驯服者的 Player 包装（如果是 ServerPlayer，否则为 null）
     */
    class Post(
        val entity: TamableAnimal,
        val owner: NmsPlayer,
        val player: TabooCorePlayer?
    ) : InternalEvent()

    companion object {
        /**
         * 实体驯服前触发，返回 null 表示事件被取消
         */
        fun firePre(entity: TamableAnimal, owner: NmsPlayer): Pre? {
            val player = if (owner is ServerPlayer) TabooCorePlayer.of(owner) else null
            val event = Pre(entity, owner, player)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 实体驯服后触发
         */
        fun firePost(entity: TamableAnimal, owner: NmsPlayer) {
            val player = if (owner is ServerPlayer) TabooCorePlayer.of(owner) else null
            Post(entity, owner, player).call()
        }
    }
}

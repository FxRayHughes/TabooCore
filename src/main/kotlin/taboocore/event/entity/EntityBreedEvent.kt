package taboocore.event.entity

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.animal.Animal
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 实体繁殖事件
 */
class EntityBreedEvent {

    /**
     * 实体繁殖前触发
     *
     * @property parent1 父母实体 1
     * @property parent2 父母实体 2
     * @property child 繁殖产生的后代（可能为 null）
     * @property breeder 触发繁殖的玩家（可能为 null）
     */
    class Pre(
        val parent1: Animal,
        val parent2: Animal,
        val child: AgeableMob?,
        val breeder: TabooCorePlayer?
    ) : CancelableInternalEvent()

    /**
     * 实体繁殖后触发
     *
     * @property parent1 父母实体 1
     * @property parent2 父母实体 2
     * @property child 繁殖产生的后代（可能为 null）
     * @property breeder 触发繁殖的玩家（可能为 null）
     */
    class Post(
        val parent1: Animal,
        val parent2: Animal,
        val child: AgeableMob?,
        val breeder: TabooCorePlayer?
    ) : InternalEvent()

    companion object {
        /**
         * 实体繁殖前触发，返回 true 表示事件被取消
         */
        fun firePre(parent1: Animal, parent2: Animal, child: AgeableMob?): Boolean {
            val breeder = parent1.loveCause
            val player = if (breeder is ServerPlayer) TabooCorePlayer.of(breeder) else null
            val event = Pre(parent1, parent2, child, player)
            event.call()
            return event.isCancelled
        }

        /**
         * 实体繁殖后触发
         */
        fun firePost(parent1: Animal, parent2: Animal, child: AgeableMob?) {
            val breeder = parent1.loveCause
            val player = if (breeder is ServerPlayer) TabooCorePlayer.of(breeder) else null
            Post(parent1, parent2, child, player).call()
        }
    }
}

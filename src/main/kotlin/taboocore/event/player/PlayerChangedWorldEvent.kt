package taboocore.event.player

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import taboocore.player.TabooCorePlayer
import taboolib.common.event.InternalEvent

/**
 * 玩家切换世界事件
 */
class PlayerChangedWorldEvent {

    /**
     * 玩家切换世界后触发（不可取消，因为维度切换完成后才触发）
     *
     * @property player 切换世界的玩家
     * @property fromLevel 原来的世界
     * @property toLevel 目标世界
     */
    class Post(
        val player: TabooCorePlayer,
        val fromLevel: ServerLevel,
        val toLevel: ServerLevel
    ) : InternalEvent()

    companion object {
        /**
         * 玩家切换世界后触发
         */
        fun firePost(player: ServerPlayer, fromLevel: ServerLevel, toLevel: ServerLevel) {
            Post(TabooCorePlayer.of(player), fromLevel, toLevel).call()
        }
    }
}

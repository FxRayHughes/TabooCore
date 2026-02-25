package taboocore.event.player

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家游戏模式变更事件
 */
class PlayerGameModeChangeEvent {

    /**
     * 玩家游戏模式变更前触发
     *
     * @property player 变更游戏模式的玩家
     * @property oldGameMode 变更前的游戏模式
     * @property newGameMode 变更后的游戏模式
     */
    class Pre(
        val player: TabooCorePlayer,
        val oldGameMode: GameType,
        val newGameMode: GameType
    ) : CancelableInternalEvent()

    /**
     * 玩家游戏模式变更后触发
     *
     * @property player 变更游戏模式的玩家
     * @property oldGameMode 变更前的游戏模式
     * @property newGameMode 变更后的游戏模式
     */
    class Post(
        val player: TabooCorePlayer,
        val oldGameMode: GameType,
        val newGameMode: GameType
    ) : InternalEvent()

    companion object {
        /**
         * 玩家游戏模式变更前触发，返回 true 表示事件被取消
         */
        fun firePre(player: ServerPlayer, oldMode: GameType, newMode: GameType): Boolean {
            val event = Pre(TabooCorePlayer.of(player), oldMode, newMode)
            event.call()
            return event.isCancelled
        }

        /**
         * 玩家游戏模式变更后触发
         */
        fun firePost(player: ServerPlayer, oldMode: GameType, newMode: GameType) {
            Post(TabooCorePlayer.of(player), oldMode, newMode).call()
        }
    }
}

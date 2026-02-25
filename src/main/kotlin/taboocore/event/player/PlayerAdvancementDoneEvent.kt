package taboocore.event.player

import net.minecraft.advancements.AdvancementHolder
import net.minecraft.server.level.ServerPlayer
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 玩家完成进度事件
 */
class PlayerAdvancementDoneEvent {

    /**
     * 玩家完成进度前触发
     *
     * @property player 完成进度的玩家
     * @property advancement 完成的进度
     * @property criterion 达成的进度条件名
     */
    class Pre(
        val player: TabooCorePlayer,
        val advancement: AdvancementHolder,
        val criterion: String
    ) : CancelableInternalEvent()

    /**
     * 玩家完成进度后触发
     *
     * @property player 完成进度的玩家
     * @property advancement 完成的进度
     * @property criterion 达成的进度条件名
     */
    class Post(
        val player: TabooCorePlayer,
        val advancement: AdvancementHolder,
        val criterion: String
    ) : InternalEvent()

    companion object {
        /**
         * 玩家完成进度前触发，返回事件对象，null 表示事件被取消
         */
        fun firePre(player: ServerPlayer, advancement: AdvancementHolder, criterion: String): Pre? {
            val event = Pre(TabooCorePlayer.of(player), advancement, criterion)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 玩家完成进度后触发
         */
        fun firePost(player: ServerPlayer, advancement: AdvancementHolder, criterion: String) {
            Post(TabooCorePlayer.of(player), advancement, criterion).call()
        }
    }
}

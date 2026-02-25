package taboocore.event.world

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import taboocore.player.TabooCorePlayer
import taboolib.common.event.CancelableInternalEvent
import taboolib.common.event.InternalEvent

/**
 * 告示牌编辑事件
 */
class SignChangeEvent {

    /**
     * 告示牌编辑前触发
     *
     * @property player 编辑告示牌的玩家
     * @property level 所在世界
     * @property pos 告示牌位置
     * @property lines 告示牌文本行（可修改）
     * @property isFront 是否为正面
     */
    class Pre(
        val player: TabooCorePlayer,
        val level: ServerLevel,
        val pos: BlockPos,
        var lines: Array<String>,
        val isFront: Boolean
    ) : CancelableInternalEvent()

    /**
     * 告示牌编辑后触发
     *
     * @property player 编辑告示牌的玩家
     * @property level 所在世界
     * @property pos 告示牌位置
     * @property lines 告示牌文本行
     * @property isFront 是否为正面
     */
    class Post(
        val player: TabooCorePlayer,
        val level: ServerLevel,
        val pos: BlockPos,
        val lines: Array<String>,
        val isFront: Boolean
    ) : InternalEvent()

    companion object {
        /**
         * 告示牌编辑前触发，返回事件对象（可读取修改后的 lines），返回 null 表示事件被取消
         */
        fun firePre(serverPlayer: ServerPlayer, level: ServerLevel, pos: BlockPos, lines: Array<String>, isFront: Boolean): Pre? {
            val event = Pre(TabooCorePlayer.of(serverPlayer), level, pos, lines, isFront)
            event.call()
            return if (event.isCancelled) null else event
        }

        /**
         * 告示牌编辑后触发
         */
        fun firePost(serverPlayer: ServerPlayer, level: ServerLevel, pos: BlockPos, lines: Array<String>, isFront: Boolean) {
            Post(TabooCorePlayer.of(serverPlayer), level, pos, lines, isFront).call()
        }
    }
}

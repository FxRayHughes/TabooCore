package taboocore.permission

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer

/**
 * 权限检查接口，后续可扩展为完整权限系统。
 *
 * 当前默认实现：判断命令来源是否为 OP（玩家）或控制台（始终通过）。
 */
fun interface PermissionChecker {
    fun check(source: CommandSourceStack): Boolean
}

/**
 * 内置权限检查器工厂
 */
object PermissionCheckers {

    /**
     * 检查来源是否为 OP。
     * - 玩家：通过服务端 OP 列表判断
     * - 非玩家（控制台、命令方块等）：始终返回 true
     */
    fun isOp(): PermissionChecker = PermissionChecker { source ->
        val entity = source.getEntity()
        if (entity is ServerPlayer) {
            source.getServer().playerList.isOp(entity.nameAndId())
        } else {
            true
        }
    }
}

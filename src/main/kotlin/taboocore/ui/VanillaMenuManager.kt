package taboocore.ui

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import taboocore.event.inventory.InventoryClickEvent
import taboocore.event.inventory.InventoryCloseEvent
import taboocore.ui.impl.ChestImpl
import taboocore.ui.impl.StorableChestImpl
import taboolib.common.event.InternalEventBus
import java.util.concurrent.ConcurrentHashMap

/**
 * 虚拟菜单管理器，追踪已打开的菜单并路由点击/关闭事件
 *
 * 通过 containerId 映射到 [ChestImpl]，在 Mixin 钩子触发的
 * [InventoryClickEvent] 和 [InventoryCloseEvent] 事件中
 * 路由到对应的菜单处理器。
 */
object VanillaMenuManager {

    /** containerId -> ChestImpl 的映射 */
    private val openMenus: MutableMap<Int, ChestImpl> = ConcurrentHashMap()

    /** 是否已初始化事件监听 */
    private var initialized = false

    /**
     * 注册菜单到管理器
     *
     * @param containerId 容器 ID
     * @param chest 菜单实例
     */
    fun register(containerId: Int, chest: ChestImpl) {
        openMenus[containerId] = chest
    }

    /**
     * 初始化事件监听器
     * 注册 InventoryClickEvent.Pre 和 InventoryCloseEvent.Post 的监听器。
     * 此方法可重复调用，但仅首次调用时注册监听器。
     */
    fun init() {
        if (initialized) return
        initialized = true

        // 监听容器点击事件（高优先级）
        InternalEventBus.listen<InventoryClickEvent.Pre>(priority = -100) { event ->
            val chest = openMenus[event.containerId] ?: return@listen
            val serverPlayer = event.player.handle
            val slotIndex = event.slotIndex
            val menuSlotCount = chest.rows * 9

            // 获取当前槽位的抽象字符
            val slotChar = if (slotIndex >= 0) chest.getSlot(slotIndex) else ' '

            // 获取当前物品
            val currentItem = if (slotIndex in 0 until menuSlotCount) {
                runCatching { event.container.getSlot(slotIndex).item.copy() }.getOrElse { ItemStack.EMPTY }
            } else {
                ItemStack.EMPTY
            }

            val clickEvent = ClickEvent(
                clicker = serverPlayer,
                rawSlot = slotIndex,
                slot = slotChar,
                clickType = ClickType.from(event.containerInput, event.buttonNum),
                currentItem = currentItem,
                cursorItem = event.carriedItem.copy(),
                builder = chest,
                containerInput = event.containerInput,
                buttonNum = event.buttonNum
            )

            // 调用 ChestImpl 的处理逻辑
            runCatching {
                chest.handleClick(clickEvent)
            }.onFailure { ex ->
                System.err.println("菜单点击处理器异常: ${ex.message}")
                ex.printStackTrace()
            }

            // StorableChest 的特殊处理：如果未取消，检查是否允许放入
            if (!clickEvent.isCancelled && chest is StorableChestImpl) {
                val rule = chest.storableRule
                if (slotIndex in 0 until menuSlotCount) {
                    val carried = event.carriedItem
                    if (!carried.isEmpty && !rule.canPlace(slotIndex, carried)) {
                        clickEvent.isCancelled = true
                    }
                }
            }

            // 如果事件被取消，阻止原版处理并重新同步
            if (clickEvent.isCancelled) {
                event.isCancelled = true
                serverPlayer.containerMenu.broadcastFullState()
            }
        }

        // 监听容器关闭事件
        InternalEventBus.listen<InventoryCloseEvent.Post>(priority = -100) { event ->
            val chest = openMenus.remove(event.containerId) ?: return@listen
            val serverPlayer = event.player.handle

            runCatching {
                chest.handleClose(serverPlayer)
            }.onFailure { ex ->
                System.err.println("菜单关闭处理器异常: ${ex.message}")
                ex.printStackTrace()
            }
        }
    }
}

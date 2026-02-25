package taboocore.ui.impl

import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import taboocore.ui.Chest
import taboocore.ui.ClickEvent
import taboocore.ui.ClickType
import taboocore.ui.VanillaMenuManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 标准容器实现
 * 使用原版 NMS ChestMenu 作为底层
 */
open class ChestImpl(override var title: String) : Chest {

    /** 行数 */
    override var rows = 1

    /** 物品与对应抽象字符关系 */
    override var items = ConcurrentHashMap<Char, ItemStack>()

    /** 抽象字符布局 */
    override var slots = CopyOnWriteArrayList<List<Char>>()

    /** 锁定主手 */
    override var handLocked = true

    /** 是否打开过 */
    override var isOpened = false

    /** 点击回调列表 */
    val clickCallbacks = CopyOnWriteArrayList<(event: ClickEvent) -> Unit>()

    /** 自身点击回调（供子类覆盖） */
    var selfClickCallback: (event: ClickEvent) -> Unit = {}

    /** 关闭回调 */
    var closeCallback: ((player: ServerPlayer, menu: Chest) -> Unit) = { _, _ -> isOpened = false }

    /** 只触发一次关闭回调 */
    var onceCloseCallback = false

    /** 是否已触发关闭 */
    var closeFired = false

    /** 构建回调 */
    var buildCallback: ((player: ServerPlayer, menu: Chest) -> Unit) = { _, _ -> isOpened = true }

    /** 异步构建回调 */
    var asyncBuildCallback: ((player: ServerPlayer, menu: Chest) -> Unit) = { _, _ -> }

    /** 最终构建回调 */
    var finalBuildCallback: ((player: ServerPlayer, menu: Chest) -> Unit) = { _, _ -> }

    /** 构建时按位置设置物品的回调列表 */
    val buildItemCallbacks = CopyOnWriteArrayList<(player: ServerPlayer, container: SimpleContainer) -> Unit>()

    /** 最后一个查看此菜单的玩家 */
    var viewer: ServerPlayer? = null

    // region 配置方法

    override fun rows(rows: Int) {
        this.rows = rows
    }

    override fun handLocked(handLocked: Boolean) {
        this.handLocked = handLocked
    }

    // endregion

    // region 构建回调

    override fun onBuild(async: Boolean, callback: (player: ServerPlayer, menu: Chest) -> Unit) {
        if (isOpened) error("页面已经打开，无法设置构建回调")
        if (async) {
            val before = asyncBuildCallback
            asyncBuildCallback = { player, menu ->
                callback(player, menu)
                before(player, menu)
            }
        } else {
            val before = buildCallback
            buildCallback = { player, menu ->
                callback(player, menu)
                before(player, menu)
            }
        }
    }

    /**
     * 当所有 build 函数执行完成时（供子类使用）
     */
    fun onFinalBuild(async: Boolean = false, callback: (player: ServerPlayer, menu: Chest) -> Unit) {
        if (async) {
            // 暂不支持异步最终构建
        } else {
            finalBuildCallback = callback
        }
    }

    override fun onClose(once: Boolean, callback: (player: ServerPlayer, menu: Chest) -> Unit) {
        closeCallback = callback
        onceCloseCallback = once
    }

    // endregion

    // region 交互方法

    override fun onClick(bind: Int, callback: (event: ClickEvent) -> Unit) {
        onClick {
            if (it.rawSlot == bind) {
                it.isCancelled = true
                if (it.clickType != ClickType.DRAG) {
                    callback(it)
                }
            }
        }
    }

    override fun onClick(bind: Char, callback: (event: ClickEvent) -> Unit) {
        onClick {
            if (it.slot == bind) {
                it.isCancelled = true
                if (it.clickType != ClickType.DRAG) {
                    callback(it)
                }
            }
        }
    }

    override fun onClick(lock: Boolean, callback: (event: ClickEvent) -> Unit) {
        if (lock) {
            clickCallbacks += {
                it.isCancelled = true
                if (it.clickType != ClickType.DRAG) {
                    callback(it)
                }
            }
        } else {
            clickCallbacks += callback
        }
    }

    /**
     * 设置自身点击回调（供子类使用）
     */
    open fun selfClick(callback: (event: ClickEvent) -> Unit) {
        selfClickCallback = callback
    }

    // endregion

    // region 布局方法

    override fun map(vararg slots: String) {
        this.slots.clear()
        this.slots.addAll(slots.map { it.toCharArray().toList() })
        if (rows < slots.size) {
            rows = slots.size
        }
    }

    override fun set(slot: Char, itemStack: ItemStack) {
        if (isOpened) error("页面已经打开，无法预设物品")
        items[slot] = itemStack
    }

    override fun set(slot: Int, itemStack: ItemStack) {
        buildItemCallbacks += { _, container -> container.setItem(slot, itemStack.copy()) }
    }

    override fun set(slot: Char, itemStack: ItemStack, onClick: ClickEvent.() -> Unit) {
        set(slot, itemStack)
        onClick(slot, onClick)
    }

    override fun set(slot: Int, itemStack: ItemStack, onClick: ClickEvent.() -> Unit) {
        set(slot, itemStack)
        onClick(slot, onClick)
    }

    override fun set(slot: Char, callback: () -> ItemStack) {
        buildItemCallbacks += { _, container ->
            getSlots(slot).forEach { s -> container.setItem(s, callback()) }
        }
    }

    override fun set(slot: Int, callback: () -> ItemStack) {
        buildItemCallbacks += { _, container -> container.setItem(slot, callback()) }
    }

    override fun getSlot(slot: Int): Char {
        var row = 0
        while (row < slots.size) {
            val line = slots[row]
            var col = 0
            while (col < line.size && col < 9) {
                if (row * 9 + col == slot) {
                    return line[col]
                }
                col++
            }
            row++
        }
        return ' '
    }

    override fun getSlots(slot: Char): List<Int> {
        val list = mutableListOf<Int>()
        var row = 0
        while (row < slots.size) {
            val line = slots[row]
            var col = 0
            while (col < line.size && col < 9) {
                if (line[col] == slot) {
                    list.add(row * 9 + col)
                }
                col++
            }
            row++
        }
        return list
    }

    override fun getFirstSlot(slot: Char): Int {
        val result = getSlots(slot)
        return if (result.isEmpty()) -1 else result[0]
    }

    // endregion

    // region 标题更新

    override fun updateTitle(title: Component) {
        this.title = title.string
        val player = viewer ?: return
        val containerId = player.containerMenu.containerId
        val menuType = getMenuType(rows)
        player.connection.send(ClientboundOpenScreenPacket(containerId, menuType, title))
        player.containerMenu.broadcastFullState()
    }

    // endregion

    /**
     * 创建标题组件
     */
    open fun createTitle(): Component {
        return Component.literal(title)
    }

    /**
     * 构建页面，返回内部容器对象
     */
    override fun build(): SimpleContainer {
        val slotCount = if (rows > 0) rows * 9 else slots.size * 9
        val container = SimpleContainer(slotCount)
        // 按照 map 布局填充物品
        var row = 0
        while (row < slots.size) {
            val line = slots[row]
            var col = 0
            while (col < line.size && col < 9) {
                val item = items[line[col]]
                if (item != null) {
                    container.setItem(row * 9 + col, item.copy())
                }
                col++
            }
            row++
        }
        return container
    }

    /**
     * 构建并为玩家打开菜单
     *
     * @param player 要打开菜单的玩家
     */
    fun open(player: ServerPlayer) {
        VanillaMenuManager.init()
        viewer = player
        val chest = this
        val menuType = getMenuType(rows)

        // 执行构建回调
        buildCallback(player, chest)

        // 执行最终构建回调
        finalBuildCallback(player, chest)

        // 构建容器
        val container = build()

        // 执行延迟物品设置回调
        buildItemCallbacks.forEach { it(player, container) }

        player.openMenu(object : MenuProvider {
            override fun getDisplayName(): Component = createTitle()

            override fun createMenu(containerId: Int, inventory: Inventory, p: Player): AbstractContainerMenu {
                val menu = VanillaMenuHolder(menuType, containerId, inventory, container, rows)
                VanillaMenuManager.register(containerId, chest)
                return menu
            }
        })
    }

    /**
     * 处理点击事件（由 VanillaMenuManager 调用）
     */
    internal fun handleClick(event: ClickEvent) {
        // 锁定主手时取消所有事件
        if (handLocked) {
            event.isCancelled = true
        }
        // 执行自身点击回调（子类可覆盖）
        selfClickCallback(event)
        // 执行所有注册的点击回调
        for (callback in clickCallbacks) {
            callback(event)
        }
    }

    /**
     * 处理关闭事件（由 VanillaMenuManager 调用）
     */
    internal fun handleClose(player: ServerPlayer) {
        if (onceCloseCallback && closeFired) return
        closeFired = true
        runCatching {
            closeCallback(player, this)
        }.onFailure { ex ->
            System.err.println("菜单关闭处理器异常: ${ex.message}")
            ex.printStackTrace()
        }
    }

    companion object {

        /**
         * 根据行数获取对应的 MenuType
         *
         * @param rows 行数（1-6）
         * @return 对应的 MenuType
         */
        fun getMenuType(rows: Int): MenuType<ChestMenu> {
            return when (rows) {
                1 -> MenuType.GENERIC_9x1
                2 -> MenuType.GENERIC_9x2
                3 -> MenuType.GENERIC_9x3
                4 -> MenuType.GENERIC_9x4
                5 -> MenuType.GENERIC_9x5
                6 -> MenuType.GENERIC_9x6
                else -> throw IllegalArgumentException("行数必须在 1-6 之间，当前: $rows")
            }
        }
    }
}

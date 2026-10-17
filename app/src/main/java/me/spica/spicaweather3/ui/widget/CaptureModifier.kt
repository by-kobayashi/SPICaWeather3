package me.spica.spicaweather3.ui.widget

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 为组件添加截图捕获和下拉菜单功能的 Modifier
 *
 * 这个 Modifier 会：
 * 1. 捕获组件的位置和尺寸
 * 2. 使用 GraphicsLayer 截取组件的图像
 * 3. 点击时显示全屏下拉菜单叠加层
 * 4. 在叠加层中显示组件截图和菜单项
 * 5. 背景模糊效果
 *
 * 使用示例：
 * ```
 * Box(
 *   modifier = Modifier
 *     .size(200.dp)
 *     .background(Color.Blue)
 *     .captureToDropdownMenu(
 *       items = listOf(
 *         DropdownMenuItem("编辑", Icons.Default.Edit) { /* 编辑逻辑 */ },
 *         DropdownMenuItem("分享", Icons.Default.Share) { /* 分享逻辑 */ },
 *         DropdownMenuItem("删除", Icons.Default.Delete) { /* 删除逻辑 */ }
 *       )
 *     )
 * ) {
 *   Text("点击显示菜单")
 * }
 * ```
 *
 * @param items 菜单项列表
 * @param onLongClick 是否使用长按触发（默认为 false，使用点击触发）
 * @param enabled 是否启用（默认为 true）
 */
fun Modifier.captureToDropdownMenu(
  items: List<DropdownMenuItem>,
  onLongClick: Boolean = false,
  enabled: Boolean = true
): Modifier {
  if (!enabled || items.isEmpty()) {
    return this
  }

  return this then CaptureDropdownMenuElement(items, null, onLongClick)
}

/**
 * 为组件添加截图捕获和下拉菜单功能的 Modifier（使用 lambda 提供菜单项）
 *
 * 与 captureToDropdownMenu 类似，但菜单项通过 lambda 动态提供，
 * 这在需要根据当前状态动态生成菜单项时很有用。
 *
 * 使用示例：
 * ```
 * var isStarred by remember { mutableStateOf(false) }
 * 
 * Box(
 *   modifier = Modifier
 *     .size(200.dp)
 *     .captureToDropdownMenuDynamic(
 *       itemsProvider = {
 *         listOf(
 *           DropdownMenuItem(
 *             text = if (isStarred) "取消收藏" else "收藏",
 *             icon = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
 *             onClick = { isStarred = !isStarred }
 *           ),
 *           DropdownMenuItem("删除", Icons.Default.Delete) { /* 删除 */ }
 *         )
 *       }
 *     )
 * )
 * ```
 *
 * @param itemsProvider 动态提供菜单项的 lambda 函数
 * @param onLongClick 是否使用长按触发（默认为 false）
 * @param enabled 是否启用（默认为 true）
 */
fun Modifier.captureToDropdownMenuDynamic(
  itemsProvider: () -> List<DropdownMenuItem>,
  onLongClick: Boolean = false,
  enabled: Boolean = true
): Modifier {
  if (!enabled) {
    return this
  }

  return this then CaptureDropdownMenuElement(emptyList(), itemsProvider, onLongClick)
}

private data class CaptureDropdownMenuElement(
  val items: List<DropdownMenuItem>,
  val itemsProvider: (() -> List<DropdownMenuItem>)?,
  val onLongClick: Boolean
) : ModifierNodeElement<CaptureDropdownMenuNode>() {
  override fun create() = CaptureDropdownMenuNode(items, itemsProvider, onLongClick)

  override fun update(node: CaptureDropdownMenuNode) {
    node.update(items, itemsProvider, onLongClick)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = if (itemsProvider == null) "captureToDropdownMenu" else "captureToDropdownMenuDynamic"
    properties["items"] = items
    properties["itemsProvider"] = itemsProvider
    properties["onLongClick"] = onLongClick
  }
}

private class CaptureDropdownMenuNode(
  private var items: List<DropdownMenuItem>,
  private var itemsProvider: (() -> List<DropdownMenuItem>)?,
  private var onLongClick: Boolean
) : DelegatingNode(), DrawModifierNode, GlobalPositionAwareModifierNode,
  CompositionLocalConsumerModifierNode {
  private var graphicsLayer: GraphicsLayer? = null
  private var hasRecordedContent = false
  private var targetOffset = Offset.Zero
  private var targetSize = IntSize.Zero
  private var captureJob: Job? = null
  private val pointerInput = delegate(
    SuspendingPointerInputModifierNode {
      if (onLongClick) {
        detectTapGestures(onLongPress = { showMenu() })
      } else {
        detectTapGestures(onTap = { showMenu() })
      }
    }
  )

  fun update(
    items: List<DropdownMenuItem>,
    itemsProvider: (() -> List<DropdownMenuItem>)?,
    onLongClick: Boolean
  ) {
    val resetInput = this.onLongClick != onLongClick || this.items != items
    this.items = items
    this.itemsProvider = itemsProvider
    this.onLongClick = onLongClick
    if (resetInput) {
      pointerInput.resetPointerInputHandler()
    }
  }

  override fun onAttach() {
    graphicsLayer = requireGraphicsContext().createGraphicsLayer()
  }

  override fun onDetach() {
    resetCapture()
    graphicsLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
    graphicsLayer = null
  }

  override fun onReset() {
    resetCapture()
    pointerInput.resetPointerInputHandler()
    invalidateDraw()
  }

  private fun resetCapture() {
    captureJob?.cancel()
    captureJob = null
    hasRecordedContent = false
    targetOffset = Offset.Zero
    targetSize = IntSize.Zero
  }

  override fun ContentDrawScope.draw() {
    val layer = graphicsLayer ?: return drawContent()
    layer.record {
      this@draw.drawContent()
    }
    hasRecordedContent = true
    drawLayer(layer)
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    targetOffset = coordinates.positionInWindow()
    targetSize = coordinates.size
  }

  private fun showMenu() {
    val layer = graphicsLayer ?: return
    if (!hasRecordedContent || targetSize.width <= 0 || targetSize.height <= 0) return
    val menuItems = itemsProvider?.invoke() ?: items
    if (menuItems.isEmpty()) return

    val controller = currentValueOf(LocalDropdownMenuController)
    val offset = targetOffset
    val size = targetSize
    captureJob?.cancel()
    captureJob = coroutineScope.launch {
      val bitmap = layer.toImageBitmap()
      controller.show(bitmap, offset, size, menuItems)
    }
  }
}

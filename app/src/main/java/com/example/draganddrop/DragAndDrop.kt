package com.example.draganddrop

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.annotation.FloatRange
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/**
 * State of Drag&Drop in LazyList. Creates with rememberLazyListDragAndDropState()
 * @param draggedItemIndex index of dragged item. Use it to get index of dragged item
 * @param draggedItemOffset offset of dragged item. Use it to get offset of dragged item
 */
@Stable
interface LazyListDragAndDropState {
    val draggedItemIndex: State<Int?>
    val draggedItemOffset: State<Float?>
}

@Stable
private class LazyListDragAndDropStateImpl(
    val _draggedItemIndex: MutableState<Int?>,
    val _draggedItemOffset: MutableState<Float?>,
    val _position: MutableState<Float?>,
    val _startIndex: MutableState<Int>,
) : LazyListDragAndDropState {
    override val draggedItemIndex: State<Int?> = _draggedItemIndex
    override val draggedItemOffset: State<Float?> = _draggedItemOffset
}

/**
 * Creates state of Drag&Drop in LazyList
 */
@Composable
fun rememberLazyListDragAndDropState(): LazyListDragAndDropState = remember {
    LazyListDragAndDropStateImpl(
        _draggedItemIndex = mutableStateOf(null),
        _draggedItemOffset = mutableStateOf(null),
        _position = mutableStateOf(null),
        _startIndex = mutableStateOf(0),
    )
}

/**
 * Adds Drag&Drop support to LazyList. If list have elements of different size it may not work properly
 * @param lazyListState the state of current LazyList
 * @param dragAndDropState state of Drag&Drop. Used to get information about dragging
 * @param onItemsOrderChanged callback where you should reorder list used in LazyList
 * @param onDragEnd callback when drag ends
 * @param autoscrollSpeed speed of autoscroll in px per second
 * @param startAutoscrollBound start bound of autoscroll,
 * @param endAutoscrollBound end bound of autoscroll
 * @param autoscrollEnabled enables or disables autoscroll
 */
@Composable
fun Modifier.dragAndDrop(
    lazyListState: LazyListState,
    dragAndDropState: LazyListDragAndDropState,
    onItemsOrderChanged: (lastIndex: Int, newIndex: Int) -> Unit,
    onDragEnd: (startIndex: Int, endIndex: Int) -> Unit = { _, _ -> },
    @FloatRange(from = 0.0) autoscrollSpeed: Float = 1500f,
    startAutoscrollBound: Float = 50f,
    endAutoscrollBound: Float = -50f,
    autoscrollEnabled: Boolean = true
): Modifier {
    val context = LocalContext.current
    val fps = rememberSaveable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display!!.refreshRate
        else (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
    }

    val coroutineScope = rememberCoroutineScope()

    val state = dragAndDropState as LazyListDragAndDropStateImpl

    var position by state._position
    var itemIndex by state._draggedItemIndex
    var itemOffset by state._draggedItemOffset
    var startIndex by state._startIndex

    fun updateItemIndex(newPos: Float) {
        lazyListState.layoutInfo.visibleItemsInfo.let { visibleItemsInfo ->
            val fvii = lazyListState.firstVisibleItemIndex
            val fviso = lazyListState.firstVisibleItemScrollOffset
            val lastIndex = itemIndex!!
            val draggedItem = visibleItemsInfo[lastIndex - lazyListState.firstVisibleItemIndex]
            visibleItemsInfo.firstOrNull { info ->
                newPos.toInt().coerceIn(lazyListState.layoutInfo.viewportStartOffset + 1, lazyListState.layoutInfo.viewportEndOffset - 1) in (info.offset + if (info.index > draggedItem.index) info.size - draggedItem.size else 0)..(info.offset + if (info.index > draggedItem.index) info.size else draggedItem.size)
            }?.let { closest ->
                val newIndex = closest.index

                if (lastIndex != newIndex) {
                    itemOffset = itemOffset?.plus(
                        (
                                visibleItemsInfo[newIndex - fvii].size + (
                                        if (newIndex < lastIndex) ((lastIndex - fvii + 1)..<(newIndex - fvii))
                                        else ((newIndex - fvii + 1)..<(lastIndex - fvii))
                                        ).sumOf {
                                        visibleItemsInfo[it].size
                                    }
                                ) * if (newIndex < lastIndex) 1f else -1f
                    )
                }

                onItemsOrderChanged(lastIndex, newIndex)

                itemIndex = newIndex

                if (fvii == lastIndex || fvii == newIndex) {
                    coroutineScope.launch {
                        lazyListState.scrollToItem(fvii, fviso)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!autoscrollEnabled) return@LaunchedEffect

        var lastFrameRemains = 0f
        while (true) {
            measureTime {
                position?.let Pos@ { pos ->
                    val scrollValue = autoscrollSpeed / fps *
                            if (pos > lazyListState.layoutInfo.viewportEndOffset + endAutoscrollBound) 1
                            else if (pos < lazyListState.layoutInfo.viewportStartOffset + startAutoscrollBound) -1
                            else 0

                    itemOffset?.plus(scrollValue)?.let {
                        itemOffset = if (itemIndex == 0 && it < 0 ||
                            itemIndex == lazyListState.layoutInfo.totalItemsCount - 1 && it > 0
                        ) 0f else it
                    }

                    lazyListState.scrollBy(scrollValue)

                    updateItemIndex(pos)
                }
            }.let {
                val lastFrameDuration = it.toDouble(DurationUnit.MILLISECONDS)

                delay((1000 / fps - lastFrameDuration - lastFrameRemains)
                    .toLong().coerceAtLeast(0))

                lastFrameRemains =
                    if (lastFrameDuration > 1000 / fps) (lastFrameDuration - 1000 / fps).toFloat()
                    else 0f
            }
        }
    }

    return this.pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                lazyListState.layoutInfo.visibleItemsInfo
                    .firstOrNull { info ->
                        offset.y.toInt() in info.offset..(info.offset + info.size)
                    }
                    ?.let {
                        position = it.offset + it.size / 2f
                        itemOffset = 0f
                        itemIndex = it.index
                        startIndex = it.index
                    }
            },
            onDragEnd = {
                onDragEnd(startIndex, itemIndex!!)
                position = null
                itemIndex = null
            },
            onDragCancel = {
                onDragEnd(startIndex, itemIndex!!)
                position = null
                itemIndex = null
            },
            onDrag = { change, dragAmount ->
                change.consume()
                position = position?.plus(dragAmount.y)

                itemOffset?.let {
                    val newOffset = it + dragAmount.y

                    if (itemIndex == 0 && newOffset < 0 ||
                        itemIndex == (lazyListState.layoutInfo.totalItemsCount - 1) && newOffset > 0
                    ) {
                        itemOffset = 0f
                        return@detectDragGesturesAfterLongPress
                    }

                    itemOffset = newOffset
                }

                position?.let { pos ->
                    updateItemIndex(pos)
                }
            }
        )
    }
}

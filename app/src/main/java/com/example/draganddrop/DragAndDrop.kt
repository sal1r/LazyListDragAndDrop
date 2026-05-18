package com.example.draganddrop

import androidx.annotation.FloatRange
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * State of Drag&Drop in LazyList. Creates with rememberLazyListDragAndDropState()
 * @param draggedItemIndex index of dragged item. Use it to get index of dragged item
 * @param draggedItemOffset offset of dragged item. Use it to get offset of dragged item
 */
@Stable
sealed interface LazyListDragAndDropState {
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
        _startIndex = mutableIntStateOf(0),
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
    val coroutineScope = rememberCoroutineScope()

    val state = dragAndDropState as LazyListDragAndDropStateImpl

    var position = state._position
    var itemIndex = state._draggedItemIndex
    var itemOffset = state._draggedItemOffset
    var startIndex = state._startIndex

    fun updateItemIndex(newPos: Float) {
        lazyListState.layoutInfo.visibleItemsInfo.let { visibleItemsInfo ->
            val fvii = lazyListState.firstVisibleItemIndex
            val fviso = lazyListState.firstVisibleItemScrollOffset
            val lastIndex = itemIndex.value!!
            val draggedItem = visibleItemsInfo[lastIndex - lazyListState.firstVisibleItemIndex]

            visibleItemsInfo.firstOrNull { info ->
                newPos.toInt().coerceIn(
                    lazyListState.layoutInfo.viewportStartOffset + 1,
                    lazyListState.layoutInfo.viewportEndOffset - 1
                ) in (
                    info.offset + if (info.index > draggedItem.index) info.size - draggedItem.size else 0
                )..(
                    info.offset + if (info.index > draggedItem.index) info.size else draggedItem.size
                )
            }?.let { closest ->
                val newIndex = closest.index

                if (lastIndex != newIndex) {
                    itemOffset.value = itemOffset.value?.plus(
                        (
                                visibleItemsInfo[newIndex - fvii].size + (
                                        if (newIndex < lastIndex) ((newIndex - fvii + 1)..<(lastIndex - fvii))
                                        else ((lastIndex - fvii + 1)..<(newIndex - fvii))
                                        ).sumOf {
                                        visibleItemsInfo[it].size
                                    }
                                ) * if (newIndex < lastIndex) 1f else -1f
                    )
                }

                onItemsOrderChanged(lastIndex, newIndex)

                itemIndex.value = newIndex

                if (fvii == lastIndex || fvii == newIndex) coroutineScope.launch {
                    lazyListState.scrollToItem(fvii, fviso)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!autoscrollEnabled) return@LaunchedEffect

        var lastFT = withFrameNanos { it }

        while (true) {
            val scrollValue = withFrameNanos { currentFT ->
                val delta = (currentFT - lastFT) / 1_000_000_000f
                lastFT = currentFT

                position.value?.let Pos@ { pos ->
                    val direction = if (pos > lazyListState.layoutInfo.viewportEndOffset + endAutoscrollBound) 1
                        else if (pos < lazyListState.layoutInfo.viewportStartOffset + startAutoscrollBound) -1
                        else 0

                    autoscrollSpeed * delta * direction
                } ?: 0f
            }

            if (scrollValue == 0f) continue

            itemOffset.value?.plus(scrollValue)?.let {
                itemOffset.value = if (itemIndex.value == 0 && it < 0 ||
                    itemIndex.value == lazyListState.layoutInfo.totalItemsCount - 1 && it > 0
                ) 0f else it
            }

            lazyListState.scrollBy(scrollValue)

            position.value?.let { updateItemIndex(it) }
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
                        position.value = it.offset + it.size / 2f
                        itemOffset.value = 0f
                        itemIndex.value = it.index
                        startIndex.value = it.index
                    }
            },
            onDragEnd = {
                onDragEnd(startIndex.value, itemIndex.value!!)
                position.value = null
                itemIndex.value = null
            },
            onDragCancel = {
                onDragEnd(startIndex.value, itemIndex.value!!)
                position.value = null
                itemIndex.value = null
            },
            onDrag = { change, dragAmount ->
                change.consume()
                position.value = position.value?.plus(dragAmount.y)

                itemOffset.value?.let {
                    val newOffset = it + dragAmount.y

                    if (itemIndex.value == 0 && newOffset < 0 ||
                        itemIndex.value == (lazyListState.layoutInfo.totalItemsCount - 1) && newOffset > 0
                    ) {
                        itemOffset.value = 0f
                        return@detectDragGesturesAfterLongPress
                    }

                    itemOffset.value = newOffset
                }

                position.value?.let { pos ->
                    updateItemIndex(pos)
                }
            }
        )
    }
}

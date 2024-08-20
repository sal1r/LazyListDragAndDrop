package com.example.draganddrop

import android.content.Context
import android.os.Build
import android.view.WindowManager
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
import kotlin.math.abs

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
    val _position: MutableState<Float?>
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
        _position = mutableStateOf(null)
    )
}

/**
 * Adds Drag&Drop support to LazyList. If list have elements of different size it may not work properly
 * @param lazyListState the state of current LazyList
 * @param dragAndDropState state of Drag&Drop. Used to get information about dragging
 * @param onItemsOrderChanged callback where you should reorder list used in LazyList
 */
@Composable
fun Modifier.dragAndDrop(
    lazyListState: LazyListState,
    dragAndDropState: LazyListDragAndDropState,
    onItemsOrderChanged: (lastIndex: Int, newIndex: Int) -> Unit
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

    suspend fun updateItemIndex(newPos: Float) {
        lazyListState.layoutInfo.visibleItemsInfo
            .minByOrNull { info ->
                abs(newPos - (info.offset + info.size / 2f))
            }
            ?.let {
                val fvii = lazyListState.firstVisibleItemIndex
                val fviso = lazyListState.firstVisibleItemScrollOffset
                val lastIndex = itemIndex!!
                val newIndex = it.index
                if (lastIndex != newIndex)
                    itemOffset = itemOffset?.minus(
                        (newIndex - lastIndex) * it.size
                    )

                onItemsOrderChanged(lastIndex, newIndex)

                itemIndex = newIndex

                if (fvii == lastIndex) {
                    lazyListState.scrollToItem(lastIndex, fviso)
                } else if (fvii == newIndex) {
                    lazyListState.scrollToItem(newIndex, fviso)
                }
            }
    }

    LaunchedEffect(Unit) {
        while (true) {
            position?.let Pos@ { pos ->
                val scrollValue = 1500f / fps *
                        if (pos > lazyListState.layoutInfo.viewportEndOffset - 50) 1
                        else if (pos < lazyListState.layoutInfo.viewportStartOffset + 50) -1
                        else 0

                itemOffset?.plus(scrollValue)?.let {
                    if (itemIndex == 0 && it < 0 ||
                        itemIndex == lazyListState.layoutInfo.totalItemsCount - 1 && it > 0
                    ) return@let

                    itemOffset = it
                }

                lazyListState.scrollBy(scrollValue)

                updateItemIndex(pos)
            }
            delay((1000 / fps).toLong())
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
                    }
            },
            onDragEnd = {
                position = null
                itemIndex = null
            },
            onDragCancel = {
                position = null
                itemIndex = null
            },
            onDrag = { change, dragAmount ->
                change.consume()
                coroutineScope.launch {
                    position = position?.plus(dragAmount.y)

                    itemOffset?.let {
                        val newOffset = it + dragAmount.y

                        if (itemIndex == 0 && newOffset < 0 ||
                            itemIndex == (lazyListState.layoutInfo.totalItemsCount - 1) && newOffset > 0
                        ) {
                            itemOffset = 0f
                            return@launch
                        }

                        itemOffset = newOffset
                    }

                    position?.let { pos ->
                        updateItemIndex(pos)
                    }
                }
            }
        )
    }
}
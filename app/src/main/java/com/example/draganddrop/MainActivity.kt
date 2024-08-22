package com.example.draganddrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.draganddrop.ui.theme.DragAndDropTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DragAndDropTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    val lazyListState = rememberLazyListState()
                    val dragAndDropState = rememberLazyListDragAndDropState()
                    var list by remember { mutableStateOf((0..100).toList()) }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                            .dragAndDrop(
                                lazyListState = lazyListState,
                                dragAndDropState = dragAndDropState,
                                onItemsOrderChanged = { lastIndex, newIndex ->
                                    list = list
                                        .toMutableList()
                                        .apply {
                                            this.add(newIndex, this.removeAt(lastIndex))
                                        }
                                }
                            )
                    ) {
                        itemsIndexed(
                            items = list,
                            key = { _, i -> i }
                        ) { idx, i ->
                            val offset =
                                if (idx == dragAndDropState.draggedItemIndex.value)
                                    dragAndDropState.draggedItemOffset.value
                                else null

                            DnDListItem(
                                text = i.toString(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (i % 2 == 0) 96.dp else 64.dp)
                                    .zIndex(offset?.let { 1f } ?: 0f)
                                    .graphicsLayer {
                                        translationY = offset ?: 0f
                                        scaleX = offset?.let { 1.1f } ?: 1f
                                        scaleY = offset?.let { 1.1f } ?: 1f
                                    }
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                                            if (i % 2 == 0) 3.dp else 9.dp
                                        )
                                    )
                                    .then(
                                        offset?.let {
                                            Modifier
                                        } ?: Modifier.animateItemPlacement(
                                            animationSpec = tween(500)
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DnDListItem(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.Center)
        )
    }
}

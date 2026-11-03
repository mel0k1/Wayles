package com.wayles.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wayles.app.td.Msg
import com.wayles.app.td.Td

@Composable
fun ChatScreen() {
    BackHandler { Td.closeChat() }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lastId = Td.messages.lastOrNull()?.id
    val firstId = Td.messages.firstOrNull()?.id
    var prevFirstId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(lastId) {
        if (Td.messages.isNotEmpty()) {
            listState.animateScrollToItem(Td.messages.size - 1)
        }
    }

    LaunchedEffect(firstId) {
        val prev = prevFirstId
        if (prev != null && firstId != null && firstId != prev) {
            val keep = Td.messages.indexOfFirst { it.id == prev }
            if (keep > 0) {
                listState.scrollToItem(keep)
            }
        }
        prevFirstId = firstId
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { idx ->
            if (idx < 10) {
                Td.loadMoreHistory()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { Td.closeChat() }) {
                Text("←", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                Td.openTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(Td.messages, key = { it.id }) { m ->
                Bubble(m)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Сообщение") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            val source = remember { MutableInteractionSource() }
            val radius = rememberPressRadius(source, 28.dp, 14.dp)
            FilledIconButton(
                onClick = {
                    Td.sendText(input)
                    input = ""
                },
                interactionSource = source,
                shape = RoundedCornerShape(radius),
                modifier = Modifier.size(56.dp)
            ) {
                Text("→", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun Bubble(m: Msg) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (m.out) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier.widthIn(max = 320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (m.out) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    m.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (m.out) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    Td.time(m.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

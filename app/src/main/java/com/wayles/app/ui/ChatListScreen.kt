package com.wayles.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayles.app.R
import com.wayles.app.td.Chat
import com.wayles.app.td.Td
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var showTheme by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuChat by remember { mutableStateOf<Chat?>(null) }
    LaunchedEffect(query) {
        if (query.isNotEmpty()) {
            delay(400)
        }
        Td.search(query)
    }
    val q = query.trim().lowercase()
    val local = Td.chats.filter { it.title.lowercase().contains(q) }
    val extra = Td.searchResults.filter { s -> local.none { it.id == s.id } }
    val shown = if (q.isEmpty()) Td.chats else local + extra
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.star),
                            contentDescription = null,
                            modifier = Modifier.height(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Wayles", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = { showTheme = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad)
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Поиск чатов") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (shown.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(R.drawable.star),
                            contentDescription = null,
                            modifier = Modifier.height(64.dp).alpha(0.55f)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (q.isEmpty()) "Пока пусто" else "Ничего не найдено",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (q.isEmpty()) "Здесь появятся ваши чаты" else "Попробуйте другой запрос",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(shown, key = { it.id }) { chat ->
                        ChatRow(chat) { menuChat = chat }
                    }
                }
            }
        }
    }
    menuChat?.let { mc ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { menuChat = null }
        ) {
            DropdownMenuItem(
                text = { Text(if (mc.pinned) "Открепить" else "Закрепить") },
                leadingIcon = {
                    Icon(Icons.Rounded.PushPin, contentDescription = null)
                },
                onClick = {
                    Td.togglePin(mc)
                    menuChat = null
                }
            )
        }
    }
    if (showTheme) {
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("Тема") },
            text = {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = ThemeStore.mode == m,
                            onClick = { ThemeStore.set(m) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = i,
                                count = ThemeMode.entries.size
                            )
                        ) {
                            Text(themeLabel(m))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTheme = false }) {
                    Text("Готово")
                }
            }
        )
    }
}

private fun themeLabel(m: ThemeMode): String = when (m) {
    ThemeMode.System -> "Система"
    ThemeMode.Light -> "Светлая"
    ThemeMode.Dark -> "Тёмная"
}

@Composable
private fun ChatRow(chat: Chat, onLongPress: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { Td.openChat(chat) }
            .pointerInput(chat.id) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initial(chat.title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.pinned) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (chat.lastText.isNotEmpty()) {
                Text(
                    chat.lastText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Td.time(chat.lastDate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (chat.unread > 0) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        chat.unread.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

private fun initial(t: String): String {
    val c = t.trim().take(1)
    return if (c.isEmpty()) "?" else c.uppercase()
}

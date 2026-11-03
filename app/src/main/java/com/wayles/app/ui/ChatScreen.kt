package com.wayles.app.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.wayles.app.td.Msg
import com.wayles.app.td.Td
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    BackHandler(enabled = Td.replyTo != null) { Td.replyTo = null }
    BackHandler { Td.closeChat() }
    var input by remember { mutableStateOf("") }
    var pendingPhoto by remember { mutableStateOf<String?>(null) }
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

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var recSecs by remember { mutableStateOf(0) }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok && recorder.start(scope)) {
            recSecs = 0
            recording = true
        }
    }
    LaunchedEffect(recording) {
        while (recording) {
            delay(1000)
            recSecs++
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val dir = File(ctx.cacheDir, "out")
            dir.mkdirs()
            val video = ctx.contentResolver.getType(uri)?.startsWith("video") == true
            val dst = File(dir, "up_${System.currentTimeMillis()}.${if (video) "mp4" else "jpg"}")
            ctx.contentResolver.openInputStream(uri)?.use { src ->
                dst.outputStream().use { out -> src.copyTo(out) }
            }
            if (video) {
                Td.sendVideo(dst.absolutePath, Td.replyTo?.id ?: 0L)
                Td.replyTo = null
            } else {
                pendingPhoto = dst.absolutePath
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(Td.openTitle, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = { Td.closeChat() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad)
        ) {
            if (Td.messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Сообщений ещё нет", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Напишите первым",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(Td.messages, key = { it.id }) { m ->
                        Bubble(
                            m,
                            Modifier.animateItem(
                                fadeInSpec = spring(stiffness = 400f),
                                placementSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                            )
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = Td.replyTo != null,
                enter = slideInVertically(spring(stiffness = 400f)) { it } + fadeIn(spring(stiffness = 400f)),
                exit = slideOutVertically(spring(stiffness = 400f)) { it } + fadeOut(spring(stiffness = 400f))
            ) {
                val r = Td.replyTo
                if (r != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.width(4.dp)
                                .height(34.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Ответ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                replyPreview(r),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { Td.replyTo = null }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if (recording) {
                    PulsingDot()
                    Spacer(Modifier.width(10.dp))
                    Text(
                        formatDuration(recSecs),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        recorder.cancel()
                        recording = false
                    }) {
                        Text("Отмена")
                    }
                    Spacer(Modifier.width(4.dp))
                    val recSource = remember { MutableInteractionSource() }
                    val recRadius = rememberPressRadius(recSource, 28.dp, 14.dp)
                    FilledIconButton(
                        onClick = {
                            val take = recorder.stop()
                            recording = false
                            if (take != null) {
                                Td.sendVoice(
                                    take.file.absolutePath,
                                    take.seconds,
                                    waveformOf(take.amps),
                                    Td.replyTo?.id ?: 0L
                                )
                                Td.replyTo = null
                            }
                        },
                        interactionSource = recSource,
                        shape = RoundedCornerShape(recRadius),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                    }
                } else {
                    val attachSource = remember { MutableInteractionSource() }
                    val attachRadius = rememberPressRadius(attachSource, 28.dp, 14.dp)
                    FilledIconButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        interactionSource = attachSource,
                        shape = RoundedCornerShape(attachRadius),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
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
                            if (input.isBlank()) {
                                micPerm.launch(android.Manifest.permission.RECORD_AUDIO)
                            } else {
                                Td.sendText(input, Td.replyTo?.id ?: 0L)
                                input = ""
                                Td.replyTo = null
                            }
                        },
                        interactionSource = source,
                        shape = RoundedCornerShape(radius),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (input.isBlank()) {
                                Icons.Rounded.Mic
                            } else {
                                Icons.AutoMirrored.Rounded.Send
                            },
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }

    pendingPhoto?.let { path ->
        Dialog(onDismissRequest = { pendingPhoto = null }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.widthIn(max = 360.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(360.dp)
                            .clip(RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        PreviewPhoto(path)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { pendingPhoto = null }) {
                            Text("Отмена")
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = {
                            pendingPhoto = null
                            Td.sendPhoto(path, Td.replyTo?.id ?: 0L)
                            Td.replyTo = null
                        }) {
                            Text("Отправить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: Msg, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var dragX by remember { mutableStateOf(0f) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .graphicsLayer { translationX = dragX }
            .pointerInput(m.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragX = (dragX + amount).coerceIn(0f, 160f)
                    },
                    onDragEnd = {
                        val fire = dragX > 110f
                        scope.launch {
                            animate(dragX, 0f, animationSpec = spring(stiffness = 500f)) { v, _ ->
                                dragX = v
                            }
                        }
                        if (fire) {
                            Td.replyTo = m
                        }
                    }
                )
            },
        horizontalArrangement = if (m.out) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier.widthIn(max = 320.dp)
                .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f))
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
                m.photoPath?.let { Photo(it) }
                if (m.duration > 0 && (m.videoPath != null || m.videoFileId != 0)) {
                    VideoBlock(m)
                }
                if (m.voiceDuration > 0) {
                    Spacer(Modifier.height(4.dp))
                    VoiceBlock(m)
                }
                if (m.text.isNotEmpty()) {
                    Text(
                        m.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (m.out) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
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

@Composable
private fun Photo(path: String) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { decodePhoto(path) }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        )
    }
}

@Composable
private fun VideoBlock(m: Msg) {
    val ctx = LocalContext.current
    val ready = m.videoPath
    Box(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = ready != null) {
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(ready!!))
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "video/mp4")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(intent)
            }
    ) {
        m.thumbPath?.let { Photo(it) }
        if (m.thumbPath == null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        }
        Box(
            modifier = Modifier.align(Alignment.Center)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            if (ready != null) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }
        Text(
            formatDuration(m.duration),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun formatDuration(s: Int): String {
    val min = s / 60
    val sec = s % 60
    return "%d:%02d".format(min, sec)
}

@Composable
private fun VoiceBlock(m: Msg) {
    var player by remember(m.voicePath) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val ready = m.voicePath
    DisposableEffect(Unit) {
        onDispose {
            player?.release()
        }
    }
    LaunchedEffect(playing, player) {
        while (playing) {
            val p = player
            if (p != null && p.duration > 0) {
                progress = p.currentPosition.toFloat() / p.duration.toFloat()
            }
            delay(50)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(230.dp)
    ) {
        FilledIconButton(
            onClick = {
                if (ready == null) {
                    return@FilledIconButton
                }
                val p = player
                if (p == null) {
                    val np = MediaPlayer()
                    val ok = runCatching {
                        np.setDataSource(ready)
                        np.setOnPreparedListener { mp ->
                            mp.start()
                            playing = true
                        }
                        np.setOnCompletionListener {
                            playing = false
                            progress = 0f
                        }
                        np.prepareAsync()
                    }.isSuccess
                    if (!ok) {
                        np.release()
                    } else {
                        player = np
                    }
                } else if (playing) {
                    p.pause()
                    playing = false
                } else {
                    p.start()
                    playing = true
                }
            },
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            if (ready == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Waveform(
            amps = m.voiceAmps,
            progress = progress,
            active = if (m.out) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.primary
            },
            idle = if (m.out) {
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            formatDuration(m.voiceDuration),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Waveform(
    amps: IntArray,
    progress: Float,
    active: Color,
    idle: Color,
    modifier: Modifier = Modifier
) {
    val bars = remember(amps) {
        if (amps.isEmpty()) IntArray(28) { 10 } else amps
    }
    Canvas(modifier.height(26.dp)) {
        val n = bars.size
        val gap = 2.dp.toPx()
        val w = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val max = (bars.maxOrNull() ?: 1).coerceAtLeast(1)
        val mid = size.height / 2f
        bars.forEachIndexed { i, v ->
            val h = (v.toFloat() / max * size.height).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = if (i < progress * n) active else idle,
                topLeft = Offset(i * (w + gap), mid - h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f)
            )
        }
    }
}

@Composable
private fun PulsingDot() {
    val t = rememberInfiniteTransition(label = "rec")
    val a by t.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier.size(12.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error.copy(alpha = a))
    )
}

private fun replyPreview(m: Msg): String = when {
    m.text.isNotEmpty() -> m.text
    m.photoPath != null || m.fileId != 0 -> "Фото"
    m.voiceDuration > 0 -> "Голосовое"
    m.duration > 0 -> "Видео"
    else -> "Сообщение"
}

@Composable
private fun PreviewPhoto(path: String) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { decodePhoto(path) }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun decodePhoto(path: String): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
}

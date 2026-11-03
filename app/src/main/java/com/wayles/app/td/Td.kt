package com.wayles.app.td

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wayles.app.BuildConfig
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class Auth { Init, Phone, Code, Password, Done }

class Chat {
    var id: Long = 0
    var title: String = ""
    var unread: Int = 0
    var lastText: String = ""
    var lastDate: Int = 0
}

class Msg {
    var id: Long = 0
    var out: Boolean = false
    var text: String = ""
    var date: Int = 0
    var photoPath by mutableStateOf<String?>(null)
    var fileId by mutableStateOf(0)
    var duration by mutableStateOf(0)
    var videoPath by mutableStateOf<String?>(null)
    var thumbPath by mutableStateOf<String?>(null)
    var videoFileId by mutableStateOf(0)
    var thumbFileId by mutableStateOf(0)
    var voicePath by mutableStateOf<String?>(null)
    var voiceFileId by mutableStateOf(0)
    var voiceDuration by mutableStateOf(0)
    var voiceAmps by mutableStateOf(IntArray(0))
}

object Td {

    init {
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(2))
        } catch (_: Throwable) {
        }
    }

    @Volatile
    private var client: Client? = null
    private var dbDir: File? = null
    private var filesRoot: File? = null
    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, (TdApi.Object) -> Unit>()
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    var mediaDir: File? = null
    private val cachePool = Executors.newSingleThreadExecutor()

    var auth by mutableStateOf(Auth.Init)
    var error by mutableStateOf("")
    var openChatId by mutableStateOf<Long?>(null)
    var openTitle by mutableStateOf("")
    var replyTo by mutableStateOf<Msg?>(null)
    private var historyEnd by mutableStateOf(false)
    private val loadingHistory = AtomicBoolean(false)
    val chats = mutableStateListOf<Chat>()
    val messages = mutableStateListOf<Msg>()

    private fun onUpdate(o: TdApi.Object) {
        when (o) {
            is TdApi.UpdateAuthorizationState -> onAuthState(o.authorizationState)
            is TdApi.UpdateNewMessage -> onNewMessage(o.message)
            is TdApi.UpdateChatLastMessage -> onLastMessage(o)
            is TdApi.UpdateChatReadInbox -> onReadInbox(o)
            is TdApi.UpdateChatTitle -> onTitle(o)
            is TdApi.UpdateDeleteMessages -> onDeleted(o)
            is TdApi.UpdateFile -> onFile(o.file)
            else -> {}
        }
    }

    private fun chatById(id: Long): Chat? = chats.firstOrNull { it.id == id }

    private fun onLastMessage(u: TdApi.UpdateChatLastMessage) {
        val row = chatById(u.chatId)
        if (row == null) {
            request(TdApi.GetChat(u.chatId)) { c ->
                if (c is TdApi.Chat) upsert(c)
            }
            return
        }
        val m = u.lastMessage ?: return
        row.lastDate = m.date
        row.lastText = content(m.content)
        chats.sortByDescending { it.lastDate }
    }

    private fun onReadInbox(u: TdApi.UpdateChatReadInbox) {
        chatById(u.chatId)?.unread = u.unreadCount
    }

    private fun onTitle(u: TdApi.UpdateChatTitle) {
        chatById(u.chatId)?.title = u.title
        if (u.chatId == openChatId) {
            openTitle = u.title
        }
    }

    private fun onDeleted(u: TdApi.UpdateDeleteMessages) {
        if (u.chatId != openChatId) {
            return
        }
        val gone = u.messageIds.toSet()
        messages.removeAll { it.id in gone }
    }

    private fun onFile(f: TdApi.File) {
        if (!f.local.isDownloadingCompleted || f.local.path.isEmpty()) {
            return
        }
        messages.forEach { m ->
            if (m.fileId == f.id) {
                m.fileId = 0
                cacheMedia(f.id, f.local.path, "jpg") { m.photoPath = it }
            }
            if (m.thumbFileId == f.id) {
                m.thumbFileId = 0
                cacheMedia(f.id, f.local.path, "jpg") { m.thumbPath = it }
            }
            if (m.videoFileId == f.id) {
                m.videoFileId = 0
                cacheMedia(f.id, f.local.path, "mp4") { m.videoPath = it }
            }
            if (m.voiceFileId == f.id) {
                m.voiceFileId = 0
                cacheMedia(f.id, f.local.path, "oga") { m.voicePath = it }
            }
        }
    }

    private fun cacheMedia(id: Int, src: String, ext: String, set: (String) -> Unit) {
        val dir = mediaDir
        if (dir == null) {
            set(src)
            return
        }
        val dst = File(dir, "m_$id.$ext")
        if (dst.exists()) {
            set(dst.absolutePath)
            return
        }
        cachePool.execute {
            val path = try {
                File(src).copyTo(dst, overwrite = false)
                dst.absolutePath
            } catch (_: Throwable) {
                src
            }
            set(path)
        }
    }

    fun sendPhoto(path: String, replyId: Long) {
        val id = openChatId ?: return
        request(
            TdApi.SendMessage(
                id,
                null,
                if (replyId != 0L) TdApi.InputMessageReplyToMessage(replyId, null, 0, null) else null,
                null,
                null,
                TdApi.InputMessagePhoto(
                    TdApi.InputFileLocal(path),
                    null,
                    null,
                    intArrayOf(),
                    0,
                    0,
                    TdApi.FormattedText("", arrayOf()),
                    false,
                    null,
                    false
                )
            )
        ) { r ->
            if (r is TdApi.Error) error = r.message
        }
    }

    fun sendVideo(path: String, replyId: Long) {
        val id = openChatId ?: return
        request(
            TdApi.SendMessage(
                id,
                null,
                if (replyId != 0L) TdApi.InputMessageReplyToMessage(replyId, null, 0, null) else null,
                null,
                null,
                TdApi.InputMessageVideo(
                    TdApi.InputFileLocal(path),
                    null,
                    null,
                    0,
                    intArrayOf(),
                    0,
                    0,
                    0,
                    true,
                    TdApi.FormattedText("", arrayOf()),
                    false,
                    null,
                    false
                )
            )
        ) { r ->
            if (r is TdApi.Error) error = r.message
        }
    }

    fun sendVoice(path: String, seconds: Int, waveform: ByteArray, replyId: Long) {
        val id = openChatId ?: return
        request(
            TdApi.SendMessage(
                id,
                null,
                if (replyId != 0L) TdApi.InputMessageReplyToMessage(replyId, null, 0, null) else null,
                null,
                null,
                TdApi.InputMessageVoiceNote(
                    TdApi.InputFileLocal(path),
                    seconds,
                    waveform,
                    TdApi.FormattedText("", arrayOf()),
                    null
                )
            )
        ) { r ->
            if (r is TdApi.Error) error = r.message
        }
    }

    private fun onAuthState(s: TdApi.AuthorizationState) {
        when (s) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> setTdlibParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> auth = Auth.Phone
            is TdApi.AuthorizationStateWaitCode -> auth = Auth.Code
            is TdApi.AuthorizationStateWaitPassword -> auth = Auth.Password
            is TdApi.AuthorizationStateReady -> {
                auth = Auth.Done
                loadChats()
            }
            is TdApi.AuthorizationStateClosed -> spawn()
            else -> {}
        }
    }

    private fun setTdlibParameters() {
        val db = dbDir ?: return
        val files = filesRoot ?: return
        request(
            TdApi.SetTdlibParameters(
                false,
                db.absolutePath,
                files.absolutePath,
                null,
                true,
                true,
                true,
                false,
                BuildConfig.TD_API_ID,
                BuildConfig.TD_API_HASH,
                Locale.getDefault().language.ifEmpty { "en" },
                Build.MODEL ?: "Android",
                "Android ${Build.VERSION.RELEASE}",
                BuildConfig.VERSION_NAME
            )
        ) { r ->
            if (r is TdApi.Error) {
                error = r.message
            }
        }
    }

    private fun onNewMessage(m: TdApi.Message) {
        if (m.chatId == openChatId && messages.none { it.id == m.id }) {
            messages.add(toMsg(m))
        }
        request(TdApi.GetChat(m.chatId)) { r ->
            if (r is TdApi.Chat) upsert(r)
        }
    }

    fun start(db: File, files: File) {
        dbDir = db
        filesRoot = files
        if (client == null) {
            spawn()
        }
    }

    private fun spawn() {
        client = Client.create({ onUpdate(it) }, null, null)
    }

    private fun request(f: TdApi.Function<*>, cb: (TdApi.Object) -> Unit) {
        val c = client ?: return
        val key = nextId.getAndIncrement()
        pending[key] = cb
        c.send(f) { r -> pending.remove(key)?.invoke(r) }
    }

    fun time(unix: Int): String = if (unix > 0) fmt.format(Date(unix * 1000L)) else ""

    fun phone(v: String) {
        error = ""
        request(TdApi.SetAuthenticationPhoneNumber(v, null)) { r ->
            if (r is TdApi.Error) error = r.message
        }
    }

    fun code(v: String) {
        error = ""
        request(TdApi.CheckAuthenticationCode(v)) { r ->
            if (r is TdApi.Error) error = r.message
        }
    }

    fun password(v: String) {
        error = ""
        request(TdApi.CheckAuthenticationPassword(v)) { r ->
            if (r is TdApi.Error) error = r.message
        }
    }

    fun loadChats() {
        request(TdApi.GetChats(null, 100)) { r ->
            if (r is TdApi.Chats) {
                r.chatIds.forEach { id ->
                    request(TdApi.GetChat(id)) { c ->
                        if (c is TdApi.Chat) upsert(c)
                    }
                }
            }
        }
    }

    private fun upsert(c: TdApi.Chat) {
        val item = chats.firstOrNull { it.id == c.id } ?: Chat().also { chats.add(it) }
        item.id = c.id
        item.title = c.title
        item.unread = c.unreadCount
        item.lastDate = c.lastMessage?.date ?: 0
        item.lastText = content(c.lastMessage?.content)
        chats.sortByDescending { it.lastDate }
    }

    private fun content(c: TdApi.MessageContent?): String = when (c) {
        is TdApi.MessageText -> c.text.text
        is TdApi.MessagePhoto -> "Фото"
        is TdApi.MessageVideo -> "Видео"
        is TdApi.MessageSticker -> "Стикер"
        is TdApi.MessageVoiceNote -> "Голос"
        is TdApi.MessageDocument -> "Документ"
        is TdApi.MessageAudio -> "Аудио"
        is TdApi.MessageAnimation -> "GIF"
        null -> ""
        else -> "Вложение"
    }

    private fun toMsg(m: TdApi.Message): Msg {
        val msg = Msg()
        msg.id = m.id
        msg.out = m.isOutgoing
        msg.date = m.date
        when (val c = m.content) {
            is TdApi.MessagePhoto -> {
                val f = c.photo.sizes.maxByOrNull { it.width * it.height }?.photo
                if (f != null) {
                    if (f.local.isDownloadingCompleted && f.local.path.isNotEmpty()) {
                        cacheMedia(f.id, f.local.path, "jpg") { msg.photoPath = it }
                    } else {
                        msg.fileId = f.id
                        request(TdApi.DownloadFile(f.id, 32, 0, 0, false)) { }
                    }
                }
            }
            is TdApi.MessageVideo -> {
                val v = c.video
                msg.duration = v.duration
                val t = v.thumbnail
                if (t != null) {
                    val tf = t.file
                    if (tf.local.isDownloadingCompleted && tf.local.path.isNotEmpty()) {
                        cacheMedia(tf.id, tf.local.path, "jpg") { msg.thumbPath = it }
                    } else {
                        msg.thumbFileId = tf.id
                        request(TdApi.DownloadFile(tf.id, 32, 0, 0, false)) { }
                    }
                }
                val vf = v.video
                if (vf.local.isDownloadingCompleted && vf.local.path.isNotEmpty()) {
                    cacheMedia(vf.id, vf.local.path, "mp4") { msg.videoPath = it }
                } else {
                    msg.videoFileId = vf.id
                    request(TdApi.DownloadFile(vf.id, 32, 0, 0, false)) { }
                }
            }
            is TdApi.MessageVoiceNote -> {
                val v = c.voiceNote
                msg.voiceDuration = v.duration
                msg.voiceAmps = IntArray(v.waveform.size) { v.waveform[it].toInt() and 0x1F }
                val vf = v.voice
                if (vf.local.isDownloadingCompleted && vf.local.path.isNotEmpty()) {
                    cacheMedia(vf.id, vf.local.path, "oga") { msg.voicePath = it }
                } else {
                    msg.voiceFileId = vf.id
                    request(TdApi.DownloadFile(vf.id, 32, 0, 0, false)) { }
                }
            }
            else -> msg.text = content(c)
        }
        return msg
    }

    fun openChat(c: Chat) {
        openChatId = c.id
        openTitle = c.title
        messages.clear()
        historyEnd = false
        loadingHistory.set(false)
        request(TdApi.OpenChat(c.id)) { }
        request(TdApi.GetChatHistory(c.id, 0, 0, 50, false)) { r ->
            if (r is TdApi.Messages) {
                val loaded = r.messages.filterNotNull()
                request(TdApi.ViewMessages(c.id, loaded.map { it.id }.toLongArray(), null, false)) { }
                loaded.reversed().forEach { m ->
                    if (messages.none { it.id == m.id }) {
                        messages.add(toMsg(m))
                    }
                }
            }
        }
    }

    fun loadMoreHistory() {
        val id = openChatId ?: return
        val oldest = messages.firstOrNull()?.id ?: return
        if (historyEnd || !loadingHistory.compareAndSet(false, true)) {
            return
        }
        request(TdApi.GetChatHistory(id, oldest, 0, 50, false)) { r ->
            if (r is TdApi.Messages) {
                val older = r.messages.filterNotNull()
                if (older.isEmpty()) {
                    historyEnd = true
                }
                older.forEach { m ->
                    if (messages.none { it.id == m.id }) {
                        messages.add(0, toMsg(m))
                    }
                }
            }
            loadingHistory.set(false)
        }
    }

    fun closeChat() {
        val id = openChatId
        if (id != null) {
            request(TdApi.CloseChat(id)) { }
        }
        openChatId = null
        openTitle = ""
        messages.clear()
    }

    fun sendText(v: String, replyId: Long) {
        val id = openChatId ?: return
        val text = v.trim()
        if (text.isEmpty()) {
            return
        }
        request(
            TdApi.SendMessage(
                id,
                null,
                if (replyId != 0L) TdApi.InputMessageReplyToMessage(replyId, null, 0, null) else null,
                null,
                null,
                TdApi.InputMessageText(TdApi.FormattedText(text, arrayOf()), null, false)
            )
        ) { r ->
            if (r is TdApi.Error) error = r.message
        }
    }
}

package com.wayles.app.td

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wayles.app.BuildConfig
import com.wayles.app.MainActivity
import com.wayles.app.R
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

enum class Auth { Init, Phone, Code, Password, Registration, OtherDevice, Done }

enum class Conn { Unknown, Waiting, Connecting, Ready, Updating }

class Chat {
    var id: Long = 0
    var title: String = ""
    var unread: Int = 0
    var lastText: String = ""
    var lastDate: Int = 0
    var pinned: Boolean = false
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

class MediaItem {
    var msgId: Long = 0
    var isVideo: Boolean = false
    var duration: Int = 0
    var thumbPath by mutableStateOf<String?>(null)
    var fullPath by mutableStateOf<String?>(null)
    var thumbFileId by mutableStateOf(0)
    var fullFileId by mutableStateOf(0)
}

object Td {

    @Volatile
    private var loaded = false
    var initError by mutableStateOf<String?>(null)
        private set

    init {
        if (loadNative()) {
            try {
                Client.execute(TdApi.SetLogVerbosityLevel(2))
            } catch (_: Throwable) {
            }
        }
    }

    @Volatile
    private var client: Client? = null
    private var dbDir: File? = null
    private var filesRoot: File? = null
    private val nextId = AtomicLong(1)
    private val searchGen = AtomicLong(0)
    private val pending = ConcurrentHashMap<Long, (TdApi.Object) -> Unit>()
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val tsFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val uiHandler = Handler(Looper.getMainLooper())
    var mediaDir: File? = null
    var appContext: Context? = null
    @Volatile
    var appOpen: Boolean = false
    private val cachePool = Executors.newSingleThreadExecutor()

    @Volatile
    private var customApiId: Int? = null
    @Volatile
    private var customApiHash: String? = null
    @Volatile
    private var wipeOnClosed = false
    private var tdLogFile: File? = null

    val usingPlaceholder: Boolean
        get() = customApiId == null &&
            BuildConfig.TD_API_ID == 6 &&
            BuildConfig.TD_API_HASH == "eb06d4abfb49dc3eeb1aeb98ae0f581e"

    var credsLabel by mutableStateOf("")
        private set
    var otherLink by mutableStateOf("")
        private set
    var phoneBusy by mutableStateOf(false)
    val diag = mutableStateListOf<String>()
    var tdLogTail by mutableStateOf<List<String>>(emptyList())
        private set

    private fun diagLog(s: String) {
        val line = "${tsFmt.format(Date())}  $s"
        diag.add(line)
        while (diag.size > 60) {
            diag.removeAt(0)
        }
    }

    fun readTdLog() {
        val f = tdLogFile ?: return
        cachePool.execute {
            try {
                tdLogTail = if (f.exists()) f.readLines().takeLast(30) else emptyList()
            } catch (_: Throwable) {
            }
        }
    }

    var auth by mutableStateOf(Auth.Init)
    var error by mutableStateOf("")
    var openChatId by mutableStateOf<Long?>(null)
    var openTitle by mutableStateOf("")
    var replyTo by mutableStateOf<Msg?>(null)
    var connection by mutableStateOf(Conn.Unknown)
    private var historyEnd by mutableStateOf(false)
    private val loadingHistory = AtomicBoolean(false)
    val chats = mutableStateListOf<Chat>()
    val messages = mutableStateListOf<Msg>()
    val searchResults = mutableStateListOf<Chat>()
    val mediaItems = mutableStateListOf<MediaItem>()
    var mediaChatId by mutableStateOf<Long?>(null)

    private fun sortChats() {
        chats.sortWith(compareByDescending<Chat> { it.pinned }.thenByDescending { it.lastDate })
    }

    private fun onUpdate(o: TdApi.Object) {
        when (o) {
            is TdApi.UpdateAuthorizationState -> onAuthState(o.authorizationState)
            is TdApi.UpdateConnectionState -> onConn(o.state)
            is TdApi.UpdateChatPosition -> onPosition(o)
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
        sortChats()
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
        mediaItems.forEach { mi ->
            if (mi.thumbFileId == f.id) {
                mi.thumbFileId = 0
                cacheMedia(f.id, f.local.path, "jpg") { mi.thumbPath = it }
            }
            if (mi.fullFileId == f.id) {
                mi.fullFileId = 0
                cacheMedia(f.id, f.local.path, if (mi.isVideo) "mp4" else "jpg") { mi.fullPath = it }
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
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                diagLog("auth: жду параметры TDLib")
                setTdlibParameters()
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                diagLog("auth: жду номер телефона")
                auth = Auth.Phone
            }
            is TdApi.AuthorizationStateWaitCode -> {
                diagLog("auth: жду код из Telegram")
                auth = Auth.Code
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                diagLog("auth: жду облачный пароль")
                auth = Auth.Password
            }
            is TdApi.AuthorizationStateWaitRegistration -> {
                diagLog("auth: номер не зарегистрирован")
                auth = Auth.Registration
            }
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                diagLog("auth: подтверждение на другом устройстве")
                otherLink = s.link
                auth = Auth.OtherDevice
            }
            is TdApi.AuthorizationStateReady -> {
                diagLog("auth: вход выполнен")
                auth = Auth.Done
                loadChats()
            }
            is TdApi.AuthorizationStateClosed -> {
                diagLog("auth: клиент закрыт")
                if (wipeOnClosed) {
                    wipeOnClosed = false
                    dbDir?.deleteRecursively()
                    diagLog("база данных удалена для новых ключей")
                }
                spawn()
            }
            else -> {}
        }
    }

    private fun onConn(s: TdApi.ConnectionState) {
        val name = when (s) {
            is TdApi.ConnectionStateReady -> "соединение готово"
            is TdApi.ConnectionStateConnecting -> "подключение…"
            is TdApi.ConnectionStateConnectingToProxy -> "подключение через прокси…"
            is TdApi.ConnectionStateWaitingForNetwork -> "нет сети"
            is TdApi.ConnectionStateUpdating -> "обновление данных…"
            else -> "неизвестно"
        }
        diagLog("сеть: $name")
        connection = when (s) {
            is TdApi.ConnectionStateReady -> Conn.Ready
            is TdApi.ConnectionStateConnecting, is TdApi.ConnectionStateConnectingToProxy -> Conn.Connecting
            is TdApi.ConnectionStateWaitingForNetwork -> Conn.Waiting
            is TdApi.ConnectionStateUpdating -> Conn.Updating
            else -> Conn.Unknown
        }
    }

    private fun onPosition(u: TdApi.UpdateChatPosition) {
        if (u.position.list !is TdApi.ChatListMain) {
            return
        }
        val row = chatById(u.chatId) ?: return
        row.pinned = u.position.isPinned
        sortChats()
    }

    private fun setTdlibParameters() {
        val db = dbDir ?: return
        val files = filesRoot ?: return
        val id = customApiId ?: BuildConfig.TD_API_ID
        val hash = customApiHash ?: BuildConfig.TD_API_HASH
        diagLog("setTdlibParameters: api_id=$id")
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
                id,
                hash,
                Locale.getDefault().language.ifEmpty { "en" },
                Build.MODEL ?: "Android",
                "Android ${Build.VERSION.RELEASE}",
                BuildConfig.VERSION_NAME
            )
        ) { r ->
            if (r is TdApi.Error) {
                diagLog("setTdlibParameters ошибка: ${r.code} ${r.message}")
                error = r.message
            } else {
                diagLog("setTdlibParameters: Ok")
            }
        }
    }

    private fun onNewMessage(m: TdApi.Message) {
        if (m.chatId == openChatId && messages.none { it.id == m.id }) {
            messages.add(toMsg(m))
        }
        if (!m.isOutgoing && (!appOpen || m.chatId != openChatId)) {
            notifyMessage(m)
        }
        request(TdApi.GetChat(m.chatId)) { r ->
            if (r is TdApi.Chat) upsert(r)
        }
    }

    private fun notifyMessage(m: TdApi.Message) {
        val ctx = appContext ?: return
        val text = content(m.content)
        if (text.isEmpty()) {
            return
        }
        request(TdApi.GetChat(m.chatId)) { r ->
            if (r !is TdApi.Chat) {
                return@request
            }
            try {
                val pi = PendingIntent.getActivity(
                    ctx,
                    0,
                    Intent(ctx, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val b = NotificationCompat.Builder(ctx, "messages")
                    .setSmallIcon(R.drawable.star)
                    .setContentTitle(r.title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                NotificationManagerCompat.from(ctx).notify((r.id % 100000).toInt(), b.build())
            } catch (_: Throwable) {
            }
        }
    }

    fun start(db: File, files: File) {
        dbDir = db
        filesRoot = files
        val ctx = appContext
        if (ctx != null) {
            try {
                val kf = File(ctx.filesDir, "tdkeys.json")
                if (kf.exists()) {
                    val o = JSONObject(kf.readText())
                    customApiId = o.optInt("id", 0).takeIf { it > 0 }
                    customApiHash = o.optString("hash", "").takeIf { it.isNotEmpty() }
                }
            } catch (_: Throwable) {
            }
            val lf = File(ctx.filesDir, "tdlog.txt")
            tdLogFile = lf
            try {
                Client.execute(TdApi.SetLogStream(TdApi.LogStreamFile(lf.absolutePath, 2_000_000L, false)))
                Client.execute(TdApi.SetLogVerbosityLevel(2))
            } catch (_: Throwable) {
            }
        }
        credsLabel = when {
            customApiId != null -> "Свои API-ключи (api_id=$customApiId)"
            usingPlaceholder -> "Тестовые ключи Telegram (api_id=6) — заблокированы"
            else -> "Ключи сборки (api_id=${BuildConfig.TD_API_ID})"
        }
        diagLog("Старт: $credsLabel")
        if (client == null) {
            spawn()
        }
    }

    private fun loadNative(): Boolean {
        if (loaded) {
            return true
        }
        for (lib in listOf("sslx", "cryptox", "tdjni")) {
            try {
                System.loadLibrary(lib)
            } catch (t: Throwable) {
                initError = "Не удалось загрузить $lib: ${t.message ?: t.javaClass.simpleName}"
                return false
            }
        }
        loaded = true
        return true
    }

    private fun spawn() {
        if (!loadNative()) {
            return
        }
        try {
            client = Client.create({ onUpdate(it) }, null, null)
        } catch (t: Throwable) {
            initError = "TDLib: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    fun retry() {
        initError = null
        if (client == null) {
            spawn()
        }
    }

    fun setApiKeys(idStr: String, hashStr: String): Boolean {
        val id = idStr.trim().toIntOrNull()
        val hash = hashStr.trim()
        if (id == null || id <= 0) {
            error = "API ID — положительное число"
            return false
        }
        if (hash.length !in 32..40 || !hash.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            error = "API HASH — строка из 32 символов 0-9a-f"
            return false
        }
        val ctx = appContext
        if (ctx == null) {
            error = "Приложение ещё не готово"
            return false
        }
        try {
            File(ctx.filesDir, "tdkeys.json")
                .writeText(JSONObject().put("id", id).put("hash", hash).toString())
        } catch (t: Throwable) {
            error = "Не удалось сохранить: ${t.message ?: t.javaClass.simpleName}"
            return false
        }
        customApiId = id
        customApiHash = hash
        credsLabel = "Свои API-ключи (api_id=$id)"
        error = ""
        diagLog("Сохранены свои API-ключи (api_id=$id), перезапуск TDLib…")
        auth = Auth.Init
        initError = null
        otherLink = ""
        val c = client
        if (c == null) {
            wipeOnClosed = false
            dbDir?.deleteRecursively()
            spawn()
        } else {
            wipeOnClosed = true
            request(TdApi.Close()) { }
        }
        return true
    }

    private fun request(f: TdApi.Function<*>, cb: (TdApi.Object) -> Unit) {
        val c = client ?: return
        val key = nextId.getAndIncrement()
        pending[key] = cb
        c.send(f) { r -> pending.remove(key)?.invoke(r) }
    }

    private fun requestAuth(f: TdApi.Function<*>, timeoutMs: Long, cb: (TdApi.Object) -> Unit) {
        val c = client
        if (c == null) {
            cb(TdApi.Error(500, "NO_CLIENT"))
            return
        }
        val key = nextId.getAndIncrement()
        val settled = AtomicBoolean(false)
        pending[key] = { r ->
            if (settled.compareAndSet(false, true)) {
                cb(r)
            }
        }
        uiHandler.postDelayed({
            if (settled.compareAndSet(false, true)) {
                pending.remove(key)
                cb(TdApi.Error(408, "TIMEOUT"))
            }
        }, timeoutMs)
        c.send(f) { r -> pending.remove(key)?.invoke(r) }
    }

    fun time(unix: Int): String = if (unix > 0) fmt.format(Date(unix * 1000L)) else ""

    fun phone(v: String) {
        error = ""
        phoneBusy = true
        diagLog("Отправка номера $v…")
        requestAuth(
            TdApi.SetAuthenticationPhoneNumber(
                normalizePhone(v),
                TdApi.PhoneNumberAuthenticationSettings(false, false, false, false, false, null, null)
            ),
            30000L
        ) { r ->
            phoneBusy = false
            when (r) {
                is TdApi.Error -> {
                    error = r.message
                    diagLog("Номер отклонён: ${r.code} ${r.message}")
                }
                else -> diagLog("Номер принят, жду код")
            }
        }
    }

    private fun normalizePhone(raw: String): String {
        val d = raw.filter { it.isDigit() }
        if (d.isEmpty()) {
            return raw.trim()
        }
        if (raw.trim().startsWith("+")) {
            return "+$d"
        }
        val fixed = if (d.length == 11 && d.startsWith("8")) "7" + d.substring(1) else d
        return "+$fixed"
    }

    fun code(v: String) {
        error = ""
        requestAuth(TdApi.CheckAuthenticationCode(v), 30000L) { r ->
            if (r is TdApi.Error) {
                error = r.message
                diagLog("Код отклонён: ${r.code} ${r.message}")
            } else {
                diagLog("Код принят")
            }
        }
    }

    fun password(v: String) {
        error = ""
        requestAuth(TdApi.CheckAuthenticationPassword(v), 30000L) { r ->
            if (r is TdApi.Error) {
                error = r.message
                diagLog("Пароль отклонён: ${r.code} ${r.message}")
            } else {
                diagLog("Пароль принят")
            }
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
        item.pinned = c.positions.any { it.isPinned && it.list is TdApi.ChatListMain }
        sortChats()
    }

    fun togglePin(c: Chat) {
        val target = !c.pinned
        c.pinned = target
        sortChats()
        request(TdApi.ToggleChatIsPinned(null, c.id, target)) { r ->
            if (r is TdApi.Error) error = r.message
        }
    }

    fun search(q: String) {
        val gen = searchGen.incrementAndGet()
        searchResults.clear()
        val query = q.trim()
        if (query.isEmpty()) {
            return
        }
        request(TdApi.SearchChatsOnServer(query, 20)) { r ->
            if (gen != searchGen.get()) {
                return@request
            }
            if (r is TdApi.Chats) {
                r.chatIds.forEach { id ->
                    request(TdApi.GetChat(id)) { c ->
                        if (gen != searchGen.get()) {
                            return@request
                        }
                        if (c is TdApi.Chat) upsertSearch(c)
                    }
                }
            }
        }
    }

    private fun upsertSearch(c: TdApi.Chat) {
        if (searchResults.any { it.id == c.id }) {
            return
        }
        val item = Chat()
        item.id = c.id
        item.title = c.title
        item.lastDate = c.lastMessage?.date ?: 0
        searchResults.add(item)
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

    fun openMedia(chatId: Long) {
        mediaChatId = chatId
        mediaItems.clear()
        loadMediaPage(chatId, 0, 0)
    }

    fun closeMedia() {
        mediaChatId = null
        mediaItems.clear()
    }

    fun downloadMedia(item: MediaItem) {
        if (item.fullFileId == 0 || item.fullPath != null) {
            return
        }
        request(TdApi.DownloadFile(item.fullFileId, 32, 0, 0, false)) { }
    }

    private fun loadMediaPage(chatId: Long, fromId: Long, depth: Int) {
        if (depth > 5) {
            return
        }
        request(TdApi.GetChatHistory(chatId, fromId, 0, 100, false)) { r ->
            if (mediaChatId != chatId) {
                return@request
            }
            if (r is TdApi.Messages) {
                val list = r.messages.filterNotNull()
                list.filter { it.content is TdApi.MessagePhoto || it.content is TdApi.MessageVideo }
                    .forEach { addMedia(it) }
                if (list.size >= 100) {
                    loadMediaPage(chatId, list.last().id, depth + 1)
                }
            }
        }
    }

    private fun addMedia(m: TdApi.Message) {
        if (mediaItems.any { it.msgId == m.id }) {
            return
        }
        val item = MediaItem()
        item.msgId = m.id
        var thumb: TdApi.File? = null
        var full: TdApi.File? = null
        when (val c = m.content) {
            is TdApi.MessagePhoto -> {
                val sizes = c.photo.sizes.filter { it.width > 0 }
                thumb = sizes.minByOrNull { it.width * it.height }?.photo
                full = sizes.maxByOrNull { it.width * it.height }?.photo
            }
            is TdApi.MessageVideo -> {
                item.isVideo = true
                item.duration = c.video.duration
                thumb = c.video.thumbnail?.file
                full = c.video.video
            }
            else -> {}
        }
        val f = full ?: return
        val t = thumb
        if (t != null) {
            if (t.local.isDownloadingCompleted && t.local.path.isNotEmpty()) {
                cacheMedia(t.id, t.local.path, "jpg") { item.thumbPath = it }
            } else {
                item.thumbFileId = t.id
                request(TdApi.DownloadFile(t.id, 1, 0, 0, false)) { }
            }
        }
        if (f.local.isDownloadingCompleted && f.local.path.isNotEmpty()) {
            cacheMedia(f.id, f.local.path, if (item.isVideo) "mp4" else "jpg") { item.fullPath = it }
        } else {
            item.fullFileId = f.id
        }
        mediaItems.add(item)
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

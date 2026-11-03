package com.wayles.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayles.app.R
import com.wayles.app.td.Auth
import com.wayles.app.td.Conn
import com.wayles.app.td.Td

@Composable
fun LoginScreen() {
    val step = Td.auth
    var value by remember(step) { mutableStateOf("") }
    var showKeys by rememberSaveable { mutableStateOf(false) }
    var showDiag by rememberSaveable { mutableStateOf(false) }
    var keyId by remember(showKeys) { mutableStateOf("") }
    var keyHash by remember(showKeys) { mutableStateOf("") }

    LaunchedEffect(showDiag) {
        if (showDiag) {
            Td.readTdLog()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.star),
            contentDescription = null,
            modifier = Modifier.height(96.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text("Wayles", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            hint(step),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (Td.connection != Conn.Ready && step != Auth.Password) {
            Spacer(Modifier.height(6.dp))
            Text(
                connText(Td.connection),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (step == Auth.Init) {
            Spacer(Modifier.height(24.dp))
            val err = Td.initError
            if (err != null) {
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                WaylesButton(text = "Повторить", onClick = { Td.retry() })
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (step == Auth.OtherDevice) {
            Spacer(Modifier.height(16.dp))
            Text(
                Td.otherLink,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
        if (step == Auth.Registration) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Сначала зарегистрируйте этот номер в официальном приложении Telegram, затем вернитесь сюда",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(32.dp))
        if (step == Auth.Phone || step == Auth.Code || step == Auth.Password) {
            val flagIcon: (@Composable () -> Unit)? = if (step == Auth.Phone) {
                { Text(CountryFlags.flag(value), fontSize = 22.sp) }
            } else {
                null
            }
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    Td.error = ""
                },
                label = { Text(label(step)) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                leadingIcon = flagIcon,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (step == Auth.Phone) KeyboardType.Phone else KeyboardType.Text
                ),
                visualTransformation = if (step == Auth.Password) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            WaylesButton(
                text = if (step == Auth.Phone && Td.phoneBusy) "Отправка…" else "Продолжить",
                enabled = value.isNotBlank() && !(step == Auth.Phone && Td.phoneBusy),
                onClick = {
                    when (step) {
                        Auth.Phone -> Td.phone(value.trim())
                        Auth.Code -> Td.code(value.trim())
                        Auth.Password -> Td.password(value)
                        else -> {}
                    }
                    value = ""
                }
            )
        }
        if (Td.error.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                prettyError(Td.error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
        if (step != Auth.Done) {
            if (Td.usingPlaceholder) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Эта сборка использует тестовые API-ключи Telegram, которые заблокированы — код входа не придёт. Получите свои ключи на my.telegram.org и укажите их ниже.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showKeys = !showKeys }) {
                    Text(if (showKeys) "Скрыть API-ключи" else "Указать API-ключи")
                }
            }
            if (showKeys) {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = keyId,
                    onValueChange = { keyId = it.filter { ch -> ch.isDigit() } },
                    label = { Text("API ID") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyHash,
                    onValueChange = { keyHash = it.trim() },
                    label = { Text("API HASH") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                WaylesButton(
                    text = "Сохранить и перезапустить",
                    enabled = keyId.isNotBlank() && keyHash.isNotBlank(),
                    onClick = {
                        if (Td.setApiKeys(keyId, keyHash)) {
                            showKeys = false
                            keyId = ""
                            keyHash = ""
                        }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showDiag = !showDiag }) {
                Text(if (showDiag) "Скрыть диагностику" else "Диагностика")
            }
            if (showDiag) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        "${Td.credsLabel} · ${Td.connection} · ${step}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Td.diag.takeLast(14).forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Td.tdLogTail.takeLast(10).forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { Td.readTdLog() }) {
                        Text("Обновить лог")
                    }
                }
            }
        }
    }
}

private fun hint(s: Auth): String = when (s) {
    Auth.Init -> "Подключение к Telegram"
    Auth.Phone -> "Войдите через номер Telegram"
    Auth.Code -> "Введите код из Telegram"
    Auth.Password -> "Введите облачный пароль"
    Auth.Registration -> "Номер не зарегистрирован"
    Auth.OtherDevice -> "Подтвердите вход на другом устройстве"
    else -> ""
}

private fun label(s: Auth): String = when (s) {
    Auth.Phone -> "Номер"
    Auth.Code -> "Код"
    Auth.Password -> "Пароль"
    else -> ""
}

private fun connText(c: Conn): String = when (c) {
    Conn.Waiting -> "Нет связи с Telegram — проверьте интернет"
    Conn.Connecting -> "Подключение к Telegram…"
    Conn.Updating -> "Обновление данных…"
    else -> ""
}

private fun prettyError(e: String): String = when {
    e.contains("RECAPTCHA", ignoreCase = true) ->
        "Telegram требует капчу: ключи этой сборки заблокированы. Укажите свои API-ключи"
    e == "API_ID_INVALID" || e == "API_ID_PUBLISHED_FLOOD" ->
        "API-ключи отклонены Telegram. Укажите свои ключи на my.telegram.org"
    e == "PHONE_NUMBER_INVALID" -> "Неверный номер. Укажите код страны, например +79991234567"
    e == "PHONE_NUMBER_FLOOD" -> "Слишком много попыток входа. Попробуйте позже"
    e == "PHONE_CODE_INVALID" -> "Неверный код"
    e == "PHONE_CODE_EXPIRED" -> "Код истёк. Войдите заново"
    e == "PASSWORD_HASH_INVALID" -> "Неверный пароль"
    e == "PHONE_NUMBER_UNOCCUPIED" -> "Номер не зарегистрирован в Telegram"
    e == "TIMEOUT" -> "Telegram не ответил за 30 секунд. Проверьте интернет и попробуйте снова"
    e == "NO_CLIENT" -> "Клиент не запущен. Попробуйте снова"
    else -> e
}

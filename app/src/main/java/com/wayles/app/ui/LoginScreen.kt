package com.wayles.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
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
        Spacer(Modifier.height(32.dp))
        if (step != Auth.Init) {
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
    }
}

private fun hint(s: Auth): String = when (s) {
    Auth.Init -> "Подключение к Telegram"
    Auth.Phone -> "Войдите через номер Telegram"
    Auth.Code -> "Введите код из Telegram"
    Auth.Password -> "Введите облачный пароль"
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

private fun prettyError(e: String): String = when (e) {
    "PHONE_NUMBER_INVALID" -> "Неверный номер. Укажите код страны, например +79991234567"
    "PHONE_NUMBER_FLOOD" -> "Слишком много попыток входа. Попробуйте позже"
    "PHONE_CODE_INVALID" -> "Неверный код"
    "PHONE_CODE_EXPIRED" -> "Код истёк. Войдите заново"
    "PASSWORD_HASH_INVALID" -> "Неверный пароль"
    "PHONE_NUMBER_UNOCCUPIED" -> "Номер не зарегистрирован в Telegram"
    else -> e
}

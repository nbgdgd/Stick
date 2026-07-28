package com.trialtracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trialtracker.app.BuildConfig
import com.trialtracker.app.ui.SourceStatus
import com.trialtracker.app.ui.UiState
import com.trialtracker.app.ui.components.SurfaceCard
import com.trialtracker.app.ui.theme.TT
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    state: UiState,
    sources: List<SourceStatus>,
    onNameChange: (String) -> Unit,
    onShowSystemAppsChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    var name by remember(state.settings.userName) { mutableStateOf(state.settings.userName) }

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Настройки", "Проверка каталога, приватность и вид списка") }

        item {
            SettingsGroup("Профиль") {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onNameChange(it)
                    },
                    label = { Text("Как к вам обращаться", color = TT.TextTertiary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = TT.SurfaceHigh,
                        unfocusedContainerColor = TT.SurfaceHigh,
                        focusedBorderColor = TT.Accent,
                        unfocusedBorderColor = TT.Border,
                        focusedTextColor = TT.TextPrimary,
                        unfocusedTextColor = TT.TextPrimary,
                        cursorColor = TT.Accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            SettingsGroup("Проверка предложений") {
                ToggleRow(
                    title = "Фоновая проверка и уведомления",
                    subtitle = "Сообщать о новых акциях для установленных приложений",
                    checked = state.settings.notificationsEnabled,
                    onChange = onNotificationsChange,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Периодичность",
                    style = MaterialTheme.typography.bodySmall,
                    color = TT.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6 to "6 ч", 12 to "12 ч", 24 to "24 ч", 72 to "3 дня").forEach { (hours, label) ->
                        val selected = state.settings.checkIntervalHours == hours
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) TT.Accent.copy(alpha = 0.18f) else TT.SurfaceHigh)
                                .clickable { onIntervalChange(hours) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = label,
                                color = if (selected) TT.Accent else TT.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Последнее обновление: ${formatSync(state.settings.lastSyncAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TT.TextTertiary,
                )
            }
        }

        item {
            SettingsGroup("Список приложений") {
                ToggleRow(
                    title = "Показывать системные приложения",
                    subtitle = "По умолчанию системные приложения скрыты",
                    checked = state.settings.showSystemApps,
                    onChange = onShowSystemAppsChange,
                )
            }
        }

        item {
            SettingsGroup("Приватность") {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Lock,
                        null,
                        tint = TT.Green,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Список ваших приложений не покидает устройство.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TT.TextPrimary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Сканирование выполняется локально через PackageManager. " +
                                "Разрешение QUERY_ALL_PACKAGES не запрашивается: в манифесте " +
                                "перечислены пакеты каталога плюс запрос по launcher-интенту — " +
                                "он нужен, чтобы сопоставлять свежие акции из фидов, пакеты " +
                                "которых заранее неизвестны.\n\n" +
                                "Каталог и фиды — публичные данные. Они скачиваются целиком " +
                                "и не связаны с тем, что установлено у вас: сопоставление " +
                                "происходит уже на устройстве.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TT.TextSecondary,
                        )
                    }
                }
            }
        }

        item {
            SettingsGroup("Источники данных") {
                sources.forEachIndexed { index, source ->
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (source.ok) TT.Green else TT.Red),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                source.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TT.TextPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                source.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = TT.TextSecondary,
                            )
                        }
                        Text(
                            text = if (source.count > 0) source.count.toString() else "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (source.ok) TT.TextPrimary else TT.TextTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Скидки читаются из публичных Atom-фидов Reddit и разбираются " +
                        "на устройстве: из заголовка берутся цены, из ссылки — package name " +
                        "приложения в Google Play, из времени поста — дата проверки.\n\n" +
                        "Пробные подписки так получить нельзя: машиночитаемого источника " +
                        "с триалами не существует, поэтому они остаются в каталоге, который " +
                        "ведётся вручную.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TT.TextTertiary,
                )
            }
        }

        item {
            Text(
                text = "Версия ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = TT.TextTertiary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = TT.TextTertiary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TT.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TT.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TT.TextPrimary,
                checkedTrackColor = TT.Accent,
                uncheckedThumbColor = TT.TextSecondary,
                uncheckedTrackColor = TT.SurfaceHigh,
                uncheckedBorderColor = TT.Border,
            ),
        )
    }
}

private fun formatSync(millis: Long): String =
    if (millis <= 0L) "ещё не выполнялось"
    else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(millis))

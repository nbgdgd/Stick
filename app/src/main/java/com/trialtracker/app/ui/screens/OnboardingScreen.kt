package com.trialtracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trialtracker.app.ui.components.IconTile
import com.trialtracker.app.ui.components.SurfaceCard
import com.trialtracker.app.ui.theme.TT

/**
 * First-run screen. Explains up front what is scanned and what leaves the device,
 * because "we look at your installed apps" needs consent framing, not a surprise.
 */
@Composable
fun OnboardingScreen(onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TT.Background)
            .verticalScroll(scroll)
            .padding(horizontal = 22.dp, vertical = 36.dp),
    ) {
        Text(
            text = "Триалы и акции\nдля ваших приложений",
            style = MaterialTheme.typography.headlineMedium,
            color = TT.TextPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Мы смотрим, какие приложения из нашего каталога есть на устройстве, " +
                "и показываем для них пробные подписки и скидки.",
            style = MaterialTheme.typography.bodyMedium,
            color = TT.TextSecondary,
        )
        Spacer(Modifier.height(26.dp))

        Bullet(
            icon = Icons.Rounded.Lock,
            tint = TT.Green,
            title = "Всё остаётся на устройстве",
            text = "Список приложений обрабатывается локально и никуда не отправляется. " +
                "Разрешение QUERY_ALL_PACKAGES не запрашивается — в манифесте перечислены " +
                "только пакеты из каталога.",
        )
        Spacer(Modifier.height(12.dp))
        Bullet(
            icon = Icons.Rounded.CardGiftcard,
            tint = TT.Accent,
            title = "Каталог проверяется вручную",
            text = "Единого API с актуальными триалами не существует. У каждого предложения " +
                "видна дата последней проверки — условия меняются и зависят от региона.",
        )
        Spacer(Modifier.height(12.dp))
        Bullet(
            icon = Icons.Rounded.Notifications,
            tint = TT.Blue,
            title = "Уведомления — по желанию",
            text = "Фоновая проверка каталога включается и выключается в настройках.",
        )

        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Как к вам обращаться (необязательно)", color = TT.TextTertiary) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TT.Surface,
                unfocusedContainerColor = TT.Surface,
                focusedBorderColor = TT.Accent,
                unfocusedBorderColor = TT.Border,
                focusedTextColor = TT.TextPrimary,
                unfocusedTextColor = TT.TextPrimary,
                cursorColor = TT.Accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(TT.Accent)
                .clickable { onDone(name) }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Начать",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Bullet(icon: ImageVector, tint: Color, title: String, text: String) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
            IconTile(tint = tint, size = 42, corner = 13) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TT.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = TT.TextSecondary)
            }
        }
    }
}

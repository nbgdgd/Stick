package com.vpet.waifu.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Дизайн-токены редизайна перед релизом в Google Play.
 *
 * Полная спецификация — docs/redesign.md. Это целевая система; существующие
 * Accents/Surfaces/StatColors из Theme.kt переводятся на эти значения по мере
 * миграции экранов и затем удаляются.
 *
 * Правила системы:
 *  - один акцент ([Semantic.Accent]) поверх нейтрального тёмного фона;
 *  - градиенты только в арте персонажа и на главном CTA;
 *  - шкала отступов 4/8/12/16/24 — других значений в вёрстке нет;
 *  - радиусы: 12 (контролы и тайлы), 16 (карточки), 24 (панели/навбар/поле
 *    игры), пилюли — RoundedCornerShape(50);
 *  - типографика: 3 размера × 2 веса, гарнитура Golos Text (OFL, кириллица).
 */
object Tokens {

    /** Нейтральная база. Без фиолетового подмеса — чистые серые. */
    object Neutral {
        val Bg = Color(0xFF0E0E12)
        val Surface = Color(0xFF16161C)
        val SurfaceHigh = Color(0xFF1D1D24)
        val Border = Color(0xFF262630)
        val BorderStrong = Color(0xFF32323E)
        val Track = Color(0xFF24242C)
        val Text = Color(0xFFF2F2F5)
        val TextSecondary = Color(0xFFA8A8B3)
        val TextDisabled = Color(0xFF62626D)
    }

    /** Вариант A — «Янтарь». */
    object Amber {
        val Accent = Color(0xFFF5A524)
        val AccentPressed = Color(0xFFD88C10)
        val OnAccent = Color(0xFF1F1300)
    }

    /** Вариант B — «Малина». Выбранный акцент приложения. */
    object Raspberry {
        val Accent = Color(0xFFE8517E)
        val AccentPressed = Color(0xFFC73A66)
        val OnAccent = Color(0xFF2A0714)
    }

    /** Вариант C — «Бирюза». */
    object Teal {
        val Accent = Color(0xFF35C9B4)
        val AccentPressed = Color(0xFF21A392)
        val OnAccent = Color(0xFF04211C)
    }

    /**
     * Семантические роли. Единственное место, где выбирается палитра:
     * смена варианта — замена трёх ссылок ниже.
     */
    object Semantic {
        val Accent = Raspberry.Accent
        val AccentPressed = Raspberry.AccentPressed
        val OnAccent = Raspberry.OnAccent

        val Danger = Color(0xFFFF6740)
        val Success = Color(0xFF4CB782)

        // Статы согласованы с акцентом: настроение — это и есть бренд-эмоция.
        val Hunger = Color(0xFFE8934A)
        val Energy = Color(0xFF54B8E8)
        val Mood = Raspberry.Accent
        val Money = Color(0xFFE8C15C)
        val Exp = Color(0xFF5FC98A)
    }

    /** Шкала отступов. Использовать только эти значения. */
    object Space {
        val Xs = 4.dp
        val S = 8.dp
        val M = 12.dp
        val L = 16.dp
        val Xl = 24.dp
    }

    /** Радиусы. Пилюли — RoundedCornerShape(50) поверх этой шкалы. */
    object Radius {
        val Control = 12.dp
        val Card = 16.dp
        val Panel = 24.dp
    }

    /**
     * Типографика: 3 размера, 2 веса. Гарнитура Golos Text подключается в
     * Theme.kt (res/font), здесь только шкала.
     */
    object Type {
        val Title = 20.sp
        val Body = 15.sp
        val Label = 12.sp
        val Regular = FontWeight.Normal
        val Strong = FontWeight.SemiBold
    }
}

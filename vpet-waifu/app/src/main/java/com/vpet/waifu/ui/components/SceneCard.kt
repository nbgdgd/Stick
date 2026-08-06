package com.vpet.waifu.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.SceneKind
import com.vpet.waifu.domain.SceneOption
import com.vpet.waifu.domain.SceneOutcome
import com.vpet.waifu.domain.Scenes
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.StatColors

/**
 * The day's small moment, with two ways to answer.
 *
 * The one place in the game that asks the player a question, and the card is
 * built around the rule that makes asking safe: **both answers are shown with
 * what they give.** No hidden right choice, no consequence revealed after the
 * fact. A two-option prompt with a concealed correct answer is how a game
 * teaches people to distrust its prompts, and this one exists to make her read
 * as a person rather than a set of bars.
 *
 * Shown once a day and then gone until tomorrow — see [Scenes].
 */
@Composable
fun SceneCard(
    scene: SceneKind,
    level: Int,
    onAnswer: (SceneOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        modifier = modifier,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        PanelCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Accents.Bright,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.scene_header),
                        style = MaterialTheme.typography.labelSmall,
                        color = Accents.Bright,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(scene.promptRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accents.Text,
                )
                Spacer(Modifier.height(12.dp))

                SceneOption.entries.forEach { option ->
                    SceneReply(
                        text = stringResource(scene.replyRes(option)),
                        outcome = Scenes.outcomeOf(scene, option, level),
                        onClick = { onAnswer(option) },
                    )
                    if (option == SceneOption.FIRST) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SceneReply(text: String, outcome: SceneOutcome, onClick: () -> Unit) {
    Column {
        OutlineButton(
            text = text,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        // What it gives, under the button rather than inside it: the sentence
        // is the choice, the numbers are the footnote.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            outcome.chips().forEach { (label, tint) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Every non-zero part of an outcome, in the colour that part always wears. */
private fun SceneOutcome.chips(): List<Pair<String, androidx.compose.ui.graphics.Color>> = buildList {
    if (money != 0) add(signed(money, "¥") to StatColors.Money)
    if (exp != 0) add(signed(exp, "EXP") to StatColors.Exp)
    if (mood != 0f) add(signed(mood.toInt(), "♥") to StatColors.Mood)
    if (energy != 0f) add(signed(energy.toInt(), "⚡") to StatColors.Energy)
    if (bond != 0) add(signed(bond, "❤") to StatColors.Mood)
}

private fun signed(value: Int, unit: String): String =
    if (value >= 0) "+$value $unit" else "$value $unit"

@StringRes
private fun SceneKind.promptRes(): Int = when (this) {
    SceneKind.LONG_DAY -> R.string.scene_long_day
    SceneKind.WINDOW_SHOPPING -> R.string.scene_window
    SceneKind.NEIGHBOUR -> R.string.scene_neighbour
    SceneKind.STUCK_ON_A_PAGE -> R.string.scene_page
    SceneKind.RAINY_AFTERNOON -> R.string.scene_rain
}

@StringRes
private fun SceneKind.replyRes(option: SceneOption): Int = when (this) {
    SceneKind.LONG_DAY ->
        if (option == SceneOption.FIRST) R.string.scene_long_day_a else R.string.scene_long_day_b
    SceneKind.WINDOW_SHOPPING ->
        if (option == SceneOption.FIRST) R.string.scene_window_a else R.string.scene_window_b
    SceneKind.NEIGHBOUR ->
        if (option == SceneOption.FIRST) R.string.scene_neighbour_a else R.string.scene_neighbour_b
    SceneKind.STUCK_ON_A_PAGE ->
        if (option == SceneOption.FIRST) R.string.scene_page_a else R.string.scene_page_b
    SceneKind.RAINY_AFTERNOON ->
        if (option == SceneOption.FIRST) R.string.scene_rain_a else R.string.scene_rain_b
}

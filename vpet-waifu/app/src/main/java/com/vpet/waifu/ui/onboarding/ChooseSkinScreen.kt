package com.vpet.waifu.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vpet.waifu.R
import com.vpet.waifu.domain.PetState
import com.vpet.waifu.ui.character.PetFigure
import com.vpet.waifu.ui.character.PetSkin
import com.vpet.waifu.ui.character.SpritePacks
import com.vpet.waifu.ui.components.PrimaryButton
import com.vpet.waifu.ui.theme.Accents
import com.vpet.waifu.ui.theme.Surfaces

/**
 * "Who are you adopting?"
 *
 * The first screen of a new save, and the only one that ever shows. Skins were
 * buried three taps deep in Settings, which meant almost nobody knew there was
 * a choice — and a game about living with one particular character should
 * probably let you meet her before it starts.
 *
 * Each card draws the **actual rig**, animating, rather than a screenshot of
 * it. Nothing here can go stale when the art changes, and what you tap is
 * literally what you get.
 *
 * The classic girl is first and pre-selected. She is the softer, rounder of the
 * two and reads as the friendlier default; the redrawn one is the sharper,
 * taller take. There is no wrong answer and no consequence — it can be changed
 * in Settings at any time, which the footer says out loud so nobody agonises.
 */
@Composable
fun ChooseSkinScreen(
    onChosen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Any installed sprite pack is offered too, so a side-loaded pack is a
    // first-class choice rather than something to go and find afterwards.
    val packs = remember { SpritePacks.installedIds(context) }
    var picked by remember { mutableStateOf(PetSkin.CLASSIC_ID) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Surfaces.Screen)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboard_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Accents.Text,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.onboard_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Accents.TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkinChoice(
                skin = PetSkin.Classic,
                title = stringResource(R.string.skin_classic),
                blurb = stringResource(R.string.onboard_classic_blurb),
                chosen = picked == PetSkin.CLASSIC_ID,
                onClick = { picked = PetSkin.CLASSIC_ID },
                modifier = Modifier.weight(1f),
            )
            SkinChoice(
                skin = PetSkin.Modern,
                title = stringResource(R.string.skin_modern),
                blurb = stringResource(R.string.onboard_modern_blurb),
                chosen = picked == PetSkin.MODERN_ID,
                onClick = { picked = PetSkin.MODERN_ID },
                modifier = Modifier.weight(1f),
            )
        }

        packs.forEach { id ->
            val pack = remember(id) { SpritePacks.load(context, id) }
            if (pack != null) {
                Spacer(Modifier.height(12.dp))
                SkinChoice(
                    skin = PetSkin.Sheet(pack),
                    title = pack.displayName,
                    blurb = stringResource(R.string.onboard_pack_blurb),
                    chosen = picked == id,
                    onClick = { picked = id },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            text = stringResource(R.string.onboard_start),
            onClick = { onChosen(picked) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.onboard_changeable),
            style = MaterialTheme.typography.bodySmall,
            color = Accents.TextDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SkinChoice(
    skin: PetSkin,
    title: String,
    blurb: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The chosen card lifts slightly. Small, because two cards side by side
    // that jump around while you compare them are harder to compare.
    val lift by animateFloatAsState(if (chosen) 1f else 0f, label = "choice")
    val border = if (chosen) Accents.Bright else Surfaces.CardBorder

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (chosen) Accents.Primary.copy(alpha = 0.10f) else Surfaces.Card)
            .border((1 + lift).dp, border.copy(alpha = 0.4f + 0.5f * lift), RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            contentAlignment = Alignment.Center,
        ) {
            // The living rig, not a picture of it: this cannot go out of date
            // when the art changes, and what you tap is what you get.
            PetFigure(
                skin = skin,
                state = PetState.HAPPY,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Accents.Text,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = blurb,
            style = MaterialTheme.typography.bodySmall,
            color = Accents.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

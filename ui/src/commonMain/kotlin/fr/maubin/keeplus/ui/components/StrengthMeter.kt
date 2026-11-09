package fr.maubin.keeplus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.generator.Strength
import fr.maubin.keeplus.generator.strengthOf
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType

/*
	Libellé d'une force de mot de passe.
*/
fun Strength.label(): String =
	when (this)
	{
		Strength.WEAK   -> "Faible"
		Strength.FAIR   -> "Moyen"
		Strength.GOOD   -> "Bon"
		Strength.STRONG -> "Excellent"
	}

/*
	Couleur d'une force : rouge sourd pour faible, vert sourd pour
	excellent, gris clair entre les deux.
*/
private fun Strength.color(): Color =
	when (this)
	{
		Strength.WEAK   -> KeeColors.danger
		Strength.FAIR   -> KeeColors.textMuted
		Strength.GOOD   -> KeeColors.text
		Strength.STRONG -> KeeColors.success
	}

/*
	Jauge de force en quatre segments, avec son libellé et l'entropie (la
	couleur ne porte jamais seule l'information).
	inBits     : entropie estimée, en bits ; 0 pour un champ vide
	inModifier : modificateur
*/
@Composable
fun StrengthMeter(
	inBits     : Double,
	inModifier : Modifier = Modifier
)
{
	val vStrength = strengthOf(inBits)													// force
	val vFilled   = if (inBits <= 0.0) 0 else vStrength.ordinal + 1						// segments pleins
	val vColor    by animateColorAsState(vStrength.color())								// couleur des segments
	Column(inModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
			repeat(4) { vIndex ->
				Box(
					Modifier
						.weight(1f)
						.height(3.dp)
						.clip(RoundedCornerShape(2.dp))
						.background(if (vIndex < vFilled) vColor else KeeColors.rule)
				)
			}
		}
		val vText = if (inBits <= 0.0) "Force du mot de passe" else "${vStrength.label()} · ${inBits.toInt()} bits"	// libellé
		BasicText(vText, style = keeType().bodySmall)
	}
}

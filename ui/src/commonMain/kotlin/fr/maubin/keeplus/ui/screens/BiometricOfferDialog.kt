package fr.maubin.keeplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.maubin.keeplus.ui.components.GhostButton
import fr.maubin.keeplus.ui.components.SilverButton
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType

/*
	Proposition, une seule fois après un déverrouillage par mot de passe,
	d'ouvrir le coffre par l'empreinte la prochaine fois.
	inOnAnswer : réponse (vrai : activer)
*/
@Composable
fun BiometricOfferDialog(inOnAnswer: (Boolean) -> Unit)
{
	val vType = keeType()																	// styles de texte
	Dialog(onDismissRequest = { inOnAnswer(false) }) {
		Column(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(KeeColors.surfaceHigh).padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			Icon(LineIcons.Fingerprint, null, Modifier.size(36.dp), tint = KeeColors.text)
			BasicText("Déverrouiller avec l'empreinte ?", style = vType.headline)
			BasicText(
				"Votre mot de passe maître sera gardé sur ce téléphone, chiffré par une clé qui ne s'ouvre qu'avec votre empreinte. Si une empreinte est ajoutée au téléphone, il faudra le ressaisir.",
				style = vType.bodySmall
			)
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				GhostButton("Plus tard", { inOnAnswer(false) }, Modifier.weight(1f))
				SilverButton("Activer", { inOnAnswer(true) }, Modifier.weight(1f))
			}
		}
	}
}

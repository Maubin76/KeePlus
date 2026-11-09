package fr.maubin.keeplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.ui.theme.KeeBrand
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType

/*
	Écran provisoire de l'étape 1 : brume, logo et titre en deux tons, pour
	vérifier le thème et les polices sur Android et sur PC. Remplacé par
	l'écran de déverrouillage à l'étape suivante.
*/
@Composable
fun WelcomePreview()
{
	val vType = keeType()																	// styles de texte
	Box(
		Modifier
			.fillMaxSize()
			.background(Brush.verticalGradient(0f to KeeColors.mistTop, 0.4f to KeeColors.mistMid, 0.7f to KeeColors.void))
			.safeDrawingPadding()
			.padding(24.dp)
	) {
		KeeBrand()
		Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(16.dp)) {
			BasicText(
				buildAnnotatedString {
					append("Vos mots de passe,\n")
					withStyle(SpanStyle(color = KeeColors.textMuted)) { append("bien ") }
					append("gardés.")
				},
				style = vType.hero
			)
			BasicText("Chez vous, sur votre PC et votre téléphone. Sans cloud, sans compte.", style = vType.bodySmall)
		}
	}
}

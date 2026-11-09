package fr.maubin.keeplus.ui.screens

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.ui.components.KeeBackdrop
import fr.maubin.keeplus.ui.components.trackPointer
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.platform.animationsEnabled
import fr.maubin.keeplus.ui.platform.rememberTilt
import fr.maubin.keeplus.ui.theme.KeeBrand
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType

/*
	Gabarit des écrans clés (accueil, création, déverrouillage) : décor
	complet avec parallaxe (inclinaison du téléphone ou souris), marque en
	haut à gauche, contenu calé en bas, au-dessus du clavier, sur 440 dp au
	plus.
	inGlow    : avancée de la vague de la grille (voir KeeBackdrop)
	inFade    : dissipation du décor (voir KeeBackdrop)
	inOnBack  : retour (flèche à gauche de la marque) ; null pour aucun
	inContent : contenu, en colonne
*/
@Composable
fun KeyScreen(
	inGlow    : Float           = -1f,
	inFade    : Float           = 0f,
	inOnBack  : (() -> Unit)?   = null,
	inContent : @Composable ColumnScope.() -> Unit
)
{
	val vAnimated = animationsEnabled()														// effets marqués permis
	val vTilt     = rememberTilt()																// inclinaison (téléphone)
	val vPointer  = remember { mutableStateOf(Offset.Zero) }								// souris (PC)
	val vTarget   = when
	{
		!vAnimated    -> Offset.Zero
		vTilt != null -> vTilt.value
		else          -> vPointer.value
	}																						// décalage voulu
	val vParallax by animateOffsetAsState(vTarget, tween(if (vTilt != null) 60 else 400))	// décalage adouci
	KeeBackdrop(
		inModifier = if (vTilt == null) Modifier.trackPointer(vPointer) else Modifier,
		inParallax = vParallax,
		inGlow     = inGlow,
		inFade     = inFade
	) {
		BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
			Column(
				Modifier
					.widthIn(max = 440.dp)
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.heightIn(min = maxHeight)
					.padding(horizontal = 24.dp, vertical = 20.dp),
				verticalArrangement = Arrangement.SpaceBetween
			) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					if (inOnBack != null)
					{
						Icon(
							LineIcons.Back, "Retour",
							Modifier
								.size(40.dp)
								.clip(CircleShape)
								.clickable(role = Role.Button, onClick = inOnBack)
								.padding(8.dp),
							tint = KeeColors.text
						)
						Spacer(Modifier.size(8.dp))
					}
					KeeBrand()
				}
				Column(Modifier.fillMaxWidth().padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = inContent)
			}
		}
	}
}

/*
	Titre des écrans clés, très grand et fin : la première partie en blanc,
	le mot de contexte en gris, le mot fort en blanc (« Vos mots de passe,
	bien gardés. »).
	inLead   : début, en blanc (peut contenir un retour à la ligne)
	inMuted  : mot de contexte, en gris
	inStrong : mot fort, en blanc
*/
@Composable
fun HeroTitle(
	inLead   : String,
	inMuted  : String,
	inStrong : String
)
{
	BasicText(
		buildAnnotatedString {
			append(inLead)
			withStyle(SpanStyle(color = KeeColors.textMuted)) { append(inMuted) }
			append(inStrong)
		},
		style = keeType().hero
	)
}

/*
	Message d'erreur : icône et texte en rouge sourd.
	inText : message ; rien n'est affiché s'il est null
*/
@Composable
fun ErrorLine(inText: String?)
{
	if (inText == null) return
	Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
		Icon(LineIcons.Alert, null, Modifier.size(18.dp), tint = KeeColors.danger)
		BasicText(inText, style = keeType().bodySmall.copy(color = KeeColors.danger))
	}
}

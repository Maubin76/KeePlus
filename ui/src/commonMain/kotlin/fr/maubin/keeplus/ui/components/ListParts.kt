package fr.maubin.keeplus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType

private val kPill = RoundedCornerShape(percent = 50)									// forme en pilule
private val kMonogramGreys = listOf(
	KeeColors.surfaceHigh, Color(0xFF202022), KeeColors.surfaceHighest, Color(0xFF2C2C2E)
)																						// fonds des monogrammes

/*
	Monogramme d'une entrée : initiale du titre dans un carré arrondi gris,
	nuance tirée de façon stable du titre (aucune icône chargée d'internet).
	inTitle    : titre de l'entrée
	inModifier : modificateur
	inSize     : côté du carré
*/
@Composable
fun Monogram(
	inTitle    : String,
	inModifier : Modifier = Modifier,
	inSize     : Dp       = 40.dp
)
{
	val vLetter = inTitle.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "·"	// initiale
	val vGrey   = kMonogramGreys[(inTitle.lowercase().hashCode() and 0x7FFFFFFF) % kMonogramGreys.size]		// fond
	Box(
		inModifier.size(inSize).clip(RoundedCornerShape(inSize * 0.3f)).background(vGrey),
		contentAlignment = Alignment.Center
	) {
		BasicText(vLetter, style = keeType().title.copy(color = KeeColors.text))
	}
}

/*
	Secret dévoilé, en JetBrains Mono : lettres en blanc, chiffres en gris
	clair, symboles en gris moyen, pour distinguer 0/O et l/1 sans couleur.
	inText : secret
*/
fun secretAnnotated(inText: String): AnnotatedString = buildAnnotatedString {
	for (vChar in inText)
	{
		val vColor = when
		{
			vChar.isLetter() -> KeeColors.passLetter
			vChar.isDigit()  -> KeeColors.passDigit
			else             -> KeeColors.passSymbol
		}																					// teinte du caractère
		withStyle(SpanStyle(color = vColor)) { append(vChar) }
	}
}

/*
	Bouton icône rond, zone de toucher de 44 dp.
	inIcon        : icône
	inDescription : description pour l'accessibilité
	inOnClick     : action
	inModifier    : modificateur
	inTint        : couleur de l'icône
*/
@Composable
fun IconAction(
	inIcon        : ImageVector,
	inDescription : String,
	inOnClick     : () -> Unit,
	inModifier    : Modifier = Modifier,
	inTint        : Color    = KeeColors.textMuted
)
{
	Icon(
		inIcon, inDescription,
		inModifier
			.size(44.dp)
			.clip(CircleShape)
			.clickable(role = Role.Button, onClick = inOnClick)
			.padding(11.dp),
		tint = inTint
	)
}

/*
	Barre de recherche : pilule, loupe à gauche, croix pour vider.
	inState       : texte cherché
	inModifier    : modificateur
	inPlaceholder : indication
*/
@Composable
fun SearchField(
	inState       : TextFieldState,
	inModifier    : Modifier = Modifier,
	inPlaceholder : String   = "Rechercher"
)
{
	BasicTextField(
		state       = inState,
		modifier    = inModifier.fillMaxWidth(),
		textStyle   = keeType().body,
		cursorBrush = SolidColor(KeeColors.text),
		lineLimits  = TextFieldLineLimits.SingleLine,
		decorator   = { vInner ->
			Row(
				Modifier.fillMaxWidth().height(48.dp).clip(kPill).background(KeeColors.surfaceHigh).padding(start = 14.dp, end = 4.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Icon(LineIcons.Search, null, Modifier.size(20.dp), tint = KeeColors.textMuted)
				Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
					if (inState.text.isEmpty()) BasicText(inPlaceholder, style = keeType().body.copy(color = KeeColors.textFaint))
					vInner()
				}
				if (inState.text.isNotEmpty()) IconAction(LineIcons.Close, "Effacer la recherche", { inState.clearText() })
			}
		}
	)
}

/*
	Pastille de filtre (groupe) : active, fond clair et texte sombre.
	inLabel    : texte
	inSelected : pastille active
	inOnClick  : action
*/
@Composable
fun FilterPill(
	inLabel    : String,
	inSelected : Boolean,
	inOnClick  : () -> Unit
)
{
	val vStyle = keeType().label																// style du texte
	BasicText(
		inLabel,
		Modifier
			.clip(kPill)
			.background(if (inSelected) KeeColors.text else Color.Transparent)
			.border(1.dp, if (inSelected) KeeColors.text else KeeColors.rule, kPill)
			.clickable(role = Role.Tab, onClick = inOnClick)
			.padding(horizontal = 16.dp, vertical = 8.dp),
		style = vStyle.copy(color = if (inSelected) KeeColors.void else KeeColors.text)
	)
}

/*
	Texte sur une ligne, coupé par des points de suspension.
	inText     : texte
	inStyle    : style
	inModifier : modificateur
*/
@Composable
fun OneLine(
	inText     : String,
	inStyle    : TextStyle,
	inModifier : Modifier = Modifier
)
{
	BasicText(inText, inModifier, style = inStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/*
	Bandeau de confirmation en bas de l'écran (« Copié — effacé dans 30 s »).
	inText     : message ; null pour le masquer
	inModifier : modificateur
*/
@Composable
fun Notice(
	inText     : String?,
	inModifier : Modifier = Modifier
)
{
	val vLast = remember { mutableStateOf("") }												// dernier message, gardé pendant la sortie
	if (inText != null) vLast.value = inText
	AnimatedVisibility(
		inText != null,
		inModifier,
		enter = fadeIn() + slideInVertically { it / 2 },
		exit  = fadeOut() + slideOutVertically { it / 2 }
	) {
		Row(
			Modifier
				.clip(kPill)
				.background(KeeColors.surfaceHighest)
				.border(1.dp, KeeColors.rule, kPill)
				.padding(horizontal = 18.dp, vertical = 12.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			BasicText(vLast.value, style = keeType().label)
		}
	}
}

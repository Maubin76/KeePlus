package fr.maubin.keeplus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.maubin.keeplus.ui.resources.Res
import fr.maubin.keeplus.ui.resources.jetbrains_mono_medium
import fr.maubin.keeplus.ui.resources.jetbrains_mono_regular
import fr.maubin.keeplus.ui.resources.satoshi_bold
import fr.maubin.keeplus.ui.resources.satoshi_light
import fr.maubin.keeplus.ui.resources.satoshi_medium
import fr.maubin.keeplus.ui.resources.satoshi_regular
import org.jetbrains.compose.resources.Font

/*
	Couleurs de KeePlus (voir DESIGN.md) : monochrome, deux couleurs d'état.
*/
@Immutable
object KeeColors
{
	val void           = Color(0xFF050505)											// bas de la brume, fond le plus profond
	val ground         = Color(0xFF0B0B0C)											// fond des écrans de travail
	val mistTop        = Color(0xFF9A9A9C)											// haut de la brume
	val mistMid        = Color(0xFF3A3A3C)											// milieu de la brume
	val surface        = Color(0xFF141415)											// cartes, champs
	val surfaceHigh    = Color(0xFF1C1C1E)											// barre de recherche, monogrammes
	val surfaceHighest = Color(0xFF262628)											// monogrammes clairs, survol
	val rule           = Color(0xFF2A2A2C)											// contours, séparateurs
	val gridLine       = Color(0x0FFFFFFF)											// trait de la grille
	val gridLineLit    = Color(0x2EFFFFFF)											// trait de la grille illuminée
	val emblem         = Color(0xFF2E2E30)											// grand bouclier du décor
	val text           = Color(0xFFF5F5F5)											// texte principal
	val textMuted      = Color(0xFF9C9C9E)											// texte secondaire, mot gris des titres
	val textFaint      = Color(0xFF6A6A6C)											// indications non essentielles
	val passLetter     = Color(0xFFF5F5F5)											// lettres d'un secret
	val passDigit      = Color(0xFFC8C8CA)											// chiffres d'un secret
	val passSymbol     = Color(0xFF8A8A8C)											// symboles d'un secret
	val silverLight    = Color(0xFFF2F2F2)											// haut du bouton argent
	val silverDark     = Color(0xFFB8B8BA)											// bas du bouton argent
	val silverInk      = Color(0xFF111111)											// texte sur l'argent
	val danger         = Color(0xFFD9645F)											// erreur, suppression
	val success        = Color(0xFF6FB592)											// synchro réussie, mot de passe fort
}

/*
	Police Satoshi, dans les graisses utilisées.
*/
@Composable
fun satoshi(): FontFamily = FontFamily(
	Font(Res.font.satoshi_light, FontWeight.Light),
	Font(Res.font.satoshi_regular, FontWeight.Normal),
	Font(Res.font.satoshi_medium, FontWeight.Medium),
	Font(Res.font.satoshi_bold, FontWeight.Bold)
)

/*
	Police JetBrains Mono, pour les secrets.
*/
@Composable
fun jetBrainsMono(): FontFamily = FontFamily(
	Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
	Font(Res.font.jetbrains_mono_medium, FontWeight.Medium)
)

/*
	Styles de texte de KeePlus (voir DESIGN.md) ; les noms Material 3 servent
	de correspondance pour les composants standard.
	hero      : très grand titre fin des écrans clés
	display   : grand titre
	headline  : titre de section
	title     : titre d'élément (entrée, carte)
	body      : texte courant
	bodySmall : texte secondaire
	label     : boutons, pastilles
	secret    : mots de passe dévoilés
*/
@Immutable
data class KeeType(
	val hero      : TextStyle,
	val display   : TextStyle,
	val headline  : TextStyle,
	val title     : TextStyle,
	val body      : TextStyle,
	val bodySmall : TextStyle,
	val label     : TextStyle,
	val secret    : TextStyle
)

/*
	Styles de texte, construits avec les polices chargées.
*/
@Composable
fun keeType(): KeeType
{
	val vSans = satoshi()																	// Satoshi
	val vMono = jetBrainsMono()																// JetBrains Mono
	return KeeType(
		hero      = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.025).em, color = KeeColors.text),
		display   = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Light, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em, color = KeeColors.text),
		headline  = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.01).em, color = KeeColors.text),
		title     = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp, color = KeeColors.text),
		body      = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = KeeColors.text),
		bodySmall = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.005.em, color = KeeColors.textMuted),
		label     = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em, color = KeeColors.text),
		secret    = TextStyle(fontFamily = vMono, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.02.em, color = KeeColors.passLetter)
	)
}

/*
	Thème de KeePlus, sombre uniquement : Material 3 aux couleurs et polices de
	la direction artistique, pour les composants standard (champs, menus).
	inContent : contenu
*/
@Composable
fun KeePlusTheme(inContent: @Composable () -> Unit)
{
	val vType   = keeType()																// styles de KeePlus
	val vColors = darkColorScheme(
		primary              = KeeColors.text,
		onPrimary            = KeeColors.void,
		secondary            = KeeColors.textMuted,
		onSecondary          = KeeColors.void,
		background           = KeeColors.ground,
		onBackground         = KeeColors.text,
		surface              = KeeColors.surface,
		onSurface            = KeeColors.text,
		surfaceVariant       = KeeColors.surfaceHigh,
		onSurfaceVariant     = KeeColors.textMuted,
		surfaceContainer     = KeeColors.surface,
		surfaceContainerHigh = KeeColors.surfaceHigh,
		outline              = KeeColors.textMuted,
		outlineVariant       = KeeColors.rule,
		error                = KeeColors.danger,
		onError              = KeeColors.void
	)																						// couleurs Material 3
	val vMaterial = Typography(
		displayLarge   = vType.hero,
		displayMedium  = vType.display,
		headlineMedium = vType.headline,
		titleMedium    = vType.title,
		bodyLarge      = vType.body,
		bodyMedium     = vType.body,
		bodySmall      = vType.bodySmall,
		labelLarge     = vType.label
	)																						// polices Material 3
	MaterialTheme(colorScheme = vColors, typography = vMaterial, content = inContent)
}

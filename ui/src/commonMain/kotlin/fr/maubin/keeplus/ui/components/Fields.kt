package fr.maubin.keeplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType

private val kFieldShape  = RoundedCornerShape(16.dp)										// forme des champs
private val kFieldHeight = 56.dp															// hauteur des champs

/*
	Champ de mot de passe : masqué par défaut, bouton pour l'afficher (en
	JetBrains Mono), sans copier ni couper. Contour clair au focus, rouge en
	cas d'erreur.
	inState       : texte saisi
	inPlaceholder : indication affichée quand le champ est vide
	inModifier    : modificateur
	inError       : contour d'erreur
	inEnabled     : champ modifiable
	inImeAction   : action du clavier (Valider, Suivant)
	inOnSubmit    : action à la validation (touche Entrée) ; null pour l'action par défaut
*/
@Composable
fun SecretField(
	inState       : TextFieldState,
	inPlaceholder : String,
	inModifier    : Modifier         = Modifier,
	inError       : Boolean          = false,
	inEnabled     : Boolean          = true,
	inImeAction   : ImeAction        = ImeAction.Done,
	inOnSubmit    : (() -> Unit)?    = null
)
{
	val vType    = keeType()																// styles de texte
	var vVisible by remember { mutableStateOf(false) }										// mot de passe affiché
	val vSource  = remember { MutableInteractionSource() }									// focus
	val vFocused by vSource.collectIsFocusedAsState()										// champ actif
	BasicSecureTextField(
		state               = inState,
		modifier            = inModifier.fillMaxWidth(),
		enabled             = inEnabled,
		textStyle           = if (vVisible) vType.secret else vType.body,
		cursorBrush         = SolidColor(KeeColors.text),
		interactionSource   = vSource,
		textObfuscationMode = if (vVisible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
		keyboardOptions     = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = inImeAction),
		onKeyboardAction    = inOnSubmit?.let { vSubmit -> KeyboardActionHandler { vSubmit() } },
		decorator           = fieldDecorator(inState, inPlaceholder, vFocused, inError) {
			Icon(
				if (vVisible) LineIcons.EyeOff else LineIcons.Eye,
				if (vVisible) "Masquer le mot de passe" else "Afficher le mot de passe",
				Modifier
					.size(40.dp)
					.clip(CircleShape)
					.clickable(role = Role.Button) { vVisible = !vVisible }
					.padding(9.dp),
				tint = KeeColors.textMuted
			)
		}
	)
}

/*
	Champ de texte simple, sur une ligne.
	inState       : texte saisi
	inPlaceholder : indication affichée quand le champ est vide
	inModifier    : modificateur
	inImeAction   : action du clavier
*/
@Composable
fun KeeTextField(
	inState       : TextFieldState,
	inPlaceholder : String,
	inModifier    : Modifier  = Modifier,
	inImeAction   : ImeAction = ImeAction.Next
)
{
	val vSource  = remember { MutableInteractionSource() }									// focus
	val vFocused by vSource.collectIsFocusedAsState()										// champ actif
	BasicTextField(
		state             = inState,
		modifier          = inModifier.fillMaxWidth(),
		textStyle         = keeType().body,
		cursorBrush       = SolidColor(KeeColors.text),
		interactionSource = vSource,
		lineLimits        = TextFieldLineLimits.SingleLine,
		keyboardOptions   = KeyboardOptions(imeAction = inImeAction),
		decorator         = fieldDecorator(inState, inPlaceholder, vFocused, false, null)
	)
}

/*
	Habillage commun des champs : fond de surface, rayon de 16, indication
	quand le champ est vide, élément optionnel à droite.
	inState       : texte saisi
	inPlaceholder : indication
	inFocused     : champ actif
	inError       : contour d'erreur
	inTrailing    : élément à droite ; null pour aucun
*/
private fun fieldDecorator(
	inState       : TextFieldState,
	inPlaceholder : String,
	inFocused     : Boolean,
	inError       : Boolean,
	inTrailing    : (@Composable () -> Unit)?
): TextFieldDecorator = TextFieldDecorator { vInner ->
	val vBorder = when
	{
		inError   -> KeeColors.danger
		inFocused -> KeeColors.textMuted
		else      -> Color.Transparent
	}																						// couleur du contour
	Row(
		Modifier
			.fillMaxWidth()
			.heightIn(min = kFieldHeight)
			.clip(kFieldShape)
			.background(KeeColors.surface)
			.border(1.dp, vBorder, kFieldShape)
			.padding(start = 18.dp, end = if (inTrailing != null) 6.dp else 18.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
			if (inState.text.isEmpty()) BasicText(inPlaceholder, style = keeType().body.copy(color = KeeColors.textFaint))
			vInner()
		}
		inTrailing?.invoke()
	}
}

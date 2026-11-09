package fr.maubin.keeplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.generator.PassphraseOptions
import fr.maubin.keeplus.generator.PasswordOptions
import fr.maubin.keeplus.generator.generatePassphrase
import fr.maubin.keeplus.generator.generatePassword
import fr.maubin.keeplus.generator.passphraseBits
import fr.maubin.keeplus.generator.passwordBits
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType
import kotlin.math.roundToInt

private val kSeparators = listOf("-" to "Tiret", "." to "Point", " " to "Espace", "_" to "Souligné")	// séparateurs proposés

/*
	Générateur intégré à l'édition : mot de passe ou phrase de passe, réglages,
	aperçu en JetBrains Mono trois nuances avec son entropie, nouveau tirage,
	et bouton pour l'utiliser.
	inOnUse    : mot de passe retenu
	inModifier : modificateur
*/
@Composable
fun GeneratorPanel(
	inOnUse    : (String) -> Unit,
	inModifier : Modifier = Modifier
)
{
	val vType       = keeType()																// styles de texte
	var vPhrase     by remember { mutableStateOf(false) }									// phrase de passe plutôt que mot de passe
	var vPassword   by remember { mutableStateOf(PasswordOptions()) }						// réglages du mot de passe
	var vPassphrase by remember { mutableStateOf(PassphraseOptions()) }						// réglages de la phrase
	var vDraw       by remember { mutableStateOf(0) }										// numéro du tirage
	val vValue      = remember(vPhrase, vPassword, vPassphrase, vDraw) {
		if (vPhrase) generatePassphrase(vPassphrase) else generatePassword(vPassword)
	}																						// valeur tirée
	val vBits       = if (vPhrase) passphraseBits(vPassphrase) else passwordBits(vPassword)	// entropie du tirage
	Column(
		inModifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(KeeColors.surface).padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp)
	) {
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			FilterPill("Mot de passe", !vPhrase) { vPhrase = false }
			FilterPill("Phrase de passe", vPhrase) { vPhrase = true }
		}
		Row(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KeeColors.surfaceHigh).padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			BasicText(secretAnnotated(vValue), Modifier.weight(1f).padding(vertical = 8.dp), style = vType.secret)
			IconAction(LineIcons.Refresh, "Nouveau tirage", { vDraw++ }, inTint = KeeColors.text)
		}
		StrengthMeter(vBits)
		if (!vPhrase)
		{
			Stepper("Longueur", vPassword.length, 8..64) { vPassword = vPassword.copy(length = it) }
			FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				FilterPill("a-z", vPassword.lower) { vPassword = vPassword.copy(lower = !vPassword.lower) }
				FilterPill("A-Z", vPassword.upper) { vPassword = vPassword.copy(upper = !vPassword.upper) }
				FilterPill("0-9", vPassword.digits) { vPassword = vPassword.copy(digits = !vPassword.digits) }
				FilterPill("#$%", vPassword.symbols) { vPassword = vPassword.copy(symbols = !vPassword.symbols) }
				FilterPill("Sans l, 1, O, 0", vPassword.avoidAmbiguous) { vPassword = vPassword.copy(avoidAmbiguous = !vPassword.avoidAmbiguous) }
			}
		}
		else
		{
			Stepper("Mots", vPassphrase.words, 3..10) { vPassphrase = vPassphrase.copy(words = it) }
			FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				for ((vSeparator, vName) in kSeparators)
				{
					FilterPill(vName, vPassphrase.separator == vSeparator) { vPassphrase = vPassphrase.copy(separator = vSeparator) }
				}
				FilterPill("Majuscules", vPassphrase.capitalize) { vPassphrase = vPassphrase.copy(capitalize = !vPassphrase.capitalize) }
				FilterPill("Un chiffre", vPassphrase.withDigit) { vPassphrase = vPassphrase.copy(withDigit = !vPassphrase.withDigit) }
			}
			BasicText("Mots tirés d'une liste anglaise de 7 776 mots (EFF).", style = vType.bodySmall.copy(color = KeeColors.textFaint))
		}
		GhostButton("Utiliser ce mot de passe", { inOnUse(vValue) })
	}
}

/*
	Réglage numérique : libellé, valeur, curseur.
	inLabel    : libellé
	inValue    : valeur
	inRange    : bornes
	inOnChange : nouvelle valeur
*/
@Composable
private fun Stepper(
	inLabel    : String,
	inValue    : Int,
	inRange    : IntRange,
	inOnChange : (Int) -> Unit
)
{
	val vType = keeType()																	// styles de texte
	Column {
		Row(Modifier.fillMaxWidth()) {
			BasicText(inLabel, Modifier.weight(1f), style = vType.bodySmall)
			BasicText("$inValue", style = vType.label)
		}
		Slider(
			value         = inValue.toFloat(),
			onValueChange = { inOnChange(it.roundToInt().coerceIn(inRange)) },
			valueRange    = inRange.first.toFloat()..inRange.last.toFloat(),
			colors        = SliderDefaults.colors(
				thumbColor         = KeeColors.text,
				activeTrackColor   = KeeColors.text,
				inactiveTrackColor = KeeColors.rule,
				activeTickColor    = KeeColors.text,
				inactiveTickColor  = KeeColors.rule
			)
		)
	}
}

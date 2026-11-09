package fr.maubin.keeplus.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.generator.Strength
import fr.maubin.keeplus.generator.estimateBits
import fr.maubin.keeplus.generator.strengthOf
import fr.maubin.keeplus.ui.components.KeeTextField
import fr.maubin.keeplus.ui.components.SecretField
import fr.maubin.keeplus.ui.components.SilverButton
import fr.maubin.keeplus.ui.components.StrengthMeter
import fr.maubin.keeplus.ui.theme.keeType
import fr.maubin.keeplus.vault.Vault
import kotlinx.coroutines.launch

/*
	Création du coffre : nom, mot de passe maître saisi deux fois, jauge de
	force. Un mot de passe faible est refusé : il protège tout le reste.
	inError    : message d'erreur de la création ; null si aucun
	inBusy     : création en cours (Argon2, écriture)
	inOnBack   : retour à l'accueil
	inOnCreate : crée le coffre (nom, mot de passe) ; null en cas d'échec
	inOnDone   : coffre créé, à ouvrir
*/
@Composable
fun CreateScreen(
	inError    : String?,
	inBusy     : Boolean,
	inOnBack   : () -> Unit,
	inOnCreate : suspend (String, CharSequence) -> Vault?,
	inOnDone   : (Vault) -> Unit
)
{
	val vScope    = rememberCoroutineScope()												// création hors interface
	val vName     = rememberTextFieldState("Mon coffre")									// nom du coffre
	val vPassword = rememberTextFieldState()												// mot de passe
	val vConfirm  = rememberTextFieldState()												// confirmation
	val vBits     by remember { derivedStateOf { estimateBits(vPassword.text) } }			// entropie estimée
	val vProblem  by remember {
		derivedStateOf {
			when
			{
				vPassword.text.isEmpty()                                   -> ""
				strengthOf(vBits) == Strength.WEAK                         -> "Mot de passe trop faible : allongez-le, ou mélangez lettres, chiffres et symboles."
				vConfirm.text.isNotEmpty() && !vConfirm.text.contentEquals(vPassword.text) -> "Les deux mots de passe ne sont pas identiques."
				vConfirm.text.isEmpty()                                    -> ""
				vName.text.isBlank()                                       -> "Donnez un nom au coffre."
				else                                                       -> null
			}
		}
	}																						// problème bloquant ; "" : saisie incomplète
	/*
		Lance la création, puis efface les champs de mot de passe.
	*/
	fun submit()
	{
		if (vProblem != null || inBusy) return
		vScope.launch {
			val vVault = inOnCreate(vName.text.toString().trim(), vPassword.text) ?: return@launch	// coffre créé
			clear(vPassword, vConfirm)
			inOnDone(vVault)
		}
	}
	KeyScreen(inOnBack = inOnBack) {
		HeroTitle("Créez votre\n", "nouveau ", "coffre.")
		BasicText(
			"Le mot de passe maître protège tout le coffre. Il ne peut pas être récupéré : choisissez-en un long, et notez-le en lieu sûr.",
			style = keeType().bodySmall
		)
		Spacer(Modifier.height(4.dp))
		KeeTextField(vName, "Nom du coffre")
		SecretField(vPassword, "Mot de passe maître", inEnabled = !inBusy, inImeAction = ImeAction.Next)
		SecretField(vConfirm, "Confirmez le mot de passe", inEnabled = !inBusy, inError = vProblem?.startsWith("Les deux") == true, inOnSubmit = ::submit)
		StrengthMeter(vBits)
		ErrorLine(inError ?: vProblem?.ifEmpty { null })
		Spacer(Modifier.height(4.dp))
		SilverButton(if (inBusy) "Création…" else "Créer le coffre", ::submit, inEnabled = vProblem == null && !inBusy)
	}
}

/*
	Vide des champs de mot de passe.
	inStates : champs à vider
*/
internal fun clear(vararg inStates: TextFieldState)
{
	inStates.forEach { it.clearText() }
}

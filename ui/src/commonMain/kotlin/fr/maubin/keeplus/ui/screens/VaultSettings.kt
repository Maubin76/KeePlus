package fr.maubin.keeplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.components.GhostButton
import fr.maubin.keeplus.ui.components.SecretField
import fr.maubin.keeplus.ui.components.TextLink
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType
import kotlinx.coroutines.launch

/*
	Réglages du coffre : son emplacement (déplacement dans un dossier choisi,
	retour à l'emplacement par défaut) et sa suppression.
	inController : état de l'appli
*/
@Composable
fun VaultSettings(inController: AppController)
{
	val vType    = keeType()																	// styles de texte
	val vScope   = rememberCoroutineScope()													// déplacement
	var vBusy    by remember { mutableStateOf(false) }											// déplacement en cours
	var vResult  by remember { mutableStateOf<String?>(null) }									// réussite
	var vError   by remember { mutableStateOf<String?>(null) }									// échec
	var vDelete  by remember { mutableStateOf(false) }											// confirmation de suppression affichée
	/*
		Lance un déplacement et en affiche le résultat.
		inMove : déplacement ; rend null si fait ou annulé, sinon la raison
	*/
	fun move(inMove: suspend () -> String?)
	{
		if (vBusy) return
		val vBefore = inController.vaultPath														// emplacement avant
		vBusy   = true
		vResult = null
		vError  = null
		vScope.launch {
			vError  = inMove()
			vResult = if (vError == null && inController.vaultPath != vBefore) "Coffre déplacé." else null
			vBusy   = false
		}
	}
	SettingsSection("Coffre") {
		if (inController.vaultMovable)
		{
			BasicText("Emplacement", style = vType.body)
			BasicText(inController.vaultFolder, style = vType.bodySmall)
			vResult?.let { BasicText(it, style = vType.body.copy(color = KeeColors.success)) }
			ErrorLine(vError)
			GhostButton(if (vBusy) "Déplacement…" else "Déplacer le coffre", { move(inController::chooseVaultFolder) }, inEnabled = !vBusy)
			if (inController.vaultCustom) TextLink("Revenir à l'emplacement par défaut", { move(inController::resetVaultFolder) })
			SettingsDivider()
		}
		GhostButton("Supprimer le coffre", { vDelete = true })
	}
	if (vDelete) DeleteVaultDialog(inController) { vDelete = false }
}

/*
	Confirmation de la suppression du coffre : le mot de passe maître est
	redemandé. Le coffre et ses sauvegardes sont effacés de l'appareil, les
	appareils appairés oubliés.
	inController : état de l'appli
	inOnDismiss  : fermer la boîte
*/
@Composable
private fun DeleteVaultDialog(
	inController : AppController,
	inOnDismiss  : () -> Unit
)
{
	val vType     = keeType()																	// styles de texte
	val vScope    = rememberCoroutineScope()													// suppression
	val vPassword = rememberTextFieldState()													// mot de passe maître
	var vBusy     by remember { mutableStateOf(false) }											// suppression en cours
	var vError    by remember { mutableStateOf<String?>(null) }									// échec
	/*
		Vérifie le mot de passe et supprime le coffre.
	*/
	fun confirm()
	{
		if (vBusy || vPassword.text.isEmpty()) return
		vBusy = true
		vScope.launch {
			vError = inController.deleteVault(vPassword.text)
			vBusy  = false
			if (vError == null) inOnDismiss() else clear(vPassword)
		}
	}
	Dialog(onDismissRequest = { if (!vBusy) inOnDismiss() }) {
		Column(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(KeeColors.surfaceHigh).padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			BasicText("Supprimer le coffre ?", style = vType.headline)
			BasicText(
				"Le coffre et ses sauvegardes seront effacés de cet appareil, et les appareils appairés oubliés. C'est définitif : exportez-le d'abord si vous voulez en garder une copie.",
				style = vType.bodySmall
			)
			SecretField(vPassword, "Mot de passe maître", inEnabled = !vBusy, inOnSubmit = ::confirm)
			ErrorLine(vError)
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				GhostButton("Annuler", inOnDismiss, Modifier.weight(1f), inEnabled = !vBusy)
				GhostButton(if (vBusy) "Suppression…" else "Supprimer", ::confirm, Modifier.weight(1f), inEnabled = !vBusy && vPassword.text.isNotEmpty())
			}
		}
	}
}

package fr.maubin.keeplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.generator.Strength
import fr.maubin.keeplus.generator.estimateBits
import fr.maubin.keeplus.generator.strengthOf
import fr.maubin.keeplus.settings.kClipboardChoices
import fr.maubin.keeplus.settings.kHiddenChoices
import fr.maubin.keeplus.settings.kIdleChoices
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.components.FilterPill
import fr.maubin.keeplus.ui.components.GhostButton
import fr.maubin.keeplus.ui.components.IconAction
import fr.maubin.keeplus.ui.components.OneLine
import fr.maubin.keeplus.ui.components.SecretField
import fr.maubin.keeplus.ui.components.SilverButton
import fr.maubin.keeplus.ui.components.StrengthMeter
import fr.maubin.keeplus.ui.components.TextLink
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType
import fr.maubin.keeplus.vault.VaultBackup
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val kDay = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)		// jour d'une sauvegarde

/*
	Réglages : verrouillage, presse-papiers, empreinte, mot de passe maître,
	sauvegardes, puis les réglages propres à la plateforme. En
	démonstration, seuls les réglages de l'appli sont proposés.
	inController : état de l'appli
	inDemo       : coffre de démonstration
	inExtra      : réglages propres à la plateforme (démarrage avec Windows, export…) ; null sans
	inOnClose    : fermer la page
*/
@Composable
fun SettingsScreen(
	inController : AppController,
	inDemo       : Boolean,
	inExtra      : (@Composable ColumnScope.() -> Unit)?,
	inOnClose    : () -> Unit
)
{
	val vType     = keeType()																// styles de texte
	val vSettings = inController.settings													// réglages
	var vRestore  by remember { mutableStateOf<VaultBackup?>(null) }						// sauvegarde à restaurer
	val vScope    = rememberCoroutineScope()													// restauration, mot de passe
	Column(
		Modifier
			.fillMaxSize()
			.background(mistTop())
			.safeDrawingPadding()
			.imePadding()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 20.dp, vertical = 12.dp)
			.widthIn(max = 640.dp),
		verticalArrangement = Arrangement.spacedBy(22.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			BasicText("Réglages", Modifier.weight(1f).padding(start = 4.dp), style = vType.headline)
			IconAction(LineIcons.Close, "Fermer", inOnClose, inTint = KeeColors.text)
		}

		SettingsSection("Verrouillage automatique") {
			BasicText("Après une période sans aucun geste", style = vType.body)
			Choices(kIdleChoices, vSettings.idleMinutes, { "$it min" }) { inController.updateSettings(vSettings.copy(idleMinutes = it)) }
			BasicText("Quand l'appli passe en arrière-plan (ou la fenêtre est réduite)", style = vType.body)
			Choices(kHiddenChoices, vSettings.hiddenSeconds, { if (it == 0) "Aussitôt" else if (it < 60) "$it s" else "${it / 60} min" }) { inController.updateSettings(vSettings.copy(hiddenSeconds = it)) }
			BasicText("Un délai laisse le temps d'aller coller un mot de passe dans une autre appli.", style = vType.bodySmall)
		}

		SettingsSection("Presse-papiers") {
			BasicText("Un secret copié est effacé après", style = vType.body)
			Choices(kClipboardChoices, vSettings.clipboardSeconds, { "$it s" }) { inController.updateSettings(vSettings.copy(clipboardSeconds = it)) }
		}

		if (!inDemo && inController.biometricAvailable)
		{
			var vReady by remember { mutableStateOf(inController.biometricReady) }			// empreinte active
			SettingsSection("Empreinte") {
				BasicText(
					if (vReady) "Le coffre se déverrouille par l'empreinte." else "L'empreinte n'est pas active : elle sera proposée après un déverrouillage par mot de passe.",
					style = vType.body
				)
				if (vReady) GhostButton("Désactiver l'empreinte", { inController.disableBiometric(); inController.offerBiometricAgain(); vReady = false })
				else TextLink("Proposer l'empreinte au prochain déverrouillage", { inController.offerBiometricAgain() })
			}
		}

		if (!inDemo)
		{
			SettingsSection("Mot de passe maître") { MasterPasswordForm(inController) }

			SettingsSection("Sauvegardes") {
				BasicText("Une copie du coffre chiffré est faite avant la première modification de chaque jour. Elle s'ouvre avec le mot de passe maître qu'avait le coffre ce jour-là.", style = vType.bodySmall)
				val vBackups = remember(inController.screen) { inController.backups() }			// sauvegardes
				if (vBackups.isEmpty()) BasicText("Aucune sauvegarde pour l'instant.", style = vType.bodySmall.copy(color = KeeColors.textFaint))
				for (vBackup in vBackups)
				{
					Row(
						Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KeeColors.surface).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(Modifier.weight(1f)) {
							OneLine(vBackup.day.format(kDay).replaceFirstChar { it.uppercaseChar() }, vType.label)
							BasicText("${(vBackup.size + 1023) / 1024} Ko", style = vType.bodySmall)
						}
						IconAction(LineIcons.Restore, "Restaurer la sauvegarde du ${vBackup.day}", { vRestore = vBackup }, inTint = KeeColors.text)
					}
				}
			}
		}

		inExtra?.let { vExtra -> Column(verticalArrangement = Arrangement.spacedBy(22.dp)) { vExtra() } }

		SettingsSection("À propos") {
			BasicText("KeePlus — coffre au format KeePass (KDBX 4, Argon2id, AES-256), sans cloud ni compte.", style = vType.bodySmall)
		}
	}
	vRestore?.let { vBackup ->
		ConfirmDialog(
			inTitle     = "Restaurer cette sauvegarde ?",
			inText      = "Le coffre actuel sera remplacé par celui du ${vBackup.day.format(kDay)} (il est lui-même sauvegardé). Le coffre se verrouille ; les autres appareils recevront la version restaurée.",
			inConfirm   = "Restaurer",
			inOnDismiss = { vRestore = null },
			inOnConfirm = { vRestore = null; vScope.launch { inController.restoreBackup(vBackup) } }
		)
	}
}

/*
	Changement du mot de passe maître : mot de passe actuel, nouveau
	(deux fois, jauge de force ; un mot de passe faible est refusé).
	inController : état de l'appli
*/
@Composable
private fun MasterPasswordForm(inController: AppController)
{
	val vType    = keeType()																	// styles de texte
	val vScope   = rememberCoroutineScope()													// changement
	var vOpen    by remember { mutableStateOf(false) }											// formulaire ouvert
	var vResult  by remember { mutableStateOf<String?>(null) }									// réussite
	var vError   by remember { mutableStateOf<String?>(null) }									// échec
	val vCurrent = rememberTextFieldState()													// mot de passe actuel
	val vNew     = rememberTextFieldState()													// nouveau
	val vConfirm = rememberTextFieldState()													// confirmation
	val vBits    by remember { derivedStateOf { estimateBits(vNew.text) } }					// entropie du nouveau
	val vProblem by remember {
		derivedStateOf {
			when
			{
				vCurrent.text.isEmpty() || vNew.text.isEmpty()                         -> ""
				strengthOf(vBits) == Strength.WEAK                                      -> "Nouveau mot de passe trop faible."
				!vConfirm.text.contentEquals(vNew.text)                                 -> if (vConfirm.text.isEmpty()) "" else "Les deux nouveaux mots de passe ne sont pas identiques."
				else                                                                    -> null
			}
		}
	}																						// problème bloquant ; "" : saisie incomplète
	vResult?.let { BasicText(it, style = vType.body.copy(color = KeeColors.success)) }
	if (!vOpen)
	{
		GhostButton("Changer le mot de passe maître", { vOpen = true; vResult = null })
		return
	}
	BasicText("Les autres appareils demanderont le nouveau mot de passe à leur prochain déverrouillage.", style = vType.bodySmall)
	SecretField(vCurrent, "Mot de passe actuel", inImeAction = ImeAction.Next)
	SecretField(vNew, "Nouveau mot de passe", inImeAction = ImeAction.Next)
	SecretField(vConfirm, "Confirmez le nouveau mot de passe", inError = vProblem?.startsWith("Les deux") == true)
	StrengthMeter(vBits)
	ErrorLine(vError ?: vProblem?.ifEmpty { null })
	Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
		GhostButton("Annuler", { vOpen = false; clear(vCurrent, vNew, vConfirm); vError = null }, Modifier.weight(1f))
		SilverButton("Changer", {
			vScope.launch {
				val vFailure = inController.changeMasterPassword(vCurrent.text, vNew.text)			// raison d'un échec
				if (vFailure == null)
				{
					clear(vCurrent, vNew, vConfirm)
					vOpen   = false
					vError  = null
					vResult = "Mot de passe maître changé."
				}
				else vError = vFailure
			}
		}, Modifier.weight(1f), inEnabled = vProblem == null)
	}
}

/*
	Choix parmi quelques valeurs, en pastilles.
	inValues   : valeurs proposées
	inSelected : valeur choisie
	inLabel    : libellé d'une valeur
	inOnPick   : nouvelle valeur
*/
@Composable
private fun Choices(
	inValues   : List<Int>,
	inSelected : Int,
	inLabel    : (Int) -> String,
	inOnPick   : (Int) -> Unit
)
{
	FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		for (vValue in inValues) FilterPill(inLabel(vValue), vValue == inSelected) { inOnPick(vValue) }
	}
}

/*
	Section des réglages : titre discret puis contenu.
	inTitle   : titre
	inContent : contenu
*/
@Composable
private fun SettingsSection(
	inTitle   : String,
	inContent : @Composable ColumnScope.() -> Unit
)
{
	Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
		BasicText(inTitle, style = keeType().bodySmall)
		inContent()
	}
}

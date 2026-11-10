package fr.maubin.keeplus

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.maubin.keeplus.ui.components.GhostButton
import fr.maubin.keeplus.ui.screens.ErrorLine
import fr.maubin.keeplus.ui.screens.SettingsSection
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType
import fr.maubin.keeplus.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/*
	Réglages propres au téléphone : auto-remplissage, export du coffre chiffré vers un fichier
	choisi (carte, clé USB, dossier partagé), pour le garder hors du
	téléphone. Le fichier exporté s'ouvre avec KeePlus, KeePassDX ou
	KeePassXC et le mot de passe maître.
	inStore : coffre du téléphone
*/
@Composable
fun PhoneSettings(inStore: VaultStore)
{
	val vType    = keeType()																	// styles de texte
	val vContext = LocalContext.current														// accès aux documents
	val vScope   = rememberCoroutineScope()													// écriture du fichier
	var vResult  by remember { mutableStateOf<String?>(null) }									// réussite
	var vError   by remember { mutableStateOf<String?>(null) }									// échec
	val vExport  = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { vUri ->
		if (vUri == null) return@rememberLauncherForActivityResult
		vScope.launch {
			val vDone = withContext(Dispatchers.IO) {
				runCatching {
					val vBytes = inStore.read() ?: error("aucun coffre")									// coffre chiffré
					vContext.contentResolver.openOutputStream(vUri)?.use { it.write(vBytes) } ?: error("fichier inaccessible")
				}
			}																						// écriture
			vResult = if (vDone.isSuccess) "Coffre exporté." else null
			vError  = vDone.exceptionOrNull()?.let { "Export impossible : ${it.message}" }
		}
	}																						// choix du fichier d'export
	AutofillSettings()
	SettingsSection("Export") {
		vResult?.let { BasicText(it, style = vType.body.copy(color = KeeColors.success)) }
		ErrorLine(vError)
		GhostButton("Exporter le coffre", { vResult = null; vError = null; vExport.launch("KeePlus-${LocalDate.now()}.kdbx") })
	}
}

/*
	Auto-remplissage : état (KeePlus est-il le service d'Android ?), relu au
	retour des réglages d'Android, et bouton qui ouvre la page où le
	choisir.
*/
@Composable
private fun AutofillSettings()
{
	val vType    = keeType()																	// styles de texte
	val vContext = LocalContext.current														// réglages d'Android
	var vEnabled by remember { mutableStateOf(false) }											// KeePlus est le service choisi
	LifecycleResumeEffect(Unit) {
		vEnabled = vContext.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
		onPauseOrDispose {}
	}
	SettingsSection("Auto-remplissage") {
		if (vEnabled)
		{
			BasicText("Actif", style = vType.body.copy(color = KeeColors.success))
		}
		else
		{
			GhostButton("Activer l'auto-remplissage", {
				runCatching {
					vContext.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${vContext.packageName}")))
				}
			})
		}
	}
}

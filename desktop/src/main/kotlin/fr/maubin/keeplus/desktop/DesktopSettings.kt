package fr.maubin.keeplus.desktop

import androidx.compose.foundation.layout.Arrangement
import fr.maubin.keeplus.desktop.quick.Hotkey
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.ui.components.FilterPill
import fr.maubin.keeplus.ui.components.GhostButton
import fr.maubin.keeplus.ui.screens.ErrorLine
import fr.maubin.keeplus.ui.screens.SettingsSection
import fr.maubin.keeplus.ui.theme.keeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

/*
	Réglages propres au PC : raccourci de la carte de saisie, démarrage avec
	Windows (appli installée seulement) et ouverture du dossier des
	sauvegardes.
	inBackups  : dossier des sauvegardes ; null sans
	inHotkey   : raccourci de la carte de saisie (texte des réglages) ; null sans carte (hors Windows)
	inHotkeyOk : le raccourci est bien enregistré auprès de Windows
	inOnHotkey : nouveau raccourci choisi
*/
@Composable
fun DesktopSettings(
	inBackups  : File?,
	inHotkey   : String?          = null,
	inHotkeyOk : Boolean          = true,
	inOnHotkey : (String) -> Unit = {}
)
{
	if (inHotkey != null) HotkeySettings(inHotkey, inHotkeyOk, inOnHotkey)
	val vType   = keeType()																	// styles de texte
	val vScope  = rememberCoroutineScope()													// commandes Windows
	var vOn     by remember { mutableStateOf(false) }										// démarrage avec Windows
	var vError  by remember { mutableStateOf<String?>(null) }								// échec
	LaunchedEffect(Unit) { vOn = withContext(Dispatchers.IO) { WindowsStartup.isOn() } }
	SettingsSection("Démarrage") {
		if (WindowsStartup.exe == null)
		{
			BasicText("Disponible une fois KeePlus installé.", style = vType.body)
		}
		else
		{
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				for (vChoice in listOf(true, false))
				{
					FilterPill(if (vChoice) "Avec Windows" else "À la demande", vOn == vChoice) {
						vScope.launch {
							vError = withContext(Dispatchers.IO) { WindowsStartup.set(vChoice) }
							vOn    = withContext(Dispatchers.IO) { WindowsStartup.isOn() }
						}
					}
				}
			}
			ErrorLine(vError)
		}
	}
	if (inBackups != null)
	{
		SettingsSection("Dossier des sauvegardes") {
			BasicText(inBackups.absolutePath, style = vType.body)
			GhostButton("Ouvrir le dossier", {
				runCatching { inBackups.mkdirs(); Desktop.getDesktop().open(inBackups) }
			})
		}
	}
}

/*
	Raccourci de la carte de saisie : combinaison actuelle, « Modifier »
	attend la combinaison suivante (Échap annule). Une combinaison refusée
	par Windows (déjà prise par une autre appli) est signalée.
	inHotkey   : raccourci actuel (texte des réglages)
	inHotkeyOk : raccourci enregistré auprès de Windows
	inOnHotkey : nouveau raccourci
*/
@Composable
private fun HotkeySettings(
	inHotkey   : String,
	inHotkeyOk : Boolean,
	inOnHotkey : (String) -> Unit
)
{
	val vType   = keeType()																	// styles de texte
	var vListen by remember { mutableStateOf(false) }											// attente d'une combinaison
	val vFocus  = remember { FocusRequester() }												// focus de la zone d'écoute
	LaunchedEffect(vListen) { if (vListen) runCatching { vFocus.requestFocus() } }
	SettingsSection("Saisie rapide") {
		Row(verticalAlignment = Alignment.CenterVertically) {
			BasicText(if (vListen) "Appuyez sur la combinaison…" else Hotkey.parse(inHotkey).label(), Modifier.weight(1f), style = vType.body)
			if (!vListen) GhostButton("Modifier", { vListen = true }, Modifier.width(140.dp))
		}
		if (vListen)
		{
			Box(
				Modifier
					.focusRequester(vFocus)
					.focusable()
					.onPreviewKeyEvent { inEvent ->
						if (inEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
						if (inEvent.key == Key.Escape)
						{
							vListen = false
							return@onPreviewKeyEvent true
						}
						val vKey = inEvent.key.nativeKeyCode														// touche (code AWT)
						if (!Hotkey.isKey(vKey)) return@onPreviewKeyEvent true
						val vHotkey = Hotkey(inEvent.isCtrlPressed, inEvent.isAltPressed, inEvent.isShiftPressed, inEvent.isMetaPressed, vKey)	// combinaison
						if (vHotkey.usable)
						{
							inOnHotkey(vHotkey.spec())
							vListen = false
						}
						true
					}
			)
		}
		if (!inHotkeyOk) ErrorLine("Ce raccourci est déjà utilisé par une autre appli.")
	}
}

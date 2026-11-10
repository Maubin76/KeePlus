package fr.maubin.keeplus.desktop

import androidx.compose.foundation.layout.Arrangement
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
import fr.maubin.keeplus.ui.theme.keeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

/*
	Réglages propres au PC : démarrage avec Windows (appli installée
	seulement) et ouverture du dossier des sauvegardes.
	inBackups : dossier des sauvegardes ; null sans
*/
@Composable
fun DesktopSettings(inBackups: File?)
{
	val vType   = keeType()																	// styles de texte
	val vScope  = rememberCoroutineScope()													// commandes Windows
	var vOn     by remember { mutableStateOf(false) }										// démarrage avec Windows
	var vError  by remember { mutableStateOf<String?>(null) }								// échec
	LaunchedEffect(Unit) { vOn = withContext(Dispatchers.IO) { WindowsStartup.isOn() } }
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		BasicText("Démarrage", style = vType.bodySmall)
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
		Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
			BasicText("Dossier des sauvegardes", style = vType.bodySmall)
			BasicText(inBackups.absolutePath, style = vType.body)
			GhostButton("Ouvrir le dossier", {
				runCatching { inBackups.mkdirs(); Desktop.getDesktop().open(inBackups) }
			})
		}
	}
}

package fr.maubin.keeplus.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import fr.maubin.keeplus.ui.WelcomePreview
import fr.maubin.keeplus.ui.theme.KeePlusTheme

/*
	Point d'entrée de l'appli PC : une fenêtre au thème de KeePlus.
*/
fun main() = application {
	val vState = rememberWindowState(size = DpSize(1100.dp, 720.dp))						// taille de la fenêtre
	Window(onCloseRequest = ::exitApplication, state = vState, title = "KeePlus") {
		KeePlusTheme {
			WelcomePreview()
		}
	}
}

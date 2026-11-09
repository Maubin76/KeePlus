package fr.maubin.keeplus.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.Timer
import kotlin.concurrent.schedule

/*
	Presse-papiers Windows : un secret copié est effacé au bout du délai,
	seulement s'il y est encore (une copie faite entre-temps est respectée).
	fTimer : minuteur des effacements
*/
private class DesktopClipboard : SecretClipboard
{
	private val fTimer = Timer("presse-papiers", true)

	/*
		Copie un texte, et programme son effacement si c'est un secret.
		inLabel  : nom de la donnée (inutilisé sur PC)
		inText   : texte copié
		inSecret : effacer après le délai
	*/
	override fun copy(
		inLabel  : String,
		inText   : String,
		inSecret : Boolean
	)
	{
		val vBoard = Toolkit.getDefaultToolkit().systemClipboard							// presse-papiers du système
		vBoard.setContents(StringSelection(inText), null)
		if (!inSecret) return
		fTimer.schedule(kClipboardSeconds * 1000L) {
			val vNow = runCatching { vBoard.getData(DataFlavor.stringFlavor) as? String }.getOrNull()	// contenu actuel
			if (vNow == inText) vBoard.setContents(StringSelection(""), null)
		}
	}
}

/*
	Presse-papiers des secrets du PC.
*/
@Composable
actual fun rememberSecretClipboard(): SecretClipboard = remember { DesktopClipboard() }

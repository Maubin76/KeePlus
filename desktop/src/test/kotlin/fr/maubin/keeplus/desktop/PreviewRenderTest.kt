package fr.maubin.keeplus.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import fr.maubin.keeplus.ui.WelcomePreview
import fr.maubin.keeplus.ui.theme.KeePlusTheme
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File

/*
	Rendu hors écran des écrans, en PNG dans build/previews, pour vérifier la
	direction artistique sans lancer l'appli.
*/
class PreviewRenderTest
{
	/*
		Écran d'accueil provisoire, au format téléphone et au format PC.
	*/
	@Test
	fun renderWelcome()
	{
		val vDir = File("build/previews").apply { mkdirs() }								// dossier des rendus
		for ((vName, vSize) in listOf("telephone" to (412 to 892), "pc" to (1100 to 720)))
		{
			ImageComposeScene(vSize.first, vSize.second) { KeePlusTheme { WelcomePreview() } }.use { vScene ->
				vScene.render()
				val vImage = vScene.render(1_000_000_000L)										// image après chargement des polices
				File(vDir, "accueil-$vName.png").writeBytes(vImage.encodeToData(EncodedImageFormat.PNG)!!.bytes)
			}
		}
	}
}

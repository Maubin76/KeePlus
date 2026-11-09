package fr.maubin.keeplus.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import fr.maubin.keeplus.ui.screens.CreateScreen
import fr.maubin.keeplus.ui.screens.HeroTitle
import fr.maubin.keeplus.ui.screens.KeyScreen
import fr.maubin.keeplus.ui.screens.UnlockScreen
import fr.maubin.keeplus.ui.screens.WelcomeScreen
import fr.maubin.keeplus.ui.theme.KeePlusTheme
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File

/*
	Rendu hors écran des écrans clés, en PNG dans build/previews, au format
	téléphone et au format PC, pour vérifier la direction artistique sans
	lancer l'appli.
*/
class PreviewRenderTest
{
	/*
		Rend un écran aux deux formats.
		inName    : nom des fichiers
		inContent : écran
	*/
	private fun render(
		inName    : String,
		inContent : @Composable () -> Unit
	)
	{
		val vDir = File("build/previews").apply { mkdirs() }								// dossier des rendus
		for ((vFormat, vSize) in listOf("telephone" to (412 to 892), "pc" to (1100 to 760)))
		{
			ImageComposeScene(vSize.first, vSize.second) { KeePlusTheme { inContent() } }.use { vScene ->
				vScene.render()
				val vImage = vScene.render(1_000_000_000L)										// image après chargement des polices
				File(vDir, "$inName-$vFormat.png").writeBytes(vImage.encodeToData(EncodedImageFormat.PNG)!!.bytes)
			}
		}
	}

	/*
		Accueil, création, déverrouillage (avec une erreur) et vague de la grille.
	*/
	@Test
	fun renderKeyScreens()
	{
		render("accueil") { WelcomeScreen(null, false, {}, {}, {}) }
		render("creation") { CreateScreen(null, false, {}, { _, _ -> null }, {}) }
		render("deverrouillage") { UnlockScreen(null, "Mot de passe incorrect.", false, null, { null }, {}, {}) }
		render("vague") { KeyScreen(inGlow = 0.35f) { HeroTitle("Déverrouillez\n", "votre ", "coffre.") } }
	}
}

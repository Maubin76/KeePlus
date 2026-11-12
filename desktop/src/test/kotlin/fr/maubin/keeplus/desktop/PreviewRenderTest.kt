package fr.maubin.keeplus.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.AppScreen
import fr.maubin.keeplus.ui.screens.CreateScreen
import fr.maubin.keeplus.ui.screens.HeroTitle
import fr.maubin.keeplus.ui.screens.KeyScreen
import fr.maubin.keeplus.ui.screens.IntroFrame
import fr.maubin.keeplus.ui.screens.IntroState
import fr.maubin.keeplus.ui.screens.UnlockScreen
import fr.maubin.keeplus.ui.screens.VaultScreen
import fr.maubin.keeplus.ui.screens.VaultSettings
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.ui.screens.WelcomeScreen
import fr.maubin.keeplus.ui.theme.KeePlusTheme
import fr.maubin.keeplus.vault.VaultStore
import kotlinx.coroutines.runBlocking
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
		Animation d'ouverture, image par image (format téléphone), assemblée
		en une planche : plaque, chute, impact, gravure, fondu.
	*/
	@Test
	fun renderIntroFrames()
	{
		val vDir    = File("build/previews").apply { mkdirs() }								// dossier des rendus
		val vSteps  = listOf(
			IntroState(), IntroState(fall = .3f), IntroState(fall = .6f), IntroState(fall = .9f),
			IntroState(fall = 1f, bounce = .075f, press = 1f, impact = 1f, shakeY = 12f, shakeX = -3.5f),
			IntroState(fall = 1f, bounce = -.03f, press = -.25f, impact = .75f, shakeY = -7f, shakeX = 2.5f, carve = .3f),
			IntroState(fall = 1f, bounce = .012f, press = .08f, impact = .45f, shakeY = 4f, carve = .7f),
			IntroState(fall = 1f, carve = 1f), IntroState(fall = 1f, carve = 1f, fade = .5f), IntroState(fall = 1f, carve = 1f, fade = .1f)
		)																						// étapes
		val vFrames = vSteps.map { vStep ->
			ImageComposeScene(412, 892) { KeePlusTheme { IntroFrame(vStep) } }.use { it.render() }
		}																						// images
		val vSurface = org.jetbrains.skia.Surface.makeRasterN32Premul(206 * vFrames.size, 446)	// planche
		vFrames.forEachIndexed { vIndex, vFrame ->
			vSurface.canvas.drawImageRect(vFrame, org.jetbrains.skia.Rect.makeXYWH(vIndex * 206f, 0f, 206f, 446f))
		}
		File(vDir, "ouverture.png").writeBytes(vSurface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes)
	}

	/*
		Fenêtre « nouvelle version disponible », par-dessus le coffre de démonstration.
	*/
	@Test
	fun renderUpdateDialog()
	{
		val vRelease = fr.maubin.keeplus.update.Release("1.1", 2, listOf(fr.maubin.keeplus.update.ReleaseAsset("KeePlus-1.1-windows.zip", 78_000_000, "http://127.0.0.1/", null)))	// release simulée
		val vManager = fr.maubin.keeplus.ui.update.UpdateManager(
			object : fr.maubin.keeplus.ui.update.UpdateInstaller
			{
				override val platform = fr.maubin.keeplus.update.UpdatePlatform.WINDOWS
				override val version  = "1.0"
				override val autoCheck = true
				override val notice: String? = null
				override val action   = "Mettre à jour"
				override fun target(inAsset: fr.maubin.keeplus.update.ReleaseAsset) = File("build/tmp/maj.zip")
				override suspend fun install(inFile: File) = fr.maubin.keeplus.ui.update.InstallStep.Started
			},
			{ true }, { "" }, {}, { vRelease }
		)																						// mises à jour simulées
		vManager.checkNow {}
		runBlocking { kotlinx.coroutines.withTimeout(5_000) { while (vManager.state == null) kotlinx.coroutines.delay(20) } }
		render("mise-a-jour") { fr.maubin.keeplus.ui.update.UpdateDialog(vManager) }
	}

	/*
		Accueil, création, déverrouillage (avec une erreur) et vague de la grille.
	*/
	@Test
	fun renderKeyScreens()
	{
		render("accueil") { WelcomeScreen(null, false, {}, {}, {}, "C:\\Users\\camille\\AppData\\Roaming\\KeePlus") }
		render("creation") { CreateScreen(null, false, {}, { _, _ -> null }, {}) }
		render("deverrouillage") { UnlockScreen(null, "Mot de passe incorrect.", false, null, { null }, {}, {}) }
		render("vague") { KeyScreen(inGlow = 0.35f) { HeroTitle("Déverrouillez\n", "votre ", "coffre.") } }
	}

	/*
		Réglages du coffre (emplacement choisi, suppression), avec un vrai
		coffre rangé hors de l'emplacement par défaut.
	*/
	@Test
	fun renderVaultSettings()
	{
		val vRoot       = kotlin.io.path.createTempDirectory("keeplus").toFile()					// dossier d'essai
		val vStore      = VaultStore(fr.maubin.keeplus.vault.VaultLocation(File(vRoot, "appli/coffre.kdbx"), File(vRoot, "appli/emplacement.txt")))	// coffre d'essai
		val vController = AppController(vStore, { null })											// appli
		runBlocking {
			vController.open(vController.create("Perso", "un mot de passe solide")!!)
			vStore.moveTo(File(vRoot, "Documents/KeePlus"))
		}
		val vShown      = AppController(vStore, { null })											// appli relancée : emplacement relu
		render("reglages-coffre") {
			androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(20.dp)) { VaultSettings(vShown) }
		}
	}

	/*
		Coffre de démonstration ouvert : liste seule, puis entrée choisie (fiche
		en plein écran sur téléphone, troisième colonne sur PC).
	*/
	@Test
	fun renderVault()
	{
		val vController = AppController(VaultStore(File("build/previews/absent.kdbx")), { null })	// appli sans coffre
		runBlocking { vController.openDemo() }
		val vOpened = vController.screen as AppScreen.Opened										// démonstration ouverte
		render("coffre") { VaultScreen(vController, vOpened.vault, true) }
		vController.selected = vOpened.vault.items().first { it.title == "Société Générale" }.id
		render("fiche") { VaultScreen(vController, vOpened.vault, true) }
		vController.startEdit(vController.selected!!)
		render("edition") { VaultScreen(vController, vOpened.vault, true) }
		vController.cancelEdit()
		vController.startNew()
		render("nouvelle") { VaultScreen(vController, vOpened.vault, true) }
		vController.cancelEdit()
		vController.showSettings = true
		render("reglages") { VaultScreen(vController, vOpened.vault, false) }
	}
}

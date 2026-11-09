package fr.maubin.keeplus.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import fr.maubin.keeplus.link.NetworkId
import fr.maubin.keeplus.sync.PcSync
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.AppScreen
import fr.maubin.keeplus.ui.screens.IntroFrame
import fr.maubin.keeplus.ui.screens.IntroState
import fr.maubin.keeplus.ui.screens.UnlockScreen
import fr.maubin.keeplus.ui.screens.VaultScreen
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.KeePlusTheme
import fr.maubin.keeplus.vault.VaultStore
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress

/*
	Captures du README (doc/images), rendues hors écran à partir du coffre de
	démonstration. Lancées seulement à la demande (les dates du coffre de
	démonstration changent chaque jour) :
	KEEPLUS_CAPTURES=1 ./gradlew :desktop:test --tests "*ReadmeScreenshotsTest*"
*/
class ReadmeScreenshotsTest
{
	@get:Rule
	val fTemp = TemporaryFolder()														// dossiers jetables

	private val fDir = File("../doc/images")											// dossier des captures

	/*
		Rend un écran en PNG.
		inName    : nom du fichier
		inW, inH  : taille en dp
		inDensity : pixels par dp
		inContent : écran
	*/
	private fun capture(
		inName    : String,
		inW       : Int,
		inH       : Int,
		inDensity : Float,
		inContent : @Composable () -> Unit
	)
	{
		fDir.mkdirs()
		ImageComposeScene((inW * inDensity).toInt(), (inH * inDensity).toInt(), Density(inDensity)) {
			KeePlusTheme { Box(Modifier.fillMaxSize().background(KeeColors.ground)) { inContent() } }
		}.use { vScene ->
			vScene.render()
			File(fDir, inName).writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		}
	}

	/*
		Appli avec le coffre de démonstration ouvert.
	*/
	private fun demo(): AppController = AppController(VaultStore(fTemp.root.resolve("absent.kdbx")), { null }).also { runBlocking { it.openDemo() } }

	/*
		Toutes les captures du README.
	*/
	@Test
	fun captures()
	{
		assumeTrue("captures sur demande : KEEPLUS_CAPTURES=1", System.getenv("KEEPLUS_CAPTURES") == "1")

		// PC : trois colonnes, une fiche ouverte.
		val vPc     = demo()																	// appli PC
		val vVault  = (vPc.screen as AppScreen.Opened).vault										// coffre de démonstration
		vPc.selected = vVault.items().first { it.title == "GitHub" }.id
		capture("vitrine-pc-coffre.png", 1280, 800, 1.5f) { VaultScreen(vPc, vVault, false) }

		// Téléphone : déverrouillage, liste, fiche, générateur.
		capture("telephone-deverrouillage.png", 412, 892, 2f) {
			UnlockScreen(null, null, false, null, { null }, {}, {}, inOnFinger = { null }, inReady = false)
		}
		val vPhone  = demo()																	// appli du téléphone
		capture("telephone-liste.png", 412, 892, 2f) { VaultScreen(vPhone, (vPhone.screen as AppScreen.Opened).vault, false) }
		vPhone.selected = (vPhone.screen as AppScreen.Opened).vault.items().first { it.title == "Société Générale" }.id
		capture("telephone-fiche.png", 412, 892, 2f) { VaultScreen(vPhone, (vPhone.screen as AppScreen.Opened).vault, false) }
		vPhone.selected = null
		vPhone.startNew()
		capture("telephone-generateur.png", 412, 892, 2f) { VaultScreen(vPhone, (vPhone.screen as AppScreen.Opened).vault, false) }

		// Animation d'ouverture : chute, impact, logo formé.
		listOf(
			"ouverture-1.png" to IntroState(fall = .45f),
			"ouverture-2.png" to IntroState(fall = 1f, bounce = .075f, press = 1f, impact = 1f, carve = .2f),
			"ouverture-3.png" to IntroState(fall = 1f, carve = 1f)
		).forEach { (vName, vState) -> capture(vName, 412, 892, 1f) { IntroFrame(vState) } }

		// PC : appairage d'un téléphone par QR code.
		val vSync = PcSync(
			fFile      = fTemp.root.resolve("appareils.json"),
			fVault     = VaultStore(fTemp.root.resolve("pc.kdbx")),
			fName      = "PC du salon",
			fVersion   = "1.2",
			fNetworks  = { listOf(NetworkId.wifi("Maison")!!) },
			fAddresses = { listOf("192.168.1.20") },
			fPort      = 0,
			fBeacon    = 0,
			fBind      = InetAddress.getLoopbackAddress()
		)																						// synchronisation du PC
		try
		{
			vSync.startPairing()
			val vDevices = demo().apply { showDevices = true }										// appli PC, page Appareils
			capture("pc-appairage.png", 1280, 800, 1.5f) {
				VaultScreen(vDevices, (vDevices.screen as AppScreen.Opened).vault, false, inDevices = { DevicesPanel(vSync) {} })
			}
		}
		finally
		{
			vSync.stop()
		}
	}
}

package fr.maubin.keeplus.desktop

import androidx.compose.foundation.background
import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.desktop.quick.QuickCardContent
import fr.maubin.keeplus.desktop.quick.NotchFrame
import fr.maubin.keeplus.autofill.DesktopTarget
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
	Captures brutes du README (build/captures), rendues hors écran à partir
	du coffre de démonstration ; ci/vitrines.py les pose ensuite dans des
	cadres de téléphone et de fenêtre (doc/images). Lancées seulement à la
	demande (les dates du coffre de démonstration changent chaque jour) :
	KEEPLUS_CAPTURES=1 ./gradlew :desktop:test --tests "*ReadmeScreenshotsTest*"
*/
class ReadmeScreenshotsTest
{
	@get:Rule
	val fTemp = TemporaryFolder()														// dossiers jetables

	private val fDir = File("../build/captures")										// captures brutes (les visuels du README en sont composés par ci/vitrines.py)

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
		capture("pc-saisie-rapide.png", 1280, 720, 1.5f) { QuickCardScene() }
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

/*
	Scène de la carte de saisie du PC : un navigateur ouvert sur une page de
	connexion, la carte en encoche en haut au milieu, avec deux comptes
	GitHub dans un coffre d'essai.
*/
@Composable
private fun QuickCardScene()
{
	val vController = remember {
		AppController(VaultStore(java.nio.file.Files.createTempDirectory("keeplus").toFile().resolve("coffre.kdbx")), { null }).also { vApp ->
			runBlocking {
				vApp.open(vApp.create("Perso", "un mot de passe solide")!!)
				vApp.edit { it.save(EntryDraft(title = "GitHub", userName = "camille.martin", password = "a", url = "https://github.com")) }
				vApp.edit { it.save(EntryDraft(title = "GitHub", userName = "cmartin-pro", password = "b", url = "https://github.com")) }
			}
		}
	}																						// coffre d'essai
	Box(Modifier.fillMaxSize().background(Color(0xFFF6F8FA))) {
		Column(Modifier.fillMaxSize()) {
			Box(Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFDEE1E6)))
			Box(Modifier.fillMaxWidth().height(46.dp).background(Color.White).padding(horizontal = 120.dp, vertical = 8.dp)) {
				Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF1F3F4)).padding(start = 18.dp), contentAlignment = Alignment.CenterStart) {
					BasicText("github.com/login", style = TextStyle(color = Color(0xFF3C4043), fontSize = 14.sp))
				}
			}
			Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
				Column(Modifier.padding(top = 120.dp).width(300.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
					BasicText("Sign in to GitHub", Modifier.align(Alignment.CenterHorizontally), style = TextStyle(color = Color(0xFF1F2328), fontSize = 22.sp))
					Column(
						Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White).padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(10.dp)
					) {
						BasicText("Username or email address", style = TextStyle(color = Color(0xFF1F2328), fontSize = 13.sp))
						Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFF6F8FA)))
						BasicText("Password", style = TextStyle(color = Color(0xFF1F2328), fontSize = 13.sp))
						Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFF6F8FA)))
						Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1F883D)), contentAlignment = Alignment.Center) {
							BasicText("Sign in", style = TextStyle(color = Color.White, fontSize = 14.sp))
						}
					}
				}
			}
		}
		Box(Modifier.align(Alignment.TopCenter)) {
			NotchFrame { QuickCardContent(vController, DesktopTarget("brave.exe", "Sign in to GitHub - Brave", "github.com"), {}, {}) }
		}
	}
}

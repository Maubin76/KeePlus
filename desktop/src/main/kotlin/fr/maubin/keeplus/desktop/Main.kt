package fr.maubin.keeplus.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.AppScreen
import fr.maubin.keeplus.ui.KeePlusApp
import fr.maubin.keeplus.ui.PickedFile
import fr.maubin.keeplus.ui.VaultFiles
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.desktop.quick.ActiveWindow
import fr.maubin.keeplus.desktop.quick.Hotkey
import fr.maubin.keeplus.desktop.quick.QuickRequest
import fr.maubin.keeplus.desktop.quick.QuickWindow
import fr.maubin.keeplus.desktop.quick.WinBridge
import fr.maubin.keeplus.desktop.update.DesktopUpdateInstaller
import fr.maubin.keeplus.desktop.update.appFolder
import fr.maubin.keeplus.desktop.update.appVersion
import fr.maubin.keeplus.desktop.update.cleanLeftover
import fr.maubin.keeplus.ui.theme.logoImage
import fr.maubin.keeplus.sync.PcSync
import fr.maubin.keeplus.settings.SettingsStore
import fr.maubin.keeplus.vault.VaultBackups
import fr.maubin.keeplus.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/*
	Dossier des données de KeePlus sur le PC : %APPDATA%\KeePlus (ou
	~/.keeplus hors de Windows).
*/
fun dataDir(): File =
	System.getenv("APPDATA")?.let { File(it, "KeePlus") } ?: File(System.getProperty("user.home"), ".keeplus")

/*
	Sélecteur de coffre du PC : boîte de dialogue « Ouvrir » de Windows,
	limitée aux fichiers .kdbx.
*/
class DesktopFiles : VaultFiles
{
	/*
		Ouvre la boîte de dialogue et lit le fichier choisi ; null si annulé.
	*/
	override suspend fun pickVault(): PickedFile?
	{
		val vChosen = withContext(Dispatchers.Main) {
			val vDialog = FileDialog(null as Frame?, "Choisir un coffre KeePass", FileDialog.LOAD).apply {
				file = "*.kdbx"
				setFilenameFilter { _, inName -> inName.endsWith(".kdbx", ignoreCase = true) }
				isVisible = true
			}																				// boîte de dialogue (modale)
			vDialog.file?.let { File(vDialog.directory, it) }
		} ?: return null																	// fichier choisi
		return withContext(Dispatchers.IO) { PickedFile(vChosen.name, vChosen.readBytes()) }
	}
}

/*
	Point d'entrée de l'appli PC : coffre dans %APPDATA%\KeePlus\coffre.kdbx.
	Une seule instance (une seconde la ramène au premier plan). Fermer la
	fenêtre garde l'appli dans la zone de notification (pour la
	synchronisation avec le téléphone) ; « Quitter » dans le menu de l'icône
	la ferme vraiment. Le coffre se verrouille avec la session Windows, et
	selon les délais réglés après que la fenêtre a été réduite ou fermée.
	Lancé avec Windows (--demarrage), il reste dans la zone de notification.
	La synchronisation avec les téléphones écoute sur le réseau de la maison
	(réseau relu toutes les 30 s). Une nouvelle version publiée est proposée
	après le déverrouillage ; pour l'installer, l'appli se ferme vraiment (fin
	du processus) et un script remplace son dossier.
	Sous Windows, le raccourci global réglé (Ctrl+Maj+Espace par défaut)
	ouvre la carte de saisie en haut de l'écran, pour la fenêtre active.
*/
fun main(inArgs: Array<String>)
{
	val vStartup  = inArgs.contains(kStartupArg)												// lancé avec Windows : fenêtre cachée
	val vInstance = SingleInstance()															// une seule appli à la fois
	if (!vInstance.claim(inNotify = !vStartup)) exitProcess(0)
	application {
		val vScope      = rememberCoroutineScope()												// rechargement après une synchronisation
		val vStore      = remember { VaultStore(File(dataDir(), "coffre.kdbx"), VaultBackups(backupDir(), 7)) }	// coffre du PC et ses sauvegardes
		val vController = remember {
			AppController(
				fStore    = vStore,
				fFiles    = DesktopFiles(),
				fSettings = SettingsStore(File(dataDir(), "reglages.json")),
				fUpdater  = DesktopUpdateInstaller(fOnQuit = ::exitApplication)
			).also { vApp ->
				if (appFolder()?.let(::cleanLeftover) == true) vApp.announce("La mise à jour précédente n'a pas pu remplacer l'appli : elle vous sera reproposée.")
			}
		}																						// état de l'appli
		val vSync       = remember {
			PcSync(
				fFile      = File(dataDir(), "appareils.json"),
				fVault     = vStore,
				fName      = pcName(),
				fVersion   = appVersion() ?: "dev",
				fNetworks  = ::currentNetworks,
				fAddresses = ::localAddresses,
				fOnStored  = { vScope.launch { vController.reloadFromDisk() } }
			)
		}																						// synchronisation avec les téléphones
		DisposableEffect(vSync) { onDispose { vSync.stop() } }
		LaunchedEffect(vSync) {
			while (true)
			{
				withContext(Dispatchers.IO) { runCatching { vSync.refresh() } }
				delay(30_000)
			}
		}
		val vWindow     = rememberWindowState(size = DpSize(1200.dp, 780.dp), position = WindowPosition.Aligned(Alignment.Center))	// fenêtre
		val vTray       = rememberTrayState()													// icône de la zone de notification
		val vIcon       = remember { BitmapPainter(logoImage(64)) }								// logo
		var vVisible    by remember { mutableStateOf(!vStartup) }								// fenêtre affichée (cachée au démarrage avec Windows)
		var vTold       by remember { mutableStateOf(false) }									// notification « toujours là » déjà montrée
		val vShow       by vInstance.requests.collectAsState()									// demandes d'une seconde instance
		LaunchedEffect(vShow) { if (vShow > 0) vVisible = true }
		LaunchedEffect(vWindow) {
			snapshotFlow { !vVisible || vWindow.isMinimized }.collect { vController.setHidden(it) }	// absence : délai de grâce
		}
		LaunchedEffect(Unit) { SessionWatch.watch { vController.lock() } }
		var vQuick      by remember { mutableStateOf<QuickRequest?>(null) }						// carte de saisie affichée ; null sans
		var vSerial     by remember { mutableStateOf(0) }										// numéro de la dernière demande
		var vHotkeyOk   by remember { mutableStateOf(true) }									// raccourci accepté par Windows
		var vDismiss    by remember { mutableStateOf(0) }										// Échap appuyée hors de la carte
		val vBridge     = remember {
			if (WinBridge.available) WinBridge(
				fOnHotkey = {
					val vActive = ActiveWindow.current()												// fenêtre active, lue tout de suite
					if (!vActive.isOwn) SwingUtilities.invokeLater {
						vSerial += 1
						vController.setHidden(false)
						vQuick = QuickRequest(vActive, vActive.target, vSerial)
					}
				},
				fOnEscape = { SwingUtilities.invokeLater { vDismiss += 1 } }
			).also { it.start() } else null
		}																						// raccourci global et presse-papiers enchaîné (Windows)
		LaunchedEffect(vController.settings.quickHotkey) {
			val vHotkey = Hotkey.parse(vController.settings.quickHotkey)							// raccourci réglé
			vHotkeyOk   = withContext(Dispatchers.IO) { vBridge?.register(vHotkey.modifiers, vHotkey.key) ?: true }
		}
		LaunchedEffect(vQuick != null) { vBridge?.escape(vQuick != null) }
		LaunchedEffect(vQuick?.serial) {
			val vRequest = vQuick ?: return@LaunchedEffect											// demande en cours
			if (!vRequest.target.isBrowser) return@LaunchedEffect
			val vUrl     = withContext(Dispatchers.IO) { vRequest.active.readUrl() }				// adresse du site
			if (vUrl != null && vQuick?.serial == vRequest.serial) vQuick = vRequest.copy(target = vRequest.target.withUrl(vUrl))
		}
		QuickWindow(
			inRequest    = vQuick,
			inController = vController,
			inOnResult   = { vResult ->
				vBridge?.offer(vResult.sequence, vController.settings.clipboardSeconds)
				vResult.change?.let { vChange -> vScope.launch { vController.edit(vChange) } }
			},
			inOnClosed   = { vRestore ->
				val vRequest = vQuick																		// demande fermée
				vQuick = null
				if (vRestore) vRequest?.active?.restore()
				if (!vVisible) vController.setHidden(true)
			},
			inOnOpenApp  = { vVisible = true },
			inIcon       = vIcon,
			inDismiss    = vDismiss
		)
		Tray(
			icon     = vIcon,
			state    = vTray,
			tooltip  = "KeePlus",
			onAction = { vVisible = true },
			menu     = {
				Item("Ouvrir KeePlus", onClick = { vVisible = true })
				Item("Verrouiller", enabled = vController.screen is AppScreen.Opened, onClick = { vController.lock() })
				Separator()
				Item("Quitter", onClick = ::exitApplication)
			}
		)
		Window(
			visible        = vVisible,
			onCloseRequest = {
				vVisible = false
				if (!vTold)
				{
					vTold = true
					vTray.sendNotification(Notification("KeePlus reste ouvert", "Il attend le téléphone près de l'horloge. « Quitter » dans le menu de l'icône pour le fermer.", Notification.Type.Info))
				}
			},
			state          = vWindow,
			title          = "KeePlus",
			icon           = vIcon
		) {
			window.minimumSize = Dimension(420, 640)
			LaunchedEffect(Unit) { TitleBar.apply(window, KeeColors.void, KeeColors.text, true) }
			LaunchedEffect(vVisible, vShow) {
				if (!vVisible) return@LaunchedEffect
				if (window.isMinimized) window.isMinimized = false
				withTimeoutOrNull(2_000) { while (!window.isShowing) delay(16) }
				if (window.isShowing) WindowFront.bring(window)
			}
			KeePlusApp(
				inController = vController,
				inSettings   = {
					DesktopSettings(
						inBackups  = vController.backupFolder,
						inHotkey   = vController.settings.quickHotkey.takeIf { vBridge != null },
						inHotkeyOk = vHotkeyOk,
						inOnHotkey = { vController.updateSettings(vController.settings.copy(quickHotkey = it)) }
					)
				},
				inDevices    = { DevicesPanel(vSync) { vController.showDevices = false } }
			)
		}
	}
	exitProcess(0)
}

/*
	Dossier des sauvegardes du coffre, visible (copie facile sur une clé
	USB) : Documents\KeePlus\Sauvegardes.
*/
fun backupDir(): File = File(System.getProperty("user.home"), "Documents/KeePlus/Sauvegardes")

/*
	Nom de ce PC : nom de l'ordinateur sous Windows, sinon nom d'hôte.
*/
fun pcName(): String =
	System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
		?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() }
		?: "PC"


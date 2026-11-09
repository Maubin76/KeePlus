package fr.maubin.keeplus.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.KeePlusApp
import fr.maubin.keeplus.ui.PickedFile
import fr.maubin.keeplus.ui.VaultFiles
import fr.maubin.keeplus.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

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
*/
fun main() = application {
	val vState      = rememberWindowState(size = DpSize(1100.dp, 760.dp))					// taille de la fenêtre
	val vController = remember { AppController(VaultStore(File(dataDir(), "coffre.kdbx")), DesktopFiles()) }	// état de l'appli
	Window(onCloseRequest = ::exitApplication, state = vState, title = "KeePlus") {
		KeePlusApp(vController)
	}
}

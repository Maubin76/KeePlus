package fr.maubin.keeplus

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.KeePlusApp
import fr.maubin.keeplus.ui.PickedFile
import fr.maubin.keeplus.ui.VaultFiles
import fr.maubin.keeplus.vault.VaultStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
	Activité unique de KeePlus. Écran protégé (FLAG_SECURE) : ni capture, ni
	aperçu dans les applications récentes. Le coffre est dans le stockage
	privé de l'appli, inaccessible aux autres applis.
	fPending    : choix de fichier en attente du sélecteur
	fPicker     : sélecteur de documents du système
	fController : état de l'appli
*/
class MainActivity : ComponentActivity(), VaultFiles
{
	private var fPending: CompletableDeferred<Uri?>? = null
	private val fPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { inUri -> fPending?.complete(inUri) }
	private lateinit var fController: AppController

	/*
		Création : écran protégé, barres système transparentes sur fond sombre,
		contenu Compose, touche Retour confiée à l'appli quand elle a un sens.
		inState : état sauvegardé
	*/
	override fun onCreate(inState: Bundle?)
	{
		super.onCreate(inState)
		window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
		enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
		fController = AppController(VaultStore(File(filesDir, "coffre.kdbx")), this)
		setContent {
			BackHandler(enabled = fController.canGoBack) { fController.back() }
			KeePlusApp(fController)
		}
	}

	/*
		Ouvre le sélecteur de documents et lit le fichier choisi ; null si annulé.
	*/
	override suspend fun pickVault(): PickedFile?
	{
		val vPending = CompletableDeferred<Uri?>().also { fPending = it }					// réponse attendue
		fPicker.launch(arrayOf("*/*"))
		val vUri     = vPending.await() ?: return null										// document choisi
		return withContext(Dispatchers.IO) {
			val vName  = contentResolver.query(vUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { vCursor ->
				if (vCursor.moveToFirst()) vCursor.getString(0) else null
			} ?: "coffre.kdbx"																// nom du fichier
			val vBytes = contentResolver.openInputStream(vUri)?.use { it.readBytes() } ?: error("Fichier illisible")	// contenu
			PickedFile(vName, vBytes)
		}
	}
}

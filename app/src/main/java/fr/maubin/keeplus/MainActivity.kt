package fr.maubin.keeplus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import fr.maubin.keeplus.settings.SettingsStore
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.KeePlusApp
import fr.maubin.keeplus.ui.PickedFile
import fr.maubin.keeplus.ui.VaultFiles
import fr.maubin.keeplus.ui.screens.LaunchIntro
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
	Activité unique de KeePlus. Écran protégé (FLAG_SECURE) : ni capture, ni
	aperçu dans les applications récentes. Le coffre est dans le stockage
	privé de l'appli, inaccessible aux autres applis.
	Verrouillage : écran éteint (aussitôt), appli en arrière-plan (au-delà du
	délai de grâce, voir AppController).
	fPending    : choix de fichier en attente du sélecteur
	fPicker     : sélecteur de documents du système
	fController : état de l'appli
	fScreenOff  : écoute de l'extinction de l'écran
	fSync       : synchronisation avec les PC
*/
class MainActivity : FragmentActivity(), VaultFiles
{
	private var fPending: CompletableDeferred<Uri?>? = null
	private val fPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { inUri -> fPending?.complete(inUri) }
	private lateinit var fController: AppController
	private lateinit var fSync: AndroidSync
	private val fScreenOff = object : BroadcastReceiver()
	{
		/*
			Écran éteint : le coffre est verrouillé tout de suite.
			inContext : contexte
			inIntent  : événement
		*/
		override fun onReceive(
			inContext : Context,
			inIntent  : Intent
		)
		{
			fController.lock()
		}
	}

	/*
		Création : écran protégé, barres système transparentes sur fond sombre,
		contenu Compose précédé de l'animation d'ouverture (au lancement
		seulement), touche Retour confiée à l'appli quand elle a un sens.
		inState : état sauvegardé
	*/
	override fun onCreate(inState: Bundle?)
	{
		super.onCreate(inState)
		window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
		enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
		fSync       = AndroidSync.get(this)
		fController = AppController(
			fStore     = fSync.fStore,
			fFiles     = this,
			fBiometric = AndroidBiometric(this),
			fSync      = fSync,
			fSettings  = SettingsStore(File(filesDir, "reglages.json")),
			fUpdater   = AndroidUpdateInstaller(applicationContext)
		)
		registerReceiver(fScreenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
		scheduleSync(this)
		lifecycleScope.launch {
			fSync.changes.collect {
				fController.reloadFromDisk()
				fController.mergePending()
			}
		}																						// coffre remplacé ou en attente de fusion
		fController.introPlaying = inState == null
		setContent {
			var vIntro by remember { mutableStateOf(inState == null) }							// animation d'ouverture affichée
			BackHandler(enabled = fController.canGoBack) { fController.back() }
			Box(Modifier.fillMaxSize()) {
				KeePlusApp(
					inController = fController,
					inSettings   = { PhoneSettings(fSync.fStore) },
					inDevices    = { DevicesScreen(fSync) { fController.showDevices = false } }
				)
				if (vIntro) LaunchIntro(inOnFaded = { fController.introPlaying = false }, inOnDone = { vIntro = false })
			}
		}
	}

	/*
		Retour au premier plan : verrouille si l'absence a été trop longue, puis
		synchronise avec les PC de la maison.
	*/
	override fun onStart()
	{
		super.onStart()
		fController.setHidden(false)
		fSync.syncSoon()
	}

	/*
		Retour à l'appli (écran d'autorisation d'installer, par exemple) : une
		mise à jour qui attendait l'autorisation reprend.
	*/
	override fun onResume()
	{
		super.onResume()
		fController.updates?.onResume()
	}

	/*
		Passage en arrière-plan : début du délai de grâce.
	*/
	override fun onStop()
	{
		super.onStop()
		fController.setHidden(true)
	}

	/*
		Fin de l'activité : plus d'écoute de l'écran.
	*/
	override fun onDestroy()
	{
		unregisterReceiver(fScreenOff)
		super.onDestroy()
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

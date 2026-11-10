package fr.maubin.keeplus.autofill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import fr.maubin.keeplus.AndroidSync
import fr.maubin.keeplus.settings.SettingsStore
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.VaultStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/*
	Coffre ouvert prêté à l'auto-remplissage, pour tout le processus :
	celui de l'appli (tant que ses délais de verrouillage ne sont pas
	dépassés), sinon celui déverrouillé par l'auto-remplissage lui-même,
	gardé le délai « en arrière-plan » des réglages (connexion en deux
	écrans : une seule empreinte), et oublié dès que l'écran s'éteint.
	fApp       : appli ouverte ; null sans
	fOwn       : coffre déverrouillé par l'auto-remplissage ; null sans
	fOwnStore  : accès au fichier qui a servi à l'ouvrir
	fOwnUntil  : fin de validité de fOwn (horloge depuis le démarrage, ms)
	fListening : extinction de l'écran écoutée
	fScope     : enregistrements qui survivent à l'écran qui les a lancés
*/
object VaultSession
{
	@Volatile private var fApp       : AppController? = null
	@Volatile private var fOwn       : Vault?         = null
	@Volatile private var fOwnStore  : VaultStore?    = null
	@Volatile private var fOwnUntil  : Long           = 0L
	private var fListening           : Boolean        = false
	private val fScope                                = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	/*
		L'appli est affichée : son coffre, s'il est ouvert, sert aussi à
		l'auto-remplissage.
		inController : état de l'appli
	*/
	fun attach(inController: AppController)
	{
		fApp = inController
	}

	/*
		L'appli se ferme : son coffre n'est plus prêté.
		inController : état de l'appli
	*/
	fun detach(inController: AppController)
	{
		if (fApp === inController) fApp = null
	}

	/*
		Coffre utilisable ; null s'il faut déverrouiller.
	*/
	fun vault(): Vault? = fApp?.activeVault() ?: fOwn?.takeIf { SystemClock.elapsedRealtime() < fOwnUntil }

	/*
		Garde un coffre déverrouillé par l'auto-remplissage, prêté le délai
		« en arrière-plan » des réglages (pas du tout si ce délai est nul ;
		il sert alors seulement à enregistrer le lien avec l'appli).
		inContext : contexte
		inVault   : coffre ouvert
		inStore   : accès au fichier qui a servi à l'ouvrir
	*/
	fun keep(
		inContext : Context,
		inVault   : Vault,
		inStore   : VaultStore
	)
	{
		val vSeconds = SettingsStore(File(inContext.filesDir, "reglages.json")).load().hiddenSeconds	// délai réglé
		listenScreen(inContext.applicationContext)
		fOwn      = inVault
		fOwnStore = inStore
		fOwnUntil = SystemClock.elapsedRealtime() + vSeconds * 1000L
	}

	/*
		Oublie le coffre déverrouillé par l'auto-remplissage.
	*/
	fun forget()
	{
		fOwn      = null
		fOwnStore = null
		fOwnUntil = 0L
	}

	/*
		Modifie le coffre prêté et l'enregistre : par l'appli si c'est le
		sien, sinon directement (l'appli et la synchronisation sont
		prévenues). Un coffre arrivé entre-temps d'un autre appareil n'est
		jamais écrasé : la modification est alors abandonnée.
		inContext : contexte
		inVault   : coffre utilisé
		inChange  : modification
	*/
	suspend fun edit(
		inContext : Context,
		inVault   : Vault,
		inChange  : (Vault) -> Vault
	)
	{
		val vApp = fApp																		// appli ouverte
		if (vApp != null && vApp.activeVault() === inVault)
		{
			vApp.edit(inChange)
			return
		}
		val vStore = fOwnStore ?: return														// accès au fichier
		if (fOwn !== inVault) return
		val vNew   = inChange(inVault)															// coffre modifié
		if (vNew === inVault) return
		if (vStore.save(vNew) != null)
		{
			forget()
			return
		}
		fOwn = vNew
		AndroidSync.get(inContext).savedElsewhere()
	}

	/*
		Lance edit sans attendre : l'écran d'auto-remplissage se ferme tout de
		suite (le formulaire est rempli aussitôt), l'enregistrement (Argon2,
		quelques secondes) se poursuit en arrière-plan. Un échec est ignoré :
		le formulaire est rempli quoi qu'il arrive.
		inContext : contexte de l'application
		inVault   : coffre utilisé
		inChange  : modification
	*/
	fun editLater(
		inContext : Context,
		inVault   : Vault,
		inChange  : (Vault) -> Vault
	)
	{
		fScope.launch { runCatching { edit(inContext, inVault, inChange) } }
	}

	/*
		Écoute l'extinction de l'écran (une fois pour le processus) : le coffre
		de l'auto-remplissage est alors oublié.
		inContext : contexte de l'application
	*/
	@Synchronized
	private fun listenScreen(inContext: Context)
	{
		if (fListening) return
		fListening = true
		inContext.registerReceiver(object : BroadcastReceiver()
		{
			/*
				Écran éteint.
				inContext : contexte
				inIntent  : événement
			*/
			override fun onReceive(
				inContext : Context,
				inIntent  : Intent
			)
			{
				forget()
			}
		}, IntentFilter(Intent.ACTION_SCREEN_OFF))
	}
}

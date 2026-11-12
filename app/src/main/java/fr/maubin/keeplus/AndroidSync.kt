package fr.maubin.keeplus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import fr.maubin.keeplus.link.NetworkId
import fr.maubin.keeplus.link.PairingOffer
import fr.maubin.keeplus.link.SyncCallListener
import fr.maubin.keeplus.link.discoverPcs
import fr.maubin.keeplus.link.isHomeNetwork
import fr.maubin.keeplus.sync.PendingMerge
import fr.maubin.keeplus.sync.PhonePeers
import fr.maubin.keeplus.sync.PhoneSync
import fr.maubin.keeplus.ui.SyncHooks
import fr.maubin.keeplus.vault.VaultBackups
import fr.maubin.keeplus.vault.VaultLocation
import fr.maubin.keeplus.vault.VaultStore
import fr.maubin.keeplus.vault.kVaultFileName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private const val kChangeDelay = 3_000L													// synchronisation après un enregistrement (ms)
private const val kMergeWaitMs = 15_000L													// attente de la fusion par l'appli, synchronisation complète (ms)

/*
	Réseau du téléphone, tel que la synchronisation le voit.
	onWifi     : relié à un Wi-Fi
	unmetered  : réseau non facturé à l'usage
	wifi       : Wi-Fi reconnu par son nom ; null sans Wi-Fi ou sans autorisation de localisation
	permission : autorisation de localisation accordée (lecture du nom du Wi-Fi)
*/
data class PhoneNetwork(
	val onWifi     : Boolean,
	val unmetered  : Boolean,
	val wifi       : NetworkId?,
	val permission : Boolean
)

/*
	Vrai si l'autorisation de localisation, nécessaire pour lire le nom du
	Wi-Fi depuis Android 10, est accordée.
	inContext : contexte
*/
fun hasWifiNamePermission(inContext: Context): Boolean =
	inContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/*
	Réseau courant du téléphone : Wi-Fi ou non, facturé ou non, nom du Wi-Fi
	si l'autorisation est accordée (seul le nom est lu, jamais une position).
	inContext : contexte
*/
fun currentPhoneNetwork(inContext: Context): PhoneNetwork
{
	val vManager = inContext.getSystemService(ConnectivityManager::class.java)			// connexions
	val vCaps    = vManager?.getNetworkCapabilities(vManager.activeNetwork)				// réseau actif
	val vOnWifi  = vCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true		// Wi-Fi
	val vAllowed = hasWifiNamePermission(inContext)										// autorisation
	@Suppress("DEPRECATION")
	val vSsid    = if (vOnWifi && vAllowed) inContext.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid else null	// nom du Wi-Fi
	return PhoneNetwork(
		onWifi     = vOnWifi,
		unmetered  = vCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
		wifi       = NetworkId.wifi(vSsid),
		permission = vAllowed
	)
}

/*
	Accès au coffre du téléphone (stockage privé, ou dossier choisi) et à
	ses sauvegardes. Un
	accès distinct retient son propre dernier coffre lu ou écrit : celui de
	l'auto-remplissage ne se confond pas avec celui de l'appli.
	inContext : contexte
*/
fun phoneStore(inContext: Context): VaultStore =
	VaultStore(
		VaultLocation(File(inContext.filesDir, kVaultFileName), File(inContext.filesDir, "emplacement.txt")),
		VaultBackups(File(inContext.filesDir, "sauvegardes"), 3)
	)

/*
	Nom du téléphone : celui donné dans les réglages d'Android, sinon le modèle.
	inContext : contexte
*/
fun phoneName(inContext: Context): String =
	Settings.Global.getString(inContext.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL

/*
	État de la synchronisation du téléphone.
*/
sealed interface PhoneStatus
{
	/*
		Synchronisation désactivée (réglage).
	*/
	data object Disabled : PhoneStatus

	/*
		Aucun PC appairé.
	*/
	data object Unpaired : PhoneStatus

	/*
		Hors du réseau familial (ou nom du Wi-Fi illisible) : rien n'est tenté.
		reason : explication
	*/
	data class Away(val reason: String) : PhoneStatus

	/*
		Synchronisation en cours.
	*/
	data object Working : PhoneStatus

	/*
		À jour.
		at  : instant (ms)
		pcs : PC joints
	*/
	data class Idle(
		val at  : Long,
		val pcs : List<String>
	) : PhoneStatus

	/*
		Coffre d'un PC à fusionner : il faut déverrouiller le coffre.
	*/
	data object Pending : PhoneStatus

	/*
		Échec (aucun PC joignable, refus…).
		message : raison
	*/
	data class Failed(val message: String) : PhoneStatus
}

/*
	Synchronisation du téléphone, une seule pour l'appli et la tâche de fond :
	**seulement sur le Wi-Fi de la maison** (nom reconnu, réseau non facturé),
	avec chaque PC appairé joignable. Le coffre est échangé tel quel ; un
	coffre de PC à fusionner attend le déverrouillage (voir SyncHooks).
	fContext : contexte de l'application
	fStore   : coffre du téléphone
	fEngine  : échanges avec les PC
*/
class AndroidSync private constructor(
	private val fContext : Context,
	val fStore           : VaultStore,
	private val fEngine  : PhoneSync
) : SyncHooks
{
	private val fScope   = CoroutineScope(SupervisorJob() + Dispatchers.Default)			// synchronisations automatiques
	private val fLock    = Mutex()															// une synchronisation à la fois
	private var fSoon    : Job? = null														// synchronisation programmée après un enregistrement
	private val fStatus  = MutableStateFlow<PhoneStatus>(initialStatus())					// état
	private val fPeers   = MutableStateFlow(fEngine.peers())								// appareils
	private val fChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 4)					// coffre remplacé ou coffre en attente
	private val fSaves   = MutableStateFlow(0)													// enregistrements du coffre par l'appli (compteur)
	private var fFull    : Job? = null														// synchronisation complète en cours
	private val fCalls   = SyncCallListener { inPcId ->
		if (fEngine.peers().pcs.any { it.id == inPcId }) fScope.launch { syncFully() }
	}																						// écoute des appels des PC appairés

	/*
		État de la synchronisation.
	*/
	val status: StateFlow<PhoneStatus> = fStatus

	/*
		PC appairés et réseaux familiaux.
	*/
	val peers: StateFlow<PhonePeers> = fPeers

	/*
		Signal : le coffre du téléphone a été remplacé, ou un coffre attend
		d'être fusionné (l'appli recharge ou fusionne si le coffre est ouvert).
	*/
	val changes: SharedFlow<Unit> = fChanged

	/*
		Réseau du téléphone.
	*/
	fun network(): PhoneNetwork = currentPhoneNetwork(fContext)

	/*
		Appairage avec le PC d'une offre scannée ; null si réussi, sinon la raison.
		inOffer : offre lue dans le QR code
	*/
	suspend fun pair(inOffer: PairingOffer): String? = withContext(Dispatchers.IO) {
		val vNet  = network()																// réseau du téléphone
		val vWifi = vNet.wifi ?: return@withContext if (!vNet.permission) "Autorisez la localisation : elle sert seulement à reconnaître le Wi-Fi de la maison." else "Reliez le téléphone au Wi-Fi de la maison."
		val vError = fEngine.pair(inOffer, vWifi)											// raison d'un échec
		refreshPeers()
		if (vError == null) fScope.launch { sync() }
		vError
	}

	/*
		Retire un PC appairé.
		inId : PC
	*/
	fun remove(inId: String)
	{
		fEngine.remove(inId)
		refreshPeers()
	}

	/*
		Oublie tous les PC appairés (coffre supprimé).
	*/
	fun forget()
	{
		fEngine.peers().pcs.forEach { fEngine.remove(it.id) }
		refreshPeers()
		fStatus.value = initialStatus()
	}

	/*
		Retire un réseau familial.
		inNetwork : réseau
	*/
	fun removeHome(inNetwork: NetworkId)
	{
		fEngine.removeHome(inNetwork)
		refreshPeers()
	}

	/*
		Active ou désactive la synchronisation.
		inOn : vrai pour l'activer
	*/
	fun setEnabled(inOn: Boolean)
	{
		fEngine.setEnabled(inOn)
		refreshPeers()
		fStatus.value = initialStatus()
		if (inOn) fScope.launch { sync() }
	}

	/*
		Synchronisation complète, à la demande (bouton, appel d'un PC) : échange
		avec chaque PC ; si un PC a aussi changé, attente de la fusion par
		l'appli (coffre ouvert) et de son enregistrement, puis renvoi aussitôt.
		Rend l'état final : « à jour », ou la raison (fusion au prochain
		déverrouillage, PC injoignable…). Un second appel pendant la première
		attend simplement son résultat.
	*/
	suspend fun syncFully(): PhoneStatus
	{
		val vJob = fFull?.takeIf { it.isActive } ?: fScope.launch {
			val vSaves = fSaves.value																// enregistrements avant l'échange
			if (sync() != PhoneStatus.Pending) return@launch
			val vMerged = withTimeoutOrNull(kMergeWaitMs) {
				fSaves.first { it > vSaves && fEngine.pending().isEmpty() }
			}																						// fusion faite et enregistrée ; null sinon
			if (vMerged != null)
			{
				fSoon?.cancel()
				sync()
			}
		}.also { fFull = it }																	// synchronisation complète
		vJob.join()
		return fStatus.value
	}

	/*
		Écoute ou non les appels des PC (« synchronisez-vous ») : seulement
		tant que l'appli est à l'écran.
		inOn : vrai pour écouter
	*/
	fun listenForCalls(inOn: Boolean)
	{
		if (inOn) runCatching { fCalls.start() } else fCalls.stop()
	}

	/*
		Lance une synchronisation en arrière-plan (retour au premier plan).
	*/
	fun syncSoon()
	{
		fScope.launch { sync() }
	}

	/*
		Synchronise avec chaque PC joignable, seulement sur le réseau familial.
	*/
	suspend fun sync(): PhoneStatus =
		fLock.withLock {
			withContext(Dispatchers.IO) {
				val vPeers = fEngine.peers()																	// appareils
				val vNet   = network()																			// réseau du téléphone
				val vNext  = when
				{
					!vPeers.enabled                                -> PhoneStatus.Disabled
					vPeers.pcs.isEmpty()                           -> PhoneStatus.Unpaired
					!vNet.onWifi                                   -> PhoneStatus.Away("Pas de Wi-Fi.")
					!vNet.unmetered                                -> PhoneStatus.Away("Wi-Fi facturé à l'usage.")
					vNet.wifi == null                              -> PhoneStatus.Away(if (!vNet.permission) "Autorisez la localisation pour reconnaître le Wi-Fi de la maison." else "Nom du Wi-Fi illisible : activez la localisation du téléphone.")
					!isHomeNetwork(listOf(vNet.wifi), vPeers.home) -> PhoneStatus.Away("Hors du réseau de la maison (« ${vNet.wifi.value} »).")
					else                                           ->
					{
						fStatus.value = PhoneStatus.Working
						val vReport = fEngine.syncAll { discoverPcs() }										// bilan
						if (vReport.took || vReport.pending) fChanged.tryEmit(Unit)
						when
						{
							vReport.errors.isNotEmpty() -> PhoneStatus.Failed(vReport.errors.joinToString("\n"))
							vReport.pending             -> PhoneStatus.Pending
							vReport.reached.isEmpty()   -> PhoneStatus.Failed("Aucun PC joignable : ouvrez KeePlus sur un PC de la maison.")
							else                        -> PhoneStatus.Idle(System.currentTimeMillis(), vReport.reached)
						}
					}
				}
				fStatus.value = vNext
				refreshPeers()
				vNext
			}
		}

	/*
		Coffres de PC en attente de fusion.
	*/
	override fun pending(): List<PendingMerge> = fEngine.pending()

	/*
		Un coffre en attente a été fusionné (ou écarté).
		inMerge  : coffre en attente
		inMerged : vrai s'il a été fusionné
	*/
	override fun settle(
		inMerge  : PendingMerge,
		inMerged : Boolean
	)
	{
		fEngine.settle(inMerge, inMerged)
		if (fStatus.value == PhoneStatus.Pending) fStatus.value = PhoneStatus.Working
	}

	/*
		Le coffre vient d'être enregistré : synchronisation quelques secondes
		plus tard (plusieurs modifications rapprochées n'en font qu'une).
	*/
	override fun changed()
	{
		fSaves.value += 1
		fSoon?.cancel()
		fSoon = fScope.launch {
			delay(kChangeDelay)
			sync()
		}
	}

	/*
		Le coffre a été enregistré hors de l'appli (auto-remplissage) : l'appli
		le relit si elle l'a ouvert, et la synchronisation suit.
	*/
	fun savedElsewhere()
	{
		fChanged.tryEmit(Unit)
		changed()
	}

	/*
		Relit les appareils enregistrés.
	*/
	private fun refreshPeers()
	{
		fPeers.value = fEngine.peers()
	}

	/*
		État de départ, selon le réglage et les PC appairés.
	*/
	private fun initialStatus(): PhoneStatus =
		when
		{
			!fEngine.peers().enabled      -> PhoneStatus.Disabled
			fEngine.peers().pcs.isEmpty() -> PhoneStatus.Unpaired
			fEngine.pending().isNotEmpty() -> PhoneStatus.Pending
			else                          -> PhoneStatus.Away("Pas encore synchronisé.")
		}

	companion object
	{
		@Volatile private var fInstance: AndroidSync? = null								// synchronisation du processus

		/*
			Synchronisation du processus (appli et tâche de fond).
			inContext : contexte
		*/
		fun get(inContext: Context): AndroidSync =
			fInstance ?: synchronized(this) {
				fInstance ?: run {
					val vApp   = inContext.applicationContext											// contexte de l'application
					val vStore = phoneStore(vApp)														// coffre du téléphone et ses sauvegardes
					AndroidSync(vApp, vStore, PhoneSync(File(vApp.filesDir, "synchro"), vStore, phoneName(vApp), BuildConfig.VERSION_NAME))
				}.also { fInstance = it }
			}
	}
}

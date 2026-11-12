package fr.maubin.keeplus.sync

import fr.maubin.keeplus.link.Beacon
import fr.maubin.keeplus.link.broadcastAddresses
import fr.maubin.keeplus.link.callPhones as sendCall
import fr.maubin.keeplus.link.kCallPort
import fr.maubin.keeplus.link.DeviceInfo
import fr.maubin.keeplus.link.DiscoveryResponder
import fr.maubin.keeplus.link.LinkCall
import fr.maubin.keeplus.link.LinkServer
import fr.maubin.keeplus.link.NetworkId
import fr.maubin.keeplus.link.PairingOffer
import fr.maubin.keeplus.link.fromBase64
import fr.maubin.keeplus.link.isHomeNetwork
import fr.maubin.keeplus.link.kDiscoveryPort
import fr.maubin.keeplus.link.kLinkPort
import fr.maubin.keeplus.link.kPairValidityMs
import fr.maubin.keeplus.link.newLinkKey
import fr.maubin.keeplus.link.toBase64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.File
import java.net.InetAddress
import java.util.UUID

/*
	Téléphone appairé avec ce PC.
	id          : identifiant du téléphone
	name        : son nom
	key         : clé secrète de la paire (base64)
	pairedAt    : instant de l'appairage (ms)
	lastSync    : dernière synchronisation réussie (ms) ; null avant la première
	lastAddress : dernière adresse d'où il s'est connecté ; null avant
*/
@Serializable
data class PairedPhone(
	val id          : String,
	val name        : String,
	val key         : String,
	val pairedAt    : Long,
	val lastSync    : Long?   = null,
	val lastAddress : String? = null
)

/*
	Fichier des appareils du PC.
	deviceId : identifiant de ce PC (tiré au hasard la première fois)
	phones   : téléphones appairés
	home     : réseaux familiaux (nom du Wi-Fi, box)
*/
@Serializable
data class PcPeers(
	val deviceId : String            = UUID.randomUUID().toString(),
	val phones   : List<PairedPhone> = emptyList(),
	val home     : List<NetworkId>   = emptyList()
)

/*
	Dernier échange d'un téléphone avec ce PC (depuis le lancement).
	at       : instant (ms)
	upToDate : vrai si les deux coffres sont identiques après l'échange ;
	           faux si le téléphone doit encore fusionner celui du PC
*/
data class PhoneExchange(
	val at       : Long,
	val upToDate : Boolean
)

/*
	État de la synchronisation du PC.
	running  : serveur à l'écoute
	onHome   : PC sur le réseau familial
	networks : réseaux auxquels le PC est relié
	home     : réseaux familiaux
	phones   : téléphones appairés
	offer    : offre d'appairage affichée (QR code) ; null sans
	paired   : téléphone appairé à l'instant (message) ; null sans
	error    : échec du démarrage ; null sinon
	last     : dernier échange de chaque téléphone, par identifiant
*/
data class PcSyncState(
	val running  : Boolean           = false,
	val onHome   : Boolean           = false,
	val networks : List<NetworkId>   = emptyList(),
	val home     : List<NetworkId>   = emptyList(),
	val phones   : List<PairedPhone> = emptyList(),
	val offer    : PairingOffer?     = null,
	val paired   : String?           = null,
	val error    : String?           = null,
	val last     : Map<String, PhoneExchange> = emptyMap()
)

/*
	Synchronisation du PC : serveur des échanges et répondeur de découverte,
	**seulement sur le réseau familial** (ou le temps d'un appairage) ;
	appairage par une offre affichée en QR code, valable cinq minutes ;
	réponse aux téléphones appairés (voir answerSync).
	fFile      : fichier des appareils (appareils.json)
	fVault     : coffre du PC
	fName      : nom de ce PC
	fVersion   : version de l'appli
	fNetworks  : lecture des réseaux du PC
	fAddresses : adresses du PC sur le réseau local (QR code)
	fOnStored  : appelé quand un téléphone a remplacé le coffre du PC (rechargement)
	fNow       : horloge (ms)
	fPort      : port du serveur (0 : port libre, tests)
	fBeacon    : port de découverte (0 : port libre, tests)
	fBind      : adresse d'écoute ; null pour toutes les interfaces
*/
class PcSync(
	private val fFile      : File,
	private val fVault     : VaultFile,
	private val fName      : String,
	private val fVersion   : String,
	private val fNetworks  : () -> List<NetworkId>,
	private val fAddresses : () -> List<String>,
	private val fOnStored  : (String) -> Unit           = {},
	private val fNow       : () -> Long                 = System::currentTimeMillis,
	private val fPort      : Int                        = kLinkPort,
	private val fBeacon    : Int                        = kDiscoveryPort,
	private val fBind      : InetAddress?               = null
)
{
	private val fState  = MutableStateFlow(load().let { PcSyncState(phones = it.phones, home = it.home) })	// état
	private var fServer : LinkServer? = null												// serveur démarré
	private var fFinder : DiscoveryResponder? = null										// répondeur démarré

	/*
		État de la synchronisation.
	*/
	val state: StateFlow<PcSyncState> = fState

	/*
		Port du serveur ; 0 à l'arrêt.
	*/
	val port: Int get() = fServer?.port ?: 0

	/*
		Présentation de ce PC.
	*/
	fun info(): DeviceInfo = DeviceInfo(load().deviceId, fName, "windows", fVersion, kVaultFormat)

	/*
		Relit le réseau du PC ; démarre ou arrête l'écoute selon qu'il est sur
		le réseau familial avec un téléphone appairé (ou qu'un appairage est
		en cours). Une offre expirée est retirée.
	*/
	@Synchronized
	fun refresh()
	{
		val vNetworks = fNetworks()																// réseaux du PC
		val vOffer    = fState.value.offer?.takeIf { it.expires > fNow() }						// offre encore valable
		val vHome     = isHomeNetwork(vNetworks, fState.value.home)								// sur le réseau familial
		fState.update { it.copy(networks = vNetworks, onHome = vHome, offer = vOffer) }
		if (vHome && fState.value.phones.isNotEmpty() || vOffer != null) start() else stop()
	}

	/*
		Affiche une offre d'appairage (QR code) valable cinq minutes ;
		l'écoute démarre le temps de l'appairage.
	*/
	@Synchronized
	fun startPairing()
	{
		start()
		val vOffer = PairingOffer(info().id, fName, fAddresses(), port, newLinkKey(), fNow() + kPairValidityMs)	// offre
		fState.update { it.copy(offer = vOffer, paired = null, networks = fNetworks()) }
	}

	/*
		Retire l'offre d'appairage affichée.
	*/
	@Synchronized
	fun cancelPairing()
	{
		fState.update { it.copy(offer = null) }
		refresh()
	}

	/*
		Appelle les téléphones appairés : ceux qui ont KeePlus ouvert à l'écran
		se synchronisent aussitôt (voir callPhones). Rend faux si le PC
		n'écoute pas (hors du réseau de la maison) : un téléphone ne pourrait
		pas le joindre.
		inPort : port d'écoute des téléphones
	*/
	fun callPhones(inPort: Int = kCallPort): Boolean
	{
		if (fServer == null) return false
		val vPhones = fState.value.phones.mapNotNull { vPhone -> vPhone.lastAddress?.let { runCatching { InetAddress.getByName(it) }.getOrNull() } }	// dernières adresses connues
		sendCall(info().id, broadcastAddresses() + vPhones, inPort)
		return true
	}

	/*
		Retire un téléphone : il n'est plus accepté.
		inId : téléphone
	*/
	@Synchronized
	fun removePhone(inId: String)
	{
		val vPeers = load().let { it.copy(phones = it.phones.filter { vPhone -> vPhone.id != inId }) }	// appareils restants
		save(vPeers)
		fState.update { it.copy(phones = vPeers.phones) }
		refresh()
	}

	/*
		Ajoute un réseau aux réseaux familiaux, ou en retire un.
		inNetwork : réseau
		inAdd     : vrai pour l'ajouter
	*/
	@Synchronized
	fun setHome(
		inNetwork : NetworkId,
		inAdd     : Boolean
	)
	{
		val vPeers = load().let { it.copy(home = if (inAdd) (it.home + inNetwork).distinct() else it.home - inNetwork) }	// réseaux familiaux
		save(vPeers)
		fState.update { it.copy(home = vPeers.home) }
		refresh()
	}

	/*
		Arrête l'écoute (fermeture de l'appli).
	*/
	@Synchronized
	fun stop()
	{
		fServer?.stop()
		fFinder?.stop()
		fServer = null
		fFinder = null
		fState.update { it.copy(running = false) }
	}

	/*
		Démarre l'écoute si elle ne tourne pas.
	*/
	private fun start()
	{
		if (fServer != null) return
		val vServer = LinkServer(::info, ::keyOf, ::handle, inNow = fNow)						// serveur
		val vFinder = DiscoveryResponder { Beacon(info().id, fName, vServer.port) }				// répondeur de découverte
		try
		{
			vServer.start(fPort, fBind)
			vFinder.start(fBeacon, fBind)
		}
		catch (vError: Exception)
		{
			vServer.stop()
			vFinder.stop()
			fState.update { it.copy(running = false, error = "Écoute impossible : ${vError.message ?: "port déjà utilisé"}. Une autre copie de KeePlus est peut-être ouverte.") }
			return
		}
		fServer = vServer
		fFinder = vFinder
		fState.update { it.copy(running = true, error = null) }
	}

	/*
		Clé d'un appareil : celle d'un téléphone appairé, ou celle de l'offre
		d'appairage en cours (pour un appareil encore inconnu).
		inId : appareil
	*/
	private fun keyOf(inId: String): ByteArray? =
		fState.value.phones.firstOrNull { it.id == inId }?.key?.let(::fromBase64)
			?: fState.value.offer?.takeIf { it.expires > fNow() }?.key

	/*
		Réponse à une demande d'un téléphone (message déjà ouvert et vérifié).
		inCall : demande
	*/
	private fun handle(inCall: LinkCall): String
	{
		val vRequest = runCatching { kSyncJson.decodeFromString(SyncRequest.serializer(), inCall.request) }.getOrNull()	// demande
			?: return reply(SyncReply(kActRefused, message = "demande illisible"))
		if (vRequest.op == kOpPair) return reply(pair(inCall, vRequest))
		val vPhone   = fState.value.phones.firstOrNull { it.id == inCall.peerId }				// téléphone appairé
			?: return reply(SyncReply(kActRefused, message = "téléphone non appairé : appairez-le depuis la page Appareils du PC"))
		if (vRequest.op != kOpSync) return reply(SyncReply(kActRefused, message = "demande « ${vRequest.op} » inconnue"))
		val vReply   = answerSync(fVault, vRequest)												// décision
		if (vReply.action != kActRefused) seen(vPhone.id, inCall.address.hostAddress)
		if (vReply.action != kActRefused) fState.update { it.copy(last = it.last + (vPhone.id to PhoneExchange(fNow(), vReply.action != kActMerge))) }
		if (vReply.action == kActStored) fOnStored(vPhone.name)
		return reply(vReply)
	}

	/*
		Appairage d'un téléphone qui a scanné l'offre (sa clé est celle de
		l'offre) : il est enregistré, les réseaux familiaux réunis, l'offre
		retirée.
		inCall    : demande
		inRequest : demande d'appairage
	*/
	@Synchronized
	private fun pair(
		inCall    : LinkCall,
		inRequest : SyncRequest
	): SyncReply
	{
		val vOffer  = fState.value.offer?.takeIf { it.expires > fNow() } ?: return SyncReply(kActRefused, message = "offre d'appairage expirée : affichez un nouveau QR code sur le PC")	// offre en cours
		val vDevice = inRequest.device ?: return SyncReply(kActRefused, message = "présentation absente")	// téléphone
		val vPeers  = load()																		// appareils enregistrés
		val vPhone  = PairedPhone(vDevice.id, vDevice.name, toBase64(vOffer.key), fNow(), lastAddress = inCall.address.hostAddress)	// nouveau téléphone
		val vNext   = vPeers.copy(
			phones = vPeers.phones.filter { it.id != vDevice.id } + vPhone,
			home   = (vPeers.home + inRequest.home + fState.value.networks).distinct()
		)																						// appareils et réseaux familiaux
		save(vNext)
		fState.update { it.copy(phones = vNext.phones, home = vNext.home, offer = null, paired = vDevice.name) }
		refresh()
		return SyncReply(kActSame, pc = info(), home = vNext.home)
	}

	/*
		Note une synchronisation réussie d'un téléphone.
		inId      : téléphone
		inAddress : son adresse
	*/
	@Synchronized
	private fun seen(
		inId      : String,
		inAddress : String
	)
	{
		val vPeers = load().let { it.copy(phones = it.phones.map { vPhone -> if (vPhone.id == inId) vPhone.copy(lastSync = fNow(), lastAddress = inAddress) else vPhone }) }	// appareils
		save(vPeers)
		fState.update { it.copy(phones = vPeers.phones) }
	}

	/*
		Réponse en texte.
		inReply : réponse
	*/
	private fun reply(inReply: SyncReply): String = kSyncJson.encodeToString(SyncReply.serializer(), inReply)

	/*
		Appareils enregistrés ; un fichier neuf (identifiant tiré) s'il est
		absent ou illisible.
	*/
	@Synchronized
	private fun load(): PcPeers =
		runCatching { kSyncJson.decodeFromString(PcPeers.serializer(), fFile.readText()) }.getOrElse { PcPeers().also(::save) }

	/*
		Enregistre les appareils (fichier provisoire puis remplacement).
		inPeers : appareils
	*/
	@Synchronized
	private fun save(inPeers: PcPeers) = writeAtomic(fFile, kSyncJson.encodeToString(PcPeers.serializer(), inPeers).toByteArray(Charsets.UTF_8))
}

/*
	Écrit un fichier sans risque de le perdre : fichier provisoire du même
	dossier, puis remplacement.
	inFile  : fichier
	inBytes : contenu
*/
internal fun writeAtomic(
	inFile  : File,
	inBytes : ByteArray
)
{
	inFile.absoluteFile.parentFile?.mkdirs()
	val vTemp = File(inFile.absoluteFile.parentFile, inFile.name + ".tmp")				// écriture provisoire
	vTemp.writeBytes(inBytes)
	java.nio.file.Files.move(vTemp.toPath(), inFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
}

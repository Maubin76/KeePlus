package fr.maubin.keeplus.sync

import fr.maubin.keeplus.link.DeviceInfo
import fr.maubin.keeplus.link.FoundPc
import fr.maubin.keeplus.link.LinkClient
import fr.maubin.keeplus.link.NetworkId
import fr.maubin.keeplus.link.PairingOffer
import fr.maubin.keeplus.link.fromBase64
import fr.maubin.keeplus.link.toBase64
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/*
	PC appairé avec ce téléphone.
	id        : identifiant du PC
	name      : son nom
	key       : clé secrète de la paire (base64)
	port      : port de son serveur
	addresses : adresses connues (dernière réussie d'abord)
	lastSync  : dernière synchronisation réussie (ms) ; null avant la première
	memory    : ce que le téléphone sait de son coffre
*/
@Serializable
data class PairedPc(
	val id        : String,
	val name      : String,
	val key       : String,
	val port      : Int,
	val addresses : List<String> = emptyList(),
	val lastSync  : Long?        = null,
	val memory    : PcSyncMemory = PcSyncMemory()
)

/*
	Fichier des appareils du téléphone.
	deviceId : identifiant de ce téléphone (tiré au hasard la première fois)
	pcs      : PC appairés
	home     : réseaux familiaux
	enabled  : synchronisation activée
*/
@Serializable
data class PhonePeers(
	val deviceId : String          = UUID.randomUUID().toString(),
	val pcs      : List<PairedPc>  = emptyList(),
	val home     : List<NetworkId> = emptyList(),
	val enabled  : Boolean         = true
)

/*
	Bilan d'une synchronisation du téléphone avec tous ses PC.
	reached : noms des PC joints
	took    : vrai si le coffre du téléphone a été remplacé (à recharger s'il est ouvert)
	pending : vrai si un coffre de PC attend d'être fusionné (au déverrouillage)
	errors  : échecs, en clair
*/
data class SyncReport(
	val reached : List<String>,
	val took    : Boolean,
	val pending : Boolean,
	val errors  : List<String>
)

/*
	Coffre d'un PC en attente de fusion dans celui du téléphone.
	pcId        : PC
	pcName      : son nom
	bytes       : son coffre
	fingerprint : empreinte de ce coffre
*/
class PendingMerge(
	val pcId        : String,
	val pcName      : String,
	val bytes       : ByteArray,
	val fingerprint : String
)

/*
	Synchronisation du téléphone, relais entre les PC : appairage par l'offre
	d'un QR code, puis échange avec chaque PC appairé joignable (dernières
	adresses, puis recherche sur le réseau local). Un coffre de PC à fusionner
	est gardé dans un fichier « attente-<PC>.kdbx » jusqu'au déverrouillage.
	Les conditions (réseau familial, réglage) sont vérifiées par l'appelant.
	fDir     : dossier des fichiers de la synchronisation
	fVault   : coffre du téléphone
	fName    : nom du téléphone
	fVersion : version de l'appli
	fNow     : horloge (ms)
*/
class PhoneSync(
	private val fDir     : File,
	private val fVault   : VaultFile,
	private val fName    : String,
	private val fVersion : String,
	private val fNow     : () -> Long = System::currentTimeMillis
)
{
	private val fFile = File(fDir, "appareils.json")										// PC appairés et réseaux familiaux

	/*
		Présentation de ce téléphone.
	*/
	fun info(): DeviceInfo = DeviceInfo(load().deviceId, fName, "android", fVersion, kVaultFormat)

	/*
		Appareils enregistrés.
	*/
	fun peers(): PhonePeers = load()

	/*
		Active ou désactive la synchronisation.
		inOn : vrai pour l'activer
	*/
	fun setEnabled(inOn: Boolean) = save(load().copy(enabled = inOn))

	/*
		Retire un PC appairé (et son coffre en attente).
		inId : PC
	*/
	fun remove(inId: String)
	{
		save(load().let { it.copy(pcs = it.pcs.filter { vPc -> vPc.id != inId }) })
		pendingFile(inId).delete()
	}

	/*
		Retire un réseau familial.
		inNetwork : réseau
	*/
	fun removeHome(inNetwork: NetworkId) = save(load().let { it.copy(home = it.home - inNetwork) })

	/*
		Appairage avec le PC d'une offre scannée : se présente avec la clé de
		l'offre (adresses de l'offre essayées l'une après l'autre), puis
		enregistre le PC et les réseaux familiaux. Rend null si l'appairage a
		réussi, sinon la raison.
		inOffer : offre lue dans le QR code
		inWifi  : Wi-Fi actuel du téléphone (réseau familial)
	*/
	fun pair(
		inOffer : PairingOffer,
		inWifi  : NetworkId
	): String?
	{
		if (inOffer.expires < fNow()) return "Ce QR code a expiré : affichez-en un nouveau sur le PC."
		val vClient  = LinkClient(info(), fNow)													// client
		val vRequest = kSyncJson.encodeToString(SyncRequest.serializer(), SyncRequest(kOpPair, device = info(), home = listOf(inWifi)))	// demande
		var vError   = "PC injoignable"															// dernière erreur
		for (vHost in inOffer.addresses)
		{
			val vReply = runCatching { kSyncJson.decodeFromString(SyncReply.serializer(), vClient.call(vHost, inOffer.port, inOffer.pcId, inOffer.key, vRequest)) }	// réponse
			vReply.onFailure { vError = it.message ?: "PC injoignable" }
			val vOk    = vReply.getOrNull() ?: continue												// réponse lue
			if (vOk.action == kActRefused) return vOk.message ?: "appairage refusé"
			val vPc    = PairedPc(inOffer.pcId, vOk.pc?.name ?: inOffer.pcName, toBase64(inOffer.key), inOffer.port, listOf(vHost) + inOffer.addresses.filter { it != vHost })	// PC appairé
			save(load().let { vPeers -> vPeers.copy(pcs = vPeers.pcs.filter { it.id != vPc.id } + vPc, home = (vPeers.home + listOf(inWifi) + vOk.home).distinct()) })
			return null
		}
		return "Appairage impossible : $vError. Vérifiez que les deux appareils sont sur le même Wi-Fi et que le QR code est encore valable."
	}

	/*
		Synchronise avec chaque PC appairé joignable.
		inDiscover : recherche des PC sur le réseau local (seulement si une adresse connue ne répond pas)
	*/
	fun syncAll(inDiscover: () -> List<FoundPc>): SyncReport
	{
		val vFound   by lazy { runCatching { inDiscover() }.getOrDefault(emptyList()) }		// PC trouvés sur le réseau local
		val vReached = mutableListOf<String>()														// PC joints
		val vErrors  = mutableListOf<String>()														// échecs
		var vTook    = false																		// coffre remplacé
		for (vPc in load().pcs)
		{
			val vKey    = fromBase64(vPc.key) ?: continue											// clé de la paire
			val vClient = LinkClient(info(), fNow)													// client
			var vDone   = false																		// PC joint
			for (vTry in 0..1)
			{
				val vHosts = if (vTry == 0) vPc.addresses else vFound.filter { it.beacon.id == vPc.id }.map { it.address }.filter { it !in vPc.addresses }	// adresses à essayer
				val vPort  = if (vTry == 0) vPc.port else vFound.firstOrNull { it.beacon.id == vPc.id }?.beacon?.port ?: vPc.port	// port
				for (vHost in vHosts)
				{
					val vOutcome = runCatching { phoneExchange({ vClient.call(vHost, vPort, vPc.id, vKey, it) }, fVault, current(vPc.id).memory) }.getOrNull() ?: continue	// issue de l'échange
					vReached += vPc.name
					when (vOutcome)
					{
						is PhoneOutcome.Synced     -> { vTook = vTook || vOutcome.took; update(vPc.id) { it.copy(memory = vOutcome.memory, lastSync = fNow()) } }
						is PhoneOutcome.MergeLater -> writeAtomic(pendingFile(vPc.id), vOutcome.bytes)
						is PhoneOutcome.Failed     -> vErrors += "${vPc.name} : ${vOutcome.message}"
					}
					update(vPc.id) { it.copy(addresses = (listOf(vHost) + it.addresses).distinct().take(4), port = vPort) }
					vDone = true
					break
				}
				if (vDone) break
			}
		}
		return SyncReport(vReached, vTook, pending().isNotEmpty(), vErrors)
	}

	/*
		Coffres de PC en attente de fusion.
	*/
	fun pending(): List<PendingMerge> =
		load().pcs.mapNotNull { vPc ->
			pendingFile(vPc.id).takeIf { it.isFile }?.readBytes()?.let { PendingMerge(vPc.id, vPc.name, it, vaultFingerprint(it)) }
		}

	/*
		Un coffre en attente a été fusionné dans celui du téléphone (ou
		abandonné) : il est oublié ; s'il a été fusionné, le PC le saura au
		prochain échange et prendra le coffre du téléphone.
		inMerge  : coffre en attente
		inMerged : vrai s'il a été fusionné
	*/
	fun settle(
		inMerge  : PendingMerge,
		inMerged : Boolean
	)
	{
		if (inMerged) update(inMerge.pcId) { it.copy(memory = it.memory.copy(merged = inMerge.fingerprint)) }
		pendingFile(inMerge.pcId).delete()
	}

	/*
		PC appairé par son identifiant (dernier état enregistré).
		inId : PC
	*/
	private fun current(inId: String): PairedPc = load().pcs.first { it.id == inId }

	/*
		Modifie un PC appairé.
		inId    : PC
		inBlock : modification
	*/
	private fun update(
		inId    : String,
		inBlock : (PairedPc) -> PairedPc
	) = save(load().let { vPeers -> vPeers.copy(pcs = vPeers.pcs.map { if (it.id == inId) inBlock(it) else it }) })

	/*
		Fichier du coffre en attente d'un PC (identifiant aux caractères sûrs).
		inId : PC
	*/
	private fun pendingFile(inId: String): File = File(fDir, "attente-" + inId.replace(Regex("[^A-Za-z0-9-]"), "_") + ".kdbx")

	/*
		Appareils enregistrés ; un fichier neuf (identifiant tiré) s'il est
		absent ou illisible.
	*/
	@Synchronized
	private fun load(): PhonePeers =
		runCatching { kSyncJson.decodeFromString(PhonePeers.serializer(), fFile.readText()) }.getOrElse { PhonePeers().also(::save) }

	/*
		Enregistre les appareils.
		inPeers : appareils
	*/
	@Synchronized
	private fun save(inPeers: PhonePeers) = writeAtomic(fFile, kSyncJson.encodeToString(PhonePeers.serializer(), inPeers).toByteArray(Charsets.UTF_8))
}

package fr.maubin.keeplus.sync

import fr.maubin.keeplus.link.DeviceInfo
import fr.maubin.keeplus.link.NetworkId
import fr.maubin.keeplus.link.fromBase64
import fr.maubin.keeplus.link.toBase64
import fr.maubin.keeplus.vault.isKeePassFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

const val kOpPair = "pair"																// appairage du téléphone
const val kOpSync = "sync"																// synchronisation : le téléphone envoie son coffre

const val kActSame    = "same"															// rien à faire : même coffre
const val kActStored  = "stored"														// le PC a pris le coffre du téléphone
const val kActAdopt   = "adopt"															// le téléphone doit prendre le coffre joint (lui n'a pas changé)
const val kActMerge   = "merge"															// les deux ont changé : le téléphone fusionne le coffre joint
const val kActRefused = "refused"														// refus (fichier illisible, appareil inconnu)

const val kVaultFormat = 4																// format des coffres échangés (KDBX 4)

internal val kSyncJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }		// messages et fichiers des appareils

/*
	Demande du téléphone au PC (contenu d'un message scellé).
	op          : nature (kOpPair, kOpSync)
	device      : présentation du téléphone (appairage)
	home        : réseaux familiaux connus du téléphone (appairage)
	vault       : coffre du téléphone, octets du fichier en base64 ; null s'il n'en a pas
	fingerprint : empreinte de ce coffre
	base        : empreinte du dernier coffre commun avec ce PC ; null avant le premier échange
	merged      : empreinte du coffre de ce PC déjà fusionné dans celui du téléphone ; null sinon
*/
@Serializable
data class SyncRequest(
	val op          : String,
	val device      : DeviceInfo?     = null,
	val home        : List<NetworkId> = emptyList(),
	val vault       : String?         = null,
	val fingerprint : String?         = null,
	val base        : String?         = null,
	val merged      : String?         = null
)

/*
	Réponse du PC (contenu d'un message scellé).
	action      : suite à donner (kActSame, kActStored, kActAdopt, kActMerge, kActRefused)
	vault       : coffre du PC, octets en base64 (kActAdopt, kActMerge)
	fingerprint : empreinte du coffre joint, ou du coffre retenu
	message     : explication, en clair
	pc          : présentation du PC (appairage)
	home        : réseaux familiaux connus du PC (appairage)
*/
@Serializable
data class SyncReply(
	val action      : String,
	val vault       : String?         = null,
	val fingerprint : String?         = null,
	val message     : String?         = null,
	val pc          : DeviceInfo?     = null,
	val home        : List<NetworkId> = emptyList()
)

/*
	Empreinte d'un coffre : SHA-256 des octets du fichier, en base64. Le
	fichier n'est jamais réécrit pendant un échange : deux appareils qui ont
	les mêmes octets ont la même empreinte.
	inBytes : octets du fichier .kdbx
*/
fun vaultFingerprint(inBytes: ByteArray): String = toBase64(MessageDigest.getInstance("SHA-256").digest(inBytes))

/*
	Fichier du coffre d'un appareil, vu par la synchronisation : lu et
	remplacé tel quel, octet pour octet, sans le déchiffrer.
*/
interface VaultFile
{
	/*
		Octets du coffre ; null si l'appareil n'a pas encore de coffre.
	*/
	fun read(): ByteArray?

	/*
		Remplace le coffre, seulement s'il n'a pas changé depuis sa lecture
		(empreinte attendue) ; faux sinon (une modification est arrivée
		entre-temps : rien n'est perdu, l'échange suivant en tiendra compte).
		inBytes    : nouveau contenu
		inExpected : empreinte attendue du coffre actuel ; null s'il ne doit pas y en avoir
	*/
	fun replace(
		inBytes    : ByteArray,
		inExpected : String?
	): Boolean
}

/*
	Décision du PC pour une demande de synchronisation d'un téléphone. Le PC
	ne garde aucune mémoire : le téléphone dit quel coffre du PC il connaît
	déjà (base, merged).
	- mêmes coffres : rien ;
	- le PC n'a pas changé depuis ce que le téléphone connaît : le PC prend
	  le coffre du téléphone ;
	- le téléphone n'a pas changé, le PC oui : le téléphone prend celui du PC ;
	- les deux ont changé : le téléphone fusionnera le coffre du PC (il faut
	  le coffre déverrouillé), puis le renverra.
	inFile    : coffre du PC
	inRequest : demande du téléphone
*/
fun answerSync(
	inFile    : VaultFile,
	inRequest : SyncRequest
): SyncReply
{
	val vPc      = inFile.read()																// coffre du PC
	val vPcFp    = vPc?.let(::vaultFingerprint)													// son empreinte
	val vPhone   = inRequest.vault?.let { fromBase64(it) ?: return SyncReply(kActRefused, message = "coffre du téléphone illisible") }	// coffre du téléphone
	if (vPhone != null && !isKeePassFile(vPhone)) return SyncReply(kActRefused, message = "le fichier du téléphone n'est pas un coffre KeePass")
	val vPhoneFp = vPhone?.let(::vaultFingerprint)												// son empreinte
	return when
	{
		vPhone == null && vPc == null                                -> SyncReply(kActSame)
		vPhone == null                                               -> SyncReply(kActAdopt, vault = toBase64(vPc!!), fingerprint = vPcFp)
		vPcFp == vPhoneFp                                            -> SyncReply(kActSame, fingerprint = vPcFp)
		vPc == null || vPcFp == inRequest.base || vPcFp == inRequest.merged ->
			if (inFile.replace(vPhone, vPcFp)) SyncReply(kActStored, fingerprint = vPhoneFp)
			else SyncReply(kActRefused, message = "le coffre du PC vient de changer : nouvel essai au prochain échange")
		vPhoneFp == inRequest.base                                   -> SyncReply(kActAdopt, vault = toBase64(vPc), fingerprint = vPcFp)
		else                                                         -> SyncReply(kActMerge, vault = toBase64(vPc), fingerprint = vPcFp)
	}
}

/*
	Ce que le téléphone sait d'un PC pour la synchronisation.
	base   : empreinte du dernier coffre commun ; null avant le premier échange
	merged : empreinte d'un coffre de ce PC déjà fusionné dans celui du téléphone ; null sinon
*/
@Serializable
data class PcSyncMemory(
	val base   : String? = null,
	val merged : String? = null
)

/*
	Issue d'un échange du téléphone avec un PC.
*/
sealed interface PhoneOutcome
{
	/*
		Coffres identiques après l'échange.
		memory : nouvelle mémoire du PC
		took   : vrai si le téléphone a pris le coffre du PC (à recharger s'il est ouvert)
	*/
	data class Synced(
		val memory : PcSyncMemory,
		val took   : Boolean
	) : PhoneOutcome

	/*
		Les deux ont changé : coffre du PC à fusionner au déverrouillage.
		bytes       : coffre du PC
		fingerprint : son empreinte
	*/
	class MergeLater(
		val bytes       : ByteArray,
		val fingerprint : String
	) : PhoneOutcome

	/*
		Échange sans suite (refus, ou coffre du téléphone modifié pendant
		l'échange).
		message : raison
	*/
	data class Failed(val message: String) : PhoneOutcome
}

/*
	Un échange de synchronisation, côté téléphone : envoie son coffre et ce
	qu'il connaît du PC, puis applique la réponse.
	inCall   : envoi d'une demande au PC (JSON) et réponse (JSON) ; lève une exception si le PC est injoignable
	inFile   : coffre du téléphone
	inMemory : ce que le téléphone sait de ce PC
*/
fun phoneExchange(
	inCall   : (String) -> String,
	inFile   : VaultFile,
	inMemory : PcSyncMemory
): PhoneOutcome
{
	val vMine    = inFile.read()																// coffre du téléphone
	val vMineFp  = vMine?.let(::vaultFingerprint)												// son empreinte
	val vRequest = SyncRequest(kOpSync, vault = vMine?.let(::toBase64), fingerprint = vMineFp, base = inMemory.base, merged = inMemory.merged)	// demande
	val vReply   = kSyncJson.decodeFromString(SyncReply.serializer(), inCall(kSyncJson.encodeToString(SyncRequest.serializer(), vRequest)))	// réponse
	return when (vReply.action)
	{
		kActSame   -> PhoneOutcome.Synced(PcSyncMemory(base = vMineFp), took = false)
		kActStored -> PhoneOutcome.Synced(PcSyncMemory(base = vMineFp), took = false)
		kActAdopt, kActMerge ->
		{
			val vPc   = vReply.vault?.let(::fromBase64)?.takeIf(::isKeePassFile) ?: return PhoneOutcome.Failed("coffre du PC illisible")	// coffre du PC
			val vPcFp = vaultFingerprint(vPc)																						// son empreinte
			when
			{
				vReply.action == kActMerge -> PhoneOutcome.MergeLater(vPc, vPcFp)
				inFile.replace(vPc, vMineFp) -> PhoneOutcome.Synced(PcSyncMemory(base = vPcFp), took = true)
				else                         -> PhoneOutcome.Failed("coffre modifié pendant l'échange : nouvel essai au prochain échange")
			}
		}
		else       -> PhoneOutcome.Failed(vReply.message ?: "refus du PC")
	}
}

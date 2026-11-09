package fr.maubin.keeplus.link

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

const val kLinkProtocol   = 1														// version du protocole entre appareils
const val kLinkPort       = 47821													// port TCP du serveur du PC
const val kDiscoveryPort  = 47820													// port UDP de la découverte
const val kMaxFrameBytes  = 64 * 1024 * 1024										// taille maximale d'une trame (64 Mo)
const val kMaxClockSkewMs = 5L * 60 * 1000											// écart d'horloge toléré entre deux appareils (5 min)

const val kKindHello  = "hello"														// présentation, en clair (diagnostic, avant appairage)
const val kKindSealed = "sealed"													// message chiffré d'une paire
const val kKindError  = "error"														// refus, en clair

internal val kLinkJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }	// messages entre appareils (versions voisines tolérées)

/*
	Présentation d'un appareil.
	id         : identifiant de l'appareil (tiré au hasard à la première ouverture)
	name       : nom affiché (« PC du bureau », « Pixel de Mathieu »)
	platform   : « windows » ou « android »
	appVersion : version de l'appli
	format     : format du fichier d'échange que l'appli sait lire
*/
@Serializable
data class DeviceInfo(
	val id         : String,
	val name       : String,
	val platform   : String,
	val appVersion : String,
	val format     : Int
)

/*
	Trame échangée sur la connexion : enveloppe en clair, contenu chiffré pour
	les messages d'une paire.
	protocol : version du protocole
	from     : identifiant de l'expéditeur
	kind     : nature (kKindHello, kKindSealed, kKindError)
	time     : date d'envoi (ms) ; protège contre le rejeu
	nonce    : numéro à usage unique (base64) ; protège contre le rejeu
	body     : contenu : présentation (hello), message scellé en base64 (sealed), raison (error)
*/
@Serializable
data class LinkFrame(
	val protocol : Int    = kLinkProtocol,
	val from     : String,
	val kind     : String,
	val time     : Long   = 0,
	val nonce    : String = "",
	val body     : String = ""
)

/*
	Données associées authentifiées d'un message scellé : expéditeur, date,
	numéro à usage unique et, pour une réponse, numéro de la demande (une
	réponse ne peut servir qu'à sa demande).
	inFrame   : trame
	inRequest : numéro de la demande (réponse) ; vide pour une demande
*/
internal fun sealedAad(
	inFrame   : LinkFrame,
	inRequest : String = ""
): ByteArray = "${inFrame.protocol}|${inFrame.from}|${inFrame.time}|${inFrame.nonce}|$inRequest".toByteArray(Charsets.UTF_8)

/*
	Écrit une trame : longueur sur 4 octets, puis le JSON.
	inOut   : flux de sortie
	inFrame : trame
*/
fun writeFrame(
	inOut   : OutputStream,
	inFrame : LinkFrame
)
{
	val vBytes = kLinkJson.encodeToString(LinkFrame.serializer(), inFrame).toByteArray(Charsets.UTF_8)	// trame encodée
	require(vBytes.size <= kMaxFrameBytes) { "trame trop grande" }
	DataOutputStream(inOut).apply { writeInt(vBytes.size); write(vBytes); flush() }
}

/*
	Lit une trame ; lève IOException si le flux est coupé, trop grand ou
	illisible.
	inIn : flux d'entrée
*/
fun readFrame(inIn: InputStream): LinkFrame
{
	val vData = DataInputStream(inIn)													// flux
	val vSize = try { vData.readInt() } catch (inError: EOFException) { throw IOException("connexion fermée") }	// longueur annoncée
	if (vSize !in 1..kMaxFrameBytes) throw IOException("trame de taille invalide : $vSize")
	val vBytes = ByteArray(vSize).also { vData.readFully(it) }							// contenu
	return runCatching { kLinkJson.decodeFromString(LinkFrame.serializer(), String(vBytes, Charsets.UTF_8)) }.getOrElse { throw IOException("trame illisible") }
}

/*
	Garde contre le rejeu : un message n'est accepté que daté de moins de
	cinq minutes d'écart et avec un numéro jamais vu dans cette fenêtre.
	inNow : horloge (ms)
*/
class ReplayGuard(private val inNow: () -> Long = System::currentTimeMillis)
{
	private val fSeen = LinkedHashMap<String, Long>()								// numéros vus et leur date

	/*
		Vrai si le message est neuf (et le retient) ; faux s'il est trop ancien,
		trop en avance ou déjà vu.
		inTime  : date du message (ms)
		inNonce : numéro à usage unique
	*/
	@Synchronized
	fun accept(
		inTime  : Long,
		inNonce : String
	): Boolean
	{
		val vNow = inNow()																// maintenant
		fSeen.entries.removeIf { vNow - it.value > 2 * kMaxClockSkewMs }
		if (kotlin.math.abs(vNow - inTime) > kMaxClockSkewMs || inNonce.isEmpty() || inNonce in fSeen) return false
		fSeen[inNonce] = inTime
		return true
	}
}

/*
	Refus d'un appareil distant, ou réponse impossible à vérifier.
	inMessage : raison
*/
class LinkException(inMessage: String) : IOException(inMessage)

package fr.maubin.keeplus.link

import java.net.InetSocketAddress
import java.net.Socket

private const val kConnectTimeoutMs = 4_000										// délai de connexion (réseau local)
private const val kReadTimeoutMs    = 30_000										// délai de réponse (gros fichier)

/*
	Client des échanges avec un PC (téléphone) : une demande par connexion.
	inSelf : présentation de cet appareil
	inNow  : horloge (ms)
*/
class LinkClient(
	private val inSelf : DeviceInfo,
	private val inNow  : () -> Long = System::currentTimeMillis
)
{
	/*
		Présentation : rend celle du PC (en clair, sans appairage) ; lève une
		exception si le PC est injoignable ou refuse.
		inHost : adresse du PC
		inPort : port du PC
	*/
	fun hello(
		inHost : String,
		inPort : Int = kLinkPort
	): DeviceInfo
	{
		val vReply = exchange(inHost, inPort, LinkFrame(from = inSelf.id, kind = kKindHello, body = kLinkJson.encodeToString(DeviceInfo.serializer(), inSelf)))	// réponse
		if (vReply.kind != kKindHello) throw LinkException(vReply.body.ifEmpty { "réponse inattendue" })
		return runCatching { kLinkJson.decodeFromString(DeviceInfo.serializer(), vReply.body) }.getOrElse { throw LinkException("présentation illisible") }
	}

	/*
		Demande scellée à un PC appairé : rend sa réponse en clair (JSON). Lève
		une exception si le PC est injoignable, refuse, ou si la réponse n'est
		pas scellée avec la clé de la paire pour cette demande.
		inHost    : adresse du PC
		inPort    : port du PC
		inPeerId  : identifiant attendu du PC
		inKey     : clé secrète de la paire
		inRequest : demande en clair (JSON)
	*/
	fun call(
		inHost    : String,
		inPort    : Int,
		inPeerId  : String,
		inKey     : ByteArray,
		inRequest : String
	): String
	{
		val vCipher  = LinkCipher(inKey)													// chiffrement de la paire
		val vFrame   = LinkFrame(from = inSelf.id, kind = kKindSealed, time = inNow(), nonce = toBase64(randomBytes(12)))	// demande, sans contenu
		val vRequest = vFrame.copy(body = toBase64(vCipher.seal(inRequest.toByteArray(Charsets.UTF_8), sealedAad(vFrame))))	// demande scellée
		val vReply   = exchange(inHost, inPort, vRequest)									// réponse
		if (vReply.kind == kKindError) throw LinkException(vReply.body)
		if (vReply.kind != kKindSealed || vReply.from != inPeerId) throw LinkException("réponse d’un autre appareil")
		val vPlain   = fromBase64(vReply.body)?.let { vCipher.open(it, sealedAad(vReply, vFrame.nonce)) } ?: throw LinkException("réponse illisible : clé différente")	// réponse en clair
		return String(vPlain, Charsets.UTF_8)
	}

	/*
		Une trame envoyée, une trame reçue.
		inHost  : adresse
		inPort  : port
		inFrame : trame envoyée
	*/
	private fun exchange(
		inHost  : String,
		inPort  : Int,
		inFrame : LinkFrame
	): LinkFrame =
		Socket().use { vSocket ->
			vSocket.connect(InetSocketAddress(inHost, inPort), kConnectTimeoutMs)
			vSocket.soTimeout = kReadTimeoutMs
			writeFrame(vSocket.getOutputStream(), inFrame)
			readFrame(vSocket.getInputStream())
		}
}

package fr.maubin.keeplus.link

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val kSocketTimeoutMs = 20_000										// délai maximal d'une connexion

/*
	Demande reçue d'un pair appairé, déjà ouverte et vérifiée.
	peerId  : identifiant du pair
	request : contenu en clair (JSON)
	address : adresse de l'appareil qui a envoyé
*/
data class LinkCall(
	val peerId  : String,
	val request : String,
	val address : InetAddress
)

/*
	Serveur des échanges entre appareils (PC) : une demande par connexion.
	Une présentation (hello) reçoit la présentation du serveur, en clair ; un
	message scellé n'est accepté que d'un pair appairé (clé connue), daté et
	jamais vu (rejeu), puis la réponse est scellée avec la même clé et liée à
	la demande. Tout le reste est refusé. Tourne sur des fils en tâche de fond.
	inInfo    : présentation de ce PC
	inKeyOf   : clé secrète d'un pair ; null s'il n'est pas appairé
	inHandler : réponse (JSON) à une demande d'un pair
	inOnHello : appelé à chaque présentation reçue (journal du diagnostic)
	inNow     : horloge (ms)
*/
class LinkServer(
	private val inInfo    : () -> DeviceInfo,
	private val inKeyOf   : (String) -> ByteArray?,
	private val inHandler : (LinkCall) -> String,
	private val inOnHello : (DeviceInfo, InetAddress) -> Unit = { _, _ -> },
	private val inNow     : () -> Long = System::currentTimeMillis
)
{
	private val fGuard  = ReplayGuard(inNow)										// garde contre le rejeu
	private var fSocket : ServerSocket? = null										// écoute ; null à l'arrêt
	private var fPool   : ExecutorService? = null									// fils des connexions

	// Port d'écoute ; 0 à l'arrêt.
	val port: Int get() = fSocket?.localPort ?: 0

	/*
		Démarre l'écoute ; sans effet si elle tourne déjà.
		inPort    : port ; 0 pour un port libre quelconque (tests)
		inAddress : adresse d'écoute ; null pour toutes les interfaces
	*/
	@Synchronized
	fun start(
		inPort    : Int          = kLinkPort,
		inAddress : InetAddress? = null
	)
	{
		if (fSocket != null) return
		val vSocket = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(inAddress, inPort)) }	// écoute
		val vPool   = Executors.newCachedThreadPool { inTask -> Thread(inTask, "KeePlus-lien").apply { isDaemon = true } }	// fils
		fSocket = vSocket
		fPool   = vPool
		vPool.execute {
			while (!vSocket.isClosed)
			{
				val vClient = runCatching { vSocket.accept() }.getOrNull() ?: break	// connexion entrante
				vPool.execute { serve(vClient) }
			}
		}
	}

	/*
		Arrête l'écoute (les connexions en cours se terminent).
	*/
	@Synchronized
	fun stop()
	{
		runCatching { fSocket?.close() }
		fPool?.shutdown()
		fSocket = null
		fPool   = null
	}

	/*
		Répond à une connexion : une trame lue, une trame écrite.
		inClient : connexion
	*/
	private fun serve(inClient: Socket)
	{
		inClient.use { vSocket ->
			vSocket.soTimeout = kSocketTimeoutMs
			val vReply = try
			{
				answer(readFrame(vSocket.getInputStream()), vSocket.inetAddress)
			}
			catch (inError: IOException)
			{
				error(inError.message ?: "trame illisible")
			}																			// réponse
			runCatching { writeFrame(vSocket.getOutputStream(), vReply) }
		}
	}

	/*
		Réponse à une trame.
		inFrame   : trame reçue
		inAddress : adresse de l'expéditeur
	*/
	private fun answer(
		inFrame   : LinkFrame,
		inAddress : InetAddress
	): LinkFrame
	{
		val vInfo = inInfo()																// présentation de ce PC
		if (inFrame.protocol != kLinkProtocol) return error("protocole ${inFrame.protocol} inconnu (ce PC parle le protocole $kLinkProtocol)")
		return when (inFrame.kind)
		{
			kKindHello  ->
			{
				runCatching { kLinkJson.decodeFromString(DeviceInfo.serializer(), inFrame.body) }.getOrNull()?.let { inOnHello(it, inAddress) }
				LinkFrame(from = vInfo.id, kind = kKindHello, body = kLinkJson.encodeToString(DeviceInfo.serializer(), vInfo))
			}
			kKindSealed ->
			{
				val vKey    = inKeyOf(inFrame.from) ?: return error("appareil non appairé")	// clé de la paire
				if (!fGuard.accept(inFrame.time, inFrame.nonce)) return error("message trop ancien ou déjà reçu (vérifiez l'heure des deux appareils)")
				val vCipher = LinkCipher(vKey)												// chiffrement de la paire
				val vPlain  = fromBase64(inFrame.body)?.let { vCipher.open(it, sealedAad(inFrame)) } ?: return error("message illisible : clé différente")	// demande en clair
				val vAnswer = runCatching { inHandler(LinkCall(inFrame.from, String(vPlain, Charsets.UTF_8), inAddress)) }.getOrElse { return error(it.message ?: "erreur du PC") }	// réponse en clair
				val vFrame  = LinkFrame(from = vInfo.id, kind = kKindSealed, time = inNow(), nonce = toBase64(randomBytes(12)))	// réponse, sans contenu
				vFrame.copy(body = toBase64(vCipher.seal(vAnswer.toByteArray(Charsets.UTF_8), sealedAad(vFrame, inFrame.nonce))))
			}
			else        -> error("demande « ${inFrame.kind} » inconnue")
		}
	}

	/*
		Trame de refus.
		inMessage : raison
	*/
	private fun error(inMessage: String): LinkFrame = LinkFrame(from = inInfo().id, kind = kKindError, body = inMessage)
}

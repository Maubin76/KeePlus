package fr.maubin.keeplus.link

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

const val kCallPort = 47822																	// port UDP de l'appel du PC au téléphone

private const val kCall = "KEEPLUS-SYNC"													// appel (suivi de la version du protocole, puis de l'identifiant du PC)

/*
	Appel du PC aux téléphones : « synchronisez-vous maintenant ». Le message
	ne porte que l'identifiant du PC ; il ne transmet rien du coffre. Un
	téléphone qui l'entend ne fait que lancer sa synchronisation habituelle
	(chiffrée, avec ses seuls PC appairés) : un faux appel ne peut rien
	obtenir. Le message est envoyé plusieurs fois, en diffusion et aux
	dernières adresses connues des téléphones (le Wi-Fi perd parfois un
	paquet).
	inPcId    : identifiant du PC
	inTargets : adresses (diffusion, adresses des téléphones)
	inPort    : port d'écoute des téléphones
	inRepeat  : nombre d'envois
*/
fun callPhones(
	inPcId    : String,
	inTargets : List<InetAddress>,
	inPort    : Int = kCallPort,
	inRepeat  : Int = 3
)
{
	val vMessage = "$kCall$kLinkProtocol:$inPcId".toByteArray(Charsets.UTF_8)				// appel
	DatagramSocket().use { vSocket ->
		vSocket.broadcast = true
		repeat(inRepeat) { vIndex ->
			if (vIndex > 0) Thread.sleep(250)
			inTargets.forEach { vTarget -> runCatching { vSocket.send(DatagramPacket(vMessage, vMessage.size, vTarget, inPort)) } }
		}
	}
}

/*
	Écoute des appels du PC (téléphone, tant que l'appli est à l'écran).
	fOnCall : appelé avec l'identifiant du PC qui appelle (sur le fil d'écoute)
	fSocket : écoute ; null à l'arrêt
*/
class SyncCallListener(private val fOnCall: (String) -> Unit)
{
	private var fSocket : DatagramSocket? = null

	/*
		Port d'écoute ; 0 à l'arrêt.
	*/
	val port: Int get() = fSocket?.localPort ?: 0

	/*
		Démarre l'écoute ; sans effet si elle tourne déjà.
		inPort    : port ; 0 pour un port libre quelconque (tests)
		inAddress : adresse d'écoute ; null pour toutes les interfaces (diffusions reçues)
	*/
	@Synchronized
	fun start(
		inPort    : Int          = kCallPort,
		inAddress : InetAddress? = null
	)
	{
		if (fSocket != null) return
		val vSocket = DatagramSocket(null).apply { reuseAddress = true; broadcast = true; bind(InetSocketAddress(inAddress, inPort)) }	// écoute
		fSocket = vSocket
		Thread({
			val vBuffer = ByteArray(256)															// appel reçu
			val vPrefix = "$kCall$kLinkProtocol:"												// début d'un appel valide
			while (!vSocket.isClosed)
			{
				val vPacket = DatagramPacket(vBuffer, vBuffer.size)									// paquet
				runCatching { vSocket.receive(vPacket) }.getOrNull() ?: break
				val vText   = String(vPacket.data, 0, vPacket.length, Charsets.UTF_8)				// contenu
				if (vText.startsWith(vPrefix)) runCatching { fOnCall(vText.removePrefix(vPrefix)) }
			}
		}, "KeePlus-appel").apply { isDaemon = true }.start()
	}

	/*
		Arrête l'écoute.
	*/
	@Synchronized
	fun stop()
	{
		runCatching { fSocket?.close() }
		fSocket = null
	}
}

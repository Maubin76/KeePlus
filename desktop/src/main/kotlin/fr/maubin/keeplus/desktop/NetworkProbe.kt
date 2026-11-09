package fr.maubin.keeplus.desktop

import fr.maubin.keeplus.link.NetworkId
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

private const val kCommandTimeoutS = 5L											// délai maximal d'une commande Windows
private val kVirtualAdapter = Regex("Hyper-V|vEthernet|WSL|VirtualBox|VMware|TAP-|WireGuard|Tailscale|ZeroTier", RegexOption.IGNORE_CASE)	// cartes virtuelles à ignorer

/*
	Nom du Wi-Fi connecté, lu dans la sortie de « netsh wlan show interfaces »
	(Windows en français ou en anglais, espace insécable avant les deux-points
	compris : la ligne « SSID », pas « BSSID ») ;
	null sans Wi-Fi connecté.
	inText : sortie de la commande
*/
fun parseSsid(inText: String): String? =
	inText.replace('\u00A0', ' ').replace('\u202F', ' ').lines()
		.firstNotNullOfOrNull { Regex("""^\s*SSID\s*:\s*(.+?)\s*$""").find(it)?.groupValues?.get(1) }?.takeIf { it.isNotEmpty() }

/*
	Passerelle par défaut, lue dans la sortie de « route print -4 0.0.0.0 » :
	ligne « 0.0.0.0  0.0.0.0  passerelle  interface  métrique », celle de plus
	petite métrique ; null sans passerelle.
	inText : sortie de la commande
*/
fun parseGateway(inText: String): String? =
	inText.lines()
		.mapNotNull { Regex("""^\s*0\.0\.0\.0\s+0\.0\.0\.0\s+(\d+\.\d+\.\d+\.\d+)\s+\S+\s+(\d+)""").find(it) }
		.minByOrNull { it.groupValues[2].toInt() }
		?.groupValues?.get(1)

/*
	Adresse matérielle d'une adresse IP, lue dans la sortie de « arp -a ip » ;
	null si elle n'y figure pas.
	inText : sortie de la commande
	inIp   : adresse IP cherchée
*/
fun parseMac(
	inText : String,
	inIp   : String
): String? =
	inText.lines().firstNotNullOfOrNull { inLine ->
		Regex("""^\s*${Regex.escape(inIp)}\s+([0-9a-fA-F]{2}(?:-[0-9a-fA-F]{2}){5})\b""").find(inLine)?.groupValues?.get(1)
	}

/*
	Réseaux auxquels ce PC est relié, tels que la synchronisation les
	reconnaît : nom du Wi-Fi s'il en a un, et box (adresse matérielle de la
	passerelle). Lus par des commandes de Windows ; vide ailleurs ou en cas
	d'échec.
*/
fun currentNetworks(): List<NetworkId>
{
	if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return emptyList()
	val vWifi    = run("cmd", "/c", "chcp 65001 >nul & netsh wlan show interfaces")?.let(::parseSsid)?.let(NetworkId::wifi)	// Wi-Fi
	val vGateway = run("route", "print", "-4", "0.0.0.0")?.let(::parseGateway)		// passerelle
	val vBox     = vGateway?.let { inIp -> run("arp", "-a", inIp)?.let { parseMac(it, inIp) } }?.let(NetworkId::box)	// box
	return listOfNotNull(vWifi, vBox)
}

/*
	Adresses IPv4 de ce PC sur le réseau local (hors boucle locale et cartes
	virtuelles : Hyper-V, WSL, VirtualBox, VMware, VPN), pour l'affichage et la
	saisie manuelle sur le téléphone.
*/
fun localAddresses(): List<String> =
	runCatching {
		NetworkInterface.getNetworkInterfaces().toList()
			.filter { it.isUp && !it.isLoopback && !it.isVirtual && !kVirtualAdapter.containsMatchIn(it.displayName.orEmpty()) }
			.flatMap { it.inetAddresses.toList() }
			.filterIsInstance<Inet4Address>()
			.filter { it.isSiteLocalAddress }
			.map { it.hostAddress }
	}.getOrDefault(emptyList())

/*
	Lance une commande et rend sa sortie ; null si elle échoue ou dépasse le
	délai.
	inCommand : commande et arguments
*/
private fun run(vararg inCommand: String): String? =
	runCatching {
		val vProcess = ProcessBuilder(*inCommand).redirectErrorStream(true).start()	// commande
		val vText    = vProcess.inputStream.bufferedReader(Charsets.UTF_8).readText()	// sortie
		if (!vProcess.waitFor(kCommandTimeoutS, TimeUnit.SECONDS)) { vProcess.destroyForcibly(); null } else vText
	}.getOrNull()

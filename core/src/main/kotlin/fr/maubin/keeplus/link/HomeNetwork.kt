package fr.maubin.keeplus.link

import kotlinx.serialization.Serializable

/*
	Réseau auquel l'appareil est relié, tel qu'on peut le reconnaître : nom du
	Wi-Fi (téléphone, PC portable), ou identifiant de la box (adresse matérielle
	de la passerelle) pour un PC branché par câble.
	kind  : « wifi » ou « box »
	value : nom du Wi-Fi, ou adresse matérielle de la box (minuscules, « aa:bb:… »)
*/
@Serializable
data class NetworkId(
	val kind  : String,
	val value : String
)
{
	// Libellé affiché (« Wi-Fi « Maison » », « Box aa:bb:cc:… »).
	val label: String get() = if (kind == kKindWifi) "Wi-Fi « $value »" else "Box $value"

	companion object
	{
		const val kKindWifi = "wifi"												// réseau reconnu par le nom du Wi-Fi
		const val kKindBox  = "box"													// réseau reconnu par la box (câble)

		/*
			Wi-Fi reconnu par son nom ; null si le nom est vide ou masqué
			(« <unknown ssid> » sans autorisation sur Android).
			inSsid : nom du Wi-Fi (guillemets d'Android acceptés)
		*/
		fun wifi(inSsid: String?): NetworkId? =
			inSsid?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }?.let { NetworkId(kKindWifi, it) }

		/*
			Box reconnue par l'adresse matérielle de la passerelle ; null si elle
			est illisible ou nulle.
			inMac : adresse matérielle (« AA-BB-CC-DD-EE-FF » ou « aa:bb:… »)
		*/
		fun box(inMac: String?): NetworkId?
		{
			val vMac = inMac?.trim()?.lowercase()?.replace('-', ':') ?: return null	// adresse normalisée
			return if (Regex("([0-9a-f]{2}:){5}[0-9a-f]{2}").matches(vMac) && vMac != "00:00:00:00:00:00" && vMac != "ff:ff:ff:ff:ff:ff") NetworkId(kKindBox, vMac) else null
		}
	}
}

/*
	Vrai si l'appareil est sur le réseau familial : un des réseaux courants
	figure parmi les réseaux reconnus. Sans réseau reconnu, ou sans réseau
	courant lisible, la synchronisation reste inactive.
	inCurrent : réseaux courants de l'appareil (un PC peut avoir Wi-Fi et câble)
	inHome    : réseaux familiaux enregistrés
*/
fun isHomeNetwork(
	inCurrent : Collection<NetworkId>,
	inHome    : Collection<NetworkId>
): Boolean = inCurrent.any { it in inHome }

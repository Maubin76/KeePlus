package fr.maubin.keeplus.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.link.NetworkId
import fr.maubin.keeplus.sync.PairedPhone
import fr.maubin.keeplus.sync.PcSync
import fr.maubin.keeplus.ui.components.FilterPill
import fr.maubin.keeplus.ui.components.GhostButton
import fr.maubin.keeplus.ui.components.IconAction
import fr.maubin.keeplus.ui.components.Monogram
import fr.maubin.keeplus.ui.components.OneLine
import fr.maubin.keeplus.ui.components.SilverButton
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.screens.ConfirmDialog
import fr.maubin.keeplus.ui.screens.ErrorLine
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val kWhen = DateTimeFormatter.ofPattern("d MMMM 'à' HH:mm", Locale.FRENCH)	// date de dernière synchronisation
private const val kCallWaitMs = 25_000L													// attente de la réponse des téléphones à un appel (ms)

/*
	Page Appareils du PC : état de l'écoute, téléphones appairés,
	synchronisation à la demande (appel des téléphones qui ont KeePlus
	ouvert), appairage d'un téléphone par QR code, réseaux de la maison.
	inSync    : synchronisation du PC
	inOnClose : fermer la page
*/
@Composable
fun DevicesPanel(
	inSync    : PcSync,
	inOnClose : () -> Unit
)
{
	val vType    = keeType()																// styles de texte
	val vState   by inSync.state.collectAsState()											// état de la synchronisation
	val vScope   = rememberCoroutineScope()													// actions (réseau, fichiers)
	var vRemove  by remember { mutableStateOf<PairedPhone?>(null) }							// téléphone à retirer
	var vNow     by remember { mutableStateOf(System.currentTimeMillis()) }					// horloge du compte à rebours
	var vCalling by remember { mutableStateOf(false) }											// appel des téléphones en cours
	var vSynced  by remember { mutableStateOf<String?>(null) }									// verdict : à jour
	var vMissed  by remember { mutableStateOf<String?>(null) }									// verdict : pas à jour, et pourquoi
	/*
		Appelle les téléphones, puis attend qu'un téléphone ait échangé son
		coffre avec le PC et donne le verdict : à jour, fusion en attente sur
		le téléphone, ou aucune réponse.
	*/
	fun syncNow()
	{
		vCalling = true
		vSynced  = null
		vMissed  = null
		vScope.launch {
			val vStart = System.currentTimeMillis()													// début de l'appel
			if (!withContext(Dispatchers.IO) { inSync.callPhones() })
			{
				vMissed  = "Le PC n'est pas sur le Wi-Fi de la maison : aucun téléphone ne peut le joindre."
				vCalling = false
				return@launch
			}
			withTimeoutOrNull(kCallWaitMs) { inSync.state.first { vState -> vState.last.values.any { it.at >= vStart && it.upToDate } } }
			delay(1_500)
			val vLast   = inSync.state.value.let { vState -> vState.phones.mapNotNull { vPhone -> vState.last[vPhone.id]?.takeIf { it.at >= vStart }?.let { vPhone.name to it } } }	// réponses à l'appel
			val vReady  = vLast.filter { it.second.upToDate }.map { it.first }							// téléphones à jour
			val vLate   = vLast.filter { !it.second.upToDate }.map { it.first }							// téléphones qui doivent fusionner
			vSynced  = vReady.takeIf { it.isNotEmpty() }?.let { "À jour avec ${it.joinToString(", ")}." }
			vMissed  = when
			{
				vLate.isNotEmpty() -> "${vLate.joinToString(", ")} : modifications des deux côtés, à fusionner. Déverrouillez KeePlus sur le téléphone."
				vReady.isEmpty()   -> "Aucun téléphone n'a répondu : ouvrez KeePlus sur le téléphone, connecté au Wi-Fi de la maison."
				else               -> null
			}
			vCalling = false
		}
	}
	LaunchedEffect(vState.offer) {
		while (vState.offer != null)
		{
			vNow = System.currentTimeMillis()
			if (vState.offer!!.expires <= vNow) { vScope.launch(Dispatchers.IO) { inSync.refresh() }; break }
			delay(1000)
		}
	}
	Column(
		Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 24.dp, vertical = 20.dp)
			.widthIn(max = 640.dp),
		verticalArrangement = Arrangement.spacedBy(18.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			IconAction(LineIcons.Back, "Retour", inOnClose, inTint = KeeColors.text)
			BasicText("Appareils", Modifier.weight(1f).padding(start = 4.dp), style = vType.headline)
		}
		statusText(vState.running, vState.onHome, vState.phones.isNotEmpty(), vState.networks)?.let { BasicText(it, style = vType.bodySmall) }
		ErrorLine(vState.error)

		Section("Téléphones") {
			if (vState.phones.isEmpty()) BasicText("Aucun téléphone appairé.", style = vType.bodySmall.copy(color = KeeColors.textFaint))
			for (vPhone in vState.phones)
			{
				Row(verticalAlignment = Alignment.CenterVertically) {
					Monogram(vPhone.name)
					Spacer(Modifier.width(14.dp))
					Column(Modifier.weight(1f)) {
						OneLine(vPhone.name, vType.title)
						OneLine(vPhone.lastSync?.let { "Synchronisé le " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(kWhen) } ?: "Jamais synchronisé", vType.bodySmall)
					}
					IconAction(LineIcons.Trash, "Retirer ${vPhone.name}", { vRemove = vPhone })
				}
			}
			if (vState.phones.isNotEmpty())
			{
				vSynced?.let { BasicText(it, style = vType.body.copy(color = KeeColors.success)) }
				ErrorLine(vMissed)
				GhostButton(if (vCalling) "Synchronisation…" else "Synchroniser maintenant", ::syncNow, inEnabled = !vCalling)
			}
			val vOffer = vState.offer																	// offre affichée
			if (vOffer != null)
			{
				Row(
					Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(KeeColors.surface).padding(18.dp),
					horizontalArrangement = Arrangement.spacedBy(20.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					QrCode(vOffer.encode(), 200.dp, "QR code d'appairage")
					Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
						BasicText("Sur le téléphone : Appareils › Appairer un PC.", style = vType.body)
						val vLeft = ((vOffer.expires - vNow) / 1000).coerceAtLeast(0)							// secondes restantes
						BasicText("Valable encore ${vLeft / 60} min ${(vLeft % 60).toString().padStart(2, '0')} s", style = vType.bodySmall.copy(color = KeeColors.textFaint))
						GhostButton("Annuler", { vScope.launch(Dispatchers.IO) { inSync.cancelPairing() } })
					}
				}
			}
			else
			{
				vState.paired?.let { BasicText("« $it » est appairé.", style = vType.body.copy(color = KeeColors.success)) }
				SilverButton("Appairer un téléphone", { vScope.launch(Dispatchers.IO) { inSync.startPairing() } })
			}
		}

		Section("Réseau de la maison") {
			if (vState.home.isEmpty() && vState.networks.isEmpty()) BasicText("Aucun réseau.", style = vType.bodySmall.copy(color = KeeColors.textFaint))
			FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				for (vNetwork in vState.home)
				{
					FilterPill("${vNetwork.label}  ✕", true) { vScope.launch(Dispatchers.IO) { inSync.setHome(vNetwork, false) } }
				}
				for (vNetwork in vState.networks.filter { it !in vState.home })
				{
					FilterPill("+ ${vNetwork.label}", false) { vScope.launch(Dispatchers.IO) { inSync.setHome(vNetwork, true) } }
				}
			}
		}
	}
	vRemove?.let { vPhone ->
		ConfirmDialog(
			inTitle     = "Retirer « ${vPhone.name} » ?",
			inText      = "Ce téléphone ne pourra plus se synchroniser avec ce PC, jusqu'à un nouvel appairage. Son coffre n'est pas effacé.",
			inConfirm   = "Retirer",
			inOnDismiss = { vRemove = null },
			inOnConfirm = { vRemove = null; vScope.launch(Dispatchers.IO) { inSync.removePhone(vPhone.id) } }
		)
	}
}

/*
	État de l'écoute, en clair ; null tant qu'aucun téléphone n'est appairé (la liste vide le dit).
	inRunning  : serveur à l'écoute
	inOnHome   : PC sur le réseau de la maison
	inPaired   : au moins un téléphone appairé
	inNetworks : réseaux du PC
*/
private fun statusText(
	inRunning  : Boolean,
	inOnHome   : Boolean,
	inPaired   : Boolean,
	inNetworks : List<NetworkId>
): String? =
	when
	{
		!inPaired                 -> null
		inRunning && inOnHome     -> "À l'écoute sur le réseau de la maison."
		inNetworks.isEmpty()      -> "Réseau du PC illisible."
		else                      -> "Hors du réseau de la maison."
	}

/*
	Section de la page : titre discret puis contenu.
	inTitle   : titre
	inContent : contenu
*/
@Composable
private fun Section(
	inTitle   : String,
	inContent : @Composable ColumnScope.() -> Unit
)
{
	Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
		BasicText(inTitle, style = keeType().bodySmall)
		inContent()
	}
}

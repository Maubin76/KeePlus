package fr.maubin.keeplus

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.link.PairingOffer
import fr.maubin.keeplus.sync.PairedPc
import fr.maubin.keeplus.ui.components.keeTexture
import fr.maubin.keeplus.ui.components.FilterPill
import fr.maubin.keeplus.ui.components.keeTexture
import fr.maubin.keeplus.ui.components.IconAction
import fr.maubin.keeplus.ui.components.keeTexture
import fr.maubin.keeplus.ui.components.Monogram
import fr.maubin.keeplus.ui.components.keeTexture
import fr.maubin.keeplus.ui.components.OneLine
import fr.maubin.keeplus.ui.components.keeTexture
import fr.maubin.keeplus.ui.components.SilverButton
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.screens.ConfirmDialog
import fr.maubin.keeplus.ui.screens.ErrorLine
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val kWhen = DateTimeFormatter.ofPattern("d MMMM 'à' HH:mm", Locale.FRENCH)	// date de dernière synchronisation

/*
	Page Appareils du téléphone : état de la synchronisation, PC appairés,
	appairage d'un PC (autorisation de localisation pour reconnaître le
	Wi-Fi, puis lecture du QR code), réseaux de la maison, interrupteur.
	inSync    : synchronisation du téléphone
	inOnClose : fermer la page
*/
@Composable
fun DevicesScreen(
	inSync    : AndroidSync,
	inOnClose : () -> Unit
)
{
	val vType    = keeType()																	// styles de texte
	val vContext = LocalContext.current														// contexte
	val vScope   = rememberCoroutineScope()													// appairage, synchronisation
	val vStatus  by inSync.status.collectAsState()												// état
	val vPeers   by inSync.peers.collectAsState()												// appareils
	var vScan    by remember { mutableStateOf(false) }											// lecteur de QR code ouvert
	var vMessage by remember { mutableStateOf<String?>(null) }									// résultat de l'appairage
	var vError   by remember { mutableStateOf<String?>(null) }									// échec de l'appairage
	var vBusy    by remember { mutableStateOf(false) }											// appairage en cours
	var vRemove  by remember { mutableStateOf<PairedPc?>(null) }								// PC à retirer
	val vAskWifi = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vGranted ->
		if (vGranted) vScan = true else vError = "Sans la localisation, KeePlus ne peut pas reconnaître le Wi-Fi de la maison."
	}																						// autorisation de lire le nom du Wi-Fi
	/*
		Ouvre le lecteur de QR code, après l'autorisation de localisation.
	*/
	fun startPairing()
	{
		vError   = null
		vMessage = null
		if (vContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) vScan = true
		else vAskWifi.launch(Manifest.permission.ACCESS_FINE_LOCATION)
	}
	Column(
		Modifier
			.fillMaxSize()
			.background(Brush.verticalGradient(0f to KeeColors.mistMid.copy(alpha = 0.32f), 0.16f to KeeColors.ground, 1f to KeeColors.ground))
			.keeTexture()
			.safeDrawingPadding()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 20.dp, vertical = 12.dp),
		verticalArrangement = Arrangement.spacedBy(18.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			IconAction(LineIcons.Back, "Retour", inOnClose, inTint = KeeColors.text)
			BasicText("Appareils", Modifier.weight(1f).padding(start = 4.dp), style = vType.headline)
		}
		Row(verticalAlignment = Alignment.CenterVertically) {
			BasicText(statusText(vStatus).orEmpty(), Modifier.weight(1f), style = vType.bodySmall.copy(color = if (vStatus is PhoneStatus.Failed) KeeColors.danger else KeeColors.textMuted))
			if (vPeers.enabled && vPeers.pcs.isNotEmpty()) IconAction(LineIcons.Sync, "Synchroniser maintenant", { inSync.syncSoon() }, inTint = KeeColors.text)
		}

		Section("PC") {
			if (vPeers.pcs.isEmpty()) BasicText("Aucun PC appairé.", style = vType.bodySmall.copy(color = KeeColors.textFaint))
			for (vPc in vPeers.pcs)
			{
				Row(verticalAlignment = Alignment.CenterVertically) {
					Monogram(vPc.name)
					Spacer(Modifier.width(14.dp))
					Column(Modifier.weight(1f)) {
						OneLine(vPc.name, vType.title)
						OneLine(vPc.lastSync?.let { "Synchronisé le " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(kWhen) } ?: "Jamais synchronisé", vType.bodySmall)
					}
					IconAction(LineIcons.Trash, "Retirer ${vPc.name}", { vRemove = vPc })
				}
			}
			vMessage?.let { BasicText(it, style = vType.body.copy(color = KeeColors.success)) }
			ErrorLine(vError)
			SilverButton(if (vBusy) "Appairage…" else "Appairer un PC", ::startPairing, inEnabled = !vBusy)
		}

		Section("Réseau de la maison") {
			if (vPeers.home.isEmpty()) BasicText("Aucun réseau.", style = vType.bodySmall.copy(color = KeeColors.textFaint))
			FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				for (vNetwork in vPeers.home)
				{
					FilterPill("${vNetwork.label}  ✕", true) { inSync.removeHome(vNetwork) }
				}
			}
		}

		Section("Synchronisation") {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				FilterPill("Activée", vPeers.enabled) { inSync.setEnabled(true) }
				FilterPill("Désactivée", !vPeers.enabled) { inSync.setEnabled(false) }
			}
		}
	}
	if (vScan)
	{
		QrScannerDialog(
			inOnResult = { vText ->
				vScan = false
				val vOffer = PairingOffer.decode(vText)												// offre lue
				if (vOffer == null) vError = "Ce QR code n'est pas un appairage KeePlus."
				else
				{
					vBusy = true
					vScope.launch {
						val vFailure = inSync.pair(vOffer)												// raison d'un échec
						vBusy = false
						if (vFailure == null) vMessage = "« ${vOffer.pcName} » est appairé." else vError = vFailure
					}
				}
			},
			inOnClose  = { vScan = false }
		)
	}
	vRemove?.let { vPc ->
		ConfirmDialog(
			inTitle     = "Retirer « ${vPc.name} » ?",
			inText      = "Ce PC ne sera plus synchronisé, jusqu'à un nouvel appairage. Son coffre n'est pas effacé.",
			inConfirm   = "Retirer",
			inOnDismiss = { vRemove = null },
			inOnConfirm = { vRemove = null; inSync.remove(vPc.id) }
		)
	}
}

/*
	État de la synchronisation, en clair ; null sans PC appairé (la liste vide le dit).
	inStatus : état
*/
private fun statusText(inStatus: PhoneStatus): String? =
	when (inStatus)
	{
		PhoneStatus.Disabled  -> "Synchronisation désactivée."
		PhoneStatus.Unpaired  -> null
		is PhoneStatus.Away   -> inStatus.reason
		PhoneStatus.Working   -> "Synchronisation en cours…"
		is PhoneStatus.Idle   -> "À jour avec ${inStatus.pcs.joinToString(", ")}."
		PhoneStatus.Pending   -> "Fusion au prochain déverrouillage."
		is PhoneStatus.Failed -> inStatus.message
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

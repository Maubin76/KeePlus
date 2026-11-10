package fr.maubin.keeplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.maubin.keeplus.ui.components.keeTexture
import fr.maubin.keeplus.ui.components.GhostButton
import fr.maubin.keeplus.ui.components.IconAction
import fr.maubin.keeplus.ui.components.Monogram
import fr.maubin.keeplus.ui.components.OneLine
import fr.maubin.keeplus.ui.components.secretAnnotated
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType
import fr.maubin.keeplus.vault.Vault
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val kCard       = RoundedCornerShape(20.dp)									// forme des cartes
private val kDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH)	// date de modification
private const val kMask = "••••••••••••"												// secret masqué (longueur fixe : ne révèle rien)

/*
	Fiche d'une entrée, en lecture : titre et adresse, carte des
	identifiants (identifiant, mot de passe, site), champs libres, notes,
	date de modification et anciennes versions. Les secrets sont masqués ;
	l'œil les dévoile en JetBrains Mono, trois nuances.
	inVault   : coffre ouvert
	inId      : entrée affichée
	inCopy    : copies
	inOnClose : fermer la fiche (flèche Retour, téléphone) ; null pour aucune
	inActions : actions sur l'entrée (modifier, corbeille, restaurer)
*/
@Composable
fun EntryDetail(
	inVault   : Vault,
	inId      : UUID,
	inCopy    : CopyActions,
	inOnClose : (() -> Unit)?,
	inActions : EntryActions
)
{
	val vDraft   = inVault.draft(inId) ?: return												// contenu de l'entrée
	val vType    = keeType()																// styles de texte
	val vHistory = inVault.history(inId).size												// anciennes versions
	val vGroup   = vDraft.groupId?.takeIf { it != inVault.root.uuid }?.let { inVault.groupName(it) }	// groupe
	Column(
		Modifier
			.fillMaxSize()
			.background(mistTop())
			.keeTexture()
			.safeDrawingPadding()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 20.dp, vertical = 12.dp)
	) {
		val vInTrash = vDraft.groupId?.let { inVault.isInTrash(it) } == true				// entrée à la corbeille
		var vConfirm by remember(inId) { mutableStateOf(false) }							// confirmation de suppression
		Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
			if (inOnClose != null) IconAction(LineIcons.Back, "Retour à la liste", inOnClose, inTint = KeeColors.text)
			Spacer(Modifier.weight(1f))
			if (vInTrash)
			{
				IconAction(LineIcons.Restore, "Restaurer", { inActions.restore(inId) }, inTint = KeeColors.text)
				IconAction(LineIcons.Trash, "Supprimer définitivement", { vConfirm = true }, inTint = KeeColors.danger)
			}
			else
			{
				IconAction(LineIcons.Edit, "Modifier", { inActions.edit(inId) }, inTint = KeeColors.text)
				IconAction(LineIcons.Trash, "Mettre à la corbeille", { inActions.trash(inId) })
			}
		}
		if (vConfirm)
		{
			ConfirmDialog(
				inTitle     = "Supprimer définitivement ?",
				inText      = "« ${vDraft.title.ifBlank { "Sans titre" }} » sera effacée du coffre, sur cet appareil et sur les autres à la prochaine synchronisation.",
				inConfirm   = "Supprimer",
				inOnDismiss = { vConfirm = false },
				inOnConfirm = { vConfirm = false; inActions.trash(inId) }
			)
		}
		Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Monogram(vDraft.title, inSize = 56.dp)
				Spacer(Modifier.width(16.dp))
				Column(Modifier.weight(1f)) {
					BasicText(vDraft.title.ifBlank { "Sans titre" }, style = vType.headline)
					val vWhere = listOfNotNull(vGroup, host(vDraft.url)).joinToString(" · ")	// groupe et site
					if (vWhere.isNotEmpty()) OneLine(vWhere, vType.bodySmall)
				}
			}
			Card {
				if (vDraft.userName.isNotEmpty()) PlainRow("Identifiant", vDraft.userName, inCopy)
				SecretRow("Mot de passe", vDraft.password, inCopy)
				if (vDraft.url.isNotEmpty()) UrlRow(vDraft.url, inCopy)
			}
			if (vDraft.fields.isNotEmpty())
			{
				Card {
					for (vField in vDraft.fields)
					{
						if (vField.secret) SecretRow(vField.name, vField.value, inCopy) else PlainRow(vField.name, vField.value, inCopy)
					}
				}
			}
			if (vDraft.notes.isNotBlank())
			{
				Card {
					Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
						BasicText("Notes", style = vType.bodySmall)
						BasicText(vDraft.notes, style = vType.body)
					}
				}
			}
			val vModified = vDraft.modified?.atZone(ZoneId.systemDefault())?.format(kDateFormat)	// date de modification
			val vFooter   = listOfNotNull(
				vModified?.let { "Modifiée le $it" },
				when (vHistory)
				{
					0    -> null
					1    -> "1 ancienne version"
					else -> "$vHistory anciennes versions"
				}
			).joinToString(" · ")																// pied de fiche
			if (vFooter.isNotEmpty()) BasicText(vFooter, Modifier.padding(horizontal = 4.dp), style = vType.bodySmall.copy(color = KeeColors.textFaint))
			Spacer(Modifier.height(80.dp))
		}
	}
}

/*
	Actions possibles depuis la fiche.
	edit    : modifier l'entrée
	trash   : mettre à la corbeille (ou supprimer pour de bon depuis la corbeille)
	restore : sortir de la corbeille
*/
class EntryActions(
	val edit    : (UUID) -> Unit,
	val trash   : (UUID) -> Unit,
	val restore : (UUID) -> Unit
)

/*
	Boîte de confirmation d'une action irréversible.
	inTitle     : question
	inText      : conséquence
	inConfirm   : libellé du bouton de confirmation
	inOnDismiss : renoncer
	inOnConfirm : confirmer
*/
@Composable
fun ConfirmDialog(
	inTitle     : String,
	inText      : String,
	inConfirm   : String,
	inOnDismiss : () -> Unit,
	inOnConfirm : () -> Unit
)
{
	Dialog(onDismissRequest = inOnDismiss) {
		Column(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(KeeColors.surfaceHigh).padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			BasicText(inTitle, style = keeType().headline)
			BasicText(inText, style = keeType().bodySmall)
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				GhostButton("Annuler", inOnDismiss, Modifier.weight(1f))
				GhostButton(inConfirm, inOnConfirm, Modifier.weight(1f))
			}
		}
	}
}

/*
	Carte de la fiche : fond de surface, rayon de 20, lignes empilées.
	inContent : lignes
*/
@Composable
private fun Card(inContent: @Composable () -> Unit)
{
	Column(Modifier.fillMaxWidth().clip(kCard).background(KeeColors.surface).padding(vertical = 4.dp)) {
		inContent()
	}
}

/*
	Ligne de la fiche : libellé au-dessus de la valeur, actions à droite.
	inLabel   : libellé
	inValue   : valeur
	inActions : boutons à droite
*/
@Composable
private fun FieldRow(
	inLabel   : String,
	inValue   : @Composable () -> Unit,
	inActions : @Composable () -> Unit
)
{
	Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			BasicText(inLabel, style = keeType().bodySmall)
			inValue()
		}
		inActions()
	}
}

/*
	Ligne d'une valeur en clair, avec copie.
	inLabel : libellé
	inValue : valeur
	inCopy  : copies
*/
@Composable
private fun PlainRow(
	inLabel : String,
	inValue : String,
	inCopy  : CopyActions
)
{
	FieldRow(inLabel, { BasicText(inValue, style = keeType().body) }) {
		IconAction(LineIcons.Copy, "Copier : $inLabel", { inCopy.copy(inLabel, inValue, false) })
	}
}

/*
	Ligne d'un secret : masqué, œil pour le dévoiler (JetBrains Mono, trois
	nuances), copie effacée après le délai.
	inLabel : libellé
	inValue : secret
	inCopy  : copies
*/
@Composable
private fun SecretRow(
	inLabel : String,
	inValue : String,
	inCopy  : CopyActions
)
{
	var vShown by remember(inValue) { mutableStateOf(false) }								// secret dévoilé
	val vType  = keeType()																	// styles de texte
	FieldRow(
		inLabel,
		{
			when
			{
				inValue.isEmpty() -> BasicText("Aucun", style = vType.body.copy(color = KeeColors.textFaint))
				vShown            -> BasicText(secretAnnotated(inValue), style = vType.secret)
				else              -> BasicText(kMask, style = vType.body.copy(color = KeeColors.textMuted))
			}
		}
	) {
		if (inValue.isNotEmpty())
		{
			IconAction(if (vShown) LineIcons.EyeOff else LineIcons.Eye, if (vShown) "Masquer : $inLabel" else "Afficher : $inLabel", { vShown = !vShown })
			IconAction(LineIcons.Copy, "Copier : $inLabel", { inCopy.copy(inLabel, inValue, true) })
		}
	}
}

/*
	Ligne de l'adresse du site : ouverture dans le navigateur, copie.
	inUrl  : adresse
	inCopy : copies
*/
@Composable
private fun UrlRow(
	inUrl  : String,
	inCopy : CopyActions
)
{
	val vUri = LocalUriHandler.current														// ouverture des liens
	FieldRow("Site", { OneLine(inUrl, keeType().body) }) {
		IconAction(LineIcons.External, "Ouvrir le site", {
			runCatching { vUri.openUri(if ("://" in inUrl) inUrl else "https://$inUrl") }
		})
		IconAction(LineIcons.Copy, "Copier : Site", { inCopy.copy("Adresse", inUrl, false) })
	}
}

/*
	Nom d'hôte d'une adresse, sans « www. » ; null si l'adresse est vide.
	inUrl : adresse
*/
private fun host(inUrl: String): String?
{
	if (inUrl.isBlank()) return null
	return inUrl.substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.").ifBlank { null }
}

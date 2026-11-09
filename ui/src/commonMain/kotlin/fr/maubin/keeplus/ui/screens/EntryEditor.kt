package fr.maubin.keeplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.maubin.keeplus.generator.estimateBits
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.components.FilterPill
import fr.maubin.keeplus.ui.components.GeneratorPanel
import fr.maubin.keeplus.ui.components.GhostButton
import fr.maubin.keeplus.ui.components.IconAction
import fr.maubin.keeplus.ui.components.KeeTextField
import fr.maubin.keeplus.ui.components.SecretField
import fr.maubin.keeplus.ui.components.SilverButton
import fr.maubin.keeplus.ui.components.StrengthMeter
import fr.maubin.keeplus.ui.components.TextLink
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType
import fr.maubin.keeplus.vault.CustomField
import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.Vault
import kotlinx.coroutines.launch
import java.util.UUID

/*
	Champ libre en cours d'édition.
	name   : nom du champ
	value  : valeur
	secret : valeur secrète
*/
private class FieldEdit(
	val name   : TextFieldState,
	val value  : TextFieldState,
	val secret : MutableState<Boolean>
)

/*
	Création ou modification d'une entrée : titre, identifiant, mot de passe
	(jauge de force, générateur intégré), site, groupe (création d'un groupe
	possible), notes, champs libres. Rien n'est enregistré avant
	« Enregistrer ».
	inController : état de l'appli
	inVault      : coffre ouvert
	inDraft      : contenu de départ (id déjà attribué pour une nouvelle entrée)
*/
@Composable
fun EntryEditor(
	inController : AppController,
	inVault      : Vault,
	inDraft      : EntryDraft
)
{
	val vType      = keeType()																// styles de texte
	val vScope     = rememberCoroutineScope()												// enregistrement
	val vIsNew     = inVault.draft(inDraft.id ?: UUID(0, 0)) == null						// nouvelle entrée
	val vTitle     = rememberTextFieldState(inDraft.title)									// titre
	val vUser      = rememberTextFieldState(inDraft.userName)								// identifiant
	val vPassword  = rememberTextFieldState(inDraft.password)								// mot de passe
	val vUrl       = rememberTextFieldState(inDraft.url)									// site
	val vNotes     = rememberTextFieldState(inDraft.notes)									// notes
	var vGroup     by remember { mutableStateOf(inDraft.groupId?.takeIf { it != inVault.root.uuid }) }	// groupe ; null : racine
	var vGenerator by remember { mutableStateOf(inDraft.password.isEmpty()) }				// générateur ouvert d'emblée pour une entrée sans mot de passe
	var vNewGroup  by remember { mutableStateOf(false) }										// création d'un groupe
	val vFields    = remember {
		mutableStateListOf<FieldEdit>().apply {
			inDraft.fields.forEach { add(FieldEdit(TextFieldState(it.name), TextFieldState(it.value), mutableStateOf(it.secret))) }
		}
	}																						// champs libres
	/*
		Enregistre l'entrée telle qu'éditée.
	*/
	fun save()
	{
		val vDraft = inDraft.copy(
			groupId  = vGroup ?: inVault.root.uuid,
			title    = vTitle.text.toString().trim(),
			userName = vUser.text.toString().trim(),
			password = vPassword.text.toString(),
			url      = vUrl.text.toString().trim(),
			notes    = vNotes.text.toString(),
			fields   = vFields
				.filter { it.name.text.isNotBlank() }
				.map { CustomField(it.name.text.toString().trim(), it.value.text.toString(), it.secret.value) }
		)																					// contenu à enregistrer
		vScope.launch { inController.saveEntry(vDraft) }
	}
	Column(
		Modifier
			.fillMaxSize()
			.background(mistTop())
			.safeDrawingPadding()
			.imePadding()
	) {
		Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
			IconAction(LineIcons.Close, "Annuler", inController::cancelEdit, inTint = KeeColors.text)
			BasicText(if (vIsNew) "Nouvelle entrée" else "Modifier l'entrée", Modifier.weight(1f).padding(start = 4.dp), style = vType.title)
		}
		Column(
			Modifier
				.weight(1f)
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 20.dp, vertical = 12.dp)
				.widthIn(max = 640.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Section("Identifiants") {
				KeeTextField(vTitle, "Titre (obligatoire)")
				KeeTextField(vUser, "Identifiant ou adresse e-mail")
				SecretField(vPassword, "Mot de passe", inImeAction = ImeAction.Next)
				StrengthMeter(estimateBits(vPassword.text))
				if (vGenerator)
				{
					GeneratorPanel({ vValue -> vPassword.setTextAndPlaceCursorAtEnd(vValue); vGenerator = false })
				}
				else
				{
					TextLink("Générer un mot de passe", { vGenerator = true })
				}
				KeeTextField(vUrl, "Site (https://…)")
			}
			Section("Groupe") {
				FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
					FilterPill("Aucun", vGroup == null) { vGroup = null }
					for (vItem in inVault.groups())
					{
						FilterPill(vItem.name, vGroup == vItem.id) { vGroup = vItem.id }
					}
					FilterPill("+ Nouveau groupe", false) { vNewGroup = true }
				}
			}
			Section("Notes") {
				KeeTextField(vNotes, "Notes", inMultiLine = true)
			}
			Section("Champs libres") {
				for (vField in vFields)
				{
					Column(
						Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(KeeColors.surface).padding(12.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						KeeTextField(vField.name, "Nom du champ")
						if (vField.secret.value) SecretField(vField.value, "Valeur", inImeAction = ImeAction.Next)
						else KeeTextField(vField.value, "Valeur")
						Row(verticalAlignment = Alignment.CenterVertically) {
							FilterPill("Secret", vField.secret.value) { vField.secret.value = !vField.secret.value }
							Spacer(Modifier.weight(1f))
							IconAction(LineIcons.Trash, "Supprimer ce champ", { vFields.remove(vField) })
						}
					}
				}
				TextLink("Ajouter un champ", {
					vFields.add(FieldEdit(TextFieldState(), TextFieldState(), mutableStateOf(false)))
				})
			}
			Spacer(Modifier.height(8.dp))
		}
		Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).widthIn(max = 640.dp)) {
			SilverButton("Enregistrer", ::save, inEnabled = vTitle.text.isNotBlank())
		}
	}
	if (vNewGroup)
	{
		NewGroupDialog(
			inOnDismiss = { vNewGroup = false },
			inOnCreate  = { vName ->
				vNewGroup = false
				vScope.launch { inController.addGroup(vName)?.let { vGroup = it } }
			}
		)
	}
}

/*
	Section de l'éditeur : titre discret puis contenu.
	inTitle   : titre de la section
	inContent : contenu
*/
@Composable
private fun Section(
	inTitle   : String,
	inContent : @Composable ColumnScope.() -> Unit
)
{
	Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
		BasicText(inTitle, style = keeType().bodySmall)
		inContent()
	}
}

/*
	Boîte de dialogue de création d'un groupe.
	inOnDismiss : fermer sans créer
	inOnCreate  : créer le groupe avec ce nom
*/
@Composable
private fun NewGroupDialog(
	inOnDismiss : () -> Unit,
	inOnCreate  : (String) -> Unit
)
{
	val vName = rememberTextFieldState()														// nom du groupe
	Dialog(onDismissRequest = inOnDismiss) {
		Column(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(KeeColors.surfaceHigh).padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			BasicText("Nouveau groupe", style = keeType().headline)
			KeeTextField(vName, "Nom du groupe", inImeAction = ImeAction.Done)
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				GhostButton("Annuler", inOnDismiss, Modifier.weight(1f))
				SilverButton("Créer", { inOnCreate(vName.text.toString()) }, Modifier.weight(1f), inEnabled = vName.text.isNotBlank())
			}
		}
	}
}

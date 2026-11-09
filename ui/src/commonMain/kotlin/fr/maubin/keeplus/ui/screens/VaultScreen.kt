package fr.maubin.keeplus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.ListFilter
import fr.maubin.keeplus.ui.components.FilterPill
import fr.maubin.keeplus.ui.components.IconAction
import fr.maubin.keeplus.ui.components.Monogram
import fr.maubin.keeplus.ui.components.Notice
import fr.maubin.keeplus.ui.components.OneLine
import fr.maubin.keeplus.ui.components.SearchField
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.platform.SecretClipboard
import fr.maubin.keeplus.ui.platform.kClipboardSeconds
import fr.maubin.keeplus.ui.platform.rememberSecretClipboard
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.KeeLogo
import fr.maubin.keeplus.ui.theme.keeType
import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.VaultItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private val kWideMin    = 840.dp															// largeur à partir de laquelle on passe en 3 colonnes
private val kGroupWidth = 260.dp															// colonne des groupes (PC)
private val kListWidth  = 380.dp															// colonne de la liste (PC)

/*
	Actions communes à la liste et à la fiche : copie avec bandeau de
	confirmation.
	fClipboard : presse-papiers des secrets
	fNotify    : affiche un bandeau
*/
class CopyActions(
	private val fClipboard : SecretClipboard,
	private val fNotify    : (String) -> Unit
)
{
	/*
		Copie une donnée et l'annonce.
		inLabel  : nom de la donnée
		inText   : valeur
		inSecret : secret, effacé après le délai
	*/
	fun copy(
		inLabel  : String,
		inText   : String,
		inSecret : Boolean
	)
	{
		if (inText.isEmpty()) return
		fClipboard.copy(inLabel, inText, inSecret)
		fNotify(if (inSecret) "$inLabel copié — effacé dans $kClipboardSeconds s" else "$inLabel copié")
	}
}

/*
	Coffre ouvert : en 3 colonnes (groupes, liste, fiche ou édition) sur un
	écran large, sinon liste, puis fiche ou édition en plein écran. Bandeau
	de confirmation des copies et des actions ; état de l'enregistrement.
	Raccourcis clavier (PC, ou clavier branché au téléphone) :
	Ctrl+F rechercher, Ctrl+N nouvelle entrée, Ctrl+E modifier, Ctrl+B copier
	l'identifiant, Ctrl+Maj+C copier le mot de passe, Ctrl+U ouvrir le site,
	Ctrl+L verrouiller, Échap annuler ou fermer.
	inController : état de l'appli
	inVault      : coffre ouvert
	inDemo       : coffre de démonstration
*/
@Composable
fun VaultScreen(
	inController : AppController,
	inVault      : Vault,
	inDemo       : Boolean
)
{
	val vScope   = rememberCoroutineScope()													// actions sur le coffre
	var vNotice  by remember { mutableStateOf<String?>(null) }								// bandeau affiché
	var vStamp   by remember { mutableStateOf(0) }											// numéro du bandeau, pour relancer le délai
	val vNotify  = { vText: String -> vNotice = vText; vStamp += 1 }							// affiche un bandeau
	val vCopy    = CopyActions(rememberSecretClipboard(), vNotify)							// copies
	val vActions = EntryActions(
		edit    = inController::startEdit,
		trash   = { vId ->
			val vForGood = inVault.draft(vId)?.groupId?.let { inVault.isInTrash(it) } == true	// déjà à la corbeille
			vScope.launch { inController.trashEntry(vId) }
			vNotify(if (vForGood) "Entrée supprimée" else "Entrée mise à la corbeille")
		},
		restore = { vId -> vScope.launch { inController.restoreEntry(vId) }; vNotify("Entrée restaurée") }
	)																						// actions de la fiche
	LaunchedEffect(vStamp) { if (vNotice != null) { delay(2600); vNotice = null } }
	val vQuery   = inController.query.text.toString()										// texte cherché
	val vItems   = remember(inVault, vQuery, inController.filter) {
		when (val vFilter = inController.filter)
		{
			ListFilter.All      -> inVault.search(vQuery)
			is ListFilter.Group -> inVault.search(vQuery, vFilter.id)
			ListFilter.Trash    -> inVault.trashItems(vQuery)
		}
	}																						// entrées affichées
	val vSearch  = remember { FocusRequester() }												// focus de la recherche
	val vRoot    = remember { FocusRequester() }												// focus de l'écran (raccourcis)
	val vUri     = LocalUriHandler.current														// ouverture des liens
	LaunchedEffect(Unit) { runCatching { vRoot.requestFocus() } }
	/*
		Raccourci clavier ; vrai s'il a été traité.
		inEvent : touche
	*/
	fun shortcut(inEvent: KeyEvent): Boolean
	{
		if (inEvent.type != KeyEventType.KeyDown) return false
		val vDraft = inController.selected?.let { inVault.draft(it) }							// entrée choisie
		if (inEvent.key == Key.Escape)
		{
			when
			{
				inController.editing != null              -> inController.cancelEdit()
				inController.query.text.isNotEmpty()      -> inController.query.clearText()
				inController.selected != null             -> inController.selected = null
				else                                      -> return false
			}
			return true
		}
		if (!inEvent.isCtrlPressed) return false
		when (inEvent.key)
		{
			Key.F -> runCatching { vSearch.requestFocus() }
			Key.N -> if (inController.editing == null) inController.startNew()
			Key.L -> inController.lock()
			Key.E -> if (vDraft != null && inController.editing == null) inController.startEdit(vDraft.id!!)
			Key.B -> if (vDraft != null) vCopy.copy("Identifiant", vDraft.userName, false)
			Key.C -> if (inEvent.isShiftPressed && vDraft != null) vCopy.copy("Mot de passe", vDraft.password, true) else return false
			Key.U -> if (vDraft != null && vDraft.url.isNotBlank()) runCatching { vUri.openUri(if ("://" in vDraft.url) vDraft.url else "https://${vDraft.url}") }
			else  -> return false
		}
		return true
	}
	BoxWithConstraints(
		Modifier
			.fillMaxSize()
			.background(KeeColors.ground)
			.onPreviewKeyEvent { shortcut(it) }
			.focusRequester(vRoot)
			.focusable()
	) {
		if (maxWidth >= kWideMin) WideVault(inController, inVault, inDemo, vItems, vCopy, vActions, vSearch)
		else CompactVault(inController, inVault, inDemo, vItems, vCopy, vActions, vSearch)
		Notice(inController.saveError ?: vNotice, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 24.dp))
	}
}

/*
	Volet affiché sur le téléphone.
*/
private sealed interface Pane
{
	/*
		Liste des entrées.
	*/
	data object List : Pane

	/*
		Fiche d'une entrée.
		id : entrée
	*/
	data class Detail(val id: UUID) : Pane

	/*
		Édition d'une entrée.
		draft : contenu de départ
	*/
	data class Edit(val draft: EntryDraft) : Pane
}

/*
	Téléphone : liste (en-tête, recherche, pastilles de groupes, bouton
	« + »), puis fiche ou édition en plein écran par-dessus.
*/
@Composable
private fun CompactVault(
	inController : AppController,
	inVault      : Vault,
	inDemo       : Boolean,
	inItems      : List<VaultItem>,
	inCopy       : CopyActions,
	inActions    : EntryActions,
	inSearch     : FocusRequester
)
{
	val vEditing  = inController.editing														// entrée en édition
	val vSelected = inController.selected?.takeIf { inVault.draft(it) != null }			// entrée affichée
	val vPane     = when
	{
		vEditing != null  -> Pane.Edit(vEditing)
		vSelected != null -> Pane.Detail(vSelected)
		else              -> Pane.List
	}																						// volet affiché
	AnimatedContent(
		vPane,
		contentKey     = { it::class },
		transitionSpec = {
			if (targetState != Pane.List) (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut()
			else fadeIn() togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
		},
		label = "volet"
	) { vShown ->
		when (vShown)
		{
			is Pane.Edit   -> EntryEditor(inController, inVault, vShown.draft)
			is Pane.Detail -> EntryDetail(inVault, vShown.id, inCopy, { inController.selected = null }, inActions)
			Pane.List      -> Box(Modifier.fillMaxSize()) {
				Column(Modifier.fillMaxSize().background(mistTop()).safeDrawingPadding()) {
					Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
						KeeLogo(inSize = 26.dp)
						Spacer(Modifier.width(10.dp))
						Column(Modifier.weight(1f)) {
							OneLine(inVault.name, keeType().title)
							SaveStatus(inController)
						}
						IconAction(LineIcons.Lock, "Verrouiller", inController::lock, inTint = KeeColors.text)
					}
					if (inDemo) DemoBadge(Modifier.padding(start = 20.dp, top = 4.dp))
					SearchField(inController.query, Modifier.padding(horizontal = 16.dp, vertical = 12.dp).focusRequester(inSearch))
					GroupPills(inController, inVault)
					EntryList(inVault, inItems, null, inController, inCopy, emptyText(inController, inVault), PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 112.dp))
				}
				if (inController.filter != ListFilter.Trash)
				{
					AddButton(inController::startNew, Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(20.dp))
				}
			}
		}
	}
}

/*
	PC : groupes à gauche, liste au centre (bouton « + » à côté de la
	recherche), fiche ou édition à droite.
*/
@Composable
private fun WideVault(
	inController : AppController,
	inVault      : Vault,
	inDemo       : Boolean,
	inItems      : List<VaultItem>,
	inCopy       : CopyActions,
	inActions    : EntryActions,
	inSearch     : FocusRequester
)
{
	Row(Modifier.fillMaxSize()) {
		GroupColumn(inController, inVault, inDemo)
		Column(
			Modifier
				.width(kListWidth)
				.fillMaxHeight()
				.background(KeeColors.ground)
		) {
			Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
				SearchField(inController.query, Modifier.weight(1f).focusRequester(inSearch))
				Spacer(Modifier.width(8.dp))
				AddButton(inController::startNew, inCompact = true)
			}
			EntryList(inVault, inItems, inController.selected, inController, inCopy, emptyText(inController, inVault), PaddingValues(8.dp))
		}
		Box(Modifier.width(1.dp).fillMaxHeight().background(KeeColors.rule))
		Box(Modifier.weight(1f).fillMaxHeight().background(mistTop())) {
			val vEditing  = inController.editing														// entrée en édition
			val vSelected = inController.selected?.takeIf { inVault.draft(it) != null }		// entrée affichée
			when
			{
				vEditing != null  -> key(vEditing.id) { EntryEditor(inController, inVault, vEditing) }
				vSelected != null -> EntryDetail(inVault, vSelected, inCopy, null, inActions)
				else              -> BasicText("Choisissez une entrée.", Modifier.align(Alignment.Center), style = keeType().bodySmall)
			}
		}
	}
}

/*
	Bouton d'ajout d'une entrée : pilule argentée « + Nouvelle entrée »
	(téléphone, flottant), ou rond argenté (PC, à côté de la recherche).
	inOnClick  : action
	inModifier : modificateur
	inCompact  : rond, sans texte
*/
@Composable
private fun AddButton(
	inOnClick  : () -> Unit,
	inModifier : Modifier = Modifier,
	inCompact  : Boolean  = false
)
{
	Row(
		inModifier
			.height(if (inCompact) 48.dp else 56.dp)
			.clip(CircleShape)
			.background(Brush.verticalGradient(listOf(KeeColors.silverLight, KeeColors.silverDark)))
			.clickable(role = Role.Button, onClick = inOnClick)
			.padding(horizontal = if (inCompact) 12.dp else 20.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(LineIcons.Plus, "Nouvelle entrée", Modifier.size(24.dp), tint = KeeColors.silverInk)
		if (!inCompact)
		{
			Spacer(Modifier.width(8.dp))
			BasicText("Nouvelle entrée", style = keeType().label.copy(color = KeeColors.silverInk))
		}
	}
}

/*
	État de l'enregistrement, sous le nom du coffre : « Enregistrement… »
	pendant l'écriture ; rien sinon (un échec s'affiche dans le bandeau).
	inController : état de l'appli
*/
@Composable
private fun SaveStatus(inController: AppController)
{
	if (inController.saving) BasicText("Enregistrement…", style = keeType().bodySmall.copy(color = KeeColors.textFaint))
}

/*
	Colonne des groupes (PC) : marque, nom du coffre, Tout, groupes avec leur
	nombre d'entrées, Corbeille, et Verrouiller en bas.
*/
@Composable
private fun GroupColumn(
	inController : AppController,
	inVault      : Vault,
	inDemo       : Boolean
)
{
	val vType = keeType()																	// styles de texte
	Column(
		Modifier
			.width(kGroupWidth)
			.fillMaxHeight()
			.background(KeeColors.void)
			.padding(horizontal = 12.dp, vertical = 20.dp)
	) {
		Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
			KeeLogo(inSize = 26.dp)
			Spacer(Modifier.width(10.dp))
			Column(Modifier.weight(1f)) {
				OneLine(inVault.name, vType.title)
				SaveStatus(inController)
			}
		}
		if (inDemo) DemoBadge(Modifier.padding(start = 8.dp, top = 10.dp))
		Spacer(Modifier.height(20.dp))
		Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			GroupRow(LineIcons.All, "Tout", inVault.items().size, 0, inController.filter == ListFilter.All) { inController.filter = ListFilter.All }
			for (vGroup in inVault.groups())
			{
				val vFilter = ListFilter.Group(vGroup.id)											// filtre du groupe
				GroupRow(LineIcons.Folder, vGroup.name, inVault.items(vGroup.id).size, vGroup.depth, inController.filter == vFilter) { inController.filter = vFilter }
			}
			val vTrash = inVault.trashItems().size													// entrées à la corbeille
			if (vTrash > 0)
			{
				Spacer(Modifier.height(12.dp))
				GroupRow(LineIcons.Trash, "Corbeille", vTrash, 0, inController.filter == ListFilter.Trash) { inController.filter = ListFilter.Trash }
			}
		}
		GroupRow(LineIcons.Lock, "Verrouiller", null, 0, false, inController::lock)
	}
}

/*
	Ligne de la colonne des groupes.
	inIcon     : icône
	inName     : nom
	inCount    : nombre d'entrées ; null pour ne pas l'afficher
	inDepth    : profondeur (retrait)
	inSelected : ligne active
	inOnClick  : action
*/
@Composable
private fun GroupRow(
	inIcon     : ImageVector,
	inName     : String,
	inCount    : Int?,
	inDepth    : Int,
	inSelected : Boolean,
	inOnClick  : () -> Unit
)
{
	val vType = keeType()																	// styles de texte
	Row(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(12.dp))
			.background(if (inSelected) KeeColors.surfaceHigh else Color.Transparent)
			.clickable(role = Role.Tab, onClick = inOnClick)
			.padding(start = 10.dp + 16.dp * inDepth, end = 12.dp, top = 10.dp, bottom = 10.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(inIcon, null, Modifier.size(20.dp), tint = if (inSelected) KeeColors.text else KeeColors.textMuted)
		Spacer(Modifier.width(12.dp))
		OneLine(inName, vType.label.copy(color = if (inSelected) KeeColors.text else KeeColors.textMuted), Modifier.weight(1f))
		if (inCount != null) BasicText("$inCount", style = vType.bodySmall.copy(color = KeeColors.textFaint))
	}
}

/*
	Pastilles des groupes (téléphone), défilant horizontalement : Tout, les
	groupes, la Corbeille si elle n'est pas vide.
*/
@Composable
private fun GroupPills(
	inController : AppController,
	inVault      : Vault
)
{
	val vGroups = inVault.groups()															// groupes
	val vTrash  = inVault.trashItems().isNotEmpty()										// corbeille non vide
	LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		item { FilterPill("Tout", inController.filter == ListFilter.All) { inController.filter = ListFilter.All } }
		items(vGroups, key = { it.id }) { vGroup ->
			val vFilter = ListFilter.Group(vGroup.id)												// filtre du groupe
			FilterPill(vGroup.name, inController.filter == vFilter) { inController.filter = vFilter }
		}
		if (vTrash) item { FilterPill("Corbeille", inController.filter == ListFilter.Trash) { inController.filter = ListFilter.Trash } }
	}
}

/*
	Liste des entrées : monogramme, titre, identifiant, copie du mot de passe
	à droite. Les lignes sont séparées par l'espacement seul.
	inVault      : coffre (mots de passe à copier)
	inItems      : entrées
	inSelected   : entrée mise en évidence (PC) ; null pour aucune
	inController : état de l'appli (choix de l'entrée)
	inCopy       : copies
	inEmpty      : texte affiché quand la liste est vide
	inPadding    : marges de la liste
*/
@Composable
private fun EntryList(
	inVault      : Vault,
	inItems      : List<VaultItem>,
	inSelected   : UUID?,
	inController : AppController,
	inCopy       : CopyActions,
	inEmpty      : String,
	inPadding    : PaddingValues
)
{
	val vType = keeType()																	// styles de texte
	if (inItems.isEmpty())
	{
		BasicText(inEmpty, Modifier.fillMaxWidth().padding(32.dp), style = vType.bodySmall)
		return
	}
	LazyColumn(contentPadding = inPadding, verticalArrangement = Arrangement.spacedBy(2.dp)) {
		items(inItems, key = { it.id }) { vItem ->
			Row(
				Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(16.dp))
					.background(if (vItem.id == inSelected) KeeColors.surfaceHigh else Color.Transparent)
					.clickable { inController.selected = vItem.id }
					.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Monogram(vItem.title)
				Spacer(Modifier.width(14.dp))
				Column(Modifier.weight(1f)) {
					OneLine(vItem.title.ifBlank { "Sans titre" }, vType.title)
					if (vItem.userName.isNotBlank()) OneLine(vItem.userName, vType.bodySmall)
				}
				IconAction(LineIcons.Copy, "Copier le mot de passe de ${vItem.title}", {
					inCopy.copy("Mot de passe", inVault.draft(vItem.id)?.password.orEmpty(), true)
				})
			}
		}
	}
}

/*
	Texte de la liste vide, selon la recherche et le filtre.
*/
private fun emptyText(
	inController : AppController,
	inVault      : Vault
): String
{
	val vQuery = inController.query.text.toString().trim()									// texte cherché
	return when
	{
		vQuery.isNotEmpty()                     -> "Aucune entrée ne correspond à « $vQuery »."
		inController.filter == ListFilter.Trash -> "La corbeille est vide."
		inVault.items().isEmpty()               -> "Le coffre est vide pour l'instant."
		else                                    -> "Ce groupe est vide."
	}
}

/*
	Badge du coffre de démonstration.
	inModifier : modificateur
*/
@Composable
private fun DemoBadge(inModifier: Modifier = Modifier)
{
	BasicText(
		"Démonstration · données inventées",
		inModifier
			.clip(CircleShape)
			.border(1.dp, KeeColors.rule, CircleShape)
			.padding(horizontal = 10.dp, vertical = 4.dp),
		style = keeType().bodySmall
	)
}

/*
	Léger voile de brume en haut des écrans de travail (120 premiers dp).
*/
internal fun mistTop(): Brush =
	Brush.verticalGradient(0f to KeeColors.mistMid.copy(alpha = 0.32f), 0.16f to KeeColors.ground, 1f to KeeColors.ground)

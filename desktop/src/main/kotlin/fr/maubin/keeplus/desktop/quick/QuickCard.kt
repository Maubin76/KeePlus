package fr.maubin.keeplus.desktop.quick

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.autofill.DesktopTarget
import fr.maubin.keeplus.autofill.PasteSequence
import fr.maubin.keeplus.autofill.addDesktopSignUp
import fr.maubin.keeplus.autofill.changePassword
import fr.maubin.keeplus.autofill.desktopMatches
import fr.maubin.keeplus.autofill.rememberWindowsApp
import fr.maubin.keeplus.autofill.sharedTitles
import fr.maubin.keeplus.autofill.suggestedUserName
import fr.maubin.keeplus.generator.PasswordOptions
import fr.maubin.keeplus.generator.generatePassword
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.AppScreen
import fr.maubin.keeplus.ui.components.AccountRow
import fr.maubin.keeplus.ui.components.FilterPill
import fr.maubin.keeplus.ui.components.GeneratedPassword
import fr.maubin.keeplus.ui.components.KeeTextField
import fr.maubin.keeplus.ui.components.SearchField
import fr.maubin.keeplus.ui.components.SecretField
import fr.maubin.keeplus.ui.components.SilverButton
import fr.maubin.keeplus.ui.components.TextLink
import fr.maubin.keeplus.ui.screens.ErrorLine
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.KeeLogo
import fr.maubin.keeplus.ui.theme.keeType
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.VaultItem
import kotlinx.coroutines.launch
import java.util.UUID

/*
	Cas de la carte de saisie.
*/
enum class QuickMode(val label: String)
{
	Login("Connexion"), SignUp("Inscription"), Change("Changer")
}

/*
	Résultat de la carte : la séquence à coller et la modification du coffre
	qui l'accompagne (compte créé, mot de passe changé, lien retenu).
	sequence : éléments à coller
	change   : modification du coffre ; null sans
*/
class QuickResult(
	val sequence : PasteSequence,
	val change   : ((Vault) -> Vault)?
)

/*
	Contenu de la carte de saisie du PC : en-tête « KeePlus », déverrouillage
	si besoin (ou renvoi vers l'appli s'il n'y a pas encore de coffre sur ce
	PC), puis le cas choisi par les pastilles
	(connexion par défaut s'il existe des comptes, inscription sinon).
	Ctrl+C (ou le bouton) copie la séquence et ferme la carte ; Échap la
	ferme sans rien copier.
	inController : état de l'appli (coffre ouvert, déverrouillage)
	inTarget     : fenêtre active
	inOnResult   : séquence prête
	inOnClose    : fermeture sans rien copier
	inOnOpenApp  : ouvrir la fenêtre de KeePlus (pas encore de coffre)
*/
@Composable
fun QuickCardContent(
	inController : AppController,
	inTarget     : DesktopTarget,
	inOnResult   : (QuickResult) -> Unit,
	inOnClose    : () -> Unit,
	inOnOpenApp  : () -> Unit = {}
)
{
	val vType    = keeType()																	// styles de texte
	val vVault   = inController.activeVault()													// coffre ouvert ; null s'il faut déverrouiller
	val vMatches = remember(vVault, inTarget) { vVault?.desktopMatches(inTarget).orEmpty() }	// comptes de la fenêtre
	var vMode    by remember(vVault == null) { mutableStateOf(if (vMatches.isEmpty()) QuickMode.SignUp else QuickMode.Login) }	// cas affiché
	val vAction  = remember { mutableStateOf<(() -> Unit)?>(null) }								// action de Ctrl+C du cas affiché
	Column(
		Modifier
			.fillMaxWidth()
			.onPreviewKeyEvent { inEvent -> keys(inEvent, vAction.value, inOnClose) }
			.padding(horizontal = 26.dp, vertical = 18.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			KeeLogo(inSize = 26.dp)
			Spacer(Modifier.width(12.dp))
			BasicText("KeePlus", Modifier.weight(1f), style = vType.title, maxLines = 1)
		}
		if (vVault != null)
		{
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				for (vChoice in QuickMode.entries) FilterPill(vChoice.label, vMode == vChoice) { vMode = vChoice }
			}
		}
		if (inController.screen !is AppScreen.Locked && inController.screen !is AppScreen.Opened)
		{
			BasicText("Aucun coffre sur ce PC.", style = vType.body)
			SilverButton("Ouvrir KeePlus", inOnOpenApp)
			return@Column
		}
		AnimatedContent(
			targetState    = if (vVault == null) null else vMode,
			transitionSpec = {
				(fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(280)) { it / 12 }) togetherWith
					fadeOut(tween(90)) using SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.9f, stiffness = 420f) }
			},
			label          = "cas de la carte"
		) { vShown ->
			Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
				when
				{
					vShown == null || vVault == null -> UnlockPart(inController)
					vShown == QuickMode.Login        -> LoginPart(vVault, inTarget, vMatches, vAction, inOnResult)
					vShown == QuickMode.SignUp       -> SignUpPart(vVault, inTarget, vAction, inOnResult)
					else                             -> ChangePart(vVault, inTarget, vMatches, vAction, inOnResult)
				}
			}
		}
	}
}

/*
	Raccourcis de la carte : Ctrl+C lance l'action du cas affiché (même dans
	un champ de saisie), Échap ferme.
	inEvent   : touche
	inAction  : action du cas affiché ; null sans
	inOnClose : fermeture
*/
private fun keys(
	inEvent   : KeyEvent,
	inAction  : (() -> Unit)?,
	inOnClose : () -> Unit
): Boolean
{
	if (inEvent.type != KeyEventType.KeyDown) return false
	return when
	{
		inEvent.key == Key.Escape                       -> { inOnClose(); true }
		inEvent.isCtrlPressed && inEvent.key == Key.C   -> { inAction?.invoke(); inAction != null }
		else                                            -> false
	}
}

/*
	Déverrouillage dans la carte : mot de passe maître.
	inController : état de l'appli
*/
@Composable
private fun UnlockPart(inController: AppController)
{
	val vScope    = rememberCoroutineScope()													// déverrouillage
	val vPassword = rememberTextFieldState()													// mot de passe maître
	val vFocus    = remember { FocusRequester() }												// focus du champ
	LaunchedEffect(Unit) { runCatching { vFocus.requestFocus() } }
	/*
		Ouvre le coffre avec le mot de passe saisi.
	*/
	fun submit()
	{
		vScope.launch { inController.unlock(vPassword.text)?.let(inController::open) }
	}
	SecretField(vPassword, "Mot de passe maître", Modifier.focusRequester(vFocus), inError = inController.error != null, inEnabled = !inController.busy, inOnSubmit = ::submit)
	ErrorLine(inController.error)
	SilverButton(if (inController.busy) "Ouverture…" else "Déverrouiller", ::submit, inEnabled = !inController.busy)
}

/*
	Connexion : comptes de la fenêtre (ou recherche dans tout le coffre),
	choix à la souris ou aux flèches ; Entrée, Ctrl+C ou un clic copie
	identifiant puis mot de passe. Pour une appli Windows, un compte trouvé
	par la recherche peut lui être lié.
	inVault    : coffre ouvert
	inTarget   : fenêtre active
	inMatches  : comptes de la fenêtre
	inAction   : action de Ctrl+C (renseignée ici)
	inOnResult : séquence prête
	inOnChoose : choix du compte seulement (changement de mot de passe) ; null pour copier
*/
@Composable
private fun LoginPart(
	inVault    : Vault,
	inTarget   : DesktopTarget,
	inMatches  : List<VaultItem>,
	inAction   : androidx.compose.runtime.MutableState<(() -> Unit)?>,
	inOnResult : (QuickResult) -> Unit,
	inOnChoose : ((VaultItem) -> Unit)? = null
)
{
	val vQuery    = rememberTextFieldState()													// texte cherché
	val vFocus    = remember { FocusRequester() }												// focus de la recherche
	val vText     = vQuery.text.toString().trim()												// recherche
	val vItems    = when
	{
		vText.isNotEmpty()     -> inVault.search(vText)
		inMatches.isNotEmpty() -> inMatches
		else                   -> inVault.items()
	}																							// comptes affichés
	val vShared   = sharedTitles(vItems)														// titres portés par plusieurs comptes
	var vIndex    by remember(vItems) { mutableIntStateOf(0) }									// compte mis en évidence
	var vRemember by remember { mutableStateOf(true) }											// lier le compte à l'appli
	val vList     = rememberLazyListState()														// défilement de la liste
	LaunchedEffect(Unit) { runCatching { vFocus.requestFocus() } }
	LaunchedEffect(vIndex) { if (vItems.isNotEmpty()) vList.animateScrollToItem(vIndex) }
	/*
		Copie le compte choisi.
		inItem : compte
	*/
	fun pick(inItem: VaultItem)
	{
		inOnChoose?.let { it(inItem); return }
		val vDraft = inVault.draft(inItem.id) ?: return										// entrée
		val vLink  = vRemember && !inTarget.isBrowser && inItem !in inMatches				// lien à retenir
		inOnResult(QuickResult(PasteSequence.login(vDraft.userName, vDraft.password), if (vLink) { vVault -> vVault.rememberWindowsApp(inItem.id, inTarget) } else null))
	}
	inAction.value = vItems.getOrNull(vIndex)?.let { vItem -> { pick(vItem) } }
	SearchField(
		vQuery,
		Modifier.focusRequester(vFocus).onPreviewKeyEvent { inEvent ->
			if (inEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
			when (inEvent.key)
			{
				Key.DirectionDown -> { vIndex = (vIndex + 1).coerceAtMost(vItems.lastIndex.coerceAtLeast(0)); true }
				Key.DirectionUp   -> { vIndex = (vIndex - 1).coerceAtLeast(0); true }
				Key.Enter         -> { vItems.getOrNull(vIndex)?.let(::pick); true }
				else              -> false
			}
		},
		"Chercher dans le coffre"
	)
	if (inOnChoose == null && !inTarget.isBrowser && inTarget.exe.isNotBlank() && (vText.isNotEmpty() || inMatches.isEmpty()))
	{
		FilterPill(if (vRemember) "✓ Proposer ce compte ici la prochaine fois" else "Proposer ce compte ici la prochaine fois", vRemember) { vRemember = !vRemember }
	}
	if (vItems.isEmpty()) BasicText("Aucun compte.", style = keeType().bodySmall)
	LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), state = vList, verticalArrangement = Arrangement.spacedBy(2.dp)) {
		itemsIndexed(vItems, key = { _, vItem -> vItem.id }) { vRow, vItem ->
			AccountRow(vItem, vItem.title in vShared, vRow == vIndex) { pick(vItem) }
		}
	}
	CopyButton(inAction.value, if (inOnChoose == null) "Copier · Ctrl+C" else "Choisir")
}

/*
	Inscription : identifiant (l'e-mail le plus utilisé du coffre, sinon
	l'identifiant le plus utilisé ; modifiable) et mot
	de passe généré de longueur réglable ; Ctrl+C copie identifiant puis mot
	de passe (qui reste pour la confirmation) et ajoute le compte au coffre.
	inVault    : coffre ouvert
	inTarget   : fenêtre active
	inAction   : action de Ctrl+C (renseignée ici)
	inOnResult : séquence prête
*/
@Composable
private fun SignUpPart(
	inVault    : Vault,
	inTarget   : DesktopTarget,
	inAction   : androidx.compose.runtime.MutableState<(() -> Unit)?>,
	inOnResult : (QuickResult) -> Unit
)
{
	val vType     = keeType()																	// styles de texte
	val vUser     = remember { TextFieldState(inVault.suggestedUserName(true).ifEmpty { inVault.suggestedUserName(false) }) }	// identifiant proposé (e-mail de préférence)
	var vOptions  by remember { mutableStateOf(PasswordOptions()) }							// réglages du générateur
	var vPassword by remember { mutableStateOf(generatePassword(vOptions)) }				// mot de passe proposé
	inAction.value = {
		val vName = vUser.text.toString().trim()													// identifiant choisi
		val vNew  = vPassword																	// mot de passe choisi
		inOnResult(QuickResult(PasteSequence.login(vName, vNew)) { vVault -> vVault.addDesktopSignUp(inTarget, vName, vNew) })
	}
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		BasicText("Identifiant", style = vType.label)
		KeeTextField(vUser, "Identifiant ou e-mail", inImeAction = ImeAction.Done)
	}
	GeneratedPassword(
		inLabel    = "Mot de passe",
		inValue    = vPassword,
		inLength   = vOptions.length,
		inOnLength = { vOptions = vOptions.copy(length = it); vPassword = generatePassword(vOptions) },
		inOnDraw   = { vPassword = generatePassword(vOptions) }
	)
	CopyButton(inAction.value)
}

/*
	Changement de mot de passe : compte (d'emblée s'il n'y en a qu'un),
	mot de passe actuel repris du coffre, nouveau mot de passe généré ;
	Ctrl+C copie identifiant, actuel puis nouveau (qui reste pour la
	confirmation) et met l'entrée à jour.
	inVault    : coffre ouvert
	inTarget   : fenêtre active
	inMatches  : comptes de la fenêtre
	inAction   : action de Ctrl+C (renseignée ici)
	inOnResult : séquence prête
*/
@Composable
private fun ChangePart(
	inVault    : Vault,
	inTarget   : DesktopTarget,
	inMatches  : List<VaultItem>,
	inAction   : androidx.compose.runtime.MutableState<(() -> Unit)?>,
	inOnResult : (QuickResult) -> Unit
)
{
	val vType     = keeType()																	// styles de texte
	var vChosen   by remember { mutableStateOf<UUID?>(inMatches.singleOrNull()?.id) }		// compte retenu
	var vOptions  by remember { mutableStateOf(PasswordOptions()) }							// réglages du générateur
	var vPassword by remember { mutableStateOf(generatePassword(vOptions)) }				// nouveau mot de passe
	val vItem     = vChosen?.let { vId -> inVault.items().firstOrNull { it.id == vId } }		// compte affiché
	if (vItem == null)
	{
		LoginPart(inVault, inTarget, inMatches, inAction, inOnResult) { vChosen = it.id }
		return
	}
	val vDraft    = inVault.draft(vItem.id)														// entrée du compte
	inAction.value = {
		val vNew = vPassword																	// nouveau mot de passe
		if (vDraft != null) inOnResult(QuickResult(PasteSequence.change(vDraft.userName, vDraft.password, vNew)) { vVault -> vVault.changePassword(vItem.id, vNew) })
	}
	Row(verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.weight(1f)) { AccountRow(vItem, true) {} }
		TextLink("Autre compte", { vChosen = null })
	}
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		BasicText("Mot de passe actuel", style = vType.label)
		BasicText(if (vDraft?.password.isNullOrEmpty()) "Aucun" else "•".repeat(vDraft!!.password.length.coerceIn(8, 20)), style = vType.secret)
	}
	GeneratedPassword(
		inLabel    = "Nouveau mot de passe",
		inValue    = vPassword,
		inLength   = vOptions.length,
		inOnLength = { vOptions = vOptions.copy(length = it); vPassword = generatePassword(vOptions) },
		inOnDraw   = { vPassword = generatePassword(vOptions) }
	)
	CopyButton(inAction.value)
}

/*
	Bouton principal du cas (même effet que Ctrl+C).
	inAction : action ; null : bouton inactif
	inLabel  : texte du bouton
*/
@Composable
private fun CopyButton(
	inAction : (() -> Unit)?,
	inLabel  : String = "Copier · Ctrl+C"
)
{
	SilverButton(inLabel, { inAction?.invoke() }, inEnabled = inAction != null)
}

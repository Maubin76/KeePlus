package fr.maubin.keeplus.autofill

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.generator.PasswordOptions
import fr.maubin.keeplus.generator.generatePassword
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.components.FilterPill
import fr.maubin.keeplus.ui.components.IconAction
import fr.maubin.keeplus.ui.components.Monogram
import fr.maubin.keeplus.ui.components.OneLine
import fr.maubin.keeplus.ui.components.SearchField
import fr.maubin.keeplus.ui.components.KeeTextField
import fr.maubin.keeplus.ui.components.SecretField
import fr.maubin.keeplus.ui.components.SilverButton
import fr.maubin.keeplus.ui.components.Stepper
import fr.maubin.keeplus.ui.components.TextLink
import fr.maubin.keeplus.ui.components.secretAnnotated
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.screens.ErrorLine
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.KeeLogo
import fr.maubin.keeplus.ui.theme.keeType
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.VaultItem
import kotlinx.coroutines.launch
import java.util.UUID

private val kCardShape  = RoundedCornerShape(36.dp)											// carte aux coins très arrondis
private val kLengths     = 8..64																// longueurs proposées (comme le générateur de l'appli)
private const val kScrim = 0.18f																	// voile sur l'appli, carte affichée
private const val kOutMs = 220																		// durée de la sortie (ms)

/*
	Apparition et disparition de la carte : elle descend du haut de l'écran
	à l'ouverture et y remonte à la fermeture ; l'action de fermeture
	(remplir, annuler) n'a lieu qu'une fois la carte sortie. Une seule
	fermeture est prise en compte.
	shown  : visibilité animée de la carte
	fAfter : action à lancer une fois la carte sortie ; null avant la fermeture
*/
class CardMotion
{
	val shown = MutableTransitionState(false).apply { targetState = true }
	private var fAfter: (() -> Unit)? = null

	/*
		Fait remonter la carte, puis lance l'action.
		inAfter : action (fermer l'écran)
	*/
	fun close(inAfter: () -> Unit)
	{
		if (fAfter != null) return
		fAfter = inAfter
		shown.targetState = false
	}

	/*
		La carte est sortie : lance l'action de fermeture, une fois.
	*/
	fun settle()
	{
		val vAfter = fAfter ?: return															// action en attente
		if (shown.isIdle && !shown.currentState)
		{
			fAfter = {}
			vAfter()
		}
	}
}

/*
	Petite carte sombre en haut de l'écran, par-dessus l'appli (au format des
	cartes du système, comme le lecteur de musique), qui descend du haut avec
	un léger rebond et y remonte à la fermeture ; le voile sur l'appli suit.
	Toucher hors de la carte, ou la touche Retour, annule.
	inMotion   : apparition et disparition
	inLabel    : nature de la carte (« Connexion », « Nouveau compte »)
	inName     : appli ou site
	inOnCancel : fermeture sans remplir
	inContent  : contenu, sous l'en-tête
*/
@Composable
private fun AutofillCardFrame(
	inMotion   : CardMotion,
	inLabel    : String,
	inName     : String,
	inOnCancel : () -> Unit,
	inContent  : @Composable ColumnScope.() -> Unit
)
{
	val vShown = inMotion.shown																	// visibilité de la carte
	val vScrim by animateFloatAsState(if (vShown.targetState) kScrim else 0f, tween(if (vShown.targetState) 260 else kOutMs))	// voile
	LaunchedEffect(vShown.isIdle, vShown.currentState) { inMotion.settle() }
	BackHandler(onBack = inOnCancel)
	Box(
		Modifier
			.fillMaxSize()
			.background(Color.Black.copy(alpha = vScrim))
			.clickable(remember { MutableInteractionSource() }, null, onClick = inOnCancel)
			.safeDrawingPadding()
			.padding(horizontal = 10.dp, vertical = 8.dp)
	) {
		AnimatedVisibility(
			visibleState = vShown,
			enter        = slideInVertically(spring(dampingRatio = 0.78f, stiffness = 420f)) { -it } +
				scaleIn(tween(260), initialScale = 0.94f) + fadeIn(tween(180)),
			exit         = slideOutVertically(tween(kOutMs, easing = FastOutLinearInEasing)) { -it } +
				scaleOut(tween(kOutMs), targetScale = 0.94f) + fadeOut(tween(kOutMs))
		) {
			Column(
				Modifier
					.fillMaxWidth()
					.shadow(24.dp, kCardShape)
					.clip(kCardShape)
					.background(Brush.verticalGradient(listOf(KeeColors.surfaceHighest, KeeColors.surface, KeeColors.void)))
					.border(1.dp, KeeColors.rule, kCardShape)
					.clickable(remember { MutableInteractionSource() }, null) {}
					.padding(horizontal = 22.dp, vertical = 20.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp)
			) {
				CardHeader(inLabel, inName)
				inContent()
			}
		}
	}
}

/*
	Inscription : déverrouille d'abord le coffre si besoin (empreinte
	d'emblée, sinon mot de passe maître dans la carte), puis propose une
	valeur pour chaque champ : identifiant ou e-mail (le plus utilisé du
	coffre, modifiable) et mot de passe généré (nouveau tirage possible),
	rempli aussi dans la confirmation.
	inMotion   : apparition et disparition
	inRequest  : ce qu'il faut remplir
	inOpened   : coffre déjà ouvert ; null s'il faut déverrouiller
	inUnlock   : déverrouillage (mot de passe, empreinte, erreurs)
	inOnOpened : coffre déverrouillé dans la carte (à garder)
	inOnFill   : remplir et enregistrer (coffre, valeurs des champs d'identité, mot de passe)
	inOnCancel : fermeture sans remplir
*/
@Composable
fun SignUpCard(
	inMotion   : CardMotion,
	inRequest  : AutofillRequest,
	inOpened   : Vault?,
	inUnlock   : AppController,
	inOnOpened : (Vault) -> Unit,
	inOnFill   : (Vault, List<String>, String) -> Unit,
	inOnCancel : () -> Unit
)
{
	var vVault by remember { mutableStateOf(inOpened) }											// coffre ouvert ; null avant
	AutofillCardFrame(inMotion, "Nouveau compte", inRequest.name, inOnCancel) {
		CardSteps(vVault, { it != null }) { vOpen ->
			if (vOpen == null) UnlockPart(inUnlock, "Déverrouillez KeePlus pour créer et enregistrer ce compte.") { vNew -> inOnOpened(vNew); vVault = vNew }
			else ProposalPart(inRequest, vOpen, inOnFill, inOnCancel)
		}
	}
}

/*
	Connexion : déverrouille d'abord le coffre si besoin (empreinte
	d'emblée, sinon mot de passe maître dans la carte), puis propose les
	comptes de cette appli ou de ce site (ou tout le coffre s'il n'y en a
	pas), avec une recherche dans tout le coffre. Pour une appli, le compte
	choisi lui est lié si la pastille est cochée.
	inMotion   : apparition et disparition
	inRequest  : ce qu'il faut remplir
	inOpened   : coffre déjà ouvert ; null s'il faut déverrouiller
	inUnlock   : déverrouillage (mot de passe, empreinte, erreurs)
	inOnOpened : coffre déverrouillé dans la carte (à garder ; peut remplir aussitôt)
	inOnPick   : compte choisi (coffre, entrée, lier à l'appli)
	inOnCancel : fermeture sans remplir
*/
@Composable
fun LoginCard(
	inMotion   : CardMotion,
	inRequest  : AutofillRequest,
	inOpened   : Vault?,
	inUnlock   : AppController,
	inOnOpened : (Vault) -> Unit,
	inOnPick   : (Vault, UUID, Boolean) -> Unit,
	inOnCancel : () -> Unit
)
{
	var vVault by remember { mutableStateOf(inOpened) }											// coffre ouvert ; null avant
	AutofillCardFrame(inMotion, "Connexion", inRequest.name, inOnCancel) {
		CardSteps(vVault, { it != null }) { vOpen ->
			if (vOpen == null) UnlockPart(inUnlock, "Déverrouillez KeePlus pour remplir ce formulaire.") { vNew -> inOnOpened(vNew); vVault = vNew }
			else PickerPart(inRequest, vOpen, inOnPick = inOnPick)
		}
	}
}

/*
	Étapes d'une carte (déverrouillage, puis choix, puis proposition…) : la
	nouvelle étape arrive en fondu en glissant légèrement vers le haut,
	l'ancienne s'efface vite, et la carte s'agrandit ou se rétracte en
	douceur pour suivre la hauteur du contenu.
	inStep    : étape actuelle (données affichées)
	inKey     : ce qui distingue deux étapes : seul un changement de clé est animé
	inContent : contenu de l'étape
*/
@Composable
private fun <S> CardSteps(
	inStep    : S,
	inKey     : (S) -> Any?,
	inContent : @Composable ColumnScope.(S) -> Unit
)
{
	AnimatedContent(
		targetState    = inStep,
		contentKey     = inKey,
		transitionSpec = {
			(fadeIn(tween(240, delayMillis = 80)) + slideInVertically(tween(320)) { it / 10 }) togetherWith
				fadeOut(tween(100)) using SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.9f, stiffness = 380f) }
		},
		label          = "étape de la carte"
	) { vStep ->
		Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) { inContent(vStep) }
	}
}

/*
	Étape de la carte de changement de mot de passe.
	vault : coffre ouvert ; null avant le déverrouillage
	id    : compte retenu ; null pendant le choix
*/
private data class ChangeStep(
	val vault : Vault?,
	val id    : UUID?
)
{
	/*
		Nature de l'étape : déverrouillage, choix du compte, proposition.
	*/
	val kind: Int get() = when { vault == null -> 0; id == null -> 1; else -> 2 }
}

/*
	En-tête de la carte : bouclier, nature de la carte, nom de l'appli ou du site.
	inLabel : nature de la carte
	inName  : appli ou site
*/
@Composable
private fun CardHeader(
	inLabel : String,
	inName  : String
)
{
	val vType = keeType()																			// styles de texte
	Row(verticalAlignment = Alignment.CenterVertically) {
		KeeLogo(inSize = 28.dp)
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f)) {
			BasicText(inLabel, style = vType.bodySmall)
			BasicText(inName, style = vType.title, maxLines = 1)
		}
	}
}

/*
	Déverrouillage dans la carte : empreinte proposée d'emblée si elle est
	active, mot de passe maître sinon (ou après annulation).
	inUnlock  : déverrouillage
	inMessage : explication
	inOnDone  : coffre ouvert
*/
@Composable
private fun UnlockPart(
	inUnlock  : AppController,
	inMessage : String,
	inOnDone  : (Vault) -> Unit
)
{
	val vType     = keeType()																	// styles de texte
	val vScope    = rememberCoroutineScope()													// déverrouillage
	val vPassword = rememberTextFieldState()													// mot de passe maître
	LaunchedEffect(Unit) {
		if (inUnlock.biometricReady) inUnlock.unlockWithBiometric()?.let(inOnDone)
	}
	/*
		Déverrouille avec le mot de passe saisi.
	*/
	fun submit()
	{
		vScope.launch { inUnlock.unlock(vPassword.text)?.let(inOnDone) }
	}
	BasicText(inMessage, style = vType.bodySmall)
	SecretField(vPassword, "Mot de passe maître", inError = inUnlock.error != null, inEnabled = !inUnlock.busy, inOnSubmit = ::submit)
	ErrorLine(inUnlock.error)
	Row(verticalAlignment = Alignment.CenterVertically) {
		if (inUnlock.biometricReady)
		{
			IconAction(LineIcons.Fingerprint, "Déverrouiller par l'empreinte", { vScope.launch { inUnlock.unlockWithBiometric()?.let(inOnDone) } })
			Spacer(Modifier.width(8.dp))
		}
		SilverButton(if (inUnlock.busy) "Ouverture…" else "Déverrouiller", ::submit, Modifier.weight(1f), inEnabled = !inUnlock.busy)
	}
}

/*
	Proposition : une ligne par champ d'identité (valeur modifiable), le mot
	de passe généré, puis « Remplir et enregistrer » ou « Annuler ».
	inRequest  : ce qu'il faut remplir
	inVault    : coffre ouvert
	inOnFill   : remplir et enregistrer
	inOnCancel : fermeture sans remplir
*/
@Composable
private fun ProposalPart(
	inRequest  : AutofillRequest,
	inVault    : Vault,
	inOnFill   : (Vault, List<String>, String) -> Unit,
	inOnCancel : () -> Unit
)
{
	val vType      = keeType()																	// styles de texte
	val vFields    = inRequest.identities.map { vField ->
		vField to remember(vField.id) { TextFieldState(inVault.suggestedUserName(vField.email)) }
	}																							// champs d'identité et leurs valeurs
	var vOptions   by remember { mutableStateOf(PasswordOptions()) }							// réglages du générateur
	var vPassword  by remember { mutableStateOf(generatePassword(vOptions)) }				// mot de passe proposé
	for ((vField, vState) in vFields)
	{
		Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
			BasicText(if (vField.email) "Adresse e-mail" else "Identifiant", style = vType.label)
			KeeTextField(vState, if (vField.email) "vous@exemple.fr" else "Identifiant", inImeAction = ImeAction.Done)
		}
	}
	GeneratedPassword(
		inLabel    = if (inRequest.newPasswords.size > 1) "Mot de passe (et sa confirmation)" else "Mot de passe",
		inValue    = vPassword,
		inLength   = vOptions.length,
		inOnLength = { vOptions = vOptions.copy(length = it); vPassword = generatePassword(vOptions) },
		inOnDraw   = { vPassword = generatePassword(vOptions) }
	)
	SilverButton("Remplir et enregistrer", { inOnFill(inVault, vFields.map { it.second.text.toString().trim() }, vPassword) })
	Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
		TextLink("Annuler", inOnCancel)
	}
}

/*
	Choix du compte dans la carte : recherche, pastille « proposer ici la
	prochaine fois » (appli seulement, quand la liste montre des comptes qui
	ne lui sont pas encore liés), puis la liste (quatre comptes
	visibles environ, la suite défile).
	inRequest  : ce qu'il faut remplir
	inVault    : coffre ouvert
	inLinkable : la pastille « proposer ici la prochaine fois » a un sens
	inOnPick   : compte choisi (coffre, entrée, lier à l'appli)
*/
@Composable
private fun PickerPart(
	inRequest  : AutofillRequest,
	inVault    : Vault,
	inLinkable : Boolean = true,
	inOnPick   : (Vault, UUID, Boolean) -> Unit
)
{
	val vType     = keeType()																	// styles de texte
	val vQuery    = rememberTextFieldState()													// texte cherché
	val vMatches  = remember(inVault) { inVault.autofillMatches(inRequest.target) }			// comptes de la cible
	var vRemember by remember { mutableStateOf(true) }											// lier le compte à l'appli
	val vText     = vQuery.text.toString().trim()												// recherche
	val vItems    = when
	{
		vText.isNotEmpty()    -> inVault.search(vText)
		vMatches.isNotEmpty() -> vMatches
		else                  -> inVault.items()
	}																						// comptes affichés
	val vShared   = sharedTitles(vItems)														// titres portés par plusieurs comptes
	SearchField(vQuery, inPlaceholder = "Chercher dans le coffre")
	if (inLinkable && inRequest.target.isApp && (vText.isNotEmpty() || vMatches.isEmpty()))
	{
		FilterPill(if (vRemember) "✓ Proposer ce compte ici la prochaine fois" else "Proposer ce compte ici la prochaine fois", vRemember) { vRemember = !vRemember }
	}
	when
	{
		vItems.isEmpty()                      -> BasicText("Aucun compte ne correspond à « $vText ».", style = vType.bodySmall)
		vText.isEmpty() && vMatches.isEmpty() -> BasicText("Aucun compte lié à « ${inRequest.name} » : choisissez-en un.", style = vType.bodySmall)
	}
	LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
		items(vItems, key = { it.id }) { vItem ->
			PickerRow(vItem, vItem.title in vShared) { inOnPick(inVault, vItem.id, vRemember && vItem !in vMatches) }
		}
	}
}

/*
	Ligne d'un compte : monogramme, titre, identifiant. Quand plusieurs
	comptes de la liste portent le même titre (plusieurs comptes pour une
	même appli), l'identifiant passe en avant (ligne principale et
	monogramme) : c'est lui qui les distingue.
	inItem      : entrée
	inUserFirst : identifiant en avant
	inOnClick   : choix du compte
*/
@Composable
private fun PickerRow(
	inItem      : VaultItem,
	inUserFirst : Boolean = false,
	inOnClick   : () -> Unit
)
{
	val vType = keeType()																		// styles de texte
	Row(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(16.dp))
			.clickable(onClick = inOnClick)
			.padding(horizontal = 4.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		val vTitle = inItem.title.ifBlank { "Sans titre" }											// titre
		val vUser  = inUserFirst && inItem.userName.isNotBlank()									// identifiant en avant
		Monogram(if (vUser) inItem.userName else inItem.title, inSize = 40.dp)
		Spacer(Modifier.width(14.dp))
		Column(Modifier.weight(1f)) {
			OneLine(if (vUser) inItem.userName else vTitle, vType.title)
			val vDetail = if (vUser) vTitle else inItem.userName									// seconde ligne
			if (vDetail.isNotBlank()) OneLine(vDetail, vType.bodySmall)
		}
	}
}

/*
	Mot de passe généré : libellé, mot de passe coloré (lettres, chiffres,
	symboles, sur plusieurs lignes s'il est long), bouton de nouveau tirage,
	curseur de longueur (un nouveau mot de passe est tiré à chaque cran).
	inLabel    : libellé
	inValue    : mot de passe
	inLength   : longueur demandée
	inOnLength : nouvelle longueur
	inOnDraw   : nouveau tirage
*/
@Composable
private fun GeneratedPassword(
	inLabel    : String,
	inValue    : String,
	inLength   : Int,
	inOnLength : (Int) -> Unit,
	inOnDraw   : () -> Unit
)
{
	val vType = keeType()																		// styles de texte
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		BasicText(inLabel, style = vType.label)
		Row(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KeeColors.void.copy(alpha = 0.6f)).padding(start = 14.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			BasicText(secretAnnotated(inValue), Modifier.weight(1f).padding(vertical = 8.dp), style = vType.secret)
			IconAction(LineIcons.Refresh, "Nouveau tirage", inOnDraw)
		}
		Stepper("Longueur", inLength, kLengths) { if (it != inLength) inOnLength(it) }
	}
}

/*
	Changement de mot de passe : déverrouille d'abord le coffre si besoin,
	fait choisir le compte (d'emblée s'il n'y en a qu'un pour cette appli ou
	ce site), puis propose un nouveau mot de passe généré, rempli dans le
	champ du nouveau et sa confirmation ; l'actuel est rempli depuis
	l'entrée, qui est mise à jour (l'ancien rejoint son historique).
	inMotion   : apparition et disparition
	inRequest  : ce qu'il faut remplir
	inOpened   : coffre déjà ouvert ; null s'il faut déverrouiller
	inUnlock   : déverrouillage (mot de passe, empreinte, erreurs)
	inOnOpened : coffre déverrouillé dans la carte (à garder)
	inOnFill   : remplir et mettre à jour (coffre, entrée, nouveau mot de passe)
	inOnCancel : fermeture sans remplir
*/
@Composable
fun ChangeCard(
	inMotion   : CardMotion,
	inRequest  : AutofillRequest,
	inOpened   : Vault?,
	inUnlock   : AppController,
	inOnOpened : (Vault) -> Unit,
	inOnFill   : (Vault, UUID, String) -> Unit,
	inOnCancel : () -> Unit
)
{
	var vVault   by remember { mutableStateOf(inOpened) }										// coffre ouvert ; null avant
	var vChosen  by remember { mutableStateOf<UUID?>(null) }									// compte choisi ; null avant
	var vPicking by remember { mutableStateOf(false) }											// choix d'un autre compte en cours
	AutofillCardFrame(inMotion, "Changer le mot de passe", inRequest.name, inOnCancel) {
		val vOpen = vVault																		// coffre affiché
		val vId   = vChosen ?: vOpen?.autofillMatches(inRequest.target)?.singleOrNull()?.id		// compte retenu
		val vStep = ChangeStep(vOpen, if (vPicking) null else vId)								// étape affichée
		CardSteps(vStep, { it.kind }) { vShown ->
			val vShownVault = vShown.vault																// coffre de l'étape
			val vShownId    = vShown.id																	// compte de l'étape
			when
			{
				vShownVault == null -> UnlockPart(inUnlock, "Déverrouillez KeePlus pour changer ce mot de passe.") { vNew -> inOnOpened(vNew); vVault = vNew }
				vShownId == null    -> PickerPart(inRequest, vShownVault, inLinkable = false) { _, vPicked, _ -> vChosen = vPicked; vPicking = false }
				else                -> ChangePart(vShownVault, vShownId, { vPicking = true }, inOnFill, inOnCancel)
			}
		}
	}
}

/*
	Changement pour un compte, dans l'ordre du formulaire : le compte (avec
	« Autre compte » pour en choisir un autre), le mot de passe actuel repris
	de l'entrée (il confirme l'identité auprès de l'appli ou du site ; masqué,
	l'œil le dévoile), puis le nouveau mot de passe généré.
	inVault    : coffre ouvert
	inId       : entrée du compte
	inOnOther  : choisir un autre compte
	inOnFill   : remplir et mettre à jour
	inOnCancel : fermeture sans remplir
*/
@Composable
private fun ChangePart(
	inVault    : Vault,
	inId       : UUID,
	inOnOther  : () -> Unit,
	inOnFill   : (Vault, UUID, String) -> Unit,
	inOnCancel : () -> Unit
)
{
	val vType     = keeType()																	// styles de texte
	val vItem     = remember(inVault, inId) { inVault.items().firstOrNull { it.id == inId } }	// compte
	val vCurrent  = remember(inVault, inId) { inVault.draft(inId)?.password.orEmpty() }		// mot de passe actuel
	var vShown    by remember { mutableStateOf(false) }										// mot de passe actuel dévoilé
	var vOptions  by remember { mutableStateOf(PasswordOptions()) }							// réglages du générateur
	var vPassword by remember { mutableStateOf(generatePassword(vOptions)) }				// nouveau mot de passe
	Row(verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.weight(1f)) { vItem?.let { vAccount -> PickerRow(vAccount) {} } }
		TextLink("Autre compte", inOnOther)
	}
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		BasicText("Mot de passe actuel", style = vType.label)
		Row(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KeeColors.void.copy(alpha = 0.6f)).padding(start = 14.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			val vText = when
			{
				vCurrent.isEmpty() -> "Aucun"
				vShown             -> vCurrent
				else               -> "•".repeat(vCurrent.length.coerceIn(8, 20))
			}																					// texte affiché
			BasicText(if (vShown && vCurrent.isNotEmpty()) secretAnnotated(vText) else AnnotatedString(vText), Modifier.weight(1f).padding(vertical = 8.dp), style = vType.secret)
			if (vCurrent.isNotEmpty())
			{
				IconAction(if (vShown) LineIcons.EyeOff else LineIcons.Eye, if (vShown) "Masquer le mot de passe actuel" else "Afficher le mot de passe actuel", { vShown = !vShown })
			}
		}
	}
	GeneratedPassword(
		inLabel    = "Nouveau mot de passe",
		inValue    = vPassword,
		inLength   = vOptions.length,
		inOnLength = { vOptions = vOptions.copy(length = it); vPassword = generatePassword(vOptions) },
		inOnDraw   = { vPassword = generatePassword(vOptions) }
	)
	SilverButton("Remplir et mettre à jour", { inOnFill(inVault, inId, vPassword) })
	Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
		TextLink("Annuler", inOnCancel)
	}
}

/*
	Enregistrement d'identifiants tapés à la main (après « Enregistrer dans
	KeePlus ? » d'Android) : déverrouille d'abord le coffre si besoin, puis
	propose de créer l'entrée, ou de mettre à jour le mot de passe d'un
	compte déjà connu ; un compte identique est simplement signalé.
	inMotion   : apparition et disparition
	inTarget   : appli ou site
	inName     : nom à montrer
	inUserName : identifiant tapé ; vide s'il n'y en avait pas
	inPassword : mot de passe tapé
	inOpened   : coffre déjà ouvert ; null s'il faut déverrouiller
	inUnlock   : déverrouillage (mot de passe, empreinte, erreurs)
	inOnOpened : coffre déverrouillé dans la carte (à garder)
	inOnSave   : enregistrer dans ce coffre
	inOnCancel : fermeture sans enregistrer
*/
@Composable
fun SaveCard(
	inMotion   : CardMotion,
	inTarget   : AutofillTarget,
	inName     : String,
	inUserName : String,
	inPassword : String,
	inOpened   : Vault?,
	inUnlock   : AppController,
	inOnOpened : (Vault) -> Unit,
	inOnSave   : (Vault) -> Unit,
	inOnCancel : () -> Unit
)
{
	val vType  = keeType()																		// styles de texte
	var vVault by remember { mutableStateOf(inOpened) }											// coffre ouvert ; null avant
	AutofillCardFrame(inMotion, "Enregistrer", inName, inOnCancel) {
		CardSteps(vVault, { it != null }) { vOpen ->
			if (vOpen == null)
			{
				UnlockPart(inUnlock, "Déverrouillez KeePlus pour enregistrer ce compte.") { vNew -> inOnOpened(vNew); vVault = vNew }
				return@CardSteps
			}
			val vState = remember(vOpen) { vOpen.savedLogin(inTarget, inUserName, inPassword) }	// compte connu ou non
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				BasicText("Identifiant", style = vType.label)
				BasicText(inUserName.ifBlank { "(aucun)" }, style = vType.body)
			}
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				BasicText("Mot de passe", style = vType.label)
				BasicText("•".repeat(inPassword.length.coerceIn(8, 20)), style = vType.secret)
			}
			when (vState)
			{
				SavedLogin.New       -> SilverButton("Enregistrer dans le coffre", { inOnSave(vOpen) })
				is SavedLogin.Update ->
				{
					BasicText("Le mot de passe de « ${vState.title} » a changé ; l'ancien restera dans son historique.", style = vType.bodySmall)
					SilverButton("Mettre à jour", { inOnSave(vOpen) })
				}
				is SavedLogin.Same   ->
				{
					BasicText("Ce compte est déjà enregistré tel quel.", style = vType.bodySmall)
					SilverButton("Fermer", inOnCancel)
				}
			}
			if (vState !is SavedLogin.Same)
			{
				Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
					TextLink("Ne pas enregistrer", inOnCancel)
				}
			}
		}
	}
}

/*
	Titres portés par plusieurs comptes d'une liste (sans tenir compte des
	majuscules).
	inItems : comptes
*/
internal fun sharedTitles(inItems: List<VaultItem>): Set<String> =
	inItems.groupBy { it.title.trim().lowercase() }.filterValues { it.size > 1 }.values.flatten().map { it.title }.toSet()

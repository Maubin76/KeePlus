package fr.maubin.keeplus.autofill

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
		val vOpen = vVault																		// coffre affiché
		if (vOpen == null) UnlockPart(inUnlock, "Déverrouillez KeePlus pour créer et enregistrer ce compte.") { vNew -> inOnOpened(vNew); vVault = vNew }
		else ProposalPart(inRequest, vOpen, inOnFill, inOnCancel)
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
		val vOpen = vVault																		// coffre affiché
		if (vOpen == null) UnlockPart(inUnlock, "Déverrouillez KeePlus pour remplir ce formulaire.") { vNew -> inOnOpened(vNew); vVault = vNew }
		else PickerPart(inRequest, vOpen, inOnPick)
	}
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
	var vPassword  by remember { mutableStateOf(generatePassword(PasswordOptions())) }		// mot de passe proposé
	for ((vField, vState) in vFields)
	{
		Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
			BasicText(if (vField.email) "Adresse e-mail" else "Identifiant", style = vType.label)
			KeeTextField(vState, if (vField.email) "vous@exemple.fr" else "Identifiant", inImeAction = ImeAction.Done)
		}
	}
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		BasicText(if (inRequest.newPasswords.size > 1) "Mot de passe (et sa confirmation)" else "Mot de passe", style = vType.label)
		Row(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KeeColors.void.copy(alpha = 0.6f)).padding(start = 14.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			BasicText(secretAnnotated(vPassword), Modifier.weight(1f), style = vType.secret, maxLines = 1)
			IconAction(LineIcons.Refresh, "Nouveau tirage", { vPassword = generatePassword(PasswordOptions()) })
		}
	}
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
	inRequest : ce qu'il faut remplir
	inVault   : coffre ouvert
	inOnPick  : compte choisi (coffre, entrée, lier à l'appli)
*/
@Composable
private fun PickerPart(
	inRequest : AutofillRequest,
	inVault   : Vault,
	inOnPick  : (Vault, UUID, Boolean) -> Unit
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
	SearchField(vQuery, inPlaceholder = "Chercher dans le coffre")
	if (inRequest.target.isApp && (vText.isNotEmpty() || vMatches.isEmpty()))
	{
		FilterPill(if (vRemember) "✓ Proposer ce compte ici la prochaine fois" else "Proposer ce compte ici la prochaine fois", vRemember) { vRemember = !vRemember }
	}
	when
	{
		vItems.isEmpty()                      -> BasicText("Aucun compte ne correspond à « $vText ».", style = vType.bodySmall)
		vText.isEmpty() && vMatches.isEmpty() -> BasicText("Aucun compte lié à « ${inRequest.name} » : choisissez-en un.", style = vType.bodySmall)
	}
	LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
		items(vItems, key = { it.id }) { vItem -> PickerRow(vItem) { inOnPick(inVault, vItem.id, vRemember && vItem !in vMatches) } }
	}
}

/*
	Ligne d'un compte : monogramme, titre, identifiant.
	inItem    : entrée
	inOnClick : choix du compte
*/
@Composable
private fun PickerRow(
	inItem    : VaultItem,
	inOnClick : () -> Unit
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
		Monogram(inItem.title, inSize = 40.dp)
		Spacer(Modifier.width(14.dp))
		Column(Modifier.weight(1f)) {
			OneLine(inItem.title.ifBlank { "Sans titre" }, vType.title)
			if (inItem.userName.isNotBlank()) OneLine(inItem.userName, vType.bodySmall)
		}
	}
}

package fr.maubin.keeplus.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.app.slice.Slice
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.View
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import fr.maubin.keeplus.R
import fr.maubin.keeplus.phoneStore
import fr.maubin.keeplus.vault.VaultItem
import java.util.concurrent.atomic.AtomicInteger

private const val kMaxSuggestions = 5													// entrées proposées au plus
private val kRequestCodes         = AtomicInteger(1)										// codes des demandes, tous différents

/*
	Service d'auto-remplissage de KeePlus. Android lui décrit l'écran qui
	demande un remplissage ; il y repère l'identifiant et le mot de passe,
	puis propose :
	- inscription : « Inscription » (carte qui propose identifiant et mot de passe) ;
	- changement de mot de passe : « Changer » (carte qui propose le nouveau) ;
	- coffre verrouillé : « Déverrouiller » (empreinte, puis choix) ;
	- coffre ouvert : les entrées de cette appli ou de ce site (l'identifiant
	  en avant quand plusieurs portent le même titre), puis
	  « Chercher dans KeePlus ».
	Le bouclier de KeePlus accompagne chaque suggestion.
	Aucune valeur ne quitte KeePlus avant l'appui : chaque suggestion passe
	par AutofillActivity, qui rend l'identifiant et le mot de passe. Rien
	n'est proposé dans KeePlus lui-même, ni avant la création du coffre.
	Identifiants tapés à la main : Android propose de les enregistrer, puis
	la carte « Enregistrer » crée l'entrée ou met son mot de passe à jour.
*/
class KeeAutofillService : AutofillService()
{
	/*
		Demande de remplissage : champs repérés, suggestions selon l'état du
		coffre (dans la liste déroulante et, s'il le permet, dans le clavier).
		inRequest  : demande, avec la structure de l'écran
		inSignal   : annulation
		inCallback : réponse
	*/
	override fun onFillRequest(
		inRequest  : FillRequest,
		inSignal   : CancellationSignal,
		inCallback : FillCallback
	)
	{
		val vStructure = inRequest.fillContexts.lastOrNull()?.structure							// écran à remplir
		val vPackage   = vStructure?.activityComponent?.packageName								// appli qui le montre
		if (vStructure == null || vPackage == null || vPackage == packageName || !phoneStore(this).exists())
		{
			inCallback.onSuccess(null)
			return
		}
		val vFields = formFields(vStructure)													// identifiant et mot de passe
		if (vFields == null)
		{
			inCallback.onSuccess(null)
			return
		}
		val vTarget  = AutofillTarget.of(vPackage, vFields.webDomain)								// appli ou site
		val vRequest = AutofillRequest(
			userName     = vFields.userName,
			password     = vFields.password,
			target       = vTarget,
			name         = vTarget.domain ?: appName(vPackage),
			newPasswords = vFields.newPasswords,
			identities   = vFields.identities
		)																						// ce qu'il faut remplir
		val vSpecs   = inRequest.inlineSuggestionsRequest?.let { vInline ->
			vInline.inlinePresentationSpecs.take(vInline.maxSuggestionCount)
		}.orEmpty()																				// emplacements dans le clavier
		val vVault   = VaultSession.vault()															// coffre ouvert ; null si verrouillé
		val vResponse = FillResponse.Builder()														// réponse
		if (vRequest.signUp)
		{
			vResponse.addDataset(suggestion(vRequest, null, "Inscription", vRequest.name, vSpecs.firstOrNull()))
		}
		else if (vRequest.change)
		{
			vResponse.addDataset(suggestion(vRequest, null, "Changer", vRequest.name, vSpecs.firstOrNull()))
		}
		else if (vVault == null)
		{
			vResponse.addDataset(suggestion(vRequest, null, "Déverrouiller", vRequest.name, vSpecs.firstOrNull()))
		}
		else
		{
			val vMatches = vVault.autofillMatches(vRequest.target).take(kMaxSuggestions)			// entrées de cette cible
			val vShared  = sharedTitles(vMatches)													// titres portés par plusieurs comptes
			vMatches.forEachIndexed { vIndex, vItem ->
				val vUser  = vItem.title in vShared && vItem.userName.isNotBlank()					// identifiant en avant
				val vTitle = vItem.title.ifBlank { "Sans titre" }									// titre
				vResponse.addDataset(suggestion(vRequest, vItem, if (vUser) vItem.userName else vTitle, if (vUser) vTitle else vItem.userName, vSpecs.getOrNull(vIndex)))
			}
			vResponse.addDataset(suggestion(vRequest, null, "Chercher", "dans KeePlus", vSpecs.getOrNull(vMatches.size)))
		}
		saveInfo(vFields)?.let(vResponse::setSaveInfo)
		inCallback.onSuccess(vResponse.build())
	}

	/*
		Enregistrement après une connexion, une inscription ou un changement
		de mot de passe tapés à la main (Android l'a proposé et l'utilisateur
		a accepté) : identifiant et mot de passe sont relus dans l'écran (ou
		dans l'écran précédent pour une connexion en deux temps), puis la
		carte « Enregistrer » s'ouvre (déverrouillage, création ou mise à jour
		de l'entrée).
		inRequest  : demande, avec les écrans remplis
		inCallback : réponse
	*/
	override fun onSaveRequest(
		inRequest  : SaveRequest,
		inCallback : SaveCallback
	)
	{
		val vContexts  = inRequest.fillContexts.map { it.structure }								// écrans, du plus ancien au plus récent
		val vStructure = vContexts.lastOrNull()														// dernier écran
		val vPackage   = vStructure?.activityComponent?.packageName									// appli qui le montre
		val vFields    = vStructure?.let(::formFields)												// champs du dernier écran
		if (vStructure == null || vPackage == null || vPackage == packageName || vFields == null)
		{
			inCallback.onSuccess()
			return
		}
		val vValues   = vContexts.fold(emptyMap<AutofillId, String>()) { vAll, vOne -> vAll + textValues(vOne) }	// valeurs saisies
		val vPassword = (vFields.newPasswords.firstOrNull() ?: vFields.password)?.let { vValues[it] }?.takeIf { it.isNotEmpty() }	// mot de passe retenu
		if (vPassword == null)
		{
			inCallback.onSuccess()
			return
		}
		val vUser     = sequenceOf(vFields.userName, vFields.identities.firstOrNull()?.id)
			.plus(vContexts.dropLast(1).reversed().asSequence().map { formFields(it)?.userName })
			.firstNotNullOfOrNull { vId -> vId?.let { vValues[it] }?.trim()?.takeIf { it.isNotEmpty() } }
			.orEmpty()																				// identifiant, ici ou à l'écran précédent
		val vTarget   = AutofillTarget.of(vPackage, vFields.webDomain)								// appli ou site
		val vIntent   = PendingIntent.getActivity(
			this,
			kRequestCodes.getAndIncrement(),
			AutofillActivity.saveIntent(this, vTarget, vTarget.domain ?: appName(vPackage), vUser, vPassword),
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
		)																						// carte « Enregistrer »
		inCallback.onSuccess(vIntent.intentSender)
	}

	/*
		Champs d'un écran (toutes ses fenêtres) ; null s'il n'y a rien à remplir.
		inStructure : écran
	*/
	private fun formFields(inStructure: AssistStructure): FormFields<AutofillId>? =
		findFormFields(FormNode(children = List(inStructure.windowNodeCount) { toFormNode(inStructure.getWindowNodeAt(it).rootViewNode) }))

	/*
		Enregistrement à proposer quand l'utilisateur tape lui-même ses
		identifiants : Android demande « Enregistrer dans KeePlus ? » quand le
		mot de passe (nouveau, sinon actuel) a été saisi et que l'écran se
		ferme ou que ses champs disparaissent (pages web). Rien n'est proposé
		pour des valeurs remplies par KeePlus et laissées telles quelles.
		inFields : champs de l'écran
	*/
	private fun saveInfo(inFields: FormFields<AutofillId>): SaveInfo?
	{
		val vRequired = inFields.newPasswords.firstOrNull() ?: inFields.password ?: return null	// champ indispensable
		val vOptional = (listOfNotNull(inFields.userName, inFields.password) + inFields.identities.map { it.id } + inFields.newPasswords)
			.distinct().filter { it != vRequired }													// autres champs relus
		return SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD, arrayOf(vRequired))
			.apply { if (vOptional.isNotEmpty()) setOptionalIds(vOptional.toTypedArray()) }
			.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
			.build()
	}

	/*
		Nom de l'appli qui demande, tel qu'Android l'affiche (applis de l'écran
		d'accueil, visibles grâce aux <queries> du manifeste), sinon son nom de
		paquet.
		inPackage : appli
	*/
	private fun appName(inPackage: String): String =
		runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(inPackage, 0)).toString() }.getOrNull()?.takeIf { it.isNotBlank() }
			?: inPackage

	/*
		Suggestion dont les valeurs sont données par AutofillActivity au
		moment de l'appui.
		inRequest : ce qu'il faut remplir
		inItem    : entrée proposée ; null pour déverrouiller ou chercher
		inTitle   : titre affiché
		inDetail  : détail affiché (identifiant, site ou appli)
		inSpec    : emplacement dans le clavier ; null sans
	*/
	@Suppress("DEPRECATION")
	private fun suggestion(
		inRequest : AutofillRequest,
		inItem    : VaultItem?,
		inTitle   : String,
		inDetail  : String,
		inSpec    : InlinePresentationSpec?
	): Dataset
	{
		val vIntent  = PendingIntent.getActivity(
			this,
			kRequestCodes.getAndIncrement(),
			AutofillActivity.intent(this, inRequest, inItem?.id),
			PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
		)																						// ouverture d'AutofillActivity
		val vViews   = presentation(this, inTitle, inDetail)										// ligne de la liste déroulante
		val vInline  = inSpec?.let { inlinePresentation(this, it, inTitle, inDetail) }				// pastille du clavier
		val vBuilder = Dataset.Builder(vViews)														// suggestion
		for (vId in inRequest.ids)
		{
			if (vInline != null) vBuilder.setValue(vId, null, vViews, vInline)
			else vBuilder.setValue(vId, null, vViews)
		}
		return vBuilder.setAuthentication(vIntent.intentSender).build()
	}
}

/*
	Ligne de la liste déroulante d'Android : logo, titre, détail.
	inContext : contexte
	inTitle   : titre
	inDetail  : détail
*/
internal fun presentation(
	inContext : Context,
	inTitle   : String,
	inDetail  : String
): RemoteViews =
	RemoteViews(inContext.packageName, R.layout.autofill_item).apply {
		setTextViewText(R.id.autofill_title, inTitle)
		setTextViewText(R.id.autofill_detail, inDetail)
		setViewVisibility(R.id.autofill_detail, if (inDetail.isBlank()) View.GONE else View.VISIBLE)
	}

/*
	Pastille de suggestion dans le clavier (Android 11 et plus) ; null si le
	clavier attend un style que KeePlus ne connaît pas.
	inContext : contexte
	inSpec    : emplacement donné par le clavier
	inTitle   : titre
	inDetail  : détail
*/
private fun inlinePresentation(
	inContext : Context,
	inSpec    : InlinePresentationSpec,
	inTitle   : String,
	inDetail  : String
): InlinePresentation?
{
	if (!UiVersions.getVersions(inSpec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null
	val vAttribution = PendingIntent.getActivity(
		inContext,
		kRequestCodes.getAndIncrement(),
		Intent(inContext, fr.maubin.keeplus.MainActivity::class.java),
		PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
	)																							// appui long : ouvre KeePlus
	val vContent = InlineSuggestionUi.newContentBuilder(vAttribution)
		.setTitle(inTitle)
		.apply { if (inDetail.isNotBlank()) setSubtitle(inDetail) }
		.setStartIcon(Icon.createWithResource(inContext, R.drawable.ic_launcher_monochrome))
		.setContentDescription("$inTitle, KeePlus")
		.build()																				// contenu de la pastille
	val vSlice: Slice = vContent.slice																// contenu pour Android
	return InlinePresentation(vSlice, inSpec, false)
}

/*
	Textes saisis dans les champs d'un écran, par champ.
	inStructure : écran
*/
private fun textValues(inStructure: AssistStructure): Map<AutofillId, String>
{
	val vResult = mutableMapOf<AutofillId, String>()											// valeurs trouvées
	fun walk(inNode: AssistStructure.ViewNode)
	{
		val vId    = inNode.autofillId																// champ
		val vValue = inNode.autofillValue?.takeIf { it.isText }?.textValue?.toString()			// texte saisi
		if (vId != null && vValue != null) vResult[vId] = vValue
		for (vIndex in 0 until inNode.childCount) walk(inNode.getChildAt(vIndex))
	}
	for (vIndex in 0 until inStructure.windowNodeCount) walk(inStructure.getWindowNodeAt(vIndex).rootViewNode)
	return vResult
}

/*
	Champ de l'écran pour la reconnaissance des champs de connexion.
	inNode : nœud de la structure transmise par Android
*/
private fun toFormNode(inNode: AssistStructure.ViewNode): FormNode<AutofillId>
{
	val vHtml = inNode.htmlInfo																	// élément HTML ; null hors page web
	val vText = inNode.autofillType == View.AUTOFILL_TYPE_TEXT && inNode.isEnabled &&
		(vHtml == null || vHtml.tag.equals("input", ignoreCase = true))						// saisie de texte
	return FormNode(
		id        = inNode.autofillId,
		text      = vText,
		visible   = inNode.visibility == View.VISIBLE,
		hints     = inNode.autofillHints?.toList().orEmpty(),
		inputType = inNode.inputType,
		html      = vHtml?.attributes?.mapNotNull { vPair -> vPair.first?.let { vKey -> vPair.second?.let { vKey.lowercase() to it } } }?.toMap().orEmpty(),
		idEntry   = inNode.idEntry,
		hint      = inNode.hint,
		webDomain = inNode.webDomain,
		value     = inNode.text?.toString()?.takeIf { inNode.idEntry in kUrlBars },
		children  = List(inNode.childCount) { toFormNode(inNode.getChildAt(it)) }
	)
}

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
	- coffre verrouillé : « Déverrouiller » (empreinte, puis choix) ;
	- coffre ouvert : les entrées de cette appli ou de ce site, puis
	  « Chercher dans KeePlus ».
	Le bouclier de KeePlus accompagne chaque suggestion.
	Aucune valeur ne quitte KeePlus avant l'appui : chaque suggestion passe
	par AutofillActivity, qui rend l'identifiant et le mot de passe. Rien
	n'est proposé dans KeePlus lui-même, ni avant la création du coffre.
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
		val vRoot   = FormNode(children = List(vStructure.windowNodeCount) { toFormNode(vStructure.getWindowNodeAt(it).rootViewNode) })	// champs de l'écran
		val vFields = findFormFields(vRoot)													// identifiant et mot de passe
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
		else if (vVault == null)
		{
			vResponse.addDataset(suggestion(vRequest, null, "Déverrouiller", vRequest.name, vSpecs.firstOrNull()))
		}
		else
		{
			val vMatches = vVault.autofillMatches(vRequest.target).take(kMaxSuggestions)			// entrées de cette cible
			vMatches.forEachIndexed { vIndex, vItem ->
				vResponse.addDataset(suggestion(vRequest, vItem, vItem.title.ifBlank { "Sans titre" }, vItem.userName, vSpecs.getOrNull(vIndex)))
			}
			vResponse.addDataset(suggestion(vRequest, null, "Chercher", "dans KeePlus", vSpecs.getOrNull(vMatches.size)))
		}
		inCallback.onSuccess(vResponse.build())
	}

	/*
		Enregistrement après une connexion : pas encore pris en charge (aucun
		enregistrement n'est demandé dans la réponse).
		inRequest  : demande
		inCallback : réponse
	*/
	override fun onSaveRequest(
		inRequest  : SaveRequest,
		inCallback : SaveCallback
	)
	{
		inCallback.onSuccess()
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

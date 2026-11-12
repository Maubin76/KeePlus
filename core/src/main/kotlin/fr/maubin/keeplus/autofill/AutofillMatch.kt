package fr.maubin.keeplus.autofill

import app.keemobile.kotpass.constants.BasicField
import fr.maubin.keeplus.vault.CustomField
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.VaultItem
import fr.maubin.keeplus.vault.placedEntries
import java.util.Locale

const val kAppScheme        = "androidapp://"										// adresse d'une appli Android (convention KeePass)
private const val kAppField = "AndroidApp"											// champ libre d'une appli (convention KeePassDX)
private const val kUrlField = "KP2A_URL"											// adresses supplémentaires (convention Keepass2Android)

/*
	Navigateurs connus : seul un navigateur peut annoncer le domaine de la
	page affichée. Le domaine annoncé par toute autre appli est ignoré (une
	appli piégée pourrait prétendre afficher le site d'une banque).
*/
val kBrowsers = setOf(
	"com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary", "org.chromium.chrome",
	"org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus", "org.mozilla.klar",
	"org.mozilla.fennec_fdroid", "io.github.forkmaintainers.iceraven", "org.torproject.torbrowser",
	"com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta", "com.microsoft.emmx",
	"com.brave.browser", "com.brave.browser_beta", "com.opera.browser", "com.opera.mini.native", "com.vivaldi.browser",
	"com.duckduckgo.mobile.android", "com.kiwibrowser.browser", "com.ecosia.android", "app.vanadium.browser",
	"com.qwant.liberty", "com.yandex.browser", "com.mi.globalbrowser", "com.huawei.browser"
)

/*
	Ce qui demande un remplissage : une appli, ou une page web dans un
	navigateur connu.
	packageName : appli qui affiche le formulaire
	domain      : domaine de la page, en minuscules et sans « www. » ; null
	              hors navigateur ou si le navigateur ne l'a pas donné
*/
class AutofillTarget private constructor(
	val packageName : String,
	val domain      : String?
)
{
	/*
		Vrai pour une appli (pas un navigateur) : le lien avec une entrée peut
		être retenu.
	*/
	val isApp: Boolean get() = packageName !in kBrowsers

	/*
		Nom à montrer : le domaine de la page, sinon le nom de l'appli.
	*/
	val label: String get() = domain ?: packageName

	companion object
	{
		/*
			Cible d'une demande : le domaine n'est retenu que s'il vient d'un
			navigateur connu.
			inPackage : appli qui affiche le formulaire
			inDomain  : domaine annoncé ; null s'il n'y en a pas
		*/
		fun of(
			inPackage : String,
			inDomain  : String?
		): AutofillTarget = AutofillTarget(inPackage, inDomain?.takeIf { inPackage in kBrowsers }?.let(::hostOf))

		/*
			Cible d'un site dont le domaine est déjà sûr (lu dans la barre
			d'adresse d'un navigateur, sur PC).
			inDomain : domaine ou adresse du site
		*/
		fun web(inDomain: String): AutofillTarget = AutofillTarget("web", hostOf(inDomain))
	}
}

/*
	Entrées du coffre (hors corbeille) proposées pour une cible, triées par
	titre : pour une page web, celles dont une adresse a le même domaine ou
	un domaine parent (« google.com » pour « accounts.google.com ») ; pour
	une appli, celles qui lui sont liées (androidapp://…).
	inTarget : cible
*/
fun Vault.autofillMatches(inTarget: AutofillTarget): List<VaultItem>
{
	val vIds = root.placedEntries(trashId)
		.filter { (_, vEntry) ->
			val vLinks = entryLinks(vEntry.fields.entries.associate { it.key to it.value.content })	// adresses et applis de l'entrée
			if (inTarget.domain != null) vLinks.hosts.any { inTarget.domain == it || inTarget.domain.endsWith(".$it") }
			else inTarget.isApp && inTarget.packageName in vLinks.apps
		}
		.map { it.second.uuid }
		.toSet()																			// entrées retenues
	if (vIds.isEmpty()) return emptyList()
	return items().filter { it.id in vIds }
}

/*
	Lie une appli à une entrée, pour la proposer d'emblée la prochaine fois :
	l'adresse de l'entrée devient androidapp://… si elle est vide, sinon un
	champ libre AndroidApp est ajouté. Rien ne change si le lien existe déjà.
	inId      : entrée
	inPackage : appli
*/
fun Vault.rememberApp(
	inId      : java.util.UUID,
	inPackage : String
): Vault
{
	val vDraft = draft(inId) ?: return this												// entrée
	val vLinks = entryLinks(mapOf(BasicField.Url() to vDraft.url) + vDraft.fields.associate { it.name to it.value })	// liens actuels
	if (inPackage in vLinks.apps) return this
	if (vDraft.url.isBlank()) return save(vDraft.copy(url = "$kAppScheme$inPackage"))
	val vNames = vDraft.fields.map { it.name }.toSet()									// champs existants
	val vName  = (sequenceOf(kAppField) + generateSequence(2) { it + 1 }.map { "${kAppField}_$it" }).first { it !in vNames }	// nom libre
	return save(vDraft.copy(fields = vDraft.fields + CustomField(vName, inPackage)))
}

/*
	Adresses d'une entrée.
	hosts : domaines des sites
	apps  : applis Android
*/
private class EntryLinks(
	val hosts : Set<String>,
	val apps  : Set<String>
)

/*
	Domaines et applis d'une entrée, d'après son adresse, ses adresses
	supplémentaires (KP2A_URL…) et ses champs AndroidApp.
	inFields : champs de l'entrée (nom → valeur)
*/
private fun entryLinks(inFields: Map<String, String>): EntryLinks
{
	val vHosts = mutableSetOf<String>()													// domaines
	val vApps  = mutableSetOf<String>()													// applis
	for ((vName, vValue) in inFields)
	{
		val vText = vValue.trim()															// valeur
		if (vText.isEmpty()) continue
		when
		{
			vName == kAppField || vName.startsWith("${kAppField}_")       -> vApps += vText.removePrefix(kAppScheme)
			vName != BasicField.Url() && !vName.startsWith(kUrlField)     -> {}
			vText.startsWith(kAppScheme, ignoreCase = true)              -> vApps += vText.substring(kAppScheme.length)
			else                                                         -> hostOf(vText)?.let { vHosts += it }
		}
	}
	return EntryLinks(vHosts, vApps)
}

/*
	Domaine d'une adresse (avec ou sans « https:// »), en minuscules, sans
	« www. » ni port ; null si ce n'est pas un domaine.
	inUrl : adresse
*/
internal fun hostOf(inUrl: String): String?
{
	val vRest = inUrl.trim().substringAfter("://", inUrl.trim())							// sans le protocole
	val vHost = vRest.substringBefore('/').substringBefore('?').substringBefore('#')
		.substringAfterLast('@').substringBefore(':')
		.lowercase(Locale.ROOT).trimEnd('.').removePrefix("www.")							// domaine
	return vHost.takeIf { '.' in it && it.none { vChar -> vChar.isWhitespace() } }
}

/*
	Titres portés par plusieurs comptes d'une liste (sans tenir compte des
	majuscules) : pour ces comptes, l'identifiant passe en avant.
	inItems : comptes
*/
fun sharedTitles(inItems: List<VaultItem>): Set<String> =
	inItems.groupBy { it.title.trim().lowercase() }.filterValues { it.size > 1 }.values.flatten().map { it.title }.toSet()

package fr.maubin.keeplus.autofill

import fr.maubin.keeplus.vault.CustomField
import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.VaultItem
import fr.maubin.keeplus.vault.placedEntries
import java.util.Locale
import java.util.UUID

private const val kWindowsField = "WindowsApp"										// champ libre d'une appli Windows (programme .exe)
private const val kMinTitle     = 3													// titre d'entrée assez long pour être cherché dans une fenêtre

/*
	Navigateurs de Windows (programme) : leur fenêtre affiche un site, dont
	le domaine est lu dans la barre d'adresse ; une appli Windows est
	reconnue par son programme.
*/
val kDesktopBrowsers = setOf(
	"chrome.exe", "msedge.exe", "brave.exe", "firefox.exe", "opera.exe", "vivaldi.exe", "librewolf.exe",
	"waterfox.exe", "chromium.exe", "arc.exe", "zen.exe", "floorp.exe", "duckduckgo.exe", "thorium.exe"
)

/*
	Fenêtre active sous Windows, telle que la carte de saisie la voit.
	exe    : programme (nom du fichier, en minuscules), par exemple brave.exe
	title  : titre de la fenêtre
	domain : domaine du site affiché (navigateur), en minuscules et sans
	         « www. » ; null hors navigateur ou s'il n'a pas pu être lu
*/
data class DesktopTarget(
	val exe    : String,
	val title  : String,
	val domain : String? = null
)
{
	/*
		Vrai pour un navigateur.
	*/
	val isBrowser: Boolean get() = exe in kDesktopBrowsers

	/*
		Nom à montrer : le domaine, sinon le titre de la fenêtre (sans le nom
		du navigateur), sinon le programme.
	*/
	val label: String
		get() = domain ?: title.substringBeforeLast(" - ").substringBeforeLast(" — ").trim().ifBlank { exe.removeSuffix(".exe") }

	/*
		Même cible avec le domaine lu dans la barre d'adresse.
		inUrl : texte de la barre d'adresse
	*/
	fun withUrl(inUrl: String?): DesktopTarget = copy(domain = inUrl?.let(::hostOf))
}

/*
	Comptes proposés pour la fenêtre active, triés par titre :
	- un site (navigateur, domaine lu) : les entrées du même domaine ou d'un
	  domaine parent, comme sur Android ;
	- une appli Windows : les entrées qui lui sont liées (champ WindowsApp),
	  sinon celles dont le titre apparaît dans le titre de la fenêtre ;
	- un navigateur dont le domaine n'a pas pu être lu : titre de la fenêtre.
	inTarget : fenêtre active
*/
fun Vault.desktopMatches(inTarget: DesktopTarget): List<VaultItem>
{
	if (inTarget.domain != null) return autofillMatches(AutofillTarget.web(inTarget.domain))
	if (!inTarget.isBrowser)
	{
		val vLinked = root.placedEntries(trashId)
			.filter { (_, vEntry) -> vEntry.fields.entries.any { (vName, vValue) -> isWindowsField(vName) && vValue.content.trim().equals(inTarget.exe, ignoreCase = true) } }
			.map { it.second.uuid }
			.toSet()																		// entrées liées au programme
		if (vLinked.isNotEmpty()) return items().filter { it.id in vLinked }
	}
	val vTitle = inTarget.title.lowercase(Locale.ROOT)										// titre de la fenêtre
	if (vTitle.isBlank()) return emptyList()
	return items().filter { vItem ->
		val vName = vItem.title.trim().lowercase(Locale.ROOT)								// titre de l'entrée
		vName.length >= kMinTitle && Regex("(^|\\W)${Regex.escape(vName)}(\\W|$)").containsMatchIn(vTitle)
	}
}

/*
	Lie une appli Windows à une entrée (champ libre WindowsApp), pour la
	proposer d'emblée la prochaine fois. Rien pour un navigateur (le domaine
	suffit) ni si le lien existe déjà.
	inId     : entrée
	inTarget : fenêtre active
*/
fun Vault.rememberWindowsApp(
	inId     : UUID,
	inTarget : DesktopTarget
): Vault
{
	if (inTarget.isBrowser || inTarget.exe.isBlank()) return this
	val vDraft = draft(inId) ?: return this												// entrée
	if (vDraft.fields.any { isWindowsField(it.name) && it.value.equals(inTarget.exe, ignoreCase = true) }) return this
	val vNames = vDraft.fields.map { it.name }.toSet()									// champs existants
	val vName  = (sequenceOf(kWindowsField) + generateSequence(2) { it + 1 }.map { "${kWindowsField}_$it" }).first { it !in vNames }	// nom libre
	return save(vDraft.copy(fields = vDraft.fields + CustomField(vName, inTarget.exe)))
}

/*
	Ajoute au coffre le compte créé depuis la carte de saisie : titre (nom
	du site ou de l'appli), identifiant, mot de passe, et l'adresse du site
	ou le lien avec l'appli Windows.
	inTarget   : fenêtre active
	inUserName : identifiant
	inPassword : mot de passe
	inId       : identifiant de la nouvelle entrée
*/
fun Vault.addDesktopSignUp(
	inTarget   : DesktopTarget,
	inUserName : String,
	inPassword : String,
	inId       : UUID = UUID.randomUUID()
): Vault
{
	val vWeb   = inTarget.domain															// domaine du site
	val vEntry = EntryDraft(
		id       = inId,
		title    = inTarget.label,
		userName = inUserName.trim(),
		password = inPassword,
		url      = vWeb?.let { "https://$it" }.orEmpty(),
		fields   = if (vWeb == null && !inTarget.isBrowser && inTarget.exe.isNotBlank()) listOf(CustomField(kWindowsField, inTarget.exe)) else emptyList()
	)																						// nouvelle entrée
	return save(vEntry)
}

/*
	Vrai pour le nom d'un champ de lien avec une appli Windows.
	inName : nom du champ
*/
private fun isWindowsField(inName: String): Boolean = inName == kWindowsField || inName.startsWith("${kWindowsField}_")

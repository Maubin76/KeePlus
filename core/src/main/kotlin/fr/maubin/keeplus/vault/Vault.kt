package fr.maubin.keeplus.vault

import app.keemobile.kotpass.constants.BasicField
import app.keemobile.kotpass.constants.GroupOverride
import app.keemobile.kotpass.constants.PredefinedIcon
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.modifiers.modifyContent
import app.keemobile.kotpass.database.modifiers.modifyCredentials
import app.keemobile.kotpass.models.DeletedObject
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.TimeData
import java.text.Collator
import java.text.Normalizer
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

private const val kTrashName = "Corbeille"										// nom de la corbeille créée par KeePlus
private val kZeroId          = UUID(0L, 0L)										// identifiant « aucun » de KeePass
private val kMarks           = Regex("\\p{M}+")									// accents, après décomposition
private val kBasicKeys       = BasicField.keys									// champs standard d'une entrée

/*
	Groupe du coffre, pour l'affichage (sans son contenu).
	id       : identifiant
	name     : nom
	parentId : groupe parent ; le groupe racine pour un groupe de premier niveau
	depth    : profondeur (0 pour un groupe de premier niveau)
*/
data class VaultGroup(
	val id       : UUID,
	val name     : String,
	val parentId : UUID,
	val depth    : Int
)

/*
	Entrée du coffre, pour les listes : sans mot de passe ni notes.
	id       : identifiant
	groupId  : groupe qui la contient
	title    : titre
	userName : identifiant de connexion
	url      : adresse du site
	modified : dernière modification ; null si inconnue
*/
data class VaultItem(
	val id       : UUID,
	val groupId  : UUID,
	val title    : String,
	val userName : String,
	val url      : String,
	val modified : Instant?
)

/*
	Champ libre d'une entrée.
	name   : nom du champ
	value  : valeur
	secret : valeur masquée à l'écran et protégée en mémoire
*/
data class CustomField(
	val name   : String,
	val value  : String,
	val secret : Boolean = false
)

/*
	Contenu complet d'une entrée, pour la fiche, l'édition et l'historique.
	id       : identifiant ; null pour une nouvelle entrée
	groupId  : groupe ; null pour le groupe racine
	title    : titre
	userName : identifiant de connexion
	password : mot de passe
	url      : adresse du site
	notes    : notes
	fields   : champs libres, dans l'ordre
	modified : dernière modification ; null pour une nouvelle entrée
*/
data class EntryDraft(
	val id       : UUID?             = null,
	val groupId  : UUID?             = null,
	val title    : String            = "",
	val userName : String            = "",
	val password : String            = "",
	val url      : String            = "",
	val notes    : String            = "",
	val fields   : List<CustomField> = emptyList(),
	val modified : Instant?          = null
)

/*
	Coffre ouvert. Immuable : chaque modification renvoie un nouveau Vault.
	Les dates sont arrondies à la seconde, la précision du format KDBX 4.
	database : coffre KeePass
	fClock   : horloge des dates de modification
*/
class Vault(
	val database       : KeePassDatabase,
	private val fClock : Clock = Clock.systemUTC()
)
{
	private val fCollator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }	// tri des titres

	/*
		Groupe racine du coffre.
	*/
	val root: Group get() = database.content.group

	/*
		Nom du coffre.
	*/
	val name: String get() = database.content.meta.name.ifBlank { root.name }

	/*
		Identifiant de la corbeille ; null si le coffre n'en a pas encore.
	*/
	val trashId: UUID?
		get() = database.content.meta.recycleBinUuid
			?.takeIf { it != kZeroId && database.content.meta.recycleBinEnabled }
			?.takeIf { root.findGroup(it) != null }

	/*
		Groupes du coffre, en profondeur d'abord, sans la racine ni la corbeille.
	*/
	fun groups(): List<VaultGroup>
	{
		val vTrash  = trashId																// corbeille, exclue
		val vResult = mutableListOf<VaultGroup>()											// groupes trouvés
		fun walk(inGroup: Group, inDepth: Int)
		{
			for (vChild in inGroup.groups)
			{
				if (vChild.uuid == vTrash) continue
				vResult += VaultGroup(vChild.uuid, vChild.name, inGroup.uuid, inDepth)
				walk(vChild, inDepth + 1)
			}
		}
		walk(root, 0)
		return vResult
	}

	/*
		Entrées hors corbeille, triées par titre.
		inGroup : groupe à lister, sous-groupes compris ; null pour tout le coffre
	*/
	fun items(inGroup: UUID? = null): List<VaultItem> =
		placed(inGroup).map { (vGroup, vEntry) -> vEntry.toItem(vGroup) }.sortedWith(compareBy(fCollator) { it.title })

	/*
		Entrées hors corbeille dont le titre, l'identifiant, l'adresse, les notes,
		les étiquettes ou un champ libre non secret contiennent tous les mots
		cherchés, sans tenir compte des majuscules ni des accents.
		inQuery : texte cherché
		inGroup : groupe où chercher ; null pour tout le coffre
	*/
	fun search(
		inQuery : String,
		inGroup : UUID? = null
	): List<VaultItem>
	{
		val vWords = normalized(inQuery).split(' ').filter { it.isNotBlank() }			// mots cherchés
		if (vWords.isEmpty()) return items(inGroup)
		return placed(inGroup)
			.filter { (_, vEntry) -> searchText(vEntry).let { vText -> vWords.all { it in vText } } }
			.map { (vGroup, vEntry) -> vEntry.toItem(vGroup) }
			.sortedWith(compareBy(fCollator) { it.title })
	}

	/*
		Entrées de la corbeille, triées par titre, filtrées comme search.
		inQuery : texte cherché ; vide pour toute la corbeille
	*/
	fun trashItems(inQuery: String = ""): List<VaultItem>
	{
		val vTrash = trashId?.let { root.findGroup(it) } ?: return emptyList()		// corbeille
		val vWords = normalized(inQuery).split(' ').filter { it.isNotBlank() }			// mots cherchés
		return vTrash.placedEntries()
			.filter { (_, vEntry) -> vWords.isEmpty() || searchText(vEntry).let { vText -> vWords.all { it in vText } } }
			.map { (vGroup, vEntry) -> vEntry.toItem(vGroup) }
			.sortedWith(compareBy(fCollator) { it.title })
	}

	/*
		Nom d'un groupe ; null s'il n'existe pas.
		inId : identifiant du groupe
	*/
	fun groupName(inId: UUID): String? = root.findGroup(inId)?.name

	/*
		Contenu complet d'une entrée ; null si elle n'existe pas.
		inId : identifiant de l'entrée
	*/
	fun draft(inId: UUID): EntryDraft? = root.findEntry(inId)?.let { (vGroup, vEntry) -> vEntry.toDraft(vGroup.uuid) }

	/*
		Anciennes versions d'une entrée, de la plus récente à la plus ancienne.
		inId : identifiant de l'entrée
	*/
	fun history(inId: UUID): List<EntryDraft>
	{
		val (vGroup, vEntry) = root.findEntry(inId) ?: return emptyList()				// entrée et son groupe
		return vEntry.history.map { it.toDraft(vGroup.uuid) }.sortedByDescending { it.modified ?: Instant.MIN }
	}

	/*
		Crée ou modifie une entrée. Une entrée modifiée garde sa version
		précédente dans son historique (limité au nombre de versions du coffre).
		Une entrée sans changement n'est pas touchée.
		inDraft : contenu de l'entrée ; id null pour une nouvelle entrée
	*/
	fun save(inDraft: EntryDraft): Vault
	{
		val vNow    = now()																// date de la modification
		val vFields = inDraft.toFields()														// champs de l'entrée
		val vGroup  = inDraft.groupId?.takeIf { root.findGroup(it) != null } ?: root.uuid	// groupe voulu
		val vFound  = inDraft.id?.let { root.findEntry(it) }								// entrée existante
		if (vFound == null)
		{
			val vEntry = Entry(
				uuid   = inDraft.id ?: UUID.randomUUID(),
				times  = TimeData.create(vNow),
				fields = vFields
			)																			// nouvelle entrée
			return withRoot(root.mapGroup(vGroup) { copy(entries = entries + vEntry) })
		}
		val (vParent, vOld) = vFound													// groupe et version actuelle
		val vSame = fieldsSignature(vOld.fields) == fieldsSignature(vFields)				// contenu inchangé
		if (vSame && vParent.uuid == vGroup) return this
		var vNew = vOld																	// nouvelle version
		if (!vSame)
		{
			vNew = vNew.copy(
				fields  = vFields,
				history = (vOld.history + vOld.copy(history = emptyList())).takeLast(historyLimit()),
				times   = (vOld.times ?: TimeData.create(vNow)).copy(lastModificationTime = vNow, lastAccessTime = vNow)
			)
		}
		if (vParent.uuid == vGroup) return withRoot(root.mapGroup(vGroup) { copy(entries = entries.map { if (it.uuid == vOld.uuid) vNew else it }) })
		vNew = vNew.copy(
			times               = (vNew.times ?: TimeData.create(vNow)).copy(locationChanged = vNow),
			previousParentGroup = vParent.uuid
		)
		return withRoot(root.removeEntry(vOld.uuid).mapGroup(vGroup) { copy(entries = entries + vNew) })
	}

	/*
		Met une entrée à la corbeille (créée au besoin). Une entrée déjà dans la
		corbeille est supprimée pour de bon et notée comme supprimée, pour que la
		suppression se propage aux autres appareils.
		inId : identifiant de l'entrée
	*/
	fun trash(inId: UUID): Vault
	{
		val (vParent, vEntry) = root.findEntry(inId) ?: return this						// entrée et son groupe
		if (isInTrash(vParent.uuid))
		{
			return Vault(
				database.modifyContent {
					copy(
						group          = group.removeEntry(inId),
						deletedObjects = deletedObjects + DeletedObject(inId, now())
					)
				},
				fClock
			)
		}
		val vWithTrash = withTrash()															// coffre avec corbeille
		return vWithTrash.moveEntry(vEntry, vParent.uuid, vWithTrash.trashId!!)
	}

	/*
		Sort une entrée de la corbeille et la remet dans son groupe d'origine
		(ou dans la racine si ce groupe n'existe plus).
		inId : identifiant de l'entrée
	*/
	fun restore(inId: UUID): Vault
	{
		val (vParent, vEntry) = root.findEntry(inId) ?: return this						// entrée et son groupe
		if (!isInTrash(vParent.uuid)) return this
		val vTarget = vEntry.previousParentGroup
			?.takeIf { root.findGroup(it) != null && !isInTrash(it) }
			?: root.uuid																// groupe où la remettre
		return moveEntry(vEntry, vParent.uuid, vTarget)
	}

	/*
		Ajoute un groupe.
		inName   : nom du groupe
		inParent : groupe parent ; null pour la racine
		inId     : identifiant du nouveau groupe
	*/
	fun addGroup(
		inName   : String,
		inParent : UUID? = null,
		inId     : UUID  = UUID.randomUUID()
	): Vault
	{
		val vGroup = Group(uuid = inId, name = inName, times = TimeData.create(now()))	// nouveau groupe
		return withRoot(root.mapGroup(inParent?.takeIf { root.findGroup(it) != null } ?: root.uuid) { copy(groups = groups + vGroup) })
	}

	/*
		Renomme un groupe.
		inId   : identifiant du groupe
		inName : nouveau nom
	*/
	fun renameGroup(
		inId   : UUID,
		inName : String
	): Vault
	{
		val vNow = now()																	// date de la modification
		return withRoot(root.mapGroup(inId) { copy(name = inName, times = (times ?: TimeData.create(vNow)).copy(lastModificationTime = vNow)) })
	}

	/*
		Met un groupe, avec tout son contenu, à la corbeille.
		inId : identifiant du groupe
	*/
	fun trashGroup(inId: UUID): Vault
	{
		if (inId == root.uuid || inId == trashId || root.findGroup(inId) == null) return this
		val vWithTrash = withTrash()															// coffre avec corbeille
		val vTrash     = vWithTrash.trashId!!													// corbeille
		val vParent    = vWithTrash.root.findParentOf(inId) ?: return this					// parent actuel
		val vNow       = now()																// date du déplacement
		val vGroup     = vWithTrash.root.findGroup(inId)!!.let {
			it.copy(times = (it.times ?: TimeData.create(vNow)).copy(locationChanged = vNow), previousParentGroup = vParent.uuid)
		}																					// groupe déplacé
		return vWithTrash.withRoot(vWithTrash.root.removeGroup(inId).mapGroup(vTrash) { copy(groups = groups + vGroup) })
	}

	/*
		Vide la corbeille : son contenu (groupes et entrées) est supprimé pour
		de bon et noté comme supprimé, pour que la suppression se propage aux
		autres appareils.
	*/
	fun emptyTrash(): Vault
	{
		val vTrash = trashId?.let { root.findGroup(it) } ?: return this					// corbeille
		val vNow   = now()																	// date de la suppression
		val vGone  = mutableListOf<DeletedObject>()											// éléments supprimés
		fun collect(inGroup: Group)
		{
			inGroup.entries.forEach { vGone += DeletedObject(it.uuid, vNow) }
			inGroup.groups.forEach { vGone += DeletedObject(it.uuid, vNow); collect(it) }
		}
		collect(vTrash)
		if (vGone.isEmpty()) return this
		return Vault(
			database.modifyContent {
				copy(
					group          = group.mapGroup(vTrash.uuid) { copy(groups = emptyList(), entries = emptyList()) },
					deletedObjects = deletedObjects + vGone
				)
			},
			fClock
		)
	}

	/*
		Vrai si ce mot de passe est le mot de passe maître du coffre.
		inPassword : mot de passe à vérifier
	*/
	fun isMasterPassword(inPassword: CharSequence): Boolean =
		database.credentials.passphrase?.getBinary()?.contentEquals(vaultCredentials(inPassword).passphrase?.getBinary()) == true

	/*
		Change le mot de passe maître : le coffre sera chiffré avec la nouvelle
		clé au prochain enregistrement.
		inPassword : nouveau mot de passe maître
	*/
	fun changeMasterPassword(inPassword: CharSequence): Vault =
		Vault(database.modifyCredentials { vaultCredentials(inPassword) }.modifyContent { copy(meta = meta.copy(masterKeyChanged = now())) }, fClock)

	/*
		Vrai si le groupe est la corbeille ou se trouve dedans.
		inGroup : identifiant du groupe
	*/
	fun isInTrash(inGroup: UUID): Boolean
	{
		val vTrash = trashId?.let { root.findGroup(it) } ?: return false				// corbeille
		return vTrash.findGroup(inGroup) != null
	}

	/*
		Coffre avec une corbeille : le coffre lui-même s'il en a une, sinon une
		copie avec une corbeille neuve dans la racine.
	*/
	private fun withTrash(): Vault
	{
		if (trashId != null) return this
		val vNow   = now()																	// date de création
		val vTrash = Group(
			uuid            = UUID.randomUUID(),
			name            = kTrashName,
			icon            = PredefinedIcon.TrashBin,
			times           = TimeData.create(vNow),
			enableAutoType  = GroupOverride.Disabled,
			enableSearching = GroupOverride.Disabled
		)																					// corbeille
		return Vault(
			database.modifyContent {
				copy(
					meta  = meta.copy(recycleBinEnabled = true, recycleBinUuid = vTrash.uuid, recycleBinChanged = vNow),
					group = group.copy(groups = group.groups + vTrash)
				)
			},
			fClock
		)
	}

	/*
		Déplace une entrée d'un groupe à un autre et note le déplacement.
		inEntry : entrée
		inFrom  : groupe actuel
		inTo    : groupe de destination
	*/
	private fun moveEntry(
		inEntry : Entry,
		inFrom  : UUID,
		inTo    : UUID
	): Vault
	{
		val vNow   = now()																	// date du déplacement
		val vMoved = inEntry.copy(
			times               = (inEntry.times ?: TimeData.create(vNow)).copy(locationChanged = vNow),
			previousParentGroup = inFrom
		)																					// entrée déplacée
		return withRoot(root.removeEntry(inEntry.uuid).mapGroup(inTo) { copy(entries = entries + vMoved) })
	}

	/*
		Nouveau Vault avec un autre groupe racine.
		inRoot : nouveau groupe racine
	*/
	private fun withRoot(inRoot: Group): Vault = Vault(database.modifyContent { copy(group = inRoot) }, fClock)

	/*
		Date actuelle, à la seconde.
	*/
	private fun now(): Instant = Instant.now(fClock).truncatedTo(ChronoUnit.SECONDS)

	/*
		Nombre de versions gardées dans l'historique d'une entrée.
	*/
	private fun historyLimit(): Int = database.content.meta.historyMaxItems.let { if (it < 0) Int.MAX_VALUE else it }

	/*
		Entrées hors corbeille avec leur groupe.
		inGroup : groupe de départ ; null pour la racine
	*/
	private fun placed(inGroup: UUID?): List<Pair<Group, Entry>>
	{
		val vStart = inGroup?.let { root.findGroup(it) } ?: root							// groupe de départ
		return vStart.placedEntries(trashId)
	}

	/*
		Texte où chercher dans une entrée, normalisé.
		inEntry : entrée
	*/
	private fun searchText(inEntry: Entry): String =
		normalized(
			inEntry.fields.entries
				.filter { (vKey, vValue) -> vKey != BasicField.Password() && vValue !is EntryValue.Encrypted }
				.joinToString(" ") { it.value.content } + " " + inEntry.tags.joinToString(" ")
		)
}

/*
	Texte en minuscules, sans accents, pour la recherche.
	inText : texte
*/
internal fun normalized(inText: String): String =
	Normalizer.normalize(inText, Normalizer.Form.NFD).replace(kMarks, "").lowercase(Locale.ROOT)

/*
	Signature du contenu des champs d'une entrée (nom, valeur, secret), pour
	comparer deux versions : les valeurs chiffrées en mémoire ont un sel
	différent à chaque fois et ne se comparent pas directement.
	inFields : champs
*/
internal fun fieldsSignature(inFields: EntryFields): List<Triple<String, String, Boolean>> =
	inFields.entries.map { (vKey, vValue) -> Triple(vKey, vValue.content, vValue is EntryValue.Encrypted) }

/*
	Entrées d'un groupe et de ses sous-groupes, avec leur groupe.
	inSkip : groupe à ignorer avec son contenu (la corbeille) ; null pour aucun
*/
internal fun Group.placedEntries(inSkip: UUID? = null): List<Pair<Group, Entry>>
{
	val vResult = mutableListOf<Pair<Group, Entry>>()									// entrées trouvées
	fun walk(inGroup: Group)
	{
		if (inGroup.uuid == inSkip) return
		inGroup.entries.forEach { vResult += inGroup to it }
		inGroup.groups.forEach { walk(it) }
	}
	walk(this)
	return vResult
}

/*
	Groupe d'identifiant donné, ce groupe compris ; null s'il n'existe pas.
	inId : identifiant cherché
*/
internal fun Group.findGroup(inId: UUID): Group? =
	if (uuid == inId) this else groups.firstNotNullOfOrNull { it.findGroup(inId) }

/*
	Parent d'un groupe ; null pour ce groupe lui-même ou un groupe absent.
	inId : identifiant du groupe
*/
internal fun Group.findParentOf(inId: UUID): Group? =
	if (groups.any { it.uuid == inId }) this else groups.firstNotNullOfOrNull { it.findParentOf(inId) }

/*
	Entrée d'identifiant donné avec son groupe ; null si elle n'existe pas.
	inId : identifiant cherché
*/
internal fun Group.findEntry(inId: UUID): Pair<Group, Entry>? =
	entries.firstOrNull { it.uuid == inId }?.let { this to it } ?: groups.firstNotNullOfOrNull { it.findEntry(inId) }

/*
	Copie de l'arbre où un groupe est transformé.
	inId    : identifiant du groupe
	inBlock : transformation
*/
internal fun Group.mapGroup(
	inId    : UUID,
	inBlock : Group.() -> Group
): Group =
	if (uuid == inId) inBlock() else copy(groups = groups.map { it.mapGroup(inId, inBlock) })

/*
	Copie de l'arbre sans une entrée.
	inId : identifiant de l'entrée
*/
internal fun Group.removeEntry(inId: UUID): Group =
	copy(entries = entries.filter { it.uuid != inId }, groups = groups.map { it.removeEntry(inId) })

/*
	Copie de l'arbre sans un groupe (ni son contenu).
	inId : identifiant du groupe
*/
internal fun Group.removeGroup(inId: UUID): Group =
	copy(groups = groups.filter { it.uuid != inId }.map { it.removeGroup(inId) })

/*
	Entrée pour les listes.
	inGroup : groupe qui la contient
*/
private fun Entry.toItem(inGroup: Group): VaultItem =
	VaultItem(
		id       = uuid,
		groupId  = inGroup.uuid,
		title    = fields.title?.content.orEmpty(),
		userName = fields.userName?.content.orEmpty(),
		url      = fields.url?.content.orEmpty(),
		modified = times?.lastModificationTime
	)

/*
	Contenu complet d'une entrée.
	inGroup : identifiant du groupe qui la contient
*/
private fun Entry.toDraft(inGroup: UUID): EntryDraft =
	EntryDraft(
		id       = uuid,
		groupId  = inGroup,
		title    = fields.title?.content.orEmpty(),
		userName = fields.userName?.content.orEmpty(),
		password = fields.password?.content.orEmpty(),
		url      = fields.url?.content.orEmpty(),
		notes    = fields.notes?.content.orEmpty(),
		fields   = fields.entries
			.filter { it.key !in kBasicKeys }
			.map { (vKey, vValue) -> CustomField(vKey, vValue.content, vValue is EntryValue.Encrypted) },
		modified = times?.lastModificationTime
	)

/*
	Champs KeePass d'une entrée : les cinq champs standard (mot de passe
	protégé), puis les champs libres. Un champ libre au nom d'un champ standard
	ou en double est ignoré.
*/
private fun EntryDraft.toFields(): EntryFields
{
	val vPairs = mutableListOf<Pair<String, EntryValue>>(
		BasicField.Title()    to EntryValue.Plain(title),
		BasicField.UserName() to EntryValue.Plain(userName),
		BasicField.Password() to EntryValue.Encrypted(EncryptedValue.fromString(password)),
		BasicField.Url()      to EntryValue.Plain(url),
		BasicField.Notes()    to EntryValue.Plain(notes)
	)																					// champs standard
	val vSeen  = kBasicKeys.toMutableSet()												// noms déjà pris
	for (vField in fields)
	{
		if (vField.name.isBlank() || !vSeen.add(vField.name)) continue
		vPairs += vField.name to if (vField.secret) EntryValue.Encrypted(EncryptedValue.fromString(vField.value)) else EntryValue.Plain(vField.value)
	}
	return EntryFields.of(*vPairs.toTypedArray())
}

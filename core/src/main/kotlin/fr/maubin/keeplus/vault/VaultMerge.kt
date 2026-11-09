package fr.maubin.keeplus.vault

import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.modifiers.binaries
import app.keemobile.kotpass.database.modifiers.modifyBinaries
import app.keemobile.kotpass.database.modifiers.modifyContent
import app.keemobile.kotpass.models.DeletedObject
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.TimeData
import java.time.Instant
import java.util.UUID

/*
	Résultat d'une fusion.
	database     : coffre fusionné (clé et en-tête du coffre local)
	localChanges : éléments (entrées et groupes) changés par rapport au coffre local :
	               0 si l'autre appareil n'apportait rien
	remoteChanges: éléments changés par rapport au coffre de l'autre appareil :
	               0 s'il est déjà à jour
*/
data class MergeResult(
	val database      : KeePassDatabase,
	val localChanges  : Int,
	val remoteChanges : Int
)

/*
	Élément du coffre mis à plat : un groupe (sans ses enfants) ou une entrée,
	avec son parent.
	item   : groupe ou entrée
	parent : identifiant du groupe parent
*/
private data class Placed<T>(
	val item   : T,
	val parent : UUID
)

/*
	Coffre mis à plat, pour la fusion.
	root    : groupe racine (sans ses enfants)
	groups  : groupes hors racine, dans l'ordre de l'arbre
	entries : entrées, dans l'ordre de l'arbre
*/
private class Flat(
	val root    : Group,
	val groups  : LinkedHashMap<UUID, Placed<Group>>,
	val entries : LinkedHashMap<UUID, Placed<Entry>>
)

/*
	Fusionne le coffre de cet appareil et celui d'un autre appareil, élément
	par élément (identifiant), sans rien demander, comme KeePassXC :
	- un élément présent des deux côtés prend le contenu de sa version la plus
	  récemment modifiée, et l'emplacement de sa version la plus récemment
	  déplacée ; pour une entrée, la version écartée rejoint l'historique ;
	- un élément présent d'un seul côté est gardé, sauf s'il a été supprimé de
	  l'autre côté après sa dernière modification ;
	- un groupe supprimé qui contient encore un élément gardé est rétabli.
	Les deux coffres doivent avoir la même clé ; le résultat garde la clé,
	l'en-tête et les réglages du coffre local.
	inLocal  : coffre de cet appareil
	inRemote : coffre de l'autre appareil
*/
fun mergeVaults(
	inLocal  : KeePassDatabase,
	inRemote : KeePassDatabase
): MergeResult
{
	val vLocalTrash  = inLocal.content.meta.recycleBinUuid								// corbeille locale
	val vRemoteTrash = inRemote.content.meta.recycleBinUuid								// corbeille de l'autre appareil
	val vAlias       = mutableMapOf(inRemote.content.group.uuid to inLocal.content.group.uuid)	// identifiants distants à remplacer
	if (vLocalTrash != null && vRemoteTrash != null && vLocalTrash != vRemoteTrash && inLocal.content.group.findGroup(vLocalTrash) != null)
	{
		vAlias[vRemoteTrash] = vLocalTrash													// deux corbeilles créées chacune de son côté : une seule
	}
	val vLocal   = flatten(inLocal.content.group, emptyMap())								// coffre local à plat
	val vRemote  = flatten(inRemote.content.group, vAlias)								// coffre distant à plat
	val vDeleted = (inLocal.content.deletedObjects + inRemote.content.deletedObjects)
		.groupBy { vAlias[it.id] ?: it.id }
		.mapValues { (_, vList) -> vList.maxOf { it.deletionTime } }					// suppressions connues, la plus récente
		.toMutableMap()

	// Groupes puis entrées : contenu de la version la plus récente, emplacement du dernier déplacement.
	val vGroups  = LinkedHashMap<UUID, Placed<Group>>()									// groupes gardés
	val vDropped = mutableMapOf<UUID, Placed<Group>>()										// groupes supprimés
	for (vId in (vLocal.groups.keys + vRemote.groups.keys))
	{
		val vKept = pick(vLocal.groups[vId], vRemote.groups[vId], vDeleted[vId], { it.times }) { vWinner, _ -> vWinner }
		if (vKept != null) vGroups[vId] = vKept else vDropped[vId] = (vLocal.groups[vId] ?: vRemote.groups[vId])!!
	}
	val vLimit   = inLocal.content.meta.historyMaxItems.let { if (it < 0) Int.MAX_VALUE else it }	// versions gardées
	val vEntries = LinkedHashMap<UUID, Placed<Entry>>()									// entrées gardées
	for (vId in (vLocal.entries.keys + vRemote.entries.keys))
	{
		pick(vLocal.entries[vId], vRemote.entries[vId], vDeleted[vId], { it.times }) { vWinner, vLoser -> withHistories(vWinner, vLoser, vLimit) }
			?.let { vEntries[vId] = it }
	}

	// Parents manquants : groupe supprimé rétabli, sinon racine.
	val vRoot = vLocal.root.uuid																// racine du résultat
	fun attach(inParent: UUID): UUID
	{
		var vCurrent = inParent																// groupe à vérifier
		while (vCurrent != vRoot && vCurrent !in vGroups)
		{
			val vGone = vDropped.remove(vCurrent) ?: return vRoot							// groupe inconnu : racine
			vGroups[vCurrent] = vGone
			vDeleted.remove(vCurrent)
			vCurrent = vGone.parent
		}
		return inParent
	}
	for (vId in vEntries.keys.toList()) vEntries[vId] = vEntries[vId]!!.let { it.copy(parent = attach(it.parent)) }
	for (vId in vGroups.keys.toList()) vGroups[vId]?.let { vGroups[vId] = it.copy(parent = attach(it.parent)) }

	// Racine, arbre, suppressions, réglages, pièces jointes.
	val vRootProps = newer(vLocal.root, vRemote.root.copy(uuid = vRoot)) { it.times }		// racine la plus récente
	val vTree      = build(vRootProps, vGroups, vEntries)									// arbre fusionné
	val vKeptIds   = vGroups.keys + vEntries.keys											// éléments gardés
	val vLocalMeta = inLocal.content.meta													// réglages locaux
	val vRemoteMeta= inRemote.content.meta													// réglages distants
	val vNamed     = if (later(vRemoteMeta.nameChanged, vLocalMeta.nameChanged)) vRemoteMeta else vLocalMeta	// nom le plus récent
	val vTrash     = vLocalTrash?.takeIf { it in vGroups } ?: (vAlias[vRemoteTrash] ?: vRemoteTrash)?.takeIf { it in vGroups }	// corbeille
	val vMerged    = inLocal
		.modifyContent {
			copy(
				group          = vTree,
				deletedObjects = vDeleted.filterKeys { it !in vKeptIds }.map { (vId, vTime) -> DeletedObject(vId, vTime) },
				meta           = meta.copy(
					name              = vNamed.name,
					nameChanged       = vNamed.nameChanged,
					recycleBinEnabled = vLocalMeta.recycleBinEnabled || vRemoteMeta.recycleBinEnabled,
					recycleBinUuid    = vTrash ?: meta.recycleBinUuid,
					customIcons       = vRemoteMeta.customIcons + meta.customIcons
				)
			)
		}
		.modifyBinaries { inRemote.binaries + it }
	val vResult    = flatten(vTree, emptyMap())												// résultat à plat
	return MergeResult(vMerged, differences(vLocal, vResult), differences(vRemote, vResult))
}

/*
	Version gardée d'un élément.
	- Des deux côtés : contenu de la version la plus récemment modifiée (à
	  égalité : la locale), emplacement de la plus récemment déplacée.
	- D'un seul côté : gardée, sauf si supprimée après sa dernière
	  modification et son dernier déplacement.
	inLocal   : version locale ; null si absente
	inRemote  : version distante ; null si absente
	inDeleted : date de suppression connue ; null si jamais supprimé
	inTimes   : dates d'un élément
	inCombine : contenu final à partir de la version gagnante et de la perdante
*/
private fun <T> pick(
	inLocal   : Placed<T>?,
	inRemote  : Placed<T>?,
	inDeleted : Instant?,
	inTimes   : (T) -> TimeData?,
	inCombine : (T, T) -> T
): Placed<T>?
{
	if (inLocal == null || inRemote == null)
	{
		val vOnly = inLocal ?: inRemote ?: return null										// seule version
		val vTimes = inTimes(vOnly.item)														// ses dates
		val vLast  = listOfNotNull(vTimes?.lastModificationTime, vTimes?.locationChanged, vTimes?.creationTime).maxOrNull()	// dernier changement
		return if (inDeleted != null && (vLast == null || !vLast.isAfter(inDeleted))) null else vOnly
	}
	val vRemoteWins = later(inTimes(inRemote.item)?.lastModificationTime, inTimes(inLocal.item)?.lastModificationTime)	// contenu distant plus récent
	val vRemoteMove = later(inTimes(inRemote.item)?.locationChanged, inTimes(inLocal.item)?.locationChanged)		// déplacement distant plus récent
	val vItem = if (vRemoteWins) inCombine(inRemote.item, inLocal.item) else inCombine(inLocal.item, inRemote.item)	// contenu final
	return Placed(vItem, if (vRemoteMove) inRemote.parent else inLocal.parent)
}

/*
	Entrée gagnante avec l'historique des deux versions, et la version
	perdante si son contenu diffère ; sans doublon, de la plus ancienne à la
	plus récente, limité aux inLimit dernières versions.
	inWinner : version gardée
	inLoser  : version écartée
	inLimit  : nombre de versions gardées
*/
private fun withHistories(
	inWinner : Entry,
	inLoser  : Entry,
	inLimit  : Int
): Entry
{
	val vCurrent = signature(inWinner)													// contenu gardé
	val vAll     = inWinner.history + inLoser.history + listOf(inLoser.copy(history = emptyList()))	// versions candidates
	val vHistory = vAll
		.filter { signature(it) != vCurrent }
		.distinctBy { it.times?.lastModificationTime to signature(it) }
		.sortedBy { it.times?.lastModificationTime ?: Instant.MIN }
		.takeLast(inLimit)																// historique fusionné
	return inWinner.copy(history = vHistory)
}

/*
	Signature du contenu d'une entrée, sans dates ni historique.
	inEntry : entrée
*/
private fun signature(inEntry: Entry): List<Any?> =
	listOf(
		fieldsSignature(inEntry.fields),
		inEntry.tags,
		inEntry.icon,
		inEntry.customIconUuid,
		inEntry.overrideUrl,
		inEntry.binaries,
		inEntry.foregroundColor,
		inEntry.backgroundColor
	)

/*
	Version la plus récemment modifiée de deux éléments ; à égalité, la première.
	inMine   : version locale
	inTheirs : version distante
	inTimes  : dates d'un élément
*/
private fun <T> newer(
	inMine   : T,
	inTheirs : T,
	inTimes  : (T) -> TimeData?
): T = if (later(inTimes(inTheirs)?.lastModificationTime, inTimes(inMine)?.lastModificationTime)) inTheirs else inMine

/*
	Vrai si la date inA est strictement postérieure à inB (date absente : la
	plus ancienne possible).
*/
private fun later(
	inA : Instant?,
	inB : Instant?
): Boolean = (inA ?: Instant.MIN).isAfter(inB ?: Instant.MIN)

/*
	Met un arbre à plat.
	inRoot  : groupe racine
	inAlias : identifiants de groupes à remplacer (racine et corbeille distantes)
*/
private fun flatten(
	inRoot  : Group,
	inAlias : Map<UUID, UUID>
): Flat
{
	val vGroups  = LinkedHashMap<UUID, Placed<Group>>()									// groupes
	val vEntries = LinkedHashMap<UUID, Placed<Entry>>()									// entrées
	fun id(inId: UUID): UUID = inAlias[inId] ?: inId
	fun walk(inGroup: Group)
	{
		val vId = id(inGroup.uuid)															// identifiant du groupe
		inGroup.entries.forEach { vEntries.putIfAbsent(it.uuid, Placed(it, vId)) }
		for (vChild in inGroup.groups)
		{
			vGroups.putIfAbsent(id(vChild.uuid), Placed(vChild.copy(uuid = id(vChild.uuid), groups = emptyList(), entries = emptyList()), vId))
			walk(vChild)
		}
	}
	walk(inRoot)
	return Flat(inRoot.copy(uuid = id(inRoot.uuid), groups = emptyList(), entries = emptyList()), vGroups, vEntries)
}

/*
	Reconstruit l'arbre à partir des éléments à plat. Un groupe inaccessible
	depuis la racine (boucle de déplacements croisés) est rattaché à la racine.
	inRoot    : groupe racine (sans enfants)
	inGroups  : groupes et leur parent
	inEntries : entrées et leur groupe
*/
private fun build(
	inRoot    : Group,
	inGroups  : LinkedHashMap<UUID, Placed<Group>>,
	inEntries : LinkedHashMap<UUID, Placed<Entry>>
): Group
{
	val vChildren = inGroups.values.groupBy({ it.parent }, { it.item })					// groupes par parent
	val vItems    = inEntries.values.groupBy({ it.parent }, { it.item })				// entrées par groupe
	val vPlaced   = mutableSetOf<UUID>()													// groupes déjà placés
	fun grow(inGroup: Group): Group
	{
		vPlaced += inGroup.uuid
		return inGroup.copy(
			groups  = vChildren[inGroup.uuid].orEmpty().filter { it.uuid !in vPlaced }.map { grow(it) },
			entries = vItems[inGroup.uuid].orEmpty()
		)
	}
	val vTree     = grow(inRoot)																// arbre accessible
	val vOrphans  = inGroups.values.map { it.item }.filter { it.uuid !in vPlaced }		// groupes en boucle
	return if (vOrphans.isEmpty()) vTree else vTree.copy(groups = vTree.groups + vOrphans.filter { it.uuid !in vPlaced }.map { grow(it) })
}

/*
	Nombre d'éléments (groupes et entrées) qui diffèrent entre deux coffres à
	plat : ajoutés, supprimés, modifiés ou déplacés.
	inBefore : coffre de départ
	inAfter  : coffre fusionné
*/
private fun differences(
	inBefore : Flat,
	inAfter  : Flat
): Int
{
	fun groupKey(inPlaced: Placed<Group>) = listOf(inPlaced.parent, inPlaced.item.name, inPlaced.item.notes, inPlaced.item.icon)
	fun entryKey(inPlaced: Placed<Entry>) = listOf(inPlaced.parent, signature(inPlaced.item), inPlaced.item.history.size)
	val vGroups  = (inBefore.groups.keys + inAfter.groups.keys).count { vId ->
		inBefore.groups[vId]?.let(::groupKey) != inAfter.groups[vId]?.let(::groupKey)
	}																					// groupes différents
	val vEntries = (inBefore.entries.keys + inAfter.entries.keys).count { vId ->
		inBefore.entries[vId]?.let(::entryKey) != inAfter.entries[vId]?.let(::entryKey)
	}																					// entrées différentes
	val vRoot    = if (inBefore.root.name != inAfter.root.name) 1 else 0				// racine renommée
	return vGroups + vEntries + vRoot
}

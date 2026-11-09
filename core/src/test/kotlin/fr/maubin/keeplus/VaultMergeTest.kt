package fr.maubin.keeplus

import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.createVault
import fr.maubin.keeplus.vault.decodeVault
import fr.maubin.keeplus.vault.encodeVault
import fr.maubin.keeplus.vault.mergeVaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/*
	Tests de la fusion de deux versions d'un même coffre (PC et téléphone),
	situation par situation.
*/
class VaultMergeTest
{
	private val fClock = TestClock()														// horloge commune
	private val fId    = UUID.randomUUID()													// entrée commune
	private val fBase  = Vault(createVault("Coffre", "maître"), fClock)
		.save(EntryDraft(id = fId, title = "Gmail", userName = "moi", password = "commun"))	// version commune

	/*
		Fusion de deux coffres, résultat sous forme de Vault.
		inLocal  : coffre local
		inRemote : coffre distant
	*/
	private fun merge(
		inLocal  : Vault,
		inRemote : Vault
	): Vault = Vault(mergeVaults(inLocal.database, inRemote.database).database, fClock)

	/*
		Ajouts de chaque côté : tous gardés ; un changement de chaque côté.
	*/
	@Test
	fun additionsOnBothSides()
	{
		fClock.tick()
		val vPc    = fBase.save(EntryDraft(title = "Impôts"))									// ajout sur le PC
		val vPhone = fBase.save(EntryDraft(title = "Netflix"))									// ajout sur le téléphone
		val vResult = mergeVaults(vPhone.database, vPc.database)								// fusion sur le téléphone
		assertEquals(listOf("Gmail", "Impôts", "Netflix"), Vault(vResult.database).items().map { it.title })
		assertEquals(1, vResult.localChanges)
		assertEquals(1, vResult.remoteChanges)
	}

	/*
		Même entrée modifiée des deux côtés : la version la plus récente gagne,
		l'autre rejoint l'historique ; aucune version perdue.
	*/
	@Test
	fun concurrentEditsKeepBothVersions()
	{
		fClock.tick()
		val vPc    = fBase.save(fBase.draft(fId)!!.copy(password = "pc"))					// modifié sur le PC
		fClock.tick()
		val vPhone = fBase.save(fBase.draft(fId)!!.copy(password = "téléphone"))			// modifié plus tard sur le téléphone
		for (vMerged in listOf(merge(vPc, vPhone), merge(vPhone, vPc)))
		{
			assertEquals("téléphone", vMerged.draft(fId)!!.password)
			assertEquals(listOf("pc", "commun"), vMerged.history(fId).map { it.password })
		}
	}

	/*
		Entrée supprimée d'un côté, inchangée de l'autre : supprimée.
	*/
	@Test
	fun deletionWins()
	{
		fClock.tick()
		val vPc     = fBase.trash(fId).let { it.trash(fId) }										// supprimée pour de bon sur le PC
		val vMerged = merge(fBase, vPc)																// fusion sur le téléphone
		assertNull(vMerged.draft(fId))
		assertEquals(fId, vMerged.database.content.deletedObjects.single().id)
	}

	/*
		Entrée supprimée d'un côté mais modifiée de l'autre après la
		suppression : gardée.
	*/
	@Test
	fun laterEditBeatsDeletion()
	{
		fClock.tick()
		val vPc     = fBase.trash(fId).trash(fId)												// supprimée sur le PC
		fClock.tick()
		val vPhone  = fBase.save(fBase.draft(fId)!!.copy(password = "nouveau"))				// modifiée ensuite sur le téléphone
		val vMerged = merge(vPhone, vPc)																// fusion
		assertEquals("nouveau", vMerged.draft(fId)!!.password)
		assertEquals(emptyList<UUID>(), vMerged.database.content.deletedObjects.map { it.id })
	}

	/*
		Entrée déplacée d'un côté et modifiée de l'autre : les deux changements
		sont gardés.
	*/
	@Test
	fun moveAndEditCombine()
	{
		val vGroup = UUID.randomUUID()																// groupe commun
		val vStart = fBase.addGroup("Mail", inId = vGroup)											// version commune
		fClock.tick()
		val vPc    = vStart.save(vStart.draft(fId)!!.copy(groupId = vGroup))					// déplacée sur le PC
		fClock.tick()
		val vPhone = vStart.save(vStart.draft(fId)!!.copy(userName = "moi@gmail.com"))		// modifiée sur le téléphone
		val vMerged = merge(vPhone, vPc)																// fusion
		assertEquals(vGroup, vMerged.draft(fId)!!.groupId)
		assertEquals("moi@gmail.com", vMerged.draft(fId)!!.userName)
	}

	/*
		Corbeille créée de chaque côté : une seule corbeille après fusion.
	*/
	@Test
	fun twoTrashesBecomeOne()
	{
		val vOther = UUID.randomUUID()																// seconde entrée
		val vStart = fBase.save(EntryDraft(id = vOther, title = "Forum"))							// version commune
		fClock.tick()
		val vPc    = vStart.trash(fId)																// corbeille créée sur le PC
		val vPhone = vStart.trash(vOther)															// autre corbeille sur le téléphone
		val vMerged = merge(vPhone, vPc)																// fusion
		assertEquals(listOf("Forum", "Gmail"), vMerged.trashItems().map { it.title })
		assertEquals(1, vMerged.root.groups.size)
		assertEquals(vPhone.trashId, vMerged.trashId)
	}

	/*
		Groupe supprimé d'un côté alors que l'autre côté y a ajouté une entrée :
		le groupe est rétabli avec l'entrée.
	*/
	@Test
	fun deletedGroupIsRestoredForNewEntry()
	{
		val vGroup = UUID.randomUUID()																// groupe commun
		val vStart = fBase.addGroup("Jeux", inId = vGroup)											// version commune
		fClock.tick()
		val vPc    = vStart.trashGroup(vGroup).emptyTrash()										// groupe supprimé pour de bon sur le PC
		fClock.tick()
		val vPhone = vStart.save(EntryDraft(title = "Steam", groupId = vGroup))					// entrée ajoutée sur le téléphone
		val vMerged = merge(vPc, vPhone)																// fusion sur le PC
		assertEquals(listOf("Jeux"), vMerged.groups().map { it.name })
		assertEquals(vGroup, vMerged.items().single { it.title == "Steam" }.groupId)
	}

	/*
		Fusion stable : refusionner le résultat avec l'une des deux versions ne
		change plus rien, même après chiffrement et relecture.
	*/
	@Test
	fun mergeIsStable()
	{
		fClock.tick()
		val vPc     = fBase.save(fBase.draft(fId)!!.copy(password = "pc")).save(EntryDraft(title = "Impôts"))	// changements sur le PC
		fClock.tick()
		val vPhone  = fBase.save(fBase.draft(fId)!!.copy(notes = "note")).trash(fId)			// changements sur le téléphone
		val vMerged = merge(vPhone, vPc)																// fusion
		val vReread = decodeVault(encodeVault(vMerged.database), "maître")						// fusion relue
		val vAgain  = mergeVaults(vReread, vMerged.database)										// nouvelle fusion
		assertEquals(0, vAgain.localChanges)
		assertEquals(0, vAgain.remoteChanges)
		assertNotNull(Vault(vReread).trashItems().singleOrNull { it.id == fId })
		assertEquals(0, mergeVaults(vMerged.database, vPc.database).let { merge(Vault(it.database), vPhone) }.let { mergeVaults(it.database, vMerged.database).localChanges })
	}
}

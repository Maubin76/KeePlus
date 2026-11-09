package fr.maubin.keeplus

import fr.maubin.keeplus.vault.CustomField
import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.createVault
import fr.maubin.keeplus.vault.decodeVault
import fr.maubin.keeplus.vault.encodeVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/*
	Tests des modifications du coffre : entrées, historique, groupes,
	recherche, corbeille.
*/
class VaultTest
{
	private val fClock = TestClock()														// horloge des tests
	private val fEmpty = Vault(createVault("Coffre", "maître"), fClock)					// coffre vide

	/*
		Une entrée modifiée garde sa version précédente dans l'historique ; une
		sauvegarde sans changement ne crée pas de version.
	*/
	@Test
	fun editKeepsHistory()
	{
		val vId      = UUID.randomUUID()														// entrée
		val vCreated = fEmpty.save(EntryDraft(id = vId, title = "Gmail", password = "un"))	// création
		fClock.tick()
		val vEdited  = vCreated.save(vCreated.draft(vId)!!.copy(password = "deux"))			// modification
		assertEquals("deux", vEdited.draft(vId)!!.password)
		assertEquals(listOf("un"), vEdited.history(vId).map { it.password })
		assertEquals(fClock.instant(), vEdited.draft(vId)!!.modified)
		assertSame(vEdited, vEdited.save(vEdited.draft(vId)!!))
	}

	/*
		L'historique est limité à 10 versions, les plus récentes.
	*/
	@Test
	fun historyIsCapped()
	{
		val vId    = UUID.randomUUID()														// entrée
		var vVault = fEmpty.save(EntryDraft(id = vId, title = "Site", password = "0"))		// coffre
		for (vIndex in 1..12)
		{
			fClock.tick()
			vVault = vVault.save(vVault.draft(vId)!!.copy(password = "$vIndex"))
		}
		assertEquals((11 downTo 2).map { "$it" }, vVault.history(vId).map { it.password })
	}

	/*
		Champs libres : gardés dans l'ordre, secrets protégés, relus à
		l'identique après chiffrement.
	*/
	@Test
	fun customFieldsSurvive()
	{
		val vFields = listOf(CustomField("Code PIN", "1234", secret = true), CustomField("Question", "Chat"))	// champs libres
		val vVault  = fEmpty.save(EntryDraft(title = "Banque", fields = vFields))			// coffre
		val vReread = Vault(decodeVault(encodeVault(vVault.database), "maître"))			// coffre relu
		assertEquals(vFields, vReread.draft(vReread.items().single().id)!!.fields)
	}

	/*
		Recherche : tous les mots, sans majuscules ni accents, dans le titre,
		l'identifiant, l'adresse et les notes ; jamais dans le mot de passe.
	*/
	@Test
	fun searchIgnoresCaseAndAccents()
	{
		val vVault = fEmpty
			.save(EntryDraft(title = "Société Générale", userName = "mathieu", url = "https://particuliers.sg.fr"))
			.save(EntryDraft(title = "Impôts", notes = "Numéro fiscal dans le tiroir", password = "generale"))
			.save(EntryDraft(title = "Gmail", userName = "mathieu@gmail.com"))					// coffre de test
		assertEquals(listOf("Société Générale"), vVault.search("generale").map { it.title })
		assertEquals(listOf("Gmail", "Société Générale"), vVault.search("MATHIEU").map { it.title })
		assertEquals(listOf("Impôts"), vVault.search("fiscal tiroir").map { it.title })
		assertEquals(emptyList<String>(), vVault.search("fiscal gmail").map { it.title })
		assertEquals(3, vVault.search("  ").size)
	}

	/*
		Groupes : ajout, sous-groupe, renommage, entrées par groupe et tri
		français des titres.
	*/
	@Test
	fun groupsAndItems()
	{
		val vPerso = UUID.randomUUID()															// groupe Perso
		val vBank  = UUID.randomUUID()															// sous-groupe Banques
		val vVault = fEmpty
			.addGroup("Perso", inId = vPerso)
			.addGroup("Banque", vPerso, vBank)
			.renameGroup(vBank, "Banques")
			.save(EntryDraft(title = "école", groupId = vPerso))
			.save(EntryDraft(title = "Crédit", groupId = vBank))
			.save(EntryDraft(title = "Amazon"))												// coffre de test
		assertEquals(listOf("Perso" to 0, "Banques" to 1), vVault.groups().map { it.name to it.depth })
		assertEquals(listOf("Amazon", "Crédit", "école"), vVault.items().map { it.title })
		assertEquals(listOf("Crédit", "école"), vVault.items(vPerso).map { it.title })
		assertEquals(listOf("Crédit"), vVault.items(vBank).map { it.title })
	}

	/*
		Déplacement d'une entrée vers un autre groupe : date de déplacement et
		groupe précédent notés, sans nouvelle version.
	*/
	@Test
	fun moveEntry()
	{
		val vGroup = UUID.randomUUID()															// groupe de destination
		val vId    = UUID.randomUUID()															// entrée
		val vStart = fEmpty.addGroup("Pro", inId = vGroup).save(EntryDraft(id = vId, title = "Slack"))	// coffre de départ
		val vMoved = vStart.save(vStart.draft(vId)!!.copy(groupId = vGroup))				// entrée déplacée
		assertEquals(vGroup, vMoved.draft(vId)!!.groupId)
		assertTrue(vMoved.history(vId).isEmpty())
	}

	/*
		Corbeille : créée au premier usage, exclue des listes et des groupes ;
		restauration dans le groupe d'origine ; suppression définitive notée.
	*/
	@Test
	fun trashRestoreAndDelete()
	{
		val vGroup   = UUID.randomUUID()														// groupe d'origine
		val vId      = UUID.randomUUID()														// entrée
		val vStart   = fEmpty.addGroup("Perso", inId = vGroup).save(EntryDraft(id = vId, title = "Vieux site", groupId = vGroup))	// coffre de départ
		assertNull(vStart.trashId)
		val vTrashed = vStart.trash(vId)															// entrée à la corbeille
		assertNotNull(vTrashed.trashId)
		assertTrue(vTrashed.items().isEmpty())
		assertTrue(vTrashed.search("vieux").isEmpty())
		assertEquals(listOf("Perso"), vTrashed.groups().map { it.name })
		assertEquals(listOf("Vieux site"), vTrashed.trashItems().map { it.title })
		val vRestored = vTrashed.restore(vId)														// entrée restaurée
		assertEquals(vGroup, vRestored.draft(vId)!!.groupId)
		val vDeleted  = vTrashed.trash(vId)														// suppression définitive
		assertNull(vDeleted.draft(vId))
		assertTrue(vDeleted.database.content.deletedObjects.any { it.id == vId })
	}

	/*
		Un groupe mis à la corbeille emporte son contenu.
	*/
	@Test
	fun trashGroup()
	{
		val vGroup = UUID.randomUUID()															// groupe
		val vVault = fEmpty.addGroup("Ancien", inId = vGroup).save(EntryDraft(title = "Forum", groupId = vGroup)).trashGroup(vGroup)	// groupe à la corbeille
		assertTrue(vVault.groups().isEmpty())
		assertTrue(vVault.items().isEmpty())
		assertEquals(listOf("Forum"), vVault.trashItems().map { it.title })
	}
}

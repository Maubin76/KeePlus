package fr.maubin.keeplus

import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.VaultBackups
import fr.maubin.keeplus.vault.VaultLocation
import fr.maubin.keeplus.vault.VaultStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/*
	Tests de l'emplacement du coffre : déplacement, retour à l'emplacement
	par défaut, coffre trouvé dans le dossier choisi, suppression.
*/
class VaultLocationTest
{
	@get:Rule
	val fFolder = TemporaryFolder()														// dossier temporaire

	/*
		Coffre de test : emplacement par défaut dans « appli », renvoi possible.
		inBackups : sauvegardes ; null sans
	*/
	private fun store(inBackups: VaultBackups? = null): VaultStore =
		VaultStore(VaultLocation(fFolder.root.resolve("appli/coffre.kdbx"), fFolder.root.resolve("appli/emplacement.txt")), inBackups)

	/*
		Le coffre déplacé est identique, l'ancien fichier disparaît, tous les
		accès suivent le renvoi ; retour à l'emplacement par défaut.
	*/
	@Test
	fun moveAndBack() = runBlocking {
		val vStore  = store()																	// coffre de l'appli
		var vVault  = vStore.create("Coffre", "maître")											// coffre neuf
		val vBytes  = vStore.read()!!															// contenu avant
		val vTarget = fFolder.root.resolve("cle-usb/KeePlus")									// dossier choisi
		assertNull(vStore.moveTo(vTarget))
		assertTrue(vStore.fLocation.custom)
		assertFalse(fFolder.root.resolve("appli/coffre.kdbx").exists())
		assertArrayEquals(vBytes, vTarget.resolve("coffre.kdbx").readBytes())
		assertArrayEquals(vBytes, store().read())
		vVault = vVault.save(EntryDraft(title = "Après"))
		assertNull(vStore.save(vVault))
		assertNotNull(store().unlock("maître").items().singleOrNull { it.title == "Après" })
		assertNull(vStore.moveTo(fFolder.root.resolve("appli")))
		assertFalse(vStore.fLocation.custom)
		assertFalse(vTarget.resolve("coffre.kdbx").exists())
		assertFalse(fFolder.root.resolve("appli/emplacement.txt").exists())
	}

	/*
		Un coffre déjà présent dans le dossier choisi n'est jamais écrasé ;
		sans coffre sur l'appareil, il devient le coffre de l'appareil.
	*/
	@Test
	fun existingVault() = runBlocking {
		val vOther  = VaultStore(fFolder.root.resolve("ailleurs/coffre.kdbx"))					// coffre déjà rangé ailleurs
		vOther.create("Autre", "autre")
		val vOthers = vOther.read()!!															// son contenu
		val vStore  = store()																	// coffre de l'appli
		vStore.create("Coffre", "maître")
		assertNotNull(vStore.moveTo(fFolder.root.resolve("ailleurs")))
		assertArrayEquals(vOthers, vOther.read())
		assertFalse(vStore.fLocation.custom)
		val vEmpty  = VaultStore(VaultLocation(fFolder.root.resolve("vide/coffre.kdbx"), fFolder.root.resolve("vide/emplacement.txt")))	// appareil sans coffre
		assertFalse(vEmpty.exists())
		assertNull(vEmpty.moveTo(fFolder.root.resolve("ailleurs")))
		assertTrue(vEmpty.exists())
		assertEquals("Autre", vEmpty.unlock("autre").name)
	}

	/*
		La suppression efface le coffre et ses sauvegardes, et garde
		l'emplacement choisi pour un prochain coffre.
	*/
	@Test
	fun delete() = runBlocking {
		val vBackups = VaultBackups(fFolder.root.resolve("sauvegardes"), 3)						// sauvegardes
		val vStore   = store(vBackups)															// coffre de l'appli
		var vVault   = vStore.create("Coffre", "maître")										// coffre neuf
		vVault = vVault.save(EntryDraft(title = "Un"))
		assertNull(vStore.save(vVault))
		assertEquals(1, vBackups.list().size)
		assertNull(vStore.moveTo(fFolder.root.resolve("choisi")))
		vStore.delete()
		assertFalse(vStore.exists())
		assertTrue(vBackups.list().isEmpty())
		assertTrue(vStore.fLocation.custom)
		vStore.create("Nouveau", "maître")
		assertTrue(fFolder.root.resolve("choisi/coffre.kdbx").isFile)
	}
}

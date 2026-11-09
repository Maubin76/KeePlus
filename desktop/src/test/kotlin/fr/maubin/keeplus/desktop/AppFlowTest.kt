package fr.maubin.keeplus.desktop

import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.AppScreen
import fr.maubin.keeplus.ui.PickedFile
import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.VaultStore
import fr.maubin.keeplus.vault.createVault
import fr.maubin.keeplus.vault.encodeVault
import fr.maubin.keeplus.vault.Vault
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/*
	Enchaînement des écrans sans interface : création, verrouillage,
	mauvais mot de passe, déverrouillage, reprise d'un coffre existant.
*/
class AppFlowTest
{
	@get:Rule
	val fFolder = TemporaryFolder()														// dossier temporaire

	/*
		Création puis verrouillage et déverrouillage du coffre de l'appareil.
	*/
	@Test
	fun createLockUnlock() = runBlocking {
		val vFile       = fFolder.root.resolve("coffre.kdbx")									// coffre de l'appareil
		val vController = AppController(VaultStore(vFile), { null })							// appli sans sélecteur
		assertEquals(AppScreen.Welcome, vController.screen)
		vController.startCreate()
		val vVault = vController.create("Perso", "un mot de passe solide")!!				// coffre créé
		vController.open(vVault)
		assertTrue(vController.screen is AppScreen.Opened)
		vController.lock()
		assertEquals(AppScreen.Locked, vController.screen)
		assertNull(vController.unlock("faux"))
		assertEquals("Mot de passe incorrect.", vController.error)
		val vOpened = vController.unlock("un mot de passe solide")							// coffre rouvert
		assertNotNull(vOpened)
		assertNull(vController.error)
		assertEquals("Perso", vOpened!!.name)
		assertEquals(AppScreen.Locked, AppController(VaultStore(vFile), { null }).screen)
	}

	/*
		Édition : ajout, modification (ancienne version gardée), corbeille,
		groupe créé ; chaque changement est écrit sur le disque. La
		démonstration, elle, n'écrit rien.
	*/
	@Test
	fun editingIsSaved() = runBlocking {
		val vFile       = fFolder.root.resolve("coffre.kdbx")									// coffre de l'appareil
		val vController = AppController(VaultStore(vFile), { null })							// appli
		vController.open(vController.create("Perso", "un mot de passe solide")!!)
		vController.startNew()
		val vNew   = vController.editing!!.copy(title = "Gmail", password = "premier")			// nouvelle entrée
		vController.saveEntry(vNew)
		assertNull(vController.editing)
		assertEquals(vNew.id, vController.selected)
		vController.startEdit(vNew.id!!)
		vController.saveEntry(vController.editing!!.copy(password = "second"))
		val vGroup = vController.addGroup("Mail")!!												// groupe créé
		val vDisk  = VaultStore(vFile).unlock("un mot de passe solide")						// coffre relu
		assertEquals("second", vDisk.draft(vNew.id!!)!!.password)
		assertEquals(listOf("premier"), vDisk.history(vNew.id!!).map { it.password })
		assertEquals("Mail", vDisk.groupName(vGroup))
		vController.trashEntry(vNew.id!!)
		assertEquals(listOf("Gmail"), VaultStore(vFile).unlock("un mot de passe solide").trashItems().map { it.title })
		assertNull(vController.saveError)

		val vDemoFile = fFolder.root.resolve("absent.kdbx")										// pas de coffre
		val vDemo     = AppController(VaultStore(vDemoFile), { null })							// appli en démonstration
		vDemo.openDemo()
		vDemo.startNew()
		vDemo.saveEntry(vDemo.editing!!.copy(title = "Essai"))
		assertTrue((vDemo.screen as AppScreen.Opened).vault.items().any { it.title == "Essai" })
		assertTrue(!vDemoFile.exists())
	}

	/*
		Reprise d'un coffre existant : un fichier quelconque est refusé, un
		coffre KeePass est ouvert avec son mot de passe et devient celui de
		l'appareil.
	*/
	@Test
	fun importExistingVault() = runBlocking {
		val vFile    = fFolder.root.resolve("coffre.kdbx")										// coffre de l'appareil
		val vKeePass = encodeVault(Vault(createVault("KeePassXC", "ancien")).save(EntryDraft(title = "Banque")).database)	// coffre existant
		var vPicked  = PickedFile("notes.txt", "pas un coffre".toByteArray())					// fichier proposé
		val vController = AppController(VaultStore(vFile), { vPicked })						// appli
		vController.startImport()
		assertEquals(AppScreen.Welcome, vController.screen)
		assertEquals("« notes.txt » n'est pas un coffre KeePass.", vController.error)
		vPicked = PickedFile("Passwords.kdbx", vKeePass)
		vController.startImport()
		assertTrue(vController.screen is AppScreen.Import)
		assertNull(vController.import("faux"))
		val vVault = vController.import("ancien")!!											// coffre repris
		assertEquals(listOf("Banque"), vVault.items().map { it.title })
		assertTrue(vFile.isFile)
		assertEquals(listOf("Banque"), VaultStore(vFile).unlock("ancien").items().map { it.title })
	}
}

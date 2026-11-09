package fr.maubin.keeplus

import fr.maubin.keeplus.settings.KeeSettings
import fr.maubin.keeplus.settings.SettingsStore
import fr.maubin.keeplus.sync.vaultFingerprint
import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.VaultBackups
import fr.maubin.keeplus.vault.VaultException
import fr.maubin.keeplus.vault.VaultStore
import fr.maubin.keeplus.vault.createVault
import fr.maubin.keeplus.vault.decodeVault
import fr.maubin.keeplus.vault.encodeVault
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/*
	Tests des sauvegardes automatiques, du changement de mot de passe maître
	et des réglages.
*/
class BackupSettingsTest
{
	@get:Rule
	val fFolder = TemporaryFolder()														// dossier temporaire

	/*
		Une sauvegarde par jour, du coffre avant la première modification du
		jour (locale ou arrivée d'un autre appareil) ; seules les plus récentes
		sont gardées ; restauration.
	*/
	@Test
	fun dailyBackups() = runBlocking {
		val vClock   = TestClock()																// horloge des tests
		val vBackups = VaultBackups(fFolder.root.resolve("sauvegardes"), 3, vClock)				// sauvegardes
		val vStore   = VaultStore(fFolder.root.resolve("coffre.kdbx"), vBackups)				// coffre
		var vVault   = vStore.create("Coffre", "maître")										// coffre neuf : rien à sauvegarder
		assertTrue(vBackups.list().isEmpty())
		val vFirst   = vStore.read()!!															// coffre du matin
		vVault = vVault.save(EntryDraft(title = "Un"))
		assertNull(vStore.save(vVault))
		vVault = vVault.save(EntryDraft(title = "Deux"))
		assertNull(vStore.save(vVault))
		assertEquals(1, vBackups.list().size)
		assertArrayEquals(vFirst, vBackups.list().single().file.readBytes())
		for (vDay in 1..4)
		{
			vClock.tick(24 * 3600)
			val vNow   = vStore.read()!!														// coffre actuel
			val vOther = encodeVault(Vault(decodeVault(vNow, "maître")).save(EntryDraft(title = "Jour $vDay")).database)	// coffre arrivé
			assertTrue(vStore.replace(vOther, vaultFingerprint(vNow)))
		}
		val vList = vBackups.list()																// sauvegardes gardées
		assertEquals(3, vList.size)
		assertEquals(LocalDate.parse("2026-01-05"), vList.first().day)
		vStore.restore(vList.last())
		assertEquals(listOf("Jour 1", "Un", "Deux").sorted(), Vault(decodeVault(vStore.read()!!, "maître")).items().map { it.title }.sorted())
	}

	/*
		Changement du mot de passe maître : l'ancien ne vaut plus, le nouveau
		ouvre le coffre, avec son contenu.
	*/
	@Test
	fun changeMasterPassword()
	{
		val vVault   = Vault(createVault("Coffre", "ancien")).save(EntryDraft(title = "Gmail"))	// coffre
		assertTrue(vVault.isMasterPassword("ancien"))
		assertFalse(vVault.isMasterPassword("nouveau"))
		val vChanged = vVault.changeMasterPassword("nouveau")										// mot de passe changé
		assertTrue(vChanged.isMasterPassword("nouveau"))
		val vBytes   = encodeVault(vChanged.database)												// coffre chiffré
		assertEquals(listOf("Gmail"), Vault(decodeVault(vBytes, "nouveau")).items().map { it.title })
		try
		{
			decodeVault(vBytes, "ancien")
			fail("L'ancien mot de passe ne devait plus ouvrir le coffre")
		}
		catch (vError: VaultException.WrongPassword)
		{
		}
	}

	/*
		Réglages : valeurs par défaut sans fichier, aller-retour.
	*/
	@Test
	fun settingsRoundTrip()
	{
		val vStore = SettingsStore(fFolder.root.resolve("reglages.json"))						// réglages
		assertEquals(KeeSettings(), vStore.load())
		vStore.save(KeeSettings(idleMinutes = 15, hiddenSeconds = 0, clipboardSeconds = 60))
		assertEquals(KeeSettings(15, 0, 60), vStore.load())
	}
}

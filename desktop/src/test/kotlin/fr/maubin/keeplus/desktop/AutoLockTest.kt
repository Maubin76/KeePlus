package fr.maubin.keeplus.desktop

import fr.maubin.keeplus.settings.KeeSettings
import fr.maubin.keeplus.settings.SettingsStore
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.AppScreen
import fr.maubin.keeplus.ui.BiometricInvalidated
import fr.maubin.keeplus.ui.BiometricUnlock
import fr.maubin.keeplus.ui.kHiddenMillis
import fr.maubin.keeplus.ui.kIdleMillis
import fr.maubin.keeplus.vault.VaultBackups
import fr.maubin.keeplus.vault.VaultStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

/*
	Empreinte simulée : garde le mot de passe en clair (test seulement).
	fSecret      : mot de passe gardé ; null si inactive
	fAccept      : l'utilisateur accepte la demande d'empreinte
	fInvalidated : clé invalidée (empreinte ajoutée)
	fOffered     : activation déjà proposée
	passwordAt   : dernier déverrouillage par mot de passe (ms)
*/
private class FakeBiometric : BiometricUnlock
{
	var fSecret      : String? = null
	var fAccept      : Boolean = true
	var fInvalidated : Boolean = false
	var fOffered     : Boolean = false
	override var passwordAt : Long = 0L

	/*
		Capteur toujours présent.
	*/
	override val available: Boolean get() = true

	/*
		Active si un mot de passe est gardé.
	*/
	override val enabled: Boolean get() = fSecret != null

	/*
		Activation déjà proposée.
	*/
	override val offered: Boolean get() = fOffered

	/*
		Note la proposition.
	*/
	override fun markOffered()
	{
		fOffered = true
	}

	/*
		Proposition à refaire.
	*/
	override fun resetOffer()
	{
		fOffered = false
	}

	/*
		Garde le mot de passe si l'utilisateur accepte.
		inPassword : mot de passe maître
	*/
	override suspend fun enable(inPassword: CharSequence): Boolean
	{
		if (!fAccept) return false
		fSecret = inPassword.toString()
		return true
	}

	/*
		Rend le mot de passe gardé, null si refusé, erreur si invalidé.
	*/
	override suspend fun unlock(): CharArray?
	{
		if (fInvalidated) throw BiometricInvalidated()
		return if (fAccept) fSecret?.toCharArray() else null
	}

	/*
		Oublie le mot de passe.
	*/
	override fun disable()
	{
		fSecret = null
	}
}

/*
	Tests du verrouillage automatique et du déverrouillage par empreinte,
	avec une horloge et une empreinte simulées.
*/
class AutoLockTest
{
	@get:Rule
	val fFolder = TemporaryFolder()														// dossier temporaire

	private var fTime = 1_000_000L															// horloge simulée (ms)

	/*
		Appli avec un coffre créé et ouvert.
		inBiometric : empreinte simulée
	*/
	private fun opened(inBiometric: BiometricUnlock? = null): AppController = runBlocking {
		val vController = AppController(VaultStore(fFolder.root.resolve("coffre.kdbx")), { null }, inBiometric, fNow = { fTime })	// appli
		vController.open(vController.create("Perso", "un mot de passe solide")!!)
		vController
	}

	/*
		Inactivité : verrouillé après 5 min sans geste ; un geste repousse le délai.
	*/
	@Test
	fun idleLocks()
	{
		val vController = opened()																// coffre ouvert
		fTime += kIdleMillis - 1000
		vController.touch()
		fTime += kIdleMillis - 1000
		vController.checkAutoLock()
		assertTrue(vController.screen is AppScreen.Opened)
		fTime += 2000
		vController.checkAutoLock()
		assertEquals(AppScreen.Locked, vController.screen)
	}

	/*
		Arrière-plan : retour rapide sans verrouillage (copier-coller), retour
		tardif verrouillé.
	*/
	@Test
	fun hiddenGrace()
	{
		val vController = opened()																// coffre ouvert
		vController.setHidden(true)
		fTime += kHiddenMillis - 5000
		vController.setHidden(false)
		assertTrue(vController.screen is AppScreen.Opened)
		vController.setHidden(true)
		fTime += kHiddenMillis
		vController.setHidden(false)
		assertEquals(AppScreen.Locked, vController.screen)
	}

	/*
		Auto-remplissage : le coffre ouvert est prêté tant que les délais de
		verrouillage ne sont pas dépassés, même avant le verrouillage
		effectif ; jamais une fois verrouillé. Une modification venue de
		l'auto-remplissage est affichée et enregistrée.
	*/
	@Test
	fun activeVaultForAutofill() = runBlocking {
		val vController = opened()																// coffre ouvert
		assertNotNull(vController.activeVault())
		vController.edit { it.addGroup("Applis") }
		assertEquals(listOf("Applis"), vController.activeVault()!!.groups().map { it.name })
		vController.lock()
		assertNull(vController.unlock("faux"))
		vController.open(vController.unlock("un mot de passe solide")!!)
		assertEquals(listOf("Applis"), vController.activeVault()!!.groups().map { it.name })
		vController.setHidden(true)
		fTime += kHiddenMillis - 5000
		assertNotNull(vController.activeVault())
		fTime += 5000
		assertNull(vController.activeVault())
		assertTrue(vController.screen is AppScreen.Opened)
		vController.setHidden(false)
		assertNull(vController.activeVault())
	}

	/*
		Fréquence du mot de passe maître avec l'empreinte : au-delà d'une
		semaine (réglage par défaut) sans saisie du mot de passe, l'empreinte
		n'est plus proposée ; une saisie la rend de nouveau disponible pour
		une semaine ; « Jamais » la laisse toujours disponible.
	*/
	@Test
	fun passwordFrequency() = runBlocking {
		val vBiometric  = FakeBiometric().apply { fSecret = "un mot de passe solide"; fOffered = true }	// empreinte active
		val vController = AppController(VaultStore(fFolder.root.resolve("coffre.kdbx")), { null }, vBiometric, fNow = { fTime })	// appli
		vController.open(vController.create("Perso", "un mot de passe solide")!!)
		vController.lock()
		val vWeek = 7 * 24 * 3_600_000L																	// une semaine (ms)
		fTime += vWeek
		assertFalse(vController.biometricReady)
		vController.open(vController.unlock("un mot de passe solide")!!)
		assertEquals(fTime, vBiometric.passwordAt)
		vController.lock()
		fTime += vWeek - 1000
		assertTrue(vController.biometricReady)
		assertNotNull(vController.unlockWithBiometric())
		fTime += 1000
		assertFalse(vController.biometricReady)
		vController.updateSettings(KeeSettings(passwordHours = 0))
		assertTrue(vController.biometricReady)
	}

	/*
		Choisir une entrée, en créer ou en modifier une ferme les réglages et
		les appareils (sur PC, ils occupent la place de la fiche).
	*/
	@Test
	fun entryClosesPages()
	{
		val vController = opened()																// coffre ouvert
		val vId         = UUID.randomUUID()													// entrée
		vController.showSettings = true
		vController.select(vId)
		assertFalse(vController.showSettings)
		assertEquals(vId, vController.selected)
		vController.showDevices = true
		vController.startNew()
		assertFalse(vController.showDevices)
		assertNotNull(vController.editing)
	}

	/*
		Délais réglés : 1 min d'inactivité, verrouillage aussitôt en
		arrière-plan ; réglages enregistrés.
	*/
	@Test
	fun settingsDriveLocking()
	{
		val vFile       = fFolder.root.resolve("reglages.json")									// fichier des réglages
		val vController = runBlocking {
			AppController(VaultStore(fFolder.root.resolve("coffre.kdbx")), { null }, fNow = { fTime }, fSettings = SettingsStore(vFile)).also {
				it.open(it.create("Perso", "un mot de passe solide")!!)
			}
		}																							// coffre ouvert
		vController.updateSettings(KeeSettings(idleMinutes = 1, hiddenSeconds = 0))
		assertEquals(KeeSettings(idleMinutes = 1, hiddenSeconds = 0), SettingsStore(vFile).load())
		fTime += 61_000
		vController.checkAutoLock()
		assertEquals(AppScreen.Locked, vController.screen)
		vController.open(runBlocking { vController.unlock("un mot de passe solide")!! })
		vController.setHidden(true)
		assertEquals(AppScreen.Locked, vController.screen)
	}

	/*
		Changement du mot de passe maître : l'ancien est refusé, l'empreinte
		qui le gardait est désactivée et sera proposée de nouveau ; restauration
		d'une sauvegarde : coffre verrouillé, ancien mot de passe.
	*/
	@Test
	fun passwordChangeAndRestore() = runBlocking {
		val vBiometric  = FakeBiometric().apply { fSecret = "un mot de passe solide"; fOffered = true }	// empreinte active
		val vBackups    = VaultBackups(fFolder.root.resolve("sauvegardes"), 3)							// sauvegardes
		val vController = AppController(VaultStore(fFolder.root.resolve("coffre.kdbx"), vBackups), { null }, vBiometric, fNow = { fTime })	// appli
		vController.open(vController.create("Perso", "un mot de passe solide")!!)
		assertEquals("Mot de passe actuel incorrect.", vController.changeMasterPassword("faux", "nouveau mot de passe solide"))
		assertNull(vController.changeMasterPassword("un mot de passe solide", "nouveau mot de passe solide"))
		assertFalse(vBiometric.enabled)
		assertFalse(vBiometric.offered)
		vController.lock()
		assertNull(vController.unlock("un mot de passe solide"))
		vController.open(vController.unlock("nouveau mot de passe solide")!!)
		val vBackup = vController.backups().single()													// sauvegarde du jour (ancien mot de passe)
		vController.restoreBackup(vBackup)
		assertEquals(AppScreen.Locked, vController.screen)
		assertNotNull(vController.error)
		assertNotNull(vController.unlock("un mot de passe solide"))
	}

	/*
		Empreinte : proposée une fois après un déverrouillage par mot de passe,
		puis utilisée ; désactivée si la clé est invalidée.
	*/
	@Test
	fun biometricFlow() = runBlocking {
		val vBiometric  = FakeBiometric()														// empreinte simulée
		val vController = opened(vBiometric)													// coffre ouvert
		vController.lock()
		assertFalse(vController.biometricReady)
		vController.open(vController.unlock("un mot de passe solide")!!)
		assertTrue(vController.offerBiometric)
		assertTrue(vController.answerBiometricOffer(true))
		assertFalse(vController.offerBiometric)
		assertEquals("un mot de passe solide", vBiometric.fSecret)
		vController.lock()
		assertTrue(vController.biometricReady)
		assertNotNull(vController.unlockWithBiometric())
		vController.lock()
		vController.open(vController.unlock("un mot de passe solide")!!)
		assertFalse(vController.offerBiometric)
		vController.lock()
		vBiometric.fAccept = false
		assertNull(vController.unlockWithBiometric())
		assertNull(vController.error)
		vBiometric.fAccept      = true
		vBiometric.fInvalidated = true
		assertNull(vController.unlockWithBiometric())
		assertNotNull(vController.error)
		assertFalse(vController.biometricReady)
	}
}

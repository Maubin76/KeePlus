package fr.maubin.keeplus.desktop

import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.AppScreen
import fr.maubin.keeplus.ui.BiometricInvalidated
import fr.maubin.keeplus.ui.BiometricUnlock
import fr.maubin.keeplus.ui.kHiddenMillis
import fr.maubin.keeplus.ui.kIdleMillis
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

/*
	Empreinte simulée : garde le mot de passe en clair (test seulement).
	fSecret      : mot de passe gardé ; null si inactive
	fAccept      : l'utilisateur accepte la demande d'empreinte
	fInvalidated : clé invalidée (empreinte ajoutée)
	fOffered     : activation déjà proposée
*/
private class FakeBiometric : BiometricUnlock
{
	var fSecret      : String? = null
	var fAccept      : Boolean = true
	var fInvalidated : Boolean = false
	var fOffered     : Boolean = false

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

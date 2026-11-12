package fr.maubin.keeplus

import fr.maubin.keeplus.link.callPhones
import fr.maubin.keeplus.link.SyncCallListener
import fr.maubin.keeplus.link.NetworkId
import fr.maubin.keeplus.link.toBase64
import fr.maubin.keeplus.sync.PcSync
import fr.maubin.keeplus.sync.PhoneSync
import fr.maubin.keeplus.sync.SyncRequest
import fr.maubin.keeplus.sync.VaultFile
import fr.maubin.keeplus.sync.answerSync
import fr.maubin.keeplus.sync.kActAdopt
import fr.maubin.keeplus.sync.kActMerge
import fr.maubin.keeplus.sync.kActSame
import fr.maubin.keeplus.sync.kActStored
import fr.maubin.keeplus.sync.kOpSync
import fr.maubin.keeplus.sync.vaultFingerprint
import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.VaultStore
import fr.maubin.keeplus.vault.createVault
import fr.maubin.keeplus.vault.decodeVault
import fr.maubin.keeplus.vault.encodeVault
import fr.maubin.keeplus.vault.mergeVaults
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress

/*
	Coffre en mémoire, pour la table de décision.
	fBytes : contenu ; null sans coffre
*/
private class MemoryFile(var fBytes: ByteArray?) : VaultFile
{
	/*
		Contenu actuel.
	*/
	override fun read(): ByteArray? = fBytes

	/*
		Remplace si l'empreinte attendue correspond.
		inBytes    : nouveau contenu
		inExpected : empreinte attendue
	*/
	override fun replace(
		inBytes    : ByteArray,
		inExpected : String?
	): Boolean
	{
		if (fBytes?.let(::vaultFingerprint) != inExpected) return false
		fBytes = inBytes
		return true
	}
}

/*
	Tests de la synchronisation des coffres : décision du PC, puis échanges
	réels entre un PC et un téléphone sur la boucle locale.
*/
class SyncTest
{
	@get:Rule
	val fFolder = TemporaryFolder()														// dossier temporaire

	private val kPassword = "mot de passe commun"											// mot de passe maître des deux appareils
	private val kHome     = NetworkId.wifi("Maison")!!										// Wi-Fi de la maison
	private var fPc       : PcSync? = null													// serveur du PC démarré

	/*
		Arrêt du serveur du PC.
	*/
	@After
	fun stop()
	{
		fPc?.stop()
	}

	/*
		Décision du PC, situation par situation (B : dernier coffre commun).
	*/
	@Test
	fun decisions()
	{
		val vBase  = encodeVault(createVault("Coffre", kPassword))							// coffre commun
		val vPcNew = encodeVault(createVault("Coffre", kPassword))							// coffre modifié sur le PC
		val vPhNew = encodeVault(createVault("Coffre", kPassword))							// coffre modifié sur le téléphone
		/*
			Demande du téléphone.
			inPhone  : son coffre
			inBase   : dernier coffre commun
			inMerged : coffre du PC déjà fusionné
		*/
		fun request(inPhone: ByteArray?, inBase: ByteArray?, inMerged: ByteArray? = null) =
			SyncRequest(kOpSync, vault = inPhone?.let(::toBase64), fingerprint = inPhone?.let(::vaultFingerprint), base = inBase?.let(::vaultFingerprint), merged = inMerged?.let(::vaultFingerprint))
		assertEquals(kActSame, answerSync(MemoryFile(vBase), request(vBase, vBase)).action)
		assertEquals(kActAdopt, answerSync(MemoryFile(vPcNew), request(vBase, vBase)).action)
		assertEquals(kActAdopt, answerSync(MemoryFile(vBase), request(null, null)).action)
		val vStored = MemoryFile(vBase)																// PC inchangé
		assertEquals(kActStored, answerSync(vStored, request(vPhNew, vBase)).action)
		assertArrayEquals(vPhNew, vStored.fBytes)
		assertEquals(kActStored, answerSync(MemoryFile(null), request(vPhNew, null)).action)
		assertEquals(kActMerge, answerSync(MemoryFile(vPcNew), request(vPhNew, vBase)).action)
		assertEquals(kActMerge, answerSync(MemoryFile(vPcNew), request(vPhNew, null)).action)
		assertEquals(kActStored, answerSync(MemoryFile(vPcNew), request(vPhNew, vBase, vPcNew)).action)
	}

	/*
		Échanges réels : appairage, téléphone sans coffre qui prend celui du PC,
		modification d'un seul côté, puis des deux côtés (fusion au
		déverrouillage du téléphone, renvoi au PC).
	*/
	@Test
	fun pairAndSync() = runBlocking {
		val vPcStore    = VaultStore(fFolder.root.resolve("pc/coffre.kdbx"))						// coffre du PC
		val vPhoneStore = VaultStore(fFolder.root.resolve("telephone/coffre.kdbx"))			// coffre du téléphone
		var vPcVault    = vPcStore.create("Coffre", kPassword).save(EntryDraft(title = "Gmail"))	// coffre du PC
		assertNull(vPcStore.save(vPcVault))
		var vStoredBy   : String? = null															// téléphone qui a remplacé le coffre du PC
		val vPc         = PcSync(
			fFile      = fFolder.root.resolve("pc/appareils.json"),
			fVault     = vPcStore,
			fName      = "PC du bureau",
			fVersion   = "test",
			fNetworks  = { listOf(kHome) },
			fAddresses = { listOf("127.0.0.1") },
			fOnStored  = { vStoredBy = it },
			fPort      = 0,
			fBeacon    = 0,
			fBind      = InetAddress.getLoopbackAddress()
		)																							// PC
		fPc = vPc
		val vPhone      = PhoneSync(fFolder.root.resolve("telephone"), vPhoneStore, "Téléphone", "test")	// téléphone

		// Appairage.
		vPc.startPairing()
		assertNull(vPhone.pair(vPc.state.value.offer!!, kHome))
		assertEquals(listOf("Téléphone"), vPc.state.value.phones.map { it.name })
		assertEquals(listOf("PC du bureau"), vPhone.peers().pcs.map { it.name })
		assertTrue(kHome in vPc.state.value.home)

		// Téléphone sans coffre : il prend celui du PC.
		var vReport = vPhone.syncAll { emptyList() }												// bilan
		assertEquals(listOf("PC du bureau"), vReport.reached)
		assertTrue(vReport.took)
		assertArrayEquals(vPcStore.read(), vPhoneStore.read())

		// Modification sur le téléphone : le PC la prend.
		var vPhoneVault = vPhoneStore.unlock(kPassword).save(EntryDraft(title = "Netflix"))		// coffre du téléphone
		assertNull(vPhoneStore.save(vPhoneVault))
		vReport = vPhone.syncAll { emptyList() }
		assertEquals("Téléphone", vStoredBy)
		assertEquals(listOf("Gmail", "Netflix"), titles(vPcStore))

		// Modification sur le PC : le téléphone la prend.
		vPcVault = Vault(decodeVault(vPcStore.read()!!, kPassword)).save(EntryDraft(title = "Impôts"))
		vPcStore.accept(vPcStore.read()!!)
		assertNull(vPcStore.save(vPcVault))
		vReport = vPhone.syncAll { emptyList() }
		assertTrue(vReport.took)
		assertEquals(listOf("Gmail", "Impôts", "Netflix"), titles(vPhoneStore))

		// Modifications des deux côtés : fusion au déverrouillage du téléphone, puis renvoi.
		vPcVault = Vault(decodeVault(vPcStore.read()!!, kPassword)).save(EntryDraft(title = "Amazon"))
		vPcStore.accept(vPcStore.read()!!)
		assertNull(vPcStore.save(vPcVault))
		vPhoneVault = vPhoneStore.unlock(kPassword).save(EntryDraft(title = "Steam"))
		assertNull(vPhoneStore.save(vPhoneVault))
		vReport = vPhone.syncAll { emptyList() }
		assertTrue(vReport.pending)
		assertFalse(vPc.state.value.last.values.single().upToDate)
		val vPending = vPhone.pending().single()													// coffre du PC à fusionner
		val vMerged  = Vault(mergeVaults(vPhoneVault.database, decodeVault(vPending.bytes, vPhoneVault.database.credentials)).database)	// fusion
		assertNull(vPhoneStore.save(vMerged))
		vPhone.settle(vPending, true)
		vReport = vPhone.syncAll { emptyList() }
		assertTrue(!vReport.pending && vReport.errors.isEmpty())
		assertEquals(listOf("Amazon", "Gmail", "Impôts", "Netflix", "Steam"), titles(vPcStore))
		assertArrayEquals(vPcStore.read(), vPhoneStore.read())
		assertTrue(vPc.state.value.last.values.single().upToDate)
	}

	/*
		Appel du PC : le téléphone à l'écoute reçoit l'identifiant du PC ; un
		message étranger est ignoré.
	*/
	@Test
	fun callReachesPhone()
	{
		val vCalls    = java.util.concurrent.LinkedBlockingQueue<String>()							// appels reçus
		val vListener = SyncCallListener { vCalls.add(it) }										// téléphone à l'écoute
		vListener.start(0, InetAddress.getLoopbackAddress())
		try
		{
			java.net.DatagramSocket().use { vSocket ->
				val vJunk = "KEEPLUS?1".toByteArray()													// demande de découverte, pas un appel
				vSocket.send(java.net.DatagramPacket(vJunk, vJunk.size, InetAddress.getLoopbackAddress(), vListener.port))
			}
			callPhones("pc-42", listOf(InetAddress.getLoopbackAddress()), vListener.port, inRepeat = 1)
			assertEquals("pc-42", vCalls.poll(2, java.util.concurrent.TimeUnit.SECONDS))
			assertNull(vCalls.poll(300, java.util.concurrent.TimeUnit.MILLISECONDS))
		}
		finally
		{
			vListener.stop()
		}
	}

	/*
		Écriture vérifiée : un coffre arrivé d'un autre appareil n'est jamais
		écrasé ; il est rendu pour être fusionné.
	*/
	@Test
	fun saveNeverOverwritesArrivals() = runBlocking {
		val vStore = VaultStore(fFolder.root.resolve("coffre.kdbx"))							// coffre
		val vMine  = vStore.create("Coffre", kPassword).save(EntryDraft(title = "Local"))		// modification locale
		val vOther = encodeVault(Vault(decodeVault(vStore.read()!!, kPassword)).save(EntryDraft(title = "Arrivé")).database)	// coffre arrivé
		assertTrue(vStore.replace(vOther, vaultFingerprint(vStore.read()!!)))
		val vArrived = vStore.save(vMine)															// écriture refusée
		assertArrayEquals(vOther, vArrived)
		vStore.accept(vArrived!!)
		val vMerged  = Vault(mergeVaults(vMine.database, decodeVault(vArrived, kPassword)).database)	// fusion
		assertNull(vStore.save(vMerged))
		assertEquals(listOf("Arrivé", "Local"), titles(vStore))
	}

	/*
		Titres des entrées d'un coffre sur le disque.
		inStore : coffre
	*/
	private fun titles(inStore: VaultStore): List<String> = Vault(decodeVault(inStore.read()!!, kPassword)).items().map { it.title }
}

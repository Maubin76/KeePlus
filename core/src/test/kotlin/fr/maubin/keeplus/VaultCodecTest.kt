package fr.maubin.keeplus

import app.keemobile.kotpass.database.header.DatabaseHeader
import app.keemobile.kotpass.database.header.KdfParameters
import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.VaultException
import fr.maubin.keeplus.vault.createVault
import fr.maubin.keeplus.vault.decodeVault
import fr.maubin.keeplus.vault.encodeVault
import fr.maubin.keeplus.vault.writeVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/*
	Tests de lecture et d'écriture des coffres : format KDBX 4 avec Argon2id,
	aller-retour, erreurs d'ouverture, coffres écrits par KeePass.
*/
class VaultCodecTest
{
	@get:Rule
	val fFolder = TemporaryFolder()														// dossier temporaire

	/*
		Un coffre créé par KeePlus est en KDBX 4.1, avec Argon2id et 64 Mio.
	*/
	@Test
	fun createdVaultUsesArgon2id()
	{
		val vDatabase = createVault("Coffre", "maître")										// coffre neuf
		val vHeader   = vDatabase.header as DatabaseHeader.Ver4x							// en-tête KDBX 4
		val vKdf      = vHeader.kdfParameters as KdfParameters.Argon2						// dérivation de la clé
		assertEquals(4, vHeader.version.major.toInt())
		assertEquals(KdfParameters.Argon2.Variant.Argon2id, vKdf.variant)
		assertEquals(64UL * 1024UL * 1024UL, vKdf.memory)
		assertEquals("Coffre", Vault(vDatabase).name)
	}

	/*
		Aller-retour : une entrée et son mot de passe survivent au chiffrement.
	*/
	@Test
	fun roundTripKeepsEntries()
	{
		val vVault  = Vault(createVault("Coffre", "maître")).save(EntryDraft(title = "Gmail", userName = "moi", password = "s3cr3t é€"))	// coffre avec une entrée
		val vStart  = System.nanoTime()													// début du chronométrage
		val vReread = Vault(decodeVault(encodeVault(vVault.database), "maître"))			// coffre relu
		println("Aller-retour Argon2id : ${(System.nanoTime() - vStart) / 1_000_000} ms")
		val vItem   = vReread.items().single()												// entrée relue
		assertEquals("Gmail", vItem.title)
		assertEquals("s3cr3t é€", vReread.draft(vItem.id)!!.password)
	}

	/*
		Un mauvais mot de passe est signalé comme tel.
	*/
	@Test
	fun wrongPasswordIsReported()
	{
		val vBytes = encodeVault(createVault("Coffre", "maître"))							// coffre chiffré
		try
		{
			decodeVault(vBytes, "autre")
			fail("Le coffre ne devait pas s'ouvrir")
		}
		catch (vError: VaultException.WrongPassword)
		{
			assertEquals("Mot de passe incorrect.", vError.message)
		}
	}

	/*
		Un fichier quelconque n'est pas pris pour un coffre.
	*/
	@Test
	fun otherFileIsNotKeePass()
	{
		try
		{
			decodeVault("Bonjour, ceci n'est pas un coffre.".toByteArray(), "maître")
			fail("Le fichier ne devait pas s'ouvrir")
		}
		catch (vError: VaultException.NotKeePass)
		{
			assertTrue(vError.message!!.contains("KeePass"))
		}
	}

	/*
		Un coffre tronqué est signalé comme endommagé.
	*/
	@Test
	fun truncatedVaultIsCorrupted()
	{
		val vBytes = encodeVault(Vault(createVault("Coffre", "maître")).save(EntryDraft(title = "Gmail")).database)	// coffre chiffré
		try
		{
			decodeVault(vBytes.copyOf(vBytes.size - 40), "maître")
			fail("Le coffre ne devait pas s'ouvrir")
		}
		catch (vError: VaultException)
		{
			assertTrue(vError is VaultException.Corrupted)
		}
	}

	/*
		Coffres écrits par KeePass (bases de test de kotpass, mot de passe « 1 ») :
		lus, modifiés, réécrits et relus.
	*/
	@Test
	fun opensVaultsWrittenByKeePass()
	{
		for (vName in listOf("ver4_argon2.kdbx", "ver4_aes.kdbx", "groups_and_entries.kdbx"))
		{
			val vBytes  = javaClass.getResourceAsStream("/kdbx/$vName")!!.readBytes()	// coffre KeePass
			val vVault  = Vault(decodeVault(vBytes, "1"))										// coffre ouvert
			val vCount  = vVault.items().size													// entrées d'origine
			val vEdited = vVault.save(EntryDraft(title = "Ajout KeePlus", password = "x"))	// coffre modifié
			val vReread = Vault(decodeVault(encodeVault(vEdited.database), "1"))				// coffre relu
			assertEquals(vName, vCount + 1, vReread.items().size)
			assertEquals(vName, vVault.groups().map { it.name }, vReread.groups().map { it.name })
		}
	}

	/*
		Écriture sur le disque : le fichier est remplacé en entier, sans
		fichier temporaire laissé derrière.
	*/
	@Test
	fun writeReplacesFile()
	{
		val vFile  = fFolder.root.resolve("coffre.kdbx")									// fichier du coffre
		val vVault = Vault(createVault("Coffre", "maître"))									// coffre vide
		writeVault(vVault.database, vFile)
		writeVault(vVault.save(EntryDraft(title = "Gmail")).database, vFile)
		assertEquals(listOf("coffre.kdbx"), fFolder.root.list()!!.toList())
		assertEquals("Gmail", Vault(decodeVault(vFile.readBytes(), "maître")).items().single().title)
		assertFalse(fFolder.root.resolve("coffre.kdbx.tmp").exists())
	}
}

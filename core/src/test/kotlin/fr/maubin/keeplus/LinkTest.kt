package fr.maubin.keeplus

import fr.maubin.keeplus.link.Beacon
import fr.maubin.keeplus.link.DeviceInfo
import fr.maubin.keeplus.link.DiscoveryResponder
import fr.maubin.keeplus.link.LinkCipher
import fr.maubin.keeplus.link.LinkClient
import fr.maubin.keeplus.link.LinkException
import fr.maubin.keeplus.link.LinkFrame
import fr.maubin.keeplus.link.LinkServer
import fr.maubin.keeplus.link.NetworkId
import fr.maubin.keeplus.link.ReplayGuard
import fr.maubin.keeplus.link.discoverPcs
import fr.maubin.keeplus.link.isHomeNetwork
import fr.maubin.keeplus.link.kMaxClockSkewMs
import fr.maubin.keeplus.link.newLinkKey
import fr.maubin.keeplus.link.readFrame
import fr.maubin.keeplus.link.writeFrame
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress

/*
	Socle des échanges entre appareils, sur la boucle locale de la machine
	(sans réseau réel) : chiffrement authentifié, trames, garde contre le
	rejeu, présentation en clair, demande scellée d'un pair appairé, refus
	d'un appareil inconnu ou d'une mauvaise clé, découverte d'un PC, réseau
	familial.
*/
class LinkTest
{
	private val kPc     = DeviceInfo("pc-1", "PC du bureau", "windows", "1.4", 8)		// PC
	private val kPhone  = DeviceInfo("tel-1", "Pixel", "android", "1.4", 8)			// téléphone
	private val kLocal  = InetAddress.getLoopbackAddress()								// boucle locale
	private val kKey    = newLinkKey()													// clé de la paire
	private var fServer : LinkServer? = null											// serveur démarré
	private var fBeacon : DiscoveryResponder? = null									// répondeur démarré

	/*
		Arrête le serveur et le répondeur.
	*/
	@After
	fun stop()
	{
		fServer?.stop()
		fBeacon?.stop()
	}

	/*
		Serveur du PC sur la boucle locale, qui connaît la clé du téléphone et
		répond « reçu : <demande> ».
		inHellos : présentations reçues
	*/
	private fun server(inHellos: MutableList<String> = mutableListOf()): LinkServer =
		LinkServer(
			inInfo    = { kPc },
			inKeyOf   = { if (it == kPhone.id) kKey else null },
			inHandler = { "reçu : ${it.request} de ${it.peerId}" },
			inOnHello = { inInfo, _ -> inHellos += inInfo.name }
		).also { it.start(0, kLocal); fServer = it }

	/*
		Chiffrement : aller-retour ; message modifié, autre clé ou autres
		données associées : refusé.
	*/
	@Test
	fun cipher()
	{
		val vCipher = LinkCipher(kKey)														// chiffrement
		val vSealed = vCipher.seal("bonjour".toByteArray(), "aad".toByteArray())			// message scellé
		assertArrayEquals("bonjour".toByteArray(), vCipher.open(vSealed, "aad".toByteArray()))
		assertNull(vCipher.open(vSealed, "autre".toByteArray()))
		assertNull(LinkCipher(newLinkKey()).open(vSealed, "aad".toByteArray()))
		assertNull(vCipher.open(vSealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }, "aad".toByteArray()))
		assertFalse(vSealed.copyOfRange(12, vSealed.size).contentEquals(vCipher.seal("bonjour".toByteArray(), "aad".toByteArray()).copyOfRange(12, vSealed.size)))	// jamais deux fois le même chiffré
	}

	/*
		Trames : aller-retour ; flux coupé ou taille absurde : erreur.
	*/
	@Test
	fun frames()
	{
		val vOut = ByteArrayOutputStream().also { writeFrame(it, LinkFrame(from = "a", kind = "hello", body = "é")) }	// trame écrite
		assertEquals("é", readFrame(ByteArrayInputStream(vOut.toByteArray())).body)
		assertThrows { readFrame(ByteArrayInputStream(vOut.toByteArray().copyOf(6))) }
		assertThrows { readFrame(ByteArrayInputStream(byteArrayOf(0x7f, 0, 0, 0))) }
		assertThrows { readFrame(ByteArrayInputStream(ByteArray(0))) }
	}

	/*
		Rejeu : numéro vu, message trop ancien ou trop en avance : refusés.
	*/
	@Test
	fun replay()
	{
		var vNow = 1_000_000_000L																// horloge
		val vGuard = ReplayGuard { vNow }														// garde
		assertTrue(vGuard.accept(vNow, "n1"))
		assertFalse(vGuard.accept(vNow, "n1"))
		assertFalse(vGuard.accept(vNow - kMaxClockSkewMs - 1, "n2"))
		assertFalse(vGuard.accept(vNow + kMaxClockSkewMs + 1, "n3"))
		vNow += 3 * kMaxClockSkewMs
		assertTrue(vGuard.accept(vNow, "n1"))													// numéro oublié après la fenêtre
	}

	/*
		Présentation en clair et demande scellée d'un pair appairé.
	*/
	@Test
	fun helloAndSealedCall()
	{
		val vHellos = mutableListOf<String>()													// présentations reçues
		val vServer = server(vHellos)															// PC
		val vClient = LinkClient(kPhone)														// téléphone
		assertEquals(kPc, vClient.hello(kLocal.hostAddress, vServer.port))
		assertEquals(listOf("Pixel"), vHellos)
		assertEquals("reçu : {\"x\":1} de tel-1", vClient.call(kLocal.hostAddress, vServer.port, kPc.id, kKey, "{\"x\":1}"))
	}

	/*
		Refus : appareil non appairé, mauvaise clé, PC inattendu, PC absent.
	*/
	@Test
	fun refusals()
	{
		val vServer = server()																	// PC
		val vHost   = kLocal.hostAddress															// adresse
		assertRefused("non appairé") { LinkClient(kPhone.copy(id = "inconnu")).call(vHost, vServer.port, kPc.id, kKey, "{}") }
		assertRefused("clé différente") { LinkClient(kPhone).call(vHost, vServer.port, kPc.id, newLinkKey(), "{}") }
		assertRefused("autre appareil") { LinkClient(kPhone).call(vHost, vServer.port, "pc-2", kKey, "{}") }
		vServer.stop()
		assertThrows { LinkClient(kPhone).hello(vHost, vServer.port.takeIf { it > 0 } ?: 1) }
	}

	/*
		Découverte : le PC répond à la demande avec son identifiant, son nom et
		son port ; une demande d'un autre protocole est ignorée.
	*/
	@Test
	fun discovery()
	{
		val vBeacon = DiscoveryResponder { Beacon(kPc.id, kPc.name, 47801) }.also { it.start(0, kLocal); fBeacon = it }	// répondeur
		val vFound  = discoverPcs(listOf(kLocal), vBeacon.port, 1_000)							// PC trouvés
		assertEquals(listOf(Beacon(kPc.id, kPc.name, 47801)), vFound.map { it.beacon })
		assertEquals(kLocal.hostAddress, vFound.single().address)
	}

	/*
		Réseau familial : nom du Wi-Fi (guillemets d'Android ôtés, nom masqué
		ignoré), box par son adresse matérielle normalisée.
	*/
	@Test
	fun homeNetwork()
	{
		val vHome = listOf(NetworkId.wifi("Maison")!!, NetworkId.box("AA-BB-CC-DD-EE-01")!!)	// réseaux familiaux
		assertTrue(isHomeNetwork(listOfNotNull(NetworkId.wifi("\"Maison\"")), vHome))
		assertTrue(isHomeNetwork(listOfNotNull(NetworkId.box("aa:bb:cc:dd:ee:01")), vHome))
		assertFalse(isHomeNetwork(listOfNotNull(NetworkId.wifi("Hôtel")), vHome))
		assertFalse(isHomeNetwork(emptyList(), vHome))
		assertNull(NetworkId.wifi("<unknown ssid>"))
		assertNull(NetworkId.box("ff-ff-ff-ff-ff-ff"))
		assertNull(NetworkId.box("pas une adresse"))
	}

	/*
		Vérifie qu'une action échoue.
		inAction : action
	*/
	private fun assertThrows(inAction: () -> Unit)
	{
		try { inAction(); fail("aucune erreur") } catch (inError: IOException) { }
	}

	/*
		Vérifie qu'une action est refusée avec une raison donnée.
		inReason : extrait attendu de la raison
		inAction : action
	*/
	private fun assertRefused(
		inReason : String,
		inAction : () -> Unit
	)
	{
		try { inAction(); fail("aucun refus") } catch (inError: LinkException) { assertTrue(inError.message, inError.message!!.contains(inReason)) }
	}
}

package fr.maubin.keeplus.link

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

const val kLinkKeyBytes = 32														// clé secrète d'une paire (AES-256)
private const val kNonceBytes = 12													// numéro à usage unique d'AES-GCM
private const val kTagBits    = 128													// étiquette d'authentification

private val kRandom = SecureRandom()												// aléa cryptographique

/*
	Nouvelle clé secrète d'une paire PC ↔ téléphone, tirée au hasard.
*/
fun newLinkKey(): ByteArray = ByteArray(kLinkKeyBytes).also { kRandom.nextBytes(it) }

/*
	Octets au hasard (numéros à usage unique, identifiants).
	inSize : nombre d'octets
*/
fun randomBytes(inSize: Int): ByteArray = ByteArray(inSize).also { kRandom.nextBytes(it) }

/*
	Octets en texte (base64 sans remplissage, sûr dans un QR code et en JSON).
	inBytes : octets
*/
fun toBase64(inBytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(inBytes)

/*
	Octets lus d'un texte base64 ; null s'il est illisible.
	inText : texte
*/
fun fromBase64(inText: String): ByteArray? = runCatching { Base64.getUrlDecoder().decode(inText) }.getOrNull()

/*
	Chiffrement authentifié des messages d'une paire (AES-GCM) : un message
	scellé ne peut être lu ni modifié sans la clé ; les données associées
	(expéditeur, date, numéro à usage unique) sont authentifiées sans être
	chiffrées, ce qui empêche de rejouer un message dans un autre contexte.
	inKey : clé secrète de la paire
*/
class LinkCipher(inKey: ByteArray)
{
	private val fKey = SecretKeySpec(inKey.copyOf(), "AES")							// clé AES

	init
	{
		require(inKey.size == kLinkKeyBytes) { "clé de ${inKey.size} octets" }
	}

	/*
		Scelle un message : numéro à usage unique suivi du chiffré.
		inPlain : message en clair
		inAad   : données associées authentifiées
	*/
	fun seal(
		inPlain : ByteArray,
		inAad   : ByteArray
	): ByteArray
	{
		val vNonce  = randomBytes(kNonceBytes)											// numéro à usage unique
		val vCipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, fKey, GCMParameterSpec(kTagBits, vNonce)); updateAAD(inAad) }	// chiffreur
		return vNonce + vCipher.doFinal(inPlain)
	}

	/*
		Ouvre un message scellé ; null s'il a été modifié, scellé avec une autre
		clé ou d'autres données associées.
		inSealed : message scellé
		inAad    : données associées attendues
	*/
	fun open(
		inSealed : ByteArray,
		inAad    : ByteArray
	): ByteArray?
	{
		if (inSealed.size < kNonceBytes + kTagBits / 8) return null
		return try
		{
			val vCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
				init(Cipher.DECRYPT_MODE, fKey, GCMParameterSpec(kTagBits, inSealed, 0, kNonceBytes))
				updateAAD(inAad)
			}																		// déchiffreur
			vCipher.doFinal(inSealed, kNonceBytes, inSealed.size - kNonceBytes)
		}
		catch (inError: AEADBadTagException)
		{
			null
		}
	}
}

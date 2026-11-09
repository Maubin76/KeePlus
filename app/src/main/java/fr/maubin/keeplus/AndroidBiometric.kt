package fr.maubin.keeplus

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import fr.maubin.keeplus.ui.BiometricInvalidated
import fr.maubin.keeplus.ui.BiometricUnlock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

private const val kKeyAlias   = "keeplus_biometric"										// clé du Keystore
private const val kPrefs      = "biometric"												// fichier des réglages (stockage privé, sans sauvegarde)
private const val kSecret     = "secret"													// mot de passe chiffré (base64)
private const val kIv         = "iv"														// vecteur d'initialisation (base64)
private const val kOffered    = "offered"												// activation déjà proposée
private const val kTransform  = "AES/GCM/NoPadding"										// chiffrement
private const val kTagBits    = 128														// taille de l'étiquette GCM

/*
	Déverrouillage par empreinte : le mot de passe maître est chiffré en
	AES-GCM par une clé du Keystore Android qui exige une authentification
	biométrique forte à chaque usage et qui est invalidée si une empreinte est
	ajoutée. Seuls le texte chiffré et le vecteur sont gardés, dans le
	stockage privé de l'appli (exclu des sauvegardes).
	fActivity : activité qui affiche la demande d'empreinte
*/
class AndroidBiometric(private val fActivity: FragmentActivity) : BiometricUnlock
{
	private val fPrefs = fActivity.getSharedPreferences(kPrefs, Context.MODE_PRIVATE)

	/*
		Capteur présent et empreinte enregistrée.
	*/
	override val available: Boolean
		get() = BiometricManager.from(fActivity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

	/*
		Mot de passe gardé pour l'empreinte.
	*/
	override val enabled: Boolean
		get() = fPrefs.contains(kSecret) && keyStore().containsAlias(kKeyAlias)

	/*
		Activation déjà proposée.
	*/
	override val offered: Boolean
		get() = fPrefs.getBoolean(kOffered, false)

	/*
		Note que l'activation a été proposée.
	*/
	override fun markOffered()
	{
		fPrefs.edit().putBoolean(kOffered, true).apply()
	}

	/*
		Active l'empreinte : clé neuve, empreinte demandée, mot de passe chiffré
		et gardé.
		inPassword : mot de passe maître
	*/
	override suspend fun enable(inPassword: CharSequence): Boolean
	{
		disable()
		val vCipher = Cipher.getInstance(kTransform).apply { init(Cipher.ENCRYPT_MODE, newKey()) }	// chiffrement
		val vReady  = prompt(vCipher, "Activer l'empreinte", "Confirmez avec votre empreinte") ?: return false	// chiffrement autorisé
		val vPlain  = toBytes(inPassword)														// mot de passe en UTF-8
		try
		{
			val vSecret = vReady.doFinal(vPlain)													// mot de passe chiffré
			fPrefs.edit()
				.putString(kSecret, Base64.encodeToString(vSecret, Base64.NO_WRAP))
				.putString(kIv, Base64.encodeToString(vReady.iv, Base64.NO_WRAP))
				.apply()
			return true
		}
		finally
		{
			vPlain.fill(0)
		}
	}

	/*
		Demande l'empreinte et déchiffre le mot de passe ; null si annulé.
	*/
	override suspend fun unlock(): CharArray?
	{
		val vSecret = fPrefs.getString(kSecret, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null	// mot de passe chiffré
		val vIv     = fPrefs.getString(kIv, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null		// vecteur
		val vCipher = try
		{
			val vKey = keyStore().getKey(kKeyAlias, null) as? SecretKey ?: throw BiometricInvalidated()	// clé
			Cipher.getInstance(kTransform).apply { init(Cipher.DECRYPT_MODE, vKey, GCMParameterSpec(kTagBits, vIv)) }
		}
		catch (vError: KeyPermanentlyInvalidatedException)
		{
			throw BiometricInvalidated()
		}																					// déchiffrement
		val vReady  = prompt(vCipher, "Déverrouiller KeePlus", "Touchez le capteur d'empreinte") ?: return null	// déchiffrement autorisé
		val vPlain  = vReady.doFinal(vSecret)													// mot de passe en UTF-8
		try
		{
			val vChars = Charsets.UTF_8.decode(ByteBuffer.wrap(vPlain))						// mot de passe
			return CharArray(vChars.remaining()).also { vChars.get(it); vChars.clear(); while (vChars.hasRemaining()) vChars.put(Char(0)) }
		}
		finally
		{
			vPlain.fill(0)
		}
	}

	/*
		Oublie le mot de passe chiffré et efface la clé.
	*/
	override fun disable()
	{
		fPrefs.edit().remove(kSecret).remove(kIv).apply()
		runCatching { keyStore().deleteEntry(kKeyAlias) }
	}

	/*
		Affiche la demande d'empreinte liée au chiffrement ; renvoie le
		chiffrement autorisé, ou null si l'utilisateur annule.
		inCipher   : chiffrement à autoriser
		inTitle    : titre de la demande
		inSubtitle : sous-titre
	*/
	private suspend fun prompt(
		inCipher   : Cipher,
		inTitle    : String,
		inSubtitle : String
	): Cipher? = suspendCancellableCoroutine { vContinuation ->
		val vCallback = object : BiometricPrompt.AuthenticationCallback()
		{
			/*
				Empreinte reconnue.
				inResult : résultat, avec le chiffrement autorisé
			*/
			override fun onAuthenticationSucceeded(inResult: BiometricPrompt.AuthenticationResult)
			{
				if (vContinuation.isActive) vContinuation.resume(inResult.cryptoObject?.cipher)
			}

			/*
				Abandon ou erreur définitive (trop d'essais, annulation).
				inCode    : code d'erreur
				inMessage : message du système
			*/
			override fun onAuthenticationError(
				inCode    : Int,
				inMessage : CharSequence
			)
			{
				if (vContinuation.isActive) vContinuation.resume(null)
			}
		}																					// réponses de la demande
		val vPrompt = BiometricPrompt(fActivity, ContextCompat.getMainExecutor(fActivity), vCallback)	// demande d'empreinte
		val vInfo   = BiometricPrompt.PromptInfo.Builder()
			.setTitle(inTitle)
			.setSubtitle(inSubtitle)
			.setNegativeButtonText("Mot de passe")
			.setAllowedAuthenticators(BIOMETRIC_STRONG)
			.build()																		// textes de la demande
		vPrompt.authenticate(vInfo, BiometricPrompt.CryptoObject(inCipher))
		vContinuation.invokeOnCancellation { vPrompt.cancelAuthentication() }
	}

	/*
		Crée une clé AES-256 neuve dans le Keystore : empreinte forte exigée à
		chaque usage, invalidée si une empreinte est ajoutée.
	*/
	private fun newKey(): SecretKey
	{
		val vSpec = KeyGenParameterSpec.Builder(kKeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
			.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
			.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
			.setKeySize(256)
			.setUserAuthenticationRequired(true)
			.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
			.setInvalidatedByBiometricEnrollment(true)
			.build()																		// réglages de la clé
		return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
			init(vSpec)
			generateKey()
		}
	}

	/*
		Keystore Android, chargé.
	*/
	private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

	/*
		Mot de passe en UTF-8, sans passer par une String.
		inText : mot de passe
	*/
	private fun toBytes(inText: CharSequence): ByteArray
	{
		val vBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(inText))						// mot de passe encodé
		val vBytes  = ByteArray(vBuffer.remaining()).also { vBuffer.get(it) }				// copie exacte
		vBuffer.clear()
		while (vBuffer.hasRemaining()) vBuffer.put(0)
		return vBytes
	}
}

package fr.maubin.keeplus.vault

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.header.KdfParameters
import app.keemobile.kotpass.errors.CryptoError
import app.keemobile.kotpass.errors.FormatError
import app.keemobile.kotpass.models.Meta
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.CharBuffer
import java.security.SecureRandom

private const val kGenerator      = "KeePlus"										// application qui a écrit le coffre
private const val kArgonMemory    = 67_108_864UL						// mémoire d'Argon2 (64 Mio)
private const val kArgonLanes     = 2U												// parallélisme d'Argon2
private const val kArgonPasses    = 3UL												// itérations d'Argon2
private const val kArgonVersion   = 0x13U											// version 1.3 d'Argon2
private const val kHistoryItems   = 10												// versions gardées par entrée

private val kRandom    = SecureRandom()											// sels, graines et vecteurs
private val kSignature = byteArrayOf(0x03, 0xD9.toByte(), 0xA2.toByte(), 0x9A.toByte(), 0x67, 0xFB.toByte(), 0x4B, 0xB5.toByte())	// début d'un fichier KeePass 2

/*
	Erreur d'ouverture d'un coffre, avec un message prêt à afficher.
*/
sealed class VaultException(
	inMessage : String,																// message pour l'utilisateur
	inCause   : Throwable? = null													// erreur d'origine
) : Exception(inMessage, inCause)
{
	/*
		Le mot de passe maître ne correspond pas au coffre.
	*/
	class WrongPassword : VaultException("Mot de passe incorrect.")

	/*
		Le fichier n'est pas un coffre KeePass.
	*/
	class NotKeePass(inCause: Throwable) : VaultException("Ce fichier n'est pas un coffre KeePass.", inCause)

	/*
		Le coffre est d'une version que KeePlus ne lit pas.
	*/
	class Unsupported(inCause: Throwable) : VaultException("Cette version de coffre KeePass n'est pas prise en charge.", inCause)

	/*
		Le coffre est endommagé (fichier tronqué, bloc altéré).
	*/
	class Corrupted(inCause: Throwable) : VaultException("Le coffre est endommagé.", inCause)
}

/*
	Clé d'un coffre à partir du mot de passe maître. Le mot de passe est
	converti sans passer par une String (qui resterait en mémoire), et la copie
	intermédiaire est effacée.
	inPassword : mot de passe maître
*/
fun vaultCredentials(inPassword: CharSequence): Credentials
{
	val vBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(inPassword))					// mot de passe en UTF-8
	val vBytes  = ByteArray(vBuffer.remaining()).also { vBuffer.get(it) }				// copie exacte
	vBuffer.clear()
	while (vBuffer.hasRemaining()) vBuffer.put(0)
	return Credentials.from(EncryptedValue.fromBinary(vBytes))						// fromBinary brouille vBytes en place
}

/*
	Crée un coffre vide au format KDBX 4 : Argon2id, AES-256, corbeille et
	historique activés.
	inName     : nom du coffre (nom du groupe racine)
	inPassword : mot de passe maître
*/
fun createVault(
	inName     : String,
	inPassword : CharSequence
): KeePassDatabase
{
	val vMeta     = Meta(
		generator         = kGenerator,
		name              = inName,
		recycleBinEnabled = true,
		historyMaxItems   = kHistoryItems
	)																				// métadonnées
	val vDatabase = KeePassDatabase.Ver4x.create(inName, vMeta, vaultCredentials(inPassword), kRandom)	// coffre vide
	return vDatabase.copy(header = vDatabase.header.copy(kdfParameters = argon2id()))
}

/*
	Paramètres de dérivation de la clé : Argon2id, avec un sel neuf.
*/
private fun argon2id(): KdfParameters.Argon2 =
	KdfParameters.Argon2(
		variant        = KdfParameters.Argon2.Variant.Argon2id,
		salt           = ByteArray(32).also { kRandom.nextBytes(it) }.toByteString(),
		parallelism    = kArgonLanes,
		memory         = kArgonMemory,
		iterations     = kArgonPasses,
		version        = kArgonVersion,
		secretKey      = null,
		associatedData = null
	)

/*
	Ouvre un coffre à partir de son contenu chiffré.
	inBytes    : contenu du fichier .kdbx
	inPassword : mot de passe maître
	Lève une VaultException si le coffre ne s'ouvre pas.
*/
fun decodeVault(
	inBytes    : ByteArray,
	inPassword : CharSequence
): KeePassDatabase = decodeVault(inBytes, vaultCredentials(inPassword))

/*
	Ouvre un coffre avec une clé déjà calculée (coffre déjà ouvert, relecture
	de contrôle, fusion avec la version d'un autre appareil).
	inBytes       : contenu du fichier .kdbx
	inCredentials : clé du coffre
	Lève une VaultException si le coffre ne s'ouvre pas.
*/
fun decodeVault(
	inBytes       : ByteArray,
	inCredentials : Credentials
): KeePassDatabase =
	try
	{
		if (!isKeePassFile(inBytes)) throw FormatError.UnknownFormat("Signature KeePass absente.")
		ByteArrayInputStream(inBytes).use { KeePassDatabase.decode(it, inCredentials) }
	}
	catch (vError: CryptoError.InvalidKey)
	{
		throw VaultException.WrongPassword()
	}
	catch (vError: FormatError.UnknownFormat)
	{
		throw VaultException.NotKeePass(vError)
	}
	catch (vError: FormatError.UnsupportedVersion)
	{
		throw VaultException.Unsupported(vError)
	}
	catch (vError: Exception)
	{
		throw VaultException.Corrupted(vError)
	}

/*
	Vrai si le contenu commence par la signature des fichiers KeePass
	(9AA2D903 puis B54BFB67, en petit-boutiste).
	inBytes : contenu du fichier
*/
fun isKeePassFile(inBytes: ByteArray): Boolean =
	inBytes.size >= kSignature.size && kSignature.indices.all { inBytes[it] == kSignature[it] }

/*
	Chiffre un coffre (nouveaux vecteurs et sels à chaque fois).
	inDatabase : coffre à écrire
*/
fun encodeVault(inDatabase: KeePassDatabase): ByteArray =
	ByteArrayOutputStream().use { vOut ->
		inDatabase.encode(vOut, random = kRandom)
		vOut.toByteArray()
	}

/*
	Écrit un coffre sur le disque sans risque de le perdre : écriture dans un
	fichier temporaire du même dossier, relecture de contrôle, puis
	remplacement du fichier d'origine.
	inDatabase : coffre à écrire
	inFile     : fichier .kdbx
	inCheck    : relire le contenu chiffré avant de remplacer le fichier
*/
fun writeVault(
	inDatabase : KeePassDatabase,
	inFile     : File,
	inCheck    : Boolean = true
)
{
	val vBytes = encodeVault(inDatabase)												// contenu chiffré
	if (inCheck) decodeVault(vBytes, inDatabase.credentials)							// relecture : lève une erreur si illisible
	inFile.absoluteFile.parentFile?.mkdirs()
	val vTemp  = File(inFile.absoluteFile.parentFile, inFile.name + ".tmp")			// fichier temporaire
	vTemp.outputStream().use { vOut ->
		vOut.write(vBytes)
		vOut.fd.sync()
	}
	java.nio.file.Files.move(
		vTemp.toPath(),
		inFile.toPath(),
		java.nio.file.StandardCopyOption.REPLACE_EXISTING,
		java.nio.file.StandardCopyOption.ATOMIC_MOVE
	)
}

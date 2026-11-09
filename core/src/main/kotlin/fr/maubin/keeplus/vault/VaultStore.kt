package fr.maubin.keeplus.vault

import fr.maubin.keeplus.sync.VaultFile
import fr.maubin.keeplus.sync.vaultFingerprint
import fr.maubin.keeplus.sync.writeAtomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val kFileLock = Any()															// accès au fichier du coffre, pour tout le processus (appli et tâche de fond)

/*
	Emplacement du coffre de l'appareil : un seul fichier .kdbx, propre à
	l'appli (stockage privé sur Android, %APPDATA%\KeePlus sur PC). Les
	opérations coûteuses (Argon2, écriture) se font hors du fil de
	l'interface.
	Le fichier peut être remplacé par la synchronisation (voir VaultFile) :
	l'appli retient l'empreinte du dernier coffre qu'elle a lu ou écrit, et
	n'écrase jamais un coffre arrivé entre-temps (voir save).
	fFile  : fichier du coffre
	fKnown : empreinte du dernier coffre lu ou écrit par l'appli ; null avant
*/
class VaultStore(private val fFile: File) : VaultFile
{
	@Volatile private var fKnown: String? = null

	/*
		Vrai si un coffre existe déjà sur l'appareil.
	*/
	fun exists(): Boolean = fFile.isFile && fFile.length() > 0

	/*
		Crée le coffre de l'appareil et l'écrit sur le disque.
		inName     : nom du coffre
		inPassword : mot de passe maître
	*/
	suspend fun create(
		inName     : String,
		inPassword : CharSequence
	): Vault = withContext(Dispatchers.Default) {
		val vVault = Vault(createVault(inName, inPassword))								// coffre neuf
		write(checkedBytes(vVault))
		vVault
	}

	/*
		Ouvre le coffre de l'appareil.
		inPassword : mot de passe maître
		Lève une VaultException si le coffre ne s'ouvre pas.
	*/
	suspend fun unlock(inPassword: CharSequence): Vault = withContext(Dispatchers.Default) {
		val vBytes = read() ?: throw VaultException.Corrupted(IllegalStateException("coffre absent"))	// contenu du fichier
		Vault(decodeVault(vBytes, inPassword)).also { fKnown = vaultFingerprint(vBytes) }
	}

	/*
		Reprend un coffre existant (KeePassXC, KeePassDX…) comme coffre de
		l'appareil : il est d'abord ouvert avec son mot de passe, puis copié tel
		quel, octet pour octet.
		inBytes    : contenu du fichier .kdbx
		inPassword : son mot de passe maître
		Lève une VaultException si le coffre ne s'ouvre pas.
	*/
	suspend fun import(
		inBytes    : ByteArray,
		inPassword : CharSequence
	): Vault = withContext(Dispatchers.Default) {
		val vVault = Vault(decodeVault(inBytes, inPassword))								// coffre ouvert
		write(inBytes)
		vVault
	}

	/*
		Enregistre le coffre après une modification, seulement si le fichier
		est encore celui que l'appli connaît. Sinon (coffre arrivé d'un autre
		appareil entre-temps), rien n'est écrit et le coffre arrivé est rendu :
		à fusionner, puis à enregistrer de nouveau.
		inVault : coffre à écrire
		Rend null si le coffre est écrit, sinon le contenu du fichier arrivé.
	*/
	suspend fun save(inVault: Vault): ByteArray? = withContext(Dispatchers.IO) {
		val vBytes = checkedBytes(inVault)													// contenu chiffré et relu
		synchronized(kFileLock) {
			val vNow = read()																// fichier actuel
			if (vNow != null && fKnown != null && vaultFingerprint(vNow) != fKnown) return@withContext vNow
			write(vBytes)
		}
		null
	}

	/*
		Contenu du fichier s'il a été remplacé par un autre appareil depuis le
		dernier coffre lu ou écrit par l'appli ; null s'il n'a pas changé.
	*/
	fun changedOnDisk(): ByteArray? = synchronized(kFileLock) {
		read()?.takeIf { fKnown != null && vaultFingerprint(it) != fKnown }
	}

	/*
		L'appli a pris en compte ce contenu du fichier (rechargé ou fusionné).
		inBytes : contenu pris en compte
	*/
	fun accept(inBytes: ByteArray)
	{
		fKnown = vaultFingerprint(inBytes)
	}

	/*
		Octets du coffre ; null si l'appareil n'a pas de coffre.
	*/
	override fun read(): ByteArray? = synchronized(kFileLock) { if (exists()) fFile.readBytes() else null }

	/*
		Remplace le coffre par celui d'un autre appareil, seulement s'il n'a
		pas changé depuis sa lecture.
		inBytes    : nouveau contenu
		inExpected : empreinte attendue du coffre actuel ; null s'il ne doit pas y en avoir
	*/
	override fun replace(
		inBytes    : ByteArray,
		inExpected : String?
	): Boolean = synchronized(kFileLock) {
		val vNow = read()?.let(::vaultFingerprint)											// empreinte actuelle
		if (vNow != inExpected) return false
		writeAtomic(fFile, inBytes)
		true
	}

	/*
		Écrit un contenu et le retient comme connu.
		inBytes : contenu
	*/
	private fun write(inBytes: ByteArray) = synchronized(kFileLock) {
		writeAtomic(fFile, inBytes)
		fKnown = vaultFingerprint(inBytes)
	}

	/*
		Coffre chiffré, relu pour contrôle avant toute écriture.
		inVault : coffre
	*/
	private fun checkedBytes(inVault: Vault): ByteArray =
		encodeVault(inVault.database).also { decodeVault(it, inVault.database.credentials) }
}

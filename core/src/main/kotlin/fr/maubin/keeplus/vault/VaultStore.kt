package fr.maubin.keeplus.vault

import fr.maubin.keeplus.sync.VaultFile
import fr.maubin.keeplus.sync.vaultFingerprint
import fr.maubin.keeplus.sync.writeAtomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val kFileLock = Any()															// accès au fichier du coffre, pour tout le processus (appli et tâche de fond)

/*
	Coffre de l'appareil : un seul fichier .kdbx, à l'emplacement par défaut
	de l'appli (stockage privé sur Android, %APPDATA%\KeePlus sur PC) ou dans
	un dossier choisi (voir VaultLocation, moveTo). Les
	opérations coûteuses (Argon2, écriture) se font hors du fil de
	l'interface.
	Le fichier peut être remplacé par la synchronisation (voir VaultFile) :
	l'appli retient l'empreinte du dernier coffre qu'elle a lu ou écrit, et
	n'écrase jamais un coffre arrivé entre-temps (voir save).
	Avant la première modification du jour, le coffre est sauvegardé.
	fLocation : emplacement du fichier du coffre
	fBackups  : sauvegardes automatiques ; null sans
	fKnown    : empreinte du dernier coffre lu ou écrit par l'appli ; null avant
*/
class VaultStore(
	val fLocation         : VaultLocation,
	val fBackups          : VaultBackups? = null
) : VaultFile
{
	@Volatile private var fKnown: String? = null

	/*
		Coffre à un emplacement fixe.
		inFile    : fichier du coffre
		inBackups : sauvegardes automatiques ; null sans
	*/
	constructor(
		inFile    : File,
		inBackups : VaultBackups? = null
	) : this(VaultLocation(inFile), inBackups)

	/*
		Fichier du coffre (relu à chaque accès : il suit un déplacement).
	*/
	val file: File get() = fLocation.file

	/*
		Vrai si un coffre existe déjà sur l'appareil.
	*/
	fun exists(): Boolean = file.let { it.isFile && it.length() > 0 }

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
	override fun read(): ByteArray? = synchronized(kFileLock) { file.takeIf { it.isFile && it.length() > 0 }?.readBytes() }

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
		val vNow = read()																	// coffre actuel
		if (vNow?.let(::vaultFingerprint) != inExpected) return false
		vNow?.let { fBackups?.daily(it) }
		writeAtomic(file, inBytes)
		true
	}

	/*
		Remet une sauvegarde à la place du coffre (le coffre actuel est
		d'abord sauvegardé, si ce n'est déjà fait aujourd'hui). Le coffre
		restauré s'ouvre avec le mot de passe maître qu'il avait.
		inBackup : sauvegarde
	*/
	fun restore(inBackup: VaultBackup)
	{
		val vBytes = inBackup.file.readBytes()												// contenu de la sauvegarde
		if (!isKeePassFile(vBytes)) throw VaultException.NotKeePass(IllegalStateException(inBackup.file.name))
		write(vBytes)
	}

	/*
		Déplace le coffre dans un autre dossier (ou y place le futur coffre
		s'il n'y en a pas encore) : copie, relue et comparée, puis bascule de
		l'emplacement, et seulement alors suppression de l'ancien fichier. Un
		coffre déjà présent dans le dossier n'est jamais écrasé ; si
		l'appareil n'a pas encore de coffre, il devient le coffre de
		l'appareil (coffre KeePlus d'une clé USB, d'un autre dossier…).
		inFolder : dossier choisi
		Rend null si c'est fait, sinon la raison de l'échec.
	*/
	fun moveTo(inFolder: File): String? = synchronized(kFileLock) {
		if (!fLocation.movable) return "Emplacement fixe."
		val vTarget  = File(inFolder, kVaultFileName).absoluteFile							// nouveau fichier
		val vCurrent = file.absoluteFile														// fichier actuel
		if (vTarget == vCurrent) return null
		if (!exists() && vTarget.isFile && runCatching { isKeePassFile(vTarget.readBytes()) }.getOrDefault(false))
		{
			fLocation.point(vTarget)
			fKnown = null
			return null
		}
		if (vTarget.exists()) return "Ce dossier contient déjà un fichier $kVaultFileName : choisissez un autre dossier."
		try
		{
			val vBytes = read()																// coffre actuel ; null sans
			if (vBytes != null)
			{
				writeAtomic(vTarget, vBytes)
				if (!vTarget.readBytes().contentEquals(vBytes))
				{
					vTarget.delete()
					return "La copie du coffre relue ne correspond pas : rien n'a été déplacé."
				}
			}
			else
			{
				vTarget.absoluteFile.parentFile?.mkdirs()
				if (vTarget.parentFile?.canWrite() != true) return "Dossier inaccessible en écriture."
			}
			fLocation.point(vTarget)
			if (vBytes != null) vCurrent.delete()
			null
		}
		catch (vError: Exception)
		{
			if (vTarget.exists() && vTarget != file.absoluteFile) vTarget.delete()
			"Dossier inaccessible : ${vError.message ?: vError::class.simpleName}"
		}
	}

	/*
		Supprime le coffre de l'appareil et ses sauvegardes. L'emplacement
		choisi est gardé pour un prochain coffre.
	*/
	fun delete() = synchronized(kFileLock) {
		file.delete()
		File(file.absoluteFile.parentFile, file.name + ".tmp").delete()
		fBackups?.clear()
		fKnown = null
	}

	/*
		Écrit un contenu et le retient comme connu.
		inBytes : contenu
	*/
	private fun write(inBytes: ByteArray) = synchronized(kFileLock) {
		read()?.let { fBackups?.daily(it) }
		writeAtomic(file, inBytes)
		fKnown = vaultFingerprint(inBytes)
	}

	/*
		Coffre chiffré, relu pour contrôle avant toute écriture.
		inVault : coffre
	*/
	private fun checkedBytes(inVault: Vault): ByteArray =
		encodeVault(inVault.database).also { decodeVault(it, inVault.database.credentials) }
}

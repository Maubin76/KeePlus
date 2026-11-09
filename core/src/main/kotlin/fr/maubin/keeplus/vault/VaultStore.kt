package fr.maubin.keeplus.vault

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
	Emplacement du coffre de l'appareil : un seul fichier .kdbx, propre à
	l'appli (stockage privé sur Android, %APPDATA%\KeePlus sur PC). Les
	opérations coûteuses (Argon2, écriture) se font hors du fil de
	l'interface.
	fFile : fichier du coffre
*/
class VaultStore(private val fFile: File)
{
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
		writeVault(vVault.database, fFile)
		vVault
	}

	/*
		Ouvre le coffre de l'appareil.
		inPassword : mot de passe maître
		Lève une VaultException si le coffre ne s'ouvre pas.
	*/
	suspend fun unlock(inPassword: CharSequence): Vault = withContext(Dispatchers.Default) {
		Vault(decodeVault(fFile.readBytes(), inPassword))
	}

	/*
		Reprend un coffre existant (KeePassXC, KeePassDX…) comme coffre de
		l'appareil : il est d'abord ouvert avec son mot de passe, puis copié tel
		quel, avec son chiffrement d'origine.
		inBytes    : contenu du fichier .kdbx
		inPassword : son mot de passe maître
		Lève une VaultException si le coffre ne s'ouvre pas.
	*/
	suspend fun import(
		inBytes    : ByteArray,
		inPassword : CharSequence
	): Vault = withContext(Dispatchers.Default) {
		val vVault = Vault(decodeVault(inBytes, inPassword))								// coffre ouvert
		writeVault(vVault.database, fFile)
		vVault
	}

	/*
		Enregistre le coffre après une modification.
		inVault : coffre à écrire
	*/
	suspend fun save(inVault: Vault) = withContext(Dispatchers.IO) {
		writeVault(inVault.database, fFile)
	}
}

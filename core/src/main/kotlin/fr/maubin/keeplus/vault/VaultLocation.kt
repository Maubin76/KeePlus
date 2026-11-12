package fr.maubin.keeplus.vault

import fr.maubin.keeplus.sync.writeAtomic
import java.io.File

const val kVaultFileName = "coffre.kdbx"												// nom du fichier du coffre

/*
	Emplacement du fichier du coffre : l'emplacement par défaut de l'appli
	(stockage privé sur Android, %APPDATA%\KeePlus sur PC), ou un fichier
	choisi par l'utilisateur, retenu dans un petit fichier de renvoi. Le
	renvoi est relu à chaque accès : tous les accès au coffre du processus
	(appli, auto-remplissage, synchronisation) suivent un déplacement.
	fDefault : fichier du coffre par défaut
	fPointer : fichier de renvoi (chemin du coffre choisi) ; null pour un emplacement fixe
*/
class VaultLocation(
	private val fDefault : File,
	private val fPointer : File? = null
)
{
	/*
		Fichier du coffre : celui du renvoi s'il y en a un, sinon celui par défaut.
	*/
	val file: File
		get() = fPointer
			?.takeIf { it.isFile }
			?.let { runCatching { it.readText(Charsets.UTF_8).trim() }.getOrNull() }
			?.takeIf { it.isNotEmpty() }
			?.let(::File)
			?: fDefault

	/*
		Vrai si le coffre est ailleurs qu'à l'emplacement par défaut.
	*/
	val custom: Boolean get() = file.absoluteFile != fDefault.absoluteFile

	/*
		Dossier du coffre par défaut.
	*/
	val defaultFolder: File get() = fDefault.absoluteFile.parentFile

	/*
		Vrai si l'emplacement peut être changé (renvoi possible).
	*/
	val movable: Boolean get() = fPointer != null

	/*
		Retient un nouvel emplacement ; le renvoi est retiré pour l'emplacement
		par défaut.
		inFile : fichier du coffre
	*/
	fun point(inFile: File)
	{
		val vPointer = fPointer ?: error("emplacement fixe")								// fichier de renvoi
		if (inFile.absoluteFile == fDefault.absoluteFile) vPointer.delete()
		else writeAtomic(vPointer, inFile.absolutePath.toByteArray(Charsets.UTF_8))
	}
}

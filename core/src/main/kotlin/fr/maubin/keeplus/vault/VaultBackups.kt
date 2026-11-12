package fr.maubin.keeplus.vault

import fr.maubin.keeplus.sync.writeAtomic
import java.io.File
import java.time.Clock
import java.time.LocalDate

private val kBackupName = Regex("""coffre-(\d{4}-\d{2}-\d{2})\.kdbx""")				// nom d'une sauvegarde

/*
	Sauvegarde du coffre.
	file : fichier de la sauvegarde
	day  : jour de la sauvegarde
	size : taille en octets
*/
data class VaultBackup(
	val file : File,
	val day  : LocalDate,
	val size : Long
)

/*
	Sauvegardes automatiques du coffre : une copie du fichier chiffré, tel
	quel, avant la première modification de chaque jour (modification locale
	ou coffre arrivé d'un autre appareil) ; seules les inKeep plus récentes
	sont gardées. Une sauvegarde s'ouvre avec le mot de passe maître qu'avait
	le coffre ce jour-là.
	fDir   : dossier des sauvegardes
	fKeep  : nombre de sauvegardes gardées
	fClock : horloge (jour courant)
*/
class VaultBackups(
	private val fDir   : File,
	private val fKeep  : Int,
	private val fClock : Clock = Clock.systemDefaultZone()
)
{
	/*
		Dossier des sauvegardes.
	*/
	val folder: File get() = fDir

	/*
		Sauvegarde le coffre s'il n'y a pas encore de sauvegarde aujourd'hui,
		puis retire les plus anciennes au-delà du nombre gardé. Un échec
		n'empêche jamais l'enregistrement du coffre.
		inBytes : contenu à sauvegarder (le coffre avant modification)
	*/
	fun daily(inBytes: ByteArray)
	{
		runCatching {
			val vToday = LocalDate.now(fClock)														// jour courant
			if (list().any { it.day == vToday }) return
			writeAtomic(File(fDir, "coffre-$vToday.kdbx"), inBytes)
			list().drop(fKeep).forEach { it.file.delete() }
		}
	}

	/*
		Supprime toutes les sauvegardes (suppression du coffre).
	*/
	fun clear()
	{
		list().forEach { it.file.delete() }
	}

	/*
		Sauvegardes, de la plus récente à la plus ancienne.
	*/
	fun list(): List<VaultBackup> =
		(fDir.listFiles() ?: emptyArray())
			.mapNotNull { vFile -> kBackupName.matchEntire(vFile.name)?.let { VaultBackup(vFile, LocalDate.parse(it.groupValues[1]), vFile.length()) } }
			.sortedByDescending { it.day }
}

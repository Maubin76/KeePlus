package fr.maubin.keeplus.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.VaultException
import fr.maubin.keeplus.vault.VaultStore
import fr.maubin.keeplus.vault.demoVault
import fr.maubin.keeplus.vault.isKeePassFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/*
	Fichier choisi par l'utilisateur (sélecteur du système).
	name  : nom du fichier, pour l'affichage
	bytes : contenu
*/
class PickedFile(
	val name  : String,
	val bytes : ByteArray
)

/*
	Accès aux fichiers propre à chaque plateforme : sélecteur de documents
	sur Android, boîte de dialogue Windows sur PC.
*/
fun interface VaultFiles
{
	/*
		Demande à l'utilisateur de choisir un coffre .kdbx ; null s'il annule.
	*/
	suspend fun pickVault(): PickedFile?
}

/*
	Filtre de la liste des entrées.
*/
sealed interface ListFilter
{
	/*
		Tout le coffre, hors corbeille.
	*/
	data object All : ListFilter

	/*
		Un groupe et ses sous-groupes.
		id : identifiant du groupe
	*/
	data class Group(val id: UUID) : ListFilter

	/*
		La corbeille.
	*/
	data object Trash : ListFilter
}

/*
	Écran affiché par l'appli.
*/
sealed interface AppScreen
{
	/*
		Premier lancement : créer un coffre ou en reprendre un.
	*/
	data object Welcome : AppScreen

	/*
		Création du coffre.
	*/
	data object Create : AppScreen

	/*
		Ouverture d'un coffre existant choisi par l'utilisateur, avant de le
		reprendre comme coffre de l'appareil.
		file : fichier choisi
	*/
	class Import(val file: PickedFile) : AppScreen

	/*
		Coffre verrouillé.
	*/
	data object Locked : AppScreen

	/*
		Coffre ouvert.
		vault : coffre
		demo  : coffre de démonstration, en mémoire seulement (jamais écrit)
	*/
	class Opened(
		val vault : Vault,
		val demo  : Boolean = false
	) : AppScreen
}

/*
	État de l'appli, commun à Android et au PC : écran affiché, opérations
	sur le coffre de l'appareil, message d'erreur.
	fStore : coffre de l'appareil
	fFiles : accès aux fichiers de la plateforme
*/
class AppController(
	private val fStore : VaultStore,
	private val fFiles : VaultFiles
)
{
	/*
		Écran affiché.
	*/
	var screen: AppScreen by mutableStateOf(if (fStore.exists()) AppScreen.Locked else AppScreen.Welcome)
		private set

	/*
		Opération en cours (Argon2, écriture) : boutons inactifs.
	*/
	var busy: Boolean by mutableStateOf(false)
		private set

	/*
		Message d'erreur de la dernière opération ; null si tout va bien.
	*/
	var error: String? by mutableStateOf(null)
		private set

	/*
		Texte cherché dans le coffre ouvert.
	*/
	val query = TextFieldState()

	/*
		Filtre de la liste : tout le coffre, un groupe ou la corbeille.
	*/
	var filter: ListFilter by mutableStateOf(ListFilter.All)

	/*
		Entrée affichée dans la fiche ; null pour aucune.
	*/
	var selected: UUID? by mutableStateOf(null)

	/*
		Entrée en cours d'édition (copie de travail) ; null hors édition.
	*/
	var editing: EntryDraft? by mutableStateOf(null)
		private set

	/*
		Écriture du coffre en cours, en arrière-plan.
	*/
	var saving: Boolean by mutableStateOf(false)
		private set

	/*
		Message d'échec de la dernière écriture ; null si elle a réussi.
	*/
	var saveError: String? by mutableStateOf(null)
		private set

	private val fSaveLock = Mutex()														// écritures l'une après l'autre

	/*
		Vrai si la touche Retour a un sens : quitter l'édition, fermer la fiche,
		quitter la création ou la reprise d'un coffre.
	*/
	val canGoBack: Boolean
		get() = screen == AppScreen.Create || screen is AppScreen.Import ||
			(screen is AppScreen.Opened && (selected != null || editing != null))

	/*
		Retour : quitte l'édition sans enregistrer, ferme la fiche ouverte, ou
		revient à l'accueil depuis la création ou la reprise d'un coffre.
	*/
	fun back()
	{
		if (!canGoBack) return
		error = null
		when
		{
			editing != null            -> editing = null
			screen is AppScreen.Opened -> selected = null
			else                       -> screen = AppScreen.Welcome
		}
	}

	/*
		Commence une nouvelle entrée, dans le groupe filtré s'il y en a un.
	*/
	fun startNew()
	{
		val vGroup = (filter as? ListFilter.Group)?.id											// groupe filtré
		editing = EntryDraft(id = UUID.randomUUID(), groupId = vGroup)
	}

	/*
		Commence la modification d'une entrée.
		inId : identifiant de l'entrée
	*/
	fun startEdit(inId: UUID)
	{
		editing = openedVault()?.draft(inId)
	}

	/*
		Quitte l'édition sans enregistrer.
	*/
	fun cancelEdit()
	{
		editing = null
	}

	/*
		Enregistre l'entrée éditée : le coffre affiché change tout de suite,
		puis il est écrit en arrière-plan (sauf démonstration).
		inDraft : contenu de l'entrée
	*/
	suspend fun saveEntry(inDraft: EntryDraft)
	{
		val vVault = openedVault() ?: return													// coffre ouvert
		editing  = null
		selected = inDraft.id
		apply(vVault.save(inDraft))
	}

	/*
		Met une entrée à la corbeille ; une entrée déjà à la corbeille est
		supprimée pour de bon.
		inId : identifiant de l'entrée
	*/
	suspend fun trashEntry(inId: UUID)
	{
		val vVault = openedVault() ?: return													// coffre ouvert
		selected = null
		apply(vVault.trash(inId))
	}

	/*
		Sort une entrée de la corbeille.
		inId : identifiant de l'entrée
	*/
	suspend fun restoreEntry(inId: UUID)
	{
		val vVault = openedVault() ?: return													// coffre ouvert
		apply(vVault.restore(inId))
	}

	/*
		Ajoute un groupe à la racine et renvoie son identifiant.
		inName : nom du groupe
	*/
	suspend fun addGroup(inName: String): UUID?
	{
		val vVault = openedVault() ?: return null												// coffre ouvert
		val vId    = UUID.randomUUID()														// nouveau groupe
		apply(vVault.addGroup(inName.trim(), inId = vId))
		return vId
	}

	/*
		Affiche un coffre modifié, puis l'écrit (sauf démonstration). Un échec
		d'écriture est signalé dans saveError ; le coffre affiché reste à jour
		et la prochaine écriture réessaiera.
		inVault : coffre modifié
	*/
	private suspend fun apply(inVault: Vault)
	{
		val vOpened = screen as? AppScreen.Opened ?: return									// coffre ouvert
		screen = AppScreen.Opened(inVault, vOpened.demo)
		if (vOpened.demo) return
		fSaveLock.withLock {
			saving = true
			try
			{
				fStore.save(inVault)
				saveError = null
			}
			catch (vError: Exception)
			{
				saveError = "Échec de l'enregistrement : ${vError.message ?: vError::class.simpleName}"
			}
			finally
			{
				saving = false
			}
		}
	}

	/*
		Coffre ouvert ; null si le coffre est verrouillé.
	*/
	private fun openedVault(): Vault? = (screen as? AppScreen.Opened)?.vault

	/*
		Va à la création du coffre.
	*/
	fun startCreate()
	{
		error  = null
		screen = AppScreen.Create
	}

	/*
		Demande un fichier .kdbx à reprendre ; un fichier qui n'est pas un coffre
		KeePass est refusé tout de suite.
	*/
	suspend fun startImport()
	{
		error = null
		val vFile = runCatching { fFiles.pickVault() }.getOrElse {
			error = "Impossible de lire ce fichier."
			return
		} ?: return																		// fichier choisi
		if (!isKeePassFile(vFile.bytes))
		{
			error = "« ${vFile.name} » n'est pas un coffre KeePass."
			return
		}
		screen = AppScreen.Import(vFile)
	}

	/*
		Crée le coffre de l'appareil ; null en cas d'échec (message dans error).
		inName     : nom du coffre
		inPassword : mot de passe maître
	*/
	suspend fun create(
		inName     : String,
		inPassword : CharSequence
	): Vault? = run { fStore.create(inName, inPassword) }

	/*
		Ouvre le coffre de l'appareil ; null en cas d'échec (message dans error).
		inPassword : mot de passe maître
	*/
	suspend fun unlock(inPassword: CharSequence): Vault? = run { fStore.unlock(inPassword) }

	/*
		Reprend le coffre choisi comme coffre de l'appareil ; null en cas
		d'échec (message dans error).
		inPassword : mot de passe du coffre choisi
	*/
	suspend fun import(inPassword: CharSequence): Vault?
	{
		val vFile = (screen as? AppScreen.Import)?.file ?: return null					// fichier choisi
		return run { fStore.import(vFile.bytes, inPassword) }
	}

	/*
		Affiche le coffre ouvert (après l'animation de déverrouillage).
		inVault : coffre ouvert
	*/
	fun open(inVault: Vault)
	{
		error  = null
		screen = AppScreen.Opened(inVault)
	}

	/*
		Ouvre le coffre de démonstration, en mémoire seulement : il n'est
		jamais écrit et ne touche pas au coffre de l'appareil.
	*/
	suspend fun openDemo()
	{
		if (busy) return
		busy = true
		try
		{
			val vVault = withContext(Dispatchers.Default) { demoVault() }					// coffre de démonstration (Argon2 à la création)
			error  = null
			screen = AppScreen.Opened(vVault, demo = true)
		}
		finally
		{
			busy = false
		}
	}

	/*
		Verrouille le coffre : il n'en reste rien en mémoire côté appli. La
		démonstration se referme sur l'accueil (ou le déverrouillage si un
		coffre existe).
	*/
	fun lock()
	{
		val vOpened = screen as? AppScreen.Opened ?: return								// coffre ouvert
		error    = null
		selected = null
		editing  = null
		filter   = ListFilter.All
		query.clearText()
		screen   = if (vOpened.demo && !fStore.exists()) AppScreen.Welcome else AppScreen.Locked
	}

	/*
		Efface le message d'erreur (saisie reprise).
	*/
	fun clearError()
	{
		error = null
	}

	/*
		Exécute une opération sur le coffre : drapeau busy, message d'erreur
		en cas d'échec.
		inBlock : opération
	*/
	private suspend fun run(inBlock: suspend () -> Vault): Vault?
	{
		if (busy) return null
		busy  = true
		error = null
		return try
		{
			inBlock()
		}
		catch (vError: VaultException)
		{
			error = vError.message
			null
		}
		catch (vError: Exception)
		{
			error = "Erreur inattendue : ${vError.message ?: vError::class.simpleName}"
			null
		}
		finally
		{
			busy = false
		}
	}
}

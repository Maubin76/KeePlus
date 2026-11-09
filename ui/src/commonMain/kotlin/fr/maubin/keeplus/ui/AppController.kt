package fr.maubin.keeplus.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.VaultException
import fr.maubin.keeplus.settings.KeeSettings
import fr.maubin.keeplus.settings.SettingsStore
import fr.maubin.keeplus.vault.VaultBackup
import fr.maubin.keeplus.vault.VaultStore
import fr.maubin.keeplus.vault.decodeVault
import fr.maubin.keeplus.vault.mergeVaults
import fr.maubin.keeplus.vault.demoVault
import fr.maubin.keeplus.vault.isKeePassFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.CharBuffer
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

const val kIdleMillis   = 5 * 60 * 1000L											// verrouillage après 5 min sans geste
const val kHiddenMillis = 60 * 1000L												// verrouillage après 60 s en arrière-plan

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
	sur le coffre de l'appareil, message d'erreur, verrouillage automatique.
	fStore     : coffre de l'appareil
	fFiles     : accès aux fichiers de la plateforme
	fBiometric : déverrouillage par empreinte ; null là où il n'existe pas (PC)
	fNow       : horloge en millisecondes (verrouillage automatique)
	fSync      : synchronisation vue par l'appli ; null sans
	fSettings  : fichier des réglages ; null pour les réglages par défaut (tests)
*/
class AppController(
	private val fStore     : VaultStore,
	private val fFiles     : VaultFiles,
	private val fBiometric : BiometricUnlock? = null,
	private val fNow       : () -> Long       = System::currentTimeMillis,
	private val fSync      : SyncHooks?       = null,
	private val fSettings  : SettingsStore?   = null
)
{
	/*
		Écran des appareils (synchronisation) affiché.
	*/
	var showDevices: Boolean by mutableStateOf(false)

	/*
		Écran des réglages affiché.
	*/
	var showSettings: Boolean by mutableStateOf(false)

	/*
		Réglages de l'appli.
	*/
	var settings: KeeSettings by mutableStateOf(fSettings?.load() ?: KeeSettings())
		private set

	/*
		Vrai si l'appareil peut déverrouiller par l'empreinte (capteur et
		empreinte enregistrée).
	*/
	val biometricAvailable: Boolean get() = fBiometric?.available == true

	private var fLastActivity : Long       = fNow()										// dernier geste de l'utilisateur
	private var fHiddenSince  : Long?      = null											// appli masquée depuis ; null si visible
	private var fPending      : CharArray? = null											// mot de passe gardé le temps de proposer l'empreinte

	/*
		Vrai si l'empreinte est active : l'écran de déverrouillage la propose.
	*/
	val biometricReady: Boolean get() = fBiometric?.let { it.available && it.enabled } == true

	/*
		Vrai s'il faut proposer d'activer l'empreinte (juste après un
		déverrouillage par mot de passe, une seule fois).
	*/
	var offerBiometric: Boolean by mutableStateOf(false)
		private set

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
			(screen is AppScreen.Opened && (selected != null || editing != null || showDevices || showSettings))

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
			showDevices                -> showDevices = false
			showSettings               -> showSettings = false
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
				var vVault = inVault																// coffre à écrire
				for (vTry in 1..3)
				{
					val vArrived = fStore.save(vVault)													// coffre arrivé entre-temps ; null si écrit
					if (vArrived == null)
					{
						saveError = null
						fSync?.changed()
						return@withLock
					}
					vVault = Vault(mergeVaults(vVault.database, decodeVault(vArrived, vVault.database.credentials)).database)
					fStore.accept(vArrived)
					if (screen is AppScreen.Opened) screen = AppScreen.Opened(vVault, false)
				}
				saveError = "Échec de l'enregistrement : le coffre change sans cesse sur un autre appareil."
			}
			catch (vError: VaultException.WrongPassword)
			{
				saveError = "Le coffre arrivé d'un autre appareil a un autre mot de passe maître : modification non enregistrée."
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
		Change les réglages et les enregistre.
		inSettings : nouveaux réglages
	*/
	fun updateSettings(inSettings: KeeSettings)
	{
		settings = inSettings
		runCatching { fSettings?.save(inSettings) }
	}

	/*
		Sauvegardes automatiques du coffre, de la plus récente à la plus
		ancienne ; aucune en démonstration.
	*/
	fun backups(): List<VaultBackup> = fStore.fBackups?.list().orEmpty()

	/*
		Dossier des sauvegardes ; null sans sauvegardes.
	*/
	val backupFolder: java.io.File? get() = fStore.fBackups?.folder

	/*
		Remet une sauvegarde à la place du coffre, puis verrouille : le coffre
		restauré s'ouvre avec le mot de passe maître qu'il avait ce jour-là.
		inBackup : sauvegarde
	*/
	suspend fun restoreBackup(inBackup: VaultBackup)
	{
		fSaveLock.withLock {
			try
			{
				withContext(Dispatchers.IO) { fStore.restore(inBackup) }
			}
			catch (vError: Exception)
			{
				saveError = "Restauration impossible : ${vError.message ?: vError::class.simpleName}"
				return@withLock
			}
			lock()
			error = "Sauvegarde du ${inBackup.day.dayOfMonth}/${inBackup.day.monthValue}/${inBackup.day.year} restaurée : saisissez le mot de passe maître qu'avait le coffre ce jour-là."
		}
		fSync?.changed()
	}

	/*
		Change le mot de passe maître du coffre ouvert ; rend null si c'est
		fait, sinon la raison. L'empreinte, qui gardait l'ancien, est
		désactivée et sera proposée de nouveau ; les autres appareils
		demanderont le nouveau mot de passe.
		inCurrent : mot de passe maître actuel
		inNew     : nouveau mot de passe maître
	*/
	suspend fun changeMasterPassword(
		inCurrent : CharSequence,
		inNew     : CharSequence
	): String?
	{
		val vOpened = screen as? AppScreen.Opened ?: return "Coffre verrouillé."					// coffre ouvert
		if (vOpened.demo) return "Pas de changement de mot de passe en démonstration."
		if (!vOpened.vault.isMasterPassword(inCurrent)) return "Mot de passe actuel incorrect."
		apply(vOpened.vault.changeMasterPassword(inNew))
		if (saveError != null) return saveError
		fBiometric?.let { if (it.enabled) { it.disable(); it.resetOffer() } }
		return null
	}

	/*
		Désactive l'empreinte (mot de passe gardé et clé effacés).
	*/
	fun disableBiometric()
	{
		fBiometric?.disable()
	}

	/*
		L'activation de l'empreinte sera proposée au prochain déverrouillage
		par mot de passe.
	*/
	fun offerBiometricAgain()
	{
		fBiometric?.resetOffer()
	}

	/*
		Le coffre a peut-être été remplacé sur le disque par la synchronisation :
		s'il est ouvert, il est relu avec sa clé (un nouveau mot de passe
		maître, changé sur un autre appareil, verrouille le coffre) ; si
		l'appareil n'avait pas de coffre, l'accueil laisse place au
		déverrouillage.
	*/
	suspend fun reloadFromDisk()
	{
		fSaveLock.withLock {
			val vOpened = screen as? AppScreen.Opened											// coffre ouvert
			if (vOpened == null)
			{
				if (screen == AppScreen.Welcome && fStore.exists()) screen = AppScreen.Locked
				return@withLock
			}
			if (vOpened.demo) return@withLock
			val vArrived = fStore.changedOnDisk() ?: return@withLock								// coffre arrivé
			try
			{
				val vVault = withContext(Dispatchers.Default) { Vault(decodeVault(vArrived, vOpened.vault.database.credentials)) }	// coffre relu
				fStore.accept(vArrived)
				if (screen is AppScreen.Opened) screen = AppScreen.Opened(vVault, false)
			}
			catch (vError: VaultException.WrongPassword)
			{
				lock()
				error = "Le mot de passe maître a changé sur un autre appareil : saisissez le nouveau."
			}
		}
	}

	/*
		Fusionne dans le coffre ouvert les coffres d'autres appareils en
		attente (modifiés des deux côtés pendant que le coffre était
		verrouillé), puis les enregistre ; la synchronisation renverra le
		résultat. Un coffre au mot de passe maître différent est écarté, avec
		un message.
	*/
	suspend fun mergePending()
	{
		val vSync = fSync ?: return																// synchronisation
		for (vPending in vSync.pending())
		{
			val vVault = openedVault()?.takeIf { (screen as AppScreen.Opened).demo.not() } ?: return	// coffre ouvert
			try
			{
				val vRemote = withContext(Dispatchers.Default) { decodeVault(vPending.bytes, vVault.database.credentials) }	// coffre de l'autre appareil
				vSync.settle(vPending, true)
				apply(Vault(mergeVaults(vVault.database, vRemote).database))
			}
			catch (vError: VaultException)
			{
				vSync.settle(vPending, false)
				saveError = "Le coffre de « ${vPending.pcName} » a un autre mot de passe maître : fusion impossible."
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
	suspend fun unlock(inPassword: CharSequence): Vault?
	{
		val vVault = run { fStore.unlock(inPassword) } ?: return null					// coffre ouvert
		val vOffer = fBiometric?.let { it.available && !it.enabled && !it.offered } == true	// proposer l'empreinte
		if (vOffer)
		{
			fPending       = CharArray(inPassword.length) { inPassword[it] }
			offerBiometric = true
		}
		return vVault
	}

	/*
		Ouvre le coffre par l'empreinte ; null si l'utilisateur annule ou en
		cas d'échec (message dans error). Si le mot de passe gardé ne
		correspond plus (changé ailleurs) ou si la clé a été invalidée,
		l'empreinte est désactivée et le mot de passe doit être ressaisi.
	*/
	suspend fun unlockWithBiometric(): Vault?
	{
		val vBiometric = fBiometric ?: return null												// empreinte
		val vChars     = try
		{
			vBiometric.unlock() ?: return null
		}
		catch (vError: BiometricInvalidated)
		{
			vBiometric.disable()
			error = vError.message
			return null
		}																					// mot de passe gardé
		try
		{
			val vVault = run { fStore.unlock(CharBuffer.wrap(vChars)) }			// coffre ouvert
			if (vVault == null && error == VaultException.WrongPassword().message)
			{
				vBiometric.disable()
				error = "Le mot de passe a changé : saisissez-le pour réactiver l'empreinte."
			}
			return vVault
		}
		finally
		{
			vChars.fill(Char(0))
		}
	}

	/*
		Réponse à la proposition d'empreinte : l'active (avec le mot de passe
		gardé depuis le déverrouillage) ou y renonce ; le mot de passe gardé
		est effacé dans tous les cas.
		inAccept : activer l'empreinte
		Renvoie vrai si l'empreinte est active.
	*/
	suspend fun answerBiometricOffer(inAccept: Boolean): Boolean
	{
		val vBiometric = fBiometric ?: return false											// empreinte
		val vPassword  = fPending																// mot de passe gardé
		offerBiometric = false
		vBiometric.markOffered()
		try
		{
			if (!inAccept || vPassword == null) return false
			return runCatching { vBiometric.enable(CharBuffer.wrap(vPassword)) }.getOrDefault(false)
		}
		finally
		{
			vPassword?.fill(Char(0))
			fPending = null
		}
	}

	/*
		Note un geste de l'utilisateur (repousse le verrouillage automatique).
	*/
	fun touch()
	{
		fLastActivity = fNow()
	}

	/*
		L'appli passe en arrière-plan (téléphone) ou la fenêtre est réduite
		(PC), ou elle revient. Au retour, le coffre est verrouillé si l'absence
		a dépassé le délai de grâce.
		inHidden : appli masquée
	*/
	fun setHidden(inHidden: Boolean)
	{
		if (inHidden)
		{
			if (fHiddenSince == null) fHiddenSince = fNow()
			if (settings.hiddenSeconds == 0) lock()
			return
		}
		val vSince = fHiddenSince																// début de l'absence
		fHiddenSince = null
		if (vSince != null && fNow() - vSince >= settings.hiddenSeconds * 1000L) lock()
		touch()
	}

	/*
		Verrouille si le coffre est ouvert et que l'utilisateur est inactif, ou
		l'appli masquée, depuis les délais réglés. Appelé chaque seconde par
		l'interface.
	*/
	fun checkAutoLock()
	{
		if (screen !is AppScreen.Opened) return
		val vNow = fNow()																	// maintenant
		val vIdle   = vNow - fLastActivity >= settings.idleMinutes * 60_000L					// inactif trop longtemps
		val vHidden = fHiddenSince?.let { vNow - it >= settings.hiddenSeconds * 1000L } == true	// masqué trop longtemps
		if (vIdle || vHidden) lock()
	}

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
		touch()
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
		error          = null
		selected       = null
		editing        = null
		showDevices    = false
		showSettings   = false
		filter         = ListFilter.All
		query.clearText()
		fPending?.fill(Char(0))
		fPending       = null
		offerBiometric = false
		screen         = if (vOpened.demo && !fStore.exists()) AppScreen.Welcome else AppScreen.Locked
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

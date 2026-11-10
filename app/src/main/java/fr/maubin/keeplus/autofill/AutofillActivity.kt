package fr.maubin.keeplus.autofill

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import fr.maubin.keeplus.AndroidBiometric
import fr.maubin.keeplus.phoneStore
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.theme.KeePlusTheme
import fr.maubin.keeplus.vault.Vault
import java.util.UUID

private const val kUserField = "keeplus.autofill.user"									// champ de l'identifiant
private const val kPassField = "keeplus.autofill.password"								// champ du mot de passe
private const val kPackage   = "keeplus.autofill.package"								// appli qui demande
private const val kDomain    = "keeplus.autofill.domain"									// domaine de la page
private const val kEntry     = "keeplus.autofill.entry"									// entrée choisie dans la suggestion
private const val kName      = "keeplus.autofill.name"									// nom à montrer
private const val kNew       = "keeplus.autofill.new"									// champs d'un nouveau mot de passe
private const val kIdentity  = "keeplus.autofill.identity"								// champs d'identité d'une inscription
private const val kEmails    = "keeplus.autofill.emails"									// champs d'identité qui attendent un e-mail
private const val kSaveUser  = "keeplus.autofill.save.user"								// identifiant tapé à enregistrer
private const val kSavePass  = "keeplus.autofill.save.password"							// mot de passe tapé à enregistrer

/*
	Ce qu'il faut remplir.
	userName : champ de l'identifiant ; null sans
	password : champ du mot de passe ; null sans
	target   : appli ou site qui demande
	name         : nom à montrer : domaine du site, ou nom de l'appli
	newPasswords : champs d'un nouveau mot de passe et de sa confirmation
	identities   : inscription : champs d'identité (identifiant, e-mail)
*/
class AutofillRequest(
	val userName     : AutofillId?,
	val password     : AutofillId?,
	val target       : AutofillTarget,
	val name         : String                          = target.label,
	val newPasswords : List<AutofillId>                = emptyList(),
	val identities   : List<IdentityField<AutofillId>> = emptyList()
)
{
	/*
		Champs à remplir.
	*/
	val ids: List<AutofillId> get() = (listOfNotNull(userName, password) + identities.map { it.id } + newPasswords).distinct()

	/*
		Vrai pour une inscription : mot de passe à créer, sans mot de passe actuel.
	*/
	val signUp: Boolean get() = password == null && newPasswords.isNotEmpty()

	/*
		Vrai pour un changement de mot de passe : actuel, puis nouveau.
	*/
	val change: Boolean get() = password != null && newPasswords.isNotEmpty()
}

/*
	Écran ouvert par l'appui sur une suggestion d'auto-remplissage. Il rend à
	Android l'identifiant et le mot de passe de l'entrée choisie :
	- entrée déjà choisie et coffre ouvert : aussitôt, sans rien afficher ;
	- connexion : carte en haut de l'écran, par-dessus l'appli (voir
	  LoginCard) : déverrouillage si besoin (empreinte d'emblée, ou mot de
	  passe maître), puis choix parmi les comptes de cette appli ou de ce
	  site, recherche dans tout le coffre ; un seul compte correspondant
	  est rempli sans choix ;
	- inscription : même carte (voir SignUpCard), qui propose identifiant
	  et mot de passe, remplit et enregistre le compte ;
	- changement de mot de passe : même carte (voir ChangeCard), qui
	  propose le nouveau, remplit l'actuel et le nouveau, met l'entrée à jour ;
	- enregistrement d'identifiants tapés à la main (après « Enregistrer
	  dans KeePlus ? » d'Android) : même carte (voir SaveCard).
	Pour une appli, l'entrée choisie lui est liée (réglable) : elle sera
	proposée d'emblée la prochaine fois. Écran protégé (FLAG_SECURE).
	fRequest : ce qu'il faut remplir
	fMotion  : apparition et disparition de la carte ; null tant qu'elle n'est pas affichée
*/
class AutofillActivity : FragmentActivity()
{
	private lateinit var fRequest: AutofillRequest
	private var fMotion: CardMotion? = null												// carte affichée ; null sans carte

	/*
		Création : remplissage immédiat si possible, sinon déverrouillage ou
		choix.
		inState : état sauvegardé
	*/
	override fun onCreate(inState: Bundle?)
	{
		super.onCreate(inState)
		window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
		enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
		val vSaved   = intent.getStringExtra(kSavePass)											// mot de passe tapé à enregistrer
		if (vSaved != null) return showSave(vSaved)
		fRequest = request(intent) ?: return finishEmpty()
		val vChosen  = intent.getStringExtra(kEntry)?.let(UUID::fromString)						// entrée de la suggestion
		val vOpened  = VaultSession.vault()														// coffre déjà ouvert
		if (fRequest.signUp) return showSignUp(vOpened)
		if (fRequest.change) return showChange(vOpened)
		if (vOpened != null && vChosen != null && vOpened.draft(vChosen) != null) return fill(vOpened, vChosen, false)
		val vStore   = phoneStore(this)															// accès au coffre
		val vUnlock  = AppController(vStore, { null }, AndroidBiometric(this))					// déverrouillage
		val vMotion  = CardMotion().also { fMotion = it }											// apparition de la carte
		setContent {
			KeePlusTheme {
				LoginCard(
					inMotion   = vMotion,
					inRequest  = fRequest,
					inOpened   = vOpened,
					inUnlock   = vUnlock,
					inOnOpened = { vNew ->
						vUnlock.open(vNew)
						vUnlock.lock()
						VaultSession.keep(this@AutofillActivity, vNew, vStore)
						val vMatches = vNew.autofillMatches(fRequest.target)							// comptes de la cible
						when
						{
							vChosen != null && vNew.draft(vChosen) != null -> fill(vNew, vChosen, false)
							vMatches.size == 1                            -> fill(vNew, vMatches.single().id, false)
						}
					},
					inOnPick   = ::fill,
					inOnCancel = ::finishEmpty
				)
			}
		}
	}

	/*
		Inscription : carte en haut de l'écran, par-dessus l'appli.
		inOpened : coffre déjà ouvert ; null s'il faut déverrouiller
	*/
	private fun showSignUp(inOpened: Vault?)
	{
		val vStore  = phoneStore(this)																// accès au coffre
		val vUnlock = AppController(vStore, { null }, AndroidBiometric(this))						// déverrouillage
		val vMotion = CardMotion().also { fMotion = it }												// apparition de la carte
		setContent {
			KeePlusTheme {
				SignUpCard(
					inMotion   = vMotion,
					inRequest  = fRequest,
					inOpened   = inOpened,
					inUnlock   = vUnlock,
					inOnOpened = { vNew ->
						vUnlock.open(vNew)
						vUnlock.lock()
						VaultSession.keep(this@AutofillActivity, vNew, vStore)
					},
					inOnFill   = ::fillSignUp,
					inOnCancel = ::finishEmpty
				)
			}
		}
	}

	/*
		Changement de mot de passe : carte en haut de l'écran, par-dessus l'appli.
		inOpened : coffre déjà ouvert ; null s'il faut déverrouiller
	*/
	private fun showChange(inOpened: Vault?)
	{
		val vStore  = phoneStore(this)																// accès au coffre
		val vUnlock = AppController(vStore, { null }, AndroidBiometric(this))						// déverrouillage
		val vMotion = CardMotion().also { fMotion = it }												// apparition de la carte
		setContent {
			KeePlusTheme {
				ChangeCard(
					inMotion   = vMotion,
					inRequest  = fRequest,
					inOpened   = inOpened,
					inUnlock   = vUnlock,
					inOnOpened = { vNew ->
						vUnlock.open(vNew)
						vUnlock.lock()
						VaultSession.keep(this@AutofillActivity, vNew, vStore)
					},
					inOnFill   = ::fillChange,
					inOnCancel = ::finishEmpty
				)
			}
		}
	}

	/*
		Enregistrement d'identifiants tapés à la main : carte en haut de
		l'écran (déverrouillage, puis création ou mise à jour de l'entrée).
		inPassword : mot de passe tapé
	*/
	private fun showSave(inPassword: String)
	{
		val vPackage = intent.getStringExtra(kPackage) ?: return finish()						// appli
		val vTarget  = AutofillTarget.of(vPackage, intent.getStringExtra(kDomain))				// appli ou site
		val vName    = intent.getStringExtra(kName) ?: vTarget.label								// nom à montrer
		val vUser    = intent.getStringExtra(kSaveUser).orEmpty()									// identifiant tapé
		val vStore   = phoneStore(this)															// accès au coffre
		val vUnlock  = AppController(vStore, { null }, AndroidBiometric(this))					// déverrouillage
		val vMotion  = CardMotion().also { fMotion = it }											// apparition de la carte
		setContent {
			KeePlusTheme {
				SaveCard(
					inMotion   = vMotion,
					inTarget   = vTarget,
					inName     = vName,
					inUserName = vUser,
					inPassword = inPassword,
					inOpened   = VaultSession.vault(),
					inUnlock   = vUnlock,
					inOnOpened = { vNew ->
						vUnlock.open(vNew)
						vUnlock.lock()
						VaultSession.keep(this@AutofillActivity, vNew, vStore)
					},
					inOnSave   = { vVault ->
						VaultSession.editLater(applicationContext, vVault) { it.saveLogin(vTarget, vName, vUser, inPassword) }
						leave()
					},
					inOnCancel = ::leave
				)
			}
		}
	}

	/*
		Changement de mot de passe accepté : rend à Android l'identifiant et
		le mot de passe actuel de l'entrée, et le nouveau dans chaque champ de
		nouveau mot de passe, puis met l'entrée à jour (l'ancien mot de passe
		rejoint son historique).
		inVault    : coffre ouvert
		inId       : entrée du compte
		inPassword : nouveau mot de passe
	*/
	@Suppress("DEPRECATION")
	private fun fillChange(
		inVault    : Vault,
		inId       : UUID,
		inPassword : String
	)
	{
		val vDraft   = inVault.draft(inId) ?: return finishEmpty()									// entrée
		val vBuilder = Dataset.Builder(presentation(this, vDraft.title, vDraft.userName))			// valeurs à remplir
		fRequest.userName?.let { vBuilder.setValue(it, AutofillValue.forText(vDraft.userName)) }
		fRequest.password?.let { vBuilder.setValue(it, AutofillValue.forText(vDraft.password)) }
		for (vId in fRequest.newPasswords) vBuilder.setValue(vId, AutofillValue.forText(inPassword))
		setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, vBuilder.build()))
		VaultSession.editLater(applicationContext, inVault) { it.changePassword(inId, inPassword) }
		leave()
	}

	/*
		Inscription acceptée : rend à Android les valeurs des champs
		d'identité et le mot de passe (dans chaque champ de nouveau mot de
		passe), puis ajoute le compte au coffre (ou met à jour le mot de passe
		d'un compte de même identifiant déjà enregistré pour cette cible).
		inVault    : coffre ouvert
		inValues   : valeurs des champs d'identité, dans l'ordre
		inPassword : mot de passe choisi
	*/
	@Suppress("DEPRECATION")
	private fun fillSignUp(
		inVault    : Vault,
		inValues   : List<String>,
		inPassword : String
	)
	{
		val vValues  = fRequest.identities.map { it.id }.zip(inValues).toMap()					// valeur de chaque champ d'identité
		val vUser    = fRequest.userName?.let { vValues[it] } ?: inValues.firstOrNull { it.isNotBlank() }.orEmpty()	// identifiant du compte
		val vBuilder = Dataset.Builder(presentation(this, fRequest.name, vUser))					// valeurs à remplir
		for ((vId, vValue) in vValues) vBuilder.setValue(vId, AutofillValue.forText(vValue))
		fRequest.userName?.takeIf { it !in vValues }?.let { vBuilder.setValue(it, AutofillValue.forText(vUser)) }
		for (vId in fRequest.newPasswords) vBuilder.setValue(vId, AutofillValue.forText(inPassword))
		setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, vBuilder.build()))
		val vTarget  = fRequest.target																// appli ou site
		val vTitle   = fRequest.name																// titre de l'entrée
		VaultSession.editLater(applicationContext, inVault) { it.saveLogin(vTarget, vTitle, vUser, inPassword) }
		leave()
	}

	/*
		Rend à Android l'identifiant et le mot de passe d'une entrée, et lie
		l'entrée à l'appli si demandé.
		inVault    : coffre ouvert
		inId       : entrée
		inRemember : lier l'entrée à l'appli
	*/
	@Suppress("DEPRECATION")
	private fun fill(
		inVault    : Vault,
		inId       : UUID,
		inRemember : Boolean
	)
	{
		val vDraft   = inVault.draft(inId) ?: return finishEmpty()									// entrée
		val vBuilder = Dataset.Builder(presentation(this, vDraft.title, vDraft.userName))			// valeurs à remplir
		fRequest.userName?.let { vBuilder.setValue(it, AutofillValue.forText(vDraft.userName)) }
		fRequest.password?.let { vBuilder.setValue(it, AutofillValue.forText(vDraft.password)) }
		setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, vBuilder.build()))
		if (inRemember && fRequest.target.isApp)
		{
			val vPackage = fRequest.target.packageName												// appli à lier
			VaultSession.editLater(applicationContext, inVault) { it.rememberApp(inId, vPackage) }
		}
		leave()
	}

	/*
		Ferme sans rien remplir.
	*/
	private fun finishEmpty()
	{
		setResult(Activity.RESULT_CANCELED)
		leave()
	}

	/*
		Ferme l'écran, le résultat étant donné : la carte remonte d'abord si
		elle est affichée.
	*/
	private fun leave()
	{
		val vMotion = fMotion ?: return finish()													// carte affichée
		vMotion.close(::finish)
	}

	companion object
	{
		/*
			Intention qui ouvre cet écran pour une suggestion.
			inContext : contexte
			inRequest : ce qu'il faut remplir
			inEntry   : entrée proposée ; null pour déverrouiller ou chercher
		*/
		fun intent(
			inContext : Context,
			inRequest : AutofillRequest,
			inEntry   : UUID?
		): Intent =
			Intent(inContext, AutofillActivity::class.java)
				.putExtra(kUserField, inRequest.userName)
				.putExtra(kPassField, inRequest.password)
				.putExtra(kPackage, inRequest.target.packageName)
				.putExtra(kDomain, inRequest.target.domain)
				.putExtra(kEntry, inEntry?.toString())
				.putExtra(kName, inRequest.name)
				.putParcelableArrayListExtra(kNew, ArrayList(inRequest.newPasswords))
				.putParcelableArrayListExtra(kIdentity, ArrayList(inRequest.identities.map { it.id }))
				.putExtra(kEmails, inRequest.identities.map { it.email }.toBooleanArray())

		/*
			Intention qui ouvre la carte « Enregistrer » pour des identifiants
			tapés à la main.
			inContext  : contexte
			inTarget   : appli ou site
			inName     : nom à montrer
			inUserName : identifiant tapé ; vide s'il n'y en avait pas
			inPassword : mot de passe tapé
		*/
		fun saveIntent(
			inContext  : Context,
			inTarget   : AutofillTarget,
			inName     : String,
			inUserName : String,
			inPassword : String
		): Intent =
			Intent(inContext, AutofillActivity::class.java)
				.putExtra(kPackage, inTarget.packageName)
				.putExtra(kDomain, inTarget.domain)
				.putExtra(kName, inName)
				.putExtra(kSaveUser, inUserName)
				.putExtra(kSavePass, inPassword)

		/*
			Ce qu'il faut remplir, d'après l'intention ; null si elle est
			incomplète.
			inIntent : intention reçue
		*/
		private fun request(inIntent: Intent): AutofillRequest?
		{
			val vUser    = IntentCompat.getParcelableExtra(inIntent, kUserField, AutofillId::class.java)	// champ de l'identifiant
			val vPass    = IntentCompat.getParcelableExtra(inIntent, kPassField, AutofillId::class.java)	// champ du mot de passe
			val vPackage = inIntent.getStringExtra(kPackage) ?: return null						// appli qui demande
			val vNew     = IntentCompat.getParcelableArrayListExtra(inIntent, kNew, AutofillId::class.java).orEmpty()		// nouveaux mots de passe
			val vIds     = IntentCompat.getParcelableArrayListExtra(inIntent, kIdentity, AutofillId::class.java).orEmpty()	// champs d'identité
			val vEmails  = inIntent.getBooleanArrayExtra(kEmails) ?: BooleanArray(vIds.size)		// lesquels attendent un e-mail
			if (vUser == null && vPass == null && vNew.isEmpty()) return null
			val vTarget  = AutofillTarget.of(vPackage, inIntent.getStringExtra(kDomain))				// appli ou site
			return AutofillRequest(
				userName     = vUser,
				password     = vPass,
				target       = vTarget,
				name         = inIntent.getStringExtra(kName) ?: vTarget.label,
				newPasswords = vNew,
				identities   = vIds.mapIndexed { vIndex, vId -> IdentityField(vId, vEmails.getOrElse(vIndex) { false }) }
			)
		}
	}
}

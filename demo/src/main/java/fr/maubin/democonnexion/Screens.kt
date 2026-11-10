package fr.maubin.democonnexion

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/*
	Texte saisi dans un champ.
	inId : champ
*/
private fun Activity.text(inId: Int): String = findViewById<EditText>(inId).text.toString()

/*
	Affiche un message d'erreur ; null pour l'effacer.
	inMessage : message
*/
private fun Activity.showError(inMessage: String?)
{
	findViewById<TextView>(R.id.error).text = inMessage.orEmpty()
}

/*
	Ouvre l'accueil du compte connecté et ferme l'écran actuel.
*/
private fun Activity.openHome()
{
	startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
	finish()
}

/*
	Connexion : identifiant et mot de passe d'un compte existant, ou lien vers
	la création d'un compte. Déjà connecté : accueil direct.
	fAccounts : comptes de la démo
*/
class LoginActivity : Activity()
{
	private lateinit var fAccounts: Accounts

	/*
		Création de l'écran.
		inState : état sauvegardé
	*/
	override fun onCreate(inState: Bundle?)
	{
		super.onCreate(inState)
		fAccounts = Accounts(this)
		if (fAccounts.current != null) return openHome()
		setContentView(R.layout.activity_login)
		findViewById<Button>(R.id.login).setOnClickListener {
			val vError = fAccounts.login(text(R.id.user), text(R.id.password))				// raison d'un échec
			if (vError == null) openHome() else showError(vError)
		}
		findViewById<Button>(R.id.register).setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
	}
}

/*
	Création d'un compte : identifiant, mot de passe et confirmation (champs
	marqués « nouveau mot de passe »), puis connexion.
	fAccounts : comptes de la démo
*/
class RegisterActivity : Activity()
{
	private lateinit var fAccounts: Accounts

	/*
		Création de l'écran.
		inState : état sauvegardé
	*/
	override fun onCreate(inState: Bundle?)
	{
		super.onCreate(inState)
		fAccounts = Accounts(this)
		setContentView(R.layout.activity_register)
		findViewById<Button>(R.id.create).setOnClickListener {
			val vError = fAccounts.register(text(R.id.user), text(R.id.password), text(R.id.confirm))	// raison d'un échec
			if (vError == null) openHome() else showError(vError)
		}
	}
}

/*
	Accueil du compte connecté : changement de mot de passe, déconnexion.
	fAccounts : comptes de la démo
*/
class HomeActivity : Activity()
{
	private lateinit var fAccounts: Accounts

	/*
		Création de l'écran ; sans compte connecté, retour à la connexion.
		inState : état sauvegardé
	*/
	override fun onCreate(inState: Bundle?)
	{
		super.onCreate(inState)
		fAccounts = Accounts(this)
		val vUser = fAccounts.current																// compte connecté
		if (vUser == null)
		{
			startActivity(Intent(this, LoginActivity::class.java))
			finish()
			return
		}
		setContentView(R.layout.activity_home)
		findViewById<TextView>(R.id.welcome).text = getString(R.string.home_welcome, vUser)
		findViewById<Button>(R.id.change).setOnClickListener { startActivity(Intent(this, ChangePasswordActivity::class.java)) }
		findViewById<Button>(R.id.logout).setOnClickListener {
			fAccounts.logout()
			startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
			finish()
		}
	}
}

/*
	Changement du mot de passe : mot de passe actuel, nouveau et confirmation.
	L'identifiant est présent mais non modifiable, comme sur la plupart des
	sites (il aide les gestionnaires de mots de passe à reconnaître le compte).
	fAccounts : comptes de la démo
*/
class ChangePasswordActivity : Activity()
{
	private lateinit var fAccounts: Accounts

	/*
		Création de l'écran.
		inState : état sauvegardé
	*/
	override fun onCreate(inState: Bundle?)
	{
		super.onCreate(inState)
		fAccounts = Accounts(this)
		setContentView(R.layout.activity_change)
		findViewById<EditText>(R.id.user).setText(fAccounts.current.orEmpty())
		findViewById<Button>(R.id.save).setOnClickListener {
			val vError = fAccounts.changePassword(text(R.id.current), text(R.id.password), text(R.id.confirm))	// raison d'un échec
			if (vError != null) return@setOnClickListener showError(vError)
			findViewById<TextView>(R.id.error).text = ""
			finish()
		}
	}
}

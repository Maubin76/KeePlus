package fr.maubin.democonnexion

import android.content.Context
import java.security.MessageDigest

private const val kAccounts = "comptes"													// fichier des comptes (stockage privé)
private const val kSession  = "session"													// compte connecté
private const val kMinimum  = 4															// longueur minimale d'un mot de passe

/*
	Comptes de la démo, gardés dans le stockage privé de l'appli : pour
	chaque identifiant, l'empreinte SHA-256 de l'identifiant et du mot de
	passe (jamais le mot de passe lui-même), et le compte connecté.
	fContext : contexte de l'appli
*/
class Accounts(private val fContext: Context)
{
	private val fPrefs = fContext.getSharedPreferences(kAccounts, Context.MODE_PRIVATE)

	/*
		Compte connecté ; null si personne ne l'est.
	*/
	val current: String? get() = fPrefs.getString(kSession, null)

	/*
		Crée un compte et le connecte ; rend null si c'est fait, sinon la raison.
		inUser     : identifiant
		inPassword : mot de passe
		inConfirm  : confirmation du mot de passe
	*/
	fun register(
		inUser     : String,
		inPassword : String,
		inConfirm  : String
	): String?
	{
		val vUser = inUser.trim()																// identifiant nettoyé
		return when
		{
			vUser.isEmpty()                 -> fContext.getString(R.string.error_user_empty)
			fPrefs.contains(key(vUser))     -> fContext.getString(R.string.error_user_taken)
			inPassword.length < kMinimum    -> fContext.getString(R.string.error_password_short, kMinimum)
			inPassword != inConfirm         -> fContext.getString(R.string.error_confirm)
			else                            ->
			{
				fPrefs.edit().putString(key(vUser), digest(vUser, inPassword)).putString(kSession, vUser).apply()
				null
			}
		}
	}

	/*
		Connecte un compte ; rend null si c'est fait, sinon la raison.
		inUser     : identifiant
		inPassword : mot de passe
	*/
	fun login(
		inUser     : String,
		inPassword : String
	): String?
	{
		val vUser = inUser.trim()																// identifiant nettoyé
		if (fPrefs.getString(key(vUser), null) != digest(vUser, inPassword)) return fContext.getString(R.string.error_login)
		fPrefs.edit().putString(kSession, vUser).apply()
		return null
	}

	/*
		Change le mot de passe du compte connecté ; rend null si c'est fait,
		sinon la raison.
		inCurrent : mot de passe actuel
		inNew     : nouveau mot de passe
		inConfirm : confirmation du nouveau mot de passe
	*/
	fun changePassword(
		inCurrent : String,
		inNew     : String,
		inConfirm : String
	): String?
	{
		val vUser = current ?: return fContext.getString(R.string.error_login)				// compte connecté
		return when
		{
			fPrefs.getString(key(vUser), null) != digest(vUser, inCurrent) -> fContext.getString(R.string.error_current)
			inNew.length < kMinimum                                        -> fContext.getString(R.string.error_password_short, kMinimum)
			inNew != inConfirm                                             -> fContext.getString(R.string.error_confirm)
			else                                                           ->
			{
				fPrefs.edit().putString(key(vUser), digest(vUser, inNew)).apply()
				null
			}
		}
	}

	/*
		Déconnecte le compte connecté.
	*/
	fun logout()
	{
		fPrefs.edit().remove(kSession).apply()
	}

	/*
		Clé d'un compte dans le fichier.
		inUser : identifiant
	*/
	private fun key(inUser: String): String = "compte:${inUser.lowercase()}"

	/*
		Empreinte SHA-256 d'un identifiant et d'un mot de passe, en hexadécimal.
		inUser     : identifiant
		inPassword : mot de passe
	*/
	private fun digest(
		inUser     : String,
		inPassword : String
	): String =
		MessageDigest.getInstance("SHA-256").digest("${inUser.lowercase()}\n$inPassword".toByteArray()).joinToString("") { "%02x".format(it) }
}

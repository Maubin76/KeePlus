package fr.maubin.keeplus.autofill

import fr.maubin.keeplus.vault.Vault
import java.util.Locale
import java.util.UUID

/*
	Ce que devient une connexion tapée à la main, comparée au coffre.
*/
sealed interface SavedLogin
{
	/*
		Compte inconnu : une entrée sera créée.
	*/
	data object New : SavedLogin

	/*
		Compte connu, mot de passe différent : l'entrée sera mise à jour.
		id    : entrée
		title : titre de l'entrée
	*/
	data class Update(
		val id    : UUID,
		val title : String
	) : SavedLogin

	/*
		Compte connu, même mot de passe : rien à faire.
		id : entrée
	*/
	data class Same(val id: UUID) : SavedLogin
}

/*
	Situation d'une connexion tapée à la main : le compte est cherché parmi
	les entrées de l'appli ou du site, par son identifiant (sans tenir
	compte des majuscules) ; sans identifiant, une entrée de la cible qui a
	déjà ce mot de passe suffit.
	inTarget   : appli ou site
	inUserName : identifiant tapé ; vide s'il n'y en avait pas
	inPassword : mot de passe tapé
*/
fun Vault.savedLogin(
	inTarget   : AutofillTarget,
	inUserName : String,
	inPassword : String
): SavedLogin
{
	val vUser    = inUserName.trim().lowercase(Locale.ROOT)									// identifiant cherché
	val vMatches = autofillMatches(inTarget)													// entrées de la cible
	if (vUser.isEmpty())
	{
		return vMatches.firstOrNull { draft(it.id)?.password == inPassword }?.let { SavedLogin.Same(it.id) } ?: SavedLogin.New
	}
	val vItem    = vMatches.firstOrNull { it.userName.trim().lowercase(Locale.ROOT) == vUser } ?: return SavedLogin.New	// entrée du compte
	return if (draft(vItem.id)?.password == inPassword) SavedLogin.Same(vItem.id) else SavedLogin.Update(vItem.id, vItem.title)
}

/*
	Enregistre une connexion tapée à la main : nouvelle entrée (titre, adresse
	de l'appli ou du site) ou mot de passe mis à jour (l'ancien rejoint
	l'historique de l'entrée) ; rien si le coffre le connaît déjà.
	inTarget   : appli ou site
	inTitle    : titre d'une nouvelle entrée
	inUserName : identifiant tapé
	inPassword : mot de passe tapé
*/
fun Vault.saveLogin(
	inTarget   : AutofillTarget,
	inTitle    : String,
	inUserName : String,
	inPassword : String
): Vault =
	when (val vState = savedLogin(inTarget, inUserName, inPassword))
	{
		SavedLogin.New       -> addSignUp(inTarget, inTitle, inUserName.trim(), inPassword)
		is SavedLogin.Update -> changePassword(vState.id, inPassword)
		is SavedLogin.Same   -> this
	}

/*
	Change le mot de passe d'une entrée ; l'ancien rejoint son historique.
	inId       : entrée
	inPassword : nouveau mot de passe
*/
fun Vault.changePassword(
	inId       : UUID,
	inPassword : String
): Vault = draft(inId)?.let { save(it.copy(password = inPassword)) } ?: this

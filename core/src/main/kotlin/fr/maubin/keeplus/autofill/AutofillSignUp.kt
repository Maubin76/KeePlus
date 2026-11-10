package fr.maubin.keeplus.autofill

import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.Vault
import java.util.UUID

/*
	Identifiant proposé pour une inscription : celui que le coffre utilise le
	plus souvent ; une adresse e-mail si le champ en attend une. Un champ
	d'identifiant sans identifiant connu reçoit l'adresse e-mail la plus
	utilisée. Vide si le coffre n'a rien qui convienne.
	inEmail : le champ attend une adresse e-mail
*/
fun Vault.suggestedUserName(inEmail: Boolean): String
{
	val vNames = items().map { it.userName.trim() }.filter { it.isNotEmpty() }			// identifiants du coffre
	val vKind  = vNames.filter { ('@' in it) == inEmail }									// ceux du bon genre
	val vPool  = if (vKind.isEmpty() && !inEmail) vNames else vKind						// candidats
	return vPool.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
}

/*
	Ajoute au coffre l'entrée d'un compte qu'on vient de créer : titre (nom
	de l'appli ou domaine), identifiant, mot de passe et adresse, pour qu'elle
	soit proposée d'emblée à la prochaine connexion (https://domaine pour un
	site, androidapp://… pour une appli).
	inTarget   : appli ou site du compte
	inTitle    : titre de l'entrée
	inUserName : identifiant choisi
	inPassword : mot de passe choisi
	inId       : identifiant de la nouvelle entrée
*/
fun Vault.addSignUp(
	inTarget   : AutofillTarget,
	inTitle    : String,
	inUserName : String,
	inPassword : String,
	inId       : UUID = UUID.randomUUID()
): Vault =
	save(
		EntryDraft(
			id       = inId,
			title    = inTitle,
			userName = inUserName,
			password = inPassword,
			url      = inTarget.domain?.let { "https://$it" } ?: "$kAppScheme${inTarget.packageName}"
		)
	)

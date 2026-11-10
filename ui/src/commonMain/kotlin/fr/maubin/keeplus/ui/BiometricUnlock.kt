package fr.maubin.keeplus.ui

/*
	L'empreinte ne peut plus servir (empreinte ajoutée au téléphone, clé
	effacée) : le mot de passe maître doit être ressaisi.
*/
class BiometricInvalidated : Exception("L'empreinte doit être réactivée : saisissez votre mot de passe.")

/*
	Déverrouillage par empreinte (Android) : le mot de passe maître est gardé
	chiffré par une clé de l'appareil, utilisable seulement après une
	authentification biométrique forte.
*/
interface BiometricUnlock
{
	/*
		Vrai si l'appareil a un capteur et au moins une empreinte enregistrée.
	*/
	val available: Boolean

	/*
		Vrai si le mot de passe maître est gardé pour l'empreinte.
	*/
	val enabled: Boolean

	/*
		Vrai si l'activation a déjà été proposée (acceptée ou refusée) : elle
		n'est proposée qu'une fois.
	*/
	val offered: Boolean

	/*
		Date du dernier déverrouillage par le mot de passe maître (ms depuis
		1970) ; 0 si inconnue. Au-delà du délai réglé, l'empreinte attend une
		nouvelle saisie du mot de passe.
	*/
	var passwordAt: Long

	/*
		Note que l'activation a été proposée.
	*/
	fun markOffered()

	/*
		L'activation sera proposée de nouveau au prochain déverrouillage par
		mot de passe.
	*/
	fun resetOffer()

	/*
		Active l'empreinte : demande l'empreinte, puis garde le mot de passe
		chiffré. Faux si l'utilisateur annule.
		inPassword : mot de passe maître
	*/
	suspend fun enable(inPassword: CharSequence): Boolean

	/*
		Demande l'empreinte et rend le mot de passe maître ; null si
		l'utilisateur annule. L'appelant efface le tableau après usage.
		Lève BiometricInvalidated si la clé n'est plus utilisable.
	*/
	suspend fun unlock(): CharArray?

	/*
		Oublie le mot de passe gardé et la clé.
	*/
	fun disable()
}

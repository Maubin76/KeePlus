package fr.maubin.keeplus.ui.platform

import androidx.compose.runtime.Composable

const val kClipboardSeconds = 30													// durée de vie d'un secret copié

/*
	Presse-papiers des secrets : copie marquée sensible (Android 13 et plus :
	pas d'aperçu dans la bulle du système), effacée au bout de
	kClipboardSeconds si elle n'a pas été remplacée entre-temps.
*/
interface SecretClipboard
{
	/*
		Copie un texte.
		inLabel  : nom de la donnée (« Mot de passe »)
		inText   : texte copié
		inSecret : donnée sensible : marquée comme telle et effacée après le délai
	*/
	fun copy(
		inLabel  : String,
		inText   : String,
		inSecret : Boolean
	)
}

/*
	Presse-papiers des secrets de la plateforme.
*/
@Composable
expect fun rememberSecretClipboard(): SecretClipboard

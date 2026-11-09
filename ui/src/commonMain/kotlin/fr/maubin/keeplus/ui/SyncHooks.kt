package fr.maubin.keeplus.ui

import fr.maubin.keeplus.sync.PendingMerge

/*
	Synchronisation vue par l'appli : coffres d'autres appareils à fusionner
	au déverrouillage, et signal de modification (le téléphone synchronise
	quelques secondes après chaque enregistrement).
*/
interface SyncHooks
{
	/*
		Coffres d'autres appareils en attente de fusion.
	*/
	fun pending(): List<PendingMerge>

	/*
		Un coffre en attente a été fusionné (ou écarté).
		inMerge  : coffre en attente
		inMerged : vrai s'il a été fusionné
	*/
	fun settle(
		inMerge  : PendingMerge,
		inMerged : Boolean
	)

	/*
		Le coffre de l'appareil vient d'être enregistré.
	*/
	fun changed()
}

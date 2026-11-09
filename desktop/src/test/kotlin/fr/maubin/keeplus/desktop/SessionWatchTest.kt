package fr.maubin.keeplus.desktop

import org.junit.Assert.assertFalse
import org.junit.Test

/*
	Détection du verrouillage de session : pendant les tests, la session est
	ouverte (ou le système n'est pas Windows) ; un appel cassé répondrait
	« verrouillée » et verrouillerait le coffre en boucle.
*/
class SessionWatchTest
{
	/*
		Session ouverte : pas de verrouillage détecté.
	*/
	@Test
	fun openSessionIsNotLocked()
	{
		assertFalse(SessionWatch.isLocked())
	}
}

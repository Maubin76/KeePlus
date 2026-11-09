package fr.maubin.keeplus.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	Détection du verrouillage de session : sous Windows, l'accès aux bureaux
	se charge et répond (verrouillée ou non selon l'instant : un test lancé
	pendant que la session est verrouillée ne doit pas échouer) ; ailleurs,
	rien n'est surveillé et la session n'est jamais vue verrouillée.
*/
class SessionWatchTest
{
	/*
		Chargement et réponse stables.
	*/
	@Test
	fun bindingLoadsAndAnswers()
	{
		val vWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")		// système
		assertEquals(vWindows, SessionWatch.available)
		val vFirst = SessionWatch.isLocked()													// première réponse
		assertEquals(vFirst, SessionWatch.isLocked())
		if (!vWindows) assertFalse(vFirst) else assertTrue(SessionWatch.available)
	}
}

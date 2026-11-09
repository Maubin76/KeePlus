package fr.maubin.keeplus.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val kSwitchDesktop = 0x0100													// DESKTOP_SWITCHDESKTOP
private const val kPollMillis    = 1500L														// intervalle de vérification

/*
	Accès aux bureaux de Windows (user32.dll).
*/
private interface Desktops : Library
{
	/*
		Ouvre le bureau qui reçoit les saisies ; null quand la session est
		verrouillée (le bureau sécurisé de Windows a la main).
		flags   : options (0)
		inherit : handle héritable
		access  : droits demandés
	*/
	fun OpenInputDesktop(
		flags   : Int,
		inherit : Boolean,
		access  : Int
	): Pointer?

	/*
		Ferme un bureau ouvert.
		desktop : bureau
	*/
	fun CloseDesktop(desktop: Pointer): Boolean
}

/*
	Surveillance du verrouillage de la session Windows (Win+L, mise en veille
	avec mot de passe) : le bureau des saisies devient inaccessible. Le
	bureau sécurisé d'une demande d'élévation (UAC) produit le même effet :
	le coffre se verrouille aussi dans ce cas, ce qui reste prudent. Hors
	Windows, rien n'est surveillé.
*/
object SessionWatch
{
	private val fDesktops: Desktops? by lazy {
		if (System.getProperty("os.name").orEmpty().startsWith("Windows")) runCatching { Native.load("user32", Desktops::class.java) }.getOrNull() else null
	}																						// bureaux ; null hors Windows

	/*
		Vrai si la session est verrouillée en ce moment.
	*/
	fun isLocked(): Boolean
	{
		val vDesktops = fDesktops ?: return false												// bureaux
		val vHandle   = vDesktops.OpenInputDesktop(0, false, kSwitchDesktop) ?: return true	// bureau des saisies
		vDesktops.CloseDesktop(vHandle)
		return false
	}

	/*
		Surveille la session et appelle inOnLock à chaque verrouillage, jusqu'à
		l'annulation de la coroutine.
		inOnLock : action au verrouillage (sur le fil de l'interface)
	*/
	suspend fun watch(inOnLock: () -> Unit)
	{
		if (fDesktops == null) return
		var vWasLocked = false																// état précédent
		while (currentCoroutineContext().isActive)
		{
			val vLocked = withContext(Dispatchers.IO) { runCatching { isLocked() }.getOrDefault(false) }	// état actuel
			if (vLocked && !vWasLocked) inOnLock()
			vWasLocked = vLocked
			delay(kPollMillis)
		}
	}
}

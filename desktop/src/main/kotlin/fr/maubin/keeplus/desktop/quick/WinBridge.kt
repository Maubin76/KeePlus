package fr.maubin.keeplus.desktop.quick

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.W32APIOptions
import fr.maubin.keeplus.autofill.PasteSequence
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val kHotkeyId       = 0x4B50											// identifiant du raccourci global
private const val kEscapeId       = 0x4B51											// identifiant de la touche Échap réservée
private const val kVkEscape       = 0x1B												// VK_ESCAPE
private const val kModNoRepeat    = 0x4000											// MOD_NOREPEAT : pas de répétition en maintenant les touches
private const val kHwndMessage    = -3												// HWND_MESSAGE : fenêtre de messages seulement
private const val kWmHotkey       = 0x0312											// WM_HOTKEY
private const val kWmRenderFormat = 0x0305											// WM_RENDERFORMAT : une appli lit le presse-papiers
private const val kWmRenderAll    = 0x0306											// WM_RENDERALLFORMATS : fin de l'appli
private const val kWmDestroyClip  = 0x0307											// WM_DESTROYCLIPBOARD : une autre appli a pris le presse-papiers
private const val kWmApp          = 0x8000											// WM_APP : messages de KeePlus
private const val kMsgRegister    = kWmApp + 1										// changer le raccourci
private const val kMsgOffer       = kWmApp + 2										// proposer l'élément courant de la séquence
private const val kMsgClear       = kWmApp + 3										// effacer le presse-papiers
private const val kMsgEscapeOn    = kWmApp + 4										// réserver la touche Échap
private const val kMsgEscapeOff   = kWmApp + 5										// libérer la touche Échap
private const val kCfUnicodeText  = 13												// CF_UNICODETEXT
private const val kGmemMoveable   = 0x0002											// GMEM_MOVEABLE
private const val kNextDelayMs    = 400L												// délai avant de proposer l'élément suivant (l'appli finit de lire)

/*
	Fonctions du presse-papiers de Windows (user32.dll).
*/
private interface Clipboard : Library
{
	/*
		Ouvre le presse-papiers pour une fenêtre.
		hwnd : fenêtre propriétaire
	*/
	fun OpenClipboard(hwnd: WinDef.HWND?): Boolean

	/*
		Ferme le presse-papiers.
	*/
	fun CloseClipboard(): Boolean

	/*
		Vide le presse-papiers ; la fenêtre qui l'a ouvert en devient propriétaire.
	*/
	fun EmptyClipboard(): Boolean

	/*
		Dépose une donnée ; null : rendu différé (WM_RENDERFORMAT à la lecture).
		format : format
		data   : mémoire globale ; null pour différer
	*/
	fun SetClipboardData(
		format : Int,
		data   : Pointer?
	): Pointer?

	/*
		Enregistre un format nommé.
		name : nom du format
	*/
	fun RegisterClipboardFormat(name: String): Int

	/*
		Fenêtre propriétaire actuelle du presse-papiers.
	*/
	fun GetClipboardOwner(): WinDef.HWND?
}

/*
	Mémoire globale de Windows (kernel32.dll), pour les données du presse-papiers.
*/
private interface GlobalMemory : Library
{
	/*
		Réserve un bloc de mémoire.
		flags : options
		bytes : taille
	*/
	fun GlobalAlloc(
		flags : Int,
		bytes : Long
	): Pointer?

	/*
		Rend l'adresse d'un bloc.
		mem : bloc
	*/
	fun GlobalLock(mem: Pointer): Pointer?

	/*
		Libère l'adresse d'un bloc.
		mem : bloc
	*/
	fun GlobalUnlock(mem: Pointer): Boolean
}

/*
	Pont avec Windows pour la carte de saisie, sur un fil dédié qui fait
	tourner la boucle de messages d'une fenêtre invisible :
	- raccourci clavier global (RegisterHotKey) ;
	- touche Échap réservée tant que la carte est affichée : elle la ferme
	  même quand la carte n'est pas au premier plan ;
	- presse-papiers enchaîné : la séquence est déposée en rendu différé, si
	  bien que Windows prévient KeePlus au moment où une appli colle ; KeePlus
	  rend l'élément courant, puis propose le suivant un instant plus tard.
	  Les secrets sont exclus de l'historique (Win+V) et de la
	  synchronisation du presse-papiers, et effacés après le délai réglé.
	Hors Windows, rien n'est disponible (available faux).
	fOnHotkey  : appelé à chaque appui du raccourci (sur le fil de Windows)
	fOnEscape  : appelé à chaque appui d'Échap réservée (sur le fil de Windows)
	fUser      : fonctions des fenêtres
	fClip      : fonctions du presse-papiers
	fMemory    : mémoire globale
	fProc      : procédure de la fenêtre (gardée en vie)
	fHwnd      : fenêtre invisible
	fThread    : fil de Windows
	fSequence  : séquence en cours ; null sans
	fPending   : raccourci à enregistrer (modificateurs, touche)
	fTimer     : délais (élément suivant, effacement)
	fFormats   : formats d'exclusion (historique, synchronisation)
	fHotkeyOk  : dernier enregistrement du raccourci accepté par Windows
	fDone      : signal de fin de l'enregistrement en cours
*/
class WinBridge(
	private val fOnHotkey : () -> Unit,
	private val fOnEscape : () -> Unit = {}
)
{
	private val fUser    = User32.INSTANCE
	private val fClip    = Native.load("user32", Clipboard::class.java, W32APIOptions.DEFAULT_OPTIONS)
	private val fMemory  = Native.load("kernel32", GlobalMemory::class.java)
	private var fProc    : WinUser.WindowProc? = null
	@Volatile private var fHwnd    : WinDef.HWND? = null
	@Volatile private var fThread  : Int          = 0
	@Volatile private var fSequence : PasteSequence? = null
	@Volatile private var fPending : Pair<Int, Int>? = null
	@Volatile private var fHotkeyOk : Boolean = false
	private val fTimer   = Executors.newSingleThreadScheduledExecutor { Thread(it, "presse-papiers enchaîné").apply { isDaemon = true } }
	private var fFormats : IntArray = IntArray(0)

	/*
		Code d'erreur de Windows à la création de la fenêtre ; 0 si elle existe.
	*/
	@Volatile var fError: Int = 0
		private set

	/*
		Vrai si la fenêtre invisible existe (le pont fonctionne).
	*/
	val ready: Boolean get() = fHwnd != null

	/*
		Démarre le fil de Windows et attend que la fenêtre existe.
	*/
	fun start()
	{
		val vReady = CountDownLatch(1)															// fenêtre créée
		Thread({ loop(vReady) }, "raccourci et presse-papiers").apply { isDaemon = true; start() }
		vReady.await(5, TimeUnit.SECONDS)
	}

	/*
		Enregistre le raccourci global (remplace le précédent) ; rend faux s'il
		est déjà pris par une autre appli.
		inModifiers : modificateurs Windows (MOD_CONTROL…)
		inKey       : touche (code virtuel)
	*/
	fun register(
		inModifiers : Int,
		inKey       : Int
	): Boolean
	{
		fPending  = inModifiers to inKey
		fHotkeyOk = false
		val vDone = CountDownLatch(1)															// enregistrement fait
		fDone     = vDone
		post(kMsgRegister)
		vDone.await(2, TimeUnit.SECONDS)
		return fHotkeyOk
	}

	@Volatile private var fDone: CountDownLatch? = null

	/*
		Réserve ou libère la touche Échap (carte affichée ou non).
		inOn : réserver
	*/
	fun escape(inOn: Boolean)
	{
		post(if (inOn) kMsgEscapeOn else kMsgEscapeOff)
	}

	/*
		Dépose une séquence dans le presse-papiers enchaîné ; elle sera
		effacée après inClearSeconds secondes.
		inSequence     : éléments à coller
		inClearSeconds : délai d'effacement
	*/
	fun offer(
		inSequence     : PasteSequence,
		inClearSeconds : Int
	)
	{
		fSequence = inSequence
		post(kMsgOffer)
		fTimer.schedule({ if (fSequence === inSequence) post(kMsgClear) }, inClearSeconds.toLong(), TimeUnit.SECONDS)
	}

	/*
		Boucle de messages : fenêtre invisible, puis messages jusqu'à la fin.
		inReady : signal de la fenêtre créée
	*/
	private fun loop(inReady: CountDownLatch)
	{
		fThread  = Kernel32.INSTANCE.GetCurrentThreadId()
		fFormats = intArrayOf(
			fClip.RegisterClipboardFormat("ExcludeClipboardContentFromMonitorProcessing"),
			fClip.RegisterClipboardFormat("CanIncludeInClipboardHistory"),
			fClip.RegisterClipboardFormat("CanUploadToCloudClipboard")
		)
		val vProc = WinUser.WindowProc { inHwnd, inMsg, inW, inL -> handle(inHwnd, inMsg, inW, inL) }	// procédure
		fProc     = vProc
		val vClass = WinUser.WNDCLASSEX().apply {
			hInstance     = Kernel32.INSTANCE.GetModuleHandle(null)
			lpfnWndProc   = vProc
			lpszClassName = "KeePlusSaisie"
		}																						// classe de la fenêtre
		fUser.RegisterClassEx(vClass)
		fHwnd = fUser.CreateWindowEx(0, "KeePlusSaisie", "KeePlus", 0, 0, 0, 0, 0, WinDef.HWND(Pointer.createConstant(kHwndMessage.toLong())), null, vClass.hInstance, null)
		fError = if (fHwnd == null) Kernel32.INSTANCE.GetLastError() else 0
		inReady.countDown()
		val vMsg = WinUser.MSG()																// message reçu
		while (fUser.GetMessage(vMsg, null, 0, 0) > 0)
		{
			fUser.TranslateMessage(vMsg)
			fUser.DispatchMessage(vMsg)
		}
	}

	/*
		Messages de la fenêtre invisible.
	*/
	private fun handle(
		inHwnd : WinDef.HWND,
		inMsg  : Int,
		inW    : WinDef.WPARAM,
		inL    : WinDef.LPARAM
	): WinDef.LRESULT
	{
		when (inMsg)
		{
			kWmHotkey       -> runCatching { if (inW.toInt() == kEscapeId) fOnEscape() else fOnHotkey() }
			kMsgEscapeOn    -> fUser.RegisterHotKey(inHwnd, kEscapeId, kModNoRepeat, kVkEscape)
			kMsgEscapeOff   -> fUser.UnregisterHotKey(inHwnd.pointer, kEscapeId)
			kMsgRegister    -> registerNow(inHwnd)
			kMsgOffer       -> offerNow(inHwnd)
			kMsgClear       -> clearNow(inHwnd)
			kWmRenderFormat -> if (inW.toInt() == kCfUnicodeText) renderNow()
			kWmRenderAll    -> {}
			kWmDestroyClip  -> fSequence = null
			else            -> return fUser.DefWindowProc(inHwnd, inMsg, inW, inL)
		}
		return WinDef.LRESULT(0)
	}

	/*
		Enregistre le raccourci demandé.
		inHwnd : fenêtre invisible
	*/
	private fun registerNow(inHwnd: WinDef.HWND)
	{
		fUser.UnregisterHotKey(inHwnd.pointer, kHotkeyId)
		val vKey  = fPending																	// raccourci demandé
		fHotkeyOk = vKey != null && fUser.RegisterHotKey(inHwnd, kHotkeyId, vKey.first or kModNoRepeat, vKey.second)
		fDone?.countDown()
	}

	/*
		Dépose l'élément courant en rendu différé, avec les formats qui
		l'excluent de l'historique et de la synchronisation.
		inHwnd : fenêtre invisible
	*/
	private fun offerNow(inHwnd: WinDef.HWND)
	{
		if (fSequence == null || !open(inHwnd)) return
		try
		{
			fClip.EmptyClipboard()
			fClip.SetClipboardData(kCfUnicodeText, null)
			for (vFormat in fFormats) if (vFormat != 0) global(ByteArray(4))?.let { fClip.SetClipboardData(vFormat, it) }
		}
		finally
		{
			fClip.CloseClipboard()
		}
	}

	/*
		Une appli colle : rend l'élément courant, puis propose le suivant un
		instant plus tard (le temps que l'appli finisse de lire).
	*/
	private fun renderNow()
	{
		val vSequence = fSequence ?: return													// séquence en cours
		global((vSequence.current + "\u0000").toByteArray(Charsets.UTF_16LE))?.let { fClip.SetClipboardData(kCfUnicodeText, it) }
		if (vSequence.advance()) fTimer.schedule({ if (fSequence === vSequence) post(kMsgOffer) }, kNextDelayMs, TimeUnit.MILLISECONDS)
	}

	/*
		Efface le presse-papiers s'il contient encore la séquence.
		inHwnd : fenêtre invisible
	*/
	private fun clearNow(inHwnd: WinDef.HWND)
	{
		fSequence = null
		if (fClip.GetClipboardOwner()?.pointer != inHwnd.pointer || !open(inHwnd)) return
		fClip.EmptyClipboard()
		fClip.CloseClipboard()
	}

	/*
		Ouvre le presse-papiers, en réessayant un court instant s'il est
		occupé par une autre appli ; faux s'il reste inaccessible.
		inHwnd : fenêtre invisible
	*/
	private fun open(inHwnd: WinDef.HWND): Boolean
	{
		repeat(10)
		{
			if (fClip.OpenClipboard(inHwnd)) return true
			Thread.sleep(20)
		}
		return false
	}

	/*
		Copie des octets dans un bloc de mémoire globale ; null en cas d'échec.
		inBytes : contenu
	*/
	private fun global(inBytes: ByteArray): Pointer?
	{
		val vMem = fMemory.GlobalAlloc(kGmemMoveable, inBytes.size.toLong()) ?: return null	// bloc
		val vPtr = fMemory.GlobalLock(vMem) ?: return null										// adresse
		vPtr.write(0, inBytes, 0, inBytes.size)
		fMemory.GlobalUnlock(vMem)
		return vMem
	}

	/*
		Envoie un message de KeePlus à la fenêtre invisible.
		inMsg : message
	*/
	private fun post(inMsg: Int)
	{
		fHwnd?.let { fUser.PostMessage(it, inMsg, WinDef.WPARAM(0), WinDef.LPARAM(0)) }
	}

	companion object
	{
		/*
			Vrai sous Windows : le pont peut fonctionner.
		*/
		val available: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")
	}
}

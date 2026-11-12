package fr.maubin.keeplus.desktop.quick

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Kernel32Util
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import fr.maubin.keeplus.autofill.DesktopTarget
import java.awt.Rectangle
import java.io.File
import java.util.concurrent.TimeUnit

private const val kQueryLimited = 0x1000											// PROCESS_QUERY_LIMITED_INFORMATION
private const val kNearest      = 2													// MONITOR_DEFAULTTONEAREST
private const val kUrlTimeoutS  = 4L												// attente maximale de la lecture de l'adresse

/*
	Lecture de l'adresse d'une fenêtre de navigateur par l'accessibilité de
	Windows (UI Automation, via PowerShell) : premier champ de saisie de la
	fenêtre, qui est la barre d'adresse dans Chrome, Edge, Brave, Firefox…
	{HWND} est remplacé par la fenêtre.
*/
private const val kUrlScript = """
${'$'}ErrorActionPreference = 'SilentlyContinue'
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes
${'$'}w = [System.Windows.Automation.AutomationElement]::FromHandle([IntPtr]{HWND})
${'$'}c = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::ControlTypeProperty, [System.Windows.Automation.ControlType]::Edit)
${'$'}e = ${'$'}w.FindFirst([System.Windows.Automation.TreeScope]::Descendants, ${'$'}c)
if (${'$'}e) { ${'$'}e.GetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern).Current.Value }
"""

/*
	Fenêtre active au moment du raccourci : son handle (pour lui rendre la
	main), ce qu'elle affiche, et l'écran qui la contient.
	hwnd   : fenêtre
	target : programme, titre (domaine lu plus tard pour un navigateur)
	screen : zone utile de son écran, en pixels
	pid    : processus de la fenêtre (0 si inconnu)
*/
class ActiveWindow(
	val hwnd   : WinDef.HWND?,
	val target : DesktopTarget,
	val screen : Rectangle?,
	val pid    : Int = 0
)
{
	/*
		Vrai si la fenêtre appartient à KeePlus lui-même (carte déjà ouverte,
		fenêtre principale) : le raccourci ne rouvre pas la carte dessus.
	*/
	val isOwn: Boolean get() = pid != 0 && pid.toLong() == ProcessHandle.current().pid()

	/*
		Rend la main à cette fenêtre (après la fermeture de la carte).
	*/
	fun restore()
	{
		hwnd?.let { runCatching { User32.INSTANCE.SetForegroundWindow(it) } }
	}

	/*
		Domaine affiché par un navigateur, lu dans sa barre d'adresse ; null
		hors navigateur ou si la lecture échoue. Lent (une à deux secondes) :
		à appeler hors du fil de l'interface.
	*/
	fun readUrl(): String?
	{
		val vHwnd = hwnd ?: return null															// fenêtre
		if (!target.isBrowser) return null
		return runCatching {
			val vScript  = kUrlScript.replace("{HWND}", com.sun.jna.Pointer.nativeValue(vHwnd.pointer).toString())	// script
			val vProcess = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", vScript)
				.redirectErrorStream(true).start()													// lecture
			if (!vProcess.waitFor(kUrlTimeoutS, TimeUnit.SECONDS)) { vProcess.destroyForcibly(); return null }
			vProcess.inputStream.bufferedReader().readText().trim().lineSequence().lastOrNull { it.isNotBlank() }
		}.getOrNull()?.takeIf { '@' !in it && ' ' !in it.trim() }
	}

	companion object
	{
		/*
			Fenêtre active en ce moment ; vide hors Windows.
		*/
		fun current(): ActiveWindow
		{
			if (!WinBridge.available) return ActiveWindow(null, DesktopTarget("", ""), null)
			val vUser  = User32.INSTANCE															// fonctions des fenêtres
			val vHwnd  = vUser.GetForegroundWindow()												// fenêtre active
			val vText  = CharArray(512)																// titre
			val vTitle = if (vHwnd != null) String(vText, 0, vUser.GetWindowText(vHwnd, vText, vText.size)) else ""
			val vPid   = IntByReference()															// processus
			if (vHwnd != null) vUser.GetWindowThreadProcessId(vHwnd, vPid)
			val vExe   = runCatching {
				val vProcess = Kernel32.INSTANCE.OpenProcess(kQueryLimited, false, vPid.value)	// accès au processus
				try { File(Kernel32Util.QueryFullProcessImageName(vProcess, 0)).name.lowercase() } finally { Kernel32.INSTANCE.CloseHandle(vProcess) }
			}.getOrDefault("")																		// programme
			return ActiveWindow(vHwnd, DesktopTarget(vExe, vTitle), screenOf(vHwnd), vPid.value)
		}

		/*
			Zone utile de l'écran qui contient une fenêtre (sans la barre des tâches).
			inHwnd : fenêtre
		*/
		private fun screenOf(inHwnd: WinDef.HWND?): Rectangle?
		{
			if (inHwnd == null) return null
			return runCatching {
				val vInfo = WinUser.MONITORINFO()													// écran
				User32.INSTANCE.GetMonitorInfo(User32.INSTANCE.MonitorFromWindow(inHwnd, kNearest), vInfo)
				vInfo.rcWork.let { Rectangle(it.left, it.top, it.right - it.left, it.bottom - it.top) }
			}.getOrNull()
		}
	}
}

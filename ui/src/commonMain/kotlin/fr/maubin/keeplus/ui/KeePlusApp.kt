package fr.maubin.keeplus.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import fr.maubin.keeplus.ui.screens.CreateScreen
import fr.maubin.keeplus.ui.screens.UnlockScreen
import fr.maubin.keeplus.ui.screens.VaultScreen
import fr.maubin.keeplus.ui.screens.WelcomeScreen
import fr.maubin.keeplus.ui.theme.KeePlusTheme
import kotlinx.coroutines.launch

/*
	Interface de KeePlus, commune à Android et au PC : enchaînement des
	écrans selon l'état de l'appli, en fondu.
	inController : état de l'appli
*/
@Composable
fun KeePlusApp(inController: AppController)
{
	val vScope = rememberCoroutineScope()													// opérations lancées par les écrans
	KeePlusTheme {
		Crossfade(inController.screen, animationSpec = tween(220), label = "écran") { vScreen ->
			when (vScreen)
			{
				AppScreen.Welcome -> WelcomeScreen(
					inError    = inController.error,
					inBusy     = inController.busy,
					inOnCreate = inController::startCreate,
					inOnImport = { vScope.launch { inController.startImport() } },
					inOnDemo   = { vScope.launch { inController.openDemo() } }
				)
				AppScreen.Create -> CreateScreen(
					inError    = inController.error,
					inBusy     = inController.busy,
					inOnBack   = inController::back,
					inOnCreate = inController::create,
					inOnDone   = inController::open
				)
				is AppScreen.Import -> UnlockScreen(
					inFileName = vScreen.file.name,
					inError    = inController.error,
					inBusy     = inController.busy,
					inOnBack   = inController::back,
					inOnUnlock = inController::import,
					inOnTyping = inController::clearError,
					inOnDone   = inController::open
				)
				AppScreen.Locked -> UnlockScreen(
					inFileName = null,
					inError    = inController.error,
					inBusy     = inController.busy,
					inOnBack   = null,
					inOnUnlock = inController::unlock,
					inOnTyping = inController::clearError,
					inOnDone   = inController::open,
					inOnDemo   = { vScope.launch { inController.openDemo() } }
				)
				is AppScreen.Opened -> VaultScreen(inController, vScreen.vault, vScreen.demo)
			}
		}
	}
}

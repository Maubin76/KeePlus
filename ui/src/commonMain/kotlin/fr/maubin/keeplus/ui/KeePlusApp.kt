package fr.maubin.keeplus.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import fr.maubin.keeplus.ui.screens.BiometricOfferDialog
import fr.maubin.keeplus.ui.screens.CreateScreen
import fr.maubin.keeplus.ui.screens.UnlockScreen
import fr.maubin.keeplus.ui.screens.VaultScreen
import fr.maubin.keeplus.ui.screens.WelcomeScreen
import fr.maubin.keeplus.ui.theme.KeePlusTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
	Interface de KeePlus, commune à Android et au PC : enchaînement des
	écrans selon l'état de l'appli, en fondu. Chaque geste (toucher, souris,
	clavier) repousse le verrouillage automatique, vérifié chaque seconde.
	À l'ouverture du coffre, les coffres d'autres appareils en attente sont
	fusionnés.
	inController : état de l'appli
	inSettings   : réglages propres à la plateforme, ajoutés à la page Réglages ; null sans
	inDevices    : écran des appareils (synchronisation), propre à la plateforme ; null sans
*/
@Composable
fun KeePlusApp(
	inController : AppController,
	inSettings   : (@Composable ColumnScope.() -> Unit)? = null,
	inDevices    : (@Composable () -> Unit)?             = null
)
{
	val vScope = rememberCoroutineScope()													// opérations lancées par les écrans
	LaunchedEffect(inController) {
		while (true)
		{
			delay(1000)
			inController.checkAutoLock()
		}
	}
	val vOpened = inController.screen is AppScreen.Opened										// coffre ouvert
	LaunchedEffect(vOpened) { if (vOpened) inController.mergePending() }
	KeePlusTheme {
		Box(
			Modifier
				.fillMaxSize()
				.pointerInput(inController) {
					awaitPointerEventScope {
						while (true)
						{
							awaitPointerEvent(PointerEventPass.Initial)
							inController.touch()
						}
					}
				}
				.onPreviewKeyEvent { inController.touch(); false }
		) {
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
						inOnDemo   = { vScope.launch { inController.openDemo() } },
						inOnFinger = if (inController.biometricReady) inController::unlockWithBiometric else null,
						inReady    = !inController.introPlaying
					)
					is AppScreen.Opened -> VaultScreen(inController, vScreen.vault, vScreen.demo, inDevices, inSettings)
				}
			}
			if (inController.offerBiometric && inController.screen is AppScreen.Opened)
			{
				BiometricOfferDialog(
					inOnAnswer = { vAccept -> vScope.launch { inController.answerBiometricOffer(vAccept) } }
				)
			}
		}
	}
}

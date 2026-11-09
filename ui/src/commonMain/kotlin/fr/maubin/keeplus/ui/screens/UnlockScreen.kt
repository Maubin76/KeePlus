package fr.maubin.keeplus.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.ui.components.GhostButton
import fr.maubin.keeplus.ui.components.SecretField
import fr.maubin.keeplus.ui.components.SilverButton
import fr.maubin.keeplus.ui.components.TextLink
import fr.maubin.keeplus.ui.platform.animationsEnabled
import fr.maubin.keeplus.ui.theme.keeType
import fr.maubin.keeplus.vault.Vault
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val kWaveMillis = 650														// durée de la vague
private const val kFadeMillis = 450														// durée de la dissipation
private const val kFadeDelay  = 300														// départ de la dissipation après la vague

/*
	Déverrouillage du coffre de l'appareil, ou ouverture d'un coffre choisi
	avant de le reprendre. Mot de passe faux : le champ tremble, double
	vibration. Mot de passe juste : vibration courte, la grille s'illumine en
	vague depuis le centre, puis la brume se dissipe vers le coffre ouvert.
	inFileName : nom du fichier choisi ; null pour le coffre de l'appareil
	inError    : message d'erreur de la dernière tentative ; null si aucun
	inBusy     : ouverture en cours (Argon2)
	inOnBack   : retour ; null s'il n'y en a pas
	inOnUnlock : ouvre le coffre avec le mot de passe ; null en cas d'échec
	inOnTyping : saisie reprise (efface l'erreur)
	inOnDone   : coffre ouvert, à afficher (après l'animation)
	inOnDemo   : ouvrir le coffre de démonstration ; null pour ne pas le proposer
	inOnFinger : ouvre le coffre par l'empreinte (null si annulé) ; null si
	             l'empreinte n'est pas active. Proposée d'emblée à l'affichage.
*/
@Composable
fun UnlockScreen(
	inFileName : String?,
	inError    : String?,
	inBusy     : Boolean,
	inOnBack   : (() -> Unit)?,
	inOnUnlock : suspend (CharSequence) -> Vault?,
	inOnTyping : () -> Unit,
	inOnDone   : (Vault) -> Unit,
	inOnDemo   : (() -> Unit)? = null,
	inOnFinger : (suspend () -> Vault?)? = null
)
{
	val vScope    = rememberCoroutineScope()												// ouverture et animations
	val vPassword = rememberTextFieldState()												// mot de passe saisi
	val vFocus    = remember { FocusRequester() }											// focus du champ
	val vHaptic   = LocalHapticFeedback.current												// vibrations
	val vAnimated = animationsEnabled()														// effets marqués permis
	val vGlow     = remember { Animatable(-1f) }											// vague de la grille
	val vFade     = remember { Animatable(0f) }												// dissipation du décor
	val vShake    = remember { Animatable(0f) }												// tremblement du champ (dp)
	LaunchedEffect(vPassword.text) { if (inError != null) inOnTyping() }
	/*
		Tente une ouverture et joue l'animation qui suit : déverrouillage
		réussi, ou tremblement et double vibration en cas d'échec.
		inOpen   : ouverture ; null en cas d'échec ou d'abandon
		inSignal : signaler un échec (pas pour une empreinte annulée)
	*/
	fun attempt(
		inOpen   : suspend () -> Vault?,
		inSignal : () -> Boolean
	)
	{
		if (inBusy || vFade.value > 0f) return
		vScope.launch {
			val vVault = inOpen()																// coffre ouvert
			if (vVault == null)
			{
				if (!inSignal()) return@launch
				vHaptic.performHapticFeedback(HapticFeedbackType.Reject)
				if (vAnimated)
				{
					vShake.snapTo(0f)
					vShake.animateTo(0f, keyframes {
						durationMillis = 360
						-10f at 50; 9f at 110; -7f at 170; 5f at 230; -2f at 290; 0f at 360
					})
				}
				return@launch
			}
			clear(vPassword)
			vHaptic.performHapticFeedback(HapticFeedbackType.Confirm)
			if (vAnimated)
			{
				vGlow.snapTo(0f)
				launch { vGlow.animateTo(1f, tween(kWaveMillis, easing = LinearEasing)) }
				delay(kFadeDelay.toLong())
			}
			vFade.animateTo(1f, tween(if (vAnimated) kFadeMillis else 200, easing = FastOutSlowInEasing))
			inOnDone(vVault)
		}
	}
	/*
		Ouverture par le mot de passe saisi.
	*/
	fun submit()
	{
		if (vPassword.text.isEmpty()) return
		attempt({ inOnUnlock(vPassword.text) }, { true })
	}
	/*
		Ouverture par l'empreinte ; un abandon ne fait ni trembler ni vibrer.
	*/
	fun finger()
	{
		val vFinger = inOnFinger ?: return															// ouverture par l'empreinte
		attempt(vFinger, { false })
	}
	LaunchedEffect(Unit) {
		if (inOnFinger != null)
		{
			delay(250)
			finger()
		}
		else runCatching { vFocus.requestFocus() }
	}
	KeyScreen(inGlow = vGlow.value, inFade = vFade.value, inOnBack = inOnBack) {
		if (inFileName == null) HeroTitle("Déverrouillez\n", "votre ", "coffre.")
		else HeroTitle("Reprenez\n", "ce ", "coffre.")
		BasicText(
			if (inFileName == null) (if (inOnFinger != null) "Touchez le capteur, ou saisissez votre mot de passe maître." else "Saisissez votre mot de passe maître.")
			else "« $inFileName » deviendra le coffre de cet appareil. Saisissez son mot de passe : le fichier d'origine n'est pas modifié.",
			style = keeType().bodySmall
		)
		Spacer(Modifier.height(4.dp))
		SecretField(
			vPassword,
			"Mot de passe maître",
			Modifier.focusRequester(vFocus).graphicsLayer { translationX = vShake.value * density },
			inError    = inError != null,
			inEnabled  = !inBusy,
			inOnSubmit = ::submit
		)
		ErrorLine(inError)
		Spacer(Modifier.height(4.dp))
		SilverButton(if (inBusy) "Ouverture…" else "Déverrouiller", ::submit, inEnabled = !inBusy && vPassword.text.isNotEmpty())
		if (inOnFinger != null) GhostButton("Utiliser l'empreinte", { finger() }, inEnabled = !inBusy)
		if (inOnBack != null && inFileName != null) TextLink("Choisir un autre fichier", inOnBack, Modifier.align(Alignment.CenterHorizontally))
		if (inOnDemo != null) TextLink("Voir le coffre de démonstration", inOnDemo, Modifier.align(Alignment.CenterHorizontally))
	}
}

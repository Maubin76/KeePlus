package fr.maubin.keeplus.ui.platform

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/*
	Vibration d'un choc lourd : primitive « thud » (sourde) si le vibreur la
	sait jouer, sinon clic fort prédéfini.
*/
@Composable
actual fun rememberImpactFeedback(): () -> Unit
{
	val vContext = LocalContext.current													// accès au vibreur
	return remember(vContext) {
		{
			runCatching {
				val vVibrator = vContext.getSystemService(Vibrator::class.java)						// vibreur
				val vEffect   = if (vVibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD))
					VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f).compose()
				else VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)	// effet joué
				vVibrator.vibrate(vEffect)
			}
		}
	}
}

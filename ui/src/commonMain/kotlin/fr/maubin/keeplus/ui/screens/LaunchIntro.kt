package fr.maubin.keeplus.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.ui.platform.animationsEnabled
import fr.maubin.keeplus.ui.platform.rememberImpactFeedback
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.drawLogoMetal
import fr.maubin.keeplus.ui.theme.drawLogoShield
import fr.maubin.keeplus.ui.theme.shieldPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow

private const val kPlateMillis  = 180														// plaque seule, avant la chute
private const val kFallMillis   = 460														// chute du bouclier
private const val kImpactMillis = 380														// choc : rebond, enfoncement, sursaut
private const val kCarveMillis  = 300														// gravure qui apparaît après le choc
private const val kHoldMillis   = 380														// logo formé, immobile
private const val kFadeMillis   = 420														// fondu vers l'écran de connexion
private const val kFallScale    = 2.8f														// taille du bouclier au départ de la chute
private val kGravity            = CubicBezierEasing(0.65f, 0f, 0.95f, 0.45f)				// chute lourde, qui accélère jusqu'au choc

/*
	État d'une image de l'animation d'ouverture.
	fall   : avancée de la chute, de 0 (bouclier loin, invisible) à 1 (posé)
	carve  : gravure dans le métal, de 0 à 1
	bounce : rebond du bouclier au choc (écart de taille : positif, il s'étale)
	press  : enfoncement de la plaque au choc (0 : au repos, 1 : enfoncée au plus fort)
	impact : intensité du choc, de 1 à l'impact à 0 (ombre de contact)
	shakeX : sursaut horizontal de l'écran (dp)
	shakeY : sursaut vertical de l'écran (dp)
	fade   : opacité de l'ensemble, de 1 à 0
*/
data class IntroState(
	val fall   : Float = 0f,
	val carve  : Float = 0f,
	val bounce : Float = 0f,
	val press  : Float = 0f,
	val impact : Float = 0f,
	val shakeX : Float = 0f,
	val shakeY : Float = 0f,
	val fade   : Float = 1f
)

/*
	Animation d'ouverture : tout l'écran devient la plaque d'aluminium
	brossé du logo ; le bouclier arrive de loin, grand et transparent, et
	tombe lourdement. Au choc : vibration sourde, la plaque s'enfonce puis
	remonte, le bouclier rebondit, l'écran sursaute, l'ombre de contact
	s'assombrit d'un coup ; puis la gravure se forme dans le métal. Après un
	temps d'arrêt, le tout s'efface en fondu sur l'écran de connexion.
	Animations du système coupées : logo formé, puis fondu court.
	inOnFaded : début du fondu (l'écran de connexion peut agir)
	inOnDone  : fin de l'animation (à retirer)
*/
@Composable
fun LaunchIntro(
	inOnFaded : () -> Unit,
	inOnDone  : () -> Unit
)
{
	val vAnimated = animationsEnabled()														// effets permis
	val vThud     = rememberImpactFeedback()													// vibration du choc
	val vFall     = remember { Animatable(if (vAnimated) 0f else 1f) }						// chute
	val vCarve    = remember { Animatable(if (vAnimated) 0f else 1f) }						// gravure
	val vBounce   = remember { Animatable(0f) }												// rebond du bouclier
	val vPress    = remember { Animatable(0f) }												// enfoncement de la plaque
	val vImpact   = remember { Animatable(0f) }												// intensité du choc
	val vShakeX   = remember { Animatable(0f) }												// sursaut horizontal
	val vShakeY   = remember { Animatable(0f) }												// sursaut vertical
	val vFade     = remember { Animatable(1f) }												// opacité
	LaunchedEffect(Unit) {
		if (vAnimated)
		{
			delay(kPlateMillis.toLong())
			vFall.animateTo(1f, tween(kFallMillis, easing = kGravity))
			vThud()
			vImpact.snapTo(1f)
			launch { vImpact.animateTo(0f, tween(kImpactMillis + kCarveMillis, easing = FastOutSlowInEasing)) }
			launch {
				vBounce.animateTo(0f, keyframes {
					durationMillis = kImpactMillis
					0.075f at 45; -0.03f at 140; 0.012f at 230; -0.004f at 310; 0f at kImpactMillis
				})
			}
			launch {
				vPress.animateTo(0f, keyframes {
					durationMillis = kImpactMillis
					1f at 50; -0.25f at 170; 0.08f at 270; 0f at kImpactMillis
				})
			}
			launch {
				vShakeY.animateTo(0f, keyframes {
					durationMillis = kImpactMillis
					12f at 35; -7f at 95; 4f at 160; -2f at 225; 0.8f at 290; 0f at kImpactMillis
				})
			}
			launch {
				vShakeX.animateTo(0f, keyframes {
					durationMillis = kImpactMillis
					-3.5f at 50; 2.5f at 120; -1.2f at 200; 0.4f at 280; 0f at kImpactMillis
				})
			}
			delay(90)
			launch { vCarve.animateTo(1f, tween(kCarveMillis, easing = FastOutSlowInEasing)) }
			delay((kImpactMillis - 90 + kHoldMillis).toLong())
		}
		else delay(500)
		inOnFaded()
		vFade.animateTo(0f, tween(if (vAnimated) kFadeMillis else 250, easing = LinearEasing))
		inOnDone()
	}
	IntroFrame(IntroState(vFall.value, vCarve.value, vBounce.value, vPress.value, vImpact.value, vShakeX.value, vShakeY.value, vFade.value))
}

/*
	Une image de l'animation d'ouverture.
	inState : avancée de chaque effet
*/
@Composable
fun IntroFrame(inState: IntroState)
{
	BoxWithConstraints(
		Modifier
			.fillMaxSize()
			.graphicsLayer { alpha = inState.fade }
			.background(KeeColors.void)
			.graphicsLayer { translationX = inState.shakeX * density; translationY = inState.shakeY * density }
	) {
		val vLogo  = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) * 0.80f).toPx() }	// côté du logo formé (pixels)
		val vLift  = 1f - inState.fall																// hauteur au-dessus du métal (1 : loin)
		val vScale = (kFallScale + (1f - kFallScale) * inState.fall) * (1f + inState.bounce)		// taille du bouclier
		val vPlate = 1f - 0.035f * inState.press													// taille de la plaque (enfoncée au choc)

		// Plaque : tout l'écran (un peu plus grande, pour que le sursaut n'en montre pas les bords) ; elle s'enfonce sous le choc.
		Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = vPlate * 1.08f; scaleY = vPlate * 1.08f }) { drawLogoMetal(vLogo / 100f) }

		// Ombre : naît pendant la chute, se resserre à l'approche ; au choc, ombre de contact dense.
		Canvas(
			Modifier
				.fillMaxSize()
				.graphicsLayer { alpha = (0.5f * inState.fall.pow(1.2f) * (1f - inState.carve * 0.7f) + 0.45f * inState.impact).coerceAtMost(0.9f) }
				.blur((2 + 22 * vLift + 6 * inState.impact).dp, BlurredEdgeTreatment.Unbounded)
		) {
			val vSide   = vLogo * 0.56f * (1f + 0.35f * vLift + 0.06f * inState.impact) * vPlate				// côté du repère du bouclier
			val vCenter = Offset(size.width / 2f + vLogo * 0.05f * vLift, size.height / 2f + vLogo * (0.015f + 0.10f * vLift))	// centre de l'ombre, décalée en bas à droite
			drawPath(shieldPath(vSide, Offset(vCenter.x - vSide / 2f, vCenter.y - vSide / 2f)), Color.Black)
		}

		// Bouclier : tombe en rapetissant vers sa place, rebondit au choc, puis se grave dans le métal.
		Canvas(
			Modifier
				.fillMaxSize()
				.graphicsLayer {
					scaleX = vScale * vPlate
					scaleY = vScale * vPlate
					alpha  = (inState.fall * 2.2f).coerceAtMost(1f)
				}
		) {
			drawLogoShield(vLogo, Offset((size.width - vLogo) / 2f, (size.height - vLogo) / 2f), inState.carve)
		}
	}
}

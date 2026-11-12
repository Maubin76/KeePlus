package fr.maubin.keeplus.desktop.quick

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import fr.maubin.keeplus.autofill.DesktopTarget
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import fr.maubin.keeplus.desktop.WindowFront
import fr.maubin.keeplus.ui.AppController
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.KeePlusTheme
import kotlinx.coroutines.launch
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

private val kBody     = 480.dp																// largeur du corps de la carte
private val kShoulder = 34.dp																// épaulement qui raccorde la carte au bord de l'écran
private const val kExStyle   = -20																	// GWL_EXSTYLE : style étendu d'une fenêtre
private const val kToolWindow = 0x00000080															// WS_EX_TOOLWINDOW : pas de bouton dans la barre des tâches
private const val kAppWindow  = 0x00040000															// WS_EX_APPWINDOW : bouton forcé dans la barre des tâches
private val kCorner   = 28.dp																// arrondi du bas de la carte

/*
	Contour de l'encoche : la carte est soudée au bord haut de l'écran ; de
	chaque côté, un épaulement part tangent au bord de l'écran et descend en
	courbe douce jusqu'au flanc de la carte (comme l'encoche d'un téléphone),
	puis le bas est arrondi.
	inSize    : taille
	inDensity : densité
	inClosed  : contour fermé par le bord du haut (forme pleine) ; sinon
	            seulement les côtés et le bas (liseré)
*/
private fun notchPath(
	inSize    : Size,
	inDensity : Density,
	inClosed  : Boolean
): Path
{
	val vS = with(inDensity) { kShoulder.toPx() }												// épaulement
	val vR = with(inDensity) { kCorner.toPx() }													// arrondi du bas
	val vW = inSize.width																		// largeur
	val vH = inSize.height																		// hauteur
	val vD = minOf(vS * 1.6f, vH - vR)															// hauteur de la descente de l'épaulement
	return Path().apply {
		moveTo(0f, 0f)
		cubicTo(vS * 0.62f, 0f, vS, vD * 0.38f, vS, vD)
		lineTo(vS, vH - vR)
		quadraticTo(vS, vH, vS + vR, vH)
		lineTo(vW - vS - vR, vH)
		quadraticTo(vW - vS, vH, vW - vS, vH - vR)
		lineTo(vW - vS, vD)
		cubicTo(vW - vS, vD * 0.38f, vW - vS * 0.62f, 0f, vW, 0f)
		if (inClosed) close()
	}
}

/*
	Forme pleine de l'encoche (fond, ombre, découpe).
*/
private object NotchShape : Shape
{
	/*
		Contour de l'encoche pour une taille donnée.
		size            : taille
		layoutDirection : sens de lecture
		density         : densité
	*/
	override fun createOutline(
		size            : Size,
		layoutDirection : LayoutDirection,
		density         : Density
	): Outline = Outline.Generic(notchPath(size, density, true))
}

/*
	Habillage de la carte de saisie : encoche sombre soudée au haut de
	l'écran (fond du plus sombre en haut au plus clair en bas, ombre, liseré
	fin sur les côtés et le bas seulement, pour que le haut se fonde dans le
	bord de l'écran). La largeur comprend les deux épaulements.
	inContent : contenu de la carte
*/
@Composable
fun NotchFrame(inContent: @Composable () -> Unit)
{
	val vDensity = LocalDensity.current															// conversion dp
	Box(
		Modifier
			.width(kBody + kShoulder * 2)
			.shadow(18.dp, NotchShape)
			.clip(NotchShape)
			.background(Brush.verticalGradient(listOf(Color.Black, KeeColors.void, KeeColors.surface)))
			.drawWithContent {
				drawContent()
				drawPath(notchPath(size, vDensity, false), KeeColors.rule, style = Stroke(1.dp.toPx()))
			}
			.padding(horizontal = kShoulder)
	) {
		inContent()
	}
}

/*
	Carte de saisie demandée par le raccourci : fenêtre active capturée et
	numéro de la demande (une nouvelle demande rouvre la carte).
	active : fenêtre active au moment du raccourci
	target : ce qu'elle affiche (domaine ajouté une fois lu)
	serial : numéro de la demande
*/
data class QuickRequest(
	val active : ActiveWindow,
	val target : DesktopTarget,
	val serial : Int
)

/*
	Fenêtre de la carte de saisie : sans bord, transparente, toujours
	au-dessus, posée en haut au milieu de l'écran de la fenêtre active. La
	carte descend du bord, et y remonte à la fermeture ; la fenêtre suit la
	hauteur de la carte (les clics à côté ne sont pas interceptés). Perdre
	le focus (clic ailleurs) ferme la carte.
	inRequest    : demande en cours ; null : carte fermée
	inController : état de l'appli
	inOnResult   : séquence prête (la carte se ferme, puis la main revient à la fenêtre active)
	inOnClosed   : carte fermée (vrai : rendre la main à la fenêtre active)
	inOnOpenApp  : ouvrir la fenêtre de KeePlus (pas encore de coffre)
	inIcon       : logo de KeePlus (barre des tâches)
	inDismiss    : demandes de fermeture venues d'ailleurs (Échap réservée) : chaque
	               nouvelle valeur fait remonter la carte
*/
@Composable
fun QuickWindow(
	inRequest    : QuickRequest?,
	inController : AppController,
	inOnResult   : (QuickResult) -> Unit,
	inOnClosed   : (Boolean) -> Unit,
	inOnOpenApp  : () -> Unit,
	inIcon       : Painter,
	inDismiss    : Int = 0
)
{
	val vRequest = inRequest ?: return															// demande affichée
	val vDensity = LocalDensity.current															// conversion dp
	val vFull    = kBody + kShoulder * 2															// largeur de la fenêtre
	val vPlace   = remember(vRequest.serial) { placement(vRequest.active.screen, vFull) }		// position (dp)
	val vState   = rememberWindowState(position = WindowPosition(vPlace.first, vPlace.second), size = DpSize(vFull, 320.dp))	// fenêtre
	val vSlide   = remember(vRequest.serial) { Animatable(-1f) }								// descente (−1 : cachée en haut, 0 : en place)
	val vScope   = rememberCoroutineScope()														// animations
	var vLeaving by remember(vRequest.serial) { mutableStateOf(false) }							// fermeture en cours
	var vFocused by remember(vRequest.serial) { mutableStateOf(false) }							// la carte a le clavier
	/*
		Remonte la carte, puis la ferme.
		inRestore : rendre la main à la fenêtre active
	*/
	fun leave(inRestore: Boolean)
	{
		if (vLeaving) return
		vLeaving = true
		vScope.launch {
			vSlide.animateTo(-1f, tween(200, easing = FastOutLinearInEasing))
			inOnClosed(inRestore)
		}
	}
	val vDismiss = remember(vRequest.serial) { inDismiss }											// demandes déjà là à l'ouverture
	LaunchedEffect(inDismiss) { if (inDismiss != vDismiss) leave(vFocused) }
	Window(
		onCloseRequest = { leave(false) },
		state          = vState,
		title          = "KeePlus",
		icon           = inIcon,
		undecorated    = true,
		transparent    = true,
		resizable      = false,
		alwaysOnTop    = true
	) {
		LaunchedEffect(vRequest.serial) {
			window.addWindowFocusListener(object : WindowAdapter()
			{
				/*
					Focus perdu (clic ailleurs) : la carte se ferme.
					inEvent : événement
				*/
				override fun windowLostFocus(inEvent: WindowEvent?)
				{
					vFocused = false
					leave(false)
				}

				/*
					Focus reçu.
					inEvent : événement
				*/
				override fun windowGainedFocus(inEvent: WindowEvent?)
				{
					vFocused = true
				}
			})
			hideFromTaskbar(window)
			WindowFront.bring(window)
			vSlide.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 380f))
		}
		KeePlusTheme {
			Box(
				Modifier
					.width(vFull)
					.onSizeChanged { vSize -> with(vDensity) { vState.size = DpSize(vFull, vSize.height.toDp() + 24.dp) } }
					.graphicsLayer { translationY = vSlide.value * size.height }
					.padding(bottom = 24.dp)
			) {
				NotchFrame {
					QuickCardContent(
						inController = inController,
						inTarget     = vRequest.target,
						inOnResult   = { vResult -> inOnResult(vResult); leave(true) },
						inOnClose    = { leave(true) },
						inOnOpenApp  = { leave(false); inOnOpenApp() }
					)
				}
			}
		}
	}
}

/*
	Retire la fenêtre de la carte de la barre des tâches (et d'Alt+Tab) :
	style « fenêtre d'outil » de Windows, appliqué fenêtre masquée un
	instant pour que la barre des tâches en tienne compte. La fenêtre
	principale de KeePlus garde son bouton. Hors Windows, rien ne change.
	inWindow : fenêtre de la carte
*/
private fun hideFromTaskbar(inWindow: java.awt.Window)
{
	if (!WinBridge.available) return
	runCatching {
		val vUser  = User32.INSTANCE																// fonctions des fenêtres
		val vHwnd  = WinDef.HWND(Native.getComponentPointer(inWindow))							// fenêtre native
		val vStyle = vUser.GetWindowLong(vHwnd, kExStyle)											// style étendu actuel
		val vTool  = (vStyle or kToolWindow) and kAppWindow.inv()									// style d'outil
		if (vTool == vStyle) return
		vUser.ShowWindow(vHwnd, WinUser.SW_HIDE)
		vUser.SetWindowLong(vHwnd, kExStyle, vTool)
		vUser.ShowWindow(vHwnd, WinUser.SW_SHOW)
	}
}

/*
	Position de la fenêtre : en haut au milieu de l'écran de la fenêtre
	active (zone utile, en pixels de Windows), convertie dans les unités de
	la fenêtre ; écran principal à défaut.
	inScreen : zone utile de l'écran ; null : écran principal
	inWidth  : largeur de la fenêtre
*/
private fun placement(
	inScreen : Rectangle?,
	inWidth  : androidx.compose.ui.unit.Dp
): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp>
{
	val vDevices = runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.toList() }.getOrDefault(emptyList())	// écrans
	val vScreen  = vDevices.map { vDevice ->
		val vConfig = vDevice.defaultConfiguration													// réglages de l'écran
		val vScale  = vConfig.defaultTransform.scaleX												// échelle (150 % : 1,5)
		val vBounds = vConfig.bounds																// zone en unités Java
		Triple(vBounds, vScale, Rectangle((vBounds.x * vScale).toInt(), (vBounds.y * vScale).toInt(), (vBounds.width * vScale).toInt(), (vBounds.height * vScale).toInt()))
	}.firstOrNull { inScreen != null && it.third.contains(inScreen.centerX.toInt(), inScreen.centerY.toInt()) } ?: vDevices.firstOrNull()?.defaultConfiguration?.let { Triple(it.bounds, it.defaultTransform.scaleX, it.bounds) }	// écran retenu
	val vBounds  = vScreen?.first ?: Rectangle(0, 0, 1280, 800)									// zone en unités Java
	val vScale   = vScreen?.second ?: 1.0															// échelle
	val vTop     = if (inScreen != null) (inScreen.y / vScale).toInt() else vBounds.y				// haut de la zone utile
	return (vBounds.x + (vBounds.width - inWidth.value.toInt()) / 2).dp to vTop.dp
}

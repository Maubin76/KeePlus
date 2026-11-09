package fr.maubin.keeplus.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
	Bouclier de KeePlus évidé d'un « + », dans un carré de côté inSize, en
	coordonnées relatives (0 à 1) : sert au logo et au grand décor flou.
	inSize   : côté du carré, en pixels
	inOrigin : coin haut gauche du carré
*/
fun shieldPath(
	inSize   : Float,
	inOrigin : Offset = Offset.Zero
): Path
{
	fun x(inValue: Float) = inOrigin.x + inValue * inSize
	fun y(inValue: Float) = inOrigin.y + inValue * inSize
	val vPath = Path().apply {
		fillType = PathFillType.EvenOdd
		// Bouclier : haut droit aux coins adoucis, flancs qui se rejoignent en pointe.
		moveTo(x(0.50f), y(0.04f))
		cubicTo(x(0.64f), y(0.11f), x(0.78f), y(0.14f), x(0.92f), y(0.15f))
		lineTo(x(0.92f), y(0.46f))
		cubicTo(x(0.92f), y(0.71f), x(0.74f), y(0.87f), x(0.50f), y(0.97f))
		cubicTo(x(0.26f), y(0.87f), x(0.08f), y(0.71f), x(0.08f), y(0.46f))
		lineTo(x(0.08f), y(0.15f))
		cubicTo(x(0.22f), y(0.14f), x(0.36f), y(0.11f), x(0.50f), y(0.04f))
		close()
		// « + » évidé, un peu au-dessus du centre.
		val vArm = 0.075f																		// demi-épaisseur des branches
		val vLen = 0.22f																		// demi-longueur des branches
		val vCx  = 0.50f																		// centre du « + »
		val vCy  = 0.47f
		moveTo(x(vCx - vArm), y(vCy - vLen))
		lineTo(x(vCx + vArm), y(vCy - vLen))
		lineTo(x(vCx + vArm), y(vCy - vArm))
		lineTo(x(vCx + vLen), y(vCy - vArm))
		lineTo(x(vCx + vLen), y(vCy + vArm))
		lineTo(x(vCx + vArm), y(vCy + vArm))
		lineTo(x(vCx + vArm), y(vCy + vLen))
		lineTo(x(vCx - vArm), y(vCy + vLen))
		lineTo(x(vCx - vArm), y(vCy + vArm))
		lineTo(x(vCx - vLen), y(vCy + vArm))
		lineTo(x(vCx - vLen), y(vCy - vArm))
		lineTo(x(vCx - vArm), y(vCy - vArm))
		close()
	}
	return vPath
}

/*
	Carré arrondi argenté portant le bouclier en noir.
*/
internal fun DrawScope.drawLogo()
{
	val vSide = size.minDimension															// côté du carré
	drawRoundRect(
		brush        = Brush.linearGradient(listOf(KeeColors.silverLight, KeeColors.silverDark), Offset.Zero, Offset(vSide, vSide)),
		size         = Size(vSide, vSide),
		cornerRadius = CornerRadius(vSide * 0.28f)
	)
	val vInset = vSide * 0.18f																// marge du bouclier
	drawPath(shieldPath(vSide - 2 * vInset, Offset(vInset, vInset)), Color(0xFF0B0B0C))
}

/*
	Logo de KeePlus en image (icône de fenêtre, zone de notification).
	inSize : côté de l'image, en pixels
*/
fun logoImage(inSize: Int): ImageBitmap
{
	val vImage = ImageBitmap(inSize, inSize)													// image
	CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(vImage), Size(inSize.toFloat(), inSize.toFloat())) { drawLogo() }
	return vImage
}

/*
	Logo de KeePlus.
	inModifier : modificateur
	inSize     : côté du logo
*/
@Composable
fun KeeLogo(
	inModifier : Modifier = Modifier,
	inSize     : Dp       = 28.dp
)
{
	Canvas(inModifier.size(inSize)) { drawLogo() }
}

/*
	Logo suivi du nom « KeePlus », en haut à gauche des écrans clés.
	inModifier : modificateur
*/
@Composable
fun KeeBrand(inModifier: Modifier = Modifier)
{
	Row(inModifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
		KeeLogo()
		BasicText("KeePlus", style = keeType().title)
	}
}

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong
import kotlin.random.Random

/*
	Logo de KeePlus (étude doc/logo) : bouclier noir à tête pointue, moitié
	droite en gris (facette), « + » découpé ; posé sur une plaque d'aluminium
	brossé au chanfrein usiné, bouclier gravé dans le métal, lumière unique en
	haut à gauche. Tout est décrit dans un carré de 100 unités.
*/

private const val kTileRadius = 26f														// arrondi de la plaque (unités)
private const val kBevel      = 2.72f													// largeur du chanfrein (unités)
private const val kShieldX    = 22.0														// repère du bouclier : coin et côté (unités)
private const val kShieldY    = 21.6
private const val kShieldS    = 56.0

private val kInk     = Color(0xFF0B0B0C)													// noir du bouclier
private val kFacet   = Color(0xFF2C2C2E)													// gris de la facette
private val kSilver  = listOf(Color(0xFFF4F4F5), Color(0xFFD9D9DB), Color(0xFFB3B3B6))	// argent de la plaque, du clair au sombre

/*
	Nombre au format des tracés SVG.
	inValue : nombre
*/
private fun n(inValue: Double): String = ((inValue * 100).roundToLong() / 100.0).toString()

/*
	Tracé SVG du bouclier à tête pointue, épaules adoucies, dans un repère.
	inX, inY : coin du repère
	inS      : côté du repère
*/
fun shieldData(
	inX : Double,
	inY : Double,
	inS : Double
): String
{
	fun p(inRx: Double, inRy: Double) = "${n(inX + inRx * inS)},${n(inY + inRy * inS)}"
	val vSoft = .025																			// arrondi des épaules
	return "M${p(.50, .05)} C${p(.62, .115)} ${p(.76, .145)} ${p(.92 - vSoft, .155)} " +
		"Q${p(.92, .157)} ${p(.92, .155 + vSoft)} L${p(.92, .47)} " +
		"C${p(.92, .72)} ${p(.74, .875)} ${p(.50, .965)} " +
		"C${p(.26, .875)} ${p(.08, .72)} ${p(.08, .47)} " +
		"L${p(.08, .155 + vSoft)} Q${p(.08, .157)} ${p(.08 + vSoft, .155)} " +
		"C${p(.24, .145)} ${p(.38, .115)} ${p(.50, .05)} Z"
}

/*
	Tracé SVG du « + », centré optiquement dans le bouclier.
	inX, inY : coin du repère du bouclier
	inS      : côté du repère
*/
fun plusData(
	inX : Double,
	inY : Double,
	inS : Double
): String
{
	val vA = .058																				// demi-épaisseur des branches
	val vL = .19																				// demi-longueur des branches
	fun q(inDx: Double, inDy: Double) = "${n(inX + (.50 + inDx) * inS)},${n(inY + (.47 + inDy) * inS)}"
	return "M${q(-vA, -vL)} L${q(vA, -vL)} L${q(vA, -vA)} L${q(vL, -vA)} L${q(vL, vA)} L${q(vA, vA)} " +
		"L${q(vA, vL)} L${q(-vA, vL)} L${q(-vA, vA)} L${q(-vL, vA)} L${q(-vL, -vA)} L${q(-vA, -vA)} Z"
}

/*
	Tracé SVG de la facette : moitié droite du repère du bouclier.
	inX, inY : coin du repère
	inS      : côté du repère
*/
fun facetData(
	inX : Double,
	inY : Double,
	inS : Double
): String = "M${n(inX + inS / 2)},${n(inY)} L${n(inX + inS)},${n(inY)} L${n(inX + inS)},${n(inY + inS)} L${n(inX + inS / 2)},${n(inY + inS)} Z"

/*
	Tracé Compose d'un tracé SVG.
	inData : tracé SVG
*/
private fun path(inData: String): Path = PathParser().parsePathString(inData).toPath()

/*
	Bouclier évidé du « + », dans un carré de côté inSize : sert au grand
	décor flou.
	inSize   : côté du carré, en pixels
	inOrigin : coin haut gauche du carré
*/
fun shieldPath(
	inSize   : Float,
	inOrigin : Offset = Offset.Zero
): Path =
	path(shieldData(inOrigin.x.toDouble(), inOrigin.y.toDouble(), inSize.toDouble()) + " " + plusData(inOrigin.x.toDouble(), inOrigin.y.toDouble(), inSize.toDouble()))
		.apply { fillType = PathFillType.EvenOdd }

/*
	Plaque d'aluminium brossé, lumière en haut à gauche, sur un contour
	donné : stries horizontales continues, lumière douce, ombre légère.
	inShape : contour de la plaque (pixels)
	inSide  : côté de référence du logo (pixels)
*/
private fun DrawScope.drawMetal(
	inShape : Path,
	inSide  : Float
)
{
	val vK      = inSide / 100f																// pixels par unité
	val vRandom = Random(5)																	// tirage stable des stries
	drawPath(inShape, Brush.linearGradient(kSilver, Offset(inSide * .15f, 0f), Offset(inSide * .85f, inSide)))
	clipPath(inShape) {
		var vY = 0f																			// hauteur de la strie
		while (vY < size.height)
		{
			val vLight = vRandom.nextBoolean()													// strie claire ou sombre
			drawLine((if (vLight) Color.White else Color.Black).copy(alpha = (.008f + vRandom.nextFloat() * .022f) * 2.4f), Offset(0f, vY), Offset(size.width, vY), (.06f + vRandom.nextFloat() * .12f) * vK)
			vY += (.08f + vRandom.nextFloat() * .16f) * vK
		}
		drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .44f), Color.Transparent), Offset(inSide * .2f, inSide * .1f), inSide * .75f))
		drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .20f)), Offset(inSide * .35f, inSide * .3f), inSide * .95f))
	}
}

/*
	Bouclier du logo, gravé ou à plat, dans le repère du logo (100 unités
	vers inSide pixels, décalé de inOffset).
	inSide   : côté du logo (pixels)
	inOffset : position du coin du logo
	inCarved : gravé dans le métal (lèvre claire, ombre, « + » creusé)
*/
fun DrawScope.drawLogoShield(
	inSide   : Float,
	inOffset : Offset  = Offset.Zero,
	inCarved : Boolean = true
)
{
	val vK      = inSide / 100f																// pixels par unité
	val vShield = path(shieldData(kShieldX, kShieldY, kShieldS))								// bouclier
	val vPlus   = path(plusData(kShieldX, kShieldY, kShieldS))									// « + »
	withTransform({ translate(inOffset.x, inOffset.y); scale(vK, vK, Offset.Zero) }) {
		if (inCarved)
		{
			translate(.6f, .9f) { drawPath(vShield, Color.White.copy(alpha = .85f)) }
			translate(-.5f, -.7f) { drawPath(vShield, Color.Black.copy(alpha = .32f)) }
		}
		drawPath(vShield, kInk)
		clipPath(vShield) { drawPath(path(facetData(kShieldX, kShieldY, kShieldS)), kFacet) }
		if (inCarved) translate(-.3f, -.4f) { drawPath(vPlus, Color.Black.copy(alpha = .35f)) }
		drawPath(vPlus, Brush.linearGradient(kSilver, Offset(15f, 0f), Offset(85f, 100f)))
	}
}

/*
	Plaque brossée seule, sans chanfrein, sur tout le carré (fond de l'icône
	adaptative Android, découpé par le lanceur).
	inSide : côté (pixels)
*/
fun DrawScope.drawLogoMetal(inSide: Float)
{
	drawMetal(Path().apply { addRect(Rect(0f, 0f, inSide, inSide)) }, inSide)
}

/*
	Logo complet dans un carré de côté inSide.
	inSide     : côté (pixels)
	inMaterial : matière complète (brossé, chanfrein, gravure) ; faux pour
	             une version à plat, plus nette en très petit
*/
fun DrawScope.drawLogo(
	inSide     : Float,
	inMaterial : Boolean = true
)
{
	val vK    = inSide / 100f																	// pixels par unité
	val vTile = Path().apply { addRoundRect(RoundRect(0f, 0f, inSide, inSide, CornerRadius(kTileRadius * vK))) }	// plaque
	if (!inMaterial)
	{
		drawPath(vTile, Brush.linearGradient(kSilver, Offset(inSide * .15f, 0f), Offset(inSide * .85f, inSide)))
		drawLogoShield(inSide, inCarved = false)
		return
	}
	val vInset = kBevel * vK																	// largeur du chanfrein
	val vFace  = Path().apply { addRoundRect(RoundRect(vInset, vInset, inSide - vInset, inSide - vInset, CornerRadius(kTileRadius * vK - vInset))) }	// face
	drawPath(vTile, Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFD2D2D4), Color(0xFF868689)), 0f, inSide))
	drawMetal(vFace, inSide)
	drawPath(vFace, Brush.verticalGradient(listOf(Color.White.copy(alpha = .9f), Color.Transparent), 0f, inSide * .06f), style = Stroke(.7f * vK))
	drawLogoShield(inSide, inCarved = true)
}

/*
	Logo en image (icônes de fenêtre et de la zone de notification, fichiers
	d'icônes).
	inSize     : côté (pixels)
	inMaterial : matière complète ; faux pour la version à plat
*/
fun logoImage(
	inSize     : Int,
	inMaterial : Boolean = true
): ImageBitmap
{
	val vImage = ImageBitmap(inSize, inSize)													// image
	CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(vImage), Size(inSize.toFloat(), inSize.toFloat())) { drawLogo(inSize.toFloat(), inMaterial) }
	return vImage
}

/*
	Logo de KeePlus : matière complète à partir de 40 dp, à plat en dessous
	(la texture y serait invisible et brouillerait le bouclier).
	inModifier : modificateur
	inSize     : côté du logo
*/
@Composable
fun KeeLogo(
	inModifier : Modifier = Modifier,
	inSize     : Dp       = 28.dp
)
{
	Canvas(inModifier.size(inSize)) { drawLogo(size.minDimension, inSize >= 40.dp) }
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

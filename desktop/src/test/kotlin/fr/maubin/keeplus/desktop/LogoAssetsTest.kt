package fr.maubin.keeplus.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import fr.maubin.keeplus.ui.theme.drawLogoMetal
import fr.maubin.keeplus.ui.theme.drawLogoShield
import fr.maubin.keeplus.ui.theme.logoImage
import fr.maubin.keeplus.ui.theme.plusData
import fr.maubin.keeplus.ui.theme.shieldData
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

private const val kAdaptive = 108.0														// côté de l'icône adaptative Android (dp)
private const val kVisible  = 72.0														// partie toujours visible après découpe par le lanceur (dp)
private val kDensities      = mapOf("mdpi" to 1f, "hdpi" to 1.5f, "xhdpi" to 2f, "xxhdpi" to 3f, "xxxhdpi" to 4f)	// densités Android
private val kIcoSizes       = listOf(16, 24, 32, 48, 64, 128, 256)						// tailles du fichier .ico

/*
	Image dessinée par un bloc.
	inSize  : côté (pixels)
	inBlock : dessin
*/
private fun image(
	inSize  : Int,
	inBlock : DrawScope.() -> Unit
): ImageBitmap
{
	val vImage = ImageBitmap(inSize, inSize)												// image
	CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(vImage), Size(inSize.toFloat(), inSize.toFloat()), inBlock)
	return vImage
}

/*
	Image encodée en PNG.
	inImage : image
*/
private fun png(inImage: ImageBitmap): ByteArray = Image.makeFromBitmap(inImage.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes

/*
	Fichier .ico (Windows) : une image PNG par taille, en petit-boutiste.
	inImages : images PNG par taille
*/
private fun ico(inImages: Map<Int, ByteArray>): ByteArray
{
	val vOut    = ByteArrayOutputStream()													// fichier
	fun u16(inValue: Int) { vOut.write(inValue and 0xFF); vOut.write(inValue shr 8 and 0xFF) }
	fun u32(inValue: Int) { u16(inValue and 0xFFFF); u16(inValue ushr 16) }
	u16(0); u16(1); u16(inImages.size)
	var vOffset = 6 + 16 * inImages.size													// début des images
	for ((vSize, vPng) in inImages)
	{
		vOut.write(if (vSize >= 256) 0 else vSize); vOut.write(if (vSize >= 256) 0 else vSize)
		vOut.write(0); vOut.write(0); u16(1); u16(32); u32(vPng.size); u32(vOffset)
		vOffset += vPng.size
	}
	inImages.values.forEach { vOut.write(it) }
	return vOut.toByteArray()
}

/*
	Génère les fichiers du logo à partir de son dessin (module :ui) : image
	de référence, icône Windows, icône adaptative Android (fond brossé et
	bouclier gravé par densité, version monochrome vectorielle). À relancer
	après toute retouche du logo ; le résultat est versionné.
*/
class LogoAssetsTest
{
	/*
		Écrit tous les fichiers du logo.
	*/
	@Test
	fun generateLogoAssets()
	{
		val vRoot = File("..")																	// racine du projet

		// Image de référence.
		File(vRoot, "doc/logo").mkdirs()
		File(vRoot, "doc/logo/keeplus-logo.png").writeBytes(png(logoImage(1024)))

		// Icône Windows : à plat en très petit, matière complète au-delà.
		File(vRoot, "desktop/icons/keeplus.ico").writeBytes(ico(kIcoSizes.associateWith { png(logoImage(it, it >= 48)) }))

		// Icône adaptative Android : la plaque de 100 unités occupe la partie visible (72 dp sur 108).
		for ((vName, vScale) in kDensities)
		{
			val vSide   = (kAdaptive * vScale).toInt()													// côté en pixels
			val vLogo   = (kVisible * vScale).toFloat()													// côté du logo en pixels
			val vOffset = ((kAdaptive - kVisible) / 2 * vScale).toFloat()								// marge
			val vDir    = File(vRoot, "app/src/main/res/mipmap-$vName").apply { mkdirs() }			// dossier de la densité
			File(vDir, "ic_launcher_background.png").writeBytes(png(image(vSide) { drawLogoMetal(vSide.toFloat()) }))
			File(vDir, "ic_launcher_foreground.png").writeBytes(png(image(vSide) { drawLogoShield(vLogo, Offset(vOffset, vOffset), inCarved = true) }))
		}

		// Version monochrome (icônes thématiques d'Android 13 et plus) : bouclier évidé, vectoriel.
		val vX = (kAdaptive - kVisible) / 2 + 22.0 * kVisible / 100									// repère du bouclier dans le carré de 108
		val vY = (kAdaptive - kVisible) / 2 + 21.6 * kVisible / 100
		val vS = 56.0 * kVisible / 100
		File(vRoot, "app/src/main/res/drawable/ic_launcher_monochrome.xml").writeText(
			"""
			|<?xml version="1.0" encoding="utf-8"?>
			|<!-- Généré par LogoAssetsTest : bouclier évidé du « + », pour les icônes thématiques. -->
			|<vector xmlns:android="http://schemas.android.com/apk/res/android"
			|	android:width="108dp"
			|	android:height="108dp"
			|	android:viewportWidth="108"
			|	android:viewportHeight="108">
			|	<path
			|		android:fillColor="#FF000000"
			|		android:fillType="evenOdd"
			|		android:pathData="${shieldData(vX, vY, vS)} ${plusData(vX, vY, vS)}" />
			|</vector>
			|""".trimMargin()
		)
	}
}

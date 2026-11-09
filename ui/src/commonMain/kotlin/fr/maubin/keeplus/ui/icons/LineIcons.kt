package fr.maubin.keeplus.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val kStroke = 1.6f														// épaisseur du trait, sur une grille de 24

/*
	Construit une icône au trait fin (grille 24 × 24, extrémités arrondies) à
	partir de tracés SVG. La couleur est donnée par la teinte de l'icône.
	inName  : nom de l'icône
	inPaths : tracés SVG, chacun dessiné en contour
*/
internal fun lineIcon(
	inName         : String,
	vararg inPaths : String
): ImageVector
{
	val vBuilder = ImageVector.Builder(
		name           = inName,
		defaultWidth   = 24.dp,
		defaultHeight  = 24.dp,
		viewportWidth  = 24f,
		viewportHeight = 24f
	)																					// icône en construction
	inPaths.forEach { vPath ->
		vBuilder.addPath(
			pathData        = addPathNodes(vPath),
			fill            = null,
			stroke          = SolidColor(Color.Black),
			strokeLineWidth = kStroke,
			strokeLineCap   = StrokeCap.Round,
			strokeLineJoin  = StrokeJoin.Round
		)
	}
	return vBuilder.build()
}

/*
	Jeu d'icônes au trait fin de KeePlus : géométrie simple, trait de 1,6,
	aucun aplat, pour s'accorder à Satoshi.
*/
object LineIcons
{
	// Afficher : œil ouvert.
	val Eye: ImageVector = lineIcon(
		"Afficher",
		"M2.5 12 C5 7.5 8.5 5.5 12 5.5 C15.5 5.5 19 7.5 21.5 12 C19 16.5 15.5 18.5 12 18.5 C8.5 18.5 5 16.5 2.5 12 Z",
		"M9 12 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0"
	)

	// Masquer : œil barré.
	val EyeOff: ImageVector = lineIcon(
		"Masquer",
		"M4 4 L20 20",
		"M9.9 5.8 C10.6 5.6 11.3 5.5 12 5.5 C15.5 5.5 19 7.5 21.5 12 C20.8 13.3 19.9 14.4 19 15.3",
		"M16.3 17.3 C15 18.1 13.5 18.5 12 18.5 C8.5 18.5 5 16.5 2.5 12 C3.4 10.3 4.6 8.9 5.9 7.9",
		"M10 10.2 A3 3 0 0 0 13.8 14"
	)

	// Verrou fermé.
	val Lock: ImageVector = lineIcon(
		"Verrouiller",
		"M6.5 10.5 H17.5 A1.5 1.5 0 0 1 19 12 V19 A1.5 1.5 0 0 1 17.5 20.5 H6.5 A1.5 1.5 0 0 1 5 19 V12 A1.5 1.5 0 0 1 6.5 10.5 Z",
		"M8 10.5 V7.5 A4 4 0 0 1 16 7.5 V10.5",
		"M12 14.5 V16.5"
	)

	// Fichier : feuille au coin plié.
	val File: ImageVector = lineIcon(
		"Fichier",
		"M13.5 3.5 H7 A1.5 1.5 0 0 0 5.5 5 V19 A1.5 1.5 0 0 0 7 20.5 H17 A1.5 1.5 0 0 0 18.5 19 V8.5 Z",
		"M13.5 3.5 V8.5 H18.5"
	)

	// Retour : flèche vers la gauche.
	val Back: ImageVector = lineIcon(
		"Retour",
		"M19 12 H5",
		"M11 6 L5 12 L11 18"
	)

	// Attention : cercle et point d'exclamation.
	val Alert: ImageVector = lineIcon(
		"Attention",
		"M3.5 12 a8.5 8.5 0 1 0 17 0 a8.5 8.5 0 1 0 -17 0",
		"M12 7.5 V12.5",
		"M12 16 V16.2"
	)
}

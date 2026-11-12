package fr.maubin.keeplus.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import fr.maubin.keeplus.desktop.quick.NotchFrame
import fr.maubin.keeplus.generator.PasswordOptions
import fr.maubin.keeplus.generator.generatePassword
import fr.maubin.keeplus.ui.components.GeneratedPassword
import fr.maubin.keeplus.ui.theme.KeePlusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	Le mot de passe généré des cartes de saisie ne change pas la hauteur de
	la carte quand sa longueur change (il rétrécit au lieu de passer à la
	ligne) : carte du PC, et largeurs de cartes de téléphone.
*/
class GeneratedPasswordTest
{
	/*
		Hauteur rendue d'un contenu, en pixels.
		inContent : contenu
	*/
	private fun heightOf(inContent: @androidx.compose.runtime.Composable (Modifier) -> Unit): Int
	{
		var vHeight = -1																		// hauteur mesurée
		ImageComposeScene(800, 600) { KeePlusTheme { inContent(Modifier.onSizeChanged { vHeight = it.height }) } }.use { it.render() }
		assertTrue("contenu mesuré", vHeight > 0)
		return vHeight
	}

	/*
		Carte du PC (encoche, marges de la carte) : même hauteur de 8 à 64
		caractères, sur plusieurs tirages.
	*/
	@Test
	fun pcCardKeepsItsHeight()
	{
		val vHeights = (8..64).flatMap { vLength ->
			List(3) {
				val vPassword = generatePassword(PasswordOptions(length = vLength))					// mot de passe tiré
				heightOf { vModifier ->
					NotchFrame {
						Column(vModifier.padding(horizontal = 26.dp, vertical = 18.dp)) {
							GeneratedPassword("Mot de passe", vPassword, vLength, {}, {})
						}
					}
				}
			}
		}																						// hauteurs mesurées
		assertEquals(listOf(vHeights.first()), vHeights.distinct())
	}

	/*
		Largeurs de cartes de téléphone (petit à grand écran) : même hauteur
		de 8 à 64 caractères.
	*/
	@Test
	fun phoneCardsKeepTheirHeight()
	{
		for (vWidth in listOf(280, 320, 360, 400))
		{
			val vHeights = (8..64).map { vLength ->
				val vPassword = generatePassword(PasswordOptions(length = vLength))						// mot de passe tiré
				heightOf { vModifier ->
					Column(vModifier.width(vWidth.dp)) { GeneratedPassword("Mot de passe", vPassword, vLength, {}, {}) }
				}
			}																					// hauteurs mesurées
			assertEquals("largeur $vWidth dp", listOf(vHeights.first()), vHeights.distinct())
		}
	}
}

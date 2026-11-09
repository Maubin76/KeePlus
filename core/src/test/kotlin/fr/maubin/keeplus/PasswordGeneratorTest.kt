package fr.maubin.keeplus

import fr.maubin.keeplus.generator.PassphraseOptions
import fr.maubin.keeplus.generator.PasswordOptions
import fr.maubin.keeplus.generator.Strength
import fr.maubin.keeplus.generator.estimateBits
import fr.maubin.keeplus.generator.generatePassphrase
import fr.maubin.keeplus.generator.generatePassword
import fr.maubin.keeplus.generator.passphraseBits
import fr.maubin.keeplus.generator.passwordBits
import fr.maubin.keeplus.generator.strengthOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	Tests du générateur de mots de passe et de phrases de passe, et de
	l'estimation de la force.
*/
class PasswordGeneratorTest
{
	/*
		Mot de passe : longueur demandée, une de chaque sorte, sans caractère
		ambigu.
	*/
	@Test
	fun passwordHasEveryKind()
	{
		repeat(200)
		{
			val vPassword = generatePassword(PasswordOptions(length = 12))				// mot de passe tiré
			assertEquals(12, vPassword.length)
			assertTrue(vPassword, vPassword.any { it.isLowerCase() })
			assertTrue(vPassword, vPassword.any { it.isUpperCase() })
			assertTrue(vPassword, vPassword.any { it.isDigit() })
			assertTrue(vPassword, vPassword.any { !it.isLetterOrDigit() })
			assertTrue(vPassword, vPassword.none { it in "Il1O0o" })
		}
	}

	/*
		Sortes limitées : chiffres seulement ; aucune sorte : minuscules.
	*/
	@Test
	fun passwordHonoursKinds()
	{
		val vDigits = generatePassword(PasswordOptions(length = 8, lower = false, upper = false, symbols = false, avoidAmbiguous = false))	// code
		assertTrue(vDigits, vDigits.all { it.isDigit() })
		val vNone   = generatePassword(PasswordOptions(lower = false, upper = false, digits = false, symbols = false))	// sans sorte
		assertTrue(vNone, vNone.all { it.isLowerCase() })
	}

	/*
		Phrase de passe : nombre de mots, séparateur, majuscules, un chiffre.
	*/
	@Test
	fun passphraseShape()
	{
		val vPhrase = generatePassphrase(PassphraseOptions(words = 6, separator = "."))		// phrase tirée
		val vWords  = vPhrase.split('.')															// ses mots
		assertEquals(6, vWords.size)
		assertTrue(vPhrase, vWords.all { it.first().isUpperCase() })
		assertEquals(vPhrase, 1, vPhrase.count { it.isDigit() })
	}

	/*
		Entropies : tirages de 20 caractères et de 5 mots solides ; mots de
		passe faibles repérés.
	*/
	@Test
	fun strengthLevels()
	{
		assertEquals(Strength.STRONG, strengthOf(passwordBits(PasswordOptions(length = 20))))
		assertEquals(Strength.GOOD, strengthOf(passphraseBits(PassphraseOptions(words = 5))))
		assertEquals(Strength.WEAK, strengthOf(estimateBits("azerty")))
		assertEquals(Strength.WEAK, strengthOf(estimateBits("123456789")))
		assertEquals(Strength.WEAK, strengthOf(estimateBits("aaaaaaaaaaaaaaaa")))
		assertTrue(estimateBits("k7#Vq0-Lm!x2Pz") > 80)
	}
}

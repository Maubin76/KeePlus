package fr.maubin.keeplus.generator

import java.security.SecureRandom
import kotlin.math.ln

private const val kLower     = "abcdefghijklmnopqrstuvwxyz"							// minuscules
private const val kUpper     = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"							// majuscules
private const val kDigits    = "0123456789"											// chiffres
private const val kSymbols   = "!#$%&*+-=?@^_~.,:;/()[]{}<>"							// symboles
private const val kAmbiguous = "Il1O0o|`'\""											// caractères faciles à confondre
private const val kWordList  = "eff_large_wordlist.txt"								// liste de mots (EFF, 7776 mots)

private val kRandom = SecureRandom()													// tirages

/*
	Réglages du générateur de mots de passe.
	length         : nombre de caractères
	lower          : minuscules
	upper          : majuscules
	digits         : chiffres
	symbols        : symboles
	avoidAmbiguous : sans caractères faciles à confondre (l, 1, O, 0…)
*/
data class PasswordOptions(
	val length         : Int     = 20,
	val lower          : Boolean = true,
	val upper          : Boolean = true,
	val digits         : Boolean = true,
	val symbols        : Boolean = true,
	val avoidAmbiguous : Boolean = true
)

/*
	Réglages du générateur de phrases de passe.
	words      : nombre de mots
	separator  : séparateur entre les mots
	capitalize : première lettre de chaque mot en majuscule
	withDigit  : un chiffre ajouté à la fin d'un mot tiré au hasard
*/
data class PassphraseOptions(
	val words      : Int     = 5,
	val separator  : String  = "-",
	val capitalize : Boolean = true,
	val withDigit  : Boolean = true
)

/*
	Force d'un mot de passe, selon son entropie estimée.
*/
enum class Strength
{
	WEAK,																				// moins de 50 bits
	FAIR,																				// de 50 à 70 bits
	GOOD,																				// de 70 à 100 bits
	STRONG																				// 100 bits et plus
}

/*
	Génère un mot de passe aléatoire, avec au moins un caractère de chaque
	sorte demandée. Sans sorte demandée : minuscules seulement.
	inOptions : réglages
	inRandom  : générateur aléatoire
*/
fun generatePassword(
	inOptions : PasswordOptions = PasswordOptions(),
	inRandom  : SecureRandom    = kRandom
): String
{
	val vSets   = passwordSets(inOptions)												// sortes de caractères
	val vAll    = vSets.joinToString("")												// tous les caractères permis
	val vLength = inOptions.length.coerceIn(vSets.size, 128)							// longueur effective
	val vChars  = CharArray(vLength)														// mot de passe
	for (vIndex in vChars.indices)
	{
		val vSet = if (vIndex < vSets.size) vSets[vIndex] else vAll						// d'abord un de chaque sorte
		vChars[vIndex] = vSet[inRandom.nextInt(vSet.length)]
	}
	for (vIndex in vChars.lastIndex downTo 1)
	{
		val vOther = inRandom.nextInt(vIndex + 1)											// mélange de Fisher-Yates
		vChars[vIndex] = vChars[vOther].also { vChars[vOther] = vChars[vIndex] }
	}
	return String(vChars).also { vChars.fill(' ') }
}

/*
	Entropie d'un mot de passe tiré par generatePassword, en bits.
	inOptions : réglages
*/
fun passwordBits(inOptions: PasswordOptions): Double
{
	val vSets = passwordSets(inOptions)													// sortes de caractères
	return inOptions.length.coerceIn(vSets.size, 128) * log2(vSets.sumOf { it.length }.toDouble())
}

/*
	Génère une phrase de passe à partir de la liste de mots de l'EFF.
	inOptions : réglages
	inRandom  : générateur aléatoire
*/
fun generatePassphrase(
	inOptions : PassphraseOptions = PassphraseOptions(),
	inRandom  : SecureRandom      = kRandom
): String
{
	val vCount = inOptions.words.coerceIn(3, 12)											// nombre de mots
	val vWords = MutableList(vCount) { kWords[inRandom.nextInt(kWords.size)] }			// mots tirés
	if (inOptions.capitalize) vWords.replaceAll { vWord -> vWord.replaceFirstChar { it.uppercaseChar() } }
	if (inOptions.withDigit)
	{
		val vIndex = inRandom.nextInt(vCount)												// mot qui reçoit le chiffre
		vWords[vIndex] = vWords[vIndex] + kDigits[inRandom.nextInt(kDigits.length)]
	}
	return vWords.joinToString(inOptions.separator)
}

/*
	Entropie d'une phrase tirée par generatePassphrase, en bits.
	inOptions : réglages
*/
fun passphraseBits(inOptions: PassphraseOptions): Double
{
	val vCount = inOptions.words.coerceIn(3, 12)											// nombre de mots
	val vDigit = if (inOptions.withDigit) log2(vCount * 10.0) else 0.0					// chiffre et sa place
	return vCount * log2(kWords.size.toDouble()) + vDigit
}

/*
	Entropie estimée d'un mot de passe quelconque, en bits : taille de
	l'alphabet utilisé, sur une longueur réduite pour les caractères répétés ou
	qui se suivent (aaaa, abcd, 1234). Estimation prudente pour les mots de
	passe saisis à la main ; un mot du dictionnaire est surestimé.
	inPassword : mot de passe
*/
fun estimateBits(inPassword: CharSequence): Double
{
	if (inPassword.isEmpty()) return 0.0
	var vPool = 0																		// taille de l'alphabet utilisé
	if (inPassword.any { it.isLowerCase() }) vPool += 26
	if (inPassword.any { it.isUpperCase() }) vPool += 26
	if (inPassword.any { it.isDigit() }) vPool += 10
	if (inPassword.any { !it.isLetterOrDigit() && it.code < 128 }) vPool += 33
	if (inPassword.any { it.code >= 128 }) vPool += 100
	var vLength = 1.0																	// longueur utile
	for (vIndex in 1 until inPassword.length)
	{
		val vStep = inPassword[vIndex].code - inPassword[vIndex - 1].code				// écart avec le précédent
		vLength += if (vStep in -1..1) 0.25 else 1.0
	}
	return vLength * log2(vPool.coerceAtLeast(2).toDouble())
}

/*
	Force correspondant à une entropie.
	inBits : entropie en bits
*/
fun strengthOf(inBits: Double): Strength =
	when
	{
		inBits < 50  -> Strength.WEAK
		inBits < 70  -> Strength.FAIR
		inBits < 100 -> Strength.GOOD
		else         -> Strength.STRONG
	}

/*
	Sortes de caractères permises par des réglages, sans les caractères
	ambigus si demandé ; minuscules si aucune sorte n'est demandée.
	inOptions : réglages
*/
private fun passwordSets(inOptions: PasswordOptions): List<String>
{
	val vSets = listOfNotNull(
		kLower.takeIf { inOptions.lower },
		kUpper.takeIf { inOptions.upper },
		kDigits.takeIf { inOptions.digits },
		kSymbols.takeIf { inOptions.symbols }
	).ifEmpty { listOf(kLower) }														// sortes demandées
	return if (!inOptions.avoidAmbiguous) vSets else vSets.map { vSet -> vSet.filter { it !in kAmbiguous } }
}

/*
	Logarithme en base 2.
	inValue : valeur strictement positive
*/
private fun log2(inValue: Double): Double = ln(inValue) / ln(2.0)

/*
	Liste de mots de l'EFF (une ligne : numéro de dés, tabulation, mot),
	chargée une fois depuis les ressources du module.
*/
private val kWords: List<String> by lazy {
	val vStream = PasswordOptions::class.java.getResourceAsStream(kWordList)
		?: error("Liste de mots introuvable : $kWordList")								// ressource du module
	vStream.bufferedReader().useLines { vLines -> vLines.mapNotNull { it.substringAfter('\t', "").trim().ifEmpty { null } }.toList() }
}

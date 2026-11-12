package fr.maubin.keeplus.autofill

/*
	Séquence de collage du presse-papiers enchaîné (PC) : chaque collage
	rend l'élément suivant ; le dernier reste en place (confirmation d'un
	mot de passe) jusqu'à l'effacement du presse-papiers.
	fItems : éléments à coller, dans l'ordre
	fNext  : rang du prochain élément
*/
class PasteSequence(private val fItems: List<String>)
{
	private var fNext = 0

	init
	{
		require(fItems.isNotEmpty()) { "séquence vide" }
	}

	/*
		Élément que le prochain collage rendra.
	*/
	val current: String get() = fItems[fNext]

	/*
		Rang du prochain élément (0 pour le premier).
	*/
	val position: Int get() = fNext

	/*
		Nombre d'éléments.
	*/
	val size: Int get() = fItems.size

	/*
		Un collage vient d'avoir lieu : passe à l'élément suivant ; le dernier
		reste. Rend vrai si l'élément a changé.
	*/
	fun advance(): Boolean
	{
		if (fNext >= fItems.lastIndex) return false
		fNext++
		return true
	}

	companion object
	{
		/*
			Connexion : identifiant, puis mot de passe (sans identifiant : le
			mot de passe seul).
			inUserName : identifiant
			inPassword : mot de passe
		*/
		fun login(
			inUserName : String,
			inPassword : String
		): PasteSequence = PasteSequence(listOfNotNull(inUserName.takeIf { it.isNotEmpty() }, inPassword))

		/*
			Changement de mot de passe : identifiant, mot de passe actuel, puis
			le nouveau (qui reste pour sa confirmation).
			inUserName : identifiant
			inCurrent  : mot de passe actuel
			inNew      : nouveau mot de passe
		*/
		fun change(
			inUserName : String,
			inCurrent  : String,
			inNew      : String
		): PasteSequence = PasteSequence(listOfNotNull(inUserName.takeIf { it.isNotEmpty() }, inCurrent, inNew))
	}
}

package fr.maubin.keeplus.autofill

import java.util.Locale

private const val kClassMask       = 0x0000000f										// classe d'un type de saisie Android
private const val kClassText       = 0x00000001										// classe texte
private const val kClassNumber     = 0x00000002										// classe nombre
private const val kVariationMask   = 0x00000ff0										// variante d'un type de saisie
private const val kTextEmail       = 0x00000020										// texte : adresse e-mail
private const val kTextPassword    = 0x00000080										// texte : mot de passe
private const val kTextVisiblePass = 0x00000090										// texte : mot de passe visible
private const val kTextWebEmail    = 0x000000d0										// texte : e-mail d'un formulaire web
private const val kTextWebPassword = 0x000000e0										// texte : mot de passe d'un formulaire web
private const val kNumberPassword  = 0x00000010										// nombre : code secret

private val kPasswordWords = listOf("password", "passwd", "pwd", "motdepasse", "mot de passe", "mot_de_passe", "mdp")	// mots d'un champ mot de passe
private val kUserWords     = listOf("user", "login", "mail", "identifiant", "pseudo", "account", "compte", "identifier")	// mots d'un champ identifiant
private val kCodeWords     = listOf("otp", "one-time", "onetime", "2fa", "totp", "sms", "verification", "captcha", "search", "recherche")	// champs à ne jamais remplir
/*
	Barres d'adresse des navigateurs en mode de compatibilité (Brave, Edge,
	Samsung Internet…) : Android y reconstruit la page depuis
	l'accessibilité, sans domaine ; il est lu dans la barre d'adresse.
	Jamais prise pour un champ à remplir.
*/
val kUrlBars = setOf("url_bar", "location_bar_edit_text", "url_field", "omnibarTextInput", "mozac_browser_toolbar_url_view", "url_edittext", "addressbarEdit")

private val kConfirmWords  = listOf("confirm", "repeat", "retype", "again", "verify", "second")	// mots d'une confirmation
private val kNewWords      = listOf("new", "nouveau", "nouvel") + kConfirmWords					// mots d'un nouveau mot de passe ou de sa confirmation

/*
	Champ d'un écran, tel que l'auto-remplissage le voit (traduit depuis la
	structure qu'Android transmet).
	id        : identifiant du champ pour Android ; null s'il ne se remplit pas
	text      : champ de saisie de texte
	visible   : champ affiché
	hints     : indications de remplissage déclarées par l'appli
	inputType : type de saisie Android
	html      : attributs HTML (type, autocomplete, name, id) d'un champ web
	idEntry   : nom de ressource du champ dans l'appli
	hint      : texte indicatif affiché dans le champ vide
	webDomain : domaine de la page web affichée, s'il y en a une
	value     : texte de la barre d'adresse (mode de compatibilité) ; null ailleurs
	children  : champs contenus
*/
data class FormNode<T>(
	val id        : T?                  = null,
	val text      : Boolean             = false,
	val visible   : Boolean             = true,
	val hints     : List<String>        = emptyList(),
	val inputType : Int                 = 0,
	val html      : Map<String, String> = emptyMap(),
	val idEntry   : String?             = null,
	val hint      : String?             = null,
	val webDomain : String?             = null,
	val value     : String?             = null,
	val children  : List<FormNode<T>>   = emptyList()
)

/*
	Champ d'identité d'un formulaire d'inscription.
	id    : identifiant du champ pour Android
	email : le champ attend une adresse e-mail
*/
data class IdentityField<T>(
	val id    : T,
	val email : Boolean
)

/*
	Champs à remplir sur un écran de connexion, d'inscription ou de
	changement de mot de passe.
	userName     : champ de l'identifiant ; null s'il n'y en a pas
	password     : champ du mot de passe actuel ; null s'il n'y en a pas
	webDomain    : domaine de la page web ; null hors page web
	newPasswords : champs d'un nouveau mot de passe et de sa confirmation
	identities   : inscription : champs d'identité (identifiant, e-mail), dans l'ordre
*/
data class FormFields<T>(
	val userName     : T?,
	val password     : T?,
	val webDomain    : String?,
	val newPasswords : List<T>                = emptyList(),
	val identities   : List<IdentityField<T>> = emptyList()
)
{
	/*
		Vrai pour une inscription : un nouveau mot de passe à choisir, sans
		mot de passe actuel.
	*/
	val signUp: Boolean get() = password == null && newPasswords.isNotEmpty()
}

/*
	Rôle d'un champ de saisie.
*/
private enum class FieldKind
{
	UserName, Email, Password, NewPassword, Other, Never;

	/*
		Vrai pour un identifiant ou une adresse e-mail.
	*/
	val identity: Boolean get() = this == UserName || this == Email

	/*
		Vrai pour un mot de passe, actuel ou nouveau.
	*/
	val secret: Boolean get() = this == Password || this == NewPassword
}

/*
	Trouve les champs d'un écran de connexion, d'inscription ou de
	changement de mot de passe.
	Un champ marqué comme mot de passe (indication, type HTML ou type de
	saisie) l'emporte sur les noms ; un mot de passe marqué « nouveau » ou
	nommé comme une confirmation est un nouveau mot de passe. Sans marque :
	deux mots de passe sont un nouveau et sa confirmation (inscription),
	trois sont l'actuel, le nouveau et sa confirmation ; un mot de passe
	suivi de sa seule confirmation est aussi une inscription.
	L'identifiant est le champ marqué comme tel avant le premier mot de
	passe, sinon le champ de texte qui le précède. Sans domaine déclaré
	(navigateur en mode de compatibilité), le domaine est celui de la barre
	d'adresse, qui n'est jamais remplie. Écran sans mot de passe
	(connexion en deux temps) : l'identifiant seul.
	Rend null s'il n'y a rien à remplir.
	inRoot : champ racine de l'écran
*/
fun <T> findFormFields(inRoot: FormNode<T>): FormFields<T>?
{
	val vInputs = mutableListOf<Pair<FormNode<T>, FieldKind>>()							// champs de saisie, dans l'ordre
	var vDomain : String? = null															// domaine de la page
	var vBar    : String? = null															// domaine lu dans la barre d'adresse
	fun walk(inNode: FormNode<T>)
	{
		if (vDomain == null) vDomain = inNode.webDomain?.takeIf { it.isNotBlank() }
		val vUrlBar = inNode.idEntry in kUrlBars											// barre d'adresse
		if (vUrlBar && vBar == null) vBar = inNode.value?.let(::hostOf)
		if (!vUrlBar && inNode.id != null && inNode.text && inNode.visible) vInputs += inNode to kindOf(inNode)
		inNode.children.forEach { walk(it) }
	}
	walk(inRoot)
	if (vDomain == null) vDomain = vBar
	var vCurrent = vInputs.indices.filter { vInputs[it].second == FieldKind.Password }		// mots de passe actuels
	var vNew     = vInputs.indices.filter { vInputs[it].second == FieldKind.NewPassword }	// nouveaux mots de passe
	if (vNew.isEmpty() && vCurrent.size == 2)
	{
		vNew     = vCurrent
		vCurrent = emptyList()
	}
	else if (vNew.isEmpty() && vCurrent.size >= 3)
	{
		vNew     = vCurrent.drop(1)
		vCurrent = vCurrent.take(1)
	}
	else if (vCurrent.size == 1 && vNew.size == 1 && vCurrent[0] < vNew[0] && kConfirmWords.any { it in wordsOf(vInputs[vNew[0]].first) })
	{
		vNew     = vCurrent + vNew
		vCurrent = emptyList()
	}
	val vFirst  = (vCurrent + vNew).minOrNull()											// premier mot de passe ; null sans
	val vBefore = vInputs.subList(0, vFirst ?: 0)										// champs avant le premier mot de passe
	val vUser   = if (vFirst != null) vBefore.lastOrNull { it.second.identity } ?: vBefore.lastOrNull { it.second == FieldKind.Other }
		else vInputs.firstOrNull { it.second.identity }										// champ de l'identifiant
	if (vFirst == null && vUser == null) return null
	val vIdentities = if (vCurrent.isEmpty() && vNew.isNotEmpty())
	{
		vBefore.filter { it.second.identity }.ifEmpty { listOfNotNull(vUser) }
			.map { (vNode, vKind) -> IdentityField<T>(vNode.id!!, vKind == FieldKind.Email) }
	}
	else emptyList()																		// champs d'identité d'une inscription
	return FormFields<T>(
		userName     = vUser?.first?.id,
		password     = vCurrent.firstOrNull()?.let { vInputs[it].first.id },
		webDomain    = vDomain,
		newPasswords = vNew.mapNotNull { vInputs[it].first.id },
		identities   = vIdentities
	)
}

/*
	Rôle d'un champ de saisie : d'abord ce que l'appli déclare, puis le type
	de saisie, enfin les mots de son nom et de son texte indicatif.
	inNode : champ
*/
private fun kindOf(inNode: FormNode<*>): FieldKind
{
	val vHints = inNode.hints.map { it.lowercase(Locale.ROOT) }							// indications
	val vAuto  = inNode.html["autocomplete"]?.lowercase(Locale.ROOT).orEmpty()			// autocomplete HTML
	val vType  = inNode.html["type"]?.lowercase(Locale.ROOT)								// type HTML
	val vWords = wordsOf(inNode)															// noms et texte indicatif
	val vFresh = kNewWords.any { it in vWords }											// nommé comme un nouveau mot de passe
	return when
	{
		vHints.any { "otp" in it || "one-time" in it } || "one-time-code" in vAuto  -> FieldKind.Never
		vHints.any { "newpassword" in it || "new-password" in it } || "new-password" in vAuto -> FieldKind.NewPassword
		vHints.any { "password" in it } || "password" in vAuto                    -> FieldKind.Password
		vHints.any { it == "emailaddress" || it == "email" } || vAuto == "email"   -> FieldKind.Email
		vHints.any { it == "username" } || vAuto == "username"                     -> FieldKind.UserName
		vType != null && vType !in listOf("text", "email", "password", "tel")      -> FieldKind.Never
		vType == "password" || isPasswordInput(inNode.inputType)                  -> if (vFresh) FieldKind.NewPassword else FieldKind.Password
		kCodeWords.any { it in vWords }                                           -> FieldKind.Never
		vType == "email" || isEmailInput(inNode.inputType)                        -> FieldKind.Email
		kPasswordWords.any { it in vWords }                                       -> if (vFresh) FieldKind.NewPassword else FieldKind.Password
		kUserWords.any { it in vWords }                                           -> if ("mail" in vWords) FieldKind.Email else FieldKind.UserName
		else                                                                      -> FieldKind.Other
	}
}

/*
	Noms et texte indicatif d'un champ, en minuscules.
	inNode : champ
*/
private fun wordsOf(inNode: FormNode<*>): String =
	listOfNotNull(inNode.idEntry, inNode.hint, inNode.html["name"], inNode.html["id"]).joinToString(" ").lowercase(Locale.ROOT)

/*
	Vrai si le type de saisie Android est un mot de passe ou un code secret.
	inType : type de saisie
*/
private fun isPasswordInput(inType: Int): Boolean
{
	val vVariation = inType and kVariationMask											// variante
	return when (inType and kClassMask)
	{
		kClassText   -> vVariation == kTextPassword || vVariation == kTextVisiblePass || vVariation == kTextWebPassword
		kClassNumber -> vVariation == kNumberPassword
		else         -> false
	}
}

/*
	Vrai si le type de saisie Android est une adresse e-mail.
	inType : type de saisie
*/
private fun isEmailInput(inType: Int): Boolean
{
	val vVariation = inType and kVariationMask											// variante
	return (inType and kClassMask) == kClassText && (vVariation == kTextEmail || vVariation == kTextWebEmail)
}

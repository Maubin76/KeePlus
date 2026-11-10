package fr.maubin.keeplus

import fr.maubin.keeplus.autofill.AutofillTarget
import fr.maubin.keeplus.autofill.FormFields
import fr.maubin.keeplus.autofill.FormNode
import fr.maubin.keeplus.autofill.IdentityField
import fr.maubin.keeplus.autofill.SavedLogin
import fr.maubin.keeplus.autofill.saveLogin
import fr.maubin.keeplus.autofill.savedLogin
import fr.maubin.keeplus.autofill.addSignUp
import fr.maubin.keeplus.autofill.suggestedUserName
import fr.maubin.keeplus.autofill.autofillMatches
import fr.maubin.keeplus.autofill.findFormFields
import fr.maubin.keeplus.autofill.rememberApp
import fr.maubin.keeplus.vault.CustomField
import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.createVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

private const val kTextInput     = 0x01												// saisie de texte
private const val kPasswordInput = 0x81												// saisie de mot de passe
private const val kEmailInput    = 0x21												// saisie d'e-mail

/*
	Tests de l'auto-remplissage : reconnaissance des champs de connexion,
	entrées proposées pour une appli ou un site, lien retenu avec une appli.
*/
class AutofillTest
{
	private val fClock = TestClock()														// horloge des tests
	private val fEmpty = Vault(createVault("Coffre", "maître"), fClock)					// coffre vide

	/*
		Champ de saisie de texte.
		inId      : identifiant
		inType    : type de saisie Android
		inIdEntry : nom de ressource
		inHints   : indications déclarées
	*/
	private fun input(
		inId      : String,
		inType    : Int          = kTextInput,
		inIdEntry : String?      = null,
		inHints   : List<String> = emptyList()
	) = FormNode(id = inId, text = true, inputType = inType, idEntry = inIdEntry, hints = inHints)

	/*
		Écran contenant des champs.
		inChildren : champs
		inDomain   : domaine de la page web
	*/
	private fun screen(
		vararg inChildren : FormNode<String>,
		inDomain          : String? = null
	) = FormNode(children = listOf(FormNode(webDomain = inDomain, children = inChildren.toList())))

	/*
		Formulaire classique : le champ de texte avant le mot de passe est
		l'identifiant, même sans nom parlant.
	*/
	@Test
	fun classicForm()
	{
		val vFields = findFormFields(screen(input("recherche", inIdEntry = "search_box"), input("a"), input("b", kPasswordInput)))	// champs trouvés
		assertEquals(FormFields("a", "b", null), vFields)
	}

	/*
		Indications déclarées et types e-mail l'emportent ; le domaine de la
		page est relevé.
	*/
	@Test
	fun hintsAndDomain()
	{
		val vFields = findFormFields(
			screen(
				input("mail", kEmailInput),
				input("divers"),
				input("secret", inHints = listOf("password")),
				inDomain = "accounts.google.com"
			)
		)																						// champs trouvés
		assertEquals(FormFields("mail", "secret", "accounts.google.com"), vFields)
	}

	/*
		Connexion en deux temps : identifiant seul ; un code reçu par SMS ou un
		champ masqué n'est jamais pris pour un mot de passe ; un écran sans
		champ de connexion ne donne rien.
	*/
	@Test
	fun twoStepsCodesAndNothing()
	{
		assertEquals(FormFields("id", null, null), findFormFields(screen(input("id", inIdEntry = "login_email"))))
		val vCode = findFormFields(screen(input("u", inIdEntry = "username"), input("c", kPasswordInput, inHints = listOf("smsOTPCode"))))	// code SMS
		assertEquals(FormFields("u", null, null), vCode)
		val vHidden = FormNode(id = "p", text = true, visible = false, inputType = kPasswordInput)	// mot de passe masqué
		assertNull(findFormFields(screen(input("note"), vHidden)))
		assertNull(findFormFields(screen(input("note"), FormNode(id = "x", text = false))))
	}

	/*
		Navigateur en mode de compatibilité (Brave) : pas de domaine déclaré,
		il est lu dans la barre d'adresse, qui n'est jamais prise pour
		l'identifiant ; un domaine déclaré l'emporte.
	*/
	@Test
	fun urlBarDomain()
	{
		val vBar    = FormNode(id = "barre", text = true, idEntry = "url_bar", value = "https://www.github.com/login")	// barre d'adresse
		val vFields = findFormFields(FormNode(children = listOf(vBar, input("mdp", kPasswordInput))))!!	// page sans identifiant
		assertEquals(FormFields(null, "mdp", "github.com"), vFields)
		val vDeclared = findFormFields(FormNode(children = listOf(vBar, FormNode(webDomain = "gitlab.com", children = listOf(input("mdp", kPasswordInput))))))!!	// domaine déclaré
		assertEquals("gitlab.com", vDeclared.webDomain)
	}

	/*
		Inscription : champs « nouveau mot de passe » (mot de passe et
		confirmation), champs d'identité avant eux (e-mail reconnu).
	*/
	@Test
	fun signUpForm()
	{
		val vFields = findFormFields(
			screen(
				input("pseudo", inHints = listOf("username")),
				input("mail", kEmailInput),
				input("mdp", kPasswordInput, inHints = listOf("newPassword")),
				input("encore", kPasswordInput, inHints = listOf("newPassword"))
			)
		)!!																						// champs trouvés
		assertTrue(vFields.signUp)
		assertNull(vFields.password)
		assertEquals(listOf("mdp", "encore"), vFields.newPasswords)
		assertEquals(listOf(IdentityField("pseudo", false), IdentityField("mail", true)), vFields.identities)
	}

	/*
		Sans indication : deux mots de passe sont une inscription (le second,
		nommé « confirm », aussi) ; trois sont un changement de mot de passe,
		où seul l'actuel se remplit ; un seul reste une connexion.
	*/
	@Test
	fun unmarkedPasswordForms()
	{
		val vTwo   = findFormFields(screen(input("u"), input("p", kPasswordInput), input("c", kPasswordInput)))!!	// deux mots de passe
		assertTrue(vTwo.signUp)
		assertEquals(listOf("p", "c"), vTwo.newPasswords)
		assertEquals(listOf(IdentityField("u", false)), vTwo.identities)
		val vNamed = findFormFields(screen(input("u"), input("p", kPasswordInput), input("c", kPasswordInput, inIdEntry = "confirm_password")))!!	// confirmation nommée
		assertEquals(listOf("p", "c"), vNamed.newPasswords)
		val vThree = findFormFields(screen(input("a", kPasswordInput), input("b", kPasswordInput), input("c", kPasswordInput)))!!	// trois mots de passe
		assertEquals("a", vThree.password)
		assertEquals(listOf("b", "c"), vThree.newPasswords)
		assertTrue(!vThree.signUp && vThree.identities.isEmpty())
		assertTrue(!findFormFields(screen(input("u"), input("p", kPasswordInput)))!!.signUp)
	}

	/*
		Identifiant proposé : le plus fréquent du coffre, e-mail ou non selon
		le champ ; un pseudo manquant est remplacé par l'e-mail.
	*/
	@Test
	fun suggestsUserNames()
	{
		assertEquals("", fEmpty.suggestedUserName(true))
		val vVault = fEmpty
			.save(EntryDraft(title = "A", userName = "jean@exemple.fr"))
			.save(EntryDraft(title = "B", userName = "jean@exemple.fr"))
			.save(EntryDraft(title = "C", userName = "autre@exemple.fr"))								// coffre
		assertEquals("jean@exemple.fr", vVault.suggestedUserName(true))
		assertEquals("jean@exemple.fr", vVault.suggestedUserName(false))
		assertEquals("jeannot", vVault.save(EntryDraft(title = "D", userName = "jeannot")).suggestedUserName(false))
	}

	/*
		Compte créé : l'entrée ajoutée est proposée d'emblée à la prochaine
		connexion, dans l'appli comme sur le site.
	*/
	@Test
	fun signUpEntryMatchesNextTime()
	{
		val vApp  = AutofillTarget.of("com.mabanque.app", null)										// appli
		val vSite = AutofillTarget.of("org.mozilla.firefox", "compte.mabanque.fr")					// site
		val vVault = fEmpty.addSignUp(vApp, "Ma Banque", "jean", "secret-1").addSignUp(vSite, "compte.mabanque.fr", "jean", "secret-2")	// coffre
		assertEquals(listOf("Ma Banque"), vVault.autofillMatches(vApp).map { it.title })
		val vWeb  = vVault.autofillMatches(vSite).single()												// entrée du site
		assertEquals("secret-2", vVault.draft(vWeb.id)!!.password)
		assertEquals("https://compte.mabanque.fr", vWeb.url)
	}

	/*
		Connexion tapée à la main : compte inconnu créé, mot de passe changé
		mis à jour (l'ancien dans l'historique), compte identique laissé tel
		quel ; l'identifiant est comparé sans les majuscules.
	*/
	@Test
	fun savesTypedLogins()
	{
		val vApp    = AutofillTarget.of("com.mabanque.app", null)										// appli
		val vFirst  = fEmpty.saveLogin(vApp, "Ma Banque", "Jean", "un")									// première connexion
		val vId     = vFirst.autofillMatches(vApp).single().id											// entrée créée
		assertEquals(SavedLogin.Same(vId), vFirst.savedLogin(vApp, "jean ", "un"))
		assertSame(vFirst, vFirst.saveLogin(vApp, "Ma Banque", "JEAN", "un"))
		assertEquals(SavedLogin.Update(vId, "Ma Banque"), vFirst.savedLogin(vApp, "jean", "deux"))
		fClock.tick()
		val vUpdated = vFirst.saveLogin(vApp, "Ma Banque", "jean", "deux")								// mot de passe changé
		assertEquals("deux", vUpdated.draft(vId)!!.password)
		assertEquals(listOf("un"), vUpdated.history(vId).map { it.password })
		assertEquals(SavedLogin.New, vUpdated.savedLogin(vApp, "marie", "trois"))
		assertEquals(SavedLogin.Same(vId), vUpdated.savedLogin(vApp, "", "deux"))
		assertEquals(SavedLogin.New, vUpdated.savedLogin(AutofillTarget.of("com.autre.app", null), "jean", "deux"))
	}

	/*
		Une page web propose les entrées du même domaine ou d'un domaine
		parent, quelle que soit la forme de l'adresse ; un domaine qui
		ressemble ne suffit pas.
	*/
	@Test
	fun matchesWebDomains()
	{
		val vVault = fEmpty
			.save(EntryDraft(title = "Google", url = "https://www.google.com/accounts"))
			.save(EntryDraft(title = "Banque", url = "mabanque.fr"))
			.save(EntryDraft(title = "Piège", url = "https://google.com.evil.io"))
			.save(EntryDraft(title = "Autre adresse", url = "https://exemple.org", fields = listOf(CustomField("KP2A_URL_1", "https://login.google.com"))))	// coffre
		val vTitles = { inDomain: String -> vVault.autofillMatches(AutofillTarget.of("com.android.chrome", inDomain)).map { it.title } }	// titres proposés
		assertEquals(listOf("Google"), vTitles("accounts.google.com"))
		assertEquals(listOf("Autre adresse", "Google"), vTitles("login.google.com"))
		assertEquals(listOf("Banque"), vTitles("www.mabanque.fr"))
		assertTrue(vTitles("mabanque-securite.fr").isEmpty())
	}

	/*
		Seul un navigateur connu peut annoncer un domaine : une appli qui
		prétend afficher un site n'obtient pas ses entrées.
	*/
	@Test
	fun onlyBrowsersAnnounceDomains()
	{
		val vVault  = fEmpty.save(EntryDraft(title = "Banque", url = "https://mabanque.fr"))	// coffre
		val vTarget = AutofillTarget.of("com.piege.app", "mabanque.fr")						// appli piégée
		assertNull(vTarget.domain)
		assertTrue(vVault.autofillMatches(vTarget).isEmpty())
	}

	/*
		Lien avec une appli : l'adresse vide devient androidapp://…, sinon un
		champ AndroidApp est ajouté ; l'entrée est alors proposée dans cette
		appli, et un second lien identique ne change rien.
	*/
	@Test
	fun remembersApps()
	{
		val vBare  = UUID.randomUUID()															// entrée sans adresse
		val vSite  = UUID.randomUUID()															// entrée avec adresse
		val vVault = fEmpty
			.save(EntryDraft(id = vBare, title = "Banque"))
			.save(EntryDraft(id = vSite, title = "Banque web", url = "https://mabanque.fr"))	// coffre
		val vApp   = AutofillTarget.of("com.mabanque.app", null)								// appli de la banque
		assertTrue(vVault.autofillMatches(vApp).isEmpty())
		val vLinked = vVault.rememberApp(vBare, "com.mabanque.app").rememberApp(vSite, "com.mabanque.app")	// liens retenus
		assertEquals("androidapp://com.mabanque.app", vLinked.draft(vBare)!!.url)
		assertEquals(listOf(CustomField("AndroidApp", "com.mabanque.app")), vLinked.draft(vSite)!!.fields)
		assertEquals(listOf("Banque", "Banque web"), vLinked.autofillMatches(vApp).map { it.title })
		assertSame(vLinked, vLinked.rememberApp(vSite, "com.mabanque.app"))
		val vTwice = vLinked.rememberApp(vSite, "com.mabanque.pro")								// seconde appli
		assertEquals("AndroidApp_2", vTwice.draft(vSite)!!.fields.last().name)
	}
}

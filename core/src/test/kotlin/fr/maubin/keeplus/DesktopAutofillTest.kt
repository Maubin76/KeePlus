package fr.maubin.keeplus

import fr.maubin.keeplus.autofill.DesktopTarget
import fr.maubin.keeplus.autofill.PasteSequence
import fr.maubin.keeplus.autofill.addDesktopSignUp
import fr.maubin.keeplus.autofill.desktopMatches
import fr.maubin.keeplus.autofill.rememberWindowsApp
import fr.maubin.keeplus.vault.EntryDraft
import fr.maubin.keeplus.vault.Vault
import fr.maubin.keeplus.vault.createVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/*
	Tests de la carte de saisie du PC : comptes proposés pour la fenêtre
	active (site, appli Windows, titre), lien retenu, compte créé, séquence
	du presse-papiers enchaîné.
*/
class DesktopAutofillTest
{
	private val fClock = TestClock()														// horloge des tests
	private val fEmpty = Vault(createVault("Coffre", "maître"), fClock)					// coffre vide

	/*
		Navigateur : le domaine lu dans la barre d'adresse choisit les comptes
		(domaine parent compris) ; un domaine qui ressemble ne suffit pas.
	*/
	@Test
	fun browserUsesDomain()
	{
		val vVault  = fEmpty
			.save(EntryDraft(title = "GitHub", url = "https://github.com"))
			.save(EntryDraft(title = "Piège", url = "https://github.com.evil.io"))			// coffre
		val vTarget = DesktopTarget("brave.exe", "Sign in to GitHub - Brave").withUrl("https://www.github.com/login")	// fenêtre de Brave
		assertEquals("github.com", vTarget.domain)
		assertEquals(listOf("GitHub"), vVault.desktopMatches(vTarget).map { it.title })
		assertTrue(vVault.desktopMatches(DesktopTarget("brave.exe", "x").withUrl("github.co")).isEmpty())
	}

	/*
		Appli Windows : les comptes liés au programme d'abord, sinon ceux dont
		le titre apparaît (mot entier) dans le titre de la fenêtre.
	*/
	@Test
	fun windowsAppsAndTitles()
	{
		val vSteam  = UUID.randomUUID()															// compte Steam
		val vVault  = fEmpty
			.save(EntryDraft(id = vSteam, title = "Steam", userName = "joueur"))
			.save(EntryDraft(title = "Ma Banque", userName = "jean"))
			.save(EntryDraft(title = "Te", userName = "trop court"))							// coffre
		val vTarget = DesktopTarget("steamwebhelper.exe", "Connexion à Steam")					// fenêtre de Steam
		assertEquals(listOf("Steam"), vVault.desktopMatches(vTarget).map { it.title })
		assertEquals(listOf("Ma Banque"), vVault.desktopMatches(DesktopTarget("banque.exe", "Ma Banque — Espace client")).map { it.title })
		assertTrue(vVault.desktopMatches(DesktopTarget("x.exe", "Steamy")).isEmpty())
		val vLinked = vVault.rememberWindowsApp(vSteam, DesktopTarget("steam.exe", "Steam"))	// lien retenu
		assertEquals(listOf("Steam"), vLinked.desktopMatches(DesktopTarget("steam.exe", "Fenêtre sans nom")).map { it.title })
		assertSame(vLinked, vLinked.rememberWindowsApp(vSteam, DesktopTarget("steam.exe", "Steam")))
		assertSame(vVault, vVault.rememberWindowsApp(vSteam, DesktopTarget("brave.exe", "Steam")))
	}

	/*
		Compte créé depuis la carte : adresse du site, ou lien avec l'appli ;
		il est proposé d'emblée la fois suivante.
	*/
	@Test
	fun signUpIsFoundNextTime()
	{
		val vSite   = DesktopTarget("firefox.exe", "Créer un compte").withUrl("https://compte.exemple.fr/inscription")	// site
		val vApp    = DesktopTarget("jeu.exe", "Le Jeu")										// appli Windows
		val vVault  = fEmpty.addDesktopSignUp(vSite, "marie", "un").addDesktopSignUp(vApp, "marie", "deux")	// coffre
		assertEquals("https://compte.exemple.fr", vVault.desktopMatches(vSite).single().url)
		val vFound  = vVault.desktopMatches(DesktopTarget("jeu.exe", "Autre titre")).single()	// compte de l'appli
		assertEquals("deux", vVault.draft(vFound.id)!!.password)
		assertEquals("Le Jeu", vFound.title)
	}

	/*
		Séquence : identifiant, puis mot de passe, le dernier élément restant ;
		sans identifiant, le mot de passe seul ; changement en trois temps.
	*/
	@Test
	fun pasteSequences()
	{
		val vLogin = PasteSequence.login("jean", "secret")										// connexion
		assertEquals("jean", vLogin.current)
		assertTrue(vLogin.advance())
		assertEquals("secret", vLogin.current)
		assertFalse(vLogin.advance())
		assertEquals("secret", vLogin.current)
		assertEquals(1, PasteSequence.login("", "secret").size)
		val vChange = PasteSequence.change("jean", "ancien", "nouveau")							// changement
		assertEquals(listOf("jean", "ancien", "nouveau", "nouveau"), List(4) { vChange.current.also { vChange.advance() } })
	}
}

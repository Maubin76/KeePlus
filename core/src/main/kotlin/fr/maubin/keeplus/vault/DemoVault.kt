package fr.maubin.keeplus.vault

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

const val kDemoPassword = "demo"														// mot de passe du coffre de démonstration

/*
	Horloge réglable, pour dater les entrées de démonstration dans le passé.
	fNow : date actuelle de l'horloge
*/
private class DemoClock(var fNow: Instant) : Clock()
{
	/*
		Fuseau : UTC.
	*/
	override fun getZone() = ZoneOffset.UTC

	/*
		Même horloge.
		inZone : fuseau demandé
	*/
	override fun withZone(inZone: java.time.ZoneId) = this

	/*
		Date actuelle de l'horloge.
	*/
	override fun instant(): Instant = fNow
}

/*
	Coffre de démonstration : groupes, entrées variées (champs libres,
	historique, mot de passe faible, entrée à la corbeille), datées sur les
	derniers mois. Identifiants et mots de passe inventés (adresses en
	example.fr) ; mot de passe maître : kDemoPassword.
	inNow : date de référence (les entrées sont datées avant)
*/
fun demoVault(inNow: Instant = Instant.now()): Vault
{
	val vClock = DemoClock(inNow.minus(Duration.ofDays(200)))								// date des premières entrées
	fun later(inDays: Long) { vClock.fNow = vClock.fNow.plus(Duration.ofDays(inDays)) }
	val vPerso  = UUID.randomUUID()															// groupe Personnel
	val vBank   = UUID.randomUUID()															// groupe Banques
	val vWork   = UUID.randomUUID()															// groupe Travail
	val vShop   = UUID.randomUUID()															// groupe Achats
	val vFun    = UUID.randomUUID()															// groupe Loisirs
	val vMail   = UUID.randomUUID()															// entrée avec historique
	val vOld    = UUID.randomUUID()															// entrée mise à la corbeille
	var vVault  = Vault(createVault("Coffre de démonstration", kDemoPassword), vClock)		// coffre
		.addGroup("Personnel", inId = vPerso)
		.addGroup("Banques", inId = vBank)
		.addGroup("Travail", inId = vWork)
		.addGroup("Achats", inId = vShop)
		.addGroup("Loisirs", inId = vFun)
	val vEntries = listOf(
		EntryDraft(id = vMail, groupId = vPerso, title = "Gmail", userName = "camille.martin@example.fr", password = "Tq4#vLm8!pZ2xR", url = "https://mail.google.com"),
		EntryDraft(groupId = vPerso, title = "Free – Espace abonné", userName = "camille.martin", password = "k7#Vq0-Lm!x2Pz", url = "https://subscribe.free.fr/login"),
		EntryDraft(groupId = vPerso, title = "Impôts", userName = "1234567890123", password = "Orange-Velours-Piano7-Lagune", url = "https://www.impots.gouv.fr", notes = "Numéro fiscal sur l'avis d'imposition."),
		EntryDraft(groupId = vPerso, title = "Ameli", userName = "2850175123456", password = "aB3\$kL9@wQ", url = "https://assure.ameli.fr"),
		EntryDraft(groupId = vPerso, title = "Wi-Fi maison", userName = "", password = "Maison-Bleue-2024", notes = "Réseau : Livebox-CM42",
			fields = listOf(CustomField("SSID", "Livebox-CM42"))),
		EntryDraft(groupId = vBank, title = "Société Générale", userName = "08451234", password = "582947", url = "https://particuliers.sg.fr",
			fields = listOf(CustomField("Code PIN carte", "4821", secret = true), CustomField("Conseiller", "M. Durand, agence Rouen centre"))),
		EntryDraft(groupId = vBank, title = "Boursorama", userName = "camille.martin@example.fr", password = "xH9!rT2#mQ7&vN", url = "https://clients.boursorama.com"),
		EntryDraft(groupId = vBank, title = "PayPal", userName = "camille.martin@example.fr", password = "Vf8%jW3^sD6@", url = "https://www.paypal.com"),
		EntryDraft(groupId = vWork, title = "Slack", userName = "c.martin@example.fr", password = "Brume-Granit-Falaise-42", url = "https://example.slack.com"),
		EntryDraft(groupId = vWork, title = "GitHub", userName = "cmartin-dev", password = "Gh!7pQz#4Lw9@Xr2", url = "https://github.com",
			fields = listOf(CustomField("Codes de secours", "a1b2-c3d4\ne5f6-g7h8\ni9j0-k1l2", secret = true))),
		EntryDraft(groupId = vWork, title = "VPN entreprise", userName = "cmartin", password = "Wn3&yB8!eK5#", url = "vpn.example.fr"),
		EntryDraft(groupId = vShop, title = "Amazon", userName = "camille.martin@example.fr", password = "Am4#zQ9!rL2&", url = "https://www.amazon.fr"),
		EntryDraft(groupId = vShop, title = "Leboncoin", userName = "camille.martin@example.fr", password = "azerty123", url = "https://www.leboncoin.fr", notes = "Mot de passe à changer."),
		EntryDraft(groupId = vShop, title = "SNCF Connect", userName = "camille.martin@example.fr", password = "Tr@in-Lent-Quai9", url = "https://www.sncf-connect.com"),
		EntryDraft(groupId = vFun, title = "Netflix", userName = "camille.martin@example.fr", password = "Nf6!bV2#kP8@", url = "https://www.netflix.com"),
		EntryDraft(groupId = vFun, title = "Spotify", userName = "camillem", password = "Sp0t!fy-Hiver-Neige", url = "https://open.spotify.com"),
		EntryDraft(groupId = vFun, title = "Steam", userName = "camille_m42", password = "St3am#Qz8!Lp4", url = "https://store.steampowered.com",
			fields = listOf(CustomField("Steam Guard", "Application mobile"))),
		EntryDraft(id = vOld, groupId = vFun, title = "Ancien forum", userName = "camille", password = "forum2012", url = "https://forum.example.fr")
	)																						// entrées
	for (vEntry in vEntries)
	{
		later(9)
		vVault = vVault.save(vEntry)
	}
	later(12)
	vVault = vVault.save(vVault.draft(vMail)!!.copy(password = "Gm#8wE2!rT5qZ1"))			// mot de passe changé : une version dans l'historique
	later(5)
	vVault = vVault.trash(vOld)
	return Vault(vVault.database)
}

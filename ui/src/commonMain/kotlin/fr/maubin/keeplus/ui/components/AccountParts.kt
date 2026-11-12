package fr.maubin.keeplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType
import fr.maubin.keeplus.vault.VaultItem

private val kLengths = 8..64																	// longueurs proposées (comme le générateur de l'appli)

/*
	Ligne d'un compte (cartes de saisie, téléphone et PC) : monogramme, titre, identifiant. Quand plusieurs
	comptes de la liste portent le même titre (plusieurs comptes pour une
	même appli), l'identifiant passe en avant (ligne principale et
	monogramme) : c'est lui qui les distingue.
	inItem      : entrée
	inUserFirst : identifiant en avant
	inSelected  : ligne mise en évidence (choix au clavier)
	inOnClick   : choix du compte
*/
@Composable
fun AccountRow(
	inItem      : VaultItem,
	inUserFirst : Boolean = false,
	inSelected  : Boolean = false,
	inOnClick   : () -> Unit
)
{
	val vType = keeType()																		// styles de texte
	Row(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(16.dp))
			.background(if (inSelected) KeeColors.surfaceHighest else Color.Transparent)
			.clickable(onClick = inOnClick)
			.padding(horizontal = 4.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		val vTitle = inItem.title.ifBlank { "Sans titre" }											// titre
		val vUser  = inUserFirst && inItem.userName.isNotBlank()									// identifiant en avant
		Monogram(if (vUser) inItem.userName else inItem.title, inSize = 40.dp)
		Spacer(Modifier.width(14.dp))
		Column(Modifier.weight(1f)) {
			OneLine(if (vUser) inItem.userName else vTitle, vType.title)
			val vDetail = if (vUser) vTitle else inItem.userName									// seconde ligne
			if (vDetail.isNotBlank()) OneLine(vDetail, vType.bodySmall)
		}
	}
}

/*
	Mot de passe généré (cartes de saisie, téléphone et PC) : libellé, mot de passe coloré (lettres, chiffres,
	symboles, sur plusieurs lignes s'il est long), bouton de nouveau tirage,
	curseur de longueur (un nouveau mot de passe est tiré à chaque cran).
	inLabel    : libellé
	inValue    : mot de passe
	inLength   : longueur demandée
	inOnLength : nouvelle longueur
	inOnDraw   : nouveau tirage
*/
@Composable
fun GeneratedPassword(
	inLabel    : String,
	inValue    : String,
	inLength   : Int,
	inOnLength : (Int) -> Unit,
	inOnDraw   : () -> Unit
)
{
	val vType = keeType()																		// styles de texte
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		BasicText(inLabel, style = vType.label)
		Row(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KeeColors.void.copy(alpha = 0.6f)).padding(start = 14.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			BasicText(secretAnnotated(inValue), Modifier.weight(1f).padding(vertical = 8.dp), style = vType.secret)
			IconAction(LineIcons.Refresh, "Nouveau tirage", inOnDraw)
		}
		Stepper("Longueur", inLength, kLengths) { if (it != inLength) inOnLength(it) }
	}
}

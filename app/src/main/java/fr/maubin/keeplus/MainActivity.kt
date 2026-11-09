package fr.maubin.keeplus

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.maubin.keeplus.ui.WelcomePreview
import fr.maubin.keeplus.ui.theme.KeePlusTheme

/*
	Activité unique de KeePlus. Écran protégé (FLAG_SECURE) : ni capture, ni
	aperçu dans les applications récentes.
*/
class MainActivity : ComponentActivity()
{
	/*
		Création : écran protégé, barres système claires sur fond sombre, contenu Compose.
		inState : état sauvegardé
	*/
	override fun onCreate(inState: Bundle?)
	{
		super.onCreate(inState)
		window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
		enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
		setContent {
			KeePlusTheme {
				WelcomePreview()
			}
		}
	}
}

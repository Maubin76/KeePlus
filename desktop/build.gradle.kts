// Appli PC (Windows) : Compose Multiplatform pour la JVM. Ouvre le même coffre
// KDBX que l'appli Android ; la synchronisation Wi-Fi viendra à l'étape 5.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.compose.multiplatform)
}

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_17)
	}
}

dependencies {
	implementation(project(":core"))
	implementation(project(":ui"))
	implementation(compose.desktop.currentOs)
	implementation(libs.compose.desktop.material3)
	implementation(libs.kotlinx.coroutines.swing)

	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.core)
}

// Version commune aux deux applis ; le paquet Windows veut trois nombres (0.1 → 0.1.0).
val kVersion = property("keeplus.version").toString()								// version affichée
val kPackage = (kVersion.split('.') + listOf("0", "0", "0")).take(3).joinToString(".")	// version du paquet

compose.desktop {
	application {
		mainClass = "fr.maubin.keeplus.desktop.MainKt"

		nativeDistributions {
			targetFormats(TargetFormat.Msi)
			packageName    = "KeePlus"
			packageVersion = kPackage
			description    = "Gestionnaire de mots de passe"
			vendor         = "Mathieu Aubin"
			modules("java.instrument", "jdk.unsupported", "java.xml", "jdk.localedata")

			windows {
				menuGroup = "KeePlus"
			}
		}
	}
}

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
	implementation(libs.jna.platform)													// barre de titre sombre, premier plan, verrouillage de session (Windows)
	implementation(libs.zxing.core)														// QR code de l'appairage du téléphone

	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.core)
}

// Version commune aux deux applis ; le paquet Windows veut trois nombres (0.1 → 0.1.0).
val kVersion = property("keeplus.version").toString()								// version affichée
val kPackage = (kVersion.split('.') + listOf("0", "0", "0")).take(3).joinToString(".")	// version du paquet

// Version lue par l'appli au lancement (mises à jour, présentation aux téléphones) :
// ressource version.properties générée à la construction.
val generateVersion = tasks.register("generateVersion") {
	val vOut = layout.buildDirectory.dir("generated/version")						// dossier généré
	inputs.property("version", kVersion)
	outputs.dir(vOut)
	doLast {
		vOut.get().file("version.properties").asFile.apply { parentFile.mkdirs(); writeText("version=$kVersion\n") }
	}
}
sourceSets.main { resources.srcDir(generateVersion) }

// Tests sans écran, comme sur la CI (Linux) : une fenêtre AWT y serait refusée.
tasks.withType<Test>().configureEach {
	systemProperty("java.awt.headless", "true")
}

compose.desktop {
	application {
		mainClass = "fr.maubin.keeplus.desktop.MainKt"
		// JDK de l'empaquetage (jpackage) : celui de Gradle par défaut (CI) ; en local, si
		// Gradle tourne sur le JDK d'Android Studio (sans jpackage) :
		// ./gradlew :desktop:packageZip -Pkeeplus.packagingJdk=C:/Java/current
		findProperty("keeplus.packagingJdk")?.let { javaHome = it.toString() }

		nativeDistributions {
			targetFormats(TargetFormat.Msi)
			packageName    = "KeePlus"
			packageVersion = kPackage
			description    = "Gestionnaire de mots de passe"
			vendor         = "Mathieu Aubin"
			modules("java.instrument", "jdk.unsupported", "java.xml", "jdk.localedata")

			windows {
				iconFile.set(project.file("icons/keeplus.ico"))
				menuGroup = "KeePlus"
			}
		}
	}
}

// Dossier portable zippé : KeePlus.exe et sa JVM, à dézipper n'importe où, sans
// installation ni droits administrateur (pas de WiX nécessaire, contrairement au .msi).
tasks.register<Zip>("packageZip") {
	group       = "distribution"
	description = "Construit le dossier portable de l'appli PC et le zippe."
	dependsOn("createDistributable")
	from(layout.buildDirectory.dir("compose/binaries/main/app"))
	archiveFileName.set("KeePlus-$kVersion-windows.zip")
	destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

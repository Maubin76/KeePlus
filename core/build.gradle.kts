// Module commun : coffre KDBX (lecture, écriture, modification), fusion de deux
// versions du coffre, générateur de mots de passe. Kotlin pur sur la JVM, sans
// Android ni interface : partagé par l'appli Android et l'appli PC.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.jvm)
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
	api(libs.kotpass)																// format KDBX 4 (KeePass)
	api(libs.okio)																	// ByteString des API de kotpass
	api(libs.kotlinx.coroutines.core)

	testImplementation(libs.junit)
}

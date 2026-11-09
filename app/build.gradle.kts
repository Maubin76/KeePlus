// Module application Android : Kotlin (intégré à AGP 9), Compose.
plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
}

android {
	namespace  = "fr.maubin.keeplus"
	compileSdk = 37

	defaultConfig {
		applicationId = "fr.maubin.keeplus"
		minSdk        = 30
		targetSdk     = 36
		versionCode   = property("keeplus.versionCode").toString().toInt()
		versionName   = property("keeplus.version").toString()
	}

	buildTypes {
		// Version optimisée (R8) ; signée avec la clé de debug tant qu'aucune clé de
		// publication n'est configurée.
		release {
			isMinifyEnabled   = true
			isShrinkResources = true
			signingConfig     = signingConfigs.getByName("debug")
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	buildFeatures {
		compose = true
	}
}

dependencies {
	implementation(project(":core"))
	implementation(project(":ui"))
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.compose.material3)
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.androidx.biometric)													// déverrouillage par empreinte
	implementation(libs.androidx.fragment.ktx)												// BiometricPrompt demande une FragmentActivity
	debugImplementation(libs.androidx.compose.ui.tooling)

	testImplementation(libs.junit)
}

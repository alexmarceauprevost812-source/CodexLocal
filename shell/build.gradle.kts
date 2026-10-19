plugins {
    alias(libs.plugins.android.application)
}

// « Coquille » : une toute petite appli Android (un WebView) qui sert de modele.
// Le Studio de CodexLocal en fait une copie, y glisse les fichiers du projet de l'utilisateur
// (assets/www), change son nom et son identifiant, la signe et l'installe. Elle n'a aucune
// permission : les applis fabriquees fonctionnent hors-ligne.
android {
    namespace = "com.codexlocal.shell"
    compileSdk = 36

    defaultConfig {
        // Reperes remplaces par le Studio (voir ConstructeurApk) : identifiant et nom de l'appli.
        applicationId = "com.codexlocal.app.p00000000"
        minSdk = 30
        // Reste sous 35 : pas d'affichage force sous les barres systeme pour les applis fabriquees.
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        // Variante du modele : comme « debug » (signee d'office) mais sans l'indicateur debuggable.
        create("modele") {
            initWith(getByName("debug"))
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

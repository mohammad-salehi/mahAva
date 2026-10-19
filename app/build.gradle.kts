import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Optional public Firebase client config; no Analytics SDK and no health payload in FCM.
val mahApplicationId = "com.mah.app"
val mahGoogleFile = rootProject.file("app/google-services.json")
val mahFirebase = if (mahGoogleFile.exists()) groovy.json.JsonSlurper().parse(mahGoogleFile) as Map<*, *> else emptyMap<Any, Any>()
val mahProject = mahFirebase["project_info"] as? Map<*, *> ?: emptyMap<Any, Any>()
val mahClients = mahFirebase["client"] as? List<*> ?: emptyList<Any>()
val mahClient = mahClients.mapNotNull { it as? Map<*, *> }.find {
    ((it["client_info"] as? Map<*, *>)?.get("android_client_info") as? Map<*, *>)?.get("package_name") == mahApplicationId
} ?: emptyMap<Any, Any>()
fun mahLiteral(value: Any?): String = "\"" + value?.toString().orEmpty().replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.mahava.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = mahApplicationId
        buildConfigField("String", "MAH_FCM_APP_ID", mahLiteral((mahClient["client_info"] as? Map<*, *>)?.get("mobilesdk_app_id")))
        buildConfigField("String", "MAH_FCM_PROJECT_ID", mahLiteral(mahProject["project_id"]))
        buildConfigField("String", "MAH_FCM_SENDER_ID", mahLiteral(mahProject["project_number"]))
        buildConfigField("String", "MAH_FCM_API_KEY", mahLiteral(((mahClient["api_key"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("current_key")))
        minSdk = 24
        targetSdk = 35
        versionCode = 140
        versionName = "1.4.8"
        testInstrumentationRunner = "com.mahava.app.MahavaTestRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "MAH_API_BASE_URL", "\"https://darapfm.ir\"")
        // Only an uncommitted local init script turns this on (debug ".demo" package).
        buildConfigField("boolean", "MAH_DEMO", "false")
        // Cafe Bazaar IAB (Poolakey). SKU must match the product id in Bazaar developer panel.
        buildConfigField(
            "String",
            "BAZAAR_RSA_PUBLIC_KEY",
            "\"MIHNMA0GCSqGSIb3DQEBAQUAA4G7ADCBtwKBrwDbkuDMRsELBkqgL+HS/x0+k8BqwSW9l2gK6NBvar7EFyzmHPjlUZZiBiLBxY3+PzsXVqEF/x4ZRr/pWXOlzjIs8gMJsVUZJymjrdKCOiiS9Zf36NIoEyuK1IwGOAOraWOb3afFLlkujew1d42Sdul3abR3UPqL76qQQNvPdxNLg+8WEjtq4gbXEBzlm4ZhclgFDsR2ADyBEtK2uC9NyJ9AXXVjaa5XYXK37a+mwjcCAwEAAQ==\""
        )
        buildConfigField("String", "BAZAAR_SKU_YEARLY", "\"mah_yearly_onetime\"")
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".redesign"
            versionNameSuffix = "-local"
        }
        release {
            isMinifyEnabled = false
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}

dependencies {
    implementation("com.google.firebase:firebase-messaging:25.0.1")
    // Use the installed compatible FIS artifact; the transitive 18.0.0 is not available here.
    implementation("com.google.firebase:firebase-installations:19.0.1")
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.google.code.gson:gson:2.11.0")

    // Cafe Bazaar In-App Billing
    implementation("com.github.cafebazaar.Poolakey:poolakey:2.2.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation(composeBom)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.work:work-testing:2.9.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test:rules:1.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}

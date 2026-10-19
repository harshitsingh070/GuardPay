// GuardPay Android companion: device call-state reporter only.
// No audio, contacts, SMS, or location — READ_PHONE_STATE exclusively.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "GuardPay Device"
include(":app")

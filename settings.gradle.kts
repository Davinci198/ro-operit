pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // Removed from the resolution path: dl.bintray.com (Bintray/JCenter shut down in 2021),
        // api.xposed.info (no Xposed dependency exists) and the oss.sonatype.org snapshots repository
        // (no -SNAPSHOT dependency exists). They only slowed every resolution down and kept domains
        // this project does not control in the artifact search path.
    }
}

rootProject.name = "Operit"
include(":app")
include(":terminal")
include(":mnn")
project(":mnn").projectDir = file("llm/mnn")
include(":llama")
project(":llama").projectDir = file("llm/llama")
include(":mmd")
project(":mmd").projectDir = file("avator/mmd")
include(":fbx")
project(":fbx").projectDir = file("avator/fbx")
include(":showerclient")
include(":quickjs")

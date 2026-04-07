pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "minimalistmusic"
include(":app")
include(":baselineprofile")

//enableFeaturePreview("VERSION_CATALOGS") // 使用版本目录
//enableFeaturePreview("PROFILEABLE") // 启用可配置文件支持
include(":benchmark")

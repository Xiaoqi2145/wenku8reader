pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
        // JitPack 置于最后：它响应慢且常超时，放在前面会让每个 Google/MavenCentral
        // 依赖都先向它轮询一次，显著拖慢依赖解析。项目中的 JitPack 依赖
        // （PhotoView、ByRecyclerView、medusa、immersionbar、rxhttp、crashx 等）
        // 在前面的仓库里找不到，仍会正确回落到这里。
        maven("https://jitpack.io")
    }
}

rootProject.name = "HikariNovel"
include(":app")

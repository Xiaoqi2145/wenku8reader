package com.cyh128.hikari_novel.util

object Constants {
    var isUpdateChecked = false

    /**
     * Where this fork publishes its builds.
     *
     * Every user-facing link used to point at the archived upstream repository,
     * so "go to download", the GitHub button, the help wiki and the crash
     * reporter all sent people to a project that no longer ships anything.
     */
    const val REPOSITORY_URL = "https://github.com/Xiaoqi2145/wenku8reader"

    const val RELEASES_URL = "$REPOSITORY_URL/releases"

    const val ISSUES_URL = "$REPOSITORY_URL/issues"
}

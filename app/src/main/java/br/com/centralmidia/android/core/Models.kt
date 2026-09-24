package br.com.centralmidia.android.core

data class AuthUser(
    val username: String,
    val name: String,
    val profile: String,
    val permissions: Set<String>,
    val mustChangePassword: Boolean = false,
)

data class AuthSession(
    val token: String,
    val expiresAt: String?,
    val user: AuthUser,
)

data class NewsSource(
    val id: String,
    val name: String,
    val region: String,
    val state: String,
    val group: String,
)

data class VideoSource(
    val id: String,
    val name: String,
    val group: String,
    val region: String,
    val state: String,
    val landingUrl: String,
    val searchUrlTemplate: String,
    val searchPrefix: String,
)

data class NewsItem(
    val title: String,
    val source: String,
    val link: String,
    val pubDate: String,
)

data class Newspaper(
    val name: String,
    val urls: List<String>,
)

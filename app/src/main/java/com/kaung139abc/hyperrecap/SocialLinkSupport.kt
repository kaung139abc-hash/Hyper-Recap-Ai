package com.kaung139abc.hyperrecap

import android.content.Intent
import android.net.Uri

/**
 * Safe hand-off for social-media page URLs.
 * This deliberately opens the official page/app; it does not bypass login,
 * DRM, platform download controls, or copyright restrictions.
 */
object SocialLinkSupport {
    enum class Platform(val label: String) {
        YOUTUBE("YouTube"),
        FACEBOOK("Facebook"),
        TIKTOK("TikTok"),
        INSTAGRAM("Instagram"),
        UNKNOWN("Unknown")
    }

    data class Link(val uri: Uri, val platform: Platform)

    private val urlPattern = Regex("https?://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE)

    fun extract(raw: String): Link? {
        val candidate = urlPattern.find(raw.trim())?.value
            ?.trimEnd('.', ',', ')', ']', '}', '!', '?', ';', ':') ?: return null
        val uri = runCatching { Uri.parse(candidate).normalizeScheme() }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.userInfo != null) return null
        val host = uri.host?.lowercase() ?: return null
        val platform = when {
            host == "youtube.com" || host.endsWith(".youtube.com") ||
                host == "youtu.be" -> Platform.YOUTUBE
            host == "facebook.com" || host.endsWith(".facebook.com") ||
                host == "fb.watch" || host.endsWith(".fb.watch") -> Platform.FACEBOOK
            host == "tiktok.com" || host.endsWith(".tiktok.com") -> Platform.TIKTOK
            host == "instagram.com" || host.endsWith(".instagram.com") -> Platform.INSTAGRAM
            else -> Platform.UNKNOWN
        }
        return Link(uri, platform)
    }

    fun openIntent(link: Link): Intent =
        Intent(Intent.ACTION_VIEW, link.uri).addCategory(Intent.CATEGORY_BROWSABLE)
}

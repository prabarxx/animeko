/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

object WebAdBlocker {
    private val blockedHostKeywords = listOf(
        "doubleclick",
        "googleadservices",
        "googlesyndication",
        "adservice",
        "adcash",
        "adsterra",
        "popads",
        "popcash",
        "exoclick",
        "propellerads",
        "monetag",
        "clickadu",
        "adnxs",
        "trafficjunky",
        "juicyads",
        "adcolony",
        "inmobi",
        "taboola",
        "outbrain",
        "criteo",
        "histats",
        "scorecardresearch",
        "google-analytics",
        "clarity.ms",
        "onesignal",
        "admaven",
        "adpushup",
        "bidvertiser",
        "adversal",
        "sovrn",
        "infolinks",
        "adblade",
        "revcontent",
        "mgid",
        "adthrive",
        "mediavine",
        "smartadserver",
        "openx",
        "rubiconproject",
        "pubmatic",
        "yieldmo",
        "teads",
        "gumgum",
        "adroll",
        "bet365",
        "1xbet",
        "betway",
        "stake.com",
        "mostbet",
        "parimatch",
        "melbet",
        "22bet",
        "betwinner",
        "casino",
        "gambling",
        "chaturbate",
        "bongacams",
        "stripchat",
        "livejasmin",
        "cam4",
        "adkeeper",
        "adsystem",
        "syndication",
        "onclick",
        "popunder",
        "adsrv",
        "adrun",
        "track",
        "telemetry",
    )

    private val blockedUrlPatterns = listOf(
        "/ads/",
        "/ad/",
        "/ad-banner",
        "/adv/",
        "/popunder",
        "/pop.",
        "/popups/",
        "/banners/",
        "/vast",
        "/vpaid",
        "ad_type=",
        "ad_unit=",
        "banner_id=",
        "zoneid=",
    )

    private val blockedSchemes = setOf(
        "intent",
        "market",
        "whatsapp",
        "tg",
        "telegram",
        "fb",
        "line",
        "itms-apps",
        "viber",
        "twitter",
        "mailto",
        "tel",
        "sms",
    )

    fun isBlockedScheme(scheme: String?): Boolean {
        if (scheme == null) return false
        return scheme.lowercase() in blockedSchemes
    }

    fun isAdUrl(url: String): Boolean {
        val lower = url.lowercase()

        // 1. Check scheme
        val schemeEnd = lower.indexOf(":")
        if (schemeEnd > 0) {
            val scheme = lower.substring(0, schemeEnd)
            if (scheme in blockedSchemes) return true
        }

        // 2. Check host keywords
        for (keyword in blockedHostKeywords) {
            if (lower.contains(keyword)) {
                return true
            }
        }

        // 3. Check path / query patterns
        for (pattern in blockedUrlPatterns) {
            if (lower.contains(pattern)) {
                return true
            }
        }

        return false
    }

    fun isAllowedHost(host: String): Boolean {
        val lower = host.lowercase()
        for (keyword in blockedHostKeywords) {
            if (lower.contains(keyword)) return false
        }
        return true
    }
}

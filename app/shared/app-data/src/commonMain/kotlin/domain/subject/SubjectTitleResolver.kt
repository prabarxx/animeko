/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.subject

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

object SubjectTitleResolver : SynchronizedObject() {
    private val memoryCache = mutableMapOf<Int, String>()

    /**
     * Checks if a string consists entirely of Latin letters, numbers, spaces, and standard punctuation,
     * without any CJK / Kanji / Kana characters.
     */
    fun isPureLatin(text: String): Boolean {
        if (text.isBlank()) return false
        val hasLatin = text.any { (it in 'a'..'z') || (it in 'A'..'Z') }
        if (!hasLatin) return false
        for (ch in text) {
            val code = ch.code
            // CJK Unified Ideographs, Extension A, Hiragana, Katakana, Bopomofo, Hangul
            if ((code in 0x4E00..0x9FFF) ||
                (code in 0x3400..0x4DBF) ||
                (code in 0x3040..0x309F) ||
                (code in 0x30A0..0x30FF) ||
                (code in 0x3100..0x312F) ||
                (code in 0xAC00..0xD7AF)
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Checks if a string has readable Latin script, allowing some non-Latin symbols as long as
     * Latin characters dominate over CJK ideographs.
     */
    fun isReadableLatin(text: String): Boolean {
        if (text.isBlank()) return false
        val hasLatin = text.any { (it in 'a'..'z') || (it in 'A'..'Z') }
        if (!hasLatin) return false
        var cjkCount = 0
        var latinCount = 0
        for (ch in text) {
            val code = ch.code
            if ((code in 0x4E00..0x9FFF) ||
                (code in 0x3400..0x4DBF) ||
                (code in 0x3040..0x309F) ||
                (code in 0x30A0..0x30FF)
            ) {
                cjkCount++
            } else if ((ch in 'a'..'z') || (ch in 'A'..'Z')) {
                latinCount++
            }
        }
        return cjkCount == 0 || latinCount >= cjkCount
    }

    /**
     * Selects the most readable Latin / Western title (Romaji / English) without Chinese/Kanji ideographs.
     */
    fun pickReadableTitle(name: String, nameCn: String = "", aliases: List<String> = emptyList()): String {
        // 1. Look for a pure Latin alias first (0 CJK characters) - e.g. "Jujutsu Kaisen", "Attack on Titan"
        val pureLatinAlias = aliases.firstOrNull { isPureLatin(it) }
        if (pureLatinAlias != null) return pureLatinAlias

        // 2. Check if original name is pure Latin
        if (isPureLatin(name)) return name

        // 3. Check for any predominantly Latin alias
        val readableAlias = aliases.firstOrNull { isReadableLatin(it) }
        if (readableAlias != null) return readableAlias

        // 4. Check if original name is predominantly Latin
        if (isReadableLatin(name)) return name

        // 5. Check if nameCn is Latin (unlikely, but possible)
        if (isPureLatin(nameCn)) return nameCn
        if (isReadableLatin(nameCn)) return nameCn

        // 6. Fallback to original name or Chinese name
        return name.ifBlank { nameCn }
    }

    fun getCachedReadableTitle(subjectId: Int): String? = synchronized(this) {
        memoryCache[subjectId]
    }

    fun setCachedReadableTitle(subjectId: Int, title: String) {
        if (title.isNotBlank()) {
            synchronized(this) {
                memoryCache[subjectId] = title
            }
        }
    }

    /**
     * Resolves a readable title for a subject, checking cache and optionally querying Bangumi API for aliases.
     */
    suspend fun resolveReadableTitle(
        subjectId: Int,
        name: String,
        nameCn: String,
        httpClient: HttpClient? = null,
    ): String {
        if (isPureLatin(name)) return name

        getCachedReadableTitle(subjectId)?.let { return it }

        if (httpClient != null && subjectId > 0) {
            val fetched = fetchAliasFromBangumi(subjectId, httpClient)
            if (fetched != null && isReadableLatin(fetched)) {
                setCachedReadableTitle(subjectId, fetched)
                return fetched
            }
        }

        val fallback = pickReadableTitle(name, nameCn)
        setCachedReadableTitle(subjectId, fallback)
        return fallback
    }

    private suspend fun fetchAliasFromBangumi(subjectId: Int, httpClient: HttpClient): String? {
        return try {
            withTimeoutOrNull(2.seconds) {
                val resp = httpClient.get("https://api.bgm.tv/v0/subjects/$subjectId") {
                    header(HttpHeaders.UserAgent, "Animeko/6.2.7 (Linux; Android)")
                }
                if (!resp.status.isSuccess()) return@withTimeoutOrNull null
                val text = resp.bodyAsText()
                val json = Json { ignoreUnknownKeys = true; isLenient = true }
                val root = json.parseToJsonElement(text).jsonObject
                val infobox = root["infobox"]?.jsonArray ?: return@withTimeoutOrNull null
                val aliases = mutableListOf<String>()
                for (boxEl in infobox) {
                    val boxObj = boxEl.jsonObject
                    val key = boxObj["key"]?.jsonPrimitive?.content
                    if (key == "别名" || key == "英文名") {
                        val valueEl = boxObj["value"]
                        if (valueEl is JsonArray) {
                            for (v in valueEl) {
                                val itemObj = v.jsonObject
                                itemObj["v"]?.jsonPrimitive?.content?.let { aliases.add(it) }
                            }
                        } else if (valueEl is JsonPrimitive) {
                            aliases.add(valueEl.content)
                        }
                    }
                }
                aliases.firstOrNull { isPureLatin(it) } ?: aliases.firstOrNull { isReadableLatin(it) }
            }
        } catch (_: Exception) {
            null
        }
    }
}

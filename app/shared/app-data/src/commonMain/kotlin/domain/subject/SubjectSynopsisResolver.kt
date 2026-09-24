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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.createDefaultHttpClient
import kotlin.time.Duration.Companion.seconds

object SubjectSynopsisResolver : SynchronizedObject() {
    private val idCache = mutableMapOf<Int, String>()
    private val textCache = mutableMapOf<String, String>()
    private val httpClient by lazy { createDefaultHttpClient() }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun hasCJK(text: String): Boolean {
        if (text.isBlank()) return false
        for (ch in text) {
            val code = ch.code
            if ((code in 0x4E00..0x9FFF) ||
                (code in 0x3400..0x4DBF) ||
                (code in 0x3040..0x30FF)
            ) {
                return true
            }
        }
        return false
    }

    fun getCachedSynopsis(subjectId: Int): String? = synchronized(this) {
        idCache[subjectId]
    }

    fun getCachedSynopsis(originalText: String): String? = synchronized(this) {
        textCache[originalText]
    }

    fun setCachedSynopsis(subjectId: Int, synopsis: String) {
        if (synopsis.isNotBlank() && subjectId > 0) {
            synchronized(this) {
                idCache[subjectId] = synopsis
            }
        }
    }

    suspend fun resolveSynopsis(
        originalSummary: String,
        subjectName: String = "",
        subjectId: Int = 0,
    ): String? = withContext(Dispatchers.IO_) {
        if (originalSummary.isBlank()) return@withContext null
        if (!hasCJK(originalSummary)) return@withContext originalSummary

        if (subjectId > 0) {
            getCachedSynopsis(subjectId)?.let { return@withContext it }
        }
        getCachedSynopsis(originalSummary)?.let { return@withContext it }

        var cleanEn: String? = null
        if (subjectName.isNotBlank() && SubjectTitleResolver.isReadableLatin(subjectName)) {
            cleanEn = fetchKitsuSynopsis(subjectName)
        }

        val textToTranslate = cleanEn?.take(400) ?: originalSummary.take(280)
        val langPair = if (cleanEn != null) "en|es" else "zh|es"

        val translated = translateWithMyMemory(textToTranslate, langPair)
        val finalResult = translated ?: cleanEn

        if (!finalResult.isNullOrBlank()) {
            synchronized(this@SubjectSynopsisResolver) {
                if (subjectId > 0) idCache[subjectId] = finalResult
                textCache[originalSummary] = finalResult
            }
        }
        finalResult
    }

    private suspend fun fetchKitsuSynopsis(query: String): String? {
        return try {
            withTimeoutOrNull(3.seconds) {
                val resp = httpClient.get("https://kitsu.io/api/edge/anime") {
                    header(HttpHeaders.UserAgent, "Animeko/6.2.7 (Linux; Android)")
                    url {
                        parameters.append("filter[text]", query)
                        parameters.append("page[limit]", "1")
                    }
                }
                if (!resp.status.isSuccess()) return@withTimeoutOrNull null
                val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                val data = root["data"]?.jsonArray ?: return@withTimeoutOrNull null
                if (data.isEmpty()) return@withTimeoutOrNull null
                val attrs = data[0].jsonObject["attributes"]?.jsonObject
                attrs?.get("synopsis")?.jsonPrimitive?.contentOrNull
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun translateWithMyMemory(text: String, langPair: String): String? {
        return try {
            withTimeoutOrNull(4.seconds) {
                val resp = httpClient.get("https://api.mymemory.translated.net/get") {
                    header(HttpHeaders.UserAgent, "Animeko/6.2.7 (Linux; Android)")
                    url {
                        parameters.append("q", text)
                        parameters.append("langpair", langPair)
                    }
                }
                if (!resp.status.isSuccess()) return@withTimeoutOrNull null
                val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                root["responseData"]?.jsonObject?.get("translatedText")?.jsonPrimitive?.contentOrNull
            }
        } catch (_: Exception) {
            null
        }
    }
}

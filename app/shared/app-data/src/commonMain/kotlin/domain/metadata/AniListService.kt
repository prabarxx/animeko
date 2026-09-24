/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.metadata

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.data.models.trending.TrendsInfo
import me.him188.ani.app.domain.subject.SubjectSynopsisResolver
import me.him188.ani.app.domain.subject.SubjectTitleResolver
import me.him188.ani.utils.coroutines.IO_
import kotlin.time.Duration.Companion.seconds

data class AniListMedia(
    val aniListId: Int,
    val title: String,
    val nativeTitle: String,
    val romajiTitle: String,
    val coverUrl: String,
    val description: String,
)

object AniListService : SynchronizedObject() {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val bangumiIdCache = mutableMapOf<Int, Int>()

    suspend fun fetchTrending(httpClient: HttpClient): TrendsInfo? = withContext(Dispatchers.IO_) {
        try {
            val query = """
                query {
                  Page(page: 1, perPage: 12) {
                    media(type: ANIME, sort: TRENDING_DESC) {
                      id
                      title {
                        romaji
                        english
                        native
                      }
                      coverImage {
                        extraLarge
                        large
                      }
                      description(asHtml: false)
                    }
                  }
                }
            """.trimIndent()

            val response = withTimeoutOrNull(6.seconds) {
                httpClient.post("https://graphql.anilist.co") {
                    header(HttpHeaders.UserAgent, "Animeko/6.2.7 (Linux; Android)")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("query", query)
                    }.toString())
                }
            } ?: return@withContext null

            if (!response.status.isSuccess()) return@withContext null

            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val mediaArray = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
                ?: return@withContext null

            val rawItems = mediaArray.mapNotNull { el ->
                val m = el.jsonObject
                val aniId = m["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val titleObj = m["title"]?.jsonObject
                val romaji = titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull ?: ""
                val english = titleObj?.get("english")?.jsonPrimitive?.contentOrNull ?: ""
                val native = titleObj?.get("native")?.jsonPrimitive?.contentOrNull ?: ""
                val coverObj = m["coverImage"]?.jsonObject
                val cover = coverObj?.get("extraLarge")?.jsonPrimitive?.contentOrNull
                    ?: coverObj?.get("large")?.jsonPrimitive?.contentOrNull ?: ""
                val desc = m["description"]?.jsonPrimitive?.contentOrNull ?: ""

                val cleanTitle = if (english.isNotBlank() && SubjectTitleResolver.isReadableLatin(english)) {
                    english
                } else if (romaji.isNotBlank() && SubjectTitleResolver.isReadableLatin(romaji)) {
                    romaji
                } else {
                    romaji.ifBlank { native }
                }

                AniListMedia(
                    aniListId = aniId,
                    title = cleanTitle,
                    nativeTitle = native,
                    romajiTitle = romaji,
                    coverUrl = cover,
                    description = desc,
                )
            }

            if (rawItems.isEmpty()) return@withContext null

            val resolvedSubjects = coroutineScope {
                rawItems.map { item ->
                    async {
                        val bgmId = resolveBangumiId(item, httpClient)
                        if (bgmId != null && bgmId > 0) {
                            SubjectTitleResolver.setCachedReadableTitle(bgmId, item.title)
                            if (item.description.isNotBlank()) {
                                SubjectSynopsisResolver.setCachedSynopsis(bgmId, item.description)
                            }
                            TrendingSubjectInfo(
                                bangumiId = bgmId,
                                nameCn = item.nativeTitle,
                                imageLarge = item.coverUrl,
                                name = item.title,
                            )
                        } else null
                    }
                }.awaitAll().filterNotNull()
            }

            if (resolvedSubjects.isEmpty()) return@withContext null
            TrendsInfo(resolvedSubjects)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchPopular(page: Int, perPage: Int, httpClient: HttpClient): List<RecommendedItemInfo>? = withContext(Dispatchers.IO_) {
        try {
            val query = """
                query (${'$'}page: Int, ${'$'}perPage: Int) {
                  Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    pageInfo {
                      total
                      hasNextPage
                    }
                    media(type: ANIME, sort: POPULARITY_DESC) {
                      id
                      title {
                        romaji
                        english
                        native
                      }
                      coverImage {
                        extraLarge
                        large
                      }
                      description(asHtml: false)
                    }
                  }
                }
            """.trimIndent()

            val response = withTimeoutOrNull(6.seconds) {
                httpClient.post("https://graphql.anilist.co") {
                    header(HttpHeaders.UserAgent, "Animeko/6.2.7 (Linux; Android)")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("query", query)
                        put("variables", buildJsonObject {
                            put("page", page)
                            put("perPage", perPage)
                        })
                    }.toString())
                }
            } ?: return@withContext null

            if (!response.status.isSuccess()) return@withContext null

            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val mediaArray = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
                ?: return@withContext null

            val rawItems = mediaArray.mapNotNull { el ->
                val m = el.jsonObject
                val aniId = m["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val titleObj = m["title"]?.jsonObject
                val romaji = titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull ?: ""
                val english = titleObj?.get("english")?.jsonPrimitive?.contentOrNull ?: ""
                val native = titleObj?.get("native")?.jsonPrimitive?.contentOrNull ?: ""
                val coverObj = m["coverImage"]?.jsonObject
                val cover = coverObj?.get("extraLarge")?.jsonPrimitive?.contentOrNull
                    ?: coverObj?.get("large")?.jsonPrimitive?.contentOrNull ?: ""
                val desc = m["description"]?.jsonPrimitive?.contentOrNull ?: ""

                val cleanTitle = if (english.isNotBlank() && SubjectTitleResolver.isReadableLatin(english)) {
                    english
                } else if (romaji.isNotBlank() && SubjectTitleResolver.isReadableLatin(romaji)) {
                    romaji
                } else {
                    romaji.ifBlank { native }
                }

                AniListMedia(
                    aniListId = aniId,
                    title = cleanTitle,
                    nativeTitle = native,
                    romajiTitle = romaji,
                    coverUrl = cover,
                    description = desc,
                )
            }

            if (rawItems.isEmpty()) return@withContext null

            val resolved = coroutineScope {
                rawItems.map { item ->
                    async {
                        val bgmId = resolveBangumiId(item, httpClient)
                        if (bgmId != null && bgmId > 0) {
                            SubjectTitleResolver.setCachedReadableTitle(bgmId, item.title)
                            if (item.description.isNotBlank()) {
                                SubjectSynopsisResolver.setCachedSynopsis(bgmId, item.description)
                            }
                            RecommendedSubjectInfo(
                                bangumiId = bgmId,
                                nameCn = item.nativeTitle,
                                imageLarge = item.coverUrl,
                                name = item.title,
                            )
                        } else null
                    }
                }.awaitAll().filterNotNull()
            }

            resolved.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveBangumiId(media: AniListMedia, httpClient: HttpClient): Int? {
        synchronized(this) {
            val cached = bangumiIdCache[media.aniListId]
            if (cached != null) return cached
        }

        val searchTerms = listOfNotNull(
            media.nativeTitle.takeIf { it.isNotBlank() },
            media.romajiTitle.takeIf { it.isNotBlank() },
            media.title.takeIf { it.isNotBlank() }
        ).distinct()

        for (term in searchTerms) {
            try {
                val resp = withTimeoutOrNull(2.seconds) {
                    httpClient.post("https://api.bgm.tv/v0/search/subjects") {
                        header(HttpHeaders.UserAgent, "Animeko/6.2.7 (Linux; Android)")
                        contentType(ContentType.Application.Json)
                        setBody(buildJsonObject {
                            put("keyword", term)
                            put("filter", buildJsonObject {
                                put("type", buildJsonArray {
                                    add(JsonPrimitive(2)) // anime
                                })
                            })
                        }.toString())
                    }
                }
                if (resp != null && resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val items = root["data"]?.jsonArray
                    if (!items.isNullOrEmpty()) {
                        val firstId = items[0].jsonObject["id"]?.jsonPrimitive?.intOrNull
                        if (firstId != null && firstId > 0) {
                            synchronized(this) {
                                bangumiIdCache[media.aniListId] = firstId
                            }
                            return firstId
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }
}

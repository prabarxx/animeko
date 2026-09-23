/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.models.AniNsfwFilter
import me.him188.ani.client.models.AniSubjectSearch
import me.him188.ani.client.models.AniSubjectSearchField
import me.him188.ani.client.models.AniSubjectSearchSortBy
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.utils.coroutines.IO_
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.ktor.createDefaultHttpClient
import kotlin.coroutines.CoroutineContext


class AniSubjectSearchService(
    private val subjectApi: ApiInvoker<SubjectsAniApi>,
    private val httpClient: HttpClient = createDefaultHttpClient(),
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    suspend fun searchSubjects(
        keyword: String,
        offset: Int? = null,
        limit: Int? = null,

        sort: SearchSort = SearchSort.MATCH,
        filters: SubjectSearchFilters? = null,
        fields: List<SubjectSearchField>? = null,
    ): List<BatchSubjectDetails> = withContext(ioDispatcher) {
        val result = try {
            subjectApi.invoke {
                searchSubjects(
                    q = keyword,
                    offset = offset,
                    limit = limit,
                    tags = filters?.tags,
                    airDates = filters?.airDates,
                    ratings = filters?.ratings,
                    ranks = filters?.ranks,
                    includeNsfw = when (filters?.nsfw) {
                        true -> AniNsfwFilter.ONLY
                        false -> AniNsfwFilter.EXCLUDE
                        null -> AniNsfwFilter.INCLUDE
                    },
                    sortBy = when (sort) {
                        SearchSort.MATCH -> AniSubjectSearchSortBy.RELEVANCE
                        SearchSort.RANK -> AniSubjectSearchSortBy.RATING_DESC
                        SearchSort.COLLECTION -> AniSubjectSearchSortBy.COLLECTION_DESC
                        SearchSort.DATE -> AniSubjectSearchSortBy.AIR_DATE_DESC
                    },
                    fields = fields?.map { it.toAniField() },
                )
            }.body().items.map { search -> search.toBatchSubjectDetails() }
        } catch (e: Exception) {
            emptyList()
        }

        if (result.isNotEmpty()) {
            return@withContext result
        }

        return@withContext searchBangumiSubjects(
            keyword = keyword,
            offset = offset ?: 0,
            limit = limit ?: 20,
        )
    }

    private suspend fun searchBangumiSubjects(
        keyword: String,
        offset: Int,
        limit: Int,
    ): List<BatchSubjectDetails> {
        return try {
            val response = httpClient.post("https://api.bgm.tv/v0/search/subjects") {
                header(HttpHeaders.UserAgent, "Animeko/6.2.1 (Linux; Android)")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("keyword", keyword)
                        put("filter", buildJsonObject {
                            put("type", buildJsonArray { add(JsonPrimitive(2)) })
                        })
                        put("limit", limit)
                        put("offset", offset)
                    }.toString(),
                )
            }
            if (!response.status.isSuccess()) {
                return emptyList()
            }
            val text = response.bodyAsText()
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val root = json.parseToJsonElement(text).jsonObject
            val dataArray = root["data"]?.jsonArray ?: return emptyList()
            dataArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val nameCn = obj["name_cn"]?.jsonPrimitive?.contentOrNull ?: ""
                val summary = obj["summary"]?.jsonPrimitive?.contentOrNull ?: ""
                val nsfw = obj["nsfw"]?.jsonPrimitive?.booleanOrNull ?: false
                val date = obj["date"]?.jsonPrimitive?.contentOrNull ?: ""
                val eps = obj["eps"]?.jsonPrimitive?.intOrNull
                    ?: obj["total_episodes"]?.jsonPrimitive?.intOrNull ?: 0

                val imagesObj = obj["images"]?.jsonObject
                val imageLarge = imagesObj?.get("large")?.jsonPrimitive?.contentOrNull
                    ?: imagesObj?.get("common")?.jsonPrimitive?.contentOrNull
                    ?: "https://static.myani.org/subjects/$id/cover/thumb"

                val ratingObj = obj["rating"]?.jsonObject
                val rank = ratingObj?.get("rank")?.jsonPrimitive?.intOrNull ?: 0
                val total = ratingObj?.get("total")?.jsonPrimitive?.intOrNull ?: 0
                val score = ratingObj?.get("score")?.jsonPrimitive?.contentOrNull ?: ""

                val tagsArray = obj["tags"]?.jsonArray ?: emptyList()
                val tags = tagsArray.mapNotNull { tagEl ->
                    val tagObj = tagEl.jsonObject
                    val tagName = tagObj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val tagCount = tagObj["count"]?.jsonPrimitive?.intOrNull ?: 0
                    Tag(tagName, tagCount)
                }

                BatchSubjectDetails(
                    subjectInfo = SubjectInfo(
                        subjectId = id,
                        subjectType = SubjectType.ANIME,
                        name = name.ifBlank { nameCn },
                        nameCn = nameCn.ifBlank { name },
                        summary = summary,
                        nsfw = nsfw,
                        imageLarge = imageLarge,
                        totalEpisodes = eps,
                        airDate = PackedDate.parseFromDate(date),
                        tags = tags,
                        aliases = emptyList(),
                        ratingInfo = RatingInfo(rank, total, RatingCounts.Zero, score),
                        collectionStats = SubjectCollectionStats.Zero,
                        completeDate = PackedDate.Invalid,
                    ),
                    mainEpisodeCount = eps,
                    lightSubjectRelations = LightSubjectRelations(emptyList(), emptyList()),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        fun sanitizeKeyword(keyword: String): String {
            return buildString(keyword.length) {
                for (c in keyword) {
                    if (MediaListFilters.charsToDeleteForSearch.contains(c.code)) {
                        append(' ')
                    } else {
                        append(c)
                    }
                }
            }
        }
    }

    private fun AniSubjectSearch.toBatchSubjectDetails(): BatchSubjectDetails {
        return BatchSubjectDetails(
            subjectInfo = SubjectInfo(
                subjectId = this.id.toInt(),
                subjectType = SubjectType.ANIME,
                name = this.name,
                nameCn = this.nameCn,
                summary = this.summary,
                nsfw = this.nsfw,
                imageLarge = this.imageLarge,
                totalEpisodes = this.mainEpisodeCount,
                airDate = PackedDate.parseFromDate(this.airDate),
                tags = this.tags.map { Tag(it.name, it.count) },
                aliases = emptyList(),
                ratingInfo = RatingInfo(this.rank ?: 0, this.ratingTotal, RatingCounts.Zero, this.score ?: ""),
                collectionStats = SubjectCollectionStats.Zero,
                completeDate = PackedDate.Invalid,

                ),
            mainEpisodeCount = this.mainEpisodeCount,
            lightSubjectRelations = LightSubjectRelations(
                lightRelatedPersonInfoList = this.lightRelatedPersonInfoList.map { pi ->
                    LightRelatedPersonInfo(pi.name, PersonPosition(pi.position))
                },
                lightRelatedCharacterInfoList = emptyList(),
            ),
        )
    }
}

private fun SubjectSearchField.toAniField(): AniSubjectSearchField = when (this) {
    SubjectSearchField.NAME -> AniSubjectSearchField.NAME
    SubjectSearchField.SUMMARY -> AniSubjectSearchField.SUMMARY
    SubjectSearchField.IMAGE_LARGE -> AniSubjectSearchField.IMAGE_LARGE
    SubjectSearchField.NSFW -> AniSubjectSearchField.NSFW
    SubjectSearchField.AIR_DATE -> AniSubjectSearchField.AIR_DATE
    SubjectSearchField.SCORE -> AniSubjectSearchField.SCORE
    SubjectSearchField.RANK -> AniSubjectSearchField.RANK
    SubjectSearchField.RATING_TOTAL -> AniSubjectSearchField.RATING_TOTAL
    SubjectSearchField.FAVORITE -> AniSubjectSearchField.FAVORITE
    SubjectSearchField.TAGS -> AniSubjectSearchField.TAGS
    SubjectSearchField.MAIN_EPISODE_COUNT -> AniSubjectSearchField.MAIN_EPISODE_COUNT
    SubjectSearchField.LIGHT_RELATED_PERSON_INFO -> AniSubjectSearchField.LIGHT_RELATED_PERSON_INFO
}

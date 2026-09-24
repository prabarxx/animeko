/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.data.models.trending.TrendsInfo
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.runWrappingExceptionAsLoadResult
import me.him188.ani.app.tools.paging.SinglePagePagingSource
import me.him188.ani.client.apis.TrendsAniApi
import me.him188.ani.client.models.AniTrends
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.error
import kotlin.coroutines.CoroutineContext

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.utils.ktor.createDefaultHttpClient

class TrendsRepository(
    private val trendsApi: ApiInvoker<TrendsAniApi>,
    private val httpClient: HttpClient = createDefaultHttpClient(),
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_
) : Repository() {
    suspend fun getTrendsInfo(): TrendsInfo {
        return withContext(ioDispatcher) {
            me.him188.ani.app.domain.metadata.AniListService.fetchTrending(httpClient)
                ?: fetchBangumiNextTrends()
                ?: trendsApi {
                    getTrends().body().toTrendsInfo()
                }
        }
    }

    // From AniList, Bangumi Next or Animeko server
    fun trendsInfoPager(): Flow<PagingData<TrendsInfo>> {
        return Pager(defaultPagingConfig) {
            SinglePagePagingSource<Unit, TrendsInfo> {
                runWrappingExceptionAsLoadResult<Unit, TrendsInfo> {
                    val trendsInfo = withContext(ioDispatcher) {
                        me.him188.ani.app.domain.metadata.AniListService.fetchTrending(httpClient)
                            ?: fetchBangumiNextTrends()
                            ?: trendsApi {
                                getTrends().body().toTrendsInfo()
                            }
                    }
                    PagingSource.LoadResult.Page(
                        listOf(trendsInfo),
                        null,
                        null,
                    )
                }.also {
                    if (it is PagingSource.LoadResult.Error) {
                        logger.error(it.throwable) { "Failed to load ani trends info." }
                    }
                }
            }
        }.flow
    }

    private suspend fun fetchBangumiNextTrends(): TrendsInfo? {
        return try {
            val response = httpClient.get("https://next.bgm.tv/p1/trending/subjects?type=2") {
                header(HttpHeaders.UserAgent, "Animeko/6.2.1 (Linux; Android)")
            }
            if (!response.status.isSuccess()) return null
            val text = response.bodyAsText()
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val root = json.parseToJsonElement(text).jsonObject
            val dataArray = root["data"]?.jsonArray ?: return null
            val subjects = dataArray.mapNotNull { itemEl ->
                val sub = itemEl.jsonObject["subject"]?.jsonObject ?: return@mapNotNull null
                val id = sub["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val name = sub["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val nameCn = sub["nameCN"]?.jsonPrimitive?.contentOrNull ?: ""
                val images = sub["images"]?.jsonObject
                val img = images?.get("large")?.jsonPrimitive?.contentOrNull
                    ?: images?.get("common")?.jsonPrimitive?.contentOrNull
                    ?: "https://static.myani.org/subjects/$id/cover/thumb"
                TrendingSubjectInfo(
                    bangumiId = id,
                    nameCn = nameCn,
                    imageLarge = img,
                    name = name,
                )
            }
            if (subjects.isEmpty()) return null
            val resolvedSubjects = coroutineScope {
                subjects.map { item ->
                    async {
                        val readable = me.him188.ani.app.domain.subject.SubjectTitleResolver.resolveReadableTitle(
                            item.bangumiId,
                            item.name,
                            item.nameCn,
                            httpClient,
                        )
                        item.copy(name = readable)
                    }
                }.awaitAll()
            }
            TrendsInfo(resolvedSubjects)
        } catch (e: Exception) {
            null
        }
    }
}

fun AniTrends.toTrendsInfo(): TrendsInfo {
    return TrendsInfo(
        subjects = trendingSubjects.map {
            TrendingSubjectInfo(it.bangumiId, it.nameCn, it.imageLarge)
        },
    )
}

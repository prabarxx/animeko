/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.media

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceTier
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.datasources.mikan.MikanCNMediaSource
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.collections.partiallyReorderBy

sealed class MediaSourceInstanceRepository : Repository() {
    abstract val flow: Flow<List<MediaSourceSave>>

    abstract suspend fun clear()
    abstract suspend fun remove(instanceId: String)

    /**
     * 一次性移除多个数据源, 只触发一次 [flow] 更新.
     */
    abstract suspend fun removeAll(instanceIds: Collection<String>)
    abstract suspend fun add(mediaSourceSave: MediaSourceSave)

    abstract suspend fun updateSave(instanceId: String, config: MediaSourceSave.() -> MediaSourceSave): Boolean

    /**
     * 一次性更新多个数据源, 只触发一次 [flow] 更新.
     */
    abstract suspend fun updateSaves(instanceIds: Collection<String>, update: MediaSourceSave.() -> MediaSourceSave)

    /**
     * @see partiallyReorderBy
     */
    abstract suspend fun partiallyReorder(newOrderInstanceIds: List<String>)
}

suspend inline fun MediaSourceInstanceRepository.updateConfig(instanceId: String, config: MediaSourceConfig): Boolean {
    return updateSave(instanceId) {
        copy(config = config)
    }
}

@Serializable
data class MediaSourceSaves(
    val instances: List<MediaSourceSave> = emptyList(),
) {
    companion object {
        val Empty = MediaSourceSaves(emptyList())
        val Default: MediaSourceSaves by lazy {
            fun createSave(
                sourceId: String,
                factoryId: FactoryId,
                isEnabled: Boolean
            ) = MediaSourceSave(
                instanceId = Uuid.randomString(),
                mediaSourceId = sourceId,
                factoryId = factoryId,
                isEnabled = isEnabled,
                config = MediaSourceConfig.Default,
            )

            val enabledBtSources: List<String> =
                listOf(MikanCNMediaSource.ID, "dmhy")
            val disabledBtSources: List<String> = listOf()

            fun createAnimeOnlineNinjaSave(): MediaSourceSave {
                val args = SelectorMediaSourceArguments(
                    name = "AnimeOnline Ninja",
                    description = "Anime en streaming con subtítulos y doblaje latino",
                    iconUrl = "https://ww3.animeonline.ninja/wp-content/uploads/2020/05/cropped-favicon-192x192.png",
                    searchConfig = SelectorSearchConfig(
                        searchUrl = "https://ww3.animeonline.ninja/?s={keyword}",
                        searchUseOnlyFirstWord = false,
                        searchRemoveSpecial = true,
                        searchUseSubjectNamesCount = 5,
                        defaultSubtitleLanguage = SubtitleLanguage.Spanish,
                        subjectFormatId = SelectorSubjectFormatA.id,
                        selectorSubjectFormatA = SelectorSubjectFormatA.Config(
                            selectLists = "div.result-item article .title a",
                            preferShorterName = true,
                        ),
                        channelFormatId = SelectorChannelFormatNoChannel.id,
                        selectorChannelFormatNoChannel = SelectorChannelFormatNoChannel.Config(
                            selectEpisodes = "ul.episodios li .episodiotitle a, ul.episodios li a, .episodios li a",
                            matchEpisodeSortFromName = """(?i)(?:(?:episodio|cap[ií]tulo|ep\.?)\s*)?(?<ep>\d+(?:\.\d+)?)""",
                        ),
                        filterByEpisodeSort = false,
                        filterBySubjectName = false,
                    ),
                    tier = MediaSourceTier(0u),
                )
                val json = Json {
                    encodeDefaults = true
                    ignoreUnknownKeys = true
                }
                return MediaSourceSave(
                    instanceId = Uuid.randomString(),
                    mediaSourceId = "animeonline-ninja",
                    factoryId = SelectorMediaSource.FactoryId,
                    isEnabled = true,
                    config = MediaSourceConfig(
                        serializedArguments = json.encodeToJsonElement(SelectorMediaSourceArguments.serializer(), args),
                    ),
                )
            }

            MediaSourceSaves(
                buildList {
                    add(createAnimeOnlineNinjaSave())
                    enabledBtSources.forEach { add(createSave(it, FactoryId(it), isEnabled = true)) }
                    disabledBtSources.forEach { add(createSave(it, FactoryId(it), isEnabled = false)) }
                },
            )
        }
    }
}

class MediaSourceInstanceRepositoryImpl(
    private val dataStore: DataStore<MediaSourceSaves>
) : MediaSourceInstanceRepository() {
    override val flow: Flow<List<MediaSourceSave>> = dataStore.data.map { it.instances }
    override suspend fun clear() {
        dataStore.updateData { MediaSourceSaves.Empty }
    }

    override suspend fun remove(instanceId: String) {
        dataStore.updateData { current ->
            current.copy(instances = current.instances.filter { it.instanceId != instanceId })
        }
    }

    override suspend fun removeAll(instanceIds: Collection<String>) {
        val ids = instanceIds.toSet()
        dataStore.updateData { current ->
            current.copy(instances = current.instances.filterNot { it.instanceId in ids })
        }
    }

    override suspend fun add(mediaSourceSave: MediaSourceSave) {
        dataStore.updateData { current ->
            if (current.instances.any { it.instanceId == mediaSourceSave.instanceId }) {
                error("Attempting to add a duplicated MediaSourceSave: $mediaSourceSave")
            }
            current.copy(instances = current.instances + mediaSourceSave)
        }
    }

    override suspend fun updateSave(instanceId: String, config: MediaSourceSave.() -> MediaSourceSave): Boolean {
        var found = false
        dataStore.updateData { current ->
            found = current.instances.any { it.instanceId == instanceId }
            if (found) {
                current.copy(
                    instances = current.instances.map { save ->
                        if (save.instanceId == instanceId) {
                            save.run(config)
                        } else {
                            save
                        }
                    },
                )
            } else {
                current
            }
        }
        return found
    }

    override suspend fun updateSaves(instanceIds: Collection<String>, update: MediaSourceSave.() -> MediaSourceSave) {
        val ids = instanceIds.toSet()
        dataStore.updateData { current ->
            current.copy(
                instances = current.instances.map { save ->
                    if (save.instanceId in ids) {
                        save.update()
                    } else {
                        save
                    }
                },
            )
        }
    }

    override suspend fun partiallyReorder(newOrderInstanceIds: List<String>) {
        dataStore.updateData { current ->
            current.copy(instances = current.instances.partiallyReorderBy({ it.instanceId }, newOrderInstanceIds))
        }
    }
}

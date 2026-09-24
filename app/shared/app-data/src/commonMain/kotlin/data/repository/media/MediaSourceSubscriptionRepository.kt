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
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscription
import me.him188.ani.utils.platform.Uuid

class MediaSourceSubscriptionRepository(
    private val dataStore: DataStore<MediaSourceSubscriptionsSaveData>,
) : Repository() {
    val flow get() = dataStore.data.map { data ->
        data.list.filterNot { isInvalidSubscriptionUrl(it.url) }
    }

    suspend fun add(subscription: MediaSourceSubscription) {
        if (isInvalidSubscriptionUrl(subscription.url)) return
        dataStore.updateData { it.copy(list = it.list.filterNot { s -> isInvalidSubscriptionUrl(s.url) } + subscription) }
    }

    /**
     * 不会删除由该订阅添加的实例
     */
    suspend fun remove(subscription: MediaSourceSubscription) {
        dataStore.updateData {
            it.copy(
                list = it.list.filterNotTo(ArrayList(it.list.size)) {
                    it.subscriptionId == subscription.subscriptionId || isInvalidSubscriptionUrl(it.url)
                },
            )
        }
    }

    /**
     * @return whether a data with id [id] was found and updated. `false` means [id] not found.
     */
    suspend fun update(
        id: String,
        update: suspend (MediaSourceSubscription) -> MediaSourceSubscription
    ): Boolean {
        var found = false
        dataStore.updateData { data ->
            data.copy(
                list = data.list.mapNotNull { subscription ->
                    if (isInvalidSubscriptionUrl(subscription.url)) {
                        null
                    } else if (subscription.subscriptionId == id) {
                        found = true
                        val updated = update(subscription)
                        if (isInvalidSubscriptionUrl(updated.url)) null else updated
                    } else {
                        subscription
                    }
                },
            )
        }
        return found
    }

    companion object {
        fun isInvalidSubscriptionUrl(url: String): Boolean {
            val trimmed = url.trim().lowercase()
            return trimmed.endsWith(".json") || trimmed.contains("creamycake.org")
        }
    }
}

@Serializable
data class MediaSourceSubscriptionsSaveData(
    val list: List<MediaSourceSubscription>,
    private val version: Int,
) {
    init {
        require(version == CURRENT_VERSION) { "Version updated to $CURRENT_VERSION" }
    }

    companion object {
        /**
         * 更新后所有用户的配置的都会丢失
         */
        private const val CURRENT_VERSION = 1

        val Default = MediaSourceSubscriptionsSaveData(
            emptyList(),
            version = CURRENT_VERSION,
        )
    }
}

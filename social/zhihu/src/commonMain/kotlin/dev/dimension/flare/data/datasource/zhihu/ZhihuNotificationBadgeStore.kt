package dev.dimension.flare.data.datasource.zhihu

import dev.dimension.flare.data.datasource.microblog.NotificationFilter

/**
 * 知乎通知角标管理
 * 支持按分类（comment/follow/vote）清除角标
 * 对应 VVO 的 VVONotificationBadgeStore
 */
internal class ZhihuNotificationBadgeStore(
    private val loader: NotificationBadgeProvider,
    private val onTotalChanged: (Int) -> Unit,
) {
    private var serverCounts: Map<NotificationFilter, Int> = emptyCounts()
    private var hydrated: Boolean = false
    private val localOverrides = mutableMapOf<NotificationFilter, Int>()

    suspend fun refreshAndGetTotal(): Int {
        serverCounts = loader.notificationBadgeCounts()
        hydrated = true
        localOverrides.clear()
        return currentTotal().also(onTotalChanged)
    }

    suspend fun clear(filter: NotificationFilter) {
        ensureHydrated()
        localOverrides[filter] = 0
        onTotalChanged(currentTotal())
    }

    suspend fun clearAll() {
        ensureHydrated()
        trackedFilters.forEach { localOverrides[it] = 0 }
        onTotalChanged(currentTotal())
    }

    private fun currentTotal(): Int = trackedFilters.sumOf { effectiveCount(it) }

    private fun effectiveCount(filter: NotificationFilter): Int = localOverrides[filter] ?: serverCounts[filter] ?: 0

    private suspend fun ensureHydrated() {
        if (!hydrated) {
            serverCounts = loader.notificationBadgeCounts()
            hydrated = true
        }
    }

    private companion object {
        val trackedFilters = listOf(
            NotificationFilter.Comment,
            NotificationFilter.Like,
            NotificationFilter.Mention,
        )

        fun emptyCounts(): Map<NotificationFilter, Int> = trackedFilters.associateWith { 0 }
    }
}

/**
 * 通知角标数量提供者
 */
internal interface NotificationBadgeProvider {
    /**
     * 返回各分类的未读数量
     * 返回格式: Map<NotificationFilter, Int>
     * 知乎 API 提供: default_notifications_count(评论), follow_notifications_count(关注), vote_thank_notifications_count(赞)
     */
    suspend fun notificationBadgeCounts(): Map<NotificationFilter, Int>
}

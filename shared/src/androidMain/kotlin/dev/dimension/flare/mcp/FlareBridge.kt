package dev.dimension.flare.mcp

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationTimelineDataSource
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.model.takeSuccess
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Public bridge for accessing [AccountRepository] from the app module.
 * Injected via Koin's @Single; AccountRepository is internal but reachable
 * within the same Koin module.
 */
@Single
public class FlareBridge : KoinComponent {
    private val repo: AccountRepository by inject()

    public suspend fun getActiveDataSource(): MicroblogDataSource? {
        val account = repo.activeAccount.firstOrNull()?.takeSuccess() ?: return null
        return repo.getOrCreateDataSource(account)
    }

    public suspend fun getActiveAccount(): UiAccount? {
        return repo.activeAccount.firstOrNull()?.takeSuccess()
    }

    public suspend fun getAccounts(): List<UiAccount> {
        return repo.allAccounts.firstOrNull() ?: emptyList()
    }

    public fun setActiveAccount(key: MicroBlogKey) {
        repo.setActiveAccount(key)
    }

    public suspend fun loadHomeTimeline(count: Int): List<UiTimelineV2.TimelinePostItem> {
        val ds = getActiveDataSource() ?: return emptyList()
        val result = ds.homeTimeline().load(count, PagingRequest.Refresh)
        return result.data.mapNotNull { it.asTimelinePostItem() }
    }

    public suspend fun searchPosts(query: String, count: Int): List<UiTimelineV2.TimelinePostItem> {
        val ds = getActiveDataSource() ?: return emptyList()
        val result = ds.searchStatus(query).load(count, PagingRequest.Refresh)
        return result.data.mapNotNull { it.asTimelinePostItem() }
    }

    public suspend fun searchUsers(query: String, count: Int): List<dev.dimension.flare.ui.model.UiProfile> {
        val ds = getActiveDataSource() ?: return emptyList()
        val result = ds.searchUser(query).load(count, PagingRequest.Refresh)
        return result.data
    }

    public suspend fun getNotifications(count: Int): List<UiTimelineV2.TimelinePostItem> {
        val ds = getActiveDataSource() ?: return emptyList()
        if (ds !is NotificationTimelineDataSource) return emptyList()
        val result = ds.notification().load(count, PagingRequest.Refresh)
        return result.data.mapNotNull { it.asTimelinePostItem() }
    }

    public suspend fun getTrending(count: Int): List<UiTimelineV2.TimelinePostItem> {
        val ds = getActiveDataSource() ?: return emptyList()
        val result = ds.discoverStatuses().load(count, PagingRequest.Refresh)
        return result.data.mapNotNull { it.asTimelinePostItem() }
    }

    public suspend fun getUserTimeline(userId: String, userHost: String, count: Int): List<UiTimelineV2.TimelinePostItem> {
        val ds = getActiveDataSource() ?: return emptyList()
        val key = MicroBlogKey(id = userId, host = userHost)
        val result = ds.userTimeline(key).load(count, PagingRequest.Refresh)
        return result.data.mapNotNull { it.asTimelinePostItem() }
    }
}

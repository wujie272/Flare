package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber

public fun ActionMenu.Companion.zhihuVoteUp(
    statusKey: MicroBlogKey,
    voted: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "zhihu_voteup_$statusKey",
        icon = if (voted) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (voted) ActionMenu.Item.Text.Localized.Type.Unlike else ActionMenu.Item.Text.Localized.Type.Like,
            ),
        count = UiNumber(count),
        color = if (voted) ActionMenu.Item.Color.PrimaryColor else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.Zhihu.VoteUp(
                    postKey = statusKey,
                    voted = voted,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
        actionFamily = PostActionFamily.Like,
    )

public fun ActionMenu.Companion.zhihuReply(
    statusKey: MicroBlogKey,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "zhihu_reply_$statusKey",
        icon = UiIcon.Reply,
        text =
            ActionMenu.Item.Text.Localized(
                ActionMenu.Item.Text.Localized.Type.Reply,
            ),
        count = UiNumber(count),
        clickEvent =
            ClickEvent.Deeplink(
                dev.dimension.flare.ui.route.DeeplinkRoute.Compose.Reply(
                    accountKey = accountKey,
                    statusKey = statusKey,
                ),
            ),
        actionFamily = dev.dimension.flare.data.datasource.microblog.PostActionFamily.Reply,
    )

public fun ActionMenu.Companion.zhihuBookmark(
    statusKey: MicroBlogKey,
    bookmarked: Boolean,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "zhihu_bookmark_$statusKey",
        icon = if (bookmarked) UiIcon.Unbookmark else UiIcon.Bookmark,
        text =
            ActionMenu.Item.Text.Localized(
                if (bookmarked) ActionMenu.Item.Text.Localized.Type.Unbookmark else ActionMenu.Item.Text.Localized.Type.Bookmark,
            ),
        count = UiNumber(0),
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.Zhihu.Bookmark(
                    postKey = statusKey,
                    bookmarked = bookmarked,
                    accountKey = accountKey,
                ),
            ),
        actionFamily = PostActionFamily.Bookmark,
    )

package dev.dimension.flare.data.network.bilibili

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal data object BilibiliPlatformDetector : PlatformDetector {
    override val priority: Int = 90

    override suspend fun detect(host: String): NodeData? {
        if (host == "bilibili.com" || host == "www.bilibili.com" || host == "m.bilibili.com") {
            return NodeData(
                host = host,
                platformId = "Bilibili",
                software = "bilibili",
                compatibleMode = false,
            )
        }
        return null
    }
}

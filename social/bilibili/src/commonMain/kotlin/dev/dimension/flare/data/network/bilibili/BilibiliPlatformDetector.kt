package dev.dimension.flare.data.network.bilibili

import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal data object BilibiliPlatformDetector : PlatformDetector {
    override val priority: Int = 90

    override suspend fun detect(host: String): NodeDetection? {
        if (host == "bilibili.com" || host == "www.bilibili.com" || host == "m.bilibili.com") {
            return NodeDetection(
                host = host,
                software = "bilibili",
                compatibleMode = false,
            )
        }
        return null
    }
}

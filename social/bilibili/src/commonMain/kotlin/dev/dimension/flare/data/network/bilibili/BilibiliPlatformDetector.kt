package dev.dimension.flare.data.network.bilibili

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType

internal data object BilibiliPlatformDetector : PlatformDetector {
    override val priority: Int = 90

    override suspend fun detect(host: String): NodeData? {
        if (host == "bilibili.com" || host == "www.bilibili.com" || host == "m.bilibili.com") {
            return NodeData(
                host = host,
                platformType = PlatformType.Bilibili,
                software = "bilibili",
                compatibleMode = false,
            )
        }
        return null
    }
}

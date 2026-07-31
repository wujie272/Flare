package dev.dimension.flare.data.network.deviantart

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType

internal data object DeviantartPlatformDetector : PlatformDetector {
    override val priority: Int = 0

    override suspend fun detect(host: String): NodeData? {
        if (host == "deviantart.com" || host == "www.deviantart.com") {
            return NodeData(
                host = host,
                platformType = PlatformType.Deviantart,
                software = "deviantart",
                compatibleMode = false,
            )
        }
        return null
    }
}

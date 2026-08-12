package dev.dimension.flare.data.network.deviantart

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal data object DeviantartPlatformDetector : PlatformDetector {
    override val priority: Int = 0

    override suspend fun detect(host: String): NodeData? {
        if (host == "deviantart.com" || host == "www.deviantart.com") {
            return NodeData(
                host = host,
                platformId = "Deviantart",
                software = "deviantart",
                compatibleMode = false,
            )
        }
        return null
    }
}

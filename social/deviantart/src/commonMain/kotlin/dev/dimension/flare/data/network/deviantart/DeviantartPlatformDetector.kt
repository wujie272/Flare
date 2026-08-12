package dev.dimension.flare.data.network.deviantart

import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal data object DeviantartPlatformDetector : PlatformDetector {
    override val priority: Int = 0

    override suspend fun detect(host: String): NodeDetection? {
        if (host == "deviantart.com" || host == "www.deviantart.com") {
            return NodeDetection(
                host = host,
                software = "deviantart",
                compatibleMode = false,
            )
        }
        return null
    }
}

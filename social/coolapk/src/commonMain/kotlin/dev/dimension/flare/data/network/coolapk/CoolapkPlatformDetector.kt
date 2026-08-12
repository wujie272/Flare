package dev.dimension.flare.data.network.coolapk

import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal const val COOLAPK_HOST = "coolapk.com"

internal data object CoolapkPlatformDetector : PlatformDetector {
    override val priority: Int = 90

    override suspend fun detect(host: String): NodeData? {
        if (!COOLAPK_HOST.equals(host, ignoreCase = true) &&
            !"api.coolapk.com".equals(host, ignoreCase = true) &&
            !"account.coolapk.com".equals(host, ignoreCase = true)
        ) {
            return null
        }
        return NodeDetection(
            host = COOLAPK_HOST,
            platformId = "Coolapk",
            software = "Coolapk",
            compatibleMode = false,
        )
    }
}

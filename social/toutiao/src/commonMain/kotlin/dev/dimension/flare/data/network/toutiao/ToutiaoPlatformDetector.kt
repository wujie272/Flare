package dev.dimension.flare.data.network.toutiao

import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal const val TOUTIAO_HOST = "www.toutiao.com"

internal data object ToutiaoPlatformDetector : PlatformDetector {
    override val priority: Int = 90
    override suspend fun detect(host: String): NodeData? {
        if (!TOUTIAO_HOST.equals(host, ignoreCase = true) && !"toutiao.com".equals(host, ignoreCase = true)) {
            return null
        }
        return NodeDetection(
            host = TOUTIAO_HOST,
            platformId = "Toutiao",
            software = "Toutiao",
            compatibleMode = false,
        )
    }
}

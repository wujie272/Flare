package dev.dimension.flare.data.network.coolapk

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType

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
        return NodeData(
            host = COOLAPK_HOST,
            platformType = PlatformType.Coolapk,
            software = PlatformType.Coolapk.name,
            compatibleMode = false,
        )
    }
}

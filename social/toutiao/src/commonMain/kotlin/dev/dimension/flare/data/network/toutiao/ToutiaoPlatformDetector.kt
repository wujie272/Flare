package dev.dimension.flare.data.network.toutiao

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType

internal const val TOUTIAO_HOST = "www.toutiao.com"

internal data object ToutiaoPlatformDetector : PlatformDetector {
    override val priority: Int = 50
    override suspend fun detect(host: String): NodeData? {
        if (!TOUTIAO_HOST.equals(host, ignoreCase = true) && !"toutiao.com".equals(host, ignoreCase = true)) {
            return null
        }
        return NodeData(
            host = TOUTIAO_HOST,
            platformType = PlatformType.Toutiao,
            software = PlatformType.Toutiao.name,
            compatibleMode = false,
        )
    }
}

package dev.dimension.flare.data.network.zhihu

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType

internal const val ZHIHU_HOST = "www.zhihu.com"

internal data object ZhihuPlatformDetector : PlatformDetector {
    override val priority: Int = 50
    override suspend fun detect(host: String): NodeData? {
        if (!ZHIHU_HOST.equals(host, ignoreCase = true) &&
            !"zhihu.com".equals(host, ignoreCase = true) &&
            !"zhuanlan.zhihu.com".equals(host, ignoreCase = true)
        ) {
            return null
        }
        return NodeData(
            host = ZHIHU_HOST,
            platformType = PlatformType.Zhihu,
            software = PlatformType.Zhihu.name,
            compatibleMode = false,
        )
    }
}

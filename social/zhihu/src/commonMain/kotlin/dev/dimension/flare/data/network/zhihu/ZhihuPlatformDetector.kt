package dev.dimension.flare.data.network.zhihu

import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector

internal const val ZHIHU_HOST = "www.zhihu.com"

internal data object ZhihuPlatformDetector : PlatformDetector {
    override val priority: Int = 90
    override suspend fun detect(host: String): NodeDetection? {
        if (!ZHIHU_HOST.equals(host, ignoreCase = true) &&
            !"zhihu.com".equals(host, ignoreCase = true) &&
            !"zhuanlan.zhihu.com".equals(host, ignoreCase = true)
        ) {
            return null
        }
        return NodeDetection(
            host = ZHIHU_HOST,
            software = "Zhihu",
            compatibleMode = false,
        )
    }
}

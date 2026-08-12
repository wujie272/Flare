package dev.dimension.flare.data.network.cbart

import dev.dimension.flare.ui.presenter.login.NodeDetection
import dev.dimension.flare.ui.presenter.login.PlatformDetector
import dev.dimension.flare.data.platform.CBART_HOST

internal data object CbartPlatformDetector : PlatformDetector {
    override val priority: Int = 90
    override suspend fun detect(host: String): NodeDetection? {
        // 妖狐吧域名：shenmatk.com, smlinzi.com, ngsbyc.com, hjtsdhao.com, yaohuba.com
        val yaohubaHosts = listOf("shenmatk.com", "smlinzi.com", "ngsbyc.com", "hjtsdhao.com", "yaohuba.com")
        val cleanHost = host.removePrefix("www.")
        if (cleanHost in yaohubaHosts || cleanHost == CBART_HOST) {
            return NodeDetection(host = CBART_HOST, software = "Cbart", compatibleMode = false)
        }
        return null
    }
}

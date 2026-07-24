package dev.dimension.flare.data.network.cbart

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.CBART_HOST
import dev.dimension.flare.model.PlatformType

internal data object CbartPlatformDetector : PlatformDetector {
    override val priority: Int = 90
    override suspend fun detect(host: String): NodeData? {
        // 妖狐吧域名：shenmatk.com, smlinzi.com, ngsbyc.com, hjtsdhao.com, yaohuba.com
        val yaohubaHosts = listOf("shenmatk.com", "smlinzi.com", "ngsbyc.com", "hjtsdhao.com", "yaohuba.com")
        val cleanHost = host.removePrefix("www.")
        if (cleanHost in yaohubaHosts || cleanHost == CBART_HOST) {
            return NodeData(host = CBART_HOST, platformType = PlatformType.Cbart, software = PlatformType.Cbart.name, compatibleMode = false)
        }
        return null
    }
}

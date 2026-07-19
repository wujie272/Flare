package dev.dimension.flare.data.network.cbart

import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.CBART_HOST
import dev.dimension.flare.model.PlatformType

internal data object CbartPlatformDetector : PlatformDetector {
    override val priority: Int = 90
    override suspend fun detect(host: String): NodeData? {
        if (!CBART_HOST.equals(host, ignoreCase = true) && !"www.$CBART_HOST".equals(host, ignoreCase = true)) return null
        return NodeData(host = CBART_HOST, platformType = PlatformType.Cbart, software = PlatformType.Cbart.name, compatibleMode = false)
    }
}

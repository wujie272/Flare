package dev.dimension.flare.data.network.cbart.platform

import platform.UIKit.UIDevice

internal actual fun getDeviceName(): String {
    val model = UIDevice.currentDevice.model
    return "$model-ios"
}

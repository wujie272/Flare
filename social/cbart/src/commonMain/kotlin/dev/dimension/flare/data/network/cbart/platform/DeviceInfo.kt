package dev.dimension.flare.data.network.cbart.platform

/**
 * 获取设备名称（用于 API 请求的 name 字段）
 * 格式：model-platform（如 "K80 Pro-android"）
 * 与 lzj.apk 的 device.model + "-" + device.platform 一致
 */
internal expect fun getDeviceName(): String

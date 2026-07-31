package dev.dimension.flare.data.network.bilibili

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.sin
import kotlin.time.Clock

/**
 * WBI 签名 + APP sign 实现
 */

// ==================== WBI 签名 ====================

/** WBI 密钥置换表（B站写死的） */
private val MIXIN_KEY_ENC_TAB: List<Int> = listOf(
    46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
    33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
    61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
    36, 20, 34, 44, 52,
)

/** 通过置换表生成 mixin key */
private fun getMixinKey(orig: String): String {
    val sb = StringBuilder()
    for (i in MIXIN_KEY_ENC_TAB) {
        if (i < orig.length) sb.append(orig[i])
    }
    return sb.toString().take(32)
}

/** 过滤非法字符（B站要求移除 !'()*） */
private fun filterIllegalChars(value: String): String =
    value.replace(Regex("[!'()*]"), "")

/** URL 编码（仅用于签名计算，不用于实际请求） */
private fun encodeURIComponent(value: String): String {
    val sb = StringBuilder()
    for (char in value) {
        when (char) {
            in 'a'..'z', in 'A'..'Z', in '0'..'9', '-', '_', '.', '~' -> sb.append(char)
            ' ' -> sb.append("%20")
            else -> sb.append(char.toString().encodeToByteArray().joinToString("") { "%${it.toUByte().toString(16).uppercase()}" })
        }
    }
    return sb.toString()
}

/**
 * WBI 签名
 * @param params 请求参数
 * @param imgKey nav 接口返回的 img_key
 * @param subKey nav 接口返回的 sub_key
 * @return 添加了 w_rid 和 wts 的参数 Map
 */
internal fun signWbi(
    params: Map<String, String>,
    imgKey: String,
    subKey: String,
): Map<String, String> {
    val mixinKey = getMixinKey(imgKey + subKey)
    val wts = Clock.System.now().epochSeconds.toString()

    // 1. 添加 wts，过滤非法字符
    val rawParams = mutableMapOf<String, String>()
    for ((key, value) in params) {
        rawParams[key] = filterIllegalChars(value)
    }
    rawParams["wts"] = wts

    // 2. 排序 key
    val sortedKeys = rawParams.keys.sorted()

    // 3. 拼接 key=encodedValue&...
    val query = sortedKeys.joinToString("&") { key ->
        "${key}=${encodeURIComponent(rawParams[key]!!)}"
    }

    // 4. MD5( query + mixinKey ) → w_rid
    val wRid = md5Hex(query + mixinKey)

    rawParams["w_rid"] = wRid
    return rawParams
}

// ==================== APP sign ====================

/** APP key 和 secret（公开的秘密） */
internal object BilibiliAppKeys {
    /** TV 端（云视听小电视） */
    internal const val TV_APP_KEY = "4409e2ce8ffd12b8"
    internal const val TV_APP_SEC = "59b43e04ad6965f34319062b478f83dd"

    /** Android 端 */
    internal const val ANDROID_APP_KEY = "1d8b6e7d45233436"
    internal const val ANDROID_APP_SEC = "560c52ccd288fed045859ed18bffd973"

    /** Android HD 端 */
    internal const val ANDROID_HD_APP_KEY = "dfca71928277209b"
    internal const val ANDROID_HD_APP_SEC = "b5475a8825547a4fc26c7d518eaaa02e"
}

/**
 * APP 签名
 * 算法: MD5(排序参数 + app_secret)
 */
internal fun signApp(
    params: Map<String, String>,
    appSecret: String = BilibiliAppKeys.ANDROID_APP_SEC,
): Map<String, String> {
    val sortedParams = params.toSortedMap()
    val query = sortedParams.entries.joinToString("&") { "${it.key}=${it.value}" }
    val sign = md5Hex(query + appSecret)
    return sortedParams + ("sign" to sign)
}

// ==================== MD5 实现（纯 Kotlin，KMP 通用）====================

internal fun md5Hex(input: String): String = md5Hex(input.encodeToByteArray())

internal fun md5Hex(message: ByteArray): String {
    // 标准 MD5 算法
    val a0: Int = 0x67452301
    val b0: Int = -0x10325477
    val c0: Int = 0x98BADCFE.toInt()
    val d0: Int = 0x10325476

    // 补位
    val origLenBits = message.size.toLong() * 8
    val padded = message.toMutableList()
    padded.add(0x80.toByte())
    while ((padded.size % 64) != 56) {
        padded.add(0x00)
    }
    // 追加原始长度（64位小端）
    for (i in 0..7) {
        padded.add((origLenBits shr (i * 8)).toByte())
    }

    fun F(x: Int, y: Int, z: Int) = (x and y) or (x.inv() and z)
    fun G(x: Int, y: Int, z: Int) = (x and z) or (y and z.inv())
    fun H(x: Int, y: Int, z: Int) = x xor y xor z
    fun I(x: Int, y: Int, z: Int) = y xor (x or z.inv())

    fun leftRotate(x: Int, c: Int): Int = (x shl c) or (x ushr (32 - c))

    val S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    val K = IntArray(64) { i ->
        (abs(sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt()
    }

    var a = a0
    var b = b0
    var c = c0
    var d = d0

    // 处理每个 512-bit 块
    for (chunkStart in padded.indices step 64) {
        val chunk = padded.subList(chunkStart, chunkStart + 64)
        val M = IntArray(16) { i ->
            (chunk[i * 4].toInt() and 0xFF) or
                ((chunk[i * 4 + 1].toInt() and 0xFF) shl 8) or
                ((chunk[i * 4 + 2].toInt() and 0xFF) shl 16) or
                ((chunk[i * 4 + 3].toInt() and 0xFF) shl 24)
        }

        var AA = a
        var BB = b
        var CC = c
        var DD = d

        for (i in 0..63) {
            val (f, g) = when (i) {
                in 0..15 -> F(BB, CC, DD) to i
                in 16..31 -> G(BB, CC, DD) to ((5 * i + 1) % 16)
                in 32..47 -> H(BB, CC, DD) to ((3 * i + 5) % 16)
                else -> I(BB, CC, DD) to ((7 * i) % 16)
            }
            val temp = DD
            DD = CC
            CC = BB
            BB = BB + leftRotate(AA + f + K[i] + M[g], S[i])
            AA = temp
        }
        a += AA
        b += BB
        c += CC
        d += DD
    }

    fun intToLittleEndianHex(n: Int): String =
        buildString {
            for (i in 0..3) {
                append(((n shr (i * 8)) and 0xFF).toUByte().toString(16).padStart(2, '0'))
            }
        }

    return intToLittleEndianHex(a) + intToLittleEndianHex(b) +
        intToLittleEndianHex(c) + intToLittleEndianHex(d)
}

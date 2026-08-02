package dev.dimension.flare.data.network.coolapk

import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Clock

internal object CoolapkAuthUtil {
    private const val ANDROID_ID = "2ac8610820fceba"
    private const val MANUFACTURER = "Xiaomi"
    private const val BRAND = "Xiaomi"
    private const val MODEL = "24122RKC7C"
    private const val BUILD = "BP2A.250605.031.A3"
    private const val APP_ID = "com.coolapk.market"
    private const val APP_VERSION = "11.2"
    private const val APP_CODE = "2105201"
    private const val API_VERSION = "11"
    private const val SDK_INT = "30"
    private const val TOKEN_SECRET = "c67ef5943784d09750dcfbb31020f0ab"
    private const val USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 10; WRT-WX9) (#Build; HUAWEI; WRT-WX9; 10.0) +CoolMarket/11.2-2105201-universal"

    private fun md5(input: String): String {
        val bytes = input.encodeToByteArray()
        val bitLength = bytes.size.toLong() * 8
        val paddedLength = (((bytes.size + 8) / 64) + 1) * 64
        val padded = ByteArray(paddedLength)
        bytes.copyInto(padded)
        padded[bytes.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[paddedLength - 8 + i] = (bitLength ushr (8 * i)).toByte()
        }
        var a0 = 0x67452301
        var b0 = 0xefcdab89.toInt()
        var c0 = 0x98badcfe.toInt()
        var d0 = 0x10325476
        val words = IntArray(16)
        var chunkOffset = 0
        while (chunkOffset < padded.size) {
            for (i in 0 until 16) {
                val offset = chunkOffset + i * 4
                words[i] = (padded[offset].toInt() and 0xff) or
                    ((padded[offset + 1].toInt() and 0xff) shl 8) or
                    ((padded[offset + 2].toInt() and 0xff) shl 16) or
                    ((padded[offset + 3].toInt() and 0xff) shl 24)
            }
            var a = a0
            var b = b0
            var c = c0
            var d = d0
            for (i in 0 until 64) {
                val f: Int
                val g: Int
                when (i) {
                    in 0..15 -> {
                        f = (b and c) or (b.inv() and d)
                        g = i
                    }
                    in 16..31 -> {
                        f = (d and b) or (d.inv() and c)
                        g = (5 * i + 1) % 16
                    }
                    in 32..47 -> {
                        f = b xor c xor d
                        g = (3 * i + 5) % 16
                    }
                    else -> {
                        f = c xor (b or d.inv())
                        g = (7 * i) % 16
                    }
                }
                val nextD = c
                c = b
                b += (a + f + MD5_K[i] + words[g]).rotateLeft(MD5_S[i])
                a = d
                d = nextD
            }
            a0 += a
            b0 += b
            c0 += c
            d0 += d
            chunkOffset += 64
        }
        return buildString(32) {
            appendLittleEndianHex(a0)
            appendLittleEndianHex(b0)
            appendLittleEndianHex(c0)
            appendLittleEndianHex(d0)
        }
    }

    private fun StringBuilder.appendLittleEndianHex(value: Int) {
        for (i in 0 until 4) {
            val byte = (value ushr (8 * i)) and 0xff
            append(HEX[byte ushr 4])
            append(HEX[byte and 0x0f])
        }
    }

    private fun b64(input: String): String {
        val bytes = input.encodeToByteArray()
        return Base64.encode(bytes).trimEnd('=')
    }

    private fun randomMac(): String = List(6) { "%02x".format(Random.nextInt(256)) }.joinToString(":")

    fun generateDeviceCode(): String {
        val mac = randomMac()
        val raw = "$ANDROID_ID;;;$mac;$MANUFACTURER;$BRAND;$MODEL;$BUILD;null"
        return b64(raw).reversed()
    }

    fun generateAppToken(deviceCode: String): String {
        val ts = (Clock.System.now().toEpochMilliseconds() / 1000).toString()
        val md5Ts = md5(ts)
        val md5Dc = md5(deviceCode)
        val tokenStr = "token://$APP_ID/$TOKEN_SECRET?$md5Ts${'$'}$md5Dc&$APP_ID"
        val bt = b64(tokenStr)
        val mt = md5(bt)
        val hexTs = "0x${ts.toLong().toString(16)}"
        return mt + md5Dc + hexTs
    }

    fun buildHeaders(
        deviceCode: String,
        appToken: String,
    ): Map<String, String> =
        mapOf(
            "User-Agent" to USER_AGENT,
            "X-Requested-With" to "XMLHttpRequest",
            "X-Sdk-Int" to SDK_INT,
            "X-Sdk-Locale" to "zh-CN",
            "X-App-Id" to APP_ID,
            "X-App-Version" to APP_VERSION,
            "X-App-Code" to APP_CODE,
            "X-Api-Version" to API_VERSION,
            "X-App-Device" to deviceCode,
            "X-App-Token" to appToken,
        )

    fun buildUserAgent(): String = USER_AGENT

    private val HEX = "0123456789abcdef".toCharArray()

    private val MD5_S =
        intArrayOf(
            7,
            12,
            17,
            22,
            7,
            12,
            17,
            22,
            7,
            12,
            17,
            22,
            7,
            12,
            17,
            22,
            5,
            9,
            14,
            20,
            5,
            9,
            14,
            20,
            5,
            9,
            14,
            20,
            5,
            9,
            14,
            20,
            4,
            11,
            16,
            23,
            4,
            11,
            16,
            23,
            4,
            11,
            16,
            23,
            4,
            11,
            16,
            23,
            6,
            10,
            15,
            21,
            6,
            10,
            15,
            21,
            6,
            10,
            15,
            21,
            6,
            10,
            15,
            21,
        )

    private val MD5_K =
        IntArray(64) { index ->
            floor(abs(sin((index + 1).toDouble())) * 4_294_967_296.0).toLong().toInt()
        }
}

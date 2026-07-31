@file:OptIn(ExperimentalUnsignedTypes::class)

package dev.dimension.flare.data.network.zhihu

/**
 * 知乎 zse-96 签名实现
 * 
 * 从 zhihu-plus-plus 移植的纯 Kotlin SM4-like 加密实现。
 * 用于生成 x-zse-96 请求头，知乎 API 风控校验。
 */
internal object ZhihuZseSigner {
    private val ZK = uintArrayOf(
        1170614578u, 1024848638u, 1413669199u, 3951632832u, 3528873006u, 2921909214u, 4151847688u, 3997739139u,
        1933479194u, 3323781115u, 3888513386u, 460404854u, 3747539722u, 2403641034u, 2615871395u, 2119585428u,
        2265697227u, 2035090028u, 2773447226u, 4289380121u, 4217216195u, 2200601443u, 3051914490u, 1579901135u,
        1321810770u, 456816404u, 2903323407u, 4065664991u, 330002838u, 3506006750u, 363569021u, 2347096187u,
    )

    private val ZB = intArrayOf(
        20, 223, 245, 7, 248, 2, 194, 209, 87, 6, 227, 253, 240, 128, 222, 91, 237, 9, 125, 157, 230,
        93, 252, 205, 90, 79, 144, 199, 159, 197, 186, 167, 39, 37, 156, 198, 38, 42, 43, 168, 217,
        153, 15, 103, 80, 189, 71, 191, 97, 84, 247, 95, 36, 69, 14, 35, 12, 171, 28, 114, 178, 148,
        86, 182, 32, 83, 158, 109, 22, 255, 94, 238, 151, 85, 77, 124, 254, 18, 4, 26, 123, 176, 232,
        193, 131, 172, 143, 142, 150, 30, 10, 146, 162, 62, 224, 218, 196, 229, 1, 192, 213, 27, 110,
        56, 231, 180, 138, 107, 242, 187, 54, 120, 19, 44, 117, 228, 215, 203, 53, 239, 251, 127, 81,
        11, 133, 96, 204, 132, 41, 115, 73, 55, 249, 147, 102, 48, 122, 145, 106, 118, 74, 190, 29, 16,
        174, 5, 177, 129, 63, 113, 99, 31, 161, 76, 246, 34, 211, 13, 60, 68, 207, 160, 65, 111, 82,
        165, 67, 169, 225, 57, 112, 244, 155, 51, 236, 200, 233, 58, 61, 47, 100, 137, 185, 64, 17, 70,
        234, 163, 219, 108, 170, 166, 59, 149, 52, 105, 24, 212, 78, 173, 45, 0, 116, 226, 119, 136,
        206, 135, 175, 195, 25, 92, 121, 208, 126, 139, 3, 75, 141, 21, 130, 98, 241, 40, 154, 66, 184,
        49, 181, 46, 243, 88, 101, 183, 8, 23, 72, 188, 104, 179, 210, 134, 250, 201, 164, 89, 216,
        202, 220, 50, 221, 152, 140, 33, 235, 214,
    )

    private const val ALPHABET = "6fpLRqJO8M/c3jnYxFkUVC4ZIG12SiH=5v0mXDazWBTsuw7QetbKdoPyAl+hN9rgE"
    private val KEY16 = "059053f7d15e01d7".encodeToByteArray()
    private val HEX = "0123456789ABCDEF".toCharArray()

    private fun readU32Be(b: ByteArray, off: Int): Int = ((b[off].toInt() and 0xFF) shl 24) or
        ((b[off + 1].toInt() and 0xFF) shl 16) or
        ((b[off + 2].toInt() and 0xFF) shl 8) or
        (b[off + 3].toInt() and 0xFF)

    private fun writeU32Be(v: Int, out: ByteArray, off: Int) {
        out[off] = (v ushr 24).toByte()
        out[off + 1] = (v ushr 16).toByte()
        out[off + 2] = (v ushr 8).toByte()
        out[off + 3] = v.toByte()
    }

    private fun gTransform(tt: Int): Int {
        val te0 = tt ushr 24 and 0xFF
        val te1 = tt ushr 16 and 0xFF
        val te2 = tt ushr 8 and 0xFF
        val te3 = tt and 0xFF
        val ti = ((ZB[te0] and 0xFF) shl 24) or
            ((ZB[te1] and 0xFF) shl 16) or
            ((ZB[te2] and 0xFF) shl 8) or
            (ZB[te3] and 0xFF)
        return ti xor (ti).rotateLeft(2) xor (ti).rotateLeft(10) xor
            (ti).rotateLeft(18) xor (ti).rotateLeft(24)
    }

    private fun rBlock(input16: ByteArray): ByteArray {
        val tr = IntArray(36)
        tr[0] = readU32Be(input16, 0)
        tr[1] = readU32Be(input16, 4)
        tr[2] = readU32Be(input16, 8)
        tr[3] = readU32Be(input16, 12)
        for (i in 0 until 32) {
            val ta = gTransform(tr[i + 1] xor tr[i + 2] xor tr[i + 3] xor ZK[i].toInt())
            tr[i + 4] = tr[i] xor ta
        }
        return ByteArray(16).also {
            writeU32Be(tr[35], it, 0)
            writeU32Be(tr[34], it, 4)
            writeU32Be(tr[33], it, 8)
            writeU32Be(tr[32], it, 12)
        }
    }

    private fun xBlocks(data: ByteArray, iv0: ByteArray): ByteArray {
        var iv = iv0
        val out = ByteArray(data.size)
        var outOff = 0
        var off = 0
        while (off < data.size) {
            val mixed = ByteArray(16)
            for (i in 0 until 16) {
                mixed[i] = (data[off + i].toInt() xor iv[i].toInt()).toByte()
            }
            iv = rBlock(mixed)
            iv.copyInto(out, outOff, 0, 16)
            off += 16
            outOff += 16
        }
        return out
    }

    private fun customEncode(bytesIn: ByteArray): String {
        var bytes = bytesIn
        val rem = bytes.size % 3
        if (rem != 0) {
            bytes += ByteArray(3 - rem)
        }
        val out = StringBuilder((bytes.size / 3) * 4)
        var i = 0
        var p = bytes.size - 1
        while (p >= 0) {
            var v = 0
            val b0 = bytes[p].toInt() and 0xFF
            val m0 = 58 ushr (8 * (i % 4)) and 0xFF
            i += 1
            v = v or ((b0 xor m0) and 0xFF)
            val b1 = bytes[p - 1].toInt() and 0xFF
            val m1 = 58 ushr (8 * (i % 4)) and 0xFF
            i += 1
            v = v or (((b1 xor m1) and 0xFF) shl 8)
            val b2 = bytes[p - 2].toInt() and 0xFF
            val m2 = 58 ushr (8 * (i % 4)) and 0xFF
            i += 1
            v = v or (((b2 xor m2) and 0xFF) shl 16)
            out.append(ALPHABET[v and 63])
            out.append(ALPHABET[(v ushr 6) and 63])
            out.append(ALPHABET[(v ushr 12) and 63])
            out.append(ALPHABET[(v ushr 18) and 63])
            p -= 3
        }
        return out.toString()
    }

    fun encryptZseV4(input: String): String {
        val plain = ArrayList<Byte>(input.length + 32)
        plain += 210.toByte()
        plain += 0.toByte()
        plain.addAll(input.encodeToByteArray().asList())
        val pad = 16 - (plain.size % 16)
        repeat(pad) { plain += pad.toByte() }
        val plainBytes = plain.toByteArray()
        val first = ByteArray(16)
        for (i in 0 until 16) {
            first[i] = (plainBytes[i].toInt() xor KEY16[i].toInt() xor 42).toByte()
        }
        val c0 = rBlock(first)
        val cipher = ByteArray(plainBytes.size)
        c0.copyInto(cipher, 0, 0, 16)
        if (plainBytes.size > 16) {
            val rest = xBlocks(plainBytes.copyOfRange(16, plainBytes.size), c0)
            rest.copyInto(cipher, 16, 0, rest.size)
        }
        return customEncode(cipher)
    }
}

internal fun md5Hex(input: String): String = md5Hex(input.encodeToByteArray())

internal fun md5Hex(message: ByteArray): String {
    val bitLength = message.size.toLong() * 8
    val paddedLength = (((message.size + 8) / 64) + 1) * 64
    val padded = ByteArray(paddedLength)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
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
        var a = a0; var b = b0; var c = c0; var d = d0
        for (i in 0 until 64) {
            val f: Int; val g: Int
            when (i) {
                in 0..15 -> { f = (b and c) or (b.inv() and d); g = i }
                in 16..31 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                in 32..47 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
            }
            val nextD = c; c = b
            b += (a + f + MD5_K[i] + words[g]).rotateLeft(MD5_S[i])
            a = d; d = nextD
        }
        a0 += a; b0 += b; c0 += c; d0 += d; chunkOffset += 64
    }
    return buildString(32) {
        appendLittleEndianHex(a0); appendLittleEndianHex(b0)
        appendLittleEndianHex(c0); appendLittleEndianHex(d0)
    }
}

private fun StringBuilder.appendLittleEndianHex(value: Int) {
    for (i in 0 until 4) {
        val byte = (value ushr (8 * i)) and 0xff
        append(HEX[byte ushr 4]); append(HEX[byte and 0x0f])
    }
}

private val HEX = "0123456789abcdef".toCharArray()

private val MD5_S = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
)

private val MD5_K = IntArray(64) { index ->
    kotlin.math.floor(kotlin.math.abs(kotlin.math.sin((index + 1).toDouble())) * 4_294_967_296.0).toLong().toInt()
}

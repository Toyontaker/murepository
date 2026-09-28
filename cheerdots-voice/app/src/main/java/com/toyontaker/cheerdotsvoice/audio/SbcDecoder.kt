package com.toyontaker.cheerdotsvoice.audio

/**
 * SBC (Bluetooth low-complexity subband codec) decoder.
 *
 * A straight Kotlin port of the fixed-point decoder in BlueZ libsbc
 * (sbc.c / sbc_tables.h, LGPL-2.1-or-later), so its output is bit-exact
 * with the reference implementation. Only decoding is implemented.
 */
class SbcDecoder {

    class DecodeException(message: String) : Exception(message)

    /** Result of decoding one frame. */
    class Frame(val sampleRate: Int, val channels: Int, val bytesConsumed: Int, val pcm: ShortArray)

    private val v = Array(2) { IntArray(170) }
    private val offset = Array(2) { IntArray(16) }
    private var initializedSubbands = 0

    // Per-frame scratch state.
    private val scaleFactor = Array(2) { IntArray(8) }
    private val sbSample = Array(16) { Array(2) { IntArray(8) } }
    private val bits = Array(2) { IntArray(8) }

    fun reset() {
        initializedSubbands = 0
    }

    /**
     * Decodes the SBC frame at the start of [data]. PCM for multiple channels is
     * returned interleaved.
     */
    fun decode(data: ByteArray, off: Int = 0, len: Int = data.size - off): Frame {
        if (len < 4) throw DecodeException("frame too short")
        fun u8(i: Int) = data[off + i].toInt() and 0xFF
        if (u8(0) != SYNCWORD) throw DecodeException("bad syncword")

        val frequency = (u8(1) shr 6) and 0x03
        val blocks = 4 * (((u8(1) shr 4) and 0x03) + 1)
        val mode = (u8(1) shr 2) and 0x03
        val channels = if (mode == MODE_MONO) 1 else 2
        val allocation = (u8(1) shr 1) and 0x01
        val subbands = if (u8(1) and 0x01 != 0) 8 else 4
        val bitpool = u8(2)

        if ((mode == MODE_MONO || mode == MODE_DUAL) && bitpool > 16 * subbands) throw DecodeException("bitpool out of range")
        if ((mode == MODE_STEREO || mode == MODE_JOINT) && bitpool > 32 * subbands) throw DecodeException("bitpool out of range")

        var consumed = 32
        val crcHeader = IntArray(11)
        crcHeader[0] = u8(1)
        crcHeader[1] = u8(2)
        var crcPos = 16

        var joint = 0
        if (mode == MODE_JOINT) {
            if (len * 8 < consumed + subbands) throw DecodeException("frame too short")
            for (sb in 0 until subbands - 1) joint = joint or (((u8(4) shr (7 - sb)) and 0x01) shl sb)
            crcHeader[crcPos / 8] = if (subbands == 4) u8(4) and 0xF0 else u8(4)
            consumed += subbands
            crcPos += subbands
        }

        if (len * 8 < consumed + 4 * subbands * channels) throw DecodeException("frame too short")

        for (ch in 0 until channels) {
            for (sb in 0 until subbands) {
                val sf = (u8(consumed shr 3) shr (4 - (consumed and 0x7))) and 0x0F
                scaleFactor[ch][sb] = sf
                crcHeader[crcPos shr 3] = crcHeader[crcPos shr 3] or (sf shl (4 - (crcPos and 0x7)))
                consumed += 4
                crcPos += 4
            }
        }

        if (u8(3) != crc8(crcHeader, crcPos)) throw DecodeException("CRC mismatch")

        calculateBits(frequency, mode, channels, allocation, subbands, bitpool)

        for (blk in 0 until blocks) {
            for (ch in 0 until channels) {
                for (sb in 0 until subbands) {
                    val nbits = bits[ch][sb]
                    val levels = (1L shl nbits) - 1
                    if (levels == 0L) {
                        sbSample[blk][ch][sb] = 0
                        continue
                    }
                    val shift = scaleFactor[ch][sb] + 1 + FIXED_EXTRA_BITS
                    var audioSample = 0L
                    for (bit in 0 until nbits) {
                        if (consumed > len * 8) throw DecodeException("frame too short")
                        // libsbc may read the byte at the end boundary; treat it as zero.
                        val byteIndex = consumed shr 3
                        val b = if (byteIndex < len) u8(byteIndex) else 0
                        if ((b shr (7 - (consumed and 0x7))) and 0x01 != 0) {
                            audioSample = audioSample or (1L shl (nbits - bit - 1))
                        }
                        consumed++
                    }
                    sbSample[blk][ch][sb] =
                        ((((audioSample shl 1) or 1L) shl shift) / levels - (1L shl shift)).toInt()
                }
            }
        }

        if (mode == MODE_JOINT) {
            for (blk in 0 until blocks) {
                for (sb in 0 until subbands) {
                    if (joint and (1 shl sb) != 0) {
                        val temp = sbSample[blk][0][sb] + sbSample[blk][1][sb]
                        sbSample[blk][1][sb] = sbSample[blk][0][sb] - sbSample[blk][1][sb]
                        sbSample[blk][0][sb] = temp
                    }
                }
            }
        }

        if (consumed and 0x7 != 0) consumed += 8 - (consumed and 0x7)

        if (initializedSubbands != subbands) initState(subbands)

        val samplesPerChannel = blocks * subbands
        val pcm = ShortArray(samplesPerChannel * channels)
        for (ch in 0 until channels) {
            for (blk in 0 until blocks) {
                if (subbands == 4) synthesizeFour(ch, blk, pcm, channels) else synthesizeEight(ch, blk, pcm, channels)
            }
        }

        return Frame(FREQUENCIES[frequency], channels, consumed shr 3, pcm)
    }

    private fun initState(subbands: Int) {
        for (ch in 0 until 2) {
            v[ch].fill(0)
            for (i in 0 until subbands * 2) offset[ch][i] = 10 * i + 10
        }
        initializedSubbands = subbands
    }

    private fun synthesizeFour(ch: Int, blk: Int, pcm: ShortArray, channels: Int) {
        val vv = v[ch]
        val off = offset[ch]
        val s = sbSample[blk][ch]
        for (i in 0 until 8) {
            off[i]--
            if (off[i] < 0) {
                off[i] = 79
                System.arraycopy(vv, 0, vv, 80, 9)
            }
            val m = i * 4
            vv[off[i]] = (synMatrix4[m] * s[0] + synMatrix4[m + 1] * s[1] +
                synMatrix4[m + 2] * s[2] + synMatrix4[m + 3] * s[3]) shr SCALE4_STAGED1_BITS
        }
        var idx = 0
        for (i in 0 until 4) {
            val k = (i + 4) and 0xF
            val oi = off[i]
            val ok = off[k]
            val acc = vv[oi] * proto4m0[idx] + vv[ok + 1] * proto4m1[idx] +
                vv[oi + 2] * proto4m0[idx + 1] + vv[ok + 3] * proto4m1[idx + 1] +
                vv[oi + 4] * proto4m0[idx + 2] + vv[ok + 5] * proto4m1[idx + 2] +
                vv[oi + 6] * proto4m0[idx + 3] + vv[ok + 7] * proto4m1[idx + 3] +
                vv[oi + 8] * proto4m0[idx + 4] + vv[ok + 9] * proto4m1[idx + 4]
            pcm[(blk * 4 + i) * channels + ch] = clip16(acc shr SCALE4_STAGED1_BITS)
            idx += 5
        }
    }

    private fun synthesizeEight(ch: Int, blk: Int, pcm: ShortArray, channels: Int) {
        val vv = v[ch]
        val off = offset[ch]
        val s = sbSample[blk][ch]
        for (i in 0 until 16) {
            off[i]--
            if (off[i] < 0) {
                off[i] = 159
                System.arraycopy(vv, 0, vv, 160, 9)
            }
            val m = i * 8
            vv[off[i]] = (synMatrix8[m] * s[0] + synMatrix8[m + 1] * s[1] +
                synMatrix8[m + 2] * s[2] + synMatrix8[m + 3] * s[3] +
                synMatrix8[m + 4] * s[4] + synMatrix8[m + 5] * s[5] +
                synMatrix8[m + 6] * s[6] + synMatrix8[m + 7] * s[7]) shr SCALE8_STAGED1_BITS
        }
        var idx = 0
        for (i in 0 until 8) {
            val k = (i + 8) and 0xF
            val oi = off[i]
            val ok = off[k]
            val acc = vv[oi] * proto8m0[idx] + vv[ok + 1] * proto8m1[idx] +
                vv[oi + 2] * proto8m0[idx + 1] + vv[ok + 3] * proto8m1[idx + 1] +
                vv[oi + 4] * proto8m0[idx + 2] + vv[ok + 5] * proto8m1[idx + 2] +
                vv[oi + 6] * proto8m0[idx + 3] + vv[ok + 7] * proto8m1[idx + 3] +
                vv[oi + 8] * proto8m0[idx + 4] + vv[ok + 9] * proto8m1[idx + 4]
            pcm[(blk * 8 + i) * channels + ch] = clip16(acc shr SCALE8_STAGED1_BITS)
            idx += 5
        }
    }

    /** Bit allocation, straight from the A2DP specification (via libsbc). */
    private fun calculateBits(sf: Int, mode: Int, channels: Int, allocation: Int, subbands: Int, bitpool: Int) {
        val offsets = if (subbands == 4) OFFSET4[sf] else OFFSET8[sf]
        val bitneed = Array(2) { IntArray(8) }

        fun computeBitneed(ch: Int): Int {
            var maxBitneed = 0
            for (sb in 0 until subbands) {
                val sfv = scaleFactor[ch][sb]
                bitneed[ch][sb] = if (allocation == ALLOCATION_SNR) {
                    sfv
                } else if (sfv == 0) {
                    -5
                } else {
                    val loudness = sfv - offsets[sb]
                    if (loudness > 0) loudness / 2 else loudness
                }
                if (bitneed[ch][sb] > maxBitneed) maxBitneed = bitneed[ch][sb]
            }
            return maxBitneed
        }

        if (mode == MODE_MONO || mode == MODE_DUAL) {
            for (ch in 0 until channels) {
                val maxBitneed = computeBitneed(ch)
                var bitcount = 0
                var slicecount = 0
                var bitslice = maxBitneed + 1
                do {
                    bitslice--
                    bitcount += slicecount
                    slicecount = 0
                    for (sb in 0 until subbands) {
                        val bn = bitneed[ch][sb]
                        if (bn > bitslice + 1 && bn < bitslice + 16) slicecount++
                        else if (bn == bitslice + 1) slicecount += 2
                    }
                } while (bitcount + slicecount < bitpool)
                if (bitcount + slicecount == bitpool) {
                    bitcount += slicecount
                    bitslice--
                }
                for (sb in 0 until subbands) {
                    bits[ch][sb] = if (bitneed[ch][sb] < bitslice + 2) 0 else minOf(bitneed[ch][sb] - bitslice, 16)
                }
                var sb = 0
                while (bitcount < bitpool && sb < subbands) {
                    if (bits[ch][sb] in 2..15) {
                        bits[ch][sb]++
                        bitcount++
                    } else if (bitneed[ch][sb] == bitslice + 1 && bitpool > bitcount + 1) {
                        bits[ch][sb] = 2
                        bitcount += 2
                    }
                    sb++
                }
                sb = 0
                while (bitcount < bitpool && sb < subbands) {
                    if (bits[ch][sb] < 16) {
                        bits[ch][sb]++
                        bitcount++
                    }
                    sb++
                }
            }
        } else {
            val maxBitneed = maxOf(computeBitneed(0), computeBitneed(1))
            var bitcount = 0
            var slicecount = 0
            var bitslice = maxBitneed + 1
            do {
                bitslice--
                bitcount += slicecount
                slicecount = 0
                for (ch in 0 until 2) {
                    for (sb in 0 until subbands) {
                        val bn = bitneed[ch][sb]
                        if (bn > bitslice + 1 && bn < bitslice + 16) slicecount++
                        else if (bn == bitslice + 1) slicecount += 2
                    }
                }
            } while (bitcount + slicecount < bitpool)
            if (bitcount + slicecount == bitpool) {
                bitcount += slicecount
                bitslice--
            }
            for (ch in 0 until 2) {
                for (sb in 0 until subbands) {
                    bits[ch][sb] = if (bitneed[ch][sb] < bitslice + 2) 0 else minOf(bitneed[ch][sb] - bitslice, 16)
                }
            }
            var ch = 0
            var sb = 0
            while (bitcount < bitpool) {
                if (bits[ch][sb] in 2..15) {
                    bits[ch][sb]++
                    bitcount++
                } else if (bitneed[ch][sb] == bitslice + 1 && bitpool > bitcount + 1) {
                    bits[ch][sb] = 2
                    bitcount += 2
                }
                if (ch == 1) {
                    ch = 0
                    sb++
                    if (sb >= subbands) break
                } else {
                    ch = 1
                }
            }
            ch = 0
            sb = 0
            while (bitcount < bitpool) {
                if (bits[ch][sb] < 16) {
                    bits[ch][sb]++
                    bitcount++
                }
                if (ch == 1) {
                    ch = 0
                    sb++
                    if (sb >= subbands) break
                } else {
                    ch = 1
                }
            }
        }
    }

    companion object {
        const val SYNCWORD = 0x9C

        private const val MODE_MONO = 0
        private const val MODE_DUAL = 1
        private const val MODE_STEREO = 2
        private const val MODE_JOINT = 3
        private const val ALLOCATION_SNR = 1

        private const val FIXED_EXTRA_BITS = 2
        private const val SCALE4_STAGED1_BITS = 15
        private const val SCALE8_STAGED1_BITS = 15

        private val FREQUENCIES = intArrayOf(16000, 32000, 44100, 48000)

        /** Calculates the SBC CRC-8 (polynomial 0x1D, initial value 0x0F) of the first [bitLength] bits. */
        fun crc8(data: IntArray, bitLength: Int): Int {
            var crc = 0x0F
            for (i in 0 until bitLength) {
                val bit = (data[i shr 3] shr (7 - (i and 7))) and 1
                val top = (crc shr 7) and 1
                crc = (crc shl 1) and 0xFF
                if (top xor bit != 0) crc = crc xor 0x1D
            }
            return crc
        }

        private fun clip16(s: Int): Short = when {
            s > 0x7FFF -> 0x7FFF
            s < -0x8000 -> -0x8000
            else -> s
        }.toShort()

        // A2DP specification, Appendix B.
        private val OFFSET4 = arrayOf(
            intArrayOf(-1, 0, 0, 0),
            intArrayOf(-2, 0, 0, 1),
            intArrayOf(-2, 0, 0, 1),
            intArrayOf(-2, 0, 0, 1),
        )
        private val OFFSET8 = arrayOf(
            intArrayOf(-2, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(-3, 0, 0, 0, 0, 0, 1, 2),
            intArrayOf(-4, 0, 0, 0, 0, 0, 1, 2),
            intArrayOf(-4, 0, 0, 0, 0, 0, 1, 2),
        )

        // Synthesis tables from libsbc sbc_tables.h, pre-shifted (SS4/SS8/SN4/SN8).
        private val proto4m0 = intArrayOf(
            0, -1431, -17773, 17772, 1430, -71, -2679, -25558,
            10177, 401, -196, -3785, -32328, 3777, -245, -359,
            -4220, -36940, -804, -511,
        )

        private val proto4m1 = intArrayOf(
            -503, -3392, -38577, -3392, -503, -511, -804, -36940,
            -4220, -359, -245, 3777, -32328, -3785, -196, 401,
            10177, -25558, -2679, -71,
        )

        private val proto8m0 = intArrayOf(
            0, -1484, -17826, 17825, 1483, -42, -2105, -21754,
            13942, 916, -90, -2742, -25579, 10243, 432, -146,
            -3342, -29150, 6844, 46, -216, -3842, -32314, 3837,
            -237, -299, -4170, -34935, 1288, -424, -388, -4253,
            -36898, -767, -523, -468, -4016, -38114, -2322, -552,
        )

        private val proto8m1 = intArrayOf(
            -528, -3392, -38524, -3392, -528, -552, -2322, -38114,
            -4016, -468, -523, -767, -36898, -4253, -388, -424,
            1288, -34935, -4170, -299, -237, 3837, -32314, -3842,
            -216, 46, 6844, -29150, -3342, -146, 432, 10243,
            -25579, -2742, -90, 916, 13942, -21754, -2105, -42,
        )

        private val synMatrix4 = intArrayOf(
            5792, -5793, -5793, 5792,
            3134, -7569, 7568, -3135,
            0, 0, 0, 0,
            -3135, 7568, -7569, 3134,
            -5793, 5792, 5792, -5793,
            -7569, -3135, 3134, 7568,
            -8192, -8192, -8192, -8192,
            -7569, -3135, 3134, 7568,
        )

        private val synMatrix8 = intArrayOf(
            5792, -5793, -5793, 5792, 5792, -5793, -5793, 5792,
            4551, -8035, 1598, 6811, -6812, -1599, 8034, -4552,
            3134, -7569, 7568, -3135, -3135, 7568, -7569, 3134,
            1598, -4552, 6811, -8035, 8034, -6812, 4551, -1599,
            0, 0, 0, 0, 0, 0, 0, 0,
            -1599, 4551, -6812, 8034, -8035, 6811, -4552, 1598,
            -3135, 7568, -7569, 3134, 3134, -7569, 7568, -3135,
            -4552, 8034, -1599, -6812, 6811, 1598, -8035, 4551,
            -5793, 5792, 5792, -5793, -5793, 5792, 5792, -5793,
            -6812, 1598, 8034, 4551, -4552, -8035, -1599, 6811,
            -7569, -3135, 3134, 7568, 7568, 3134, -3135, -7569,
            -8035, -6812, -4552, -1599, 1598, 4551, 6811, 8034,
            -8192, -8192, -8192, -8192, -8192, -8192, -8192, -8192,
            -8035, -6812, -4552, -1599, 1598, 4551, 6811, 8034,
            -7569, -3135, 3134, 7568, 7568, 3134, -3135, -7569,
            -6812, 1598, 8034, 4551, -4552, -8035, -1599, 6811,
        )
    }
}

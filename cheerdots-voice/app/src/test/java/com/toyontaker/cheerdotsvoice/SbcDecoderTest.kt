package com.toyontaker.cheerdotsvoice

import com.toyontaker.cheerdotsvoice.audio.SbcDecoder
import com.toyontaker.cheerdotsvoice.protocol.CheerdotsProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SbcDecoderTest {

    private fun resource(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes() }

    private fun hex(s: String) = s.split(' ').map { it.toInt(16).toByte() }.toByteArray()

    private fun pcm(name: String): ShortArray {
        val bytes = resource(name)
        val out = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
        return out
    }

    @Test
    fun decodesStandardSbcBitExactWithLibsbc() {
        // Encoded and decoded by BlueZ libsbc: 16 kHz, mono, 8 subbands, 8 blocks, bitpool 12.
        val sbc = resource("chirp_16k_mono_sb8_blk8_bp12.sbc")
        val decoder = SbcDecoder()
        val out = ArrayList<Short>()
        var pos = 0
        while (pos < sbc.size) {
            val frame = decoder.decode(sbc, pos, sbc.size - pos)
            assertEquals(20, frame.bytesConsumed)
            assertEquals(16000, frame.sampleRate)
            assertEquals(64, frame.pcm.size)
            frame.pcm.forEach { out.add(it) }
            pos += frame.bytesConsumed
        }
        assertArrayEquals(pcm("chirp_libsbc_decoded.pcm"), out.toShortArray())
    }

    /**
     * Reference stream in the Cheerdots format (10 blocks, header 9C 11 0C, 23-byte
     * frames), produced by libsbc with its mSBC path patched to the parameters
     * the official Cheerdots app hard-codes, and decoded by that same libsbc.
     */
    @Test
    fun decodesCheerdotsPacketsBitExactWithLibsbc() {
        val sbc = resource("chirp_cheerdots_10blocks.sbc")
        assertEquals(0, sbc.size % 23)
        val decoder = SbcDecoder()
        val out = ArrayList<Short>()
        for (pos in sbc.indices step 23) {
            // What the device sends: the frame without its 3-byte header.
            val packet = sbc.copyOfRange(pos + 3, pos + 23)
            assertEquals(CheerdotsProtocol.AUDIO_PACKET_SIZE, packet.size)
            val frame = decoder.decode(
                CheerdotsProtocol.audioPacketToSbcFrame(packet),
                blocksOverride = CheerdotsProtocol.AUDIO_SBC_BLOCKS,
            )
            assertEquals(23, frame.bytesConsumed)
            assertEquals(80, frame.pcm.size)
            frame.pcm.forEach { out.add(it) }
        }
        assertArrayEquals(pcm("chirp_cheerdots_10blocks_libsbc_decoded.pcm"), out.toShortArray())
    }

    @Test
    fun decodesPacketsCapturedFromARealDevice() {
        // Notifications from characteristic 2b14 captured with nRF Connect while holding the voice key.
        val packets = listOf(
            hex("17 67 55 65 44 b4 bb 4a dc 19 ab a6 3a 6b 76 b7 73 98 c6 8b"),
            hex("31 66 67 54 45 86 d5 55 2c 06 c5 66 55 6d 86 d7 6d 86 d7 4d"),
        )
        val decoder = SbcDecoder()
        val out = ArrayList<Short>()
        for (p in packets) {
            val frame = decoder.decode(
                CheerdotsProtocol.audioPacketToSbcFrame(p),
                blocksOverride = CheerdotsProtocol.AUDIO_SBC_BLOCKS,
            )
            assertEquals(23, frame.bytesConsumed)
            frame.pcm.forEach { out.add(it) }
        }
        assertArrayEquals(pcm("real_packets_libsbc_decoded.pcm"), out.toShortArray())
    }

    @Test(expected = SbcDecoder.DecodeException::class)
    fun rejectsCorruptedFrames() {
        val packet = hex("18 67 55 65 44 b4 bb 4a dc 19 ab a6 3a 6b 76 b7 73 98 c6 8b")
        SbcDecoder().decode(CheerdotsProtocol.audioPacketToSbcFrame(packet))
    }

    @Test
    fun parsesKeyEvents() {
        val up = hex("0a 00 00 50 28 22 00 02 00 00 c9 4f 9e 10 00 03")
        assertEquals(CheerdotsProtocol.Event.KeyEvent(CheerdotsProtocol.Key.VOICE_INPUT_UP), CheerdotsProtocol.parseEvent(up))
        assertEquals(CheerdotsProtocol.Event.Battery(85), CheerdotsProtocol.parseEvent(byteArrayOf(85)))
    }
}

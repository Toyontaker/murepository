package com.toyontaker.cheerdotsvoice.protocol

import java.util.UUID

/**
 * Cheerdots 2 BLE protocol, as observed from the official desktop app (v1.0.16)
 * and verified against a real device.
 *
 * The device is a Telink-based HID mouse that additionally exposes vendor GATT
 * services. Everything here sits next to the HID service, so an app can use it
 * while Android keeps the device connected as a mouse.
 */
object CheerdotsProtocol {

    private fun telinkUuid(suffix: String): UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d$suffix")

    /** Control service ("SPP" in the official app). */
    val SERVICE_CONTROL: UUID = telinkUuid("1910")

    /** Device -> host events (key presses, status). Notify. */
    val CHAR_EVENTS: UUID = telinkUuid("2b10")

    /** Host -> device commands. Write without response. */
    val CHAR_COMMAND: UUID = telinkUuid("2b11")

    /** Device -> host microphone audio (headerless SBC frames). Notify. */
    val CHAR_AUDIO: UUID = telinkUuid("2b14")

    /** Secondary audio service; the official app subscribes to it too. */
    val SERVICE_AUDIO_AUX: UUID = telinkUuid("1911")
    val CHAR_AUDIO_AUX: UUID = telinkUuid("2b18")

    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Report prefix used for every command and event on the control service. */
    const val REPORT_ID = 0x0A

    /**
     * Tells the device which OS the host app runs on. The official app sends this
     * right after connecting; the device only streams microphone audio afterwards.
     * The macOS build sends OS type 0x01.
     */
    val CMD_SET_OS_TYPE = byteArrayOf(0x0A, 0x03, 0xC5.toByte(), 0x01, 0x00)

    /** Requests a 16-byte status packet on [CHAR_EVENTS]. */
    val CMD_GET_STATUS = byteArrayOf(0x0A, 0x04, 0xA0.toByte(), 0x00, 0x00)

    /** Handshake sent after subscribing, in this order. */
    val HANDSHAKE = listOf(CMD_SET_OS_TYPE, CMD_GET_STATUS)

    /**
     * Each audio notification is one 20-byte SBC frame with the constant 3-byte
     * header (sync 0x9C, 16 kHz / 8 blocks / mono / loudness / 8 subbands,
     * bitpool 12) stripped. The first byte is the frame CRC; the last 3 bytes
     * are not part of the frame.
     */
    const val AUDIO_PACKET_SIZE = 20
    val SBC_HEADER = byteArrayOf(0x9C.toByte(), 0x11, 0x0C)
    const val AUDIO_SAMPLE_RATE = 16000

    /** Rebuilds a complete SBC frame from one audio notification. */
    fun audioPacketToSbcFrame(packet: ByteArray): ByteArray {
        val frame = ByteArray(SBC_HEADER.size + packet.size)
        SBC_HEADER.copyInto(frame)
        packet.copyInto(frame, SBC_HEADER.size)
        return frame
    }

    /** Key codes found at byte 5 of a 16-byte event packet. */
    object Key {
        const val VOICE_INPUT_DOWN = 0x21
        const val VOICE_INPUT_UP = 0x22
        const val SINGLE_VOICE = 0x41
        const val RECORD_TOGGLE = 0x42
        const val LASER_DOUBLE_CLICK = 0x51
    }

    sealed interface Event {
        /** A key event; [code] is one of [Key]. */
        data class KeyEvent(val code: Int) : Event

        /** Status reply (byte 5 == 0). */
        data class Status(val raw: ByteArray) : Event

        /** Battery level in percent (1-byte packet). */
        data class Battery(val percent: Int) : Event

        data class Unknown(val raw: ByteArray) : Event
    }

    /** Parses a notification from [CHAR_EVENTS]. */
    fun parseEvent(value: ByteArray): Event {
        if (value.size == 1) return Event.Battery(value[0].toInt() and 0xFF)
        if (value.size == 16 && (value[0].toInt() and 0xFF) == REPORT_ID) {
            val code = value[5].toInt() and 0xFF
            return if (code == 0) Event.Status(value) else Event.KeyEvent(code)
        }
        return Event.Unknown(value)
    }
}

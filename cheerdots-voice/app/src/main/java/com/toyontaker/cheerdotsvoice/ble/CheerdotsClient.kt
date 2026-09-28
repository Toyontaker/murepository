package com.toyontaker.cheerdotsvoice.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.toyontaker.cheerdotsvoice.protocol.CheerdotsProtocol
import java.util.ArrayDeque

/**
 * GATT client for the Cheerdots 2 vendor services.
 *
 * Connects to an already bonded device, subscribes to the event and audio
 * characteristics, sends the handshake and then reports events and raw audio
 * packets. All [Listener] callbacks run on the main thread except
 * [Listener.onAudioPacket], which runs on the Binder thread to keep latency low.
 */
@SuppressLint("MissingPermission") // Callers must hold BLUETOOTH_CONNECT.
class CheerdotsClient(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onStateChanged(state: State) {}
        fun onEvent(event: CheerdotsProtocol.Event) {}
        fun onAudioPacket(packet: ByteArray) {}
        fun onLog(message: String) {}
    }

    enum class State { DISCONNECTED, CONNECTING, READY }

    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var address: String? = null
    private var wantConnected = false

    /** Serialized GATT operations; Android allows only one in flight. */
    private val ops = ArrayDeque<(BluetoothGatt) -> Boolean>()
    private var opInFlight = false

    var state = State.DISCONNECTED
        private set(value) {
            field = value
            main.post { listener.onStateChanged(value) }
        }

    fun connect(deviceAddress: String) {
        wantConnected = true
        if (address == deviceAddress && gatt != null) return
        close()
        wantConnected = true
        address = deviceAddress
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            log("Bluetooth is not available")
            return
        }
        val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)
        state = State.CONNECTING
        log("Connecting to ${device.name ?: deviceAddress}")
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun close() {
        wantConnected = false
        main.removeCallbacksAndMessages(null)
        synchronized(ops) {
            ops.clear()
            opInFlight = false
        }
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        address = null
        if (state != State.DISCONNECTED) state = State.DISCONNECTED
    }

    /** Sends a raw command to the control characteristic. */
    fun sendCommand(bytes: ByteArray) {
        enqueue { g ->
            val ch = g.getService(CheerdotsProtocol.SERVICE_CONTROL)?.getCharacteristic(CheerdotsProtocol.CHAR_COMMAND)
                ?: return@enqueue false
            writeCharacteristic(g, ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            // gatt may still be unassigned if this fires before connectGatt() returns.
            if (gatt != null && g != gatt) return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Connected, discovering services")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Disconnected (status $status)")
                synchronized(ops) {
                    ops.clear()
                    opInFlight = false
                }
                state = State.DISCONNECTED
                scheduleReconnect()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (gatt != null && g != gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Service discovery failed ($status)")
                return
            }
            val control = g.getService(CheerdotsProtocol.SERVICE_CONTROL)
            if (control == null) {
                log("Control service not found - is this a Cheerdots 2?")
                return
            }
            subscribe(control.getCharacteristic(CheerdotsProtocol.CHAR_EVENTS))
            subscribe(control.getCharacteristic(CheerdotsProtocol.CHAR_AUDIO))
            subscribe(g.getService(CheerdotsProtocol.SERVICE_AUDIO_AUX)?.getCharacteristic(CheerdotsProtocol.CHAR_AUDIO_AUX))
            for (cmd in CheerdotsProtocol.HANDSHAKE) sendCommand(cmd)
            enqueue {
                log("Ready")
                state = State.READY
                false
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) log("Subscribe ${descriptor.characteristic.uuid} failed ($status)")
            opDone()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            opDone()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleNotification(characteristic, value)
        }

        @Deprecated("Used on Android 12")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handleNotification(characteristic, characteristic.value ?: return)
        }
    }

    private fun handleNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        when (characteristic.uuid) {
            CheerdotsProtocol.CHAR_AUDIO, CheerdotsProtocol.CHAR_AUDIO_AUX -> {
                if (value.size == CheerdotsProtocol.AUDIO_PACKET_SIZE) listener.onAudioPacket(value)
            }
            CheerdotsProtocol.CHAR_EVENTS -> {
                val event = CheerdotsProtocol.parseEvent(value)
                main.post { listener.onEvent(event) }
            }
        }
    }

    private fun subscribe(ch: BluetoothGattCharacteristic?) {
        if (ch == null) return
        enqueue { g ->
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(CheerdotsProtocol.CCCD) ?: return@enqueue false
            writeDescriptor(g, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }
    }

    /** Queues [op]; it returns true when it started an async operation that will call [opDone]. */
    private fun enqueue(op: (BluetoothGatt) -> Boolean) {
        synchronized(ops) { ops.addLast(op) }
        main.post { drain() }
    }

    private fun opDone() {
        synchronized(ops) { opInFlight = false }
        main.post { drain() }
    }

    private fun drain() {
        while (true) {
            val g = gatt ?: return
            val op = synchronized(ops) {
                if (opInFlight || ops.isEmpty()) return
                opInFlight = true
                ops.removeFirst()
            }
            val started = try {
                op(g)
            } catch (e: SecurityException) {
                log("Permission error: ${e.message}")
                false
            }
            if (started) return
            synchronized(ops) { opInFlight = false }
        }
    }

    private fun writeDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            d.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(d)
        }

    private fun writeCharacteristic(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, type: Int): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, value, type) == BluetoothStatusCodes.SUCCESS
        } else {
            ch.writeType = type
            @Suppress("DEPRECATION")
            ch.value = value
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }

    private fun scheduleReconnect() {
        val target = address ?: return
        if (!wantConnected) return
        main.postDelayed({
            if (!wantConnected || state != State.DISCONNECTED) return@postDelayed
            gatt?.close()
            gatt = null
            address = null
            connect(target)
        }, RECONNECT_DELAY_MS)
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        main.post { listener.onLog(message) }
    }

    companion object {
        private const val TAG = "CheerdotsClient"
        private const val RECONNECT_DELAY_MS = 3000L
    }
}

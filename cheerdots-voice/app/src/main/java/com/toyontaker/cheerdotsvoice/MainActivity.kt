package com.toyontaker.cheerdotsvoice

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.toyontaker.cheerdotsvoice.ble.CheerdotsClient

/**
 * Setup and diagnostics: permissions, device selection, keyboard activation,
 * and tests that exercise the microphone without the keyboard.
 */
class MainActivity : Activity(), VoiceController.Listener {

    private lateinit var settings: Settings
    private lateinit var controller: VoiceController
    private lateinit var deviceView: TextView
    private lateinit var stateView: TextView
    private lateinit var resultView: TextView
    private lateinit var logView: TextView
    private val logLines = ArrayDeque<String>()
    private var lastRecording: ByteArray? = null
    private var track: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)
        controller = VoiceController(this, this)

        deviceView = findViewById(R.id.device)
        stateView = findViewById(R.id.state)
        resultView = findViewById(R.id.result)
        logView = findViewById(R.id.log)

        findViewById<Button>(R.id.btn_permissions).setOnClickListener { requestPermissions() }
        findViewById<Button>(R.id.btn_device).setOnClickListener { chooseDevice() }
        findViewById<Button>(R.id.btn_ime_settings).setOnClickListener {
            startActivity(Intent(SystemSettings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btn_ime_pick).setOnClickListener {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        }
        findViewById<Button>(R.id.btn_test_recognize).setOnClickListener { startTest(VoiceController.Mode.RECOGNIZE) }
        findViewById<Button>(R.id.btn_test_record).setOnClickListener { startTest(VoiceController.Mode.RECORD) }
        findViewById<Button>(R.id.btn_play).setOnClickListener { play() }
        findViewById<CheckBox>(R.id.offline).apply {
            isChecked = settings.preferOffline
            setOnCheckedChangeListener { _, checked -> settings.preferOffline = checked }
        }
        setUpEngineSettings()

        showDevice()
        onConnectionChanged(CheerdotsClient.State.DISCONNECTED)
        if (!hasPermissions()) requestPermissions()
    }

    override fun onStop() {
        // Leave the connection to the keyboard while the app is in the background.
        controller.disconnect()
        super.onStop()
    }

    override fun onDestroy() {
        controller.release()
        track?.release()
        super.onDestroy()
    }

    private fun setUpEngineSettings() {
        val offline = findViewById<CheckBox>(R.id.offline)
        val geminiSettings = findViewById<View>(R.id.gemini_settings)
        fun showFor(engine: Settings.Engine) {
            offline.visibility = if (engine == Settings.Engine.ANDROID) View.VISIBLE else View.GONE
            geminiSettings.visibility = if (engine == Settings.Engine.GEMINI) View.VISIBLE else View.GONE
        }
        findViewById<RadioGroup>(R.id.engine).apply {
            check(if (settings.engine == Settings.Engine.GEMINI) R.id.engine_gemini else R.id.engine_android)
            setOnCheckedChangeListener { _, id ->
                settings.engine = if (id == R.id.engine_gemini) Settings.Engine.GEMINI else Settings.Engine.ANDROID
                showFor(settings.engine)
            }
        }
        showFor(settings.engine)

        val key = findViewById<EditText>(R.id.gemini_key)
        key.setText(settings.geminiApiKey)
        key.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveGeminiKey(key) }
        key.setOnEditorActionListener { _, _, _ ->
            saveGeminiKey(key)
            key.clearFocus()
            false
        }
        findViewById<CheckBox>(R.id.gemini_smart).apply {
            isChecked = settings.geminiSmart
            setOnCheckedChangeListener { _, checked -> settings.geminiSmart = checked }
        }
    }

    private fun saveGeminiKey(field: EditText) {
        val value = field.text.toString().trim()
        if (value == settings.geminiApiKey) return
        settings.geminiApiKey = value
        Toast.makeText(this, R.string.gemini_key_saved, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        saveGeminiKey(findViewById(R.id.gemini_key))
        super.onPause()
    }

    private fun hasPermissions() = REQUIRED_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun requestPermissions() = requestPermissions(REQUIRED_PERMISSIONS, 1)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        log(if (hasPermissions()) "権限OK" else "権限が不足しています")
        if (hasPermissions() && settings.deviceAddress == null) chooseDevice()
    }

    @SuppressLint("MissingPermission")
    private fun chooseDevice() {
        if (!hasPermissions()) {
            requestPermissions()
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val devices = adapter.bondedDevices.sortedByDescending { it.name?.contains("cheerdots", ignoreCase = true) == true }
        if (devices.isEmpty()) {
            log("ペアリング済みのデバイスがありません")
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_device)
            .setItems(devices.map { "${it.name ?: "?"}  ${it.address}" }.toTypedArray()) { _, which ->
                settings.deviceAddress = devices[which].address
                showDevice()
            }
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun showDevice() {
        val address = settings.deviceAddress
        deviceView.text = if (address == null) getString(R.string.no_device) else getString(R.string.device_fmt, address)
    }

    private fun startTest(mode: VoiceController.Mode) {
        if (!hasPermissions()) {
            requestPermissions()
            return
        }
        controller.mode = mode
        resultView.text = getString(R.string.hold_key)
        if (controller.connectionState == CheerdotsClient.State.DISCONNECTED && !controller.connect()) {
            chooseDevice()
        }
    }

    private fun play() {
        val recorded = lastRecording ?: return
        // Lead-in silence: the speaker path takes a moment to wake up and would clip the start.
        val pcm = ByteArray(PLAYBACK_LEAD_IN_BYTES) + recorded
        track?.release()
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        t.play()
        track = t
    }

    private fun log(message: String) {
        logLines.addLast(message)
        while (logLines.size > 40) logLines.removeFirst()
        logView.text = logLines.joinToString("\n")
    }

    override fun onConnectionChanged(state: CheerdotsClient.State) {
        stateView.setText(
            when (state) {
                CheerdotsClient.State.READY -> R.string.status_ready
                CheerdotsClient.State.CONNECTING -> R.string.status_connecting
                CheerdotsClient.State.DISCONNECTED -> R.string.status_disconnected
            }
        )
    }

    override fun onSessionStarted() {
        resultView.setText(R.string.status_listening)
    }

    override fun onProcessing() {
        resultView.setText(R.string.status_processing)
    }

    override fun onPartial(text: String) {
        resultView.text = text
    }

    override fun onFinal(text: String) {
        resultView.text = text.ifEmpty { getString(R.string.empty_result) }
    }

    override fun onRecorded(pcm: ByteArray, sampleRate: Int) {
        lastRecording = pcm
        resultView.text = getString(R.string.recorded_fmt, pcm.size / 2.0 / sampleRate)
        play()
    }

    override fun onError(message: String) {
        resultView.text = message
    }

    override fun onLog(message: String) = log(message)

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val PLAYBACK_LEAD_IN_BYTES = SAMPLE_RATE * 2 * 300 / 1000
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.RECORD_AUDIO)
    }
}

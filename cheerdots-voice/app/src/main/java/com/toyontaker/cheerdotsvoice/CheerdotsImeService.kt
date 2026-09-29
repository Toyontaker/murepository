package com.toyontaker.cheerdotsvoice

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import com.toyontaker.cheerdotsvoice.ble.CheerdotsClient

/**
 * Keyboard that types what is spoken into the Cheerdots microphone.
 * Hold the voice key on the Cheerdots, speak, release.
 */
class CheerdotsImeService : InputMethodService(), VoiceController.Listener {

    private lateinit var controller: VoiceController
    private var statusView: TextView? = null
    private var previewView: TextView? = null
    private var composing = false

    override fun onCreate() {
        super.onCreate()
        controller = VoiceController(this, this)
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard, null)
        statusView = view.findViewById(R.id.status)
        previewView = view.findViewById(R.id.preview)
        view.findViewById<Button>(R.id.key_backspace).setOnClickListener { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
        view.findViewById<Button>(R.id.key_space).setOnClickListener { currentInputConnection?.commitText(" ", 1) }
        view.findViewById<Button>(R.id.key_enter).setOnClickListener { sendEnter() }
        view.findViewById<Button>(R.id.key_switch).setOnClickListener {
            if (!switchToPreviousInputMethod()) {
                getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showInputMethodPicker()
            }
        }
        view.findViewById<Button>(R.id.key_reconnect).setOnClickListener {
            controller.disconnect()
            connect()
        }
        showConnection(controller.connectionState)
        return view
    }

    /**
     * Cheerdots itself is a Bluetooth HID device, so Android treats it as a
     * hardware keyboard and the default implementation would hide this view.
     */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    /** The view is a small strip; never take over the screen in landscape. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (controller.connectionState == CheerdotsClient.State.DISCONNECTED) connect()
        showConnection(controller.connectionState)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        finishComposing()
        super.onFinishInputView(finishingInput)
    }

    private fun connect() {
        if (!controller.connect()) statusView?.setText(R.string.status_no_device)
    }

    private fun sendEnter() {
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun finishComposing() {
        if (composing) {
            currentInputConnection?.finishComposingText()
            composing = false
        }
    }

    private fun showConnection(state: CheerdotsClient.State) {
        statusView?.setText(
            when (state) {
                CheerdotsClient.State.READY -> R.string.status_ready
                CheerdotsClient.State.CONNECTING -> R.string.status_connecting
                CheerdotsClient.State.DISCONNECTED -> R.string.status_disconnected
            }
        )
    }

    override fun onConnectionChanged(state: CheerdotsClient.State) = showConnection(state)

    override fun onSessionStarted() {
        statusView?.setText(R.string.status_listening)
        previewView?.text = ""
    }

    override fun onProcessing() {
        statusView?.setText(R.string.status_processing)
    }

    override fun onPartial(text: String) {
        previewView?.text = text
        currentInputConnection?.setComposingText(text, 1)
        composing = true
    }

    override fun onFinal(text: String) {
        val ic = currentInputConnection
        if (text.isNotEmpty()) {
            ic?.commitText(text, 1)
        } else {
            finishComposing()
        }
        composing = false
        previewView?.text = text
        showConnection(controller.connectionState)
    }

    override fun onError(message: String) {
        finishComposing()
        previewView?.text = message
        showConnection(controller.connectionState)
    }
}

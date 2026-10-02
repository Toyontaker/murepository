package com.toyontaker.cheerdotsvoice

import android.annotation.SuppressLint
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.TextView
import com.toyontaker.cheerdotsvoice.ble.CheerdotsClient
import com.toyontaker.cheerdotsvoice.speech.TextRefiner

/**
 * Keyboard that types what is spoken into the Cheerdots microphone.
 * Hold the voice key on the Cheerdots, speak, release.
 */
class CheerdotsImeService : InputMethodService(), VoiceController.Listener {

    private lateinit var controller: VoiceController
    private var statusView: TextView? = null
    private var previewView: TextView? = null
    private var composing = false
    private val handler = Handler(Looper.getMainLooper())
    private var backspaceRepeats = 0

    /** Repeats backspace while the key is held, speeding up after a while. */
    private val backspaceRepeat: Runnable = object : Runnable {
        override fun run() {
            deleteBackward()
            backspaceRepeats++
            handler.postDelayed(this, if (backspaceRepeats < FAST_REPEAT_AFTER) REPEAT_INTERVAL_MS else FAST_REPEAT_INTERVAL_MS)
        }
    }
    private var undoButton: Button? = null
    private var proofreading = false

    /** What the last proofread replaced, for undo. */
    private data class Undo(val start: Int, val original: String, val replaced: String)
    private var undo: Undo? = null

    override fun onCreate() {
        super.onCreate()
        controller = VoiceController(this, this)
        controller.inputContextProvider = ::currentFieldContext
    }

    /** Describes the focused field for LLM context; private fields expose nothing. */
    private fun currentFieldContext(): TextRefiner.InputContext? {
        val info = currentInputEditorInfo ?: return null
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val password = (cls == InputType.TYPE_CLASS_TEXT && variation in PASSWORD_VARIATIONS) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val incognito = info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        if (password || incognito) return TextRefiner.InputContext(private = true)
        return TextRefiner.InputContext(
            appPackage = info.packageName,
            fieldHint = info.hintText?.toString(),
            textBeforeCursor = currentInputConnection?.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString(),
        )
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard, null)
        statusView = view.findViewById(R.id.status)
        previewView = view.findViewById(R.id.preview)
        setUpBackspace(view.findViewById(R.id.key_backspace))
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
        view.findViewById<Button>(R.id.key_proofread).setOnClickListener { proofread() }
        undoButton = view.findViewById<Button>(R.id.key_undo).apply {
            setOnClickListener { undoProofread() }
            isEnabled = undo != null
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
        handler.removeCallbacks(backspaceRepeat)
        finishComposing()
        setUndo(null)
        super.onFinishInputView(finishingInput)
    }

    private fun deleteBackward() = sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)

    // Touch handling implements key repeat; accessibility clicks still go through the click listener.
    @SuppressLint("ClickableViewAccessibility")
    private fun setUpBackspace(key: Button) {
        key.setOnClickListener { deleteBackward() }
        key.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    deleteBackward()
                    backspaceRepeats = 0
                    handler.postDelayed(backspaceRepeat, REPEAT_START_DELAY_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(backspaceRepeat)
                }
            }
            true
        }
    }

    /** A span of the field's text, in absolute character offsets. */
    private data class Span(val start: Int, val end: Int, val text: String)

    /** The selection if there is one, otherwise the whole field. */
    private fun proofreadTarget(ic: InputConnection): Span? {
        val extracted = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = MAX_PROOFREAD_CHARS * 2 }, 0)
            ?: return null
        val text = extracted.text?.toString() ?: return null
        val offset = extracted.startOffset
        val selStart = minOf(extracted.selectionStart, extracted.selectionEnd).coerceIn(0, text.length)
        val selEnd = maxOf(extracted.selectionStart, extracted.selectionEnd).coerceIn(0, text.length)
        return if (selStart != selEnd) {
            Span(offset + selStart, offset + selEnd, text.substring(selStart, selEnd))
        } else {
            Span(offset, offset + text.length, text)
        }
    }

    /** Current text at [start, start + length), or null when it cannot be read. */
    private fun textAt(ic: InputConnection, start: Int, length: Int): String? {
        val extracted = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = MAX_PROOFREAD_CHARS * 2 }, 0)
            ?: return null
        val text = extracted.text?.toString() ?: return null
        val from = start - extracted.startOffset
        if (from < 0 || from + length > text.length) return null
        return text.substring(from, from + length)
    }

    private fun replace(ic: InputConnection, start: Int, end: Int, text: String) {
        ic.beginBatchEdit()
        ic.finishComposingText()
        ic.setSelection(start, end)
        ic.commitText(text, 1)
        ic.endBatchEdit()
    }

    private fun proofread() {
        if (proofreading) return
        val ic = currentInputConnection ?: return
        if (currentFieldContext()?.private == true) {
            previewView?.setText(R.string.proofread_private)
            return
        }
        finishComposing()
        val target = proofreadTarget(ic)
        if (target == null) {
            previewView?.setText(R.string.proofread_unsupported)
            return
        }
        if (target.text.isBlank()) {
            previewView?.setText(R.string.proofread_empty)
            return
        }
        if (target.text.length > MAX_PROOFREAD_CHARS) {
            previewView?.text = getString(R.string.proofread_too_long, MAX_PROOFREAD_CHARS)
            return
        }
        proofreading = true
        statusView?.setText(R.string.status_proofreading)
        controller.proofread(target.text) { result ->
            proofreading = false
            showConnection(controller.connectionState)
            val fixed = result.getOrElse {
                previewView?.text = it.message
                return@proofread
            }
            // Keep the original's surrounding whitespace; the model trims it.
            val lead = target.text.takeWhile { it.isWhitespace() }
            val trail = target.text.takeLastWhile { it.isWhitespace() }
            val replacement = lead + fixed.trim() + trail
            if (replacement == target.text) {
                previewView?.setText(R.string.proofread_no_changes)
                return@proofread
            }
            val current = currentInputConnection
            if (current == null || textAt(current, target.start, target.text.length) != target.text) {
                previewView?.setText(R.string.proofread_changed)
                return@proofread
            }
            replace(current, target.start, target.end, replacement)
            setUndo(Undo(target.start, target.text, replacement))
            previewView?.setText(R.string.proofread_done)
        }
    }

    private fun undoProofread() {
        val u = undo ?: return
        val ic = currentInputConnection ?: return
        if (textAt(ic, u.start, u.replaced.length) != u.replaced) {
            previewView?.setText(R.string.proofread_changed)
            setUndo(null)
            return
        }
        replace(ic, u.start, u.start + u.replaced.length, u.original)
        setUndo(null)
        previewView?.setText(R.string.proofread_undone)
    }

    private fun setUndo(value: Undo?) {
        undo = value
        undoButton?.isEnabled = value != null
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

    override fun onProcessing(stage: VoiceController.Stage) {
        statusView?.setText(
            if (stage == VoiceController.Stage.REFINING) R.string.status_refining else R.string.status_processing
        )
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

    private companion object {
        const val CONTEXT_CHARS = 1000
        const val MAX_PROOFREAD_CHARS = 4000
        const val REPEAT_START_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 60L
        const val FAST_REPEAT_INTERVAL_MS = 25L
        const val FAST_REPEAT_AFTER = 20
        val PASSWORD_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
    }
}

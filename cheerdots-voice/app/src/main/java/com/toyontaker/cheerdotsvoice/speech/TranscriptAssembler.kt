package com.toyontaker.cheerdotsvoice.speech

/**
 * Builds the full transcript of one push-to-talk session from recognizer callbacks.
 *
 * Recognizers report text per segment: partial results cover only the current
 * segment and start over when a new one begins, and final results may come back
 * empty. Using only the latest partial therefore drops the beginning of longer
 * utterances. This keeps finished segments and appends the current one.
 */
class TranscriptAssembler {
    private val committed = StringBuilder()
    private var current = ""

    val text: String get() = committed.toString() + current

    fun reset() {
        committed.setLength(0)
        current = ""
    }

    fun onPartial(partial: String) {
        if (partial.isEmpty()) return
        if (current.isNotEmpty() && startsNewSegment(current, partial)) committed.append(current)
        current = partial
    }

    /** A finished segment (segmented sessions). */
    fun onSegment(segment: String) {
        committed.append(segment.ifEmpty { current })
        current = ""
    }

    /** The recognizer's final result; empty results keep what was collected. */
    fun onFinal(result: String) {
        if (result.isEmpty()) return
        if (committed.isNotEmpty() && result.startsWith(committed)) {
            // Final covers the whole utterance.
            committed.setLength(0)
        }
        current = result
    }

    companion object {
        /**
         * True when [next] is the start of a new segment rather than a revision of
         * [prev]. Revisions keep roughly the same text at the start (for example
         * 今日の天気 -> 明日の天気) or contain the previous text (天気 -> 明日の天気),
         * while a new segment is unrelated to the beginning of the previous one.
         */
        fun startsNewSegment(prev: String, next: String): Boolean {
            if (prev[0] == next[0]) return false
            if (next.contains(prev) || prev.contains(next)) return false
            val n = minOf(prev.length, next.length)
            val same = (0 until n).count { prev[it] == next[it] }
            return same * 2 < n
        }
    }
}

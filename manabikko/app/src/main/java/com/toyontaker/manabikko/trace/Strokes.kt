package com.toyontaker.manabikko.trace

import android.content.Context

/**
 * 書き順データ(assets/strokes.tsv)。KanjiVG(CC BY-SA 3.0)より。
 * 1文字ごとに、書く じゅんばんの 線(SVG path、109×109 の 座標)が ならぶ。
 */
object Strokes {
    /** KanjiVG の 座標の 大きさ。 */
    const val BOX = 109f

    @Volatile private var cache: Map<String, List<String>>? = null

    fun get(context: Context, char: String): List<String> =
        (cache ?: parse(context.assets.open("strokes.tsv").bufferedReader().use { it.readText() }).also { cache = it })[char]
            ?: emptyList()

    fun parse(text: String): Map<String, List<String>> =
        text.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line ->
                val (char, paths) = line.split('\t', limit = 2)
                char to paths.split('|')
            }
}

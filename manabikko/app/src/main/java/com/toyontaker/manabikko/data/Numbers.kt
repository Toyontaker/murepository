package com.toyontaker.manabikko.data

object Numbers {
    /** 1〜10 のよみかた。 */
    val readings = listOf("いち", "に", "さん", "よん", "ご", "ろく", "なな", "はち", "きゅう", "じゅう")

    /** 「〜こ」と かぞえるときの よみかた。 */
    val counters = listOf("いっこ", "にこ", "さんこ", "よんこ", "ごこ", "ろっこ", "ななこ", "はっこ", "きゅうこ", "じゅっこ")

    /** かぞえる ものの えもじ と なまえ。 */
    val things = listOf(
        "🍎" to "りんご", "🍓" to "いちご", "🍌" to "ばなな", "🐟" to "さかな",
        "🚗" to "くるま", "⭐" to "ほし", "🎈" to "ふうせん", "🐥" to "ひよこ",
    )

    fun reading(n: Int) = readings[n - 1]
    fun counter(n: Int) = counters[n - 1]
}

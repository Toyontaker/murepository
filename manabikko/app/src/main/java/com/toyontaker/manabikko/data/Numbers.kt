package com.toyontaker.manabikko.data

/** じょすうし(〜こ、〜ほん …)。readings は 1〜10 の よみかた。 */
enum class Counter(val unit: String, val readings: List<String>) {
    Ko("こ", listOf("いっこ", "にこ", "さんこ", "よんこ", "ごこ", "ろっこ", "ななこ", "はっこ", "きゅうこ", "じゅっこ")),
    Hon("ほん", listOf("いっぽん", "にほん", "さんぼん", "よんほん", "ごほん", "ろっぽん", "ななほん", "はっぽん", "きゅうほん", "じゅっぽん")),
    Hiki("ひき", listOf("いっぴき", "にひき", "さんびき", "よんひき", "ごひき", "ろっぴき", "ななひき", "はっぴき", "きゅうひき", "じゅっぴき")),
    Dai("だい", listOf("いちだい", "にだい", "さんだい", "よんだい", "ごだい", "ろくだい", "ななだい", "はちだい", "きゅうだい", "じゅうだい")),
    Wa("わ", listOf("いちわ", "にわ", "さんわ", "よんわ", "ごわ", "ろくわ", "ななわ", "はちわ", "きゅうわ", "じゅうわ")),
    ;

    /** 「さんぼん」 */
    fun reading(n: Int) = readings[n - 1]

    /** 数字の あとに つける ひょうき。「3ぼん」の「ぼん」。 */
    fun suffix(n: Int) = reading(n).takeLast(unit.length)

    /** 「3ぼん」 */
    fun label(n: Int) = "$n${suffix(n)}"

    /** 「なんぼん?」 */
    val question: String
        get() = when (this) {
            Ko -> "いくつ"
            Hon -> "なんぼん"
            Hiki -> "なんびき"
            else -> "なん$unit"
        }
}

/** かぞえる もの。 */
data class Thing(val emoji: String, val name: String, val counter: Counter)

object Numbers {
    /** 1〜10 のよみかた。 */
    val readings = listOf("いち", "に", "さん", "よん", "ご", "ろく", "なな", "はち", "きゅう", "じゅう")

    val things = listOf(
        Thing("🍎", "りんご", Counter.Ko),
        Thing("🍌", "ばなな", Counter.Hon),
        Thing("🐟", "さかな", Counter.Hiki),
        Thing("🚗", "くるま", Counter.Dai),
        Thing("🍓", "いちご", Counter.Ko),
        Thing("✏️", "えんぴつ", Counter.Hon),
        Thing("🐶", "いぬ", Counter.Hiki),
        Thing("🐥", "ひよこ", Counter.Wa),
        Thing("🚌", "ばす", Counter.Dai),
        Thing("🎈", "ふうせん", Counter.Ko),
    )

    fun reading(n: Int) = readings[n - 1]
}

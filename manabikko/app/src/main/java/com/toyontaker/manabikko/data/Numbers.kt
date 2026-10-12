package com.toyontaker.manabikko.data

/**
 * じょすうし(〜こ、〜ほん …)。
 *
 * @param readings 1〜10 の よみかた(画面ひょうじ用)
 * @param kanji 読み上げ用(「3本」と わたすと エンジンが「さんぼん」と 正しく 読む)
 * @param question 画面に だす ききかた(「なんぼん」)
 * @param sayQuestion 読み上げ用の ききかた(「何本」)
 * @param speakReadings 「3玉」のように エンジンが 読みを まちがえやすい ものは readings を そのまま 読ませる
 */
enum class Counter(
    val unit: String,
    val kanji: String,
    val question: String,
    val sayQuestion: String,
    val readings: List<String>,
    private val speakReadings: Boolean = false,
) {
    Ko("こ", "個", "いくつ", "いくつ", listOf("いっこ", "にこ", "さんこ", "よんこ", "ごこ", "ろっこ", "ななこ", "はっこ", "きゅうこ", "じゅっこ")),
    Hon("ほん", "本", "なんぼん", "何本", listOf("いっぽん", "にほん", "さんぼん", "よんほん", "ごほん", "ろっぽん", "ななほん", "はっぽん", "きゅうほん", "じゅっぽん")),
    Hiki("ひき", "匹", "なんびき", "何匹", listOf("いっぴき", "にひき", "さんびき", "よんひき", "ごひき", "ろっぴき", "ななひき", "はっぴき", "きゅうひき", "じゅっぴき")),
    Dai("だい", "台", "なんだい", "何台", listOf("いちだい", "にだい", "さんだい", "よんだい", "ごだい", "ろくだい", "ななだい", "はちだい", "きゅうだい", "じゅうだい")),
    Wa("わ", "羽", "なんわ", "何羽", listOf("いちわ", "にわ", "さんわ", "よんわ", "ごわ", "ろくわ", "ななわ", "はちわ", "きゅうわ", "じゅうわ")),
    Satsu("さつ", "冊", "なんさつ", "何冊", listOf("いっさつ", "にさつ", "さんさつ", "よんさつ", "ごさつ", "ろくさつ", "ななさつ", "はっさつ", "きゅうさつ", "じゅっさつ")),
    Soku("そく", "足", "なんぞく", "何足", listOf("いっそく", "にそく", "さんぞく", "よんそく", "ごそく", "ろくそく", "ななそく", "はっそく", "きゅうそく", "じゅっそく")),
    Tama(
        "たま", "玉", "いくつ", "いくつ",
        listOf("ひとたま", "ふたたま", "みたま", "よんたま", "ごたま", "ろくたま", "ななたま", "はちたま", "きゅうたま", "じゅったま"),
        speakReadings = true,
    ),
    Mai("まい", "枚", "なんまい", "何枚", listOf("いちまい", "にまい", "さんまい", "よんまい", "ごまい", "ろくまい", "ななまい", "はちまい", "きゅうまい", "じゅうまい")),
    Tou("とう", "頭", "なんとう", "何頭", listOf("いっとう", "にとう", "さんとう", "よんとう", "ごとう", "ろくとう", "ななとう", "はっとう", "きゅうとう", "じゅっとう")),
    ;

    /** 「さんぼん」 */
    fun reading(n: Int) = readings[n - 1]

    /** 数字の あとに つける ひょうき。「3ぼん」の「ぼん」。 */
    fun suffix(n: Int) = reading(n).takeLast(unit.length)

    /** 「3ぼん」 */
    fun label(n: Int) = "$n${suffix(n)}"

    /** 読み上げ用「3本」 */
    fun say(n: Int) = if (speakReadings) reading(n) else "$n$kanji"
}

/** かぞえる もの。say は 読み上げ用の 書きかた。 */
data class Thing(val emoji: String, val name: String, val counter: Counter, val say: String = name)

object Numbers {
    /** 1〜10 のよみかた。 */
    val readings = listOf("いち", "に", "さん", "よん", "ご", "ろく", "なな", "はち", "きゅう", "じゅう")

    val things = listOf(
        Thing("🍎", "りんご", Counter.Ko, "リンゴ"),
        Thing("🍌", "ばなな", Counter.Hon, "バナナ"),
        Thing("🐟", "さかな", Counter.Hiki, "魚"),
        Thing("🚗", "くるま", Counter.Dai, "車"),
        Thing("📕", "えほん", Counter.Satsu, "絵本"),
        Thing("👟", "くつ", Counter.Soku, "靴"),
        Thing("🥬", "きゃべつ", Counter.Tama, "キャベツ"),
        Thing("🍪", "くっきー", Counter.Mai, "クッキー"),
        Thing("🐘", "ぞう", Counter.Tou, "象"),
        Thing("🐥", "ひよこ", Counter.Wa, "ヒヨコ"),
        Thing("🍓", "いちご", Counter.Ko, "イチゴ"),
        Thing("✏️", "えんぴつ", Counter.Hon, "鉛筆"),
        Thing("🐶", "いぬ", Counter.Hiki, "犬"),
        Thing("🚌", "ばす", Counter.Dai, "バス"),
        Thing("🧦", "くつした", Counter.Soku, "靴下"),
        Thing("🎈", "ふうせん", Counter.Ko, "風船"),
    )

    fun reading(n: Int) = readings[n - 1]
}

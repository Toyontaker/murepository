package com.toyontaker.manabikko.data

/** ひらがな 1文字と、その文字ではじまる(または ふくむ)ことばの例。 */
data class Kana(val char: String, val word: String, val emoji: String)

object Hiragana {
    /** 五十音表。null は空きマス(や行・わ行)。 */
    val table: List<List<Kana?>> = listOf(
        listOf(Kana("あ", "あり", "🐜"), Kana("い", "いぬ", "🐶"), Kana("う", "うし", "🐮"), Kana("え", "えんぴつ", "✏️"), Kana("お", "おにぎり", "🍙")),
        listOf(Kana("か", "かめ", "🐢"), Kana("き", "きりん", "🦒"), Kana("く", "くま", "🐻"), Kana("け", "けーき", "🍰"), Kana("こ", "こあら", "🐨")),
        listOf(Kana("さ", "さかな", "🐟"), Kana("し", "しか", "🦌"), Kana("す", "すいか", "🍉"), Kana("せ", "せっけん", "🧼"), Kana("そ", "そら", "🌤️")),
        listOf(Kana("た", "たまご", "🥚"), Kana("ち", "ちょうちょ", "🦋"), Kana("つ", "つき", "🌙"), Kana("て", "てぶくろ", "🧤"), Kana("と", "とり", "🐦")),
        listOf(Kana("な", "なす", "🍆"), Kana("に", "にんじん", "🥕"), Kana("ぬ", "ぬいぐるみ", "🧸"), Kana("ね", "ねこ", "🐱"), Kana("の", "のーと", "📓")),
        listOf(Kana("は", "はな", "🌷"), Kana("ひ", "ひよこ", "🐤"), Kana("ふ", "ふね", "🚢"), Kana("へ", "へび", "🐍"), Kana("ほ", "ほし", "⭐")),
        listOf(Kana("ま", "まじょ", "🧙"), Kana("み", "みかん", "🍊"), Kana("む", "むし", "🐛"), Kana("め", "めがね", "👓"), Kana("も", "もも", "🍑")),
        listOf(Kana("や", "やま", "⛰️"), null, Kana("ゆ", "ゆきだるま", "⛄"), null, Kana("よ", "よっと", "⛵")),
        listOf(Kana("ら", "らいおん", "🦁"), Kana("り", "りんご", "🍎"), Kana("る", "かえる", "🐸"), Kana("れ", "れもん", "🍋"), Kana("ろ", "ろけっと", "🚀")),
        listOf(Kana("わ", "わに", "🐊"), null, Kana("を", "えをかく", "🎨"), null, Kana("ん", "ぱん", "🍞")),
    )

    /** 空きマスをのぞいた 46 文字。 */
    val all: List<Kana> = table.flatten().filterNotNull()
}

package com.toyontaker.manabikko.data

/** かな 1文字と、その文字ではじまる(または ふくむ)ことばの例。 */
data class Kana(val char: String, val word: String, val emoji: String)

/** 五十音表(ひらがな または カタカナ)。 */
interface KanaSet {
    /** null は空きマス(や行・わ行)。 */
    val table: List<List<Kana?>>

    /** 空きマスをのぞいた 文字。 */
    val all: List<Kana> get() = table.flatten().filterNotNull()
}

object Hiragana : KanaSet {
    override val table: List<List<Kana?>> = listOf(
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

    override val all: List<Kana> = super.all
}

object Katakana : KanaSet {
    // 「ヲ」は ことばの例が ないので のぞいている
    override val table: List<List<Kana?>> = listOf(
        listOf(Kana("ア", "アヒル", "🦆"), Kana("イ", "イルカ", "🐬"), Kana("ウ", "ウインナー", "🌭"), Kana("エ", "エビ", "🦐"), Kana("オ", "オレンジ", "🍊")),
        listOf(Kana("カ", "カメラ", "📷"), Kana("キ", "キウイ", "🥝"), Kana("ク", "クッキー", "🍪"), Kana("ケ", "ケーキ", "🍰"), Kana("コ", "コアラ", "🐨")),
        listOf(Kana("サ", "サンドイッチ", "🥪"), Kana("シ", "シャツ", "👕"), Kana("ス", "スプーン", "🥄"), Kana("セ", "セロリ", "🥬"), Kana("ソ", "ソフトクリーム", "🍦")),
        listOf(Kana("タ", "タクシー", "🚕"), Kana("チ", "チーズ", "🧀"), Kana("ツ", "ツリー", "🎄"), Kana("テ", "テレビ", "📺"), Kana("ト", "トマト", "🍅")),
        listOf(Kana("ナ", "ナッツ", "🥜"), Kana("ニ", "ニワトリ", "🐔"), Kana("ヌ", "カヌー", "🛶"), Kana("ネ", "ネクタイ", "👔"), Kana("ノ", "ノート", "📓")),
        listOf(Kana("ハ", "ハンバーガー", "🍔"), Kana("ヒ", "ヒコーキ", "✈️"), Kana("フ", "フォーク", "🍴"), Kana("ヘ", "ヘリコプター", "🚁"), Kana("ホ", "ホットケーキ", "🥞")),
        listOf(Kana("マ", "マイク", "🎤"), Kana("ミ", "ミルク", "🥛"), Kana("ム", "ハム", "🍖"), Kana("メ", "メロン", "🍈"), Kana("モ", "モノレール", "🚝")),
        listOf(Kana("ヤ", "ヤシのき", "🌴"), null, Kana("ユ", "ユーフォー", "🛸"), null, Kana("ヨ", "ヨット", "⛵")),
        listOf(Kana("ラ", "ラーメン", "🍜"), Kana("リ", "リボン", "🎀"), Kana("ル", "ルビー", "💎"), Kana("レ", "レモン", "🍋"), Kana("ロ", "ロボット", "🤖")),
        listOf(Kana("ワ", "ワニ", "🐊"), null, null, null, Kana("ン", "パン", "🍞")),
    )

    override val all: List<Kana> = super.all
}

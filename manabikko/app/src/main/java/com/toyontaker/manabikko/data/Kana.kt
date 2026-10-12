package com.toyontaker.manabikko.data

/**
 * かな 1文字と、その文字ではじまる(または ふくむ)ことばの例。
 * say は 読み上げ用の 書きかた(漢字などで 書くと アクセントが 自然に なる)。
 */
data class Kana(val char: String, val word: String, val emoji: String, val say: String = word)

/** 五十音表(ひらがな または カタカナ)。 */
interface KanaSet {
    /** null は空きマス(や行・わ行)。 */
    val table: List<List<Kana?>>

    /** 空きマスをのぞいた 文字。 */
    val all: List<Kana> get() = table.flatten().filterNotNull()
}

object Hiragana : KanaSet {
    override val table: List<List<Kana?>> = listOf(
        listOf(Kana("あ", "あり", "🐜", "蟻"), Kana("い", "いぬ", "🐶", "犬"), Kana("う", "うし", "🐮", "牛"), Kana("え", "えんぴつ", "✏️", "鉛筆"), Kana("お", "おにぎり", "🍙")),
        listOf(Kana("か", "かめ", "🐢", "亀"), Kana("き", "きりん", "🦒", "キリン"), Kana("く", "くま", "🐻", "熊"), Kana("け", "けーき", "🍰", "ケーキ"), Kana("こ", "こあら", "🐨", "コアラ")),
        listOf(Kana("さ", "さかな", "🐟", "魚"), Kana("し", "しか", "🦌", "鹿"), Kana("す", "すいか", "🍉", "スイカ"), Kana("せ", "せっけん", "🧼", "石鹸"), Kana("そ", "そら", "🌤️", "空")),
        listOf(Kana("た", "たまご", "🥚", "卵"), Kana("ち", "ちょうちょ", "🦋"), Kana("つ", "つき", "🌙", "月"), Kana("て", "てぶくろ", "🧤", "手袋"), Kana("と", "とり", "🐦", "鳥")),
        listOf(Kana("な", "なす", "🍆", "ナス"), Kana("に", "にんじん", "🥕", "ニンジン"), Kana("ぬ", "ぬいぐるみ", "🧸"), Kana("ね", "ねこ", "🐱", "猫"), Kana("の", "のーと", "📓", "ノート")),
        listOf(Kana("は", "はな", "🌷", "花"), Kana("ひ", "ひよこ", "🐤"), Kana("ふ", "ふね", "🚢", "船"), Kana("へ", "へび", "🐍", "蛇"), Kana("ほ", "ほし", "⭐", "星")),
        listOf(Kana("ま", "まじょ", "🧙", "魔女"), Kana("み", "みかん", "🍊"), Kana("む", "むし", "🐛", "虫"), Kana("め", "めがね", "👓", "眼鏡"), Kana("も", "もも", "🍑", "桃")),
        listOf(Kana("や", "やま", "⛰️", "山"), null, Kana("ゆ", "ゆきだるま", "⛄", "雪だるま"), null, Kana("よ", "よっと", "⛵", "ヨット")),
        listOf(Kana("ら", "らいおん", "🦁", "ライオン"), Kana("り", "りんご", "🍎"), Kana("る", "かえる", "🐸", "カエル"), Kana("れ", "れもん", "🍋", "レモン"), Kana("ろ", "ろけっと", "🚀", "ロケット")),
        listOf(Kana("わ", "わに", "🐊", "ワニ"), null, Kana("を", "えをかく", "🎨", "絵を描く"), null, Kana("ん", "ぱん", "🍞", "パン")),
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
        listOf(Kana("ハ", "ハンバーガー", "🍔"), Kana("ヒ", "ヒコーキ", "✈️", "飛行機"), Kana("フ", "フォーク", "🍴"), Kana("ヘ", "ヘリコプター", "🚁"), Kana("ホ", "ホットケーキ", "🥞")),
        listOf(Kana("マ", "マイク", "🎤"), Kana("ミ", "ミルク", "🥛"), Kana("ム", "ハム", "🍖"), Kana("メ", "メロン", "🍈"), Kana("モ", "モノレール", "🚝")),
        listOf(Kana("ヤ", "ヤシのき", "🌴", "ヤシの木"), null, Kana("ユ", "ユーフォー", "🛸"), null, Kana("ヨ", "ヨット", "⛵")),
        listOf(Kana("ラ", "ラーメン", "🍜"), Kana("リ", "リボン", "🎀"), Kana("ル", "ルビー", "💎"), Kana("レ", "レモン", "🍋"), Kana("ロ", "ロボット", "🤖")),
        listOf(Kana("ワ", "ワニ", "🐊"), null, null, null, Kana("ン", "パン", "🍞")),
    )

    override val all: List<Kana> = super.all
}

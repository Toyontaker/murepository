# まなびっこ 🐣

3さいごろの子ども向けの、**ひらがな**と**かず**をおぼえる Android アプリです。
文字がまだ読めなくても遊べるように、ボタンは大きく、すべて**音声で読み上げ**ます(端末の日本語読み上げエンジンを使います)。

| ホーム | ひらがな | かず | なぞりがき | クイズ |
|---|---|---|---|---|
| ![](app/screenshots/home.png) | ![](app/screenshots/hiragana.png) | ![](app/screenshots/counting.png) | ![](app/screenshots/tracing.png) | ![](app/screenshots/quiz.png) |

## あそびかた

- **ひらがな** … 五十音表から文字をタップすると、大きく表示して「あ。あり」のように読み上げます。◀ ▶ でじゅんばんに見られます。46文字すべてに、絵文字つきのことばがあります。
- **かず** … 1〜10 を選ぶと、その数だけりんごなどが出てきます。ひとつずつタップすると「いち、に、さん…」と数え、さいごに「さんこ。よくできたね!」とほめてくれます。できたら自動でつぎの数へ進みます。
- **なぞりがき** … うすく書かれたひらがなを、ゆびでなぞります。線は1本ごとに色が変わります。「できた」で 💮。
- **クイズ** … 「『あ』はどれかな?」「りんごはいくつ?」を3択で出題します(数は 3さい向けに 1〜5)。5問で1ラウンド、1回目で正解すると ⭐ がもらえます。まちがえても、ぷるぷるゆれて「もういっかい!」と言うだけなので、何度でもやり直せます。

## つくり

- Kotlin + Jetpack Compose(外部ライブラリなし、画像素材なし ― イラストは絵文字)
- 音声は Android 標準の `TextToSpeech`(日本語)。ゆっくり・すこし高めの声に設定しています。
- 縦向き・横向き、スマホ・タブレットに対応。あそんでいる間は画面がスリープしません。
- 対応 OS: Android 8.0(API 26)以上

```
app/src/main/java/com/toyontaker/manabikko/
├── MainActivity.kt      画面の切りかえ
├── Speaker.kt           音声読み上げ
├── data/                ひらがな・数のデータ
└── ui/                  各画面(Home / Hiragana / Counting / Tracing / Quiz)
```

## ビルドとインストール

Android Studio でこの `manabikko` フォルダを開いて ▶ 実行するのがかんたんです。
コマンドラインの場合(Android SDK が必要):

```bash
cd manabikko
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

> 音声が出ないときは、端末の「設定 → システム → 言語と入力 → テキスト読み上げの出力」で
> Google 音声サービスなどの日本語音声データが入っているか確認してください。

### スクリーンショットの更新

画面のスクリーンショットは Robolectric + Roborazzi で JVM 上に描画しています(エミュレータ不要)。

```bash
./gradlew recordRoborazziDebug   # app/screenshots/*.png を更新
```

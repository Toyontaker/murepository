# まなびっこ 正式版リリース手順(Google Play)

バージョン 1.0.0(versionCode 1)/ targetSdk 36(Android 16)/ minSdk 26(Android 8.0)

---

## 1. アップロード鍵を つくる(最初の 1回だけ・手元の PC で)

**この鍵を なくすと アプリを 更新できなく なります。** パスワードと あわせて 安全な 場所に バックアップしてください。
(Play App Signing を 使うので、なくした ときは Play Console から アップロード鍵の 再設定を 申請できますが、時間が かかります)

```bash
keytool -genkeypair -v -keystore ~/manabikko-upload.jks \
  -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

`manabikko/keystore.properties` を 作る(**Git には 入りません**。`.gitignore` 済み):

```properties
storeFile=/home/あなた/manabikko-upload.jks
storePassword=(きめた パスワード)
keyAlias=upload
keyPassword=(きめた パスワード)
```

## 2. リリース用 AAB を つくる

```bash
cd manabikko
./gradlew lintRelease testDebugUnitTest bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

## 3. Play Console で アプリを 作成

- アプリ名:まなびっこ ひらがな・カタカナ・かず / 既定の言語:日本語 / アプリ / 無料
- **パッケージ名 `com.toyontaker.manabikko` は 公開後 変更できません。** 変える なら この前に。
- Play App Signing:そのまま 有効(Google が アプリ署名鍵を 管理)

## 4. 「アプリの コンテンツ」の 回答

| 項目 | 回答 |
|---|---|
| プライバシーポリシー | `docs/privacy-policy.html` を 公開した URL(GitHub Pages など)。連絡先メールを 記入してから 公開 |
| 広告 | 広告なし |
| アプリへの アクセス | 制限なし(ログイン不要) |
| コンテンツの レーティング | IARC アンケート:暴力・性的表現・言葉・薬物・ギャンブル・ユーザー間交流・位置共有・デジタル購入 すべて「いいえ」→ 全年齢(IARC 3+ 相当) |
| ターゲット ユーザー | **5歳以下**(+ 必要なら 6〜8歳)。子ども向けなので ファミリー ポリシーが 適用 |
| ファミリー ポリシー | 広告 SDK なし・データ収集なし・外部リンクなし(保護者用の 説明画面にも リンクは なし) |
| データ セーフティ | 「データを 収集しない」「データを 共有しない」。(通信なし・権限なし。`PrivacyGuardTest` が 権限の ふえすぎを 見はっています) |
| ニュース/健康/金融/政府 | いずれも 該当なし |

## 5. ストア掲載情報

- テキスト:`store/listing-ja.md`
- アプリアイコン:`store/icon-512.png`(512×512)
- フィーチャー グラフィック:`store/feature-graphic-1024x500.png`
- スマホ:`store/phone/*.png`(1080×1920、6枚)
- 7インチ / 10インチ タブレット:`store/tablet-7in/*.png`(1200×1920)、`store/tablet-10in/*.png`(1600×2560)
- カテゴリ:教育

画像の 作りなおし:
```bash
./gradlew recordRoborazziDebug --tests '*StoreAssetsTest*'
python3 tools/flatten_store_images.py   # 透過を けして Play の 条件を チェック
```

## 6. テスト → 公開

- **2023年11月13日 以降に 作った 個人の デベロッパー アカウントは、本番公開の 前に
  「クローズド テスト」を 12人以上・14日間 つづける 必要が あります**(組織アカウントは 対象外)。
  知り合いの Android ユーザー 12人以上に テスターとして 参加してもらい、実際に あそんでもらってください。
- 内部テスト → クローズドテスト に AAB を アップロードすると、**プレリリース レポート**
  (Google の 実機で 自動テスト・アクセシビリティ チェック)が 届きます。警告が ないか 確認してください。
- 問題なければ 本番トラックへ。

## 7. 自動テストの 範囲(リリース前に 全部 通ること)

| 種類 | 内容 | 実行 |
|---|---|---|
| lint | debug / release とも 警告ゼロ(warningsAsErrors) | `./gradlew lintDebug lintRelease` |
| ロジック | じょすうし、出題じゅん、採点(全91文字で しきい値を 確認)、書き順ガイド | `testDebugUnitTest` |
| 画面の流れ | ホーム⇔全画面・戻るボタン・ながおし・クイズ5問・かず完走・なぞり はなまる→つぎ・回転 | `testDebugUnitTest`(Robolectric) |
| 画面サイズ | 320×568 / スマホ横 / 7・10インチ 縦横 / 文字サイズ2倍 で ぜんぶの 画面 | `ScreenSizeTest`(`build/screen-sizes/`) |
| プライバシー | 通信・位置・マイク・広告ID などの 権限が ないこと、バックアップ無効 | `PrivacyGuardTest` |
| 実機(エミュレータ) | Android 8.0 と 16 で 端末テスト + 縮小済み リリースAPKに ランダム操作 5000回(クラッシュ/ANR で 失敗) | GitHub Actions `manabikko (Android)` |

## 8. 手動チェックリスト(実機で 1回)

自動テストで 確かめられない「音」「手ざわり」「端末の 機能」を 確認します。

- [ ] 初回起動で「何して遊ぶ?」と 日本語で 読み上げる(声の 抑揚が 自然か)
- [ ] 効果音(ぽん/ドレミ/ピロリン/ぼよよん/ファンファーレ/しゅっ)が 鳴る。マナーモードで 鳴らない
- [ ] 指で なぞった 線が 指に ついてくる(遅れ・とぎれ が ない)。はなまる → 次の文字へ
- [ ] 数字ボタン(1秒)・設定ボタン(1.5秒)が 長押しで だけ 反応する
- [ ] タイトル 3秒 長押しで アプリ固定 → ホーム/履歴ボタンで 外に 出られない → 解除できる
- [ ] 全画面(バーが 隠れる)。端から スワイプで 一時的に 出て、また 隠れる
- [ ] 縦⇔横 回転で 落ちない・状態が 消えない
- [ ] 別アプリに 切り替えると 読み上げが 止まる
- [ ] 読み上げデータが ない 端末で、無音でも 遊べる(ℹ️ 画面に 案内が 出る)
- [ ] Android 8〜9 の 古い 端末で 絵文字が □ に ならない

package com.toyontaker.manabikko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * おとな向けの「このアプリについて」:バージョン、つかいかたの ヒント、プライバシー、クレジット。
 * (こどもが 外部サイトへ 出ていかないよう、リンクは おかず 文字だけ)
 */
@Composable
fun AboutScreen(onHome: () -> Unit, speechReady: Boolean) {
    val context = LocalContext.current
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: ""

    Column(Modifier.fillMaxSize()) {
        TopBar("アプリについて", Palette.Yellow, onHome)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("まなびっこ  バージョン $version") {
                Body("3さいごろの こども向けに、ひらがな・カタカナ・かず を あそびながら おぼえる アプリです。")
            }
            Section("よみあげ") {
                Body(
                    if (speechReady) {
                        "日本語の よみあげが つかえます。"
                    } else {
                        "日本語の よみあげが つかえません。端末の 設定 → システム → 言語と入力 → " +
                            "テキスト読み上げの出力 で、Google 音声サービス などの 日本語音声データを 入れてください。"
                    },
                )
            }
            Section("おとなの かたへ") {
                Body(
                    "・ホームの「まなびっこ」を 3びょう ながおし すると、アプリの固定(こどもが ほかの アプリへ " +
                        "うつれない)を オン/オフ できます。端末の 設定で「アプリ固定」を オンに しておいてください。\n" +
                        "・なぞりがきの 右上(あ→)、かずの 右上(🎲)は 1.5びょう ながおし で 設定を かえられます。",
                )
            }
            Section("プライバシー") {
                Body(
                    "このアプリは 個人情報や 利用データを いっさい あつめず、外部に おくりません。" +
                        "広告・アプリ内課金・インターネット通信は ありません。" +
                        "えらんだ 設定は この端末の なかだけに 保存されます。",
                )
            }
            Section("クレジット・ライセンス") {
                Body(
                    "書き順データ:KanjiVG(© Ulrich Apel, CC BY-SA 3.0, kanjivg.tagaini.net)を 加工して 使用しています。" +
                        "加工した データも CC BY-SA 3.0 で 提供します。\n" +
                        "効果音:このアプリの ために プログラムで 作成した オリジナルです。\n" +
                        "オープンソース:Android Jetpack(AndroidX・Jetpack Compose)、Kotlin — Apache License 2.0。\n" +
                        "絵文字は 端末の 絵文字フォントで 表示しています。",
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, fontSize = 16.sp, color = Palette.Ink)
}

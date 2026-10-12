#!/bin/bash
# エミュレータで:端末テスト → リリースAPK(R8で 縮小した 本番と 同じ もの)に モンキーテスト。
# GitHub Actions の android-emulator-runner から よばれる。
set -euxo pipefail
cd "$(dirname "$0")/.."
PKG=com.toyontaker.manabikko
OUT=build/emulator-results
mkdir -p "$OUT"

# 1) 端末テスト(debug ビルド + androidTest)。失敗しても モンキーテストは 続けて、最後に まとめて 判定する
set +e
./gradlew --no-daemon connectedDebugAndroidTest
DEVICE_TESTS=$?
set -e

# 2) リリースAPKを 入れなおして、ランダム操作を 5000回
adb uninstall "$PKG" || true
adb install -r app/build/outputs/apk/release/app-release.apk
# 古い Android の エミュレータでは ログの 消去が 失敗する ことが あるので、
# かわりに 開始時刻を おぼえて それ以降の ログだけ 見る
adb logcat -c || true
SINCE=$(adb shell date '+%m-%d %H:%M:%S.000' | tr -d '\r')
adb shell am start -W -n "$PKG/.MainActivity"
sleep 5
adb exec-out screencap -p > "$OUT/release-start.png"
set +e
adb shell monkey -p "$PKG" --pct-syskeys 0 --pct-appswitch 0 --pct-anyevent 0 \
  --throttle 40 -s 20261012 -v 5000 > "$OUT/monkey.txt" 2>&1
MONKEY=$?
set -e
adb exec-out screencap -p > "$OUT/release-after-monkey.png"
adb logcat -d -T "$SINCE" > "$OUT/logcat.txt"

# 落ちていないか(モンキーの 完走 と、アプリの クラッシュ / ANR が ないこと)
tail -5 "$OUT/monkey.txt"
if [ $MONKEY -ne 0 ] || ! grep -q "Monkey finished" "$OUT/monkey.txt"; then
  echo "::error::monkey test did not finish"; exit 1
fi
if grep -E "FATAL EXCEPTION|ANR in $PKG" -A 20 "$OUT/logcat.txt" | grep -q "$PKG"; then
  grep -E "FATAL EXCEPTION|ANR in" -A 30 "$OUT/logcat.txt" | head -80
  echo "::error::crash or ANR in release build"; exit 1
fi
echo "release build survived 5000 random events"
if [ $DEVICE_TESTS -ne 0 ]; then
  echo "::error::device tests failed"; exit 1
fi

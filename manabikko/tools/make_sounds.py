"""効果音(res/raw/*.wav)を合成して作るスクリプト。外部素材を使わないため。

    python3 tools/make_sounds.py
"""
import math
import os
import random
import struct
import wave

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def tone(freq, dur, vol=0.5, decay=6.0, harmonics=(1.0, 0.3, 0.1), sweep_to=None):
    """やわらかい ベル/木琴っぽい音。sweep_to で周波数をなめらかに変える。"""
    n = int(RATE * dur)
    out = []
    phase = [0.0] * len(harmonics)
    for i in range(n):
        t = i / RATE
        f = freq if sweep_to is None else freq + (sweep_to - freq) * (i / n)
        attack = min(1.0, t / 0.005)
        env = attack * math.exp(-decay * t)
        s = 0.0
        for k, a in enumerate(harmonics):
            phase[k] += 2 * math.pi * f * (k + 1) / RATE
            s += a * math.sin(phase[k])
        out.append(vol * env * s / sum(harmonics))
    return out


def silence(dur):
    return [0.0] * int(RATE * dur)


def mix(*tracks):
    """(開始秒, サンプル列) を重ねる。"""
    end = max(int(start * RATE) + len(s) for start, s in tracks)
    buf = [0.0] * end
    for start, s in tracks:
        o = int(start * RATE)
        for i, v in enumerate(s):
            buf[o + i] += v
    return buf


def save(name, samples):
    peak = max(1e-9, max(abs(v) for v in samples))
    gain = min(1.0, 0.9 / peak)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(v * gain * 32767)) for v in samples))


C5, D5, E5, G5, A5, C6, E6, G6 = 523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1318.5, 1568.0

# ぽん(ボタンを おしたとき)
save("pop", tone(380, 0.12, decay=30, harmonics=(1.0, 0.2), sweep_to=900))

# ポーン(かぞえるとき。再生速度を変えて ドレミ… にする)
save("ding", tone(C5, 0.6, decay=7, harmonics=(1.0, 0.0, 0.25, 0.0, 0.08)))

# ピロリン♪(せいかい)
save("correct", mix(*[(i * 0.09, tone(f, 0.5, decay=8)) for i, f in enumerate([C5, E5, G5, C6])]))

# ぼよよん(ちがうよ)
save("wrong", mix((0.0, tone(330, 0.18, decay=12, sweep_to=260)), (0.17, tone(260, 0.3, decay=9, sweep_to=200))))

# ファンファーレ(ぜんぶ できた!)
notes = [(0.0, G5), (0.12, G5), (0.24, G5), (0.36, C6), (0.62, A5), (0.74, C6), (0.86, E6)]
fan = [(t, tone(f, 0.35, decay=6)) for t, f in notes]
fan.append((0.86, tone(G6, 0.9, decay=3, vol=0.25)))
fan.append((0.86, tone(C6, 0.9, decay=3, vol=0.3)))
save("fanfare", mix(*fan))

# しゅっ(けす)
random.seed(1)
n = int(RATE * 0.25)
lp = 0.0
sw = []
for i in range(n):
    a = 0.05 + 0.5 * (i / n)  # だんだん 高い音に
    lp += a * (random.uniform(-1, 1) - lp)
    sw.append(0.6 * lp * math.sin(math.pi * i / n))
save("swish", sw)

print("ok:", sorted(os.listdir(OUT)))

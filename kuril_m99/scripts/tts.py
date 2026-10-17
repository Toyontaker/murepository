import json, os, subprocess
from gtts import gTTS
from narration import SCENES
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cache", "tts")
os.makedirs(OUT, exist_ok=True)
meta = {}
for sid, sents in SCENES:
    meta[sid] = []
    for k, s in enumerate(sents):
        mp3 = f"{OUT}/{sid}_{k}.mp3"; wav = f"{OUT}/{sid}_{k}.wav"
        if not os.path.exists(wav):
            gTTS(s, lang="ja").save(mp3)
            # speed up slightly, trim edge silence, mono 44.1k
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp3, "-af",
                "silenceremove=start_periods=1:start_threshold=-45dB,areverse,silenceremove=start_periods=1:start_threshold=-45dB,areverse,atempo=1.2",
                "-ar", "44100", "-ac", "1", wav], check=True)
        d = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", wav]))
        meta[sid].append(d)
json.dump(meta, open(f"{OUT}/durations.json", "w"), indent=1)
print({k: round(sum(v), 1) for k, v in meta.items()}, round(sum(sum(v) for v in meta.values())))

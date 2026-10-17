"""Narration with Gemini TTS. Key is read from the GEMINI_API_KEY env var (never stored in the repo).
Writes cache/tts_gemini/<scene>_<k>.wav + durations.json; render with TTS_DIR=cache/tts_gemini."""
import base64, difflib, json, os, re, subprocess, sys, time, urllib.request, urllib.error
from narration import SCENES

MODEL = os.environ.get("TTS_MODEL", "gemini-3.8-flash-tts")
VOICE = os.environ.get("TTS_VOICE", "Kore")
STYLE = ""  # a style prompt sometimes got read aloud, so send the plain text only
CHECK_MODEL = os.environ.get("CHECK_MODEL", "gemini-3.8-flash")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cache", "tts_gemini")
os.makedirs(OUT, exist_ok=True)
KEY = os.environ["GEMINI_API_KEY"]
URL = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent"

def synth(text):
    body = json.dumps({"contents": [{"parts": [{"text": STYLE + text}]}],
        "generationConfig": {"responseModalities": ["AUDIO"],
            "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": VOICE}}}}}).encode()
    for attempt in range(4):
        req = urllib.request.Request(URL, body, {"Content-Type": "application/json", "x-goog-api-key": KEY})
        try:
            d = json.load(urllib.request.urlopen(req, timeout=180))
            part = d["candidates"][0]["content"]["parts"][0]["inlineData"]
            return base64.b64decode(part["data"])
        except urllib.error.HTTPError as e:
            msg = e.read().decode()
            if e.code == 429 and "PerDay" in msg:
                sys.exit(f"daily quota exhausted for {MODEL}: {msg}")
            print("retry", e.code, msg[:150]); time.sleep(5 * 2 ** attempt)
        except (KeyError, IndexError) as e:
            print("no audio in response, retry"); time.sleep(3)
    sys.exit("failed: " + text)

def post(url, body):
    req = urllib.request.Request(url, json.dumps(body).encode(), {"Content-Type": "application/json", "x-goog-api-key": KEY})
    return json.load(urllib.request.urlopen(req, timeout=180))

def transcribe(wav):
    mp3 = wav[:-4] + ".chk.mp3"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ar", "16000", "-b:a", "32k", mp3], check=True)
    data = base64.b64encode(open(mp3, "rb").read()).decode(); os.remove(mp3)
    for attempt in range(4):
        try:
            d = post(f"https://generativelanguage.googleapis.com/v1beta/models/{CHECK_MODEL}:generateContent",
                {"contents": [{"parts": [{"inline_data": {"mime_type": "audio/mp3", "data": data}},
                 {"text": "この音声を一字一句そのまま文字起こししてください。英語も含めて全て書き、文字起こしだけを出力してください。"}]}]})
            return d["candidates"][0]["content"]["parts"][0]["text"]
        except Exception as e:
            print("transcribe retry", e); time.sleep(5 * 2 ** attempt)
    return ""

def norm(s):
    return re.sub(r"[\s、。，．,.!！?？「」『』（）()・:：]", "", s)

def matches(expected, heard):
    a, b = norm(expected), norm(heard)
    r = difflib.SequenceMatcher(None, a, b).ratio()
    return r >= 0.8 and len(b) <= len(a) * 1.25, r

only = sys.argv[1:]  # optional "scene_k" ids for a quick test
meta = {}
for sid, sents in SCENES:
    meta[sid] = []
    for k, s in enumerate(sents):
        tag = f"{sid}_{k}"; wav = f"{OUT}/{tag}.wav"
        if only and tag not in only: continue
        ok_flag = f"{OUT}/{tag}.verified"
        for attempt in range(6):
            if os.path.exists(wav) and os.path.exists(ok_flag): break
            if os.path.exists(wav):
                good, r = matches(s, heard := transcribe(wav))
                if good:
                    open(ok_flag, "w").write(heard); break
                print(f"mismatch {tag} ({r:.2f}): {heard.strip()[:80]}", flush=True); os.remove(wav)
            pcm = f"{OUT}/{tag}.pcm"
            open(pcm, "wb").write(synth(s))
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", "24000", "-ac", "1", "-i", pcm,
                "-af", "silenceremove=start_periods=1:start_threshold=-45dB,areverse,silenceremove=start_periods=1:start_threshold=-45dB,areverse",
                "-ar", "44100", "-ac", "1", wav], check=True)
            os.remove(pcm); print("generated", tag, flush=True)
        else:
            sys.exit(f"could not get a matching clip for {tag}")
        meta[sid].append(float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", wav])))
if not only:
    json.dump(meta, open(f"{OUT}/durations.json", "w"), indent=1)
    print({k: round(sum(v), 1) for k, v in meta.items()}, round(sum(sum(v) for v in meta.values())))

"""キーワード別年次件数のみを取得 (arxiv_counts.py の KEYWORDS を分割並列で処理)。
使い方: python3 scripts/arxiv_keywords.py <出力json> <part> <nparts>"""
import json, sys, time
sys.path.insert(0, "scripts")
from arxiv_counts import KEYWORDS, YEARS, search_total

out_path, part, nparts = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
names = [n for i, n in enumerate(KEYWORDS) if i % nparts == part]
out = {}
for n in names:
    out[n] = {}
    for y in YEARS:
        out[n][str(y)] = search_total(KEYWORDS[n], y)
        time.sleep(2)
    print(n, out[n], flush=True)
    json.dump(out, open(out_path, "w"), ensure_ascii=False, indent=1)
print("DONE", flush=True)

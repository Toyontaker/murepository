"""scripts/report_template.html に data/*.json を埋め込み、report.html を生成する。
使い方: python3 scripts/build_html.py [出力パス] [--fragment]
  --fragment: <!doctype>/<html>/<head>/<body> を付けない (Artifact 公開用)"""
import json, sys

c = json.load(open("data/arxiv_counts.json"))
r = json.load(open("data/recent_analysis.json"))
cs = c["lists"]["cs"]
data = {
    "lists": c["lists"],
    "share": {n: {y: round(100 * v / cs[y], 2) for y, v in d.items() if v is not None}
              for n, d in c["keywords"].items()},
    "topic_share": r["topic_share"],
    "emerging": r["emerging_share"],
    "hf_emerging": r["hf_emerging_share"],
    "hf_top": r["hf_top"],
    "dedup_n": r["dedup_n"],
}
tpl = open("scripts/report_template.html", encoding="utf-8").read()
tpl = tpl.replace("/*__DATA__*/null", json.dumps(data, ensure_ascii=False, separators=(",", ":")))
head, body = tpl.split("<!--BODY-->")
out = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else "report.html"
if "--fragment" in sys.argv:
    html = head + body
else:
    html = ('<!doctype html>\n<html lang="ja">\n<head>\n<meta charset="utf-8">\n'
            '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
            + head + "</head>\n<body>\n" + body + "</body>\n</html>\n")
open(out, "w", encoding="utf-8").write(html)
print(out, len(html))

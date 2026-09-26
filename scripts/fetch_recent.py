"""最新論文の収集:
 (a) arXiv 月別一覧ページから、指定月の全タイトル (cs.LG/cs.AI/cs.CL/cs.CV) を取得
 (b) Hugging Face Daily Papers API から日別の注目論文 (要旨・upvote付き) を取得
使い方: python3 scripts/fetch_recent.py
"""
import datetime, html, json, re, sys, time, urllib.request

UA = {"User-Agent": "ai-ml-trends-report/1.0 (research; low rate)"}
CATS = ["cs.LG", "cs.AI", "cs.CL", "cs.CV"]
MONTHS = ["2024-09", "2025-09", "2026-09"]

def fetch(url, retries=5):
    for i in range(retries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=90) as r:
                return r.read().decode("utf-8", "replace")
        except Exception as e:
            print("retry", i, url, e, file=sys.stderr, flush=True)
            time.sleep(15 * (i + 1))
    return ""

def arxiv_month_titles(cat, month):
    items, skip, total = {}, 0, None
    while total is None or skip < total:
        page = fetch(f"https://arxiv.org/list/{cat}/{month}?skip={skip}&show=2000")
        m = re.search(r"Total of ([\d,]+) entries", page)
        total = int(m.group(1).replace(",", "")) if m else 0
        ids = re.findall(r'href\s*=\s*"/abs/([\d.]+)"', page)
        titles = re.findall(r"<span class='descriptor'>Title:</span>\s*(.*?)\s*</div>", page, re.S)
        for i, t in zip(ids, titles):
            items[i] = html.unescape(re.sub(r"\s+", " ", t)).strip()
        skip += 2000
        time.sleep(5)
    return total, items

def hf_daily(start, end):
    out, d = [], start
    while d <= end:
        raw = fetch(f"https://huggingface.co/api/daily_papers?date={d.isoformat()}&limit=100")
        try:
            for x in json.loads(raw or "[]"):
                p = x.get("paper", {})
                out.append({"id": p.get("id"), "date": d.isoformat(), "title": p.get("title"),
                            "summary": p.get("summary"), "upvotes": p.get("upvotes"),
                            "ai_keywords": p.get("ai_keywords")})
        except Exception as e:
            print("hf parse error", d, e, file=sys.stderr)
        d += datetime.timedelta(days=1)
        time.sleep(1)
    return out

def main():
    res = {"generated": time.strftime("%Y-%m-%d"), "arxiv": {}}
    for month in MONTHS:
        for cat in CATS:
            total, items = arxiv_month_titles(cat, month)
            res["arxiv"][f"{cat}/{month}"] = {"total": total, "titles": items}
            print(cat, month, total, len(items), flush=True)
            json.dump(res, open("data/recent_titles.json", "w"), ensure_ascii=False)
    hf = hf_daily(datetime.date(2026, 8, 1), datetime.date(2026, 9, 25))
    json.dump(hf, open("data/hf_daily_papers_2026-08_09.json", "w"), ensure_ascii=False, indent=0)
    print("hf", len(hf), flush=True)

if __name__ == "__main__":
    main()

"""arXiv の一覧ページ・詳細検索ページの件数表示から、AI/ML 関連の年別投稿数と
キーワード別件数を集計する (export.arxiv.org API がレート制限で使えない環境向け)。

使い方: python3 scripts/arxiv_counts.py data/arxiv_counts.json
"""
import json, re, sys, time, urllib.parse, urllib.request

UA = {"User-Agent": "ai-ml-trends-report/1.0 (research; low rate)"}
YEARS = list(range(2006, 2027))
LIST_CATS = ["cs", "cs.LG", "cs.AI", "cs.CL", "cs.CV", "stat.ML", "cs.RO", "cs.MA"]
# 詳細検索は abstract 検索 (cs + stat アーカイブ、クロスリスト含む)。
KEYWORDS = {
    "SVM / カーネル法": ['"support vector"', '"kernel methods"'],
    "グラフィカルモデル / 変分推論": ['"graphical model"', '"variational inference"'],
    "深層学習": ['"deep learning"', '"deep neural"'],
    "CNN (畳み込み)": ['convolutional'],
    "RNN / LSTM": ['"recurrent neural"', 'LSTM'],
    "GAN": ['"generative adversarial"'],
    "強化学習": ['"reinforcement learning"'],
    "グラフニューラルネット": ['"graph neural"'],
    "Transformer / Attention": ['transformer', '"self-attention"'],
    "自己教師あり / 対照学習": ['"self-supervised"', '"contrastive learning"'],
    "大規模言語モデル (LLM)": ['"large language model"', 'LLM', 'LLMs'],
    "拡散モデル": ['"diffusion model"', '"diffusion models"'],
    "マルチモーダル / VLM": ['multimodal', '"vision-language"'],
    "エージェント (LLM/AI agent)": ['agentic', '"LLM agents"', '"AI agents"', '"LLM-based agents"'],
    "推論 (reasoning)": ['reasoning'],
    "RAG (検索拡張生成)": ['"retrieval-augmented"', '"retrieval augmented"'],
    "解釈性 / 説明可能AI": ['interpretability', 'explainable'],
    "連合学習": ['"federated learning"'],
    "Mixture of Experts": ['"mixture of experts"', '"mixture-of-experts"'],
    "状態空間モデル / Mamba": ['"state space model"', 'Mamba'],
    "世界モデル": ['"world model"', '"world models"'],
    "Embodied / ロボット基盤モデル": ['embodied', '"vision-language-action"'],
    "量子化 / 蒸留 / 効率化": ['quantization', '"knowledge distillation"'],
    "RLHF / 選好最適化": ['RLHF', '"preference optimization"', '"human feedback"'],
    "テスト時計算 / 思考連鎖": ['"chain-of-thought"', '"test-time"'],
}

def fetch(url, retries=6):
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read().decode("utf-8", "replace")
        except Exception as e:
            print("retry", i, url[:90], e, file=sys.stderr, flush=True)
            time.sleep(10 * (i + 1))
    return ""

def list_total(cat, year):
    html = fetch(f"https://arxiv.org/list/{cat}/{year}?skip=0&show=25")
    m = re.search(r"Total of ([\d,]+) entries", html)
    return int(m.group(1).replace(",", "")) if m else None

def search_total(terms, year):
    """terms は OR 結合。件数を返す (0件ページも判定)。"""
    params = [("advanced", ""), ("classification-computer_science", "y"),
              ("classification-statistics", "y"),
              ("classification-physics_archives", "all"),
              ("classification-include_cross_list", "include"),
              ("date-filter_by", "specific_year"), ("date-year", str(year)),
              ("date-date_type", "submitted_date"), ("abstracts", "hide"),
              ("size", "25"), ("order", "-announced_date_first")]
    for i, t in enumerate(terms):
        params += [(f"terms-{i}-operator", "AND" if i == 0 else "OR"),
                   (f"terms-{i}-term", t), (f"terms-{i}-field", "abstract")]
    html = fetch("https://arxiv.org/search/advanced?" + urllib.parse.urlencode(params))
    m = re.search(r"of ([\d,]+) results", html)
    if m:
        return int(m.group(1).replace(",", ""))
    if "Sorry, your query" in html or "no results" in html.lower():
        return 0
    return None

def main(path):
    try:
        out = json.load(open(path))
    except Exception:
        out = {"lists": {}, "keywords": {}}
    out["generated"] = time.strftime("%Y-%m-%d")
    for c in LIST_CATS:
        d = out["lists"].setdefault(c, {})
        for y in YEARS:
            if d.get(str(y)) is None:
                d[str(y)] = list_total(c, y); time.sleep(3)
        print(c, d, flush=True)
        json.dump(out, open(path, "w"), ensure_ascii=False, indent=1)
    for name, terms in KEYWORDS.items():
        d = out["keywords"].setdefault(name, {})
        for y in YEARS:
            if d.get(str(y)) is None:
                d[str(y)] = search_total(terms, y); time.sleep(3)
        print(name, d, flush=True)
        json.dump(out, open(path, "w"), ensure_ascii=False, indent=1)

if __name__ == "__main__":
    main(sys.argv[1])

"""最新タイトルのキーワード出現率を 2024-09 / 2025-09 / 2026-09 で比較し、
HF Daily Papers の上位論文を抽出する。結果は data/recent_analysis.json に出力。"""
import collections, json, re

TOPICS = {
    "LLM / 言語モデル": r"\b(llms?|large language models?|language models?)\b",
    "エージェント / Agentic": r"\b(agents?|agentic|multi-agent)\b",
    "推論 / Reasoning": r"\breason(ing|ers?)?\b",
    "強化学習 (RL / RLVR / GRPO)": r"\b(reinforcement learning|rl|rlhf|rlvr|grpo|policy optimization)\b",
    "拡散 / フローマッチング": r"\b(diffusion|flow matching|rectified flow|denoising)\b",
    "マルチモーダル / VLM / MLLM": r"\b(multimodal|multi-modal|vision-language|vlms?|mllms?|lmms?)\b",
    "動画生成・理解": r"\bvideos?\b",
    "3D / 4D / Gaussian Splatting": r"\b(3d|4d|gaussian splatting|nerf)\b",
    "世界モデル": r"\bworld models?\b",
    "ロボット / Embodied / VLA": r"\b(robot\w*|embodied|vla|vision-language-action|manipulation)\b",
    "RAG / 検索": r"\b(retrieval|rag)\b",
    "安全性 / アラインメント": r"\b(safety|safe|alignment|jailbreak\w*|red[- ]teaming|harmful)\b",
    "解釈性 / Mechanistic": r"\b(interpretab\w*|explainab\w*|mechanistic|sparse autoencoders?|circuits?)\b",
    "ベンチマーク / 評価": r"\b(benchmark\w*|evaluat\w*)\b",
    "効率化 (量子化・蒸留・推論高速化)": r"\b(quantiz\w*|distill\w*|pruning|efficient|kv cache|speculative decoding|sparse attention)\b",
    "Transformer / Attention": r"\b(transformers?|attention)\b",
    "状態空間 / 線形注意 (Mamba等)": r"\b(mamba|state[- ]space|linear attention|ssms?)\b",
    "Mixture of Experts": r"\b(mixture[- ]of[- ]experts|moe)\b",
    "グラフ (GNN)": r"\b(graph neural|gnns?)\b",
    "連合学習 / プライバシー": r"\b(federated|differential privacy|privacy)\b",
    "コード生成 / SWE": r"\b(code generation|coding|software engineering|program synthesis|swe)\b",
    "数学 / 定理証明": r"\b(math\w*|theorem|lean|proofs?)\b",
    "科学のためのAI (AI4Science)": r"\b(protein|molecul\w*|materials?|chemistry|drug|climate|weather|physics-informed|scientific)\b",
    "医療 / ヘルスケア": r"\b(medical|clinical|health\w*|patients?|radiology|pathology)\b",
    "テスト時計算 / CoT": r"\b(test-time|chain-of-thought|cot|thinking)\b",
    "合成データ": r"\bsynthetic data\b",
    "トークナイズ / 長文脈": r"\b(long[- ]context|tokeniz\w*|context window)\b",
    "音声 / Speech": r"\b(speech|audio|asr|tts)\b",
}

def main():
    rt = json.load(open("data/recent_titles.json"))["arxiv"]
    months = sorted({k.split("/")[1] for k in rt})
    res = {"monthly_totals": {}, "topic_share": {}, "dedup_n": {}}
    for m in months:
        titles = {}
        for k, v in rt.items():
            if k.endswith(m):
                res["monthly_totals"][k] = v["total"]
                titles.update(v["titles"])
        res["dedup_n"][m] = len(titles)
        low = [t.lower() for t in titles.values()]
        for name, pat in TOPICS.items():
            rx = re.compile(pat)
            share = sum(1 for t in low if rx.search(t)) / max(1, len(low))
            res["topic_share"].setdefault(name, {})[m] = round(100 * share, 2)
    try:
        hf = json.load(open("data/hf_daily_papers_2026-08_09.json"))
        seen, uniq = set(), []
        for p in hf:
            if p["id"] and p["id"] not in seen:
                seen.add(p["id"]); uniq.append(p)
        res["hf_n"] = len(uniq)
        res["hf_top"] = [{k: p[k] for k in ("id", "title", "upvotes", "date")}
                         for p in sorted(uniq, key=lambda p: -(p["upvotes"] or 0))[:40]]
        kw = collections.Counter()
        for p in uniq:
            for k in set(x.lower() for x in (p.get("ai_keywords") or [])):
                kw[k] += 1
        res["hf_keywords"] = kw.most_common(60)
        low = [((p["title"] or "") + " " + (p["summary"] or "")).lower() for p in uniq]
        res["hf_topic_share"] = {n: round(100 * sum(1 for t in low if re.search(pat, t)) / max(1, len(low)), 1)
                                 for n, pat in TOPICS.items()}
    except FileNotFoundError:
        pass
    json.dump(res, open("data/recent_analysis.json", "w"), ensure_ascii=False, indent=1)
    for n, d in sorted(res["topic_share"].items(), key=lambda x: -x[1].get(months[-1], 0)):
        print(f"{n:32s}", d)

if __name__ == "__main__":
    main()

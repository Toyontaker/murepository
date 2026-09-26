"""data/*.json から図 (figures/*.png) を生成する。"""
import json
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager

font_manager.fontManager.addfont("/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf")
plt.rcParams.update({
    "font.family": "IPAGothic", "font.size": 10,
    "figure.facecolor": "#fcfcfb", "axes.facecolor": "#fcfcfb",
    "axes.edgecolor": "#898781", "axes.labelcolor": "#52514e",
    "xtick.color": "#52514e", "ytick.color": "#52514e", "text.color": "#0b0b0b",
    "axes.spines.top": False, "axes.spines.right": False,
    "axes.grid": True, "grid.color": "#e6e5e0", "grid.linewidth": 0.8,
    "lines.linewidth": 2, "legend.frameon": False,
})
SERIES = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300", "#4a3aa7", "#e34948"]
ANNUALIZE_2026 = 365 / 268  # 2026-01-01〜09-25 の 268 日分を年換算

counts = json.load(open("data/arxiv_counts.json"))
years = list(range(2006, 2027))

def series(d):
    return [d.get(str(y)) for y in years]

# 図1: カテゴリ別年間件数
fig, ax = plt.subplots(figsize=(9, 5))
for i, c in enumerate(["cs.LG", "cs.AI", "cs.CV", "cs.CL", "stat.ML", "cs.RO"]):
    d = counts["lists"].get(c)
    if not d:
        continue
    v = series(d)
    ax.plot(years[:-1], v[:-1], color=SERIES[i], label=c, marker="o", markersize=3)
    if v[-1] is not None:
        est = v[-1] * ANNUALIZE_2026
        ax.plot([2025, 2026], [v[-2], est], color=SERIES[i], linestyle=":")
        ax.plot([2026], [est], marker="o", markersize=6, markerfacecolor="#fcfcfb", color=SERIES[i])
        ax.annotate(c, (2026, est), xytext=(6, 0), textcoords="offset points", va="center", color="#52514e")
ax.set_xlim(2005.5, 2027.3)
ax.xaxis.set_major_locator(matplotlib.ticker.MultipleLocator(2))
ax.set_title("arXiv の分野別 年間掲載件数 (2006〜2026)", loc="left", fontsize=13)
ax.set_ylabel("件数 / 年")
ax.yaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda x, _: f"{int(x):,}"))
ax.legend(loc="upper left", ncol=2)
fig.text(0.01, 0.01, "出典: arXiv 一覧ページ (arxiv.org/list/<cat>/<year>) の件数。2026年は9/25までの実績を年換算した推計(白抜き・点線)。",
         fontsize=8, color="#898781")
fig.tight_layout(rect=(0, 0.03, 1, 1))
fig.savefig("figures/fig1_category_growth.png", dpi=150)

# 図2: キーワード比率 (cs 全体件数に対する割合) の小多面図
panels = [
    ("① 統計的機械学習〜深層学習初期", ["SVM / カーネル法", "グラフィカルモデル / 変分推論", "CNN (畳み込み)", "RNN / LSTM", "GAN"]),
    ("② 深層学習の汎用化", ["深層学習", "Transformer / Attention", "強化学習", "グラフニューラルネット", "自己教師あり / 対照学習"]),
    ("③ 基盤モデル・生成AI", ["大規模言語モデル (LLM)", "拡散モデル", "マルチモーダル / VLM", "RAG (検索拡張生成)", "Mixture of Experts"]),
    ("④ 2024年以降の新潮流", ["推論 (reasoning)", "エージェント (LLM/AI agent)", "テスト時計算 / 思考連鎖", "RLHF / 選好最適化", "世界モデル"]),
]
cs_total = counts["lists"]["cs"]
kw = counts.get("keywords", {})
fig, axes = plt.subplots(2, 2, figsize=(12, 8), sharex=True)
for ax, (title, names) in zip(axes.flat, panels):
    for i, n in enumerate(names):
        d = kw.get(n)
        if not d:
            continue
        pts = [(y, 100 * d[str(y)] / cs_total[str(y)]) for y in years
               if d.get(str(y)) is not None and cs_total.get(str(y))]
        if not pts:
            continue
        ax.plot([p[0] for p in pts], [p[1] for p in pts], color=SERIES[i], label=n)
    ax.set_title(title, loc="left", fontsize=11)
    ax.xaxis.set_major_locator(matplotlib.ticker.MultipleLocator(4))
    ax.yaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda x, _: f"{x:g}%"))
    ax.legend(fontsize=8, loc="upper left")
fig.suptitle("研究テーマの盛衰: 要旨にキーワードを含む論文の割合 (arXiv cs 全体の件数に対する比)", x=0.01, ha="left", fontsize=13)
fig.text(0.01, 0.005, "出典: arXiv 詳細検索 (abstract, cs+stat, クロスリスト含む, 投稿年) の件数 ÷ arXiv cs 年間掲載件数。2026年は9/25まで。比率は目安(分母と分子の範囲は完全には一致しない)。",
         fontsize=8, color="#898781")
fig.tight_layout(rect=(0, 0.02, 1, 0.96))
fig.savefig("figures/fig2_keyword_trends.png", dpi=150)

# 図3: 直近3年の9月 タイトルに含まれるトピック比率
ra = json.load(open("data/recent_analysis.json"))
ts = ra["topic_share"]
months = ["2024-09", "2025-09", "2026-09"]
top = sorted(ts, key=lambda n: -ts[n]["2026-09"])[:18]
top = top[::-1]
fig, ax = plt.subplots(figsize=(9, 8))
shades = ["#b7d3f6", "#5598e7", "#184f95"]
h = 0.26
for j, m in enumerate(months):
    ax.barh([k + (j - 1) * h for k in range(len(top))], [ts[n][m] for n in top],
            height=h - 0.03, color=shades[j], label=m)
for k, n in enumerate(top):
    ax.annotate(f"{ts[n]['2026-09']:.1f}%", (ts[n]["2026-09"], k + h), xytext=(4, 0),
                textcoords="offset points", va="center", fontsize=8, color="#52514e")
ax.set_yticks(range(len(top)), top)
ax.grid(axis="y", visible=False)
ax.xaxis.set_major_locator(matplotlib.ticker.MultipleLocator(2))
ax.xaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda x, _: f"{x:.0f}%"))
ax.set_title("最新投稿のトピック構成: 9月投稿論文のタイトルに占める割合", loc="left", fontsize=13)
ax.legend(loc="lower right", title="投稿月")
n = ra["dedup_n"]
fig.text(0.01, 0.01, f"出典: arXiv 月別一覧 cs.LG/cs.AI/cs.CL/cs.CV の重複除去タイトル (n = {n['2024-09']:,} / {n['2025-09']:,} / {n['2026-09']:,})。2026-09 は25日まで。",
         fontsize=8, color="#898781")
fig.tight_layout(rect=(0, 0.03, 1, 1))
fig.savefig("figures/fig3_recent_topics.png", dpi=150)
print("ok")

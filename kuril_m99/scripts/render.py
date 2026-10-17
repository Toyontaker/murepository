"""Render the hypothetical Kuril M9.9 scenario video (1280x720, Japanese narration)."""
import json, math, os, subprocess, wave
import numpy as np
from functools import lru_cache
from matplotlib import font_manager
from matplotlib.figure import Figure
from matplotlib.backends.backend_agg import FigureCanvasAgg
from matplotlib.patches import Polygon as MPoly, FancyBboxPatch
from matplotlib.path import Path
from PIL import Image, ImageDraw, ImageFont
import geo
from narration import SCENES

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
TTS = os.path.join(ROOT, "cache", "tts")
OUT = os.path.join(ROOT, "kuril_m99_scenario.mp4")
W, H, FPS = 1280, 720, 24
FONT = "/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf"
font_manager.fontManager.addfont(FONT)
import matplotlib, logging
logging.getLogger("matplotlib.font_manager").setLevel(logging.ERROR)
matplotlib.rcParams["font.family"] = font_manager.FontProperties(fname=FONT).get_name()

BG, PANEL, FG, MUTED = "#0b1220", "#152038", "#e8edf5", "#8b97ad"
RED, AMBER, CYAN, GREEN, LAND, SEA = "#ff5a5f", "#ffb547", "#4cc9f0", "#5dd39e", "#26324a", "#0e1a2e"
LEAD, GAP, TAIL = 0.6, 0.35, 1.0

# ---------- helpers ----------
def new_fig():
    fig = Figure(figsize=(W / 100, H / 100), dpi=100, facecolor=BG)
    FigureCanvasAgg(fig)
    return fig

def to_arr(fig):
    fig.canvas.draw()
    return np.asarray(fig.canvas.buffer_rgba())[:, :, :3].copy()

def header(fig, kicker, title):
    fig.text(0.04, 0.935, kicker, color=AMBER, fontsize=15, va="center")
    fig.text(0.04, 0.88, title, color=FG, fontsize=28, va="center", weight="bold")

def footer_note(fig, s):
    fig.text(0.96, 0.175, s, color=MUTED, fontsize=10, ha="right", va="bottom")

@lru_cache(None)
def polys(res):
    return geo.land_polys(res)

def draw_land(ax, res, x0, x1, y0, y1, color=LAND, edge="#3b4a68", z=1):
    for p in polys(res):
        if p[:, 0].max() < x0 or p[:, 0].min() > x1 or p[:, 1].max() < y0 or p[:, 1].min() > y1:
            continue
        ax.add_patch(MPoly(p, closed=True, fc=color, ec=edge, lw=0.6, zorder=z))

def map_ax(fig, rect, x0, x1, y0, y1, res="50m"):
    ax = fig.add_axes(rect)
    ax.set_facecolor(SEA); ax.set_xlim(x0, x1); ax.set_ylim(y0, y1)
    ax.set_aspect(1 / math.cos(math.radians((y0 + y1) / 2)))
    ax.set_xticks([]); ax.set_yticks([])
    for s in ax.spines.values(): s.set_color("#33415c")
    draw_land(ax, res, x0, x1, y0, y1)
    return ax

def star(ax, label=None, size=420):
    ax.scatter([geo.EPI[0]], [geo.EPI[1]], marker="*", s=size, c=RED, ec="white", lw=1.2, zorder=10)
    if label:
        ax.text(geo.EPI[0] + 0.8, geo.EPI[1] + 0.6, label, color=FG, fontsize=13, zorder=11, weight="bold")

def rupture_outline(ax, fill=None):
    ax.add_patch(MPoly(geo.RUPTURE, closed=True, fc=fill or "none", ec=RED, lw=2, ls="-", zorder=6, alpha=0.95))
    t = np.array(geo.TRENCH)
    ax.plot(t[:, 0] + 0.5, t[:, 1] - 0.4, color=MUTED, lw=1.5, ls=(0, (6, 4)), zorder=5)

def card(fig, x, y, w, h, fc=PANEL, ec="#2a3a5c", lw=1.2):
    fig.add_artist(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0,rounding_size=0.012",
                                  transform=fig.transFigure, fc=fc, ec=ec, lw=lw, zorder=0))

# ---------- subtitles ----------
SUB_FONT = ImageFont.truetype(FONT, 30)

def wrap(text, maxw):
    if SUB_FONT.getlength(text) <= maxw:
        return [text]
    if SUB_FONT.getlength(text) <= 2 * maxw - 60:
        # two balanced lines, preferring a break after a comma near the middle
        n = len(text); best = None
        for i in range(1, n):
            a, b = text[:i], text[i:]
            if SUB_FONT.getlength(a) > maxw or SUB_FONT.getlength(b) > maxw or b[0] in "、。」）":
                continue
            score = abs(SUB_FONT.getlength(a) - SUB_FONT.getlength(b)) - (380 if a[-1] in "、。" else 0)
            if best is None or score < best[0]:
                best = (score, i)
        if best:
            return [text[:best[1]], text[best[1]:]]
    lines, cur = [], ""
    for ch in text:
        if SUB_FONT.getlength(cur + ch) > maxw:
            lines.append(cur); cur = ch
        else:
            cur += ch
    if cur: lines.append(cur)
    # don't start a line with punctuation
    for i in range(1, len(lines)):
        while lines[i] and lines[i][0] in "、。」）":
            lines[i - 1] += lines[i][0]; lines[i] = lines[i][1:]
    return [l for l in lines if l]

def with_sub(arr, text):
    if not text: return arr
    img = Image.fromarray(arr).convert("RGBA")
    lines = wrap(text, W - 140)
    lh = 42; bh = lh * len(lines) + 22
    ov = Image.new("RGBA", img.size, (0, 0, 0, 0)); d = ImageDraw.Draw(ov)
    y0 = H - bh - 14
    d.rounded_rectangle((50, y0, W - 50, y0 + bh), 14, fill=(0, 0, 0, 175))
    for i, l in enumerate(lines):
        tw = SUB_FONT.getlength(l)
        d.text(((W - tw) / 2, y0 + 11 + i * lh), l, font=SUB_FONT, fill=(255, 255, 255, 255))
    return np.asarray(Image.alpha_composite(img, ov).convert("RGB"))

# ---------- scenes: each returns frame(t, D, k) ; k = current sentence index ----------
def scene_title():
    @lru_cache(None)
    def base():
        fig = new_fig()
        ax = map_ax(fig, [0.42, 0.12, 0.6, 0.88], 130, 172, 32, 60)
        ax.set_facecolor(BG)
        for s in ax.spines.values(): s.set_visible(False)
        star(ax, "推定震源（誤報）")
        for r, a in [(2.0, .7), (4.0, .45), (6.5, .25), (9.5, .12)]:
            ax.add_patch(matplotlib.patches.Ellipse(geo.EPI, r * 2 / math.cos(math.radians(48.2)), r * 2,
                         fc="none", ec=RED, lw=1.5, alpha=a, zorder=9))
        fig.text(0.05, 0.78, "仮想シナリオ", color=AMBER, fontsize=20)
        fig.text(0.05, 0.64, "千島列島 M9.9", color=FG, fontsize=58, weight="bold")
        fig.text(0.05, 0.53, "もし昨日の“誤報”が現実だったら", color=FG, fontsize=28)
        fig.text(0.05, 0.43, "2026年10月16日の緊急地震速報（取消）をもとにした想定", color=MUTED, fontsize=15)
        return to_arr(fig)
    return lambda t, D, k: base()

def scene_what():
    rows = [("12:38:27", "緊急地震速報 第1報", "当初の推定は M6.2"),
            ("12:39:23", "規模を M9.9 に更新", "北緯48.2° 東経153.4° 深さ140km"),
            ("12:40", "警報（北海道〜東海）", "根室地方で最大震度7の予想"),
            ("12:48", "警報を取り消し", "警報の取消は初めて"),
            ("実際", "函館で震度1程度", "気象庁「規模を過大に推定」")]
    @lru_cache(None)
    def base(k):
        fig = new_fig(); header(fig, "2026年10月16日 昼", "昨日、何が起きたか")
        for i, (tm, a, b) in enumerate(rows):
            y = 0.71 - i * 0.105; on = i <= k; cur = i == k
            card(fig, 0.04, y, 0.5, 0.088, fc="#1d2c4d" if cur else PANEL, ec=AMBER if cur else "#2a3a5c")
            col = (RED if i in (1, 2) else CYAN if i == 3 else GREEN if i == 4 else FG) if on else "#4a5670"
            fig.text(0.055, y + 0.044, tm, color=col, fontsize=17, weight="bold", va="center")
            fig.text(0.16, y + 0.058, a, color=FG if on else "#4a5670", fontsize=16, va="center")
            fig.text(0.16, y + 0.024, b, color=MUTED if on else "#3a4560", fontsize=12, va="center")
        ax = map_ax(fig, [0.57, 0.19, 0.4, 0.72], 136, 166, 34, 56)
        star(ax, "推定震源（誤）")
        if k >= 4:
            ax.text(150.5, 36.0, "実際の地震：\nカムチャツカ半島付近\nM6.9程度・深さ約160km", color=GREEN, fontsize=12, zorder=12, linespacing=1.4)
        ax.text(141.0, 41.0, "北海道", color=MUTED, fontsize=11, zorder=12)
        return to_arr(fig)
    return lambda t, D, k: base(k)

def scene_scale():
    data = [("1995 阪神・淡路 M7.3", 10 ** (1.5 * (7.3 - 9.0)), MUTED),
            ("2025 カムチャツカ M8.8", 10 ** (1.5 * (8.8 - 9.0)), CYAN),
            ("2011 東日本 M9.0", 1.0, CYAN),
            ("1960 チリ M9.5（観測史上最大）", 10 ** (1.5 * 0.5), AMBER),
            ("想定 千島列島 M9.9", 10 ** (1.5 * 0.9), RED)]
    def frame(t, D, k):
        g = min(1.0, max(0.0, (t - 0.5) / 2.5)); g = 1 - (1 - g) ** 3
        fig = new_fig(); header(fig, "M9.9はどれくらい大きいか", "地震のエネルギーを比べると")
        ax = fig.add_axes([0.34, 0.24, 0.58, 0.56]); ax.set_facecolor(BG)
        for i, (lab, v, c) in enumerate(data):
            y = len(data) - 1 - i
            ax.barh(y, v * g, color=c, height=0.62)
            txt = "約1/8000" if v < 0.01 else f"×{v:.1f}" if v < 1 else ("基準 ×1" if v == 1 else f"×{v:.0f}")
            ax.text(max(v * g, 0.05) + 0.35, y, txt, color=FG, va="center", fontsize=17, weight="bold")
            fig.text(0.325, 0.24 + 0.56 * (y + 0.5) / len(data), lab, color=FG, ha="right", va="center", fontsize=15)
        ax.set_xlim(0, 27); ax.set_ylim(-0.5, len(data) - 0.5); ax.axis("off")
        fig.text(0.34, 0.205, "東日本大震災（M9.0）を1としたエネルギー比　　M が 1 上がると約32倍", color=MUTED, fontsize=12)
        return to_arr(fig)
    return frame

def scene_rupture():
    lons = np.arange(138, 168.01, 0.08); lats = np.arange(38, 58.01, 0.08)
    LON, LAT = np.meshgrid(lons, lats)
    inside = Path(geo.RUPTURE).contains_points(np.c_[LON.ravel(), LAT.ravel()]).reshape(LON.shape)
    dist = geo.hav_km(geo.EPI[0], geo.EPI[1], LON, LAT)
    TMAX = 420.0
    def frame(t, D, k):
        ts = TMAX * min(1.0, max(0.0, (t - 1.0) / (D * 0.7)))
        fig = new_fig(); header(fig, "想定：千島海溝の巨大地震", "断層はどこまでずれるか")
        ax = map_ax(fig, [0.03, 0.19, 0.6, 0.66], 138, 168, 38, 58)
        broke = inside & (dist <= 2.5 * ts)
        img = np.zeros(LON.shape + (4,)); img[broke] = [1, 0.35, 0.37, 0.75]
        front = inside & (abs(dist - 2.5 * ts) < 25)
        img[front] = [1, 0.85, 0.4, 1]
        ax.imshow(img, extent=(lons[0], lons[-1], lats[0], lats[-1]), origin="lower", zorder=4, interpolation="nearest")
        rupture_outline(ax); star(ax, size=300)
        ax.text(158.4, 47.0, "千島海溝", color=MUTED, fontsize=11, rotation=40, zorder=12)
        ax.text(156.8, 55.6, "カムチャツカ", color=MUTED, fontsize=11, zorder=12)
        ax.text(139.6, 43.4, "北海道", color=MUTED, fontsize=11, zorder=12)
        card(fig, 0.66, 0.23, 0.31, 0.6)
        fig.text(0.68, 0.79, "破壊開始から", color=MUTED, fontsize=14)
        fig.text(0.68, 0.72, f"{int(ts)//60}分{int(ts)%60:02d}秒", color=AMBER, fontsize=34, weight="bold")
        items = [("断層の長さ", "1,500km以上"), ("断層の幅", "約200km"), ("平均のずれ", "数十メートル"), ("揺れの継続", "3〜5分以上")]
        for i, (a, b) in enumerate(items):
            fig.text(0.68, 0.62 - i * 0.085, a, color=MUTED, fontsize=13)
            fig.text(0.95, 0.62 - i * 0.085, b, color=FG, fontsize=16, ha="right", weight="bold")
        fig.text(0.68, 0.255, "※ 規模の経験式からの概算", color=MUTED, fontsize=10)
        footer_note(fig, "赤枠：想定する震源域　点線：千島・カムチャツカ海溝")
        return to_arr(fig)
    return frame

BANDS = [(80, "6強〜7", "#a0007a"), (180, "6弱〜6強", "#d7263d"), (350, "5弱〜5強", "#ff8c42"),
         (700, "4", "#ffd23f"), (1100, "3", "#5aa9e6")]

def scene_shake():
    @lru_cache(None)
    def base():
        lons, lats, dist, land = geo.shaking_grid()
        fig = new_fig(); header(fig, "揺れの目安（概念図）", "北海道東部で震度7のおそれ")
        x0, x1, y0, y1 = 128, 166, 30, 55
        ax = map_ax(fig, [0.03, 0.19, 0.62, 0.66], x0, x1, y0, y1)
        img = np.zeros(dist.shape + (4,))
        prev = -1
        for lim, _, col in BANDS:
            m = land & (dist > prev) & (dist <= lim)
            img[m] = list(matplotlib.colors.to_rgba(col)); prev = lim
        ax.imshow(img, extent=(lons[0], lons[-1], lats[0], lats[-1]), origin="lower", zorder=3, interpolation="nearest")
        draw_land(ax, "50m", x0, x1, y0, y1, color="none", edge="#cfd6e4", z=4)
        rupture_outline(ax)
        for n, lo, la, dx in [("釧路", 144.38, 42.98, 0.5), ("札幌", 141.35, 43.06, -3.2), ("青森", 140.74, 40.82, -3.2),
                              ("仙台", 140.87, 38.27, -3.2), ("東京", 139.69, 35.69, -3.2), ("大阪", 135.5, 34.69, -3.2)]:
            ax.scatter([lo], [la], s=18, c="white", zorder=8)
            ax.text(lo + dx, la - (0.9 if dx > 0 else 0), n, color="white", fontsize=11, zorder=9, va="center")
        card(fig, 0.67, 0.23, 0.30, 0.6)
        fig.text(0.69, 0.78, "想定される震度", color=FG, fontsize=16, weight="bold")
        for i, (_, lab, col) in enumerate(BANDS):
            y = 0.70 - i * 0.07
            fig.add_artist(matplotlib.patches.Rectangle((0.69, y), 0.03, 0.045, transform=fig.transFigure, fc=col))
            fig.text(0.735, y + 0.022, "震度" + lab, color=FG, fontsize=15, va="center")
        fig.text(0.69, 0.30, "長周期地震動で\n東京・大阪の高層ビルも\n大きく長く揺れる", color=AMBER, fontsize=12.5, linespacing=1.5)
        footer_note(fig, "震源域からの距離だけで描いた目安。地盤や地形で大きく変わる")
        return to_arr(fig)
    return lambda t, D, k: base()

JP_TXT = {"根室": "数分〜20分", "宮古（岩手）": "30分〜1時間", "銚子（千葉）": "1〜1.5時間"}

def scene_tsunami():
    lons, lats, tt, land = geo.travel_times()
    arr = geo.city_arrivals(lons, lats, tt)
    TMAX = 22.0
    tt_m = np.where(np.isfinite(tt), tt, 1e9)
    def hm(h):
        return f"{int(h)}時間{int(round((h % 1) * 60)):02d}分" if h >= 1 else f"{int(round(h * 60))}分"
    def frame(t, D, k):
        th = TMAX * min(1.0, max(0.0, (t - 0.8) / (D * 0.9)))
        fig = new_fig()
        fig.text(0.03, 0.935, "津波の広がり（到達時間の目安）", color=AMBER, fontsize=15, va="center")
        fig.text(0.03, 0.88, "太平洋全体へ", color=FG, fontsize=28, va="center", weight="bold")
        x0, x1, y0, y1 = 112, 296, -50, 62
        ax = fig.add_axes([0.01, 0.17, 0.69, 0.66]); ax.set_facecolor(SEA)
        ax.set_xlim(x0, x1); ax.set_ylim(y0, y1); ax.set_aspect(1.15)
        ax.set_xticks([]); ax.set_yticks([])
        for s in ax.spines.values(): s.set_color("#33415c")
        img = np.zeros(tt.shape + (4,))
        reached = tt_m <= th
        age = np.clip((th - tt_m) / 3.0, 0, 1)
        img[reached] = np.c_[0.30 + 0 * age[reached], 0.79 - 0.4 * age[reached], 0.94 - 0.35 * age[reached], 0.85 - 0.6 * age[reached]]
        front = reached & (th - tt_m < 0.22)
        img[front] = [1.0, 0.95, 0.6, 1.0]
        ax.imshow(img, extent=(lons[0] - .25, lons[-1] + .25, lats[0] - .25, lats[-1] + .25), origin="lower", zorder=2, interpolation="nearest")
        draw_land(ax, "110m", x0, x1, y0, y1, color="#3a4866", edge="#56688c", z=3)
        ax.add_patch(MPoly(geo.RUPTURE, closed=True, fc=RED, ec=RED, lw=1, zorder=6, alpha=0.9))
        card(fig, 0.715, 0.17, 0.27, 0.78)
        fig.text(0.73, 0.905, "地震発生から", color=MUTED, fontsize=13)
        fig.text(0.73, 0.845, hm(th) if th > 0 else "0分", color=AMBER, fontsize=30, weight="bold")
        for i, (n, lo, la, h) in enumerate(arr):
            hit = h <= th
            ax.scatter([lo], [la], s=40 if hit else 16, c=AMBER if hit else "#6b7a96", ec="white" if hit else "none", zorder=8)
            if hit and n in ("ヒロ（ハワイ）", "サンフランシスコ", "シドニー", "バルパライソ（チリ）", "リマ（ペルー）"):
                ax.text(lo + 2, la + 1.5, n.split("（")[0], color=FG, fontsize=9.5, zorder=9)
            y = 0.775 - i * 0.063
            fig.text(0.73, y, n, color=FG if hit else "#5a6782", fontsize=13)
            val = JP_TXT.get(n, "約" + hm(round(h * 2) / 2).replace("00分", "").replace("30分", "半"))
            fig.text(0.975, y, val if hit else "—", color=AMBER if hit else "#5a6782", fontsize=13, ha="right", weight="bold")
        fig.text(0.73, 0.19, "水深一様の簡易計算。日本沿岸は\n浅い海で遅れる分を見込んだ値", color=MUTED, fontsize=9.5, linespacing=1.4)
        return to_arr(fig)
    return frame

def scene_height():
    rows = [("北海道 太平洋側", 20, 35, "30m超も"), ("三陸沿岸", 10, 25, "10〜20m超"),
            ("関東・東海の沿岸", 3, 10, "数m〜10m"), ("ハワイ", 5, 15, "5〜15m"),
            ("北米西海岸", 2, 10, "数m〜10m"), ("南米・オセアニア", 1, 6, "数m")]
    @lru_cache(None)
    def base(k):
        fig = new_fig(); header(fig, "津波の高さの目安", "北海道では30mを超えるおそれ")
        ax = fig.add_axes([0.25, 0.25, 0.47, 0.55]); ax.set_facecolor(BG)
        for i, (n, lo, hi, lab) in enumerate(rows):
            y = len(rows) - 1 - i
            ax.barh(y, hi - lo, left=lo, color=RED if i < 3 else CYAN, height=0.55, alpha=0.9)
            ax.text(hi + 0.8, y, lab, color=FG, va="center", fontsize=14, weight="bold")
            ax.text(-0.02, y, n, transform=ax.get_yaxis_transform(), color=FG, ha="right", va="center", fontsize=15)
        for x, lab, c in [(28, "内閣府想定 M9.3\nえりも町 約28m", AMBER), (40.1, "2011年 最大遡上\n40.1m", MUTED)]:
            ax.axvline(x, color=c, ls="--", lw=1.4); ax.text(x, len(rows) - 0.1, lab, color=c, fontsize=10.5, ha="center", va="bottom")
        ax.set_xlim(0, 46); ax.set_ylim(-0.5, len(rows) + 0.9)
        ax.tick_params(colors=MUTED, labelsize=11); ax.set_yticks([])
        for s in ("top", "right", "left"): ax.spines[s].set_visible(False)
        ax.spines["bottom"].set_color("#33415c"); ax.set_xlabel("津波の高さ (m)", color=MUTED, fontsize=12)
        card(fig, 0.75, 0.25, 0.22, 0.55)
        fig.text(0.765, 0.76, "M9.3想定との比較", color=MUTED, fontsize=12)
        fig.text(0.765, 0.69, "エネルギー 約8倍", color=AMBER, fontsize=17, weight="bold")
        fig.text(0.765, 0.60, "1960年チリ地震", color=MUTED, fontsize=12)
        fig.text(0.765, 0.47, "約17,000km先の\n日本で死者142人", color=FG, fontsize=15, weight="bold", linespacing=1.4)
        fig.text(0.765, 0.29, "※ バーは本動画の概算で\n不確実性が大きい", color=MUTED, fontsize=10, linespacing=1.4)
        return to_arr(fig)
    return lambda t, D, k: base(0)

def scene_society():
    cards = [("人的被害", "内閣府のM9.3想定で\n死者 約2.2万〜10万人\n（冬の夜・避難が遅い場合）"),
             ("国際的な被害", "ロシアの千島列島・\nカムチャツカ、太平洋の\n島々・環太平洋の国々"),
             ("孤立・停電・寒さ", "道路や港の寸断で孤立\n北海道全域の停電\n冬は低体温症の危険"),
             ("原子力施設", "沿岸の施設で\n津波・電源喪失への\n安全確認が必要"),
             ("経済・物流", "漁業・水産業に打撃\n部品供給網の寸断\n金融・保険市場の混乱"),
             ("余震・誘発地震", "M8クラスの余震\n日本海溝などで\n誘発地震のおそれ")]
    hl = {0: set(range(6)), 1: {0}, 2: {1}, 3: {2, 3, 4}, 4: {5}}
    @lru_cache(None)
    def base(k):
        fig = new_fig(); header(fig, "社会への影響", "被害は日本の外にも広がる")
        for i, (a, b) in enumerate(cards):
            c, r = i % 3, i // 3
            x, y = 0.04 + c * 0.31, 0.52 - r * 0.32
            on = i in hl.get(k, set())
            card(fig, x, y, 0.29, 0.29, fc="#1d2c4d" if on else PANEL, ec=AMBER if on else "#2a3a5c", lw=2 if on else 1.2)
            fig.text(x + 0.02, y + 0.235, a, color=AMBER if on else FG, fontsize=18, weight="bold", va="center")
            fig.text(x + 0.02, y + 0.12, b, color=FG if on else MUTED, fontsize=13.5, va="center", linespacing=1.5)
        return to_arr(fig)
    return lambda t, D, k: base(k)

def scene_prepare():
    items = ["強い揺れ・長い揺れを感じたら、警報を待たずに高い所へ",
             "津波は何度も来る。第1波が最大とは限らない",
             "冬の避難に備えて、防寒具を非常持ち出し袋へ",
             "緊急地震速報は外れることもある。それでもまず身を守る",
             "避難場所と経路を、今のうちに確かめる"]
    show = {0: 0, 1: 1, 2: 2, 3: 3, 4: 4, 5: 5}
    @lru_cache(None)
    def base(k):
        fig = new_fig(); header(fig, "今日からできること", "もしもの時に命を守るために")
        n = show.get(k, 5)
        for i, s in enumerate(items):
            y = 0.70 - i * 0.1
            on = i < n
            card(fig, 0.06, y, 0.88, 0.08, fc=PANEL if on else BG, ec="#2a3a5c" if on else "#1a2438")
            fig.text(0.085, y + 0.04, "✓" if on else "・", color=GREEN if on else "#3a4560", fontsize=22, va="center", weight="bold")
            fig.text(0.13, y + 0.04, s, color=FG if on else "#3a4560", fontsize=19, va="center")
        return to_arr(fig)
    return lambda t, D, k: base(k)

def scene_end():
    @lru_cache(None)
    def base():
        fig = new_fig(); header(fig, "おわりに", "この動画は仮想のシナリオです")
        lines = ["数値は公開資料と簡易計算による目安で、実際の被害を予測するものではありません。",
                 "",
                 "主な出典・方法",
                 "・気象庁 報道発表、マイナビニュース、J-CASTニュース（2026年10月16日の緊急地震速報）",
                 "・内閣府「日本海溝・千島海溝沿いの巨大地震モデル」および被害想定",
                 "・気象庁「2025年7月30日 カムチャツカ半島付近の地震」",
                 "・断層規模：マグニチュードの経験式（宇津）　津波到達時間：水深一様（約5,400m）の簡易計算",
                 "・地図：Natural Earth　音声：合成音声"]
        for i, s in enumerate(lines):
            fig.text(0.06, 0.74 - i * 0.06, s, color=AMBER if s == "主な出典・方法" else FG if i == 0 else MUTED,
                     fontsize=17 if i == 0 else 14, weight="bold" if s == "主な出典・方法" else "normal")
        return to_arr(fig)
    return lambda t, D, k: base()

BUILDERS = {"title": scene_title, "what": scene_what, "scale": scene_scale, "rupture": scene_rupture,
            "shake": scene_shake, "tsunami": scene_tsunami, "height": scene_height,
            "society": scene_society, "prepare": scene_prepare, "end": scene_end}
ANIMATED = {"scale", "rupture", "tsunami"}

def read_wav(p):
    with wave.open(p) as w:
        return np.frombuffer(w.readframes(w.getnframes()), np.int16)

def main():
    durs = json.load(open(os.path.join(TTS, "durations.json")))
    SR = 44100
    plan, audio = [], []
    for sid, sents in SCENES:
        starts, t = [], LEAD
        for d in durs[sid]:
            starts.append(t); t += d + GAP
        D = t - GAP + TAIL
        buf = np.zeros(int(D * SR) + 1, np.int16)
        for k, st in enumerate(starts):
            a = read_wav(os.path.join(TTS, f"{sid}_{k}.wav")); i = int(st * SR)
            buf[i:i + len(a)] = a[:len(buf) - i]
        audio.append(buf); plan.append((sid, sents, starts, D))
    wav_path = os.path.join(ROOT, "cache", "narration.wav")
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(np.concatenate(audio).tobytes())
    ff = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24",
                           "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-", "-i", wav_path,
                           "-c:v", "libx264", "-preset", "medium", "-crf", "24", "-pix_fmt", "yuv420p",
                           "-c:a", "aac", "-b:a", "128k", "-shortest", "-movflags", "+faststart", OUT],
                          stdin=subprocess.PIPE)
    total = 0
    for sid, sents, starts, D in plan:
        fr = BUILDERS[sid](); n = int(round(D * FPS)); last = None; sub_cache = {}
        for f in range(n):
            t = f / FPS
            k = max([i for i, s in enumerate(starts) if s <= t + 0.05] or [0])
            sub = sents[k] if t >= starts[0] - 0.05 else ""
            if sid in ANIMATED:
                if f % 2 == 0 or last is None:
                    last = with_sub(fr(t, D, k), sub)
                frame = last
            else:
                key = (k, sub)
                if key not in sub_cache: sub_cache = {key: with_sub(fr(t, D, k), sub)}
                frame = sub_cache[key]
            ff.stdin.write(frame.tobytes())
        total += n
        print(f"{sid}: {D:.1f}s", flush=True)
    ff.stdin.close(); ff.wait()
    print("frames", total, "->", OUT)

if __name__ == "__main__":
    import sys
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        sid, t = sys.argv[2], float(sys.argv[3])
        durs = json.load(open(os.path.join(TTS, "durations.json")))
        sents = dict(SCENES)[sid]
        D = LEAD + sum(durs[sid]) + GAP * (len(sents) - 1) + TAIL
        k = int(sys.argv[4]) if len(sys.argv) > 4 else 0
        Image.fromarray(with_sub(BUILDERS[sid]()(t, D, k), sents[k])).save(os.path.join(ROOT, "cache", f"prev_{sid}.png"))
    else:
        main()

"""Geometry for the hypothetical Kuril M9.9 scenario: rupture zone, land masks,
tsunami travel times (uniform deep-ocean speed, land blocks), shaking bands."""
import heapq, math, os
import numpy as np
from matplotlib.path import Path
import cartopy.io.shapereader as shpreader

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "..", "cache")
os.makedirs(CACHE, exist_ok=True)

# False EEW epicenter (JMA record, 2026-10-16): 48.2N 153.4E, depth 140 km
EPI = (153.4, 48.2)
# Hypothetical megathrust rupture zone (lon, lat): trench side then landward side
TRENCH = [(144.0, 41.2), (146.5, 41.9), (148.6, 42.9), (150.8, 44.0), (152.9, 45.4),
          (154.9, 46.9), (156.8, 48.6), (158.6, 50.5), (160.6, 52.3), (161.8, 53.6)]
LANDWARD = [(159.6, 53.8), (158.3, 52.2), (156.4, 50.2), (153.0, 48.8), (151.8, 47.4),
            (150.4, 45.6), (148.0, 44.2), (145.8, 43.2), (143.4, 42.4)]
RUPTURE = TRENCH + LANDWARD
TSUNAMI_KMH = 830.0  # ~sqrt(g*5400m), deep Pacific


def hav_km(lon1, lat1, lon2, lat2):
    lon1, lat1, lon2, lat2 = map(np.radians, (lon1, lat1, lon2, lat2))
    a = np.sin((lat2 - lat1) / 2) ** 2 + np.cos(lat1) * np.cos(lat2) * np.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6371.0 * np.arcsin(np.sqrt(np.clip(a, 0, 1)))


def land_polys(res):
    """Land polygons as lists of (N,2) arrays, longitudes in 0..360 (duplicated for wrap)."""
    out = []
    for geom in shpreader.Reader(shpreader.natural_earth(res, "physical", "land")).geometries():
        for g in getattr(geom, "geoms", [geom]):
            xy = np.asarray(g.exterior.coords)
            # Shift whole polygons, never single vertices, so shapes stay intact
            out.append(xy.copy())
            out.append(xy + [360, 0])
    return out


def land_mask(lons, lats, res):
    LON, LAT = np.meshgrid(lons, lats)
    pts = np.c_[LON.ravel(), LAT.ravel()]
    mask = np.zeros(len(pts), bool)
    for p in land_polys(res):
        x0, y0 = p.min(0); x1, y1 = p.max(0)
        sel = (pts[:, 0] >= x0) & (pts[:, 0] <= x1) & (pts[:, 1] >= y0) & (pts[:, 1] <= y1)
        if sel.any():
            mask[sel] |= Path(p).contains_points(pts[sel])
    return mask.reshape(LON.shape)


def travel_times():
    f = os.path.join(CACHE, "tsunami_tt.npz")
    if os.path.exists(f):
        d = np.load(f); return d["lons"], d["lats"], d["tt"], d["land"]
    lons = np.arange(100, 300.01, 0.5); lats = np.arange(-62, 66.01, 0.5)
    land = land_mask(lons, lats, "110m")
    LON, LAT = np.meshgrid(lons, lats)
    inside = Path(RUPTURE).contains_points(np.c_[LON.ravel(), LAT.ravel()]).reshape(LON.shape)
    ny, nx = land.shape
    tt = np.full(land.shape, np.inf)
    pq = []
    for j, i in zip(*np.where(inside & ~land)):
        tt[j, i] = 0.0; heapq.heappush(pq, (0.0, j, i))
    nb = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1),
          (-2, -1), (-2, 1), (2, -1), (2, 1), (-1, -2), (1, -2), (-1, 2), (1, 2)]
    while pq:
        t, j, i = heapq.heappop(pq)
        if t > tt[j, i]: continue
        for dj, di in nb:
            jj, ii = j + dj, i + di
            if 0 <= jj < ny and 0 <= ii < nx and not land[jj, ii]:
                d = hav_km(lons[i], lats[j], lons[ii], lats[jj]) / TSUNAMI_KMH
                if t + d < tt[jj, ii]:
                    tt[jj, ii] = t + d; heapq.heappush(pq, (t + d, jj, ii))
    np.savez(f, lons=lons, lats=lats, tt=tt, land=land)
    return lons, lats, tt, land


CITIES = [  # name, lon(0-360), lat
    ("根室", 145.8, 42.9), ("宮古（岩手）", 142.3, 39.6), ("銚子（千葉）", 141.2, 35.6),
    ("那覇", 127.6, 26.2), ("ヒロ（ハワイ）", 204.9, 19.8), ("サンフランシスコ", 237.4, 37.7),
    ("シドニー", 151.3, -33.9), ("リマ（ペルー）", 282.8, -12.1), ("バルパライソ（チリ）", 288.3, -33.0)]


def city_arrivals(lons, lats, tt):
    LON, LAT = np.meshgrid(lons, lats)
    out = []
    for name, lo, la in CITIES:
        d = hav_km(lo, la, LON, LAT); d[~np.isfinite(tt)] = 1e9
        j, i = np.unravel_index(np.argmin(d), d.shape)
        out.append((name, lo, la, float(tt[j, i] + d[j, i] / TSUNAMI_KMH)))
    return out


def shaking_grid():
    f = os.path.join(CACHE, "shake.npz")
    if os.path.exists(f):
        d = np.load(f); return d["lons"], d["lats"], d["dist"], d["land"]
    lons = np.arange(125, 172.01, 0.1); lats = np.arange(28, 60.01, 0.1)
    land = land_mask(lons, lats, "50m")
    LON, LAT = np.meshgrid(lons, lats)
    poly = np.array(RUPTURE + [RUPTURE[0]])
    bpts = []
    for a, b in zip(poly[:-1], poly[1:]):
        n = max(2, int(hav_km(*a, *b) / 8))
        bpts += [a + (b - a) * k / n for k in range(n)]
    bpts = np.array(bpts)
    dist = np.full(LON.size, np.inf); flat = np.c_[LON.ravel(), LAT.ravel()]
    for k in range(0, len(flat), 20000):
        c = flat[k:k + 20000]
        dist[k:k + 20000] = hav_km(c[:, :1], c[:, 1:], bpts[None, :, 0], bpts[None, :, 1]).min(1)
    dist[Path(RUPTURE).contains_points(flat)] = 0
    dist = dist.reshape(LON.shape)
    np.savez(f, lons=lons, lats=lats, dist=dist, land=land)
    return lons, lats, dist, land


if __name__ == "__main__":
    lons, lats, tt, land = travel_times()
    for n, lo, la, t in city_arrivals(lons, lats, tt):
        print(f"{n}: {t:.2f} h")
    p = np.array(RUPTURE)
    print("rupture length km ~", hav_km(144.0, 41.8, 160.8, 53.0))
    print("epi inside:", Path(RUPTURE).contains_point(EPI))
    l2, la2, dist, land2 = shaking_grid()
    for n, lo, la in [("札幌", 141.35, 43.06), ("釧路", 144.38, 42.98), ("青森", 140.74, 40.82), ("仙台", 140.87, 38.27), ("東京", 139.69, 35.69)]:
        j = np.argmin(abs(la2 - la)); i = np.argmin(abs(l2 - lo)); print(n, round(float(dist[j, i])))

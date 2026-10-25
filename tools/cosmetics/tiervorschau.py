"""Vorschau der Pets ohne Spiel: alle Voxel als Wuerfel, schraeg von vorne, von der Seite und von hinten.

  python3 tools/cosmetics/tiervorschau.py <ausgabe.png>
"""
import math, os, sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tiere


def sammeln():
    out = []
    tiere.tiere(lambda kat, id_, name, **kw: out.append(dict(id=id_, name=name, **kw)))
    return out


def rgb(h):
    return tuple(int(h[k:k + 2], 16) for k in (0, 2, 4))


def wuerfel(d):
    vox = []
    for t in d['parts']:
        sch = t['vox']; h = len(sch); dd = max(len(l) for l in sch); w = max(len(r) for l in sch for r in l)
        belegt = set()
        for y, l in enumerate(sch):
            for z, r in enumerate(l):
                for x, ch in enumerate(r):
                    if ch != '.': belegt.add((x, y, z))
        for (x, y, z) in belegt:
            if all(n in belegt for n in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1))):
                continue
            ch = sch[y][z][x]
            if ch not in t['colors']: continue
            p = ((x + 0.5 - w / 2) * t['px'] + t['at'][0], (y + 0.5 - h / 2) * t['px'] + t['at'][1], (z + 0.5 - dd / 2) * t['px'] + t['at'][2])
            vox.append((p, t['px'], rgb(t['colors'][ch]), ch in t.get('glow', '')))
    return vox


def bild(d, yaw, G=300):
    img = Image.new('RGB', (G, G), (40, 38, 52))
    dr = ImageDraw.Draw(img)
    ya, pa = math.radians(yaw), math.radians(18)
    s = G / 13.0
    boden = d.get('ground')
    mitte_y = 3.2 if boden else 0.8

    def proj(x, y, z):
        x1 = x * math.cos(ya) - z * math.sin(ya)
        z1 = x * math.sin(ya) + z * math.cos(ya)
        y1 = y * math.cos(pa) + z1 * math.sin(pa)
        z2 = -y * math.sin(pa) + z1 * math.cos(pa)
        return G / 2 + x1 * s, G * 0.55 - (y1 - mitte_y) * s, z2

    if boden:
        pts = [proj(a, 0, b) for a, b in ((-6, -6), (6, -6), (6, 6), (-6, 6))]
        dr.polygon([(p[0], p[1]) for p in pts], fill=(58, 74, 52))
    flaechen = []
    for (cx, cy, cz), g, f, glow in wuerfel(d):
        h = g / 2
        for ecken, hell in (([(-h, h, -h), (h, h, -h), (h, h, h), (-h, h, h)], 1.0),
                            ([(-h, -h, -h), (h, -h, -h), (h, h, -h), (-h, h, -h)], 0.82),
                            ([(-h, -h, h), (h, -h, h), (h, h, h), (-h, h, h)], 0.66),
                            ([(-h, -h, -h), (-h, -h, h), (-h, h, h), (-h, h, -h)], 0.74),
                            ([(h, -h, -h), (h, -h, h), (h, h, h), (h, h, -h)], 0.74),
                            ([(-h, -h, -h), (h, -h, -h), (h, -h, h), (-h, -h, h)], 0.5)):
            pts = [proj(cx + a, cy + b, cz + c) for a, b, c in ecken]
            tiefe = sum(p[2] for p in pts) / 4
            k = 1.0 if glow else hell
            flaechen.append((tiefe, [(p[0], p[1]) for p in pts], tuple(min(255, int(v * k)) for v in f)))
    flaechen.sort(key=lambda t: -t[0])
    for _, pts, f in flaechen:
        dr.polygon(pts, fill=f)
    dr.text((6, G - 14), d['name'] + ('  (Boden)' if boden else ''), fill=(255, 255, 255))
    return img


if __name__ == '__main__':
    ds = sammeln()
    G = 300
    sheet = Image.new('RGB', (G * 3, G * len(ds)), (20, 18, 28))
    for i, d in enumerate(ds):
        for j, yaw in enumerate((-35, 90, 160)):
            sheet.paste(bild(d, yaw, G), (j * G, i * G))
    sheet.save(sys.argv[1])
    print(sys.argv[1], len(ds))

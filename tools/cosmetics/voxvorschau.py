"""Vorschau der Voxel-Huete ohne Spiel: liest designs.txt und zeichnet jeden Hut
schraeg von vorne (und von der Seite) auf einen grauen Kopf. Nur zum Entwerfen.

  python3 tools/cosmetics/voxvorschau.py <ausgabe.png> [kategorie]
"""
import math, os, sys
from PIL import Image, ImageDraw

HIER = os.path.dirname(os.path.abspath(__file__))
DESIGNS = os.path.join(HIER, '..', '..', 'src', 'main', 'resources', 'assets', 'vortexclient', 'cosmetics', 'designs.txt')


def lesen(kat):
    out = []
    zeilen = open(DESIGNS).read().split('\n')
    i = 0
    while i < len(zeilen):
        z = zeilen[i].strip()
        if z.startswith('== ' + kat + ' '):
            d = {'id': z.split()[2], 'name': '', 'farben': {}, 'teile': []}
            i += 1
            while i < len(zeilen) and not zeilen[i].strip().startswith('== '):
                l = zeilen[i].strip(); i += 1
                if l.startswith('name:'): d['name'] = l[5:].strip()
                elif l.startswith('colors:'): d['farben'].update(farben(l[7:]))
                elif l.startswith('vox:'):
                    opt = dict(p.split('=', 1) for p in l[4:].split() if '=' in p)
                    eig = dict(d['farben'])
                    schichten, sch = [], []
                    while i < len(zeilen):
                        b = zeilen[i]; i += 1
                        tb = b.strip()
                        if tb == 'end': break
                        if tb.startswith('colors:'): eig.update(farben(tb[7:])); continue
                        if tb == '--': schichten.append(sch); sch = []; continue
                        sch.append(b[2:] if b.startswith('  ') else tb)
                    if sch: schichten.append(sch)
                    d['teile'].append(dict(at=[float(v) for v in opt['at'].split(',')], px=float(opt['px']),
                                           rot=[float(v) for v in opt.get('rot', '0,0,0').split(',')], anim=opt.get('anim', ''),
                                           glow=opt.get('glow', ''), farben=eig, vox=schichten))
            out.append(d)
            continue
        i += 1
    return out


def farben(v):
    m = {}
    for p in v.split():
        if len(p) > 2 and p[1] == '=':
            h = p[2:]
            m[p[0]] = tuple(int(h[k:k + 2], 16) for k in (0, 2, 4))
    return m


def drehe(p, rot):
    x, y, z = p
    rx, ry, rz = [math.radians(a) for a in rot]
    # Reihenfolge wie im Client: Y, dann X, dann Z (auf den Punkt rueckwaerts angewandt)
    c, s = math.cos(rz), math.sin(rz); x, y = x * c - y * s, x * s + y * c
    c, s = math.cos(rx), math.sin(rx); y, z = y * c - z * s, y * s + z * c
    c, s = math.cos(ry), math.sin(ry); x, z = x * c + z * s, -x * s + z * c
    return x, y, z


def wuerfel(d):
    """Alle Voxel als (Mitte im Kopf-Raum, Groesse, Farbe, leuchtet)."""
    out = []
    for t in d['teile']:
        vox = t['vox']; h = len(vox)
        dd = max(len(l) for l in vox); w = max(len(r) for l in vox for r in l)
        for y, l in enumerate(vox):
            for z, r in enumerate(l):
                for x, ch in enumerate(r):
                    if ch == '.' or ch not in t['farben']: continue
                    p = ((x + 0.5 - w / 2) * t['px'], (y + 0.5 - h / 2) * t['px'], (z + 0.5 - dd / 2) * t['px'])
                    p = drehe(p, t['rot'])
                    out.append(((p[0] + t['at'][0], p[1] + t['at'][1], p[2] + t['at'][2]), t['px'], t['farben'][ch], ch in t['glow']))
    return out


def bild(d, yaw, groesse=260):
    img = Image.new('RGB', (groesse, groesse), (44, 42, 56))
    dr = ImageDraw.Draw(img)
    ya, pa = math.radians(yaw), math.radians(22)
    s = groesse / 26.0

    def proj(x, y, z):
        # Kamera vor dem Gesicht (-z), um die y-Achse gedreht, leicht von oben
        x1 = x * math.cos(ya) - z * math.sin(ya)
        z1 = x * math.sin(ya) + z * math.cos(ya)
        y1 = y * math.cos(pa) + z1 * math.sin(pa)
        z2 = -y * math.sin(pa) + z1 * math.cos(pa)
        return groesse / 2 + x1 * s, groesse * 0.68 - (y1 - 4) * s, z2

    flaechen = []
    # Kopf (8x8x8) als graue Wuerfelflaechen
    teile = [((0, 4, 0), 8, (150, 120, 105), False)] + wuerfel(d)
    for (cx, cy, cz), g, f, glow in teile:
        h = g / 2
        for n, ecken, hell in (((0, 1, 0), [(-h, h, -h), (h, h, -h), (h, h, h), (-h, h, h)], 1.0),
                               ((0, 0, -1), [(-h, -h, -h), (h, -h, -h), (h, h, -h), (-h, h, -h)], 0.82),
                               ((0, 0, 1), [(-h, -h, h), (h, -h, h), (h, h, h), (-h, h, h)], 0.7),
                               ((-1, 0, 0), [(-h, -h, -h), (-h, -h, h), (-h, h, h), (-h, h, -h)], 0.75),
                               ((1, 0, 0), [(h, -h, -h), (h, -h, h), (h, h, h), (h, h, -h)], 0.75),
                               ((0, -1, 0), [(-h, -h, -h), (h, -h, -h), (h, -h, h), (-h, -h, h)], 0.55)):
            pts = [proj(cx + a, cy + b, cz + c) for a, b, c in ecken]
            tiefe = sum(p[2] for p in pts) / 4
            k = 1.0 if glow else hell
            flaechen.append((tiefe, [(p[0], p[1]) for p in pts], tuple(min(255, int(v * k)) for v in f)))
    flaechen.sort(key=lambda t: -t[0])
    for _, pts, f in flaechen:
        dr.polygon(pts, fill=f)
    dr.text((6, groesse - 14), d['name'], fill=(255, 255, 255))
    return img


if __name__ == '__main__':
    ziel = sys.argv[1]
    kat = sys.argv[2] if len(sys.argv) > 2 else 'hat'
    ds = lesen(kat)
    G = 230
    sheet = Image.new('RGB', (G * 3, G * len(ds)), (20, 18, 28))
    for i, d in enumerate(ds):
        for j, yaw in enumerate((0, -40, 90)):
            sheet.paste(bild(d, yaw, G), (j * G, i * G))
    sheet.save(ziel)
    print(ziel, len(ds))

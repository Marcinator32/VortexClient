"""Vortex-Cosmetics: Pixel-Kunst fuer Bandanas, Face, Back, Auras und Shield Skins.

Erzeugt:
  src/main/resources/assets/vortexclient/cosmetics/designs.txt   (liest der Client)
  src/main/resources/assets/vortexclient/textures/cosmetics/shield/<id>.png
und optional Vorschaubilder:  python3 tools/cosmetics/art.py <vorschau-ordner>

Alle Designs sind eigene Entwuerfe. Koordinaten in designs.txt:
  head  -- Ursprung Mitte Kopfunterseite, y nach oben (0..8), Gesicht bei z = -4
  body  -- Ursprung Mitte Halsansatz, y nach oben (0..-12), Ruecken bei z = +2
  Kunst wird von vorne gesehen gezeichnet (Rucksaecke: von hinten gesehen).
"""
import math, os, sys, random

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'vortexclient')

# ---------------------------------------------------------------- Raster
class G:
    def __init__(s, w, h, fill='.'):
        s.w, s.h = w, h
        s.c = [[fill] * w for _ in range(h)]
    def set(s, x, y, ch):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < s.w and 0 <= y < s.h: s.c[y][x] = ch
    def get(s, x, y):
        return s.c[y][x] if 0 <= x < s.w and 0 <= y < s.h else '.'
    def rect(s, x0, y0, x1, y1, ch):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1): s.set(x, y, ch)
    def disc(s, cx, cy, r, ch, r0=-1):
        for y in range(s.h):
            for x in range(s.w):
                d = math.hypot(x - cx, y - cy)
                if d <= r + 0.2 and d > r0: s.set(x, y, ch)
    def line(s, x0, y0, x1, y1, ch, dicke=0):
        n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for i in range(n + 1):
            t = i / n
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            if dicke:
                for dy in range(-dicke, dicke + 1):
                    for dx in range(-dicke, dicke + 1):
                        if dx * dx + dy * dy <= dicke * dicke: s.set(x + dx, y + dy, ch)
            else: s.set(x, y, ch)
    def outline(s, ch, inner=None):
        """Rand um alles Nicht-Leere ziehen (nur auf leeren Feldern)."""
        add = []
        for y in range(s.h):
            for x in range(s.w):
                if s.c[y][x] != '.': continue
                for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                    n = s.get(x + dx, y + dy)
                    if n != '.' and n != ch and (inner is None or n in inner):
                        add.append((x, y)); break
        for x, y in add: s.c[y][x] = ch
    def mirror_x(s):
        for y in range(s.h):
            for x in range(s.w // 2):
                if s.c[y][x] != '.': s.c[y][s.w - 1 - x] = s.c[y][x]
                elif s.c[y][s.w - 1 - x] != '.': s.c[y][x] = s.c[y][s.w - 1 - x]
    def rows(s): return [''.join(r) for r in s.c]

def from_rows(rows):
    g = G(max(len(r) for r in rows), len(rows))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r): g.c[y][x] = ch
    return g

# ---------------------------------------------------------------- Ausgabe
OUT = []

def design(kat, id_, name, **kw):
    OUT.append(dict(kat=kat, id=id_, name=name, **kw))

def part(art, colors, at=(0, 0, 0), px=0.5, depth=1, rot=(0, 0, 0), glow='', anim=''):
    if isinstance(art, G): art = art.rows()
    return dict(art=art, colors=colors, at=at, px=px, depth=depth, rot=rot, glow=glow, anim=anim)

# ======================================================================
# FACE
# ======================================================================
def scale2x(rows):
    """EPX/Scale2x: doppelte Aufloesung mit geglaetteten Diagonalen."""
    h, w = len(rows), max(len(r) for r in rows)
    g = lambda x, y: rows[y][x] if 0 <= y < h and 0 <= x < len(rows[y]) else '.'
    out = [[''] * (w * 2) for _ in range(h * 2)]
    for y in range(h):
        for x in range(w):
            p, a, b, c, d = g(x, y), g(x, y - 1), g(x + 1, y), g(x - 1, y), g(x, y + 1)
            e0 = e1 = e2 = e3 = p
            if c == a and c != d and a != b: e0 = a
            if a == b and a != c and b != d: e1 = b
            if d == c and d != b and c != a: e2 = c
            if b == d and b != a and d != c: e3 = d
            out[2 * y][2 * x], out[2 * y][2 * x + 1], out[2 * y + 1][2 * x], out[2 * y + 1][2 * x + 1] = e0, e1, e2, e3
    return [''.join(r) for r in out]

def raster(w, h, fn):
    """Bild w x h aus einer Funktion fn(x, y) -> Zeichen; x, y = Pixelmitte (y nach unten)."""
    return [''.join(fn(x + 0.5, y + 0.5) for x in range(w)) for y in range(h)]

def herz_sdf(x, y):
    """< 0 innerhalb eines Herzens. Spitze bei (0, 0.55), Boegen oben bis y ~ -0.6 (y nach unten)."""
    px, py = abs(x), -y + 0.55
    if py + px > 1.0:
        return math.hypot(px - 0.25, py - 0.75) - math.sqrt(2) / 4
    m = 0.5 * max(px + py, 0.0)
    d = math.sqrt(min((px) ** 2 + (py - 1.0) ** 2, (px - m) ** 2 + (py - m) ** 2))
    return d * (1 if px - py > 0 else -1)

def stern_sdf(x, y, r1=1.0, r2=0.45):
    a = math.atan2(x, -y); r = math.hypot(x, y)
    k = (a % (2 * math.pi / 5)) / (2 * math.pi / 5)
    grenze = r2 + (r1 - r2) * abs(1 - 2 * k) ** 1.6
    return r - grenze

def face():
    F = 0.25   # feine Brillen: halbe Pixel der alten Version
    # --- Pixel Shades: schwarze Sonnenbrille, feiner, mit Glanzstreifen
    def shades(x, y):
        u, v = x / 2, y / 2                     # in alten Pixeln (18 x 4)
        if v < 1.0: return 'k'                  # oberer Steg
        for x0 in (0.3, 9.7):                   # zwei Glaeser, unten schraeg
            lx = u - x0
            if 0 <= lx <= 8.0 and v <= 4.0 - max(0, (lx - 5.2) * 0.9) - max(0, (1.6 - lx) * 0.9):
                if 1.2 < v < 3.0 and abs(lx - (v - 1.2) - 1.3) < 0.55: return 'w'
                if 1.2 < v < 2.4 and abs(lx - (v - 1.2) - 2.6) < 0.3: return 'w'
                return 'K' if v > 3.0 else 'k'
        if 8.2 <= u <= 9.8 and v < 1.6: return 'k'
        return '.'
    design('face', 'pixel_shades', 'Pixel Shades', arms='k', colors={'k': '101016', 'K': '24242E', 'w': 'E6E8FF'},
           parts=[part(raster(36, 8, shades), {}, at=(0, 4.0, -4.85), px=F),
                  part(["W.", "WW", ".W"], {'W': 'FFFFFF'}, at=(0, 4.2, -5.0), px=0.35, depth=0.3, rot=(0, 0, -20), glow='W', anim='glint:3.6')])

    # --- Heart Glasses: glatte Herzen mit Glanz, duenner Rahmen
    def herzen(x, y):
        for cx in (8.6, 27.4):
            d = herz_sdf((x - cx) / 9.0, (y - 8.4) / 9.0)
            if d < -0.11:
                hx, hy = (x - cx) / 9.0, (y - 8.4) / 9.0
                if math.hypot(hx + 0.45, hy + 0.45) < 0.17: return 'w'
                return 'l' if hy < -0.25 and hx < 0 else 'p'
            if d < 0.0: return 'r'
        if 13.0 <= x <= 23.0 and 4.6 <= y <= 5.8: return 'r'
        return '.'
    design('face', 'heart_glasses', 'Heart Glasses', arms='r', colors={'p': 'FF3E8E', 'l': 'FF86BC', 'w': 'FFFFFF', 'r': 'C2185B'},
           parts=[part(raster(36, 17, herzen), {}, at=(0, 3.7, -4.85), px=F, anim='pulse')])

    # --- Neon Visor: Band mit runden Enden, Lauflicht faehrt hin und her
    def visor(x, y):
        rx = min(x, 36 - x)
        unten = 10 - max(0, 2.2 - abs(x - 18) * 0.55)      # Nasen-Aussparung unten in der Mitte
        if y < 1 or y > unten or (rx < 2.5 and math.hypot(2.5 - rx, max(0, abs(y - 5.5) - 2)) > 2.6): return '.'
        if y < 2 or y > unten - 1 or rx < 1.4: return 'd'
        if int(y) % 2 == 1 and rx > 3: return 's'               # feine Scanlines
        t = (y - 2) / 7
        return 'C' if t < 0.34 else ('B' if t < 0.67 else 'P')
    design('face', 'neon_visor', 'Neon Visor', arms='d', colors={'d': '1A1230', 's': '2A1F55', 'C': '38E1FF', 'B': '7C8CFF', 'P': 'B26BFF'},
           parts=[part(raster(36, 11, visor), {}, at=(0, 4.0, -4.85), px=F, glow='CBP'),
                  part(["W", "W", "W", "W", "W", "W"], {'W': 'F2FDFF'}, at=(0, 4.0, -4.97), px=F, depth=0.5, glow='W', anim='scan:3.6')])

    # --- Star Glasses: glatte Sterne mit Verlauf, Funkeln
    def sterne(x, y):
        for cx in (8.6, 27.4):
            d = stern_sdf((x - cx) / 8.4, (y - 8.6) / 8.4)
            if d < -0.12:
                r = math.hypot(x - cx, y - 8.6) / 8.4
                return 'o' if r < 0.28 else ('y' if r < 0.62 else 'Y')
            if d < 0.0: return 'b'
        if 16.6 <= x <= 19.4 and 6.8 <= y <= 8.0: return 'b'
        return '.'
    design('face', 'star_glasses', 'Star Glasses', arms='b', colors={'Y': 'FFE680', 'y': 'FFD23F', 'o': 'FF8A1F', 'b': 'C77A12'},
           parts=[part(raster(36, 17, sterne), {}, at=(0, 3.7, -4.85), px=F),
                  part(["..W..", ".WWW.", "WWWWW", ".WWW.", "..W.."], {'W': 'FFFDE8'}, at=(3.9, 5.6, -5.0), px=0.18, depth=0.4, glow='W', anim='twinkle'),
                  part(["..W..", ".WWW.", "WWWWW", ".WWW.", "..W.."], {'W': 'FFFDE8'}, at=(-3.3, 2.4, -5.0), px=0.14, depth=0.4, glow='W', anim='twinkle:0.5')])

    # --- 3D Glasses: duenner weisser Rahmen, rot/cyan mit Glanz
    def brille3d(x, y):
        if y < 0 or y > 10: return '.'
        for x0, ch, hl in ((1.5, 'r', 'R'), (19.5, 'c', 'C')):
            if x0 + 1 <= x <= x0 + 14 and 1 <= y <= 9:
                if x0 + 1 <= x <= x0 + 14 and 2 <= y <= 8 and x0 + 2 <= x <= x0 + 13:
                    return hl if (x - x0) + y < 7 and (x - x0) + y > 5.2 else ch
                return 'w'
        if 15.5 <= x <= 20.5 and y <= 3: return 'w'
        return '.'
    design('face', 'retro_3d', '3D Glasses', arms='w', colors={'w': 'F2F2F2', 'r': 'E3343A', 'R': 'FF8A8E', 'c': '29C6E8', 'C': '9BEFFF'},
           parts=[part(raster(36, 11, brille3d), {}, at=(0, 3.9, -4.85), px=F)])

    # --- Monocle: feiner Goldring, Glas mit Glanz, Kette schwingt
    def monokel(x, y):
        r = math.hypot(x - 8, y - 8)
        if r > 7.6: return '.'
        if r > 6.2: return 'G' if (x - 8) + (y - 8) < -2 else 'g'
        if math.hypot(x - 5.6, y - 5.4) < 1.3: return 'w'
        return 'L' if (x - 8) + (y - 8) < -3 else 'l'
    kette = ['g' if y % 3 != 2 else 'h' for y in range(22)]
    design('face', 'monocle', 'Monocle', colors={'g': 'E8B53A', 'G': 'FFE08A', 'h': 'A8761A', 'l': 'BFE7FF', 'L': 'E6F7FF', 'w': 'FFFFFF'},
           parts=[part(raster(16, 16, monokel), {}, at=(-2, 4.2, -4.85), px=F),
                  part(kette, {}, at=(-0.4, 0.95, -4.85), px=F, rot=(0, 0, -18), anim='swing:7')])

    # --- Skull Mask: ganze Gesichtsmaske
    s = [
        "..bbbbbbbbbbbb..",
        ".bbbbbbbbbbbbbb.",
        "bbbbbbbbbbbbbbbb",
        "bbbbbbbbbbbbbbbb",
        "bbkkkkbbbbkkkkbb",
        "bkkkkkkbbkkkkkkb",
        "bkkrkkkbbkkkrkkb",
        "bkkkkkkbbkkkkkkb",
        "bbkkkkbbbbkkkkbb",
        "bbbbbbbkkbbbbbbb",
        "bbbbbbkkkkbbbbbb",
        ".bbbbbbbbbbbbbb.",
        ".bwbwbwbwbwbwbb.",
        ".bkbkbkbkbkbkbb.",
        "..bwbwbwbwbwbb..",
        "...bbbbbbbbbb...",
    ]
    s = scale2x(s)
    # Schattierung: Wangen und Stirn etwas dunkler, Augen-Glut
    s = [''.join('B' if ch == 'b' and ((y > 18 and (x < 5 or x > 26)) or y < 2) else ch for x, ch in enumerate(r)) for y, r in enumerate(s)]
    design('face', 'skull_mask', 'Skull Mask', colors={'b': 'E9E4D4', 'B': 'CFC8B4', 'k': '1A1714', 'w': 'FFFFFF', 'r': 'E33B3B'},
           parts=[part(s, {}, at=(0, 4.0, -4.85), px=F, glow='r'),
                  part(["rr", "rr"], {'r': 'FF5A4A'}, at=(-2.25, 4.75, -5.03), px=0.25, depth=0.3, glow='r', anim='pulse:0.35'),
                  part(["rr", "rr"], {'r': 'FF5A4A'}, at=(2.25, 4.75, -5.03), px=0.25, depth=0.3, glow='r', anim='pulse:0.35')])

    # --- Ninja Mask: Tuch um die untere Kopfhaelfte (Ring)
    design('face', 'ninja_mask', 'Ninja Mask', ring=dict(rows=['kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk',
                                                             'kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk',
                                                             'rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr',
                                                             'kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk',
                                                             'kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk',
                                                             'kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk'],
                                                       y0=0.2, px=0.5, knot='k', tails='kr'),
           colors={'k': '22222B', 'r': 'C8243A'})

    # --- Fox Mask: Kitsune, weiss mit roten Zeichen, Ohren
    f = [
        "..e..........e....",
        ".eee........eee...",
        ".eRe........eRe...",
        "eeRee......eeRee..",
        "eeeeeeeeeeeeeeee..",
        "eeeeeeeeeeeeeeee..",
        "eRReeeeeeeeeeRRe..",
        "eekRRe.eeeeRRkee..",
        "eekkkReeeeRkkkee..",
        "eeeeeeeeeeeeeeee..",
        "eeeeeeeeeeeeeeee..",
        ".eeeeeeRReeeeee...",
        ".eeeeeekkeeeeee...",
        "..eeeeeeeeeeee....",
        "...eeRRRRRRee.....",
        "....eeeeeeee......",
        "......eeee........",
    ]
    f = [r[:16] for r in f]
    g = from_rows(f)
    design('face', 'fox_mask', 'Fox Mask', colors={'e': 'F7F3EC', 'R': 'D9283A', 'k': '18141C'},
           parts=[part(scale2x(g.rows()), {}, at=(0, 4.6, -4.85), px=F)])

    # --- Bow: Schleife oben am Kopf
    b = [
        "pp......pp",
        "pPp....pPp",
        "pPPp..pPPp",
        "pPPPkkPPPp",
        "pPPPkkPPPp",
        "pPPp..pPPp",
        "pPp....pPp",
        "pp......pp",
    ]
    design('face', 'bow', 'Bow', colors={'p': 'FF5FA8', 'P': 'FF9BCB', 'k': 'D93A86'},
           parts=[part(scale2x(b), {}, at=(2.6, 8.4, -1.5), px=F, depth=4, rot=(0, -20, 18), anim='sway:6')])

# ======================================================================
# BANDANAS -- Band (32 Spalten rund um den Kopf: vorne 8 | links 8 | hinten 8 | rechts 8)
# ======================================================================
# Bewegung je Bandana (seit 4.63): flow = Spalten je Sekunde, die das Muster um den Kopf
# laeuft (negativ = andersherum); fx = was das Leuchten tut (pulse, flash, twinkle);
# extra_glow = Zeichen, die zusaetzlich leuchten (fuer fx).
BANDANA_BEWEGUNG = {
    'lightning': dict(flow=7, fx='flash', extra_glow='w'),
    'flame': dict(flow=4, fx='pulse', extra_glow='y'),
    'rainbow': dict(flow=12),
    'camo': dict(flow=0),
    'galaxy': dict(flow=2, fx='twinkle'),
    'vortex': dict(flow=-9, fx='pulse', extra_glow='w'),
    'racing': dict(flow=14),
    'sakura': dict(flow=2),
    'ice': dict(flow=1, fx='twinkle', extra_glow='w'),
    'toxic': dict(flow=5, fx='pulse'),
    'neon': dict(flow=8, fx='pulse'),
    'lava': dict(flow=2, fx='pulse'),
    'ocean': dict(flow=6),
    'tiger': dict(flow=0),
    'urban_camo': dict(flow=0),
    'classic': dict(flow=1),
}

def bandana(id_, name, colors, rows, tails, glow=''):
    bw = BANDANA_BEWEGUNG.get(id_, {})
    glow = glow + bw.get('extra_glow', '')
    design('bandana', id_, name, colors=colors, ring=dict(rows=rows, y0=5.0, px=0.5, knot=tails[0], tails=tails,
           flow=bw.get('flow', 0), fx=bw.get('fx', '')), glow=glow)

N = 64   # Spalten rund um den Kopf (je Seite 16, ein halber Pixel breit)

def band(fn, n=4):
    return [''.join(fn(x, y) for x in range(N)) for y in range(n)]

def bandanas():
    # Lightning: weisser Zickzack-Blitz auf schwarz
    def blitz(x, y):
        k = x % 8
        z = [0, 1, 2, 3, 2, 1, 0, 1][k]
        return 'w' if y == z or (y == z + 1 and k in (3, 6)) else 'k'
    bandana('lightning', 'Lightning', {'k': '17171E', 'w': 'F2F6FF'}, band(blitz), 'kw')
    # Flame: rot mit Flammenzungen
    def flamme(x, y):
        h = [0, 1, 2, 3, 2, 1, 2, 3, 3, 2, 1, 0][x % 12]
        if y >= 4 - h: return 'y' if y >= 4 - h + 2 else 'o'
        return 'r'
    bandana('flame', 'Flame', {'r': '6E0D10', 'o': 'F05A16', 'y': 'FFC23A'}, band(flamme), 'ro')
    # Rainbow: schraege Streifen
    bandana('rainbow', 'Rainbow', {'r': 'E8434B', 'o': 'F59A2C', 'y': 'F7D43C', 'g': '4CC25B', 'b': '3D8BE6', 'v': '8E5BE3'},
            band(lambda x, y: 'roygbv'[((x + y) // 2) % 6]), 'rb')
    # Camo: Flecken
    rnd = random.Random(4)
    cam = [[rnd.choice('ggdb') for _ in range(N)] for _ in range(4)]
    for _ in range(4):
        cam = [[max(set(w := [cam[y][x], cam[y][(x + 1) % N], cam[y][x - 1], cam[(y + 1) % 4][x]]), key=w.count) for x in range(N)] for y in range(4)]
    bandana('camo', 'Camo', {'g': '4B5E2E', 'd': '2F3B1E', 'b': '7A6A45'}, [''.join(r) for r in cam], 'gd')
    # Galaxy: dunkel mit Nebel und kleinen Sternen
    rnd = random.Random(9)
    sterne = {(rnd.randrange(N), rnd.randrange(4)) for _ in range(14)}
    def galaxy(x, y):
        if (x, y) in sterne: return 'S'
        v = math.sin(x * 0.35) + math.cos(y * 1.3 + x * 0.12)
        return 'p' if v > 0.9 else ('m' if v > 0 else 'n')
    bandana('galaxy', 'Galaxy', {'n': '140F38', 'm': '2B1C66', 'p': '6A3FC4', 'S': 'FFFFFF'}, band(galaxy), 'nm', glow='S')
    # Vortex: lila/blau mit hellem V-Muster
    def vortex(x, y):
        k = x % 8
        if (k, y) in ((1, 0), (7, 0), (2, 1), (6, 1), (3, 2), (5, 2), (4, 3)): return 'w'
        return 'v' if (x // 16) % 2 == 0 else 'b'
    bandana('vortex', 'Vortex', {'v': '7C3AED', 'b': '3B5BDB', 'w': 'D3A6FF'}, band(vortex), 'vb')
    # Racing: Schachbrett
    bandana('racing', 'Racing', {'k': '141418', 'w': 'F4F4F4'}, band(lambda x, y: 'k' if (x // 2 + y // 2) % 2 else 'w'), 'kw')
    # Sakura: hell mit kleinen Blueten
    def sakura(x, y):
        k, m = x % 10, (x // 10) % 2
        cy = 1 if m == 0 else 2
        if (k, y) == (4, cy): return 'Y'
        if (k in (3, 5) and y == cy) or (k == 4 and y in (cy - 1, cy + 1)): return 'p'
        return 'l'
    bandana('sakura', 'Sakura', {'l': 'FFE3EE', 'p': 'FF7FB0', 'Y': 'FFD94A'}, band(sakura), 'lp')
    # Ice: Eiskristalle
    def eis(x, y):
        k = x % 9
        if (k == 4 and y in (0, 1, 2, 3)) or (y in (1, 2) and k in (3, 5)): return 'w'
        return 'b' if (x // 3 + y) % 3 == 0 else 'c'
    bandana('ice', 'Ice', {'c': 'A8E6FF', 'b': '6CC6F0', 'w': 'FFFFFF'}, band(eis), 'cb')
    # Toxic: dunkel mit leuchtenden Tropfen
    def toxic(x, y):
        k = x % 7
        if k == 3 and y >= 1: return 'L'
        if k in (2, 4) and y == 3: return 'L'
        if k == 3 and y == 0: return 'l'
        return 'k'
    bandana('toxic', 'Toxic', {'k': '162016', 'l': '3E8F2A', 'L': '9BFF3A'}, band(toxic), 'kL', glow='L')

# ======================================================================
# BACK -- von hinten gesehen; Ruecken bei z = +2, Koerper y 0..-12
# ======================================================================
RUECKEN = 2.12   # Ruecken-Oberflaeche (z) plus etwas Luft

def hinten(tiefe, px=0.5, extra=0.0):
    """z-Mitte eines Teils, das mit seiner Tiefe direkt am Ruecken anliegt."""
    return round(RUECKEN + extra + tiefe * px / 2, 3)

def rel(at, rotz, ox, oy):
    """Mitte eines Unterteils, das im (um rotz gedrehten) Raster des Hauptteils um ox/oy versetzt liegt."""
    c, si = math.cos(math.radians(rotz)), math.sin(math.radians(rotz))
    return (round(at[0] + ox * c - oy * si, 3), round(at[1] + ox * si + oy * c, 3))

def gurte(farbe_k, schnalle=None):
    """Schultergurte: vorne ueber die Brust und oben ueber die Schulter."""
    vorne = ['g'] * 13
    if schnalle: vorne[9] = 's'
    oben = ['g'] * 9
    teile = []
    for x in (-2.2, 2.2):
        teile.append(part(vorne, {'g': farbe_k, 's': schnalle or farbe_k}, at=(x, -3.1, -2.18), px=0.5, depth=0.5, anim='strap'))
        teile.append(part(oben, {'g': farbe_k}, at=(x, 0.13, 0.0), px=0.5, depth=0.5, rot=(90, 0, 0), anim='strap'))
    return teile

def back():
    # --- Teddy Backpack: Kopf und Koerper mit Volumen, Schnauze steht vor, Arme, Gurte
    kopf = [
        "..bb........bb..",
        ".bBBb......bBBb.",
        ".bBkbbbbbbbbkBb.",
        "..bbbbbbbbbbbb..",
        "..bbbbbbbbbbbb..",
        ".bbbkkbbbbkkbbb.",
        ".bbbkwbbbbkwbbb.",
        ".bbbbbbbbbbbbbb.",
        ".bbbbbbbbbbbbbb.",
        "..bbbbbbbbbbbb..",
        "...bbbbbbbbbb...",
        "....bbbbbbbb....",
    ]
    koerper = [
        "..bbbbbbbbbbbb..",
        ".bbbbBBBBBBbbbb.",
        "bbbbBBBBBBBBbbbb",
        "bbbbBBBBBBBBbbbb",
        "bbbbBBBBBBBBbbbb",
        ".bbbBBBBBBBBbbb.",
        ".bbbbBBBBBBbbbb.",
        "..bbbbbbbbbbbb..",
        "..BBB......BBB..",
        "..bbb......bbb..",
    ]
    schnauze = ["BBBB", "BnnB", "BBBB", ".kk."]
    arm = [".b", "bb", "bb", "bb", "BB"]
    tc = {'b': '8A5A35', 'B': 'C49067', 'k': '1D1410', 'w': 'FFFFFF', 'n': '3A241A'}
    design('back', 'teddy_backpack', 'Teddy Backpack', colors=tc,
           parts=[part(koerper, {}, at=(0, -8.6, hinten(6)), px=0.5, depth=6, anim='bob'),
                  part(kopf, {}, at=(0, -3.1, hinten(5)), px=0.5, depth=5, anim='bob'),
                  part(schnauze, {}, at=(0, -4.3, hinten(5) + 1.25 + 0.4), px=0.5, depth=1.6, anim='bob'),
                  part(arm, {}, at=(-3.3, -8.0, hinten(6) + 1.0), px=0.5, depth=3, rot=(0, 0, -25), anim='bob'),
                  part([r[::-1] for r in arm], {}, at=(3.3, -8.0, hinten(6) + 1.0), px=0.5, depth=3, rot=(0, 0, 25), anim='bob')]
                 + gurte('6B3F22'))

    # --- Explorer Backpack: Koerper mit Tiefe, Deckel und Aussentasche stehen vor, Schlafrolle oben
    p = G(16, 18)
    p.rect(1, 0, 14, 17, 'l')
    p.rect(2, 0, 2, 17, 's'); p.rect(13, 0, 13, 17, 's')
    klappe = G(16, 7)
    klappe.rect(1, 0, 14, 5, 'L'); klappe.rect(1, 6, 14, 6, 'd')
    klappe.rect(6, 4, 9, 6, 'g'); klappe.rect(7, 5, 8, 5, 'd')
    tasche = G(10, 6)
    tasche.rect(0, 0, 9, 5, 'L'); tasche.rect(0, 0, 9, 0, 'd'); tasche.rect(4, 1, 5, 2, 'g')
    roll = G(18, 5)
    roll.rect(0, 0, 17, 4, 'r')
    for x in (3, 14): roll.rect(x, 0, x, 4, 's')
    roll.rect(0, 0, 17, 0, 'R'); roll.rect(0, 4, 17, 4, 'R')
    ec = {'l': '6B4423', 'L': '845530', 'd': '3E2614', 'g': 'D9A93A', 's': '2F1D10', 'r': '2F6B4F', 'R': '24543E'}
    design('back', 'explorer_pack', 'Explorer Backpack', colors=ec,
           parts=[part(p, {}, at=(0, -6.6, hinten(7)), depth=7, anim='bob'),
                  part(klappe, {}, at=(0, -3.4, hinten(8)), depth=8, anim='bob'),
                  part(tasche, {}, at=(0, -8.6, hinten(7) + 1.75 + 0.6), depth=2.4, anim='bob'),
                  part(roll, {}, at=(0, -1.0, hinten(5)), depth=5, anim='bob')]
                 + gurte('2F1D10', 'D9A93A'))

    # --- Greatsword (diagonal) -- Klinge flach, Griff und Parierstange dicker
    s = G(9, 46)
    s.rect(3, 11, 5, 43, 'm')          # Klinge
    s.rect(4, 11, 4, 42, 'M')
    s.set(3, 43, '.'); s.set(5, 43, '.'); s.rect(4, 43, 4, 45, 'm')
    griff = G(9, 11)
    griff.rect(3, 0, 5, 1, 'p'); griff.rect(4, 2, 4, 8, 'h')
    for y in range(2, 9, 2): griff.set(4, y, 'H')
    griff.rect(0, 9, 8, 10, 'g'); griff.rect(4, 9, 4, 10, 'G')
    glanz = ["W", "W", "W", "W"]
    gc = {'p': 'D9A93A', 'h': '3B2416', 'H': '5A3A24', 'g': 'C8962E', 'G': 'F2D46B', 'm': 'B9C1CC', 'M': 'E9EEF4'}
    # Teile liegen im gedrehten Schwert-Raster: gleiche Mitte, Griff oben
    design('back', 'greatsword', 'Greatsword', colors=gc,
           parts=[part(s, {}, at=(0, -5.5, hinten(1.5)), depth=1.5, rot=(0, 0, 38), anim='bob'),
                  part(griff, {}, at=(*rel((0, -5.5), 38, 0, 8.75), hinten(3)), depth=3, rot=(0, 0, 38), anim='bob'),
                  part(glanz, {'W': 'FFFFFF'}, at=(0, -5.5, hinten(1.5) + 0.45), px=0.5, depth=0.2, rot=(0, 0, 38), glow='W', anim='glint:5')])

    # --- Crystal Scythe -- Kristallklinge pulsiert
    sc = G(26, 40)
    sc.line(14, 6, 14, 39, 'w', 0)     # Stiel
    sc.line(15, 6, 15, 39, 'W', 0)
    for y in range(10, 39, 6): sc.rect(14, y, 15, y, 'b')
    for i in range(0, 15):
        a_ = math.radians(180 + i * 6)
        for r in range(9, 14):
            x = 14 + math.cos(a_) * r * 1.0
            y = 7 + math.sin(a_) * r * 0.55
            sc.set(x, y, 'C' if r < 12 else 'c')
    sc.rect(13, 4, 16, 7, 'b')
    sc.outline('d', inner='Cc')
    design('back', 'crystal_scythe', 'Crystal Scythe', colors={'w': '4A3A6B', 'W': '6A568F', 'b': 'C9D3E6', 'C': '6FF3FF', 'c': '2BB8E6', 'd': '1C5E86'},
           parts=[part(sc, {}, at=(0, -5.2, hinten(2)), depth=2, rot=(0, 0, -28), glow='C', anim='bob'),
                  part(["..W..", ".WWW.", "WWWWW", ".WWW.", "..W.."], {'W': 'C8FBFF'}, at=(-3.9, -0.6, hinten(2) + 0.6), px=0.22, depth=0.4, glow='W', anim='twinkle')])

    # --- Guitar -- Korpus mit Volumen, Hals duenner, Saiten
    gt = G(14, 42)
    gt.disc(6.5, 33, 6.4, 'w'); gt.disc(6.5, 24, 5.0, 'w')
    gt.disc(6.5, 33, 5.4, 'W'); gt.disc(6.5, 24, 4.0, 'W')
    gt.disc(6.5, 28, 1.8, 'k')
    gt.rect(4, 37, 9, 37, 'k')
    hals = G(14, 21)
    hals.rect(5, 2, 8, 20, 'n'); hals.rect(6, 2, 7, 20, 'N')
    for y in range(4, 20, 3): hals.rect(5, y, 8, y, 'f')
    hals.rect(4, 0, 9, 3, 'h')
    for y in (0, 2): hals.set(3, y, 'f'); hals.set(10, y, 'f')
    saiten = G(14, 42)
    for x in (6, 7): saiten.line(x, 3, x, 37, 's')
    gc2 = {'w': 'B5672F', 'W': 'D8914E', 'k': '1E140E', 'n': '5A3A24', 'N': '7A5236', 'f': 'C9C9C9', 'h': '3A2416', 's': 'EDEDED'}
    design('back', 'guitar', 'Guitar', colors=gc2,
           parts=[part(gt, {}, at=(0, -6.0, hinten(5)), depth=5, rot=(0, 0, -32), anim='bob'),
                  part(hals, {}, at=(*rel((0, -6.0), -32, 0, 5.25), hinten(2)), depth=2, rot=(0, 0, -32), anim='bob'),
                  part(saiten, {}, at=(0, -6.0, hinten(5) + 1.3), depth=0.2, rot=(0, 0, -32), anim='bob')])

    # --- Katana (in der Scheide)
    k = G(5, 46)
    k.rect(1, 0, 3, 9, 'h')
    for y in range(0, 10, 2): k.rect(1, y, 3, y, 'H')
    k.rect(0, 10, 4, 11, 'g')
    k.rect(1, 12, 3, 45, 's'); k.rect(2, 12, 2, 44, 'S')
    for y in (18, 30): k.rect(1, y, 3, y, 'g')
    design('back', 'katana', 'Katana', colors={'h': '1C1C24', 'H': 'E8E1D0', 'g': 'D9A93A', 's': '7E1620', 'S': 'A82432'},
           parts=[part(k, {}, at=(0, -5.5, hinten(3)), depth=3, rot=(0, 0, 40), anim='bob')])

    # --- Surfboard
    sb = G(12, 44)
    for y in range(44):
        t_ = y / 43
        w = 5.6 * math.sin(math.pi * min(1, (t_ * 1.05) + 0.02)) ** 0.6
        for x in range(12):
            if abs(x - 5.5) <= w: sb.set(x, y, 'w')
    for y in range(44):
        for x in range(12):
            if sb.get(x, y) == 'w' and abs(x - 5.5) < 1.2: sb.set(x, y, 'b')
            if sb.get(x, y) == 'w' and 14 < y < 20: sb.set(x, y, 'y')
    sb.outline('B')
    design('back', 'surfboard', 'Surfboard', colors={'w': 'F7F1E1', 'b': '2B8FE6', 'y': 'FFC93C', 'B': '1F6FB8'},
           parts=[part(sb, {}, at=(0, -5.5, hinten(2)), depth=2, rot=(0, 0, -14), anim='bob')])

    # --- Rocket Pack -- zwei runde Tanks, Mittelteil, flackernde Flammen
    tank = G(5, 18)
    tank.rect(0, 3, 4, 17, 'm'); tank.rect(1, 3, 1, 17, 'M'); tank.rect(4, 3, 4, 17, 'n')
    tank.rect(1, 0, 3, 2, 'r'); tank.rect(0, 2, 4, 2, 'r')
    tank.rect(0, 15, 4, 17, 'd')
    mitte = G(6, 11)
    mitte.rect(0, 0, 5, 10, 'd'); mitte.rect(2, 2, 3, 3, 'L'); mitte.rect(1, 6, 4, 6, 'm'); mitte.rect(1, 8, 4, 8, 'm')
    flamme = G(3, 7)
    flamme.rect(0, 0, 2, 1, 'Y'); flamme.rect(0, 2, 2, 4, 'O'); flamme.rect(1, 5, 1, 6, 'R')
    rc = {'m': 'B8BEC9', 'M': 'E6EAF0', 'n': '8A909C', 'r': 'D92E3A', 'd': '3A4150', 'L': '5EF2FF', 'Y': 'FFF36A', 'O': 'FF9A2E', 'R': 'FF4A2E'}
    design('back', 'rocket_pack', 'Rocket Pack', colors=rc,
           parts=[part(tank, {}, at=(-2.25, -6.0, hinten(5)), depth=5, anim='bob'),
                  part(tank, {}, at=(2.25, -6.0, hinten(5)), depth=5, anim='bob'),
                  part(mitte, {}, at=(0, -5.6, hinten(3)), depth=3, anim='bob', glow='L'),
                  part(flamme, {}, at=(-2.25, -12.4, hinten(5)), depth=3, glow='YOR', anim='flicker'),
                  part(flamme, {}, at=(2.25, -12.4, hinten(5)), depth=3, glow='YOR', anim='flicker')]
                 + gurte('3A4150'))

    # --- Quiver -- runder Koecher mit Pfeilen
    q = G(10, 30)
    q.rect(2, 8, 7, 29, 'l'); q.rect(3, 8, 3, 29, 'L')
    q.rect(2, 12, 7, 12, 'd'); q.rect(2, 24, 7, 24, 'd')
    pfeile = G(10, 9)
    for i, x in enumerate((2, 4, 6)):
        pfeile.rect(x + 1, 3 + i, x + 1, 8, 's')
        pfeile.rect(x, i, x + 2, 2 + i, 'f' if i != 1 else 'F')
    qc = {'l': '7A4A26', 'L': '9A6236', 'd': '3E2614', 's': 'C9B28A', 'f': 'F2F2F2', 'F': 'D9283A'}
    design('back', 'quiver', 'Quiver', colors=qc,
           parts=[part(q, {}, at=(1.5, -4.5, hinten(5)), depth=5, rot=(0, 0, -22), anim='bob'),
                  part(pfeile, {}, at=(*rel((1.5, -4.5), -22, 0, 5.25), hinten(5)), depth=2, rot=(0, 0, -22), anim='bob')])

# ======================================================================
# AURAS -- kleine Sprites, die um den ganzen Koerper schweben
# ======================================================================
def aura(id_, name, motion, count, sprites, colors, px=0.7, glow='', spin=1.0, size=1.0, legacy=''):
    design('aura', id_, name, motion=motion, count=count, sprites=sprites, colors=colors, px=px, glow=glow, spin=spin, size=size, legacy=legacy)

def auras():
    aura('sakura', 'Sakura Aura', 'fall', 10,
         [[".p.", "pyp", ".p."], ["..p", ".pP", "pP."], [".pp", "pPp", "pp."]],
         {'p': 'FFB3D1', 'P': 'FF7FB0', 'y': 'FFE58A'}, legacy='cherry')
    aura('snow', 'Snow Aura', 'fall', 12,
         [["w.w.w", ".wcw.", "wcCcw", ".wcw.", "w.w.w"], [".w.", "wcw", ".w."]],
         {'w': 'FFFFFF', 'c': 'BFEAFF', 'C': '8FD8FF'}, glow='C', legacy='snow')
    aura('autumn', 'Autumn Aura', 'fall', 9,
         [["..o.", ".ooo", "oooo", ".rr.", "..b."], ["..y", ".yo", "yo.", "b.."], [".r.", "rRr", ".b."]],
         {'o': 'F07D1E', 'r': 'D2381F', 'R': 'A8221A', 'y': 'F5C036', 'b': '6B3A1E'})
    aura('butterfly', 'Butterfly Aura', 'flutter', 6,
         [["cc...cc", "cCc.cCc", "ccckccc", ".ccKcc.", ".cc.cc."], ["pp...pp", "pPp.pPp", "pppkppp", ".ppKpp.", ".pp.pp."]],
         {'c': '45E0C8', 'C': 'C9FFF5', 'p': 'FF6FA8', 'P': 'FFD0E3', 'k': '1D1D24', 'K': '3A3A44'})
    aura('flame', 'Flame Aura', 'rise', 10,
         [[".Y.", "YyY", "yoy", "oro", ".r."], ["Y.", "yY", "oy", "ro"]],
         {'Y': 'FFF6A0', 'y': 'FFD23A', 'o': 'FF8A1F', 'r': 'E8401C'}, glow='Yyor', legacy='flames')
    aura('soul', 'Soul Aura', 'rise', 10,
         [[".W.", "WcW", "cbc", "bBb", ".B."], ["W.", "cW", "bc", "Bb"]],
         {'W': 'E8FFFF', 'c': '7FF3FF', 'b': '2BC4E8', 'B': '1A7FAF'}, glow='WcbB', legacy='soul_flames')
    aura('yin_yang', 'Yin-Yang Aura', 'twin', 2,
         [["....wwwwww....", "..wwwwwwwwww..", ".www......www.", "ww..........ww", "w............w"],
          ["....kkkkkk....", "..kkkkkkkkkk..", ".kkk......kkk.", "kk..........kk", "k............k"]],
         {'w': 'F6F6F6', 'k': '18181E'}, px=0.8, spin=0)
    aura('neon_rings', 'Neon Rings', 'rings', 3,
         [[".pppppp.", "p......p", "p......p", "p......p", "p......p", ".pppppp."]],
         {'p': 'C06BFF'}, px=1.6, glow='p', spin=0, legacy='magic')
    aura('hearts', 'Heart Aura', 'orbit', 6,
         [[".r.r.", "rRrrr", "rrrrr", ".rrr.", "..r.."]],
         {'r': 'F2385F', 'R': 'FFB0C2'}, glow='', legacy='hearts')
    aura('stars', 'Star Aura', 'twinkle', 9,
         [["..y..", ".yYy.", "yYWYy", ".yYy.", "..y.."], [".y.", "yWy", ".y."]],
         {'y': 'FFC93A', 'Y': 'FFE58A', 'W': 'FFFFFF'}, glow='yYW', legacy='sparkles,totem,glow')
    aura('music', 'Music Aura', 'orbit', 5,
         [["..kk", "..kK", "..k.", "..k.", "kkk.", "kk.."], [".kkkk", ".k..k", ".k..k", "kk.kk", "kk.kk"]],
         {'k': 'B26BFF', 'K': 'E2C6FF'}, glow='kK', legacy='notes')
    aura('lightning', 'Lightning Aura', 'flicker', 7,
         [["....yy", "...yWy", "..yWy.", ".yWy..", "yWWWWy", "..yWy.", ".yWy..", ".yy...", "yy....", "y....."],
          ["..yy", ".yW.", "yWWy", ".yWy", "yW..", "y..."]],
         {'y': '7FD8FF', 'W': 'FFFFFF'}, glow='yW', px=0.6, legacy='electric')
    aura('bubbles', 'Bubble Aura', 'rise', 10,
         [[".bb.", "bWcb", "bccb", ".bb."], [".b.", "bWb", ".b."]],
         {'b': '7FD4FF', 'c': 'CDEFFF', 'W': 'FFFFFF'}, spin=0)
    aura('emerald', 'Emerald Aura', 'orbit', 7,
         [[".g.", "gGg", ".g."], ["..g..", ".gGg.", "gGWGg", ".gGg.", "..g.."]],
         {'g': '2FD06A', 'G': '8BFFB4', 'W': 'FFFFFF'}, glow='gGW', legacy='emerald,enchant')

def auras_neu():
    # Seit 4.38: neue Bewegungen (spiral, halo, vortex, swarm, atom, rain)
    aura('galaxy', 'Galaxy Aura', 'spiral', 12,
         [["..w..", ".wPw.", "wPWPw", ".wPw.", "..w.."], [".p.", "pPp", ".p."], ["b"]],
         {'w': 'E8DCFF', 'P': 'B98CFF', 'W': 'FFFFFF', 'p': '8B5CF6', 'b': '6EA8FF'}, glow='wPWpb')
    aura('halo', 'Halo', 'halo', 14,
         [[".y.", "yWy", ".y."], ["y"]],
         {'y': 'FFD75E', 'W': 'FFF6C8'}, glow='yW', px=0.45, spin=0)
    aura('tornado', 'Tornado Aura', 'vortex', 18,
         [[".gg.", "gGGg", ".gg."], [".g.", "gGg", ".g."]],
         {'g': 'B9C2CF', 'G': 'E9EEF5'}, px=0.65)
    aura('fireflies', 'Firefly Aura', 'swarm', 14,
         [[".y.", "yWy", ".y."], [".yy.", "yWWy", "yWWy", ".yy."]],
         {'y': 'D7FF5E', 'W': 'FFFFD9'}, glow='yW', px=0.6, spin=0)
    aura('atom', 'Atom Aura', 'atom', 9,
         [[".c.", "cWc", ".c."], ["..c..", ".cCc.", "cCWCc", ".cCc.", "..c.."]],
         {'c': '35D6FF', 'C': '9BEBFF', 'W': 'FFFFFF'}, glow='cCW', px=0.55, spin=0)
    aura('rain_cloud', 'Rain Aura', 'rain', 20,
         [[".b.", "bBb", "bBb", ".B."], ["b", "B", "B"]],
         {'b': 'BFE6FF', 'B': '5AA9E6'}, glow='b', px=0.55, spin=0)
    aura('embers', 'Ember Aura', 'vortex', 16,
         [[".o.", "oyo", ".o."], ["yo", "or"], [".r.", "roy", ".r."]],
         {'y': 'FFE27A', 'o': 'FF9A3C', 'r': 'E8401C'}, glow='yor', px=0.55)
    aura('void', 'Void Aura', 'spiral', 12,
         [[".k.", "kPk", ".k."], ["..p..", ".pkp.", "pkKkp", ".pkp.", "..p.."]],
         {'k': '1A1024', 'K': '000000', 'p': '7A2BD9', 'P': 'C27BFF'}, glow='pP')
    aura('gold_coins', 'Coin Aura', 'orbit', 8,
         [[".yyy.", "yYYYy", "yYoYy", "yYYYy", ".yyy."]],
         {'y': 'E9A812', 'Y': 'FFD75E', 'o': 'B07A00'}, glow='Y', px=0.6)
    aura('ice', 'Ice Aura', 'twinkle', 12,
         [["..c..", ".cWc.", "cWWWc", ".cWc.", "..c.."], ["c.c", ".W.", "c.c"]],
         {'c': '8FE3FF', 'W': 'F2FCFF'}, glow='cW', px=0.6)

# ======================================================================
# SHIELD SKINS -- Texturen 128x128 (Schild-Modell: Platte vorne bei (2,2) 24x44)
# ======================================================================
def hats():
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import huete
    huete.huete(design)

def pets():
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import tiere
    tiere.tiere(design)

def shields():
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import schilde
    schilde.erzeugen(os.path.join(RES, 'textures', 'cosmetics', 'shield'), design)


# ======================================================================
# NEU IN 4.37 -- weitere Face, Bandanas und Back-Items
# ======================================================================
def face_neu():
    F = 0.25
    # --- Aviators: Tropfenglaeser mit Goldrahmen, Verlauf braun -> bernstein, Doppelsteg
    def aviator(x, y):
        for cx in (9.0, 27.0):
            dx, dy = (x - cx) / 7.4, (y - 5.6) / 5.6
            seite = 1 if cx < 18 else -1
            rx = 1.0 - 0.28 * max(0.0, dy) + 0.08 * seite * dx * (dy > 0)
            e = (dx / rx) ** 2 + dy ** 2
            if e <= 1.0:
                if e > 0.80: return 'G' if dy < 0 else 'g'
                if -0.75 < dx + dy * 0.6 < -0.45 and dy < 0.2: return 'w'
                k = (dy + 1) / 2
                return 'a' if k < 0.35 else ('b' if k < 0.7 else 'c')
        if 15.0 <= x <= 21.0 and (0.9 <= y <= 1.9 or 3.6 <= y <= 4.3): return 'g'
        return '.'
    design('face', 'aviators', 'Aviators', arms='g', colors={'g': 'C8962E', 'G': 'FFE08A', 'a': '2B1E16', 'b': '6B3A1E', 'c': 'B8742C', 'w': 'FFF3D6'},
           parts=[part(raster(36, 12, aviator), {}, at=(0, 3.9, -4.85), px=F),
                  part(["W.", "WW", ".W"], {'W': 'FFFFFF'}, at=(-2.2, 4.4, -5.0), px=0.3, depth=0.3, rot=(0, 0, -20), glow='W', anim='glint:3.6')])

    # --- Eye Patch: Augenklappe mit Band schraeg ueber den Kopf
    def klappe(x, y):
        e = ((x - 6) / 6.0) ** 2 + ((y - 5) / 5.2) ** 2
        if e > 1: return '.'
        if e > 0.78: return 'K'
        return 'k' if (x - 6) + (y - 5) > -3 else 'h'
    # Band: schraeg ueber das Gesicht genau von Kante zu Kante (36 px = 9 Pixel lang, 62 Grad),
    # dann an beiden Kopfseiten nach hinten -- vorher ragte es weit ueber den Kopf hinaus
    band = ['s'] * 36
    seite = ['s' * 33]
    design('face', 'eye_patch', 'Eye Patch', colors={'k': '15151A', 'K': '2A2A33', 'h': '3A3A46', 's': '1E1E26'},
           parts=[part(raster(12, 10, klappe), {}, at=(-2.0, 4.2, -4.9), px=F, depth=1.2),
                  part(band, {}, at=(0.0, 4.9, -4.82), px=F, depth=0.6, rot=(0, 0, -62)),
                  part(seite, {}, at=(4.1, 7.0, 0.0), px=F, depth=0.6, rot=(0, 90, 0)),
                  part(seite, {}, at=(-4.1, 2.8, 0.0), px=F, depth=0.6, rot=(0, 90, 0))])

    # --- Shutter Shades: Lamellen-Brille in Neon, durch die Schlitze sieht man die Augen
    def shutter(x, y):
        if y < 0.0 or y > 11.0: return '.'
        aussen = x < 1.2 or x > 34.8 or y < 1.2 or y > 9.8
        steg = 16.5 <= x <= 19.5 and y > 6.0
        if steg: return '.'
        if aussen: return 'P'
        if 16.0 <= x <= 20.0: return 'P' if y < 3 else '.'
        return 'p' if int(y) % 2 == 0 else '.'
    design('face', 'shutter_shades', 'Shutter Shades', arms='P', colors={'P': 'FF2BD6', 'p': 'FF7BE6'},
           parts=[part(raster(36, 11, shutter), {}, at=(0, 4.0, -4.85), px=F, glow='Pp')])

    # --- Ski Goggles: grosse Brille mit Spiegelglas (orange -> violett) und breitem Band
    def brille(x, y):
        dx, dy = (x - 20) / 19.5, (y - 7) / 7.2
        e = abs(dx) ** 3.2 + abs(dy) ** 2.6
        nase = abs(x - 20) < 3.2 and y > 10.2
        if e > 1 or nase: return '.'
        if e > 0.72: return 'f' if dy > -0.3 else 'F'
        k = (y - 1) / 12
        if -2.5 < (x - 20) * 0.35 + (y - 7) < -0.8 and x < 16: return 'w'
        return 'o' if k < 0.33 else ('m' if k < 0.66 else 'v')
    design('face', 'ski_goggles', 'Ski Goggles', colors={'F': '2A2F3A', 'f': '1A1E26', 'o': 'FFB13B', 'm': 'FF5F7E', 'v': '7B5CFF', 'w': 'FFF6E8'},
           ring=dict(rows=['kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk', 'cccccccccccccccccccccccccccccccc', 'kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk'],
                     y0=3.3, px=0.5, knot='k', tails=''),
           parts=[part(raster(40, 14, brille), {}, at=(0, 4.0, -5.05), px=F, depth=1.6)],
           **{})
    OUT[-1]['colors'].update({'k': '1A1E26', 'c': '3BC9FF'})


def bandanas_neu():
    # Neon: zwei leuchtende Wellen (cyan, magenta) auf fast schwarz
    def neon(x, y):
        a = 1.5 + 1.5 * math.sin(x * 2 * math.pi / 16)
        b = 1.5 + 1.5 * math.sin(x * 2 * math.pi / 16 + math.pi)
        if abs(y - a) < 0.55: return 'C'
        if abs(y - b) < 0.55: return 'M'
        return 'k'
    bandana('neon', 'Neon', {'k': '0B0B1A', 'C': '38E8FF', 'M': 'FF3EDB'}, band(neon), 'kC', glow='CM')
    # Lava: dunkles Gestein mit gluehenden Rissen
    rnd = random.Random(21)
    riss = set()
    for _ in range(9):
        x, y = rnd.randrange(N), rnd.randrange(4)
        for _ in range(9):
            riss.add((x % N, y)); x += 1; y = max(0, min(3, y + rnd.choice((-1, 0, 0, 1))))
    def lava(x, y):
        if (x, y) in riss: return 'y' if (x * 7 + y) % 5 == 0 else 'o'
        return 'K' if (x // 3 + y * 2) % 5 == 0 else 'k'
    bandana('lava', 'Lava', {'k': '1B1414', 'K': '2E2220', 'o': 'FF6A1F', 'y': 'FFD23A'}, band(lava), 'ko', glow='oy')
    # Ocean: Blauverlauf mit weissen Wellenkaemmen
    def ozean(x, y):
        kamm = 1 + (1 if (x % 12) in (3, 4, 5, 6) else 0) - (1 if (x % 12) in (5,) else 0)
        if y == kamm and (x % 12) in (2, 3, 4, 5, 6, 7): return 'w'
        return 'l' if y <= 1 else 'd'
    bandana('ocean', 'Ocean', {'l': '2E9BE6', 'd': '1C5FB0', 'w': 'EAF8FF'}, band(ozean), 'dl')
    # Tiger: orange mit schwarzen Streifen
    rnd = random.Random(5)
    streifen = [rnd.randrange(N) for _ in range(14)]
    def tiger(x, y):
        for s in streifen:
            if (x - s - y // 2) % N < (2 if y in (1, 2) else 1): return 'k'
        return 'O' if y in (1, 2) else 'o'
    bandana('tiger', 'Tiger', {'o': 'E07A1F', 'O': 'F59A3A', 'k': '1A1210'}, band(tiger), 'ok')
    # Urban Camo: Grautoene
    rnd = random.Random(11)
    cam = [[rnd.choice('aabcd') for _ in range(N)] for _ in range(4)]
    for _ in range(3):
        cam = [[max(set(w := [cam[y][x], cam[y][(x + 1) % N], cam[y][x - 1], cam[(y + 1) % 4][x]]), key=w.count) for x in range(N)] for y in range(4)]
    bandana('urban_camo', 'Urban Camo', {'a': '9AA0A8', 'b': '6B717A', 'c': '3E434A', 'd': 'D0D4DA'}, [''.join(r) for r in cam], 'ac')
    # Classic: rotes Tuch mit weissen Tupfen und Randlinie
    def klassik(x, y):
        if y == 0 and x % 4 == 0: return 'w'
        if y == 3 and x % 4 == 2: return 'w'
        if y in (1, 2) and (x % 8, y) in ((1, 1), (2, 2), (5, 1), (6, 2)): return 'W'
        return 'r'
    bandana('classic', 'Classic', {'r': 'B3161E', 'w': 'F6F1E8', 'W': 'FFFFFF'}, band(klassik), 'rw')


def back_neu():
    # --- Cyber Pack: kantiger Rucksack, leuchtender Kern, Lueftungsschlitze
    k = G(14, 16)
    k.rect(0, 0, 13, 15, 'd'); k.rect(1, 1, 12, 14, 'D')
    for y in (3, 5, 7): k.rect(2, y, 4, y, 'v'); k.rect(9, y, 11, y, 'v')
    k.rect(0, 0, 13, 0, 'e'); k.rect(0, 15, 13, 15, 'e')
    kern = G(6, 6)
    kern.disc(2.5, 2.5, 2.6, 'C'); kern.disc(2.5, 2.5, 1.4, 'W')
    streif = ["L"] * 12
    cc = {'d': '1B1F2A', 'D': '262B38', 'v': '0E1118', 'e': '3A4152', 'C': '22D3EE', 'W': 'E0FBFF', 'L': '22D3EE'}
    design('back', 'cyber_pack', 'Cyber Pack', colors=cc,
           parts=[part(k, {}, at=(0, -6.4, hinten(5)), depth=5, anim='bob'),
                  part(kern, {}, at=(0, -8.4, hinten(5) + 1.35), depth=0.6, glow='CW', anim='pulse:0.12'),
                  part(streif, {}, at=(-3.3, -6.4, hinten(5) + 1.3), px=0.5, depth=0.3, glow='L', anim='bob'),
                  part(streif, {}, at=(3.3, -6.4, hinten(5) + 1.3), px=0.5, depth=0.3, glow='L', anim='bob')]
                 + gurte('1B1F2A', '22D3EE'))

    # --- Battle Axe: Doppelaxt diagonal
    stiel = G(4, 44)
    stiel.rect(1, 0, 2, 43, 'h')
    for y in range(4, 44, 5): stiel.rect(1, y, 2, y, 'H')
    stiel.rect(0, 40, 3, 43, 'g')
    kopf = G(20, 14)
    for y in range(14):
        for x in range(20):
            dx = abs(x - 9.5)
            breite = 3 + 6.5 * (1 - abs(y - 6.5) / 7.5) ** 0.5
            if dx < 1.5 or (dx < breite and dx > 1.5 and abs(y - 6.5) < 2 + dx * 0.55):
                g_ = 'm' if dx < breite - 1.2 else 'M'
                kopf.set(x, y, 'b' if dx < 1.5 else g_)
    ac = {'h': '5A3A24', 'H': '3B2416', 'g': 'C8962E', 'm': 'A7B0BC', 'M': 'E6ECF2', 'b': '4A505A'}
    design('back', 'battle_axe', 'Battle Axe', colors=ac,
           parts=[part(stiel, {}, at=(0, -5.5, hinten(2)), depth=2, rot=(0, 0, -36), anim='bob'),
                  part(kopf, {}, at=(*rel((0, -5.5), -36, 0, 8.5), hinten(2.4)), depth=2.4, rot=(0, 0, -36), anim='bob')])

    # --- Skateboard: Deck mit Grafik, Achsen und Rollen
    d = G(10, 40)
    for y in range(40):
        for x in range(10):
            ry = min(y, 39 - y)
            if ry < 4 and math.hypot(x - 4.5, 4 - ry) > 5.2: continue
            d.set(x, y, 'k')
    for y in range(40):
        for x in range(10):
            if d.get(x, y) == 'k':
                if 10 < y < 30 and abs(x - 4.5) < 3.2: d.set(x, y, 'p' if (y // 3) % 2 else 'P')
                elif abs(x - 4.5) < 0.6: d.set(x, y, 'y')
    achse = G(12, 3)
    achse.rect(1, 1, 10, 1, 's'); achse.rect(0, 0, 1, 2, 'w'); achse.rect(10, 0, 11, 2, 'w')
    sc = {'k': '1C1C22', 'p': '7C3AED', 'P': '22D3EE', 'y': 'F2D46B', 's': 'B9C1CC', 'w': 'F2F2F2'}
    design('back', 'skateboard', 'Skateboard', colors=sc,
           parts=[part(d, {}, at=(0, -5.5, hinten(1.2)), depth=1.2, rot=(0, 0, 18), anim='bob'),
                  part(achse, {}, at=(*rel((0, -5.5), 18, 0, 6.5), hinten(1.2) + 1.0), depth=1.4, rot=(0, 0, 18), anim='bob'),
                  part(achse, {}, at=(*rel((0, -5.5), 18, 0, -6.5), hinten(1.2) + 1.0), depth=1.4, rot=(0, 0, 18), anim='bob')])


# ======================================================================
# FLUEGEL (seit 4.63) -- Fluegel-Rucksaecke mit Gelenken
# ======================================================================
# Je Design EIN Fluegel (der rechte, von hinten gesehen): Spalte 0 = am Ruecken,
# rechts = Fluegelspitze, Zeile 0 = oben. Der Client spiegelt ihn fuer links, teilt
# ihn in drei Glieder (Schulter, Mitte, Spitze) und bewegt sie einzeln: angelegt beim
# Schleichen, halb offen im Stehen, weit offen und kraeftig schlagend beim Fliegen.
# anim=wing:<art>  art: feather (Federn), bat (Haut zwischen Knochen), butterfly
FW, FH = 40, 32

def _feder(g, ax, ay, winkel, laenge, breite, fuell, rand, kiel, spitze=None):
    """Eine Feder: Ansatz (ax, ay), Richtung in Grad (0 = nach aussen, 90 = nach unten),
    vorne rund, zum Ansatz schmal. Rand dunkler, Kiel in der Mitte, Spitze optional anders."""
    w = math.radians(winkel)
    dx, dy = math.cos(w), math.sin(w)
    nx, ny = -dy, dx
    x0, x1 = int(min(ax, ax + dx * laenge) - breite - 2), int(max(ax, ax + dx * laenge) + breite + 2)
    y0, y1 = int(min(ay, ay + dy * laenge) - breite - 2), int(max(ay, ay + dy * laenge) + breite + 2)
    for y in range(max(0, y0), min(g.h, y1 + 1)):
        for x in range(max(0, x0), min(g.w, x1 + 1)):
            px_, py_ = x + 0.5 - ax, y + 0.5 - ay
            t = (px_ * dx + py_ * dy) / laenge
            q = px_ * nx + py_ * ny
            if t < 0 or t > 1.04: continue
            halb = breite * (0.45 + 0.55 * math.sin(math.pi * min(1.0, t * 0.62 + 0.38))) * (1.0 if t < 0.82 else math.sqrt(max(0.0, (1.04 - t) / 0.22)))
            if abs(q) > halb: continue
            if q > halb - 0.75 or t > 0.985: ch = rand
            elif abs(q) < 0.45 and t < 0.86: ch = kiel
            elif spitze and t > 0.78: ch = spitze
            else: ch = fuell
            g.set(x, y, ch)

def _federn(innen, mitte, aussen, kiel, rand, glanz=None, flamme=False, kristall=False, energie=False):
    """Federfluegel aus einzelnen Federn in Schichten: lange Handschwingen aussen (zuerst,
    liegen hinten), Armschwingen innen, darueber zwei Reihen Deckfedern und die Vorderkante."""
    g = G(FW, FH)
    spitze_h = 'F' if flamme else ('E' if energie else ('S' if kristall else None))
    # Handschwingen: am Unterarm vom Handgelenk zur Spitze, faechern von "nach unten" zu "nach aussen"
    for k in range(6):
        a = k / 5
        ax, ay = 21 + a * 14, 4.2 + a * 4.6
        _feder(g, ax, ay, 82 - a * 56, 25 - a * 7, 3.7, aussen, rand, kiel, spitze_h)
    # Armschwingen: am Oberarm, haengen nach unten
    for k in range(5):
        a = k / 4
        ax, ay = 3 + a * 17, 8.5 - a * 4.5
        _feder(g, ax, ay, 95 - a * 12, 17 + a * 5, 3.9, mitte, rand, kiel, spitze_h if flamme else None)
    # Deckfedern: zwei Reihen kleiner Federn entlang der Vorderkante
    for k in range(7):
        a = k / 6
        ax, ay = 3 + a * 29, 9.5 - 5.5 * math.sin(math.pi * min(1.0, a * 0.95)) + a * 1.5
        _feder(g, ax, ay, 76 - a * 34, 10.5, 3.4, innen, rand, innen)
    for k in range(8):
        a = k / 7
        ax, ay = 1 + a * 33, 7.5 - 5.8 * math.sin(math.pi * min(1.0, a * 0.9)) + a * 2.2
        _feder(g, ax, ay, 66 - a * 30, 6.5, 3.0, innen, innen, innen)
    # Vorderkante (der Knochen unter den Federn)
    for x in range(FW - 3):
        a = x / (FW - 1)
        y = 7.0 - 5.8 * math.sin(math.pi * min(1.0, a * 0.9)) + a * 2.2
        for dy in (-1, 0, 1):
            if g.get(x, int(round(y + dy))) != '.' or dy == 0: g.set(x, y + dy, innen)
        if glanz: g.set(x, y - 1, glanz)
    if kristall:
        for (x, y) in ((8, 12), (14, 18), (24, 14), (30, 20), (19, 24), (34, 16), (11, 22)):
            if g.get(x, y) != '.': g.set(x, y, 'S')
    g.outline(rand)
    return g

def _haut(haut, haut2, knochen, rand, kralle, schuppen=False, dornen=False):
    """Haut-Fluegel (Fledermaus/Drache): Arm bis zum Handgelenk, Finger zur Kante, Haut mit Boegen."""
    g = G(FW, FH)
    schulter, hand = (0.02, 0.22), (0.46, 0.10)
    finger = [(0.99, 0.16), (0.90, 0.62), (0.66, 0.94), (0.36, 0.90)]
    def strecke(ax, ay, bx, by, u, v):
        dx, dy = bx - ax, by - ay
        t = max(0.0, min(1.0, ((u - ax) * dx + (v - ay) * dy) / (dx * dx + dy * dy)))
        return math.hypot(u - (ax + t * dx), v - (ay + t * dy)), t
    winkel = [math.atan2(fy - hand[1], fx - hand[0]) for fx, fy in finger] + [math.atan2(0.88 - hand[1], 0.04 - hand[0])]
    laengen = [math.hypot(fx - hand[0], fy - hand[1]) for fx, fy in finger] + [math.hypot(0.04 - hand[0], 0.88 - hand[1])]
    for y in range(FH):
        for x in range(FW):
            u, v = (x + 0.5) / FW, (y + 0.5) / FH
            d_arm, _ = strecke(*schulter, *hand, u, v)
            knoch = d_arm < 0.032
            for fx, fy in finger:
                d, t = strecke(*hand, fx, fy, u, v)
                if d < 0.024 + 0.012 * (1 - t): knoch = True
            # Haut: im Faecher zwischen den Fingern, mit Boegen zur Kante
            w = math.atan2(v - hand[1], u - hand[0]); r = math.hypot(u - hand[0], v - hand[1])
            haut_da = False
            for i in range(len(winkel) - 1):
                a0, a1 = winkel[i], winkel[i + 1]
                if a0 <= w <= a1 or (a1 < a0 and (w >= a0 or w <= a1)):
                    a = (w - a0) / (a1 - a0) if a1 != a0 else 0
                    rand_r = laengen[i] + (laengen[i + 1] - laengen[i]) * a - 0.13 * math.sin(math.pi * a)
                    haut_da = r < rand_r
            oberhalb_arm = v < 0.22 - 0.12 * min(1.0, u / 0.46) - 0.02
            if knoch and not oberhalb_arm: ch = knochen
            elif haut_da and not oberhalb_arm and u > 0.015:
                ch = haut2 if (schuppen and (x + (y // 2) * 2) % 4 == 0) or (not schuppen and (x * 3 + y) % 7 == 0) else haut
            else: continue
            g.set(x, y, ch)
    # Krallen an den Fingerspitzen, Dornen auf dem Arm
    for fx, fy in finger[:1]:
        g.set(fx * FW - 1, fy * FH - 1, kralle); g.set(fx * FW, fy * FH - 2, kralle)
    g.set(hand[0] * FW, hand[1] * FH - 2, kralle); g.set(hand[0] * FW + 1, hand[1] * FH - 3, kralle)
    if dornen:
        for k in range(1, 5):
            px_ = k / 5 * hand[0]; py_ = schulter[1] + (hand[1] - schulter[1]) * k / 5
            g.set(px_ * FW, py_ * FH - 2, kralle)
    g.outline(rand)
    return g

def _falter(c1, c2, auge, ader, rand, glanz):
    """Schmetterling: grosser oberer und kleiner unterer Fluegel, Adern, Augenflecken."""
    g = G(FW, FH)
    for y in range(FH):
        for x in range(FW):
            u, v = (x + 0.5) / FW, (y + 0.5) / FH
            o = ((u - 0.50) / 0.50) ** 2 + ((v - 0.30) / 0.30) ** 2
            un = ((u - 0.34) / 0.34) ** 2 + ((v - 0.72) / 0.27) ** 2
            if o > 1 and un > 1: continue
            ch = c1 if o <= 1 else c2
            # Verlauf nach aussen heller
            if o <= 1 and o > 0.55: ch = c2
            w = math.atan2(v - 0.48, u + 0.05)
            if abs(math.sin(w * 7)) < 0.12: ch = ader
            r_auge = math.hypot(u - 0.68, v - 0.27)
            if r_auge < 0.10: ch = auge if r_auge > 0.065 else ('O' if r_auge > 0.03 else auge)
            if math.hypot(u - 0.38, v - 0.76) < 0.05: ch = glanz
            g.set(x, y, ch)
    g.outline(rand)
    return g

def fluegel_neu():
    def wing(id_, name, art, colors, glow='', kind='feather', px=0.42):
        design('back', id_, name, colors=colors,
               parts=[part(art, {}, at=(1.4, -2.4, hinten(1, px, extra=0.6)), px=px, depth=1.0, glow=glow, anim='wing:' + kind)])

    wing('angel_wings', 'Angel Wings', _federn('w', 'W', 'b', 'B', 'o', glanz='g'),
         {'w': 'FFFFFF', 'W': 'EEF3FB', 'b': 'DCE6F6', 'B': 'C3D2EC', 'o': '9FB3D6', 'g': 'FFE08A'}, glow='g')
    wing('demon_wings', 'Demon Wings', _haut('m', 'M', 'r', 'k', 'R'),
         {'m': '3A1238', 'M': '531A50', 'r': '1A0818', 'k': '0B0309', 'R': 'FF3B3B'}, glow='R', kind='bat')
    wing('dragon_wings', 'Dragon Wings', _haut('d', 'D', 'b', 'k', 'H', schuppen=True, dornen=True),
         {'d': '1F6B52', 'D': '2C8C6A', 'b': '163B30', 'k': '07140F', 'H': 'E6D9A8'}, kind='bat')
    wing('phoenix_wings', 'Phoenix Wings', _federn('y', 'o', 'r', 'R', 'k', glanz='Y', flamme=True),
         {'y': 'FFC93A', 'o': 'FF8A1F', 'r': 'E8401C', 'R': 'B3200F', 'k': '5A1206', 'Y': 'FFF1A8', 'F': 'FFE36B'}, glow='yYF')
    wing('vortex_wings', 'Vortex Wings', _federn('v', 'p', 'P', 'c', 'k', glanz='c', energie=True),
         {'v': '6D28D9', 'p': '7C3AED', 'P': '5B21B6', 'c': '67E8F9', 'k': '1E0B45', 'E': 'A5F3FC'}, glow='cE')
    wing('frost_wings', 'Frost Wings', _federn('i', 'I', 'b', 'w', 'n', glanz='w', kristall=True),
         {'i': 'D6F3FF', 'I': 'B4E6FB', 'b': '8FD3F4', 'w': 'FFFFFF', 'n': '4E9CC4', 'S': 'FFFFFF'}, glow='wS')
    wing('shadow_wings', 'Shadow Wings', _federn('a', 'b', 'c', 'V', 'k', glanz='V'),
         {'a': '22202B', 'b': '1A1822', 'c': '111018', 'V': 'B26BFF', 'k': '050408'}, glow='V')
    wing('butterfly_wings', 'Butterfly Wings', _falter('p', 'P', 'k', 'K', 'k', 'w'),
         {'p': '3B82F6', 'P': '60D0FF', 'k': '0F172A', 'K': '1E293B', 'w': 'E0F2FE', 'O': 'FB923C'}, glow='O', kind='butterfly', px=0.36)


# ======================================================================
# NECK (seit 4.63) -- Ketten um den Hals mit pendelndem Anhaenger
# ======================================================================
# chain: link (Glieder), cuban (dicke flache Glieder), rope (gedreht), beads (Perlen), ball (Kugelkette)
# Farben: c = Kette, C = Glanzlicht. drop = wie tief die Kette vorne haengt (Pixel).
# Teile: anim=pendant (der Anhaenger, pendelt), anim=icon (nur das Bild im Menue).

def _anhaenger(form):
    if form == 'coin':
        g = G(7, 7); g.disc(3, 3, 3.2, 'p'); g.disc(3, 3, 2.1, 'P')
        for (x, y) in ((1, 2), (2, 3), (3, 4), (4, 3), (5, 2)): g.set(x, y, 'q')
        return g
    if form == 'tag':
        g = G(5, 8); g.rect(0, 1, 4, 7, 'p'); g.rect(1, 2, 3, 6, 'P'); g.set(2, 1, '.'); g.rect(1, 4, 3, 4, 'q'); return g
    if form == 'diamond':
        g = G(7, 8)
        for y in range(8):
            for x in range(7):
                if abs(x - 3) + abs(y - 2.5) * (1.0 if y < 3 else 0.62) <= 3.2: g.set(x, y, 'p')
        g.set(2, 1, 'q'); g.set(3, 2, 'q'); g.set(4, 4, 'P'); return g
    if form == 'crystal':
        g = G(5, 10)
        for y in range(10):
            w = min(y, 9 - y, 2)
            for x in range(2 - w, 3 + w): g.set(x, y, 'p')
        for y in range(2, 8): g.set(1, y, 'q')
        g.rect(0, 0, 4, 0, 'P'); return g
    if form == 'heart':
        g = G(7, 6)
        for y in range(6):
            for x in range(7):
                if herz_sdf((x - 3) / 3.2, (y - 2.6) / 3.0) <= 0: g.set(x, y, 'p')
        g.set(1, 1, 'q'); g.set(2, 1, 'q'); return g
    if form == 'moon':
        g = G(7, 7); g.disc(3, 3, 3.2, 'p')
        for y in range(7):
            for x in range(7):
                if math.hypot(x - 4.6, y - 2.3) < 2.6: g.set(x, y, '.')
        g.set(1, 2, 'q'); return g
    if form == 'skull':
        return from_rows(['.ppppp.', 'ppppppp', 'pkkpkkp', 'pkkpkkp', 'ppp.ppp', '.ppppp.', '.p.p.p.'])
    if form == 'opal':
        g = G(5, 7); g.disc(2, 3, 2.4, 'p'); g.set(1, 2, 'q'); g.set(2, 4, 'P'); g.outline('P'); return g
    if form == 'emerald':
        g = G(7, 8); g.rect(1, 0, 5, 7, 'P'); g.rect(2, 1, 4, 6, 'p'); g.set(2, 1, 'q'); g.set(3, 2, 'q'); return g
    if form == 'blackhole':
        g = G(11, 9)
        for y in range(9):
            for x in range(11):
                d = math.hypot(x - 5, y - 4)
                if d <= 3.3: g.set(x, y, 'P' if d > 2.6 else 'k')
        for x in range(11):
            g.set(x, 4, 'p' if 2 <= x <= 8 else 'q')
        g.set(5, 4, 'q'); g.set(3, 1, 'P'); g.set(7, 1, 'P'); return g
    if form == 'star':
        g = G(9, 9)
        for y in range(9):
            for x in range(9):
                if stern_sdf((x - 4) / 4.4, (y - 4.3) / 4.4) <= 0: g.set(x, y, 'p')
        g.set(4, 2, 'q'); g.set(3, 4, 'q'); g.outline('P'); return g
    if form == 'sun':
        g = G(11, 11); g.disc(5, 5, 2.8, 'p'); g.disc(5, 5, 1.6, 'q')
        for k in range(8):
            w = k * math.pi / 4
            for r in (3.8, 4.6):
                g.set(5 + math.cos(w) * r, 5 + math.sin(w) * r, 'P')
        return g
    if form == 'bolt':
        return from_rows(['...pp', '..pp.', '.pp..', 'ppppp', '..pp.', '.pp..', 'pp...', 'p....'])
    return G(1, 1)

def _ketten_bild(form, c, C, pend):
    """Menue-Bild: Kette als U von vorne, Anhaenger unten in der Mitte."""
    g = G(20, 18)
    for i in range(80):
        t = i / 79 * 2 - 1
        x = 9.5 + t * 8.5
        y = 1 + 8.5 * (1 - t * t)
        ch = 'C' if (form in ('beads', 'ball') and i % 6 < 3) or (form not in ('beads', 'ball') and i % 8 < 2) else 'c'
        g.set(x, y, ch)
        if form in ('cuban', 'beads'): g.set(x, y + 1, ch)
    if pend is not None:
        ox = 10 - pend.w // 2
        for y in range(pend.h):
            for x in range(pend.w):
                if pend.c[y][x] != '.': g.set(ox + x, 10 + y, pend.c[y][x])
    return g

def ketten():
    def kette(id_, name, form, c, C, drop=4.6, pend=None, pc=None, glow=''):
        cols = {'c': c, 'C': C}
        if pc: cols.update(pc)
        p_g = _anhaenger(pend) if pend else None
        teile = [part(_ketten_bild(form, c, C, p_g), {}, px=0.5, depth=0.5, glow=glow, anim='icon')]
        if p_g is not None:
            teile.append(part(p_g, {}, at=(0, -drop - 0.4, -2.45), px=0.32, depth=0.7, glow=glow, anim='pendant'))
        design('neck', id_, name, colors=cols, chain=form, drop=drop, chainglow=1 if 'C' in glow else 0, parts=teile, glow=glow)
    gold = {'p': 'E8B730', 'P': 'FFE27A', 'q': 'A8770F'}
    silber = {'p': 'C9CED6', 'P': 'F2F5F8', 'q': '7D8590'}
    kette('gold_chain', 'Gold Chain', 'link', 'D9A82A', 'FFE17A', pend='coin', pc=gold)
    kette('silver_chain', 'Silver Chain', 'link', 'B7BEC7', 'F4F7FA', pend='tag', pc=silber)
    kette('iced_cuban', 'Iced Cuban', 'cuban', 'DDE6F0', 'FFFFFF', drop=3.8, glow='C')
    kette('diamond_pendant', 'Diamond Pendant', 'link', 'D9A82A', 'FFE17A', drop=5.2, pend='diamond',
          pc={'p': '67E8F9', 'P': '22B8D8', 'q': 'FFFFFF'}, glow='pq')
    kette('vortex_pendant', 'Vortex Pendant', 'rope', '1E1B2B', '3A3550', drop=5.0, pend='crystal',
          pc={'p': '8B5CF6', 'P': 'D8B4FE', 'q': 'C4B5FD'}, glow='pPq')
    kette('heart_locket', 'Heart Locket', 'ball', 'C9CED6', 'FFFFFF', pend='heart', pc={'p': 'E11D48', 'q': 'FDA4AF'})
    kette('pearl_necklace', 'Pearl Necklace', 'beads', 'F4EFE6', 'FFFFFF', drop=3.6)
    kette('dog_tags', 'Dog Tags', 'ball', '9AA3AE', 'E5E9EE', drop=5.4, pend='tag', pc=silber)
    kette('moon_charm', 'Moon Charm', 'link', 'B7BEC7', 'F4F7FA', drop=4.8, pend='moon', pc={'p': 'FDE68A', 'q': 'FFFBEB'}, glow='pq')
    kette('skull_chain', 'Skull Chain', 'cuban', '23222A', '4A4855', drop=4.4, pend='skull', pc={'p': 'EDE6D6', 'k': '111014'})
    kette('fire_opal', 'Fire Opal', 'beads', 'D9A82A', 'FFE17A', drop=4.8, pend='opal', pc={'p': 'FF7A1A', 'P': 'B33A0A', 'q': 'FFE08A'}, glow='pq')
    kette('emerald_amulet', 'Emerald Amulet', 'rope', '6B4A22', '9C7240', drop=5.0, pend='emerald',
          pc={'p': '10B981', 'P': 'D9A82A', 'q': 'A7F3D0'}, glow='pq')

# ======================================================================
def schreiben():
    p = os.path.join(RES, 'cosmetics', 'designs.txt')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    L = ["# Erzeugt von tools/cosmetics/art.py -- nicht von Hand aendern.", ""]
    for d in OUT:
        L.append(f"== {d['kat']} {d['id']}")
        L.append(f"name: {d['name']}")
        if d.get('colors'): L.append('colors: ' + ' '.join(f"{k}={v}" for k, v in d['colors'].items()))
        if d.get('glow'): L.append(f"glow: {d['glow']}")
        if d.get('arms'): L.append(f"arms: {d['arms']}")
        for k in ('motion', 'count', 'px', 'spin', 'size', 'legacy'):
            if k in d and d['kat'] == 'aura' and d[k] != '': L.append(f"{k}: {d[k]}")
        if d.get('text'): L.append(f"text: {d['text']}")
        for k in ('chain', 'drop', 'chainglow'):
            if k in d and d['kat'] == 'neck' and d[k] != 0: L.append(f"{k}: {d[k]}")
        for k in ('frames', 'fps'):
            if k in d and d['kat'] == 'shield': L.append(f"{k}: {d[k]}")
        if d.get('ring'):
            r = d['ring']
            extra = (f" flow={r['flow']}" if r.get('flow') else '') + (f" fx={r['fx']}" if r.get('fx') else '')
            L.append(f"ring: y0={r['y0']} px={r['px']} knot={r['knot']} tails={r['tails']}{extra}")
            L.extend('  ' + row for row in r['rows'])
            L.append('end')
        for pt in d.get('parts', []):
            if 'vox' in pt:
                L.append('vox: at=%s,%s,%s px=%s rot=%s,%s,%s%s%s' % (*pt['at'], pt['px'], *pt['rot'],
                         (' glow=' + pt['glow']) if pt['glow'] else '', (' anim=' + pt['anim']) if pt['anim'] else ''))
                if pt['colors']: L.append('colors: ' + ' '.join(f"{k}={v}" for k, v in pt['colors'].items()))
                for i, schicht in enumerate(pt['vox']):
                    if i: L.append('  --')
                    L.extend('  ' + row for row in schicht)
                L.append('end')
                continue
            L.append('part: at=%s,%s,%s px=%s depth=%s rot=%s,%s,%s%s%s' % (*pt['at'], pt['px'], pt['depth'], *pt['rot'],
                     (' glow=' + pt['glow']) if pt['glow'] else '', (' anim=' + pt['anim']) if pt['anim'] else ''))
            if pt['colors']: L.append('colors: ' + ' '.join(f"{k}={v}" for k, v in pt['colors'].items()))
            L.extend('  ' + row for row in pt['art'])
            L.append('end')
        for sp in d.get('sprites', []):
            L.append('sprite:')
            L.extend('  ' + row for row in sp)
            L.append('end')
        L.append('')
    open(p, 'w').write('\n'.join(L))
    return p

def vorschau(ordner):
    from PIL import Image, ImageDraw
    os.makedirs(ordner, exist_ok=True)
    def col(h): return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    for kat in ('face', 'bandana', 'back', 'aura', 'neck'):
        ds = [d for d in OUT if d['kat'] == kat]
        tiles = []
        for d in ds:
            S = 8
            img = Image.new('RGB', (260, 260), (40, 38, 52))
            dr = ImageDraw.Draw(img)
            cols = dict(d.get('colors') or {})
            arts = []
            if d.get('ring'): arts.append(d['ring']['rows'])
            for pt in d.get('parts', []): arts.append(pt['art'])
            for sp in d.get('sprites', []): arts.append(sp)
            y0 = 6
            for art in arts:
                w = max(len(r) for r in art); s = max(2, min(S, 240 // max(w, 1), 200 // max(len(art), 1)))
                x0 = (260 - w * s) // 2
                for y, r in enumerate(art):
                    for x, ch in enumerate(r):
                        if ch != '.' and ch in cols: dr.rectangle([x0 + x * s, y0 + y * s, x0 + x * s + s - 1, y0 + y * s + s - 1], fill=col(cols[ch]))
                y0 += len(art) * s + 6
                if y0 > 230: break
            dr.text((6, 246), d['name'], fill=(255, 255, 255))
            tiles.append(img)
        n = len(tiles); cols_n = 5; rows_n = (n + cols_n - 1) // cols_n
        sheet = Image.new('RGB', (260 * cols_n, 260 * rows_n), (20, 18, 28))
        for i, t in enumerate(tiles): sheet.paste(t, ((i % cols_n) * 260, (i // cols_n) * 260))
        sheet.save(os.path.join(ordner, kat + '.png'))
    # Schilde
    sd = os.path.join(RES, 'textures', 'cosmetics', 'shield')
    fs = sorted(f for f in os.listdir(sd) if f.endswith('.png'))
    sheet = Image.new('RGB', (len(fs) * 110, 200), (20, 18, 28))
    for i, f in enumerate(fs):
        im = Image.open(os.path.join(sd, f)).crop((2, 2, 26, 46)).resize((96, 176), Image.NEAREST)
        sheet.paste(im, (i * 110 + 7, 12), im)
    sheet.save(os.path.join(ordner, 'shield.png'))

# ======================================================================
# Seit 4.77: mehr Cosmetics, feiner gezeichnet (Viertelpixel bei Brillen und Masken)
# ======================================================================
def mehr_477():
    F = 0.25
    def rund(x, y):
        for cx in (9.0, 27.0):
            d = math.hypot(x - cx, y - 6.0)
            if 4.4 <= d <= 5.4: return 'G' if y < 4.2 else 'g'
            if d < 4.4 and -1.2 < (x - cx) + (y - 6.0) * 0.8 < 0.2 and y < 6.0: return 'w'
        if 14.0 <= x <= 22.0 and 3.0 <= y <= 3.9 + 0.6 * math.sin((x - 14) / 8 * math.pi): return 'g'
        return '.'
    design('face', 'round_glasses', 'Round Glasses', arms='g', colors={'g': 'C8962E', 'G': 'FFE08A', 'w': 'FFFFFF'},
           parts=[part(raster(36, 12, rund), {}, at=(0, 3.9, -4.85), px=F, glow='w')])

    def maske(x, y):
        u, v = (x - 18) / 18.0, (y - 6) / 6.0
        rand_ = abs(u) ** 1.6 + (v * (1.15 if v > 0 else 0.9)) ** 2
        if rand_ > 1.0 or (abs(u) < 0.12 and v > 0.25): return '.'
        for cx in (9.0, 27.0):
            e = ((x - cx) / 4.6) ** 2 + ((y - 6.2) / 2.6) ** 2
            if e < 1.0: return '.'
            if e < 1.45: return 'G'
        if rand_ > 0.82: return 'g'
        return 'p' if (x + y) % 6 < 3 or y > 7 else 'P'
    design('face', 'masquerade', 'Masquerade', colors={'p': '4C1D95', 'P': '6D28D9', 'g': 'C8962E', 'G': 'FFE08A'},
           parts=[part(raster(36, 12, maske), {}, at=(0, 3.9, -4.85), px=F, glow='G')])

    def auge(x, y):
        e = ((x - 9) / 6.6) ** 2 + ((y - 6) / 4.4) ** 2
        if e > 1.0:
            if 15 <= x <= 36 and 4.6 <= y <= 5.6: return 'f'
            return '.'
        if e > 0.72: return 'F'
        ring = math.hypot(x - 9, y - 6)
        if ring < 1.3: return 'W'
        if ring < 2.6: return 'R'
        return 'r' if int(y * 2) % 2 == 0 else 'd'
    design('face', 'cyber_eye', 'Cyber Eye', colors={'F': '2A2F3A', 'f': '3A404D', 'R': 'FF2D55', 'r': '7A0F24', 'd': '4A0A17', 'W': 'FFE4EA'},
           parts=[part(raster(36, 12, auge), {}, at=(0, 3.9, -4.85), px=F, glow='RW')])

    def grill(x, y):
        if not (2.0 <= x <= 22.0): return '.'
        zahn = (x - 2.0) % 4.0
        if zahn < 0.7: return 'q'
        if y < 1.0 or y > 4.0: return '.'
        if int(x) % 8 == 5 and 1.6 < y < 3.0: return 'D'
        return 'G' if y < 2.0 else 'g'
    design('face', 'gold_grill', 'Gold Grill', colors={'g': 'D9A82A', 'G': 'FFE17A', 'q': '8A6512', 'D': 'E0F7FF'},
           parts=[part(raster(24, 5, grill), {}, at=(0, 1.35, -4.85), px=F, glow='D')])

    def kette(id_, name, form, c, C, drop=4.6, pend=None, pc=None, glow=''):
        cols = {'c': c, 'C': C}
        if pc: cols.update(pc)
        p_g = _anhaenger(pend) if pend else None
        teile = [part(_ketten_bild(form, c, C, p_g), {}, px=0.5, depth=0.5, glow=glow, anim='icon')]
        if p_g is not None:
            teile.append(part(p_g, {}, at=(0, -drop - 0.4, -2.45), px=0.3, depth=0.7, glow=glow, anim='pendant'))
        design('neck', id_, name, colors=cols, chain=form, drop=drop, chainglow=1 if 'C' in glow else 0, parts=teile, glow=glow)
    kette('event_horizon', 'Event Horizon', 'rope', '1E1B2B', 'C8962E', drop=5.0, pend='blackhole',
          pc={'k': '050505', 'p': 'FFC94D', 'P': 'C27C0E', 'q': 'FFF1C9'}, glow='pq')
    kette('star_charm', 'Star Charm', 'link', 'B7BEC7', 'F4F7FA', drop=4.8, pend='star', pc={'p': 'FDE047', 'P': 'CA8A04', 'q': 'FFFBEB'}, glow='pq')
    kette('sun_medallion', 'Sun Medallion', 'cuban', 'D9A82A', 'FFE17A', drop=4.4, pend='sun', pc={'p': 'F59E0B', 'P': 'FDE68A', 'q': 'FFF7D6'}, glow='q')
    kette('thunder_bolt', 'Thunder Bolt', 'ball', '9AA3AE', 'E5E9EE', drop=5.0, pend='bolt', pc={'p': 'FDE047'}, glow='p')

    def wing(id_, name, art, colors, glow='', kind='feather', px=0.42):
        design('back', id_, name, colors=colors,
               parts=[part(art, {}, at=(1.4, -2.4, hinten(1, px, extra=0.6)), px=px, depth=1.0, glow=glow, anim='wing:' + kind)])
    wing('golden_wings', 'Golden Wings', _federn('y', 'Y', 'o', 'O', 'k', glanz='w'),
         {'y': 'FFD54A', 'Y': 'FFE89A', 'o': 'E0A526', 'O': 'B7791F', 'k': '5A3A0A', 'w': 'FFFBEA'}, glow='w')
    wing('neon_wings', 'Neon Wings', _federn('n', 'm', 'M', 'c', 'k', glanz='c', energie=True),
         {'n': '1A0B2E', 'm': 'C026D3', 'M': '86198F', 'c': '22D3EE', 'k': '0A0414', 'E': 'F0ABFC'}, glow='cmE')

    def gold_band(x, y):
        if y in (0, 3): return 'G'
        k = (x + (y == 2) * 2) % 8
        return 'g' if k < 4 else 'k'
    bandana('gold_lux', 'Gold Lux', {'k': '0E0C08', 'g': 'C8962E', 'G': 'FFE08A'}, band(gold_band), 'kg', glow='G')
    def herz_band(x, y):
        k = x % 8
        herz = {(1, 1), (2, 1), (4, 1), (5, 1), (1, 2), (2, 2), (3, 2), (4, 2), (5, 2), (2, 3), (3, 3), (4, 3)}
        if (k, y) in herz or (k == 3 and y == 1 and False): return 'p'
        return 'w' if (x // 8 + y) % 2 == 0 else 'W'
    bandana('hearts', 'Hearts', {'w': 'FFE4EF', 'W': 'FFD1E3', 'p': 'E11D74'}, band(herz_band), 'wp')

# ======================================================================
# Seit 4.79: grosse Auren (weiche Leuchteffekte, gezeichnet von GrossAura.java).
# Hier nur Name, Art (motion: big_...) und ein Bildchen fuers Menue.
# ======================================================================
def grosse_auren():
    def bild(fn, n=16):
        return [''.join(fn(x + 0.5 - n / 2, y + 0.5 - n / 2) for x in range(n)) for y in range(n)]
    def arkan(x, y):
        r = math.hypot(x, y)
        if 6.6 <= r <= 7.7: return 'v'
        if abs(abs(x) + abs(y) - 5.2) < 0.6 or (max(abs(x), abs(y)) > 3.2 and max(abs(x), abs(y)) < 4.0 and r < 5.6): return 'c'
        if r < 1.4: return 'w'
        return '.'
    def sonne(x, y):
        r = math.hypot(x, y); w = math.atan2(y, x)
        if r < 3.2: return 'w' if r < 1.8 else 'y'
        if r < 7.6 and abs(math.sin(w * 6)) > 0.82 and r > 4.2: return 'o'
        return '.'
    def wirbel(x, y):
        r = math.hypot(x, y); w = math.atan2(y, x)
        if r > 7.6 or r < 0.8: return '.'
        k = (w + r * 0.62) % (math.pi * 2 / 3)
        if k < 0.75: return 'm' if r > 4 else 'c'
        return '.'
    def frost(x, y):
        r = math.hypot(x, y); w = math.atan2(y, x)
        a = (w % (math.pi / 3)) - math.pi / 6
        if r < 7.2 and abs(r * math.sin(a)) < 0.6: return 'w'
        if 2.5 < r < 6 and abs(abs(r * math.sin(a)) - (r - 2.5) * 0.45) < 0.45 and abs(a) < 0.6: return 'b'
        return '.'
    def inferno(x, y):
        r = math.hypot(x, y * 1.6)
        if 5.2 < r < 7.4 and y > -1: return 'o'
        if y <= 2 and abs(x) > 4 and abs(x) < 7.6:
            k = (abs(x) * 1.7 + y * 0.4) % 2.0
            if k < 1.1 and y > -6 + (abs(abs(x) - 5.8)) * 2: return 'y' if y > -2 else 'o'
        return '.'
    def polar(x, y):
        for i, ch in enumerate('gcv'):
            yc = -4 + i * 3.5 + 1.6 * math.sin(x * 0.55 + i)
            if abs(y - yc) < 0.9: return ch
        return '.'
    def gross(id_, name, motion, fn, colors):
        design('aura', id_, name, motion=motion, count=1, sprites=[bild(fn)], colors=colors, px=0.7, glow=''.join(colors.keys()), spin=1.0, size=1.0, legacy='')
    gross('arcane_circle', 'Arcane Circle', 'big_arcane', arkan, {'v': '8B5CF6', 'c': '67E8F9', 'w': 'FFFFFF'})
    gross('solar_crown', 'Solar Crown', 'big_solar', sonne, {'w': 'FFF7D6', 'y': 'FBBF24', 'o': 'F59E0B'})
    gross('void_vortex', 'Void Vortex', 'big_vortex', wirbel, {'m': 'E879F9', 'c': '67E8F9'})
    gross('frost_nova', 'Frost Nova', 'big_frost', frost, {'w': 'F0F9FF', 'b': '7DD3FC'})
    gross('inferno_ring', 'Inferno Ring', 'big_inferno', inferno, {'o': 'F97316', 'y': 'FDE047'})
    gross('aurora_veil', 'Aurora Veil', 'big_aurora', polar, {'g': '34D399', 'c': '22D3EE', 'v': 'A78BFA'})

if __name__ == '__main__':
    face(); face_neu(); bandanas(); bandanas_neu(); back(); back_neu(); fluegel_neu(); ketten(); auras(); auras_neu(); hats(); shields(); mehr_477(); grosse_auren(); pets()
    print(schreiben(), len(OUT))
    if len(sys.argv) > 1: vorschau(sys.argv[1])

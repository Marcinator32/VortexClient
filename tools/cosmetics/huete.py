"""Huete & Co. (seit 4.32): Voxel-Modelle statt glatter Formen.

Jeder Hut besteht aus einem oder mehreren Voxel-Teilen. Ein Voxel ist 0.5
Minecraft-Pixel gross (16 Voxel ueber den Kopf), der Client zeichnet sie mit
Umgebungsverdeckung (dunklere Ecken) -- dadurch wirken sie plastisch.

Koordinaten wie bei den anderen Kopf-Cosmetics (Kopf-Raum in Pixeln):
  x links/rechts (von vorne gesehen), y nach oben (Kopf 0..8), z: Gesicht bei -4.
Ein Teil wird ueber eine Funktion f(x, y, z) -> Zeichen beschrieben, die an der
Mitte jedes Voxels im Kopf-Raum ausgewertet wird ('.' oder None = leer).

Alle Designs sind eigene Entwuerfe.
"""
import math

VS = 0.5   # Voxelgroesse in Pixeln


def _h(x, y, z, seed=0):
    n = (int(x * 73.1) * 374761393 + int(y * 31.7) * 668265263 + int(z * 57.3) * 2147483647 + seed * 97) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def teil(grenzen, fn, colors, glow='', anim='', vs=VS, mitte=None):
    """Voxel-Teil aus einer Funktion im Kopf-Raum. grenzen = (x0, x1, y0, y1, z0, z1) in Pixeln.
    mitte: Drehpunkt/Position des Teils (Standard: Mitte der Grenzen); die Funktion
    wird trotzdem im Kopf-Raum ausgewertet."""
    x0, x1, y0, y1, z0, z1 = grenzen
    w, h, d = round((x1 - x0) / vs), round((y1 - y0) / vs), round((z1 - z0) / vs)
    schichten = []
    for iy in range(h):
        y = y0 + (iy + 0.5) * vs
        schicht = []
        for iz in range(d):
            z = z0 + (iz + 0.5) * vs
            reihe = []
            for ix in range(w):
                x = x0 + (ix + 0.5) * vs
                ch = fn(x, y, z)
                reihe.append(ch if ch else '.')
            schicht.append(''.join(reihe))
        schichten.append(schicht)
    # leere Raender abschneiden waere moeglich; Mitte muss dann mitwandern -- einfach behalten
    at = ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
    return dict(vox=schichten, colors=colors, at=tuple(round(v, 3) for v in at), px=vs, rot=(0, 0, 0), glow=glow, anim=anim)


def lokal(groesse, fn, colors, at, glow='', anim='', vs=VS, rot=(0, 0, 0)):
    """Voxel-Teil, beschrieben um den eigenen Ursprung (fuer bewegte Teile: kreisen, drehen).
    groesse = (bx, by, bz) halbe Kantenlaengen in Pixeln."""
    bx, by, bz = groesse
    t = teil((-bx, bx, -by, by, -bz, bz), fn, colors, glow, anim, vs)
    t['at'] = tuple(round(v, 3) for v in at)
    t['rot'] = rot
    return t


# ----------------------------------------------------------------------
# Formen
# ----------------------------------------------------------------------

def kugel(x, y, z, cx, cy, cz, r):
    return (x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2 <= r * r


def strecke_abstand(p, a, b):
    ax, ay, az = a; bx, by, bz = b; px, py, pz = p
    vx, vy, vz = bx - ax, by - ay, bz - az
    l2 = vx * vx + vy * vy + vz * vz
    t = 0 if l2 == 0 else max(0, min(1, ((px - ax) * vx + (py - ay) * vy + (pz - az) * vz) / l2))
    qx, qy, qz = ax + t * vx, ay + t * vy, az + t * vz
    return math.sqrt((px - qx) ** 2 + (py - qy) ** 2 + (pz - qz) ** 2), t


def roehre(p, pfad, radien):
    """Abstand zu einem Rohr entlang pfad (Punkte) mit Radien; liefert (drin, u 0..1)."""
    best, bu = 1e9, 0
    n = len(pfad) - 1
    for i in range(n):
        d, t = strecke_abstand(p, pfad[i], pfad[i + 1])
        r = radien[i] + (radien[i + 1] - radien[i]) * t
        if d - r < best:
            best, bu = d - r, (i + t) / n
    return best <= 0, bu


# ----------------------------------------------------------------------
# Die Huete
# ----------------------------------------------------------------------

def huete(design):
    TOP = 8.0

    # --- Halo: goldener Lichtring, schwebt und dreht sich
    def halo(x, y, z):
        r = math.hypot(x, z)
        d = math.hypot(r - 4.7, y)
        if d > 0.62: return None
        return 'w' if y > 0.15 and r < 4.9 else 'y'
    sterne = []
    for i in range(3):
        sterne.append(lokal((0.6, 0.6, 0.6), lambda x, y, z: 'W' if abs(x) + abs(y) + abs(z) < 0.75 else None,
                            {'W': 'FFFFFF'}, (0, 11.6, 0), glow='W', anim=f'orbit:5.6,0.06,{i * 2.09:.2f}+twinkle:{i / 3:.2f}', vs=0.25))
    design('hat', 'halo', 'Halo', text='A floating ring of light',
           parts=[lokal((5.5, 0.75, 5.5), halo, {'y': 'FFC93C', 'w': 'FFF4C8'}, (0, 11.4, 0), glow='yw', anim='hover:0.4,0.07+spinY:1.2', rot=(10, 0, 0))] + sterne)

    # --- Royal Crown: Goldreif mit Zacken, Perlen, Samt und leuchtenden Edelsteinen
    def krone(x, y, z):
        r = math.hypot(x, z); a = math.atan2(z, x)
        # Samtkappe innen
        if r < 4.5 and y > TOP and ((x / 4.4) ** 2 + ((y - TOP + 0.2) / 2.4) ** 2 + (z / 4.4) ** 2) <= 1:
            return 'R' if y > TOP + 1.4 else 'r'
        # Kreuz oben
        if abs(x) < 0.4 and abs(z) < 0.4 and TOP + 2.0 < y < TOP + 3.9: return 'G'
        if abs(x) < 1.1 and abs(z) < 0.4 and TOP + 3.0 < y < TOP + 3.6: return 'G'
        if 4.55 <= r <= 5.35:
            if TOP - 0.5 <= y <= TOP + 1.6:
                # Edelsteine im Band
                for k, (ga, ch) in enumerate([(-math.pi / 2, 'e'), (0, 's'), (math.pi, 's'), (math.pi / 2, 'm')]):
                    gx, gz = 5.3 * math.cos(ga), 5.3 * math.sin(ga)
                    if kugel(x, y, z, gx, TOP + 0.55, gz, 0.75): return ch
                if abs(y - (TOP + 1.45)) < 0.25 or abs(y - (TOP - 0.35)) < 0.25: return 'G'
                rille = abs(math.sin(a * 8)) < 0.12
                return 'd' if rille else 'g'
            # Zacken
            k = round((a + math.pi) / (math.pi / 4)) % 8
            ak = -math.pi + k * math.pi / 4
            da = abs((a - ak + math.pi) % (2 * math.pi) - math.pi) * r
            hoch = 3.0 if k % 2 == 0 else 2.0
            if TOP + 1.6 < y <= TOP + 1.6 + hoch and da < 1.25 * (1 - (y - TOP - 1.6) / hoch):
                return 'G' if da < 0.35 else 'g'
        # Perlen auf den Zacken
        for k in range(8):
            ak = -math.pi + k * math.pi / 4
            hoch = 3.0 if k % 2 == 0 else 2.0
            if kugel(x, y, z, 4.95 * math.cos(ak), TOP + 1.85 + hoch, 4.95 * math.sin(ak), 0.62): return 'p'
        return None
    funkeln = [lokal((0.5, 0.5, 0.5), lambda x, y, z: 'W' if abs(x) + abs(y) + abs(z) < 0.6 else None, {'W': 'FFFFFF'},
                     (px_, TOP + 1.0, pz_), glow='W', anim=f'twinkle:{ph}', vs=0.25)
               for px_, pz_, ph in ((0.8, -5.6, 0.0), (-5.6, 0.6, 0.35), (5.6, -0.6, 0.7))]
    design('hat', 'crown', 'Royal Crown', text='Gold, pearls and glowing gems',
           parts=[teil((-6, 6, TOP - 0.6, TOP + 5.4, -6, 6), krone,
                       {'g': 'E2A93A', 'G': 'FFE08A', 'd': 'A8740F', 'p': 'F6F1E7', 'r': '8E1B2E', 'R': 'B8263E',
                        'e': 'FF2D55', 's': '3D7BFF', 'm': '2BD67B'}, glow='esm')] + funkeln)

    # --- Demon Horns: gebogen, Rillen, gluehende Spitzen
    def hornpfad(s):
        pfad, rad = [], []
        for i in range(13):
            u = i / 12
            pfad.append((s * (2.4 + 2.8 * u - 0.6 * u * u), TOP - 0.4 + 5.6 * u - 1.4 * u * u, -0.6 + 0.6 * u + 3.2 * u * u))
            rad.append(1.35 * (1 - u) + 0.12)
        return pfad, rad
    pfade = [hornpfad(-1), hornpfad(1)]
    def hoerner(x, y, z):
        for pfad, rad in pfade:
            drin, u = roehre((x, y, z), pfad, rad)
            if drin:
                if u > 0.82: return 'y' if u > 0.92 else 'o'
                if u > 0.68: return 'r'
                return 'k' if int(u * 18) % 3 == 0 else 'm'
        return None
    design('hat', 'horns', 'Demon Horns', text='Curved horns with glowing tips',
           parts=[teil((-7, 7, TOP - 1, TOP + 5, -2.5, 4.5), hoerner,
                       {'k': '1E0A10', 'm': '4A1220', 'r': 'B3261E', 'o': 'FF7A1E', 'y': 'FFD84D'}, glow='oy')])

    # --- Orbit: drei leuchtende Kugeln mit Schweif kreisen um den Kopf
    teile = []
    for i, (f, h) in enumerate((('5EEAFF', 'D8FBFF'), ('FF5EDB', 'FFD3F6'), ('FFD45E', 'FFF4D0'))):
        ph = i * 2.094
        for k, (r, nach) in enumerate(((1.1, 0), (0.75, 0.32), (0.5, 0.6), (0.3, 0.85))):
            def orb(x, y, z, r=r):
                d = math.sqrt(x * x + y * y + z * z)
                if d > r: return None
                return 'h' if (x + y) > r * 0.35 else 'c'
            teile.append(lokal((1.25, 1.25, 1.25), orb, {'c': f, 'h': h}, (0, 4.5 + 2.4 * math.sin(ph), 0), glow='ch',
                               anim=f'orbit:7.2,0.11,{ph - nach:.2f}+hover:1.0,0.05,{ph:.2f}', vs=0.25))
    design('hat', 'orbit', 'Orbit', text='Three glowing orbs circling you', parts=teile)

    # --- Crystal Crown: schwebende, sich drehende Kristalle
    def kristall(hoch, breit):
        def f(x, y, z):
            t = 1 - abs(y) / hoch
            if t <= 0: return None
            hexr = max(abs(x), abs(x) * 0.5 + abs(z) * 0.866)
            if hexr > breit * t: return None
            if y > 0 and hexr > breit * t - 0.35: return 'C'
            return 'c' if x > 0 else 'v'
        return f
    teile = []
    for i in range(6):
        ph = i * math.pi / 3
        farben = {'c': '8FF7FF', 'C': 'E8FFFF', 'v': '5BC8E8'} if i % 2 == 0 else {'c': 'C7A6FF', 'C': 'F3EAFF', 'v': '9A6BEA'}
        teile.append(lokal((1.2, 2.0, 1.2), kristall(1.9, 0.95), farben, (0, TOP + 2.4, 0), glow='cCv',
                           anim=f'orbit:5.0,0.025,{ph:.2f}+hover:0.6,0.08,{ph:.2f}+spinY:2.5', vs=0.25))
    teile.append(lokal((1.7, 2.8, 1.7), kristall(2.7, 1.4), {'c': 'B794FF', 'C': 'F5EEFF', 'v': '8A5CF6'}, (0, TOP + 4.6, 0),
                       glow='cCv', anim='hover:0.5,0.06+spinY:-1.5', vs=0.25))
    design('hat', 'crystals', 'Crystal Crown', text='Floating crystals of light', parts=teile)

    # --- Neon Headphones
    def kopfhoerer(x, y, z):
        # Buegel: Halbkreis ueber dem Kopf
        r = math.hypot(x, y - 3.2)
        if y > 3.2 and 5.0 <= r <= 5.9 and abs(z) <= 0.75:
            return 'p' if r < 5.35 and abs(x) < 2.5 else 'k'
        # Muscheln seitlich
        for s, ring in ((-1, 'c'), (1, 'm')):
            cx = s * 5.25
            dy, dz = y - 3.4, z
            rr = math.hypot(dy, dz)
            if abs(x - cx) <= 0.95 and rr <= 2.55:
                aussen = s * (x - cx) > 0.45
                if aussen and 1.75 <= rr <= 2.3: return ring
                if aussen and rr < 1.2: return 'g'
                return 'k' if rr > 2.3 or not aussen else 'K'
            if abs(x - cx + s * 1.15) <= 0.25 and rr <= 2.2: return 'q'   # Polster
        return None
    design('hat', 'headphones', 'Neon Headphones', text='Glowing headphones with soft cushions',
           parts=[teil((-6.6, 6.6, 0.5, 9.5, -2.8, 2.8), kopfhoerer,
                       {'k': '22222C', 'K': '34343F', 'p': '3A3A48', 'g': '50505E', 'q': '17171D', 'c': '38E1FF', 'm': 'FF4FD8'},
                       glow='cm')])

    # --- Fox Ears: zucken ab und zu
    def ohr(farbe_aussen, farbe_innen, hoehe=4.2, breite=2.1):
        def f(x, y, z):
            t = (y + hoehe / 2) / hoehe
            if not (0 <= t <= 1): return None
            b = breite * (1 - t)
            if abs(x) > b: return None
            if z < -0.25 and abs(x) < b - 0.55 and t < 0.75: return 'i'
            if t > 0.72: return 'k'
            return 'o' if x < b - 0.5 else 'O'
        return f
    ohren = []
    for s, ph in ((-1, 0), (1, 37)):
        ohren.append(lokal((2.2, 2.2, 0.75), ohr(None, None), {'o': 'E8803A', 'O': 'C96A2A', 'i': 'FFF1E4', 'k': '3A2216'},
                           (s * 2.5, TOP + 1.8, 0.4), anim=f'twitch:{-s * 16},{ph}', rot=(0, 0, -s * 14)))
    design('hat', 'fox_ears', 'Fox Ears', text='Fluffy ears that twitch', parts=ohren)

    # --- Flame Crown: Flammenzungen flackern rund um den Kopf
    def flamme(x, y, z):
        t = (y + 1.9) / 3.8
        if not (0 <= t <= 1): return None
        r = math.hypot(x, z)
        breite = 1.0 * math.sin(math.pi * min(1, t * 1.25)) ** 0.8 * (1 - t) + 0.05
        if r > breite: return None
        if r < breite * 0.45 and t < 0.6: return 'w'
        if t > 0.7: return 'y'
        return 'o' if t > 0.3 else 'r'
    teile = [teil((-5.4, 5.4, TOP - 0.3, TOP + 0.7, -5.4, 5.4),
                  lambda x, y, z: ('R' if (math.hypot(x, z) - 4.75) ** 2 + (y - TOP - 0.2) ** 2 <= 0.36 else None),
                  {'R': 'B3261E'}, glow='R')]
    for i in range(10):
        a = i * 2 * math.pi / 10
        teile.append(lokal((1.1, 1.9, 1.1), flamme, {'r': 'E8401C', 'o': 'FF8A2B', 'y': 'FFD24A', 'w': 'FFF6C8'},
                           (4.7 * math.cos(a), TOP + 2.0, 4.7 * math.sin(a)), glow='royw', anim=f'flicker:{i * 1.7:.1f}', vs=0.25))
    design('hat', 'flame', 'Flame Crown', text='A ring of living fire', parts=teile)

    # --- Witch Hat: breite Krempe, geknickte Spitze, leuchtendes Band
    def hexenhut(x, y, z):
        # Krempe
        r = math.hypot(x, z)
        if TOP - 0.7 <= y <= TOP - 0.15 and r <= 8.0 - 0.25 * math.sin(math.atan2(z, x) * 5):
            return 'd' if r > 7.4 else 'p'
        if y < TOP - 0.2: return None
        h = y - TOP + 0.7
        knick = max(0, h - 5.0) / 3.5
        cz = 2.6 * knick * knick
        cx = 0.3 * knick
        rad = 4.8 * (1 - h / 9.5)
        if rad <= 0: return None
        rr = math.hypot(x - cx, z - cz)
        if rr > rad: return None
        if 0.6 <= h <= 1.4:
            if abs(x) < 0.9 and z < 0 and 0.75 <= h <= 1.25: return 'b' if abs(x) > 0.45 or abs(h - 1.0) > 0.2 else 'g'
            return 'G'
        return 'P' if (x - cx) < -rad * 0.3 else 'p'
    sterne = [lokal((0.5, 0.5, 0.5), lambda x, y, z: 'W' if abs(x) + abs(y) + abs(z) < 0.62 else None, {'W': 'FFF3A0'},
                    (0, TOP + 7.5, 2.4), glow='W', anim=f'orbit:2.2,0.07,{i * 2.09:.2f}+twinkle:{i / 3:.2f}', vs=0.25) for i in range(3)]
    design('hat', 'witch_hat', 'Witch Hat', text='Bent tip, glowing band and stars',
           parts=[teil((-8.5, 8.5, TOP - 0.7, TOP + 9, -8.5, 8.5), hexenhut,
                       {'p': '4A2478', 'P': '6A3AA0', 'd': '2A1248', 'G': '8CFF6B', 'b': 'E2A93A', 'g': '2A1248'}, glow='G')] + sterne)

    # --- Top Hat: Zylinder mit Satinband, Krempe leicht hochgebogen
    def zylinder(x, y, z):
        r = math.hypot(x, z)
        krempe_y = TOP - 0.9 + 0.05 * x * x
        if r <= 6.9 and krempe_y <= y <= krempe_y + 0.6: return 'k'
        if r <= 4.75 and TOP - 1.0 <= y <= TOP + 6.0:
            if TOP - 0.4 <= y <= TOP + 1.0: return 'r' if (x - z) % 2.2 > 0.25 else 'R'
            if r > 4.2 and -3.4 < x < -2.3: return 'h'
            return 'k' if y < TOP + 5.6 else 'K'
        return None
    design('hat', 'top_hat', 'Top Hat', text='Classic gentleman style',
           parts=[teil((-7.5, 7.5, TOP - 1.0, TOP + 6.0, -7.5, 7.5), zylinder, {'k': '17171D', 'K': '23232B', 'h': '3A3A48', 'r': 'B3203A', 'R': '8E1B2E'})])

    # --- Cowboy Hat: Krempe an den Seiten hochgebogen, Delle oben, Band mit Nieten
    def cowboy(x, y, z):
        ex = (x / 8.4) ** 2 + (z / 7.0) ** 2
        krempe = TOP - 0.8 + 1.6 * max(0, abs(x) - 4.6) ** 2 / 12 - 0.4 * (z < 0) * max(0, -z - 3.5) / 3
        if ex <= 1 and krempe <= y <= krempe + 0.55: return 'b' if ex < 0.85 else 'd'
        r = math.hypot(x / 1.05, z)
        if r <= 4.85 and TOP - 0.8 <= y:
            oben = TOP + 3.8 - 0.9 * math.exp(-x * x / 2.5) - 0.25 * (r / 4.85) ** 4
            if y > oben: return None
            if TOP - 0.4 <= y <= TOP + 0.5:
                a = math.atan2(z, x)
                return 's' if abs(math.sin(a * 6)) > 0.93 and r > 4.4 else 'D'
            return 'B' if x < -1.0 else 'b'
        return None
    design('hat', 'cowboy_hat', 'Cowboy Hat', text='Rolled brim and a studded band',
           parts=[teil((-9, 9, TOP - 1.0, TOP + 4.5, -7.5, 7.5), cowboy, {'b': '9A6236', 'B': 'B27645', 'd': '6E4425', 'D': '3E2614', 's': 'D9DDE3'})])

    # --- Propeller Cap: bunte Muetze, der Propeller dreht sich
    def muetze(x, y, z):
        if y < TOP - 1.4: return None
        e = (x / 4.85) ** 2 + ((y - TOP + 1.4) / 4.2) ** 2 + (z / 4.85) ** 2
        if e <= 1:
            if y > TOP + 2.4 and abs(x) < 0.6 and abs(z) < 0.6: return 'w'
            a = math.atan2(z, x)
            return 'rybg'[int(((a + math.pi) / (math.pi / 2) + 0.5)) % 4]
        # Schirm vorne
        if TOP - 1.4 <= y <= TOP - 0.9 and z < -3.0 and (x / 4.2) ** 2 + ((z + 3.0) / 3.4) ** 2 <= 1: return 'r'
        if abs(x) < 0.35 and abs(z) < 0.35 and TOP + 2.6 <= y <= TOP + 3.5: return 'w'   # Stiel
        return None
    def propeller(x, y, z):
        if abs(y) > 0.25: return None
        for s, f in ((1, 'y'), (-1, 'b')):
            if (((x - s * 2.1) / 2.1) ** 2 + (z / 0.75) ** 2) <= 1: return f
        if x * x + z * z < 0.45: return 'r'
        return None
    design('hat', 'propeller_cap', 'Propeller Cap', text='The propeller never stops',
           parts=[teil((-5, 5, TOP - 1.5, TOP + 4.0, -6.5, 5), muetze, {'r': 'E8434B', 'y': 'F7D43C', 'b': '3D8BE6', 'g': '4CC25B', 'w': 'F4F4F4'}),
                  lokal((4.4, 0.25, 1.0), propeller, {'y': 'F7D43C', 'b': '3D8BE6', 'r': 'E8434B'}, (0, TOP + 3.6, 0), anim='spinY:28', vs=0.25)])

    # --- Viking Helmet: Stahlhaube mit Bronzeband, Nasenschutz und Hoernern
    hpfade = []
    for s in (-1, 1):
        pf, rd = [], []
        for i in range(10):
            u = i / 9
            pf.append((s * (4.6 + 2.4 * u), TOP - 0.2 + 2.0 * u + 2.4 * u * u, 0.3 - 0.8 * u))
            rd.append(1.1 * (1 - u) + 0.15)
        hpfade.append((pf, rd))
    def wikinger(x, y, z):
        for pf, rd in hpfade:
            drin, u = roehre((x, y, z), pf, rd)
            if drin: return 'K' if u > 0.8 else 'w'
        e = (x / 4.85) ** 2 + ((y - TOP + 1.6) / 4.2) ** 2 + (z / 4.85) ** 2
        if e <= 1 and y >= TOP - 1.6 and e >= 0.72:
            if y < TOP - 0.6: return 'n' if abs(math.atan2(z, x) % (math.pi / 4) - 0.2) < 0.08 else 'g'
            if abs(x) < 0.4 and y > TOP + 1: return 'g'
            return 's' if x < 0 else 'S'
        # Nasenschutz
        if abs(x) < 0.55 and -5.2 < z < -4.6 and TOP - 3.6 < y < TOP - 1.0: return 'g'
        return None
    design('hat', 'viking_helmet', 'Viking Helmet', text='Steel, bronze and mighty horns',
           parts=[teil((-8, 8, TOP - 4, TOP + 6, -5.5, 5.5), wikinger,
                       {'s': '9AA3AE', 'S': 'C3CBD4', 'g': 'B5803A', 'n': 'E3C27A', 'w': 'F1E6CC', 'K': '6B5A44'})])

    # --- Santa Hat: Fellrand, Spitze faellt zur Seite, Bommel
    def muetze_rot(x, y, z):
        r = math.hypot(x, z)
        if 4.4 <= r <= 5.5 and TOP - 0.6 <= y <= TOP + 1.2 + 0.25 * _h(x, 0, z, 3):
            return 'w' if _h(x, y, z) > 0.3 else 'W'
        h = y - TOP - 1.0
        if h < 0: return None
        knick = max(0, h - 2.5)
        cx = 1.6 * knick ** 1.3
        cy_off = 0
        rad = 4.5 * (1 - h / 7.0)
        if rad <= 0.3: return None
        if math.hypot(x - cx, z) <= rad: return 'r' if (x - cx) > -rad * 0.4 else 'R'
        return None
    design('hat', 'santa_hat', 'Santa Hat', text='Fluffy trim and a bouncing pompom',
           parts=[teil((-6, 10, TOP - 0.8, TOP + 7, -6, 6), muetze_rot, {'r': 'D62828', 'R': 'A81C1C', 'w': 'F7F7F7', 'W': 'E4E4EA'}),
                  lokal((1.3, 1.3, 1.3), lambda x, y, z: ('w' if x * x + y * y + z * z <= 1.25 ** 2 else None), {'w': 'F7F7F7'},
                        (6.6, TOP + 6.2, 0), anim='swing:9', vs=0.25)])

    # --- Party Hat: gestreifter Kegel, schief, Bommel oben
    def party(x, y, z):
        h = y - TOP
        if h < 0: return None
        rad = 2.9 * (1 - h / 6.2)
        if rad <= 0: return None
        if math.hypot(x, z) > rad: return None
        streifen = int(((h + math.atan2(z, x) * 0.35) / 1.3) % 3)
        a = math.atan2(z, x)
        if math.hypot(x, z) > rad - 0.5 and (int(h * 1.6) + int((a + math.pi) * 2.2)) % 5 == 0: return 'w'
        return 'pyc'[streifen]
    neig = math.radians(14)
    kegel = teil((-3, 3, TOP, TOP + 6.5, -3, 3), party, {'p': 'FF5FA8', 'y': 'FFD23F', 'c': '38C8F0', 'w': 'FFFFFF'})
    bommel = lokal((0.9, 0.9, 0.9), lambda x, y, z: ('w' if x * x + y * y + z * z <= 0.85 ** 2 else None), {'w': 'FFF6FB'},
                   (0, 0, 0), anim='breathe:0.12')
    # schief: um den Fusspunkt (x 1.6, y TOP) gekippt
    fx, fy = 1.6, TOP
    for pt, hoehe in ((kegel, 3.25), (bommel, 6.6)):
        pt['rot'] = (0, 0, -14)
        pt['at'] = (round(fx + hoehe * math.sin(neig), 3), round(fy + hoehe * math.cos(neig), 3), 0)
    design('hat', 'party_hat', 'Party Hat', text='Stripes, dots and a pompom', parts=[kegel, bommel])

    # --- Cat Ears
    def katzenohr(x, y, z):
        t = (y + 1.8) / 3.6
        if not (0 <= t <= 1): return None
        b = 1.95 * (1 - t)
        if abs(x) > b: return None
        if z < -0.2 and abs(x) < b - 0.5 and t < 0.7: return 'i'
        return 'k' if x < b - 0.45 else 'K'
    design('hat', 'cat_ears', 'Cat Ears', text='Soft ears with pink insides',
           parts=[lokal((2.1, 1.9, 0.75), katzenohr, {'k': '2A2A33', 'K': '3C3C48', 'i': 'FF9FC6'}, (s * 2.6, TOP + 1.6, 0.3),
                        anim=f'twitch:{-s * 18},{ph}', rot=(0, 0, -s * 16)) for s, ph in ((-1, 11), (1, 53))])

    # --- Bunny Ears: lang, ein Ohr geknickt, wiegen sich
    def hasenohr(x, y, z):
        t = (y + 3.6) / 7.2
        if not (0 <= t <= 1): return None
        b = 1.15 * math.sin(math.pi * min(1, 0.15 + t * 0.95)) ** 0.5
        if abs(x) > b: return None
        if z < -0.2 and abs(x) < b - 0.45 and 0.1 < t < 0.9: return 'i'
        return 'w' if x < b - 0.4 else 'W'
    design('hat', 'bunny_ears', 'Bunny Ears', text='Long ears that sway',
           parts=[lokal((1.3, 3.7, 0.6), hasenohr, {'w': 'F7F4F0', 'W': 'E2DDD6', 'i': 'FFB7D2'}, (-2.0, TOP + 3.3, 0.3),
                        anim='sway:4,0', rot=(0, 0, 8)),
                  lokal((1.3, 3.7, 0.6), hasenohr, {'w': 'F7F4F0', 'W': 'E2DDD6', 'i': 'FFB7D2'}, (2.6, TOP + 2.6, 0.3),
                        anim='sway:5,1.5', rot=(0, 0, -34))])

    # --- Mushroom Hat: roter Hut mit weissen Punkten
    def pilz(x, y, z):
        h = y - TOP + 0.4
        e = (x / 6.4) ** 2 + (h / 3.6) ** 2 + (z / 6.4) ** 2
        if h < 0 or e > 1: return None
        if h < 0.6: return 'u' if math.hypot(x, z) > 4.6 else None
        # Punkte auf der Oberflaeche
        if e > 0.78:
            for k in range(9):
                a = k * 2.4
                py = 1.4 + 1.6 * ((k * 0.37) % 1)
                px, pz = 5.0 * math.cos(a) * (1 - (py - 1.4) / 3.5), 5.0 * math.sin(a) * (1 - (py - 1.4) / 3.5)
                if (x - px) ** 2 + (h - py) ** 2 + (z - pz) ** 2 < 1.2: return 'w'
            if h > 3.0 and x * x + z * z < 2.2: return 'w'
        return 'r' if x > -1.5 else 'R'
    design('hat', 'mushroom_hat', 'Mushroom Hat', text='A cozy toadstool cap',
           parts=[teil((-6.5, 6.5, TOP - 0.4, TOP + 3.3, -6.5, 6.5), pilz, {'r': 'E0302A', 'R': 'B8241F', 'w': 'FFF6EA', 'u': 'EADCC8'})])


    # ================= neu in 4.37 =================
    def eckig(x, z, r, p=4.0):
        """Abgerundetes Quadrat (deckt die Kopf-Ecken ab): <= 1 innerhalb."""
        return (abs(x) / r) ** p + (abs(z) / r) ** p

    # --- Snapback: Muetze mit Naehten, flacher Schirm nach vorne, leuchtendes V vorne
    BASIS = TOP - 1.6
    def snapback(x, y, z):
        h = y - BASIS
        if h >= 0:
            r = 4.8 * math.sqrt(max(0.0, 1 - (max(0.0, h - 1.6) / 2.0) ** 2))
            if r > 0 and eckig(x, z, r) <= 1:
                if h > 3.3 and x * x + z * z < 0.6: return 'b'
                if z < -2.6 and h < 3.0:
                    vy = h - 1.55
                    if -1.1 < vy < 1.0 and abs(abs(x) - (vy + 1.15) * 0.62) < 0.42: return 'v'
                    return 'F'
                a = math.atan2(z, x)
                if abs(math.sin(a * 3)) < 0.13 and h > 0.4: return 'd'
                return 'c'
        # Schirm: halbe Ellipse vor dem Kopf, leicht nach unten geneigt
        if z < -3.3:
            vor = -z - 3.3
            schirm_y = BASIS + 0.15 - 0.07 * vor
            if (x / 4.6) ** 2 + (vor / 5.4) ** 2 <= 1 and schirm_y <= y <= schirm_y + 0.55:
                return 'u' if y < schirm_y + 0.25 else 'B'
        return None
    design('hat', 'snapback', 'Snapback', text='Flat brim with a glowing V',
           parts=[teil((-5, 5, BASIS - 1.0, BASIS + 3.6, -9.0, 5.0), snapback,
                       {'c': '1C1C24', 'd': '2E2E3A', 'F': '23232D', 'v': 'A78BFA', 'b': '7C3AED', 'B': '7C3AED', 'u': '4C1D95'}, glow='v')])

    # --- Beanie: Strickmuetze mit Umschlag und Bommel
    BB = TOP - 2.6
    def beanie(x, y, z):
        h = y - BB
        if h < 0: return None
        if h < 1.6:
            if eckig(x, z, 5.05) <= 1:
                a = math.atan2(z, x)
                return 'C' if abs(math.sin(a * 16)) < 0.35 else 'c'
            return None
        r = 4.85 * math.sqrt(max(0.0, 1 - (max(0.0, h - 2.6) / 2.4) ** 2))
        if r > 0 and eckig(x, z, r) <= 1:
            rippe = abs(math.sin(math.atan2(z, x) * 14))
            return 'k' if rippe > 0.3 else 'K'
        if (x * x + z * z + (h - 5.6) ** 2) <= 1.9 ** 2:
            return 'p' if (x + z + (h - 5.6)) < 1.0 else 'P'
        return None
    design('hat', 'beanie', 'Beanie', text='Knitted, with a fluffy pom-pom',
           parts=[teil((-5.5, 5.5, BB, BB + 7.0, -5.5, 5.5), beanie,
                       {'k': '5B21B6', 'K': '4C1D95', 'c': 'EDE9FE', 'C': 'C4B5FD', 'p': 'FFFFFF', 'P': 'E5E7EB'})])

    # --- Pirate Hat: Dreispitz mit hochgebogener Krempe und Goldborte
    BP = TOP - 1.2
    def dreispitz(x, y, z):
        h = y - BP
        r = math.hypot(x, z)
        a = math.degrees(math.atan2(z, x))
        rel_ = ((a + 90) % 120) - 60
        rand = min(4.75 / max(0.35, math.cos(math.radians(rel_))), 8.0)
        if r <= rand and r > 4.0:
            t = max(0.0, (r - 4.4) / max(0.1, rand - 4.4))
            yb = 0.2 + 2.2 * t ** 1.6
            steig = 2.2 * 1.6 * t ** 0.6 / max(0.1, rand - 4.4)
            if yb - 0.1 <= h <= yb + 0.6 + steig * 0.6:
                return 'g' if rand - r < 0.65 else ('k' if h > yb + 0.25 else 'K')
        if 0 <= h <= 3.2 and eckig(x, z, 4.7 * math.sqrt(max(0.0, 1 - (max(0.0, h - 1.4) / 1.9) ** 2)) + 1e-6) <= 1:
            return 'k' if h > 0.8 else 'r'
        return None
    design('hat', 'pirate_hat', 'Pirate Hat', text='A tricorn with a golden trim',
           parts=[teil((-8.5, 8.5, BP - 0.2, BP + 3.8, -8.5, 8.5), dreispitz,
                       {'k': '17171D', 'K': '22222A', 'g': 'D9A93A', 'r': '8E1B2E'})])

    # --- Frost Crown: Eiszacken rund um den Kopf, Spitzen leuchten
    zacken = [(k * 45 + 22.5, 2.2 + (1.6 if k in (5, 6) else 0.7 * ((k * 3) % 2))) for k in range(8)]
    def frost(x, y, z):
        h = y - (TOP - 0.8)
        if eckig(x, z, 5.15) > 1 or eckig(x, z, 4.45) < 1: return None
        if 0 <= h <= 1.1: return 'b' if h < 0.55 else 'B'
        a = math.degrees(math.atan2(z, x)) % 360
        for wz, hoch in zacken:
            d = abs((a - wz + 180) % 360 - 180)
            breite = 18 * (1 - (h - 1.1) / hoch)
            if 1.1 < h <= 1.1 + hoch and d < breite:
                return 'G' if h > 1.1 + hoch * 0.7 else ('i' if d < breite * 0.5 else 'I')
        return None
    design('hat', 'frost_crown', 'Frost Crown', text='Ice spikes that glow in the dark',
           parts=[teil((-5.5, 5.5, TOP - 0.8, TOP + 3.3, -5.5, 5.5), frost,
                       {'b': '7DD3FC', 'B': 'BAE6FD', 'i': 'E0F2FE', 'I': '93C5FD', 'G': 'F0FBFF'}, glow='G')])

    # --- Bucket Hat: weiche Krempe nach unten, Steppnaehte
    BU = TOP - 1.9
    def eimer(x, y, z):
        h = y - BU
        rk = 4.9 - 0.12 * max(0.0, h)
        if 0 <= h <= 3.4 and eckig(x, z, rk) <= 1:
            if h > 3.0: return 'D'
            return 's' if abs(h - 1.2) < 0.18 or abs(h - 2.2) < 0.18 else 'd'
        r = math.hypot(x, z)
        if 4.6 < r <= 7.4:
            yb = 0.05 - (r - 4.6) * 0.42
            if yb <= h <= yb + 0.5:
                return 's' if abs(r - 6.6) < 0.22 else 'd'
        return None
    design('hat', 'bucket_hat', 'Bucket Hat', text='Soft brim with stitching',
           parts=[teil((-7.5, 7.5, BU - 1.6, BU + 3.6, -7.5, 7.5), eimer,
                       {'d': '2B5C8A', 'D': '23507A', 's': '8FB3D9'})])

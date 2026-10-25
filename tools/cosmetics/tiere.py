"""Pets (seit 4.95, neu gestaltet in 4.98): kleine Voxel-Tiere.

Pet-Raum (in Minecraft-Pixeln): y nach oben, Gesicht zeigt nach -z (wie der Spieler).
Fliegende Pets: Ursprung = Mitte des Tieres; der Client setzt sie neben die Schulter und
laesst sie schweben. Boden-Pets (ground=1): Ursprung = Boden unter dem Tier (Fuesse bei y = 0);
der Client stellt sie neben die Fuesse und laesst sie mitlaufen.

Animationen der Teile (Client, ZubehoerEbene):
  flap:grad,tempo,phase    Fluegel schlagen (Drehung um die Laengsachse)
  leg:phase,py             Bein schwingt beim Laufen (Drehpunkt py Pixel ueber der Teil-Mitte)
  wag:grad,tempo,pz        Schwanz wedelt (Drehung um y, Drehpunkt pz Pixel vor der Teil-Mitte)
  hop:hoehe                huepft (Schleim), mit Stauchen beim Aufkommen
  waddle:grad              watschelt beim Laufen (Pinguin)
  swing:grad               pendelt (Ohren)

Schattierung: jede Farbe gibt es hell (oben, Licht) und dunkel (unten); dazu kommen im Spiel
die weichen Schatten an Ecken (Netz.voxel). Augen sind gross, mit Glanzpunkt.

Alle Designs sind eigene Entwuerfe.
"""
import math
import huete

VS = 0.25


def ell(x, y, z, cx, cy, cz, rx, ry, rz):
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2 <= 1


def ellw(x, y, z, cx, cy, cz, rx, ry, rz):
    """Wert < 1 innerhalb der Ellipse (fuer weiche Uebergaenge)."""
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2


def schatten(y, cy, ry, hell, mitte, dunkel):
    """Oben hell, unten dunkel -- y relativ zur Form."""
    u = (y - cy) / ry
    return hell if u > 0.35 else (dunkel if u < -0.45 else mitte)


def auge(x, y, z, cx, cy, s, r=0.42, nach=-1):
    """Grosses rundes Auge auf der Flaeche: Rueckgabe 'K' (Pupille), 'W' (Glanz) oder None.
    s = Seite (-1/1), Auge bei (s*cx, cy)."""
    dx, dy = x - s * cx, y - cy
    d = math.hypot(dx, dy)
    if d > r: return None
    if math.hypot(dx + 0.14 * s * 0 - 0.12, dy - 0.14) < r * 0.36: return 'W'
    return 'K'


def tiere(design):
    teil, lokal, roehre = huete.teil, huete.lokal, huete.roehre

    # ================================================================ BODEN-PETS

    # --- Fox Kit: orangefarbener Fuchs mit weisser Brust, dunklen Pfoten und buschigem Schwanz
    def fuchs(x, y, z):
        # Kopf
        if ell(x, y, z, 0, 4.6, -2.3, 1.75, 1.55, 1.55):
            if z < -3.45:
                a = auge(x, y, z, 0.72, 4.85, 1 if x > 0 else -1, 0.4)
                if a: return a
            if y < 4.2 and abs(x) > 0.55 and z < -2.6: return 'w'
            return schatten(y, 4.6, 1.55, 'O', 'o', 'r')
        # Schnauze
        if ell(x, y, z, 0, 4.0, -3.9, 0.75, 0.6, 0.9):
            if z < -4.6 and y > 3.95: return 'k'
            return 'w' if y < 4.2 else 'o'
        # Ohren
        for s in (-1, 1):
            d, u = huete.strecke_abstand((x, y, z), (0.95 * s, 5.6, -2.3), (1.25 * s, 7.1, -2.1))
            if d <= 0.62 - 0.55 * u: return 'k' if u > 0.65 else ('p' if abs(z + 2.55) < 0.12 and abs(x) < 1.25 else 'o')
        # Koerper
        if ell(x, y, z, 0, 2.7, 0.5, 1.45, 1.35, 2.4):
            if y < 2.3 and z < 1.4 and abs(x) < 0.9: return 'w'
            return schatten(y, 2.7, 1.35, 'O', 'o', 'r')
        # Brust
        if ell(x, y, z, 0, 3.0, -1.4, 1.0, 1.1, 0.8): return 'w'
        return None

    def fuchsbein(x, y, z):
        if not (abs(x) < 0.42 and abs(z) < 0.42): return None
        return 'k' if y < 0.15 else 'o'

    def fuchsschwanz(x, y, z):
        drin, u = roehre((x, y, z), [(0, 0, -1.4), (0, 0.6, 0.2), (0, 1.5, 1.5), (0, 2.3, 2.2)], [0.55, 0.95, 0.9, 0.35])
        if not drin: return None
        return 'w' if u > 0.78 else ('O' if y > 0.9 else 'o')

    farben_fuchs = {'o': 'F07A1E', 'O': 'FF9A3D', 'r': 'B4520F', 'w': 'FFF4E6', 'k': '2A1A12', 'K': '120A06', 'W': 'FFFFFF', 'p': 'FFB4A8'}
    teile = [teil((-1.9, 1.9, 1.2, 7.3, -4.9, 3.0), fuchs, farben_fuchs, vs=VS, mitte=(0, 0, 0))]
    for i, (bx, bz) in enumerate(((0.8, -1.1), (-0.8, -1.1), (0.8, 2.0), (-0.8, 2.0))):
        ph = 0 if i in (0, 3) else 3.1416
        teile.append(lokal((0.45, 0.8, 0.45), fuchsbein, farben_fuchs, at=(bx, 0.8, bz), anim='leg:%s,0.8' % ph, vs=VS))
    teile.append(lokal((0.8, 2.6, 2.6), fuchsschwanz, farben_fuchs, at=(0, 2.9, 3.6), anim='wag:16,0.18,-1.4', vs=VS))
    design('pet', 'fox', 'Fox Kit', text='A little fox that trots along at your feet', ground=1, parts=teile)

    # --- Kitty: graue Tigerkatze mit gruenen Augen, rosa Nase und geringeltem Schwanz
    def katze(x, y, z):
        if ell(x, y, z, 0, 4.4, -2.0, 1.65, 1.45, 1.45):
            if z < -3.05:
                s = 1 if x > 0 else -1
                dx, dy = x - s * 0.68, y - 4.55
                d = math.hypot(dx, dy * 1.1)
                if d < 0.46:
                    if abs(dx) < 0.1: return 'K'
                    if math.hypot(dx - 0.12 * s * 0 - 0.1, dy - 0.15) < 0.14: return 'W'
                    return 'g'
                if abs(x) < 0.22 and 3.95 < y < 4.15: return 'p'
                if y < 4.0 and abs(x) < 0.8: return 'w'
            streif = abs(x) < 1.1 and y > 5.2 and int((x + 2) / 0.45) % 2 == 0
            return 'd' if streif else schatten(y, 4.4, 1.45, 'A', 'a', 'd')
        for s in (-1, 1):
            d, u = huete.strecke_abstand((x, y, z), (0.9 * s, 5.4, -1.9), (1.1 * s, 6.7, -1.9))
            if d <= 0.55 - 0.5 * u: return 'p' if (abs(z + 2.25) < 0.12 and u < 0.7) else 'a'
        if ell(x, y, z, 0, 2.6, 0.6, 1.3, 1.3, 2.3):
            if y < 2.0 and abs(x) < 0.8: return 'w'
            if y > 2.4 and int((z + 3) / 0.7) % 2 == 0: return 'd'
            return schatten(y, 2.6, 1.3, 'A', 'a', 'd')
        return None

    def katzenbein(x, y, z):
        if not (abs(x) < 0.38 and abs(z) < 0.38): return None
        return 'w' if y < 0.1 else 'a'

    def katzenschwanz(x, y, z):
        drin, u = roehre((x, y, z), [(0, -1.6, -1.8), (0, -0.6, 0), (0, 1.4, 0.7), (0, 2.6, 0.0)], [0.36, 0.33, 0.3, 0.28])
        if not drin: return None
        return 'd' if int(u * 8) % 2 else 'a'

    farben_katze = {'a': '8B8F98', 'A': 'A9ADB5', 'd': '4B5059', 'w': 'F3F4F6', 'g': '4ADE80', 'K': '0F172A', 'W': 'FFFFFF', 'p': 'F9A8D4'}
    teile = [teil((-1.8, 1.8, 1.2, 7.1, -3.5, 3.0), katze, farben_katze, vs=VS, mitte=(0, 0, 0))]
    for i, (bx, bz) in enumerate(((0.7, -0.9), (-0.7, -0.9), (0.7, 2.1), (-0.7, 2.1))):
        ph = 0 if i in (0, 3) else 3.1416
        teile.append(lokal((0.4, 0.8, 0.4), katzenbein, farben_katze, at=(bx, 0.8, bz), anim='leg:%s,0.8' % ph, vs=VS))
    teile.append(lokal((0.5, 2.7, 2.1), katzenschwanz, farben_katze, at=(0, 4.2, 4.6), anim='wag:12,0.1,-1.8', vs=VS))
    design('pet', 'cat', 'Kitty', text='A curious kitten that follows you everywhere', ground=1, parts=teile)

    # --- Golden Pup: goldener Welpe mit Schlappohren, rosa Zunge und wedelndem Schwanz
    def welpe(x, y, z):
        if ell(x, y, z, 0, 4.7, -2.1, 1.8, 1.65, 1.6):
            if z < -3.35:
                a = auge(x, y, z, 0.7, 5.0, 1 if x > 0 else -1, 0.42)
                if a: return a
            return schatten(y, 4.7, 1.65, 'Y', 'y', 'b')
        if ell(x, y, z, 0, 4.05, -3.75, 0.95, 0.75, 0.85):
            if z < -4.45 and y > 4.15 and abs(x) < 0.38: return 'k'
            if y < 3.55 and abs(x) < 0.35 and z < -3.9: return 'p'
            return 'c'
        if ell(x, y, z, 0, 2.7, 0.4, 1.55, 1.45, 2.3):
            if y < 2.1 and abs(x) < 0.9: return 'c'
            return schatten(y, 2.7, 1.45, 'Y', 'y', 'b')
        return None

    def welpenohr(s):
        def f(x, y, z):
            return 'b' if ell(x, y, z, 0.15 * s, -0.5, 0, 0.35, 1.05, 0.6) else None
        return f

    def welpenbein(x, y, z):
        if not (abs(x) < 0.46 and abs(z) < 0.46): return None
        return 'c' if y < 0.2 else 'y'

    def welpenschwanz(x, y, z):
        drin, u = roehre((x, y, z), [(0, -0.8, -1.0), (0, 0.4, 0.2), (0, 1.3, 0.6)], [0.42, 0.36, 0.22])
        return ('Y' if u > 0.5 else 'y') if drin else None

    farben_welpe = {'y': 'E0A23C', 'Y': 'F4C46A', 'b': 'A86A1E', 'c': 'FCE8C4', 'k': '1F140B', 'K': '120A04', 'W': 'FFFFFF', 'p': 'F472B6'}
    teile = [teil((-2.0, 2.0, 1.2, 6.4, -4.7, 2.8), welpe, farben_welpe, vs=VS, mitte=(0, 0, 0))]
    for s in (1, -1):
        teile.append(lokal((0.5, 1.6, 0.7), welpenohr(s), farben_welpe, at=(1.75 * s, 5.4, -2.0), anim='swing:9', vs=VS))
    for i, (bx, bz) in enumerate(((0.85, -1.0), (-0.85, -1.0), (0.85, 1.9), (-0.85, 1.9))):
        ph = 0 if i in (0, 3) else 3.1416
        teile.append(lokal((0.5, 0.8, 0.5), welpenbein, farben_welpe, at=(bx, 0.8, bz), anim='leg:%s,0.8' % ph, vs=VS))
    teile.append(lokal((0.5, 1.5, 1.2), welpenschwanz, farben_welpe, at=(0, 3.4, 3.0), anim='wag:28,0.45,-1.0', vs=VS))
    design('pet', 'pup', 'Golden Pup', text='A happy puppy that wags its tail', ground=1, parts=teile)

    # --- Pebble the Penguin: watschelt jetzt am Boden
    def pinguin(x, y, z):
        if ell(x, y, z, 0, 2.6, 0, 1.6, 2.2, 1.45):
            if z < -0.45 and ellw(x, y, z, 0, 2.3, -1.0, 1.05, 1.75, 1.0) < 1: return 'w'
            return 'n' if y > 3.6 else 'k'
        if ell(x, y, z, 0, 5.1, -0.1, 1.3, 1.15, 1.2):
            if z < -0.85:
                sx = 1 if x > 0 else -1
                d = math.hypot(x - 0.55 * sx, y - 5.35)
                if d < 0.5:
                    if d < 0.3: return 'W' if math.hypot(x - 0.55 * sx - 0.1, y - 5.45) < 0.11 else 'K'
                    return 'w'
                if abs(x) < 0.85 and y < 5.0: return 'w'
            return 'n'
        d, u = huete.strecke_abstand((x, y, z), (0, 4.85, -1.2), (0, 4.7, -2.0))
        if d < 0.32 - 0.18 * u: return 'o'
        if ell(x, y, z, 0.6, 0.25, -0.6, 0.45, 0.25, 0.7) or ell(x, y, z, -0.6, 0.25, -0.6, 0.45, 0.25, 0.7): return 'o'
        if ell(x, y, z, 0, 3.0, -1.2, 0.5, 0.45, 0.2): return 'y'
        return None

    def flosse(s):
        def f(x, y, z):
            if x * s < 0: return None
            return 'n' if ell(x, y, z, 0.25 * s, -0.7, 0, 0.3, 1.1, 0.5) else None
        return f

    farben_pinguin = {'k': '1E293B', 'n': '334155', 'w': 'F8FAFC', 'K': '0B1020', 'W': 'FFFFFF', 'o': 'F59E0B', 'y': 'FDE68A'}
    teile = [teil((-1.7, 1.7, 0, 6.4, -2.3, 1.5), pinguin, farben_pinguin, vs=VS, mitte=(0, 0, 0), anim='waddle:10')]
    for s in (1, -1):
        teile.append(lokal((0.6, 1.9, 0.6), flosse(s), farben_pinguin, at=(1.65 * s, 3.4, 0.0), anim='flap:%s,0.25,0' % (-12 * s), vs=VS))
    design('pet', 'penguin', 'Pebble the Penguin', text='Waddles along right next to you', ground=1, parts=teile)

    # --- Slimey: huepft am Boden, durchscheinend wirkend mit dunklerem Kern
    def schleim(x, y, z):
        cx, cy, cz, h = 0, 1.9, 0, 1.9
        e = max(abs(x - cx), abs(y - cy), abs(z - cz))
        if e > h: return None
        kante = sum(1 for v in (abs(x - cx), abs(y - cy), abs(z - cz)) if v > h - 0.35)
        if kante >= 2: return None
        if z < -1.55:
            s = 1 if x > 0 else -1
            if abs(x - 0.75 * s) < 0.32 and 2.15 < y < 2.85: return 'W' if (y > 2.6 and abs(x - 0.75 * s - 0.1) < 0.12) else 'k'
            if abs(x) < 0.45 and 1.25 < y < 1.5: return 'k'
            if abs(abs(x) - 1.15) < 0.25 and 1.6 < y < 1.85: return 'p'
        if max(abs(x), abs(y - cy), abs(z)) < 0.9: return 'd'
        if y > cy + 1.3: return 'G'
        return 'g' if y > cy - 1.2 else 'd'
    design('pet', 'slime', 'Slimey', text='A bouncy slime that hops beside you', ground=1,
           parts=[teil((-1.9, 1.9, 0, 3.8, -1.9, 1.9), schleim, {'g': '4ADE80', 'G': '86EFAC', 'd': '16A34A', 'k': '14532D', 'W': 'ECFDF5', 'p': 'F9A8D4'},
                       anim='hop:2.2', vs=VS, mitte=(0, 0, 0))])

    # ================================================================ FLIEGENDE PETS

    # --- Mini Dragon: violetter Drache mit grossen Augen, Bauchplatten, Hoernern und Fluegeln
    def drache(x, y, z):
        if ell(x, y, z, 0, 1.6, -1.6, 1.55, 1.35, 1.4):
            if z < -2.65:
                a = auge(x, y, z, 0.62, 1.85, 1 if x > 0 else -1, 0.42)
                if a: return a
            return schatten(y, 1.6, 1.35, 'P', 'p', 'q')
        if ell(x, y, z, 0, 1.15, -2.85, 0.8, 0.6, 0.75):
            if abs(abs(x) - 0.32) < 0.13 and y > 1.35 and z < -3.4: return 'k'
            return 'p'
        for s in (-1, 1):
            d, u = huete.strecke_abstand((x, y, z), (0.6 * s, 2.6, -1.4), (0.95 * s, 3.8, -0.6))
            if d <= 0.34 - 0.26 * u: return 'h'
        if ell(x, y, z, 0, 0, 0.6, 1.35, 1.25, 1.8):
            if y < -0.35 and z < 1.6 and abs(x) < 0.9: return 'b' if int((y + 3) / 0.4) % 2 else 'B'
            return schatten(y, 0, 1.25, 'P', 'p', 'q')
        drin, u = roehre((x, y, z), [(0, -0.2, 2.2), (0, 0.2, 3.3), (0, 1.0, 4.2), (0, 1.8, 4.5)], [0.6, 0.45, 0.3, 0.15])
        if drin: return 'h' if u > 0.85 else ('P' if y > 0.5 + u * 1.2 else 'p')
        for s in (-1, 1):
            for zz in (-0.2, 1.4):
                if abs(x - 0.8 * s) < 0.32 and -1.7 < y < -0.8 and abs(z - zz) < 0.32: return 'p' if y > -1.45 else 'h'
        if abs(x) < 0.16 and 0.95 < y < 1.5 and -0.4 < z < 1.9 and (int((z + 0.4) / 0.45) % 2 == 0): return 'h'
        return None
    farbe_drache = {'p': '7C3AED', 'P': '9F67FF', 'q': '4C1D95', 'b': 'DDD6FE', 'B': 'C4B5FD', 'e': '1E0B3A', 'W': 'FFFFFF', 'K': '1E0B3A', 'k': '1E1B2E', 'h': 'F5E6FF'}

    def fluegel(s):
        def f(x, y, z):
            if x * s < 0: return None
            r = abs(x)
            if abs(y - 0.12 * r) > 0.13 or r > 3.6: return None
            vorn = -0.5 + 0.1 * r
            hinten = 1.7 - 0.3 * r + 0.4 * math.cos(r * 2.6)
            if not (vorn <= z <= hinten): return None
            if z - vorn < 0.28 or abs(r - 1.8) < 0.12 or abs(r - 2.9) < 0.12: return 'q'
            return 'M' if z < (vorn + hinten) / 2 else 'm'
        return f
    teile = [teil((-1.8, 1.8, -1.9, 4.0, -3.7, 4.8), drache, farbe_drache, vs=VS)]
    for s in (1, -1):
        teile.append(lokal((3.7, 0.9, 2.1), fluegel(s), {'m': 'A78BFA', 'M': 'C4B5FD', 'q': '5B21B6'},
                           at=(1.1 * s, 1.0, 0.4), anim='flap:%s,0.3,0' % (-30 * s), vs=VS))
    design('pet', 'mini_dragon', 'Mini Dragon', text='A tiny dragon that flaps along beside you', parts=teile)

    # --- Busy Bee: rund und flauschig, grosse Augen, Fuehler, schnelle Fluegel
    def biene(x, y, z):
        if ell(x, y, z, 0, 0, 0.4, 1.55, 1.45, 1.8):
            band = int((z + 1.4) / 0.7) % 2
            if band: return 'k'
            return schatten(y, 0, 1.45, 'Y', 'y', 'o')
        if ell(x, y, z, 0, 0.35, -1.55, 1.2, 1.1, 1.0):
            if z < -2.3:
                a = auge(x, y, z, 0.55, 0.55, 1 if x > 0 else -1, 0.5)
                if a: return a
                if abs(abs(x) - 0.95) < 0.22 and abs(y - 0.0) < 0.18: return 'p'
            return 'Y'
        for s in (-1, 1):
            d, u = huete.strecke_abstand((x, y, z), (0.35 * s, 1.2, -1.9), (0.8 * s, 2.3, -2.4))
            if d < 0.12: return 'k'
            if ell(x, y, z, 0.83 * s, 2.35, -2.42, 0.22, 0.22, 0.22): return 'k'
        drin, u = roehre((x, y, z), [(0, -0.1, 2.1), (0, -0.25, 2.8)], [0.3, 0.06])
        if drin: return 'k'
        return None

    def bienenfluegel(s):
        def f(x, y, z):
            if x * s < 0 or abs(y) > 0.13: return None
            e = ellw(x, 0, z, 1.3 * s, 0, 0.25, 1.35, 1, 0.8)
            if e > 1: return None
            return 'v' if e > 0.72 else 'w'
        return f
    teile = [teil((-1.6, 1.6, -1.5, 2.7, -2.6, 3.0), biene, {'y': 'FACC15', 'Y': 'FDE047', 'o': 'CA8A04', 'k': '1C1917', 'K': '0C0A09', 'W': 'FFFFFF', 'p': 'FDA4AF'}, vs=VS)]
    for s in (1, -1):
        teile.append(lokal((2.8, 0.3, 1.2), bienenfluegel(s), {'w': 'E0F2FE', 'v': 'BAE6FD'}, at=(0.45 * s, 1.45, 0.1),
                           anim='flap:%s,1.7,0' % (-24 * s), vs=VS))
    design('pet', 'bee', 'Busy Bee', text='Buzzes over your shoulder', parts=teile)

    # --- Boo: freundlicher Geist mit roten Baeckchen, leuchtet im Dunkeln
    def geist(x, y, z):
        r = math.hypot(x, z)
        welle = -1.7 + 0.35 * math.sin(math.atan2(z, x) * 5)
        if y > 0.3:
            if not ell(x, y, z, 0, 0.3, 0, 1.6, 1.8, 1.55): return None
        elif r > 1.6 or y < welle:
            return None
        if z < -1.0:
            s = 1 if x > 0 else -1
            if math.hypot((x - 0.58 * s) / 0.36, (y - 0.85) / 0.5) < 1: return 'W' if math.hypot(x - 0.58 * s - 0.1, y - 1.05) < 0.13 else 'k'
            if abs(x) < 0.35 and -0.15 < y < 0.2 and abs(x) > 0.05 * 0: return 'm'
            if math.hypot(abs(x) - 1.0, y - 0.35) < 0.24: return 'p'
        return 'w' if y > -0.6 else 'g'
    design('pet', 'ghost', 'Boo', text='A friendly ghost that glows in the dark',
           parts=[teil((-1.75, 1.75, -2.1, 2.25, -1.75, 1.75), geist,
                       {'w': 'F8FAFC', 'g': 'DBEAFE', 'k': '1E293B', 'W': 'FFFFFF', 'm': '475569', 'p': 'FDA4AF'}, glow='wg', vs=VS)])

    # --- Creator Phoenix: nur fuer Vortex Creator -- gluehender Vogel mit Flammenfluegeln
    def phoenix(x, y, z):
        if ell(x, y, z, 0, 0, 0.3, 1.25, 1.25, 1.75):
            if y < -0.4 and z < 0.6: return 'y'
            return schatten(y, 0, 1.25, 'O', 'o', 'r')
        if ell(x, y, z, 0, 1.2, -1.35, 0.95, 0.9, 0.9):
            if z < -2.0:
                a = auge(x, y, z, 0.45, 1.4, 1 if x > 0 else -1, 0.28)
                if a: return a
            return 'O'
        d, u = huete.strecke_abstand((x, y, z), (0, 1.05, -2.1), (0, 0.75, -2.8))
        if d < 0.26 - 0.16 * u: return 'Y'
        for k, (a, b) in enumerate((((0, 1.9, -1.3), (0, 3.0, -0.6)), ((0.35, 1.8, -1.0), (0.6, 2.7, -0.2)), ((-0.35, 1.8, -1.0), (-0.6, 2.7, -0.2)))):
            d, u = huete.strecke_abstand((x, y, z), a, b)
            if d < 0.2 - 0.12 * u: return 'Y' if u > 0.6 else 'o'
        for k, ww in enumerate((-0.5, 0, 0.5)):
            drin, u = roehre((x, y, z), [(0, -0.2, 1.8), (ww * 1.2, -0.9, 3.0), (ww * 2.2, -1.6, 4.2)], [0.45, 0.32, 0.14])
            if drin: return 'Y' if u > 0.7 else ('o' if u > 0.35 else 'r')
        return None

    def flamme(s):
        def f(x, y, z):
            if x * s < 0: return None
            r = abs(x)
            if abs(y - 0.18 * r) > 0.14 or r > 3.3: return None
            vorn = -0.5 + 0.1 * r
            hinten = 1.5 - 0.25 * r + 0.45 * math.cos(r * 3.1)
            if vorn <= z <= hinten:
                q = (z - vorn) / max(0.1, hinten - vorn)
                return 'Y' if q > 0.72 else ('o' if q > 0.35 else 'r')
            return None
        return f
    farben_ph = {'o': 'F97316', 'O': 'FB923C', 'r': 'DC2626', 'y': 'FDE047', 'Y': 'FEF08A', 'K': '1C0A00', 'W': 'FFFFFF'}
    teile = [teil((-1.5, 1.5, -2.0, 3.2, -3.0, 4.6), phoenix, farben_ph, glow='oOrYy', vs=VS)]
    for s in (1, -1):
        teile.append(lokal((3.4, 0.9, 1.8), flamme(s), farben_ph, at=(1.0 * s, 0.7, 0.3),
                           anim='flap:%s,0.35,0' % (-30 * s), glow='oYr', vs=VS))
    design('pet', 'phoenix', 'Creator Phoenix', text='Only for Vortex Creators: a blazing phoenix', parts=teile)

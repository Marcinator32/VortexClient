package com.vortex.client.gui;

import com.vortex.client.core.ConfigManager;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ArmorHudModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * HUD-Editor: HUD-Elemente und die einzelnen ArmorHUD-Teile mit der Maus
 * verschieben, statt Zahlen einzutippen.
 *
 * Neu gegenueber der ersten Fassung:
 *   - Raster im Hintergrund als optische Orientierung
 *   - Einrasten an Raster, Bildschirmkanten, Bildschirmmitte und an den Kanten
 *     der uebrigen Elemente -- mit Hilfslinien, die zeigen, woran gerade
 *     ausgerichtet wird
 *   - Werkzeugleiste zum Umschalten von Raster und Einrasten
 *   - Positionsanzeige am gezogenen Element
 *
 * Bedienung: linke Maustaste halten und ziehen, ESC schliesst und speichert.
 */
public class HudEditorScreen extends Screen {

    /** Abstand, ab dem eingerastet wird. */
    private static final int SNAP_DIST = 6;
    /** Rasterweite in Pixeln (umschaltbar 4 / 8 / 16 / 32). */
    private static int GRID = 8;
    private static final int[] GRIDS = {4, 8, 16, 32};

    private static final int C_BAR   = VortexStyle.BAR;
    private static final int C_INNER = VortexStyle.INNER;

    private static boolean showGrid = true;
    private static boolean snapping = true;

    private int mx = 0, my = 0;

    private HudElement draggingElement = null;
    private NumberSetting dragPartOffX = null;
    private NumberSetting dragPartOffY = null;
    private int partStartOffX = 0, partStartOffY = 0;
    private int dragStartMouseX = 0, dragStartMouseY = 0;
    private boolean dragArmor = false;
    private int armorStartBaseX = 0, armorStartBaseY = 0;
    private int[] armorStartBox = null;
    private int grabOffsetX = 0, grabOffsetY = 0;

    private final List<int[]> guidesV = new ArrayList<>();
    private final List<int[]> guidesH = new ArrayList<>();

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    private List<HudElement> elements() {
        List<HudElement> list = new ArrayList<>();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (com.vortex.client.core.CleanModules.versteckt(m)) continue;
            if (m instanceof HudElement he && m.isEnabled()) list.add(he);
            if (m instanceof com.vortex.client.module.modules.ItemCounterModule ic
                    && m.isEnabled()) {
                list.addAll(ic.getCounters());
            }
        }
        return list;
    }

    private ArmorHudModule armor() {
        ArmorHudModule a = ModuleManager.INSTANCE.get(ArmorHudModule.class);
        return (a != null && a.isEnabled()) ? a : null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.mx = mouseX;
        this.my = mouseY;

        ctx.fill(0, 0, this.width, this.height, 0x88000000);

        int accent = Theme.INSTANCE.accent.get() | 0xFF000000;

        if (showGrid) drawGrid(ctx);

        int cxm = this.width / 2, cym = this.height / 2;
        ctx.fill(cxm, 0, cxm + 1, this.height, 0x18FFFFFF);
        ctx.fill(0, cym, this.width, cym + 1, 0x18FFFFFF);

        for (HudElement he : elements()) {
            int x = he.hudX().getInt();
            int y = he.hudY().getInt();
            int w = he.hudWidth();
            int h = he.hudHeight();

            boolean hovered = inside(mouseX, mouseY, x, y, w, h);
            boolean active = (he == draggingElement);
            int border = active ? accent : (hovered ? 0xFF9AD8FF : 0x99FFFFFF);
            int fill = active ? withAlpha(accent, 0x50) : (hovered ? 0x30FFFFFF : 0x1AFFFFFF);

            ctx.fill(x, y, x + w, y + h, fill);
            drawBorder(ctx, x, y, w, h, border);
            ctx.text(this.font, he.hudName(), x + 3, y + 3, 0xFFFFFFFF);

            if (active) {
                String pos = x + ", " + y;
                ctx.text(this.font, Component.literal(pos),
                        x, y - 11, accent);
            }
        }

        ArmorHudModule a = armor();
        if (a != null) {
            int[] b = armorBox(a);
            boolean hovered = inside(mouseX, mouseY, b[0], b[1], b[2], b[3]);
            int border = dragArmor ? accent : (hovered ? 0xFF9AD8FF : 0x99FFFFFF);
            int fill = dragArmor ? withAlpha(accent, 0x50) : (hovered ? 0x30FFFFFF : 0x1AFFFFFF);
            ctx.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], fill);
            drawBorder(ctx, b[0], b[1], b[2], b[3], border);
            ctx.text(this.font, "ArmorHUD", b[0] + 3, b[1] - 10 < 0 ? b[1] + b[3] + 2 : b[1] - 10, 0xFFFFFFFF);
            if (dragArmor) ctx.text(this.font, Component.literal(a.baseX.getInt() + ", " + a.baseY.getInt()),
                    b[0], b[1] + b[3] + 2, accent);
            if (!com.vortex.client.hud.ArmorHud.karteAktiv(a)) {
                for (ArmorHudModule.ArmorPart part : a.computeParts(this.width, this.height)) {
                    int x = part.x, y = part.y, sz = part.size;
                    boolean hov = inside(mouseX, mouseY, x, y, sz, sz);
                    boolean active = (dragPartOffX != null
                            && dragPartOffX == part.offsetX && dragPartOffY == part.offsetY);
                    int rand = active ? accent : (hov ? 0xFF9AD8FF : 0x66CCCCCC);
                    ctx.fill(x, y, x + sz, y + sz, active ? withAlpha(accent, 0x50) : 0x2A55AAFF);
                    drawBorder(ctx, x, y, sz, sz, rand);
                    ctx.text(this.font, part.name.substring(0, 1), x + 5, y + 4, 0xFFFFFFFF);
                }
            }
        }

        for (int[] g : guidesV) {
            ctx.fill(g[0], Math.min(g[1], g[2]), g[0] + 1, Math.max(g[1], g[2]), accent);
        }
        for (int[] g : guidesH) {
            ctx.fill(Math.min(g[1], g[2]), g[0], Math.max(g[1], g[2]), g[0] + 1, accent);
        }

        drawToolbar(ctx, accent);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void drawGrid(GuiGraphicsExtractor ctx) {
        int i = 0;
        for (int x = 0; x < this.width; x += GRID, i++) {
            ctx.fill(x, 0, x + 1, this.height, i % 4 == 0 ? 0x1CFFFFFF : 0x0AFFFFFF);
        }
        i = 0;
        for (int y = 0; y < this.height; y += GRID, i++) {
            ctx.fill(0, y, this.width, y + 1, i % 4 == 0 ? 0x1CFFFFFF : 0x0AFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        ArmorHudModule am = armor();
        if (am != null && vertical != 0) {
            int[] b = armorBox(am);
            if (inside(mouseX, mouseY, b[0], b[1], b[2], b[3])) {
                double neu = Math.max(am.scale.getMin(), Math.min(am.scale.getMax(), am.scale.get() + Math.signum(vertical) * 0.1));
                am.scale.set(Math.round(neu * 10) / 10.0);
                zeige("ArmorHUD: " + Math.round(am.scale.get() * 100) + " %");
                return true;
            }
        }
        List<HudElement> els = elements();
        for (int i = els.size() - 1; i >= 0; i--) {
            HudElement he = els.get(i);
            if (!inside(mouseX, mouseY, he.hudX().getInt(), he.hudY().getInt(), he.hudWidth(), he.hudHeight())) continue;
            NumberSetting sc = he.hudScale();
            if (sc == null || vertical == 0) return true;
            double schritt = Math.max(sc.getStep(), 0.05);
            double neu = Math.max(sc.getMin(), Math.min(sc.getMax(), sc.get() + Math.signum(vertical) * schritt));
            sc.set(Math.round(neu * 100) / 100.0);
            zeige(he.hudName() + ": " + Math.round(sc.get() * 100) + " %");
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private record Knopf(float x, float y, float w, float h, Runnable run) {}
    private final List<Knopf> knoepfe = new ArrayList<>();
    private boolean profileOffen = false;
    private String meldung = null;
    private long meldungBis = 0;

    private void drawToolbar(GuiGraphicsExtractor ctx, int accent) {
        knoepfe.clear();
        com.vortex.client.gui.glatt.Glatt.Schrift F = com.vortex.client.gui.glatt.Glatt.Schrift.FETT;
        com.vortex.client.gui.glatt.Glatt.Schrift N = com.vortex.client.gui.glatt.Glatt.Schrift.NORMAL;
        String[] texte = {"Grid", GRID + " px", "Snap", "Profiles", "Style", "Done"};
        boolean[] an = {showGrid, false, snapping, profileOffen, false, false};
        float pad = 10, gap = 4, h = 22;
        float titelB = com.vortex.client.gui.glatt.Glatt.breite("HUD Editor", F);
        float summe = titelB + 18;
        float[] bw = new float[texte.length];
        for (int i = 0; i < texte.length; i++) { bw[i] = com.vortex.client.gui.glatt.Glatt.breite(texte[i], F) + 20; summe += bw[i] + gap; }
        float W = summe + pad * 2 - gap;
        float x0 = Math.max(4, (this.width - W) / 2f), y0 = 6;
        com.vortex.client.gui.glatt.Glatt.schatten(ctx, x0, y0 + 2, W, h + 8, 10, 10, 0x66000000);
        com.vortex.client.gui.glatt.Glatt.rund(ctx, x0, y0, W, h + 8, 10, 0xF2111218);
        com.vortex.client.gui.glatt.Glatt.rahmen(ctx, x0, y0, W, h + 8, 10, 1, 0x18FFFFFF);
        float x = x0 + pad;
        com.vortex.client.gui.glatt.Glatt.text(ctx, "HUD Editor", x, y0 + 4 + (h - 9) / 2f, 0xFFF2F3F7, F);
        x += titelB + 18;
        Runnable[] aktion = {
            () -> showGrid = !showGrid,
            () -> { int i = 0; while (i < GRIDS.length && GRIDS[i] != GRID) i++; GRID = GRIDS[(i + 1) % GRIDS.length]; },
            () -> snapping = !snapping,
            () -> profileOffen = !profileOffen,
            () -> this.minecraft.gui.setScreen(new HudStyleScreen(this)),
            this::onClose,
        };
        for (int i = 0; i < texte.length; i++) {
            float kx = x, ky = y0 + 4;
            boolean hov = inside(mx, my, (int) kx, (int) ky, (int) bw[i], (int) h);
            int grund = an[i] ? (accent & 0x00FFFFFF) | 0xCC000000 : (hov ? 0x26FFFFFF : 0x12FFFFFF);
            if (i == texte.length - 1) grund = hov ? accent : (accent & 0x00FFFFFF) | 0xB0000000;
            com.vortex.client.gui.glatt.Glatt.rund(ctx, kx, ky, bw[i], h, 7, grund);
            com.vortex.client.gui.glatt.Glatt.textMitte(ctx, texte[i], kx + bw[i] / 2f, ky + (h - 9) / 2f,
                    an[i] || i == texte.length - 1 || hov ? 0xFFFFFFFF : 0xFFA4A8B8, F);
            knoepfe.add(new Knopf(kx, ky, bw[i], h, aktion[i]));
            if (i == 3) profilKnopfX = kx;
            x += bw[i] + gap;
        }
        if (profileOffen) profilListe(ctx, profilKnopfX, y0 + h + 12, accent);

        String hin = meldung != null && System.currentTimeMillis() < meldungBis ? meldung
                : "Drag to move  \u00B7  Scroll to resize  \u00B7  Shift-drag moves one armor piece  \u00B7  Esc saves";
        float hb = com.vortex.client.gui.glatt.Glatt.breite(hin, N) + 20;
        if (hb < this.width - 8) {
            float hx = (this.width - hb) / 2f, hy = this.height - 22;
            com.vortex.client.gui.glatt.Glatt.rund(ctx, hx, hy, hb, 16, 8, 0xC0111218);
            com.vortex.client.gui.glatt.Glatt.textMitte(ctx, hin, this.width / 2f, hy + 4, 0xFFA4A8B8, N);
        }
    }

    private float profilKnopfX = 0;

    /** Aufgeklappte Profilliste: Klick laedt, x loescht, unten "Save current layout". */
    private void profilListe(GuiGraphicsExtractor ctx, float x, float y, int accent) {
        com.vortex.client.gui.glatt.Glatt.Schrift F = com.vortex.client.gui.glatt.Glatt.Schrift.FETT;
        com.vortex.client.gui.glatt.Glatt.Schrift N = com.vortex.client.gui.glatt.Glatt.Schrift.NORMAL;
        List<String> profile = com.vortex.client.hud.HudProfile.alle();
        float w = 180, zh = 20;
        int zeilen = Math.min(profile.size(), Math.max(1, (int) ((this.height - y - 60) / (zh + 2))));
        float h = 8 + (profile.isEmpty() ? 18 : zeilen * (zh + 2)) + 26 + 8;
        x = Math.min(x, this.width - w - 6);
        com.vortex.client.gui.glatt.Glatt.schatten(ctx, x, y + 2, w, h, 10, 10, 0x66000000);
        com.vortex.client.gui.glatt.Glatt.rund(ctx, x, y, w, h, 10, 0xF7111218);
        com.vortex.client.gui.glatt.Glatt.rahmen(ctx, x, y, w, h, 10, 1, 0x18FFFFFF);
        knoepfe.add(new Knopf(x, y, w, h, () -> {}));
        float cy = y + 8;
        if (profile.isEmpty()) {
            com.vortex.client.gui.glatt.Glatt.text(ctx, "No profiles yet", x + 10, cy + 4, 0xFF5D6172, N);
            cy += 18;
        }
        for (int i = 0; i < zeilen; i++) {
            String n = profile.get(i);
            boolean hov = inside(mx, my, (int) x + 4, (int) cy, (int) w - 8, (int) zh);
            if (hov) com.vortex.client.gui.glatt.Glatt.rund(ctx, x + 4, cy, w - 8, zh, 6, 0x1AFFFFFF);
            com.vortex.client.gui.glatt.Glatt.text(ctx, com.vortex.client.gui.glatt.Glatt.kuerzen(n, (int) w - 40, N), x + 12, cy + 6, 0xFFF2F3F7, N);
            float dx = x + w - 22;
            boolean hovX = inside(mx, my, (int) dx, (int) cy + 2, 16, 16);
            com.vortex.client.gui.glatt.Glatt.symbol(ctx, com.vortex.client.gui.glatt.Symbole.Symbol.KREUZ, dx + 3, cy + 5, 10, hovX ? 0xFFF87171 : 0xFF5D6172);
            knoepfe.add(new Knopf(x + 4, cy, w - 30, zh, () -> {
                boolean ok = com.vortex.client.hud.HudProfile.laden(n);
                zeige(ok ? "Loaded \"" + n + "\"" : "Could not load \"" + n + "\"");
                profileOffen = false;
            }));
            knoepfe.add(new Knopf(dx, cy + 2, 16, 16, () -> {
                com.vortex.client.hud.HudProfile.loeschen(n);
                zeige("Deleted \"" + n + "\"");
            }));
            cy += zh + 2;
        }
        cy += 4;
        float sh = 22;
        boolean hovS = inside(mx, my, (int) x + 6, (int) cy, (int) w - 12, (int) sh);
        com.vortex.client.gui.glatt.Glatt.rund(ctx, x + 6, cy, w - 12, sh, 7, hovS ? accent : (accent & 0x00FFFFFF) | 0x99000000);
        com.vortex.client.gui.glatt.Glatt.textMitte(ctx, "Save current layout", x + w / 2f, cy + 6.5f, 0xFFFFFFFF, F);
        knoepfe.add(new Knopf(x + 6, cy, w - 12, sh, () -> {
            HudEditorScreen zurueck = this;
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(new TextEingabeScreen(zurueck, "Profile name", "",
                    "For example PvP, Building, Recording", name -> {
                        String sauber = com.vortex.client.hud.HudProfile.sauber(name);
                        if (sauber.isEmpty()) { zurueck.zeige("Name needs letters or numbers"); return; }
                        boolean ok = com.vortex.client.hud.HudProfile.speichern(sauber);
                        zurueck.zeige(ok ? "Saved \"" + sauber + "\"" : "Could not save");
                    }));
        }));
    }

    void zeige(String text) {
        meldung = text;
        meldungBis = System.currentTimeMillis() + 2500;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            for (int i = knoepfe.size() - 1; i >= 0; i--) {
                Knopf k = knoepfe.get(i);
                if (mx >= k.x() && mx < k.x() + k.w() && my >= k.y() && my < k.y() + k.h()) {
                    k.run().run();
                    return true;
                }
            }
            if (profileOffen) {
                profileOffen = false;
                return true;
            }

            ArmorHudModule a = armor();
            if (a != null) {
                if (click.hasShiftDown()) {
                    List<ArmorHudModule.ArmorPart> parts = a.computeParts(this.width, this.height);
                    for (int i = parts.size() - 1; i >= 0; i--) {
                        ArmorHudModule.ArmorPart part = parts.get(i);
                        if (inside(mx, my, part.x, part.y, part.size, part.size)) {
                            if (com.vortex.client.hud.ArmorHud.karteAktiv(a))
                                zeige("Moving single pieces switches the ArmorHUD to the classic look");
                            dragPartOffX = part.offsetX;
                            dragPartOffY = part.offsetY;
                            partStartOffX = part.offsetX.getInt();
                            partStartOffY = part.offsetY.getInt();
                            dragStartMouseX = mx;
                            dragStartMouseY = my;
                            return true;
                        }
                    }
                }
                int[] b = armorBox(a);
                if (inside(mx, my, b[0], b[1], b[2], b[3])) {
                    dragArmor = true;
                    armorStartBaseX = a.baseX.getInt();
                    armorStartBaseY = a.baseY.getInt();
                    armorStartBox = b;
                    dragStartMouseX = mx;
                    dragStartMouseY = my;
                    return true;
                }
            }

            List<HudElement> els = elements();
            for (int i = els.size() - 1; i >= 0; i--) {
                HudElement he = els.get(i);
                int x = he.hudX().getInt();
                int y = he.hudY().getInt();
                if (inside(mx, my, x, y, he.hudWidth(), he.hudHeight())) {
                    draggingElement = he;
                    grabOffsetX = mx - x;
                    grabOffsetY = my - y;
                    return true;
                }
            }
        }
        if (click.button() == 1) {
            ArmorHudModule a = armor();
            if (a != null) {
                int[] b = armorBox(a);
                if (inside(mx, my, b[0], b[1], b[2], b[3]) && a.einzelnVerschoben()) {
                    a.einzelnZuruecksetzen();
                    zeige("ArmorHUD pieces lined up again");
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private int[] armorBox(ArmorHudModule a) {
        int[] b = com.vortex.client.hud.ArmorHud.bereich(net.minecraft.client.Minecraft.getInstance(), a, this.width, this.height);
        if (b[2] < 18) { b[0] -= (18 - b[2]) / 2; b[2] = 18; }
        if (b[3] < 18) { b[1] -= (18 - b[3]) / 2; b[3] = 18; }
        return b;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        guidesV.clear();
        guidesH.clear();

        if (dragPartOffX != null && dragPartOffY != null) {
            dragPartOffX.set(partStartOffX + (mx - dragStartMouseX));
            dragPartOffY.set(partStartOffY + (my - dragStartMouseY));
            return true;
        }

        if (dragArmor && armorStartBox != null) {
            ArmorHudModule a = armor();
            if (a == null) { dragArmor = false; return true; }
            int[] b0 = armorStartBox;
            int newX = clamp(b0[0] + (mx - dragStartMouseX), 0, this.width - b0[2]);
            int newY = clamp(b0[1] + (my - dragStartMouseY), 0, this.height - b0[3]);
            if (snapping) {
                newX = snapX(newX, b0[2]);
                newY = snapY(newY, b0[3]);
            }
            a.baseX.set(armorStartBaseX + (newX - b0[0]));
            a.baseY.set(armorStartBaseY + (newY - b0[1]));
            return true;
        }

        if (draggingElement != null) {
            int w = draggingElement.hudWidth();
            int h = draggingElement.hudHeight();
            int newX = clamp(mx - grabOffsetX, 0, this.width - w);
            int newY = clamp(my - grabOffsetY, 0, this.height - h);

            if (snapping) {
                newX = snapX(newX, w);
                newY = snapY(newY, h);
            }
            draggingElement.hudX().set(newX);
            draggingElement.hudY().set(newY);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    /**
     * Waagerechtes Einrasten. Geprueft werden linke Kante, rechte Kante und
     * Mitte des gezogenen Elements gegen: Bildschirmkanten, Bildschirmmitte,
     * Rasterlinien und die Kanten der uebrigen Elemente.
     */
    private int snapX(int x, int w) {
        List<Integer> targets = new ArrayList<>();
        targets.add(0);
        targets.add(this.width);
        targets.add(this.width / 2);
        for (HudElement other : elements()) {
            if (other == draggingElement) continue;
            int ox = other.hudX().getInt();
            targets.add(ox);
            targets.add(ox + other.hudWidth());
        }

        int best = x;
        int bestDist = SNAP_DIST + 1;
        int guide = Integer.MIN_VALUE;

        for (int tX : targets) {
            if (Math.abs(x - tX) < bestDist) {
                bestDist = Math.abs(x - tX); best = tX; guide = tX;
            }
            if (Math.abs((x + w) - tX) < bestDist) {
                bestDist = Math.abs((x + w) - tX); best = tX - w; guide = tX;
            }
            if (Math.abs((x + w / 2) - tX) < bestDist) {
                bestDist = Math.abs((x + w / 2) - tX); best = tX - w / 2; guide = tX;
            }
        }

        if (bestDist <= SNAP_DIST) {
            guidesV.add(new int[] { guide, 0, this.height });
            return clamp(best, 0, this.width - w);
        }
        int snapped = Math.round(x / (float) GRID) * GRID;
        return clamp(snapped, 0, this.width - w);
    }

    /** Senkrechtes Einrasten -- gleiche Logik wie {@link #snapX}. */
    private int snapY(int y, int h) {
        List<Integer> targets = new ArrayList<>();
        targets.add(0);
        targets.add(this.height);
        targets.add(this.height / 2);
        for (HudElement other : elements()) {
            if (other == draggingElement) continue;
            int oy = other.hudY().getInt();
            targets.add(oy);
            targets.add(oy + other.hudHeight());
        }

        int best = y;
        int bestDist = SNAP_DIST + 1;
        int guide = Integer.MIN_VALUE;

        for (int tY : targets) {
            if (Math.abs(y - tY) < bestDist) {
                bestDist = Math.abs(y - tY); best = tY; guide = tY;
            }
            if (Math.abs((y + h) - tY) < bestDist) {
                bestDist = Math.abs((y + h) - tY); best = tY - h; guide = tY;
            }
            if (Math.abs((y + h / 2) - tY) < bestDist) {
                bestDist = Math.abs((y + h / 2) - tY); best = tY - h / 2; guide = tY;
            }
        }

        if (bestDist <= SNAP_DIST) {
            guidesH.add(new int[] { guide, 0, this.width });
            return clamp(best, 0, this.height - h);
        }
        int snapped = Math.round(y / (float) GRID) * GRID;
        return clamp(snapped, 0, this.height - h);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        boolean was = (draggingElement != null) || (dragPartOffX != null) || dragArmor;
        dragArmor = false;
        armorStartBox = null;
        draggingElement = null;
        dragPartOffX = null;
        dragPartOffY = null;
        guidesV.clear();
        guidesH.clear();
        if (was) return true;
        return super.mouseReleased(click);
    }

    @Override
    public void removed() {
        ConfigManager.save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int clamp(int v, int min, int max) {
        if (max < min) return min;
        return v < min ? min : (v > max ? max : v);
    }

    private boolean inside(double px, double py, int x, int y, int w, int h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    private void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int mixColor(int a, int b, float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (((int) (aa + (ba - aa) * t)) << 24)
                | (((int) (ar + (br - ar) * t)) << 16)
                | (((int) (ag + (bg - ag) * t)) << 8)
                | ((int) (ab + (bb - ab) * t));
    }

    /** Rechteck mit leicht abgerundeten Ecken -- wie im uebrigen Client. */
    private void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(3, Math.min(w / 2, h / 2));
        ctx.fill(x, y + r, x + w, y + h - r, color);
        ctx.fill(x + r, y, x + w - r, y + r, color);
        ctx.fill(x + r, y + h - r, x + w - r, y + h, color);
        for (int i = 0; i < r; i++) {
            int ein = r - i - 1;
            ctx.fill(x + ein, y + i, x + r, y + i + 1, color);
            ctx.fill(x + w - r, y + i, x + w - ein, y + i + 1, color);
            ctx.fill(x + ein, y + h - i - 1, x + r, y + h - i, color);
            ctx.fill(x + w - r, y + h - i - 1, x + w - ein, y + h - i, color);
        }
    }

}

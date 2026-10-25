package com.vortex.client.hud;

import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ArmorHudModule;
import com.vortex.client.module.modules.CpsModule;
import com.vortex.client.module.modules.KeystrokesModule;
import com.vortex.client.module.modules.TotemPopperModule;
import com.vortex.client.module.modules.SessionStatsModule;
import com.vortex.client.module.modules.CoordinatesModule;
import com.vortex.client.module.modules.PotionEffectsModule;
import com.vortex.client.module.modules.FpsModule;
import com.vortex.client.module.modules.PingModule;
import com.vortex.client.module.modules.TotemCountModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Zeichnet die HUD-Overlays ueber die HudElementRegistry-API.
 */
public final class HudRenderer {

    private static final String MOD_ID = "vortexclient";

    public static void register() {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
            (context, tickCounter) -> onHudRender(context)
        );

        try {
            HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, vanilla -> (ctx, tick) -> {
                Module m = find(PotionEffectsModule.class);
                if (m != null && m.isEnabled()) return;
                vanilla.extractRenderState(ctx, tick);
            });
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("HudRenderer.effects", pvpErr);
        }
    }

    private static Module find(Class<? extends Module> type) {
        return ModuleManager.INSTANCE.get(type);
    }

    private static void onHudRender(GuiGraphicsExtractor context) {
        long pvpT0 = System.nanoTime();
        try {
            onHudRenderInner(context);
        } finally {
            com.vortex.client.core.Profiler.record("HUD", System.nanoTime() - pvpT0);
        }
    }

    private static void onHudRenderInner(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();

        if (client.font == null) return;

        com.vortex.client.hud.WaypointHud.draw(context, client);
        drawKeystrokes(context, client);
        drawTotemPopper(context, client);
        drawRecordingHint(context, client);
        ArmorWarning.render(context, client);
        ItemCounterRenderer.render(context, client);
        DebugOverlay.render(context, client);
        drawSessionStats(context, client);
        ExtraHud.render(context, client);
        BossBars.render(context, client);
        SlotLock.render(context, client);
        try { ModulListe.render(context, client); } catch (Throwable pvpErr) { com.vortex.client.core.Errors.report("ModuleList", pvpErr); }
        try { InfoBar.render(context, client); } catch (Throwable pvpErr) { com.vortex.client.core.Errors.report("InfoBar", pvpErr); }
        try { LeistenZahlen.render(context, client); } catch (Throwable pvpErr) { com.vortex.client.core.Errors.report("LeistenZahlen", pvpErr); }

        CpsModule cps = (CpsModule) find(CpsModule.class);
        if (cps != null && cps.isEnabled()) {
            String wert;
            switch (cps.mode.getIndex()) {
                case 1:
                    wert = String.valueOf(CpsCounter.RIGHT.getCps());
                    break;
                case 2:
                    wert = CpsCounter.LEFT.getCps() + " | " + CpsCounter.RIGHT.getCps();
                    break;
                default:
                    wert = String.valueOf(CpsCounter.LEFT.getCps());
                    break;
            }
            HudText.zeile(context, client.font, cps.x.getInt(), cps.y.getInt(), cps.scale.getFloat(),
                    cps.style, cps.color, "CPS", wert, HudStyle.FORM_LABEL_FIRST);
        }

        FpsModule fps = (FpsModule) find(FpsModule.class);
        if (fps != null && fps.isEnabled()) {
            HudText.zeile(context, client.font, fps.x.getInt(), fps.y.getInt(), fps.scale.getFloat(),
                    fps.style, fps.color, "FPS", String.valueOf(client.getFps()), HudStyle.FORM_LABEL_LAST);
        }

        PingModule ping = (PingModule) find(PingModule.class);
        if (ping != null && ping.isEnabled() && client.player != null
                && client.getConnection() != null) {
            int latency = 0;
            boolean own = false;
            if (ping.measure.get()) {
                PingMeter.setInterval((long) (ping.interval.get() * 1000));
                int measured = PingMeter.get();
                if (measured >= 0 && PingMeter.age() < 15_000L) {
                    latency = measured;
                    own = true;
                }
            }
            if (!own) {
                try {
                    net.minecraft.client.multiplayer.PlayerInfo entry =
                            client.getConnection()
                            .getPlayerInfo(client.player.getUUID());
                    if (entry != null) latency = entry.getLatency();
                } catch (Throwable ignored) {
                }
            }
            String text = own ? (latency + " ms") : (latency + " ms*");
            HudText.zeile(context, client.font, ping.x.getInt(), ping.y.getInt(), ping.scale.getFloat(),
                    ping.style, ping.color, "Ping", text, HudStyle.FORM_VALUE_ONLY);
        }

        CoordinatesModule coords = (CoordinatesModule) find(CoordinatesModule.class);
        if (coords != null && coords.isEnabled() && client.player != null && HudKarte.modern(coords.style.look)) {
            try { KoordKarte.render(context, client, coords); } catch (Throwable kErr) { com.vortex.client.core.Errors.report("Coordinates", kErr); }
        } else if (coords != null && coords.isEnabled() && client.player != null) {
            double px = Math.round(client.player.getX() * 10.0) / 10.0;
            double py = Math.round(client.player.getY() * 10.0) / 10.0;
            double pz = Math.round(client.player.getZ() * 10.0) / 10.0;

            String dir;
            switch (client.player.getDirection()) {
                case NORTH -> dir = "N";
                case SOUTH -> dir = "S";
                case EAST  -> dir = "E";
                case WEST  -> dir = "W";
                default    -> dir = "";
            }

            String text = px + " " + py + " " + pz + "  [" + dir + "]";
            if (com.vortex.client.module.modules.StreamerModeModule.koordinatenVerstecken()) {
                text = "hidden  [" + dir + "]";
            }
            HudText.zeile(context, client.font, coords.x.getInt(), coords.y.getInt(), coords.scale.getFloat(),
                    coords.style, coords.color, "XYZ", text, HudStyle.FORM_LABEL_FIRST);
        }

        PotionEffectsModule potions = (PotionEffectsModule) find(PotionEffectsModule.class);
        if (potions != null && potions.isEnabled() && client.player != null) {
            try {
                PotionHud.zeichnen(context, client, potions);
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("PotionHud", pvpErr);
            }
        }

        TotemCountModule totem = (TotemCountModule) find(TotemCountModule.class);
        if (totem != null && totem.isEnabled() && client.player != null) {
            int count = TotemCountModule.countTotems();
            int tx = totem.x.getInt();
            int ty = totem.y.getInt();

            var totemItem = TotemCountModule.totem();
            if (totemItem != null) {
                if (totemIcon == null
                        || !totemIcon.is(totemItem)) {
                    totemIcon = new net.minecraft.world.item.ItemStack(totemItem);
                }
            }

            if (count != lastTotemCount || totemCountText == null) {
                lastTotemCount = count;
                totemCountText = Component.literal("x" + count);
            }
            HudText.mitIcon(context, client.font, tx, ty, totem.scale.getFloat(),
                    totem.style, totem.color, totemCountText.getString(), totemIcon);
        }

        com.vortex.client.module.modules.OpponentInfoModule gegner =
                (com.vortex.client.module.modules.OpponentInfoModule) find(com.vortex.client.module.modules.OpponentInfoModule.class);
        if (gegner != null && gegner.isEnabled() && client.player != null) {
            try { OpponentHud.zeichnen(context, client, gegner); }
            catch (Throwable gErr) { com.vortex.client.core.Errors.report("OpponentHud", gErr); }
        }

        ArmorHudModule armor = (ArmorHudModule) find(ArmorHudModule.class);
        if (armor != null && armor.isEnabled() && client.player != null) {
            ArmorHud.render(context, client);
        }

        RadarRenderer.render(context, client);

        try { com.vortex.client.musik.MusikHud.render(context, client); }
        catch (Throwable musikErr) { com.vortex.client.core.Errors.report("NowPlaying", musikErr); }

        com.vortex.client.module.modules.PlayerListEspModule plist =
                (com.vortex.client.module.modules.PlayerListEspModule)
                        find(com.vortex.client.module.modules.PlayerListEspModule.class);
        if (plist != null && plist.isEnabled() && client.player != null
                && client.level != null) {
            try {
                long nowMs = System.currentTimeMillis();
                if (nowMs - playerListBuilt > 200) {
                    playerListBuilt = nowMs;
                    playerLines.clear();
                    for (net.minecraft.client.player.AbstractClientPlayer p
                            : client.level.players()) {
                        if (p == client.player) continue;
                        int dist = (int) client.player.distanceTo(p);
                        playerLines.add(p.getName().getString() + "  " + dist + "m");
                    }
                }
                java.util.List<String> lines = playerLines;
                int screenW = client.getWindow().getGuiScaledWidth();
                int y = 2;
                String header = "Spieler: " + lines.size();
                int hw = client.font.width(header);
                context.text(client.font, Component.literal(header),
                        screenW - hw - 2, y, plist.getColor());
                y += 11;
                for (String line : lines) {
                    int w = client.font.width(line);
                    context.text(client.font, Component.literal(line),
                            screenW - w - 2, y, plist.getColor());
                    y += 10;
                    if (y > client.getWindow().getGuiScaledHeight() - 10) break;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Beginnt eine Skalierung um den Punkt (anchorX, anchorY). Alles bis zum
     * passenden popScale wird mit dem Faktor scale gezeichnet, wobei der
     * Ankerpunkt fix bleibt (das Element waechst/schrumpft also an seiner
     * Position statt zum Bildschirmrand zu wandern).
     *
     * Nutzt den 2D-Matrixstack (Matrix3x2fStack) von GuiGraphicsExtractor.getMatrices(),
     * der in 1.21.11 fuer GUI-Transforms zustaendig ist.
     */
    /**
     * Zeichnet die Keystrokes-Anzeige (WASD, Leertaste, Maustasten).
     *
     * Gedrueckte Tasten werden hervorgehoben. Die Maustasten zeigen zusaetzlich
     * die Klicks pro Sekunde, damit man sein Klickverhalten im Blick hat.
     */
    /** Liste der Spieler mit verbrauchten Totems. */
    /**
     * Shows that a macro is being recorded.
     *
     * Without this the feature is a guessing game: you press Record, the menu
     * closes, and nothing on screen tells you whether anything is being
     * captured -- or how to stop. The line says both.
     */
    private static void drawRecordingHint(GuiGraphicsExtractor ctx, Minecraft client) {
        if (!com.vortex.client.macro.MacroManager.isRecording()) return;
        var macro = com.vortex.client.macro.MacroManager.recordingMacro();
        if (macro == null) return;

        String text = "\u25CF REC  " + macro.name + "  \u00B7  "
                + macro.steps.size() + " steps  \u00B7  Right Shift \u2192 Macros \u2192 Stop";
        int w = client.font.width(text);
        int x = (client.getWindow().getGuiScaledWidth() - w) / 2;
        int y = 6;

        float pulse = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 400.0);
        int alpha = (int) (255 * pulse) << 24;

        ctx.fill(x - 6, y - 3, x + w + 6, y + 11, 0x90000000);
        ctx.text(client.font, Component.literal(text),
                x, y, alpha | 0xFF5555);
    }

    /** Header of the totem popper list -- never changes, built once. */
    private static final Component TOTEM_POPPER_TITLE = Component.literal("Totems");

    private static void drawTotemPopper(GuiGraphicsExtractor ctx, Minecraft client) {
        TotemPopperModule mod = (TotemPopperModule) find(TotemPopperModule.class);
        if (mod == null || !mod.isEnabled()) return;
        if (!mod.showList.get()) return;
        if (client.font == null) return;

        var list = com.vortex.client.hud.TotemPops.top(mod.maxEntries.getInt());
        if (list.isEmpty()) return;

        java.util.List<HudText.Zeile> zeilen = new java.util.ArrayList<>(list.size() + 1);
        zeilen.add(new HudText.Zeile(null, TOTEM_POPPER_TITLE.getString()));
        for (var e : list) {
            boolean fresh = mod.highlight.get() && e.since < 2000;
            zeilen.add(new HudText.Zeile(e.name, String.valueOf(e.count),
                    fresh ? mod.highlightColor.get() : 0));
        }
        HudText.block(ctx, client.font, mod.x.getInt(), mod.y.getInt(), mod.scale.getFloat(),
                mod.style, mod.color, zeilen, HudStyle.FORM_LABEL_FIRST, 0, 1f, null);
    }

    /** Spielzeit, Tode, eigene Totems, hoechste Klickrate. */
    private static void drawSessionStats(GuiGraphicsExtractor ctx, Minecraft client) {
        SessionStatsModule mod = (SessionStatsModule) find(SessionStatsModule.class);
        if (mod == null || !mod.isEnabled()) return;
        if (client.font == null) return;


        long nowMs = System.currentTimeMillis();
        if (nowMs - sessionTextsBuilt > 500 || sessionTime == null) {
            sessionTextsBuilt = nowMs;
            sessionTime   = com.vortex.client.hud.SessionStats.playtime();
            sessionDeaths = String.valueOf(com.vortex.client.hud.SessionStats.getDeaths());
            sessionTotems = String.valueOf(com.vortex.client.hud.SessionStats.getOwnTotems());
            sessionCps    = String.valueOf(com.vortex.client.hud.SessionStats.getMaxCps());
        }

        java.util.List<HudText.Zeile> zeilen = new java.util.ArrayList<>(4);
        if (mod.showTime.get())   zeilen.add(new HudText.Zeile("Time", sessionTime));
        if (mod.showDeaths.get()) zeilen.add(new HudText.Zeile("Deaths", sessionDeaths));
        if (mod.showTotems.get()) zeilen.add(new HudText.Zeile("Totems", sessionTotems));
        if (mod.showMaxCps.get()) zeilen.add(new HudText.Zeile("Max CPS", sessionCps));
        HudText.block(ctx, client.font, mod.x.getInt(), mod.y.getInt(), mod.scale.getFloat(),
                mod.style, mod.color, zeilen, HudStyle.FORM_LABEL_FIRST, 0, 1f, null);
    }

    private static long sessionTextsBuilt = 0L;
    private static String sessionTime, sessionDeaths, sessionTotems, sessionCps;

    private static void drawKeystrokes(GuiGraphicsExtractor ctx, Minecraft client) {
        KeystrokesModule mod = (KeystrokesModule) find(KeystrokesModule.class);
        if (mod == null || !mod.isEnabled()) return;
        if (client.options == null || client.font == null) return;

        int baseX = mod.x.getInt();
        int baseY = mod.y.getInt();
        tastenModern = HudKarte.modern(mod.look);
        pushScale(ctx, baseX, baseY, mod.scale.getFloat());

        int idle = mod.idleColor.get();
        int press = mod.pressColor.get();
        int textCol = mod.color.get();

        final int KEY = 20;
        final int GAP = 2;

        drawKey(ctx, client, baseX + KEY + GAP, baseY, KEY, KEY,
                "W", client.options.keyUp.isDown(), idle, press, textCol);

        int row2 = baseY + KEY + GAP;
        drawKey(ctx, client, baseX, row2, KEY, KEY,
                "A", client.options.keyLeft.isDown(), idle, press, textCol);
        drawKey(ctx, client, baseX + KEY + GAP, row2, KEY, KEY,
                "S", client.options.keyDown.isDown(), idle, press, textCol);
        drawKey(ctx, client, baseX + (KEY + GAP) * 2, row2, KEY, KEY,
                "D", client.options.keyRight.isDown(), idle, press, textCol);

        int nextY = row2 + KEY + GAP;
        int fullW = KEY * 3 + GAP * 2;

        if (mod.showMouse.get()) {
            int half = (fullW - GAP) / 2;
            drawKey(ctx, client, baseX, nextY, half, KEY,
                    "L " + CpsCounter.LEFT.getCps(),
                    client.options.keyAttack.isDown(), idle, press, textCol);
            drawKey(ctx, client, baseX + half + GAP, nextY, half, KEY,
                    "R " + CpsCounter.RIGHT.getCps(),
                    client.options.keyUse.isDown(), idle, press, textCol);
            nextY += KEY + GAP;
        }

        if (mod.showSpace.get()) {
            drawKey(ctx, client, baseX, nextY, fullW, 10,
                    "", client.options.keyJump.isDown(), idle, press, textCol);
        }

        popScale(ctx);
    }

    /** Eine einzelne Taste: Flaeche plus mittige Beschriftung. */
    private static void drawKey(GuiGraphicsExtractor ctx, Minecraft client,
                                int x, int y, int w, int h, String label,
                                boolean pressed, int idle, int press, int textCol) {
        if (tastenModern) { tasteKarte(ctx, x, y, w, h, label, pressed, textCol); return; }
        ctx.fill(x, y, x + w, y + h, pressed ? press : idle);
        if (label == null || label.isEmpty()) return;
        int col = pressed ? 0xFF101014 : textCol;
        int tw = client.font.width(label);
        ctx.text(client.font, Component.literal(label),
                x + (w - tw) / 2, y + (h - 8) / 2, col);
    }

    private static boolean tastenModern;

    /** Weicher Druck-Wert je Taste (Karten-Stil). */
    private static final java.util.Map<String, double[]> TASTEN = new java.util.HashMap<>();

    /**
     * Taste im HUD Design "Modern" (seit 4.56): runde Karte; gedrueckt fuellt sie sich weich
     * mit dem Akzent-Verlauf. Maustasten: Buchstabe gedimmt, Klicks pro Sekunde hell.
     */
    private static void tasteKarte(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean pressed, int textCol) {
        String key = x + ":" + y + ":" + w;
        double[] zz = TASTEN.computeIfAbsent(key, k -> new double[]{0, 0});
        long jetzt = System.nanoTime();
        double dt = zz[1] == 0 ? 0.016 : Math.min(0.1, (jetzt - zz[1]) / 1e9);
        zz[1] = jetzt;
        zz[0] += ((pressed ? 1 : 0) - zz[0]) * (1 - Math.exp(-(pressed ? 30 : 10) * dt));
        float[] z = {(float) zz[0]};
        float r = Math.min(HudKarte.radius(), 4f);
        com.vortex.client.gui.glatt.Glatt.schatten(ctx, x, y + 1, w, h, r + 1, 2f, 0x55000000);
        com.vortex.client.gui.glatt.Glatt.rund(ctx, x, y, w, h, r, 0xD014151B);
        if (z[0] > 0.01f) {
            int a = Math.round(255 * 0.85f * z[0]);
            com.vortex.client.gui.glatt.Glatt.rund(ctx, x, y, w, h, r, (a << 24) | (HudKarte.akzent(0.3f) & 0xFFFFFF));
            com.vortex.client.gui.glatt.Glatt.rund(ctx, x + w / 2f, y, w / 2f, h, r, (Math.round(a * 0.6f) << 24) | (HudKarte.akzent(1f) & 0xFFFFFF));
        }
        if (label == null || label.isEmpty()) {
            com.vortex.client.gui.glatt.Glatt.rund(ctx, x + w / 2f - 8, y + h / 2f - 0.5f, 16, 1, 0.5f, 0x60FFFFFF);
            return;
        }
        int hell = textCol | 0xFF000000;
        int sp = label.indexOf(' ');
        if (sp > 0) {
            String a = label.substring(0, sp), b = label.substring(sp + 1);
            float bw = HudKarte.breite(a, false) + 3 + HudKarte.breite(b, true);
            float tx = x + (w - bw) / 2f, ty = y + (h - 8) / 2f;
            HudKarte.text(ctx, a, tx, ty, z[0] > 0.5f ? hell : HudKarte.labelFarbe(), false);
            HudKarte.text(ctx, b, tx + HudKarte.breite(a, false) + 3, ty, hell, true);
        } else {
            HudKarte.text(ctx, label, x + (w - HudKarte.breite(label, true)) / 2f, y + (h - 8) / 2f, hell, true);
        }
    }

    /**
     * Scaling helper, shared with the other HUD parts.
     *
     * Package visible rather than private so ArmorWarning uses the very same
     * one -- a second copy of this would drift, and elements would then behave
     * differently in the editor for no reason anyone could see.
     */
    /** The totem icon, built once rather than every frame. */
    private static net.minecraft.world.item.ItemStack totemIcon = null;
    private static int lastTotemCount = Integer.MIN_VALUE;
    private static Component totemCountText = null;

    /** Cached player list, so it is not rebuilt on every frame. */
    private static final java.util.List<String> playerLines = new java.util.ArrayList<>();

    /** When that list was last rebuilt. */
    private static long playerListBuilt = 0L;

    static void pushScale(GuiGraphicsExtractor context, float anchorX, float anchorY, float scale) {
        var m = context.pose();
        m.pushMatrix();
        m.translate(anchorX, anchorY);
        m.scale(scale, scale);
        m.translate(-anchorX, -anchorY);
    }

    static void popScale(GuiGraphicsExtractor context) {
        context.pose().popMatrix();
    }

    /** Macht den ersten Buchstaben jedes Wortes gross ("strength" -> "Strength"). */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (cap && Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
                if (c == ' ') cap = true;
            }
        }
        return sb.toString();
    }

    /** Wandelt 1..n in roemische Zahlen (I, II, III, IV, ...). */
    private static String toRoman(int n) {
        if (n <= 0) return "";
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                n -= values[i];
                sb.append(symbols[i]);
            }
        }
        return sb.toString();
    }
}

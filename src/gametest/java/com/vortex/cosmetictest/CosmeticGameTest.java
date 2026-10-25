package com.vortex.cosmetictest;

import com.vortex.client.cosmetics.ActiveCape;
import com.vortex.client.cosmetics.Cosmetics;
import com.vortex.client.cosmetics.CosmeticsScreen;
import com.vortex.client.cosmetics.EigenesCape;
import com.vortex.client.cosmetics.Huete;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cosmetics im echten Spiel: jeder Hut, ein eigenes Cape-Bild und Partikel,
 * jeweils von hinten und vorne fotografiert. Die Bilder landen im
 * screenshots-Ordner des Testlaufs und werden von Hand angesehen -- ob ein
 * Hut schief sitzt, kann kein Assert pruefen.
 *
 * Start:  ./gradlew runClientGameTest -PcosmeticTests
 */
public class CosmeticGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if ("titel".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { titel(ctx); return; }
        if ("gui".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { guiTest(ctx); return; }
        if ("home".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { homeTest(ctx); return; }
        if ("leistung".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { leistungTest(ctx); return; }
        if ("neu438".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { neu438(ctx); return; }
        if ("schutz".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { schutzTest(ctx); return; }
        if ("glitzer".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { glitzerTest(ctx); return; }
        if ("esc".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { escTest(ctx); return; }
        if ("hud".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { hudDesignTest(ctx); return; }
        if ("ruestung".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { ruestungTest(ctx); return; }
        if ("look".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { lookTest(ctx); return; }
        if ("styles".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { stylesTest(ctx); return; }
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext srv = sp.getServer();
            srv.runCommand("gamemode creative @a");
            srv.runCommand("time set noon");
            srv.runCommand("weather clear 1000000");
            srv.runCommand("tp @a 0.5 ~ 0.5 0 0");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });

            if ("pixel".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { pixel(ctx, srv); return; }
            animiert(ctx, srv);
            if ("anim".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) return;

            for (Huete.Hut h : Huete.alle().values()) {
                waehle(ctx, "", h.id(), "");
                fotos(ctx, "hat-" + h.id());
            }

            Path bild = Path.of(System.getProperty("java.io.tmpdir"), "vortex-cape-test.png");
            ctx.runOnClient(mc -> {
                try {
                    BufferedImage img = new BufferedImage(400, 640, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = img.createGraphics();
                    g.setPaint(new GradientPaint(0, 0, new Color(0x8B5CF6), 400, 640, new Color(0x3B82F6)));
                    g.fillRect(0, 0, 400, 640);
                    g.setColor(new Color(0xE0234E));
                    g.fillRect(0, 0, 400, 70);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 90));
                    g.drawString("V", 160, 380);
                    g.dispose();
                    ImageIO.write(img, "png", bild.toFile());
                } catch (Exception e) { throw new RuntimeException(e); }
                String fehler = EigenesCape.bauen(bild);
                if (fehler != null) throw new AssertionError("Cape picture failed: " + fehler);
            });
            waehle(ctx, EigenesCape.ID, "", "");
            ctx.runOnClient(mc -> ActiveCape.neuLaden());
            ctx.waitTicks(20);
            fotos(ctx, "cape-custom");

            waehle(ctx, "vortex_blue", "", "");
            ctx.waitTicks(60);
            fotos(ctx, "cape-vortex-blue");

            Path seiten = Path.of(System.getProperty("java.io.tmpdir"), "vortex-cape-sides.png");
            ctx.runOnClient(mc -> {
                try {
                    BufferedImage img = new BufferedImage(400, 640, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = img.createGraphics();
                    g.setColor(new Color(0xD02030)); g.fillRect(0, 0, 200, 640);
                    g.setColor(new Color(0x2050D0)); g.fillRect(200, 0, 200, 640);
                    g.setColor(Color.WHITE); g.fillRect(0, 0, 120, 120);
                    g.setColor(Color.YELLOW); g.fillRect(0, 560, 400, 80);
                    g.dispose();
                    ImageIO.write(img, "png", seiten.toFile());
                } catch (Exception e) { throw new RuntimeException(e); }
                String fehler = EigenesCape.bauen(seiten);
                if (fehler != null) throw new AssertionError("Cape picture failed: " + fehler);
            });
            waehle(ctx, EigenesCape.ID, "", "");
            ctx.runOnClient(mc -> ActiveCape.neuLaden());
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> Cosmetics.setzeCapePhysik(false));
            fotos(ctx, "physics-off-vanilla");
            ctx.runOnClient(mc -> Cosmetics.setzeCapePhysik(true));
            fotos(ctx, "physics-on");
            for (boolean an : new boolean[] { false, true }) {
                ctx.runOnClient(mc -> {
                    Cosmetics.setzeCapePhysik(an);
                    CosmeticsScreen sc = new CosmeticsScreen(null);
                    mc.gui.setScreen(sc);
                    sc.vorschauDrehen(90f, 0f);
                });
                ctx.waitTicks(10);
                ctx.takeScreenshot("physics-side-" + (an ? "on" : "off"));
                ctx.runOnClient(mc -> mc.gui.setScreen(null));
            }
            for (boolean an : new boolean[] { false, true }) {
                ctx.runOnClient(mc -> {
                    Cosmetics.setzeCapePhysik(an);
                    CosmeticsScreen sc = new CosmeticsScreen(null);
                    mc.gui.setScreen(sc);
                    sc.vorschauDrehen(90f, 0f);
                });
                for (int i = 0; i < 20; i++) {
                    ctx.runOnClient(mc -> mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.28));
                    ctx.waitTick();
                }
                ctx.takeScreenshot("physics-side-moving-" + (an ? "on" : "off"));
                ctx.runOnClient(mc -> mc.gui.setScreen(null));
                srv.runCommand("tp @a 0.5 ~ 0.5 0 0");
                ctx.waitTicks(20);
            }
            ctx.runOnClient(mc -> Cosmetics.setzeCapePhysik(true));
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.getInput().holdKey(o -> o.keyUp);
            ctx.waitTicks(25);
            ctx.takeScreenshot("physics-walking-1");
            ctx.waitTicks(3);
            ctx.takeScreenshot("physics-walking-2");
            ctx.getInput().releaseKey(o -> o.keyUp);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            srv.runCommand("tp @a 0.5 ~ 0.5 0 0");
            ctx.waitTicks(20);

            waehle(ctx, "", "", "");
            for (String e : com.vortex.client.cosmetics.Emotes.alle().keySet()) {
                ctx.runOnClient(mc -> com.vortex.client.cosmetics.Emotes.spielen(e));
                ctx.waitTicks(16);
                ctx.takeScreenshot("emote-" + e);
                ctx.waitTicks(14);
                ctx.takeScreenshot("emote-" + e + "-b");
                ctx.waitTicks(26);
            }
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); mc.gui.setScreen(new com.vortex.client.cosmetics.EmoteRadScreen()); });
            ctx.waitTicks(10);
            ctx.takeScreenshot("emote-wheel");
            for (int s = 1; s < com.vortex.client.cosmetics.Emotes.SEITEN.length; s++) {
                ctx.runOnClient(mc -> mc.gui.screen().mouseScrolled(0, 0, 0, -1));
                ctx.waitTicks(8);
                ctx.takeScreenshot("emote-wheel-seite-" + s);
            }
            ctx.runOnClient(mc -> { mc.gui.setScreen(null); if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });

            waehle(ctx, EigenesCape.ID, "crown", "hearts");
            ctx.waitTicks(40);
            fotos(ctx, "combo-crown-hearts");
            waehle(ctx, "", "halo", "flames");
            ctx.waitTicks(40);
            fotos(ctx, "combo-halo-flames");

            ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); mc.options.setCameraType(CameraType.FIRST_PERSON); });
            ctx.runOnClient(mc -> mc.gui.setScreen(new CosmeticsScreen(null)));
            ctx.waitTicks(40);
            ctx.takeScreenshot("menu-cosmetics");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
            waehle(ctx, EigenesCape.ID, "crown", "hearts");
            ctx.runOnClient(mc -> ActiveCape.neuLaden());
        }
        ctx.waitTicks(60);
        ctx.takeScreenshot("title-screen");
        ctx.waitTicks(30);
        ctx.takeScreenshot("title-screen-later");
    }

    /**
     * Nur das Hauptmenue: gross, mit der Maus ueber einem Vortex-Knopf und
     * einem Vanilla-Knopf, und im kleinen Standardfenster (854x480).
     */
    private static void titel(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1280, 720);
        ctx.waitTicks(100);
        ctx.takeScreenshot("titel-1280");
        maus(ctx, true);
        ctx.waitTicks(15);
        ctx.takeScreenshot("titel-1280-hover-vortex");
        maus(ctx, false);
        ctx.waitTicks(15);
        ctx.takeScreenshot("titel-1280-hover-vanilla");
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.waitTicks(30);
        ctx.takeScreenshot("titel-1920");
        ctx.getInput().resizeWindow(854, 480);
        ctx.waitTicks(30);
        ctx.takeScreenshot("titel-854");
        ctx.getInput().resizeWindow(640, 480);
        ctx.waitTicks(30);
        ctx.takeScreenshot("titel-640");
        maus(ctx, true);
        ctx.waitTicks(15);
        ctx.takeScreenshot("titel-640-hover-vortex");
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)));
            ctx.waitTicks(30);
            ctx.takeScreenshot("pause-1280");
        }
    }

    /**
     * Schutz gegen Mod-Erkennung: ein Schild mit Mod-Schluesseln, der Editor
     * geht auf -- was wuerde an den Server zurueckgehen? Einmal mit, einmal
     * ohne Schutz. Ergebnis im Log als "SCHUTZ-PRUEFUNG".
     */
    private static void schutzTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext srv = sp.getServer();
            ctx.waitTicks(40);
            srv.runCommand("execute at @p run setblock ~2 ~ ~ oak_sign{front_text:{messages:["
                    + "{translate:\"key.vortexclient.clickgui\"},{keybind:\"key.vortexclient.clickgui\"},"
                    + "{translate:\"menu.singleplayer\"},{keybind:\"key.jump\"}]}}");
            ctx.waitTicks(20);
            for (boolean an : new boolean[]{true, false}) {
                String[] zeilen = ctx.computeOnClient(mc -> {
                    com.vortex.client.core.ClientSettings.INSTANCE.hideModsFromServers.set(an);
                    var pos = mc.player.blockPosition().offset(2, 0, 0);
                    var be = mc.level.getBlockEntity(pos);
                    if (!(be instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)) return new String[]{"kein Schild bei " + pos};
                    var sc = new net.minecraft.client.gui.screens.inventory.SignEditScreen(sign, true, false);
                    try {
                        var f = net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen.class.getDeclaredField("messages");
                        f.setAccessible(true);
                        return ((String[]) f.get(sc)).clone();
                    } catch (Exception e) {
                        return new String[]{"Fehler " + e};
                    }
                });
                System.out.println("SCHUTZ-PRUEFUNG " + (an ? "mit Schutz" : "ohne Schutz") + ": " + String.join(" | ", zeilen));
            }
            ctx.runOnClient(mc -> com.vortex.client.core.ClientSettings.INSTANCE.hideModsFromServers.set(true));
            srv.runCommand("execute at @p run setblock ~2 ~ ~1 oak_sign{front_text:{messages:["
                    + "{translate:\"key.meteor-client.open-gui\"},{keybind:\"key.meteor-client.open-gui\"},"
                    + "{translate:\"key.sodium.options\"},{text:\"normal\"}]}}");
            ctx.waitTicks(20);
            String[] meteor = ctx.computeOnClient(mc -> {
                var be = mc.level.getBlockEntity(mc.player.blockPosition().offset(2, 0, 1));
                if (!(be instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)) return new String[]{"kein Schild"};
                var sc = new net.minecraft.client.gui.screens.inventory.SignEditScreen(sign, true, false);
                try {
                    var f = net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen.class.getDeclaredField("messages");
                    f.setAccessible(true);
                    return ((String[]) f.get(sc)).clone();
                } catch (Exception e) {
                    return new String[]{"Fehler " + e};
                }
            });
            System.out.println("SCHUTZ-PRUEFUNG fremde Schluessel: " + String.join(" | ", meteor));
            String menue = ctx.computeOnClient(mc -> net.minecraft.network.chat.Component.translatable("key.vortexclient.clickgui").getString());
            System.out.println("SCHUTZ-PRUEFUNG danach im Menue: " + menue);
        }
    }

    /** Alle Vortex-Bildschirme, die ohne besondere Daten aufgehen. */
    private static java.util.List<java.util.Map.Entry<String, java.util.function.Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen>>> bildschirme() {
        java.util.List<java.util.Map.Entry<String, java.util.function.Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen>>> l = new java.util.ArrayList<>();
        l.add(java.util.Map.entry("clickgui", p -> new com.vortex.client.gui.ClickGui()));
        l.add(java.util.Map.entry("panelgui", p -> new com.vortex.client.gui.PanelGui()));
        l.add(java.util.Map.entry("home", p -> new com.vortex.client.gui.HomeScreen()));
        l.add(java.util.Map.entry("hud-editor", p -> new com.vortex.client.gui.HudEditorScreen()));
        l.add(java.util.Map.entry("settings", p -> new com.vortex.client.gui.SettingsScreen(p, "Client Settings",
                com.vortex.client.core.ClientSettings.INSTANCE.all())));
        l.add(java.util.Map.entry("colorpicker", p -> new com.vortex.client.gui.ColorPickerScreen(p, com.vortex.client.gui.Theme.INSTANCE.accent)));
        l.add(java.util.Map.entry("waypoints", com.vortex.client.gui.WaypointScreen::new));
        l.add(java.util.Map.entry("macros", com.vortex.client.gui.MacroScreen::new));
        l.add(java.util.Map.entry("friends", com.vortex.client.gui.FriendsScreen::new));
        l.add(java.util.Map.entry("skins", com.vortex.client.gui.SkinScreen::new));
        l.add(java.util.Map.entry("bot", com.vortex.client.gui.BotScreen::new));
        l.add(java.util.Map.entry("community", com.vortex.client.gui.CommunityScreen::new));
        l.add(java.util.Map.entry("presets", com.vortex.client.gui.PresetScreen::new));
        l.add(java.util.Map.entry("cosmetics", com.vortex.client.cosmetics.CosmeticsScreen::new));
        l.add(java.util.Map.entry("music", com.vortex.client.musik.MusikScreen::new));
        l.add(java.util.Map.entry("itemcounter", com.vortex.client.gui.ItemCounterScreen::new));
        l.add(java.util.Map.entry("pictures", com.vortex.client.gui.menu.PicturesScreen::new));
        l.add(java.util.Map.entry("styles", com.vortex.client.gui.StylesScreen::new));
        l.add(java.util.Map.entry("keys", com.vortex.client.gui.KeyListScreen::new));
        l.add(java.util.Map.entry("beta", com.vortex.client.beta.BetaScreen::new));
        l.add(java.util.Map.entry("account", p -> new com.vortex.client.account.AccountScreen()));
        l.add(java.util.Map.entry("host", com.vortex.client.gui.menu.HostScreen::new));
        l.add(java.util.Map.entry("antirender", com.vortex.client.gui.AntiRenderScreen::new));
        l.add(java.util.Map.entry("norenderblocks", com.vortex.client.gui.NoRenderBlocksScreen::new));
        l.add(java.util.Map.entry("emotes", p -> new com.vortex.client.cosmetics.EmoteRadScreen()));
        return l;
    }

    /**
     * Jeder Vortex-Bildschirm in kleinen und grossen Fenstern: Foto plus
     * Liste aller Knoepfe/Felder, die ganz oder teilweise ausserhalb liegen
     * (landet im Log als "GUI-PRUEFUNG").
     */
    private static void guiTest(ClientGameTestContext ctx) {
        String nur = System.getenv("VORTEX_GUI_ONLY");
        int[][] groessen = {{640, 480}, {854, 480}, {1920, 1080}};
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            ctx.waitTicks(40);
            for (int[] g : groessen) {
                ctx.getInput().resizeWindow(g[0], g[1]);
                ctx.waitTicks(10);
                for (var e : bildschirme()) {
                    if (nur != null && !nur.isBlank() && !java.util.Arrays.asList(nur.split(",")).contains(e.getKey())) continue;
                    String name = e.getKey() + "-" + g[0] + "x" + g[1];
                    try {
                        ctx.runOnClient(mc -> mc.gui.setScreen(e.getValue().apply(new net.minecraft.client.gui.screens.PauseScreen(true))));
                        ctx.waitTicks(12);
                        ctx.takeScreenshot(name);
                        String bericht = ctx.computeOnClient(mc -> pruefen(mc));
                        System.out.println("GUI-PRUEFUNG " + name + ": " + bericht);
                    } catch (Throwable t) {
                        System.out.println("GUI-PRUEFUNG " + name + ": FEHLER " + t);
                    }
                    ctx.runOnClient(mc -> mc.gui.setScreen(null));
                    ctx.waitTicks(2);
                }
            }
        }
    }

    private static String pruefen(net.minecraft.client.Minecraft mc) {
        var sc = mc.gui.screen();
        if (sc == null) return "kein Bildschirm";
        int w = sc.width, h = sc.height;
        StringBuilder b = new StringBuilder("gui " + w + "x" + h);
        int raus = 0;
        for (var c : sc.children()) {
            if (!(c instanceof net.minecraft.client.gui.components.AbstractWidget wd) || !wd.visible) continue;
            if (wd.getX() < 0 || wd.getY() < 0 || wd.getX() + wd.getWidth() > w || wd.getY() + wd.getHeight() > h) {
                raus++;
                if (raus <= 6) b.append(" | RAUS '").append(wd.getMessage().getString()).append("' ")
                        .append(wd.getX()).append(',').append(wd.getY()).append(' ').append(wd.getWidth()).append('x').append(wd.getHeight());
            }
        }
        return raus == 0 ? b.append(" ok").toString() : b.append(" | ").append(raus).append(" ausserhalb").toString();
    }

    /** Maus auf den ersten Vortex-Knopf bzw. den ersten Vanilla-Knopf setzen. */
    private static void maus(ClientGameTestContext ctx, boolean vortex) {
        double[] pos = ctx.computeOnClient(mc -> {
            var sc = mc.gui.screen();
            if (sc == null) return new double[]{0, 0};
            double f = mc.getWindow().getGuiScale();
            for (var c : sc.children()) {
                if (!(c instanceof net.minecraft.client.gui.components.AbstractWidget w)) continue;
                boolean istVortex = w instanceof com.vortex.client.gui.menu.TitelSymbol || w instanceof com.vortex.client.gui.menu.NeonKnopf;
                if (istVortex != vortex || (!vortex && w.getWidth() < 60)) continue;
                return new double[]{(w.getX() + w.getWidth() / 2.0) * f, (w.getY() + w.getHeight() / 2.0) * f};
            }
            return new double[]{0, 0};
        });
        ctx.getInput().setCursorPos(pos[0], pos[1]);
    }

    /** Animierte Capes (CapeKunst), Glanz auf Hueten, Song-Karte ueber dem Kopf, Cape-Galerie. */
    private static void animiert(ClientGameTestContext ctx, TestServerContext srv) {
        String[] capes = com.vortex.client.cosmetics.CapeKunst.alle().keySet().toArray(new String[0]);
        for (String c : capes) {
            waehle(ctx, c, "", "");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(12);
            ctx.takeScreenshot("anim-" + c + "-a");
            ctx.waitTicks(14);
            ctx.takeScreenshot("anim-" + c + "-b");
        }
        ctx.runOnClient(mc -> mc.options.fov().set(30));
        for (String c : capes) {
            waehle(ctx, c, "", "");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(10);
            ctx.takeScreenshot("nah-" + c);
        }
        for (String c : new String[] { "anim_vortex--r", "anim_vortex--rsg", "anim_vortex--h4", "anim_carbon--h6", "anim_neon--m" }) {
            waehle(ctx, c, "", "");
            ctx.waitTicks(10);
            ctx.takeScreenshot("stil-" + c);
        }
        ctx.runOnClient(mc -> mc.options.fov().set(70));
        srv.runCommand("time set midnight");
        for (String c : new String[] { "anim_nether", "anim_end", "anim_neon" }) {
            waehle(ctx, c, "crown", "");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(12);
            ctx.takeScreenshot("anim-night-" + c);
        }
        srv.runCommand("item replace entity @a armor.chest with elytra");
        for (String c : new String[] { "anim_vortex_sigil", "anim_neon_lightning", "anim_rift" }) {
            waehle(ctx, c, "", "");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(12);
            ctx.takeScreenshot("elytra-night-" + c);
        }
        srv.runCommand("time set noon");
        ctx.waitTicks(4);
        ctx.takeScreenshot("elytra-day-anim_rift");
        srv.runCommand("item replace entity @a armor.chest with air");
        waehle(ctx, "anim_vortex", "halo", "");
        ctx.getInput().holdKey(o -> o.keyUp);
        ctx.waitTicks(20);
        ctx.takeScreenshot("anim-walking");
        ctx.getInput().releaseKey(o -> o.keyUp);
        srv.runCommand("tp @a 0.5 ~ 0.5 0 0");
        ctx.waitTicks(10);
        for (String h : Huete.alle().keySet()) {
            waehle(ctx, "anim_royal", h, "");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(8);
            ctx.takeScreenshot("hat3d-" + h + "-front");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(6);
            ctx.takeScreenshot("hat3d-" + h + "-back");
        }
        srv.runCommand("time set midnight");
        for (String h : new String[] { "halo", "flame", "headphones" }) {
            waehle(ctx, "anim_vortex", h, "");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(8);
            ctx.takeScreenshot("hat3d-night-" + h);
        }
        srv.runCommand("time set noon");
        ctx.runOnClient(mc -> {
            var m = com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.SpotifyModule.class);
            if (m != null && !m.isEnabled()) m.toggle();
            if (m != null) m.ownAboveHead.set(true);
            try {
                BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setPaint(new GradientPaint(0, 0, new Color(0xFF5E62), 128, 128, new Color(0x2B1055)));
                g.fillRect(0, 0, 128, 128);
                g.setColor(Color.WHITE);
                g.fillOval(34, 34, 60, 60);
                g.dispose();
                var out = new java.io.ByteArrayOutputStream();
                ImageIO.write(img, "png", out);
                com.vortex.client.musik.Cover.testBild("https://i.scdn.co/image/test", out.toByteArray(), 0xFF5E62);
            } catch (Exception e) { throw new RuntimeException(e); }
            long jetzt = System.currentTimeMillis();
            com.vortex.client.musik.MusikDienst.testEigener(new com.vortex.client.musik.Song("t1", "Midnight City Lights", "Vortex Test Band", "Album",
                    "https://i.scdn.co/image/test", 215000, 64000, true, jetzt, ""));
        });
        waehle(ctx, "anim_aurora", "", "");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        ctx.waitTicks(10);
        ctx.takeScreenshot("song-card-cover");
        ctx.runOnClient(mc -> {
            var np = com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.NowPlayingModule.class);
            if (np != null && !np.isEnabled()) np.toggle();
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        });
        ctx.waitTicks(5);
        ctx.takeScreenshot("song-hud-cover");
        ctx.runOnClient(mc -> com.vortex.client.musik.MusikDienst.testEigener(new com.vortex.client.musik.Song("", "Blinding Lights", "The Weeknd", "",
                "", 0, 0, true, System.currentTimeMillis(), "")));
        ctx.waitTicks(200);
        ctx.runOnClient(mc -> {
            var e = com.vortex.client.musik.MusikDienst.eigener();
            System.out.println("[vortex-test] app song cover=" + e.cover() + " dauer=" + e.dauer());
        });
        ctx.takeScreenshot("song-app-itunes");
        ctx.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
        ctx.runOnClient(mc -> com.vortex.client.musik.MusikDienst.testEigener(new com.vortex.client.musik.Song("", "Song From The App", "Some Artist", "",
                "", 0, 0, true, System.currentTimeMillis(), "")));
        ctx.waitTicks(5);
        ctx.takeScreenshot("song-card-nocover");
        ctx.runOnClient(mc -> com.vortex.client.musik.MusikDienst.testEigener(null));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
        ctx.runOnClient(mc -> mc.gui.setScreen(new CosmeticsScreen(null)));
        ctx.waitTicks(30);
        ctx.takeScreenshot("menu-capes-gallery");
        ctx.getInput().resizeWindow(1600, 900);
        ctx.runOnClient(mc -> { mc.options.guiScale().set(2); mc.resizeGui(); Cosmetics.speichern(Cosmetics.eigene().mitCape("anim_vortex--rh3"), false); });
        ctx.runOnClient(mc -> mc.gui.setScreen(new CosmeticsScreen(null)));
        ctx.waitTicks(40);
        ctx.takeScreenshot("menu-capes-gallery-gross");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
    }

    /** Pixel-Cosmetics (4.29): Bandanas, Face, Back, Auras, Shields -- nah (kleines Sichtfeld), gross. */
    private static void pixel(ClientGameTestContext ctx, TestServerContext srv) {
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.runOnClient(mc -> { mc.options.fov().set(30); mc.options.guiScale().set(3); mc.resizeGui(); });
        ctx.waitTicks(10);
        java.util.function.BiConsumer<String, java.util.function.UnaryOperator<Cosmetics.Auswahl>> setze = (n, f) ->
                ctx.runOnClient(mc -> Cosmetics.speichern(f.apply(Cosmetics.Auswahl.LEER), false));
        for (var d : com.vortex.client.cosmetics.Zubehoer.von(com.vortex.client.cosmetics.Zubehoer.Kategorie.HAT)) {
            setze.accept(d.id(), a -> a.mitHut(d.id()));
            ansicht(ctx, "hat-" + d.id() + "-front", CameraType.THIRD_PERSON_FRONT, 20);
            ansicht(ctx, "hat-" + d.id() + "-back", CameraType.THIRD_PERSON_BACK, 30);
        }
        for (var d : com.vortex.client.cosmetics.Zubehoer.von(com.vortex.client.cosmetics.Zubehoer.Kategorie.FACE)) {
            setze.accept(d.id(), a -> a.mitGesicht(d.id()));
            ansicht(ctx, "face-" + d.id() + "-front", CameraType.THIRD_PERSON_FRONT, 0);
            vorschau(ctx, "face-" + d.id() + "-side", 3, 90f);
        }
        for (var d : com.vortex.client.cosmetics.Zubehoer.von(com.vortex.client.cosmetics.Zubehoer.Kategorie.BANDANA)) {
            setze.accept(d.id(), a -> a.mitBandana(d.id()));
            ansicht(ctx, "bandana-" + d.id() + "-front", CameraType.THIRD_PERSON_FRONT, 25);
            ansicht(ctx, "bandana-" + d.id() + "-back", CameraType.THIRD_PERSON_BACK, 20);
            vorschau(ctx, "bandana-" + d.id() + "-side", 2, 90f);
        }
        for (var d : com.vortex.client.cosmetics.Zubehoer.von(com.vortex.client.cosmetics.Zubehoer.Kategorie.BACK)) {
            setze.accept(d.id(), a -> a.mitRuecken(d.id()));
            ansicht(ctx, "back-" + d.id() + "-back", CameraType.THIRD_PERSON_BACK, 0);
            vorschau(ctx, "back-" + d.id() + "-side", 4, 90f);
            vorschau(ctx, "back-" + d.id() + "-angled", 4, 35f);
        }
        for (var d : com.vortex.client.cosmetics.Zubehoer.von(com.vortex.client.cosmetics.Zubehoer.Kategorie.NECK)) {
            setze.accept(d.id(), a -> a.mitRuecken("").mitHals(d.id()));
            ansicht(ctx, "neck-" + d.id() + "-front", CameraType.THIRD_PERSON_FRONT, 15);
            vorschau(ctx, "neck-" + d.id() + "-side", 3, 60f);
        }
        setze.accept("", a -> a.mitHals(""));
        for (String id : new String[] { "angel_wings", "dragon_wings" }) {
            setze.accept(id, a -> a.mitRuecken(id));
            ctx.getInput().holdKey(o -> o.keyShift);
            ctx.waitTicks(30);
            ansicht(ctx, "back-" + id + "-sneak", CameraType.THIRD_PERSON_BACK, 0);
            ctx.getInput().releaseKey(o -> o.keyShift);
            ctx.waitTicks(30);
        }
        setze.accept("rainbow", a -> a.mitNamensstil("rainbow"));
        for (String id : new String[] { "teddy_backpack", "greatsword" }) {
            setze.accept(id, a -> a.mitRuecken(id).mitCape("anim_vortex"));
            ansicht(ctx, "back-" + id + "-cape", CameraType.THIRD_PERSON_BACK, 0);
            vorschau(ctx, "back-" + id + "-cape-side", 4, 90f);
        }
        for (var d : com.vortex.client.cosmetics.Zubehoer.von(com.vortex.client.cosmetics.Zubehoer.Kategorie.AURA)) {
            setze.accept(d.id(), a -> a.mitAura(d.id()));
            ansicht(ctx, "aura-" + d.id() + "-a", CameraType.THIRD_PERSON_FRONT, 10);
            ctx.waitTicks(17);
            ctx.takeScreenshot("aura-" + d.id() + "-b");
        }
        srv.runCommand("item replace entity @a weapon.offhand with minecraft:shield");
        ctx.runOnClient(mc -> com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.ShieldStatusModule.class).setEnabled(false));
        for (var d : com.vortex.client.cosmetics.Zubehoer.von(com.vortex.client.cosmetics.Zubehoer.Kategorie.SHIELD)) {
            setze.accept(d.id(), a -> a.mitSchild(d.id()));
            ctx.getInput().holdKey(o -> o.keyUse);
            ansicht(ctx, "shield-" + d.id() + "-block", CameraType.THIRD_PERSON_FRONT, 0);
            ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.waitTicks(4);
            ansicht(ctx, "shield-" + d.id() + "-hold", CameraType.THIRD_PERSON_FRONT, 0);
            vorschau(ctx, "shield-" + d.id() + "-back", 6, 200f);
        }
        ctx.runOnClient(mc -> com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.ShieldStatusModule.class).setEnabled(true));
        for (String id : new String[] { "vortex", "" }) {
            String n = id.isEmpty() ? "vanilla" : id;
            setze.accept(id, a -> a.mitSchild(id));
            ansicht(ctx, "status-" + n + "-normal", CameraType.THIRD_PERSON_FRONT, -35);
            ctx.getInput().holdKey(o -> o.keyUse);
            ctx.waitTicks(1);
            ctx.takeScreenshot("status-" + n + "-raising");
            ctx.waitTicks(10);
            ctx.takeScreenshot("status-" + n + "-blocking");
            ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.runOnClient(mc -> mc.player.getCooldowns().addCooldown(mc.player.getOffhandItem(), 100));
            ctx.waitTicks(3);
            ctx.takeScreenshot("status-" + n + "-broken");
            ctx.waitTicks(100);
        }
        ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); mc.options.fov().set(70); });
        for (String id : new String[] { "vortex", "sakura" }) {
            setze.accept(id, a -> a.mitSchild(id));
            ansicht(ctx, "shield-" + id + "-firstperson", CameraType.FIRST_PERSON, 0);
            ctx.getInput().holdKey(o -> o.keyUse);
            ctx.waitTicks(8);
            ctx.takeScreenshot("shield-" + id + "-firstperson-block");
            ctx.getInput().releaseKey(o -> o.keyUse);
        }
        ctx.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); mc.options.fov().set(30); });
        setze.accept("combo", a -> a.mitBandana("lightning").mitGesicht("pixel_shades").mitRuecken("teddy_backpack").mitAura("sakura").mitSchild("vortex").mitCape("anim_vortex"));
        ansicht(ctx, "combo-front", CameraType.THIRD_PERSON_FRONT, 25);
        ansicht(ctx, "combo-back", CameraType.THIRD_PERSON_BACK, 25);
        srv.runCommand("time set midnight");
        setze.accept("night", a -> a.mitGesicht("neon_visor").mitRuecken("rocket_pack").mitAura("soul").mitBandana("toxic"));
        ansicht(ctx, "night-front", CameraType.THIRD_PERSON_FRONT, 25);
        ansicht(ctx, "night-back", CameraType.THIRD_PERSON_BACK, 25);
        srv.runCommand("time set noon");
        ctx.runOnClient(mc -> { mc.options.fov().set(70); mc.options.setCameraType(CameraType.FIRST_PERSON); });
        for (int i = 1; i <= 6; i++) {
            final int r = i;
            ctx.runOnClient(mc -> { var s = new CosmeticsScreen(null); mc.gui.setScreen(s); s.zeigeReiter(r); });
            ctx.waitTicks(20);
            ctx.takeScreenshot("menu-tab-" + i);
        }
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
    }

    /** Glitzern (Sparkle), Bandana-Physik und Cape-Wellen (seit 4.35). */
    private static void glitzerTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext srv = sp.getServer();
            srv.runCommand("gamemode creative @a");
            srv.runCommand("time set noon");
            srv.runCommand("weather clear 1000000");
            srv.runCommand("tp @a 0.5 ~ 0.5 0 0");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
            ctx.getInput().resizeWindow(1920, 1080);
            ctx.runOnClient(mc -> { mc.options.fov().set(30); mc.options.guiScale().set(3); mc.resizeGui(); });
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> Cosmetics.speichern(Cosmetics.Auswahl.LEER.mitHut("crown--s").mitBandana("lightning--s").mitGesicht("pixel_shades--s"), false));
            for (int i = 0; i < 4; i++) ansicht(ctx, "glitzer-kopf-" + i, CameraType.THIRD_PERSON_FRONT, 20);
            ctx.runOnClient(mc -> Cosmetics.speichern(Cosmetics.Auswahl.LEER.mitHut("crown").mitBandana("lightning").mitGesicht("pixel_shades"), false));
            ansicht(ctx, "glitzer-kopf-aus", CameraType.THIRD_PERSON_FRONT, 20);
            ctx.runOnClient(mc -> Cosmetics.speichern(Cosmetics.Auswahl.LEER.mitRuecken("teddy_backpack--s").mitSchild("vortex--s"), false));
            srv.runCommand("item replace entity @a weapon.offhand with minecraft:shield");
            for (int i = 0; i < 3; i++) ansicht(ctx, "glitzer-ruecken-" + i, CameraType.THIRD_PERSON_BACK, 0);
            ansicht(ctx, "glitzer-schild", CameraType.THIRD_PERSON_FRONT, -35);
            srv.runCommand("item replace entity @a weapon.offhand with minecraft:air");
            ctx.runOnClient(mc -> Cosmetics.speichern(Cosmetics.Auswahl.LEER.mitBandana("lightning"), false));
            for (boolean an : new boolean[] { true, false }) {
                ctx.runOnClient(mc -> Cosmetics.setzeBandanaPhysik(an));
                String n = an ? "an" : "aus";
                ansicht(ctx, "bandana-physik-" + n + "-steht", CameraType.THIRD_PERSON_BACK, 0);
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
                ctx.getInput().holdKey(o -> o.keySprint);
                ctx.getInput().holdKey(o -> o.keyUp);
                ctx.waitTicks(25);
                ctx.takeScreenshot("bandana-physik-" + n + "-laeuft");
                ctx.getInput().releaseKey(o -> o.keyUp);
                ctx.getInput().releaseKey(o -> o.keySprint);
                ctx.getInput().holdKey(o -> o.keyLeft);
                ctx.waitTicks(20);
                ctx.takeScreenshot("bandana-physik-" + n + "-seitwaerts");
                ctx.getInput().releaseKey(o -> o.keyLeft);
                srv.runCommand("tp @a 0.5 ~ 0.5 0 0");
                ctx.waitTicks(20);
            }
            ctx.runOnClient(mc -> Cosmetics.setzeBandanaPhysik(true));
            ctx.runOnClient(mc -> Cosmetics.speichern(Cosmetics.Auswahl.LEER.mitCape("anim_vortex"), false));
            for (boolean an : new boolean[] { true, false }) {
                ctx.runOnClient(mc -> Cosmetics.setzeCapeWellen(an));
                String n = an ? "an" : "aus";
                ansicht(ctx, "cape-wellen-" + n + "-steht", CameraType.THIRD_PERSON_BACK, 0);
                vorschau(ctx, "cape-wellen-" + n + "-seite", 0, 90f);
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
                ctx.getInput().holdKey(o -> o.keyUp);
                ctx.waitTicks(25);
                ctx.takeScreenshot("cape-wellen-" + n + "-laeuft");
                ctx.getInput().releaseKey(o -> o.keyUp);
                srv.runCommand("tp @a 0.5 ~ 0.5 0 0");
                ctx.waitTicks(20);
            }
            ctx.runOnClient(mc -> Cosmetics.setzeCapeWellen(true));
            for (int r : new int[] { 0, 2 }) {
                final int rr = r;
                ctx.runOnClient(mc -> { var s = new CosmeticsScreen(null); mc.gui.setScreen(s); s.zeigeReiter(rr); });
                ctx.waitTicks(20);
                ctx.takeScreenshot("glitzer-menu-" + r);
            }
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
        }
    }

    /** Menue-Vorschau mit Reiter und Drehwinkel (die Kamera im Spiel folgt der Blickrichtung, Seiten gehen nur hier). */
    private static void vorschau(ClientGameTestContext ctx, String name, int reiter, float grad) {
        ctx.runOnClient(mc -> { var s = new CosmeticsScreen(null); mc.gui.setScreen(s); s.zeigeReiter(reiter); s.vorschauDrehen(grad, 0f); });
        ctx.waitTicks(10);
        ctx.takeScreenshot(name);
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.waitTicks(2);
    }

    private static void ansicht(ClientGameTestContext ctx, String name, CameraType cam, float yaw) {
        ctx.runOnClient(mc -> {
            mc.options.setCameraType(cam);
            mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setYHeadRot(yaw); mc.player.yBodyRot = yaw; mc.player.yBodyRotO = yaw;
            mc.player.setXRot(0);
        });
        ctx.waitTicks(8);
        ctx.takeScreenshot(name);
    }

    private static void waehle(ClientGameTestContext ctx, String cape, String hut, String partikel) {
        ctx.runOnClient(mc -> Cosmetics.speichern(new Cosmetics.Auswahl(cape, hut, partikel, 2), false));
        ctx.waitTicks(5);
    }

    private static void fotos(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(10);
        ctx.takeScreenshot(name + "-back");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        ctx.waitTicks(10);
        ctx.takeScreenshot(name + "-front");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    /** Performance-Module und Potato Mode: Wirkung, Stufen, Wiederherstellen der Optionen. */
    private static void leistungTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext srv = sp.getServer();
            srv.runCommand("gamemode creative @a");
            srv.runCommand("time set noon");
            srv.runCommand("weather clear 1000000");
            srv.runCommand("tp @a 0.5 ~ 0.5 0 0");
            ctx.waitTicks(20);
            srv.runCommand("fill -8 ~ 8 8 ~7 8 stone");
            for (int i = -3; i <= 3; i++) srv.runCommand("summon pig " + (i * 2) + " ~ 14 {NoAI:1b}");
            srv.runCommand("summon pig 2 ~ 4 {NoAI:1b}");
            for (int i = 0; i < 6; i++) srv.runCommand("summon item " + (i - 3) + " ~ 3 {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767,Age:-32768}");
            for (int i = 0; i < 6; i++) srv.runCommand("summon item " + (14 + i) + " ~ " + (30 + i) + " {Item:{id:\"minecraft:gold_ingot\",count:1},PickupDelay:32767,Age:-32768}");
            srv.runCommand("setblock -3 ~ 5 chest");
            srv.runCommand("setblock 12 ~ 60 chest");
            srv.runCommand("setblock 13 ~ 60 chest");
            ctx.waitTicks(60);
            ctx.takeScreenshot("leistung-1-aus");

            StringBuilder log = new StringBuilder();
            String vorher = ctx.computeOnClient(mc -> optionen(mc));
            log.append("vorher: ").append(vorher).append('\n');

            Class<?>[] neu = {com.vortex.client.module.modules.DynamicFpsModule.class,
                    com.vortex.client.module.modules.EntityCullingModule.class,
                    com.vortex.client.module.modules.BlockEntityDistanceModule.class,
                    com.vortex.client.module.modules.ParticleLimiterModule.class,
                    com.vortex.client.module.modules.ItemLimiterModule.class};
            ctx.runOnClient(mc -> {
                for (Class<?> c : neu) {
                    var m = com.vortex.client.module.ModuleManager.INSTANCE.get((Class) c);
                    ((com.vortex.client.module.Module) m).setEnabled(true);
                }
            });
            ctx.waitTicks(30);
            ctx.takeScreenshot("leistung-2-module");
            log.append("module an: ").append((String) ctx.computeOnClient(mc -> pruefe(mc))).append('\n');
            ctx.runOnClient(mc -> {
                for (Class<?> c : neu) {
                    var m = com.vortex.client.module.ModuleManager.INSTANCE.get((Class) c);
                    ((com.vortex.client.module.Module) m).setEnabled(false);
                }
            });
            ctx.waitTicks(10);

            for (String stufe : new String[]{"Balanced", "Aggressive", "Maximum"}) {
                ctx.runOnClient(mc -> {
                    var p = com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.PotatoModeModule.class);
                    p.strength.set(stufe);
                    p.setEnabled(true);
                });
                ctx.waitTicks(40);
                ctx.takeScreenshot("leistung-3-potato-" + stufe.toLowerCase());
                log.append("potato ").append(stufe).append(": ").append((String) ctx.computeOnClient(mc -> optionen(mc)))
                        .append(" | ").append((String) ctx.computeOnClient(mc -> pruefe(mc))).append('\n');
                ctx.runOnClient(mc -> com.vortex.client.module.ModuleManager.INSTANCE
                        .get(com.vortex.client.module.modules.PotatoModeModule.class).setEnabled(false));
                ctx.waitTicks(20);
                String danach = ctx.computeOnClient(mc -> optionen(mc));
                log.append("  danach: ").append(danach).append(danach.equals(vorher) ? "  [OK wiederhergestellt]" : "  [FEHLER]").append('\n');
            }
            ctx.takeScreenshot("leistung-4-zurueck");
            System.out.println("LEISTUNG-ERGEBNIS\n" + log);
            ctx.runOnClient(mc -> {
                try {
                    java.nio.file.Path d = mc.gameDirectory.toPath().resolve("screenshots");
                    java.nio.file.Files.createDirectories(d);
                    java.nio.file.Files.writeString(d.resolve("leistung.txt"), log.toString());
                } catch (Exception e) { System.out.println("LEISTUNG: " + e); }
            });
        }
    }

    private static String optionen(net.minecraft.client.Minecraft mc) {
        var o = mc.options;
        return "rd=" + o.renderDistance().get() + " sim=" + o.simulationDistance().get() + " fps=" + o.framerateLimit().get()
                + " part=" + o.particles().get() + " blur=" + o.menuBackgroundBlurriness().get()
                + " shadows=" + o.entityShadows().get() + " clouds=" + o.cloudStatus().get() + " ent=" + o.entityDistanceScaling().get();
    }

    /** Fragt die Regeln direkt ab: Schwein hinter der Wand, Schwein davor, ferne Items/Truhen, Partikel, FPS. */
    private static String pruefe(net.minecraft.client.Minecraft mc) {
        var cam = mc.gameRenderer.mainCamera().position();
        int hinten = 0, vorne = 0, itemsWeg = 0, itemsNah = 0;
        for (var e : mc.level.entitiesForRendering()) {
            boolean weg = com.vortex.client.util.Leistung.entityVerstecken(e, cam.x, cam.y, cam.z);
            if (e instanceof net.minecraft.world.entity.animal.pig.Pig) {
                if (e.getZ() > 10 && weg) hinten++;
                if (e.getZ() < 6 && !weg) vorne++;
            }
            if (e instanceof net.minecraft.world.entity.item.ItemEntity) {
                if (e.getZ() > 20 && weg) itemsWeg++;
                if (e.getZ() < 6 && !weg) itemsNah++;
            }
        }
        int verworfen = 0;
        for (int i = 0; i < 1000; i++) if (com.vortex.client.util.Leistung.partikelVerwerfen()) verworfen++;
        boolean truheWeit = com.vortex.client.util.Leistung.blockEntityZuWeit(new net.minecraft.core.BlockPos(12, (int) cam.y, 60), cam.x, cam.y, cam.z);
        boolean truheNah = com.vortex.client.util.Leistung.blockEntityZuWeit(new net.minecraft.core.BlockPos(-3, (int) cam.y, 5), cam.x, cam.y, cam.z);
        return "schweine hinter wand versteckt=" + hinten + "/7 davor sichtbar=" + vorne + "/1"
                + " items weit weg=" + itemsWeg + "/6 nah sichtbar=" + itemsNah + "/6"
                + " truhe weit weg=" + truheWeit + " nah weg=" + truheNah
                + " partikel verworfen=" + verworfen + "/1000"
                + " fps(hintergrund)=" + com.vortex.client.util.Leistung.fpsGrenze(260);
    }

    /** Neuer Rechtsshift-Bildschirm: drei Fenstergroessen, mit Favoriten und beim Suchen. */

    private static void stylesTest(ClientGameTestContext ctx) {
        try {
            Path zip = ctx.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("styles-test.zip"));
            stylesTestZip(zip);
            String msg = ctx.computeOnClient(mc -> com.vortex.client.styles.Styles.importieren(zip));
            System.out.println("STYLES-IMPORT: " + msg);
            for (var k : com.vortex.client.styles.Kategorie.values()) {
                long imp = com.vortex.client.styles.Styles.stile(k).stream().filter(st -> st.importiert()).count();
                if (imp > 0) System.out.println("STYLES-KATEGORIE " + k.id + ": " + imp + " importiert");
            }
        } catch (Throwable t) {
            System.out.println("STYLES-IMPORT: FEHLER " + t);
        }
        ctx.getInput().resizeWindow(1280, 720);
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> mc.gui.setScreen(new com.vortex.client.gui.HomeScreen()));
        ctx.waitTicks(20);
        ctx.takeScreenshot("styles-home");
        com.vortex.client.styles.Kategorie[] zeigen = {com.vortex.client.styles.Kategorie.CRYSTAL, com.vortex.client.styles.Kategorie.GLOWSTONE,
                com.vortex.client.styles.Kategorie.DEEPSLATE, com.vortex.client.styles.Kategorie.SWORD, com.vortex.client.styles.Kategorie.GRASS,
                com.vortex.client.styles.Kategorie.FIRE, com.vortex.client.styles.Kategorie.OBSIDIAN, com.vortex.client.styles.Kategorie.INVENTORY};
        for (var k : zeigen) {
            ctx.runOnClient(mc -> {
                var sc = new com.vortex.client.gui.StylesScreen(null);
                sc.zeigeKategorie(k);
                mc.gui.setScreen(sc);
            });
            ctx.waitTicks(30);
            ctx.takeScreenshot("styles-menu-" + k.id);
        }
        ctx.runOnClient(mc -> mc.gui.setScreen(null));

        ctx.runOnClient(mc -> {
            com.vortex.client.styles.Styles.allesZuruecksetzen();
            com.vortex.client.styles.Styles.waehle(com.vortex.client.styles.Kategorie.GLOWSTONE, "imp:test_red");
            com.vortex.client.styles.Styles.waehle(com.vortex.client.styles.Kategorie.OBSIDIAN, "imp:styles_test");
            com.vortex.client.styles.Styles.waehle(com.vortex.client.styles.Kategorie.CRYSTAL, "imp:test_blue");
            com.vortex.client.styles.Styles.waehle(com.vortex.client.styles.Kategorie.DEEPSLATE, "gen:purple");
            com.vortex.client.styles.Styles.waehle(com.vortex.client.styles.Kategorie.STONE, "gen:vortex");
            com.vortex.client.styles.Styles.waehle(com.vortex.client.styles.Kategorie.SWORD, "gen:red");
            com.vortex.client.styles.Styles.waehle(com.vortex.client.styles.Kategorie.GRASS, "gen:pink");
            com.vortex.client.styles.Styles.waehle(com.vortex.client.styles.Kategorie.FIRE, "gen:blue");
            com.vortex.client.styles.Styles.waehle(com.vortex.client.styles.Kategorie.ANCHOR, "gen:lime");
            com.vortex.client.styles.Styles.waehle(com.vortex.client.styles.Kategorie.SNOW, "gen:lavender");
            com.vortex.client.styles.Styles.waehle(com.vortex.client.styles.Kategorie.HOTBAR, "gen:vortex");
            com.vortex.client.styles.Styles.waehle(com.vortex.client.styles.Kategorie.ARMOR, "gen:black");
            com.vortex.client.styles.Styles.setzeEffekt(com.vortex.client.styles.Kategorie.FIRE, "height", "low");
            com.vortex.client.styles.Styles.setzeEffekt(com.vortex.client.styles.Kategorie.OBSIDIAN, "look", "potato");
            com.vortex.client.styles.Styles.setzeEffekt(com.vortex.client.styles.Kategorie.STONE, "look", "edges");
            com.vortex.client.styles.Styles.setzeEffekt(com.vortex.client.styles.Kategorie.CRYSTAL, "frame", "hidden");
            com.vortex.client.styles.Styles.setzeEffekt(com.vortex.client.styles.Kategorie.TOTEM, "pop", "small");
            com.vortex.client.styles.Styles.setzeEffekt(com.vortex.client.styles.Kategorie.INVENTORY, "look", "dark");
        });
        for (var k : new com.vortex.client.styles.Kategorie[] { com.vortex.client.styles.Kategorie.FIRE, com.vortex.client.styles.Kategorie.TOTEM }) {
            ctx.runOnClient(mc -> {
                var sc = new com.vortex.client.gui.StylesScreen(null);
                sc.zeigeKategorie(k);
                mc.gui.setScreen(sc);
            });
            ctx.waitTicks(30);
            ctx.takeScreenshot("styles-effekte-" + k.id);
        }
        ctx.runOnClient(mc -> {
            var sc = new com.vortex.client.gui.StylesScreen(null);
            sc.zeigeKategorie(com.vortex.client.styles.Kategorie.STONE);
            mc.gui.setScreen(sc);
        });
        ctx.waitTicks(30);
        ctx.takeScreenshot("styles-menu-gewaehlt");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        stylesAnwendenUndWarten(ctx, "anwenden", true);
        System.out.println("STYLES-PRUEFUNG: " + ctx.computeOnClient(CosmeticGameTest::stylesPruefen));

        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext srv = sp.getServer();
            srv.runCommand("gamemode creative @a");
            srv.runCommand("time set noon");
            srv.runCommand("weather clear 1000000");
            srv.runCommand("tp @a 0.5 ~ 0.5 0 22");
            ctx.waitTicks(40);
            int y = ctx.computeOnClient(mc -> mc.player.blockPosition().getY());
            srv.runCommand("fill -6 " + y + " 2 6 " + (y + 5) + " 9 air");
            srv.runCommand("fill -6 " + (y - 1) + " 2 6 " + (y - 1) + " 9 grass_block");
            String[] reihe = {"glowstone", "deepslate", "stone", "obsidian", "respawn_anchor[charges=4]", "snow_block", "deepslate_diamond_ore"};
            for (int i = 0; i < reihe.length; i++) srv.runCommand("setblock " + (i - 3) + " " + y + " 4 " + reihe[i]);
            srv.runCommand("setblock -3 " + y + " 6 netherrack");
            srv.runCommand("setblock -3 " + (y + 1) + " 6 fire");
            srv.runCommand("setblock 3 " + y + " 6 iron_ore");
            srv.runCommand("setblock 2 " + y + " 6 crying_obsidian");
            srv.runCommand("summon end_crystal 0.5 " + (y + 1) + " 7.5 {ShowBottom:0b}");
            srv.runCommand("item replace entity @a weapon.mainhand with diamond_sword");
            srv.runCommand("item replace entity @a hotbar.1 with end_crystal");
            srv.runCommand("item replace entity @a hotbar.2 with diamond_chestplate");
            srv.runCommand("item replace entity @a hotbar.3 with netherite_sword");
            ctx.waitTicks(60);
            ctx.takeScreenshot("styles-welt");
            srv.runCommand("tp @a 0.5 ~ 0.5 0 50");
            ctx.waitTicks(20);
            ctx.takeScreenshot("styles-welt-boden");
            ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
            ctx.waitTicks(10);
            ctx.takeScreenshot("styles-inventar-vorher");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
            ctx.runOnClient(mc -> com.vortex.client.styles.Styles.waehle(com.vortex.client.styles.Kategorie.INVENTORY, "gen:purple"));
            stylesAnwendenUndWarten(ctx, "inventar", true);
            ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
            ctx.waitTicks(10);
            ctx.takeScreenshot("styles-inventar-lila");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
            ctx.runOnClient(mc -> com.vortex.client.styles.Styles.allesZuruecksetzen());
            stylesAnwendenUndWarten(ctx, "zuruecksetzen", false);
            System.out.println("STYLES-ZURUECK: aktiv=" + ctx.computeOnClient(mc -> com.vortex.client.styles.Styles.aktiv())
                    + " ordner=" + ctx.computeOnClient(mc -> Files.exists(mc.getResourcePackDirectory().resolve(com.vortex.client.styles.Styles.PACK_ORDNER)))
                    + " glowstone=" + ctx.computeOnClient(mc -> quelle(mc, "textures/block/glowstone.png")));
            ctx.waitTicks(20);
            ctx.takeScreenshot("styles-welt-vanilla");
        }
    }

    private static void stylesAnwendenUndWarten(ClientGameTestContext ctx, String name, boolean an) {
        java.util.concurrent.CompletableFuture<String> f = ctx.computeOnClient(mc -> com.vortex.client.styles.Styles.anwenden());
        for (int i = 0; i < 120 && !f.isDone(); i++) ctx.waitTicks(5);
        System.out.println("STYLES-ANWENDEN " + name + ": " + (f.isDone() ? f.join() : "haengt"));
        for (int i = 0; i < 60; i++) {
            String q = ctx.computeOnClient(mc -> quelle(mc, "textures/block/glowstone.png"));
            if (an == q.contains("Vortex Styles")) break;
            ctx.waitTicks(10);
        }
        ctx.waitTicks(40);
    }

    private static String quelle(net.minecraft.client.Minecraft mc, String pfad) {
        return mc.getResourceManager().getResource(net.minecraft.resources.Identifier.withDefaultNamespace(pfad))
                .map(r -> r.sourcePackId()).orElse("fehlt");
    }

    private static String stylesPruefen(net.minecraft.client.Minecraft mc) {
        StringBuilder b = new StringBuilder();
        b.append("aktiv=").append(com.vortex.client.styles.Styles.aktiv());
        b.append(" packs=").append(mc.options.resourcePacks);
        for (String p : new String[]{"textures/block/glowstone.png", "textures/block/deepslate.png", "textures/item/diamond_sword.png",
                "textures/item/wooden_sword.png", "textures/colormap/grass.png", "textures/block/fire_0.png", "textures/block/fire_0.png.mcmeta",
                "textures/block/obsidian.png", "textures/entity/end_crystal/end_crystal.png", "textures/entity/equipment/humanoid/diamond.png",
                "textures/entity/equipment/humanoid/iron.png"}) {
            b.append(" | ").append(p.substring(p.lastIndexOf('/') + 1)).append('=').append(quelle(mc, p));
        }
        try (var in = mc.getResourceManager().getResource(net.minecraft.resources.Identifier.withDefaultNamespace("textures/block/glowstone.png")).get().open()) {
            BufferedImage i = ImageIO.read(in);
            b.append(" | glowstone-pixel=").append(Integer.toHexString(i.getRGB(1, 1)));
        } catch (Throwable t) {
            b.append(" | glowstone-pixel=FEHLER ").append(t);
        }
        return b.toString();
    }

    /** Test-Sammlung wie ein Mod mit mehreren Packs, dazu ein altes 1.8-Pack in der Wurzel. */
    private static void stylesTestZip(Path zip) throws Exception {
        try (var out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            stylesEintrag(out, "resourcepacks/glowstone_test_red/pack.mcmeta", "{\"pack\":{\"description\":\"t\",\"pack_format\":75}}".getBytes());
            stylesEintrag(out, "resourcepacks/glowstone_test_red/assets/minecraft/textures/block/glowstone.png", stylesBild(16, 16, 0xFFE02020, 0xFFFF8080));
            stylesEintrag(out, "resourcepacks/crystal_test_blue/assets/minecraft/textures/entity/end_crystal/end_crystal.png", stylesBild(64, 32, 0xFF2040E0, 0xFF80C0FF));
            stylesEintrag(out, "assets/minecraft/textures/blocks/obsidian.png", stylesBild(16, 16, 0xFF20A040, 0xFF104020));
            stylesEintrag(out, "assets/minecraft/textures/blocks/dirt.png", stylesBild(16, 16, 0xFF000000, 0xFFFFFFFF));
            stylesEintrag(out, "../../boese.png", stylesBild(16, 16, 0xFF000000, 0xFFFFFFFF));
        }
    }

    private static void stylesEintrag(java.util.zip.ZipOutputStream out, String name, byte[] b) throws Exception {
        out.putNextEntry(new java.util.zip.ZipEntry(name));
        out.write(b);
        out.closeEntry();
    }

    private static byte[] stylesBild(int w, int h, int a, int c) throws Exception {
        BufferedImage i = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) i.setRGB(x, y, ((x / 4 + y / 4) % 2 == 0) ? a : c);
        var o = new java.io.ByteArrayOutputStream();
        ImageIO.write(i, "png", o);
        return o.toByteArray();
    }

    private static void homeTest(ClientGameTestContext ctx) {
        int[][] groessen = {{1920, 1080, 0}, {1920, 1080, 2}, {854, 480, 0}, {640, 480, 0}};
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            ctx.waitTicks(40);
            for (int[] g : groessen) {
                ctx.runOnClient(mc -> { mc.options.guiScale().set(g[2]); });
                ctx.getInput().resizeWindow(g[0], g[1]);
                ctx.waitTicks(10);
                String n = g[0] + "x" + g[1] + (g[2] > 0 ? "-gui" + g[2] : "");
                ctx.runOnClient(mc -> mc.gui.setScreen(new com.vortex.client.gui.HomeScreen()));
                ctx.waitTicks(20);
                ctx.takeScreenshot("home-" + n);
                ctx.runOnClient(mc -> {
                    for (String f : new String[]{"Fullbright", "Zoom", "Keystrokes", "Coordinates"}) {
                        if (!com.vortex.client.gui.GuiState.isFavorite(f)) com.vortex.client.gui.GuiState.toggleFavorite(f);
                    }
                });
                ctx.waitTicks(5);
                ctx.takeScreenshot("home-favoriten-" + n);
                ctx.runOnClient(mc -> {
                    try {
                        var fz = com.vortex.client.gui.HomeScreen.class.getDeclaredField("tastenZeile");
                        fz.setAccessible(true);
                        fz.set(mc.gui.screen(), 4);
                    } catch (Exception e) { System.out.println("HOME-AUSWAHL: " + e); }
                });
                ctx.waitTicks(8);
                ctx.takeScreenshot("home-auswahl-" + n);
                ctx.runOnClient(mc -> mc.gui.setScreen(null));
                ctx.runOnClient(mc -> { for (String f : new String[]{"Fullbright", "Zoom", "Keystrokes", "Coordinates"}) com.vortex.client.gui.GuiState.toggleFavorite(f); });
                ctx.waitTicks(4);
            }
            ctx.runOnClient(mc -> mc.options.guiScale().set(0));
            ctx.getInput().resizeWindow(1920, 1080);
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> mc.gui.setScreen(new com.vortex.client.gui.PanelGui()));
            ctx.waitTicks(20);
            ctx.takeScreenshot("modulmenue");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
            ctx.runOnClient(mc -> mc.options.guiScale().set(0));
            ctx.getInput().resizeWindow(1280, 720);
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> {
                com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.OpponentInfoModule.class).setEnabled(true);
                com.vortex.client.hud.HudProfile.speichern("PvP");
                com.vortex.client.hud.HudProfile.speichern("Building");
                mc.gui.setScreen(new com.vortex.client.gui.HudEditorScreen());
            });
            ctx.waitTicks(20);
            ctx.takeScreenshot("hud-editor");
            ctx.runOnClient(mc -> {
                try {
                    var f = com.vortex.client.gui.HudEditorScreen.class.getDeclaredField("profileOffen");
                    f.setAccessible(true);
                    f.set(mc.gui.screen(), true);
                } catch (Exception e) { System.out.println("HUD-PROFILE: " + e); }
            });
            ctx.waitTicks(5);
            ctx.takeScreenshot("hud-editor-profile");
            boolean geladen = ctx.computeOnClient(mc -> com.vortex.client.hud.HudProfile.laden("PvP"));
            System.out.println("HUD-PROFILE: laden=" + geladen + " liste=" + ctx.computeOnClient(mc -> com.vortex.client.hud.HudProfile.alle()));
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
            ctx.waitTicks(5);
            ctx.takeScreenshot("hud-ingame");
        }
    }

    /** 4.38: Outfits, Cape-Editor, neue Auren, Gegner-Info. */
    /**
     * 4.53: Pausenmenue mit Vanilla-Aufbau + Knopf "Vortex" unter "Back to Game", Vortex-Farben.
     * Cosmetics: halb sichtbare Karte anklicken (seit 4.52).
     */
    private static void escTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            ctx.waitTicks(40);
            int[][] groessen = {{854, 480, 0}, {1920, 1080, 3}, {1280, 720, 2}, {640, 480, 2}, {854, 480, 4}};
            for (int[] g : groessen) {
                ctx.getInput().resizeWindow(g[0], g[1]);
                ctx.runOnClient(mc -> { mc.options.guiScale().set(g[2]); mc.resizeGui(); });
                ctx.waitTicks(8);
                ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)));
                ctx.waitTicks(20);
                ctx.takeScreenshot("pause-" + g[0] + "x" + g[1] + "-s" + g[2]);
                System.out.println("ESC-TEST pause " + g[0] + "x" + g[1] + " scale " + g[2] + ": " + ctx.computeOnClient(mc -> beschreibe(mc.gui.screen())));
                System.out.println("ESC-TEST layout " + g[0] + "x" + g[1] + " scale " + g[2] + ": " + ctx.computeOnClient(mc -> aufbau(mc.gui.screen())));
            }
            ctx.getInput().resizeWindow(1280, 720);
            ctx.runOnClient(mc -> { mc.options.guiScale().set(2); mc.resizeGui(); });
            ctx.waitTicks(8);
            ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)));
            ctx.waitTicks(15);
            for (String ziel : new String[]{"vortex", "menu.options", "menu.returnToMenu"}) {
                double[] pos = ctx.computeOnClient(mc -> knopfMitte(mc, ziel));
                if (pos == null) { System.out.println("ESC-TEST hover " + ziel + ": not found"); continue; }
                ctx.getInput().setCursorPos(pos[0], pos[1]);
                ctx.waitTicks(10);
                ctx.takeScreenshot("pause-hover-" + ziel.replace("menu.", ""));
            }
            double[] v = ctx.computeOnClient(mc -> knopfMitte(mc, "vortex"));
            ctx.getInput().setCursorPos(v[0], v[1]);
            ctx.waitTicks(3);
            ctx.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
            ctx.waitTicks(20);
            System.out.println("ESC-TEST vortex opens: " + ctx.computeOnClient(mc -> beschreibe(mc.gui.screen())));
            ctx.takeScreenshot("rightshift-from-pause");
            ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(10);
            System.out.println("ESC-TEST escape in vortex menu goes to: " + ctx.computeOnClient(mc -> beschreibe(mc.gui.screen()) + " | " + aufbau(mc.gui.screen())));
            double[] o = ctx.computeOnClient(mc -> knopfMitte(mc, "menu.options"));
            ctx.getInput().setCursorPos(o[0], o[1]);
            ctx.waitTicks(3);
            ctx.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
            ctx.waitTicks(10);
            System.out.println("ESC-TEST options opens: " + ctx.computeOnClient(mc -> beschreibe(mc.gui.screen())));
            ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(10);
            System.out.println("ESC-TEST back from options: " + ctx.computeOnClient(mc -> beschreibe(mc.gui.screen()) + " | " + aufbau(mc.gui.screen())));
            ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(6);
            System.out.println("ESC-TEST escape closes: " + ctx.computeOnClient(mc -> beschreibe(mc.gui.screen())));
            ctx.runOnClient(mc -> com.vortex.client.core.ClientSettings.INSTANCE.vortexPauseMenu.set(false));
            ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)));
            ctx.waitTicks(15);
            ctx.takeScreenshot("pause-setting-off");
            System.out.println("ESC-TEST setting off: " + ctx.computeOnClient(mc -> aufbau(mc.gui.screen())));
            ctx.runOnClient(mc -> com.vortex.client.core.ClientSettings.INSTANCE.vortexPauseMenu.set(true));
            ctx.runOnClient(mc -> mc.gui.setScreen(null));

            for (int[] g : new int[][]{{854, 480, 2}, {1280, 720, 3}, {1920, 1080, 4}}) {
                ctx.getInput().resizeWindow(g[0], g[1]);
                ctx.runOnClient(mc -> { mc.options.guiScale().set(g[2]); mc.resizeGui(); });
                ctx.waitTicks(8);
                for (int reiter : new int[]{1, 2, 5}) {
                    for (int obenTeil = 0; obenTeil < 2; obenTeil++) {
                        final boolean oben = obenTeil == 1;
                        ctx.runOnClient(mc -> {
                            var sc = new com.vortex.client.cosmetics.CosmeticsScreen(null);
                            mc.gui.setScreen(sc);
                            sc.zeigeReiter(reiter);
                        });
                        ctx.waitTicks(6);
                        if (oben) {
                            ctx.runOnClient(mc -> { try { var f = mc.gui.screen().getClass().getDeclaredField("scroll"); f.setAccessible(true); f.setFloat(mc.gui.screen(), 37f); } catch (Exception e) { } });
                            ctx.waitTicks(4);
                        }
                        double[] karte = ctx.computeOnClient(mc -> teilKarte(mc, oben));
                        if (karte[2] <= 0) { System.out.println("ESC-TEST cosmetics " + g[0] + " tab " + reiter + (oben ? " top" : " bottom") + ": no partial card"); continue; }
                        String vorher = ctx.computeOnClient(mc -> waehlt(mc.gui.screen()));
                        ctx.getInput().setCursorPos(karte[0], karte[1]);
                        ctx.waitTicks(3);
                        ctx.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
                        ctx.waitTicks(8);
                        String nachher = ctx.computeOnClient(mc -> waehlt(mc.gui.screen()));
                        if (g[0] == 854 && reiter == 2) ctx.takeScreenshot("cosmetics-partial-" + (oben ? "top" : "bottom"));
                        System.out.println("ESC-TEST cosmetics " + g[0] + " tab " + reiter + (oben ? " top" : " bottom") + " (visible " + karte[2]
                                + " of card): before '" + vorher + "' after '" + nachher + "'");
                    }
                }
            }
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
        }
    }

    /** 4.58: "Look" je HUD-Element -- gemischt alt und neu. */
    private static void lookTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext srv = sp.getServer();
            srv.runCommand("gamemode survival @a");
            srv.runCommand("time set noon");
            srv.runCommand("item replace entity @a armor.head with minecraft:diamond_helmet[damage=200]");
            srv.runCommand("item replace entity @a armor.chest with minecraft:netherite_chestplate");
            srv.runCommand("effect give @a minecraft:speed 300 1");
            srv.runCommand("tp @a 0.5 ~ 0.5 30 10");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
            String[][] el = {{"FPS", "6", "6", "Classic"}, {"CPS", "6", "20", "Modern"}, {"Coordinates", "6", "40", "Modern"}, {"Clock", "6", "80", "Classic"},
                    {"TPS", "6", "96", "Auto"}, {"Keystrokes", "6", "250", "Classic"}, {"Module List", "9999", "4", "Classic"}, {"Potion Effects", "120", "6", "Modern"},
                    {"Speedometer", "6", "120", "Modern"}};
            for (String design : new String[]{"Modern", "Classic"}) {
                ctx.runOnClient(mc -> {
                    var d = com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.HudDesignModule.class);
                    d.setEnabled(true); d.style.set(design);
                    for (var m : com.vortex.client.module.ModuleManager.INSTANCE.getModules()) {
                        if (!(m instanceof com.vortex.client.hud.HudElement he)) continue;
                        String[] e = null;
                        for (String[] x : el) if (x[0].equals(m.getName())) e = x;
                        m.setEnabled(e != null);
                        if (e == null) continue;
                        he.hudX().set(Integer.parseInt(e[1])); he.hudY().set(Integer.parseInt(e[2]));
                        for (var st : m.getSettings()) if (st.getName().equals("Look") && st instanceof com.vortex.client.core.setting.ModeSetting ms) ms.set(e[3]);
                    }
                });
                ctx.waitTicks(15);
                ctx.takeScreenshot("look-mixed-design-" + design.toLowerCase());
            }
            System.out.println("LOOK-TEST errors: " + ctx.computeOnClient(mc -> com.vortex.client.core.Errors.summary()));
        }
    }

    /** 4.57: ArmorHUD -- Karte/Classic, Zahlen links/rechts, Zahlengroesse. */
    private static void ruestungTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext srv = sp.getServer();
            srv.runCommand("gamemode survival @a");
            srv.runCommand("time set noon");
            srv.runCommand("item replace entity @a armor.head with minecraft:diamond_helmet[damage=200]");
            srv.runCommand("item replace entity @a armor.chest with minecraft:netherite_chestplate");
            srv.runCommand("item replace entity @a armor.legs with minecraft:diamond_leggings[damage=420]");
            srv.runCommand("item replace entity @a armor.feet with minecraft:iron_boots[damage=180]");
            srv.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword");
            srv.runCommand("tp @a 0.5 ~ 0.5 30 10");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
            String[][] faelle = {{"Card", "Auto", "1.0"}, {"Card", "Left", "1.0"}, {"Card", "Auto", "1.5"}, {"Card", "Left", "0.7"},
                    {"Classic", "Auto", "1.0"}, {"Classic", "Right", "1.0"}, {"Classic", "Left", "1.6"}, {"Classic", "Right", "0.7"}};
            for (String[] f : faelle) {
                ctx.runOnClient(mc -> {
                    var m = com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.ArmorHudModule.class);
                    m.setEnabled(true);
                    m.look.set(f[0]);
                    m.numberSide.set(f[1]);
                    m.numberSize.set(Double.parseDouble(f[2]));
                });
                ctx.waitTicks(4);
                ctx.takeScreenshot("armor-" + f[0] + "-" + f[1] + "-" + f[2]);
            }
            System.out.println("ARMOR-TEST errors: " + ctx.computeOnClient(mc -> com.vortex.client.core.Errors.summary()));
        }
    }

    /** 4.56: HUD Design -- alle HUD-Elemente, einmal "Classic", einmal "Modern". */
    private static void hudDesignTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext srv = sp.getServer();
            srv.runCommand("gamemode survival @a");
            srv.runCommand("time set noon");
            srv.runCommand("weather clear 1000000");
            srv.runCommand("item replace entity @a armor.head with minecraft:diamond_helmet");
            srv.runCommand("item replace entity @a armor.chest with minecraft:netherite_chestplate");
            srv.runCommand("item replace entity @a armor.legs with minecraft:diamond_leggings");
            srv.runCommand("item replace entity @a armor.feet with minecraft:iron_boots");
            srv.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword");
            srv.runCommand("item replace entity @a hotbar.1 with minecraft:ender_pearl 16");
            srv.runCommand("item replace entity @a weapon.offhand with minecraft:totem_of_undying");
            srv.runCommand("give @a minecraft:totem_of_undying 5");
            srv.runCommand("effect give @a minecraft:speed 300 1");
            srv.runCommand("effect give @a minecraft:strength 120 0");
            srv.runCommand("effect give @a minecraft:fire_resistance 600 0");
            srv.runCommand("tp @a 0.5 ~ 0.5 30 10");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.player.getCooldowns().addCooldown(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL), 400));
            java.util.List<String> namen = ctx.computeOnClient(mc -> {
                java.util.List<String> l = new java.util.ArrayList<>();
                for (var m : com.vortex.client.module.ModuleManager.INSTANCE.getModules()) {
                    if (m instanceof com.vortex.client.hud.HudElement || m.getName().equals("ArmorHUD")) { m.setEnabled(true); l.add(m.getName()); }
                }
                for (var m : com.vortex.client.module.ModuleManager.INSTANCE.getModules())
                    if (m.getName().equals("Fullbright") || m.getName().equals("Zoom") || m.getName().equals("Freelook")) m.setEnabled(true);
                return l;
            });
            System.out.println("HUD-TEST elements: " + namen);
            for (int[] g : new int[][]{{1280, 720, 2}, {1920, 1080, 3}}) {
                ctx.getInput().resizeWindow(g[0], g[1]);
                ctx.runOnClient(mc -> { mc.options.guiScale().set(g[2]); mc.resizeGui(); });
                ctx.waitTicks(10);
                for (String stil : new String[]{"Classic", "Modern"}) {
                    ctx.runOnClient(mc -> {
                        var d = com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.HudDesignModule.class);
                        d.setEnabled(true);
                        d.style.set(stil);
                    });
                    ctx.waitTicks(6);
                    ctx.runOnClient(mc -> {
                        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
                        int x = 4, y = 4, spalteB = 0;
                        for (var m : com.vortex.client.module.ModuleManager.INSTANCE.getModules()) {
                            if (!(m instanceof com.vortex.client.hud.HudElement he) || !m.isEnabled()) continue;
                            if (m.getName().equals("Module List")) { he.hudX().set(sw); he.hudY().set(2); continue; }
                            int w = Math.round(he.hudWidth() * he.hudScale().getFloat()), h = Math.round(he.hudHeight() * he.hudScale().getFloat());
                            if (y + h > sh - 30 && y > 4) { x += spalteB + 6; y = 4; spalteB = 0; }
                            he.hudX().set(x); he.hudY().set(y);
                            y += h + 4;
                            spalteB = Math.max(spalteB, w);
                        }
                    });
                    ctx.waitTicks(4);
                    ctx.runOnClient(mc -> {
                        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
                        int x = 4, y = 4, spalteB = 0;
                        for (var m : com.vortex.client.module.ModuleManager.INSTANCE.getModules()) {
                            if (!(m instanceof com.vortex.client.hud.HudElement he) || !m.isEnabled() || m.getName().equals("Module List")) continue;
                            int w = Math.round(he.hudWidth() * he.hudScale().getFloat()), h = Math.round(he.hudHeight() * he.hudScale().getFloat());
                            if (y + h > sh - 30 && y > 4) { x += spalteB + 6; y = 4; spalteB = 0; }
                            he.hudX().set(x); he.hudY().set(y);
                            y += h + 4;
                            spalteB = Math.max(spalteB, w);
                        }
                    });
                    ctx.waitTicks(10);
                    ctx.takeScreenshot("hud-" + stil.toLowerCase() + "-" + g[0]);
                }
            }
            ctx.getInput().resizeWindow(1280, 720);
            ctx.runOnClient(mc -> { mc.options.guiScale().set(2); mc.resizeGui(); mc.gui.hud.getChat().clearMessages(false); });
            ctx.waitTicks(5);
            for (String name : namen) {
                for (String stil : new String[]{"Classic", "Modern"}) {
                    ctx.runOnClient(mc -> {
                        var d = com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.HudDesignModule.class);
                        d.style.set(stil);
                        for (var m : com.vortex.client.module.ModuleManager.INSTANCE.getModules()) {
                            if (m.getName().equals("ArmorHUD")) m.setEnabled(m.getName().equals(name));
                            if (!(m instanceof com.vortex.client.hud.HudElement he)) continue;
                            m.setEnabled(m.getName().equals(name));
                            if (m.getName().equals(name)) { he.hudX().set(10); he.hudY().set(10); }
                        }
                    });
                    ctx.waitTicks(4);
                    ctx.takeScreenshot("el-" + name.replace(' ', '_') + "-" + stil.toLowerCase());
                }
            }
            java.util.Map<String, int[]> lage = new java.util.LinkedHashMap<>();
            lage.put("Info Bar", new int[]{6, 6}); lage.put("Coordinates", new int[]{6, 31}); lage.put("Potion Effects", new int[]{6, 72});
            lage.put("Compass Bar", new int[]{-1, 4}); lage.put("Module List", new int[]{9999, 4}); lage.put("Keystrokes", new int[]{6, 250});
            lage.put("Speedometer", new int[]{6, 200}); lage.put("Reach Display", new int[]{6, 222}); lage.put("TPS", new int[]{60, 200}); lage.put("ArmorHUD", new int[]{0, 0});
            ctx.runOnClient(mc -> {
                mc.gui.hud.getChat().clearMessages(false);
                var d = com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.HudDesignModule.class);
                d.style.set("Modern");
                for (var m : com.vortex.client.module.ModuleManager.INSTANCE.getModules()) {
                    int[] l = lage.get(m.getName());
                    if (!(m instanceof com.vortex.client.hud.HudElement) && !m.getName().equals("ArmorHUD")) continue;
                    m.setEnabled(l != null);
                    if (l != null && m instanceof com.vortex.client.hud.HudElement he && l[0] >= 0) { he.hudX().set(l[0]); he.hudY().set(l[1]); }
                }
            });
            ctx.waitTicks(20);
            ctx.takeScreenshot("hud-modern-layout");
            System.out.println("HUD-TEST errors: " + ctx.computeOnClient(mc -> com.vortex.client.core.Errors.summary()));
        }
    }

    private static String beschreibe(net.minecraft.client.gui.screens.Screen sc) {
        if (sc == null) return "null";
        String n = sc.getClass().getSimpleName();
        if (sc instanceof com.vortex.client.gui.HomeScreen h) n += h.ausPause() ? " (from pause menu)" : " (right shift)";
        return n + (sc.isPauseScreen() ? ", pauses" : "");
    }

    /** Knoepfe des Bildschirms: Name@x,y,b,h; prueft Ueberlappung und Rand. */
    private static String aufbau(net.minecraft.client.gui.screens.Screen sc) {
        if (sc == null) return "no screen";
        StringBuilder b = new StringBuilder();
        java.util.List<net.minecraft.client.gui.components.AbstractWidget> l = new java.util.ArrayList<>();
        for (var c : sc.children()) if (c instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible) l.add(w);
        l.sort(java.util.Comparator.comparingInt((net.minecraft.client.gui.components.AbstractWidget w) -> w.getY()).thenComparingInt(w -> w.getX()));
        int fehler = 0;
        for (var w : l) {
            String n = w.getMessage().getString();
            b.append(n.length() > 14 ? n.substring(0, 14) : n).append('@').append(w.getX()).append(',').append(w.getY())
                    .append(' ').append(w.getWidth()).append('x').append(w.getHeight()).append("; ");
            if (w.getX() < 0 || w.getY() < 0 || w.getX() + w.getWidth() > sc.width || w.getY() + w.getHeight() > sc.height) fehler++;
            for (var u : l) if (u != w && u.getX() < w.getX() + w.getWidth() && w.getX() < u.getX() + u.getWidth()
                    && u.getY() < w.getY() + w.getHeight() && w.getY() < u.getY() + u.getHeight()) fehler++;
        }
        return "screen " + sc.width + "x" + sc.height + ", problems " + fehler + ": " + b;
    }

    /** Mitte eines Knopfs in Fensterpixeln ("vortex" = der Vortex-Knopf, sonst Sprachschluessel). */
    private static double[] knopfMitte(net.minecraft.client.Minecraft mc, String ziel) {
        var sc = mc.gui.screen();
        if (sc == null) return null;
        double s = mc.getWindow().getGuiScale();
        for (var c : sc.children()) {
            if (!(c instanceof net.minecraft.client.gui.components.AbstractWidget w)) continue;
            boolean passt = ziel.equals("vortex") ? w instanceof com.vortex.client.gui.menu.PauseMenu.VortexKnopf
                    : ziel.equals(com.vortex.client.gui.menu.PauseMenu.schluessel(w));
            if (passt) return new double[]{(w.getX() + w.getWidth() / 2.0) * s, (w.getY() + w.getHeight() / 2.0) * s};
        }
        return null;
    }

    /** Teilweise sichtbare Karte: unten (oben=false) oder oben (oben=true); {x, y in Pixeln, sichtbare Hoehe}. */
    private static double[] teilKarte(net.minecraft.client.Minecraft mc, boolean oben) {
        try {
            var sc = mc.gui.screen();
            var f = sc.getClass().getDeclaredField("flaechen"); f.setAccessible(true);
            java.util.List<?> l = (java.util.List<?>) f.get(sc);
            double bx = 0, by = oben ? Double.MAX_VALUE : -1, bh = 0;
            java.util.Map<Integer, Integer> breiten = new java.util.HashMap<>();
            for (Object o : l) {
                var mw0 = o.getClass().getDeclaredMethod("w"); mw0.setAccessible(true);
                int w0 = (int) mw0.invoke(o);
                if (w0 > 40) breiten.merge(w0, 1, Integer::sum);
            }
            int kartenBreite = breiten.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(-1);
            for (Object o : l) {
                var cl = o.getClass();
                var mx = cl.getDeclaredMethod("x"); var my = cl.getDeclaredMethod("y"); var mw = cl.getDeclaredMethod("w"); var mh = cl.getDeclaredMethod("h");
                for (var m : new java.lang.reflect.Method[]{mx, my, mw, mh}) m.setAccessible(true);
                int x = (int) mx.invoke(o), y = (int) my.invoke(o), w = (int) mw.invoke(o), h = (int) mh.invoke(o);
                if (w != kartenBreite) continue;
                if (oben ? y < by : y > by) { by = y; bx = x + w / 2.0; bh = h; }
            }
            double fk = mc.getWindow().getGuiScale();
            if (by < 0 || by == Double.MAX_VALUE) return new double[]{0, 0, 0};
            return new double[]{bx * fk, (by + Math.max(1, bh / 2.0)) * fk, bh};
        } catch (Exception e) { System.out.println("ESC-TEST reflect " + e); return new double[]{0, 0, 0}; }
    }

    private static String waehlt(net.minecraft.client.gui.screens.Screen sc) {
        try {
            var m = sc.getClass().getDeclaredMethod("gewaehlt");
            m.setAccessible(true);
            var f = sc.getClass().getDeclaredField("scroll"); f.setAccessible(true);
            return m.invoke(sc) + " (scroll " + f.getFloat(sc) + ")";
        } catch (Exception e) { return "? " + e; }
    }

    private static void neu438(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext srv = sp.getServer();
            srv.runCommand("gamemode survival @a");
            srv.runCommand("time set noon");
            srv.runCommand("weather clear 1000000");
            srv.runCommand("difficulty peaceful");
            srv.runCommand("tp @a 0.5 ~ 0.5 0 0");
            ctx.waitTicks(40);
            ctx.getInput().resizeWindow(1920, 1080);
            ctx.runOnClient(mc -> { mc.options.guiScale().set(3); mc.resizeGui(); });
            ctx.waitTicks(10);

            ctx.runOnClient(mc -> {
                Cosmetics.speichern(Cosmetics.Auswahl.LEER.mitHut("crown").mitAura("galaxy"), false);
                com.vortex.client.cosmetics.Outfits.speichern("Galaxy King");
                Cosmetics.speichern(Cosmetics.Auswahl.LEER.mitAura("fireflies").mitBandana("lightning"), false);
                com.vortex.client.cosmetics.Outfits.speichern("Night Walk");
                var sc = new com.vortex.client.cosmetics.CosmeticsScreen(null);
                mc.gui.setScreen(sc);
                try {
                    var f = sc.getClass().getDeclaredField("reiter");
                    f.setAccessible(true);
                    for (Object o : f.getType().getEnumConstants()) if (o.toString().equals("OUTFITS")) f.set(sc, o);
                } catch (Exception e) { System.out.println("NEU438: " + e); }
            });
            ctx.waitTicks(20);
            ctx.takeScreenshot("outfits");
            boolean angezogen = ctx.computeOnClient(mc -> {
                var l = com.vortex.client.cosmetics.Outfits.alle();
                for (var o : l) if (o.name().equals("Galaxy King")) { com.vortex.client.cosmetics.Outfits.anziehen(o); }
                return Cosmetics.eigene().hut().equals("crown") && Cosmetics.eigene().partikel().equals("galaxy");
            });
            System.out.println("NEU438: outfit angezogen=" + angezogen);

            ctx.runOnClient(mc -> mc.gui.setScreen(new com.vortex.client.cosmetics.CapeEditorScreen(null)));
            ctx.waitTicks(20);
            ctx.takeScreenshot("cape-editor");
            ctx.runOnClient(mc -> {
                try {
                    Class<?> k = com.vortex.client.cosmetics.CapeEditorScreen.class;
                    var sc = mc.gui.screen();
                    java.lang.reflect.Field f = k.getDeclaredField("muster"); f.setAccessible(true); f.setInt(null, 2);
                    f = k.getDeclaredField("text"); f.setAccessible(true); ((com.vortex.client.core.setting.StringSetting) f.get(null)).set("VORTEX");
                    f = k.getDeclaredField("schmutzig"); f.setAccessible(true); f.setBoolean(sc, true);
                    java.lang.reflect.Field fx = k.getDeclaredField("cx"), fy = k.getDeclaredField("cy"), fw = k.getDeclaredField("cw"), fh = k.getDeclaredField("ch");
                    for (var x : new java.lang.reflect.Field[]{fx, fy, fw, fh}) x.setAccessible(true);
                    int cx = fx.getInt(sc), cy = fy.getInt(sc), cw = fw.getInt(sc), ch = fh.getInt(sc);
                    var m = k.getDeclaredMethod("malen", double.class, double.class, boolean.class);
                    m.setAccessible(true);
                    for (int i = 0; i < 30; i++) m.invoke(sc, cx + cw * (0.15 + i * 0.023), cy + ch * (0.08 + i * 0.01), i == 0);
                } catch (Exception e) { System.out.println("NEU438: malen " + e); }
            });
            ctx.waitTicks(10);
            ctx.takeScreenshot("cape-editor-bemalt");
            ctx.getInput().resizeWindow(854, 480);
            ctx.runOnClient(mc -> { mc.options.guiScale().set(0); mc.resizeGui(); });
            ctx.waitTicks(10);
            ctx.takeScreenshot("cape-editor-854");
            ctx.getInput().resizeWindow(1920, 1080);
            ctx.runOnClient(mc -> { mc.options.guiScale().set(3); mc.resizeGui(); });
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> {
                try {
                    var m = com.vortex.client.cosmetics.CapeEditorScreen.class.getDeclaredMethod("speichernUndTragen");
                    m.setAccessible(true);
                    m.invoke(mc.gui.screen());
                } catch (Exception e) { System.out.println("NEU438: " + e); }
                mc.gui.setScreen(null);
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.options.fov().set(30);
            });
            ctx.waitTicks(20);
            System.out.println("NEU438: cape=" + ctx.computeOnClient(mc -> Cosmetics.eigene().cape()));
            ansicht(ctx, "cape-editor-am-spieler", CameraType.THIRD_PERSON_BACK, 0);

            for (String a : new String[]{"galaxy", "halo", "tornado", "fireflies", "atom", "rain_cloud", "embers", "void", "gold_coins", "ice"}) {
                ctx.runOnClient(mc -> Cosmetics.speichern(Cosmetics.Auswahl.LEER.mitAura(a), false));
                ctx.waitTicks(30);
                ansicht(ctx, "aura-" + a, CameraType.THIRD_PERSON_FRONT, 20);
            }
            ctx.runOnClient(mc -> { Cosmetics.speichern(Cosmetics.Auswahl.LEER, false); mc.options.setCameraType(CameraType.FIRST_PERSON); if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); mc.options.fov().set(70); });

            srv.runCommand("difficulty easy");
            srv.runCommand("execute at @p run summon minecraft:zombie ~ ~ ~3 {NoAI:1b,Health:14f,ArmorItems:[{id:\"minecraft:diamond_boots\",count:1},{},{id:\"minecraft:iron_chestplate\",count:1},{id:\"minecraft:golden_helmet\",count:1}],HandItems:[{id:\"minecraft:iron_sword\",count:1},{}]}");
            srv.runCommand("item replace entity @e[type=zombie,limit=1] armor.head with minecraft:golden_helmet");
            srv.runCommand("item replace entity @e[type=zombie,limit=1] armor.chest with minecraft:iron_chestplate");
            srv.runCommand("item replace entity @e[type=zombie,limit=1] armor.feet with minecraft:diamond_boots");
            srv.runCommand("item replace entity @e[type=zombie,limit=1] weapon.mainhand with minecraft:iron_sword");
            srv.runCommand("effect give @e[type=zombie] minecraft:absorption 60 1");
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> {
                var m = (com.vortex.client.module.modules.OpponentInfoModule) com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.OpponentInfoModule.class);
                m.playersOnly.set(false);
                m.x.set(20); m.y.set(60);
                m.setEnabled(true);
                for (var e : mc.level.entitiesForRendering()) {
                    if (e instanceof net.minecraft.world.entity.monster.zombie.Zombie z) { mc.gameMode.attack(mc.player, z); break; }
                }
            });
            ctx.waitTicks(20);
            ctx.takeScreenshot("gegner-info");
        }
    }
}

package com.vortex.client.cosmetics;

import com.mojang.blaze3d.platform.InputConstants;
import com.vortex.client.gui.VortexStyle;
import com.vortex.client.gui.glatt.Glatt;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Emote-Rad (Taste B, seit 4.68 neu).
 *
 *   - 4 Seiten mit je 8 Emotes (Greet, Dance, Fun, Taunt). Mausrad oder B nochmal:
 *     naechste Seite, Mausrad zurueck: vorige.
 *   - Zeigen und klicken spielt das Emote -- oder B gedrueckt halten, auf ein Emote
 *     zeigen und loslassen. Zahlen 1-8 spielen direkt.
 *   - In der Mitte deine Figur, die das Emote unter der Maus vorfuehrt.
 *   - Alles weich: Aufgehen, Seitenwechsel, Hervorheben und der Zeiger folgen der Maus
 *     mit sanftem Nachlauf statt zu springen.
 *
 * Esc, Rechtsklick oder ein Klick in die Mitte schliesst.
 */
public class EmoteRadScreen extends Screen {
    private static int letzteSeite;

    private int seite = letzteSeite;
    private List<Emotes.Emote> liste = Emotes.seite(seite);
    private int gezeigt = -1;
    private final float[] hervor = new float[8];
    private float zeiger = Float.NaN, zeigerSicht;
    private final long offenSeit = System.currentTimeMillis();
    private long seitenWechsel = 0;
    private int richtung = 1;
    private long letztesBild;
    /** War die Emote-Taste beim Oeffnen gedrueckt? Dann spielt Loslassen das gezeigte Emote. */
    private boolean gehalten;

    public EmoteRadScreen() {
        super(Component.literal("Emotes"));
        gehalten = tasteUnten();
    }

    private static KeyMapping taste() { return com.vortex.client.VortexClientMod.emoteKeyMapping(); }

    private boolean tasteUnten() {
        try {
            KeyMapping k = taste();
            if (k == null) return false;
            InputConstants.Key key = InputConstants.getKey(k.saveString());
            return key.getType() == InputConstants.Type.KEYSYM && key.getValue() > 0
                    && InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), key.getValue());
        } catch (Throwable t) {
            return false;
        }
    }

    private float radius() { return Math.max(92f, Math.min(this.width, this.height) * 0.3f); }

    /** Welches Emote liegt in Richtung der Maus? -1 = Mitte. */
    private int unterMaus(double mx, double my) {
        double dx = mx - this.width / 2.0, dy = my - this.height / 2.0;
        if (dx * dx + dy * dy < 46 * 46 || liste.isEmpty()) return -1;
        double winkel = Math.atan2(dx, -dy);
        if (winkel < 0) winkel += Math.PI * 2;
        double stueck = Math.PI * 2 / liste.size();
        return (int) Math.floor((winkel + stueck / 2) / stueck) % liste.size();
    }

    private void blaettern(int um) {
        seite = Math.floorMod(seite + um, Emotes.SEITEN.length);
        letzteSeite = seite;
        liste = Emotes.seite(seite);
        richtung = um >= 0 ? 1 : -1;
        seitenWechsel = System.currentTimeMillis();
        java.util.Arrays.fill(hervor, 0f);
    }

    private static float glatt(float v) {
        v = Math.max(0f, Math.min(1f, v));
        return v * v * (3f - 2f * v);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        long jetzt = System.currentTimeMillis();
        float dt = letztesBild == 0 ? 0f : Math.min(0.1f, (jetzt - letztesBild) / 1000f);
        letztesBild = jetzt;
        float auf = glatt((jetzt - offenSeit) / 180f);
        float wechsel = seitenWechsel == 0 ? 1f : glatt((jetzt - seitenWechsel) / 220f);
        float folge = 1f - (float) Math.exp(-dt * 16f);

        if (gehalten && !tasteUnten()) {
            gehalten = false;
            if (jetzt - offenSeit > 250 && gezeigt >= 0) { abspielen(gezeigt); return; }
        }

        float cx = this.width / 2f, cy = this.height / 2f, r = radius() * (0.86f + 0.14f * auf);
        gezeigt = unterMaus(mouseX, mouseY);
        Emotes.vorschau(gezeigt >= 0 ? liste.get(gezeigt).id() : null);

        g.fill(0, 0, this.width, this.height, Glatt.alpha(0xA0060409, auf));
        Glatt.licht(g, cx, cy, r + 70, Glatt.alpha(VortexStyle.VIOLETT, 0.16f * auf));
        Glatt.kreis(g, cx, cy, r * 2 + 76, Glatt.alpha(0xD00E0B16, auf));
        Glatt.kreis(g, cx, cy, r * 2 + 2, Glatt.alpha(0x40A78BFA, auf));
        Glatt.kreis(g, cx, cy, r * 2 - 2, Glatt.alpha(0xF00E0B16, auf));

        double mdx = mouseX - cx, mdy = mouseY - cy;
        boolean aussen = mdx * mdx + mdy * mdy >= 46 * 46;
        if (aussen) {
            float ziel = (float) Math.atan2(mdx, -mdy);
            if (Float.isNaN(zeiger)) zeiger = ziel;
            float d = ziel - zeiger;
            while (d > Math.PI) d -= (float) (Math.PI * 2);
            while (d < -Math.PI) d += (float) (Math.PI * 2);
            zeiger += d * folge;
        }
        zeigerSicht += ((aussen ? 1f : 0f) - zeigerSicht) * folge;
        if (!Float.isNaN(zeiger) && zeigerSicht > 0.02f) {
            for (int i = 0; i < 6; i++) {
                float w = zeiger + (i - 2.5f) * 0.035f;
                float px = cx + (float) Math.sin(w) * r, py = cy - (float) Math.cos(w) * r;
                Glatt.kreis(g, px, py, 3.2f, Glatt.alpha(0xFFC4B5FD, zeigerSicht * auf * (1f - Math.abs(i - 2.5f) / 3.2f)));
            }
        }

        int n = liste.size();
        float versatz = (1f - wechsel) * 0.45f * richtung;
        for (int i = 0; i < n; i++) {
            hervor[i] += ((i == gezeigt ? 1f : 0f) - hervor[i]) * folge;
            float h = hervor[i];
            double w = Math.PI * 2 * i / n + versatz;
            float rr = r + 6f * h;
            float x = cx + (float) Math.sin(w) * rr, y = cy - (float) Math.cos(w) * rr;
            float a = auf * wechsel;
            if (h > 0.02f) Glatt.licht(g, x, y, 34 + 10 * h, Glatt.alpha(VortexStyle.VIOLETT, 0.5f * h * a));
            Glatt.kreis(g, x, y, 62 + 10 * h, Glatt.alpha(Glatt.mix(0xFF2A2440, 0xFFA78BFA, h), a));
            Glatt.kreis(g, x, y, 58 + 10 * h, Glatt.alpha(Glatt.mix(0xFF16121F, 0xFF2A1F4A, h), a));
            String name = Glatt.kuerzen(liste.get(i).name(), 56, Glatt.Schrift.FETT);
            Glatt.textMitte(g, String.valueOf(i + 1), x, y - 12, Glatt.alpha(0xFF8D86A6, a * (0.6f + 0.4f * h)), Glatt.Schrift.NORMAL);
            Glatt.textMitte(g, name, x, y - 2, Glatt.alpha(Glatt.mix(0xFFC9C3DC, 0xFFFFFFFF, h), a), Glatt.Schrift.FETT);
        }

        Glatt.kreis(g, cx, cy, 104, Glatt.alpha(0xF0181424, auf));
        Glatt.kreis(g, cx, cy, 100, Glatt.alpha(0xFF120E1B, auf));
        if (!figur(g, cx, cy)) {
            Glatt.textMitte(g, gezeigt >= 0 ? liste.get(gezeigt).name() : "Emotes", cx, cy - 4, Glatt.alpha(0xFFF2F0F8, auf), Glatt.Schrift.FETT);
        }

        float oben = cy - r - 52;
        String titel = Emotes.SEITEN[seite];
        Glatt.textMitte(g, "‹   " + titel + "   ›", cx, oben, Glatt.alpha(0xFFF2F0F8, auf), Glatt.Schrift.FETT);
        Glatt.textMitte(g, gezeigt >= 0 ? liste.get(gezeigt).name() : "Point at an emote", cx, oben + 12, Glatt.alpha(0xFFB9B2CF, auf), Glatt.Schrift.NORMAL);
        float unten = cy + r + 44;
        int seiten = Emotes.SEITEN.length;
        for (int i = 0; i < seiten; i++) {
            float px = cx + (i - (seiten - 1) / 2f) * 14f;
            boolean an = i == seite;
            Glatt.kreis(g, px, unten, an ? 7f : 5f, Glatt.alpha(an ? 0xFFA78BFA : 0xFF4A4460, auf));
        }
        String b = com.vortex.client.VortexClientMod.emoteKeyName();
        Glatt.textMitte(g, "Scroll or " + b + ": next page   ·   Click or release " + b + ": play   ·   1-8", cx, unten + 10,
                Glatt.alpha(0xFF8D86A6, auf), Glatt.Schrift.NORMAL);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    /** Eigene Figur von vorne in der Mitte, sie spielt die Vorschau (Emotes.VORSCHAU_ID). false = keine Welt. */
    private boolean figur(GuiGraphicsExtractor g, float cx, float cy) {
        Player p = this.minecraft == null ? null : this.minecraft.player;
        if (p == null) return false;
        float bodyRot = p.yBodyRot, bodyRotO = p.yBodyRotO, yRot = p.getYRot(), yRotO = p.yRotO, xRot = p.getXRot(), xRotO = p.xRotO, head = p.yHeadRot, headO = p.yHeadRotO;
        try {
            p.yBodyRot = p.yBodyRotO = 180f;
            p.setYRot(180f); p.yRotO = 180f;
            p.setXRot(0f); p.xRotO = 0f;
            p.yHeadRot = p.yHeadRotO = 180f;
            var state = this.minecraft.getEntityRenderDispatcher().extractEntity(p, 1f);
            if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState a) {
                a.id = Emotes.VORSCHAU_ID;
                a.walkAnimationSpeed = 0f;
            }
            state.lightCoords = 0xF000F0;
            state.shadowPieces.clear();
            state.outlineColor = 0;
            Quaternionf drehen = new Quaternionf().rotateZ((float) Math.PI)
                    .mul(new Quaternionf().rotateX((float) Math.toRadians(-4f)))
                    .mul(new Quaternionf().rotateY((float) Math.toRadians(-20f)));
            float groesse = 40f;
            Vector3f mitte = new Vector3f(0, p.getBbHeight() / 2f + 0.06f, 0);
            int x0 = Math.round(cx - 48), y0 = Math.round(cy - 48), x1 = Math.round(cx + 48), y1 = Math.round(cy + 48);
            //#if 26
            g.entity(state, groesse, mitte, drehen, null, x0, y0, x1, y1);
            //#else
            //$ g.submitEntityRenderState(state, groesse, mitte, drehen, null, x0, y0, x1, y1);
            //#endif
            return true;
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("EmoteRad.figur", t);
            return false;
        } finally {
            p.yBodyRot = bodyRot; p.yBodyRotO = bodyRotO; p.setYRot(yRot); p.yRotO = yRotO;
            p.setXRot(xRot); p.xRotO = xRotO; p.yHeadRot = head; p.yHeadRotO = headO;
        }
    }

    private void abspielen(int i) {
        if (i < 0 || i >= liste.size()) return;
        String id = liste.get(i).id();
        onClose();
        Emotes.spielen(id);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (click.button() == 1) { onClose(); return true; }
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        int i = unterMaus(click.x(), click.y());
        if (i >= 0) abspielen(i); else onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0) blaettern(vertical < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (k >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && k <= org.lwjgl.glfw.GLFW.GLFW_KEY_8) { abspielen(k - org.lwjgl.glfw.GLFW.GLFW_KEY_1); return true; }
        KeyMapping t = taste();
        if (t != null && t.matches(e)) { if (!gehalten) blaettern(1); return true; }
        if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT || k == org.lwjgl.glfw.GLFW.GLFW_KEY_D) { blaettern(1); return true; }
        if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT || k == org.lwjgl.glfw.GLFW.GLFW_KEY_A) { blaettern(-1); return true; }
        return super.keyPressed(e);
    }

    @Override
    public void removed() {
        Emotes.vorschau(null);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

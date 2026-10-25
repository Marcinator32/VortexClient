package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Gegner-Info: eine Karte im HUD mit dem Gegner, mit dem du gerade kaempfst --
 * Kopf, Name, Leben (mit Absorption), Ruestung samt Haltbarkeit, was er in den
 * Haenden haelt, Entfernung, Ping, verbrauchte Totems und wer gerade vorne
 * liegt.
 *
 * Gegner = wen du zuletzt geschlagen hast, sonst wer dich zuletzt geschlagen
 * hat, sonst der Spieler im Fadenkreuz. Nach "Keep" Sekunden ohne Kampf
 * verschwindet die Karte wieder.
 *
 * Rein anzeigend: alles kommt aus Daten, die der Client ohnehin bekommt.
 */
public class OpponentInfoModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 200, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 140, 0, 1080, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 2.5, 0.05);
    public final ColorSetting color = new ColorSetting("Accent", 0xFF8B5CF6);
    public final BooleanSetting playersOnly = new BooleanSetting("Players Only", true);
    public final BooleanSetting crosshair = new BooleanSetting("Show On Crosshair", true);
    public final BooleanSetting armor = new BooleanSetting("Armor & Items", true);
    public final BooleanSetting pops = new BooleanSetting("Totem Pops", true);
    public final BooleanSetting lead = new BooleanSetting("Who Is Ahead", true);
    public final NumberSetting keep = new NumberSetting("Keep (s)", 6, 1, 30, 1);

    public OpponentInfoModule() {
        super("Opponent Info", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(scale);
        addSetting(color);
        addSetting(playersOnly);
        addSetting(crosshair);
        addSetting(armor);
        addSetting(pops);
        addSetting(lead);
        addSetting(keep);
    }

    @Override public String hudName() { return "Opponent"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return Math.round(com.vortex.client.hud.OpponentHud.BREITE * scale.getFloat()); }
    @Override public int hudHeight() { return Math.round(com.vortex.client.hud.OpponentHud.hoehe(this) * scale.getFloat()); }
}

package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Info Bar (seit 4.56): eine Karte mit Vortex-Logo, FPS, Ping, TPS und Uhrzeit nebeneinander,
 * durch feine Striche getrennt. Zeichnen: hud/InfoBar.
 */
public class InfoBarModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 3840, 1);
    public final NumberSetting y = new NumberSetting("Y", 4, 0, 2160, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);
    public final ColorSetting color = new ColorSetting("Text Color", 0xFFF2F3F7);
    public final BooleanSetting logo = new BooleanSetting("Logo", true);
    public final BooleanSetting fps = new BooleanSetting("FPS", true);
    public final BooleanSetting ping = new BooleanSetting("Ping", true);
    public final BooleanSetting tps = new BooleanSetting("TPS", false);
    public final BooleanSetting clock = new BooleanSetting("Clock", true);
    public final ModeSetting clockFormat = new ModeSetting("Clock Format", 0, "24h", "12h");

    public InfoBarModule() {
        super("Info Bar", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(scale);
        addSetting(color);
        addSetting(logo);
        addSetting(fps);
        addSetting(ping);
        addSetting(tps);
        addSetting(clock);
        addSetting(clockFormat);
    }

    @Override public String hudName() { return "Info Bar"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return com.vortex.client.hud.InfoBar.breite(); }
    @Override public int hudHeight() { return com.vortex.client.hud.InfoBar.HOEHE; }
}

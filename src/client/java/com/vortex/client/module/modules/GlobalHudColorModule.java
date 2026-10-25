package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;

/**
 * "HUD-Farbe" -- wendet EINE Farbe auf alle HUD-Elemente gleichzeitig an
 * (FPS, CPS, Koordinaten, Trank-Effekte, Ruestungs-HUD).
 *
 * Bedienung im ClickGUI:
 *   - "Farbe" einstellen -> wird beim Aendern SOFORT live auf alle uebertragen.
 *   - "Jetzt anwenden" -> uebertraegt die Farbe noch einmal manuell.
 *   - "Zuruecksetzen (weiss)" -> setzt alle HUD-Texte wieder auf Weiss.
 *
 * So muss man nicht jedes HUD-Element einzeln einfaerben. Dieses Modul ist
 * ein Werkzeug, kein an/aus-Feature -- der "Aktiviert"-Schalter spielt hier
 * keine Rolle, es zaehlen nur die Knoepfe.
 */
public class GlobalHudColorModule extends Module {

    private static final int WHITE = 0xFFFFFFFF;

    public final ColorSetting color = new ColorSetting("Color", WHITE);
    public final BooleanSetting apply = new BooleanSetting("Apply Now", false);
    public final BooleanSetting reset = new BooleanSetting("Reset (White)", false);

    public GlobalHudColorModule() {
        super("HUD Color", Category.HUD);
        addSetting(color);
        addSetting(apply);
        addSetting(reset);
    }

    /** Uebertraegt die eingestellte Farbe auf alle HUD-Elemente. */
    public void applyToAll() {
        applyColor(color);
        apply.set(false);
    }

    /** Setzt alle HUD-Texte wieder auf Weiss zurueck. */
    public void resetToWhite() {
        color.set(WHITE);
        color.setType(ColorSetting.SOLID);
        applyColor(color);
        reset.set(false);
    }

    /**
     * Uebertraegt die Farbe auf alle HUD-Elemente (inkl. ArmorHud) -- mit
     * Verlauf, Art und Tempo. Ein Verlauf auf "HUD Color" macht also alle
     * HUD-Texte zum Verlauf.
     */
    private void applyColor(ColorSetting c) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof HudElement he && he.hudColor() != null) {
                he.hudColor().copyFrom(c);
            }
            if (m instanceof ArmorHudModule armor) {
                armor.textColor.copyFrom(c);
            }
        }
    }

    /** Kein eigener Eintrag im Mod-Menue mehr: eingestellt wird ueber HUD Style (HudLook). */
    @Override
    public boolean imMenue() { return false; }
}

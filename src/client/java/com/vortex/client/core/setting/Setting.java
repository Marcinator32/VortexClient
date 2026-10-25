package com.vortex.client.core.setting;

/**
 * Basis fuer alle Einstellungen eines Moduls.
 *
 * Die Idee (das ist der Kern des ganzen Customizing-Systems):
 * Ein Modul kennt seine eigenen Einstellungen NICHT als festen Code,
 * sondern als Liste von Setting-Objekten. Das GUI liest diese Liste
 * und baut daraus automatisch die passenden Bedienelemente.
 *
 * Neuer Einstellungstyp = neue Unterklasse hier + ein Renderer im GUI.
 * Du musst nie das GUI fuer jedes einzelne Modul anpassen.
 */
public abstract class Setting {

    private final String name;

    protected Setting(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** Fuer Speichern/Laden: Wert als String. */
    public abstract String serialize();

    /** Fuer Speichern/Laden: Wert aus String wiederherstellen. */
    public abstract void deserialize(String value);

    private String defaultValue = null;

    /** Merkt sich den aktuellen Wert als Ausgangswert (einmalig). */
    public void rememberDefault() {
        if (defaultValue == null) {
            try {
                defaultValue = serialize();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Setzt die Einstellung auf ihren Ausgangswert zurueck. */
    public void resetToDefault() {
        if (defaultValue == null) return;
        try {
            deserialize(defaultValue);
        } catch (Throwable ignored) {
        }
    }
}

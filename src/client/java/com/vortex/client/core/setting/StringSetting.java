package com.vortex.client.core.setting;

/**
 * Ein Texteingabefeld fuer benutzerdefinierte Werte (z.B. Filter-Keywords,
 * Namen, Listen von Block-IDs). Seit 4.32 in allen Einstellungsmenues
 * bearbeitbar (Klick auf den Wert oeffnet {@code TextEingabeScreen}).
 */
public class StringSetting extends Setting {

    private String value;
    private final String hinweis;

    public StringSetting(String name, String defaultValue) {
        this(name, defaultValue, "");
    }

    /** @param hinweis kurze Erklaerung ueber dem Eingabefeld, z. B. "Block-IDs, mit Komma getrennt" */
    public StringSetting(String name, String defaultValue, String hinweis) {
        super(name);
        this.value = defaultValue;
        this.hinweis = hinweis == null ? "" : hinweis;
    }

    public String get() {
        return value;
    }

    public void set(String value) {
        this.value = value == null ? "" : value;
    }

    public String hinweis() {
        return hinweis;
    }

    /** Der Text als Liste: durch Komma, Semikolon oder Zeilenumbruch getrennt, ohne Leereintraege. */
    public java.util.List<String> liste() {
        java.util.List<String> l = new java.util.ArrayList<>();
        if (value == null) return l;
        for (String s : value.split("[,;\\n]")) {
            String t = s.trim();
            if (!t.isEmpty()) l.add(t);
        }
        return l;
    }

    @Override
    public String serialize() {
        return value == null ? "" : value.replace("\n", ",").replace("\r", "");
    }

    @Override
    public void deserialize(String value) {
        this.value = value == null ? "" : value;
    }
}

package com.vortex.client.account;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.Proxy;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Den angemeldeten Account im laufenden Spiel komplett austauschen (seit 4.86).
 *
 * Nur den "User" zu ersetzen (wie frueher) reichte nicht: Minecraft merkt sich beim Start
 * noch das Profil mit Skin, den Dienst fuer Chat-Schluessel und Kontoeinstellungen und die
 * Chat-Signatur des alten Accounts. Darum werden hier alle diese Teile neu gebaut:
 *   User (Name, UUID, Token), Profil + Skin, UserApiService, Konto-Eigenschaften,
 *   Chat-Schluessel -- danach Figur im Hauptmenue, Kopf im Vortex-Menue und die
 *   Verbindung zum Freunde-Server.
 *
 * Alles ueber Reflection nach Feld-TYP (nicht Name): so klappt es in allen Minecraft-
 * Versionen, auch wenn die Felder beim Spielen andere Namen tragen. Nur aufrufen, wenn
 * keine Welt offen ist.
 */
public final class KontoWechsel {
    private KontoWechsel() {}

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("vortexclient");

    /** Muss im Haupt-Thread laufen. */
    public static void anwenden(String name, String uuidText, String token) {
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = uuid(uuidText);
        User user = new User(name, uuid, token, Optional.empty(), Optional.empty());
        setzeNachTyp(mc, User.class.getName(), user, true);

        Object auth = null;
        Object api = null;
        try {
            Class<?> ygg = Class.forName("com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService");
            auth = ygg.getConstructor(Proxy.class).newInstance(mc.getProxy());
            api = ygg.getMethod("createUserApiService", String.class).invoke(auth, token);
        } catch (Throwable e) {
            LOGGER.warn("[vortexclient] Account switch: user services not renewed ({})", e.toString());
        }
        if (api != null) {
            setzeNachTyp(mc, "com.mojang.authlib.minecraft.UserApiService", api, false);
            final Object dienst = api;
            setzeFuture(mc, "UserProperties", CompletableFuture.supplyAsync(() -> eigenschaften(dienst)));
            try {
                Class<?> kpm = Class.forName(net.minecraft.client.multiplayer.ProfileKeyPairManager.class.getName());
                for (Method m : kpm.getDeclaredMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == kpm && p.length == 3
                            && p[1] == User.class && p[2] == Path.class && p[0].isInstance(api)) {
                        m.setAccessible(true);
                        setzeNachTyp(mc, kpm.getName(), m.invoke(null, api, user, mc.gameDirectory.toPath()), false);
                        break;
                    }
                }
            } catch (Throwable e) {
                LOGGER.warn("[vortexclient] Account switch: chat keys not renewed ({})", e.toString());
            }
        }

        final Object sitzung = sitzungsDienst(mc);
        setzeFuture(mc, "ProfileResult", CompletableFuture.supplyAsync(() -> profil(sitzung, uuid, name)));

        try { com.vortex.client.cosmetics.TitelFigur.neuLaden(); } catch (Throwable ignored) { }
        try { com.vortex.client.gui.HomeScreen.kopfNeu(); } catch (Throwable ignored) { }
        try { com.vortex.client.cosmetics.CosmeticsSync.kontoGewechselt(); } catch (Throwable ignored) { }
        try { com.vortex.client.cosmetics.Emotes.kontoGewechselt(); } catch (Throwable ignored) { }
        try { com.vortex.client.social.Social.kontoGewechselt(); } catch (Throwable ignored) { }
        LOGGER.info("[vortexclient] Switched account to {}", name);
    }

    private static UUID uuid(String s) {
        String t = String.valueOf(s).replace("-", "");
        if (t.length() == 32) {
            return new UUID(Long.parseUnsignedLong(t.substring(0, 16), 16), Long.parseUnsignedLong(t.substring(16), 16));
        }
        return UUID.fromString(s);
    }

    /** UserApiService.fetchProperties(), sonst die leeren Offline-Eigenschaften. */
    private static Object eigenschaften(Object api) {
        try {
            return api.getClass().getMethod("fetchProperties").invoke(api);
        } catch (Throwable e) {
            try {
                return Class.forName("com.mojang.authlib.minecraft.UserApiService").getField("OFFLINE_PROPERTIES").get(null);
            } catch (Throwable e2) {
                return null;
            }
        }
    }

    /** Profil mit Skin vom Mojang-Server; klappt das nicht, wenigstens Name und UUID. */
    private static Object profil(Object sitzung, UUID uuid, String name) {
        if (sitzung != null) {
            try {
                Method f = sitzung.getClass().getMethod("fetchProfile", UUID.class, boolean.class);
                Object r = f.invoke(sitzung, uuid, true);
                if (r != null) return r;
            } catch (Throwable e) {
                LOGGER.warn("[vortexclient] Account switch: profile not loaded ({})", e.toString());
            }
        }
        try {
            Class<?> pr = Class.forName("com.mojang.authlib.yggdrasil.ProfileResult");
            GameProfile gp = new GameProfile(uuid, name);
            for (Constructor<?> c : pr.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 2 && p[0] == GameProfile.class && p[1] == Set.class) return c.newInstance(gp, Set.of());
                if (p.length == 1 && p[0] == GameProfile.class) return c.newInstance(gp);
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /** Minecrafts MinecraftSessionService -- direkt als Feld oder in einem Dienste-Objekt. */
    private static Object sitzungsDienst(Minecraft mc) {
        try {
            for (Field f : felder(mc.getClass())) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                if (t.getName().equals("com.mojang.authlib.minecraft.MinecraftSessionService")) {
                    f.setAccessible(true);
                    return f.get(mc);
                }
                if (t.getName().startsWith("com.") || t.isPrimitive() || t.isArray()) continue;
                for (Method m : t.getMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType().getName().equals("com.mojang.authlib.minecraft.MinecraftSessionService")) {
                        f.setAccessible(true);
                        Object o = f.get(mc);
                        if (o != null) return m.invoke(o);
                    }
                }
            }
        } catch (Throwable e) {
            LOGGER.warn("[vortexclient] Account switch: no session service ({})", e.toString());
        }
        return null;
    }

    private static java.util.List<Field> felder(Class<?> k) {
        java.util.List<Field> l = new java.util.ArrayList<>();
        for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) l.addAll(java.util.Arrays.asList(c.getDeclaredFields()));
        return l;
    }

    /** Das (einzige) Feld dieses Typs setzen. */
    private static void setzeNachTyp(Object ziel, String typ, Object wert, boolean pflicht) {
        if (wert == null) return;
        for (Field f : felder(ziel.getClass())) {
            if (Modifier.isStatic(f.getModifiers()) || !f.getType().getName().equals(typ)) continue;
            try {
                f.setAccessible(true);
                f.set(ziel, wert);
                return;
            } catch (Throwable e) {
                if (pflicht) throw new IllegalStateException("Could not set " + typ, e);
                LOGGER.warn("[vortexclient] Account switch: {} not replaced ({})", typ, e.toString());
                return;
            }
        }
        if (pflicht) throw new IllegalStateException("No field of type " + typ);
    }

    /** Das CompletableFuture-Feld setzen, dessen Inhaltstyp so heisst (ProfileResult, UserProperties). */
    private static void setzeFuture(Object ziel, String inhalt, CompletableFuture<?> wert) {
        for (Field f : felder(ziel.getClass())) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != CompletableFuture.class) continue;
            if (!f.getGenericType().getTypeName().contains(inhalt)) continue;
            try {
                f.setAccessible(true);
                f.set(ziel, wert);
            } catch (Throwable e) {
                LOGGER.warn("[vortexclient] Account switch: {} not replaced ({})", inhalt, e.toString());
            }
            return;
        }
    }
}

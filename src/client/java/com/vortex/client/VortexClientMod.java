package com.vortex.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.vortex.client.core.ConfigManager;
import com.vortex.client.gui.ClickGui;
import com.vortex.client.hud.HudRenderer;
import com.vortex.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client-Einstiegspunkt ("onEnable").
 */
public class VortexClientMod implements ClientModInitializer {

    public static final String MOD_ID = "vortexclient";

    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

    /** Which toggle keys were held last tick, for edge detection. */
    private static final java.util.Map<String, Boolean> toggleKeyDown =
            new java.util.HashMap<>();

    private static KeyMapping openClickGuiKey;
    private static KeyMapping openHudEditorKey;
    private static KeyMapping friendsKey;
    private static boolean friendsKeyWasDown;
    private static KeyMapping emoteKey;

    /** Anzeigename der Freunde-Taste (fuer Hinweise wie "druecke O"). */
    public static String emoteKeyName() {
        return emoteKey == null ? "B" : emoteKey.getTranslatedKeyMessage().getString();
    }

    /** Die Emote-Taste (fuer das Rad: B nochmal = naechste Seite, B loslassen = abspielen). */
    public static KeyMapping emoteKeyMapping() { return emoteKey; }

    /**
     * Emote-Taste neu belegen (Cosmetics-Menue, seit 4.68) -- wie in den Steuerungs-Optionen:
     * Taste setzen, Zuordnung neu aufbauen, options.txt speichern.
     */
    public static void setzeEmoteTaste(InputConstants.Key taste) {
        if (emoteKey == null || taste == null) return;
        emoteKey.setKey(taste);
        KeyMapping.resetMapping();
        net.minecraft.client.Minecraft.getInstance().options.save();
    }

    public static String friendsKeyName() {
        return friendsKey == null ? "O" : friendsKey.getTranslatedKeyMessage().getString();
    }

    /** Fallback-Flankenerkennung, falls 26.2 die KeyMapping-Queue nicht fuellt. */
    private static boolean clickGuiKeyWasDown;

    @Override
    public void onInitializeClient() {
        ModuleManager.INSTANCE.getModules();

        ConfigManager.loadActivePreset();
        ConfigManager.load();

        com.vortex.client.cosmetics.ActiveCape.init();
        try { com.vortex.client.cosmetics.Cosmetics.register(); } catch (Throwable pvpErr) { com.vortex.client.core.Errors.report("Cosmetics", pvpErr); }

        HudRenderer.register();

        com.vortex.client.hud.HitboxRenderer.register();
        com.vortex.client.hud.NoFall.register();
        com.vortex.client.hud.ChunkBorders.register();
        com.vortex.client.hud.ProjectilePath.register();
        com.vortex.client.hud.SessionStats.register();
        com.vortex.client.hud.PingMeter.start();
        com.vortex.client.macro.MacroManager.register();
        com.vortex.client.hud.AutoReconnect.register();
        com.vortex.client.hud.ChatCopy.register();
        com.vortex.client.core.EntityCache.register();
        com.vortex.client.hud.CombatTracker.register();
        com.vortex.client.hud.CombatFx.register();
        com.vortex.client.hud.DamageNumbers.register();
        com.vortex.client.hud.PearlTracker.register();
        com.vortex.client.hud.Glint.register();
        com.vortex.client.hud.TickRate.register();
        com.vortex.client.hud.ExtraHud.register();
        com.vortex.client.core.PerfRecorder.register();
        com.vortex.client.hud.BlockOutline.register();
        com.vortex.client.hud.HitColor.register();
        com.vortex.client.hud.FogCheck.register();
        com.vortex.client.hud.TimeWeather.register();
        com.vortex.client.hud.Freelook.register();
        com.vortex.client.hud.SlotLock.register();

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN
                .register((handler, sender, client) -> {
                    Thread t = new Thread(() -> {
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ignored) {
                            return;
                        }
                        client.execute(() ->
                                com.vortex.client.hud.PerformanceHint.maybeShow(client));
                    }, "vortex-hint");
                    t.setDaemon(true);
                    t.start();
                });
        com.vortex.client.hud.WaypointRenderer.register();
        com.vortex.client.waypoint.WaypointActions.register();
        try { com.vortex.client.gui.menu.PauseMenu.register(); } catch (Throwable pvpErr) { com.vortex.client.core.Errors.report("PauseMenu", pvpErr); }
        try { com.vortex.client.core.MenueWaechter.register(); } catch (Throwable pvpErr) { com.vortex.client.core.Errors.report("MenueWaechter", pvpErr); }
        try { com.vortex.client.gui.menu.MenuLeiste.register(); } catch (Throwable pvpErr) { com.vortex.client.core.Errors.report("MenuLeiste", pvpErr); }
        try { com.vortex.client.gui.menu.WeltHosten.register(); } catch (Throwable pvpErr) { com.vortex.client.core.Errors.report("WeltHosten", pvpErr); }
        try { com.vortex.client.gui.menu.HostKnoepfe.register(); } catch (Throwable pvpErr) { com.vortex.client.core.Errors.report("HostKnoepfe", pvpErr); }
        com.vortex.client.freecam.Freecam.registerSafety();
        try { com.vortex.client.beta.BetaTest.register(); } catch (Throwable pvpErr) { com.vortex.client.core.Errors.report("BetaTest", pvpErr); }
        com.vortex.client.command.ClientCommands.register();
        com.vortex.client.command.FriendCommands.register();
        try { com.vortex.client.hud.LightOverlay.register(); } catch (Throwable e) { com.vortex.client.core.Errors.report("LightOverlay", e); }
        try { com.vortex.client.hud.ChatFilter.register(); } catch (Throwable e) { com.vortex.client.core.Errors.report("ChatFilter", e); }
        try { com.vortex.client.hud.BetterTooltips.register(); } catch (Throwable e) { com.vortex.client.core.Errors.report("BetterTooltips", e); }
        try { com.vortex.client.hud.AutoGG.register(); } catch (Throwable e) { com.vortex.client.core.Errors.report("AutoGG", e); }
        try { com.vortex.client.command.ListCommands.register(); } catch (Throwable e) { com.vortex.client.core.Errors.report("ListCommands", e); }

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
            .register((handler, client) -> {
                com.vortex.client.freecam.Freecam.beenden(null);
                com.vortex.client.hud.TotemPops.reset();
                com.vortex.client.waypoint.WaypointWorlds.vergessen();
                com.vortex.client.hud.PingMeter.stop();
                com.vortex.client.hud.PingMeter.start();
            });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> com.vortex.client.core.ExitGuard.start());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                for (var module : ModuleManager.INSTANCE.getModules()) {
                    if (module instanceof com.vortex.client.module.modules.PotatoModeModule p
                            && p.isEnabled()) {
                        p.restoreOriginals();
                    }
                }
            } catch (Throwable ignored) {
            }
            ConfigManager.save();
        });

        openClickGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.vortexclient.clickgui", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

        openHudEditorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.vortexclient.hudeditor", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_CONTROL, CATEGORY));

        friendsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.vortexclient.friends", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O, CATEGORY));
        com.vortex.client.social.Social.register();
        com.vortex.client.musik.MusikDienst.register();
        com.vortex.client.gui.Panoramen.register();

        emoteKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.vortexclient.emotes", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B, CATEGORY));

        final boolean[] synced = { false };

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!synced[0]) {
                synced[0] = true;
                for (var module : ModuleManager.INSTANCE.getModules()) {
                    try {
                        module.syncState();
                    } catch (Throwable ignored) {
                    }
                }
            }
            boolean openClickGui = false;
            while (openClickGuiKey.consumeClick()) {
                openClickGui = true;
            }

            boolean rightShiftDown = false;
            try {
                var gk = KeyMappingHelper.getBoundKeyOf(openClickGuiKey);
                rightShiftDown = gk.getType() == InputConstants.Type.KEYSYM && gk.getValue() > 0
                        && InputConstants.isKeyDown(client.getWindow(), gk.getValue());
            } catch (Throwable ignored) { }
            if (rightShiftDown && !clickGuiKeyWasDown) {
                openClickGui = true;
            }
            clickGuiKeyWasDown = rightShiftDown;

            if (openClickGui && client.gui.screen() == null) {
                client.gui.setScreen(com.vortex.client.core.ClientSettings.INSTANCE.rightShiftOpens.getIndex() == 1
                        ? new com.vortex.client.gui.PanelGui() : new com.vortex.client.gui.HomeScreen());
                            }

            while (openHudEditorKey.consumeClick()) {
                client.gui.setScreen(new com.vortex.client.gui.HudEditorScreen());
            }

            while (emoteKey.consumeClick()) {
                if (client.gui.screen() == null && client.player != null) client.gui.setScreen(new com.vortex.client.cosmetics.EmoteRadScreen());
            }

            boolean openFriends = false;
            while (friendsKey.consumeClick()) openFriends = true;
            try {
                int fk = KeyMappingHelper.getBoundKeyOf(friendsKey).getValue();
                boolean down = fk > 0 && InputConstants.isKeyDown(client.getWindow(), fk);
                if (down && !friendsKeyWasDown) openFriends = true;
                friendsKeyWasDown = down;
            } catch (Throwable ignored) { }
            if (openFriends && client.gui.screen() == null) {
                client.gui.setScreen(new com.vortex.client.gui.FriendsScreen(null));
            }

            try {
                com.vortex.client.core.CleanModules.tick(client);
            } catch (Throwable cleanErr) {
                com.vortex.client.core.Errors.report("CleanModules", cleanErr);
            }

            try {
                if (client.gui.screen() == null) {
                    for (var module : ModuleManager.INSTANCE.getModules()) {
                        int code = module.getToggleKey().getKeyCode();
                        if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) continue;
                        if (com.vortex.client.core.CleanModules.versteckt(module)) continue;
                        boolean down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                                client.getWindow(), code);
                        boolean was = Boolean.TRUE.equals(toggleKeyDown.get(module.getName()));
                        if (down && !was) {
                            module.toggle();
                            com.vortex.client.core.ClientSettings.umgeschaltet(module, true);
                            com.vortex.client.core.ConfigManager.save();
                        }
                        toggleKeyDown.put(module.getName(), down);
                    }
                } else {
                    toggleKeyDown.clear();
                }
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("ModuleToggleKeys", pvpErr);
            }

            try {
                com.vortex.client.module.Module fc =
                        com.vortex.client.freecam.Freecam.module();
                boolean sollAn = (fc != null && fc.isEnabled());
                if (sollAn != com.vortex.client.freecam.Freecam.isActive()) {
                    com.vortex.client.freecam.Freecam.toggle();
                }
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("FreecamFollowsModule", pvpErr);
            }
});

        System.out.println("[" + MOD_ID + "] Client gestartet.");
    }
}

package svemocan.vanilla_storage_interface.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

public class IpnCompatWrapper {
    public static int triggerKey = GLFW.GLFW_KEY_SPACE;
    public static int triggerMouse = -1;
    public static int moveAllMod = GLFW.GLFW_KEY_LEFT_SHIFT;
    public static int moveMatchMod = GLFW.GLFW_KEY_LEFT_CONTROL;
    public static int includeHotbarMod = GLFW.GLFW_KEY_LEFT_ALT;

    public static void updateKeys() {
        // 1. Set the standard IPN Defaults initially
        triggerKey = GLFW.GLFW_KEY_SPACE;
        triggerMouse = -1;
        moveAllMod = GLFW.GLFW_KEY_LEFT_SHIFT;
        moveMatchMod = GLFW.GLFW_KEY_LEFT_CONTROL;
        includeHotbarMod = GLFW.GLFW_KEY_LEFT_ALT;

        if (!FabricLoader.getInstance().isModLoaded("inventoryprofilesnext")) {
            return; // Soft Dependency: Ignore completely if IPN is not installed
        }

        // 2. Layer 1: Attempt to read live API properties (The API Hook)
        // We use reflection so that if the IPN developers change their Kotlin ABI, your mod still compiles cleanly!
        boolean apiSuccess = tryLoadLiveApi();

        // 3. Layer 2: Read JSON configuration for overrides
        // If the API hook fails, we safely catch it and read the config file.
        // Since defaults are set above, any missing keys in the JSON naturally fall back to defaults!
        if (!apiSuccess) {
            tryLoadJsonOverrides();
        }
    }

    private static boolean tryLoadLiveApi() {
        try {
            Class<?> modSettingsClass = Class.forName("org.anti_ad.mc.ipnext.config.ModSettings");
            Object settingsInst = modSettingsClass.getField("INSTANCE").get(null);

            Object allModObj = modSettingsClass.getMethod("getMOVE_ALL_MODIFIER").invoke(settingsInst);
            parseModifierObj(allModObj, 1);

            Object matchModObj = modSettingsClass.getMethod("getMOVE_FOCUS_MACH_MODIFIER").invoke(settingsInst);
            parseModifierObj(matchModObj, 2);

            Object hotbarModObj = modSettingsClass.getMethod("getINCLUDE_HOTBAR_MODIFIER").invoke(settingsInst);
            parseModifierObj(hotbarModObj, 3);

            Class<?> hotkeysClass = Class.forName("org.anti_ad.mc.ipnext.config.Hotkeys");
            Object hotkeysInst = hotkeysClass.getField("INSTANCE").get(null);

            Object triggerObj = hotkeysClass.getMethod("getMOVE_ALL_ITEMS").invoke(hotkeysInst);
            parseTriggerObj(triggerObj);

            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void tryLoadJsonOverrides() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Path ipnConfig = configDir.resolve("InventoryProfilesNext/inventoryprofiles.json");
            if (!Files.exists(ipnConfig)) ipnConfig = configDir.resolve("inventoryprofiles.json");

            if (Files.exists(ipnConfig)) {
                String content = Files.readString(ipnConfig);
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();

                if (json.has("Hotkeys") && json.getAsJsonObject("Hotkeys").has("move_all_items")) {
                    String keys = json.getAsJsonObject("Hotkeys").getAsJsonObject("move_all_items").getAsJsonObject("main").get("keys").getAsString();
                    parseTriggerString(keys);
                }

                if (json.has("ModSettings")) {
                    com.google.gson.JsonObject settings = json.getAsJsonObject("ModSettings");
                    if (settings.has("move_all_modifier")) moveAllMod = mapToGlfw(settings.getAsJsonObject("move_all_modifier").get("keys").getAsString().split(",")[0]);
                    if (settings.has("move_focus_mach_modifier")) moveMatchMod = mapToGlfw(settings.getAsJsonObject("move_focus_mach_modifier").get("keys").getAsString().split(",")[0]);
                    if (settings.has("include_hotbar_modifier")) includeHotbarMod = mapToGlfw(settings.getAsJsonObject("include_hotbar_modifier").get("keys").getAsString().split(",")[0]);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void parseModifierObj(Object obj, int type) {
        try {
            String str = obj.getClass().getMethod("getConfigString").invoke(obj).toString();
            int key = mapToGlfw(str.split(",")[0]);
            if (type == 1) moveAllMod = key;
            else if (type == 2) moveMatchMod = key;
            else if (type == 3) includeHotbarMod = key;
        } catch (Exception ignored) {}
    }

    private static void parseTriggerObj(Object obj) {
        try {
            String str = obj.getClass().getMethod("getConfigString").invoke(obj).toString();
            parseTriggerString(str);
        } catch (Exception ignored) {}
    }

    private static void parseTriggerString(String keys) {
        String[] parts = keys.split(",");
        String finalKey = parts[parts.length - 1].trim().toUpperCase();
        if (finalKey.startsWith("BUTTON_")) {
            try {
                triggerMouse = Integer.parseInt(finalKey.replace("BUTTON_", "")) - 1;
                triggerKey = -1;
            } catch (Exception ignored) {}
        } else {
            triggerKey = mapToGlfw(finalKey);
            triggerMouse = -1;
        }
    }

    private static int mapToGlfw(String key) {
        key = key.trim().toUpperCase();
        if (key.startsWith("BUTTON_")) return -1;
        return switch (key) {
            case "LEFT_ALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "RIGHT_ALT" -> GLFW.GLFW_KEY_RIGHT_ALT;
            case "LEFT_CONTROL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "RIGHT_CONTROL" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "LEFT_SHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "RIGHT_SHIFT" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "CAPS_LOCK" -> GLFW.GLFW_KEY_CAPS_LOCK;
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "NONE" -> -1;
            default -> {
                if (key.length() == 1 && key.charAt(0) >= 'A' && key.charAt(0) <= 'Z') {
                    yield GLFW.GLFW_KEY_A + (key.charAt(0) - 'A');
                }
                yield -1;
            }
        };
    }
}
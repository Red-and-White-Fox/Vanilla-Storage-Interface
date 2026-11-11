package svemocan.vanilla_storage_interface.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class IpnCompatWrapper {
    // Minecraft 26.3 uses SDL key scancodes (Space=44, LShift=225, LCtrl=224, LAlt=226)
    public static int triggerKey = 44; // KEY_SPACE
    public static int triggerMouse = -1;
    public static int moveAllMod = 225; // KEY_LSHIFT
    public static int moveMatchMod = 224; // KEY_LCONTROL
    public static int includeHotbarMod = 226; // KEY_LALT

    public static void updateKeys() {
        // 1. Set the standard IPN Defaults initially
        triggerKey = 44;
        triggerMouse = -1;
        moveAllMod = 225;
        moveMatchMod = 224;
        includeHotbarMod = 226;

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
                    if (settings.has("move_all_modifier")) moveAllMod = mapToKey(settings.getAsJsonObject("move_all_modifier").get("keys").getAsString().split(",")[0]);
                    if (settings.has("move_focus_mach_modifier")) moveMatchMod = mapToKey(settings.getAsJsonObject("move_focus_mach_modifier").get("keys").getAsString().split(",")[0]);
                    if (settings.has("include_hotbar_modifier")) includeHotbarMod = mapToKey(settings.getAsJsonObject("include_hotbar_modifier").get("keys").getAsString().split(",")[0]);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void parseModifierObj(Object obj, int type) {
        try {
            String str = obj.getClass().getMethod("getConfigString").invoke(obj).toString();
            int key = mapToKey(str.split(",")[0]);
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
                triggerMouse = Integer.parseInt(finalKey.replace("BUTTON_", ""));
                triggerKey = -1;
            } catch (Exception ignored) {}
        } else {
            triggerKey = mapToKey(finalKey);
            triggerMouse = -1;
        }
    }

    private static int mapToKey(String key) {
        key = key.trim().toUpperCase();
        if (key.startsWith("BUTTON_")) return -1;
        return switch (key) {
            case "LEFT_ALT" -> 226; // KEY_LALT
            case "RIGHT_ALT" -> 230; // KEY_RALT
            case "LEFT_CONTROL" -> 224; // KEY_LCONTROL
            case "RIGHT_CONTROL" -> 228; // KEY_RCONTROL
            case "LEFT_SHIFT" -> 225; // KEY_LSHIFT
            case "RIGHT_SHIFT" -> 229; // KEY_RSHIFT
            case "CAPS_LOCK" -> 57; // KEY_CAPSLOCK
            case "SPACE" -> 44; // KEY_SPACE
            case "TAB" -> 43; // KEY_TAB
            case "NONE" -> -1;
            default -> {
                if (key.length() == 1 && key.charAt(0) >= 'A' && key.charAt(0) <= 'Z') {
                    yield 4 + (key.charAt(0) - 'A'); // KEY_A = 4
                }
                yield -1;
            }
        };
    }
}

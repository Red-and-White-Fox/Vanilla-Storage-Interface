package svemocan.vanilla_storage_interface.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import svemocan.VanillaStorageInterface;

@Config(name = VanillaStorageInterface.MOD_ID)
public class VanillaStorageConfig implements ConfigData {

    public enum DefragSortMode {
        SMART_PACKING,
        ADAPTIVE_PACKING,
        ALPHABETICAL,
        QUANTITY_DESCENDING,
        QUANTITY_ASCENDING,
        REGISTRY_ID
    }

    @ConfigEntry.Category("void_storage")
    @ConfigEntry.Gui.Tooltip
    public boolean enableVoidTerminal = true;

    @ConfigEntry.Category("void_storage")
    @ConfigEntry.Gui.Tooltip
    public boolean enableVoidRaw = true;

    @ConfigEntry.Category("box_storage")
    @ConfigEntry.Gui.Tooltip
    public boolean enableBoxTerminal = true;

    @ConfigEntry.Category("box_storage")
    @ConfigEntry.Gui.Tooltip
    public boolean enableBoxRaw = true;

    @ConfigEntry.Category("player_storage")
    @ConfigEntry.Gui.Tooltip
    public boolean enablePlayerInventoryTerminal = true;

    @ConfigEntry.Category("controls")
    @ConfigEntry.Gui.Tooltip
    public boolean invertShiftModifiers = false;

    @ConfigEntry.Category("controls")
    @ConfigEntry.Gui.Tooltip
    public boolean autoFocusSearchBar = true;

    @ConfigEntry.Category("controls")
    @ConfigEntry.Gui.Tooltip
    public boolean ipnHasPriorityOverAutoFocus = true;

    @ConfigEntry.Category("misc")
    @ConfigEntry.Gui.Tooltip
    public boolean syncSearchWithREI = false;

    @ConfigEntry.Category("misc")
    @ConfigEntry.Gui.Tooltip
    public boolean renderGhostItems = true;

    @ConfigEntry.Category("misc")
    @ConfigEntry.Gui.Tooltip
    public boolean muteInterfaceSounds = false;

    @ConfigEntry.Category("misc")
    @ConfigEntry.Gui.Tooltip
    public boolean enableIlluminatedInterfaces = true;

    @ConfigEntry.Category("misc")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public DefragSortMode defragmentationSortMode = DefragSortMode.ALPHABETICAL;

    @ConfigEntry.Category("misc")
    @ConfigEntry.Gui.Excluded
    public int lastSortMode = 0;
}
package svemocan.vanilla_storage_interface.rei;

import dev.architectury.event.CompoundEventResult;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.item.ItemStack;
import svemocan.vanilla_storage_interface.gui.StorageInterfaceScreen;

public class VanillaStorageREIClientPlugin implements REIClientPlugin {

    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.registerFocusedStack((screen, mouse) -> {

            if ((Object) screen instanceof StorageInterfaceScreen storageScreen) {

                ItemStack hoveredItem = storageScreen.getHoveredVirtualItem(mouse.getX(), mouse.getY());

                if (hoveredItem != null && !hoveredItem.isEmpty()) {
                    // Wrap the found item in 'interruptTrue' to tell REI: "Found the item! Stop searching!"
                    return CompoundEventResult.interruptTrue(EntryStacks.of(hoveredItem));
                }
            }

            // Return 'pass' to tell REI: "There's no item under the mouse here, fall back to vanilla logic."
            return CompoundEventResult.pass();
        });
    }
}
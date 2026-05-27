package svemocan.vanilla_storage_interface;

import net.minecraft.item.Item;
import java.util.LinkedHashMap;
import java.util.Map;

public class VirtualInventory {
    // LinkedHashMap is explicitly used to preserve predictable insertion order,
    // ensuring the UI layout mirrors the structural layout of the scanned containers.
    public final Map<Item, Integer> items = new LinkedHashMap<>();
    public int emptyShulkerSlots = 0;

    public void addItem(Item item, int count) {
        this.items.put(item, this.items.getOrDefault(item, 0) + count);
    }
}
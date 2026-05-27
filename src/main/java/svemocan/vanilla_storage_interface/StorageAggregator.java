package svemocan.vanilla_storage_interface;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import svemocan.vanilla_storage_interface.network.VirtualItem;
import java.util.ArrayList;
import java.util.List;

public class StorageAggregator {

    public static class VirtualInventory {
        public final List<VirtualItem> items = new ArrayList<>();
        public int emptyShulkerSlots = 0;

        public void addItem(ItemStack newStack) {
            if (newStack.isEmpty()) return;

            // Content aggregation: Iterates through current virtual list to group matching items.
            // Uses areItemsAndComponentsEqual to ensure components (enchantments, damage, custom names)
            // match perfectly before stacking them beyond standard 64 limits in the UI.
            for (int i = 0; i < items.size(); i++) {
                VirtualItem vi = items.get(i);
                if (ItemStack.areItemsAndComponentsEqual(vi.stack(), newStack)) {
                    items.set(i, new VirtualItem(vi.stack(), vi.count() + newStack.getCount()));
                    return;
                }
            }
            items.add(new VirtualItem(newStack.copyWithCount(1), newStack.getCount()));
        }
    }

    // Universal aggregator that flattens any vanilla Inventory abstraction into our VirtualInventory model
    public static VirtualInventory buildFromInventory(World world, Inventory inv) {
        VirtualInventory virtualInventory = new VirtualInventory();
        if (inv == null) return virtualInventory;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();

            // Nested Shulker handling: extracts items directly from the component map to flatten the UI layout
            if (isShulkerBox(item)) {
                ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);

                if (container != null) {
                    int occupiedSlots = 0;
                    for (ItemStack innerStack : container.iterateNonEmpty()) {
                        virtualInventory.addItem(innerStack);
                        occupiedSlots++;
                    }
                    // Keep track of internal space available for future item injections/sorting logic
                    virtualInventory.emptyShulkerSlots += (27 - occupiedSlots);
                } else {
                    virtualInventory.emptyShulkerSlots += 27;
                }
            } else {
                virtualInventory.addItem(stack);
            }
        }
        return virtualInventory;
    }

    // Resolves physical block positions and leverages vanilla Hopper logic to safely grab inventories (handles Double Chests seamlessly)
    public static VirtualInventory build(World world, BlockPos interfacePos) {
        BlockState state = world.getBlockState(interfacePos);

        if (!state.contains(StorageInterfaceBlock.FACING)) return new VirtualInventory();

        Direction facing = state.get(StorageInterfaceBlock.FACING);
        BlockPos targetPos = interfacePos.offset(facing.getOpposite());

        Inventory inv = HopperBlockEntity.getInventoryAt(world, targetPos);

        return buildFromInventory(world, inv);
    }

    private static boolean isShulkerBox(Item item) {
        return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    public static VirtualInventory buildMode3(net.minecraft.entity.player.PlayerInventory playerInv) {
        VirtualInventory virtualInventory = new VirtualInventory();

        // Slot indexes 9 to 35 specifically map out the main 3 inventory rows, excluding the hotbar (0-8)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = playerInv.getStack(i);
            if (stack.isEmpty()) continue;

            if (isShulkerBox(stack.getItem())) {
                net.minecraft.component.type.ContainerComponent container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
                if (container != null) {
                    int occupied = 0;
                    for (ItemStack inner : container.iterateNonEmpty()) {
                        virtualInventory.addItem(inner);
                        occupied++;
                    }
                    virtualInventory.emptyShulkerSlots += (27 - occupied);
                } else {
                    virtualInventory.emptyShulkerSlots += 27;
                }
            } else {
                virtualInventory.addItem(stack);
            }
        }
        return virtualInventory;
    }
}
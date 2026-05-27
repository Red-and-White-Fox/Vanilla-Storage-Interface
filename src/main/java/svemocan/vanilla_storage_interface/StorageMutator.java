package svemocan.vanilla_storage_interface;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import svemocan.vanilla_storage_interface.network.StorageActionPayload;

public class StorageMutator {

    public static void handleAction(ServerPlayerEntity player, StorageActionPayload payload) {
        if (!(player.currentScreenHandler instanceof svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler handler)) return;

        World world = player.getServerWorld();
        BlockPos interfacePos = handler.getInterfacePos();
        String action = payload.action();
        ItemStack targetStack = payload.stack();
        int amountNeeded = payload.amount();

        // Handles cosmetic alterations to the block's physical item frame overlay overlay via network packet payload actions.
        if (action.equals("CLEAR_DISPLAY") || action.equals("SET_DISPLAY") || action.equals("SCALE_DISPLAY") || action.equals("ROTATE_DISPLAY") || action.equals("RESET_DISPLAY")) {
            if (handler.getTerminalMode() == 0) {
                BlockEntity be = world.getBlockEntity(interfacePos);
                if (be instanceof StorageInterfaceBlockEntity interfaceBe) {
                    if (action.equals("CLEAR_DISPLAY")) {
                        interfaceBe.setDisplayItem(new ItemStack(net.minecraft.item.Items.STRUCTURE_VOID));
                    } else if (action.equals("SET_DISPLAY")) {
                        interfaceBe.setDisplayItem(targetStack.copyWithCount(1));
                    } else if (action.equals("SCALE_DISPLAY")) {
                        interfaceBe.setDisplayScale(interfaceBe.getDisplayScale() + (amountNeeded > 0 ? 0.05f : -0.05f));
                    } else if (action.equals("ROTATE_DISPLAY")) {
                        interfaceBe.setDisplayRotation(interfaceBe.getDisplayRotation() + (amountNeeded > 0 ? 15.0f : -15.0f));
                    } else if (action.equals("RESET_DISPLAY")) {
                        interfaceBe.setDisplayScale(1.0f);
                        interfaceBe.setDisplayRotation(0.0f);
                    }
                }
            }
            return;
        }

        Inventory inv = null;
        boolean isVirtualTerminal = (handler.getTerminalMode() != 0);

        if (handler.getTerminalMode() == 0) {
            net.minecraft.block.BlockState state = world.getBlockState(interfacePos);
            if (state.contains(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING)) {
                net.minecraft.util.math.Direction facing = state.get(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING);
                inv = net.minecraft.block.entity.HopperBlockEntity.getInventoryAt(world, interfacePos.offset(facing.getOpposite()));
            }
        } else if (handler.getTerminalMode() == 1) {
            inv = player.getEnderChestInventory();
        } else if (handler.getTerminalMode() == 2) {
            ItemStack shulkerStack = player.getInventory().getStack(handler.getShulkerSlot());
            if (isShulkerBox(shulkerStack.getItem())) inv = new ShulkerItemInventory(shulkerStack);
        } else if (handler.getTerminalMode() == 3) {
            inv = new net.minecraft.inventory.SimpleInventory(27);
            for(int i = 0; i < 27; i++) {
                inv.setStack(i, player.getInventory().getStack(i + 9));
            }
        }

        if (inv == null) return;

        if (action.equals("INSERT_ALL_MATCHING")) {
            ItemStack syncType = targetStack;
            if (syncType.isEmpty()) return;

            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack invStack = player.getInventory().getStack(i);
                if (!invStack.isEmpty() && ItemStack.areItemsAndComponentsEqual(invStack, syncType)) {
                    int leftOver = executeInsert(inv, invStack, invStack.getCount());
                    invStack.setCount(leftOver);
                }
            }
        }
        else if (action.equals("EXTRACT")) {
            ItemStack cursorStack = player.currentScreenHandler.getCursorStack();
            if (cursorStack.isEmpty() || (ItemStack.areItemsAndComponentsEqual(cursorStack, targetStack) && cursorStack.getCount() < cursorStack.getMaxCount())) {
                int spaceInCursor = cursorStack.isEmpty() ? targetStack.getMaxCount() : cursorStack.getMaxCount() - cursorStack.getCount();
                int maxToTake = Math.min(amountNeeded, spaceInCursor);

                ItemStack extractedItems = executeExtract(inv, targetStack, maxToTake);

                if (extractedItems != null && !extractedItems.isEmpty()) {
                    if (cursorStack.isEmpty()) {
                        player.currentScreenHandler.setCursorStack(extractedItems);
                    } else {
                        cursorStack.increment(extractedItems.getCount());

                        // CRITICAL FIX: Modifying the stack properties directly on the server does not trigger an implicit packet updates.
                        // Re-running setCursorStack forces an explicit ServerScreenHandler sync payload to fire down to the client.
                        player.currentScreenHandler.setCursorStack(cursorStack);
                    }
                }
            }
        }
        else if (action.equals("SHIFT_EXTRACT")) {
            ItemStack extractedItems = executeExtract(inv, targetStack, amountNeeded);

            if (extractedItems != null && !extractedItems.isEmpty()) {
                player.getInventory().insertStack(extractedItems);
                if (extractedItems.getCount() > 0) {
                    executeInsert(inv, extractedItems, extractedItems.getCount());
                }
            }
        }
        else if (action.equals("EXTRACT_ALL")) {
            int spaceInPlayer = calculatePlayerSpace(player.getInventory(), targetStack);
            if (spaceInPlayer > 0) {
                ItemStack extractedItems = executeExtract(inv, targetStack, spaceInPlayer);
                if (extractedItems != null && !extractedItems.isEmpty()) {
                    player.getInventory().insertStack(extractedItems);
                    if (extractedItems.getCount() > 0) {
                        executeInsert(inv, extractedItems, extractedItems.getCount());
                    }
                }
            }
        }
        else if (action.equals("DUMP_INVENTORY")) {
            // Move everything from main inventory (slots 9-35) to storage. Hotbar (0-8) is preserved.
            for (int i = 9; i < 36; i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (!stack.isEmpty()) {
                    int leftover = executeInsert(inv, stack, stack.getCount());
                    stack.setCount(leftover);
                }
            }
        }
        else if (action.equals("REFILL_INVENTORY")) {
            // Top off all partial stacks in player's main inv + hotbar (slots 0-35)
            for (int i = 0; i < 36; i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getCount() < stack.getMaxCount()) {
                    int needed = stack.getMaxCount() - stack.getCount();
                    ItemStack extracted = executeExtract(inv, stack, needed);
                    if (extracted != null && !extracted.isEmpty()) {
                        stack.increment(extracted.getCount());
                    }
                }
            }
        }
        else if (action.equals("INSERT_CURSOR")) {
            ItemStack cursorStack = player.currentScreenHandler.getCursorStack();
            if (!cursorStack.isEmpty() && (targetStack.isEmpty() || ItemStack.areItemsAndComponentsEqual(cursorStack, targetStack))) {
                int amountToInsert = Math.min(amountNeeded, cursorStack.getCount());
                int leftOver = executeInsert(inv, cursorStack, amountToInsert);
                int successfullyInserted = amountToInsert - leftOver;

                if (successfullyInserted > 0) {
                    cursorStack.decrement(successfullyInserted);
                }
            }
        }
        else if (action.equals("QUICK_MOVE")) {
            net.minecraft.screen.slot.Slot slot = handler.slots.get(amountNeeded);
            if (slot != null && slot.hasStack()) {
                ItemStack stack = slot.getStack();
                int leftOver = executeInsert(inv, stack, stack.getCount());
                stack.setCount(leftOver);
                slot.markDirty();
            }
        }

        if (handler.getTerminalMode() == 3) {
            for(int i = 0; i < 27; i++) {
                player.getInventory().setStack(i + 9, inv.getStack(i));
            }
        }

        inv.markDirty();

        svemocan.vanilla_storage_interface.StorageAggregator.VirtualInventory vi;

        if (handler.getTerminalMode() == 3) {
            vi = StorageAggregator.buildMode3(player.getInventory());
        } else if (isVirtualTerminal) {
            vi = StorageAggregator.buildFromInventory(world, inv);
        } else {
            vi = StorageAggregator.build(world, interfacePos);
        }

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player,
                new svemocan.vanilla_storage_interface.network.StorageSyncPayload(vi.items, vi.emptyShulkerSlots, interfacePos, isVirtualTerminal)
        );
    }

    private static ItemStack executeExtract(Inventory inv, ItemStack targetStack, int amountNeeded) {
        if (targetStack.isEmpty() || amountNeeded <= 0) return ItemStack.EMPTY;

        int extracted = 0;

        for (int i = 0; i < inv.size() && extracted < amountNeeded; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;

            // Deep component extraction: item contents inside nested Shulker boxes must be extracted via component maps.
            if (isShulkerBox(stack.getItem())) {
                net.minecraft.component.type.ContainerComponent container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
                if (container != null) {
                    int emptySlot = -1;

                    // ANTI-DUPLICATION GUARD: If the Shulker Boxes themselves are stacked (count > 1), we cannot mutate
                    // the container components directly on the stack or it duplicates the mutation across all boxes.
                    // We must find an open slot to safely decouple and isolate a single Shulker Box item.
                    if (stack.getCount() > 1) {
                        for (int j = 0; j < inv.size(); j++) {
                            if (inv.getStack(j).isEmpty()) {
                                emptySlot = j;
                                break;
                            }
                        }
                        if (emptySlot == -1) continue;
                    }

                    net.minecraft.util.collection.DefaultedList<ItemStack> shulkerItems = net.minecraft.util.collection.DefaultedList.ofSize(27, ItemStack.EMPTY);
                    int slot = 0;
                    boolean modified = false;

                    for (ItemStack innerStack : container.iterateNonEmpty()) {
                        ItemStack copied = innerStack.copy();
                        if (extracted < amountNeeded && ItemStack.areItemsAndComponentsEqual(copied, targetStack)) {
                            int take = Math.min(amountNeeded - extracted, copied.getCount());
                            extracted += take;
                            copied.decrement(take);
                            modified = true;
                        }
                        if (!copied.isEmpty()) shulkerItems.set(slot++, copied);
                    }
                    if (modified) {
                        if (stack.getCount() > 1) {
                            ItemStack singleBox = stack.copyWithCount(1);
                            singleBox.set(net.minecraft.component.DataComponentTypes.CONTAINER, net.minecraft.component.type.ContainerComponent.fromStacks(shulkerItems));
                            ItemStack leftoverBoxes = stack.copyWithCount(stack.getCount() - 1);
                            inv.setStack(i, singleBox);
                            inv.setStack(emptySlot, leftoverBoxes);
                        } else {
                            stack.set(net.minecraft.component.DataComponentTypes.CONTAINER, net.minecraft.component.type.ContainerComponent.fromStacks(shulkerItems));
                            inv.setStack(i, stack);
                        }
                    }
                }
            }
            else if (ItemStack.areItemsAndComponentsEqual(stack, targetStack)) {
                int take = Math.min(amountNeeded - extracted, stack.getCount());
                extracted += take;
                stack.decrement(take);
            }
        }

        if (extracted > 0) {
            return targetStack.copyWithCount(extracted);
        }
        return ItemStack.EMPTY;
    }

    public static boolean insertDirectly(net.minecraft.server.network.ServerPlayerEntity player, svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler handler, ItemStack stackToInsert) {
        World world = player.getServerWorld();
        Inventory inv = null;
        boolean isVirtualTerminal = (handler.getTerminalMode() != 0);

        if (handler.getTerminalMode() == 0) {
            net.minecraft.block.BlockState state = world.getBlockState(handler.getInterfacePos());
            if (state.contains(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING)) {
                net.minecraft.util.math.Direction facing = state.get(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING);
                inv = HopperBlockEntity.getInventoryAt(world, handler.getInterfacePos().offset(facing.getOpposite()));
            }
        } else if (handler.getTerminalMode() == 1) {
            inv = player.getEnderChestInventory();
        } else if (handler.getTerminalMode() == 2) {
            ItemStack shulkerStack = player.getInventory().getStack(handler.getShulkerSlot());
            if (isShulkerBox(shulkerStack.getItem())) inv = new ShulkerItemInventory(shulkerStack);
        }

        if (inv == null || stackToInsert.isEmpty()) return false;

        int initial = stackToInsert.getCount();
        int leftOver = executeInsert(inv, stackToInsert, stackToInsert.getCount());

        if (leftOver < initial) {
            stackToInsert.setCount(leftOver);
            inv.markDirty();
            svemocan.vanilla_storage_interface.StorageAggregator.VirtualInventory vi = isVirtualTerminal
                    ? StorageAggregator.buildFromInventory(world, inv)
                    : StorageAggregator.build(world, handler.getInterfacePos());

            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new svemocan.vanilla_storage_interface.network.StorageSyncPayload(vi.items, vi.emptyShulkerSlots, handler.getInterfacePos(), isVirtualTerminal));
            return true;
        }
        return false;
    }

    public static boolean isShulkerBox(Item item) {
        return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    public static int executeInsert(Inventory inv, ItemStack originalStack, int amountToInsert) {
        if (originalStack.isEmpty() || amountToInsert <= 0) return amountToInsert;
        boolean isTargetShulker = isShulkerBox(originalStack.getItem());

        // Prevents endless recursion loop crashes caused by trying to insert a shulker box into another shulker block instance
        if (isTargetShulker && inv instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity) return amountToInsert;
        if (isTargetShulker) {
            net.minecraft.component.type.ContainerComponent container = originalStack.getOrDefault(net.minecraft.component.DataComponentTypes.CONTAINER, net.minecraft.component.type.ContainerComponent.DEFAULT);
            if (container.iterateNonEmpty().iterator().hasNext()) return amountToInsert;
        }

        amountToInsert = insertIntoExisting(inv, originalStack, amountToInsert, isTargetShulker);
        if (amountToInsert <= 0) return 0;

        amountToInsert = insertIntoEmpty(inv, originalStack, amountToInsert, isTargetShulker);

        if (isTargetShulker && amountToInsert > 0) {
            amountToInsert = autoCompressLooseItems(inv, originalStack, amountToInsert);
        }

        return amountToInsert;
    }

    private static int insertIntoExisting(Inventory inv, ItemStack payload, int amount, boolean isShulker) {
        for (int i = 0; i < inv.size() && amount > 0; i++) {
            ItemStack slotStack = inv.getStack(i);

            if (!isShulker && isShulkerBox(slotStack.getItem())) {
                if (slotStack.getCount() > 1) {
                    int emptySlot = -1;
                    for (int j = 0; j < inv.size(); j++) {
                        if (inv.getStack(j).isEmpty()) {
                            emptySlot = j;
                            break;
                        }
                    }
                    if (emptySlot == -1) continue;

                    ItemStack singleBox = slotStack.copyWithCount(1);
                    ItemStack leftoverBoxes = slotStack.copyWithCount(slotStack.getCount() - 1);
                    int remaining = mergeIntoShulkerComponent(singleBox, payload, amount, false);

                    if (remaining < amount) {
                        amount = remaining;
                        inv.setStack(i, singleBox);
                        inv.setStack(emptySlot, leftoverBoxes);
                    }
                } else {
                    amount = mergeIntoShulkerComponent(slotStack, payload, amount, false);
                    inv.setStack(i, slotStack);
                }
            }
            else if (ItemStack.areItemsAndComponentsEqual(slotStack, payload) && slotStack.getCount() < slotStack.getMaxCount()) {
                if (!inv.isValid(i, payload)) continue;

                int space = slotStack.getMaxCount() - slotStack.getCount();
                int give = Math.min(amount, space);
                slotStack.increment(give);
                amount -= give;
            }
        }
        return amount;
    }

    private static int insertIntoEmpty(Inventory inv, ItemStack payload, int amount, boolean isShulker) {
        for (int i = 0; i < inv.size() && amount > 0; i++) {
            ItemStack slotStack = inv.getStack(i);

            if (!isShulker && isShulkerBox(slotStack.getItem())) {
                if (slotStack.getCount() > 1) {
                    int emptySlot = -1;
                    for (int j = 0; j < inv.size(); j++) {
                        if (inv.getStack(j).isEmpty()) {
                            emptySlot = j;
                            break;
                        }
                    }
                    if (emptySlot == -1) continue;

                    ItemStack singleBox = slotStack.copyWithCount(1);
                    ItemStack leftoverBoxes = slotStack.copyWithCount(slotStack.getCount() - 1);
                    int remaining = mergeIntoShulkerComponent(singleBox, payload, amount, true);

                    if (remaining < amount) {
                        amount = remaining;
                        inv.setStack(i, singleBox);
                        inv.setStack(emptySlot, leftoverBoxes);
                    }
                } else {
                    amount = mergeIntoShulkerComponent(slotStack, payload, amount, true);
                    inv.setStack(i, slotStack);
                }
            }
            else if (slotStack.isEmpty()) {
                if (!inv.isValid(i, payload)) continue;

                int give = Math.min(amount, payload.getMaxCount());
                inv.setStack(i, payload.copyWithCount(give));
                amount -= give;
            }
        }
        return amount;
    }

    private static int mergeIntoShulkerComponent(ItemStack shulkerBox, ItemStack payload, int amount, boolean allowEmptySlots) {
        net.minecraft.component.type.ContainerComponent container = shulkerBox.getOrDefault(net.minecraft.component.DataComponentTypes.CONTAINER, net.minecraft.component.type.ContainerComponent.DEFAULT);

        int size = 27;
        net.minecraft.util.collection.DefaultedList<ItemStack> items = net.minecraft.util.collection.DefaultedList.ofSize(size, ItemStack.EMPTY);

        int index = 0;
        for (ItemStack inner : container.iterateNonEmpty()) {
            items.set(index++, inner.copy());
        }

        boolean modified = false;
        for (int j = 0; j < size && amount > 0; j++) {
            ItemStack innerSlot = items.get(j);

            if (allowEmptySlots && innerSlot.isEmpty()) {
                int give = Math.min(amount, payload.getMaxCount());
                items.set(j, payload.copyWithCount(give));
                amount -= give;
                modified = true;
            } else if (!allowEmptySlots && ItemStack.areItemsAndComponentsEqual(innerSlot, payload) && innerSlot.getCount() < innerSlot.getMaxCount()) {
                int space = innerSlot.getMaxCount() - innerSlot.getCount();
                int give = Math.min(amount, space);
                innerSlot.increment(give);
                amount -= give;
                modified = true;
            }
        }

        if (modified) {
            shulkerBox.set(net.minecraft.component.DataComponentTypes.CONTAINER, net.minecraft.component.type.ContainerComponent.fromStacks(items));
        }
        return amount;
    }

    private static int autoCompressLooseItems(Inventory inv, ItemStack emptyShulkerPayload, int amount) {
        // Backpacker logic: Automatically converts loose items inside targeted chests into compressed shulker items if empty boxes are present
        for (int i = 0; i < inv.size() && amount > 0; i++) {
            ItemStack chestStack = inv.getStack(i);

            if (!chestStack.isEmpty() && !isShulkerBox(chestStack.getItem())) {
                net.minecraft.util.collection.DefaultedList<ItemStack> newShulkerItems = net.minecraft.util.collection.DefaultedList.ofSize(27, ItemStack.EMPTY);
                newShulkerItems.set(0, chestStack.copy());

                ItemStack swappedShulker = emptyShulkerPayload.copyWithCount(1);
                swappedShulker.set(net.minecraft.component.DataComponentTypes.CONTAINER, net.minecraft.component.type.ContainerComponent.fromStacks(newShulkerItems));

                inv.setStack(i, swappedShulker);
                amount -= 1;
            }
        }
        return amount;
    }

    private static int calculatePlayerSpace(net.minecraft.entity.player.PlayerInventory playerInv, ItemStack target) {
        int space = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = playerInv.getStack(i);
            if (stack.isEmpty()) {
                space += target.getMaxCount();
            } else if (ItemStack.areItemsAndComponentsEqual(stack, target)) {
                space += (target.getMaxCount() - stack.getCount());
            }
        }
        return space;
    }

    // Exposes a wrapper layout to treat a nested Shulker Box item component instance exactly like a standard vanilla Block Inventory interface.
    public static class ShulkerItemInventory implements Inventory {
        private final ItemStack shulkerStack;
        private final net.minecraft.util.collection.DefaultedList<ItemStack> items = net.minecraft.util.collection.DefaultedList.ofSize(27, ItemStack.EMPTY);

        public ShulkerItemInventory(ItemStack stack) {
            this.shulkerStack = stack;
            net.minecraft.component.type.ContainerComponent container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
            if (container != null) {
                int i = 0;
                for (ItemStack inner : container.iterateNonEmpty()) {
                    if (i < 27) items.set(i++, inner.copy());
                }
            }
        }

        @Override public int size() { return 27; }
        @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getStack(int slot) { return items.get(slot); }
        @Override public ItemStack removeStack(int slot, int amount) {
            ItemStack result = net.minecraft.inventory.Inventories.splitStack(items, slot, amount);
            if (!result.isEmpty()) markDirty();
            return result;
        }
        @Override public ItemStack removeStack(int slot) {
            ItemStack result = net.minecraft.inventory.Inventories.removeStack(items, slot);
            markDirty();
            return result;
        }
        @Override public void setStack(int slot, ItemStack stack) {
            items.set(slot, stack);
            markDirty();
        }
        @Override public void markDirty() {
            shulkerStack.set(net.minecraft.component.DataComponentTypes.CONTAINER, net.minecraft.component.type.ContainerComponent.fromStacks(items));
        }
        @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { return true; }
        @Override public void clear() { items.clear(); markDirty(); }

    }
}
package svemocan.vanilla_storage_interface;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import svemocan.VanillaStorageInterface;
import svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler;
import svemocan.vanilla_storage_interface.network.StorageActionPayload;

public class StorageMutator {

    private static final java.util.Map<java.util.UUID, Long> defragCooldowns = new java.util.HashMap<>();

    public static Container getInventoryForHandler(StorageInterfaceScreenHandler handler, ServerPlayer player) {
        int mode = handler.getTerminalMode();
        Level world = player.level();

        if (mode == 0) {
            BlockPos pos = handler.getInterfacePos();
            net.minecraft.world.level.block.state.BlockState state = world.getBlockState(pos);
            if (state.hasProperty(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING)) {
                net.minecraft.core.Direction facing = state.getValue(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING);
                return HopperBlockEntity.getContainerAt(world, pos.relative(facing.getOpposite()));
            }
            return null;
        } else if (mode == 1) {
            return player.getEnderChestInventory();
        } else if (mode == 2) {
            ItemStack shulkerStack = player.getInventory().getItem(handler.getShulkerSlot());
            if (isShulkerBox(shulkerStack.getItem())) {
                return new ShulkerItemInventory(shulkerStack);
            }
            return null;
        } else if (mode == 3) {
            final Inventory pInv = player.getInventory();
            return new Container() {
                @Override public int getContainerSize() { return 27; }
                @Override public boolean isEmpty() {
                    for (int i = 9; i < 36; i++) {
                        if (!pInv.getItem(i).isEmpty()) return false;
                    }
                    return true;
                }
                @Override public ItemStack getItem(int slot) { return pInv.getItem(slot + 9); }
                @Override public ItemStack removeItem(int slot, int amount) { return pInv.removeItem(slot + 9, amount); }
                @Override public ItemStack removeItemNoUpdate(int slot) { return pInv.removeItemNoUpdate(slot + 9); }
                @Override public void setItem(int slot, ItemStack stack) { pInv.setItem(slot + 9, stack); }
                @Override public void setChanged() { pInv.setChanged(); }
                @Override public boolean stillValid(Player player) { return true; }
                @Override public void clearContent() { for (int i = 9; i < 36; i++) pInv.setItem(i, ItemStack.EMPTY); }
            };
        }
        return null;
    }

    public static void handleAction(ServerPlayer player, StorageActionPayload payload) {
        if (!(player.containerMenu instanceof svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler handler)) return;

        Level world = player.level();
        BlockPos interfacePos = handler.getInterfacePos();
        String action = payload.action();
        ItemStack targetStack = payload.stack();
        int amountNeeded = payload.amount();

        if (action.equals("CLEAR_DISPLAY") || action.equals("SET_DISPLAY") || action.equals("SCALE_DISPLAY") || action.equals("ROTATE_DISPLAY") || action.equals("RESET_DISPLAY")) {
            if (handler.getTerminalMode() == 0) {
                BlockEntity be = world.getBlockEntity(interfacePos);
                if (be instanceof StorageInterfaceBlockEntity interfaceBe) {
                    if (action.equals("CLEAR_DISPLAY")) {
                        interfaceBe.setDisplayItem(new ItemStack(net.minecraft.world.item.Items.STRUCTURE_VOID));
                    } else if (action.equals("SET_DISPLAY")) {
                        ItemStack safeCursor = player.containerMenu.getCarried();
                        interfaceBe.setDisplayItem(safeCursor.isEmpty() ? ItemStack.EMPTY : safeCursor.copyWithCount(1));
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

        Container inv = getInventoryForHandler(handler, player);
        boolean isVirtualTerminal = (handler.getTerminalMode() != 0);

        if (inv == null) return;

        // --- NATIVE VANILLA UI INTERACTIONS ---
        if (action.equals("EXTRACT")) {
            ItemStack cursorStack = player.containerMenu.getCarried();
            if (cursorStack.isEmpty() || (itemsMatch(cursorStack, targetStack) && cursorStack.getCount() < cursorStack.getMaxStackSize())) {
                int spaceInCursor = cursorStack.isEmpty() ? targetStack.getMaxStackSize() : cursorStack.getMaxStackSize() - cursorStack.getCount();
                int maxToTake = Math.min(amountNeeded, spaceInCursor);

                if (maxToTake > 0) {
                    ItemStack extractedItems = executeExtract(inv, targetStack, maxToTake);
                    if (extractedItems != null && !extractedItems.isEmpty()) {
                        if (cursorStack.isEmpty()) {
                            player.containerMenu.setCarried(extractedItems);
                        } else {
                            cursorStack.grow(extractedItems.getCount());
                            player.containerMenu.setCarried(cursorStack);
                        }
                    }
                }
            }
        }
        else if (action.equals("SHIFT_EXTRACT") || action.equals("EXTRACT_ALL")) {
            int[] targets = getTargetSlots(action, amountNeeded, handler.getTerminalMode());
            int space = calculatePlayerSpace(player.getInventory(), targetStack, targets);
            int takeAmount = action.equals("SHIFT_EXTRACT") ? Math.min(amountNeeded, space) : space;

            if (takeAmount > 0) {
                ItemStack extractedItems = executeExtract(inv, targetStack, takeAmount);
                if (extractedItems != null && !extractedItems.isEmpty()) {
                    insertIntoPlayer(player, extractedItems, targets);
                    if (extractedItems.getCount() > 0) {
                        executeInsert(inv, extractedItems, extractedItems.getCount());
                    }
                }
            }
        }
        else if (action.equals("INSERT_CURSOR")) {
            ItemStack cursorStack = player.containerMenu.getCarried();
            if (!cursorStack.isEmpty() && (targetStack.isEmpty() || itemsMatch(cursorStack, targetStack))) {
                int amountToInsert = Math.min(amountNeeded, cursorStack.getCount());
                int leftOver = executeInsert(inv, cursorStack, amountToInsert);
                int successfullyInserted = amountToInsert - leftOver;

                if (successfullyInserted > 0) {
                    cursorStack.shrink(successfullyInserted);
                }
            }
        }
        else if (action.equals("QUICK_MOVE")) {
            net.minecraft.world.inventory.Slot slot = handler.slots.get(amountNeeded);
            if (slot != null && slot.hasItem()) {
                ItemStack stack = slot.getItem();
                if (handler.getTerminalMode() == 3 && slot.index < 27) {
                    int[] hotbarSlots = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
                    insertIntoPlayer(player, stack, hotbarSlots);
                    if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
                    else slot.setChanged();
                } else {
                    int leftOver = executeInsert(inv, stack, stack.getCount());
                    stack.setCount(leftOver);
                    if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
                    else slot.setChanged();
                }
            }
        }
        else if (action.equals("THROW")) {
            int takeAmount = Math.min(amountNeeded, targetStack.getMaxStackSize());
            ItemStack extracted = executeExtract(inv, targetStack, takeAmount);
            if (extracted != null && !extracted.isEmpty()) {
                player.drop(extracted, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        else if (action.equals("INSERT_ALL_MATCHING")) {
            int[] sources = getSourceSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i : sources) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && itemsMatch(stack, targetStack)) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                    int leftover = executeInsert(inv, stack.copy(), stack.getCount());
                    if (leftover > 0) player.getInventory().setItem(i, stack.copyWithCount(leftover));
                }
            }
        }
        else if (action.equals("DUMP_INVENTORY")) {
            int[] sources = getSourceSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i : sources) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    if (handler.getTerminalMode() == 3 && isShulkerBox(stack.getItem())) continue;

                    player.getInventory().setItem(i, ItemStack.EMPTY);
                    int leftover = executeInsert(inv, stack.copy(), stack.getCount());
                    if (leftover > 0) player.getInventory().setItem(i, stack.copyWithCount(leftover));
                }
            }
        }
        else if (action.equals("REFILL_INVENTORY")) {
            int[] targets = getTargetSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i : targets) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.getCount() < stack.getMaxStackSize()) {
                    int needed = stack.getMaxStackSize() - stack.getCount();
                    ItemStack extracted = executeExtract(inv, stack.copy(), needed);
                    if (extracted != null && !extracted.isEmpty()) {
                        stack.grow(extracted.getCount());
                    }
                }
            }
        }

        // --- CORE IPN HOTKEY ROUTING HANDLERS ---
        else if (action.equals("IPN_MOVE_MATCHING_TO_STORAGE")) {
            int[] sources = getSourceSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i : sources) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && itemsMatch(stack, targetStack)) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                    int leftover = executeInsert(inv, stack.copy(), stack.getCount());
                    if (leftover > 0) player.getInventory().setItem(i, stack.copyWithCount(leftover));
                }
            }
        }
        else if (action.equals("IPN_MOVE_MATCHING_TO_PLAYER")) {
            int[] targets = getTargetSlots(action, amountNeeded, handler.getTerminalMode());
            int space = calculatePlayerSpace(player.getInventory(), targetStack, targets);
            if (space > 0) {
                ItemStack extractedItems = executeExtract(inv, targetStack, space);
                if (extractedItems != null && !extractedItems.isEmpty()) {
                    insertIntoPlayer(player, extractedItems, targets);
                    if (extractedItems.getCount() > 0) executeInsert(inv, extractedItems, extractedItems.getCount());
                }
            }
        }
        else if (action.equals("IPN_MOVE_ALL_TO_STORAGE")) {
            int[] sources = getSourceSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i : sources) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    if (handler.getTerminalMode() == 3 && isShulkerBox(stack.getItem())) continue;

                    player.getInventory().setItem(i, ItemStack.EMPTY);
                    int leftover = executeInsert(inv, stack.copy(), stack.getCount());
                    if (leftover > 0) player.getInventory().setItem(i, stack.copyWithCount(leftover));
                }
            }
        }
        else if (action.equals("IPN_MOVE_ALL_TO_PLAYER")) {
            int[] targets = getTargetSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    if (isShulkerBox(stack.getItem())) {
                        net.minecraft.world.item.component.ItemContainerContents container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
                        if (container != null) {
                            for (ItemStackTemplate template : container.nonEmptyItems()) {
                                ItemStack inner = template.create();
                                if (!inner.isEmpty()) {
                                    int space = calculatePlayerSpace(player.getInventory(), inner, targets);
                                    if (space > 0) {
                                        ItemStack extracted = executeExtract(inv, inner.copy(), space);
                                        insertIntoPlayer(player, extracted, targets);
                                        if (extracted.getCount() > 0) executeInsert(inv, extracted, extracted.getCount());
                                    }
                                }
                            }
                        }
                    } else {
                        int space = calculatePlayerSpace(player.getInventory(), stack, targets);
                        if (space > 0) {
                            ItemStack extracted = executeExtract(inv, stack.copy(), space);
                            insertIntoPlayer(player, extracted, targets);
                            if (extracted.getCount() > 0) executeInsert(inv, extracted, extracted.getCount());
                        }
                    }
                }
            }
        }
        else if (action.equals("IPN_REFILL_STORAGE")) {
            int[] sources = getSourceSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && !isShulkerBox(stack.getItem())) {
                    handleStacks(player, inv, sources, stack);
                } else if (isShulkerBox(stack.getItem())) {
                    net.minecraft.world.item.component.ItemContainerContents container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
                    if (container != null) {
                        for (ItemStackTemplate template : container.nonEmptyItems()) {
                            ItemStack inner = template.create();
                            if (!inner.isEmpty()) {
                                handleStacks(player, inv, sources, inner);
                            }
                        }
                    }
                }
            }
        }
        else if (action.equals("IPN_REFILL_PLAYER")) {
            int[] targets = getTargetSlots(action, amountNeeded, handler.getTerminalMode());

            // Step 1: Collect unique item types currently in the player's inventory to avoid double-processing
            java.util.List<ItemStack> uniqueTypes = new java.util.ArrayList<>();
            for (int i : targets) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    boolean exists = false;
                    for (ItemStack u : uniqueTypes) {
                        if (itemsMatch(u, stack)) { exists = true; break; }
                    }
                    if (!exists) uniqueTypes.add(stack.copy());
                }
            }

            // Step 2: For each unique item, drain as much of that item type from storage as the player can hold
            for (ItemStack uniqueType : uniqueTypes) {
                int space = calculatePlayerSpace(player.getInventory(), uniqueType, targets);

                if (space > 0) {
                    ItemStack extracted = executeExtract(inv, uniqueType, space);
                    if (extracted != null && !extracted.isEmpty()) {
                        insertIntoPlayer(player, extracted, targets);

                        // Fallback safety net: if items somehow couldn't fit, refund them directly back to storage
                        if (extracted.getCount() > 0) {
                            executeInsert(inv, extracted, extracted.getCount());
                        }
                    }
                }
            }
        }

        // --- DEFRAGMENTATION HANDLERS ---
        else if (action.equals("DEFRAGMENT_SHULKERS") || action.equals("DEFRAGMENT_ALL")) {
            long currentTime = net.minecraft.util.Util.getMillis();
            long lastUsed = defragCooldowns.getOrDefault(player.getUUID(), 0L);

            if (currentTime - lastUsed < 3000) return;
            defragCooldowns.put(player.getUUID(), currentTime);

            boolean fullDefrag = action.equals("DEFRAGMENT_ALL");
            executeDefragment(player, inv, fullDefrag);
            world.playSound(null, interfacePos, net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);

            // Safety net: Kick any other players viewing the exact same storage to prevent UI desyncing
            for (ServerPlayer otherPlayer : player.level().getServer().getPlayerList().getPlayers()) {
                if (otherPlayer != player && otherPlayer.containerMenu instanceof svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler otherHandler) {
                    if (otherHandler.getTerminalMode() == handler.getTerminalMode() &&
                            otherHandler.getInterfacePos().equals(handler.getInterfacePos()) &&
                            otherHandler.getShulkerSlot() == handler.getShulkerSlot()) {

                        otherPlayer.closeContainer();
                        otherPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cStorage was reorganized. Please reopen the interface."), true);
                    }
                }
            }
        }

        inv.setChanged();
        handler.broadcastChanges();

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

    private static void handleStacks(ServerPlayer player, Container inv, int[] sources, ItemStack stack) {
        for (int slot : sources) {
            ItemStack playerStack = player.getInventory().getItem(slot);
            if (!playerStack.isEmpty() && itemsMatch(playerStack, stack)) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                int leftover = executeInsert(inv, playerStack.copy(), playerStack.getCount());
                if (leftover > 0) player.getInventory().setItem(slot, playerStack.copyWithCount(leftover));
            }
        }
    }

    private static int calculatePlayerSpace(net.minecraft.world.entity.player.Inventory playerInv, ItemStack target, int[] targetSlots) {
        int space = 0;
        int max = target.getMaxStackSize();
        for (int i : targetSlots) {
            ItemStack s = playerInv.getItem(i);
            if (s.isEmpty()) space += max;
            else if (itemsMatch(s, target)) space += (max - s.getCount());
        }
        return space;
    }

    private static void insertIntoPlayer(ServerPlayer player, ItemStack stack, int[] targetSlots) {
        int max = stack.getMaxStackSize();
        for (int i : targetSlots) {
            if (stack.isEmpty()) break;
            ItemStack current = player.getInventory().getItem(i);
            if (!current.isEmpty() && itemsMatch(current, stack) && current.getCount() < max) {
                int add = Math.min(stack.getCount(), max - current.getCount());
                current.grow(add);
                stack.shrink(add);
            }
        }
        for (int i : targetSlots) {
            if (stack.isEmpty()) break;
            ItemStack current = player.getInventory().getItem(i);
            if (current.isEmpty()) {
                int add = Math.min(stack.getCount(), max);
                player.getInventory().setItem(i, stack.copyWithCount(add));
                stack.shrink(add);
            }
        }
    }

    private static int[] getSourceSlots(String action, int amountNeeded, int terminalMode) {
        if (terminalMode == 3) {
            // In Mode 3, storage is main inventory (9-35), so sources dumped to storage are hotbar slots (0-8)
            int[] hotbar = new int[9];
            for (int i = 0; i < 9; i++) hotbar[i] = i;
            return hotbar;
        }

        if (action.startsWith("IPN_")) {
            boolean includeHotbar = (amountNeeded & 1) != 0 || (amountNeeded & 4) != 0;
            if (!includeHotbar) {
                int[] invOnly = new int[27];
                for (int i = 0; i < 27; i++) invOnly[i] = i + 9;
                return invOnly;
            }
        }

        int[] allSlots = new int[36];
        for (int i = 0; i < 36; i++) allSlots[i] = i;
        return allSlots;
    }

    private static int[] getTargetSlots(String action, int amountNeeded, int terminalMode) {
        if (terminalMode == 3) {
            // In Mode 3, target slots prioritize hotbar (0-8), followed by main inventory (9-35)
            int[] slots = new int[36];
            for (int i = 0; i < 9; i++) slots[i] = i;
            for (int i = 9; i < 36; i++) slots[i] = i;
            return slots;
        }

        if (action.startsWith("IPN_")) {
            boolean includeHotbar = (amountNeeded & 1) != 0 || (amountNeeded & 4) != 0;
            if (!includeHotbar) {
                int[] invOnly = new int[27];
                for (int i = 0; i < 27; i++) invOnly[i] = i + 9;
                return invOnly;
            }
        }

        int[] allSlots = new int[36];
        for (int i = 0; i < 36; i++) allSlots[i] = i;
        return allSlots;
    }

    private static ItemStack executeExtract(Container inv, ItemStack targetStack, int amountNeeded) {
        if (targetStack == null || targetStack.isEmpty() || amountNeeded <= 0) {
            return ItemStack.EMPTY;
        }

        int extracted = 0;
        ItemStack safeReference = targetStack.copy();

        for (int i = 0; i < inv.getContainerSize() && extracted < amountNeeded; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            if (isShulkerBox(stack.getItem())) {
                net.minecraft.world.item.component.ItemContainerContents container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
                if (container != null) {
                    int emptySlot = -1;

                    if (stack.getCount() > 1) {
                        for (int j = 0; j < inv.getContainerSize(); j++) {
                            if (inv.getItem(j).isEmpty()) { emptySlot = j; break; }
                        }
                        if (emptySlot == -1) continue;
                    }

                    net.minecraft.core.NonNullList<ItemStack> shulkerItems = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
                    int slot = 0;
                    boolean modified = false;

                    for (ItemStackTemplate template : container.nonEmptyItems()) {
                        ItemStack copied = template.create();
                        if (extracted < amountNeeded && itemsMatch(copied, targetStack)) {
                            if (safeReference.isEmpty()) {
                                safeReference = copied.copy();
                            }
                            int take = Math.min(amountNeeded - extracted, copied.getCount());
                            extracted += take;
                            copied.shrink(take);
                            modified = true;
                        }
                        if (!copied.isEmpty()) shulkerItems.set(slot++, copied);
                    }
                    if (modified) {
                        if (stack.getCount() > 1) {
                            ItemStack singleBox = stack.copyWithCount(1);
                            singleBox.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(shulkerItems));
                            ItemStack leftoverBoxes = stack.copyWithCount(stack.getCount() - 1);
                            inv.setItem(i, singleBox);
                            inv.setItem(emptySlot, leftoverBoxes);
                        } else {
                            stack.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(shulkerItems));
                            inv.setItem(i, stack);
                        }
                    }
                }
            }
            else if (itemsMatch(stack, targetStack)) {
                if (safeReference.isEmpty()) {
                    safeReference = stack.copy();
                }
                int take = Math.min(amountNeeded - extracted, stack.getCount());
                extracted += take;
                stack.shrink(take);
            }
        }

        if (extracted > 0 && !safeReference.isEmpty()) {
            return safeReference.copyWithCount(extracted);
        }
        return ItemStack.EMPTY;
    }

    public static boolean insertDirectly(net.minecraft.server.level.ServerPlayer player, svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler handler, ItemStack stackToInsert) {
        Level world = player.level();
        Container inv = getInventoryForHandler(handler, player);
        boolean isVirtualTerminal = (handler.getTerminalMode() != 0);

        if (inv == null) return false;

        int originalCount = stackToInsert.getCount();
        int leftOver = executeInsert(inv, stackToInsert, originalCount);
        stackToInsert.setCount(leftOver);

        inv.setChanged();
        handler.broadcastChanges();

        svemocan.vanilla_storage_interface.StorageAggregator.VirtualInventory vi;
        if (handler.getTerminalMode() == 3) {
            vi = StorageAggregator.buildMode3(player.getInventory());
        } else if (isVirtualTerminal) {
            vi = StorageAggregator.buildFromInventory(world, inv);
        } else {
            vi = StorageAggregator.build(world, handler.getInterfacePos());
        }

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player,
                new svemocan.vanilla_storage_interface.network.StorageSyncPayload(vi.items, vi.emptyShulkerSlots, handler.getInterfacePos(), isVirtualTerminal)
        );

        return leftOver < originalCount;
    }

    private static int executeInsert(Container inv, ItemStack payload, int amount) {
        if (payload.isEmpty() || amount <= 0) return amount;

        boolean isShulker = isShulkerBox(payload.getItem());
        if (isShulker && (inv instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity || inv instanceof ShulkerItemInventory)) {
            return amount;
        }
        if (isShulker && payload.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY).nonEmptyItems().iterator().hasNext()) {
            return amount;
        }

        // Phase 1: Stack on identical loose items in Chest/Inventory
        amount = fillMatchingLooseSlots(inv, payload, amount, false);
        if (amount == 0) return 0;

        // Phase 2: Inject into already occupied Shulkers holding the exact same item
        amount = fillMatchingShulkers(inv, payload, amount, isShulker);
        if (amount == 0) return 0;

        // Phase 3: Fill completely empty physical slots with the loose items
        amount = fillEmptyLooseSlots(inv, payload, amount);
        if (amount == 0) return 0;

        // Phase 4: Put items into any Shulker that has blank open slots
        amount = fillEmptyShulkerSlots(inv, payload, amount, isShulker);
        if (amount == 0) return 0;

        // Phase 5: Auto-compress into empty shulker boxes
        if (isShulker && !payload.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY).nonEmptyItems().iterator().hasNext()) {
            amount = autoCompressLooseItems(inv, payload, amount);
        }

        return amount;
    }

    private static int fillMatchingLooseSlots(Container inv, ItemStack payload, int amount, boolean allowEmpty) {
        int max = payload.getMaxStackSize();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == payload) continue;
            if (!inv.canPlaceItem(i, payload)) continue;

            if (!stack.isEmpty() && !isShulkerBox(stack.getItem()) && itemsMatch(stack, payload)) {
                int space = max - stack.getCount();
                if (space > 0) {
                    int give = Math.min(amount, space);
                    stack.grow(give);
                    amount -= give;
                }
            }
        }
        return amount;
    }

    private static int fillEmptyLooseSlots(Container inv, ItemStack payload, int amount) {
        int max = payload.getMaxStackSize();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == payload) continue;
            if (!inv.canPlaceItem(i, payload)) continue;

            if (stack.isEmpty()) {
                int give = Math.min(amount, max);
                inv.setItem(i, payload.copyWithCount(give));
                amount -= give;
            }
        }
        return amount;
    }

    private static int fillMatchingShulkers(Container inv, ItemStack payload, int amount, boolean isShulker) {
        if (isShulker) return amount;

        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack slotStack = inv.getItem(i);

            if (isShulkerBox(slotStack.getItem())) {
                net.minecraft.world.item.component.ItemContainerContents container = slotStack.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY);
                boolean containsItem = false;
                for (ItemStackTemplate template : container.nonEmptyItems()) {
                    ItemStack inner = template.create();
                    if (itemsMatch(inner, payload)) { containsItem = true; break; }
                }

                if (containsItem) {
                    if (slotStack.getCount() > 1) {
                        int emptySlot = -1;
                        for (int j = 0; j < inv.getContainerSize(); j++) {
                            if (inv.getItem(j).isEmpty()) { emptySlot = j; break; }
                        }
                        if (emptySlot == -1) continue;

                        ItemStack singleBox = slotStack.copyWithCount(1);
                        ItemStack leftoverBoxes = slotStack.copyWithCount(slotStack.getCount() - 1);
                        int remaining = mergeIntoShulkerComponent(singleBox, payload, amount, true);

                        if (remaining < amount) {
                            amount = remaining;
                            inv.setItem(i, singleBox);
                            inv.setItem(emptySlot, leftoverBoxes);
                        }
                    } else {
                        amount = mergeIntoShulkerComponent(slotStack, payload, amount, true);
                    }
                }
            }
        }
        return amount;
    }

    private static int fillEmptyShulkerSlots(Container inv, ItemStack payload, int amount, boolean isShulker) {
        if (isShulker) return amount;

        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack slotStack = inv.getItem(i);

            if (isShulkerBox(slotStack.getItem())) {
                if (slotStack.getCount() > 1) {
                    int emptySlot = -1;
                    for (int j = 0; j < inv.getContainerSize(); j++) {
                        if (inv.getItem(j).isEmpty()) { emptySlot = j; break; }
                    }
                    if (emptySlot == -1) continue;

                    ItemStack singleBox = slotStack.copyWithCount(1);
                    ItemStack leftoverBoxes = slotStack.copyWithCount(slotStack.getCount() - 1);
                    int remaining = mergeIntoShulkerComponent(singleBox, payload, amount, true);

                    if (remaining < amount) {
                        amount = remaining;
                        inv.setItem(i, singleBox);
                        inv.setItem(emptySlot, leftoverBoxes);
                    }
                } else {
                    amount = mergeIntoShulkerComponent(slotStack, payload, amount, true);
                }
            }
        }
        return amount;
    }

    private static int mergeIntoShulkerComponent(ItemStack shulkerBox, ItemStack payload, int amount, boolean allowEmptySlots) {
        net.minecraft.world.item.component.ItemContainerContents container = shulkerBox.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY);

        int size = 27;
        net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(size, ItemStack.EMPTY);

        int index = 0;
        for (ItemStackTemplate template : container.nonEmptyItems()) {
            items.set(index++, template.create());
        }

        boolean modified = false;

        // Phase 1: Stack onto existing matching items
        for (int j = 0; j < size && amount > 0; j++) {
            ItemStack innerSlot = items.get(j);
            if (!innerSlot.isEmpty() && itemsMatch(innerSlot, payload) && innerSlot.getCount() < innerSlot.getMaxStackSize()) {
                int space = innerSlot.getMaxStackSize() - innerSlot.getCount();
                int give = Math.min(amount, space);
                innerSlot.grow(give);
                amount -= give;
                modified = true;
            }
        }

        // Phase 2: If allowed, fill into completely empty shulker slots
        if (allowEmptySlots && amount > 0) {
            for (int j = 0; j < size && amount > 0; j++) {
                ItemStack innerSlot = items.get(j);
                if (innerSlot.isEmpty()) {
                    int give = Math.min(amount, payload.getMaxStackSize());
                    items.set(j, payload.copyWithCount(give));
                    amount -= give;
                    modified = true;
                }
            }
        }

        if (modified) {
            shulkerBox.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
        }
        return amount;
    }

    private static int autoCompressLooseItems(Container inv, ItemStack emptyShulkerPayload, int amount) {
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack chestStack = inv.getItem(i);
            if (chestStack == emptyShulkerPayload) continue;

            if (!chestStack.isEmpty() && !isShulkerBox(chestStack.getItem())) {
                net.minecraft.core.NonNullList<ItemStack> newShulkerItems = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
                newShulkerItems.set(0, chestStack.copy());

                ItemStack swappedShulker = emptyShulkerPayload.copyWithCount(1);
                swappedShulker.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(newShulkerItems));

                inv.setItem(i, swappedShulker);
                amount -= 1;
            }
        }
        return amount;
    }

    private static void executeDefragment(ServerPlayer player, Container inv, boolean fullDefrag) {
        java.util.List<ItemStack> pool = new java.util.ArrayList<>();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            if (isShulkerBox(stack.getItem()) && stack.getCount() == 1) {
                net.minecraft.world.item.component.ItemContainerContents container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
                if (container != null) {
                    for (ItemStackTemplate template : container.nonEmptyItems()) {
                        addToPool(pool, template.create());
                    }
                }
                stack.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY);
            } else if (fullDefrag && !isShulkerBox(stack.getItem())) {
                addToPool(pool, stack.copy());
                inv.setItem(i, ItemStack.EMPTY);
            }
        }

        if (pool.isEmpty()) return;

        svemocan.vanilla_storage_interface.config.VanillaStorageConfig.DefragSortMode mode =
                VanillaStorageInterface.CONFIG.defragmentationSortMode;

        java.util.Map<String, Integer> totalQuantities = new java.util.HashMap<>();
        if (mode == svemocan.vanilla_storage_interface.config.VanillaStorageConfig.DefragSortMode.QUANTITY_DESCENDING ||
                mode == svemocan.vanilla_storage_interface.config.VanillaStorageConfig.DefragSortMode.QUANTITY_ASCENDING) {
            for (ItemStack s : pool) {
                String key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString() + s.getHoverName().getString();
                totalQuantities.put(key, totalQuantities.getOrDefault(key, 0) + s.getCount());
            }
        }

        pool.sort((a, b) -> {
            if (mode == svemocan.vanilla_storage_interface.config.VanillaStorageConfig.DefragSortMode.QUANTITY_DESCENDING) {
                String keyA = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(a.getItem()).toString() + a.getHoverName().getString();
                String keyB = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(b.getItem()).toString() + b.getHoverName().getString();
                int cmp = Integer.compare(totalQuantities.getOrDefault(keyB, 0), totalQuantities.getOrDefault(keyA, 0));
                if (cmp != 0) return cmp;
                return a.getHoverName().getString().compareToIgnoreCase(b.getHoverName().getString());
            } else if (mode == svemocan.vanilla_storage_interface.config.VanillaStorageConfig.DefragSortMode.QUANTITY_ASCENDING) {
                String keyA = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(a.getItem()).toString() + a.getHoverName().getString();
                String keyB = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(b.getItem()).toString() + b.getHoverName().getString();
                int cmp = Integer.compare(totalQuantities.getOrDefault(keyA, 0), totalQuantities.getOrDefault(keyB, 0));
                if (cmp != 0) return cmp;
                return a.getHoverName().getString().compareToIgnoreCase(b.getHoverName().getString());
            } else if (mode == svemocan.vanilla_storage_interface.config.VanillaStorageConfig.DefragSortMode.REGISTRY_ID) {
                String idA = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(a.getItem()).toString();
                String idB = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(b.getItem()).toString();
                int cmp = idA.compareToIgnoreCase(idB);
                if (cmp != 0) return cmp;
            }
            int nameCmp = a.getHoverName().getString().compareToIgnoreCase(b.getHoverName().getString());
            if (nameCmp != 0) return nameCmp;
            return Integer.compare(b.getCount(), a.getCount());
        });

        int totalPhysicalSlots = inv.getContainerSize();
        int totalAvailableShulkers = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isShulkerBox(s.getItem()) && s.getCount() == 1) totalAvailableShulkers++;
        }

        // Apply chosen heuristic
        if (mode == svemocan.vanilla_storage_interface.config.VanillaStorageConfig.DefragSortMode.SMART_PACKING) {
            pool = applyJardaHeuristic(pool);
        } else if (mode == svemocan.vanilla_storage_interface.config.VanillaStorageConfig.DefragSortMode.ADAPTIVE_PACKING) {
            pool = applyAdaptivePacking(pool, totalAvailableShulkers, totalPhysicalSlots);
        }

        // Re-inject items back into single Shulker Boxes first
        for (int i = 0; i < inv.getContainerSize() && !pool.isEmpty(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isShulkerBox(stack.getItem()) && stack.getCount() == 1) {
                net.minecraft.core.NonNullList<ItemStack> packedItems = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
                int slot = 0;
                while (slot < 27 && !pool.isEmpty()) {
                    packedItems.set(slot++, pool.remove(0));
                }
                stack.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(packedItems));
            }
        }

        // Place any remaining overflow back into empty loose storage slots
        for (int i = 0; i < inv.getContainerSize() && !pool.isEmpty(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                inv.setItem(i, pool.remove(0));
            }
        }

        // Fallback safety net: if items somehow exceeded available storage, drop them safely to prevent any voiding
        while (!pool.isEmpty()) {
            ItemStack overflow = pool.remove(0);
            if (!overflow.isEmpty()) {
                player.drop(overflow, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
    }

    private static void addToPool(java.util.List<ItemStack> pool, ItemStack stack) {
        if (stack.isEmpty()) return;
        int max = stack.getMaxStackSize();

        for (ItemStack p : pool) {
            if (itemsMatch(p, stack) && p.getCount() < max) {
                int give = Math.min(stack.getCount(), max - p.getCount());
                p.grow(give);
                stack.shrink(give);
                if (stack.isEmpty()) return;
            }
        }

        while (stack.getCount() > max) {
            pool.add(stack.copyWithCount(max));
            stack.shrink(max);
        }
        if (!stack.isEmpty()) {
            pool.add(stack);
        }
    }

    private static boolean itemsMatch(ItemStack s1, ItemStack s2) {
        if (ItemStack.isSameItemSameComponents(s1, s2)) return true;

        if (s1.getItem() instanceof net.minecraft.world.item.BannerItem && s1.getItem() == s2.getItem()) {
            boolean namesMatch = java.util.Objects.equals(s1.getHoverName(), s2.getHoverName());
            var p1 = s1.get(net.minecraft.core.component.DataComponents.BANNER_PATTERNS);
            var p2 = s2.get(net.minecraft.core.component.DataComponents.BANNER_PATTERNS);
            return namesMatch && java.util.Objects.equals(p1, p2);
        }
        return false;
    }

    public static boolean isShulkerBox(Item item) {
        return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static java.util.List<ItemStack> applyAdaptivePacking(java.util.List<ItemStack> inputPool, int availableShulkers, int totalContainerSlots) {
        if (inputPool.isEmpty()) return inputPool;

        int totalStacks = inputPool.size();
        double capacityRatio = (double) (availableShulkers * 27) / (double) Math.max(1, totalStacks);

        int threshold = 27; // Default: Strict packing
        if (capacityRatio > 2.0) {
            threshold = 9; // High capacity headroom: isolate medium batches into dedicated boxes
        } else if (capacityRatio > 1.2) {
            threshold = 18; // Moderate capacity headroom: isolate larger batches
        }

        java.util.Map<Item, java.util.List<ItemStack>> groups = new java.util.LinkedHashMap<>();
        for (ItemStack s : inputPool) {
            groups.computeIfAbsent(s.getItem(), k -> new java.util.ArrayList<>()).add(s);
        }

        java.util.List<ItemStack> result = new java.util.ArrayList<>();
        java.util.List<ItemStack> leftovers = new java.util.ArrayList<>();

        for (java.util.List<ItemStack> group : groups.values()) {
            if (group.size() >= threshold) {
                result.addAll(group);
                // Fill up to the next 27-slot boundary to keep the Shulker dedicated exclusively to this item
                int pad = 27 - (group.size() % 27);
                if (pad < 27) {
                    for (int i = 0; i < pad; i++) result.add(ItemStack.EMPTY);
                }
            } else {
                leftovers.addAll(group);
            }
        }

        // Repack smaller item leftovers into remaining Shulker capacity using Smart Packing
        result.addAll(applyJardaHeuristic(leftovers));
        return result;
    }

    private static java.util.List<ItemStack> applyJardaHeuristic(java.util.List<ItemStack> inputPool) {
        if (inputPool.isEmpty()) return inputPool;

        java.util.Map<Item, java.util.List<ItemStack>> itemGroups = new java.util.LinkedHashMap<>();
        for (ItemStack stack : inputPool) {
            itemGroups.computeIfAbsent(stack.getItem(), k -> new java.util.ArrayList<>()).add(stack);
        }

        java.util.List<ItemStack> result = new java.util.ArrayList<>();
        java.util.List<java.util.List<ItemStack>> unallocatedGroups = new java.util.ArrayList<>();

        // Phase 1: Pure Shulker Extraction (Strip out complete 27-slot chunks of identical items)
        for (java.util.List<ItemStack> group : itemGroups.values()) {
            while (group.size() >= 27) {
                for (int i = 0; i < 27; i++) {
                    result.add(group.remove(0));
                }
            }
            if (!group.isEmpty()) {
                unallocatedGroups.add(group);
            }
        }

        // Phase 2: First-Fit Decreasing Knapsack Repacking
        // Sort remaining item sets descending by size so large sets settle into Shulkers first
        unallocatedGroups.sort((a, b) -> Integer.compare(b.size(), a.size()));

        java.util.List<ItemStack> currentBox = new java.util.ArrayList<>();

        for (java.util.List<ItemStack> group : unallocatedGroups) {
            int needed = group.size();
            int remainingInBox = 27 - currentBox.size();

            if (needed <= remainingInBox) {
                // Group fits completely within current box
                currentBox.addAll(group);
                if (currentBox.size() == 27) {
                    result.addAll(currentBox);
                    currentBox.clear();
                }
            } else {
                // Group exceeds current box space: split it cleanly across boundaries
                int canTake = remainingInBox;
                for (int i = 0; i < canTake; i++) {
                    currentBox.add(group.remove(0));
                }
                result.addAll(currentBox);
                currentBox.clear();

                // Open new box and push leftovers
                currentBox.addAll(group);
            }
        }

        if (!currentBox.isEmpty()) {
            result.addAll(currentBox);
            // Pad the final box to ensure the iteration cycle completes cleanly
            int padding = 27 - currentBox.size();
            for (int i = 0; i < padding; i++) result.add(net.minecraft.world.item.ItemStack.EMPTY);
        }

        return result;
    }

    public static class ShulkerItemInventory implements Container {
        private final ItemStack shulkerStack;
        private final net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);

        public ShulkerItemInventory(ItemStack stack) {
            this.shulkerStack = stack;
            net.minecraft.world.item.component.ItemContainerContents container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
            if (container != null) {
                container.copyInto(items);
            }
        }

        @Override public int getContainerSize() { return 27; }
        @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getItem(int slot) { return items.get(slot); }
        @Override public ItemStack removeItem(int slot, int amount) {
            ItemStack result = net.minecraft.world.ContainerHelper.removeItem(items, slot, amount);
            if (!result.isEmpty()) setChanged();
            return result;
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            ItemStack result = net.minecraft.world.ContainerHelper.takeItem(items, slot);
            setChanged();
            return result;
        }
        @Override public void setItem(int slot, ItemStack stack) {
            items.set(slot, stack);
            setChanged();
        }
        @Override public void setChanged() {
            shulkerStack.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
        }
        @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return !isShulkerBox(stack.getItem()); }
        @Override public void clearContent() { items.clear(); setChanged(); }
    }
}

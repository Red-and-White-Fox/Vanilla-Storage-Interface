package svemocan.vanilla_storage_interface;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import svemocan.vanilla_storage_interface.network.StorageActionPayload;

public class StorageMutator {

    private static final java.util.Map<java.util.UUID, Long> defragCooldowns = new java.util.HashMap<>();

    public static void handleAction(ServerPlayerEntity player, StorageActionPayload payload) {
        if (!(player.currentScreenHandler instanceof svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler handler)) return;

        World world = player.getServerWorld();
        BlockPos interfacePos = handler.getInterfacePos();
        String action = payload.action();
        ItemStack targetStack = payload.stack();
        int amountNeeded = payload.amount();

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

            if (shulkerStack.getCount() > 1) {
                int emptySlot = player.getInventory().getEmptySlot();
                if (emptySlot != -1) {
                    ItemStack singleBox = shulkerStack.copyWithCount(1);
                    ItemStack leftover = shulkerStack.copyWithCount(shulkerStack.getCount() - 1);
                    player.getInventory().setStack(handler.getShulkerSlot(), singleBox);
                    player.getInventory().setStack(emptySlot, leftover);
                    shulkerStack = singleBox;
                } else {
                    return;
                }
            }

            if (isShulkerBox(shulkerStack.getItem())) inv = new ShulkerItemInventory(shulkerStack);
        } else if (handler.getTerminalMode() == 3) {
            final net.minecraft.entity.player.PlayerInventory pInv = player.getInventory();
            inv = new net.minecraft.inventory.Inventory() {
                @Override public int size() { return 27; }
                @Override public boolean isEmpty() {
                    for(int i=9; i<36; i++) if(!pInv.getStack(i).isEmpty()) return false;
                    return true;
                }
                @Override public ItemStack getStack(int slot) { return pInv.getStack(slot + 9); }
                @Override public ItemStack removeStack(int slot, int amount) { return pInv.removeStack(slot + 9, amount); }
                @Override public ItemStack removeStack(int slot) { return pInv.removeStack(slot + 9); }
                @Override public void setStack(int slot, ItemStack stack) { pInv.setStack(slot + 9, stack); }
                @Override public void markDirty() { pInv.markDirty(); }
                @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
                @Override public void clear() { for(int i=9; i<36; i++) pInv.setStack(i, ItemStack.EMPTY); }
            };
        }

        if (inv == null) return;

        int pStart = (payload.amount() == 1) ? 0 : 9;

        // --- NATIVE VANILLA UI INTERACTIONS ---
        if (action.equals("EXTRACT")) {
            ItemStack cursorStack = player.currentScreenHandler.getCursorStack();
            if (cursorStack.isEmpty() || (itemsMatch(cursorStack, targetStack) && cursorStack.getCount() < cursorStack.getMaxCount())) {
                int spaceInCursor = cursorStack.isEmpty() ? targetStack.getMaxCount() : cursorStack.getMaxCount() - cursorStack.getCount();
                int maxToTake = Math.min(amountNeeded, spaceInCursor);

                if (maxToTake > 0) {
                    ItemStack extractedItems = executeExtract(inv, targetStack, maxToTake);
                    if (extractedItems != null && !extractedItems.isEmpty()) {
                        if (cursorStack.isEmpty()) {
                            player.currentScreenHandler.setCursorStack(extractedItems);
                        } else {
                            cursorStack.increment(extractedItems.getCount());
                            player.currentScreenHandler.setCursorStack(cursorStack);
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
            ItemStack cursorStack = player.currentScreenHandler.getCursorStack();
            if (!cursorStack.isEmpty() && (targetStack.isEmpty() || itemsMatch(cursorStack, targetStack))) {
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
        else if (action.equals("THROW")) {
            int takeAmount = Math.min(amountNeeded, targetStack.getMaxCount());
            ItemStack extracted = executeExtract(inv, targetStack, takeAmount);
            if (extracted != null && !extracted.isEmpty()) {
                // true = retain ownership temporarily (so player doesn't instantly vacuum it back into their physical inventory)
                player.dropItem(extracted, false, true);
            }
        }
        else if (action.equals("INSERT_ALL_MATCHING")) {
            int[] sources = getSourceSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i : sources) {
                ItemStack stack = player.getInventory().getStack(i);
                if (!stack.isEmpty() && itemsMatch(stack, targetStack)) {
                    player.getInventory().setStack(i, ItemStack.EMPTY);
                    int leftover = executeInsert(inv, stack, stack.getCount());
                    if (leftover > 0) player.getInventory().setStack(i, stack.copyWithCount(leftover));
                }
            }
        }
        else if (action.equals("DUMP_INVENTORY")) {
            int[] sources = getSourceSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i : sources) {
                ItemStack stack = player.getInventory().getStack(i);
                if (!stack.isEmpty()) {
                    if (handler.getTerminalMode() == 3 && isShulkerBox(stack.getItem())) continue;

                    player.getInventory().setStack(i, ItemStack.EMPTY);
                    int leftover = executeInsert(inv, stack, stack.getCount());
                    if (leftover > 0) player.getInventory().setStack(i, stack.copyWithCount(leftover));
                }
            }
        }
        else if (action.equals("REFILL_INVENTORY")) {
            int[] targets = getTargetSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i : targets) {
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

        // --- CORE IPN HOTKEY ROUTING HANDLERS ---
        else if (action.equals("IPN_MOVE_MATCHING_TO_STORAGE")) {
            int[] sources = getSourceSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i : sources) {
                ItemStack stack = player.getInventory().getStack(i);
                if (!stack.isEmpty() && itemsMatch(stack, targetStack)) {
                    player.getInventory().setStack(i, ItemStack.EMPTY);
                    int leftover = executeInsert(inv, stack, stack.getCount());
                    if (leftover > 0) player.getInventory().setStack(i, stack.copyWithCount(leftover));
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
                ItemStack stack = player.getInventory().getStack(i);
                if (!stack.isEmpty()) {
                    if (handler.getTerminalMode() == 3 && isShulkerBox(stack.getItem())) continue;

                    player.getInventory().setStack(i, ItemStack.EMPTY);
                    int leftover = executeInsert(inv, stack, stack.getCount());
                    if (leftover > 0) player.getInventory().setStack(i, stack.copyWithCount(leftover));
                }
            }
        }
        else if (action.equals("IPN_MOVE_ALL_TO_PLAYER")) {
            int[] targets = getTargetSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty()) {
                    if (isShulkerBox(stack.getItem())) {
                        net.minecraft.component.type.ContainerComponent container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
                        if (container != null) {
                            for (ItemStack inner : container.iterateNonEmpty()) {
                                if (!inner.isEmpty()) {
                                    int space = calculatePlayerSpace(player.getInventory(), inner, targets);
                                    if (space > 0) {
                                        ItemStack extracted = executeExtract(inv, inner, space);
                                        insertIntoPlayer(player, extracted, targets);
                                        if (extracted.getCount() > 0) executeInsert(inv, extracted, extracted.getCount());
                                    }
                                }
                            }
                        }
                    } else {
                        int space = calculatePlayerSpace(player.getInventory(), stack, targets);
                        if (space > 0) {
                            ItemStack extracted = executeExtract(inv, stack, space);
                            insertIntoPlayer(player, extracted, targets);
                            if (extracted.getCount() > 0) executeInsert(inv, extracted, extracted.getCount());
                        }
                    }
                }
            }
        }
        else if (action.equals("IPN_REFILL_STORAGE")) {
            int[] sources = getSourceSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty() && !isShulkerBox(stack.getItem())) {
                    for (int p : sources) {
                        ItemStack pStack = player.getInventory().getStack(p);
                        if (itemsMatch(stack, pStack)) {
                            player.getInventory().setStack(p, ItemStack.EMPTY);
                            int leftover = executeInsert(inv, pStack, pStack.getCount());
                            if (leftover > 0) player.getInventory().setStack(p, pStack.copyWithCount(leftover));
                        }
                    }
                } else if (isShulkerBox(stack.getItem())) {
                    net.minecraft.component.type.ContainerComponent container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
                    if (container != null) {
                        for (ItemStack inner : container.iterateNonEmpty()) {
                            if (!inner.isEmpty()) {
                                for (int p : sources) {
                                    ItemStack pStack = player.getInventory().getStack(p);
                                    if (itemsMatch(inner, pStack)) {
                                        player.getInventory().setStack(p, ItemStack.EMPTY);
                                        int leftover = executeInsert(inv, pStack, pStack.getCount());
                                        if (leftover > 0) player.getInventory().setStack(p, pStack.copyWithCount(leftover));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        else if (action.equals("IPN_REFILL_PLAYER")) {
            int[] targets = getTargetSlots(action, amountNeeded, handler.getTerminalMode());
            for (int i : targets) {
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

        // --- DEFRAGMENTATION HANDLERS ---
        else if (action.equals("DEFRAGMENT_SHULKERS") || action.equals("DEFRAGMENT_ALL")) {
            long currentTime = net.minecraft.util.Util.getMeasuringTimeMs();
            long lastUsed = defragCooldowns.getOrDefault(player.getUuid(), 0L);

            if (currentTime - lastUsed < 3000) return;
            defragCooldowns.put(player.getUuid(), currentTime);

            boolean fullDefrag = action.equals("DEFRAGMENT_ALL");
            executeDefragment(inv, fullDefrag);
            world.playSound(null, interfacePos, net.minecraft.sound.SoundEvents.BLOCK_SMITHING_TABLE_USE, net.minecraft.sound.SoundCategory.BLOCKS, 0.6f, 1.2f);

            // Safety net: Kick any other players viewing the exact same storage to prevent UI desyncing
            for (ServerPlayerEntity otherPlayer : player.server.getPlayerManager().getPlayerList()) {
                if (otherPlayer != player && otherPlayer.currentScreenHandler instanceof svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler otherHandler) {
                    if (otherHandler.getTerminalMode() == handler.getTerminalMode() &&
                            otherHandler.getInterfacePos().equals(handler.getInterfacePos()) &&
                            otherHandler.getShulkerSlot() == handler.getShulkerSlot()) {

                        otherPlayer.closeHandledScreen();
                        otherPlayer.sendMessage(net.minecraft.text.Text.literal("§cStorage was reorganized. Please reopen the interface."), true);
                    }
                }
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

    private static int[] getTargetSlots(String action, int payloadAmount, int terminalMode) {
        if (terminalMode == 3) {
            return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        }
        if (action.startsWith("IPN_") && payloadAmount == 0) {
            int[] slots = new int[27];
            for (int i = 0; i < 27; i++) slots[i] = i + 9;
            return slots;
        }
        int[] slots = new int[36];
        for (int i = 0; i < 27; i++) slots[i] = i + 9;
        for (int i = 0; i < 9; i++) slots[i + 27] = i;
        return slots;
    }

    private static int[] getSourceSlots(String action, int payloadAmount, int terminalMode) {
        if (terminalMode == 3) {
            return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        }
        if (action.startsWith("IPN_") && payloadAmount == 0) {
            int[] slots = new int[27];
            for (int i = 0; i < 27; i++) slots[i] = i + 9;
            return slots;
        }
        int[] slots = new int[36];
        for (int i = 0; i < 27; i++) slots[i] = i + 9;
        for (int i = 0; i < 9; i++) slots[i + 27] = i;
        return slots;
    }

    private static boolean itemsMatch(ItemStack s1, ItemStack s2) {
        if (ItemStack.areItemsAndComponentsEqual(s1, s2)) return true;

        if (s1.getItem() instanceof net.minecraft.item.BannerItem && s2.getItem() instanceof net.minecraft.item.BannerItem) {
            if (!s1.isOf(s2.getItem())) return false;
            if (!s1.getName().getString().equals(s2.getName().getString())) return false;

            var p1 = s1.get(net.minecraft.component.DataComponentTypes.BANNER_PATTERNS);
            var p2 = s2.get(net.minecraft.component.DataComponentTypes.BANNER_PATTERNS);
            if (p1 == null && p2 == null) return true;
            if (p1 != null && p1.equals(p2)) return true;
        }
        return false;
    }

    private static void insertIntoPlayer(PlayerEntity player, ItemStack stack, int[] targetSlots) {
        for (int i : targetSlots) {
            if (stack.isEmpty()) break;
            ItemStack pStack = player.getInventory().getStack(i);
            if (pStack.isEmpty()) {
                int give = Math.min(stack.getCount(), stack.getMaxCount());
                player.getInventory().setStack(i, stack.copyWithCount(give));
                stack.decrement(give);
            } else if (itemsMatch(pStack, stack) && pStack.getCount() < pStack.getMaxCount()) {
                int space = pStack.getMaxCount() - pStack.getCount();
                int give = Math.min(space, stack.getCount());
                pStack.increment(give);
                stack.decrement(give);
            }
        }
    }

    private static int calculatePlayerSpace(net.minecraft.entity.player.PlayerInventory playerInv, ItemStack target, int[] targetSlots) {
        int space = 0;
        for (int i : targetSlots) {
            ItemStack stack = playerInv.getStack(i);
            if (stack.isEmpty()) {
                space += target.getMaxCount();
            } else if (itemsMatch(stack, target)) {
                space += (target.getMaxCount() - stack.getCount());
            }
        }
        return space;
    }

    private static ItemStack executeExtract(Inventory inv, ItemStack targetStack, int amountNeeded) {
        if (targetStack.isEmpty() || amountNeeded <= 0) return ItemStack.EMPTY;

        int extracted = 0;

        for (int i = 0; i < inv.size() && extracted < amountNeeded; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;

            if (isShulkerBox(stack.getItem())) {
                net.minecraft.component.type.ContainerComponent container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
                if (container != null) {
                    int emptySlot = -1;

                    if (stack.getCount() > 1) {
                        for (int j = 0; j < inv.size(); j++) {
                            if (inv.getStack(j).isEmpty()) { emptySlot = j; break; }
                        }
                        if (emptySlot == -1) continue;
                    }

                    net.minecraft.util.collection.DefaultedList<ItemStack> shulkerItems = net.minecraft.util.collection.DefaultedList.ofSize(27, ItemStack.EMPTY);
                    int slot = 0;
                    boolean modified = false;

                    for (ItemStack innerStack : container.iterateNonEmpty()) {
                        ItemStack copied = innerStack.copy();
                        if (extracted < amountNeeded && itemsMatch(copied, targetStack)) {
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
            else if (itemsMatch(stack, targetStack)) {
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
        } else if (handler.getTerminalMode() == 3) {
            final net.minecraft.entity.player.PlayerInventory pInv = player.getInventory();
            inv = new net.minecraft.inventory.Inventory() {
                @Override public int size() { return 27; }
                @Override public boolean isEmpty() {
                    for(int i=9; i<36; i++) if(!pInv.getStack(i).isEmpty()) return false;
                    return true;
                }
                @Override public ItemStack getStack(int slot) { return pInv.getStack(slot + 9); }
                @Override public ItemStack removeStack(int slot, int amount) { return pInv.removeStack(slot + 9, amount); }
                @Override public ItemStack removeStack(int slot) { return pInv.removeStack(slot + 9); }
                @Override public void setStack(int slot, ItemStack stack) { pInv.setStack(slot + 9, stack); }
                @Override public void markDirty() { pInv.markDirty(); }
                @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
                @Override public void clear() { for(int i=9; i<36; i++) pInv.setStack(i, ItemStack.EMPTY); }
            };
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

        if (isTargetShulker && inv instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity) return amountToInsert;
        if (isTargetShulker) {
            net.minecraft.component.type.ContainerComponent container = originalStack.getOrDefault(net.minecraft.component.DataComponentTypes.CONTAINER, net.minecraft.component.type.ContainerComponent.DEFAULT);
            if (container.iterateNonEmpty().iterator().hasNext()) return amountToInsert;
        }

        amountToInsert = topOffExistingStacks(inv, originalStack, amountToInsert, isTargetShulker);
        if (amountToInsert <= 0) return 0;

        amountToInsert = fillMatchingShulkers(inv, originalStack, amountToInsert, isTargetShulker);
        if (amountToInsert <= 0) return 0;

        amountToInsert = fillAnyEmpty(inv, originalStack, amountToInsert, isTargetShulker);

        if (isTargetShulker && amountToInsert > 0) {
            amountToInsert = autoCompressLooseItems(inv, originalStack, amountToInsert);
        }

        return amountToInsert;
    }

    private static int topOffExistingStacks(Inventory inv, ItemStack payload, int amount, boolean isShulker) {
        for (int i = 0; i < inv.size() && amount > 0; i++) {
            ItemStack slotStack = inv.getStack(i);
            if (slotStack == payload) continue;

            if (!isShulker && isShulkerBox(slotStack.getItem())) {
                if (slotStack.getCount() > 1) {
                    int emptySlot = -1;
                    for (int j = 0; j < inv.size(); j++) {
                        if (inv.getStack(j).isEmpty()) { emptySlot = j; break; }
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
            else if (itemsMatch(slotStack, payload) && slotStack.getCount() < slotStack.getMaxCount()) {
                if (!inv.isValid(i, payload)) continue;

                int space = slotStack.getMaxCount() - slotStack.getCount();
                int give = Math.min(amount, space);
                slotStack.increment(give);
                amount -= give;
            }
        }
        return amount;
    }

    private static int fillMatchingShulkers(Inventory inv, ItemStack payload, int amount, boolean isShulker) {
        if (isShulker) return amount;

        for (int i = 0; i < inv.size() && amount > 0; i++) {
            ItemStack slotStack = inv.getStack(i);

            if (isShulkerBox(slotStack.getItem())) {
                net.minecraft.component.type.ContainerComponent container = slotStack.getOrDefault(net.minecraft.component.DataComponentTypes.CONTAINER, net.minecraft.component.type.ContainerComponent.DEFAULT);
                boolean containsItem = false;
                for (ItemStack inner : container.iterateNonEmpty()) {
                    if (itemsMatch(inner, payload)) { containsItem = true; break; }
                }

                if (containsItem) {
                    if (slotStack.getCount() > 1) {
                        int emptySlot = -1;
                        for (int j = 0; j < inv.size(); j++) {
                            if (inv.getStack(j).isEmpty()) { emptySlot = j; break; }
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
            }
        }
        return amount;
    }

    private static int fillAnyEmpty(Inventory inv, ItemStack payload, int amount, boolean isShulker) {
        for (int i = 0; i < inv.size() && amount > 0; i++) {
            ItemStack slotStack = inv.getStack(i);
            if (slotStack == payload) continue;

            if (!isShulker && isShulkerBox(slotStack.getItem())) {
                if (slotStack.getCount() > 1) {
                    int emptySlot = -1;
                    for (int j = 0; j < inv.size(); j++) {
                        if (inv.getStack(j).isEmpty()) { emptySlot = j; break; }
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
            } else if (!allowEmptySlots && itemsMatch(innerSlot, payload) && innerSlot.getCount() < innerSlot.getMaxCount()) {
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
        for (int i = 0; i < inv.size() && amount > 0; i++) {
            ItemStack chestStack = inv.getStack(i);
            if (chestStack == emptyShulkerPayload) continue;

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

    private static void executeDefragment(Inventory inv, boolean fullDefrag) {
        java.util.List<ItemStack> pool = new java.util.ArrayList<>();

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;

            if (isShulkerBox(stack.getItem()) && stack.getCount() == 1) {
                net.minecraft.component.type.ContainerComponent container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
                if (container != null) {
                    for (ItemStack inner : container.iterateNonEmpty()) {
                        addToPool(pool, inner.copy());
                    }
                }
                stack.set(net.minecraft.component.DataComponentTypes.CONTAINER, net.minecraft.component.type.ContainerComponent.DEFAULT);
            } else if (fullDefrag && !isShulkerBox(stack.getItem())) {
                addToPool(pool, stack.copy());
                inv.setStack(i, ItemStack.EMPTY);
            }
        }

        if (pool.isEmpty()) return;

        // Pre-calculate absolute total quantities for global quantity sorting
        java.util.Map<String, Integer> totalQuantities = new java.util.HashMap<>();
        for (ItemStack s : pool) {
            String name = s.getName().getString();
            totalQuantities.put(name, totalQuantities.getOrDefault(name, 0) + s.getCount());
        }

        // Pass 2: Sort the pool based on the user's config preference
        svemocan.vanilla_storage_interface.config.VanillaStorageConfig.DefragSortMode mode =
                svemocan.VanillaStorageInterface.CONFIG.defragmentationSortMode;

        pool.sort((s1, s2) -> {
            switch (mode) {
                case QUANTITY_DESCENDING:
                    int qtyDesc = Integer.compare(totalQuantities.get(s2.getName().getString()), totalQuantities.get(s1.getName().getString()));
                    if (qtyDesc != 0) return qtyDesc;
                    return s1.getName().getString().compareToIgnoreCase(s2.getName().getString());
                case QUANTITY_ASCENDING:
                    int qtyAsc = Integer.compare(totalQuantities.get(s1.getName().getString()), totalQuantities.get(s2.getName().getString()));
                    if (qtyAsc != 0) return qtyAsc;
                    return s1.getName().getString().compareToIgnoreCase(s2.getName().getString());
                case REGISTRY_ID:
                    String id1 = net.minecraft.registry.Registries.ITEM.getId(s1.getItem()).toString();
                    String id2 = net.minecraft.registry.Registries.ITEM.getId(s2.getItem()).toString();
                    int idCompare = id1.compareToIgnoreCase(id2);
                    if (idCompare != 0) return idCompare;
                    return Integer.compare(s2.getCount(), s1.getCount());
                case ALPHABETICAL:
                default:
                    int nameCompare = s1.getName().getString().compareToIgnoreCase(s2.getName().getString());
                    if (nameCompare != 0) return nameCompare;
                    return Integer.compare(s2.getCount(), s1.getCount());
            }
        });

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (isShulkerBox(stack.getItem()) && stack.getCount() == 1) {
                if (pool.isEmpty()) break;

                net.minecraft.util.collection.DefaultedList<ItemStack> shulkerItems = net.minecraft.util.collection.DefaultedList.ofSize(27, ItemStack.EMPTY);
                int slot = 0;
                while (slot < 27 && !pool.isEmpty()) {
                    shulkerItems.set(slot++, pool.remove(0));
                }
                stack.set(net.minecraft.component.DataComponentTypes.CONTAINER, net.minecraft.component.type.ContainerComponent.fromStacks(shulkerItems));
            }
        }

        if (fullDefrag && !pool.isEmpty()) {
            for (int i = 0; i < inv.size(); i++) {
                if (pool.isEmpty()) break;
                if (inv.getStack(i).isEmpty()) {
                    inv.setStack(i, pool.remove(0));
                }
            }
        }
    }

    private static void addToPool(java.util.List<ItemStack> pool, ItemStack newStack) {
        if (newStack.isEmpty()) return;
        for (ItemStack existing : pool) {
            if (itemsMatch(existing, newStack)) {
                int space = existing.getMaxCount() - existing.getCount();
                int give = Math.min(space, newStack.getCount());
                existing.increment(give);
                newStack.decrement(give);
                if (newStack.isEmpty()) return;
            }
        }
        if (!newStack.isEmpty()) pool.add(newStack);
    }

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
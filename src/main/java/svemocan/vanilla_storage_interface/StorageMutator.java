package svemocan.vanilla_storage_interface;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import svemocan.vanilla_storage_interface.network.StorageActionPayload;

public class StorageMutator {

    private static final java.util.Map<java.util.UUID, Long> defragCooldowns = new java.util.HashMap<>();

    public static void handleAction(ServerPlayer player, StorageActionPayload payload) {
        if (!(player.containerMenu instanceof svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler handler)) return;

        Level world = player.serverLevel();
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

        Container inv = null;
        boolean isVirtualTerminal = (handler.getTerminalMode() != 0);

        if (handler.getTerminalMode() == 0) {
            net.minecraft.world.level.block.state.BlockState state = world.getBlockState(interfacePos);
            if (state.hasProperty(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING)) {
                net.minecraft.core.Direction facing = state.getValue(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING);
                inv = net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(world, interfacePos.relative(facing.getOpposite()));
            }
        } else if (handler.getTerminalMode() == 1) {
            inv = player.getEnderChestInventory();} else if (handler.getTerminalMode() == 2) {
            ItemStack shulkerStack = player.getInventory().getItem(handler.getShulkerSlot());
            if (isShulkerBox(shulkerStack.getItem())) inv = new ShulkerItemInventory(shulkerStack);
        } else if (handler.getTerminalMode() == 3) {
            final net.minecraft.world.entity.player.Inventory pInv = player.getInventory();
            inv = new net.minecraft.world.Container() {
                @Override public int getContainerSize() { return 27; }
                @Override public boolean isEmpty() {
                    for(int i=9; i<36; i++) if(!pInv.getItem(i).isEmpty()) return false;
                    return true;
                }
                @Override public ItemStack getItem(int slot) { return pInv.getItem(slot + 9); }
                @Override public ItemStack removeItem(int slot, int amount) { return pInv.removeItem(slot + 9, amount); }
                @Override public ItemStack removeItemNoUpdate(int slot) { return pInv.removeItemNoUpdate(slot + 9); }
                @Override public void setItem(int slot, ItemStack stack) { pInv.setItem(slot + 9, stack); }
                @Override public void setChanged() { pInv.setChanged(); }
                @Override public boolean stillValid(Player player) { return true; }
                @Override public void clearContent() { for(int i=9; i<36; i++) pInv.setItem(i, ItemStack.EMPTY); }
            };
        }

        if (inv == null) return;

        int pStart = (payload.amount() == 1) ? 0 : 9;

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
                int leftOver = executeInsert(inv, stack, stack.getCount());
                stack.setCount(leftOver);
                slot.setChanged();
            }
        }
        else if (action.equals("THROW")) {
            int takeAmount = Math.min(amountNeeded, targetStack.getMaxStackSize());
            ItemStack extracted = executeExtract(inv, targetStack, takeAmount);
            if (extracted != null && !extracted.isEmpty()) {
                // true = retain ownership temporarily (so player doesn't instantly vacuum it back into their physical inventory)
                player.drop(extracted, false, true);
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
                            for (ItemStack inner : container.nonEmptyItems()) {
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
                        for (ItemStack inner : container.nonEmptyItems()) {
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

            // Step 2: Vacuum all available matching items from the storage for each unique type found
            for (ItemStack uniqueType : uniqueTypes) {
                int space = calculatePlayerSpace(player.getInventory(), uniqueType, targets);
                if (space > 0) {
                    ItemStack extracted = executeExtract(inv, uniqueType, space);
                    if (extracted != null && !extracted.isEmpty()) {
                        insertIntoPlayer(player, extracted, targets);

                        // Failsafe: Put back any absolute overflow
                        if (extracted.getCount() > 0) executeInsert(inv, extracted, extracted.getCount());
                    }
                }
            }
        }

        // --- DEFRAGMENTATION HANDLERS ---
        else if (action.equals("DEFRAGMENT_SHULKERS") || action.equals("DEFRAGMENT_ALL")) {
            long currentTime = net.minecraft.Util.getMillis();
            long lastUsed = defragCooldowns.getOrDefault(player.getUUID(), 0L);

            if (currentTime - lastUsed < 3000) return;
            defragCooldowns.put(player.getUUID(), currentTime);

            boolean fullDefrag = action.equals("DEFRAGMENT_ALL");
            executeDefragment(inv, fullDefrag);
            world.playSound(null, interfacePos, net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);

            // Safety net: Kick any other players viewing the exact same storage to prevent UI desyncing
            for (ServerPlayer otherPlayer : player.server.getPlayerList().getPlayers()) {
                if (otherPlayer != player && otherPlayer.containerMenu instanceof svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler otherHandler) {
                    if (otherHandler.getTerminalMode() == handler.getTerminalMode() &&
                            otherHandler.getInterfacePos().equals(handler.getInterfacePos()) &&
                            otherHandler.getShulkerSlot() == handler.getShulkerSlot()) {

                        otherPlayer.closeContainer();
                        otherPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("§cStorage was reorganized. Please reopen the interface."), true);
                    }
                }
            }
        }

        inv.setChanged();

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

    private static void handleStacks(ServerPlayer player, Container inv, int[] sources, ItemStack inner) {
        for (int p : sources) {
            ItemStack pStack = player.getInventory().getItem(p);
            if (itemsMatch(inner, pStack)) {
                player.getInventory().setItem(p, ItemStack.EMPTY);
                int leftover = executeInsert(inv, pStack.copy(), pStack.getCount());
                if (leftover > 0) player.getInventory().setItem(p, pStack.copyWithCount(leftover));
            }
        }
    }

    private static int[] getTargetSlots(String action, int payloadAmount, int terminalMode) {
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
        if (ItemStack.isSameItemSameComponents(s1, s2)) return true;

        if (s1.getItem() instanceof net.minecraft.world.item.BannerItem && s2.getItem() instanceof net.minecraft.world.item.BannerItem) {
            if (!s1.is(s2.getItem())) return false;
            if (!s1.getHoverName().getString().equals(s2.getHoverName().getString())) return false;

            var p1 = s1.get(net.minecraft.core.component.DataComponents.BANNER_PATTERNS);
            var p2 = s2.get(net.minecraft.core.component.DataComponents.BANNER_PATTERNS);
            if (p1 == null && p2 == null) return true;
            if (p1 != null && p1.equals(p2)) return true;
        }
        return false;
    }

    private static void insertIntoPlayer(Player player, ItemStack stack, int[] targetSlots) {
        for (int i : targetSlots) {
            if (stack.isEmpty()) break;
            ItemStack pStack = player.getInventory().getItem(i);
            if (pStack.isEmpty()) {
                int give = Math.min(stack.getCount(), stack.getMaxStackSize());
                player.getInventory().setItem(i, stack.copyWithCount(give));
                stack.shrink(give);
            } else if (itemsMatch(pStack, stack) && pStack.getCount() < pStack.getMaxStackSize()) {
                int space = pStack.getMaxStackSize() - pStack.getCount();
                int give = Math.min(space, stack.getCount());
                pStack.grow(give);
                stack.shrink(give);
            }
        }
    }

    private static int calculatePlayerSpace(net.minecraft.world.entity.player.Inventory playerInv, ItemStack target, int[] targetSlots) {
        int space = 0;
        for (int i : targetSlots) {
            ItemStack stack = playerInv.getItem(i);
            if (stack.isEmpty()) {
                space += target.getMaxStackSize();
            } else if (itemsMatch(stack, target)) {
                space += (target.getMaxStackSize() - stack.getCount());
            }
        }
        return space;
    }

    private static ItemStack executeExtract(Container inv, ItemStack targetStack, int amountNeeded) {
        if (targetStack.isEmpty() || amountNeeded <= 0) return ItemStack.EMPTY;

        ItemStack safeReference = targetStack.copyWithCount(1);
        int extracted = 0;

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

                    for (ItemStack innerStack : container.nonEmptyItems()) {
                        ItemStack copied = innerStack.copy();
                        if (extracted < amountNeeded && itemsMatch(copied, targetStack)) {
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
                int take = Math.min(amountNeeded - extracted, stack.getCount());
                extracted += take;
                stack.shrink(take);
            }
        }

        if (extracted > 0) {
            return safeReference.copyWithCount(extracted);
        }
        return ItemStack.EMPTY;
    }

    public static boolean insertDirectly(net.minecraft.server.level.ServerPlayer player, svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler handler, ItemStack stackToInsert) {
        Level world = player.serverLevel();
        Container inv = null;
        boolean isVirtualTerminal = (handler.getTerminalMode() != 0);

        if (handler.getTerminalMode() == 0) {
            net.minecraft.world.level.block.state.BlockState state = world.getBlockState(handler.getInterfacePos());
            if (state.hasProperty(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING)) {
                net.minecraft.core.Direction facing = state.getValue(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING);
                inv = HopperBlockEntity.getContainerAt(world, handler.getInterfacePos().relative(facing.getOpposite()));
            }
        } else if (handler.getTerminalMode() == 1) {
            inv = player.getEnderChestInventory();
        } else if (handler.getTerminalMode() == 2) {
            ItemStack shulkerStack = player.getInventory().getItem(handler.getShulkerSlot());
            if (isShulkerBox(shulkerStack.getItem())) inv = new ShulkerItemInventory(shulkerStack);
        } else if (handler.getTerminalMode() == 3) {
            final net.minecraft.world.entity.player.Inventory pInv = player.getInventory();
            inv = new net.minecraft.world.Container() {
                @Override public int getContainerSize() { return 27; }
                @Override public boolean isEmpty() {
                    for(int i=9; i<36; i++) if(!pInv.getItem(i).isEmpty()) return false;
                    return true;
                }
                @Override public ItemStack getItem(int slot) { return pInv.getItem(slot + 9); }
                @Override public ItemStack removeItem(int slot, int amount) { return pInv.removeItem(slot + 9, amount); }
                @Override public ItemStack removeItemNoUpdate(int slot) { return pInv.removeItemNoUpdate(slot + 9); }
                @Override public void setItem(int slot, ItemStack stack) { pInv.setItem(slot + 9, stack); }
                @Override public void setChanged() { pInv.setChanged(); }
                @Override public boolean stillValid(Player player) { return true; }
                @Override public void clearContent() { for(int i=9; i<36; i++) pInv.setItem(i, ItemStack.EMPTY); }
            };
        }

        if (inv == null || stackToInsert.isEmpty()) return false;

        int initial = stackToInsert.getCount();
        int leftOver = executeInsert(inv, stackToInsert, stackToInsert.getCount());

        if (leftOver < initial) {
            stackToInsert.setCount(leftOver);
            inv.setChanged();
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

    public static int executeInsert(Container inv, ItemStack originalStack, int amountToInsert) {
        if (originalStack.isEmpty() || amountToInsert <= 0) return amountToInsert;
        boolean isTargetShulker = isShulkerBox(originalStack.getItem());

        if (isTargetShulker && (inv instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity || inv instanceof ShulkerItemInventory)) return amountToInsert;
        if (isTargetShulker) {
            net.minecraft.world.item.component.ItemContainerContents container = originalStack.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY);
            if (container.nonEmptyItems().iterator().hasNext()) return amountToInsert;
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

    private static int topOffExistingStacks(Container inv, ItemStack payload, int amount, boolean isShulker) {
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack slotStack = inv.getItem(i);
            if (slotStack == payload) continue;

            if (!isShulker && isShulkerBox(slotStack.getItem())) {
                if (slotStack.getCount() > 1) {
                    int emptySlot = -1;
                    for (int j = 0; j < inv.getContainerSize(); j++) {
                        if (inv.getItem(j).isEmpty()) { emptySlot = j; break; }
                    }
                    if (emptySlot == -1) continue;

                    ItemStack singleBox = slotStack.copyWithCount(1);
                    ItemStack leftoverBoxes = slotStack.copyWithCount(slotStack.getCount() - 1);
                    int remaining = mergeIntoShulkerComponent(singleBox, payload, amount, false);

                    if (remaining < amount) {
                        amount = remaining;
                        inv.setItem(i, singleBox);
                        inv.setItem(emptySlot, leftoverBoxes);
                    }
                } else {
                    amount = mergeIntoShulkerComponent(slotStack, payload, amount, false);
                    inv.setItem(i, slotStack);
                }
            }
            else if (itemsMatch(slotStack, payload) && slotStack.getCount() < slotStack.getMaxStackSize()) {
                if (!inv.canPlaceItem(i, payload)) continue;

                int space = slotStack.getMaxStackSize() - slotStack.getCount();
                int give = Math.min(amount, space);
                slotStack.grow(give);
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
                for (ItemStack inner : container.nonEmptyItems()) {
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
                        inv.setItem(i, slotStack);
                    }
                }
            }
        }
        return amount;
    }

    private static int fillAnyEmpty(Container inv, ItemStack payload, int amount, boolean isShulker) {
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack slotStack = inv.getItem(i);
            if (slotStack == payload) continue;

            if (!isShulker && isShulkerBox(slotStack.getItem())) {
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
                    inv.setItem(i, slotStack);
                }
            }
            else if (slotStack.isEmpty()) {
                if (!inv.canPlaceItem(i, payload)) continue;

                int give = Math.min(amount, payload.getMaxStackSize());
                inv.setItem(i, payload.copyWithCount(give));
                amount -= give;
            }
        }
        return amount;
    }

    private static int mergeIntoShulkerComponent(ItemStack shulkerBox, ItemStack payload, int amount, boolean allowEmptySlots) {
        net.minecraft.world.item.component.ItemContainerContents container = shulkerBox.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY);

        int size = 27;
        net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(size, ItemStack.EMPTY);

        int index = 0;
        for (ItemStack inner : container.nonEmptyItems()) {
            items.set(index++, inner.copy());
        }

        boolean modified = false;
        for (int j = 0; j < size && amount > 0; j++) {
            ItemStack innerSlot = items.get(j);

            if (allowEmptySlots && innerSlot.isEmpty()) {
                int give = Math.min(amount, payload.getMaxStackSize());
                items.set(j, payload.copyWithCount(give));
                amount -= give;
                modified = true;
            } else if (!allowEmptySlots && itemsMatch(innerSlot, payload) && innerSlot.getCount() < innerSlot.getMaxStackSize()) {
                int space = innerSlot.getMaxStackSize() - innerSlot.getCount();
                int give = Math.min(amount, space);
                innerSlot.grow(give);
                amount -= give;
                modified = true;
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

    private static void executeDefragment(Container inv, boolean fullDefrag) {
        java.util.List<ItemStack> pool = new java.util.ArrayList<>();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            if (isShulkerBox(stack.getItem()) && stack.getCount() == 1) {
                net.minecraft.world.item.component.ItemContainerContents container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
                if (container != null) {
                    for (ItemStack inner : container.nonEmptyItems()) {
                        addToPool(pool, inner.copy());
                    }
                }
                stack.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY);
            } else if (fullDefrag && !isShulkerBox(stack.getItem())) {
                addToPool(pool, stack.copy());
                inv.setItem(i, ItemStack.EMPTY);
            }
        }

        if (pool.isEmpty()) return;

        // Pre-calculate absolute total quantities for global quantity sorting
        java.util.Map<String, Integer> totalQuantities = new java.util.HashMap<>();
        for (ItemStack s : pool) {
            String name = s.getHoverName().getString();
            totalQuantities.put(name, totalQuantities.getOrDefault(name, 0) + s.getCount());
        }

        // Pass 2: Sort the pool based on the user's config preference
        svemocan.vanilla_storage_interface.config.VanillaStorageConfig.DefragSortMode mode =
                svemocan.VanillaStorageInterface.CONFIG.defragmentationSortMode;

        if (mode == svemocan.vanilla_storage_interface.config.VanillaStorageConfig.DefragSortMode.SMART_PACKING ||
                mode == svemocan.vanilla_storage_interface.config.VanillaStorageConfig.DefragSortMode.ADAPTIVE_PACKING) {

            boolean isAdaptive = (mode == svemocan.vanilla_storage_interface.config.VanillaStorageConfig.DefragSortMode.ADAPTIVE_PACKING);
            pool = applyJardaHeuristic(pool, inv.getContainerSize(), isAdaptive);

        } else {
            pool.sort((s1, s2) -> {
                switch (mode) {
                    case QUANTITY_DESCENDING:
                        int qtyDesc = Integer.compare(totalQuantities.get(s2.getHoverName().getString()), totalQuantities.get(s1.getHoverName().getString()));
                        if (qtyDesc != 0) return qtyDesc;
                        return s1.getHoverName().getString().compareToIgnoreCase(s2.getHoverName().getString());
                    case QUANTITY_ASCENDING:
                        int qtyAsc = Integer.compare(totalQuantities.get(s1.getHoverName().getString()), totalQuantities.get(s2.getHoverName().getString()));
                        if (qtyAsc != 0) return qtyAsc;
                        return s1.getHoverName().getString().compareToIgnoreCase(s2.getHoverName().getString());
                    case REGISTRY_ID:
                        String id1 = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s1.getItem()).toString();
                        String id2 = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s2.getItem()).toString();
                        int idCompare = id1.compareToIgnoreCase(id2);
                        if (idCompare != 0) return idCompare;
                        return Integer.compare(s2.getCount(), s1.getCount());
                    case ALPHABETICAL:
                    default:
                        int nameCompare = s1.getHoverName().getString().compareToIgnoreCase(s2.getHoverName().getString());
                        if (nameCompare != 0) return nameCompare;
                        return Integer.compare(s2.getCount(), s1.getCount());
                }
            });
        }

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isShulkerBox(stack.getItem()) && stack.getCount() == 1) {
                if (pool.isEmpty()) break;

                net.minecraft.core.NonNullList<ItemStack> shulkerItems = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
                int slot = 0;
                while (slot < 27 && !pool.isEmpty()) {
                    shulkerItems.set(slot++, pool.remove(0));
                }
                stack.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(shulkerItems));
            }
        }

        if (fullDefrag && !pool.isEmpty()) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (pool.isEmpty()) break;
                if (inv.getItem(i).isEmpty()) {
                    inv.setItem(i, pool.remove(0));
                }
            }
        }
    }

    private static void addToPool(java.util.List<ItemStack> pool, ItemStack newStack) {
        if (newStack.isEmpty()) return;
        for (ItemStack existing : pool) {
            if (itemsMatch(existing, newStack)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                int give = Math.min(space, newStack.getCount());
                existing.grow(give);
                newStack.shrink(give);
                if (newStack.isEmpty()) return;
            }
        }
        if (!newStack.isEmpty()) pool.add(newStack);
    }

    /**
     * The Jarda Heuristic (Bin-Packing / Knapsack Algorithm)
     * Dedicated to our discrete optimization class professor :D.
     * valuates storage density to perfectly fill or dynamically isolate Shulker Boxes.
     */
    private static java.util.List<net.minecraft.world.item.ItemStack> applyJardaHeuristic(java.util.List<net.minecraft.world.item.ItemStack> condensedStacks, int interfaceSlots, boolean isAdaptive) {
        java.util.List<java.util.List<net.minecraft.world.item.ItemStack>> groups = new java.util.ArrayList<>();

        // Group all identical items together
        for (net.minecraft.world.item.ItemStack stack : condensedStacks) {
            boolean found = false;
            for (java.util.List<net.minecraft.world.item.ItemStack> group : groups) {
                if (itemsMatch(group.get(0), stack)) {
                    group.add(stack);
                    found = true;
                    break;
                }
            }
            if (!found) {
                java.util.List<net.minecraft.world.item.ItemStack> newGroup = new java.util.ArrayList<>();
                newGroup.add(stack);
                groups.add(newGroup);
            }
        }

        java.util.List<net.minecraft.world.item.ItemStack> result = new java.util.ArrayList<>();
        java.util.List<java.util.List<net.minecraft.world.item.ItemStack>> remainingGroups = new java.util.ArrayList<>();

        // Determine Phase 1 Threshold based on current storage density
        int phaseOneThreshold = 27; // Default: Strict space efficiency
        if (isAdaptive && interfaceSlots > 0) {
            double densityRatio = condensedStacks.size() / (double) (interfaceSlots * 27);
            if (densityRatio <= 0.3) {
                phaseOneThreshold = 9; // Abundant space: Isolate groups of 9+
            } else if (densityRatio <= 0.6) {
                phaseOneThreshold = 18; // Moderate space: Isolate groups of 18+
            }
        }

        // Phase 1: Pure Boxes (Extract groups that meet the dynamic threshold)
        for (java.util.List<net.minecraft.world.item.ItemStack> group : groups) {
            while (group.size() >= phaseOneThreshold) {
                int extractCount = Math.min(group.size(), 27); // Cap at physical box limit
                for (int i = 0; i < extractCount; i++) result.add(group.remove(0));

                // CRITICAL: Pad the remainder of the virtual box to isolate the items
                int padding = 27 - extractCount;
                for (int i = 0; i < padding; i++) result.add(net.minecraft.world.item.ItemStack.EMPTY);
            }
            if (!group.isEmpty()) remainingGroups.add(group);
        }

        // Phase 2: Knapsack Heuristic (Sort by largest remaining groups first)
        remainingGroups.sort((a, b) -> Integer.compare(b.size(), a.size()));
        java.util.List<net.minecraft.world.item.ItemStack> currentBox = new java.util.ArrayList<>();

        while (!remainingGroups.isEmpty()) {
            if (currentBox.size() == 27) {
                result.addAll(currentBox);
                currentBox.clear();
            }

            int spaceLeft = 27 - currentBox.size();
            boolean packedAnything = false;

            for (int i = 0; i < remainingGroups.size(); i++) {
                java.util.List<net.minecraft.world.item.ItemStack> group = remainingGroups.get(i);
                if (group.size() <= spaceLeft) {
                    currentBox.addAll(group);
                    remainingGroups.remove(i);
                    packedAnything = true;
                    break;
                }
            }

            if (!packedAnything && !remainingGroups.isEmpty()) {
                // Failsafe: Split the absolute smallest group to perfectly plug the gap
                java.util.List<net.minecraft.world.item.ItemStack> smallestGroup = remainingGroups.get(remainingGroups.size() - 1);
                for (int i = 0; i < spaceLeft; i++) {
                    if (smallestGroup.isEmpty()) break;
                    currentBox.add(smallestGroup.remove(0));
                }
                if (smallestGroup.isEmpty()) remainingGroups.remove(smallestGroup);
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
                int i = 0;
                for (ItemStack inner : container.nonEmptyItems()) {
                    if (i < 27) items.set(i++, inner.copy());
                }
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
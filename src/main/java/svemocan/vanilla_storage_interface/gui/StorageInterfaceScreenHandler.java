package svemocan.vanilla_storage_interface.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import svemocan.VanillaStorageInterface;

public class StorageInterfaceScreenHandler extends AbstractContainerMenu {

    private final BlockPos interfacePos;
    private final int terminalMode;
    private final int shulkerSlot;
    private final net.minecraft.world.entity.player.Player player;
    private int updateTimer = 0;
    private int lastHash = -1;

    public StorageInterfaceScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, net.minecraft.core.BlockPos.ZERO, 0, -1);
    }

    public StorageInterfaceScreenHandler(int syncId, Inventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, pos, 0, -1);
    }

    public StorageInterfaceScreenHandler(int syncId, Inventory playerInventory, BlockPos pos, int mode, int shulkerSlot) {
        super(VanillaStorageInterface.STORAGE_INTERFACE_SCREEN_HANDLER, syncId);
        this.interfacePos = pos;
        this.terminalMode = mode;
        this.shulkerSlot = shulkerSlot;
        this.player = playerInventory.player;

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int slotIndex = col + row * 9 + 9;
                if (this.terminalMode == 2 && slotIndex == this.shulkerSlot) {
                    this.addSlot(new Slot(playerInventory, slotIndex, 8 + col * 18, 140 + row * 18) {
                        @Override public boolean mayPickup(Player playerEntity) { return false; }
                        @Override public boolean mayPlace(ItemStack stack) { return false; }
                    });
                } else {
                    this.addSlot(new Slot(playerInventory, slotIndex, 8 + col * 18, 140 + row * 18));
                }
            }
        }
        for (int col = 0; col < 9; ++col) {
            int slotIndex = col;
            if (this.terminalMode == 2 && slotIndex == this.shulkerSlot) {
                this.addSlot(new Slot(playerInventory, slotIndex, 8 + col * 18, 198) {
                    @Override public boolean mayPickup(Player playerEntity) { return false; }
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                });
            } else {
                this.addSlot(new Slot(playerInventory, slotIndex, 8 + col * 18, 198));
            }
        }
    }

    public BlockPos getInterfacePos() { return this.interfacePos; }
    public int getTerminalMode() { return this.terminalMode; }
    public int getShulkerSlot() { return this.shulkerSlot; }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        if (!this.player.level().isClientSide) {
            if (this.updateTimer++ % 10 == 0) {
                net.minecraft.server.level.ServerPlayer serverPlayer = (net.minecraft.server.level.ServerPlayer) this.player;
                net.minecraft.world.level.Level world = serverPlayer.serverLevel();
                svemocan.vanilla_storage_interface.StorageAggregator.VirtualInventory vi = null;
                boolean isVirtual = (this.terminalMode != 0);

                if (this.terminalMode == 0) {
                    net.minecraft.world.level.block.state.BlockState state = world.getBlockState(this.interfacePos);
                    if (!(state.getBlock() instanceof svemocan.vanilla_storage_interface.StorageInterfaceBlock)) {
                        serverPlayer.closeContainer();
                        return;
                    }
                    vi = svemocan.vanilla_storage_interface.StorageAggregator.build(world, this.interfacePos);
                } else if (this.terminalMode == 1) {
                    vi = svemocan.vanilla_storage_interface.StorageAggregator.buildFromInventory(world, serverPlayer.getEnderChestInventory());
                } else if (this.terminalMode == 2) {
                    net.minecraft.world.item.ItemStack shulkerStack = serverPlayer.getInventory().getItem(this.shulkerSlot);
                    if (svemocan.vanilla_storage_interface.StorageMutator.isShulkerBox(shulkerStack.getItem())) {
                        vi = svemocan.vanilla_storage_interface.StorageAggregator.buildFromInventory(world, new svemocan.vanilla_storage_interface.StorageMutator.ShulkerItemInventory(shulkerStack));
                    } else {
                        serverPlayer.closeContainer();
                        return;
                    }
                } else if (this.terminalMode == 3) {
                    vi = svemocan.vanilla_storage_interface.StorageAggregator.buildMode3(serverPlayer.getInventory());
                }

                if (vi != null) {
                    int currentHash = vi.emptyShulkerSlots;
                    for (svemocan.vanilla_storage_interface.network.VirtualItem item : vi.items) {
                        currentHash = currentHash * 31 + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.stack().getItem()).hashCode();
                        currentHash = currentHash * 31 + item.count();
                        if (item.stack().getComponentsPatch() != null) {
                            currentHash = currentHash * 31 + item.stack().getComponentsPatch().hashCode();
                        }
                    }

                    if (this.lastHash == -1) {
                        this.lastHash = currentHash;
                    } else if (currentHash != this.lastHash) {
                        this.lastHash = currentHash;
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer, new svemocan.vanilla_storage_interface.network.StorageSyncPayload(vi.items, vi.emptyShulkerSlots, this.interfacePos, isVirtual));
                    }
                }
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (player.level().isClientSide) return ItemStack.EMPTY;

        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            ItemStack originalStack = stackInSlot.copy();

            slot.set(ItemStack.EMPTY);

            boolean inserted = svemocan.vanilla_storage_interface.StorageMutator.insertDirectly(
                    (net.minecraft.server.level.ServerPlayer) player, this, stackInSlot
            );

            if (inserted) {
                if (!stackInSlot.isEmpty()) {
                    slot.set(stackInSlot);
                }
                slot.setChanged();
                return originalStack;
            } else {
                slot.set(originalStack);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.terminalMode == 0) {
            // Ensure the physical block still exists and the player is within 8 blocks (64 block radius squared)
            if (player.level().getBlockEntity(this.interfacePos) instanceof svemocan.vanilla_storage_interface.StorageInterfaceBlockEntity) {
                return player.distanceToSqr(this.interfacePos.getX() + 0.5, this.interfacePos.getY() + 0.5, this.interfacePos.getZ() + 0.5) <= 64.0;
            }
            return false;
        } else if (this.terminalMode == 2) {
            // Ensure the player hasn't moved the active Shulker Box out of its slot via other means
            return svemocan.vanilla_storage_interface.StorageMutator.isShulkerBox(player.getInventory().getItem(this.shulkerSlot).getItem());
        }
        // Mode 1 (Ender Chest) and Mode 3 (Player Inv) are strictly bound to the player entity itself, so they are always valid
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            net.minecraft.world.level.Level world = player.level();

            if (this.terminalMode == 1) {
                world.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ENDER_CHEST_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
            } else if (this.terminalMode == 2) {
                world.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SHULKER_BOX_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
            } else if (this.terminalMode == 0) {
                net.minecraft.world.level.block.state.BlockState state = world.getBlockState(this.interfacePos);
                if (state.hasProperty(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING)) {
                    net.minecraft.core.Direction facing = state.getValue(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING);
                    net.minecraft.core.BlockPos targetPos = this.interfacePos.relative(facing.getOpposite());
                    net.minecraft.world.level.block.state.BlockState targetState = world.getBlockState(targetPos);
                    net.minecraft.world.level.block.Block targetBlock = targetState.getBlock();

                    if (targetBlock instanceof net.minecraft.world.level.block.ChestBlock) {
                        world.playSound(null, this.interfacePos, net.minecraft.sounds.SoundEvents.CHEST_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
                    } else if (targetBlock instanceof net.minecraft.world.level.block.BarrelBlock) {
                        world.playSound(null, this.interfacePos, net.minecraft.sounds.SoundEvents.BARREL_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
                    }
                }
            }
        }
    }
}
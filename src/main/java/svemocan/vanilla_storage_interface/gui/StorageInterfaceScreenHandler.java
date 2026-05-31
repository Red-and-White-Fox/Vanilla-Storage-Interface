package svemocan.vanilla_storage_interface.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;
import svemocan.VanillaStorageInterface;

public class StorageInterfaceScreenHandler extends ScreenHandler {

    private final BlockPos interfacePos;
    private final int terminalMode;
    private final int shulkerSlot;
    private final net.minecraft.entity.player.PlayerEntity player;
    private int updateTimer = 0;
    private int lastHash = -1;

    public StorageInterfaceScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, net.minecraft.util.math.BlockPos.ORIGIN, 0, -1);
    }

    public StorageInterfaceScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, pos, 0, -1);
    }

    public StorageInterfaceScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos, int mode, int shulkerSlot) {
        super(VanillaStorageInterface.STORAGE_INTERFACE_SCREEN_HANDLER, syncId);
        this.interfacePos = pos;
        this.terminalMode = mode;
        this.shulkerSlot = shulkerSlot;
        this.player = playerInventory.player;

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    public BlockPos getInterfacePos() { return this.interfacePos; }
    public int getTerminalMode() { return this.terminalMode; }
    public int getShulkerSlot() { return this.shulkerSlot; }

    @Override
    public void sendContentUpdates() {
        super.sendContentUpdates();

        if (!this.player.getWorld().isClient) {
            if (this.updateTimer++ % 10 == 0) {
                net.minecraft.server.network.ServerPlayerEntity serverPlayer = (net.minecraft.server.network.ServerPlayerEntity) this.player;
                net.minecraft.world.World world = serverPlayer.getServerWorld();
                svemocan.vanilla_storage_interface.StorageAggregator.VirtualInventory vi = null;
                boolean isVirtual = (this.terminalMode != 0);

                if (this.terminalMode == 0) {
                    net.minecraft.block.BlockState state = world.getBlockState(this.interfacePos);
                    if (!(state.getBlock() instanceof svemocan.vanilla_storage_interface.StorageInterfaceBlock)) {
                        serverPlayer.closeHandledScreen();
                        return;
                    }
                    vi = svemocan.vanilla_storage_interface.StorageAggregator.build(world, this.interfacePos);
                } else if (this.terminalMode == 1) {
                    vi = svemocan.vanilla_storage_interface.StorageAggregator.buildFromInventory(world, serverPlayer.getEnderChestInventory());
                } else if (this.terminalMode == 2) {
                    net.minecraft.item.ItemStack shulkerStack = serverPlayer.getInventory().getStack(this.shulkerSlot);
                    if (svemocan.vanilla_storage_interface.StorageMutator.isShulkerBox(shulkerStack.getItem())) {
                        vi = svemocan.vanilla_storage_interface.StorageAggregator.buildFromInventory(world, new svemocan.vanilla_storage_interface.StorageMutator.ShulkerItemInventory(shulkerStack));
                    } else {
                        serverPlayer.closeHandledScreen();
                        return;
                    }
                } else if (this.terminalMode == 3) {
                    vi = svemocan.vanilla_storage_interface.StorageAggregator.buildMode3(serverPlayer.getInventory());
                }

                if (vi != null) {
                    int currentHash = vi.emptyShulkerSlots;
                    for (svemocan.vanilla_storage_interface.network.VirtualItem item : vi.items) {
                        currentHash = currentHash * 31 + net.minecraft.registry.Registries.ITEM.getId(item.stack().getItem()).hashCode();
                        currentHash = currentHash * 31 + item.count();
                        if (item.stack().getComponentChanges() != null) {
                            currentHash = currentHash * 31 + item.stack().getComponentChanges().hashCode();
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
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        if (player.getWorld().isClient) return ItemStack.EMPTY;

        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasStack()) {
            ItemStack stackInSlot = slot.getStack();
            ItemStack originalStack = stackInSlot.copy();

            slot.setStackNoCallbacks(ItemStack.EMPTY);

            boolean inserted = svemocan.vanilla_storage_interface.StorageMutator.insertDirectly(
                    (net.minecraft.server.network.ServerPlayerEntity) player, this, stackInSlot
            );

            if (inserted) {
                if (!stackInSlot.isEmpty()) {
                    slot.setStackNoCallbacks(stackInSlot);
                }
                slot.markDirty();
                return originalStack;
            } else {
                slot.setStackNoCallbacks(originalStack);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (!player.getWorld().isClient) {
            net.minecraft.world.World world = player.getWorld();

            if (this.terminalMode == 1) {
                world.playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_ENDER_CHEST_CLOSE, net.minecraft.sound.SoundCategory.BLOCKS, 0.5f, 1.0f);
            } else if (this.terminalMode == 2) {
                world.playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_SHULKER_BOX_CLOSE, net.minecraft.sound.SoundCategory.BLOCKS, 0.5f, 1.0f);
            } else if (this.terminalMode == 0) {
                net.minecraft.block.BlockState state = world.getBlockState(this.interfacePos);
                if (state.contains(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING)) {
                    net.minecraft.util.math.Direction facing = state.get(svemocan.vanilla_storage_interface.StorageInterfaceBlock.FACING);
                    net.minecraft.util.math.BlockPos targetPos = this.interfacePos.offset(facing.getOpposite());
                    net.minecraft.block.BlockState targetState = world.getBlockState(targetPos);
                    net.minecraft.block.Block targetBlock = targetState.getBlock();

                    if (targetBlock instanceof net.minecraft.block.ChestBlock) {
                        world.playSound(null, this.interfacePos, net.minecraft.sound.SoundEvents.BLOCK_CHEST_CLOSE, net.minecraft.sound.SoundCategory.BLOCKS, 0.5f, 1.0f);
                    } else if (targetBlock instanceof net.minecraft.block.BarrelBlock) {
                        world.playSound(null, this.interfacePos, net.minecraft.sound.SoundEvents.BLOCK_BARREL_CLOSE, net.minecraft.sound.SoundCategory.BLOCKS, 0.5f, 1.0f);
                    }
                }
            }
        }
    }
}
package svemocan.vanilla_storage_interface;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import svemocan.VanillaStorageInterface;

public class StorageInterfaceBlockEntity extends BlockEntity {
    private ItemStack displayItem = ItemStack.EMPTY;
    private float displayScale = 2.0f;
    private float displayRotation = 0.0f;

    public StorageInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(VanillaStorageInterface.STORAGE_INTERFACE_BLOCK_ENTITY, pos, state);
    }

    public ItemStack getDisplayItem() { return this.displayItem; }
    public float getDisplayScale() { return this.displayScale; }
    public float getDisplayRotation() { return this.displayRotation; }

    public void setDisplayItem(ItemStack stack) {
        this.displayItem = stack;
        this.syncToClient();
    }

    public void setDisplayScale(float scale) {
        // Enforce boundaries to prevent oversized item models from aggressively clipping through neighboring blocks
        this.displayScale = Math.max(0.1f, Math.min(2.0f, scale));
        this.syncToClient();
    }

    public void setDisplayRotation(float rotation) {
        this.displayRotation = rotation % 360.0f;
        this.syncToClient();
    }

    private void syncToClient() {
        this.markDirty();
        if (this.world != null && !this.world.isClient) {
            // Block update flag 3 (Block.NOTIFY_LISTENERS | Block.NOTIFY_ALL) forces an immediate
            // state synchronization to nearby clients and requests a block re-render chunk update.
            this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), 3);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        if (!this.displayItem.isEmpty()) {
            // Encode handles modern component-based serialization introduced in 1.20.5+ automatically
            nbt.put("DisplayItem", this.displayItem.encode(registryLookup));
        }
        nbt.putFloat("DisplayScale", this.displayScale);
        nbt.putFloat("DisplayRotation", this.displayRotation);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        ItemStack oldItem = this.displayItem;
        float oldScale = this.displayScale;
        float oldRotation = this.displayRotation;

        if (nbt.contains("DisplayItem", NbtElement.COMPOUND_TYPE)) {
            this.displayItem = ItemStack.fromNbtOrEmpty(registryLookup, nbt.getCompound("DisplayItem"));
        } else {
            this.displayItem = ItemStack.EMPTY;
        }

        if (nbt.contains("DisplayScale")) this.displayScale = nbt.getFloat("DisplayScale");
        if (nbt.contains("DisplayRotation")) this.displayRotation = nbt.getFloat("DisplayRotation");

        if (this.world != null && this.world.isClient) {
            // Client-Side Mesh Rebuild Trigger: If data arriving via the sync network packet differs
            // from the current client properties, explicitly signal a chunk re-render.
            if (!ItemStack.areItemsAndComponentsEqual(oldItem, this.displayItem) || oldScale != this.displayScale || oldRotation != this.displayRotation) {
                this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), 3);
            }
        }
    }

    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }
}
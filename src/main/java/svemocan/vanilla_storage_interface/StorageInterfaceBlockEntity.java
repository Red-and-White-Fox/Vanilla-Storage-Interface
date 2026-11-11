package svemocan.vanilla_storage_interface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            // Block update flag 3 (Block.NOTIFY_LISTENERS | Block.NOTIFY_ALL) forces an immediate
            // state synchronization to nearby clients and requests a block re-render chunk update.
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.displayItem.isEmpty()) {
            output.store("DisplayItem", ItemStack.CODEC, this.displayItem);
        }
        output.putFloat("DisplayScale", this.displayScale);
        output.putFloat("DisplayRotation", this.displayRotation);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ItemStack oldItem = this.displayItem;
        float oldScale = this.displayScale;
        float oldRotation = this.displayRotation;

        this.displayItem = input.read("DisplayItem", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.displayScale = input.getFloatOr("DisplayScale", 2.0f);
        this.displayRotation = input.getFloatOr("DisplayRotation", 0.0f);

        if (this.level != null && this.level.isClientSide()) {
            // Client-Side Mesh Rebuild Trigger: If data arriving via the sync network packet differs
            // from the current client properties, explicitly signal a chunk re-render.
            if (!ItemStack.isSameItemSameComponents(oldItem, this.displayItem) || oldScale != this.displayScale || oldRotation != this.displayRotation) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return saveWithoutMetadata(registryLookup);
    }
}

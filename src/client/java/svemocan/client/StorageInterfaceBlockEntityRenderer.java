package svemocan.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import svemocan.VanillaStorageInterface;
import svemocan.vanilla_storage_interface.StorageInterfaceBlock;
import svemocan.vanilla_storage_interface.StorageInterfaceBlockEntity;

public class StorageInterfaceBlockEntityRenderer implements BlockEntityRenderer<StorageInterfaceBlockEntity, StorageInterfaceBlockEntityRenderer.StorageInterfaceRenderState> {
    private final ItemModelResolver itemModelResolver;

    public StorageInterfaceBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    public static class StorageInterfaceRenderState extends BlockEntityRenderState {
        public final ItemStackRenderState itemRenderState = new ItemStackRenderState();
        public Direction facing = Direction.NORTH;
        public float scale = 1.0f;
        public float rotation = 0.0f;
    }

    @Override
    public StorageInterfaceRenderState createRenderState() {
        return new StorageInterfaceRenderState();
    }

    @Override
    public void extractRenderState(StorageInterfaceBlockEntity blockEntity, StorageInterfaceRenderState renderState, float partialTicks, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay);

        if (!VanillaStorageInterface.CONFIG.renderGhostItems) {
            renderState.itemRenderState.clear();
            return;
        }

        ItemStack displayStack = blockEntity.getDisplayItem();
        if (displayStack == null || displayStack.isEmpty() || displayStack.is(Items.STRUCTURE_VOID)) {
            renderState.itemRenderState.clear();
            return;
        }

        renderState.facing = blockEntity.getBlockState().hasProperty(StorageInterfaceBlock.FACING)
                ? blockEntity.getBlockState().getValue(StorageInterfaceBlock.FACING)
                : Direction.NORTH;
        renderState.scale = blockEntity.getDisplayScale();
        renderState.rotation = blockEntity.getDisplayRotation();

        int seed = (int) blockEntity.getBlockPos().asLong();
        this.itemModelResolver.updateForTopItem(renderState.itemRenderState, displayStack, ItemDisplayContext.FIXED, blockEntity.getLevel(), null, seed);
    }

    @Override
    public void submit(StorageInterfaceRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        if (renderState.itemRenderState.isEmpty()) {
            return;
        }

        poseStack.pushPose();

        float itemScale = 0.35f * renderState.scale;
        float glassDepthOffset = 0.47f;

        poseStack.translate(0.5f, 0.5f, 0.5f);

        Direction facing = renderState.facing;
        poseStack.translate(facing.getStepX() * glassDepthOffset, facing.getStepY() * glassDepthOffset, facing.getStepZ() * glassDepthOffset);

        switch (facing) {
            case NORTH -> poseStack.rotateDegrees(Axis.YP, 180.0f);
            case SOUTH -> {}
            case WEST  -> poseStack.rotateDegrees(Axis.YP, -90.0f);
            case EAST  -> poseStack.rotateDegrees(Axis.YP, 90.0f);
            case UP    -> {
                poseStack.rotateDegrees(Axis.YP, 180.0f);
                poseStack.rotateDegrees(Axis.XP, 90.0f);
            }
            case DOWN  -> {
                poseStack.rotateDegrees(Axis.YP, 180.0f);
                poseStack.rotateDegrees(Axis.XP, -90.0f);
            }
        }

        if (renderState.rotation != 0.0f) {
            poseStack.rotateDegrees(Axis.ZP, -renderState.rotation);
        }

        poseStack.scale(itemScale, itemScale, itemScale);

        renderState.itemRenderState.submit(poseStack, submitNodeCollector, renderState.lightCoords, OverlayTexture.NO_OVERLAY, 0);

        poseStack.popPose();
    }
}

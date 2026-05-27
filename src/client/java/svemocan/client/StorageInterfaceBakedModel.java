package svemocan.client;

import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import svemocan.VanillaStorageInterface;
import svemocan.vanilla_storage_interface.StorageInterfaceBlock;
import svemocan.vanilla_storage_interface.StorageInterfaceBlockEntity;

import java.util.List;
import java.util.function.Supplier;

public class StorageInterfaceBakedModel implements BakedModel, FabricBakedModel {
    private final BakedModel baseModel;

    public StorageInterfaceBakedModel(BakedModel baseModel) {
        this.baseModel = baseModel;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<net.minecraft.util.math.random.Random> randomSupplier, RenderContext context) {
        // Render the underlying block's geometry first before dealing with the dynamic item overlay
        ((FabricBakedModel) this.baseModel).emitBlockQuads(blockView, state, pos, randomSupplier, context);

        if (!VanillaStorageInterface.CONFIG.renderGhostItems) return;

        BlockEntity blockEntity = blockView.getBlockEntity(pos);
        if (blockEntity instanceof StorageInterfaceBlockEntity interfaceEntity) {
            ItemStack displayStack = interfaceEntity.getDisplayItem();

            if (displayStack != null && !displayStack.isEmpty() && !displayStack.isOf(net.minecraft.item.Items.STRUCTURE_VOID)) {
                BakedModel itemModel = MinecraftClient.getInstance().getItemRenderer().getModel(displayStack, null, null, 0);
                Direction facing = state.contains(StorageInterfaceBlock.FACING) ? state.get(StorageInterfaceBlock.FACING) : Direction.NORTH;

                // ==========================================
                // TUNING VARIABLES
                // ==========================================
                float ITEM_SCALE = 0.35f;
                float GLASS_DEPTH_OFFSET = -0.47f;

                // Grab the exact Matrix Minecraft uses to flatten swords/fences into item frames natively.
                // This ensures non-standard item models lie flat against the interface.
                net.minecraft.client.render.model.json.ModelTransformation transformation = itemModel.getTransformation();
                net.minecraft.client.render.model.json.Transformation fixed = transformation.fixed;
                Matrix4f fixedMatrix = null;

                if (fixed != null && fixed != net.minecraft.client.render.model.json.Transformation.IDENTITY) {
                    net.minecraft.client.util.math.MatrixStack dummyStack = new net.minecraft.client.util.math.MatrixStack();
                    fixed.apply(false, dummyStack);
                    fixedMatrix = dummyStack.peek().getPositionMatrix();
                }

                QuadEmitter emitter = context.getEmitter();
                Direction[] searchDirections = new Direction[]{null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

                Vector3f vec = new Vector3f();
                RenderMaterial defaultMaterial = RendererAccess.INSTANCE.getRenderer().materialFinder().find();

                for (Direction d : searchDirections) {
                    for (BakedQuad quad : itemModel.getQuads(null, d, randomSupplier.get())) {
                        emitter.fromVanilla(quad, defaultMaterial, d);

                        for (int i = 0; i < 4; i++) {
                            vec.set(emitter.x(i), emitter.y(i), emitter.z(i));

                            // Shift to origin (0,0,0) before applying rotations/scale to prevent orbital transformations
                            vec.sub(0.5f, 0.5f, 0.5f);

                            // Apply the item frame fixed transformation matrix if one exists
                            if (fixedMatrix != null) {
                                vec.mulPosition(fixedMatrix);
                            }

                            // Vanilla item frames natively apply a 180-degree Y rotation, so we turn it around.
                            vec.rotateY((float) Math.PI);

                            vec.mul(ITEM_SCALE * interfaceEntity.getDisplayScale());

                            // We add a negative sign here because flipping the Y-axis above naturally reverses the Z-axis rotation visually.
                            vec.rotateZ(interfaceEntity.getDisplayRotation() * ((float) -Math.PI / 180F));

                            switch (facing) {
                                case NORTH -> vec.rotateY((float) Math.PI);
                                case SOUTH -> {}
                                case WEST  -> vec.rotateY((float) (-Math.PI / 2));
                                case EAST  -> vec.rotateY((float) (Math.PI / 2));
                                case UP    -> { vec.rotateY((float) Math.PI); vec.rotateX((float) (Math.PI / 2)); }
                                case DOWN  -> { vec.rotateY((float) Math.PI); vec.rotateX((float) (-Math.PI / 2)); }
                            }

                            vec.add(0.5f + facing.getOffsetX() * GLASS_DEPTH_OFFSET,
                                    0.5f + facing.getOffsetY() * GLASS_DEPTH_OFFSET,
                                    0.5f + facing.getOffsetZ() * GLASS_DEPTH_OFFSET);

                            emitter.pos(i, vec.x, vec.y, vec.z);
                        }

                        // Null out cullFace to prevent the dynamic item overlay from disappearing when a block is placed next to it
                        emitter.cullFace(null);
                        emitter.emit();
                    }
                }
            }
        }
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<net.minecraft.util.math.random.Random> randomSupplier, RenderContext context) {
        ((FabricBakedModel) this.baseModel).emitItemQuads(stack, randomSupplier, context);
    }

    @Override public List<BakedQuad> getQuads(BlockState state, Direction face, net.minecraft.util.math.random.Random random) { return baseModel.getQuads(state, face, random); }
    @Override public boolean useAmbientOcclusion() { return baseModel.useAmbientOcclusion(); }
    @Override public boolean hasDepth() { return baseModel.hasDepth(); }
    @Override public boolean isSideLit() { return baseModel.isSideLit(); }
    @Override public boolean isBuiltin() { return baseModel.isBuiltin(); }
    @Override public Sprite getParticleSprite() { return baseModel.getParticleSprite(); }
    @Override public net.minecraft.client.render.model.json.ModelTransformation getTransformation() { return baseModel.getTransformation(); }
    @Override public ModelOverrideList getOverrides() { return baseModel.getOverrides(); }
}
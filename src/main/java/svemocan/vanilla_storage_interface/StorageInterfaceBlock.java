package svemocan.vanilla_storage_interface;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import net.minecraft.sound.BlockSoundGroup;
import svemocan.VanillaStorageInterface;

public class StorageInterfaceBlock extends Block implements BlockEntityProvider {
    public static final DirectionProperty FACING = Properties.FACING;
    public static final net.minecraft.state.property.BooleanProperty ON_CHEST = net.minecraft.state.property.BooleanProperty.of("on_chest");
    public static final net.minecraft.state.property.BooleanProperty ILLUMINATED = net.minecraft.state.property.BooleanProperty.of("illuminated");

    // Bounding boxes are split between centered/lowered alignments to prevent the model
    // from floating awkwardly when attached to smaller non-full containers like Chests.
    private static final VoxelShape NORTH_CENTERED = Block.createCuboidShape(1, 1, 15, 15, 15, 16);
    private static final VoxelShape SOUTH_CENTERED = Block.createCuboidShape(1, 1, 0, 15, 15, 1);
    private static final VoxelShape WEST_CENTERED = Block.createCuboidShape(15, 1, 1, 16, 15, 15);
    private static final VoxelShape EAST_CENTERED = Block.createCuboidShape(0, 1, 1, 1, 15, 15);

    private static final VoxelShape NORTH_LOWERED = Block.createCuboidShape(1, 0, 15, 15, 14, 16);
    private static final VoxelShape SOUTH_LOWERED = Block.createCuboidShape(1, 0, 0, 15, 14, 1);
    private static final VoxelShape WEST_LOWERED = Block.createCuboidShape(15, 0, 1, 16, 14, 15);
    private static final VoxelShape EAST_LOWERED = Block.createCuboidShape(0, 0, 1, 1, 14, 15);

    private static final VoxelShape UP_SHAPE = Block.createCuboidShape(1, 0, 1, 15, 1, 15);
    private static final VoxelShape DOWN_SHAPE = Block.createCuboidShape(1, 15, 1, 15, 16, 15);

    public StorageInterfaceBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH).with(ON_CHEST, false).with(ILLUMINATED, false));
    }

    @Override
    public BlockSoundGroup getSoundGroup(BlockState state) {
        return VanillaStorageInterface.CONFIG.muteInterfaceSounds ? VanillaStorageInterface.SILENT_SOUNDS : VanillaStorageInterface.INTERFACE_SOUNDS;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON_CHEST, ILLUMINATED);
    }

    @Override
    public boolean canPlaceAt(BlockState state, net.minecraft.world.WorldView world, BlockPos pos) {
        // Validation check: The block can only exist if attached directly to an active Inventory block structure
        net.minecraft.util.math.Direction facing = state.get(FACING);
        BlockPos targetPos = pos.offset(facing.getOpposite());
        return world.getBlockEntity(targetPos) instanceof net.minecraft.inventory.Inventory;
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && !player.isCreative()) {
            net.minecraft.util.ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), new net.minecraft.item.ItemStack(this.asItem()));
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    public void onDestroyedByExplosion(World world, BlockPos pos, net.minecraft.world.explosion.Explosion explosion) {
        if (!world.isClient) {
            net.minecraft.util.ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), new net.minecraft.item.ItemStack(this.asItem()));
        }
        super.onDestroyedByExplosion(world, pos, explosion);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        net.minecraft.item.ItemStack handStack = player.getStackInHand(player.getActiveHand());

        // Decorative/Utility triggers: Handles visual panel illumination adjustments
        if (handStack.isOf(net.minecraft.item.Items.GLOW_INK_SAC) && !state.get(ILLUMINATED)) {
            world.setBlockState(pos, state.with(ILLUMINATED, true), 3);
            world.playSound(null, pos, net.minecraft.sound.SoundEvents.ITEM_GLOW_INK_SAC_USE, net.minecraft.sound.SoundCategory.BLOCKS, 1.0F, 1.0F);
            if (!player.isCreative()) handStack.decrement(1);
            return ActionResult.SUCCESS;
        } else if ((handStack.isOf(net.minecraft.item.Items.SPONGE) || handStack.isOf(net.minecraft.item.Items.WET_SPONGE)) && state.get(ILLUMINATED)) {
            world.setBlockState(pos, state.with(ILLUMINATED, false), 3);
            world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_SPONGE_ABSORB, net.minecraft.sound.SoundCategory.BLOCKS, 1.0F, 1.0F);
            return ActionResult.SUCCESS;
        }

        if (!world.isClient) {
            net.minecraft.util.math.Direction facing = state.get(FACING);
            net.minecraft.util.math.BlockPos attachedPos = pos.offset(facing.getOpposite());
            net.minecraft.block.Block attachedBlock = world.getBlockState(attachedPos).getBlock();

            // Match sound design contextually to mimic standard container handling upon activation
            if (attachedBlock instanceof net.minecraft.block.ChestBlock) {
                world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_CHEST_OPEN, net.minecraft.sound.SoundCategory.BLOCKS, 0.5F, world.random.nextFloat() * 0.1F + 0.9F);
            } else if (attachedBlock instanceof net.minecraft.block.BarrelBlock) {
                world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_BARREL_OPEN, net.minecraft.sound.SoundCategory.BLOCKS, 0.5F, world.random.nextFloat() * 0.1F + 0.9F);
            } else if (attachedBlock instanceof net.minecraft.block.EnderChestBlock) {
                world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_ENDER_CHEST_OPEN, net.minecraft.sound.SoundCategory.BLOCKS, 0.5F, world.random.nextFloat() * 0.1F + 0.9F);
            } else if (attachedBlock instanceof net.minecraft.block.ShulkerBoxBlock) {
                world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_SHULKER_BOX_OPEN, net.minecraft.sound.SoundCategory.BLOCKS, 0.5F, world.random.nextFloat() * 0.1F + 0.9F);
            }

            player.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                    (syncId, inv, p) -> new svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler(syncId, inv, pos),
                    net.minecraft.text.Text.literal("Storage Interface")
            ));

            svemocan.vanilla_storage_interface.StorageAggregator.VirtualInventory vi = svemocan.vanilla_storage_interface.StorageAggregator.build(world, pos);

            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    (net.minecraft.server.network.ServerPlayerEntity) player,
                    new svemocan.vanilla_storage_interface.network.StorageSyncPayload(vi.items, vi.emptyShulkerSlots, pos, false)
            );
        }
        return net.minecraft.util.ActionResult.SUCCESS;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        boolean onChest = state.get(ON_CHEST);
        return switch (state.get(FACING)) {
            case SOUTH -> onChest ? SOUTH_LOWERED : SOUTH_CENTERED;
            case WEST -> onChest ? WEST_LOWERED : WEST_CENTERED;
            case EAST -> onChest ? EAST_LOWERED : EAST_CENTERED;
            case UP -> UP_SHAPE;
            case DOWN -> DOWN_SHAPE;
            default -> onChest ? NORTH_LOWERED : NORTH_CENTERED;
        };
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getSide();
        BlockPos attachedPos = ctx.getBlockPos().offset(facing.getOpposite());
        Block attachedBlock = ctx.getWorld().getBlockState(attachedPos).getBlock();
        boolean isChest = attachedBlock instanceof net.minecraft.block.ChestBlock || attachedBlock instanceof net.minecraft.block.EnderChestBlock;
        return this.getDefaultState().with(FACING, facing).with(ON_CHEST, isChest).with(ILLUMINATED, false);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, net.minecraft.world.WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        // Listens to shifts behind its facing surface; triggers recalculations if the container type drops or transforms
        if (direction == state.get(FACING).getOpposite()) {
            boolean isChest = neighborState.getBlock() instanceof net.minecraft.block.ChestBlock || neighborState.getBlock() instanceof net.minecraft.block.EnderChestBlock;
            return state.with(ON_CHEST, isChest);
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StorageInterfaceBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
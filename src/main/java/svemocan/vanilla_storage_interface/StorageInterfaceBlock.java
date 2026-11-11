package svemocan.vanilla_storage_interface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import svemocan.VanillaStorageInterface;

public class StorageInterfaceBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ON_CHEST = net.minecraft.world.level.block.state.properties.BooleanProperty.create("on_chest");
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ILLUMINATED = net.minecraft.world.level.block.state.properties.BooleanProperty.create("illuminated");

    // Bounding boxes are split between centered/lowered alignments to prevent the model
    // from floating awkwardly when attached to smaller non-full containers like Chests.
    private static final VoxelShape NORTH_CENTERED = Block.box(1, 1, 15, 15, 15, 16);
    private static final VoxelShape SOUTH_CENTERED = Block.box(1, 1, 0, 15, 15, 1);
    private static final VoxelShape WEST_CENTERED = Block.box(15, 1, 1, 16, 15, 15);
    private static final VoxelShape EAST_CENTERED = Block.box(0, 1, 1, 1, 15, 15);

    private static final VoxelShape NORTH_LOWERED = Block.box(1, 0, 15, 15, 14, 16);
    private static final VoxelShape SOUTH_LOWERED = Block.box(1, 0, 0, 15, 14, 1);
    private static final VoxelShape WEST_LOWERED = Block.box(15, 0, 1, 16, 14, 15);
    private static final VoxelShape EAST_LOWERED = Block.box(0, 0, 1, 1, 14, 15);

    private static final VoxelShape UP_SHAPE = Block.box(1, 0, 1, 15, 1, 15);
    private static final VoxelShape DOWN_SHAPE = Block.box(1, 15, 1, 15, 16, 15);

    public StorageInterfaceBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON_CHEST, false).setValue(ILLUMINATED, false));
    }

    @Override
    public SoundType getSoundType(BlockState state) {
        return VanillaStorageInterface.CONFIG.muteInterfaceSounds ? VanillaStorageInterface.SILENT_SOUNDS : VanillaStorageInterface.INTERFACE_SOUNDS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON_CHEST, ILLUMINATED);
    }

    @Override
    public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader world, BlockPos pos) {
        // Validation check: The block can only exist if attached directly to an active Inventory block structure
        net.minecraft.core.Direction facing = state.getValue(FACING);
        BlockPos targetPos = pos.relative(facing.getOpposite());
        return world.getBlockEntity(targetPos) instanceof net.minecraft.world.Container;
    }

    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide() && !player.isCreative()) {
            net.minecraft.world.Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), new net.minecraft.world.item.ItemStack(this.asItem()));
        }
        return super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void wasExploded(net.minecraft.server.level.ServerLevel world, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        net.minecraft.world.Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), new net.minecraft.world.item.ItemStack(this.asItem()));
        super.wasExploded(world, pos, explosion);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        net.minecraft.world.item.ItemStack handStack = player.getItemInHand(player.getUsedItemHand());

        // Decorative/Utility triggers: Handles visual panel illumination adjustments
        if (handStack.is(net.minecraft.world.item.Items.GLOW_INK_SAC) && !state.getValue(ILLUMINATED)) {
            world.setBlock(pos, state.setValue(ILLUMINATED, true), 3);
            world.playSound(null, pos, net.minecraft.sounds.SoundEvents.GLOW_INK_SAC_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.isCreative()) handStack.shrink(1);
            return InteractionResult.SUCCESS;
        } else if ((handStack.is(net.minecraft.world.item.Items.SPONGE) || handStack.is(net.minecraft.world.item.Items.WET_SPONGE)) && state.getValue(ILLUMINATED)) {
            world.setBlock(pos, state.setValue(ILLUMINATED, false), 3);
            world.playSound(null, pos, net.minecraft.sounds.SoundEvents.SPONGE_ABSORB, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        if (!world.isClientSide()) {
            net.minecraft.core.Direction facing = state.getValue(FACING);
            net.minecraft.core.BlockPos attachedPos = pos.relative(facing.getOpposite());
            net.minecraft.world.level.block.Block attachedBlock = world.getBlockState(attachedPos).getBlock();

            // Match sound design contextually to mimic standard container handling upon activation
            if (attachedBlock instanceof net.minecraft.world.level.block.ChestBlock) {
                world.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHEST_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, world.getRandom().nextFloat() * 0.1F + 0.9F);
            } else if (attachedBlock instanceof net.minecraft.world.level.block.BarrelBlock) {
                world.playSound(null, pos, net.minecraft.sounds.SoundEvents.BARREL_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, world.getRandom().nextFloat() * 0.1F + 0.9F);
            } else if (attachedBlock instanceof net.minecraft.world.level.block.EnderChestBlock) {
                world.playSound(null, pos, net.minecraft.sounds.SoundEvents.ENDER_CHEST_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, world.getRandom().nextFloat() * 0.1F + 0.9F);
            } else if (attachedBlock instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
                world.playSound(null, pos, net.minecraft.sounds.SoundEvents.SHULKER_BOX_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, world.getRandom().nextFloat() * 0.1F + 0.9F);
            }

            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (syncId, inv, p) -> new svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler(syncId, inv, pos),
                    net.minecraft.network.chat.Component.literal("Storage Interface")
            ));

            svemocan.vanilla_storage_interface.StorageAggregator.VirtualInventory vi = svemocan.vanilla_storage_interface.StorageAggregator.build(world, pos);

            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    (net.minecraft.server.level.ServerPlayer) player,
                    new svemocan.vanilla_storage_interface.network.StorageSyncPayload(vi.items, vi.emptyShulkerSlots, pos, false)
            );
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        boolean onChest = state.getValue(ON_CHEST);
        return switch (state.getValue(FACING)) {
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
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getClickedFace();
        BlockPos attachedPos = ctx.getClickedPos().relative(facing.getOpposite());
        Block attachedBlock = ctx.getLevel().getBlockState(attachedPos).getBlock();
        boolean isChest = attachedBlock instanceof net.minecraft.world.level.block.ChestBlock || attachedBlock instanceof net.minecraft.world.level.block.EnderChestBlock;
        return this.defaultBlockState().setValue(FACING, facing).setValue(ON_CHEST, isChest).setValue(ILLUMINATED, false);
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader world, net.minecraft.world.level.ScheduledTickAccess tickAccess, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, net.minecraft.util.RandomSource random) {
        // Listens to shifts behind its facing surface; triggers recalculations if the container type drops or transforms
        if (direction == state.getValue(FACING).getOpposite()) {
            boolean isChest = neighborState.getBlock() instanceof net.minecraft.world.level.block.ChestBlock || neighborState.getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock;
            return state.setValue(ON_CHEST, isChest);
        }
        return super.updateShape(state, world, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageInterfaceBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}

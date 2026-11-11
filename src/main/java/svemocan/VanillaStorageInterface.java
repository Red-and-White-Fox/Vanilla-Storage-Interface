package svemocan;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import svemocan.vanilla_storage_interface.StorageAggregator;
import svemocan.vanilla_storage_interface.StorageInterfaceBlock;
import svemocan.vanilla_storage_interface.StorageInterfaceBlockEntity;
import svemocan.vanilla_storage_interface.StorageMutator;
import svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler;
import svemocan.vanilla_storage_interface.network.OpenRawUIPayload;
import svemocan.vanilla_storage_interface.network.OpenTerminalPayload;
import svemocan.vanilla_storage_interface.network.StorageActionPayload;
import svemocan.vanilla_storage_interface.network.StorageSyncPayload;
import java.util.HashMap;
import java.util.Map;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import svemocan.vanilla_storage_interface.config.VanillaStorageConfig;

public class VanillaStorageInterface implements ModInitializer {
    public static VanillaStorageConfig CONFIG;
    public static final String MOD_ID = "vanilla_storage_interface";
    public static net.minecraft.world.inventory.MenuType<svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler> STORAGE_INTERFACE_SCREEN_HANDLER;

    public static final Map<String, Block> INTERFACE_BLOCKS = new HashMap<>();
    public static BlockEntityType<StorageInterfaceBlockEntity> STORAGE_INTERFACE_BLOCK_ENTITY;
    public static CreativeModeTab VANILLA_STORAGE_GROUP;

    public static final SoundType INTERFACE_SOUNDS = new SoundType(
            1.0F, 1.0F,
            SoundEvents.WOOD_BREAK,
            SoundEvents.WOOD_STEP,
            SoundEvents.ITEM_FRAME_PLACE,
            SoundEvents.WOOD_HIT,
            SoundEvents.WOOD_FALL
    );

    // Mute capability framework: Uses INTENTIONALLY_EMPTY sound events to safely cancel acoustic
    // feedback via code-driven configuration without altering standard physical voxel mining attributes.
    public static final SoundType SILENT_SOUNDS = new SoundType(
            0.0F, 1.0F,
            SoundEvents.EMPTY,
            SoundEvents.EMPTY,
            SoundEvents.EMPTY,
            SoundEvents.EMPTY,
            SoundEvents.EMPTY
    );

    public static final String[] MATERIAL_TYPES = {
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "mangrove", "cherry", "bamboo", "crimson", "warped",
            "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log",
            "mangrove_log", "cherry_log", "bamboo_block", "crimson_stem", "warped_stem",
            "stripped_oak_log", "stripped_spruce_log", "stripped_birch_log", "stripped_jungle_log",
            "stripped_acacia_log", "stripped_dark_oak_log", "stripped_mangrove_log",
            "stripped_cherry_log", "stripped_bamboo_block", "stripped_crimson_stem", "stripped_warped_stem",
            "black_stained_glass"
    };

    @Override
    public void onInitialize() {
        AutoConfig.register(VanillaStorageConfig.class, JanksonConfigSerializer::new);
        CONFIG = AutoConfig.getConfigHolder(VanillaStorageConfig.class).getConfig();

        // Data-driven iteration loop: dynamically populates registries across all material targets
        // to minimize boilerplate registration footprints.
        for (String wood : MATERIAL_TYPES) {
            Item frameItem = new Item(new Item.Properties());
            Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, wood + "_storage_frame"), frameItem);

            Block interfaceBlock = new StorageInterfaceBlock(
                    BlockBehaviour.Properties.of()
                            .strength(2.5f, 2.5f)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()
                            .lightLevel(state -> state.getValue(StorageInterfaceBlock.ILLUMINATED) ? 10 : 0)
            );
            Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, wood + "_storage_interface"), interfaceBlock);
            Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, wood + "_storage_interface"), new BlockItem(interfaceBlock, new Item.Properties()));

            INTERFACE_BLOCKS.put(wood, interfaceBlock);
        }

        VANILLA_STORAGE_GROUP = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, "main"),
                FabricCreativeModeTab.builder()
                        .icon(() -> new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(MOD_ID, "oak_storage_interface"))))
                        .title(Component.translatable("itemGroup.vanilla_storage_interface.main"))
                        .displayItems((context, entries) -> {
                            for (String wood : MATERIAL_TYPES) {
                                entries.accept(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(MOD_ID, wood + "_storage_frame")));
                                entries.accept(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(MOD_ID, wood + "_storage_interface")));
                            }
                        })
                        .build()
        );

        // Architectural Note: Mapping the entire dynamic block array to a single shared BlockEntityType
        // optimizes data tracker assignments and keeps block entity tracking overhead minimal.
        Block[] blockArray = INTERFACE_BLOCKS.values().toArray(new Block[0]);
        STORAGE_INTERFACE_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "storage_interface"),
                FabricBlockEntityTypeBuilder.<StorageInterfaceBlockEntity>create(StorageInterfaceBlockEntity::new, blockArray).build()
        );

        STORAGE_INTERFACE_SCREEN_HANDLER = Registry.register(
                BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(MOD_ID, "storage_interface"),
                new net.minecraft.world.inventory.MenuType<>(svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler::new, net.minecraft.world.flag.FeatureFlagSet.of())
        );

        // Modern Fabric Networking Framework: Network payloads require metadata type split registrations.
        // Codecs must be declared on PayloadTypeRegistry prior to attaching receiver endpoints via ServerPlayNetworking.
        PayloadTypeRegistry.serverboundPlay().register(OpenTerminalPayload.ID, OpenTerminalPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OpenRawUIPayload.ID, OpenRawUIPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(OpenTerminalPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.minecraft.server.level.ServerPlayer player = context.player();

                if (payload.mode() == 1 && CONFIG.enableVoidTerminal) {
                    player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ENDER_CHEST_OPEN, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, player.level().getRandom().nextFloat() * 0.1F + 0.9F);
                    player.openMenu(new SimpleMenuProvider((s, i, p) -> new StorageInterfaceScreenHandler(s, i, p.blockPosition(), 1, -1), Component.literal("Void Terminal")));
                    StorageAggregator.VirtualInventory vi = StorageAggregator.buildFromInventory(player.level(), player.getEnderChestInventory());
                    ServerPlayNetworking.send(player, new StorageSyncPayload(vi.items, vi.emptyShulkerSlots, player.blockPosition(), true));
                }
                else if (payload.mode() == 2 && CONFIG.enableBoxTerminal) {
                    int targetIndex = payload.targetId();
                    net.minecraft.world.item.ItemStack targetShulker = player.getInventory().getItem(targetIndex);

                    if (StorageMutator.isShulkerBox(targetShulker.getItem())) {
                        if (targetShulker.getCount() > 1) {
                            int emptySlot = player.getInventory().getFreeSlot();
                            if (emptySlot != -1) {
                                ItemStack singleBox = targetShulker.copyWithCount(1);
                                ItemStack leftover = targetShulker.copyWithCount(targetShulker.getCount() - 1);
                                player.getInventory().setItem(targetIndex, singleBox);
                                player.getInventory().setItem(emptySlot, leftover);
                                targetShulker = singleBox;
                            } else {
                                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cCannot open stacked Shulker Box. Your inventory is full!"), true);
                                return;
                            }
                        }

                        final net.minecraft.world.item.ItemStack finalShulker = targetShulker; // CRITICAL FIX: Captures the effectively final state for the lambda

                        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SHULKER_BOX_OPEN, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, player.level().getRandom().nextFloat() * 0.1F + 0.9F);
                        player.openMenu(new SimpleMenuProvider((s, i, p) -> new StorageInterfaceScreenHandler(s, i, p.blockPosition(), 2, targetIndex), finalShulker.getHoverName()));
                        StorageAggregator.VirtualInventory vi = StorageAggregator.buildFromInventory(player.level(), new StorageMutator.ShulkerItemInventory(finalShulker));
                        ServerPlayNetworking.send(player, new StorageSyncPayload(vi.items, vi.emptyShulkerSlots, player.blockPosition(), true));
                    }
                }
                else if (payload.mode() == 3 && CONFIG.enablePlayerInventoryTerminal) {
                    player.openMenu(new SimpleMenuProvider((s, i, p) -> new StorageInterfaceScreenHandler(s, i, p.blockPosition(), 3, -1), Component.literal("Player Inventory Terminal")));
                    StorageAggregator.VirtualInventory vi = StorageAggregator.buildMode3(player.getInventory());
                    ServerPlayNetworking.send(player, new StorageSyncPayload(vi.items, vi.emptyShulkerSlots, player.blockPosition(), true));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(OpenRawUIPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.minecraft.server.level.ServerPlayer player = context.player();

                if (payload.mode() == 1 && CONFIG.enableVoidRaw) {
                    player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ENDER_CHEST_OPEN, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, player.level().getRandom().nextFloat() * 0.1F + 0.9F);
                    player.openMenu(new SimpleMenuProvider((syncId, inv, p) ->
                            net.minecraft.world.inventory.ChestMenu.threeRows(syncId, inv, player.getEnderChestInventory()), Component.literal("Raw Void Storage")));
                }
                else if (payload.mode() == 2 && CONFIG.enableBoxRaw) {
                    net.minecraft.world.item.ItemStack targetShulker = player.getInventory().getItem(payload.targetId());
                    if (StorageMutator.isShulkerBox(targetShulker.getItem())) {
                        if (targetShulker.getCount() > 1) {
                            int emptySlot = player.getInventory().getFreeSlot();
                            if (emptySlot != -1) {
                                ItemStack singleBox = targetShulker.copyWithCount(1);
                                ItemStack leftover = targetShulker.copyWithCount(targetShulker.getCount() - 1);
                                player.getInventory().setItem(payload.targetId(), singleBox);
                                player.getInventory().setItem(emptySlot, leftover);
                                targetShulker = singleBox;
                            } else {
                                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cCannot open stacked Shulker Box. Your inventory is full!"), true);
                                return;
                            }
                        }

                        final net.minecraft.world.item.ItemStack finalShulker = targetShulker;

                        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SHULKER_BOX_OPEN, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, player.level().getRandom().nextFloat() * 0.1F + 0.9F);
                        player.openMenu(new SimpleMenuProvider((syncId, inv, p) ->
                                new net.minecraft.world.inventory.ShulkerBoxMenu(syncId, inv, new StorageMutator.ShulkerItemInventory(finalShulker)), finalShulker.getHoverName()));
                    }
                }
            });
        });

        PayloadTypeRegistry.clientboundPlay().register(StorageSyncPayload.ID, StorageSyncPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StorageActionPayload.ID, StorageActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(StorageActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> StorageMutator.handleAction(context.player(), payload));
        });
    }
}

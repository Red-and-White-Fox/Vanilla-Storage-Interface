package svemocan;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
    public static net.minecraft.screen.ScreenHandlerType<svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler> STORAGE_INTERFACE_SCREEN_HANDLER;

    public static final Map<String, Block> INTERFACE_BLOCKS = new HashMap<>();
    public static BlockEntityType<StorageInterfaceBlockEntity> STORAGE_INTERFACE_BLOCK_ENTITY;
    public static ItemGroup VANILLA_STORAGE_GROUP;

    public static final BlockSoundGroup INTERFACE_SOUNDS = new BlockSoundGroup(
            1.0F, 1.0F,
            SoundEvents.BLOCK_WOOD_BREAK,
            SoundEvents.BLOCK_WOOD_STEP,
            SoundEvents.ENTITY_ITEM_FRAME_PLACE,
            SoundEvents.BLOCK_WOOD_HIT,
            SoundEvents.BLOCK_WOOD_FALL
    );

    // Mute capability framework: Uses INTENTIONALLY_EMPTY sound events to safely cancel acoustic
    // feedback via code-driven configuration without altering standard physical voxel mining attributes.
    public static final BlockSoundGroup SILENT_SOUNDS = new BlockSoundGroup(
            0.0F, 1.0F,
            SoundEvents.INTENTIONALLY_EMPTY,
            SoundEvents.INTENTIONALLY_EMPTY,
            SoundEvents.INTENTIONALLY_EMPTY,
            SoundEvents.INTENTIONALLY_EMPTY,
            SoundEvents.INTENTIONALLY_EMPTY
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
            Item frameItem = new Item(new Item.Settings());
            Registry.register(Registries.ITEM, Identifier.of(MOD_ID, wood + "_storage_frame"), frameItem);

            Block interfaceBlock = new StorageInterfaceBlock(
                    AbstractBlock.Settings.create()
                            .strength(2.5f, 2.5f)
                            .requiresTool()
                            .nonOpaque()
                            .luminance(state -> state.get(StorageInterfaceBlock.ILLUMINATED) ? 10 : 0)
            );
            Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, wood + "_storage_interface"), interfaceBlock);
            Registry.register(Registries.ITEM, Identifier.of(MOD_ID, wood + "_storage_interface"), new BlockItem(interfaceBlock, new Item.Settings()));

            INTERFACE_BLOCKS.put(wood, interfaceBlock);
        }

        VANILLA_STORAGE_GROUP = Registry.register(Registries.ITEM_GROUP, Identifier.of(MOD_ID, "main"),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(Registries.ITEM.get(Identifier.of(MOD_ID, "oak_storage_interface"))))
                        .displayName(Text.translatable("itemGroup.vanilla_storage_interface.main"))
                        .entries((context, entries) -> {
                            for (String wood : MATERIAL_TYPES) {
                                entries.add(Registries.ITEM.get(Identifier.of(MOD_ID, wood + "_storage_frame")));
                                entries.add(Registries.ITEM.get(Identifier.of(MOD_ID, wood + "_storage_interface")));
                            }
                        })
                        .build()
        );

        // Architectural Note: Mapping the entire dynamic block array to a single shared BlockEntityType
        // optimizes data tracker assignments and keeps block entity tracking overhead minimal.
        Block[] blockArray = INTERFACE_BLOCKS.values().toArray(new Block[0]);
        STORAGE_INTERFACE_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "storage_interface"),
                BlockEntityType.Builder.create(StorageInterfaceBlockEntity::new, blockArray).build()
        );

        STORAGE_INTERFACE_SCREEN_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER, Identifier.of(MOD_ID, "storage_interface"),
                new net.minecraft.screen.ScreenHandlerType<>(svemocan.vanilla_storage_interface.gui.StorageInterfaceScreenHandler::new, net.minecraft.resource.featuretoggle.FeatureSet.empty())
        );

        // Modern Fabric Networking Framework (1.20.5+): Network payloads require metadata type split registrations.
        // Codecs must be declared on PayloadTypeRegistry prior to attaching receiver endpoints via ServerPlayNetworking.
        PayloadTypeRegistry.playC2S().register(OpenTerminalPayload.ID, OpenTerminalPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenRawUIPayload.ID, OpenRawUIPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(OpenTerminalPayload.ID, (payload, context) -> {
            // Context execution switching: Inbound packets arrive on netty network worker threads.
            // Any modification of inventory states or world interactions MUST be wrapped in context.server().execute()
            // to bounce execution back safely onto the main synchronized server tick loop thread.
            context.server().execute(() -> {
                net.minecraft.server.network.ServerPlayerEntity player = context.player();

                if (payload.mode() == 1 && CONFIG.enableVoidTerminal) {
                    player.getServerWorld().playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_ENDER_CHEST_OPEN, net.minecraft.sound.SoundCategory.PLAYERS, 0.5F, player.getServerWorld().random.nextFloat() * 0.1F + 0.9F);
                    player.openHandledScreen(new SimpleNamedScreenHandlerFactory((s, i, p) -> new StorageInterfaceScreenHandler(s, i, p.getBlockPos(), 1, -1), Text.literal("Void Terminal")));
                    StorageAggregator.VirtualInventory vi = StorageAggregator.buildFromInventory(player.getServerWorld(), player.getEnderChestInventory());
                    ServerPlayNetworking.send(player, new StorageSyncPayload(vi.items, vi.emptyShulkerSlots, player.getBlockPos(), true));
                }
                else if (payload.mode() == 2 && CONFIG.enableBoxTerminal) {
                    int targetIndex = payload.targetId();
                    net.minecraft.item.ItemStack targetShulker = player.getInventory().getStack(targetIndex);

                    if (StorageMutator.isShulkerBox(targetShulker.getItem())) {
                        player.getServerWorld().playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_SHULKER_BOX_OPEN, net.minecraft.sound.SoundCategory.PLAYERS, 0.5F, player.getServerWorld().random.nextFloat() * 0.1F + 0.9F);
                        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((s, i, p) -> new StorageInterfaceScreenHandler(s, i, p.getBlockPos(), 2, targetIndex), targetShulker.getName()));
                        StorageAggregator.VirtualInventory vi = StorageAggregator.buildFromInventory(player.getServerWorld(), new StorageMutator.ShulkerItemInventory(targetShulker));
                        ServerPlayNetworking.send(player, new StorageSyncPayload(vi.items, vi.emptyShulkerSlots, player.getBlockPos(), true));
                    }
                }
                else if (payload.mode() == 3 && CONFIG.enablePlayerInventoryTerminal) {
                    player.openHandledScreen(new SimpleNamedScreenHandlerFactory((s, i, p) -> new StorageInterfaceScreenHandler(s, i, p.getBlockPos(), 3, -1), Text.literal("Player Inventory Terminal")));
                    StorageAggregator.VirtualInventory vi = StorageAggregator.buildMode3(player.getInventory());
                    ServerPlayNetworking.send(player, new StorageSyncPayload(vi.items, vi.emptyShulkerSlots, player.getBlockPos(), true));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(OpenRawUIPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.minecraft.server.network.ServerPlayerEntity player = context.player();

                // Raw UI Bypass Routing: Allows custom storage definitions (like our ShulkerItemInventory wrappers)
                // to inject themselves straight into default vanilla screen factories, bypassing custom interfaces.
                if (payload.mode() == 1 && CONFIG.enableVoidRaw) {
                    player.getServerWorld().playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_ENDER_CHEST_OPEN, net.minecraft.sound.SoundCategory.PLAYERS, 0.5F, player.getServerWorld().random.nextFloat() * 0.1F + 0.9F);
                    player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, p) ->
                            net.minecraft.screen.GenericContainerScreenHandler.createGeneric9x3(syncId, inv, player.getEnderChestInventory()), Text.literal("Raw Void Storage")));
                }
                else if (payload.mode() == 2 && CONFIG.enableBoxRaw) {
                    net.minecraft.item.ItemStack targetShulker = player.getInventory().getStack(payload.targetId());
                    if (StorageMutator.isShulkerBox(targetShulker.getItem())) {
                        player.getServerWorld().playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_SHULKER_BOX_OPEN, net.minecraft.sound.SoundCategory.PLAYERS, 0.5F, player.getServerWorld().random.nextFloat() * 0.1F + 0.9F);
                        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, p) ->
                                new net.minecraft.screen.ShulkerBoxScreenHandler(syncId, inv, new StorageMutator.ShulkerItemInventory(targetShulker)), targetShulker.getName()));
                    }
                }
            });
        });

        PayloadTypeRegistry.playS2C().register(StorageSyncPayload.ID, StorageSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StorageActionPayload.ID, StorageActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(StorageActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> StorageMutator.handleAction(context.player(), payload));
        });
    }
}
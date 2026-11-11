package svemocan.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import svemocan.VanillaStorageInterface;
import svemocan.vanilla_storage_interface.gui.StorageInterfaceScreen;
import svemocan.vanilla_storage_interface.network.OpenRawUIPayload;
import svemocan.vanilla_storage_interface.network.OpenTerminalPayload;
import svemocan.vanilla_storage_interface.network.StorageSyncPayload;

public class VanillaStorageInterfaceClient implements ClientModInitializer {
    public static KeyMapping openVoidKey;
    public static KeyMapping openHoverKey;
    public static KeyMapping openHeldKey;
    public static KeyMapping openPlayerInvKey;

    public static double savedMouseX = -1;
    public static double savedMouseY = -1;

    @Override
    public void onInitializeClient() {
        MenuScreens.register(VanillaStorageInterface.STORAGE_INTERFACE_SCREEN_HANDLER, StorageInterfaceScreen::new);

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("vanilla_storage", "keys"));

        openVoidKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vanilla_storage.open_void", InputConstants.KEY_V, category));
        openHoverKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vanilla_storage.open_hover", InputConstants.KEY_H, category));
        openHeldKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vanilla_storage.open_held", InputConstants.KEY_B, category));
        openPlayerInvKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vanilla_storage.open_player_inv", InputConstants.KEY_N, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            boolean isShift = client.hasShiftDown();
            boolean invert = VanillaStorageInterface.CONFIG.invertShiftModifiers;

            // Determines which UI payload to send. XOR-style logic allows the config to flip the default shift behavior.
            boolean wantsRawUI = invert ? !isShift : isShift;

            // VOID TERMINAL
            while (openVoidKey.consumeClick()) {
                if (client.player.getInventory().contains(stack -> stack.is(Items.ENDER_CHEST))) {
                    ClientPlayNetworking.send(wantsRawUI ? new OpenRawUIPayload(1, -1) : new OpenTerminalPayload(1, -1));
                } else {
                    client.player.sendOverlayMessage(Component.literal("§cYou need an Ender Chest in your inventory!"));
                }
            }

            // HELD SHULKER TERMINAL
            while (openHeldKey.consumeClick()) {
                int targetSlot = -1;
                if (client.player.getMainHandItem().getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) {
                    targetSlot = client.player.getInventory().getSelectedSlot();
                } else if (client.player.getOffhandItem().getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) {
                    targetSlot = Inventory.SLOT_OFFHAND;
                }

                if (targetSlot != -1) {
                    ClientPlayNetworking.send(wantsRawUI ? new OpenRawUIPayload(2, targetSlot) : new OpenTerminalPayload(2, targetSlot));
                } else {
                    client.player.sendOverlayMessage(Component.literal("§cYou must be holding a Shulker Box!"));
                }
            }

            // PLAYER INVENTORY TERMINAL
            while (openPlayerInvKey.consumeClick()) {
                ClientPlayNetworking.send(new OpenTerminalPayload(3, -1));
            }
        });

        // Register custom block entity renderer for dynamic ghost item display on storage interface faces
        BlockEntityRendererRegistry.register(VanillaStorageInterface.STORAGE_INTERFACE_BLOCK_ENTITY, StorageInterfaceBlockEntityRenderer::new);

        // Global Receiver registration is safe here and avoids conflicts with Server initialization.
        ClientPlayNetworking.registerGlobalReceiver(StorageSyncPayload.ID, (payload, context) -> {
            // Screen updates MUST be scheduled on the main client thread to prevent concurrent modification crashes
            context.client().execute(() -> {
                if (context.client().gui.screen() instanceof StorageInterfaceScreen screen) {
                    screen.updateData(payload.items(), payload.emptyShulkerSlots(), payload.pos(), payload.isVirtual());
                }
            });
        });
    }
}

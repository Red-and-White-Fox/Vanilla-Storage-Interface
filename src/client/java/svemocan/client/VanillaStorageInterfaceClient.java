package svemocan.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import svemocan.VanillaStorageInterface;
import svemocan.vanilla_storage_interface.gui.StorageInterfaceScreen;
import svemocan.vanilla_storage_interface.network.StorageSyncPayload;

public class VanillaStorageInterfaceClient implements ClientModInitializer {
    public static net.minecraft.client.option.KeyBinding openVoidKey;
    public static net.minecraft.client.option.KeyBinding openHoverKey;
    public static net.minecraft.client.option.KeyBinding openHeldKey;
    public static net.minecraft.client.option.KeyBinding openPlayerInvKey;

    public static double savedMouseX = -1;
    public static double savedMouseY = -1;

    @Override
    public void onInitializeClient() {
        HandledScreens.register(VanillaStorageInterface.STORAGE_INTERFACE_SCREEN_HANDLER, StorageInterfaceScreen::new);

        openVoidKey = KeyBindingHelper.registerKeyBinding(new net.minecraft.client.option.KeyBinding("key.vanilla_storage.open_void", net.minecraft.client.util.InputUtil.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_V, "category.vanilla_storage.keys"));
        openHoverKey = KeyBindingHelper.registerKeyBinding(new net.minecraft.client.option.KeyBinding("key.vanilla_storage.open_hover", net.minecraft.client.util.InputUtil.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_H, "category.vanilla_storage.keys"));
        openHeldKey = KeyBindingHelper.registerKeyBinding(new net.minecraft.client.option.KeyBinding("key.vanilla_storage.open_held", net.minecraft.client.util.InputUtil.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_B, "category.vanilla_storage.keys"));
        openPlayerInvKey = KeyBindingHelper.registerKeyBinding(new net.minecraft.client.option.KeyBinding("key.vanilla_storage.open_player_inv", net.minecraft.client.util.InputUtil.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_N, "category.vanilla_storage.keys"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            boolean isShift = net.minecraft.client.gui.screen.Screen.hasShiftDown();
            boolean invert = VanillaStorageInterface.CONFIG.invertShiftModifiers;

            // Determines which UI payload to send. XOR-style logic allows the config to flip the default shift behavior.
            boolean wantsRawUI = invert ? !isShift : isShift;

            // VOID TERMINAL
            while (openVoidKey.wasPressed()) {
                if (client.player.getInventory().containsAny(stack -> stack.isOf(net.minecraft.item.Items.ENDER_CHEST))) {
                    ClientPlayNetworking.send(wantsRawUI ? new svemocan.vanilla_storage_interface.network.OpenRawUIPayload(1, -1) : new svemocan.vanilla_storage_interface.network.OpenTerminalPayload(1, -1));
                } else {
                    client.player.sendMessage(net.minecraft.text.Text.literal("§cYou need an Ender Chest in your inventory!"), true);
                }
            }

            // HELD SHULKER TERMINAL
            while (openHeldKey.wasPressed()) {
                int targetSlot = -1;
                if (client.player.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem bi && bi.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock) {
                    targetSlot = client.player.getInventory().selectedSlot;
                } else if (client.player.getOffHandStack().getItem() instanceof net.minecraft.item.BlockItem bi && bi.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock) {
                    // Slot 40 is the hardcoded vanilla index for the player's offhand slot
                    targetSlot = 40;
                }

                if (targetSlot != -1) {
                    ClientPlayNetworking.send(wantsRawUI ? new svemocan.vanilla_storage_interface.network.OpenRawUIPayload(2, targetSlot) : new svemocan.vanilla_storage_interface.network.OpenTerminalPayload(2, targetSlot));
                } else {
                    client.player.sendMessage(net.minecraft.text.Text.literal("§cYou must be holding a Shulker Box!"), true);
                }
            }

            // PLAYER INVENTORY TERMINAL
            while (openPlayerInvKey.wasPressed()) {
                ClientPlayNetworking.send(new svemocan.vanilla_storage_interface.network.OpenTerminalPayload(3, -1));
            }
        });

        ModelLoadingPlugin.register(pluginContext -> {
            pluginContext.modifyModelAfterBake().register((originalModel, context) -> {
                net.minecraft.client.util.ModelIdentifier modelId = context.topLevelId();

                if (modelId != null) {
                    // In modern Fabric, ModelIdentifier is a record, meaning we extract the Identifier directly via id()
                    net.minecraft.util.Identifier id = modelId.id();
                    if (id.getNamespace().equals("vanilla_storage_interface") && id.getPath().contains("storage_interface")) {
                        // Wraps the native block model with our dynamic renderer to display items on the block's face
                        return new StorageInterfaceBakedModel(originalModel);
                    }
                }
                return originalModel;
            });
        });

        // Global Receiver registration is safe here and avoids conflicts with Server initialization.
        ClientPlayNetworking.registerGlobalReceiver(StorageSyncPayload.ID, (payload, context) -> {
            // Screen updates MUST be scheduled on the main client thread to prevent concurrent modification crashes
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof StorageInterfaceScreen screen) {
                    screen.updateData(payload.items(), payload.emptyShulkerSlots(), payload.pos(), payload.isVirtual());
                }
            });
        });

        // Ensures custom interface blocks render transparency (glass parts) correctly
        for (net.minecraft.block.Block block : VanillaStorageInterface.INTERFACE_BLOCKS.values()) {
            net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                    block,
                    net.minecraft.client.render.RenderLayer.getTranslucent()
            );
        }
    }
}
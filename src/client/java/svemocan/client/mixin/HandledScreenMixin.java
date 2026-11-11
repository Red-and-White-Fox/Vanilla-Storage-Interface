package svemocan.client.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import svemocan.VanillaStorageInterface;
import svemocan.client.VanillaStorageInterfaceClient;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {

    @Shadow @Nullable protected Slot hoveredSlot;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (VanillaStorageInterfaceClient.openHoverKey.matches(keyCode, scanCode)) {

            if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
                ItemStack hoveredItem = this.hoveredSlot.getItem();

                boolean isShift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                boolean invert = VanillaStorageInterface.CONFIG.invertShiftModifiers;

                // Determines which UI to open. If invert config is true, pressing the key WITHOUT shift triggers RawUI instead.
                boolean wantsRawUI = invert ? !isShift : isShift;

                if (hoveredItem.is(Items.ENDER_CHEST)) {
                    ClientPlayNetworking.send(wantsRawUI ? new svemocan.vanilla_storage_interface.network.OpenRawUIPayload(1, -1) : new svemocan.vanilla_storage_interface.network.OpenTerminalPayload(1, -1));

                    // Canceling the callback sets the return value and prevents vanilla's default key press logic from firing underneath our UI
                    cir.setReturnValue(true);
                    return;
                }

                if (hoveredItem.getItem() instanceof net.minecraft.world.item.BlockItem blockItem && blockItem.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {

                    // Capture current mouse coordinates so the custom UI can correctly restore the cursor position or align its elements upon opening
                    net.minecraft.client.Minecraft clientInstance = net.minecraft.client.Minecraft.getInstance();
                    VanillaStorageInterfaceClient.savedMouseX = clientInstance.mouseHandler.xpos();
                    VanillaStorageInterfaceClient.savedMouseY = clientInstance.mouseHandler.ypos();

                    ClientPlayNetworking.send(wantsRawUI ? new svemocan.vanilla_storage_interface.network.OpenRawUIPayload(2, this.hoveredSlot.getContainerSlot()) : new svemocan.vanilla_storage_interface.network.OpenTerminalPayload(2, this.hoveredSlot.getContainerSlot()));
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}
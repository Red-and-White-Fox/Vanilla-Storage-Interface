package svemocan.client.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import svemocan.VanillaStorageInterface;
import svemocan.client.VanillaStorageInterfaceClient;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow @Nullable protected Slot focusedSlot;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (VanillaStorageInterfaceClient.openHoverKey.matchesKey(keyCode, scanCode)) {

            if (this.focusedSlot != null && this.focusedSlot.hasStack()) {
                ItemStack hoveredItem = this.focusedSlot.getStack();

                boolean isShift = net.minecraft.client.gui.screen.Screen.hasShiftDown();
                boolean invert = VanillaStorageInterface.CONFIG.invertShiftModifiers;

                // Determines which UI to open. If invert config is true, pressing the key WITHOUT shift triggers RawUI instead.
                boolean wantsRawUI = invert ? !isShift : isShift;

                if (hoveredItem.isOf(Items.ENDER_CHEST)) {
                    ClientPlayNetworking.send(wantsRawUI ? new svemocan.vanilla_storage_interface.network.OpenRawUIPayload(1, -1) : new svemocan.vanilla_storage_interface.network.OpenTerminalPayload(1, -1));

                    // Canceling the callback sets the return value and prevents vanilla's default key press logic from firing underneath our UI
                    cir.setReturnValue(true);
                    return;
                }

                if (hoveredItem.getItem() instanceof net.minecraft.item.BlockItem blockItem && blockItem.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock) {

                    // Capture current mouse coordinates so the custom UI can correctly restore the cursor position or align its elements upon opening
                    net.minecraft.client.MinecraftClient clientInstance = net.minecraft.client.MinecraftClient.getInstance();
                    VanillaStorageInterfaceClient.savedMouseX = clientInstance.mouse.getX();
                    VanillaStorageInterfaceClient.savedMouseY = clientInstance.mouse.getY();

                    ClientPlayNetworking.send(wantsRawUI ? new svemocan.vanilla_storage_interface.network.OpenRawUIPayload(2, this.focusedSlot.getIndex()) : new svemocan.vanilla_storage_interface.network.OpenTerminalPayload(2, this.focusedSlot.getIndex()));
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}
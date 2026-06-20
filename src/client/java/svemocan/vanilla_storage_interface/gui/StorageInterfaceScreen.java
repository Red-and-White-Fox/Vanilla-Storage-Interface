package svemocan.vanilla_storage_interface.gui;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import svemocan.VanillaStorageInterface;
import svemocan.client.VanillaStorageInterfaceClient;
import svemocan.vanilla_storage_interface.compat.IpnCompatWrapper;
import svemocan.vanilla_storage_interface.config.VanillaStorageConfig;
import svemocan.vanilla_storage_interface.network.StorageActionPayload;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import svemocan.vanilla_storage_interface.network.VirtualItem;

import java.util.ArrayList;
import java.util.List;

public class StorageInterfaceScreen extends HandledScreen<StorageInterfaceScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(VanillaStorageInterface.MOD_ID, "textures/gui/storage_interface.png");
    private static final Identifier TEXTURE_VIRTUAL = Identifier.of(VanillaStorageInterface.MOD_ID, "textures/gui/void_terminal_interface.png");

    private int lastSwipedSlotId = -1;
    private long pauseSortingTime = 0;

    private int emptyShulkerSlots = 0;
    private final int titleColor;
    private final int playerInventoryTitleColor;
    private net.minecraft.util.math.BlockPos physicalPos = null;
    private boolean isVirtual = false;

    private List<VirtualItem> virtualItems = new ArrayList<>();
    private List<VirtualItem> filteredItems = new ArrayList<>();
    private TextFieldWidget searchBox;
    private int scrollOffset = 0;
    private static final int COLUMNS = 9;
    private static final int ROWS = 5;
    public enum SortMode { NONE, DESC, ASC, NAME }
    private SortMode currentSortMode = SortMode.NONE;

    private int ghostX, ghostY;
    private svemocan.vanilla_storage_interface.StorageInterfaceBlockEntity cachedInterfaceEntity = null;

    private long lastShiftClickTime = 0;
    private int lastShiftClickedSlotId = -1;
    private ItemStack lastShiftClickedStack = ItemStack.EMPTY;

    private boolean interceptDropKeyChar = false;

    public StorageInterfaceScreen(StorageInterfaceScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 222;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
        this.titleColor = 0xFFFFFF;
        this.playerInventoryTitleColor = 0xFFFFFF;
    }

    public void updateData(List<VirtualItem> items, int emptySlots, net.minecraft.util.math.BlockPos pos, boolean isVirtual) {
        this.virtualItems = items;
        this.emptyShulkerSlots = emptySlots;
        this.physicalPos = pos;
        this.isVirtual = isVirtual;

        if (!isVirtual && this.client != null && this.client.world != null && this.physicalPos != null) {
            net.minecraft.block.entity.BlockEntity be = this.client.world.getBlockEntity(this.physicalPos);
            if (be instanceof svemocan.vanilla_storage_interface.StorageInterfaceBlockEntity interfaceBe) {
                this.cachedInterfaceEntity = interfaceBe;
            }
        }
        this.refresh();
    }

    private ItemStack getGhostSlotItem() {
        if (this.cachedInterfaceEntity != null) return this.cachedInterfaceEntity.getDisplayItem();
        return ItemStack.EMPTY;
    }

    private boolean isVirtualTerminal() { return this.isVirtual; }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        if (this.pauseSortingTime > 0 && net.minecraft.util.Util.getMeasuringTimeMs() >= this.pauseSortingTime) {
            this.pauseSortingTime = 0;
            this.refresh();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean isVirtual = isVirtualTerminal();

        if (!this.searchBox.isMouseOver(mouseX, mouseY)) {
            this.searchBox.setFocused(false);
            if (this.getFocused() == this.searchBox) this.setFocused(null);
        }

        if (!isVirtual && mouseX >= ghostX && mouseX < ghostX + 18 && mouseY >= ghostY && mouseY < ghostY + 18) {
            if (button == 1) {
                net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                ClientPlayNetworking.send(new StorageActionPayload("CLEAR_DISPLAY", new ItemStack(net.minecraft.item.Items.STRUCTURE_VOID), 1));
                return true;
            } else if (button == 2) {
                net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1.0F));
                ClientPlayNetworking.send(new StorageActionPayload("RESET_DISPLAY", ItemStack.EMPTY, 0));
                return true;
            }

            net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            ItemStack cursorStack = this.getScreenHandler().getCursorStack();
            ItemStack payloadStack = cursorStack.isEmpty() ? ItemStack.EMPTY : cursorStack.copyWithCount(1);
            ClientPlayNetworking.send(new StorageActionPayload("SET_DISPLAY", payloadStack, 1));
            return true;
        }

        if (button == 1 && this.searchBox.isMouseOver(mouseX, mouseY)) {
            this.searchBox.setText("");
            return true;
        }

        int startX = (this.width - this.backgroundWidth) / 2;
        int startY = (this.height - this.backgroundHeight) / 2;

        if (button == 1 && mouseX >= startX + 99 && mouseX <= startX + 149 && mouseY >= startY + 19 && mouseY <= startY + 33) {
            net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.setSortMode(SortMode.NONE);
            return true;
        }

        if (button == IpnCompatWrapper.triggerMouse && IpnCompatWrapper.triggerMouse != -1) {
            executeIpnAction(mouseX, mouseY);
            return true;
        }

        if (this.focusedSlot != null && this.focusedSlot.inventory instanceof PlayerInventory) {
            long time = net.minecraft.util.Util.getMeasuringTimeMs();
            boolean isDoubleClick = (time - lastShiftClickTime < 250) && (lastShiftClickedSlotId == this.focusedSlot.id);

            ItemStack cursorStack = this.getScreenHandler().getCursorStack();
            ItemStack hoveredStack = this.focusedSlot.getStack();

            if (Screen.hasShiftDown()) {
                ItemStack targetSync = cursorStack.isEmpty() ? (isDoubleClick ? lastShiftClickedStack : hoveredStack) : cursorStack;

                if (isDoubleClick && button == 0 && !targetSync.isEmpty()) {
                    ClientPlayNetworking.send(new StorageActionPayload("INSERT_ALL_MATCHING", targetSync, 0));
                    this.lastShiftClickedStack = ItemStack.EMPTY;
                    return true;
                } else if (!hoveredStack.isEmpty()) {
                    this.lastShiftClickTime = time;
                    this.lastShiftClickedSlotId = this.focusedSlot.id;
                    this.lastShiftClickedStack = hoveredStack.copy();

                    ClientPlayNetworking.send(new StorageActionPayload("QUICK_MOVE", ItemStack.EMPTY, this.focusedSlot.id));
                    return true;
                }
            }
        }

        if (button != 0 && button != 1 && button != 2) return super.mouseClicked(mouseX, mouseY, button);

        boolean overVirtualGrid = mouseX >= startX + 8 && mouseX < startX + 8 + (COLUMNS * 18) && mouseY >= startY + 36 && mouseY < startY + 36 + (ROWS * 18);
        ItemStack cursorStack = this.getScreenHandler().getCursorStack();
        ItemStack hoveredItem = getHoveredVirtualItem(mouseX, mouseY);

        if (overVirtualGrid) {
            if (button == 2 && hoveredItem != null && !cursorStack.isEmpty() && ItemStack.areItemsAndComponentsEqual(cursorStack, hoveredItem)) {
                int needed = cursorStack.getMaxCount() - cursorStack.getCount();
                if (needed > 0) {
                    net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    ClientPlayNetworking.send(new StorageActionPayload("EXTRACT", hoveredItem, needed));
                    return true;
                }
            }

            if (button == 0 || button == 1) {
                if (!cursorStack.isEmpty()) {
                    net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    ClientPlayNetworking.send(new StorageActionPayload("INSERT_CURSOR", ItemStack.EMPTY, (button == 0) ? cursorStack.getCount() : 1));
                    return true;
                } else if (hoveredItem != null) {
                    net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    ClientPlayNetworking.send(new StorageActionPayload(Screen.hasShiftDown() ? "SHIFT_EXTRACT" : "EXTRACT", hoveredItem, (button == 0) ? 64 : 1));
                    this.pauseSortingTime = net.minecraft.util.Util.getMeasuringTimeMs() + 1500;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean hasShift = Screen.hasShiftDown();
        boolean hasCtrl = Screen.hasControlDown();

        if (!isVirtualTerminal() && mouseX >= ghostX && mouseX < ghostX + 18 && mouseY >= ghostY && mouseY < ghostY + 18) {
            if (hasShift) {
                net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.ITEM_SPYGLASS_USE, verticalAmount > 0 ? 1.2F : 0.8F));
                ClientPlayNetworking.send(new StorageActionPayload("SCALE_DISPLAY", ItemStack.EMPTY, verticalAmount > 0 ? 1 : -1));
            } else {
                net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1.0F));
                ClientPlayNetworking.send(new StorageActionPayload("ROTATE_DISPLAY", ItemStack.EMPTY, verticalAmount > 0 ? 1 : -1));
            }
            return true;
        }

        int startX = (this.width - this.backgroundWidth) / 2;
        int startY = (this.height - this.backgroundHeight) / 2;
        boolean overVirtualGrid = mouseX >= startX + 8 && mouseX < startX + 8 + (COLUMNS * 18) && mouseY >= startY + 36 && mouseY < startY + 36 + (ROWS * 18);

        if (overVirtualGrid) {
            if (hasShift || hasCtrl) {
                if (verticalAmount < 0) {
                    ItemStack hoveredItem = getHoveredVirtualItem(mouseX, mouseY);
                    if (hoveredItem != null) {
                        net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        ClientPlayNetworking.send(new StorageActionPayload("SHIFT_EXTRACT", hoveredItem, hasShift ? 64 : 1));
                        this.pauseSortingTime = net.minecraft.util.Util.getMeasuringTimeMs() + 1500;
                    }
                }
                return true;
            } else {
                int maxRows = (int) Math.ceil((double) filteredItems.size() / COLUMNS);
                if (verticalAmount < 0 && scrollOffset < maxRows - ROWS) {
                    scrollOffset++;
                    return true;
                } else if (verticalAmount > 0 && scrollOffset > 0) {
                    scrollOffset--;
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    public ItemStack getHoveredVirtualItem(double mouseX, double mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        int startX = x + 8;
        int startY = y + 36;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = (scrollOffset * COLUMNS) + (row * COLUMNS) + col;
                if (index >= filteredItems.size()) return null;

                int slotX = startX + (col * 18);
                int slotY = startY + (row * 18);

                if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                    return filteredItems.get(index).stack();
                }
            }
        }
        return null;
    }

    @Override
    protected void init() {
        super.init();
        IpnCompatWrapper.updateKeys();

        int startX = (this.width - this.backgroundWidth) / 2;
        int startY = (this.height - this.backgroundHeight) / 2;

        this.ghostX = startX + 152;
        this.ghostY = startY + 18;

        this.currentSortMode = SortMode.values()[VanillaStorageInterface.CONFIG.lastSortMode];

        this.searchBox = new TextFieldWidget(this.textRenderer, startX + 10, startY + 23, 81, 14, Text.literal("Search..."));
        this.searchBox.setDrawsBackground(false);
        this.searchBox.setChangedListener(query -> {
            this.pauseSortingTime = 0;
            this.updateSearch(query);
        });
        this.addDrawableChild(this.searchBox);

        this.addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("9"), btn -> {
            this.setSortMode(SortMode.DESC);
            this.setFocused(null);
        }).dimensions(startX + 99, startY + 19, 14, 14).build());

        this.addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("1"), btn -> {
            this.setSortMode(SortMode.ASC);
            this.setFocused(null);
        }).dimensions(startX + 117, startY + 19, 14, 14).build());

        this.addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("A"), btn -> {
            this.setSortMode(SortMode.NAME);
            this.setFocused(null);
        }).dimensions(startX + 135, startY + 19, 14, 14).build());

        updateSearch("");

        if (VanillaStorageInterfaceClient.savedMouseX != -1) {
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(this.client.getWindow().getHandle(), VanillaStorageInterfaceClient.savedMouseX, VanillaStorageInterfaceClient.savedMouseY);
            VanillaStorageInterfaceClient.savedMouseX = -1;
            VanillaStorageInterfaceClient.savedMouseY = -1;
        }
    }

    private void setSortMode(SortMode mode) {
        this.currentSortMode = mode;
        VanillaStorageInterface.CONFIG.lastSortMode = mode.ordinal();
        AutoConfig.getConfigHolder(VanillaStorageConfig.class).save();
        this.refresh();
    }

    public void refresh() {
        if (net.minecraft.util.Util.getMeasuringTimeMs() < this.pauseSortingTime) {
            for (int i = 0; i < this.filteredItems.size(); i++) {
                VirtualItem oldItem = this.filteredItems.get(i);
                int newCount = 0;
                for (VirtualItem vi : this.virtualItems) {
                    if (ItemStack.areItemsAndComponentsEqual(vi.stack(), oldItem.stack())) {
                        newCount = vi.count();
                        break;
                    }
                }
                this.filteredItems.set(i, new VirtualItem(oldItem.stack(), newCount));
            }
            this.filteredItems.removeIf(vi -> vi.count() <= 0);
            return;
        }
        if (this.searchBox != null) this.updateSearch(this.searchBox.getText());
    }

    private String getPrimaryEnchantmentName(ItemStack stack, String defaultName) {
        net.minecraft.component.type.ItemEnchantmentsComponent enchantments = stack.get(net.minecraft.component.DataComponentTypes.STORED_ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            return enchantments.getEnchantments().iterator().next().value().description().getString();
        }
        return defaultName;
    }

    private void updateSearch(String query) {
        String lowerQuery = query.toLowerCase();

        if (VanillaStorageInterface.CONFIG.syncSearchWithREI && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) {
            svemocan.vanilla_storage_interface.rei.REISyncHelper.setSearch(lowerQuery);
        }

        java.util.stream.Stream<VirtualItem> stream = virtualItems.stream()
                .filter(vi -> {
                    String name = vi.stack().getName().getString().toLowerCase();
                    if (name.contains(lowerQuery)) return true;

                    net.minecraft.component.type.ItemEnchantmentsComponent enchantments = vi.stack().get(net.minecraft.component.DataComponentTypes.STORED_ENCHANTMENTS);
                    if (enchantments == null) enchantments = vi.stack().get(net.minecraft.component.DataComponentTypes.ENCHANTMENTS);

                    if (enchantments != null) {
                        for (var entry : enchantments.getEnchantments()) {
                            String enchName = entry.value().description().getString().toLowerCase();
                            if (enchName.contains(lowerQuery)) return true;
                        }
                    }
                    return false;
                });

        switch (currentSortMode) {
            case DESC -> stream = stream.sorted((v1, v2) -> Integer.compare(v2.count(), v1.count()));
            case ASC -> stream = stream.sorted((v1, v2) -> Integer.compare(v1.count(), v2.count()));
            case NAME -> stream = stream.sorted((v1, v2) -> {
                String n1 = v1.stack().getName().getString();
                String n2 = v2.stack().getName().getString();
                if (v1.stack().isOf(net.minecraft.item.Items.ENCHANTED_BOOK) && v2.stack().isOf(net.minecraft.item.Items.ENCHANTED_BOOK)) {
                    n1 = getPrimaryEnchantmentName(v1.stack(), n1);
                    n2 = getPrimaryEnchantmentName(v2.stack(), n2);
                }
                return n1.compareToIgnoreCase(n2);
            });
            case NONE -> {}
        }

        this.filteredItems = new java.util.ArrayList<>(stream.toList());
        if (!query.isEmpty()) this.scrollOffset = 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.searchBox.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);

        ItemStack hoveredItem = getHoveredVirtualItem(mouseX, mouseY);
        if (hoveredItem != null) context.drawItemTooltip(this.textRenderer, hoveredItem, mouseX, mouseY);

        if (!isVirtualTerminal() && mouseX >= ghostX && mouseX < ghostX + 18 && mouseY >= ghostY && mouseY < ghostY + 18) {
            ItemStack displayStack = getGhostSlotItem();
            if (!displayStack.isEmpty() && !displayStack.isOf(net.minecraft.item.Items.STRUCTURE_VOID)) {
                context.drawItemTooltip(this.textRenderer, displayStack, mouseX, mouseY);
            } else {
                context.drawTooltip(this.textRenderer, Text.literal("Display Icon Slot"), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        context.drawTexture(isVirtualTerminal() ? TEXTURE_VIRTUAL : TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight, 176, 222);

        int highlightX = 0;
        switch (currentSortMode) {
            case DESC -> highlightX = x + 99;
            case ASC -> highlightX = x + 117;
            case NAME -> highlightX = x + 135;
            case NONE -> {}
        }
        if (highlightX != 0) {
            context.fill(highlightX - 1, y + 18, highlightX + 15, y + 34, 0x8000FF00);
        }

        if (!isVirtualTerminal()) {
            ItemStack displayItem = getGhostSlotItem();
            if (!displayItem.isEmpty() && !displayItem.isOf(net.minecraft.item.Items.STRUCTURE_VOID)) {
                context.drawItem(displayItem, ghostX, ghostY);
            }
            if (mouseX >= ghostX && mouseX < ghostX + 16 && mouseY >= ghostY && mouseY < ghostY + 16) {
                context.fill(ghostX, ghostY, ghostX + 16, ghostY + 16, 0x80FFFFFF);
            }
        }

        int startX = x + 8;
        int startY = y + 36;
        int index = scrollOffset * COLUMNS;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                if (index >= filteredItems.size()) break;

                var entry = filteredItems.get(index);
                ItemStack stack = entry.stack();
                int count = entry.count();

                int slotX = startX + (col * 18);
                int slotY = startY + (row * 18);

                context.drawItem(stack, slotX, slotY);
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 200);

                String countText = formatCount(count);
                float scale = countText.length() >= 3 ? 0.75f : 1.0f;
                context.getMatrices().scale(scale, scale, 1.0f);

                int textX = (int) ((slotX + 17) / scale) - this.textRenderer.getWidth(countText);
                int textY = (int) ((slotY + 17) / scale) - 8;

                context.drawTextWithShadow(this.textRenderer, countText, textX, textY, 0xFFFFFF);
                context.getMatrices().pop();

                if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                    context.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FFFFFF);
                }
                index++;
            }
        }
    }

    private String formatCount(int count) {
        if (count < 1000) return String.valueOf(count);
        return String.format("%.1fk", count / 1000.0f);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && Screen.hasShiftDown() && this.getScreenHandler().getCursorStack().isEmpty()) {
            if (this.focusedSlot != null && this.focusedSlot.inventory instanceof PlayerInventory) {
                if (this.focusedSlot.id != this.lastSwipedSlotId && this.focusedSlot.hasStack()) {
                    this.lastSwipedSlotId = this.focusedSlot.id;
                    net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.ITEM_BUNDLE_INSERT, 1.0F));
                    ClientPlayNetworking.send(new StorageActionPayload("QUICK_MOVE", ItemStack.EMPTY, this.focusedSlot.id));
                    return true;
                }
            }

            int startX = (this.width - this.backgroundWidth) / 2;
            int startY = (this.height - this.backgroundHeight) / 2;
            boolean overVirtualGrid = mouseX >= startX + 8 && mouseX < startX + 8 + (COLUMNS * 18) && mouseY >= startY + 36 && mouseY < startY + 36 + (ROWS * 18);

            if (overVirtualGrid) {
                int col = (int) (mouseX - (startX + 8)) / 18;
                int row = (int) (mouseY - (startY + 36)) / 18;
                int gridId = (scrollOffset * COLUMNS) + (row * COLUMNS) + col + 1000;

                if (gridId != this.lastSwipedSlotId) {
                    ItemStack hoveredItem = getHoveredVirtualItem(mouseX, mouseY);
                    if (hoveredItem != null) {
                        this.lastSwipedSlotId = gridId;
                        net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.ITEM_BUNDLE_REMOVE_ONE, 1.0F));
                        ClientPlayNetworking.send(new StorageActionPayload("SHIFT_EXTRACT", hoveredItem, 64));
                        this.pauseSortingTime = net.minecraft.util.Util.getMeasuringTimeMs() + 1500;
                        return true;
                    }
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) this.lastSwipedSlotId = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        this.interceptDropKeyChar = false; // Reset tracker

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(keyCode, scanCode, modifiers);

        if (this.searchBox.isFocused()) {
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) return true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_S && Screen.hasControlDown()) {
            boolean fullDefrag = Screen.hasShiftDown();
            ClientPlayNetworking.send(new StorageActionPayload(fullDefrag ? "DEFRAGMENT_ALL" : "DEFRAGMENT_SHULKERS", ItemStack.EMPTY, 0));
            net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (keyCode == IpnCompatWrapper.triggerKey && IpnCompatWrapper.triggerKey != -1) {
            boolean shouldAutofocus = VanillaStorageInterface.CONFIG.autoFocusSearchBar && !VanillaStorageInterface.CONFIG.ipnHasPriorityOverAutoFocus;

            if (!shouldAutofocus) {
                double scaledMouseX = this.client.mouse.getX() * (double) this.client.getWindow().getScaledWidth() / (double) this.client.getWindow().getWidth();
                double scaledMouseY = this.client.mouse.getY() * (double) this.client.getWindow().getScaledHeight() / (double) this.client.getWindow().getHeight();
                executeIpnAction(scaledMouseX, scaledMouseY);
                return true;
            }
        }

        if (this.client.options.dropKey.matchesKey(keyCode, scanCode)) {
            this.interceptDropKeyChar = true; // Tell charTyped to ignore the resulting character

            double scaledMouseX = this.client.mouse.getX() * (double) this.client.getWindow().getScaledWidth() / (double) this.client.getWindow().getWidth();
            double scaledMouseY = this.client.mouse.getY() * (double) this.client.getWindow().getScaledHeight() / (double) this.client.getWindow().getHeight();

            ItemStack hoveredItem = getHoveredVirtualItem(scaledMouseX, scaledMouseY);
            if (hoveredItem != null) {
                boolean dropEntireStack = Screen.hasControlDown();
                ClientPlayNetworking.send(new StorageActionPayload("THROW", hoveredItem, dropEntireStack ? 64 : 1));
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void executeIpnAction(double mouseX, double mouseY) {
        long handle = this.client.getWindow().getHandle();
        boolean hasMatchMod = net.minecraft.client.util.InputUtil.isKeyPressed(handle, IpnCompatWrapper.moveMatchMod) && IpnCompatWrapper.moveMatchMod != -1;
        boolean hasAllMod = net.minecraft.client.util.InputUtil.isKeyPressed(handle, IpnCompatWrapper.moveAllMod) && IpnCompatWrapper.moveAllMod != -1;
        boolean hasHotbarMod = net.minecraft.client.util.InputUtil.isKeyPressed(handle, IpnCompatWrapper.includeHotbarMod) && IpnCompatWrapper.includeHotbarMod != -1;

        int hotbarFlag = hasHotbarMod ? 1 : 0;

        int startX = (this.width - this.backgroundWidth) / 2;
        int startY = (this.height - this.backgroundHeight) / 2;
        boolean isOverVirtual = mouseX >= startX + 8 && mouseX < startX + 8 + (COLUMNS * 18) && mouseY >= startY + 36 && mouseY < startY + 36 + (ROWS * 18);
        boolean isOverPlayer = this.focusedSlot != null && this.focusedSlot.inventory instanceof PlayerInventory;

        boolean isDirectedAtStorage = mouseX >= startX && mouseX < startX + this.backgroundWidth && mouseY >= startY && mouseY < startY + 140;

        if (hasMatchMod) {
            if (isOverPlayer && this.focusedSlot.hasStack()) {
                ClientPlayNetworking.send(new StorageActionPayload("IPN_MOVE_MATCHING_TO_STORAGE", this.focusedSlot.getStack(), hotbarFlag));
            } else if (isOverVirtual) {
                ItemStack hoveredItem = getHoveredVirtualItem(mouseX, mouseY);
                if (hoveredItem != null) {
                    ClientPlayNetworking.send(new StorageActionPayload("IPN_MOVE_MATCHING_TO_PLAYER", hoveredItem, hotbarFlag));
                }
            }
        } else if (hasAllMod) {
            if (isDirectedAtStorage) {
                ClientPlayNetworking.send(new StorageActionPayload("IPN_MOVE_ALL_TO_PLAYER", ItemStack.EMPTY, hotbarFlag));
            } else {
                ClientPlayNetworking.send(new StorageActionPayload("IPN_MOVE_ALL_TO_STORAGE", ItemStack.EMPTY, hotbarFlag));
            }
        } else {
            if (isDirectedAtStorage) {
                ClientPlayNetworking.send(new StorageActionPayload("IPN_REFILL_PLAYER", ItemStack.EMPTY, hotbarFlag));
            } else {
                ClientPlayNetworking.send(new StorageActionPayload("IPN_REFILL_STORAGE", ItemStack.EMPTY, hotbarFlag));
            }
        }
        net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.interceptDropKeyChar) {
            this.interceptDropKeyChar = false;
            if (isHoveringOverInventories()) {
                return false;
            }
        }

        if (!this.searchBox.isFocused() && VanillaStorageInterface.CONFIG.ipnHasPriorityOverAutoFocus) {
            long handle = this.client.getWindow().getHandle();
            if ((IpnCompatWrapper.triggerKey != -1 && net.minecraft.client.util.InputUtil.isKeyPressed(handle, IpnCompatWrapper.triggerKey)) ||
                    (IpnCompatWrapper.moveAllMod != -1 && net.minecraft.client.util.InputUtil.isKeyPressed(handle, IpnCompatWrapper.moveAllMod)) ||
                    (IpnCompatWrapper.moveMatchMod != -1 && net.minecraft.client.util.InputUtil.isKeyPressed(handle, IpnCompatWrapper.moveMatchMod)) ||
                    (IpnCompatWrapper.includeHotbarMod != -1 && net.minecraft.client.util.InputUtil.isKeyPressed(handle, IpnCompatWrapper.includeHotbarMod))) {
                return false;
            }
        }

        if (VanillaStorageInterface.CONFIG.autoFocusSearchBar) {
            if (!this.searchBox.isFocused()) {
                this.searchBox.setFocused(true);
                this.setFocused(this.searchBox);
                this.searchBox.charTyped(chr, modifiers);
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    private boolean isHoveringOverInventories() {
        if (this.client == null) return false;

        double mouseX = this.client.mouse.getX() * (double) this.client.getWindow().getScaledWidth() / (double) this.client.getWindow().getWidth();
        double mouseY = this.client.mouse.getY() * (double) this.client.getWindow().getScaledHeight() / (double) this.client.getWindow().getHeight();

        int startX = (this.width - this.backgroundWidth) / 2;
        int startY = (this.height - this.backgroundHeight) / 2;

        boolean overVirtualGrid = mouseX >= startX + 8 && mouseX < startX + 8 + (COLUMNS * 18) && mouseY >= startY + 36 && mouseY < startY + 36 + (ROWS * 18);

        return overVirtualGrid || this.focusedSlot != null;
    }
}
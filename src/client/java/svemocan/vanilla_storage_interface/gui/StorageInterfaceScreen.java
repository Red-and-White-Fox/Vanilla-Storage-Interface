package svemocan.vanilla_storage_interface.gui;

import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lwjgl.sdl.SDLMouse;
import svemocan.VanillaStorageInterface;
import svemocan.client.VanillaStorageInterfaceClient;
import svemocan.vanilla_storage_interface.StorageInterfaceBlockEntity;
import svemocan.vanilla_storage_interface.compat.IpnCompatWrapper;
import svemocan.vanilla_storage_interface.config.VanillaStorageConfig;
import svemocan.vanilla_storage_interface.network.StorageActionPayload;
import svemocan.vanilla_storage_interface.network.VirtualItem;

import java.util.ArrayList;
import java.util.List;

public class StorageInterfaceScreen extends AbstractContainerScreen<StorageInterfaceScreenHandler> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(VanillaStorageInterface.MOD_ID, "textures/gui/storage_interface.png");
    private static final Identifier TEXTURE_VIRTUAL = Identifier.fromNamespaceAndPath(VanillaStorageInterface.MOD_ID, "textures/gui/void_terminal_interface.png");

    private int lastSwipedSlotId = -1;
    private long pauseSortingTime = 0;

    private int emptyShulkerSlots = 0;
    private final int titleColor;
    private final int playerInventoryTitleColor;
    private BlockPos physicalPos = null;
    private boolean isVirtual = false;

    private List<VirtualItem> virtualItems = new ArrayList<>();
    private List<VirtualItem> filteredItems = new ArrayList<>();
    private EditBox searchBox;
    private int scrollOffset = 0;
    private static final int COLUMNS = 9;
    private static final int ROWS = 5;
    public enum SortMode { NONE, DESC, ASC, NAME }
    private SortMode currentSortMode = SortMode.NONE;

    private int ghostX, ghostY;
    private StorageInterfaceBlockEntity cachedInterfaceEntity = null;

    private long lastShiftClickTime = 0;
    private int lastShiftClickedSlotId = -1;
    private ItemStack lastShiftClickedStack = ItemStack.EMPTY;

    private boolean interceptDropKeyChar = false;

    public StorageInterfaceScreen(StorageInterfaceScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 176, 222);
        this.inventoryLabelY = this.imageHeight - 94;
        this.titleColor = 0xFFFFFF;
        this.playerInventoryTitleColor = 0xFFFFFF;
    }

    public void updateData(List<VirtualItem> items, int emptySlots, BlockPos pos, boolean isVirtual) {
        this.virtualItems = items;
        this.emptyShulkerSlots = emptySlots;
        this.physicalPos = pos;
        this.isVirtual = isVirtual;

        if (!isVirtual && this.minecraft != null && this.minecraft.level != null && this.physicalPos != null) {
            BlockEntity be = this.minecraft.level.getBlockEntity(this.physicalPos);
            if (be instanceof StorageInterfaceBlockEntity interfaceBe) {
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
    public void containerTick() {
        super.containerTick();
        if (this.pauseSortingTime > 0 && Util.getMillis() >= this.pauseSortingTime) {
            this.pauseSortingTime = 0;
            this.refresh();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        boolean isVirtual = isVirtualTerminal();

        if (!this.searchBox.isMouseOver(mouseX, mouseY)) {
            this.searchBox.setFocused(false);
            if (this.getFocused() == this.searchBox) this.setFocused(null);
        }

        if (!isVirtual && mouseX >= ghostX && mouseX < ghostX + 18 && mouseY >= ghostY && mouseY < ghostY + 18) {
            if (button == 1) {
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                ClientPlayNetworking.send(new StorageActionPayload("CLEAR_DISPLAY", new ItemStack(Items.STRUCTURE_VOID), 1));
                return true;
            } else if (button == 2) {
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F));
                ClientPlayNetworking.send(new StorageActionPayload("RESET_DISPLAY", ItemStack.EMPTY, 0));
                return true;
            }

            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            ItemStack cursorStack = this.getMenu().getCarried();
            ItemStack payloadStack = cursorStack.isEmpty() ? ItemStack.EMPTY : cursorStack.copyWithCount(1);
            ClientPlayNetworking.send(new StorageActionPayload("SET_DISPLAY", payloadStack, 1));
            return true;
        }

        if (button == 1 && this.searchBox.isMouseOver(mouseX, mouseY)) {
            this.searchBox.setValue("");
            return true;
        }

        int startX = (this.width - this.imageWidth) / 2;
        int startY = (this.height - this.imageHeight) / 2;

        if (button == 1 && mouseX >= startX + 99 && mouseX <= startX + 149 && mouseY >= startY + 19 && mouseY <= startY + 33) {
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.setSortMode(SortMode.NONE);
            return true;
        }

        if (button == IpnCompatWrapper.triggerMouse && IpnCompatWrapper.triggerMouse != -1) {
            executeIpnAction(mouseX, mouseY);
            return true;
        }

        if (this.hoveredSlot != null && this.hoveredSlot.container instanceof Inventory) {
            this.lastSwipedSlotId = this.hoveredSlot.index; // Record physical slot interaction to prevent double drag triggers

            long time = Util.getMillis();
            boolean isSlotDoubleClick = (time - lastShiftClickTime < 250) && (lastShiftClickedSlotId == this.hoveredSlot.index);

            ItemStack cursorStack = this.getMenu().getCarried();
            ItemStack hoveredStack = this.hoveredSlot.getItem();

            if (event.hasShiftDown()) {
                ItemStack targetSync = cursorStack.isEmpty() ? (isSlotDoubleClick ? lastShiftClickedStack : hoveredStack) : cursorStack;

                if (isSlotDoubleClick && button == 0 && !targetSync.isEmpty()) {
                    ClientPlayNetworking.send(new StorageActionPayload("INSERT_ALL_MATCHING", targetSync, 0));
                    this.lastShiftClickedStack = ItemStack.EMPTY;
                    return true;
                } else if (!hoveredStack.isEmpty()) {
                    this.lastShiftClickTime = time;
                    this.lastShiftClickedSlotId = this.hoveredSlot.index;
                    this.lastShiftClickedStack = hoveredStack.copy();

                    ClientPlayNetworking.send(new StorageActionPayload("QUICK_MOVE", ItemStack.EMPTY, this.hoveredSlot.index));
                    return true;
                }
            }
        }

        if (button != 0 && button != 1 && button != 2) return super.mouseClicked(event, doubleClick);

        boolean overVirtualGrid = mouseX >= startX + 8 && mouseX < startX + 8 + (COLUMNS * 18) && mouseY >= startY + 36 && mouseY < startY + 36 + (ROWS * 18);
        ItemStack cursorStack = this.getMenu().getCarried();
        ItemStack hoveredItem = getHoveredVirtualItem(mouseX, mouseY);

        if (overVirtualGrid) {
            int col = (int) (mouseX - (startX + 8)) / 18;
            int row = (int) (mouseY - (startY + 36)) / 18;
            int gridId = (scrollOffset * COLUMNS) + (row * COLUMNS) + col + 1000;

            if (button == 2 && hoveredItem != null && !cursorStack.isEmpty() && ItemStack.isSameItemSameComponents(cursorStack, hoveredItem)) {
                int needed = cursorStack.getMaxStackSize() - cursorStack.getCount();
                if (needed > 0) {
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    ClientPlayNetworking.send(new StorageActionPayload("EXTRACT", hoveredItem, needed));
                    return true;
                }
            }

            if (button == 0 || button == 1) {
                if (!cursorStack.isEmpty()) {
                    this.lastSwipedSlotId = gridId; // Record virtual slot interaction
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    ClientPlayNetworking.send(new StorageActionPayload("INSERT_CURSOR", ItemStack.EMPTY, (button == 0) ? cursorStack.getCount() : 1));
                    return true;
                } else if (hoveredItem != null) {
                    this.lastSwipedSlotId = gridId; // Record virtual slot interaction
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    ClientPlayNetworking.send(new StorageActionPayload(event.hasShiftDown() ? "SHIFT_EXTRACT" : "EXTRACT", hoveredItem, (button == 0) ? hoveredItem.getMaxStackSize() : 1));
                    this.pauseSortingTime = Util.getMillis() + 1500;
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean hasShift = Minecraft.getInstance().hasShiftDown();
        boolean hasCtrl = Minecraft.getInstance().hasControlDown();

        if (!isVirtualTerminal() && mouseX >= ghostX && mouseX < ghostX + 18 && mouseY >= ghostY && mouseY < ghostY + 18) {
            if (hasShift) {
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.SPYGLASS_USE, verticalAmount > 0 ? 1.2F : 0.8F));
                ClientPlayNetworking.send(new StorageActionPayload("SCALE_DISPLAY", ItemStack.EMPTY, verticalAmount > 0 ? 1 : -1));
            } else {
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1.0F));
                ClientPlayNetworking.send(new StorageActionPayload("ROTATE_DISPLAY", ItemStack.EMPTY, verticalAmount > 0 ? 1 : -1));
            }
            return true;
        }

        int startX = (this.width - this.imageWidth) / 2;
        int startY = (this.height - this.imageHeight) / 2;
        boolean overVirtualGrid = mouseX >= startX + 8 && mouseX < startX + 8 + (COLUMNS * 18) && mouseY >= startY + 36 && mouseY < startY + 36 + (ROWS * 18);

        if (overVirtualGrid) {
            if (hasShift || hasCtrl) {
                if (verticalAmount < 0) {
                    ItemStack hoveredItem = getHoveredVirtualItem(mouseX, mouseY);
                    if (hoveredItem != null) {
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        ClientPlayNetworking.send(new StorageActionPayload("SHIFT_EXTRACT", hoveredItem, hasShift ? hoveredItem.getMaxStackSize() : 1));
                        this.pauseSortingTime = Util.getMillis() + 1500;
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
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
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

        int startX = (this.width - this.imageWidth) / 2;
        int startY = (this.height - this.imageHeight) / 2;

        this.ghostX = startX + 152;
        this.ghostY = startY + 18;

        this.currentSortMode = SortMode.values()[VanillaStorageInterface.CONFIG.lastSortMode];

        this.searchBox = new EditBox(this.font, startX + 10, startY + 23, 81, 14, Component.literal("Search..."));
        this.searchBox.setBordered(false);
        this.searchBox.setResponder(query -> {
            this.pauseSortingTime = 0;
            this.updateSearch(query);
        });
        this.addRenderableWidget(this.searchBox);

        this.addRenderableWidget(Button.builder(Component.literal("9"), btn -> {
            this.setSortMode(SortMode.DESC);
            this.setFocused(null);
        }).bounds(startX + 99, startY + 19, 14, 14).build());

        this.addRenderableWidget(Button.builder(Component.literal("1"), btn -> {
            this.setSortMode(SortMode.ASC);
            this.setFocused(null);
        }).bounds(startX + 117, startY + 19, 14, 14).build());

        this.addRenderableWidget(Button.builder(Component.literal("A"), btn -> {
            this.setSortMode(SortMode.NAME);
            this.setFocused(null);
        }).bounds(startX + 135, startY + 19, 14, 14).build());

        updateSearch("");

        if (VanillaStorageInterfaceClient.savedMouseX != -1) {
            SDLMouse.SDL_WarpMouseInWindow(this.minecraft.getWindow().handle(), (float) VanillaStorageInterfaceClient.savedMouseX, (float) VanillaStorageInterfaceClient.savedMouseY);
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
        if (Util.getMillis() < this.pauseSortingTime) {
            for (int i = 0; i < this.filteredItems.size(); i++) {
                VirtualItem oldItem = this.filteredItems.get(i);
                int newCount = 0;
                for (VirtualItem vi : this.virtualItems) {
                    if (ItemStack.isSameItemSameComponents(vi.stack(), oldItem.stack())) {
                        newCount = vi.count();
                        break;
                    }
                }
                this.filteredItems.set(i, new VirtualItem(oldItem.stack(), newCount));
            }
            this.filteredItems.removeIf(vi -> vi.count() <= 0);

            String currentQuery = this.searchBox != null ? this.searchBox.getValue() : "";
            for (VirtualItem vi : this.virtualItems) {
                boolean exists = false;
                for (VirtualItem existing : this.filteredItems) {
                    if (ItemStack.isSameItemSameComponents(vi.stack(), existing.stack())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists && matchesSearch(vi, currentQuery)) {
                    this.filteredItems.add(vi);
                }
            }
            return;
        }
        if (this.searchBox != null) this.updateSearch(this.searchBox.getValue());
    }

    private String getPrimaryEnchantmentName(ItemStack stack, String defaultName) {
        ItemEnchantments enchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            return enchantments.keySet().iterator().next().value().description().getString();
        }
        return defaultName;
    }

    private boolean matchesSearch(VirtualItem vi, String query) {
        if (query == null || query.isEmpty()) return true;
        String lowerQuery = query.toLowerCase();
        String name = vi.stack().getHoverName().getString().toLowerCase();
        if (name.contains(lowerQuery)) return true;

        ItemEnchantments enchantments = vi.stack().get(DataComponents.STORED_ENCHANTMENTS);
        if (enchantments == null) enchantments = vi.stack().get(DataComponents.ENCHANTMENTS);

        if (enchantments != null) {
            for (var entry : enchantments.keySet()) {
                String enchName = entry.value().description().getString().toLowerCase();
                if (enchName.contains(lowerQuery)) return true;
            }
        }
        return false;
    }

    private void updateSearch(String query) {
        String lowerQuery = query.toLowerCase();

        if (VanillaStorageInterface.CONFIG.syncSearchWithREI && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) {
            svemocan.vanilla_storage_interface.rei.REISyncHelper.setSearch(lowerQuery);
        }

        java.util.stream.Stream<VirtualItem> stream = virtualItems.stream()
                .filter(vi -> matchesSearch(vi, lowerQuery));

        switch (currentSortMode) {
            case DESC -> stream = stream.sorted((v1, v2) -> Integer.compare(v2.count(), v1.count()));
            case ASC -> stream = stream.sorted((v1, v2) -> Integer.compare(v1.count(), v2.count()));
            case NAME -> stream = stream.sorted((v1, v2) -> {
                String n1 = v1.stack().getHoverName().getString();
                String n2 = v2.stack().getHoverName().getString();
                if (v1.stack().is(Items.ENCHANTED_BOOK) && v2.stack().is(Items.ENCHANTED_BOOK)) {
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
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractBackground(extractor, mouseX, mouseY, delta);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        extractor.blit(RenderPipelines.GUI_TEXTURED, isVirtualTerminal() ? TEXTURE_VIRTUAL : TEXTURE, x, y, 0.0f, 0.0f, this.imageWidth, this.imageHeight, 256, 256);

        int highlightX = 0;
        switch (currentSortMode) {
            case DESC -> highlightX = x + 99;
            case ASC -> highlightX = x + 117;
            case NAME -> highlightX = x + 135;
            case NONE -> {}
        }
        if (highlightX != 0) {
            extractor.fill(highlightX - 1, y + 18, highlightX + 15, y + 34, 0x8000FF00);
        }
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractContents(extractor, mouseX, mouseY, delta);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        if (!isVirtualTerminal()) {
            ItemStack displayItem = getGhostSlotItem();
            if (!displayItem.isEmpty() && !displayItem.is(Items.STRUCTURE_VOID)) {
                extractor.fakeItem(displayItem, ghostX, ghostY);
            }
            if (mouseX >= ghostX && mouseX < ghostX + 16 && mouseY >= ghostY && mouseY < ghostY + 16) {
                extractor.fill(ghostX, ghostY, ghostX + 16, ghostY + 16, 0x80FFFFFF);
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

                extractor.fakeItem(stack, slotX, slotY);

                String countText = formatCount(count);
                extractor.itemDecorations(this.font, stack, slotX, slotY, countText);

                if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                    extractor.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FFFFFF);
                }
                index++;
            }
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        super.extractTooltip(extractor, mouseX, mouseY);

        ItemStack hoveredItem = getHoveredVirtualItem(mouseX, mouseY);
        if (hoveredItem != null) {
            extractor.setTooltipForNextFrame(this.font, hoveredItem, mouseX, mouseY);
        }

        if (!isVirtualTerminal() && mouseX >= ghostX && mouseX < ghostX + 18 && mouseY >= ghostY && mouseY < ghostY + 18) {
            ItemStack displayStack = getGhostSlotItem();
            if (!displayStack.isEmpty() && !displayStack.is(Items.STRUCTURE_VOID)) {
                extractor.setTooltipForNextFrame(this.font, displayStack, mouseX, mouseY);
            } else {
                extractor.setTooltipForNextFrame(this.font, Component.literal("Display Icon Slot"), mouseX, mouseY);
            }
        }
    }

    private String formatCount(int count) {
        if (count < 1000) return String.valueOf(count);
        return String.format("%.1fk", count / 1000.0f);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (button == 0 && event.hasShiftDown() && this.getMenu().getCarried().isEmpty()) {
            if (this.hoveredSlot != null && this.hoveredSlot.container instanceof Inventory) {
                if (this.hoveredSlot.index != this.lastSwipedSlotId && this.hoveredSlot.hasItem()) {
                    this.lastSwipedSlotId = this.hoveredSlot.index;
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BUNDLE_INSERT, 1.0F));
                    ClientPlayNetworking.send(new StorageActionPayload("QUICK_MOVE", ItemStack.EMPTY, this.hoveredSlot.index));
                    return true;
                }
            }

            int startX = (this.width - this.imageWidth) / 2;
            int startY = (this.height - this.imageHeight) / 2;
            boolean overVirtualGrid = mouseX >= startX + 8 && mouseX < startX + 8 + (COLUMNS * 18) && mouseY >= startY + 36 && mouseY < startY + 36 + (ROWS * 18);

            if (overVirtualGrid) {
                int col = (int) (mouseX - (startX + 8)) / 18;
                int row = (int) (mouseY - (startY + 36)) / 18;
                int gridId = (scrollOffset * COLUMNS) + (row * COLUMNS) + col + 1000;

                if (gridId != this.lastSwipedSlotId) {
                    ItemStack hoveredItem = getHoveredVirtualItem(mouseX, mouseY);
                    if (hoveredItem != null) {
                        this.lastSwipedSlotId = gridId;
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BUNDLE_REMOVE_ONE, 1.0F));
                        ClientPlayNetworking.send(new StorageActionPayload("SHIFT_EXTRACT", hoveredItem, hoveredItem.getMaxStackSize()));
                        this.pauseSortingTime = Util.getMillis() + 1500;
                        return true;
                    }
                }
            }
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) this.lastSwipedSlotId = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        this.interceptDropKeyChar = false; // Reset tracker

        if (event.isEscape()) return super.keyPressed(event);

        if (this.searchBox.isFocused()) {
            if (this.searchBox.keyPressed(event)) return true;
            if (this.minecraft.options.keyInventory.matches(event)) return true;
            return super.keyPressed(event);
        }

        if (event.key() == InputConstants.KEY_S && event.hasControlDown()) {
            boolean fullDefrag = event.hasShiftDown();
            ClientPlayNetworking.send(new StorageActionPayload(fullDefrag ? "DEFRAGMENT_ALL" : "DEFRAGMENT_SHULKERS", ItemStack.EMPTY, 0));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (event.key() == IpnCompatWrapper.triggerKey && IpnCompatWrapper.triggerKey != -1) {
            boolean shouldAutofocus = VanillaStorageInterface.CONFIG.autoFocusSearchBar && !VanillaStorageInterface.CONFIG.ipnHasPriorityOverAutoFocus;

            if (!shouldAutofocus) {
                double scaledMouseX = this.minecraft.mouseHandler.xpos() * (double) this.minecraft.getWindow().getGuiScaledWidth() / (double) this.minecraft.getWindow().getScreenWidth();
                double scaledMouseY = this.minecraft.mouseHandler.ypos() * (double) this.minecraft.getWindow().getGuiScaledHeight() / (double) this.minecraft.getWindow().getScreenHeight();
                executeIpnAction(scaledMouseX, scaledMouseY);
                return true;
            }
        }

        if (this.minecraft.options.keyDrop.matches(event)) {
            this.interceptDropKeyChar = true; // Tell charTyped to ignore the resulting character

            double scaledMouseX = this.minecraft.mouseHandler.xpos() * (double) this.minecraft.getWindow().getGuiScaledWidth() / (double) this.minecraft.getWindow().getScreenWidth();
            double scaledMouseY = this.minecraft.mouseHandler.ypos() * (double) this.minecraft.getWindow().getGuiScaledHeight() / (double) this.minecraft.getWindow().getScreenHeight();

            ItemStack hoveredItem = getHoveredVirtualItem(scaledMouseX, scaledMouseY);
            if (hoveredItem != null) {
                boolean dropEntireStack = event.hasControlDown();
                ClientPlayNetworking.send(new StorageActionPayload("THROW", hoveredItem, dropEntireStack ? hoveredItem.getMaxStackSize() : 1));
                return true;
            }
        }

        return super.keyPressed(event);
    }

    private void executeIpnAction(double mouseX, double mouseY) {
        boolean hasMatchMod = InputConstants.isKeyDown(IpnCompatWrapper.moveMatchMod) && IpnCompatWrapper.moveMatchMod != -1;
        boolean hasAllMod = InputConstants.isKeyDown(IpnCompatWrapper.moveAllMod) && IpnCompatWrapper.moveAllMod != -1;
        boolean hasHotbarMod = InputConstants.isKeyDown(IpnCompatWrapper.includeHotbarMod) && IpnCompatWrapper.includeHotbarMod != -1;

        int hotbarFlag = hasHotbarMod ? 1 : 0;

        int startX = (this.width - this.imageWidth) / 2;
        int startY = (this.height - this.imageHeight) / 2;
        boolean isOverVirtual = mouseX >= startX + 8 && mouseX < startX + 8 + (COLUMNS * 18) && mouseY >= startY + 36 && mouseY < startY + 36 + (ROWS * 18);
        boolean isOverPlayer = this.hoveredSlot != null && this.hoveredSlot.container instanceof Inventory;

        boolean isDirectedAtStorage = mouseX >= startX && mouseX < startX + this.imageWidth && mouseY >= startY && mouseY < startY + 140;

        if (hasMatchMod) {
            if (isOverPlayer && this.hoveredSlot.hasItem()) {
                ClientPlayNetworking.send(new StorageActionPayload("IPN_MOVE_MATCHING_TO_STORAGE", this.hoveredSlot.getItem(), hotbarFlag));
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
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.interceptDropKeyChar) {
            this.interceptDropKeyChar = false;
            if (isHoveringOverInventories()) {
                return false;
            }
        }

        if (!this.searchBox.isFocused() && VanillaStorageInterface.CONFIG.ipnHasPriorityOverAutoFocus) {
            if ((IpnCompatWrapper.triggerKey != -1 && InputConstants.isKeyDown(IpnCompatWrapper.triggerKey)) ||
                    (IpnCompatWrapper.moveAllMod != -1 && InputConstants.isKeyDown(IpnCompatWrapper.moveAllMod)) ||
                    (IpnCompatWrapper.moveMatchMod != -1 && InputConstants.isKeyDown(IpnCompatWrapper.moveMatchMod)) ||
                    (IpnCompatWrapper.includeHotbarMod != -1 && InputConstants.isKeyDown(IpnCompatWrapper.includeHotbarMod))) {
                return false;
            }
        }

        if (VanillaStorageInterface.CONFIG.autoFocusSearchBar) {
            if (!this.searchBox.isFocused()) {
                this.searchBox.setFocused(true);
                this.setFocused(this.searchBox);
                this.searchBox.charTyped(event);
                return true;
            }
        }
        return super.charTyped(event);
    }

    private boolean isHoveringOverInventories() {
        if (this.minecraft == null) return false;

        double mouseX = this.minecraft.mouseHandler.xpos() * (double) this.minecraft.getWindow().getGuiScaledWidth() / (double) this.minecraft.getWindow().getScreenWidth();
        double mouseY = this.minecraft.mouseHandler.ypos() * (double) this.minecraft.getWindow().getGuiScaledHeight() / (double) this.minecraft.getWindow().getScreenHeight();

        int startX = (this.width - this.imageWidth) / 2;
        int startY = (this.height - this.imageHeight) / 2;

        boolean overVirtualGrid = mouseX >= startX + 8 && mouseX < startX + 8 + (COLUMNS * 18) && mouseY >= startY + 36 && mouseY < startY + 36 + (ROWS * 18);

        return overVirtualGrid || this.hoveredSlot != null;
    }
}

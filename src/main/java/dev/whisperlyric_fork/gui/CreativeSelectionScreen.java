package dev.whisperlyric_fork.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CreativeSelectionScreen extends Screen {
    private final Screen parent;
    private final SlotSelectionScreen.SlotType slotType;
    private final Consumer<SlotSelectionScreen.SlotSelectionResult> callback;
    
    private List<ItemStack> availableItems = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private int itemsPerPage = 36;
    
    private Button btnConfirm;
    private Button btnCancel;
    private Button btnScrollUp;
    private Button btnScrollDown;
    
    private int gridStartX;
    private int gridStartY;
    private int slotSize = 18;
    private int gridCols = 9;
    private int gridRows = 4;
    
    public CreativeSelectionScreen(Screen parent, SlotSelectionScreen.SlotType slotType, Consumer<SlotSelectionScreen.SlotSelectionResult> callback) {
        super(Component.literal("从创造物品栏选择"));
        this.parent = parent;
        this.slotType = slotType;
        this.callback = callback;
    }
    
    @Override
    protected void init() {
        super.init();
        
        loadCreativeItems();
        
        gridStartX = (this.width - gridCols * slotSize) / 2;
        gridStartY = 40;
        
        int centerX = this.width / 2;
        int buttonY = gridStartY + gridRows * slotSize + 10;
        
        btnScrollUp = Button.builder(
            Component.literal("↑"),
            button -> scrollUp()
        ).bounds(gridStartX - 25, gridStartY + 20, 20, 20).build();
        
        btnScrollDown = Button.builder(
            Component.literal("↓"),
            button -> scrollDown()
        ).bounds(gridStartX - 25, gridStartY + 60, 20, 20).build();
        
        btnConfirm = Button.builder(
            Component.literal("确认选择"),
            button -> confirmSelection()
        ).bounds(centerX - 105, buttonY, 100, 20).build();
        
        btnCancel = Button.builder(
            Component.literal("取消"),
            button -> Minecraft.getInstance().setScreen(parent)
        ).bounds(centerX + 5, buttonY, 100, 20).build();
        
        addRenderableWidget(btnScrollUp);
        addRenderableWidget(btnScrollDown);
        addRenderableWidget(btnConfirm);
        addRenderableWidget(btnCancel);
    }
    
    private void loadCreativeItems() {
        availableItems.clear();
        
        for (var item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
            availableItems.add(new ItemStack(item));
        }
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal("从创造物品栏选择"),
            this.width / 2,
            20,
            0xFFFFFF
        );
        
        renderGrid(guiGraphics, mouseX, mouseY);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void renderGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (int i = 0; i < itemsPerPage; i++) {
            int itemIndex = scrollOffset + i;
            if (itemIndex >= availableItems.size()) break;
            
            int row = i / gridCols;
            int col = i % gridCols;
            int x = gridStartX + col * slotSize;
            int y = gridStartY + row * slotSize;
            
            renderItem(guiGraphics, x, y, itemIndex, mouseX, mouseY);
        }
    }
    
    private void renderItem(GuiGraphics guiGraphics, int x, int y, int itemIndex, int mouseX, int mouseY) {
        boolean isSelected = itemIndex == selectedIndex;
        boolean isMouseOver = mouseX >= x && mouseX < x + slotSize &&
                            mouseY >= y && mouseY < y + slotSize;
        
        int bgColor = isSelected ? 0x80FF8000 : (isMouseOver ? 0x80FFFFFF : 0xFF373737);
        guiGraphics.fill(x, y, x + slotSize, y + slotSize, bgColor);
        
        guiGraphics.fill(x - 1, y - 1, x + slotSize + 1, y, 0xFF000000);
        guiGraphics.fill(x - 1, y + slotSize, x + slotSize + 1, y + slotSize + 1, 0xFF000000);
        guiGraphics.fill(x - 1, y, x, y + slotSize, 0xFF000000);
        guiGraphics.fill(x + slotSize, y, x + slotSize + 1, y + slotSize, 0xFF000000);
        
        ItemStack stack = availableItems.get(itemIndex);
        guiGraphics.renderItem(stack, x + 1, y + 1);
        guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, x + 1, y + 1);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int clickedIndex = getItemAtPosition((int)mouseX, (int)mouseY);
            if (clickedIndex >= 0) {
                selectedIndex = clickedIndex;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private int getItemAtPosition(int mouseX, int mouseY) {
        for (int i = 0; i < itemsPerPage; i++) {
            int itemIndex = scrollOffset + i;
            if (itemIndex >= availableItems.size()) break;
            
            int row = i / gridCols;
            int col = i % gridCols;
            int x = gridStartX + col * slotSize;
            int y = gridStartY + row * slotSize;
            
            if (mouseX >= x && mouseX < x + slotSize &&
                mouseY >= y && mouseY < y + slotSize) {
                return itemIndex;
            }
        }
        return -1;
    }
    
    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset -= gridCols;
        }
    }
    
    private void scrollDown() {
        int maxOffset = Math.max(0, availableItems.size() - itemsPerPage);
        if (scrollOffset < maxOffset) {
            scrollOffset += gridCols;
        }
    }
    
    private void confirmSelection() {
        if (selectedIndex >= 0 && selectedIndex < availableItems.size()) {
            ItemStack stack = availableItems.get(selectedIndex);
            callback.accept(new SlotSelectionScreen.SlotSelectionResult(
                SlotSelectionScreen.SlotSelectionResult.SelectionType.ITEM_SELECTED,
                stack.copy(),
                1
            ));
        }
        Minecraft.getInstance().setScreen(parent);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

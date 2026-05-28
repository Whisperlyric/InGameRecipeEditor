package dev.whisperlyric_fork.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class PlayerInventorySelectionScreen extends Screen {
    private final Screen parent;
    private final Consumer<SlotSelectionScreen.SlotSelectionResult> callback;
    
    private Inventory playerInventory;
    private int selectedSlot = -1;
    private Button btnConfirm;
    private Button btnCancel;
    
    private int inventoryStartX;
    private int inventoryStartY;
    private int slotSize = 18;
    
    public PlayerInventorySelectionScreen(Screen parent, Consumer<SlotSelectionScreen.SlotSelectionResult> callback) {
        super(Component.literal("从玩家物品栏选择"));
        this.parent = parent;
        this.callback = callback;
    }
    
    @Override
    protected void init() {
        super.init();
        
        playerInventory = Minecraft.getInstance().player.getInventory();
        
        inventoryStartX = (this.width - 176) / 2;
        inventoryStartY = (this.height - 166) / 2;
        
        int centerX = this.width / 2;
        int buttonY = inventoryStartY + 166 + 10;
        
        btnConfirm = Button.builder(
            Component.literal("确认选择"),
            button -> confirmSelection()
        ).bounds(centerX - 105, buttonY, 100, 20).build();
        
        btnCancel = Button.builder(
            Component.literal("取消"),
            button -> Minecraft.getInstance().setScreen(parent)
        ).bounds(centerX + 5, buttonY, 100, 20).build();
        
        addRenderableWidget(btnConfirm);
        addRenderableWidget(btnCancel);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal("从玩家物品栏选择"),
            this.width / 2,
            inventoryStartY - 20,
            0xFFFFFF
        );
        
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal("点击选择物品槽位"),
            this.width / 2,
            inventoryStartY - 8,
            0xAAAAAA
        );
        
        renderInventory(guiGraphics, mouseX, mouseY);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void renderInventory(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col + 9;
                int x = inventoryStartX + col * slotSize;
                int y = inventoryStartY + row * slotSize;
                
                renderSlot(guiGraphics, x, y, slotIndex, mouseX, mouseY);
            }
        }
        
        for (int col = 0; col < 9; col++) {
            int slotIndex = col;
            int x = inventoryStartX + col * slotSize;
            int y = inventoryStartY + 58;
            
            renderSlot(guiGraphics, x, y, slotIndex, mouseX, mouseY);
        }
    }
    
    private void renderSlot(GuiGraphics guiGraphics, int x, int y, int slotIndex, int mouseX, int mouseY) {
        boolean isSelected = slotIndex == selectedSlot;
        boolean isMouseOver = mouseX >= x && mouseX < x + slotSize &&
                            mouseY >= y && mouseY < y + slotSize;
        
        int bgColor = isSelected ? 0x80FF8000 : (isMouseOver ? 0x80FFFFFF : 0xFF373737);
        guiGraphics.fill(x, y, x + slotSize, y + slotSize, bgColor);
        
        guiGraphics.fill(x - 1, y - 1, x + slotSize + 1, y, 0xFF000000);
        guiGraphics.fill(x - 1, y + slotSize, x + slotSize + 1, y + slotSize + 1, 0xFF000000);
        guiGraphics.fill(x - 1, y, x, y + slotSize, 0xFF000000);
        guiGraphics.fill(x + slotSize, y, x + slotSize + 1, y + slotSize, 0xFF000000);
        
        ItemStack stack = playerInventory.getItem(slotIndex);
        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x + 1, y + 1);
            guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, x + 1, y + 1);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int clickedSlot = getSlotAtPosition((int)mouseX, (int)mouseY);
            if (clickedSlot >= 0) {
                selectedSlot = clickedSlot;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private int getSlotAtPosition(int mouseX, int mouseY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col + 9;
                int x = inventoryStartX + col * slotSize;
                int y = inventoryStartY + row * slotSize;
                
                if (mouseX >= x && mouseX < x + slotSize &&
                    mouseY >= y && mouseY < y + slotSize) {
                    return slotIndex;
                }
            }
        }
        
        for (int col = 0; col < 9; col++) {
            int slotIndex = col;
            int x = inventoryStartX + col * slotSize;
            int y = inventoryStartY + 58;
            
            if (mouseX >= x && mouseX < x + slotSize &&
                mouseY >= y && mouseY < y + slotSize) {
                return slotIndex;
            }
        }
        
        return -1;
    }
    
    private void confirmSelection() {
        if (selectedSlot >= 0) {
            ItemStack stack = playerInventory.getItem(selectedSlot);
            if (!stack.isEmpty()) {
                callback.accept(new SlotSelectionScreen.SlotSelectionResult(
                    SlotSelectionScreen.SlotSelectionResult.SelectionType.ITEM_SELECTED,
                    stack.copy(),
                    stack.getCount()
                ));
            }
        }
        Minecraft.getInstance().setScreen(parent);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

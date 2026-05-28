package dev.whisperlyric_fork.gui;

import com.wzz.registerhelper.gui.TagSelectorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import java.awt.*;
import java.util.function.Consumer;

public class FluidSelectionScreen extends Screen {
    private final Screen parent;
    private final Rectangle slotBounds;
    private final Consumer<FluidSelectionResult> callback;
    
    private Button btnChangeAmount;
    private Button btnSelectFromJEI;
    private Button btnAddTagGroup;
    private Button btnCancel;
    
    public static class FluidSelectionResult {
        public final SelectionType type;
        public final Object value;
        public final long amount;
        
        public enum SelectionType {
            AMOUNT_CHANGED,
            FLUID_SELECTED,
            TAG_GROUP_SELECTED,
            CANCELLED
        }
        
        public FluidSelectionResult(SelectionType type, Object value, long amount) {
            this.type = type;
            this.value = value;
            this.amount = amount;
        }
    }
    
    public FluidSelectionScreen(Screen parent, Rectangle slotBounds, Consumer<FluidSelectionResult> callback) {
        super(Component.literal("流体选择"));
        this.parent = parent;
        this.slotBounds = slotBounds;
        this.callback = callback;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int buttonWidth = 150;
        int buttonHeight = 20;
        int spacing = 25;
        
        int startY = centerY - 60;
        
        btnChangeAmount = Button.builder(
            Component.literal("更改数量"),
            button -> openAmountAdjustment()
        ).bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build();
        
        btnSelectFromJEI = Button.builder(
            Component.literal("从JEI选择"),
            button -> openJEISelection()
        ).bounds(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight).build();
        
        btnAddTagGroup = Button.builder(
            Component.literal("添加标签组"),
            button -> openTagGroupSelection()
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight).build();
        
        btnCancel = Button.builder(
            Component.literal("取消"),
            button -> closeScreen()
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 3 + 10, buttonWidth, buttonHeight).build();
        
        addRenderableWidget(btnChangeAmount);
        addRenderableWidget(btnSelectFromJEI);
        addRenderableWidget(btnAddTagGroup);
        addRenderableWidget(btnCancel);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal("流体选择操作"),
            this.width / 2,
            this.height / 2 - 80,
            0xFFFFFF
        );
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void openAmountAdjustment() {
        Minecraft.getInstance().setScreen(new FluidAmountAdjustmentScreen(this, 1, 10000, 1000, amount -> {
            callback.accept(new FluidSelectionResult(
                FluidSelectionResult.SelectionType.AMOUNT_CHANGED,
                null,
                amount
            ));
        }));
    }
    
    private void openJEISelection() {
        Minecraft.getInstance().setScreen(new JEISelectionScreen(this, SlotSelectionScreen.SlotType.FLUID, result -> {
            if (result != null && result.value instanceof FluidStack fluidStack) {
                callback.accept(new FluidSelectionResult(
                    FluidSelectionResult.SelectionType.FLUID_SELECTED,
                    fluidStack,
                    fluidStack.getAmount()
                ));
            }
        }));
    }
    
    private void openTagGroupSelection() {
        Minecraft.getInstance().setScreen(new TagSelectorScreen(this, tagId -> {
            if (tagId != null) {
                callback.accept(new FluidSelectionResult(
                    FluidSelectionResult.SelectionType.TAG_GROUP_SELECTED,
                    tagId,
                    1
                ));
            }
        }));
    }
    
    private void closeScreen() {
        callback.accept(new FluidSelectionResult(
            FluidSelectionResult.SelectionType.CANCELLED,
            null,
            0
        ));
        Minecraft.getInstance().setScreen(parent);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package dev.whisperlyric_fork.gui;

import com.wzz.registerhelper.gui.TagSelectorScreen;
import com.wzz.registerhelper.gui.recipe.component.ChemicalSlotComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.util.function.Consumer;

public class ChemicalSelectionScreen extends Screen {
    private final Screen parent;
    private final Rectangle slotBounds;
    private final ChemicalSlotComponent.ChemicalType chemicalType;
    private final Consumer<ChemicalSelectionResult> callback;
    
    private Button btnChangeAmount;
    private Button btnSelectFromJEI;
    private Button btnAddTagGroup;
    private Button btnCancel;
    
    public static class ChemicalSelectionResult {
        public final SelectionType type;
        public final Object value;
        public final long amount;
        
        public enum SelectionType {
            AMOUNT_CHANGED,
            CHEMICAL_SELECTED,
            TAG_GROUP_SELECTED,
            CANCELLED
        }
        
        public ChemicalSelectionResult(SelectionType type, Object value, long amount) {
            this.type = type;
            this.value = value;
            this.amount = amount;
        }
    }
    
    public ChemicalSelectionScreen(Screen parent, Rectangle slotBounds, ChemicalSlotComponent.ChemicalType chemicalType, Consumer<ChemicalSelectionResult> callback) {
        super(Component.literal(chemicalType.getDisplayName() + "选择"));
        this.parent = parent;
        this.slotBounds = slotBounds;
        this.chemicalType = chemicalType;
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
            Component.literal(chemicalType.getDisplayName() + "选择操作"),
            this.width / 2,
            this.height / 2 - 80,
            0xFFFFFF
        );
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void openAmountAdjustment() {
        Minecraft.getInstance().setScreen(new ChemicalAmountAdjustmentScreen(this, chemicalType, 1, 10000, 100, amount -> {
            callback.accept(new ChemicalSelectionResult(
                ChemicalSelectionResult.SelectionType.AMOUNT_CHANGED,
                null,
                amount
            ));
        }));
    }
    
    private void openJEISelection() {
        Minecraft.getInstance().setScreen(new JEISelectionScreen(this, convertSlotType(), result -> {
            if (result != null && result.value instanceof String chemicalId) {
                callback.accept(new ChemicalSelectionResult(
                    ChemicalSelectionResult.SelectionType.CHEMICAL_SELECTED,
                    chemicalId,
                    result.amount
                ));
            }
        }));
    }
    
    private void openTagGroupSelection() {
        Minecraft.getInstance().setScreen(new TagSelectorScreen(this, tagId -> {
            if (tagId != null) {
                callback.accept(new ChemicalSelectionResult(
                    ChemicalSelectionResult.SelectionType.TAG_GROUP_SELECTED,
                    tagId,
                    1
                ));
            }
        }));
    }
    
    private SlotSelectionScreen.SlotType convertSlotType() {
        return switch (chemicalType) {
            case GAS -> SlotSelectionScreen.SlotType.GAS;
            case SLURRY -> SlotSelectionScreen.SlotType.SLURRY;
            case PIGMENT -> SlotSelectionScreen.SlotType.PIGMENT;
            case INFUSE_TYPE -> SlotSelectionScreen.SlotType.INFUSE_TYPE;
        };
    }
    
    private void closeScreen() {
        callback.accept(new ChemicalSelectionResult(
            ChemicalSelectionResult.SelectionType.CANCELLED,
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

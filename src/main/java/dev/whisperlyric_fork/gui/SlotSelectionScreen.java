package dev.whisperlyric_fork.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.gui.ItemSelectorScreen;
import com.wzz.registerhelper.gui.TagSelectorScreen;
import com.wzz.registerhelper.gui.CustomTagCreatorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.function.Consumer;

public class SlotSelectionScreen extends Screen {
    private final Screen parent;
    private final Rectangle slotBounds;
    private final SlotType slotType;
    private final Consumer<SlotSelectionResult> callback;
    
    private Button btnChangeAmount;
    private Button btnSelectFromCreative;
    private Button btnSelectFromInventory;
    private Button btnSelectFromJEI;
    private Button btnAddTagGroup;
    private Button btnCancel;
    
    public enum SlotType {
        ITEM,
        FLUID,
        GAS,
        SLURRY,
        PIGMENT,
        INFUSE_TYPE
    }
    
    public static class SlotSelectionResult {
        public final SelectionType type;
        public final Object value;
        public final int amount;
        
        public enum SelectionType {
            AMOUNT_CHANGED,
            ITEM_SELECTED,
            FLUID_SELECTED,
            GAS_SELECTED,
            TAG_GROUP_SELECTED,
            CANCELLED
        }
        
        public SlotSelectionResult(SelectionType type, Object value, int amount) {
            this.type = type;
            this.value = value;
            this.amount = amount;
        }
    }
    
    public SlotSelectionScreen(Screen parent, Rectangle slotBounds, SlotType slotType, Consumer<SlotSelectionResult> callback) {
        super(Component.literal("槽位选择"));
        this.parent = parent;
        this.slotBounds = slotBounds;
        this.slotType = slotType;
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
        
        int startY = centerY - 80;
        
        btnChangeAmount = Button.builder(
            Component.literal("更改数量"),
            button -> openAmountAdjustment()
        ).bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build();
        
        btnSelectFromCreative = Button.builder(
            Component.literal("从创造物品栏选择"),
            button -> openCreativeSelection()
        ).bounds(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight).build();
        
        btnSelectFromInventory = Button.builder(
            Component.literal("从玩家物品栏选择"),
            button -> openInventorySelection()
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight).build();
        
        btnSelectFromJEI = Button.builder(
            Component.literal("从JEI选择"),
            button -> openJEISelection()
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 3, buttonWidth, buttonHeight).build();
        
        btnAddTagGroup = Button.builder(
            Component.literal("添加物品标签组"),
            button -> openTagGroupSelection()
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 4, buttonWidth, buttonHeight).build();
        
        btnCancel = Button.builder(
            Component.literal("取消"),
            button -> closeScreen()
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 5 + 10, buttonWidth, buttonHeight).build();
        
        addRenderableWidget(btnChangeAmount);
        addRenderableWidget(btnSelectFromCreative);
        addRenderableWidget(btnSelectFromInventory);
        addRenderableWidget(btnSelectFromJEI);
        addRenderableWidget(btnAddTagGroup);
        addRenderableWidget(btnCancel);
        
        updateButtonVisibility();
    }
    
    private void updateButtonVisibility() {
        btnSelectFromCreative.visible = slotType == SlotType.ITEM;
        btnAddTagGroup.visible = slotType == SlotType.ITEM;
        btnSelectFromJEI.visible = true;
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal("选择操作"),
            this.width / 2,
            this.height / 2 - 100,
            0xFFFFFF
        );
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void openAmountAdjustment() {
        Minecraft.getInstance().setScreen(new NumberAdjustmentScreen(this, 1, 64, 1, amount -> {
            callback.accept(new SlotSelectionResult(
                SlotSelectionResult.SelectionType.AMOUNT_CHANGED,
                null,
                amount
            ));
        }));
    }
    
    private void openCreativeSelection() {
        Minecraft.getInstance().setScreen(new ItemSelectorScreen(this, stack -> {
            if (stack != null && !stack.isEmpty()) {
                callback.accept(new SlotSelectionResult(
                    SlotSelectionResult.SelectionType.ITEM_SELECTED,
                    stack,
                    stack.getCount()
                ));
            }
        }));
    }
    
    private void openInventorySelection() {
        Minecraft.getInstance().setScreen(new PlayerInventorySelectionScreen(this, result -> {
            if (result != null) {
                callback.accept(result);
            }
        }));
    }
    
    private void openJEISelection() {
        Minecraft.getInstance().setScreen(new JEISelectionScreen(this, slotType, result -> {
            if (result != null) {
                callback.accept(result);
            }
        }));
    }
    
    private void openTagGroupSelection() {
        Minecraft.getInstance().setScreen(new TagSelectorScreen(this, tagId -> {
            if (tagId != null) {
                callback.accept(new SlotSelectionResult(
                    SlotSelectionResult.SelectionType.TAG_GROUP_SELECTED,
                    tagId,
                    1
                ));
            }
        }));
    }
    
    private void closeScreen() {
        callback.accept(new SlotSelectionResult(
            SlotSelectionResult.SelectionType.CANCELLED,
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

package dev.whisperlyric.ingamerecipeeditor.workspace;

import dev.whisperlyric.ingamerecipeeditor.schema.SlotDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 配方槽位文本编辑界面
 * 用于通过文本输入编辑槽位内容（物品ID、流体ID、化学品ID等）
 */
public class RecipeIngredientTextEditScreen extends Screen {
    
    private static final int PANEL_WIDTH = 280;
    private static final int ITEM_PANEL_HEIGHT = 88;
    private static final int AMOUNT_PANEL_HEIGHT = 118;
    private static final int DETAIL_PANEL_HEIGHT = 148;
    
    private final Screen parent;
    private final WorkspaceEditor editor;
    private final WorkspaceSlot slot;
    private final IngredientEditValue initialValue;
    
    private EditBox inputBox;
    private EditBox amountBox;
    private EditBox detailBox;
    private Button confirmButton;
    
    public RecipeIngredientTextEditScreen(Screen parent, WorkspaceEditor editor, WorkspaceSlot slot, IngredientEditValue initialValue) {
        super(Component.translatable("ingamerecipeeditor.screen.ingredient_edit.title"));
        this.parent = parent;
        this.editor = editor;
        this.slot = slot;
        this.initialValue = initialValue;
    }
    
    @Override
    protected void init() {
        int panelHeight = getPanelHeight();
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - panelHeight) / 2;
        
        // 创建输入框
        inputBox = new EditBox(
            this.font,
            left + 12, top + 28,
            PANEL_WIDTH - 24, 20,
            Component.translatable("ingamerecipeeditor.screen.ingredient_edit.input")
        );
        inputBox.setMaxLength(256);
        inputBox.setValue(initialValue.ingredientId());
        inputBox.setResponder(value -> updateConfirmButton());
        this.addRenderableWidget(inputBox);
        
        int buttonY = top + 58;
        
        // 创建数量输入框（如果需要）
        if (hasAmountEdit()) {
            amountBox = new EditBox(
                this.font,
                left + 12, top + 58,
                88, 20,
                Component.translatable("ingamerecipeeditor.screen.ingredient_edit.amount")
            );
            amountBox.setMaxLength(10);
            amountBox.setValue(String.valueOf(Math.max(1, initialValue.amount())));
            amountBox.setResponder(value -> updateConfirmButton());
            this.addRenderableWidget(amountBox);
            
            // 数量标签
            buttonY = top + 88;
            
            // 创建详情输入框（如果需要）
            if (supportsDetailEdit()) {
                detailBox = new EditBox(
                    this.font,
                    left + 12, top + 88,
                    PANEL_WIDTH - 24, 20,
                    getDetailLabel()
                );
                detailBox.setMaxLength(256);
                detailBox.setValue(initialValue.detailId());
                detailBox.setResponder(value -> updateConfirmButton());
                this.addRenderableWidget(detailBox);
                
                buttonY = top + 118;
            }
        }
        
        // 创建确认按钮
        confirmButton = Button.builder(
            Component.translatable("ingamerecipeeditor.screen.ingredient_edit.confirm"),
            button -> onConfirm()
        ).bounds(left + PANEL_WIDTH - 164, buttonY, 72, 20).build();
        this.addRenderableWidget(confirmButton);
        
        // 创建取消按钮
        this.addRenderableWidget(Button.builder(
            Component.translatable("ingamerecipeeditor.screen.ingredient_edit.cancel"),
            button -> onClose()
        ).bounds(left + PANEL_WIDTH - 84, buttonY, 72, 20).build());
        
        this.setInitialFocus(inputBox);
        updateConfirmButton();
    }
    
    /**
     * 更新确认按钮状态
     */
    private void updateConfirmButton() {
        if (confirmButton != null && inputBox != null) {
            boolean valid = isValidInput(inputBox.getValue());
            
            if (hasAmountEdit() && amountBox != null) {
                valid = valid && parseAmount() > 0;
            }
            
            if (supportsDetailEdit() && detailBox != null) {
                valid = valid && isValidDetail(detailBox.getValue(), inputBox.getValue());
            }
            
            confirmButton.active = valid;
        }
    }
    
    /**
     * 确认编辑
     */
    private void onConfirm() {
        String ingredientId = normalizeInput(inputBox.getValue());
        int amount = hasAmountEdit() ? parseAmount() : 1;
        String detailId = detailBox != null ? detailBox.getValue() : "";
        
        // 根据槽位类型设置数据
        setSlotData(ingredientId, amount, detailId);
        
        onClose();
    }
    
    /**
     * 设置槽位数据
     */
    private void setSlotData(String ingredientId, int amount, String detailId) {
        SlotDefinition.IngredientType type = slot.getType();
        
        if (ingredientId == null || ingredientId.isEmpty()) {
            slot.clearData();
            return;
        }
        
        switch (type) {
            case ITEM -> setItemSlotData(ingredientId, amount);
            case FLUID -> setFluidSlotData(ingredientId, amount, detailId);
            case GAS, INFUSE_TYPE, PIGMENT, SLURRY, ANY -> setChemicalSlotData(ingredientId, amount);
            default -> slot.setData(ingredientId);
        }
    }
    
    /**
     * 设置物品槽位数据
     */
    private void setItemSlotData(String ingredientId, int amount) {
        if (ingredientId.startsWith("#")) {
            // 标签输入，暂时存储为字符串
            slot.setData(ingredientId);
        } else {
            ResourceLocation itemId = ResourceLocation.tryParse(ingredientId);
            if (itemId != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemId)) {
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
                slot.setData(new net.minecraft.world.item.ItemStack(item, amount));
            }
        }
    }
    
    /**
     * 设置流体槽位数据
     */
    private void setFluidSlotData(String ingredientId, int amount, String detailId) {
        if (ingredientId.startsWith("#")) {
            slot.setData(ingredientId);
        } else {
            ResourceLocation fluidId = ResourceLocation.tryParse(ingredientId);
            if (fluidId != null && net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(fluidId)) {
                net.minecraft.world.level.material.Fluid fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(fluidId);
                net.minecraftforge.fluids.FluidStack fluidStack = new net.minecraftforge.fluids.FluidStack(fluid, amount);
                slot.setData(fluidStack);
            }
        }
    }
    
    /**
     * 设置化学品槽位数据
     */
    private void setChemicalSlotData(String chemicalId, int amount) {
        // 暂时存储为字符串ID和数量
        slot.setData(new ChemicalData(chemicalId, amount));
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        
        int panelHeight = getPanelHeight();
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - panelHeight) / 2;
        
        // 渲染面板背景
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + panelHeight, 0xF0101010);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + 1, 0xFF707070);
        guiGraphics.fill(left, top + panelHeight - 1, left + PANEL_WIDTH, top + panelHeight, 0xFF707070);
        guiGraphics.fill(left, top, left + 1, top + panelHeight, 0xFF707070);
        guiGraphics.fill(left + PANEL_WIDTH - 1, top, left + PANEL_WIDTH, top + panelHeight, 0xFF707070);
        
        // 渲染标题
        guiGraphics.drawString(this.font, this.title, left + 12, top + 10, 0xFFE0E0E0, false);
        
        // 渲染数量标签
        if (hasAmountEdit()) {
            guiGraphics.drawString(
                this.font,
                Component.translatable("ingamerecipeeditor.screen.ingredient_edit.amount"),
                left + 108, top + 64,
                0xFFE0E0E0, false
            );
            
            if (supportsDetailEdit()) {
                guiGraphics.drawString(
                    this.font,
                    getDetailLabel(),
                    left + 12, top + 112,
                    0xFFE0E0E0, false
                );
            }
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            if (confirmButton != null && confirmButton.active) {
                onConfirm();
            }
            return true;
        }
        
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    // ========== 辅助方法 ==========
    
    private int getPanelHeight() {
        if (supportsDetailEdit()) return DETAIL_PANEL_HEIGHT;
        if (hasAmountEdit()) return AMOUNT_PANEL_HEIGHT;
        return ITEM_PANEL_HEIGHT;
    }
    
    private boolean hasAmountEdit() {
        return initialValue.hasAmount();
    }
    
    private boolean supportsDetailEdit() {
        // 流体槽位可能需要详情（如药水）
        return slot.getType() == SlotDefinition.IngredientType.FLUID;
    }
    
    private Component getDetailLabel() {
        return Component.translatable("ingamerecipeeditor.screen.ingredient_edit.detail");
    }
    
    private int parseAmount() {
        if (amountBox == null) return 1;
        try {
            return Integer.parseInt(amountBox.getValue().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private boolean isValidInput(String input) {
        return normalizeInput(input) != null;
    }
    
    private boolean isValidDetail(String detail, String ingredientId) {
        // 详情可以为空
        if (detail == null || detail.trim().isEmpty()) return true;
        ResourceLocation id = ResourceLocation.tryParse(detail.trim());
        return id != null;
    }
    
    private String normalizeInput(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) return null;
        
        // 标签输入
        if (value.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(value.substring(1));
            return tagId == null ? null : "#" + tagId;
        }
        
        // ID输入
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) return null;
        
        // 根据槽位类型验证ID是否存在
        SlotDefinition.IngredientType type = slot.getType();
        switch (type) {
            case ITEM -> {
                if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) return null;
            }
            case FLUID -> {
                if (!net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(id)) return null;
            }
            // 化学品类型暂时不验证
        }
        
        return id.toString();
    }
    
    /**
     * 成分编辑值
     */
    public record IngredientEditValue(
        SlotDefinition.IngredientType kind,
        String ingredientId,
        int amount,
        String detailId,
        boolean amountEditable
    ) {
        public IngredientEditValue(SlotDefinition.IngredientType kind, String ingredientId, int amount) {
            this(kind, ingredientId, amount, "", kind != SlotDefinition.IngredientType.ITEM);
        }
        
        public boolean hasAmount() {
            return amountEditable;
        }
        
        public boolean hasDetail() {
            return detailId != null && !detailId.isEmpty();
        }
    }
    
    /**
     * 化学品数据（临时存储）
     */
    public record ChemicalData(String id, int amount) {}
}
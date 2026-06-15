package dev.whisperlyric.ingamerecipeeditor.workspace;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.schema.PropertyDefinition;
import dev.whisperlyric.ingamerecipeeditor.schema.RecipeSchema;
import dev.whisperlyric.ingamerecipeeditor.schema.SlotDefinition;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 配方工作区界面屏幕
 * 提供配方可视化编辑界面
 */
public class RecipeWorkspaceScreen extends Screen {
    
    private static final int PADDING = 10;
    private static final int SLOT_SIZE = 18;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    
    private final WorkspaceEditor editor;
    private final Screen parentScreen;
    
    // 界面位置
    private int guiLeft;
    private int guiTop;
    private int guiWidth;
    private int guiHeight;
    
    // 属性编辑框
    private final List<EditBox> propertyEditBoxes = new ArrayList<>();
    
    // 当前选中的槽位
    private WorkspaceSlot selectedSlot;
    
    // 拖拽的物品
    private ItemStack draggedItem;
    
    // 按钮组件
    private Button confirmButton;
    private Button cancelButton;
    private Button clearButton;
    
    // 配方ID（从JEI配方布局获取）
    private final String recipeId;
    
    // JEI配方布局（可选）
    private final IRecipeLayoutDrawable<?> recipeLayout;
    
    // 是否为编辑模式（true=编辑现有配方，false=新建/复制配方）
    private final boolean editMode;
    
    /**
     * 从WorkspaceEditor创建工作区界面
     */
    public RecipeWorkspaceScreen(WorkspaceEditor editor, Screen parentScreen) {
        super(Component.translatable("ingamerecipeeditor.screen.workspace.title"));
        this.editor = editor;
        this.parentScreen = parentScreen;
        this.recipeId = null;
        this.recipeLayout = null;
        this.editMode = false;
    }
    
    /**
     * 从JEI配方布局创建工作区界面
     * @param parentScreen 父界面
     * @param recipeIdOrType 配方ID或配方类型
     * @param recipeLayout JEI配方布局
     * @param editMode 是否为编辑模式
     */
    public RecipeWorkspaceScreen(Screen parentScreen, String recipeIdOrType, IRecipeLayoutDrawable<?> recipeLayout, boolean editMode) {
        super(Component.translatable("ingamerecipeeditor.screen.workspace.title"));
        this.parentScreen = parentScreen;
        this.recipeLayout = recipeLayout;
        this.editMode = editMode;
        
        // 从配方布局获取配方类型并创建编辑器
        IRecipeCategory<?> category = recipeLayout.getRecipeCategory();
        String recipeType = category.getRecipeType().getUid().toString();
        this.editor = new WorkspaceEditor(recipeType);
        
        if (editMode) {
            // 编辑模式：recipeIdOrType是配方ID
            this.recipeId = recipeIdOrType;
            InGameRecipeEditor.LOGGER.info("从JEI配方布局创建编辑工作区: {} (类型: {})", recipeId, recipeType);
        } else {
            // 新建/复制模式：recipeIdOrType可能是配方ID或配方类型
            this.recipeId = recipeIdOrType.contains(":") ? recipeIdOrType : null;
            InGameRecipeEditor.LOGGER.info("从JEI配方布局创建新工作区 (类型: {})", recipeType);
        }
    }
    
    /**
     * 获取配方ID
     */
    public String getRecipeId() {
        return recipeId;
    }
    
    /**
     * 获取JEI配方布局
     */
    public IRecipeLayoutDrawable<?> getRecipeLayout() {
        return recipeLayout;
    }
    
    /**
     * 是否为编辑模式
     */
    public boolean isEditMode() {
        return editMode;
    }
    
    @Override
    protected void init() {
        // 检查配方类型是否已注册
        if (!editor.isSchemaRegistered()) {
            // 显示未注册提示界面
            guiWidth = 300;
            guiHeight = 150;
            guiLeft = (this.width - guiWidth) / 2;
            guiTop = (this.height - guiHeight) / 2;
            
            // 只添加关闭按钮
            cancelButton = Button.builder(
                Component.translatable("ingamerecipeeditor.screen.workspace.cancel"),
                button -> onClose()
            ).bounds(guiLeft + guiWidth / 2 - 40, guiTop + guiHeight - 30, 80, 20).build();
            this.addRenderableWidget(cancelButton);
            return;
        }
        
        // 计算界面尺寸
        RecipeSchema schema = editor.getSchema().orElse(null);
        if (schema != null) {
            guiWidth = Math.max(200, schema.getWidth() + 2 * PADDING);
            guiHeight = Math.max(150, schema.getHeight() + 2 * PADDING + 30); // 底部留空间给按钮
        } else {
            guiWidth = 200;
            guiHeight = 150;
        }
        
        guiLeft = (this.width - guiWidth) / 2;
        guiTop = (this.height - guiHeight) / 2;
        
        // 创建按钮
        int buttonY = guiTop + guiHeight - PADDING - BUTTON_HEIGHT;
        
        confirmButton = Button.builder(
            Component.translatable("ingamerecipeeditor.screen.workspace.confirm"),
            button -> onConfirm()
        ).bounds(guiLeft + PADDING, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(confirmButton);
        
        cancelButton = Button.builder(
            Component.translatable("ingamerecipeeditor.screen.workspace.cancel"),
            button -> onClose()
        ).bounds(guiLeft + guiWidth - PADDING - BUTTON_WIDTH, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(cancelButton);
        
        clearButton = Button.builder(
            Component.translatable("ingamerecipeeditor.screen.workspace.clear"),
            button -> onClear()
        ).bounds(guiLeft + guiWidth / 2 - BUTTON_WIDTH / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(clearButton);
        
        // 创建属性编辑框
        createPropertyEditBoxes();
        
        // 更新确认按钮状态
        updateConfirmButton();
    }
    
    /**
     * 创建属性编辑框
     */
    private void createPropertyEditBoxes() {
        propertyEditBoxes.clear();
        
        RecipeSchema schema = editor.getSchema().orElse(null);
        if (schema == null) return;
        
        List<PropertyDefinition> properties = schema.getProperties();
        
        if (properties.isEmpty()) return;
        
        int propY = guiTop + guiHeight - PADDING - BUTTON_HEIGHT - 25;
        int propX = guiLeft + PADDING;
        
        for (PropertyDefinition prop : properties) {
            EditBox editBox = new EditBox(
                this.font,
                propX, propY,
                60, 18,
                Component.literal(prop.getId())
            );
            
            editBox.setMaxLength(10);
            
            // 设置初始值
            Object defaultValue = prop.getDefaultValue();
            if (defaultValue != null) {
                editBox.setValue(String.valueOf(defaultValue));
            }
            
            editBox.setResponder(value -> {
                try {
                    Object parsedValue = parsePropertyValue(value, prop.getType());
                    editor.setPropertyData(prop.getId(), parsedValue);
                    updateConfirmButton();
                } catch (NumberFormatException e) {
                    // 无效输入，保持原值
                }
            });
            
            this.addRenderableWidget(editBox);
            propertyEditBoxes.add(editBox);
            
            propX += 70;
            if (propX > guiLeft + guiWidth - PADDING - 60) {
                propX = guiLeft + PADDING;
                propY -= 20;
            }
        }
    }
    
    /**
     * 解析属性值
     */
    private Object parsePropertyValue(String value, PropertyDefinition.PropertyType type) {
        return switch (type) {
            case INTEGER -> Integer.parseInt(value);
            case FLOAT -> Float.parseFloat(value);
            case BOOLEAN -> Boolean.parseBoolean(value);
            case STRING -> value;
        };
    }
    
    /**
     * 更新确认按钮状态
     */
    private void updateConfirmButton() {
        if (confirmButton != null) {
            // 检查所有必需槽位是否有数据
            boolean allRequiredFilled = true;
            for (WorkspaceSlot slot : editor.getSlots()) {
                if (slot.isRequired() && slot.isInJson() && !slot.hasData()) {
                    allRequiredFilled = false;
                    break;
                }
            }
            confirmButton.active = allRequiredFilled;
        }
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染背景
        this.renderBackground(guiGraphics);
        
        // 检查配方类型是否已注册
        if (!editor.isSchemaRegistered()) {
            // 渲染未注册提示界面
            renderPanel(guiGraphics);
            
            // 渲染标题
            guiGraphics.drawString(
                this.font,
                Component.translatable("ingamerecipeeditor.screen.workspace.title"),
                guiLeft + PADDING, guiTop + 5,
                0xFFFFFF, false
            );
            
            // 渲染错误消息
            guiGraphics.drawCenteredString(
                this.font,
                editor.getUnregisteredMessage(),
                guiLeft + guiWidth / 2, guiTop + 40,
                0xFF5555
            );
            
            // 渲染配方类型
            guiGraphics.drawCenteredString(
                this.font,
                Component.literal(editor.getRecipeType()),
                guiLeft + guiWidth / 2, guiTop + 60,
                0xAAAAAA
            );
            
            // 渲染按钮
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }
        
        // 渲染主面板
        renderPanel(guiGraphics);
        
        // 渲染标题
        guiGraphics.drawString(
            this.font,
            Component.literal(editor.getDisplayName()),
            guiLeft + PADDING, guiTop + 5,
            0xFFFFFF, false
        );
        
        // 渲染槽位
        renderSlots(guiGraphics, mouseX, mouseY);
        
        // 渲染属性标签
        renderPropertyLabels(guiGraphics);
        
        // 渲染按钮和编辑框
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // 渲染拖拽物品
        if (draggedItem != null && !draggedItem.isEmpty()) {
            guiGraphics.renderItem(draggedItem, mouseX - 8, mouseY - 8);
        }
        
        // 渲染槽位提示
        renderSlotTooltip(guiGraphics, mouseX, mouseY);
    }
    
    /**
     * 渲染主面板
     */
    private void renderPanel(GuiGraphics guiGraphics) {
        // 渲染背景
        guiGraphics.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + guiHeight, 0xF0100010);
        
        // 渲染边框
        guiGraphics.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + 1, 0xFF707070);
        guiGraphics.fill(guiLeft, guiTop + guiHeight - 1, guiLeft + guiWidth, guiTop + guiHeight, 0xFF707070);
        guiGraphics.fill(guiLeft, guiTop, guiLeft + 1, guiTop + guiHeight, 0xFF707070);
        guiGraphics.fill(guiLeft + guiWidth - 1, guiTop, guiLeft + guiWidth, guiTop + guiHeight, 0xFF707070);
    }
    
    /**
     * 渲染槽位
     */
    private void renderSlots(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (WorkspaceSlot slot : editor.getSlots()) {
            renderSlot(guiGraphics, slot, mouseX, mouseY);
        }
    }
    
    /**
     * 渲染单个槽位
     */
    private void renderSlot(GuiGraphics guiGraphics, WorkspaceSlot slot, int mouseX, int mouseY) {
        int x = guiLeft + slot.getX();
        int y = guiTop + slot.getY();
        int width = slot.getWidth();
        int height = slot.getHeight();
        
        // 渲染槽位背景
        int bgColor = slot.isHovered() ? 0xFF555555 : 0xFF8B8B8B;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);
        
        // 渲染槽位边框
        int borderColor = slot.isSelected() ? 0xFFFFFFFF : (slot.isHovered() ? 0xFFAAAAAA : 0xFF373737);
        guiGraphics.fill(x, y, x + width, y + 1, borderColor);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        guiGraphics.fill(x, y, x + 1, y + height, borderColor);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, borderColor);
        
        // 渲染槽位内容
        renderSlotContent(guiGraphics, slot, x, y);
    }
    
    /**
     * 渲染槽位内容
     */
    private void renderSlotContent(GuiGraphics guiGraphics, WorkspaceSlot slot, int x, int y) {
        Object data = slot.getData();
        
        if (data == null) {
            // 空槽位，渲染占位符
            if (slot.isItemSlot()) {
                // 渲染物品槽位占位符
            } else if (slot.isFluidSlot()) {
                // 渲染流体槽位占位符
            } else if (slot.isChemicalSlot()) {
                // 渲染化学品槽位占位符
            }
            return;
        }
        
        // 根据类型渲染内容
        if (data instanceof ItemStack itemStack) {
            guiGraphics.renderItem(itemStack, x + 1, y + 1);
            guiGraphics.renderItemDecorations(this.font, itemStack, x + 1, y + 1);
        } else if (data instanceof FluidStack fluidStack) {
            renderFluidSlot(guiGraphics, fluidStack, x, y, slot.getWidth(), slot.getHeight());
        } else if (slot.isChemicalSlot()) {
            renderChemicalSlot(guiGraphics, data, x, y, slot.getWidth(), slot.getHeight());
        }
    }
    
    /**
     * 渲染流体槽位
     */
    private void renderFluidSlot(GuiGraphics guiGraphics, FluidStack fluidStack, int x, int y, int width, int height) {
        if (fluidStack.isEmpty()) return;
        
        // 获取流体纹理
        try {
            net.minecraft.world.level.material.Fluid fluid = fluidStack.getFluid();
            net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions fluidExtensions = 
                net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid);
            
            if (fluidExtensions != null) {
                ResourceLocation stillTexture = fluidExtensions.getStillTexture(fluidStack);
                if (stillTexture != null) {
                    TextureAtlasSprite sprite = Minecraft.getInstance()
                        .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                        .apply(stillTexture);
                    
                    if (sprite != null) {
                        int color = fluidExtensions.getTintColor(fluidStack);
                        float r = ((color >> 16) & 0xFF) / 255.0f;
                        float g = ((color >> 8) & 0xFF) / 255.0f;
                        float b = (color & 0xFF) / 255.0f;
                        
                        RenderSystem.setShaderColor(r, g, b, 1.0f);
                        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
                        
                        guiGraphics.blit(x + 1, y + 1, 0, width - 2, height - 2, sprite);
                        
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                    }
                }
            }
        } catch (Exception e) {
            // 渲染失败
        }
        
        // 渲染数量
        String amountText = formatAmount(fluidStack.getAmount());
        guiGraphics.drawString(this.font, amountText, x + 2, y + height - 10, 0xFFFFFF, true);
    }
    
    /**
     * 渲染化学品槽位（Mekanism）
     */
    private void renderChemicalSlot(GuiGraphics guiGraphics, Object chemicalData, int x, int y, int width, int height) {
        // 使用ChemicalSlotRenderer渲染
        ChemicalSlotRenderer.renderChemical(guiGraphics, chemicalData, x, y, width, height);
    }
    
    /**
     * 渲染属性标签
     */
    private void renderPropertyLabels(GuiGraphics guiGraphics) {
        RecipeSchema schema = editor.getSchema().orElse(null);
        if (schema == null) return;
        
        List<PropertyDefinition> properties = schema.getProperties();
        
        if (properties.isEmpty()) return;
        
        int propY = guiTop + guiHeight - PADDING - BUTTON_HEIGHT - 40;
        int propX = guiLeft + PADDING;
        
        for (PropertyDefinition prop : properties) {
            guiGraphics.drawString(
                this.font,
                Component.literal(prop.getId() + ":"),
                propX, propY,
                0xAAAAAA, false
            );
            propX += 70;
            if (propX > guiLeft + guiWidth - PADDING - 60) {
                propX = guiLeft + PADDING;
                propY -= 20;
            }
        }
    }
    
    /**
     * 渲染槽位提示
     */
    private void renderSlotTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (WorkspaceSlot slot : editor.getSlots()) {
            if (slot.isHovered()) {
                List<Component> tooltip = new ArrayList<>();
                
                // 添加槽位信息
                tooltip.add(Component.literal(slot.getId()));
                tooltip.add(Component.literal("类型: " + slot.getType().name()));
                tooltip.add(Component.literal("角色: " + slot.getRole().name()));
                
                // 添加数据信息
                Object data = slot.getData();
                if (data != null) {
                    if (data instanceof ItemStack itemStack) {
                        tooltip.add(itemStack.getDisplayName());
                        tooltip.add(Component.literal("数量: " + itemStack.getCount()));
                    } else if (data instanceof FluidStack fluidStack) {
                        tooltip.add(Component.literal(fluidStack.getDisplayName().getString()));
                        tooltip.add(Component.literal("数量: " + fluidStack.getAmount() + " mB"));
                    }
                }
                
                guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                break;
            }
        }
    }
    
    /**
     * 格式化数量显示
     */
    private String formatAmount(int amount) {
        if (amount >= 1000000) {
            return String.format("%.1fB", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        } else {
            return String.valueOf(amount);
        }
    }
    
    // ========== 交互处理 ==========
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检查是否点击了槽位
        for (WorkspaceSlot slot : editor.getSlots()) {
            int slotX = guiLeft + slot.getX();
            int slotY = guiTop + slot.getY();
            
            if (mouseX >= slotX && mouseX < slotX + slot.getWidth() &&
                mouseY >= slotY && mouseY < slotY + slot.getHeight()) {
                
                if (button == 0) { // 左键
                    selectedSlot = slot;
                    slot.onClick((int) mouseX, (int) mouseY, button);
                    
                    // 如果槽位有物品，开始拖拽
                    Object data = slot.getData();
                    if (data instanceof ItemStack itemStack) {
                        draggedItem = itemStack.copy();
                        slot.clearData();
                    }
                    
                    updateConfirmButton();
                    return true;
                } else if (button == 1) { // 右键
                    // 清除槽位
                    slot.clearData();
                    updateConfirmButton();
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggedItem != null) {
            // 检查是否释放在槽位上
            for (WorkspaceSlot slot : editor.getSlots()) {
                int slotX = guiLeft + slot.getX();
                int slotY = guiTop + slot.getY();
                
                if (mouseX >= slotX && mouseX < slotX + slot.getWidth() &&
                    mouseY >= slotY && mouseY < slotY + slot.getHeight()) {
                    
                    if (slot.isItemSlot() && !slot.isRenderOnly()) {
                        slot.setItemData(draggedItem.copy());
                        updateConfirmButton();
                    }
                    break;
                }
            }
            
            draggedItem = null;
            selectedSlot = null;
            return true;
        }
        
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggedItem != null) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        // 更新槽位悬停状态
        for (WorkspaceSlot slot : editor.getSlots()) {
            int slotX = guiLeft + slot.getX();
            int slotY = guiTop + slot.getY();
            
            boolean hovered = mouseX >= slotX && mouseX < slotX + slot.getWidth() &&
                              mouseY >= slotY && mouseY < slotY + slot.getHeight();
            
            slot.onMouseMove((int) mouseX, (int) mouseY);
        }
    }
    
    // ========== 按钮事件处理 ==========
    
    private void onConfirm() {
        try {
            // 提交草稿
            Optional<Map<String, Object>> data = Optional.of(editor.collectData());
            
            if (data.isPresent()) {
                InGameRecipeEditor.LOGGER.info("配方数据已收集");
                // TODO: 发送到服务器或保存到文件
                onClose();
            }
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.error("提交配方失败: {}", e.getMessage());
        }
    }
    
    private void onClear() {
        editor.clearAllData();
        updateConfirmButton();
    }
    
    @Override
    public void onClose() {
        if (parentScreen != null) {
            Minecraft.getInstance().setScreen(parentScreen);
        } else {
            super.onClose();
        }
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        
        if (keyCode == 257 || keyCode == 335) { // Enter
            if (confirmButton.active) {
                onConfirm();
            }
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
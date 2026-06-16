package dev.whisperlyric.ingamerecipeeditor.workspace;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.schema.RecipeSchema;
import dev.whisperlyric.ingamerecipeeditor.schema.SchemaRegistry;
import dev.whisperlyric.ingamerecipeeditor.util.JeiRecipeHelper;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.gui.textures.Textures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方工作区界面 - 用于编辑配方
 * 使用JEI的界面类型显示配方布局，界面更大更清晰
 */
public class RecipeWorkspaceScreen extends Screen {
    private static final int PADDING = 15;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_WIDTH = 90;

    private final Screen parent;
    private final String recipeId;
    private final IRecipeLayoutDrawable<?> recipeLayout;
    private final Object recipe;
    private final List<IRecipeSlotView> slots;
    private final boolean editMode;
    
    private int layoutX;
    private int layoutY;
    private int panelWidth;
    private int panelHeight;
    
    private Button submitButton;
    private Button cancelButton;
    private Button clearButton;

    /**
     * 创建工作区界面
     * @param parent 父界面
     * @param recipeIdOrType 配方ID或配方类型
     * @param recipeLayout JEI配方布局
     * @param editMode 是否为编辑模式
     */
    public RecipeWorkspaceScreen(Screen parent, String recipeIdOrType, IRecipeLayoutDrawable<?> recipeLayout, boolean editMode) {
        super(Component.translatable("ingamerecipeeditor.screen.workspace.title"));
        this.parent = parent;
        this.recipeLayout = recipeLayout;
        this.recipe = recipeLayout.getRecipe();
        this.slots = recipeLayout.getRecipeSlotsView().getSlotViews();
        this.editMode = editMode;
        
        // 使用JeiRecipeHelper获取正确的配方ID
        this.recipeId = JeiRecipeHelper.getRecipeId(recipeLayout);
        
        // 初始化编辑状态
        if (recipeId != null && !recipeId.isEmpty()) {
            RecipeEditManager.startEdit(recipeId, recipeLayout);
        }
        
        InGameRecipeEditor.LOGGER.info("创建工作区界面: {} (编辑模式: {})", recipeId, editMode);
    }

    @Override
    protected void init() {
        // 计算配方布局位置（居中显示）
        @SuppressWarnings("removal")
        Rect2i layoutRect = recipeLayout.getRect();
        int layoutWidth = layoutRect.getWidth();
        int layoutHeight = layoutRect.getHeight();
        
        // 添加额外的空间用于按钮
        int totalHeight = layoutHeight + PADDING * 2 + BUTTON_HEIGHT + PADDING;
        
        // 配方布局居中
        this.layoutX = (this.width - layoutWidth) / 2;
        this.layoutY = (this.height - totalHeight) / 2 + PADDING;
        
        // 设置配方布局的位置（这样drawRecipe内部会使用正确的位置）
        recipeLayout.setPosition(this.layoutX, this.layoutY);
        
        // 计算面板尺寸
        this.panelWidth = layoutWidth + PADDING * 2;
        this.panelHeight = layoutHeight + PADDING * 2;
        
        // 创建按钮（底部居中）
        int buttonY = layoutY + layoutHeight + PADDING;
        int totalButtonWidth = BUTTON_WIDTH * 3 + PADDING * 2;
        int buttonStartX = (this.width - totalButtonWidth) / 2;
        
        this.submitButton = Button.builder(
            Component.translatable("ingamerecipeeditor.screen.workspace.submit"),
            button -> submit()
        ).bounds(buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.submitButton);
        
        this.cancelButton = Button.builder(
            Component.translatable("ingamerecipeeditor.screen.workspace.cancel"),
            button -> cancel()
        ).bounds(buttonStartX + BUTTON_WIDTH + PADDING, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.cancelButton);
        
        this.clearButton = Button.builder(
            Component.translatable("ingamerecipeeditor.screen.workspace.clear"),
            button -> clear()
        ).bounds(buttonStartX + BUTTON_WIDTH * 2 + PADDING * 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.clearButton);
        
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (submitButton != null && recipeId != null) {
            this.submitButton.active = RecipeEditManager.hasDraft(recipeId);
        }
    }

    private void submit() {
        if (recipeId != null) {
            RecipeEditManager.submit(recipeId);
        }
        close();
    }

    private void cancel() {
        if (recipeId != null) {
            RecipeEditManager.clear(recipeId);
        }
        close();
    }

    private void clear() {
        if (recipeId != null) {
            RecipeEditManager.clear(recipeId);
            RecipeEditManager.startEdit(recipeId, recipeLayout);
        }
        updateButtonStates();
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        cancel();
    }

    @Override
    public void tick() {
        super.tick();
        // 应用草稿到布局
        if (recipeId != null) {
            RecipeEditManager.applyDraftToLayout(recipeLayout);
        }
        updateButtonStates();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染背景
        this.renderBackground(guiGraphics);
        
        // 计算面板位置
        @SuppressWarnings("removal")
        Rect2i layoutRect = recipeLayout.getRect();
        int layoutWidth = layoutRect.getWidth();
        int layoutHeight = layoutRect.getHeight();
        int panelX = layoutX - PADDING;
        int panelY = layoutY - PADDING;
        int contentPanelWidth = layoutWidth + PADDING * 2;
        int contentPanelHeight = layoutHeight + PADDING * 2;
        
        // 渲染配方布局背景面板
        Textures textures = Internal.getTextures();
        DrawableNineSliceTexture backgroundTexture = textures.getRecipeBackground();
        backgroundTexture.draw(guiGraphics, panelX, panelY, contentPanelWidth, contentPanelHeight);
        
        // 渲染配方布局（包括槽位内容物）
        renderRecipeLayout(guiGraphics, mouseX, mouseY, partialTick);
        
        // 渲染标题区域（在面板外顶部）
        int headerY = panelY - this.font.lineHeight * 2 - 8;
        
        // 第一行：配方编辑工作区 + 编码文件状态
        Component titleText = Component.translatable("ingamerecipeeditor.screen.workspace.title");
        String encodingStatus = getEncodingFileStatusText();
        String headerLine = titleText.getString() + " " + encodingStatus;
        int headerLineWidth = this.font.width(headerLine);
        guiGraphics.drawString(
            this.font,
            headerLine,
            (this.width - headerLineWidth) / 2,
            headerY,
            0xFFE0E0E0,
            false
        );
        
        // 第二行：配方ID
        if (recipeId != null && !recipeId.isEmpty()) {
            Component recipeIdText = Component.literal(recipeId);
            guiGraphics.drawString(
                this.font,
                recipeIdText,
                (this.width - this.font.width(recipeIdText)) / 2,
                headerY + this.font.lineHeight + 2,
                0xFFA0A0A0,
                false
            );
        }
        
        // 渲染按钮和子组件
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // 渲染槽位高亮和tooltip
        renderSlotHighlight(guiGraphics, mouseX, mouseY);
    }

    /**
     * 获取编码文件状态文本
     */
    private String getEncodingFileStatusText() {
        String recipeType = JeiRecipeHelper.getRecipeType(recipeLayout);
        RecipeSchema schema = SchemaRegistry.getSchema(recipeType).orElse(null);
        
        if (schema != null) {
            if (schema.useEncodingFile()) {
                return "\u00a7a[编码文件]"; // 绿色
            } else {
                return "\u00a7e[备用方案]"; // 黄色
            }
        } else {
            return "\u00a7c[无编码]"; // 红色
        }
    }

    /**
     * 渲染配方布局
     * 注意：drawRecipe内部会使用setPosition设置的位置进行translate，
     * 所以这里不需要额外的poseStack.translate
     */
    private void renderRecipeLayout(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 使用recipeLayout.drawRecipe渲染完整的配方布局（包括槽位内容物）
        // drawRecipe内部会使用area的位置进行translate
        recipeLayout.drawRecipe(guiGraphics, mouseX, mouseY);
        
        // 不调用drawOverlays，避免JEI的tooltip与我们自定义的tooltip重叠
        // recipeLayout.drawOverlays(guiGraphics, mouseX, mouseY);
        
        // 渲染编辑的槽位内容（叠加渲染化学物质）
        renderEditedSlots(guiGraphics);
    }

    /**
     * 渲染编辑的槽位内容
     * 注意：slot.getRect()返回的是相对于配方布局的相对位置，
     * 需要加上layoutX/layoutY来得到绝对屏幕位置
     */
    private void renderEditedSlots(GuiGraphics guiGraphics) {
        // 获取配方布局的当前位置（setPosition后可能已更新）
        @SuppressWarnings("removal")
        Rect2i layoutRect = recipeLayout.getRect();
        int currentLayoutX = layoutRect.getX();
        int currentLayoutY = layoutRect.getY();
        
        for (IRecipeSlotView slotView : slots) {
            if (!(slotView instanceof IRecipeSlotDrawable slot)) {
                continue;
            }
            if (slot.getRole() != RecipeIngredientRole.INPUT && 
                slot.getRole() != RecipeIngredientRole.OUTPUT) {
                continue;
            }

            if (recipeId == null) continue;

            RecipeEditManager.IngredientEditValue editValue = RecipeEditManager.getSlotEditValue(
                recipeId, recipe, slots, slot
            ).orElse(null);

            if (editValue == null) {
                continue;
            }

            @SuppressWarnings("removal")
            Rect2i slotRect = slot.getRect();
            // slot.getRect()返回相对位置，加上配方布局的绝对位置
            int slotX = currentLayoutX + slotRect.getX();
            int slotY = currentLayoutY + slotRect.getY();
            int width = slotRect.getWidth();
            int height = slotRect.getHeight();

            // 根据类型渲染化学物质
            if (editValue.kind().isChemical()) {
                String chemicalId = editValue.ingredientId();
                long amount = editValue.amount();
                long capacity = getSlotCapacity(slot, editValue.kind());

                if (chemicalId != null && !chemicalId.isEmpty() && amount > 0) {
                    ChemicalSlotRenderer.renderChemical(guiGraphics, chemicalId, amount, capacity, slotX, slotY, width, height);
                }
            }
        }
    }

    /**
     * 获取槽位容量
     */
    private long getSlotCapacity(IRecipeSlotDrawable slot, RecipeEditManager.IngredientKind kind) {
        try {
            var ingredients = slot.getAllIngredients().toList();
            for (var ingredient : ingredients) {
                if (ingredient != null) {
                    Object value = ingredient.getIngredient();
                    if (RecipeEditManager.isChemicalStack(value)) {
                        return RecipeEditManager.chemicalStackAmount(value);
                    }
                }
            }
        } catch (Exception ignored) {}
        
        return 1000;
    }

    /**
     * 渲染槽位高亮
     */
    private void renderSlotHighlight(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 获取配方布局的当前位置
        @SuppressWarnings("removal")
        Rect2i layoutRect = recipeLayout.getRect();
        int currentLayoutX = layoutRect.getX();
        int currentLayoutY = layoutRect.getY();
        
        for (IRecipeSlotView slotView : slots) {
            if (!(slotView instanceof IRecipeSlotDrawable slot)) {
                continue;
            }
            if (slot.getRole() != RecipeIngredientRole.INPUT && 
                slot.getRole() != RecipeIngredientRole.OUTPUT) {
                continue;
            }
            
            @SuppressWarnings("removal")
            Rect2i slotRect = slot.getRect();
            // slot.getRect()返回相对位置，加上配方布局的绝对位置
            int slotX = currentLayoutX + slotRect.getX();
            int slotY = currentLayoutY + slotRect.getY();
            
            if (mouseX >= slotX && mouseX < slotX + slotRect.getWidth() &&
                mouseY >= slotY && mouseY < slotY + slotRect.getHeight()) {
                
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
                );
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.5F);
                
                guiGraphics.fill(
                    slotX, slotY,
                    slotX + slotRect.getWidth(), slotY + slotRect.getHeight(),
                    0x40FFFFFF
                );
                
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.disableBlend();
                
                renderSlotTooltip(guiGraphics, slot, mouseX, mouseY);
                break;
            }
        }
    }

    /**
     * 渲染槽位tooltip
     */
    private void renderSlotTooltip(GuiGraphics guiGraphics, IRecipeSlotDrawable slot, int mouseX, int mouseY) {
        List<Component> tooltipLines = new ArrayList<>();
        
        // 先尝试获取槽位实际的物品/流体tooltip
        try {
            var allIngredients = slot.getAllIngredients().toList();
            for (var ingredient : allIngredients) {
                if (ingredient != null) {
                    Object value = ingredient.getIngredient();
                    if (value instanceof ItemStack stack) {
                        tooltipLines.addAll(getTooltipFromItem(stack));
                    } else if (value instanceof net.minecraftforge.fluids.FluidStack stack) {
                        tooltipLines.add(Component.literal(stack.getDisplayName().getString()));
                        tooltipLines.add(Component.literal(stack.getFluid().builtInRegistryHolder().key().location().toString())
                            .withStyle(s -> s.withColor(0x808080)));
                    } else if (RecipeEditManager.isChemicalStack(value)) {
                        String chemicalId = RecipeEditManager.getChemicalId(value);
                        long amount = RecipeEditManager.chemicalStackAmount(value);
                        tooltipLines.addAll(ChemicalSlotRenderer.getChemicalTooltip(chemicalId, amount, amount));
                    }
                }
            }
        } catch (Exception ignored) {}
        
        // 如果没有从槽位获取到信息，尝试从编辑值获取
        if (tooltipLines.isEmpty() && recipeId != null) {
            RecipeEditManager.IngredientEditValue editValue = RecipeEditManager.getSlotEditValue(
                recipeId, recipe, slots, slot
            ).orElse(null);
            
            if (editValue != null) {
                tooltipLines.add(Component.literal(editValue.ingredientId()));
                if (editValue.hasAmount()) {
                    tooltipLines.add(Component.translatable(
                        "ingamerecipeeditor.tooltip.workspace.amount",
                        editValue.amount()
                    ));
                }
            }
        }
        
        if (!tooltipLines.isEmpty()) {
            guiGraphics.renderComponentTooltip(this.font, tooltipLines, mouseX, mouseY);
        }
    }
    
    /**
     * 从ItemStack获取tooltip
     */
    private List<Component> getTooltipFromItem(ItemStack stack) {
        try {
            Minecraft mc = Minecraft.getInstance();
            return stack.getTooltipLines(mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);
        } catch (Exception e) {
            List<Component> lines = new ArrayList<>();
            lines.add(stack.getHoverName());
            return lines;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleSlotClick(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 处理槽位点击
     */
    private boolean handleSlotClick(double mouseX, double mouseY, int mouseButton) {
        if (recipeId == null) return false;
        
        // 获取配方布局的当前位置
        @SuppressWarnings("removal")
        Rect2i layoutRect = recipeLayout.getRect();
        int currentLayoutX = layoutRect.getX();
        int currentLayoutY = layoutRect.getY();
        
        for (IRecipeSlotView slotView : slots) {
            if (!(slotView instanceof IRecipeSlotDrawable slot)) {
                continue;
            }
            if (slot.getRole() != RecipeIngredientRole.INPUT && 
                slot.getRole() != RecipeIngredientRole.OUTPUT) {
                continue;
            }
            
            @SuppressWarnings("removal")
            Rect2i slotRect = slot.getRect();
            // slot.getRect()返回相对位置，加上配方布局的绝对位置
            int slotX = currentLayoutX + slotRect.getX();
            int slotY = currentLayoutY + slotRect.getY();
            
            if (mouseX >= slotX && mouseX < slotX + slotRect.getWidth() &&
                mouseY >= slotY && mouseY < slotY + slotRect.getHeight()) {
                
                if (mouseButton == 0) {
                    // 左键点击：打开编辑界面
                    RecipeEditManager.IngredientEditValue initialValue = RecipeEditManager.getSlotEditValue(
                        recipeId, recipe, slots, slot
                    ).orElseGet(() -> new RecipeEditManager.IngredientEditValue(
                        RecipeEditManager.getSlotIngredientKind(recipeId, recipe, slots, slot),
                        "",
                        1
                    ));
                    Minecraft.getInstance().setScreen(new RecipeIngredientTextEditScreen(
                        this,
                        recipeId,
                        slots,
                        slot,
                        initialValue
                    ));
                    return true;
                } else if (mouseButton == 1) {
                    // 右键点击：清除槽位
                    if (!hasCarriedItem()) {
                        RecipeEditManager.clearSlot(recipeId, slots, slot);
                        return true;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasCarriedItem() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        return !minecraft.player.containerMenu.getCarried().isEmpty();
    }

    /**
     * 处理拖拽物品到槽位
     */
    public boolean handleDraggedIngredient(Object ingredient, double mouseX, double mouseY) {
        if (recipeId == null) return false;
        
        // 获取配方布局的当前位置
        @SuppressWarnings("removal")
        Rect2i layoutRect = recipeLayout.getRect();
        int currentLayoutX = layoutRect.getX();
        int currentLayoutY = layoutRect.getY();
        
        for (IRecipeSlotView slotView : slots) {
            if (!(slotView instanceof IRecipeSlotDrawable slot)) {
                continue;
            }
            if (slot.getRole() != RecipeIngredientRole.INPUT && 
                slot.getRole() != RecipeIngredientRole.OUTPUT) {
                continue;
            }
            
            @SuppressWarnings("removal")
            Rect2i slotRect = slot.getRect();
            // slot.getRect()返回相对位置，加上配方布局的绝对位置
            int slotX = currentLayoutX + slotRect.getX();
            int slotY = currentLayoutY + slotRect.getY();
            
            if (mouseX >= slotX && mouseX < slotX + slotRect.getWidth() &&
                mouseY >= slotY && mouseY < slotY + slotRect.getHeight()) {
                
                if (ingredient instanceof ItemStack stack) {
                    RecipeEditManager.replaceSlot(recipeId, slots, slot, stack);
                    return true;
                } else if (ingredient instanceof net.minecraftforge.fluids.FluidStack stack) {
                    RecipeEditManager.replaceSlot(recipeId, slots, slot, stack);
                    return true;
                } else if (RecipeEditManager.isChemicalStack(ingredient)) {
                    RecipeEditManager.replaceResourceSlot(recipeId, slots, slot, ingredient);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 获取槽位的绝对位置
     */
    @SuppressWarnings("removal")
    public Rect2i getSlotAbsoluteRect(IRecipeSlotDrawable slot) {
        Rect2i layoutRect = recipeLayout.getRect();
        Rect2i slotRect = slot.getRect();
        return new Rect2i(
            layoutRect.getX() + slotRect.getX(),
            layoutRect.getY() + slotRect.getY(),
            slotRect.getWidth(),
            slotRect.getHeight()
        );
    }

    public String getRecipeId() {
        return recipeId;
    }

    public List<IRecipeSlotView> getSlots() {
        return slots;
    }

    public IRecipeLayoutDrawable<?> getRecipeLayout() {
        return recipeLayout;
    }

    public int getLayoutX() {
        return layoutX;
    }

    public int getLayoutY() {
        return layoutY;
    }

    public int getPanelWidth() {
        return panelWidth;
    }

    public int getPanelHeight() {
        return panelHeight;
    }
}
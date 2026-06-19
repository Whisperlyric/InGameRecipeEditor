package dev.whisperlyric.ingamerecipeeditor.workspace;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.network.NetworkHandler;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方工作区界面 - 用于编辑配方
 * 使用JEI的界面类型显示配方布局，界面更大更清晰
 */
public class RecipeWorkspaceScreen extends Screen {
    private static final int PADDING = 8;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_WIDTH = 44;

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
    // 编辑相关按钮
    private Button editButton; // 打开文本编辑界面
    private Button tagSelectButton;
    private Button objectSelectButton;

    // 当前选中的槽位
    private IRecipeSlotDrawable selectedSlot;

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
            // 获取配方类型
            String recipeType = JeiRecipeHelper.getRecipeType(recipeLayout);
            
            // 加载配方JSON（与JEIRecipeManager参考项目一致）
            // 优先从ResourceManager加载原始JSON文件
            JsonObject recipeJson = null;
            if (recipe instanceof Recipe<?> mcRecipe) {
                recipeJson = JeiRecipeHelper.loadRecipeJson(recipeId, recipeType).orElse(null);
            }
            
            // 存储配方信息到RecipeWorkspaceManager
            if (recipeJson != null) {
                RecipeWorkspaceManager.getInstance().editExistingRecipe(recipeId, recipeJson);
            } else if (recipeType != null) {
                // 对于非Recipe<?>类型的配方（如Mekanism化学品配方），使用recipeType创建草稿
                RecipeWorkspaceManager.getInstance().createDraftWithType(recipeId, recipeType);
            }
            
            RecipeEditManager.startEdit(recipeId, recipeLayout);
        }
    }

    @Override
    protected void init() {
        // 计算配方布局位置
        @SuppressWarnings("removal")
        Rect2i layoutRect = recipeLayout.getRect();
        int layoutWidth = layoutRect.getWidth();
        int layoutHeight = layoutRect.getHeight();
        
        // 添加额外的空间用于按钮
        int totalHeight = layoutHeight + PADDING * 2 + 60 + PADDING;
        
        // 配方布局居中
        this.layoutX = (this.width - layoutWidth) / 2;
        this.layoutY = (this.height - totalHeight) / 2 + PADDING;
        
        // 设置配方布局的位置（这样drawRecipe内部会使用正确的位置）
        recipeLayout.setPosition(this.layoutX, this.layoutY);
        
        // 清除所有轮换（初始化时清除上一工作区的轮换状态）
        IngredientCycleManager.clearAllCycles();
        
        // 如果是新建模式（editMode=false），清除所有槽位的显示，使初始渲染为空
        if (!editMode) {
            RecipeEditManager.clearAllSlotDisplays(recipeLayout);
        }
        
        // 计算面板尺寸
        this.panelWidth = layoutWidth + PADDING * 2;
        this.panelHeight = layoutHeight + PADDING * 2;
        
        // 创建按钮
        int buttonY = layoutY + layoutHeight + PADDING + 32;
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

        int editBtnY = buttonY - BUTTON_HEIGHT - 4;
        int editTotalWidth = BUTTON_WIDTH * 4 + PADDING * 3;
        int editStartX = (this.width - editTotalWidth) / 2;

        this.editButton = Button.builder(Component.translatable("ingamerecipeeditor.screen.workspace.edit"), b -> {
            if (selectedSlot != null) {
                // 检查槽位是否被清除，如果被清除则不允许编辑数量
                if (IngredientCycleManager.isSlotCleared(selectedSlot)) {
                    return;
                }
                RecipeEditManager.IngredientEditValue initialValue = RecipeEditManager.getSlotEditValue(
                    recipeId, recipe, slots, selectedSlot
                ).orElseGet(() -> new RecipeEditManager.IngredientEditValue(
                    RecipeEditManager.getSlotIngredientKind(recipeId, recipe, slots, selectedSlot),
                    "",
                    1
                ));
                RecipeIngredientTextEditScreen.open(
                    this,
                    recipeId,
                    slots,
                    selectedSlot,
                    initialValue
                );
            }
        }).bounds(editStartX, editBtnY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.editButton);

        this.tagSelectButton = Button.builder(Component.translatable("ingamerecipeeditor.screen.workspace.select_tag"), b -> {
            if (selectedSlot != null && minecraft != null) {
                RecipeEditManager.IngredientKind kind = RecipeEditManager.getSlotIngredientKind(recipeId, recipe, slots, selectedSlot);
                // 化学品槽位和输出槽位不支持标签选择
                if (kind.isChemical() || selectedSlot.getRole() == RecipeIngredientRole.OUTPUT) {
                    return;
                }
                // 根据槽位类型预设标签类型过滤
                dev.whisperlyric.ingamerecipeeditor.gui.TagSelectorScreen.TagType initialTagType = switch (kind) {
                    case ITEM -> dev.whisperlyric.ingamerecipeeditor.gui.TagSelectorScreen.TagType.ITEMS;
                    case FLUID -> dev.whisperlyric.ingamerecipeeditor.gui.TagSelectorScreen.TagType.FLUIDS;
                    default -> dev.whisperlyric.ingamerecipeeditor.gui.TagSelectorScreen.TagType.ALL;
                };
                try {
                    minecraft.setScreen(new dev.whisperlyric.ingamerecipeeditor.gui.TagSelectorScreen(this, tagId -> {
                        try {
                            RecipeEditManager.setSlotEditValue(
                                recipeId,
                                slots,
                                selectedSlot,
                                new RecipeEditManager.IngredientEditValue(kind, "#" + tagId.toString(), 1)
                            );
                        } catch (Exception ignored) {}
                    }, initialTagType));
                } catch (Exception ignored) {}
            }
        }).bounds(editStartX + BUTTON_WIDTH + PADDING, editBtnY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.tagSelectButton);

        this.objectSelectButton = Button.builder(Component.translatable("ingamerecipeeditor.screen.workspace.select_object"), b -> {
            if (selectedSlot != null && minecraft != null) {
                RecipeEditManager.IngredientKind kind = RecipeEditManager.getSlotIngredientKind(recipeId, recipe, slots, selectedSlot);
                try {
                    switch (kind) {
                        case FLUID -> minecraft.setScreen(new dev.whisperlyric.ingamerecipeeditor.gui.FluidSelectorScreen(this, fluidId -> {
                            try {
                                var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(fluidId);
                                if (fluid != null) {
                                    RecipeEditManager.replaceSlot(
                                        recipeId, slots, selectedSlot,
                                        new net.minecraftforge.fluids.FluidStack(fluid, 1000)
                                    );
                                }
                            } catch (Exception ignored) {}
                        }));
                        case GAS -> minecraft.setScreen(new dev.whisperlyric.ingamerecipeeditor.gui.ChemicalSelectorScreen(this, chemicalId -> {
                            try {
                                RecipeEditManager.setSlotEditValue(
                                    recipeId, slots, selectedSlot,
                                    new RecipeEditManager.IngredientEditValue(RecipeEditManager.IngredientKind.GAS, chemicalId, 1000)
                                );
                            } catch (Exception ignored) {}
                        }, dev.whisperlyric.ingamerecipeeditor.gui.ChemicalSelectorScreen.ChemicalType.GAS));
                        case INFUSION -> minecraft.setScreen(new dev.whisperlyric.ingamerecipeeditor.gui.ChemicalSelectorScreen(this, chemicalId -> {
                            try {
                                RecipeEditManager.setSlotEditValue(
                                    recipeId, slots, selectedSlot,
                                    new RecipeEditManager.IngredientEditValue(RecipeEditManager.IngredientKind.INFUSION, chemicalId, 1000)
                                );
                            } catch (Exception ignored) {}
                        }, dev.whisperlyric.ingamerecipeeditor.gui.ChemicalSelectorScreen.ChemicalType.INFUSE_TYPE));
                        case PIGMENT -> minecraft.setScreen(new dev.whisperlyric.ingamerecipeeditor.gui.ChemicalSelectorScreen(this, chemicalId -> {
                            try {
                                RecipeEditManager.setSlotEditValue(
                                    recipeId, slots, selectedSlot,
                                    new RecipeEditManager.IngredientEditValue(RecipeEditManager.IngredientKind.PIGMENT, chemicalId, 1000)
                                );
                            } catch (Exception ignored) {}
                        }, dev.whisperlyric.ingamerecipeeditor.gui.ChemicalSelectorScreen.ChemicalType.PIGMENT));
                        case SLURRY -> minecraft.setScreen(new dev.whisperlyric.ingamerecipeeditor.gui.ChemicalSelectorScreen(this, chemicalId -> {
                            try {
                                RecipeEditManager.setSlotEditValue(
                                    recipeId, slots, selectedSlot,
                                    new RecipeEditManager.IngredientEditValue(RecipeEditManager.IngredientKind.SLURRY, chemicalId, 1000)
                                );
                            } catch (Exception ignored) {}
                        }, dev.whisperlyric.ingamerecipeeditor.gui.ChemicalSelectorScreen.ChemicalType.SLURRY));
                        case RESOURCE -> minecraft.setScreen(new dev.whisperlyric.ingamerecipeeditor.gui.ChemicalSelectorScreen(this, chemicalId -> {
                            try {
                                RecipeEditManager.setSlotEditValue(
                                    recipeId, slots, selectedSlot,
                                    new RecipeEditManager.IngredientEditValue(RecipeEditManager.IngredientKind.RESOURCE, chemicalId, 1000)
                                );
                            } catch (Exception ignored) {}
                        }, dev.whisperlyric.ingamerecipeeditor.gui.ChemicalSelectorScreen.ChemicalType.ANY));
                        default -> minecraft.setScreen(new dev.whisperlyric.ingamerecipeeditor.gui.ItemSelectorScreen(this, itemStack -> {
                            try {
                                RecipeEditManager.replaceSlot(recipeId, slots, selectedSlot, itemStack);
                            } catch (Exception ignored) {}
                        }));
                    }
                } catch (Exception ignored) {}
            }
        }).bounds(editStartX + (BUTTON_WIDTH + PADDING) * 2, editBtnY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.objectSelectButton);

        // 编辑其他属性按钮（打开属性编辑界面）
        Button propsBtn = Button.builder(Component.translatable("ingamerecipeeditor.screen.workspace.other_properties"), b -> {
            if (minecraft != null) {
                minecraft.setScreen(new PropertiesEditScreen(this, recipeId));
            }
        }).bounds(editStartX + (BUTTON_WIDTH + PADDING) * 3, editBtnY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(propsBtn);
        
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (submitButton != null && recipeId != null) {
            this.submitButton.active = RecipeEditManager.hasDraft(recipeId);
        }
        boolean hasSelection = this.selectedSlot != null;
        // 检查槽位是否被清除，如果被清除则禁用编辑按钮
        boolean isSlotCleared = hasSelection && IngredientCycleManager.isSlotCleared(selectedSlot);
        if (this.editButton != null) this.editButton.active = hasSelection && !isSlotCleared;
        // 化学品槽位和输出槽位不支持标签选择，禁用标签选择按钮
        if (this.tagSelectButton != null) {
            RecipeEditManager.IngredientKind kind = hasSelection ? RecipeEditManager.getSlotIngredientKind(recipeId, recipe, slots, selectedSlot) : null;
            boolean isOutput = hasSelection && selectedSlot.getRole() == RecipeIngredientRole.OUTPUT;
            this.tagSelectButton.active = hasSelection && kind != null && !kind.isChemical() && !isOutput;
        }
        if (this.objectSelectButton != null) this.objectSelectButton.active = hasSelection;
    }

    private void submit() {
        if (recipeId != null) {
            var result = RecipeEditManager.submit(recipeId);
            if (result.isPresent()) {
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(result.get());
                NetworkHandler.sendRecipeExport(recipeId, json);
                Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("ingamerecipeeditor.message.recipe_export_sent", recipeId), false);
            } else {
                Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("ingamerecipeeditor.message.recipe_export_no_changes"), false);
            }
        }
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
            
            // 清除所有轮换
            IngredientCycleManager.clearAllCycles();
            
            if (editMode) {
                // 编辑/复制模式：恢复原内容
                RecipeEditManager.clearDisplayOverrides(recipeLayout);
            } else {
                // 新建模式：清空所有槽位
                RecipeEditManager.clearAllSlotDisplays(recipeLayout);
            }
            
            // 重新开始编辑（保存原始值）
            RecipeEditManager.startEdit(recipeId, recipeLayout);
            
            // 立即应用一次草稿，避免下一帧tick前出现旧内容
            RecipeEditManager.applyDraftToLayout(recipeLayout, recipeId);
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
            RecipeEditManager.applyDraftToLayout(recipeLayout, recipeId);
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
        
        // 自定义渲染槽位高亮和tooltip（显示编辑后的信息）
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
        // JEI渲染配方内容（槽位物品/流体等）
        recipeLayout.drawRecipe(guiGraphics, mouseX, mouseY);
        
        // 在JEI绘制后重新应用编辑值，因为JEI的drawRecipe可能会重置displayOverrides
        // if (recipeId != null) {
        //     RecipeEditManager.applyDraftToLayout(recipeLayout);
        // }
        // 在JEI绘制后强制应用轮换，防止JEI的渲染覆盖它们
        // 只在索引变化时更新，避免闪烁
        try {
            dev.whisperlyric.ingamerecipeeditor.workspace.IngredientCycleManager.forceUpdateAllNow();
        } catch (Exception ignored) {}
        
        // 调用drawOverlays，但Mixin会拦截它，只渲染非槽位的叠加层（箭头、进度条等）
        // 槽位高亮和tooltip由工作区自定义渲染（renderSlotHighlight）
        recipeLayout.drawOverlays(guiGraphics, mouseX, mouseY);
        
        // 在配方布局渲染后，叠加渲染编辑的槽位内容
        // JEI 的 display overrides 系统不支持设置自定义渲染器，所以我们需要手动渲染化学物质
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

                // 先用背景色遮盖JEI渲染的原始化学品内容
                guiGraphics.fill(slotX, slotY, slotX + width, slotY + height, 0xFF0C0C18);

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

        // 渲染选中槽位的白色边框（边长+2，向外扩展1px）
        if (this.selectedSlot != null) {
            try {
                IRecipeSlotDrawable sel = this.selectedSlot;
                @SuppressWarnings("removal")
                Rect2i slotRect = sel.getRect();
                int slotX = currentLayoutX + slotRect.getX() - 1; // 向外扩展1px
                int slotY = currentLayoutY + slotRect.getY() - 1;
                int w = slotRect.getWidth() + 2; // 边长+2
                int h = slotRect.getHeight() + 2;
                // 1px border in white
                guiGraphics.fill(slotX, slotY, slotX + w, slotY + 1, 0xFFFFFFFF);
                guiGraphics.fill(slotX, slotY + h - 1, slotX + w, slotY + h, 0xFFFFFFFF);
                guiGraphics.fill(slotX, slotY, slotX + 1, slotY + h, 0xFFFFFFFF);
                guiGraphics.fill(slotX + w - 1, slotY, slotX + w, slotY + h, 0xFFFFFFFF);
            } catch (Exception ignored) {}
        }
    }

    /**
     * 渲染槽位tooltip
     */
    private void renderSlotTooltip(GuiGraphics guiGraphics, IRecipeSlotDrawable slot, int mouseX, int mouseY) {
        List<Component> tooltipLines = new ArrayList<>();
        
        // 检查槽位是否被清除
        boolean isSlotCleared = IngredientCycleManager.isSlotCleared(slot);
        
        // 检查是否有编辑值
        RecipeEditManager.IngredientEditValue editValue = null;
        if (recipeId != null) {
            editValue = RecipeEditManager.getSlotEditValue(
                recipeId, recipe, slots, slot
            ).orElse(null);
        }
        
        if (isSlotCleared) {
            // 槽位被清除，显示空提示
            tooltipLines.add(Component.translatable("ingamerecipeeditor.tooltip.slot_empty"));
        } else if (editValue != null && editValue.kind().isChemical()) {
            // 有化学品编辑值，使用自定义tooltip
            tooltipLines.addAll(ChemicalSlotRenderer.getChemicalTooltip(
                editValue.ingredientId(), editValue.amount(), editValue.amount()));
        } else if (editValue != null) {
            // 有其他编辑值时，过滤JEI tooltip中的多候选信息
            List<Component> jeiTooltip = slot.getTooltip();
            for (Component line : jeiTooltip) {
                String text = line.getString();
                // 过滤掉"接受以下任何物品"和tag列表信息
                if (!text.contains("接受以下任何物品") && 
                    !text.startsWith("#") && 
                    !text.contains("Accepts any of")) {
                    tooltipLines.add(line);
                }
            }
            // 如果编辑值是tag，补全tag显示
            String ingredientId = editValue.ingredientId();
            if (ingredientId.startsWith("#")) {
                String tagId = ingredientId.substring(1);
                tooltipLines.add(Component.literal("#" + tagId)
                    .withStyle(style -> style.withColor(0xFF5555FF)));
            }
        } else {
            // 无编辑值且未被清除，使用JEI的tooltip
            tooltipLines.addAll(slot.getTooltip());
        }
        
        guiGraphics.renderComponentTooltip(this.font, tooltipLines, mouseX, mouseY);
    }
    
    /**
     * 获取原料的显示名称
     */
    private Component getIngredientDisplayName(RecipeEditManager.IngredientEditValue editValue) {
        String ingredientId = editValue.ingredientId();
        
        // 处理标签情况（以#开头），支持包含花括号或多个标签
        if (ingredientId.startsWith("#")) {
            String tagRaw = ingredientId.substring(1);
            tagRaw = tagRaw.replaceAll("[{}\\s]","");
            if (tagRaw.contains(",")) {
                String[] tags = tagRaw.split(",");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < tags.length; i++) {
                    String tag = tags[i].trim();
                    if (i > 0) sb.append(", ");
                    sb.append(formatTagDisplayName(tag));
                }
                return Component.literal(sb.toString());
            }
            return Component.literal(formatTagDisplayName(tagRaw));
        }
        
        try {
            ResourceLocation id = ResourceLocation.tryParse(ingredientId);
            if (id == null) {
                return Component.literal(ingredientId);
            }
            
            switch (editValue.kind()) {
                case ITEM -> {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
                    if (item != null) {
                        ItemStack stack = new ItemStack(item);
                        return Component.literal(stack.getDisplayName().getString());
                    }
                }
                case FLUID -> {
                    var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id);
                    if (fluid != null) {
                        net.minecraftforge.fluids.FluidStack stack = new net.minecraftforge.fluids.FluidStack(fluid, 1000);
                        return Component.literal(stack.getDisplayName().getString());
                    }
                }
                default -> {
                    // 化学物质：尝试获取显示名称
                    if (editValue.kind().isChemical()) {
                        String name = ChemicalSlotRenderer.getChemicalDisplayName(ingredientId);
                        return Component.literal(name);
                    }
                }
            }
        } catch (Exception ignored) {}
        
        return Component.literal(ingredientId);
    }
    
    /**
     * 格式化标签显示名称
     */
    private String formatTagDisplayName(String tagId) {
        // 从标签ID中提取路径部分并格式化（保留斜杠等特殊字符）
        String path = tagId.contains(":") ? tagId.substring(tagId.indexOf(":") + 1) : tagId;
        
        // 将下划线替换为空格，但保留斜杠
        path = path.replace("_", " ");
        
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : path.toCharArray()) {
            if (Character.isSpaceChar(c) || c == '/') {
                // 空格或斜杠后需要大写
                result.append(c);
                if (c == '/') {
                    result.append(' '); // 斜杠后添加空格以便阅读
                }
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * 获取流体显示名称
     */
    private String getFluidDisplayName(String fluidId) {
        if (fluidId == null || fluidId.isEmpty()) {
            return "空";
        }
        
        // 处理标签情况（以#开头）
        if (fluidId.startsWith("#")) {
            String tagRaw = fluidId.substring(1).replaceAll("[{}\\s]", "");
            if (tagRaw.contains(",")) {
                String[] tags = tagRaw.split(",");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < tags.length; i++) {
                    String tag = tags[i].trim();
                    if (i > 0) sb.append(", ");
                    sb.append(formatTagDisplayName(tag));
                }
                return sb.toString();
            }
            return formatTagDisplayName(tagRaw);
        }
        
        try {
            ResourceLocation id = ResourceLocation.tryParse(fluidId);
            if (id == null) {
                return fluidId;
            }
            
            var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id);
            if (fluid != null) {
                net.minecraftforge.fluids.FluidStack stack = new net.minecraftforge.fluids.FluidStack(fluid, 1000);
                return stack.getDisplayName().getString();
            }
        } catch (Exception ignored) {}
        
        // 格式化ID作为备用名称
        String path = fluidId.contains(":") ? fluidId.substring(fluidId.indexOf(":") + 1) : fluidId;
        path = path.replace("_", " ");
        
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : path.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
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
        // 先尝试槽位点击（选中/右键清除）
        if (handleSlotClick(mouseX, mouseY, button)) {
            return true;
        }
        // 再尝试按钮/控件点击
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true; // 按钮被点击，不取消选中
        }
        // 点击在配方布局面板区域内但不在槽位上，取消选中
        if (button == 0 && this.selectedSlot != null) {
            Rect2i layoutRect = recipeLayout.getRect();
            int panelX = layoutRect.getX() - PADDING;
            int panelY = layoutRect.getY() - PADDING;
            int panelW = layoutRect.getWidth() + PADDING * 2;
            int panelH = layoutRect.getHeight() + PADDING * 2;
            if (mouseX >= panelX && mouseX < panelX + panelW &&
                mouseY >= panelY && mouseY < panelY + panelH) {
                this.selectedSlot = null;
                updateButtonStates();
            }
        }
        return false;
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
                    // 左键点击：选取槽位（在其外侧绘制1px白框并保持）
                    if (this.selectedSlot == slot) {
                        // 再次左键点击取消选取
                        this.selectedSlot = null;
                    } else {
                        this.selectedSlot = slot;
                    }
                    updateButtonStates();
                    return true;
                } else if (mouseButton == 1) {
                    // 右键点击：清除槽位（如果允许），保留之前的行为检查
                    if (!RecipeEditManager.canClearSlots(recipeId, recipe)) {
                        return true;
                    }
                    if (hasCarriedItem()) {
                        return false;
                    }
                    // 根据模式清除槽位：编辑模式恢复原内容，新建模式设置为空
                    RecipeEditManager.clearSlot(recipeId, slots, slot, editMode);
                    // 如果被清除的槽位是当前选中槽位，则取消选中
                    if (this.selectedSlot == slot) {
                        this.selectedSlot = null;
                        updateButtonStates();
                    }
                    return true;
                }
            }
        }
        // 点击不在任何槽位上，返回false让mouseClicked处理后续逻辑
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
package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.gui.recipe.*;
import com.wzz.registerhelper.gui.recipe.component.*;
import com.wzz.registerhelper.gui.recipe.component.renderer.SlotRenderer;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeBuilder;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig.*;
import com.wzz.registerhelper.gui.recipe.layout.LayoutManager;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import com.wzz.registerhelper.network.BlacklistClientHelper;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.tags.CustomTagManager;
import com.wzz.registerhelper.util.ModLogger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class RecipeCreatorScreen extends Screen {

    private static final int PADDING = 20;

    public boolean switchingToJEI = false;

    // 核心组件
    private SlotManager slotManager;
    private FillModeHandler fillModeHandler;
    private RecipeLoader recipeLoader;

    // 动态尺寸变量
    private int contentWidth;
    private int contentHeight;
    private int leftPos, topPos;

    // 配方状态 - 使用新的动态系统
    private RecipeTypeDefinition currentRecipeType;
    private String currentCraftingMode = "shaped"; // 字符串而不是枚举
    private String currentCookingType = "smelting";
    private int customTier = 1; // 用于支持动态tier
    private boolean isEditingExisting = false;
    private ResourceLocation editingRecipeId = null;

    // UI控件
    private Button recipeTypeButton;
    private CycleButton<String> craftingModeButton;
    private CycleButton<String> cookingTypeButton;
    private CycleButton<Integer> tierButton;
    private CycleButton<FillMode> fillModeButton;
    private EditBox cookingTimeBox;
    private EditBox cookingExpBox;
    private Button createButton;
    private Button cancelButton;
    private Button clearAllButton;
    private Button selectBrushItemButton;
    private Button editExistingRecipeButton;
    private ComponentRenderManager componentRenderManager;
    private boolean menuOpen = false;
    
    private int selectedSlotIndex = -1;
    private boolean isResultSlotSelected = false;
    private FluidSlotComponent selectedFluidSlot = null;
    private GasSlotComponent selectedGasSlot = null;
    private ChemicalSlotComponent selectedChemicalSlot = null;
    private RecipeComponent selectedOutputComponent = null;
    private SlotComponent selectedOutputSlot = null;
    
    // 槽位操作按钮（5个）
    private Button btnChangeAmount;
    private Button btnSelectFromCreative;
    private Button btnSelectFromInventory;
    private Button btnSelectFromJEI;
    private Button btnAddTagGroup;
    private Button rotaryModeButton;
    private String currentRotaryMode = "reversible";
    
    // 特殊配方属性
    private Button btnEditSpecialProperties;
    private Map<String, Object> specialProperties = new HashMap<>();
    
    /** 下拉菜单的屏幕坐标（render 时计算，mouseClicked 时判断） */
    private int menuBtnX, menuBtnY, menuBtnW = 68;

    /** 下拉菜单三个选项的标签/动作 */
    private static final String[]   MENU_LABELS  = { "黑名单管理", "覆盖管理", "添加黑名单" };
    private static final int        MENU_ITEM_H  = 18;

    // 构造函数
    public RecipeCreatorScreen() {
        super(Component.literal("配方创建器"));
        // 设置默认配方类型
        this.currentRecipeType = DynamicRecipeTypeConfig.getRecipeType("crafting_shaped");
        if (this.currentRecipeType == null) {
            // 后备方案：使用第一个可用的配方类型
            List<RecipeTypeDefinition> available = DynamicRecipeTypeConfig.getAvailableRecipeTypes();
            this.currentRecipeType = available.isEmpty() ? null : available.get(0);
        }
        initializeComponents();
    }
    private RecipeLoader.LoadResult pendingLoadResult = null;

    public RecipeCreatorScreen(ResourceLocation recipeId) {
        super(Component.literal("配方编辑器"));
        this.editingRecipeId = recipeId;
        this.isEditingExisting = true;
        // 设置默认配方类型
        this.currentRecipeType = DynamicRecipeTypeConfig.getRecipeType("crafting_shaped");
        initializeComponents();
        pendingLoadResult = recipeLoader.loadRecipe(recipeId);
        if (pendingLoadResult.success) {
            RecipeTypeDefinition loadedType = findRecipeTypeDefinition(pendingLoadResult);
            if (loadedType != null) {
                this.currentRecipeType = loadedType;
                this.currentCraftingMode = pendingLoadResult.craftingMode != null ?
                        pendingLoadResult.craftingMode.name().toLowerCase() : "shaped";
                this.currentCookingType = pendingLoadResult.cookingType != null ?
                        pendingLoadResult.cookingType.name().toLowerCase() : "smelting";
                this.customTier = pendingLoadResult.avaritiaTeir;
            }
        }
    }

    /**
     * 初始化核心组件
     */
    private void initializeComponents() {
        // 初始化回调函数
        Consumer<String> errorCallback = this::displayError;
        Consumer<String> successCallback = this::displaySuccess;
        Consumer<Integer> itemSelectorCallback = this::openItemSelectorForSlot;
        Runnable brushSelectorCallback = this::openBrushSelector;

        // 创建组件实例
        this.fillModeHandler = new FillModeHandler(errorCallback, itemSelectorCallback, brushSelectorCallback);
        this.recipeLoader = new RecipeLoader(this::displayInfo);

        calculateDynamicSize();
        this.componentRenderManager = new ComponentRenderManager(this.font);
    }

    /**
     * 获取网格尺寸（含当前自适应 spacing）。
     * 对支持 Tier 的配方类型，使用当前 customTier 对应的实际尺寸，
     * 确保 calculateDynamicSize 能正确计算窗口高度，避免按钮遮挡格子。
     */
    private SlotManager.GridDimensions getGridDimensions() {
        if (currentRecipeType == null) {
            return new SlotManager.GridDimensions(3, 3);
        }

        int gridWidth, gridHeight;

        // Tier 动态类型：用当前 customTier 对应的实际尺寸
        if (currentRecipeType.isAvaritiaType() ||
                Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class))) {
            int gridSize = DynamicRecipeBuilder.getGridSizeForTier(customTier);
            gridWidth = gridSize;
            gridHeight = gridSize;
        } else {
            // 固定尺寸类型（包括 ArcaneVortex 16x16 等大型固定配方台）
            gridWidth  = currentRecipeType.getMaxGridWidth();
            gridHeight = currentRecipeType.getMaxGridHeight();
        }

        // 无论是动态 Tier 还是固定大格子，都需要计算自适应间距，
        // 否则 16x16 固定格子在小屏幕上同样会溢出遮住按钮。
        int spacing = computeAdaptiveSpacing(Math.max(gridWidth, gridHeight));
        return new SlotManager.GridDimensions(gridWidth, gridHeight, spacing);
    }

    /**
     * 根据屏幕可用高度，为指定 gridSize 计算合适的槽位间距。
     * <p>
     * 垂直方向布局：
     *   - 顶部控件区（含标题）：150px
     *   - 格子区：gridSize * spacing
     *   - 底部按钮区：35px
     *   - 屏幕上下留边：40px
     * <p>
     * 因此：spacing = (screenH - 40 - 150 - 35) / gridSize
     * 结果钳制到 [MIN_SLOT_SPACING, DEFAULT_SLOT_SPACING]。
     */
    private int computeAdaptiveSpacing(int gridSize) {
        if (this.height == 0 || gridSize <= 0) return SlotManager.DEFAULT_SLOT_SPACING;
        // 上下屏幕留白 40 + 顶部控件 150 + 底部按钮区 35
        int overhead = 40 + 150 + 35;
        int available = this.height - overhead;
        int spacing = available / gridSize;
        return Math.max(SlotManager.MIN_SLOT_SPACING,
                Math.min(SlotManager.DEFAULT_SLOT_SPACING, spacing));
    }

    @Override
    protected void init() {
        if (componentRenderManager != null) {
            componentRenderManager.clear();
        }

        // 按顺序调用
        calculateDynamicSize();
        initializeControls();
        initializeComponentRenderers();

        // 应用待加载的配方数据
        if (pendingLoadResult != null && pendingLoadResult.success) {
            applyLoadedRecipe(pendingLoadResult);
            pendingLoadResult = null;
        }

        updateVisibility();
    }

    /**
     * 计算动态尺寸
     */
    private void calculateDynamicSize() {
        if (currentRecipeType == null) {
            ModLogger.getLogger().error("calculateDynamicSize: currentRecipeType为null");
            return;
        }

        // 构造函数里 width/height 尚未初始化，跳过
        if (this.width == 0 || this.height == 0) return;

        // 获取当前配方类型的网格尺寸（已内含自适应 spacing）
        SlotManager.GridDimensions gridDim = getGridDimensions();

        // 计算所需的最小尺寸
        // 纵向：顶部控件区150 + 格子 + 底部按钮区35 + 上下留白20*2
        int rightPanelWidth = 150;
        this.contentWidth = Math.max(560, PADDING + gridDim.getPixelWidth() + PADDING + rightPanelWidth + PADDING);
        this.contentHeight = Math.max(480, PADDING + 150 + gridDim.getPixelHeight() + 35 + PADDING);

        this.contentWidth = Math.min(this.contentWidth, this.width - 40);
        this.contentHeight = Math.min(this.contentHeight, this.height - 40);

        this.leftPos = (this.width - contentWidth) / 2;
        this.topPos = (this.height - contentHeight) / 2;

        int rightPanelX = leftPos + contentWidth - 150;

        if (slotManager == null) {
            slotManager = new SlotManager(leftPos + PADDING, topPos, rightPanelX);
            slotManager.setSlotSpacing(gridDim.spacing());
            updateSlotManagerRecipeType();
        } else {
            // 检查网格尺寸是否改变
            SlotManager.GridDimensions oldDim = slotManager.getGridDimensions();
            if (oldDim == null ||
                    oldDim.getPixelWidth() != gridDim.getPixelWidth() ||
                    oldDim.getPixelHeight() != gridDim.getPixelHeight()) {

                slotManager = new SlotManager(leftPos + PADDING, topPos, rightPanelX);
                slotManager.setSlotSpacing(gridDim.spacing());
                updateSlotManagerRecipeType();
            } else {
                // 网格尺寸没变，只更新坐标和 spacing
                slotManager.setSlotSpacing(gridDim.spacing());
                slotManager.updateCoordinates(leftPos + PADDING, topPos, rightPanelX);
                updateSlotManagerRecipeType();
            }
        }
    }

    /**
     * 更新SlotManager的配方类型
     */
    private void updateSlotManagerRecipeType() {
        if (slotManager == null) {
            ModLogger.getLogger().error("slotManager为null");
            return;
        }

        if (currentRecipeType == null) {
            ModLogger.getLogger().error("currentRecipeType为null");
            return;
        }
        boolean shouldPreserve = (pendingLoadResult == null);
        slotManager.setRecipeType(currentRecipeType, customTier, shouldPreserve);
    }

    /**
     * 从选择器加载配方
     */
    private void loadSelectedRecipe(ResourceLocation recipeId) {
        UnifiedRecipeInfo info = recipeLoader.findRecipeInfo(recipeId);
        if (info == null) {
            displayError("找不到配方信息: " + recipeId);
            return;
        }

        this.editingRecipeId = recipeId;
        this.isEditingExisting = true;

        RecipeLoader.LoadResult result = recipeLoader.loadRecipe(recipeId);
        if (!result.success) {
            displayError(result.message);
            return;
        }

        RecipeTypeDefinition loadedType = findRecipeTypeDefinition(result);
        if (loadedType == null) {
            displayError("无法识别配方类型: " + result.originalRecipeTypeId + " 类型：" + currentRecipeType.getId());
            return;
        }

        int inferredTier = inferTierFromIngredientCount(result.ingredients.size(), loadedType);
        if (inferredTier != result.avaritiaTeir) {
            result.avaritiaTeir = inferredTier;
        }

        boolean typeChanged = !loadedType.getId().equals(this.currentRecipeType.getId());
        boolean tierChanged = result.avaritiaTeir != this.customTier;

        if (typeChanged || tierChanged) {
            this.currentRecipeType = loadedType;
            this.currentCraftingMode = result.craftingMode != null ?
                    result.craftingMode.name().toLowerCase() : "shaped";
            this.currentCookingType = result.cookingType != null ?
                    result.cookingType.name().toLowerCase() : "smelting";
            this.customTier = result.avaritiaTeir;

            this.slotManager = null;
            this.pendingLoadResult = result;

            this.clearWidgets();
            this.init();

        } else {
            // 类型没变，直接应用材料
            this.currentCraftingMode = result.craftingMode != null ?
                    result.craftingMode.name().toLowerCase() : "shaped";
            this.currentCookingType = result.cookingType != null ?
                    result.cookingType.name().toLowerCase() : "smelting";

            if (craftingModeButton != null) {
                craftingModeButton.setValue(currentCraftingMode);
            }
            if (cookingTypeButton != null) {
                cookingTypeButton.setValue(currentCookingType);
            }
            if (tierButton != null) {
                tierButton.setValue(customTier);
            }

            applyLoadedRecipe(result);
        }

        String buttonText = "更新配方";
        if (info.hasOverride || (!recipeLoader.isCustomRecipe(recipeId))) {
            buttonText += " (覆盖)";
        }
        if (createButton != null) {
            createButton.setMessage(Component.literal(buttonText));
        }

        displayInfo("已载入 " + info.description);
    }

    /**
     * 根据材料数量推断tier
     */
    private int inferTierFromIngredientCount(int ingredientCount, RecipeTypeDefinition recipeType) {
        if (!Boolean.TRUE.equals(recipeType.getProperty("supportsTiers", Boolean.class))) {
            return 1;
        }
        return DynamicRecipeBuilder.getTierFromIngredientCount(ingredientCount);
    }

    /**
     * 配方类型更改处理
     */
    private void onRecipeTypeChanged(RecipeTypeDefinition newType) {
        if (newType == null) {
            ModLogger.getLogger().error("新配方类型为null，取消切换");
            return;
        }

        clearAllSelections();
        
        specialProperties.clear();

        // 保存数据
        ItemStack currentResult = slotManager != null ? slotManager.getResultItem() : ItemStack.EMPTY;
        List<ItemStack> currentIngredients = slotManager != null ?
                new ArrayList<>(slotManager.getIngredients()) : new ArrayList<>();

        this.currentRecipeType = newType;

        Integer defaultTier = newType.getProperty("tier", Integer.class);
        if (defaultTier != null) {
            this.customTier = defaultTier;
        }

        String mode = newType.getProperty("mode", String.class);
        if (mode != null) {
            this.currentCraftingMode = mode;
        }

        this.clearWidgets();
        this.init();

        if (slotManager != null && !currentResult.isEmpty()) {
            slotManager.setResultItem(currentResult);

            int newSlotCount = slotManager.getIngredientSlots().size();
            for (int i = 0; i < Math.min(currentIngredients.size(), newSlotCount); i++) {
                if (!currentIngredients.get(i).isEmpty()) {
                    slotManager.setIngredient(i, currentIngredients.get(i));
                }
            }
        }

        syncDataToRenderer();
    }

    /**
     * 初始化组件渲染器 - 修复版
     */
    private void initializeComponentRenderers() {
        if (slotManager == null) {
            ModLogger.getLogger().warn("slotManager为空，无法初始化渲染器");
            return;
        }

        if (componentRenderManager == null) {
            ModLogger.getLogger().warn("componentRenderManager为空，无法初始化渲染器");
            return;
        }

        List<RecipeComponent> components = slotManager.getComponents();
        if (components == null || components.isEmpty()) {
            componentRenderManager.clear();
            //ModLogger.getLogger().warn("components为空，无法初始化渲染器");
            return;
        }

        // 设置回调
        componentRenderManager.setSlotCallbacks(
                this::openItemSelectorForSlot,
                this::clearIngredientSlot
        );
        componentRenderManager.setOutputSlotCallbacks(
                this::onOutputSlotSelected,
                this::clearOutputSlot
        );
        componentRenderManager.setResultCallback(this::openResultSelector);
        componentRenderManager.setFluidSlotCallback(this::onFluidSlotClicked);
        componentRenderManager.setGasSlotCallback(this::onGasSlotClicked);
        componentRenderManager.setChemicalSlotCallback(this::onChemicalSlotClicked);

        // 初始化渲染器
        componentRenderManager.initializeRenderers(components);

        // 注册EditBox
        for (EditBox editBox : componentRenderManager.getEditBoxes()) {
            addRenderableWidget(editBox);
        }

        // 同步当前数据
        syncDataToRenderer();
    }

    /**
     * 应用已加载的配方数据
     */
    private void applyLoadedRecipe(RecipeLoader.LoadResult result) {
        if (slotManager != null) {
            if (result.ingredientsData != null && !result.ingredientsData.isEmpty()) {
                slotManager.setIngredientsData(result.ingredientsData);
            } else {
                slotManager.setIngredients(result.ingredients);
            }
            slotManager.setResultItem(result.resultItem);
            
            if (result.outputComponent != null) {
                slotManager.setOutputComponent(result.outputComponent);
            }
        }
        syncDataToRenderer();
    }

    /**
     * 同步数据到渲染器
     */
    private void syncDataToRenderer() {
        if (componentRenderManager == null) {
            ModLogger.getLogger().warn("componentRenderManager 为空，无法同步");
            return;
        }

        if (slotManager == null) {
            ModLogger.getLogger().warn("slotManager 为空，无法同步");
            return;
        }

        // 同步所有槽位物品 - 使用 ingredientSlots 的 index 字段作为 key
        List<SlotManager.IngredientSlot> ingredientSlots = slotManager.getIngredientSlots();
        for (int i = 0; i < ingredientSlots.size(); i++) {
            SlotManager.IngredientSlot slot = ingredientSlots.get(i);
            IngredientData data = slotManager.getIngredientData(i);
            ItemStack item = data != null ? data.getItemStack() : ItemStack.EMPTY;
            componentRenderManager.updateSlotItem(slot.index(), item);
        }

        // 同步结果物品
        ItemStack result = slotManager.getResultItem();
        componentRenderManager.updateResultItem(result);
        
        // 同步输出槽物品
        Map<Integer, ItemStack> outputSlotItems = slotManager.getOutputSlotItems();
        for (Map.Entry<Integer, ItemStack> entry : outputSlotItems.entrySet()) {
            componentRenderManager.updateSlotItem(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 根据加载结果查找配方类型定义
     */
    private RecipeTypeDefinition findRecipeTypeDefinition(RecipeLoader.LoadResult result) {
        // 优先使用原始配方类型ID
        if (result.originalRecipeTypeId != null && !result.originalRecipeTypeId.isEmpty()) {
            RecipeTypeDefinition found = DynamicRecipeTypeConfig.getRecipeType(result.originalRecipeTypeId);
            if (found != null) {
                return found;
            }
            found = findByProcessorSupport(result.originalRecipeTypeId);
            if (found != null) {
                return found;
            }
            if (result.originalRecipeTypeId.contains("shaped_table")) {
                found = DynamicRecipeTypeConfig.getRecipeType("avaritia_shaped");
                if (found != null) {
                    return found;
                }
            } else if (result.originalRecipeTypeId.contains("shapeless_table")) {
                found = DynamicRecipeTypeConfig.getRecipeType("avaritia_shapeless");
                if (found != null) {
                    return found;
                }
            }
        }

        // 降级到枚举类型匹配
        if (result.recipeType != null) {
            String recipeTypeName = result.recipeType.name().toLowerCase();

            switch (recipeTypeName) {
                case "crafting" -> {
                    String mode = result.craftingMode != null ? result.craftingMode.name().toLowerCase() : "shaped";
                    String typeId = "crafting_" + mode;
                    return DynamicRecipeTypeConfig.getRecipeType(typeId);

                }
                case "cooking" -> {
                    String cookingType = result.cookingType != null ? result.cookingType.name().toLowerCase() : "smelting";
                    return DynamicRecipeTypeConfig.getRecipeType(cookingType);
                }
                case "avaritia" -> {
                    String mode = result.craftingMode != null ? result.craftingMode.name().toLowerCase() : "shaped";
                    String typeId = "avaritia_" + mode;

                    RecipeTypeDefinition found = DynamicRecipeTypeConfig.getRecipeType(typeId);
                    if (found != null) {
                        return found;
                    }

                    // 如果没找到，尝试查找原始ID（移除 ":crafting_table_recipe" 后缀）
                    if (result.originalRecipeTypeId != null) {
                        String namespace = result.originalRecipeTypeId.split(":")[0];
                        if ("avaritia".equals(namespace)) {
                            // 尝试直接使用 "avaritia_shaped"
                            return DynamicRecipeTypeConfig.getRecipeType("avaritia_shaped");
                        }
                    }
                    return found;

                }
                case "brewing" -> {
                    return DynamicRecipeTypeConfig.getRecipeType("brewing");
                }
                case "stonecutting" -> {
                    return DynamicRecipeTypeConfig.getRecipeType("stonecutting");
                }
                case "smithing", "smithing_transform" -> {
                    return DynamicRecipeTypeConfig.getRecipeType("smithing_transform");
                }
            }
        }
        return null;
    }

    /**
     * 通过 processor 的 supportedRecipeTypes 查找配方类型定义
     */
    private RecipeTypeDefinition findByProcessorSupport(String recipeTypeId) {
        for (RecipeTypeDefinition definition : DynamicRecipeTypeConfig.getAvailableRecipeTypes()) {
            ModRecipeProcessor processor = definition.getProcessor();
            if (processor != null) {
                String[] supportedTypes = processor.getSupportedRecipeTypes();
                if (supportedTypes != null) {
                    for (String supportedType : supportedTypes) {
                        String fullType = supportedType.contains(":") ? supportedType : definition.getModId() + ":" + supportedType;
                        if (recipeTypeId.equals(fullType) || recipeTypeId.endsWith(":" + supportedType)) {
                            return definition;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 初始化控件
     */
    private void initializeControls() {
        // 第一行控件 - 动态布局
        int controlStartX = leftPos + 15;
        int controlY1 = topPos + 35;
        int controlSpacing = 10;
        int currentX = controlStartX;

        String currentTypeName = currentRecipeType != null ? currentRecipeType.getDisplayName() : "选择配方类型";

        recipeTypeButton = addRenderableWidget(Button.builder(
                        Component.literal(currentTypeName + " ▼"),
                        button -> openRecipeTypeSelector())
                .bounds(currentX, controlY1, 120, 20)
                .build());
        recipeTypeButton.setMessage(Component.literal(currentTypeName + " ▼"));
        currentX += 130 + controlSpacing;

        // 合成模式选择器（动态显示）
        craftingModeButton = addRenderableWidget(CycleButton.<String>builder(
                        mode -> Component.literal(getDisplayNameForMode(mode)))
                .withValues("shaped", "shapeless")
                .withInitialValue(currentCraftingMode)
                .displayOnlyValue()
                .create(currentX, controlY1, 60, 20,
                        Component.literal("合成模式"), this::onCraftingModeChanged));

        // 烹饪类型选择器（动态显示）
        cookingTypeButton = addRenderableWidget(CycleButton.<String>builder(
                        type -> Component.literal(getDisplayNameForCookingType(type)))
                .withValues(getCookingTypes())
                .withInitialValue(currentCookingType)
                .displayOnlyValue()
                .create(currentX, controlY1, 70, 20,
                        Component.literal("烹饪类型"), this::onCookingTypeChanged));
        currentX += 80 + controlSpacing;

        // 等级选择器（支持动态等级）
        tierButton = addRenderableWidget(CycleButton.<Integer>builder(
                        tier -> Component.literal("T" + tier))
                .withValues(getAvailableTiers())
                .withInitialValue(customTier)
                .displayOnlyValue()
                .create(currentX, controlY1, 50, 20,
                        Component.literal("等级"), this::onTierChanged));

        // 第二行控件
        int controlY2 = topPos + 65;
        currentX = controlStartX;

        fillModeButton = addRenderableWidget(CycleButton.<FillMode>builder(
                        mode -> Component.literal(mode.getDisplayName()))
                .withValues(FillMode.values())
                .withInitialValue(fillModeHandler.getCurrentMode())
                .displayOnlyValue()
                .create(currentX, controlY2, 80, 20,
                        Component.literal("填充模式"), this::onFillModeChanged));
        currentX += 90 + controlSpacing;

        selectBrushItemButton = addRenderableWidget(Button.builder(
                        Component.literal("选择画笔物品"),
                        button -> fillModeHandler.openBrushSelector())
                .bounds(currentX, controlY2, 100, 20)
                .build());
        currentX += 110 + controlSpacing;

        Button createCustomTagButton = addRenderableWidget(Button.builder(
                        Component.literal("创建自定义标签"),
                        button -> openCustomTagCreator())
                .bounds(currentX, controlY2, 110, 20)
                .build());

        // 右侧面板
        initializeRightPanel();
        
        // 回旋式气液转换器模式切换按钮
        if (currentRecipeType != null && currentRecipeType.getId().equals("mekanism:rotary")) {
            String modeName = switch (currentRotaryMode) {
                case "reversible" -> "可逆模式";
                case "decondensation" -> "液体蒸发";
                case "condensation" -> "气体冷凝";
                default -> "可逆模式";
            };
            rotaryModeButton = addRenderableWidget(Button.builder(
                            Component.literal(modeName),
                            button -> cycleRotaryMode())
                    .bounds(leftPos + 15, topPos + 95, 100, 20)
                    .build());
        }

        // 底部按钮
        initializeBottomButtons();
        
        // 槽位操作按钮（5个）
        initializeSlotOperationButtons();
    }
    
    /**
     * 初始化槽位操作按钮
     */
    private void initializeSlotOperationButtons() {
        int buttonY = topPos + contentHeight - 60;
        int buttonWidth = 100;
        int buttonHeight = 20;
        int spacing = 10;
        int startX = leftPos + 20;
        
        btnChangeAmount = addRenderableWidget(Button.builder(
            Component.literal("更改数量"),
            button -> onChangeAmountClicked()
        ).bounds(startX, buttonY, buttonWidth, buttonHeight).build());
        
        btnSelectFromCreative = addRenderableWidget(Button.builder(
            Component.literal("创造物品栏"),
            button -> onSelectFromCreativeClicked()
        ).bounds(startX + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight).build());
        
        btnSelectFromInventory = addRenderableWidget(Button.builder(
            Component.literal("玩家物品栏"),
            button -> onSelectFromInventoryClicked()
        ).bounds(startX + 2 * (buttonWidth + spacing), buttonY, buttonWidth, buttonHeight).build());
        
        btnSelectFromJEI = addRenderableWidget(Button.builder(
            Component.literal("JEI选择"),
            button -> onSelectFromJEIClicked()
        ).bounds(startX + 3 * (buttonWidth + spacing), buttonY, buttonWidth, buttonHeight).build());
        
        btnAddTagGroup = addRenderableWidget(Button.builder(
            Component.literal("添加标签组"),
            button -> onAddTagGroupClicked()
        ).bounds(startX + 4 * (buttonWidth + spacing), buttonY, buttonWidth, buttonHeight).build());
        
        updateSlotOperationButtonsVisibility();
    }
    
    /**
     * 更新槽位操作按钮的可见性
     */
    private void updateSlotOperationButtonsVisibility() {
        boolean hasSelection = selectedSlotIndex >= 0 || isResultSlotSelected || 
                               selectedFluidSlot != null || selectedGasSlot != null || 
                               selectedChemicalSlot != null || selectedOutputComponent != null ||
                               selectedOutputSlot != null;
        
        boolean isBulkSlotSelected = false;
        
        if (selectedSlotIndex >= 0) {
            if (slotManager != null) {
                for (RecipeComponent component : slotManager.getComponents()) {
                    if (component instanceof SlotComponent slotComp && slotComp.getSlotIndex() == selectedSlotIndex) {
                        isBulkSlotSelected = slotComp.isBulkSlot();
                        break;
                    }
                }
            }
        } else if (isResultSlotSelected) {
            isBulkSlotSelected = true;
        } else if (selectedFluidSlot != null || selectedGasSlot != null || selectedChemicalSlot != null) {
            isBulkSlotSelected = true;
        } else if (selectedOutputSlot != null) {
            isBulkSlotSelected = selectedOutputSlot.isBulkSlot();
        } else if (selectedOutputComponent instanceof SlotComponent slotComp) {
            isBulkSlotSelected = slotComp.isBulkSlot();
        }
        
        btnChangeAmount.visible = hasSelection && isBulkSlotSelected;
        btnSelectFromCreative.visible = hasSelection && (selectedSlotIndex >= 0 || isResultSlotSelected || selectedOutputComponent != null || selectedOutputSlot != null);
        btnSelectFromInventory.visible = hasSelection && (selectedSlotIndex >= 0 || isResultSlotSelected || selectedOutputComponent != null || selectedOutputSlot != null);
        btnSelectFromJEI.visible = hasSelection;
        btnAddTagGroup.visible = hasSelection;
    }

    /**
     * 获取模式显示名称
     */
    private String getDisplayNameForMode(String mode) {
        return switch (mode) {
            case "shaped" -> "有序";
            case "shapeless" -> "无序";
            default -> mode;
        };
    }

    /**
     * 获取烹饪类型显示名称
     */
    private String getDisplayNameForCookingType(String type) {
        return switch (type) {
            case "smelting" -> "熔炉";
            case "blasting" -> "高炉";
            case "smoking" -> "烟熏炉";
            case "campfire_cooking" -> "营火";
            default -> type;
        };
    }

    /**
     * 获取可用的烹饪类型
     */
    private String[] getCookingTypes() {
        return new String[]{"smelting", "blasting", "smoking", "campfire_cooking"};
    }

    /**
     * 获取可用的等级列表。
     * 根据配方类型注册时的 maxGridWidth 反推最大 Tier，
     * 避免出现超出实际格子上限的无效 Tier。
     */
    private Integer[] getAvailableTiers() {
        if (currentRecipeType != null &&
                (currentRecipeType.isAvaritiaType() ||
                        Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class)))) {

            // 用已注册的最大格子数推算上限，例如：
            //   avaritia      9x9  → T4
            //   ArcaneVortex  16x16 → T6
            int maxGrid = currentRecipeType.getMaxGridWidth();
            int maxTier = DynamicRecipeBuilder.getMaxTierForGridSize(maxGrid);

            Integer[] tiers = new Integer[maxTier];
            for (int i = 0; i < maxTier; i++) {
                tiers[i] = i + 1;
            }
            return tiers;
        }
        return new Integer[]{1};
    }

    /**
     * 初始化右侧面板
     */
    private void initializeRightPanel() {
        int rightPanelX = leftPos + contentWidth - 150 + 10;
        int rightPanelStartY = topPos + 130;

        String defaultTime = getDefaultTimeForCurrentType();
        String defaultExp = getDefaultExpForCurrentType();

        cookingTimeBox = new EditBox(this.font, rightPanelX + 60, rightPanelStartY + 30, 60, 20,
                Component.literal("烹饪时间"));
        cookingTimeBox.setValue(defaultTime);
        cookingTimeBox.setFilter(text -> text.matches("\\d*") &&
                (text.isEmpty() || Integer.parseInt(text) <= 32000));
        addRenderableWidget(cookingTimeBox);

        cookingExpBox = new EditBox(this.font, rightPanelX + 60, rightPanelStartY + 60, 60, 20,
                Component.literal("烹饪经验"));
        cookingExpBox.setValue(defaultExp);
        cookingExpBox.setFilter(text -> text.matches("\\d*\\.?\\d*"));
        addRenderableWidget(cookingExpBox);

        clearAllButton = addRenderableWidget(Button.builder(
                        Component.literal("清空材料"),
                        button -> clearAllIngredients())
                .bounds(rightPanelX, rightPanelStartY + 120, 80, 20)
                .build());
        
        btnEditSpecialProperties = addRenderableWidget(Button.builder(
                        Component.literal("编辑特殊配方属性"),
                        button -> onEditSpecialPropertiesClicked())
                .bounds(rightPanelX - 64 - 120, rightPanelStartY - 20, 120, 20)
                .build());
        
        updateSpecialPropertiesButtonVisibility();

    }

    /**
     * 获取当前类型的默认时间
     */
    private String getDefaultTimeForCurrentType() {
        if (currentRecipeType != null && currentRecipeType.supportsCookingSettings()) {
            String defaultTime = currentRecipeType.getProperty("defaultTime", String.class);
            if (defaultTime != null) {
                return defaultTime;
            }
        }
        return switch (currentCookingType) {
            case "blasting", "smoking" -> "100";
            case "campfire_cooking" -> "600";
            default -> "200";
        };
    }

    /**
     * 获取当前类型的默认经验
     */
    private String getDefaultExpForCurrentType() {
        if (currentRecipeType != null && currentRecipeType.supportsCookingSettings()) {
            String defaultExp = currentRecipeType.getProperty("defaultExp", String.class);
            if (defaultExp != null) {
                return defaultExp;
            }
        }
        return switch (currentCookingType) {
            case "smoking", "campfire_cooking" -> "0.35";
            default -> "0.7";
        };
    }

    /**
     * 初始化底部按钮（自适应单行/双行）
     */
    private void initializeBottomButtons() {
        int sp      = 6;
        int btnH    = 20;
        int btnY    = topPos + contentHeight - 28;
        int centerX = leftPos + contentWidth / 2;

        // ── 主行按钮（4个 + 2个操作）共 6 个，总宽约 462 ─────────────
        // [⚙管理▾=68] [编辑配方=72] [配方克隆=72] [sp*3] [创建/更新=84] [取消=52]
        int totalW = menuBtnW + 72 + 72 + sp*4 + 84 + 52;
        int startX = centerX - totalW / 2;

        // ⚙管理▾（普通按钮，点击切换 menuOpen）
        menuBtnX = startX;
        menuBtnY = btnY;
        addRenderableWidget(Button.builder(
                Component.literal("§7⚙ 管理 ▾"),
                btn -> menuOpen = !menuOpen
        ).bounds(startX, btnY, menuBtnW, btnH).build());
        startX += menuBtnW + sp;

        // 编辑配方
        editExistingRecipeButton = makeBtn("编辑配方", this::openRecipeSelector, startX, btnY, 72);
        startX += 72 + sp;

        // 配方克隆（新）
        makeBtn("配方克隆", this::openCloneWizard, startX, btnY, 72);
        startX += 72 + sp * 2;

        // 创建/更新
        createButton = makeBtn(isEditingExisting ? "更新配方" : "创建配方",
                this::createRecipe, startX, btnY, 84);
        startX += 84 + sp;

        // 取消
        cancelButton = makeBtn("取消", this::onClose, startX, btnY, 52);
    }

    private void openCloneWizard() {
        if (minecraft != null) {
            minecraft.setScreen(new RecipeCloneWizardScreen(this, this::loadSelectedRecipe));
        }
    }

    /** 简化按钮创建 */
    private Button makeBtn(String label, Runnable action, int x, int y, int w) {
        return addRenderableWidget(Button.builder(Component.literal(label), btn -> action.run())
                .bounds(x, y, w, 20).build());
    }

    /**
     * 更新控件可见性
     */
    private void updateVisibility() {
        if (currentRecipeType == null) return;

        // 合成模式按钮
        if (craftingModeButton != null) {
            String category = currentRecipeType.getProperty("category", String.class);
            craftingModeButton.visible = "crafting".equals(category) || "avaritia".equals(category);
        }

        // 烹饪类型按钮
        if (cookingTypeButton != null) {
            cookingTypeButton.visible = currentRecipeType.supportsCookingSettings();
        }

        // 等级按钮
        if (tierButton != null) {
            boolean shouldShowTier = currentRecipeType.isAvaritiaType() ||
                    Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class));

            tierButton.visible = shouldShowTier;
            tierButton.active = shouldShowTier;

            if (tierButton.visible) {
                tierButton.setValue(customTier);
            }
        }

        // 填充模式按钮
        if (fillModeButton != null) {
            fillModeButton.visible = currentRecipeType.supportsFillMode();
        }

        // 画笔选择按钮
        if (selectBrushItemButton != null) {
            selectBrushItemButton.visible = currentRecipeType.supportsFillMode() &&
                    fillModeHandler.shouldShowBrushSelector();
        }

        // 烹饪相关输入框
        boolean isCookingType = currentRecipeType.supportsCookingSettings();
        if (cookingTimeBox != null) {
            cookingTimeBox.visible = isCookingType;
            cookingTimeBox.setVisible(isCookingType);
            if (isCookingType) {
                cookingTimeBox.setValue(getDefaultTimeForCurrentType());
            }
        }
        if (cookingExpBox != null) {
            cookingExpBox.visible = isCookingType;
            cookingExpBox.setVisible(isCookingType);
            if (isCookingType) {
                cookingExpBox.setValue(getDefaultExpForCurrentType());
            }
        }
        
        updateSpecialPropertiesButtonVisibility();
    }

    /**
     * 打开配方类型选择器
     */
    private void openCustomTagCreator() {
        if (minecraft != null) {
            minecraft.setScreen(new CustomTagCreatorScreen(this, (tagId, itemStacks) -> {
                if (tagId != null && !itemStacks.isEmpty()) {
                    CustomTagManager.registerTag(tagId, itemStacks);
                    displaySuccess("自定义标签已创建: #" + tagId);
                }
            }));
        }
    }

    private void openRecipeTypeSelector() {
        if (minecraft != null) {
            List<RecipeTypeDefinition> availableTypes = DynamicRecipeTypeConfig.getAvailableDisplayRecipeTypes();
            if (availableTypes.isEmpty()) {
                displayError("没有可用的配方类型");
                return;
            }
            minecraft.setScreen(new RecipeTypeSelectorScreen(this, this::onRecipeTypeSelected,
                    availableTypes, currentRecipeType));
        }
    }

    /**
     * 配方类型选择回调
     */
    private void onRecipeTypeSelected(RecipeTypeDefinition newType) {
        if (newType != currentRecipeType) {
            onRecipeTypeChanged(newType);
        }
    }

    private void onCraftingModeChanged(CycleButton<String> button, String newMode) {
        this.currentCraftingMode = newMode;
    }

    private void onCookingTypeChanged(CycleButton<String> button, String newType) {
        this.currentCookingType = newType;
        if (cookingTimeBox != null) {
            cookingTimeBox.setValue(getDefaultTimeForCurrentType());
        }
        if (cookingExpBox != null) {
            cookingExpBox.setValue(getDefaultExpForCurrentType());
        }
    }

    private void onTierChanged(CycleButton<Integer> button, Integer newTier) {
        if (this.customTier != newTier) {
            this.customTier = newTier;
            if (currentRecipeType != null &&
                    (currentRecipeType.isAvaritiaType() ||
                            Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class)))) {
                updateSlotManagerRecipeType();
                calculateDynamicSize();
            }
        }
    }
    
    private void cycleRotaryMode() {
        clearAllSelections();
        
        currentRotaryMode = switch (currentRotaryMode) {
            case "reversible" -> "decondensation";
            case "decondensation" -> "condensation";
            case "condensation" -> "reversible";
            default -> "reversible";
        };
        
        String modeName = switch (currentRotaryMode) {
            case "reversible" -> "可逆模式";
            case "decondensation" -> "液体蒸发";
            case "condensation" -> "气体冷凝";
            default -> "可逆模式";
        };
        
        if (rotaryModeButton != null) {
            rotaryModeButton.setMessage(Component.literal(modeName));
        }
        
        LayoutManager.setRotaryCondensentratorMode(currentRotaryMode);
        updateSlotManagerRecipeType();
        calculateDynamicSize();
        initializeComponentRenderers();
    }

    private void onFillModeChanged(CycleButton<FillMode> button, FillMode newMode) {
        fillModeHandler.setCurrentMode(newMode);
        updateVisibility();
    }

    private void openItemSelectorForSlot(int slotIndex) {
        if (minecraft != null) {
            // 打开材料类型选择器
            minecraft.setScreen(new IngredientTypeSelector(this, slotIndex, selectionType -> {
                handleIngredientTypeSelection(slotIndex, selectionType);
            }));
        }
    }

    private void openBrushSelector() {
        if (minecraft != null) {
            minecraft.setScreen(new IngredientTypeSelector(this, -1, this::handleBrushSelection));
        }
    }

    private void handleBrushSelection(IngredientTypeSelector.SelectionType type) {
        if (minecraft == null) return;

        switch (type) {
            case ALL_ITEMS -> minecraft.setScreen(new ItemSelectorScreen(this, item -> {
                fillModeHandler.setBrushItem(item);
                displayInfo("已设置画笔物品: " + item.getHoverName().getString());
            }));
            case INVENTORY -> minecraft.setScreen(new InventoryItemSelectorScreen(this, item -> {
                fillModeHandler.setBrushItem(item);
                displayInfo("已设置画笔物品: " + item.getHoverName().getString());
            }));
            case TAG, CUSTOM_TAG -> {
                displayError("画笔模式不支持标签");
                minecraft.setScreen(this);
            }
        }
    }

    private void openResultSelector() {
        if (minecraft != null) {
            minecraft.setScreen(new ItemSelectorScreen(this, item -> {
                slotManager.setResultItem(item);
                if (componentRenderManager != null) {
                    componentRenderManager.updateResultItem(item);
                }
            }));
        }
    }
    
    private void onOutputSlotSelected(int slotIndex) {
        clearAllSelections();
        
        if (slotManager != null) {
            List<RecipeComponent> outputComponents = slotManager.getOutputComponents();
            for (RecipeComponent comp : outputComponents) {
                if (comp instanceof SlotComponent slotComp && slotComp.getSlotIndex() == slotIndex) {
                    selectedOutputSlot = slotComp;
                    break;
                }
            }
        }
        
        updateSlotOperationButtonsVisibility();
    }
    
    private void openOutputItemSelector(int slotIndex) {
        if (minecraft != null) {
            minecraft.setScreen(new ItemSelectorScreen(this, item -> {
                if (componentRenderManager != null) {
                    componentRenderManager.updateSlotItem(slotIndex, item);
                }
            }));
        }
    }
    
    private void clearIngredientSlot(int slotIndex) {
        // 将 SlotComponent 的 slotIndex 转换为 ingredients 列表的索引
        int listIndex = slotManager.findIngredientListIndex(slotIndex);
        if (listIndex >= 0) {
            slotManager.clearSlot(listIndex);
            if (componentRenderManager != null) {
                componentRenderManager.updateSlotItem(slotIndex, ItemStack.EMPTY);
            }
        }
    }
    
    private void clearOutputSlot(int slotIndex) {
        if (componentRenderManager != null) {
            componentRenderManager.updateSlotItem(slotIndex, ItemStack.EMPTY);
        }
    }
    
    private void onFluidSlotClicked(FluidSlotComponent fluidSlot) {
        clearAllSelections();
        selectedFluidSlot = fluidSlot;
        selectedOutputComponent = fluidSlot;
        updateSlotOperationButtonsVisibility();
    }
    
    private void onGasSlotClicked(GasSlotComponent gasSlot) {
        clearAllSelections();
        
        if (gasSlot.getId().toLowerCase().contains("output")) {
            selectedOutputComponent = gasSlot;
            selectedGasSlot = gasSlot;
        } else {
            selectedGasSlot = gasSlot;
            selectedOutputComponent = gasSlot;
        }
        
        updateSlotOperationButtonsVisibility();
    }
    
    private void onChemicalSlotClicked(ChemicalSlotComponent chemicalSlot) {
        clearAllSelections();
        
        if (chemicalSlot.getId().toLowerCase().contains("output")) {
            selectedOutputComponent = chemicalSlot;
            selectedChemicalSlot = chemicalSlot;
        } else {
            selectedChemicalSlot = chemicalSlot;
            selectedOutputComponent = chemicalSlot;
        }
        
        updateSlotOperationButtonsVisibility();
    }
    
    public void clearAllSelections() {
        selectedSlotIndex = -1;
        isResultSlotSelected = false;
        selectedFluidSlot = null;
        selectedGasSlot = null;
        selectedChemicalSlot = null;
        selectedOutputComponent = null;
        selectedOutputSlot = null;
        updateSlotOperationButtonsVisibility();
    }

    private void clearAllIngredients() {
        slotManager.clearAllIngredients();
        fillModeHandler.reset();
        editingRecipeId = null;
        isEditingExisting = false;
        createButton.setMessage(Component.literal("创建配方"));
        
        if (componentRenderManager != null && slotManager != null) {
            componentRenderManager.clear();
            List<RecipeComponent> components = slotManager.getComponents();
            if (!components.isEmpty()) {
                componentRenderManager.initializeRenderers(components);
            }
            
            List<RecipeComponent> outputComponents = slotManager.getOutputComponents();
            for (RecipeComponent comp : outputComponents) {
                if (comp instanceof SlotComponent slotComp) {
                    componentRenderManager.updateSlotItem(slotComp.getSlotIndex(), ItemStack.EMPTY);
                    slotManager.setOutputSlotItem(slotComp.getSlotIndex(), ItemStack.EMPTY);
                } else if (comp instanceof GasSlotComponent gasComp) {
                    gasComp.setGasId("");
                    gasComp.setAmount(0);
                } else if (comp instanceof ChemicalSlotComponent chemicalComp) {
                    chemicalComp.setChemicalId("");
                    chemicalComp.setAmount(0);
                } else if (comp instanceof FluidSlotComponent fluidComp) {
                    fluidComp.setFluidId("");
                    fluidComp.setAmount(0);
                }
            }
        }
    }

    private void openRecipeSelector() {
        if (minecraft != null) {
            // 检查是否为远程服务器且缓存为空
            if (RecipeLoader.isRemoteServer()) {
                if (!com.wzz.registerhelper.network.RecipeClientCache.isLoaded()) {
                    displayInfo("正在从服务器加载配方列表，请稍候...");
                    recipeLoader.requestServerRecipes();
                    // 添加回调，数据加载完成后重新打开选择器
                    com.wzz.registerhelper.network.RecipeClientCache.addLoadCallback(recipes -> {
                        if (minecraft != null) {
                            minecraft.execute(() -> {
                                if (!recipes.isEmpty()) {
                                    List<UnifiedRecipeInfo> editableRecipes = new ArrayList<>();
                                    for (UnifiedRecipeInfo info : recipes) {
                                        if (!info.isBlacklisted) {
                                            editableRecipes.add(info);
                                        }
                                    }
                                    if (!editableRecipes.isEmpty()) {
                                        minecraft.setScreen(new RecipeSelectorScreen(this, this::loadSelectedRecipe,
                                                editableRecipes, "选择要编辑的配方"));
                                    } else {
                                        displayError("没有找到可编辑的配方");
                                    }
                                } else {
                                    displayError("没有找到可编辑的配方");
                                }
                            });
                        }
                    });
                    return;
                }
            }

            List<UnifiedRecipeInfo> editableRecipes = recipeLoader.getEditableRecipes();
            if (editableRecipes.isEmpty()) {
                displayError("没有找到可编辑的配方");
                return;
            }
            minecraft.setScreen(new RecipeSelectorScreen(this, this::loadSelectedRecipe,
                    editableRecipes, "选择要编辑的配方"));
        }
    }

    private void openAddBlacklist() {
        if (minecraft != null) {
            if (RecipeLoader.isRemoteServer()) {
                if (!com.wzz.registerhelper.network.RecipeClientCache.isLoaded()) {
                    displayInfo("正在从服务器加载配方列表，请稍候...");
                    recipeLoader.requestServerRecipes();
                    com.wzz.registerhelper.network.RecipeClientCache.addLoadCallback(recipes -> {
                        if (minecraft != null) {
                            minecraft.execute(() -> {
                                if (!recipes.isEmpty()) {
                                    minecraft.setScreen(new RecipeSelectorScreen(
                                            this, this::handleRecipeOperation,
                                            new ArrayList<>(recipes), "添加/移除黑名单"));
                                } else {
                                    displayError("没有找到任何配方");
                                }
                            });
                        }
                    });
                    return;
                }
            }
            List<UnifiedRecipeInfo> allRecipes = recipeLoader.getAllRecipes();
            if (allRecipes.isEmpty()) { displayError("没有找到任何配方"); return; }
            minecraft.setScreen(new RecipeSelectorScreen(
                    this, this::handleRecipeOperation, allRecipes, "添加/移除黑名单"));
        }
    }

    /**
     * 处理材料类型选择
     */
    private void handleIngredientTypeSelection(int slotIndex, IngredientTypeSelector.SelectionType type) {
        if (minecraft == null) return;
        
        // 将 SlotComponent 的 slotIndex 转换为 ingredients 列表的索引
        int listIndex = slotManager.findIngredientListIndex(slotIndex);
        if (listIndex < 0) {
            displayError("无法找到对应的槽位索引");
            return;
        }

        switch (type) {
            case ALL_ITEMS -> {
                // 从所有物品选择（原有功能）
                minecraft.setScreen(new ItemSelectorScreen(this, item -> {
                    IngredientData data = IngredientData.fromItem(item);
                    slotManager.setIngredientData(listIndex, data);
                    // 同步到渲染器 - 使用 SlotComponent 的 slotIndex 作为 key
                    if (componentRenderManager != null) {
                        componentRenderManager.updateSlotItem(slotIndex, item);
                    }
                }));
            }
            case INVENTORY -> {
                // 从背包选择（带NBT）
                minecraft.setScreen(new InventoryItemSelectorScreen(this, item -> {
                    IngredientData data = IngredientData.fromItem(item);
                    slotManager.setIngredientData(listIndex, data);
                    // 同步到渲染器 - 使用 SlotComponent 的 slotIndex 作为 key
                    if (componentRenderManager != null) {
                        componentRenderManager.updateSlotItem(slotIndex, item);
                    }
                }));
            }
            case TAG -> {
                // 选择标签
                minecraft.setScreen(new TagSelectorScreen(this, tagId -> {
                    IngredientData data = IngredientData.fromTag(tagId);
                    slotManager.setIngredientData(listIndex, data);
                    // 同步到渲染器（使用标签的第一个物品显示）- 使用 SlotComponent 的 slotIndex 作为 key
                    if (componentRenderManager != null) {
                        componentRenderManager.updateSlotItem(slotIndex, data.getDisplayStack());
                    }
                    displayInfo("已添加标签: #" + tagId);
                }));
            }
            case CUSTOM_TAG -> {
                // 创建自定义标签
                minecraft.setScreen(new CustomTagCreatorScreen(this, (tagId, items) -> {
                    CustomTagManager.registerTag(tagId, items);
                    IngredientData data = IngredientData.fromCustomTag(tagId, items);
                    slotManager.setIngredientData(listIndex, data);
                    // 同步到渲染器（使用第一个物品显示）- 使用 SlotComponent 的 slotIndex 作为 key
                    if (componentRenderManager != null) {
                        componentRenderManager.updateSlotItem(slotIndex, data.getDisplayStack());
                    }
                    displayInfo("已添加自定义标签: #" + tagId + " (包含 " + items.size() + " 个物品)");
                }));
            }
        }
    }

    private void openBlacklistManager() {
        if (minecraft != null) {
            minecraft.setScreen(new BlacklistManagerScreen(this));
        }
    }

    private void openOverrideManager() {
        if (minecraft != null) {
            minecraft.setScreen(new OverrideManagerScreen(this));
        }
    }

    private void handleRecipeOperation(ResourceLocation recipeId) {
        UnifiedRecipeInfo info = recipeLoader.findRecipeInfo(recipeId);
        if (info == null) {
            displayError("找不到配方信息: " + recipeId);
            return;
        }

        try {
            boolean success = false;
            String resultMessage = "";

            if (info.isBlacklisted) {
                // 使用网络包辅助类
                success = BlacklistClientHelper.removeFromBlacklist(recipeId);
                resultMessage = success ? "正在恢复配方" : "恢复配方失败";
            } else if (info.hasOverride) {
                success = UnifiedRecipeOverrideManager.removeOverride(recipeId);
                resultMessage = success ? "覆盖已移除" : "移除覆盖失败";
            } else {
                // 使用网络包辅助类
                success = BlacklistClientHelper.addToBlacklist(recipeId);
                resultMessage = success ? "正在禁用配方" : "禁用配方失败";
            }

            if (success) {
                displaySuccess(resultMessage + ": " + recipeId + " 使用 /reload 刷新配方");

                if (editingRecipeId != null && editingRecipeId.equals(recipeId)) {
                    if (info.isBlacklisted) {
                        clearAllIngredients();
                        displayInfo("配方已被禁用，已退出编辑模式");
                    } else if (info.hasOverride) {
                        displayInfo("覆盖已移除，当前编辑的是原始配方");
                    }
                }
            } else {
                displayError(resultMessage + ": " + recipeId);
            }

        } catch (Exception e) {
            displayError("操作配方时发生错误: " + e.getMessage());
        }
    }

    private void createRecipe() {
        if (currentRecipeType == null) {
            displayError("请选择配方类型！");
            return;
        }

        String recipeTypeId = currentRecipeType.getId();
        boolean isPressurizedReaction = "mekanism:reaction".equals(recipeTypeId);
        
        boolean hasItemInput = false;
        List<RecipeComponent> components = slotManager.getComponents();
        for (RecipeComponent component : components) {
            if (component instanceof SlotComponent slotComp) {
                if (slotComp.getId().toLowerCase().contains("input")) {
                    hasItemInput = true;
                    break;
                }
            }
        }
        
        if (hasItemInput) {
            List<ItemStack> ingredients = slotManager.getIngredients();
            for (int i = 0; i < ingredients.size(); i++) {
                ItemStack item = ingredients.get(i);
                if (item.isEmpty()) {
                    displayError("请设置物品输入！");
                    return;
                }
            }
        }
        
        for (RecipeComponent component : components) {
            if (component instanceof FluidSlotComponent fluidComp) {
                if (!isPressurizedReaction || !fluidComp.getId().contains("input")) {
                    if (fluidComp.getAmount() <= 0 || fluidComp.getFluidId() == null || fluidComp.getFluidId().isEmpty()) {
                        displayError("请选择流体输入并设置数量！");
                        return;
                    }
                }
            } else if (component instanceof GasSlotComponent gasComp) {
                if (gasComp.getId().contains("input")) {
                    if (gasComp.getAmount() <= 0 || gasComp.getGasId() == null || gasComp.getGasId().isEmpty()) {
                        displayError("请选择气体输入并设置数量！");
                        return;
                    }
                }
            } else if (component instanceof ChemicalSlotComponent chemicalComp) {
                if (chemicalComp.getId().contains("input")) {
                    if (chemicalComp.getAmount() <= 0 || chemicalComp.getChemicalId() == null || chemicalComp.getChemicalId().isEmpty()) {
                        displayError("请选择化学物质输入并设置数量！");
                        return;
                    }
                }
            }
        }

        List<RecipeComponent> outputComponents = slotManager.getOutputComponents();
        if (!outputComponents.isEmpty()) {
            if (isPressurizedReaction) {
                boolean hasValidOutput = false;
                for (RecipeComponent outputComp : outputComponents) {
                    if (outputComp instanceof SlotComponent slotComp) {
                        ItemStack item = slotManager.getOutputSlotItem(slotComp.getSlotIndex());
                        if (!item.isEmpty()) {
                            hasValidOutput = true;
                            break;
                        }
                    } else if (outputComp instanceof GasSlotComponent gasComp) {
                        if (gasComp.getAmount() > 0 && gasComp.getGasId() != null && !gasComp.getGasId().isEmpty()) {
                            hasValidOutput = true;
                            break;
                        }
                    }
                }
                if (!hasValidOutput) {
                    displayError("加压反应室至少需要一个输出（物品或气体）！");
                    return;
                }
            } else {
                for (RecipeComponent outputComp : outputComponents) {
                    if (outputComp instanceof EnergySlotComponent energyComp) {
                        if (energyComp.getEnergy() <= 0) {
                            displayError("请设置能量值！");
                            return;
                        }
                    } else if (outputComp instanceof FluidSlotComponent fluidComp) {
                        if (fluidComp.getAmount() <= 0 || fluidComp.getFluidId() == null || fluidComp.getFluidId().isEmpty()) {
                            displayError("请选择流体并设置数量！");
                            return;
                        }
                    } else if (outputComp instanceof GasSlotComponent gasComp) {
                        if (gasComp.getAmount() <= 0 || gasComp.getGasId() == null || gasComp.getGasId().isEmpty()) {
                            displayError("请选择气体并设置数量！");
                            return;
                        }
                    } else if (outputComp instanceof ChemicalSlotComponent chemicalComp) {
                        if (chemicalComp.getAmount() <= 0 || chemicalComp.getChemicalId() == null || chemicalComp.getChemicalId().isEmpty()) {
                            displayError("请选择化学物质并设置数量！");
                            return;
                        }
                    } else if (outputComp instanceof SlotComponent slotComp) {
                        ItemStack item = slotManager.getOutputSlotItem(slotComp.getSlotIndex());
                        if (item.isEmpty()) {
                            String label = slotComp.hasLabel() ? slotComp.getLabel() : "输出";
                            displayError("请设置" + label + "物品！");
                            return;
                        }
                    }
                }
            }
        } else {
            RecipeComponent outputComponent = slotManager.getOutputComponent();
            if (outputComponent != null) {
                if (outputComponent instanceof EnergySlotComponent energyComp) {
                    if (energyComp.getEnergy() <= 0) {
                        displayError("请设置能量值！");
                        return;
                    }
                } else if (outputComponent instanceof FluidSlotComponent fluidComp) {
                    if (fluidComp.getAmount() <= 0 || fluidComp.getFluidId() == null || fluidComp.getFluidId().isEmpty()) {
                        displayError("请选择流体并设置数量！");
                        return;
                    }
                } else if (outputComponent instanceof GasSlotComponent gasComp) {
                    if (gasComp.getAmount() <= 0 || gasComp.getGasId() == null || gasComp.getGasId().isEmpty()) {
                        displayError("请选择气体并设置数量！");
                        return;
                    }
                }
            } else {
                ItemStack resultItem = slotManager.getResultItem();
                if (resultItem.isEmpty()) {
                    displayError("请选择结果物品！");
                    return;
                }
            }
        }

        ItemStack resultItem = slotManager.getResultItem();
        resultItem = resultItem.isEmpty() ? ItemStack.EMPTY : resultItem.copy();

        try {
            float cookingTime = currentRecipeType.supportsCookingSettings() ?
                    Float.parseFloat(cookingTimeBox.getValue()) : 0;
            float cookingExp = currentRecipeType.supportsCookingSettings() ?
                    Float.parseFloat(cookingExpBox.getValue()) : 0;

            DynamicRecipeBuilder.BuildParams params = getBuildParams(resultItem, cookingTime, cookingExp);
            DynamicRecipeBuilder dynamicBuilder = new DynamicRecipeBuilder(this::displaySuccess, this::displayError);
            dynamicBuilder.buildRecipe(params);
        } catch (NumberFormatException e) {
            displayError("请输入有效的时间或经验值！");
        }
    }

    private DynamicRecipeBuilder.BuildParams getBuildParams(ItemStack resultItem, float cookingTime, float cookingExp) {
        Map<String, Object> componentData = new HashMap<>();
        if (componentRenderManager != null) {
            componentData = componentRenderManager.getDataManager().getAllData();
        }
        
        if (slotManager != null) {
            for (RecipeComponent component : slotManager.getComponents()) {
                if (component instanceof FluidSlotComponent fluidComp) {
                    String id = fluidComp.getId();
                    String fluidId = fluidComp.getFluidId();
                    if (fluidId != null && !fluidId.isEmpty()) {
                        if (id.contains("input")) {
                            componentData.put("fluidInput", fluidId);
                            componentData.put("fluidInputAmount", (int) fluidComp.getAmount());
                        } else if (id.contains("output")) {
                            componentData.put("fluidOutput", fluidId);
                            componentData.put("fluidOutputAmount", (int) fluidComp.getAmount());
                        }
                    }
                } else if (component instanceof GasSlotComponent gasComp) {
                    String id = gasComp.getId();
                    String gasId = gasComp.getGasId();
                    if (gasId != null && !gasId.isEmpty()) {
                        if (id.contains("input")) {
                            componentData.put("gasInput", gasId);
                            componentData.put("gasInputAmount", (int) gasComp.getAmount());
                        } else if (id.contains("output")) {
                            componentData.put("gasOutput", gasId);
                            componentData.put("gasOutputAmount", (int) gasComp.getAmount());
                        }
                    }
                } else if (component instanceof ChemicalSlotComponent chemicalComp) {
                    String id = chemicalComp.getId();
                    String chemicalId = chemicalComp.getChemicalId();
                    if (chemicalId != null && !chemicalId.isEmpty()) {
                        if (id.contains("input")) {
                            componentData.put("inputGas", chemicalId);
                            componentData.put("inputAmount", (int) chemicalComp.getAmount());
                            componentData.put("chemicalType", chemicalComp.getChemicalType().getId());
                        }
                    }
                }
            }
        }
        
        RecipeComponent outputComp = slotManager.getOutputComponent();
        if (outputComp instanceof FluidSlotComponent fluidComp) {
            String fluidId = fluidComp.getFluidId();
            if (fluidId != null && !fluidId.isEmpty()) {
                componentData.put("fluidOutput", fluidId);
                componentData.put("fluidOutputAmount", (int) fluidComp.getAmount());
            }
        } else if (outputComp instanceof GasSlotComponent gasComp) {
            String gasId = gasComp.getGasId();
            if (gasId != null && !gasId.isEmpty()) {
                componentData.put("gasOutput", gasId);
                componentData.put("gasOutputAmount", (int) gasComp.getAmount());
            }
        }
        
        List<RecipeComponent> outputComps = slotManager.getOutputComponents();
        if (!outputComps.isEmpty()) {
            List<ItemStack> outputItems = new ArrayList<>();
            List<Double> outputProbabilities = new ArrayList<>();
            
            for (RecipeComponent comp : outputComps) {
                if (comp instanceof SlotComponent slotComp) {
                    ItemStack item = slotManager.getOutputSlotItem(slotComp.getSlotIndex());
                    outputItems.add(item);
                    outputProbabilities.add(slotComp.getProbability());
                } else if (comp instanceof GasSlotComponent gasComp) {
                    String gasId = gasComp.getGasId();
                    if (gasId != null && !gasId.isEmpty()) {
                        if (gasComp.getId().contains("output")) {
                            componentData.put("gasOutput", gasId);
                            componentData.put("gasOutputAmount", (int) gasComp.getAmount());
                        }
                    }
                } else if (comp instanceof ChemicalSlotComponent chemicalComp) {
                    String chemicalId = chemicalComp.getChemicalId();
                    if (chemicalId != null && !chemicalId.isEmpty()) {
                        if (chemicalComp.getId().contains("output")) {
                            componentData.put("chemicalOutput", chemicalId);
                            componentData.put("chemicalOutputAmount", (int) chemicalComp.getAmount());
                            componentData.put("chemicalType", chemicalComp.getChemicalType().getId());
                        }
                    }
                }
            }
            
            if (!outputItems.isEmpty()) {
                componentData.put("outputItems", outputItems);
                componentData.put("outputProbabilities", outputProbabilities);
            }
        }
        
        componentData.put("rotaryMode", currentRotaryMode);
        
        componentData.putAll(specialProperties);

        List<IngredientData> ingredientsData = slotManager.getIngredientsData();
        // NBT控制已改为 per-slot，由各槽位 IngredientData.isIncludeNBT() 决定
        List<ItemStack> ingredients = slotManager.getIngredients();
        DynamicRecipeBuilder.BuildParams buildParams = new DynamicRecipeBuilder.BuildParams(
                currentRecipeType,
                currentCraftingMode,
                currentCookingType,
                customTier,
                resultItem,
                ingredients,
                ingredientsData,
                cookingTime,
                cookingExp,
                editingRecipeId,
                isEditingExisting,
                componentData
        );
        buildParams.componentDataManager = componentRenderManager.getDataManager();
        buildParams.outputComponent = slotManager.getOutputComponent();
        buildParams.rotaryMode = currentRotaryMode;
        buildParams.outputSlotItems = slotManager.getOutputSlotItems();
        return buildParams;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        // ── 外框 ──
        guiGraphics.fill(leftPos - 1, topPos - 1, leftPos + contentWidth + 1, topPos + contentHeight + 1, 0xFF0A0A0A);
        // ── 主背景 ──
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + contentHeight, 0xFF252525);

        // ── 标题栏 ──
        int titleBarH = 30;
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + titleBarH, 0xFF1A3A6A);
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + 1, 0xFF4A7ACF);          // 顶部高亮线
        guiGraphics.fill(leftPos, topPos + titleBarH - 1, leftPos + contentWidth, topPos + titleBarH, 0xFF223B80); // 底部线

        // ── 控件区背景 ──
        guiGraphics.fill(leftPos + 1, topPos + titleBarH, leftPos + contentWidth - 1, topPos + 98, 0xFF202020);
        guiGraphics.fill(leftPos + 5, topPos + 98, leftPos + contentWidth - 5, topPos + 99, 0xFF333333);

        // ── 主内容区背景 ──
        guiGraphics.fill(leftPos + 1, topPos + 99, leftPos + contentWidth - 1, topPos + contentHeight - 35, 0xFF1E1E1E);

        // ── 底部按钮区背景 ──
        guiGraphics.fill(leftPos + 1, topPos + contentHeight - 35, leftPos + contentWidth - 1, topPos + contentHeight - 1, 0xFF202020);
        guiGraphics.fill(leftPos + 5, topPos + contentHeight - 36, leftPos + contentWidth - 5, topPos + contentHeight - 35, 0xFF333333);

        // ── 右侧面板区域分隔线 ──
        int rightPanelX = leftPos + contentWidth - 150 + 10;
        guiGraphics.fill(rightPanelX - 18, topPos + titleBarH + 2, rightPanelX - 16, topPos + contentHeight - 36, 0xFF333333);
        guiGraphics.fill(rightPanelX - 17, topPos + titleBarH + 2, rightPanelX - 15, topPos + contentHeight - 36, 0xFF444444);

        // ── 标题文字 ──
        String titleText = isEditingExisting ?
                (editingRecipeId != null ? "§e配方编辑器  §7- §f" + editingRecipeId : "§e配方编辑器")
                : "§b配方创建器";
        guiGraphics.drawCenteredString(this.font, titleText, leftPos + contentWidth / 2, topPos + 11, 0xFFFFFF);

        renderLabels(guiGraphics);

        // 使用组件渲染器渲染
        if (componentRenderManager != null && !slotManager.getComponents().isEmpty()) {
            componentRenderManager.renderAll(guiGraphics, mouseX, mouseY);
            // 如果使用组件渲染器，还需要手动渲染结果槽
            renderResultSlot(guiGraphics, mouseX, mouseY);
        } else {
            renderSlots(guiGraphics, mouseX, mouseY);
        }
        
        // 绘制选中框
        renderSelectionBox(guiGraphics);

        if (currentRecipeType != null && currentRecipeType.supportsFillMode()) {
            renderFillModeHint(guiGraphics);
        }
        if (menuOpen) {
            int mx2 = menuBtnX;
            int my2 = menuBtnY - MENU_ITEM_H * MENU_LABELS.length - 2; // 向上弹出

            // 浮层背景
            guiGraphics.fill(mx2 - 1, my2 - 1,
                    mx2 + menuBtnW + 1, menuBtnY,
                    0xFF080808);
            guiGraphics.fill(mx2, my2,
                    mx2 + menuBtnW, menuBtnY - 1,
                    0xFF2A2A3A);

            for (int i = 0; i < MENU_LABELS.length; i++) {
                int iy = my2 + i * MENU_ITEM_H;
                boolean hov = mouseX >= mx2 && mouseX < mx2 + menuBtnW
                        && mouseY >= iy && mouseY < iy + MENU_ITEM_H;
                if (hov) guiGraphics.fill(mx2, iy, mx2 + menuBtnW, iy + MENU_ITEM_H, 0xFF3A3A5A);
                // 左侧色条（黑名单=红, 覆盖=紫, 添加黑名单=橙）
                int[] barColors = { 0xFFAA3333, 0xFF9933AA, 0xFFCC7700 };
                guiGraphics.fill(mx2, iy, mx2 + 3, iy + MENU_ITEM_H, barColors[i]);
                guiGraphics.drawString(this.font, MENU_LABELS[i],
                        mx2 + 6, iy + 5, hov ? 0xFFFFFF : 0xCCCCCC, false);
            }
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltips(guiGraphics, mouseX, mouseY);
    }
    
    /**
     * 绘制选中框
     */
    private void renderSelectionBox(GuiGraphics guiGraphics) {
        if (selectedSlotIndex >= 0 && slotManager != null) {
            List<SlotManager.IngredientSlot> slots = slotManager.getIngredientSlots();
            if (selectedSlotIndex < slots.size()) {
                SlotManager.IngredientSlot slot = slots.get(selectedSlotIndex);
                drawSelectionBox(guiGraphics, slot.x(), slot.y(), 18, 18);
            }
        } else if (isResultSlotSelected && slotManager != null) {
            SlotManager.IngredientSlot resultSlot = slotManager.getResultSlot();
            if (resultSlot != null) {
                drawSelectionBox(guiGraphics, resultSlot.x(), resultSlot.y(), 18, 18);
            }
        } else if (selectedFluidSlot != null) {
            drawSelectionBox(guiGraphics, selectedFluidSlot.getX(), selectedFluidSlot.getY(), 16, 58);
        } else if (selectedGasSlot != null) {
            drawSelectionBox(guiGraphics, selectedGasSlot.getX(), selectedGasSlot.getY(), 16, 58);
        } else if (selectedChemicalSlot != null) {
            drawSelectionBox(guiGraphics, selectedChemicalSlot.getX(), selectedChemicalSlot.getY(), 16, 58);
        } else if (selectedOutputSlot != null) {
            drawSelectionBox(guiGraphics, selectedOutputSlot.getX(), selectedOutputSlot.getY(), 18, 18);
        }
    }
    
    /**
     * 绘制选中框（亮框）
     */
    private void drawSelectionBox(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int borderWidth = 2;
        int color = 0xFFFFFF00; // 黄色边框
        
        // 上边
        guiGraphics.fill(x - borderWidth, y - borderWidth, x + width + borderWidth, y, color);
        // 下边
        guiGraphics.fill(x - borderWidth, y + height, x + width + borderWidth, y + height + borderWidth, color);
        // 左边
        guiGraphics.fill(x - borderWidth, y, x, y + height, color);
        // 右边
        guiGraphics.fill(x + width, y, x + width + borderWidth, y + height, color);
    }
    
    private void renderLabels(GuiGraphics guiGraphics) {
        int labelStartX = leftPos + 15;
        int labelY1 = topPos + 25;
        int labelY2 = topPos + 55;

        guiGraphics.drawString(this.font, "§7配方类型:", labelStartX, labelY1, 0xAAAAAA, false);

        if (currentRecipeType != null) {
            String namespaceInfo = "§8" + currentRecipeType.getModId() + "§7:§8" + currentRecipeType.getId();
            guiGraphics.drawString(this.font, namespaceInfo, labelStartX + 100, labelY1 + 64, 0x666666, false);

            String category = currentRecipeType.getProperty("category", String.class);

            if ("crafting".equals(category) || "avaritia".equals(category)) {
                guiGraphics.drawString(this.font, "§7合成模式:", labelStartX + 140, labelY1, 0xAAAAAA, false);
                guiGraphics.drawString(this.font, "§7填充模式:", labelStartX, labelY2, 0xAAAAAA, false);
            } else if (currentRecipeType.supportsCookingSettings()) {
                guiGraphics.drawString(this.font, "§7烹饪类型:", labelStartX + 140, labelY1, 0xAAAAAA, false);
            }

            if (currentRecipeType.isAvaritiaType() || Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class))) {
                guiGraphics.drawString(this.font, "§7等级:", labelStartX + 220, labelY1, 0xAAAAAA, false);
            }
        }

        // 右侧面板标签
        int rightPanelX = leftPos + contentWidth - 150 + 10;
        int rightPanelStartY = topPos + 130;

        guiGraphics.drawString(this.font, "§7结果:", rightPanelX, rightPanelStartY - 20, 0xAAAAAA, false);

        if (currentRecipeType != null && currentRecipeType.supportsCookingSettings()) {
            guiGraphics.drawString(this.font, "§7时间:", rightPanelX, rightPanelStartY + 20, 0xAAAAAA, false);
            guiGraphics.drawString(this.font, "§7经验:", rightPanelX, rightPanelStartY + 50, 0xAAAAAA, false);
        }

        // 底部状态信息
        if (isEditingExisting) {
            guiGraphics.drawString(this.font, "§6编辑模式", labelStartX, topPos + contentHeight - 45, 0xFFCC00, false);
        }
    }

    private void renderResultSlot(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (slotManager == null) return;
        
        List<RecipeComponent> outputComponents = slotManager.getOutputComponents();
        if (!outputComponents.isEmpty() && componentRenderManager != null) {
            for (RecipeComponent outputComp : outputComponents) {
                ComponentRenderer renderer = componentRenderManager.createRenderer(outputComp);
                if (renderer != null) {
                    renderer.render(guiGraphics, this.font, mouseX, mouseY);
                }
            }
        } else {
            RecipeComponent outputComponent = slotManager.getOutputComponent();
            if (outputComponent != null && componentRenderManager != null) {
                ComponentRenderer renderer = componentRenderManager.createRenderer(outputComponent);
                if (renderer != null) {
                    renderer.render(guiGraphics, this.font, mouseX, mouseY);
                }
            } else {
                SlotManager.IngredientSlot resultSlot = slotManager.getResultSlot();
                if (resultSlot != null) {
                    renderSlot(guiGraphics, resultSlot, mouseX, mouseY, slotManager.getResultItem());
                }
            }
        }
    }

    private void renderSlots(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 渲染材料槽位
        for (int i = 0; i < slotManager.getIngredientSlots().size(); i++) {
            SlotManager.IngredientSlot slot = slotManager.getIngredientSlots().get(i);
            IngredientData data = slotManager.getIngredientData(i);
            ItemStack displayItem = data != null ? data.getDisplayStack() : ItemStack.EMPTY;
            renderSlot(guiGraphics, slot, mouseX, mouseY, displayItem);
        }

        // 渲染结果槽位
        SlotManager.IngredientSlot resultSlot = slotManager.getResultSlot();
        if (resultSlot != null) {
            renderSlot(guiGraphics, resultSlot, mouseX, mouseY, slotManager.getResultItem());
        }
    }

    private void renderSlot(GuiGraphics guiGraphics, SlotManager.IngredientSlot slot,
                            int mouseX, int mouseY, ItemStack displayItem) {
        boolean isMouseOver = mouseX >= slot.x() && mouseX < slot.x() + 18 &&
                mouseY >= slot.y() && mouseY < slot.y() + 18;

        int bgColor = isMouseOver ? 0xFF1D3555 : 0xFF141414;
        guiGraphics.fill(slot.x(), slot.y(), slot.x() + 18, slot.y() + 18, bgColor);

        // 内陷边框（Minecraft 风格：左上暗，右下亮）
        guiGraphics.fill(slot.x(),      slot.y(),      slot.x() + 18, slot.y() + 1,  0xFF080808); // 顶
        guiGraphics.fill(slot.x(),      slot.y(),      slot.x() + 1,  slot.y() + 18, 0xFF080808); // 左
        guiGraphics.fill(slot.x(),      slot.y() + 17, slot.x() + 18, slot.y() + 18, 0xFF3A3A3A); // 底
        guiGraphics.fill(slot.x() + 17, slot.y(),      slot.x() + 18, slot.y() + 18, 0xFF3A3A3A); // 右

        if (isMouseOver) {
            guiGraphics.fill(slot.x() + 1, slot.y() + 1, slot.x() + 17, slot.y() + 17, 0x30AACCFF);
        }

        // 获取对应的IngredientData
        int slotIndex = slot.index();
        IngredientData data = null;

        if (slotIndex >= 0 && slotManager != null) {
            data = slotManager.getIngredientData(slotIndex);
        }

        // 渲染物品或标签图标
        if (data != null && !data.isEmpty()) {
            ItemStack stackToRender = data.getDisplayStack();

            if (!stackToRender.isEmpty()) {
                guiGraphics.renderItem(stackToRender, slot.x() + 1, slot.y() + 1);
                
                boolean isBulkSlot = false;
                if (slotManager != null) {
                    for (RecipeComponent component : slotManager.getComponents()) {
                        if (component instanceof SlotComponent slotComp && slotComp.getSlotIndex() == slotIndex) {
                            isBulkSlot = slotComp.isBulkSlot();
                            break;
                        }
                    }
                }
                
                if (isBulkSlot && stackToRender.getCount() > 1) {
                    guiGraphics.renderItemDecorations(this.font, stackToRender, slot.x() + 1, slot.y() + 1, null);
                }
            }

            // 在右上角显示类型指示器
            switch (data.getType()) {
                case TAG -> {
                    // 标签：金色#标记 + 半透明金色背景
                    guiGraphics.fill(slot.x() + 10, slot.y() + 1, slot.x() + 18, slot.y() + 9, 0x80FFD700);
                    guiGraphics.drawString(this.font, "§6§l#", slot.x() + 11, slot.y() + 1, 0xFFFFFF, true);
                }
                case CUSTOM_TAG -> {
                    // 自定义标签：青色#标记 + 半透明青色背景
                    guiGraphics.fill(slot.x() + 10, slot.y() + 1, slot.x() + 18, slot.y() + 9, 0x8000FFFF);
                    guiGraphics.drawString(this.font, "§b§l#", slot.x() + 11, slot.y() + 1, 0xFFFFFF, true);
                }
                case ITEM -> {
                    if (data.hasNBT()) {
                        int barColor;
                        String label;
                        switch (data.getNbtMode()) {
                            case "partial" -> { barColor = 0xFFFFAA00; label = "§eN"; } // 橙黄=部分匹配
                            case "none"    -> { barColor = 0xFF555555; label = "§8N"; } // 灰=忽略
                            default        -> { barColor = 0xFFCC44FF; label = "§dN"; } // 紫=精确
                        }
                        guiGraphics.fill(slot.x() + 1, slot.y() + 15, slot.x() + 17, slot.y() + 17, barColor);
                        guiGraphics.drawString(this.font, label, slot.x() + 6, slot.y() + 10, 0xFFFFFF, true);
                    }
                }
            }
        } else if (!displayItem.isEmpty()) {
            boolean isBulkSlot = false;
            if (slotManager != null) {
                for (RecipeComponent component : slotManager.getComponents()) {
                    if (component instanceof SlotComponent slotComp && slotComp.getSlotIndex() == slotIndex) {
                        isBulkSlot = slotComp.isBulkSlot();
                        break;
                    }
                }
            }
            
            guiGraphics.renderItem(displayItem, slot.x() + 1, slot.y() + 1);
            
            if (isBulkSlot && displayItem.getCount() > 1) {
                guiGraphics.renderItemDecorations(this.font, displayItem, slot.x() + 1, slot.y() + 1, null);
            }
        }
    }

    private void renderFillModeHint(GuiGraphics guiGraphics) {
        String hint = fillModeHandler.getHintText();
        int hintX = leftPos + 15;
        int hintY = topPos + 125;
        guiGraphics.drawString(this.font, hint, hintX, hintY, 0x666666, false);
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 材料槽位工具提示
        for (int i = 0; i < slotManager.getIngredientSlots().size(); i++) {
            SlotManager.IngredientSlot slot = slotManager.getIngredientSlots().get(i);
            if (mouseX >= slot.x() && mouseX < slot.x() + 18 &&
                    mouseY >= slot.y() && mouseY < slot.y() + 18) {

                IngredientData data = slotManager.getIngredientData(i);

                if (!data.isEmpty()) {
                    List<Component> tooltip = new ArrayList<>();

                    // 根据类型显示不同的工具提示
                    switch (data.getType()) {
                        case ITEM -> {
                            ItemStack stack = data.getItemStack();
                            // 有NBT时附加操作提示
                            if (data.hasNBT()) {
                                List<Component> itemTip = new ArrayList<>();
                                itemTip.add(stack.getHoverName().copy());
                                String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                                itemTip.add(Component.literal("§8" + itemId));
                                itemTip.add(Component.literal(""));
                                switch (data.getNbtMode()) {
                                    case "exact" -> {
                                        itemTip.add(Component.literal("§d■ 精确匹配 NBT"));
                                        itemTip.add(Component.literal("§7中键：切换忽略 NBT"));
                                        itemTip.add(Component.literal("§7Shift+中键：设置忽略 Key（部分匹配）"));
                                    }
                                    case "partial" -> {
                                        itemTip.add(Component.literal("§e■ 部分匹配，忽略: §f" + data.getIgnoreNbtKeys()));
                                        itemTip.add(Component.literal("§7中键：切换忽略 NBT（会清除忽略 Key）"));
                                        itemTip.add(Component.literal("§7Shift+中键：重新编辑忽略 Key"));
                                    }
                                    case "none" -> {
                                        itemTip.add(Component.literal("§8■ 忽略 NBT"));
                                        itemTip.add(Component.literal("§7中键：切换精确匹配"));
                                        itemTip.add(Component.literal("§7Shift+中键：设置忽略 Key（部分匹配）"));
                                    }
                                }
                                itemTip.add(Component.literal("§7左键：修改材料  右键：清除材料"));
                                guiGraphics.renderTooltip(this.font, itemTip, Optional.empty(), mouseX, mouseY);
                                return;
                            } else {
                                guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
                            }
                            return;
                        }
                        case TAG -> {
                            tooltip.add(Component.literal("§6§l[标签材料]"));
                            tooltip.add(Component.literal("§e#" + data.getTagId()));

                            // 显示标签包含的物品示例
                            ItemStack displayItem = data.getDisplayStack();
                            if (!displayItem.isEmpty()) {
                                tooltip.add(Component.literal("§7示例: " + displayItem.getHoverName().getString()));
                            }

                            tooltip.add(Component.literal("§8匹配该标签的所有物品"));
                        }
                        case CUSTOM_TAG -> {
                            tooltip.add(Component.literal("§b§l[自定义标签]"));
                            tooltip.add(Component.literal("§3#" + data.getTagId()));

                            List<ItemStack> items = data.getCustomTagItems();
                            tooltip.add(Component.literal("§7包含 " + items.size() + " 个物品:"));

                            // 显示前3个物品
                            int showCount = Math.min(3, items.size());
                            for (int j = 0; j < showCount; j++) {
                                tooltip.add(Component.literal("  §8• " +
                                        items.get(j).getHoverName().getString()));
                            }

                            if (items.size() > 3) {
                                tooltip.add(Component.literal("  §8... 还有 " +
                                        (items.size() - 3) + " 个物品"));
                            }
                        }
                    }

                    tooltip.add(Component.literal("")); // 空行
                    tooltip.add(Component.literal("§7左键: 修改材料"));
                    if (data.getType() == IngredientData.Type.ITEM && data.hasNBT()) {
                        if (data.isIncludeNBT()) {
                            tooltip.add(Component.literal("§d■ 底部紫色条 = 匹配NBT（中键切换）"));
                        } else {
                            tooltip.add(Component.literal("§8■ 底部灰色条 = 忽略NBT（中键切换）"));
                        }
                        tooltip.add(Component.literal("§7右键: 清空槽位"));
                    } else {
                        tooltip.add(Component.literal("§7右键: 清空槽位"));
                    }

                    guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                } else {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.literal("§7空槽位"));
                    tooltip.add(Component.literal("§8左键选择材料类型"));
                    guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                }
                return;
            }
        }

        // 结果槽位工具提示
        List<RecipeComponent> outputComponents = slotManager.getOutputComponents();
        if (outputComponents.isEmpty()) {
            RecipeComponent outputComponent = slotManager.getOutputComponent();
            if (outputComponent == null) {
                SlotManager.IngredientSlot resultSlot = slotManager.getResultSlot();
                if (resultSlot != null && 
                        mouseX >= resultSlot.x() && mouseX < resultSlot.x() + 18 &&
                        mouseY >= resultSlot.y() && mouseY < resultSlot.y() + 18) {
                    if (!slotManager.getResultItem().isEmpty()) {
                        guiGraphics.renderTooltip(this.font, slotManager.getResultItem(), mouseX, mouseY);
                    } else {
                        guiGraphics.renderTooltip(this.font, Component.literal("点击选择结果物品"), mouseX, mouseY);
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menuOpen) {
            int mx2 = menuBtnX;
            int my2 = menuBtnY - MENU_ITEM_H * MENU_LABELS.length - 2;
            if (mouseX >= mx2 && mouseX < mx2 + menuBtnW
                    && mouseY >= my2 && mouseY < menuBtnY - 1) {
                int idx = (int)(mouseY - my2) / MENU_ITEM_H;
                menuOpen = false;
                switch (idx) {
                    case 0 -> openBlacklistManager();
                    case 1 -> openOverrideManager();
                    case 2 -> openAddBlacklist();       // 原"配方操作"，改名后的方法
                }
                return true;
            }
            // 点击菜单外 → 关闭
            menuOpen = false;
        }
        if (slotManager != null) {
            for (int i = 0; i < slotManager.getIngredientSlots().size(); i++) {
                SlotManager.IngredientSlot slot = slotManager.getIngredientSlots().get(i);
                if (mouseX >= slot.x() && mouseX < slot.x() + 18 &&
                        mouseY >= slot.y() && mouseY < slot.y() + 18) {
                    IngredientData slotData = slotManager.getIngredientData(i);
                    // 中键：切换该槽位 NBT 匹配（仅对带NBT的物品有效）
                    if (button == 2 && slotData.getType() == IngredientData.Type.ITEM
                            && slotData.hasNBT()) {

                        boolean shift = hasShiftDown(); // Screen.hasShiftDown()

                        if (shift) {
                            // Shift+中键：打开独立的 NbtIgnoreEditorScreen
                            if (minecraft != null) {
                                minecraft.setScreen(new NbtIgnoreEditorScreen(
                                        this,          // parent = 当前 RecipeCreatorScreen
                                        slotData,      // 目标 IngredientData（直接引用，修改立即生效）
                                        () -> {        // 确认回调：刷新显示
                                            displayInfo(slotData.getIgnoreNbtKeys().isEmpty()
                                                    ? "§d[NBT] 已切回精确匹配"
                                                    : "§e[NBT] 部分匹配，忽略 " + slotData.getIgnoreNbtKeys().size() + " 个 Key");
                                        }
                                ));
                            }
                            return true;
                        } else {
                            // 普通中键：精确 ↔ 忽略（与原逻辑完全一致）
                            // 若当前是部分匹配，先退出部分匹配再走二档切换
                            if ("partial".equals(slotData.getNbtMode())) {
                                slotData.setIgnoreNbtKeys(List.of());
                            }
                            boolean nowOn = slotData.toggleIncludeNBT();
                            displayInfo(nowOn ? "§d[NBT] 精确匹配 NBT" : "§8[NBT] 忽略 NBT");
                        }
                        return true;
                    }
                    // 右键：清空槽位
                    if (button == 1) {
                        slotManager.clearSlot(i);
                        if (componentRenderManager != null) {
                            IngredientData clearedData = slotManager.getIngredientData(i);
                            componentRenderManager.updateSlotItem(slot.index(), clearedData.getItemStack());
                        }
                        return true;
                    }
                    
                    // 左键：选中槽位（不打开界面）
                    if (button == 0) {
                        clearAllSelections();
                        selectedSlotIndex = i;
                        updateSlotOperationButtonsVisibility();
                        return true;
                    }
                    return true;
                }
            }
        }

        // 处理结果槽点击
        if (slotManager != null) {
            List<RecipeComponent> outputComponents = slotManager.getOutputComponents();
            
            if (!outputComponents.isEmpty()) {
                for (RecipeComponent outputComp : outputComponents) {
                    if (componentRenderManager != null) {
                        ComponentRenderer renderer = componentRenderManager.createRenderer(outputComp);
                        if (renderer != null && renderer.mouseClicked(mouseX, mouseY, button)) {
                            return true;
                        }
                    }
                }
            } else {
                RecipeComponent outputComponent = slotManager.getOutputComponent();
                
                if (outputComponent != null) {
                    if (componentRenderManager != null) {
                        ComponentRenderer renderer = componentRenderManager.createRenderer(outputComponent);
                        if (renderer != null && renderer.mouseClicked(mouseX, mouseY, button)) {
                            return true;
                        }
                    }
                } else {
                    SlotManager.IngredientSlot resultSlot = slotManager.getResultSlot();
                    if (mouseX >= resultSlot.x() && mouseX < resultSlot.x() + 18 &&
                            mouseY >= resultSlot.y() && mouseY < resultSlot.y() + 18) {
                        // 右键：清空结果槽
                        if (button == 1) {
                            slotManager.setResultItem(ItemStack.EMPTY);
                            if (componentRenderManager != null) {
                                componentRenderManager.updateResultItem(ItemStack.EMPTY);
                            }
                            return true;
                        }
                        // 左键：选中结果槽
                        if (button == 0) {
                            clearAllSelections();
                            isResultSlotSelected = true;
                            updateSlotOperationButtonsVisibility();
                            return true;
                        }
                        return true;
                    }
                }
            }
        }

        // 其他组件的点击处理
        if (componentRenderManager != null &&
                componentRenderManager.handleMouseClick(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    // ── 槽位操作按钮处理 ──────────────────────────────────────────
    
    /**
     * 1. 更改数量
     */
    private void onChangeAmountClicked() {
        if (selectedSlotIndex >= 0) {
            IngredientData data = slotManager.getIngredientData(selectedSlotIndex);
            if (data != null && !data.isEmpty()) {
                int currentAmount = data.getAmount();
                int minAmount = 1;
                int maxAmount = 64;
                
                if (data.getType() == IngredientData.Type.ITEM && !data.getItemStack().isEmpty()) {
                    maxAmount = data.getItemStack().getMaxStackSize();
                }
                
                if (minecraft != null) {
                    minecraft.setScreen(new dev.whisperlyric_fork.gui.NumberAdjustmentScreen(
                        this,
                        minAmount,
                        maxAmount,
                        currentAmount,
                        newAmount -> {
                            data.setAmount(newAmount);
                            if (componentRenderManager != null) {
                                List<SlotManager.IngredientSlot> ingredientSlots = slotManager.getIngredientSlots();
                                if (selectedSlotIndex < ingredientSlots.size()) {
                                    int slotIndex = ingredientSlots.get(selectedSlotIndex).index();
                                    componentRenderManager.updateSlotItem(slotIndex, data.getItemStack());
                                }
                            }
                            displayInfo("已更改数量为: " + newAmount);
                        }
                    ));
                }
            } else {
                displayError("该槽位为空，无法更改数量");
            }
        } else if (isResultSlotSelected) {
            if (slotManager != null) {
                ItemStack resultStack = slotManager.getResultItem();
                if (!resultStack.isEmpty()) {
                    int currentAmount = resultStack.getCount();
                    int minAmount = 1;
                    int maxAmount = resultStack.getMaxStackSize();
                    
                    if (minecraft != null) {
                        minecraft.setScreen(new dev.whisperlyric_fork.gui.NumberAdjustmentScreen(
                            this,
                            minAmount,
                            maxAmount,
                            currentAmount,
                            newAmount -> {
                                ItemStack newResult = resultStack.copy();
                                newResult.setCount(newAmount);
                                slotManager.setResultItem(newResult);
                                if (componentRenderManager != null) {
                                    componentRenderManager.updateResultItem(newResult);
                                }
                                displayInfo("已更改结果数量为: " + newAmount);
                            }
                        ));
                    }
                } else {
                    displayError("结果槽位为空，无法更改数量");
                }
            }
        } else if (selectedFluidSlot != null) {
            long currentAmount = selectedFluidSlot.getAmount();
            long maxAmount = selectedFluidSlot.getMaxAmount();
            if (minecraft != null) {
                minecraft.setScreen(new dev.whisperlyric_fork.gui.NumberAdjustmentScreen(
                    this,
                    0,
                    (int) Math.min(maxAmount, Integer.MAX_VALUE),
                    (int) currentAmount,
                    newAmount -> {
                        selectedFluidSlot.setAmount(newAmount);
                        if (componentRenderManager != null && slotManager != null) {
                            componentRenderManager.initializeRenderers(slotManager.getComponents());
                        }
                        displayInfo("已更改流体数量为: " + newAmount + " mB");
                    }
                ));
            }
        } else if (selectedGasSlot != null) {
            int currentAmount = selectedGasSlot.getAmount();
            int maxAmount = 10000;
            if (minecraft != null) {
                minecraft.setScreen(new dev.whisperlyric_fork.gui.NumberAdjustmentScreen(
                    this,
                    0,
                    maxAmount,
                    currentAmount,
                    newAmount -> {
                        selectedGasSlot.setAmount(newAmount);
                        if (componentRenderManager != null && slotManager != null) {
                            componentRenderManager.initializeRenderers(slotManager.getComponents());
                        }
                        displayInfo("已更改气体数量为: " + newAmount);
                    }
                ));
            }
        } else if (selectedChemicalSlot != null) {
            long currentAmount = selectedChemicalSlot.getAmount();
            long maxAmount = selectedChemicalSlot.getMaxAmount();
            if (minecraft != null) {
                minecraft.setScreen(new dev.whisperlyric_fork.gui.NumberAdjustmentScreen(
                    this,
                    0,
                    (int) Math.min(maxAmount, Integer.MAX_VALUE),
                    (int) currentAmount,
                    newAmount -> {
                        selectedChemicalSlot.setAmount(newAmount);
                        if (componentRenderManager != null && slotManager != null) {
                            componentRenderManager.initializeRenderers(slotManager.getComponents());
                        }
                        displayInfo("已更改化学物质数量为: " + newAmount);
                    }
                ));
            }
        } else if (selectedOutputSlot != null) {
            int slotIndex = selectedOutputSlot.getSlotIndex();
            if (componentRenderManager != null) {
                ItemStack currentStack = componentRenderManager.createRenderer(selectedOutputSlot) instanceof SlotRenderer slotRenderer
                    ? slotRenderer.getItem() 
                    : ItemStack.EMPTY;
                
                if (!currentStack.isEmpty()) {
                    int currentAmount = currentStack.getCount();
                    int minAmount = 1;
                    int maxAmount = currentStack.getMaxStackSize();
                    
                    if (minecraft != null) {
                        minecraft.setScreen(new dev.whisperlyric_fork.gui.NumberAdjustmentScreen(
                            this,
                            minAmount,
                            maxAmount,
                            currentAmount,
                            newAmount -> {
                                ItemStack newStack = currentStack.copy();
                                newStack.setCount(newAmount);
                                if (slotManager != null) {
                                    slotManager.setOutputSlotItem(slotIndex, newStack);
                                }
                                componentRenderManager.updateSlotItem(slotIndex, newStack);
                                displayInfo("已更改输出数量为: " + newAmount);
                            }
                        ));
                    }
                } else {
                    displayError("输出槽位为空，无法更改数量");
                }
            }
        }
    }
    
    /**
     * 2. 从创造物品栏选择
     */
    private void onSelectFromCreativeClicked() {
        if (minecraft != null) {
            minecraft.setScreen(new ItemSelectorScreen(
                this,
                stack -> {
                    if (stack != null && !stack.isEmpty()) {
                        applySelectedItem(stack);
                    }
                }
            ));
        }
    }
    
    private void onEditSpecialPropertiesClicked() {
        if (minecraft != null && currentRecipeType != null) {
            String machineType = getMachineTypeForSpecialProperties();
            if (machineType != null) {
                initializeDefaultSpecialProperties(machineType);
                
                minecraft.setScreen(new dev.whisperlyric_fork.gui.SpecialPropertiesEditScreen(
                    this,
                    machineType,
                    specialProperties,
                    result -> {
                        specialProperties.putAll(result);
                        displayInfo("已更新特殊配方属性");
                    }
                ));
            }
        }
    }
    
    private String getMachineTypeForSpecialProperties() {
        if (currentRecipeType == null) return null;
        
        String typeId = currentRecipeType.getId();
        if (typeId.equals("mekanism:sawing")) {
            return "mekanism:sawing";
        } else if (typeId.equals("mekanism:separating")) {
            return "mekanism:separating";
        } else if (typeId.equals("mekanism:reaction")) {
            return "mekanism:reaction";
        }
        return null;
    }
    
    private void initializeDefaultSpecialProperties(String machineType) {
        switch (machineType) {
            case "mekanism:sawing" -> {
                if (!specialProperties.containsKey("secondaryChance")) {
                    specialProperties.put("secondaryChance", 1.0);
                }
            }
            case "mekanism:separating" -> {
                if (!specialProperties.containsKey("energyMultiplier")) {
                    specialProperties.put("energyMultiplier", 1.0);
                }
            }
            case "mekanism:reaction" -> {
                if (!specialProperties.containsKey("duration")) {
                    specialProperties.put("duration", 100L);
                }
                if (!specialProperties.containsKey("energyRequired")) {
                    specialProperties.put("energyRequired", 100000L);
                }
            }
        }
    }
    
    private void updateSpecialPropertiesButtonVisibility() {
        String machineType = getMachineTypeForSpecialProperties();
        btnEditSpecialProperties.visible = (machineType != null);
    }
    
    /**
     * 3. 从玩家物品栏选择
     */
    private void onSelectFromInventoryClicked() {
        if (minecraft != null) {
            minecraft.setScreen(new dev.whisperlyric_fork.gui.PlayerInventorySelectionScreen(
                this,
                result -> {
                    if (result.value instanceof net.minecraft.world.item.ItemStack stack) {
                        applySelectedItem(stack);
                    }
                }
            ));
        }
    }
    
    /**
     * 4. 从JEI选择
     */
    private void onSelectFromJEIClicked() {
        if (minecraft != null) {
            dev.whisperlyric_fork.gui.SlotSelectionScreen.SlotType slotType = 
                dev.whisperlyric_fork.gui.SlotSelectionScreen.SlotType.ITEM;
            boolean gasOnly = false;
            
            if (selectedSlotIndex >= 0) {
                IngredientData data = slotManager.getIngredientData(selectedSlotIndex);
                if (data != null) {
                    if (data.getType() == IngredientData.Type.FLUID) {
                        slotType = dev.whisperlyric_fork.gui.SlotSelectionScreen.SlotType.FLUID;
                    } else if (data.getType() == IngredientData.Type.GAS) {
                        slotType = dev.whisperlyric_fork.gui.SlotSelectionScreen.SlotType.GAS;
                    }
                }
            } else if (selectedFluidSlot != null) {
                slotType = dev.whisperlyric_fork.gui.SlotSelectionScreen.SlotType.FLUID;
            } else if (selectedGasSlot != null) {
                slotType = dev.whisperlyric_fork.gui.SlotSelectionScreen.SlotType.GAS;
                gasOnly = selectedGasSlot.isGasOnly();
            } else if (selectedChemicalSlot != null) {
                slotType = switch (selectedChemicalSlot.getChemicalType()) {
                    case GAS -> dev.whisperlyric_fork.gui.SlotSelectionScreen.SlotType.GAS;
                    case SLURRY -> dev.whisperlyric_fork.gui.SlotSelectionScreen.SlotType.SLURRY;
                    case PIGMENT -> dev.whisperlyric_fork.gui.SlotSelectionScreen.SlotType.PIGMENT;
                    case INFUSE_TYPE -> dev.whisperlyric_fork.gui.SlotSelectionScreen.SlotType.INFUSE_TYPE;
                };
            } else if (selectedOutputSlot != null) {
                slotType = dev.whisperlyric_fork.gui.SlotSelectionScreen.SlotType.ITEM;
            }
            
            minecraft.setScreen(new dev.whisperlyric_fork.gui.JEISelectionScreen(
                this,
                slotType,
                result -> {
                    if (selectedFluidSlot != null && result.value instanceof net.minecraftforge.fluids.FluidStack fluidStack) {
                        selectedFluidSlot.setFluidId(fluidStack.getFluid().builtInRegistryHolder().key().location().toString());
                        long amount = fluidStack.getAmount();
                        if (amount <= 0) amount = 1;
                        selectedFluidSlot.setAmount(amount);
                        if (componentRenderManager != null && slotManager != null) {
                            componentRenderManager.initializeRenderers(slotManager.getComponents());
                        }
                        displayInfo("已设置流体: " + fluidStack.getDisplayName().getString() + " (" + amount + "mB)");
                    } else if (selectedGasSlot != null && result.value instanceof String gasId) {
                        selectedGasSlot.setGasId(gasId);
                        int amount = result.amount > 0 ? result.amount : 1;
                        selectedGasSlot.setAmount(amount);
                        displayInfo("已设置气体: " + gasId);
                    } else if (selectedChemicalSlot != null && result.value instanceof String chemicalId) {
                        selectedChemicalSlot.setChemicalId(chemicalId);
                        long amount = result.amount > 0 ? result.amount : 1;
                        selectedChemicalSlot.setAmount(amount);
                        displayInfo("已设置化学物质: " + chemicalId);
                    } else {
                        applySelectedItem(result.value);
                    }
                    clearAllSelections();
                },
                gasOnly
            ));
        }
    }
    
    /**
     * 5. 添加物品标签组
     */
    private void onAddTagGroupClicked() {
        if (minecraft != null) {
            minecraft.setScreen(new TagSelectorScreen(
                this,
                tagId -> {
                    if (tagId != null) {
                        applySelectedTagById(tagId);
                    }
                }
            ));
        }
    }
    
    private void applySelectedTagById(ResourceLocation tagId) {
        if (selectedSlotIndex >= 0) {
            IngredientData data = IngredientData.fromTag(tagId);
            slotManager.setIngredientData(selectedSlotIndex, data);
            displayInfo("已设置标签: #" + tagId);
        } else if (selectedFluidSlot != null) {
            selectedFluidSlot.setFluidId("#" + tagId);
            displayInfo("已设置流体标签: #" + tagId);
        } else if (selectedGasSlot != null) {
            selectedGasSlot.setGasId("#" + tagId);
            displayInfo("已设置气体标签: #" + tagId);
        } else if (selectedChemicalSlot != null) {
            selectedChemicalSlot.setChemicalId("#" + tagId);
            displayInfo("已设置化学物质标签: #" + tagId);
        }
    }
    
    /**
     * 应用选中的物品
     */
    private void applySelectedItem(Object value) {
        if (value instanceof net.minecraft.world.item.ItemStack stack) {
            ItemStack stackToSet = stack.copy();
            
            boolean shouldLimitToSingle = false;
            
            if (selectedSlotIndex >= 0) {
                List<SlotManager.IngredientSlot> ingredientSlots = slotManager.getIngredientSlots();
                if (selectedSlotIndex < ingredientSlots.size()) {
                    int componentSlotIndex = ingredientSlots.get(selectedSlotIndex).index();
                    
                    if (slotManager != null) {
                        for (RecipeComponent component : slotManager.getComponents()) {
                            if (component instanceof SlotComponent slotComp && slotComp.getSlotIndex() == componentSlotIndex) {
                                if (!slotComp.isBulkSlot()) {
                                    shouldLimitToSingle = true;
                                }
                                break;
                            }
                        }
                    }
                }
            } else if (isResultSlotSelected) {
                shouldLimitToSingle = false;
            } else if (selectedOutputSlot != null) {
                if (!selectedOutputSlot.isBulkSlot()) {
                    shouldLimitToSingle = true;
                }
            }
            
            if (shouldLimitToSingle && stackToSet.getCount() > 1) {
                stackToSet.setCount(1);
            }
            
            if (selectedSlotIndex >= 0) {
                slotManager.setIngredient(selectedSlotIndex, stackToSet);
                if (componentRenderManager != null) {
                    // 使用 ingredientSlots 的 index 字段作为 slotItems 的 key
                    List<SlotManager.IngredientSlot> ingredientSlots = slotManager.getIngredientSlots();
                    if (selectedSlotIndex < ingredientSlots.size()) {
                        int slotIndex = ingredientSlots.get(selectedSlotIndex).index();
                        componentRenderManager.updateSlotItem(slotIndex, stackToSet);
                    }
                }
                displayInfo("已设置物品: " + stackToSet.getHoverName().getString());
            } else if (isResultSlotSelected) {
                slotManager.setResultItem(stackToSet);
                if (componentRenderManager != null) {
                    componentRenderManager.updateResultItem(stackToSet);
                }
                displayInfo("已设置结果物品: " + stackToSet.getHoverName().getString());
            } else if (selectedOutputSlot != null) {
                int slotIndex = selectedOutputSlot.getSlotIndex();
                if (slotManager != null) {
                    slotManager.setOutputSlotItem(slotIndex, stackToSet);
                }
                if (componentRenderManager != null) {
                    componentRenderManager.updateSlotItem(slotIndex, stackToSet);
                }
                displayInfo("已设置输出物品: " + stackToSet.getHoverName().getString());
            }
        } else if (value instanceof net.minecraftforge.fluids.FluidStack fluidStack) {
            if (selectedSlotIndex >= 0) {
                IngredientData data = IngredientData.fromFluid(fluidStack.getFluid().builtInRegistryHolder().key().location().toString(), fluidStack.getAmount());
                slotManager.setIngredientData(selectedSlotIndex, data);
                displayInfo("已设置流体: " + fluidStack.getFluid().builtInRegistryHolder().key().location());
            }
        } else if (value instanceof String gasId) {
            if (selectedSlotIndex >= 0) {
                IngredientData data = IngredientData.fromGas(gasId, 1000);
                slotManager.setIngredientData(selectedSlotIndex, data);
                displayInfo("已设置气体: " + gasId);
            }
        }
    }
    
    /**
     * 应用选中的标签
     */
    private void applySelectedTag(net.minecraft.tags.TagKey<?> tag) {
        if (selectedSlotIndex >= 0) {
            @SuppressWarnings("unchecked")
            net.minecraft.tags.TagKey<net.minecraft.world.item.Item> itemTag = (net.minecraft.tags.TagKey<net.minecraft.world.item.Item>) tag;
            IngredientData data = IngredientData.fromTag(itemTag.location());
            slotManager.setIngredientData(selectedSlotIndex, data);
            displayInfo("已设置标签: #" + tag.location());
        }
    }

    @Override
    public void removed() {
        if (!switchingToJEI) {
            super.removed();
        }
    }

    public void setSwitchingToJEI(boolean value) {
        this.switchingToJEI = value;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // 消息显示方法
    private void displayError(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("§c" + message));
        }
    }

    private void displaySuccess(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("§a" + message));
        }
    }

    private void displayInfo(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("§e" + message));
        }
    }
}
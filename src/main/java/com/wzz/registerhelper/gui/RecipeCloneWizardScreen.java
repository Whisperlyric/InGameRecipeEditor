package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.gui.util.RecipePreviewRenderer;
import com.wzz.registerhelper.gui.util.RecipePreviewRenderer.PreviewSlot;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

/**
 * 配方克隆向导
 * 布局（固定三栏）：
 * ┌──────────────────────────────────────────────────────┐
 * │  标题栏                                               │
 * ├──────────────┬──────────────────┬───────────────────┤
 * │ 【物品选择】  │  【配方列表】      │  【配方预览】      │
 * │  搜索框       │  ─ 作为原料(N)   │  材料槽 3x3       │
 * │  背包物品     │  ─ 作为产物(N)   │  → 产物槽         │
 * │  （图标网格） │  （可滚动列表）   │  ID/来源信息       │
 * ├──────────────┴──────────────────┴───────────────────┤
 * │  [克隆此配方为模板]           [重选物品]  [关闭]       │
 * └──────────────────────────────────────────────────────┘
 */
@OnlyIn(Dist.CLIENT)
public class RecipeCloneWizardScreen extends Screen {

    // ── 尺寸常量 ─────────────────────────────────────────────────
    private static final int W  = 700;
    private static final int H  = 440;
    private static final int PAD = 8;

    // 三栏宽度
    private static final int COL1_W = 180;   // 物品选择
    private static final int COL2_W = 220;   // 配方列表
    // COL3 = 剩余宽度

    private static final int SLOT   = 18;
    private static final int ROW_H  = 20;
    private static final int TITLE_H = 28;
    private static final int FOOT_H  = 30;
    private static final int CONTENT_Y_OFFSET = TITLE_H + 6; // 内容区起始偏移

    // ── 回调 ─────────────────────────────────────────────────────
    private final Screen parent;
    private final Consumer<ResourceLocation> onClone;

    // ── Step 1：物品选择 ──────────────────────────────────────────
    private final List<ItemStack> allItems      = new ArrayList<>();
    private final List<ItemStack> filteredItems = new ArrayList<>();
    private EditBox searchBox;
    private int itemScroll = 0;
    private ItemStack targetItem = ItemStack.EMPTY;

    // 拼音搜索助手
    private final PinyinSearchHelper<ItemStack> searchHelper;

    private static final int ITEM_COLS = 8;
    private static final int ITEM_ROWS = 5;  // 减少物品行数，为流体和化学品腾出空间

    // ── 流体选择 ──────────────────────────────────────────────────
    private final List<FluidStack> allFluids = new ArrayList<>();
    private final List<FluidStack> filteredFluids = new ArrayList<>();
    private EditBox fluidSearchBox;
    private int fluidScroll = 0;
    private FluidStack targetFluid = FluidStack.EMPTY;
    private final PinyinSearchHelper<FluidStack> fluidSearchHelper;
    private static final int FLUID_ROWS = 2;  // 流体行数

    // ── 化学品选择 ──────────────────────────────────────────────────
    private final List<ChemicalEntry> allChemicals = new ArrayList<>();
    private final List<ChemicalEntry> filteredChemicals = new ArrayList<>();
    private EditBox chemicalSearchBox;
    private int chemicalScroll = 0;
    private ChemicalEntry targetChemical = null;
    private final PinyinSearchHelper<ChemicalEntry> chemicalSearchHelper;
    private static final int CHEMICAL_ROWS = 2;  // 化学品行数

    // ── Step 2：配方列表 ──────────────────────────────────────────
    private final List<RecipeEntry> recipeList = new ArrayList<>();  // 合并列表（原料在前，产物在后）
    private int recipeScroll  = 0;
    private int selectedIdx   = -1;
    private int listVisRows;   // 计算后赋值

    // ── Step 3：预览 ──────────────────────────────────────────────
    private final List<PreviewSlot> currentRecipeSlots = new ArrayList<>();
    private PreviewSlot currentResultSlot = null;

    // ── 滚动条拖动状态 ────────────────────────────────────────────
    private boolean draggingItemScroll = false;
    private boolean draggingFluidScroll = false;
    private boolean draggingChemicalScroll = false;
    private boolean draggingRecipeScroll = false;
    private int dragScrollY = 0;  // 拖动起始Y坐标

    // ── 面板坐标（init后有效）────────────────────────────────────
    private int px, py;
    private int c1x, c2x, c3x, contentY, contentH;
    private int col3W;

    public RecipeCloneWizardScreen(Screen parent, Consumer<ResourceLocation> onClone) {
        super(Component.literal("配方克隆向导"));
        this.parent  = parent;
        this.onClone = onClone;
        this.searchHelper = new PinyinSearchHelper<>(
                item -> item.getHoverName().getString(),
                item -> {
                    ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item.getItem());
                    return rl != null ? rl.toString() : "";
                }
        );
        this.fluidSearchHelper = new PinyinSearchHelper<>(
                fluid -> fluid.getDisplayName().getString(),
                fluid -> {
                    ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
                    return rl != null ? rl.toString() : "";
                }
        );
        this.chemicalSearchHelper = new PinyinSearchHelper<>(
                chemical -> chemical.getDisplayName(),
                chemical -> chemical.id != null ? chemical.id.toString() : ""
        );
        loadItems();
        loadFluids();
        loadChemicals();
    }

    // ── 物品加载 ─────────────────────────────────────────────────
    private void loadItems() {
        // 背包物品优先（方便选当前拿着的素材）
        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (ItemStack s : player.getInventory().items) {
                if (!s.isEmpty()) addUnique(s.copy());
            }
        }
        // 全部注册物品
        for (var item : ForgeRegistries.ITEMS.getValues()) addUnique(new ItemStack(item));
        filteredItems.addAll(allItems);
        // 构建拼音缓存
        searchHelper.buildCache(allItems);
    }

    private void addUnique(ItemStack s) {
        for (ItemStack x : allItems) if (ItemStack.isSameItem(x, s)) return;
        allItems.add(s);
    }

    // ── 流体加载 ─────────────────────────────────────────────────
    private void loadFluids() {
        for (Fluid fluid : ForgeRegistries.FLUIDS.getValues()) {
            if (fluid != Fluids.EMPTY && fluid != Fluids.LAVA && fluid != Fluids.WATER) {
                // 只添加源流体，避免重复
                if (fluid.isSource(fluid.defaultFluidState())) {
                    allFluids.add(new FluidStack(fluid, 1000));
                }
            }
        }
        filteredFluids.addAll(allFluids);
        fluidSearchHelper.buildCache(allFluids);
    }

    // ── 化学品加载 ───────────────────────────────────────────────
    private void loadChemicals() {
        try {
            // 尝试加载Mekanism化学品
            if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism")) {
                // 气体
                try {
                    for (var gas : mekanism.api.MekanismAPI.gasRegistry().getValues()) {
                        if (!gas.isEmptyType()) {
                            allChemicals.add(new ChemicalEntry(
                                    mekanism.api.MekanismAPI.gasRegistry().getKey(gas),
                                    RecipePreviewRenderer.ContentType.GAS,
                                    gas.getTint()
                            ));
                        }
                    }
                } catch (Exception e) {}

                // 灌注类型
                try {
                    for (var infuseType : mekanism.api.MekanismAPI.infuseTypeRegistry().getValues()) {
                        if (!infuseType.isEmptyType()) {
                            allChemicals.add(new ChemicalEntry(
                                    mekanism.api.MekanismAPI.infuseTypeRegistry().getKey(infuseType),
                                    RecipePreviewRenderer.ContentType.INFUSE_TYPE,
                                    infuseType.getTint()
                            ));
                        }
                    }
                } catch (Exception e) {}

                // 颜料
                try {
                    for (var pigment : mekanism.api.MekanismAPI.pigmentRegistry().getValues()) {
                        if (!pigment.isEmptyType()) {
                            allChemicals.add(new ChemicalEntry(
                                    mekanism.api.MekanismAPI.pigmentRegistry().getKey(pigment),
                                    RecipePreviewRenderer.ContentType.PIGMENT,
                                    pigment.getTint()
                            ));
                        }
                    }
                } catch (Exception e) {}

                // 矿泥
                try {
                    for (var slurry : mekanism.api.MekanismAPI.slurryRegistry().getValues()) {
                        if (!slurry.isEmptyType()) {
                            allChemicals.add(new ChemicalEntry(
                                    mekanism.api.MekanismAPI.slurryRegistry().getKey(slurry),
                                    RecipePreviewRenderer.ContentType.SLURRY,
                                    slurry.getTint()
                            ));
                        }
                    }
                } catch (Exception e) {}
            }
        } catch (Exception e) {}
        
        filteredChemicals.addAll(allChemicals);
        chemicalSearchHelper.buildCache(allChemicals);
    }

    // ── 搜索（支持中文名称、拼音、首字母、mod过滤） ───────────────
    private void onSearch(String text) {
        filteredItems.clear();
        if (text.isBlank()) {
            filteredItems.addAll(allItems);
        } else {
            for (ItemStack s : allItems) {
                if (searchHelper.matches(s, text)) {
                    filteredItems.add(s);
                }
            }
        }
        itemScroll = 0;
    }

    private void onFluidSearch(String text) {
        filteredFluids.clear();
        if (text.isBlank()) {
            filteredFluids.addAll(allFluids);
        } else {
            for (FluidStack f : allFluids) {
                if (fluidSearchHelper.matches(f, text)) {
                    filteredFluids.add(f);
                }
            }
        }
        fluidScroll = 0;
    }

    private void onChemicalSearch(String text) {
        filteredChemicals.clear();
        if (text.isBlank()) {
            filteredChemicals.addAll(allChemicals);
        } else {
            for (ChemicalEntry c : allChemicals) {
                if (chemicalSearchHelper.matches(c, text)) {
                    filteredChemicals.add(c);
                }
            }
        }
        chemicalScroll = 0;
    }

    // ── 选中物品 → 加载配方 ──────────────────────────────────────
    private void pickItem(ItemStack item) {
        targetItem = item.copy();
        recipeList.clear();
        selectedIdx   = -1;
        recipeScroll  = 0;
        currentRecipeSlots.clear();
        currentResultSlot = null;

        RecipeManager rm = getRM();
        if (rm == null) return;

        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (Recipe<?> r : rm.getRecipes()) {
            try {
                boolean asIng = false, asRes = false;
                ItemStack res = r.getResultItem(Minecraft.getInstance().level.registryAccess());
                if (ItemStack.isSameItem(res, item)) asRes = true;
                for (Ingredient ing : r.getIngredients()) {
                    if (!ing.isEmpty()) for (ItemStack m : ing.getItems())
                        if (ItemStack.isSameItem(m, item)) { asIng = true; break; }
                    if (asIng) break;
                }
                if ((asIng || asRes) && seen.add(r.getId()))
                    recipeList.add(new RecipeEntry(r, asIng, asRes));
            } catch (Exception ignored) {}
        }
        // 排序：产物在前，原料在后
        recipeList.sort(Comparator.comparingInt(e -> (e.asResult ? 0 : 1)));
    }

    // ── 选中配方 → 更新预览 ──────────────────────────────────────
    private void selectRecipe(int idx) {
        selectedIdx = idx;
        currentRecipeSlots.clear();
        currentResultSlot = null;
        if (idx < 0 || idx >= recipeList.size()) return;
        Recipe<?> r = recipeList.get(idx).recipe;
        
        // 解析配方数据
        parseRecipe(r);
    }
    
    /**
     * 解析配方数据（支持普通配方和Mekanism配方）
     */
    private void parseRecipe(Recipe<?> recipe) {
        String recipeTypeName = recipe.getType().toString().toLowerCase();
        
        // 检查是否为Mekanism配方
        if (recipeTypeName.contains("mekanism") || recipeTypeName.contains("rotary") || 
            recipeTypeName.contains("condensentrator") || recipeTypeName.contains("metallurgic") ||
            recipeTypeName.contains("infuser") || recipeTypeName.contains("energy_conversion") ||
            recipeTypeName.contains("infusion_conversion") || recipeTypeName.contains("sawmill") ||
            recipeTypeName.contains("pressurized")) {
            parseMekanismRecipe(recipe);
        } else {
            parseNormalRecipe(recipe);
        }
    }
    
    /**
     * 解析普通配方
     */
    private void parseNormalRecipe(Recipe<?> recipe) {
        try {
            int slotX = c3x + 8;
            int slotY = contentY + 20;
            int slotSpacing = SLOT + 3;
            
            // 解析输入材料
            List<Ingredient> ingredients = recipe.getIngredients();
            int cols = ingredients.size() <= 9 ? 3 : ingredients.size() <= 16 ? 4 : 5;
            
            for (int i = 0; i < ingredients.size(); i++) {
                Ingredient ingredient = ingredients.get(i);
                if (!ingredient.isEmpty()) {
                    ItemStack[] items = ingredient.getItems();
                    if (items.length > 0) {
                        int x = slotX + (i % cols) * slotSpacing;
                        int y = slotY + (i / cols) * slotSpacing;
                        currentRecipeSlots.add(new PreviewSlot(x, y, items[0].copy()));
                    }
                }
            }
            
            // 解析输出
            ItemStack result = recipe.getResultItem(Minecraft.getInstance().level.registryAccess()).copy();
            if (!result.isEmpty()) {
                int rows = ingredients.isEmpty() ? 0 : (ingredients.size() - 1) / cols + 1;
                int arrowY = slotY + rows * slotSpacing / 2 - 4;
                int resultX = slotX + cols * slotSpacing + 4;
                int resultY = slotY + rows * slotSpacing / 2 - SLOT / 2;
                currentResultSlot = new PreviewSlot(resultX, resultY, result);
            }
        } catch (Exception e) {
            // 解析失败，清空预览
            currentRecipeSlots.clear();
            currentResultSlot = null;
        }
    }
    
    /**
     * 解析Mekanism配方
     */
    private void parseMekanismRecipe(Recipe<?> recipe) {
        try {
            int slotX = c3x + 8;
            int slotY = contentY + 20;
            int slotSpacing = SLOT + 3;
            
            String recipeTypeName = recipe.getType().toString().toLowerCase();
            
            // 回旋式气液转换器
            if (recipeTypeName.contains("rotary") || recipeTypeName.contains("condensentrator")) {
                java.lang.reflect.Method getFluidInput = recipe.getClass().getMethod("getFluidInput");
                java.lang.reflect.Method getGasInput = recipe.getClass().getMethod("getGasInput");
                java.lang.reflect.Method getOutputDefinition = recipe.getClass().getMethod("getOutputDefinition");
                
                Object fluidInput = getFluidInput.invoke(recipe);
                Object gasInput = getGasInput.invoke(recipe);
                
                boolean hasFluidInput = false;
                boolean hasGasInput = false;
                net.minecraftforge.fluids.FluidStack fluidStack = null;
                mekanism.api.chemical.gas.GasStack gasStack = null;
                
                if (fluidInput instanceof mekanism.api.recipes.ingredients.FluidStackIngredient fluidIngredient) {
                    List<net.minecraftforge.fluids.FluidStack> matchingStacks = fluidIngredient.getRepresentations();
                    if (!matchingStacks.isEmpty()) {
                        fluidStack = matchingStacks.get(0);
                        hasFluidInput = true;
                    }
                }
                
                if (gasInput instanceof mekanism.api.recipes.ingredients.ChemicalStackIngredient.GasStackIngredient gasIngredient) {
                    List<mekanism.api.chemical.gas.GasStack> matchingStacks = gasIngredient.getRepresentations();
                    if (!matchingStacks.isEmpty()) {
                        gasStack = matchingStacks.get(0);
                        hasGasInput = true;
                    }
                }
                
                // 输入槽
                if (hasFluidInput) {
                    currentRecipeSlots.add(new PreviewSlot(slotX, slotY, fluidStack));
                } else if (hasGasInput) {
                    ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                    if (gasId != null) {
                        currentRecipeSlots.add(new PreviewSlot(slotX, slotY, gasId.toString(), 
                                RecipePreviewRenderer.ContentType.GAS, gasStack.getAmount()));
                    }
                }
                
                // 输出槽
                Object outputDef = getOutputDefinition.invoke(recipe);
                if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                    Object output = outputList.get(0);
                    int resultX = slotX + slotSpacing * 2;
                    int resultY = slotY;
                    
                    if (output instanceof net.minecraftforge.fluids.FluidStack outputFluid) {
                        currentResultSlot = new PreviewSlot(resultX, resultY, outputFluid);
                    } else if (output instanceof mekanism.api.chemical.gas.GasStack outputGas) {
                        ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(outputGas.getType());
                        if (gasId != null) {
                            currentResultSlot = new PreviewSlot(resultX, resultY, gasId.toString(), 
                                    RecipePreviewRenderer.ContentType.GAS, outputGas.getAmount());
                        }
                    }
                }
                return;
            }
            
            // 能量转换
            if (recipeTypeName.contains("energy_conversion")) {
                java.lang.reflect.Method getInput = recipe.getClass().getMethod("getInput");
                
                Object input = getInput.invoke(recipe);
                if (input instanceof mekanism.api.recipes.ingredients.ItemStackIngredient ingredient) {
                    List<ItemStack> matchingStacks = ingredient.getRepresentations();
                    if (!matchingStacks.isEmpty()) {
                        currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0).copy()));
                    }
                }
                
                java.lang.reflect.Method getOutputDefinition = recipe.getClass().getMethod("getOutputDefinition");
                Object outputDef = getOutputDefinition.invoke(recipe);
                
                if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                    Object output = outputList.get(0);
                    long energyValue = 0;
                    
                    if (output instanceof Long energyOutput) {
                        energyValue = energyOutput;
                    } else if (output instanceof mekanism.api.math.FloatingLong floatingLong) {
                        energyValue = floatingLong.longValue();
                    }
                    
                    if (energyValue > 0) {
                        int resultX = slotX + slotSpacing * 2;
                        currentResultSlot = new PreviewSlot(resultX, slotY, energyValue);
                    }
                }
                return;
            }
            
            // 其他Mekanism配方类型 - 通用解析
            java.lang.reflect.Method getItemInput = null;
            java.lang.reflect.Method getFluidInput = null;
            java.lang.reflect.Method getChemicalInput = null;
            java.lang.reflect.Method getOutput = null;
            java.lang.reflect.Method getOutputDefinition = null;
            
            try { getItemInput = recipe.getClass().getMethod("getItemInput"); } catch (Exception e) {}
            try { getFluidInput = recipe.getClass().getMethod("getFluidInput"); } catch (Exception e) {}
            try { getChemicalInput = recipe.getClass().getMethod("getChemicalInput"); } catch (Exception e) {}
            try { getOutput = recipe.getClass().getMethod("getOutput"); } catch (Exception e) {}
            try { getOutputDefinition = recipe.getClass().getMethod("getOutputDefinition"); } catch (Exception e) {}
            
            int currentX = slotX;
            
            if (getItemInput != null) {
                Object itemInput = getItemInput.invoke(recipe);
                if (itemInput instanceof mekanism.api.recipes.ingredients.ItemStackIngredient ingredient) {
                    List<ItemStack> matchingStacks = ingredient.getRepresentations();
                    if (!matchingStacks.isEmpty()) {
                        currentRecipeSlots.add(new PreviewSlot(currentX, slotY, matchingStacks.get(0).copy()));
                        currentX += slotSpacing;
                    }
                }
            }
            
            if (getFluidInput != null) {
                Object fluidInput = getFluidInput.invoke(recipe);
                if (fluidInput instanceof mekanism.api.recipes.ingredients.FluidStackIngredient fluidIngredient) {
                    List<net.minecraftforge.fluids.FluidStack> matchingStacks = fluidIngredient.getRepresentations();
                    if (!matchingStacks.isEmpty()) {
                        currentRecipeSlots.add(new PreviewSlot(currentX, slotY, matchingStacks.get(0)));
                        currentX += slotSpacing;
                    }
                }
            }
            
            if (getChemicalInput != null) {
                Object chemicalInput = getChemicalInput.invoke(recipe);
                if (chemicalInput != null) {
                    Class<?> inputClass = chemicalInput.getClass();
                    java.lang.reflect.Method getRepresentations = inputClass.getMethod("getRepresentations");
                    List<?> matchingStacks = (List<?>) getRepresentations.invoke(chemicalInput);
                    
                    if (!matchingStacks.isEmpty()) {
                        Object stack = matchingStacks.get(0);
                        Class<?> stackClass = stack.getClass();
                        java.lang.reflect.Method getType = stackClass.getMethod("getType");
                        java.lang.reflect.Method getAmount = stackClass.getMethod("getAmount");
                        
                        Object chemical = getType.invoke(stack);
                        long amount = (Long) getAmount.invoke(stack);
                        
                        ResourceLocation chemicalId = null;
                        RecipePreviewRenderer.ContentType chemicalType = RecipePreviewRenderer.ContentType.GAS;
                        
                        String chemicalClassName = chemical.getClass().getName();
                        if (chemicalClassName.contains("Gas")) {
                            chemicalType = RecipePreviewRenderer.ContentType.GAS;
                            mekanism.api.chemical.gas.Gas gas = (mekanism.api.chemical.gas.Gas) chemical;
                            chemicalId = mekanism.api.MekanismAPI.gasRegistry().getKey(gas);
                        } else if (chemicalClassName.contains("InfuseType")) {
                            chemicalType = RecipePreviewRenderer.ContentType.INFUSE_TYPE;
                            mekanism.api.chemical.infuse.InfuseType infuseType = (mekanism.api.chemical.infuse.InfuseType) chemical;
                            chemicalId = mekanism.api.MekanismAPI.infuseTypeRegistry().getKey(infuseType);
                        } else if (chemicalClassName.contains("Pigment")) {
                            chemicalType = RecipePreviewRenderer.ContentType.PIGMENT;
                            mekanism.api.chemical.pigment.Pigment pigment = (mekanism.api.chemical.pigment.Pigment) chemical;
                            chemicalId = mekanism.api.MekanismAPI.pigmentRegistry().getKey(pigment);
                        } else if (chemicalClassName.contains("Slurry")) {
                            chemicalType = RecipePreviewRenderer.ContentType.SLURRY;
                            mekanism.api.chemical.slurry.Slurry slurry = (mekanism.api.chemical.slurry.Slurry) chemical;
                            chemicalId = mekanism.api.MekanismAPI.slurryRegistry().getKey(slurry);
                        }
                        
                        if (chemicalId != null) {
                            currentRecipeSlots.add(new PreviewSlot(currentX, slotY, chemicalId.toString(), chemicalType, amount));
                            currentX += slotSpacing;
                        }
                    }
                }
            }
            
            // 输出
            if (getOutput != null) {
                Object output = getOutput.invoke(recipe);
                if (output instanceof ItemStack itemStack) {
                    int resultX = currentX + slotSpacing;
                    currentResultSlot = new PreviewSlot(resultX, slotY, itemStack.copy());
                } else if (output instanceof net.minecraftforge.fluids.FluidStack fluidStack) {
                    int resultX = currentX + slotSpacing;
                    currentResultSlot = new PreviewSlot(resultX, slotY, fluidStack.copy());
                } else if (output instanceof mekanism.api.chemical.ChemicalStack<?> chemicalStack) {
                    Object chemical = chemicalStack.getType();
                    long amount = chemicalStack.getAmount();
                    
                    ResourceLocation chemicalId = null;
                    RecipePreviewRenderer.ContentType chemicalType = RecipePreviewRenderer.ContentType.GAS;
                    
                    String chemicalClassName = chemical.getClass().getName();
                    if (chemicalClassName.contains("Gas")) {
                        chemicalType = RecipePreviewRenderer.ContentType.GAS;
                        mekanism.api.chemical.gas.Gas gas = (mekanism.api.chemical.gas.Gas) chemical;
                        chemicalId = mekanism.api.MekanismAPI.gasRegistry().getKey(gas);
                    } else if (chemicalClassName.contains("InfuseType")) {
                        chemicalType = RecipePreviewRenderer.ContentType.INFUSE_TYPE;
                        mekanism.api.chemical.infuse.InfuseType infuseType = (mekanism.api.chemical.infuse.InfuseType) chemical;
                        chemicalId = mekanism.api.MekanismAPI.infuseTypeRegistry().getKey(infuseType);
                    } else if (chemicalClassName.contains("Pigment")) {
                        chemicalType = RecipePreviewRenderer.ContentType.PIGMENT;
                        mekanism.api.chemical.pigment.Pigment pigment = (mekanism.api.chemical.pigment.Pigment) chemical;
                        chemicalId = mekanism.api.MekanismAPI.pigmentRegistry().getKey(pigment);
                    } else if (chemicalClassName.contains("Slurry")) {
                        chemicalType = RecipePreviewRenderer.ContentType.SLURRY;
                        mekanism.api.chemical.slurry.Slurry slurry = (mekanism.api.chemical.slurry.Slurry) chemical;
                        chemicalId = mekanism.api.MekanismAPI.slurryRegistry().getKey(slurry);
                    }
                    
                    if (chemicalId != null) {
                        int resultX = currentX + slotSpacing;
                        currentResultSlot = new PreviewSlot(resultX, slotY, chemicalId.toString(), chemicalType, amount);
                    }
                }
            }
            
            if (getOutputDefinition != null) {
                Object outputDef = getOutputDefinition.invoke(recipe);
                if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                    Object output = outputList.get(0);
                    if (output instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                        int resultX = currentX + slotSpacing;
                        currentResultSlot = new PreviewSlot(resultX, slotY, itemStack.copy());
                    } else if (output instanceof net.minecraftforge.fluids.FluidStack fluidStack) {
                        int resultX = currentX + slotSpacing;
                        currentResultSlot = new PreviewSlot(resultX, slotY, fluidStack.copy());
                    }
                }
            }
            
            // 如果没有解析到任何槽位，回退到普通配方解析
            if (currentRecipeSlots.isEmpty() && currentResultSlot == null) {
                parseNormalRecipe(recipe);
            }
            
        } catch (Exception e) {
            // 解析失败，回退到普通配方解析
            parseNormalRecipe(recipe);
        }
    }

    private RecipeManager getRM() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s != null) return s.getRecipeManager();
        if (Minecraft.getInstance().level != null)
            return Minecraft.getInstance().level.getRecipeManager();
        return null;
    }

    // ── init ─────────────────────────────────────────────────────
    @Override
    protected void init() {
        px = (width  - W) / 2;
        py = (height - H) / 2;

        contentY = py + CONTENT_Y_OFFSET;
        contentH = H - CONTENT_Y_OFFSET - FOOT_H;
        listVisRows = (contentH - 4) / ROW_H;

        c1x = px + PAD;
        c2x = c1x + COL1_W + PAD;
        c3x = c2x + COL2_W + PAD;
        col3W = W - (c3x - px) - PAD;

        // 物品搜索框（栏1顶部）
        searchBox = new EditBox(font, c1x, contentY + 2, COL1_W, 14,
                Component.literal("搜索物品"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("§8名称/拼音/首字母/@mod"));
        searchBox.setResponder(this::onSearch);
        addWidget(searchBox);
        searchBox.setFocused(true);

        // 流体搜索框
        int fluidSearchY = contentY + 2 + (ITEM_ROWS * (SLOT + 2)) + 30;  // 下移16像素
        fluidSearchBox = new EditBox(font, c1x, fluidSearchY, COL1_W, 14,
                Component.literal("搜索流体"));
        fluidSearchBox.setMaxLength(64);
        fluidSearchBox.setHint(Component.literal("§8流体名称/拼音"));
        fluidSearchBox.setResponder(this::onFluidSearch);
        addWidget(fluidSearchBox);

        // 化学品搜索框
        int chemicalSearchY = fluidSearchY + 16 + (FLUID_ROWS * (SLOT + 2)) + 30;  // 下移16像素
        chemicalSearchBox = new EditBox(font, c1x, chemicalSearchY, COL1_W, 14,
                Component.literal("搜索化学品"));
        chemicalSearchBox.setMaxLength(64);
        chemicalSearchBox.setHint(Component.literal("§8化学品名称/拼音"));
        chemicalSearchBox.setResponder(this::onChemicalSearch);
        addWidget(chemicalSearchBox);

        // 底部按钮
        int footY = py + H - FOOT_H + 5;
        addRenderableWidget(Button.builder(
                Component.literal("§a⬡ 克隆此配方为模板"),
                btn -> doClone()
        ).bounds(c3x, footY, col3W, 20).build());

        addRenderableWidget(Button.builder(Component.literal("§7重选物品"),
                        btn -> { targetItem = ItemStack.EMPTY; recipeList.clear(); selectedIdx=-1; })
                .bounds(c2x, footY, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal("§c关闭"),
                        btn -> onClose())
                .bounds(c2x + 74, footY, 50, 20).build());
    }

    private void doClone() {
        if (selectedIdx >= 0 && selectedIdx < recipeList.size()) {
            onClone.accept(recipeList.get(selectedIdx).id);
            onClose();
        }
    }

    // ── 渲染 ─────────────────────────────────────────────────────
    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);

        // 外框 + 背景
        g.fill(px-1, py-1, px+W+1, py+H+1, 0xFF060606);
        g.fill(px, py, px+W, py+H, 0xFF181820);

        // 标题栏
        g.fill(px, py, px+W, py+TITLE_H, 0xFF1A3050);
        g.fill(px, py+TITLE_H-1, px+W, py+TITLE_H, 0xFF3A70A0);
        g.drawCenteredString(font, "§b配方克隆向导", px+W/2, py+10, 0xFFFFFF);

        // 三栏分割线
        int divColor = 0xFF2A2A3A;
        g.fill(c2x-PAD/2, contentY, c2x-PAD/2+1, contentY+contentH, divColor);
        g.fill(c3x-PAD/2, contentY, c3x-PAD/2+1, contentY+contentH, divColor);

        // 底栏分割线
        g.fill(px, py+H-FOOT_H, px+W, py+H-FOOT_H+1, divColor);

        renderCol1(g, mx, my);    // 物品、流体、化学品选择
        renderCol2(g, mx, my);    // 配方列表
        renderCol3(g, mx, my);    // 预览
        searchBox.render(g, mx, my, pt);
        fluidSearchBox.render(g, mx, my, pt);
        chemicalSearchBox.render(g, mx, my, pt);
        super.render(g, mx, my, pt);
    }

    // ── 栏1：物品、流体、化学品选择 ──────────────────────────────
    private void renderCol1(GuiGraphics g, int mx, int my) {
        // ── 物品选择 ────────────────────────────────────────────────
        g.drawString(font, "§7物品",
                c1x, contentY - 10, 0xAAAAAA, false);

        int gridTop = contentY + 18;
        int maxItemScroll = Math.max(0,
                (filteredItems.size() + ITEM_COLS - 1) / ITEM_COLS - ITEM_ROWS);
        itemScroll = clamp(itemScroll, 0, maxItemScroll);

        // 网格背景
        int gridW = ITEM_COLS * (SLOT+2);
        int gridH = ITEM_ROWS * (SLOT+2);
        g.fill(c1x-1, gridTop-1, c1x+gridW+1, gridTop+gridH+1, 0xFF333340);
        g.fill(c1x,   gridTop,   c1x+gridW,   gridTop+gridH,   0xFF0C0C18);

        for (int row = 0; row < ITEM_ROWS; row++) {
            for (int col = 0; col < ITEM_COLS; col++) {
                int idx = (row + itemScroll) * ITEM_COLS + col;
                if (idx >= filteredItems.size()) break;
                ItemStack it = filteredItems.get(idx);
                int sx = c1x + col*(SLOT+2);
                int sy = gridTop + row*(SLOT+2);
                boolean hov = mx>=sx && mx<sx+SLOT && my>=sy && my<sy+SLOT;
                boolean sel = ItemStack.isSameItem(it, targetItem);
                if (sel) g.fill(sx, sy, sx+SLOT, sy+SLOT, 0xFF3A6A3A);
                else if (hov) g.fill(sx, sy, sx+SLOT, sy+SLOT, 0xFF3A3A6A);
                RenderSystem.enableDepthTest();
                g.renderItem(it, sx+1, sy+1);
                RenderSystem.disableDepthTest();
                if (hov) g.renderTooltip(font, it, mx, my);
            }
        }

        // 物品滚动条
        renderSB(g, c1x+gridW+1, gridTop, 3, gridH,
                (filteredItems.size()+ITEM_COLS-1)/ITEM_COLS, ITEM_ROWS, itemScroll);

        // ── 流体选择 ────────────────────────────────────────────────
        int fluidTop = gridTop + gridH + 36;  // 下移16像素
        g.drawString(font, "§7流体",
                c1x, fluidTop - 16, 0xAAAAAA, false);

        int fluidGridTop = fluidTop + 2;
        int maxFluidScroll = Math.max(0,
                (filteredFluids.size() + ITEM_COLS - 1) / ITEM_COLS - FLUID_ROWS);
        fluidScroll = clamp(fluidScroll, 0, maxFluidScroll);

        int fluidGridH = FLUID_ROWS * (SLOT+2);
        g.fill(c1x-1, fluidGridTop-1, c1x+gridW+1, fluidGridTop+fluidGridH+1, 0xFF333340);
        g.fill(c1x,   fluidGridTop,   c1x+gridW,   fluidGridTop+fluidGridH,   0xFF0C0C18);

        for (int row = 0; row < FLUID_ROWS; row++) {
            for (int col = 0; col < ITEM_COLS; col++) {
                int idx = (row + fluidScroll) * ITEM_COLS + col;
                if (idx >= filteredFluids.size()) break;
                FluidStack fluid = filteredFluids.get(idx);
                int sx = c1x + col*(SLOT+2);
                int sy = fluidGridTop + row*(SLOT+2);
                boolean hov = mx>=sx && mx<sx+SLOT && my>=sy && my<sy+SLOT;
                boolean sel = !targetFluid.isEmpty() && fluid.isFluidStackIdentical(targetFluid);
                if (sel) g.fill(sx, sy, sx+SLOT, sy+SLOT, 0xFF3A6A3A);
                else if (hov) g.fill(sx, sy, sx+SLOT, sy+SLOT, 0xFF3A3A6A);
                
                // 渲染流体（JEI样式）
                renderFluidSlot(g, fluid, sx+1, sy+1);
                
                if (hov) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(fluid.getDisplayName());
                    tooltip.add(Component.literal("§8" + ForgeRegistries.FLUIDS.getKey(fluid.getFluid())));
                    g.renderTooltip(font, tooltip, Optional.empty(), mx, my);
                }
            }
        }

        // 流体滚动条
        renderSB(g, c1x+gridW+1, fluidGridTop, 3, fluidGridH,
                (filteredFluids.size()+ITEM_COLS-1)/ITEM_COLS, FLUID_ROWS, fluidScroll);

        // ── 化学品选择 ──────────────────────────────────────────────
        int chemicalTop = fluidGridTop + fluidGridH + 44;  // 下移16像素
        g.drawString(font, "§7化学品",
                c1x, chemicalTop - 16, 0xAAAAAA, false);

        int chemicalGridTop = chemicalTop + 2;
        int maxChemicalScroll = Math.max(0,
                (filteredChemicals.size() + ITEM_COLS - 1) / ITEM_COLS - CHEMICAL_ROWS);
        chemicalScroll = clamp(chemicalScroll, 0, maxChemicalScroll);

        int chemicalGridH = CHEMICAL_ROWS * (SLOT+2);
        g.fill(c1x-1, chemicalGridTop-1, c1x+gridW+1, chemicalGridTop+chemicalGridH+1, 0xFF333340);
        g.fill(c1x,   chemicalGridTop,   c1x+gridW,   chemicalGridTop+chemicalGridH,   0xFF0C0C18);

        for (int row = 0; row < CHEMICAL_ROWS; row++) {
            for (int col = 0; col < ITEM_COLS; col++) {
                int idx = (row + chemicalScroll) * ITEM_COLS + col;
                if (idx >= filteredChemicals.size()) break;
                ChemicalEntry chemical = filteredChemicals.get(idx);
                int sx = c1x + col*(SLOT+2);
                int sy = chemicalGridTop + row*(SLOT+2);
                boolean hov = mx>=sx && mx<sx+SLOT && my>=sy && my<sy+SLOT;
                boolean sel = chemical.equals(targetChemical);
                if (sel) g.fill(sx, sy, sx+SLOT, sy+SLOT, 0xFF3A6A3A);
                else if (hov) g.fill(sx, sy, sx+SLOT, sy+SLOT, 0xFF3A3A6A);
                
                // 渲染化学品（JEI样式）
                renderChemicalSlot(g, chemical, sx+1, sy+1);
                
                if (hov) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.literal(chemical.getDisplayName()));
                    tooltip.add(Component.literal("§8" + chemical.id));
                    tooltip.add(Component.literal("§7类型: " + chemical.type.name()));
                    g.renderTooltip(font, tooltip, Optional.empty(), mx, my);
                }
            }
        }

        // 化学品滚动条
        renderSB(g, c1x+gridW+1, chemicalGridTop, 3, chemicalGridH,
                (filteredChemicals.size()+ITEM_COLS-1)/ITEM_COLS, CHEMICAL_ROWS, chemicalScroll);
    }
    
    /**
     * 渲染流体槽位（JEI样式）
     */
    private void renderFluidSlot(GuiGraphics g, FluidStack fluid, int x, int y) {
        if (fluid.isEmpty()) return;
        
        Fluid f = fluid.getFluid();
        IClientFluidTypeExtensions fluidExt = IClientFluidTypeExtensions.of(f);
        
        int tintColor = fluidExt.getTintColor(fluid);
        float r = ((tintColor >> 16) & 0xFF) / 255.0f;
        float g2 = ((tintColor >> 8) & 0xFF) / 255.0f;
        float b = (tintColor & 0xFF) / 255.0f;
        
        // 获取静止纹理（参考JEI实现）
        ResourceLocation stillTexture = fluidExt.getStillTexture(fluid);
        TextureAtlasSprite sprite = null;
        
        if (stillTexture != null) {
            try {
                sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(stillTexture);
                // 检查是否为缺失纹理
                if (sprite != null && sprite.contents().name().toString().contains("missingno")) {
                    sprite = null;
                }
            } catch (Exception e) {
                // 纹理加载失败
                sprite = null;
            }
        }
        
        if (sprite != null) {
            // 使用JEI样式的瓦片式渲染
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
            RenderSystem.setShaderColor(r, g2, b, 1.0f);
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            
            int width = 16;
            int height = 16;
            
            int textureWidth = 16;
            int textureHeight = 16;
            
            int xTileCount = width / textureWidth;
            int xRemainder = width - (xTileCount * textureWidth);
            int yTileCount = height / textureHeight;
            int yRemainder = height - (yTileCount * textureHeight);
            int yStart = y + height;
            
            float uMin = sprite.getU0();
            float uMax = sprite.getU1();
            float vMin = sprite.getV0();
            float vMax = sprite.getV1();
            float uDif = uMax - uMin;
            float vDif = vMax - vMin;
            
            RenderSystem.enableBlend();
            
            com.mojang.blaze3d.vertex.BufferBuilder vertexBuffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            vertexBuffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            org.joml.Matrix4f matrix4f = g.pose().last().pose();
            
            for (int xTile = 0; xTile <= xTileCount; xTile++) {
                int tileWidth = (xTile == xTileCount) ? xRemainder : textureWidth;
                if (tileWidth == 0) {
                    break;
                }
                int tileX = x + (xTile * textureWidth);
                int maskRight = textureWidth - tileWidth;
                int shiftedX = tileX + textureWidth - maskRight;
                float uLocalDif = uDif * maskRight / textureWidth;
                float uLocalMin = uMin;
                float uLocalMax = uMax - uLocalDif;
                
                for (int yTile = 0; yTile <= yTileCount; yTile++) {
                    int tileHeight = (yTile == yTileCount) ? yRemainder : textureHeight;
                    if (tileHeight == 0) {
                        break;
                    }
                    int tileY = yStart - ((yTile + 1) * textureHeight);
                    int maskTop = textureHeight - tileHeight;
                    float vLocalDif = vDif * maskTop / textureHeight;
                    float vLocalMin = vMin + vLocalDif;
                    float vLocalMax = vMax;
                    
                    vertexBuffer.vertex(matrix4f, tileX, tileY + textureHeight, 0).uv(uLocalMin, vLocalMax).endVertex();
                    vertexBuffer.vertex(matrix4f, shiftedX, tileY + textureHeight, 0).uv(uLocalMax, vLocalMax).endVertex();
                    vertexBuffer.vertex(matrix4f, shiftedX, tileY + maskTop, 0).uv(uLocalMax, vLocalMin).endVertex();
                    vertexBuffer.vertex(matrix4f, tileX, tileY + maskTop, 0).uv(uLocalMin, vLocalMin).endVertex();
                }
            }
            
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(vertexBuffer.end());
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            // 当纹理不可用时，使用颜色填充
            RenderSystem.setShaderColor(r, g2, b, 1.0f);
            g.fill(x, y, x + 16, y + 16, 0xFFFFFFFF);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
    
    /**
     * 渲染化学品槽位（JEI样式）
     */
    private void renderChemicalSlot(GuiGraphics g, ChemicalEntry chemical, int x, int y) {
        if (chemical == null || chemical.id == null) return;
        
        try {
            ResourceLocation location = chemical.id;
            int color = chemical.color;
            ResourceLocation texture = getChemicalTexture(chemical.id.toString(), chemical.type);
            
            com.mojang.logging.LogUtils.getLogger().info("renderChemicalSlot: id={}, color={:#x}, texture={}, type={}", chemical.id, color, texture, chemical.type);
            
            if (texture == null) {
                return;
            }
            
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g2 = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            
            RenderSystem.setShaderColor(r, g2, b, 1.0f);
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
            
            if (sprite == null || sprite.contents().name().toString().contains("missingno")) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                return;
            }
            
            RenderSystem.setShaderTexture(0, sprite.atlasLocation());
            
            int width = 16;
            int height = 16;
            
            int textureWidth = 16;
            int textureHeight = 16;
            
            int xTileCount = width / textureWidth;
            int xRemainder = width - (xTileCount * textureWidth);
            int yTileCount = height / textureHeight;
            int yRemainder = height - (yTileCount * textureHeight);
            int yStart = y + height;
            
            float uMin = sprite.getU0();
            float uMax = sprite.getU1();
            float vMin = sprite.getV0();
            float vMax = sprite.getV1();
            float uDif = uMax - uMin;
            float vDif = vMax - vMin;
            
            RenderSystem.enableBlend();
            
            com.mojang.blaze3d.vertex.BufferBuilder vertexBuffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            vertexBuffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            org.joml.Matrix4f matrix4f = g.pose().last().pose();
            
            for (int xTile = 0; xTile <= xTileCount; xTile++) {
                int tileWidth = (xTile == xTileCount) ? xRemainder : textureWidth;
                if (tileWidth == 0) {
                    break;
                }
                int tileX = x + (xTile * textureWidth);
                int maskRight = textureWidth - tileWidth;
                int shiftedX = tileX + textureWidth - maskRight;
                float uLocalDif = uDif * maskRight / textureWidth;
                float uLocalMin = uMin;
                float uLocalMax = uMax - uLocalDif;
                
                for (int yTile = 0; yTile <= yTileCount; yTile++) {
                    int tileHeight = (yTile == yTileCount) ? yRemainder : textureHeight;
                    if (tileHeight == 0) {
                        break;
                    }
                    int tileY = yStart - ((yTile + 1) * textureHeight);
                    int maskTop = textureHeight - tileHeight;
                    float vLocalDif = vDif * maskTop / textureHeight;
                    float vLocalMin = vMin + vLocalDif;
                    float vLocalMax = vMax;
                    
                    vertexBuffer.vertex(matrix4f, tileX, tileY + textureHeight, 0).uv(uLocalMin, vLocalMax).endVertex();
                    vertexBuffer.vertex(matrix4f, shiftedX, tileY + textureHeight, 0).uv(uLocalMax, vLocalMax).endVertex();
                    vertexBuffer.vertex(matrix4f, shiftedX, tileY + maskTop, 0).uv(uLocalMax, vLocalMin).endVertex();
                    vertexBuffer.vertex(matrix4f, tileX, tileY + maskTop, 0).uv(uLocalMin, vLocalMin).endVertex();
                }
            }
            
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(vertexBuffer.end());
            RenderSystem.disableBlend();
            
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            
        } catch (Exception e) {
            // 渲染失败，使用简单的颜色填充
            com.mojang.logging.LogUtils.getLogger().error("renderChemicalSlot failed for {}", chemical.id, e);
            int color = chemical.color;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g2 = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            
            RenderSystem.setShaderColor(r, g2, b, 1.0f);
            g.fill(x, y, x + 16, y + 16, 0xFFFFFFFF);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
    
    private ResourceLocation getChemicalTexture(String chemicalId, RecipePreviewRenderer.ContentType type) {
        if (chemicalId == null || chemicalId.isEmpty()) {
            return null;
        }
        
        try {
            ResourceLocation location = ResourceLocation.parse(chemicalId);
            
            if (type == RecipePreviewRenderer.ContentType.GAS) {
                mekanism.api.chemical.gas.Gas gas = mekanism.api.MekanismAPI.gasRegistry().getValue(location);
                if (gas != null && !gas.isEmptyType()) {
                    return gas.getIcon();
                }
            } else if (type == RecipePreviewRenderer.ContentType.INFUSE_TYPE) {
                mekanism.api.chemical.infuse.InfuseType infuseType = mekanism.api.MekanismAPI.infuseTypeRegistry().getValue(location);
                if (infuseType != null && !infuseType.isEmptyType()) {
                    return infuseType.getIcon();
                }
            } else if (type == RecipePreviewRenderer.ContentType.PIGMENT) {
                mekanism.api.chemical.pigment.Pigment pigment = mekanism.api.MekanismAPI.pigmentRegistry().getValue(location);
                if (pigment != null && !pigment.isEmptyType()) {
                    return pigment.getIcon();
                }
            } else if (type == RecipePreviewRenderer.ContentType.SLURRY) {
                mekanism.api.chemical.slurry.Slurry slurry = mekanism.api.MekanismAPI.slurryRegistry().getValue(location);
                if (slurry != null && !slurry.isEmptyType()) {
                    return slurry.getIcon();
                }
            }
        } catch (Exception e) {
        }
        
        return null;
    }

    // ── 栏2：配方列表 ────────────────────────────────────────────
    private void renderCol2(GuiGraphics g, int mx, int my) {
        if (targetItem.isEmpty()) {
            g.drawCenteredString(font, "§8← 请先选择对象",
                    c2x+COL2_W/2, contentY+contentH/2, 0x444455);
            return;
        }

        g.drawString(font,
                "§7② 选择配方  §8(" + recipeList.size() + "个)",
                c2x, contentY-10, 0xAAAAAA, false);

        // 列表背景
        g.fill(c2x-1, contentY-1, c2x+COL2_W+1, contentY+contentH+1, 0xFF333340);
        g.fill(c2x,   contentY,   c2x+COL2_W,   contentY+contentH,   0xFF0C0C18);

        if (recipeList.isEmpty()) {
            g.drawCenteredString(font, "§8无含此物品的配方",
                    c2x+COL2_W/2, contentY+contentH/2, 0x444455);
            return;
        }

        int maxS = Math.max(0, recipeList.size() - listVisRows);
        recipeScroll = clamp(recipeScroll, 0, maxS);

        // 分组标题（产物组 / 原料组）
        boolean shownResTitle = false, shownIngTitle = false;
        int drawn = 0;
        for (int i = recipeScroll; i < recipeList.size() && drawn < listVisRows; i++) {
            RecipeEntry e = recipeList.get(i);
            int ry = contentY + drawn * ROW_H;

            // 分组标题行（占1行）
            if (e.asResult && !shownResTitle) {
                shownResTitle = true;
                g.fill(c2x, ry, c2x+COL2_W, ry+ROW_H, 0xFF1A1A30);
                g.drawString(font, "§b▶ 作为产物", c2x+4, ry+6, 0x88AAFF, false);
                drawn++; ry = contentY + drawn * ROW_H;
                if (drawn >= listVisRows) break;
            }
            if (!e.asResult && !shownIngTitle) {
                shownIngTitle = true;
                g.fill(c2x, ry, c2x+COL2_W, ry+ROW_H, 0xFF1A1A30);
                g.drawString(font, "§e▶ 作为原料", c2x+4, ry+6, 0xFFCC44, false);
                drawn++; ry = contentY + drawn * ROW_H;
                if (drawn >= listVisRows) break;
            }

            boolean sel = i == selectedIdx;
            boolean hov = mx>=c2x && mx<c2x+COL2_W-3 && my>=ry && my<ry+ROW_H;
            g.fill(c2x, ry, c2x+COL2_W, ry+ROW_H-1,
                    sel ? 0xFF1E3A5A : hov ? 0xFF1A2030 : 0xFF0C0C18);

            // 左色条
            g.fill(c2x, ry, c2x+3, ry+ROW_H-1,
                    e.asResult ? 0xFF4488FF : 0xFFFFCC44);

            // 产物图标
            if (!e.result.isEmpty()) {
                RenderSystem.enableDepthTest();
                g.renderItem(e.result, c2x+5, ry+1);
                RenderSystem.disableDepthTest();
            }

            // 配方ID（截断）
            String label = e.id.getPath();
            int maxTW = COL2_W - SLOT - 14;
            while (font.width(label) > maxTW && label.length() > 4)
                label = label.substring(0, label.length()-1);
            if (!label.equals(e.id.getPath())) label += "…";
            g.drawString(font, "§f" + label, c2x+SLOT+9, ry+3, 0xFFFFFF, false);
            g.drawString(font, "§8" + e.typeName + "  §7" + e.id.getNamespace(),
                    c2x+SLOT+9, ry+12, 0xFFFFFF, false);
            drawn++;
        }

        // 滚动条（基于行数，包含分组标题）
        renderSB(g, c2x+COL2_W-3, contentY, 3, contentH,
                recipeList.size() + 2, listVisRows, recipeScroll);
    }

    // ── 栏3：配方预览 ────────────────────────────────────────────
    private void renderCol3(GuiGraphics g, int mx, int my) {
        g.drawString(font, "§7③ 预览", c3x, contentY-10, 0xAAAAAA, false);

        // 背景
        g.fill(c3x-1, contentY-1, c3x+col3W+1, contentY+contentH+1, 0xFF333340);
        g.fill(c3x,   contentY,   c3x+col3W,   contentY+contentH,   0xFF0C0C18);

        if (selectedIdx < 0 || selectedIdx >= recipeList.size()) {
            g.drawCenteredString(font, "§8← 请先选择配方",
                    c3x+col3W/2, contentY+contentH/2, 0x444455);
            return;
        }

        RecipeEntry entry = recipeList.get(selectedIdx);

        // 配方类型标题
        g.fill(c3x, contentY, c3x+col3W, contentY+14, 0xFF111130);
        g.drawString(font, "§7" + entry.typeName + "  §8" + entry.id.getNamespace(),
                c3x+4, contentY+3, 0xAAAAAA, false);

        // 使用RecipePreviewRenderer渲染槽位
        for (PreviewSlot slot : currentRecipeSlots) {
            RecipePreviewRenderer.renderSlot(g, slot, false);
        }
        
        // 渲染结果槽
        if (currentResultSlot != null) {
            RecipePreviewRenderer.renderSlot(g, currentResultSlot, true);
        }
        
        // 渲染箭头
        if (!currentRecipeSlots.isEmpty() && currentResultSlot != null) {
            // 计算箭头位置
            int lastInputX = 0;
            int lastInputY = 0;
            for (PreviewSlot slot : currentRecipeSlots) {
                if (slot.x > lastInputX) {
                    lastInputX = slot.x;
                    lastInputY = slot.y;
                }
            }
            
            int arrowX = lastInputX + SLOT + 4;
            int arrowY = lastInputY + SLOT / 2 - 4;
            
            // 绘制箭头
            g.drawString(font, "§e→", arrowX, arrowY, 0xFFAA00, false);
        }

        // 配方ID信息
        if (entry != null) {
            int infoY = contentY + contentH - 30;
            g.drawString(font, "§7ID: §f" + entry.id.toString(),
                    c3x + 4, infoY, 0xAAAAAA, false);
        }
    }

    private void renderSB(GuiGraphics g, int x, int y, int w, int h,
                          int total, int visible, int scroll) {
        if (total <= visible) return;
        int maxS   = total - visible;
        int thumbH = Math.max(8, h * visible / total);
        int thumbY = y + (maxS > 0 ? scroll * (h-thumbH) / maxS : 0);
        g.fill(x, y, x+w, y+h, 0xFF333333);
        g.fill(x, thumbY, x+w, thumbY+thumbH, 0xFF7799FF);
    }

    // ── 输入 ─────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int gridTop = contentY + 18;
        int gridW   = ITEM_COLS*(SLOT+2);
        int gridH   = ITEM_ROWS*(SLOT+2);
        int sbX = c1x + gridW + 1;  // 滚动条X位置
        int sbW = 3;  // 滚动条宽度
        
        // ── 滚动条点击检测 ────────────────────────────────────────
        // 物品滚动条
        int maxItemScroll = Math.max(0, (filteredItems.size()+ITEM_COLS-1)/ITEM_COLS-ITEM_ROWS);
        if (maxItemScroll > 0 && mx >= sbX && mx < sbX + sbW && my >= gridTop && my < gridTop + gridH) {
            draggingItemScroll = true;
            dragScrollY = (int)my;
            return true;
        }
        
        // 流体滚动条
        int fluidTop = gridTop + gridH + 36;
        int fluidGridH = FLUID_ROWS * (SLOT+2);
        int fluidGridTop = fluidTop + 2;
        int maxFluidScroll = Math.max(0, (filteredFluids.size()+ITEM_COLS-1)/ITEM_COLS-FLUID_ROWS);
        if (maxFluidScroll > 0 && mx >= sbX && mx < sbX + sbW && my >= fluidGridTop && my < fluidGridTop + fluidGridH) {
            draggingFluidScroll = true;
            dragScrollY = (int)my;
            return true;
        }
        
        // 化学品滚动条
        int chemicalTop = fluidGridTop + fluidGridH + 36;
        int chemicalGridH = CHEMICAL_ROWS * (SLOT+2);
        int chemicalGridTop = chemicalTop + 2;
        int maxChemicalScroll = Math.max(0, (filteredChemicals.size()+ITEM_COLS-1)/ITEM_COLS-CHEMICAL_ROWS);
        if (maxChemicalScroll > 0 && mx >= sbX && mx < sbX + sbW && my >= chemicalGridTop && my < chemicalGridTop + chemicalGridH) {
            draggingChemicalScroll = true;
            dragScrollY = (int)my;
            return true;
        }
        
        // 配方列表滚动条
        int maxRecipeScroll = Math.max(0, recipeList.size() - listVisRows + 2);
        int recipeSbX = c2x + COL2_W - 3;
        if (maxRecipeScroll > 0 && mx >= recipeSbX && mx < recipeSbX + 3 && my >= contentY && my < contentY + contentH) {
            draggingRecipeScroll = true;
            dragScrollY = (int)my;
            return true;
        }
        
        // ── 网格点击检测 ────────────────────────────────────────────
        // 物品网格
        if (mx>=c1x && mx<c1x+gridW && my>=gridTop && my<gridTop+gridH) {
            int col = ((int)mx-c1x)/(SLOT+2);
            int row = ((int)my-gridTop)/(SLOT+2);
            int idx = (row+itemScroll)*ITEM_COLS+col;
            if (idx >= 0 && idx < filteredItems.size()) {
                pickItem(filteredItems.get(idx)); return true;
            }
        }
        
        // 流体网格
        if (mx>=c1x && mx<c1x+gridW && my>=fluidGridTop && my<fluidGridTop+fluidGridH) {
            int col = ((int)mx-c1x)/(SLOT+2);
            int row = ((int)my-fluidGridTop)/(SLOT+2);
            int idx = (row+fluidScroll)*ITEM_COLS+col;
            if (idx >= 0 && idx < filteredFluids.size()) {
                targetFluid = filteredFluids.get(idx).copy();
                targetItem = ItemStack.EMPTY;  // 清除物品选择
                targetChemical = null;  // 清除化学品选择
                return true;
            }
        }
        
        // 化学品网格
        if (mx>=c1x && mx<c1x+gridW && my>=chemicalGridTop && my<chemicalGridTop+chemicalGridH) {
            int col = ((int)mx-c1x)/(SLOT+2);
            int row = ((int)my-chemicalGridTop)/(SLOT+2);
            int idx = (row+chemicalScroll)*ITEM_COLS+col;
            if (idx >= 0 && idx < filteredChemicals.size()) {
                targetChemical = filteredChemicals.get(idx);
                targetItem = ItemStack.EMPTY;  // 清除物品选择
                targetFluid = FluidStack.EMPTY;  // 清除流体选择
                return true;
            }
        }
        
        // 配方列表（需要把显示行映射回数据行）
        if (!targetItem.isEmpty() && mx>=c2x && mx<c2x+COL2_W-3
                && my>=contentY && my<contentY+contentH) {
            int clickRow = ((int)my-contentY)/ROW_H;
            // 重建 drawn→dataIdx 映射
            int drawn=0; boolean shownR=false, shownI=false;
            for (int i=recipeScroll; i<recipeList.size() && drawn<listVisRows; i++) {
                RecipeEntry e = recipeList.get(i);
                if (e.asResult && !shownR) { shownR=true; drawn++; if(drawn>listVisRows) break; }
                if (!e.asResult && !shownI) { shownI=true; drawn++; if(drawn>listVisRows) break; }
                if (drawn-1 == clickRow || drawn == clickRow) {
                    if (i == selectedIdx && btn==0) { doClone(); return true; }
                    selectRecipe(i); return true;
                }
                if (drawn == clickRow+1) { selectRecipe(i); return true; }
                drawn++;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }
    
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        int gridTop = contentY + 18;
        int gridH   = ITEM_ROWS*(SLOT+2);
        
        // 物品滚动条拖动
        if (draggingItemScroll) {
            int maxScroll = Math.max(0, (filteredItems.size()+ITEM_COLS-1)/ITEM_COLS-ITEM_ROWS);
            if (maxScroll > 0) {
                int thumbH = Math.max(8, gridH * ITEM_ROWS / ((filteredItems.size()+ITEM_COLS-1)/ITEM_COLS));
                int scrollableH = gridH - thumbH;
                if (scrollableH > 0) {
                    int deltaY = (int)my - dragScrollY;
                    int deltaScroll = (int)((deltaY * maxScroll) / (double)scrollableH);
                    itemScroll = clamp(itemScroll + deltaScroll, 0, maxScroll);
                    dragScrollY = (int)my;
                }
            }
            return true;
        }
        
        // 流体滚动条拖动
        if (draggingFluidScroll) {
            int fluidGridH = FLUID_ROWS * (SLOT+2);
            int maxScroll = Math.max(0, (filteredFluids.size()+ITEM_COLS-1)/ITEM_COLS-FLUID_ROWS);
            if (maxScroll > 0) {
                int thumbH = Math.max(8, fluidGridH * FLUID_ROWS / ((filteredFluids.size()+ITEM_COLS-1)/ITEM_COLS));
                int scrollableH = fluidGridH - thumbH;
                if (scrollableH > 0) {
                    int deltaY = (int)my - dragScrollY;
                    int deltaScroll = (int)((deltaY * maxScroll) / (double)scrollableH);
                    fluidScroll = clamp(fluidScroll + deltaScroll, 0, maxScroll);
                    dragScrollY = (int)my;
                }
            }
            return true;
        }
        
        // 化学品滚动条拖动
        if (draggingChemicalScroll) {
            int chemicalGridH = CHEMICAL_ROWS * (SLOT+2);
            int maxScroll = Math.max(0, (filteredChemicals.size()+ITEM_COLS-1)/ITEM_COLS-CHEMICAL_ROWS);
            if (maxScroll > 0) {
                int thumbH = Math.max(8, chemicalGridH * CHEMICAL_ROWS / ((filteredChemicals.size()+ITEM_COLS-1)/ITEM_COLS));
                int scrollableH = chemicalGridH - thumbH;
                if (scrollableH > 0) {
                    int deltaY = (int)my - dragScrollY;
                    int deltaScroll = (int)((deltaY * maxScroll) / (double)scrollableH);
                    chemicalScroll = clamp(chemicalScroll + deltaScroll, 0, maxScroll);
                    dragScrollY = (int)my;
                }
            }
            return true;
        }
        
        // 配方列表滚动条拖动
        if (draggingRecipeScroll) {
            int maxScroll = Math.max(0, recipeList.size() - listVisRows + 2);
            if (maxScroll > 0) {
                int thumbH = Math.max(8, contentH * listVisRows / (recipeList.size() + 2));
                int scrollableH = contentH - thumbH;
                if (scrollableH > 0) {
                    int deltaY = (int)my - dragScrollY;
                    int deltaScroll = (int)((deltaY * maxScroll) / (double)scrollableH);
                    recipeScroll = clamp(recipeScroll + deltaScroll, 0, maxScroll);
                    dragScrollY = (int)my;
                }
            }
            return true;
        }
        
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }
    
    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingItemScroll = false;
        draggingFluidScroll = false;
        draggingChemicalScroll = false;
        draggingRecipeScroll = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int gridTop = contentY + 18;
        int gridW   = ITEM_COLS*(SLOT+2);
        int gridH   = ITEM_ROWS*(SLOT+2);
        
        // 物品滚动
        if (mx>=c1x && mx<c1x+gridW && my>=gridTop && my<gridTop+gridH) {
            int max = Math.max(0,(filteredItems.size()+ITEM_COLS-1)/ITEM_COLS-ITEM_ROWS);
            itemScroll = clamp(itemScroll-(int)delta, 0, max); return true;
        }
        
        // 流体滚动
        int fluidTop = gridTop + gridH + 36;
        int fluidGridH = FLUID_ROWS * (SLOT+2);
        int fluidGridTop = fluidTop + 2;
        if (mx>=c1x && mx<c1x+gridW && my>=fluidGridTop && my<fluidGridTop+fluidGridH) {
            int max = Math.max(0,(filteredFluids.size()+ITEM_COLS-1)/ITEM_COLS-FLUID_ROWS);
            fluidScroll = clamp(fluidScroll-(int)delta, 0, max); return true;
        }
        
        // 化学品滚动
        int chemicalTop = fluidGridTop + fluidGridH + 36;
        int chemicalGridH = CHEMICAL_ROWS * (SLOT+2);
        int chemicalGridTop = chemicalTop + 2;
        if (mx>=c1x && mx<c1x+gridW && my>=chemicalGridTop && my<chemicalGridTop+chemicalGridH) {
            int max = Math.max(0,(filteredChemicals.size()+ITEM_COLS-1)/ITEM_COLS-CHEMICAL_ROWS);
            chemicalScroll = clamp(chemicalScroll-(int)delta, 0, max); return true;
        }
        
        // 配方列表滚动
        if (!targetItem.isEmpty() && mx>=c2x && mx<c2x+COL2_W) {
            recipeScroll = clamp(recipeScroll-(int)delta, 0,
                    Math.max(0, recipeList.size()-listVisRows+2)); return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mods) {
        if (searchBox.isFocused()) return searchBox.keyPressed(kc, sc, mods);
        if (fluidSearchBox.isFocused()) return fluidSearchBox.keyPressed(kc, sc, mods);
        if (chemicalSearchBox.isFocused()) return chemicalSearchBox.keyPressed(kc, sc, mods);
        if (kc == 264 && selectedIdx < recipeList.size()-1) { selectRecipe(selectedIdx+1); return true; }
        if (kc == 265 && selectedIdx > 0)                   { selectRecipe(selectedIdx-1); return true; }
        if (kc == 257 || kc == 335)                          { doClone(); return true; }
        if (kc == 256) { onClose(); return true; }
        return super.keyPressed(kc, sc, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (searchBox.isFocused()) return searchBox.charTyped(c, mods);
        if (fluidSearchBox.isFocused()) return fluidSearchBox.charTyped(c, mods);
        if (chemicalSearchBox.isFocused()) return chemicalSearchBox.charTyped(c, mods);
        return super.charTyped(c, mods);
    }

    @Override
    public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── 数据类 ───────────────────────────────────────────────────
    /**
     * 化学品条目（用于显示气体、灌注类型、颜料、矿泥）
     */
    private static class ChemicalEntry {
        final ResourceLocation id;
        final RecipePreviewRenderer.ContentType type;
        final int color;
        
        ChemicalEntry(ResourceLocation id, RecipePreviewRenderer.ContentType type, int color) {
            this.id = id;
            this.type = type;
            this.color = color;
        }
        
        String getDisplayName() {
            if (id == null) return "Unknown";
            String path = id.getPath();
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
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ChemicalEntry other)) return false;
            return Objects.equals(id, other.id) && type == other.type;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(id, type);
        }
    }
    
    private static class RecipeEntry {
        final ResourceLocation id;
        final Recipe<?>        recipe;
        final ItemStack        result;
        final String           typeName;
        final boolean          asIngredient, asResult;

        RecipeEntry(Recipe<?> r, boolean asIng, boolean asRes) {
            this.recipe      = r;
            this.id          = r.getId();
            this.asIngredient = asIng;
            this.asResult    = asRes;
            this.typeName    = classify(r);
            ItemStack res = ItemStack.EMPTY;
            try { res = r.getResultItem(Minecraft.getInstance().level.registryAccess()).copy(); }
            catch (Exception ignored) {}
            this.result = res;
        }

        private static String classify(Recipe<?> r) {
            String t = r.getType().toString().toLowerCase();
            if (t.contains("shaped"))    return "有序合成";
            if (t.contains("shapeless")) return "无序合成";
            if (t.contains("smelting"))  return "熔炼";
            if (t.contains("blasting"))  return "高炉";
            if (t.contains("smoking"))   return "烟熏";
            if (t.contains("campfire"))  return "营火";
            return t.replaceAll(".*:", "");
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
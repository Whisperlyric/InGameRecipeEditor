package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.gui.util.RecipePreviewRenderer;
import com.wzz.registerhelper.gui.util.RecipePreviewRenderer.PreviewSlot;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.slurry.Slurry;
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
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int W  = 700;
    private static final int H  = 440;
    private static final int PAD = 8;

    // 三栏宽度
    private static final int COL1_W = 180;   // 物品选择
    private static final int COL2_W = 220;   // 配方列表
    // COL3 = 剩余宽度

    private static final int SLOT   = 18;
    private static final int SLOT_SPACING = 21;  // 槽位间距
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
        targetFluid = FluidStack.EMPTY;
        targetChemical = null;
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
                String recipeTypeName = r.getType().toString().toLowerCase();
                
                // 检查Mekanism配方中的物品输入/输出
                if (recipeTypeName.contains("mekanism") || recipeTypeName.contains("sawing") ||
                    recipeTypeName.contains("sawmill") || recipeTypeName.contains("energy_conversion") ||
                    recipeTypeName.contains("reaction") || recipeTypeName.contains("pressurized") ||
                    recipeTypeName.contains("combining") || recipeTypeName.contains("crushing") ||
                    recipeTypeName.contains("enriching") || recipeTypeName.contains("purifying") ||
                    recipeTypeName.contains("injecting") || recipeTypeName.contains("compressing") ||
                    recipeTypeName.contains("painting") || recipeTypeName.contains("nucleosynthesizing") ||
                    recipeTypeName.contains("smelting") || recipeTypeName.contains("metallurgic") ||
                    recipeTypeName.contains("infuser") || recipeTypeName.contains("infusion_conversion") ||
                    recipeTypeName.contains("crystallizing")) {
                    try {
                        // 检查物品输入 - 尝试多种方法名
                        String[] itemInputMethods = {"getInput", "getItemInput", "getMainInput", "getInputSolid"};
                        for (String methodName : itemInputMethods) {
                            if (asIng) break;
                            try {
                                java.lang.reflect.Method getInputMethod = r.getClass().getMethod(methodName);
                                Object input = getInputMethod.invoke(r);
                                if (input instanceof mekanism.api.recipes.ingredients.ItemStackIngredient itemIngredient) {
                                    List<net.minecraft.world.item.ItemStack> matchingStacks = itemIngredient.getRepresentations();
                                    for (net.minecraft.world.item.ItemStack matching : matchingStacks) {
                                        if (ItemStack.isSameItem(matching, item)) {
                                            asIng = true;
                                            break;
                                        }
                                    }
                                }
                            } catch (Exception e) {}
                        }
                        
                        // 检查物品输出 - 尝试多种方法名
                        if (!asIng) {
                            String[] itemOutputMethods = {"getOutputDefinition", "getMainOutputDefinition", 
                                "getOutput", "getSecondaryOutputDefinition"};
                            for (String methodName : itemOutputMethods) {
                                if (asRes) break;
                                try {
                                    java.lang.reflect.Method getOutputMethod = r.getClass().getMethod(methodName);
                                    Object outputDef = getOutputMethod.invoke(r);
                                    
                                    if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                                        for (Object output : outputList) {
                                            if (output instanceof net.minecraft.world.item.ItemStack outputItem && !outputItem.isEmpty()) {
                                                if (ItemStack.isSameItem(outputItem, item)) {
                                                    asRes = true;
                                                    break;
                                                }
                                            }
                                        }
                                    } else if (outputDef instanceof net.minecraft.world.item.ItemStack outputItem && !outputItem.isEmpty()) {
                                        if (ItemStack.isSameItem(outputItem, item)) {
                                            asRes = true;
                                        }
                                    }
                                } catch (Exception e) {}
                            }
                        }
                    } catch (Exception e) {}
                }
                
                // 原版配方检查（作为后备）
                if (!asIng && !asRes) {
                    try {
                        ItemStack res = r.getResultItem(Minecraft.getInstance().level.registryAccess());
                        if (ItemStack.isSameItem(res, item)) asRes = true;
                        for (Ingredient ing : r.getIngredients()) {
                            if (!ing.isEmpty()) for (ItemStack m : ing.getItems())
                                if (ItemStack.isSameItem(m, item)) { asIng = true; break; }
                            if (asIng) break;
                        }
                    } catch (Exception e) {}
                }
                
                if ((asIng || asRes) && seen.add(r.getId()))
                    recipeList.add(new RecipeEntry(r, asIng, asRes));
            } catch (Exception ignored) {}
        }
        // 排序：产物在前，原料在后
        recipeList.sort(Comparator.comparingInt(e -> (e.asResult ? 0 : 1)));
    }
    
    // ── 选中流体 → 加载配方 ──────────────────────────────────────
    private void pickFluid(FluidStack fluid) {
        targetFluid = fluid.copy();
        targetItem = ItemStack.EMPTY;
        targetChemical = null;
        recipeList.clear();
        selectedIdx   = -1;
        recipeScroll  = 0;
        currentRecipeSlots.clear();
        currentResultSlot = null;

        RecipeManager rm = getRM();
        if (rm == null) return;

        Fluid targetFluidType = fluid.getFluid();
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (Recipe<?> r : rm.getRecipes()) {
            try {
                boolean asIng = false, asRes = false;
                String recipeTypeName = r.getType().toString().toLowerCase();
                
                // 检查所有Mekanism配方中的流体输入/输出
                if (recipeTypeName.contains("mekanism") || recipeTypeName.contains("rotary") || 
                    recipeTypeName.contains("condensentrator") || recipeTypeName.contains("reaction") ||
                    recipeTypeName.contains("sawing") || recipeTypeName.contains("evaporating") ||
                    recipeTypeName.contains("separating") || recipeTypeName.contains("energy_conversion")) {
                    try {
                        // 检查流体输入 - 尝试多种方法名
                        String[] fluidInputMethods = {"getFluidInput", "getInputFluid", "getInput"};
                        for (String methodName : fluidInputMethods) {
                            if (asIng) break;
                            try {
                                java.lang.reflect.Method getFluidInput = r.getClass().getMethod(methodName);
                                Object fluidInput = getFluidInput.invoke(r);
                                if (fluidInput instanceof mekanism.api.recipes.ingredients.FluidStackIngredient fluidIngredient) {
                                    List<FluidStack> matchingStacks = fluidIngredient.getRepresentations();
                                    for (FluidStack matching : matchingStacks) {
                                        if (matching.getFluid() == targetFluidType) {
                                            asIng = true;
                                            break;
                                        }
                                    }
                                }
                            } catch (Exception e) {}
                        }
                        
                        // 检查流体输出
                        if (!asIng) {
                            String[] fluidOutputMethods = {"getFluidOutputDefinition", "getOutputDefinition"};
                            for (String methodName : fluidOutputMethods) {
                                if (asRes) break;
                                try {
                                    java.lang.reflect.Method getFluidOutputDef = r.getClass().getMethod(methodName);
                                    Object outputDef = getFluidOutputDef.invoke(r);
                                    if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                                        for (Object output : outputList) {
                                            if (output instanceof FluidStack outputFluid) {
                                                if (outputFluid.getFluid() == targetFluidType) {
                                                    asRes = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {}
                            }
                        }
                    } catch (Exception e) {}
                }
                
                if ((asIng || asRes) && seen.add(r.getId()))
                    recipeList.add(new RecipeEntry(r, asIng, asRes));
            } catch (Exception ignored) {}
        }
        recipeList.sort(Comparator.comparingInt(e -> (e.asResult ? 0 : 1)));
    }
    
    // ── 选中化学品 → 加载配方 ──────────────────────────────────────
    private void pickChemical(ChemicalEntry chemical) {
        targetChemical = chemical;
        targetItem = ItemStack.EMPTY;
        targetFluid = FluidStack.EMPTY;
        recipeList.clear();
        selectedIdx   = -1;
        recipeScroll  = 0;
        currentRecipeSlots.clear();
        currentResultSlot = null;

        RecipeManager rm = getRM();
        if (rm == null) return;

        ResourceLocation chemicalId = chemical.id;
        RecipePreviewRenderer.ContentType chemicalType = chemical.type;
        
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (Recipe<?> r : rm.getRecipes()) {
            try {
                boolean asIng = false, asRes = false;
                String recipeTypeName = r.getType().toString().toLowerCase();
                
                // 检查所有可能包含化学品的配方类型
                if (recipeTypeName.contains("mekanism") || recipeTypeName.contains("rotary") ||
                    recipeTypeName.contains("condensentrator") || recipeTypeName.contains("reaction") ||
                    recipeTypeName.contains("sawing") || recipeTypeName.contains("evaporating") ||
                    recipeTypeName.contains("separating") || recipeTypeName.contains("energy_conversion") ||
                    recipeTypeName.contains("nucleosynthesizing") || recipeTypeName.contains("injecting") ||
                    recipeTypeName.contains("compressing") || recipeTypeName.contains("painting") ||
                    recipeTypeName.contains("enriching") || recipeTypeName.contains("purifying") ||
                    recipeTypeName.contains("crushing") || recipeTypeName.contains("smelting")) {
                    try {
                        // 回旋式气液转换器特殊处理
                        if (recipeTypeName.contains("rotary") || recipeTypeName.contains("condensentrator")) {
                            try {
                                java.lang.reflect.Method hasFluidToGas = r.getClass().getMethod("hasFluidToGas");
                                java.lang.reflect.Method hasGasToFluid = r.getClass().getMethod("hasGasToFluid");
                                
                                boolean canFluidToGas = (boolean) hasFluidToGas.invoke(r);
                                boolean canGasToFluid = (boolean) hasGasToFluid.invoke(r);
                                
                                // 气体→流体模式：检查气体输入
                                if (canGasToFluid) {
                                    java.lang.reflect.Method getGasInput = r.getClass().getMethod("getGasInput");
                                    Object gasInput = getGasInput.invoke(r);
                                    if (gasInput != null) {
                                        try {
                                            java.lang.reflect.Method getRepresentations = gasInput.getClass().getMethod("getRepresentations");
                                            List<?> matchingStacks = (List<?>) getRepresentations.invoke(gasInput);
                                            
                                            for (Object stack : matchingStacks) {
                                                ResourceLocation foundId = getChemicalIdFromStack(stack);
                                                if (foundId != null && foundId.equals(chemicalId)) {
                                                    asIng = true;
                                                    break;
                                                }
                                            }
                                        } catch (Exception e) {}
                                    }
                                }
                                
                                // 流体→气体模式：检查气体输出
                                if (canFluidToGas && !asIng) {
                                    java.lang.reflect.Method getGasOutputDef = r.getClass().getMethod("getGasOutputDefinition");
                                    Object gasOutputDef = getGasOutputDef.invoke(r);
                                    // 处理List类型的输出
                                    if (gasOutputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                                        for (Object output : outputList) {
                                            ResourceLocation foundId = getChemicalIdFromOutput(output);
                                            if (foundId != null && foundId.equals(chemicalId)) {
                                                asRes = true;
                                                break;
                                            }
                                        }
                                    } 
                                    // 处理单个GasStack输出
                                    else if (gasOutputDef instanceof mekanism.api.chemical.gas.GasStack gasStack) {
                                        ResourceLocation foundId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                                        if (foundId != null && foundId.equals(chemicalId)) {
                                            asRes = true;
                                        }
                                    }
                                }
                            } catch (Exception e) {}
                        } else if (recipeTypeName.contains("reaction") || recipeTypeName.contains("pressurized")) {
                            // 加压反应室特殊处理
                            try {
                                // 检查气体输入
                                java.lang.reflect.Method getInputGas = r.getClass().getMethod("getInputGas");
                                Object gasInput = getInputGas.invoke(r);
                                if (gasInput != null) {
                                    try {
                                        java.lang.reflect.Method getRepresentations = gasInput.getClass().getMethod("getRepresentations");
                                        List<?> matchingStacks = (List<?>) getRepresentations.invoke(gasInput);
                                        
                                        for (Object stack : matchingStacks) {
                                            ResourceLocation foundId = getChemicalIdFromStack(stack);
                                            if (foundId != null && foundId.equals(chemicalId)) {
                                                asIng = true;
                                                break;
                                            }
                                        }
                                    } catch (Exception e) {}
                                }
                                
                                // 检查气体输出（通过getOutputDefinition获取，然后调用gas()方法）
                                if (!asIng) {
                                    java.lang.reflect.Method getOutputDef = r.getClass().getMethod("getOutputDefinition");
                                    Object outputDef = getOutputDef.invoke(r);
                                    if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                                        Object outputObj = outputList.get(0);
                                        try {
                                            java.lang.reflect.Method gasMethod = outputObj.getClass().getMethod("gas");
                                            Object gasOutput = gasMethod.invoke(outputObj);
                                            if (gasOutput instanceof mekanism.api.chemical.gas.GasStack gasStack && !gasStack.isEmpty()) {
                                                ResourceLocation foundId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                                                if (foundId != null && foundId.equals(chemicalId)) {
                                                    asRes = true;
                                                }
                                            }
                                        } catch (Exception e) {}
                                    }
                                }
                            } catch (Exception e) {}
                        } else if (recipeTypeName.contains("separating")) {
                            // 电解分离器特殊处理
                            try {
                                // 检查气体输出（通过getOutputDefinition获取ChemicalPair，然后调用left()和right()方法）
                                java.lang.reflect.Method getOutputDef = r.getClass().getMethod("getOutputDefinition");
                                Object outputDef = getOutputDef.invoke(r);
                                if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                                    Object outputObj = outputList.get(0);
                                    try {
                                        // 尝试获取left和right气体输出
                                        java.lang.reflect.Method getLeft = outputObj.getClass().getMethod("left");
                                        java.lang.reflect.Method getRight = outputObj.getClass().getMethod("right");
                                        
                                        Object leftGas = getLeft.invoke(outputObj);
                                        Object rightGas = getRight.invoke(outputObj);
                                        
                                        // 检查左侧气体输出
                                        if (leftGas instanceof mekanism.api.chemical.gas.GasStack leftGasStack && !leftGasStack.isEmpty()) {
                                            ResourceLocation foundId = mekanism.api.MekanismAPI.gasRegistry().getKey(leftGasStack.getType());
                                            if (foundId != null && foundId.equals(chemicalId)) {
                                                asRes = true;
                                            }
                                        }
                                        
                                        // 检查右侧气体输出
                                        if (!asRes && rightGas instanceof mekanism.api.chemical.gas.GasStack rightGasStack && !rightGasStack.isEmpty()) {
                                            ResourceLocation foundId = mekanism.api.MekanismAPI.gasRegistry().getKey(rightGasStack.getType());
                                            if (foundId != null && foundId.equals(chemicalId)) {
                                                asRes = true;
                                            }
                                        }
                                    } catch (Exception e) {
                                        // 如果不是ChemicalPair，尝试直接作为GasStack处理
                                        if (outputObj instanceof mekanism.api.chemical.gas.GasStack gasStack && !gasStack.isEmpty()) {
                                            ResourceLocation foundId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                                            if (foundId != null && foundId.equals(chemicalId)) {
                                                asRes = true;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {}
                        } else {
                            // 检查化学品输入 - 尝试多种方法名
                            String[] inputMethodNames = {
                                "getChemicalInput", "getGasInput", "getInfuseInput", 
                                "getSlurryInput", "getPigmentInput", "getLeftInput", "getRightInput",
                                "getInput"  // 通用输入方法
                            };
                            
                            for (String methodName : inputMethodNames) {
                                if (asIng) break;
                                try {
                                    java.lang.reflect.Method inputMethod = r.getClass().getMethod(methodName);
                                    Object input = inputMethod.invoke(r);
                                    if (input != null) {
                                        // 尝试获取representations
                                        try {
                                            java.lang.reflect.Method getRepresentations = input.getClass().getMethod("getRepresentations");
                                            List<?> matchingStacks = (List<?>) getRepresentations.invoke(input);
                                            
                                            for (Object stack : matchingStacks) {
                                                ResourceLocation foundId = getChemicalIdFromStack(stack);
                                                if (foundId != null && foundId.equals(chemicalId)) {
                                                    asIng = true;
                                                    break;
                                                }
                                            }
                                        } catch (Exception e) {}
                                    }
                                } catch (Exception e) {}
                            }
                            
                            // 检查化学品输出 - 尝试多种方法名
                            if (!asIng) {
                                String[] outputMethodNames = {
                                    "getOutputDefinition", "getGasOutputDefinition", 
                                    "getOutput"  // 通用输出方法
                                };
                                
                                for (String methodName : outputMethodNames) {
                                    if (asRes) break;
                                    try {
                                        java.lang.reflect.Method outputMethod = r.getClass().getMethod(methodName);
                                        Object outputDef = outputMethod.invoke(r);
                                        
                                        // 处理List输出
                                        if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                                            for (Object output : outputList) {
                                                ResourceLocation foundId = getChemicalIdFromOutput(output);
                                                if (foundId != null && foundId.equals(chemicalId)) {
                                                    asRes = true;
                                                    break;
                                                }
                                            }
                                        }
                                        // 处理单个ChemicalStack输出
                                        else if (outputDef instanceof mekanism.api.chemical.ChemicalStack<?> chemicalStack) {
                                            ResourceLocation foundId = getChemicalIdFromChemical(chemicalStack.getType());
                                            if (foundId != null && foundId.equals(chemicalId)) {
                                                asRes = true;
                                            }
                                        }
                                        // 处理GasStack输出
                                        else if (outputDef instanceof mekanism.api.chemical.gas.GasStack gasStack) {
                                            ResourceLocation foundId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                                            if (foundId != null && foundId.equals(chemicalId)) {
                                                asRes = true;
                                            }
                                        }
                                    } catch (Exception e) {}
                                }
                            }
                        }
                    } catch (Exception e) {}
                }
                
                if ((asIng || asRes) && seen.add(r.getId()))
                    recipeList.add(new RecipeEntry(r, asIng, asRes));
            } catch (Exception ignored) {}
        }
        recipeList.sort(Comparator.comparingInt(e -> (e.asResult ? 0 : 1)));
    }
    
    /**
     * 从化学品堆栈获取化学品ID
     */
    private ResourceLocation getChemicalIdFromStack(Object stack) {
        try {
            java.lang.reflect.Method getType = stack.getClass().getMethod("getType");
            Object chem = getType.invoke(stack);
            return getChemicalIdFromChemical(chem);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 从输出对象获取化学品ID
     */
    private ResourceLocation getChemicalIdFromOutput(Object output) {
        if (output instanceof mekanism.api.chemical.ChemicalStack<?> chemicalStack) {
            return getChemicalIdFromChemical(chemicalStack.getType());
        } else if (output instanceof mekanism.api.chemical.gas.GasStack gasStack) {
            return mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
        } else if (output instanceof mekanism.api.chemical.merged.BoxedChemicalStack boxedStack) {
            mekanism.api.chemical.ChemicalStack<?> chemicalStack = boxedStack.getChemicalStack();
            return getChemicalIdFromChemical(chemicalStack.getType());
        }
        // 尝试通过反射获取类型
        try {
            java.lang.reflect.Method getType = output.getClass().getMethod("getType");
            Object chem = getType.invoke(output);
            return getChemicalIdFromChemical(chem);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 从化学品对象获取ID
     */
    private ResourceLocation getChemicalIdFromChemical(Object chem) {
        if (chem instanceof mekanism.api.chemical.gas.Gas gas) {
            return mekanism.api.MekanismAPI.gasRegistry().getKey(gas);
        } else if (chem instanceof mekanism.api.chemical.infuse.InfuseType infuseType) {
            return mekanism.api.MekanismAPI.infuseTypeRegistry().getKey(infuseType);
        } else if (chem instanceof mekanism.api.chemical.pigment.Pigment pigment) {
            return mekanism.api.MekanismAPI.pigmentRegistry().getKey(pigment);
        } else if (chem instanceof mekanism.api.chemical.slurry.Slurry slurry) {
            return mekanism.api.MekanismAPI.slurryRegistry().getKey(slurry);
        }
        return null;
    }
    
    /**
     * 从化学品栈创建输出槽
     * @param chemicalStack 化品栈对象
     * @return 创建的PreviewSlot，如果无法创建则返回null
     */
    private PreviewSlot createChemicalOutputSlot(Object chemicalStack) {
        if (chemicalStack == null) return null;
        
        try {
            Object chemical = null;
            long amount = 0;
            
            // 处理BoxedChemicalStack
            if (chemicalStack instanceof mekanism.api.chemical.merged.BoxedChemicalStack boxedStack) {
                mekanism.api.chemical.ChemicalStack<?> stack = boxedStack.getChemicalStack();
                chemical = stack.getType();
                amount = stack.getAmount();
            } 
            // 处理ChemicalStack
            else if (chemicalStack instanceof mekanism.api.chemical.ChemicalStack<?> stack) {
                chemical = stack.getType();
                amount = stack.getAmount();
            }
            // 处理GasStack
            else if (chemicalStack instanceof mekanism.api.chemical.gas.GasStack gasStack) {
                chemical = gasStack.getType();
                amount = gasStack.getAmount();
            }
            
            if (chemical == null) return null;
            
            ResourceLocation chemicalId = getChemicalIdFromChemical(chemical);
            if (chemicalId == null) return null;
            
            RecipePreviewRenderer.ContentType type = RecipePreviewRenderer.ContentType.GAS;
            if (chemical instanceof mekanism.api.chemical.infuse.InfuseType) {
                type = RecipePreviewRenderer.ContentType.INFUSE_TYPE;
            } else if (chemical instanceof mekanism.api.chemical.pigment.Pigment) {
                type = RecipePreviewRenderer.ContentType.PIGMENT;
            } else if (chemical instanceof mekanism.api.chemical.slurry.Slurry) {
                type = RecipePreviewRenderer.ContentType.SLURRY;
            }
            
            return new PreviewSlot(0, 0, chemicalId.toString(), type, amount);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 从化学品输入Ingredient创建输入槽
     * @param chemicalInput 化品输入Ingredient对象
     * @param slotX 槽位X位置
     * @param slotY 槽位Y位置
     * @return 创建的PreviewSlot，如果无法创建则返回null
     */
    private PreviewSlot createChemicalInputSlot(Object chemicalInput, int slotX, int slotY) {
        if (chemicalInput == null) return null;
        
        try {
            java.lang.reflect.Method getRepresentations = chemicalInput.getClass().getMethod("getRepresentations");
            List<?> matchingStacks = (List<?>) getRepresentations.invoke(chemicalInput);
            
            if (!matchingStacks.isEmpty()) {
                Object stack = matchingStacks.get(0);
                PreviewSlot slot = createChemicalOutputSlot(stack);
                if (slot != null) {
                    return new PreviewSlot(slotX, slotY, (String) slot.content, slot.type, slot.amount);
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    // ── 选中配方 → 更新预览 ──────────────────────────────────────
    private void selectRecipe(int idx) {
        selectedIdx = idx;
        currentRecipeSlots.clear();
        currentResultSlot = null;
        if (idx < 0 || idx >= recipeList.size()) return;
        RecipeEntry entry = recipeList.get(idx);
        Recipe<?> r = entry.recipe;
        
        // 解析配方数据（传递entry用于判断化学品是作为输入还是输出）
        parseRecipe(r, entry);
    }
    
    /**
     * 计算输出槽位置（紧挨着箭头）
     * @param lastInputX 最后一个输入槽的X位置
     * @param inputY 输入槽的Y位置
     * @return 输出槽的PreviewSlot位置
     */
    private int[] calculateOutputPosition(int lastInputX, int inputY) {
        // 箭头宽度约10像素，间距4像素
        int arrowWidth = 10;
        int spacing = 4;
        int outputX = lastInputX + SLOT + spacing + arrowWidth + spacing;
        int outputY = inputY;
        return new int[]{outputX, outputY};
    }
    
    /**
     * 计算箭头位置
     * @param lastInputX 最后一个输入槽的X位置
     * @param inputY 输入槽的Y位置
     * @return 箭头的X和Y位置
     */
    private int[] calculateArrowPosition(int lastInputX, int inputY) {
        int spacing = 4;
        int arrowX = lastInputX + SLOT + spacing;
        int arrowY = inputY + SLOT / 2 - 4;  // 箭头居中对齐
        return new int[]{arrowX, arrowY};
    }
    
    /**
     * 获取最后一个输入槽的X位置
     */
    private int getLastInputX() {
        int lastX = c3x + 8;
        for (PreviewSlot slot : currentRecipeSlots) {
            // 忽略副输出槽（位置为(-1, -1)或(-2, -2)）
            if ((slot.x == -1 && slot.y == -1) || (slot.x == -2 && slot.y == -2)) continue;
            if (slot.x > lastX) {
                lastX = slot.x;
            }
        }
        return lastX;
    }
    
    /**
     * 获取输入槽的中心Y位置
     */
    private int getInputCenterY() {
        if (currentRecipeSlots.isEmpty()) {
            return contentY + 20;
        }
        // 找到最右侧的输入槽的Y位置（忽略副输出槽）
        int lastX = c3x + 8;
        int centerY = contentY + 20;
        for (PreviewSlot slot : currentRecipeSlots) {
            // 忽略副输出槽（位置为(-1, -1)或(-2, -2)）
            if ((slot.x == -1 && slot.y == -1) || (slot.x == -2 && slot.y == -2)) continue;
            if (slot.x > lastX) {
                lastX = slot.x;
                centerY = slot.y;
            }
        }
        return centerY;
    }
    
    /**
     * 创建一个新的PreviewSlot对象，使用指定位置
     * @param original 原始PreviewSlot
     * @param x 新的X位置
     * @param y 新的Y位置
     * @return 新的PreviewSlot对象
     */
    private PreviewSlot createSlotAtPosition(PreviewSlot original, int x, int y) {
        switch (original.type) {
            case ITEM:
                return new PreviewSlot(x, y, (net.minecraft.world.item.ItemStack) original.content);
            case FLUID:
                return new PreviewSlot(x, y, (FluidStack) original.content);
            case ENERGY:
                return new PreviewSlot(x, y, original.amount);
            case GAS, INFUSE_TYPE, PIGMENT, SLURRY:
                return new PreviewSlot(x, y, (String) original.content, original.type, original.amount);
            default:
                return original;
        }
    }
    
    /**
     * 解析配方数据（支持普通配方和Mekanism配方）
     */
    private void parseRecipe(Recipe<?> recipe) {
        parseRecipe(recipe, null);
    }
    
    /**
     * 解析配方数据（支持普通配方和Mekanism配方）
     * @param recipe 配方对象
     * @param entry 配方条目（用于判断化学品是作为输入还是输出）
     */
    private void parseRecipe(Recipe<?> recipe, RecipeEntry entry) {
        String recipeTypeName = recipe.getType().toString().toLowerCase();
        
        // 计算起始位置
        int startX = c3x + 8;
        int startY = contentY + 20;
        
        // 检查是否为Mekanism配方
        if (recipeTypeName.contains("mekanism") || recipeTypeName.contains("rotary") || 
            recipeTypeName.contains("condensentrator") || recipeTypeName.contains("metallurgic") ||
            recipeTypeName.contains("infuser") || recipeTypeName.contains("energy_conversion") ||
            recipeTypeName.contains("infusion_conversion") || recipeTypeName.contains("sawmill") ||
            recipeTypeName.contains("pressurized") || recipeTypeName.contains("separating") ||
            recipeTypeName.contains("chemical_infusing") || recipeTypeName.contains("pigment_mixing") ||
            recipeTypeName.contains("crystallizing") || recipeTypeName.contains("combining") ||
            recipeTypeName.contains("injecting") || recipeTypeName.contains("nucleosynthesizing") ||
            recipeTypeName.contains("compressing") || recipeTypeName.contains("painting") ||
            recipeTypeName.contains("enriching") || recipeTypeName.contains("purifying") ||
            recipeTypeName.contains("crushing") || recipeTypeName.contains("smelting") ||
            recipeTypeName.contains("reaction") || recipeTypeName.contains("sawing") ||
            recipeTypeName.contains("evaporating")) {
            parseMekanismRecipe(recipe, startX, startY, entry);
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
    private void parseMekanismRecipe(Recipe<?> recipe, int startX, int startY) {
        parseMekanismRecipe(recipe, startX, startY, null);
    }
    
    /**
     * 解析Mekanism配方
     * @param recipe 配方对象
     * @param startX 起始X位置
     * @param startY 起始Y位置
     * @param entry 配方条目（用于判断化学品是作为输入还是输出）
     */
    private void parseMekanismRecipe(Recipe<?> recipe, int startX, int startY, RecipeEntry entry) {
        try {
            int slotX = startX;
            int slotY = startY;
            
            String recipeTypeName = recipe.getType().toString().toLowerCase();
            
            // 电解分离器
            if (recipeTypeName.contains("separating")) {
                try {
                    // 清除默认的结果槽位，因为电解分离器有两个气体输出槽
                    currentResultSlot = null;
                    
                    java.lang.reflect.Method getInput = recipe.getClass().getMethod("getInput");
                    
                    Object input = getInput.invoke(recipe);
                    
                    // 流体输入槽
                    if (input instanceof mekanism.api.recipes.ingredients.FluidStackIngredient fluidIngredient) {
                        List<FluidStack> matchingStacks = fluidIngredient.getRepresentations();
                        if (!matchingStacks.isEmpty()) {
                            currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0)));
                        }
                    } else if (input instanceof FluidStack fluidStack) {
                        currentRecipeSlots.add(new PreviewSlot(slotX, slotY, fluidStack));
                    }
                    
                    // 获取气体输出定义
                    java.lang.reflect.Method getOutputDefinition = recipe.getClass().getMethod("getOutputDefinition");
                    Object outputDef = getOutputDefinition.invoke(recipe);
                    
                    if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                        Object outputObj = outputList.get(0);
                        
                        try {
                            java.lang.reflect.Method getLeft = outputObj.getClass().getMethod("left");
                            java.lang.reflect.Method getRight = outputObj.getClass().getMethod("right");
                            
                            Object leftGas = getLeft.invoke(outputObj);
                            Object rightGas = getRight.invoke(outputObj);
                            
                            // 左侧气体输出槽 - 使用特殊标记(-3, -3)
                            if (leftGas instanceof mekanism.api.chemical.gas.GasStack leftGasStack && !leftGasStack.isEmpty()) {
                                ResourceLocation leftGasId = mekanism.api.MekanismAPI.gasRegistry().getKey(leftGasStack.getType());
                                if (leftGasId != null) {
                                    currentRecipeSlots.add(new PreviewSlot(-3, -3, leftGasId.toString(), 
                                            RecipePreviewRenderer.ContentType.GAS, leftGasStack.getAmount()));
                                }
                            }
                            
                            // 右侧气体输出槽 - 使用特殊标记(-4, -4)
                            if (rightGas instanceof mekanism.api.chemical.gas.GasStack rightGasStack && !rightGasStack.isEmpty()) {
                                ResourceLocation rightGasId = mekanism.api.MekanismAPI.gasRegistry().getKey(rightGasStack.getType());
                                if (rightGasId != null) {
                                    currentRecipeSlots.add(new PreviewSlot(-4, -4, rightGasId.toString(), 
                                            RecipePreviewRenderer.ContentType.GAS, rightGasStack.getAmount()));
                                }
                            }
                        } catch (NoSuchMethodException e) {
                            // 如果不是ChemicalPair，尝试直接作为GasStack处理
                            if (outputObj instanceof mekanism.api.chemical.gas.GasStack gasStack && !gasStack.isEmpty()) {
                                ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                                if (gasId != null) {
                                    currentRecipeSlots.add(new PreviewSlot(-3, -3, gasId.toString(), 
                                            RecipePreviewRenderer.ContentType.GAS, gasStack.getAmount()));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("解析电解分离器配方失败: {}", e.getMessage());
                }
                return;
            }
            
            // 回旋式气液转换器
            if (recipeTypeName.contains("rotary") || recipeTypeName.contains("condensentrator")) {
                try {
                    // RotaryRecipe有两种转换模式：流体→气体 和 气体→流体
                    java.lang.reflect.Method hasFluidToGas = recipe.getClass().getMethod("hasFluidToGas");
                    java.lang.reflect.Method hasGasToFluid = recipe.getClass().getMethod("hasGasToFluid");
                    
                    boolean canFluidToGas = (boolean) hasFluidToGas.invoke(recipe);
                    boolean canGasToFluid = (boolean) hasGasToFluid.invoke(recipe);
                    
                    // 根据化学品是作为输入还是输出来决定显示哪种模式
                    // 如果化学品作为输入（asIngredient=true），显示气体→流体模式
                    // 如果化学品作为输出（asResult=true），显示流体→气体模式
                    boolean showGasToFluid = entry != null && entry.asIngredient;
                    boolean showFluidToGas = entry != null && entry.asResult;
                    
                    // 如果entry为null或无法判断，优先显示流体→气体转换
                    if (showFluidToGas || (!showGasToFluid && canFluidToGas)) {
                        java.lang.reflect.Method getFluidInput = recipe.getClass().getMethod("getFluidInput");
                        java.lang.reflect.Method getGasOutputDefinition = recipe.getClass().getMethod("getGasOutputDefinition");
                        
                        Object fluidInput = getFluidInput.invoke(recipe);
                        Object gasOutputDef = getGasOutputDefinition.invoke(recipe);
                        
                        // 流体输入槽
                        if (fluidInput instanceof mekanism.api.recipes.ingredients.FluidStackIngredient fluidIngredient) {
                            List<FluidStack> matchingStacks = fluidIngredient.getRepresentations();
                            if (!matchingStacks.isEmpty()) {
                                currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0)));
                            }
                        }
                        
                        // 气体输出槽 - 处理List和单个GasStack两种情况
                        if (gasOutputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                            Object output = outputList.get(0);
                            if (output instanceof mekanism.api.chemical.gas.GasStack outputGas) {
                                ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(outputGas.getType());
                                if (gasId != null) {
                                    currentResultSlot = new PreviewSlot(0, 0, gasId.toString(), 
                                            RecipePreviewRenderer.ContentType.GAS, outputGas.getAmount());
                                }
                            }
                        } else if (gasOutputDef instanceof mekanism.api.chemical.gas.GasStack outputGas) {
                            ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(outputGas.getType());
                            if (gasId != null) {
                                currentResultSlot = new PreviewSlot(0, 0, gasId.toString(), 
                                        RecipePreviewRenderer.ContentType.GAS, outputGas.getAmount());
                            }
                        }
                    } else if (showGasToFluid || canGasToFluid) {
                        java.lang.reflect.Method getGasInput = recipe.getClass().getMethod("getGasInput");
                        java.lang.reflect.Method getFluidOutputDefinition = recipe.getClass().getMethod("getFluidOutputDefinition");
                        
                        Object gasInput = getGasInput.invoke(recipe);
                        Object fluidOutputDef = getFluidOutputDefinition.invoke(recipe);
                        
                        // 气体输入槽
                        if (gasInput instanceof mekanism.api.recipes.ingredients.ChemicalStackIngredient.GasStackIngredient gasIngredient) {
                            List<mekanism.api.chemical.gas.GasStack> matchingStacks = gasIngredient.getRepresentations();
                            if (!matchingStacks.isEmpty()) {
                                mekanism.api.chemical.gas.GasStack gasStack = matchingStacks.get(0);
                                ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                                if (gasId != null) {
                                    currentRecipeSlots.add(new PreviewSlot(slotX, slotY, gasId.toString(), 
                                            RecipePreviewRenderer.ContentType.GAS, gasStack.getAmount()));
                                }
                            }
                        }
                        
                        // 流体输出槽 - 处理List和单个FluidStack两种情况
                        if (fluidOutputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                            Object output = outputList.get(0);
                            if (output instanceof FluidStack outputFluid) {
                                currentResultSlot = new PreviewSlot(0, 0, outputFluid);
                            }
                        } else if (fluidOutputDef instanceof FluidStack outputFluid) {
                            currentResultSlot = new PreviewSlot(0, 0, outputFluid);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("解析回旋式气液转换器配方失败: {}", e.getMessage());
                }
                return;
            }
            
            // 化学灌注器（冶金灌注机）
            if (recipeTypeName.contains("metallurgic") || recipeTypeName.contains("infuser")) {
                try {
                    java.lang.reflect.Method getItemInput = recipe.getClass().getMethod("getItemInput");
                    java.lang.reflect.Method getChemicalInput = recipe.getClass().getMethod("getChemicalInput");
                    java.lang.reflect.Method getOutputDefinition = recipe.getClass().getMethod("getOutputDefinition");
                    
                    Object itemInput = getItemInput.invoke(recipe);
                    Object chemicalInput = getChemicalInput.invoke(recipe);
                    
                    // 物品输入槽
                    if (itemInput instanceof mekanism.api.recipes.ingredients.ItemStackIngredient ingredient) {
                        List<net.minecraft.world.item.ItemStack> matchingStacks = ingredient.getRepresentations();
                        if (!matchingStacks.isEmpty()) {
                            currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0).copy()));
                            slotX += SLOT_SPACING;
                        }
                    }
                    
                    // 灌注类型输入槽（化学品输入）
                    if (chemicalInput != null) {
                        try {
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
                                
                                if (chemical instanceof mekanism.api.chemical.infuse.InfuseType infuseType) {
                                    ResourceLocation infuseId = mekanism.api.MekanismAPI.infuseTypeRegistry().getKey(infuseType);
                                    if (infuseId != null) {
                                        currentRecipeSlots.add(new PreviewSlot(slotX, slotY, infuseId.toString(), 
                                                RecipePreviewRenderer.ContentType.INFUSE_TYPE, amount));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("解析灌注类型输入失败: {}", e.getMessage());
                        }
                    }
                    
                    // 输出槽
                    Object outputDef = getOutputDefinition.invoke(recipe);
                    if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                        Object output = outputList.get(0);
                        if (output instanceof net.minecraft.world.item.ItemStack itemStack && !itemStack.isEmpty()) {
                            currentResultSlot = new PreviewSlot(0, 0, itemStack.copy());
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("解析化学灌注器配方失败: {}", e.getMessage());
                }
                return;
            }
            
            // 化学灌注器（Chemical Infusing）- 两个气体输入
            if (recipeTypeName.contains("chemical_infusing") || recipeTypeName.contains("chemicalinfusing")) {
                try {
                    java.lang.reflect.Method getLeftInput = recipe.getClass().getMethod("getLeftInput");
                    java.lang.reflect.Method getRightInput = recipe.getClass().getMethod("getRightInput");
                    java.lang.reflect.Method getOutputDefinition = recipe.getClass().getMethod("getOutputDefinition");
                    
                    Object leftInput = getLeftInput.invoke(recipe);
                    Object rightInput = getRightInput.invoke(recipe);
                    
                    // 左侧气体输入槽
                    if (leftInput instanceof mekanism.api.recipes.ingredients.ChemicalStackIngredient.GasStackIngredient leftGasIngredient) {
                        List<mekanism.api.chemical.gas.GasStack> matchingStacks = leftGasIngredient.getRepresentations();
                        if (!matchingStacks.isEmpty()) {
                            mekanism.api.chemical.gas.GasStack gasStack = matchingStacks.get(0);
                            ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                            if (gasId != null) {
                                currentRecipeSlots.add(new PreviewSlot(slotX, slotY, gasId.toString(), 
                                        RecipePreviewRenderer.ContentType.GAS, gasStack.getAmount()));
                                slotX += SLOT_SPACING;
                            }
                        }
                    }
                    
                    // 右侧气体输入槽
                    if (rightInput instanceof mekanism.api.recipes.ingredients.ChemicalStackIngredient.GasStackIngredient rightGasIngredient) {
                        List<mekanism.api.chemical.gas.GasStack> matchingStacks = rightGasIngredient.getRepresentations();
                        if (!matchingStacks.isEmpty()) {
                            mekanism.api.chemical.gas.GasStack gasStack = matchingStacks.get(0);
                            ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                            if (gasId != null) {
                                currentRecipeSlots.add(new PreviewSlot(slotX, slotY, gasId.toString(), 
                                        RecipePreviewRenderer.ContentType.GAS, gasStack.getAmount()));
                            }
                        }
                    }
                    
                    // 输出槽
                    Object outputDef = getOutputDefinition.invoke(recipe);
                    if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                        Object output = outputList.get(0);
                        if (output instanceof mekanism.api.chemical.gas.GasStack outputGas) {
                            ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(outputGas.getType());
                            if (gasId != null) {
                                currentResultSlot = new PreviewSlot(0, 0, gasId.toString(), 
                                        RecipePreviewRenderer.ContentType.GAS, outputGas.getAmount());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("解析化学灌注器配方失败: {}", e.getMessage());
                }
                return;
            }
            
            // 颜料混合器（Pigment Mixing）- 两个颜料输入
            if (recipeTypeName.contains("pigment_mixing") || recipeTypeName.contains("pigmentmixing")) {
                try {
                    java.lang.reflect.Method getLeftInput = recipe.getClass().getMethod("getLeftInput");
                    java.lang.reflect.Method getRightInput = recipe.getClass().getMethod("getRightInput");
                    java.lang.reflect.Method getOutputDefinition = recipe.getClass().getMethod("getOutputDefinition");
                    
                    Object leftInput = getLeftInput.invoke(recipe);
                    Object rightInput = getRightInput.invoke(recipe);
                    
                    // 左侧颜料输入槽
                    if (leftInput != null) {
                        try {
                            Class<?> inputClass = leftInput.getClass();
                            java.lang.reflect.Method getRepresentations = inputClass.getMethod("getRepresentations");
                            List<?> matchingStacks = (List<?>) getRepresentations.invoke(leftInput);
                            
                            if (!matchingStacks.isEmpty()) {
                                Object stack = matchingStacks.get(0);
                                Class<?> stackClass = stack.getClass();
                                java.lang.reflect.Method getType = stackClass.getMethod("getType");
                                java.lang.reflect.Method getAmount = stackClass.getMethod("getAmount");
                                
                                Object chemical = getType.invoke(stack);
                                long amount = (Long) getAmount.invoke(stack);
                                
                                if (chemical instanceof mekanism.api.chemical.pigment.Pigment pigment) {
                                    ResourceLocation pigmentId = mekanism.api.MekanismAPI.pigmentRegistry().getKey(pigment);
                                    if (pigmentId != null) {
                                        currentRecipeSlots.add(new PreviewSlot(slotX, slotY, pigmentId.toString(), 
                                                RecipePreviewRenderer.ContentType.PIGMENT, amount));
                                        slotX += SLOT_SPACING;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("解析左侧颜料输入失败: {}", e.getMessage());
                        }
                    }
                    
                    // 右侧颜料输入槽
                    if (rightInput != null) {
                        try {
                            Class<?> inputClass = rightInput.getClass();
                            java.lang.reflect.Method getRepresentations = inputClass.getMethod("getRepresentations");
                            List<?> matchingStacks = (List<?>) getRepresentations.invoke(rightInput);
                            
                            if (!matchingStacks.isEmpty()) {
                                Object stack = matchingStacks.get(0);
                                Class<?> stackClass = stack.getClass();
                                java.lang.reflect.Method getType = stackClass.getMethod("getType");
                                java.lang.reflect.Method getAmount = stackClass.getMethod("getAmount");
                                
                                Object chemical = getType.invoke(stack);
                                long amount = (Long) getAmount.invoke(stack);
                                
                                if (chemical instanceof mekanism.api.chemical.pigment.Pigment pigment) {
                                    ResourceLocation pigmentId = mekanism.api.MekanismAPI.pigmentRegistry().getKey(pigment);
                                    if (pigmentId != null) {
                                        currentRecipeSlots.add(new PreviewSlot(slotX, slotY, pigmentId.toString(), 
                                                RecipePreviewRenderer.ContentType.PIGMENT, amount));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("解析右侧颜料输入失败: {}", e.getMessage());
                        }
                    }
                    
                    // 输出槽
                    Object outputDef = getOutputDefinition.invoke(recipe);
                    if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                        Object output = outputList.get(0);
                        if (output instanceof mekanism.api.chemical.pigment.PigmentStack outputPigment) {
                            ResourceLocation pigmentId = mekanism.api.MekanismAPI.pigmentRegistry().getKey(outputPigment.getType());
                            if (pigmentId != null) {
                                currentResultSlot = new PreviewSlot(0, 0, pigmentId.toString(), 
                                        RecipePreviewRenderer.ContentType.PIGMENT, outputPigment.getAmount());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("解析颜料混合器配方失败: {}", e.getMessage());
                }
                return;
            }
            
            // 化学结晶器（Chemical Crystallizer）- 四种化学品通用输入
            if (recipeTypeName.contains("crystallizing")) {
                try {
                    java.lang.reflect.Method getInput = recipe.getClass().getMethod("getInput");
                    java.lang.reflect.Method getOutputDefinition = recipe.getClass().getMethod("getOutputDefinition");
                    
                    Object input = getInput.invoke(recipe);
                    
                    // 化学品输入槽（通用槽，支持气体、灌注类型、颜料、浆液）
                    if (input != null) {
                        try {
                            Class<?> inputClass = input.getClass();
                            java.lang.reflect.Method getRepresentations = inputClass.getMethod("getRepresentations");
                            List<?> matchingStacks = (List<?>) getRepresentations.invoke(input);
                            
                            if (!matchingStacks.isEmpty()) {
                                Object stack = matchingStacks.get(0);
                                Class<?> stackClass = stack.getClass();
                                java.lang.reflect.Method getType = stackClass.getMethod("getType");
                                java.lang.reflect.Method getAmount = stackClass.getMethod("getAmount");
                                
                                Object chemical = getType.invoke(stack);
                                long amount = (Long) getAmount.invoke(stack);
                                
                                ResourceLocation chemicalId = null;
                                RecipePreviewRenderer.ContentType chemicalType = RecipePreviewRenderer.ContentType.GAS;
                                
                                // 使用 instanceof 直接判断化学品类型
                                if (chemical instanceof mekanism.api.chemical.gas.Gas gas) {
                                    chemicalType = RecipePreviewRenderer.ContentType.GAS;
                                    chemicalId = mekanism.api.MekanismAPI.gasRegistry().getKey(gas);
                                } else if (chemical instanceof mekanism.api.chemical.infuse.InfuseType infuseType) {
                                    chemicalType = RecipePreviewRenderer.ContentType.INFUSE_TYPE;
                                    chemicalId = mekanism.api.MekanismAPI.infuseTypeRegistry().getKey(infuseType);
                                } else if (chemical instanceof mekanism.api.chemical.pigment.Pigment pigment) {
                                    chemicalType = RecipePreviewRenderer.ContentType.PIGMENT;
                                    chemicalId = mekanism.api.MekanismAPI.pigmentRegistry().getKey(pigment);
                                } else if (chemical instanceof mekanism.api.chemical.slurry.Slurry slurry) {
                                    chemicalType = RecipePreviewRenderer.ContentType.SLURRY;
                                    chemicalId = mekanism.api.MekanismAPI.slurryRegistry().getKey(slurry);
                                }
                                
                                if (chemicalId != null) {
                                    currentRecipeSlots.add(new PreviewSlot(slotX, slotY, chemicalId.toString(), chemicalType, amount));
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("解析化学品输入失败: {}", e.getMessage());
                        }
                    }
                    
                    // 输出槽
                    Object outputDef = getOutputDefinition.invoke(recipe);
                    if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                        Object output = outputList.get(0);
                        if (output instanceof net.minecraft.world.item.ItemStack itemStack && !itemStack.isEmpty()) {
                            currentResultSlot = new PreviewSlot(0, 0, itemStack.copy());
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("解析化学结晶器配方失败: {}", e.getMessage());
                }
                return;
            }
            
            // 融合机（Combiner）- 两个物品输入，一上一下排列
            if (recipeTypeName.contains("combining")) {
                try {
                    java.lang.reflect.Method getMainInput = recipe.getClass().getMethod("getMainInput");
                    java.lang.reflect.Method getExtraInput = recipe.getClass().getMethod("getExtraInput");
                    java.lang.reflect.Method getOutputDefinition = recipe.getClass().getMethod("getOutputDefinition");
                    
                    Object mainInput = getMainInput.invoke(recipe);
                    Object extraInput = getExtraInput.invoke(recipe);
                    
                    // 主输入槽（上方）
                    if (mainInput instanceof mekanism.api.recipes.ingredients.ItemStackIngredient mainIngredient) {
                        List<net.minecraft.world.item.ItemStack> matchingStacks = mainIngredient.getRepresentations();
                        if (!matchingStacks.isEmpty()) {
                            currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0).copy()));
                        }
                    }
                    
                    // 额外输入槽（下方）
                    if (extraInput instanceof mekanism.api.recipes.ingredients.ItemStackIngredient extraIngredient) {
                        List<net.minecraft.world.item.ItemStack> matchingStacks = extraIngredient.getRepresentations();
                        if (!matchingStacks.isEmpty()) {
                            currentRecipeSlots.add(new PreviewSlot(slotX, slotY + SLOT_SPACING, matchingStacks.get(0).copy()));
                        }
                    }
                    
                    // 输出槽
                    Object outputDef = getOutputDefinition.invoke(recipe);
                    if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                        Object output = outputList.get(0);
                        if (output instanceof net.minecraft.world.item.ItemStack itemStack && !itemStack.isEmpty()) {
                            currentResultSlot = new PreviewSlot(0, 0, itemStack.copy());
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("解析融合机配方失败: {}", e.getMessage());
                }
                return;
            }
            
            if (recipeTypeName.contains("energy_conversion")) {
                try {
                    java.lang.reflect.Method getInput = recipe.getClass().getMethod("getInput");
                    
                    Object input = getInput.invoke(recipe);
                    if (input instanceof mekanism.api.recipes.ingredients.ItemStackIngredient ingredient) {
                        List<net.minecraft.world.item.ItemStack> matchingStacks = ingredient.getRepresentations();
                        if (!matchingStacks.isEmpty()) {
                            currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0).copy()));
                        }
                    }
                    
                    try {
                        java.lang.reflect.Method getOutputDefinition = recipe.getClass().getMethod("getOutputDefinition");
                        Object outputDef = getOutputDefinition.invoke(recipe);
                        
                        if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                            Object output = outputList.get(0);
                            long energyValue = 0;
                            
                            if (output instanceof Long energyOutput) {
                                energyValue = energyOutput;
                            } else if (output instanceof mekanism.api.math.FloatingLong floatingLong) {
                                energyValue = floatingLong.longValue();
                            } else {
                                LOGGER.warn("Energy conversion: output is not Long or FloatingLong, class={}", output.getClass().getName());
                            }
                            
                            if (energyValue > 0) {
                                currentResultSlot = new PreviewSlot(0, 0, energyValue);
                                LOGGER.info("Energy conversion: Created PreviewSlot with amount={}", energyValue);
                            }
                        } else {
                            LOGGER.warn("Energy conversion: outputDef is not List or is empty");
                        }
                    } catch (Exception e) {
                        LOGGER.warn("解析能量转换配方输出失败: {}", e.getMessage());
                    }
                } catch (Exception e) {
                    LOGGER.warn("解析能量转换配方失败: {}", e.getMessage());
                }
                return;
            }
            
            if (recipeTypeName.contains("infusion_conversion")) {
                try {
                    java.lang.reflect.Method getInput = recipe.getClass().getMethod("getInput");
                    java.lang.reflect.Method getOutputDefinition = recipe.getClass().getMethod("getOutputDefinition");
                    
                    Object input = getInput.invoke(recipe);
                    if (input instanceof mekanism.api.recipes.ingredients.ItemStackIngredient ingredient) {
                        List<net.minecraft.world.item.ItemStack> matchingStacks = ingredient.getRepresentations();
                        if (!matchingStacks.isEmpty()) {
                            currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0).copy()));
                        }
                    }
                    
                    Object outputDef = getOutputDefinition.invoke(recipe);
                    if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                        Object output = outputList.get(0);
                        if (output instanceof mekanism.api.chemical.ChemicalStack<?> chemicalStack) {
                            Object chemical = chemicalStack.getType();
                            long amount = chemicalStack.getAmount();
                            
                            ResourceLocation chemicalId = null;
                            
                            // 直接检查是否是InfuseType实例
                            if (chemical instanceof mekanism.api.chemical.infuse.InfuseType infuseType) {
                                chemicalId = mekanism.api.MekanismAPI.infuseTypeRegistry().getKey(infuseType);
                                LOGGER.info("Infusion conversion: chemicalId = {}", chemicalId);
                            } else {
                                LOGGER.info("Infusion conversion: chemical is not InfuseType, class = {}", chemical.getClass().getName());
                            }
                            
                            if (chemicalId != null) {
                                currentResultSlot = new PreviewSlot(0, 0, chemicalId.toString(), RecipePreviewRenderer.ContentType.INFUSE_TYPE, amount);
                                LOGGER.info("Infusion conversion: Created PreviewSlot with id={}, type={}, amount={}", chemicalId, RecipePreviewRenderer.ContentType.INFUSE_TYPE, amount);
                            } else {
                                LOGGER.warn("Infusion conversion: chemicalId is null!");
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("解析灌注类型转换配方失败: {}", e.getMessage());
                }
                return;
            }
            
            if (recipe instanceof mekanism.api.recipes.SawmillRecipe sawmillRecipe) {
                try {
                    java.lang.reflect.Method getInput = sawmillRecipe.getClass().getMethod("getInput");
                    java.lang.reflect.Method getMainOutputDefinition = sawmillRecipe.getClass().getMethod("getMainOutputDefinition");
                    java.lang.reflect.Method getSecondaryOutputDefinition = sawmillRecipe.getClass().getMethod("getSecondaryOutputDefinition");
                    
                    Object input = getInput.invoke(sawmillRecipe);
                    if (input instanceof mekanism.api.recipes.ingredients.ItemStackIngredient ingredient) {
                        List<net.minecraft.world.item.ItemStack> matchingStacks = ingredient.getRepresentations();
                        if (!matchingStacks.isEmpty()) {
                            currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0).copy()));
                        }
                    }
                    
                    Object mainOutput = getMainOutputDefinition.invoke(sawmillRecipe);
                    Object secondaryOutput = getSecondaryOutputDefinition.invoke(sawmillRecipe);
                    
                    // 主输出槽
                    if (mainOutput instanceof List<?> mainList && !mainList.isEmpty()) {
                        Object output = mainList.get(0);
                        if (output instanceof net.minecraft.world.item.ItemStack itemStack && !itemStack.isEmpty()) {
                            currentResultSlot = new PreviewSlot(0, 0, itemStack.copy());
                        }
                    }
                    
                    // 副输出槽 - 位置在主输出槽下方（使用临时标记，渲染时会调整位置）
                    if (secondaryOutput instanceof List<?> secondaryList && !secondaryList.isEmpty()) {
                        Object output = secondaryList.get(0);
                        if (output instanceof net.minecraft.world.item.ItemStack itemStack && !itemStack.isEmpty()) {
                            // 使用特殊标记位置，在渲染时调整
                            currentRecipeSlots.add(new PreviewSlot(-1, -1, itemStack.copy()));
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("解析SawmillRecipe失败: {}", e.getMessage());
                }
                return;
            }
            
            if (recipe instanceof mekanism.api.recipes.PressurizedReactionRecipe pressurizedRecipe) {
                try {
                    java.lang.reflect.Method getInputSolid = pressurizedRecipe.getClass().getMethod("getInputSolid");
                    java.lang.reflect.Method getInputFluid = pressurizedRecipe.getClass().getMethod("getInputFluid");
                    java.lang.reflect.Method getInputGas = pressurizedRecipe.getClass().getMethod("getInputGas");
                    java.lang.reflect.Method getOutputDefinition = pressurizedRecipe.getClass().getMethod("getOutputDefinition");
                    
                    Object inputSolid = getInputSolid.invoke(pressurizedRecipe);
                    if (inputSolid instanceof mekanism.api.recipes.ingredients.ItemStackIngredient ingredient) {
                        List<net.minecraft.world.item.ItemStack> matchingStacks = ingredient.getRepresentations();
                        if (!matchingStacks.isEmpty()) {
                            currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0).copy()));
                            slotX += SLOT_SPACING;
                        }
                    }
                    
                    Object inputFluid = getInputFluid.invoke(pressurizedRecipe);
                    if (inputFluid instanceof mekanism.api.recipes.ingredients.FluidStackIngredient fluidIngredient) {
                        List<FluidStack> matchingStacks = fluidIngredient.getRepresentations();
                        if (!matchingStacks.isEmpty()) {
                            currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0)));
                            slotX += SLOT_SPACING;
                        }
                    }
                    
                    Object inputGas = getInputGas.invoke(pressurizedRecipe);
                    if (inputGas instanceof mekanism.api.recipes.ingredients.ChemicalStackIngredient.GasStackIngredient gasIngredient) {
                        List<mekanism.api.chemical.gas.GasStack> matchingStacks = gasIngredient.getRepresentations();
                        if (!matchingStacks.isEmpty()) {
                            mekanism.api.chemical.gas.GasStack gasStack = matchingStacks.get(0);
                            ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                            if (gasId != null) {
                                currentRecipeSlots.add(new PreviewSlot(slotX, slotY, gasId.toString(), 
                                        RecipePreviewRenderer.ContentType.GAS, gasStack.getAmount()));
                            }
                        }
                    }
                    
                    Object outputDef = getOutputDefinition.invoke(pressurizedRecipe);
                    
                    if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                        Object outputObj = outputList.get(0);
                        
                        try {
                            java.lang.reflect.Method itemMethod = outputObj.getClass().getMethod("item");
                            java.lang.reflect.Method gasMethod = outputObj.getClass().getMethod("gas");
                            
                            Object itemOutput = itemMethod.invoke(outputObj);
                            Object gasOutput = gasMethod.invoke(outputObj);
                            
                            // 物品输出作为主输出槽
                            if (itemOutput instanceof net.minecraft.world.item.ItemStack itemStack && !itemStack.isEmpty()) {
                                currentResultSlot = new PreviewSlot(0, 0, itemStack.copy());
                            }
                            
                            // 气体输出作为副输出槽，位置在主输出右侧（使用特殊标记）
                            if (gasOutput instanceof mekanism.api.chemical.gas.GasStack gasStack && !gasStack.isEmpty()) {
                                ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                                if (gasId != null) {
                                    // 使用特殊标记位置(-2, -2)表示气体副输出槽，渲染时会放在主输出右侧
                                    currentRecipeSlots.add(new PreviewSlot(-2, -2, gasId.toString(), 
                                            RecipePreviewRenderer.ContentType.GAS, gasStack.getAmount()));
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("解析输出失败: {}", e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("解析PressurizedReactionRecipe失败: {}", e.getMessage());
                }
                return;
            }
            
            if (recipe instanceof mekanism.api.recipes.MekanismRecipe mekanismRecipe) {
                try {
                    java.lang.reflect.Method getInput = null;
                    java.lang.reflect.Method getItemInput = null;
                    java.lang.reflect.Method getGasInput = null;
                    java.lang.reflect.Method getFluidInput = null;
                    java.lang.reflect.Method getChemicalInput = null;
                    java.lang.reflect.Method getOutput = null;
                    java.lang.reflect.Method getOutputDefinition = null;
                    
                    for (java.lang.reflect.Method method : recipe.getClass().getMethods()) {
                        String methodName = method.getName();
                        if (methodName.equals("getInput")) {
                            getInput = method;
                        } else if (methodName.equals("getItemInput")) {
                            getItemInput = method;
                        } else if (methodName.equals("getGasInput")) {
                            getGasInput = method;
                        } else if (methodName.equals("getFluidInput")) {
                            getFluidInput = method;
                        } else if (methodName.equals("getChemicalInput")) {
                            getChemicalInput = method;
                        } else if (methodName.equals("getOutput")) {
                            getOutput = method;
                        } else if (methodName.equals("getOutputDefinition")) {
                            getOutputDefinition = method;
                        }
                    }
                    
                    if (getInput != null) {
                        try {
                            Object input = getInput.invoke(recipe);
                            if (input instanceof mekanism.api.recipes.ingredients.ItemStackIngredient ingredient) {
                                List<net.minecraft.world.item.ItemStack> matchingStacks = ingredient.getRepresentations();
                                if (!matchingStacks.isEmpty()) {
                                    currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0).copy()));
                                    slotX += SLOT_SPACING;
                                }
                            } else if (input instanceof mekanism.api.recipes.ingredients.FluidStackIngredient fluidIngredient) {
                                List<FluidStack> matchingStacks = fluidIngredient.getRepresentations();
                                if (!matchingStacks.isEmpty()) {
                                    currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0)));
                                    slotX += SLOT_SPACING;
                                }
                            } else if (input instanceof mekanism.api.recipes.ingredients.ChemicalStackIngredient.GasStackIngredient gasIngredient) {
                                List<mekanism.api.chemical.gas.GasStack> matchingStacks = gasIngredient.getRepresentations();
                                if (!matchingStacks.isEmpty()) {
                                    mekanism.api.chemical.gas.GasStack gasStack = matchingStacks.get(0);
                                    ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                                    if (gasId != null) {
                                        currentRecipeSlots.add(new PreviewSlot(slotX, slotY, gasId.toString(), 
                                                RecipePreviewRenderer.ContentType.GAS, gasStack.getAmount()));
                                        slotX += SLOT_SPACING;
                                    }
                                }
                            } else if (input != null) {
                                PreviewSlot slot = createChemicalInputSlot(input, slotX, slotY);
                                if (slot != null) {
                                    currentRecipeSlots.add(slot);
                                    slotX += SLOT_SPACING;
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                    
                    if (getItemInput != null) {
                        try {
                            Object itemInput = getItemInput.invoke(recipe);
                            if (itemInput instanceof mekanism.api.recipes.ingredients.ItemStackIngredient ingredient) {
                                List<net.minecraft.world.item.ItemStack> matchingStacks = ingredient.getRepresentations();
                                if (!matchingStacks.isEmpty()) {
                                    currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0).copy()));
                                    slotX += SLOT_SPACING;
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                    
                    if (getGasInput != null) {
                        try {
                            Object gasInput = getGasInput.invoke(recipe);
                            if (gasInput instanceof mekanism.api.recipes.ingredients.ChemicalStackIngredient.GasStackIngredient gasIngredient) {
                                List<mekanism.api.chemical.gas.GasStack> matchingStacks = gasIngredient.getRepresentations();
                                if (!matchingStacks.isEmpty()) {
                                    mekanism.api.chemical.gas.GasStack gasStack = matchingStacks.get(0);
                                    ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                                    if (gasId != null) {
                                        currentRecipeSlots.add(new PreviewSlot(slotX, slotY, gasId.toString(), 
                                                RecipePreviewRenderer.ContentType.GAS, gasStack.getAmount()));
                                        slotX += SLOT_SPACING;
                                    }
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                    
                    if (getFluidInput != null) {
                        try {
                            Object fluidInput = getFluidInput.invoke(recipe);
                            if (fluidInput instanceof mekanism.api.recipes.ingredients.FluidStackIngredient fluidIngredient) {
                                List<FluidStack> matchingStacks = fluidIngredient.getRepresentations();
                                if (!matchingStacks.isEmpty()) {
                                    currentRecipeSlots.add(new PreviewSlot(slotX, slotY, matchingStacks.get(0)));
                                    slotX += SLOT_SPACING;
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                    
                    if (getChemicalInput != null) {
                        try {
                            Object chemicalInput = getChemicalInput.invoke(recipe);
                            PreviewSlot slot = createChemicalInputSlot(chemicalInput, slotX, slotY);
                            if (slot != null) {
                                currentRecipeSlots.add(slot);
                                slotX += SLOT_SPACING;
                            }
                        } catch (Exception e) {
                        }
                    }
                    
                    if (getOutput != null) {
                        try {
                            Object output = getOutput.invoke(recipe);
                            if (output instanceof net.minecraft.world.item.ItemStack itemStack) {
                                currentResultSlot = new PreviewSlot(0, 0, itemStack.copy());
                            } else if (output instanceof FluidStack fluidStack) {
                                currentResultSlot = new PreviewSlot(0, 0, fluidStack.copy());
                            } else {
                                PreviewSlot slot = createChemicalOutputSlot(output);
                                if (slot != null) {
                                    currentResultSlot = slot;
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                    
                    if (getOutputDefinition != null) {
                        try {
                            Object outputDef = getOutputDefinition.invoke(recipe);
                            // 处理List输出
                            if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                                Object output = outputList.get(0);
                                if (output instanceof FluidStack fluidStack) {
                                    currentResultSlot = new PreviewSlot(0, 0, fluidStack.copy());
                                } else if (output instanceof net.minecraft.world.item.ItemStack itemStack && !itemStack.isEmpty()) {
                                    currentResultSlot = new PreviewSlot(0, 0, itemStack.copy());
                                } else {
                                    PreviewSlot slot = createChemicalOutputSlot(output);
                                    if (slot != null) {
                                        currentResultSlot = slot;
                                    }
                                }
                            } 
                            // 处理单个输出对象（非List）
                            else if (outputDef instanceof FluidStack fluidStack) {
                                currentResultSlot = new PreviewSlot(0, 0, fluidStack.copy());
                            } else if (outputDef instanceof net.minecraft.world.item.ItemStack itemStack && !itemStack.isEmpty()) {
                                currentResultSlot = new PreviewSlot(0, 0, itemStack.copy());
                            } else {
                                PreviewSlot slot = createChemicalOutputSlot(outputDef);
                                if (slot != null) {
                                    currentResultSlot = slot;
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                    
                } catch (Exception e) {
                    LOGGER.warn("解析Mekanism配方失败: {}", e.getMessage());
                }
            }
            
            List<Ingredient> ingredients = recipe.getIngredients();
            if (!ingredients.isEmpty() && currentRecipeSlots.isEmpty()) {
                for (Ingredient ingredient : ingredients) {
                    if (!ingredient.isEmpty()) {
                        ItemStack[] items = ingredient.getItems();
                        if (items.length > 0) {
                            currentRecipeSlots.add(new PreviewSlot(slotX, slotY, items[0].copy()));
                            slotX += SLOT_SPACING;
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            LOGGER.warn("解析Mekanism配方失败: {}", e.getMessage());
            List<Ingredient> ingredients = recipe.getIngredients();
            int slotX = startX;

            for (Ingredient ingredient : ingredients) {
                if (!ingredient.isEmpty()) {
                    ItemStack[] items = ingredient.getItems();
                    if (items.length > 0) {
                        currentRecipeSlots.add(new PreviewSlot(slotX, startY, items[0].copy()));
                        slotX += SLOT_SPACING;
                    }
                }
            }
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
        // 检查是否有选中的对象（物品、流体或化学品）
        boolean hasSelection = !targetItem.isEmpty() || !targetFluid.isEmpty() || targetChemical != null;
        
        if (!hasSelection) {
            g.drawCenteredString(font, "§8← 请先选择对象",
                    c2x+COL2_W/2, contentY+contentH/2, 0x444455);
            return;
        }

        // 显示选中对象的类型
        String selectionType = !targetItem.isEmpty() ? "物品" : 
                               !targetFluid.isEmpty() ? "流体" : "化学品";
        g.drawString(font,
                "§7② 选择配方  §8(" + recipeList.size() + "个) §7[" + selectionType + "]",
                c2x, contentY-10, 0xAAAAAA, false);

        // 列表背景
        g.fill(c2x-1, contentY-1, c2x+COL2_W+1, contentY+contentH+1, 0xFF333340);
        g.fill(c2x,   contentY,   c2x+COL2_W,   contentY+contentH,   0xFF0C0C18);

        if (recipeList.isEmpty()) {
            g.drawCenteredString(font, "§8无含此对象的配方",
                    c2x+COL2_W/2, contentY+contentH/2, 0x444455);
            return;
        }

        int maxS = Math.max(0, recipeList.size() - listVisRows + 2);
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

            // 产物图标（支持多种类型）
            try {
                Object preview = e.outputPreview;
                
                if (preview instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                    RenderSystem.enableDepthTest();
                    g.renderItem(itemStack, c2x+5, ry+1);
                    RenderSystem.disableDepthTest();
                } else if (preview instanceof FluidStack fluidStack && !fluidStack.isEmpty()) {
                    renderFluidPreview(g, fluidStack, c2x+5, ry+1);
                } else if (preview instanceof mekanism.api.chemical.gas.GasStack gasStack) {
                    renderGasPreview(g, gasStack, c2x+5, ry+1);
                } else if (preview instanceof mekanism.api.chemical.ChemicalStack<?> chemicalStack) {
                    renderChemicalStackPreview(g, chemicalStack, c2x+5, ry+1);
                } else if (preview instanceof mekanism.api.chemical.infuse.InfuseType infuseType) {
                    renderInfuseTypePreview(g, infuseType, c2x+5, ry+1);
                } else if (preview instanceof mekanism.api.chemical.pigment.Pigment pigment) {
                    renderPigmentPreview(g, pigment, c2x+5, ry+1);
                } else if (preview instanceof mekanism.api.chemical.slurry.Slurry slurry) {
                    renderSlurryPreview(g, slurry, c2x+5, ry+1);
                } else if (!e.result.isEmpty()) {
                    RenderSystem.enableDepthTest();
                    g.renderItem(e.result, c2x+5, ry+1);
                    RenderSystem.disableDepthTest();
                } else {
                    // 空槽背景
                    g.fill(c2x+5, ry+1, c2x+21, ry+17, 0xFF333333);
                }
            } catch (Exception ex) {
                g.fill(c2x+5, ry+1, c2x+21, ry+17, 0xFFAA3333);
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

        // 分离正常输入槽和副输出槽
        // (-1, -1)：物品副输出槽（精密锯木机），渲染在主输出下方
        // (-2, -2)：气体副输出槽（加压反应室），渲染在主输出右侧
        List<PreviewSlot> normalInputSlots = new ArrayList<>();
        PreviewSlot itemSecondaryOutputSlot = null;  // 物品副输出槽
        PreviewSlot gasSecondaryOutputSlot = null;   // 气体副输出槽
        PreviewSlot electrolyticLeftOutputSlot = null;  // 电解分离器左侧输出槽
        PreviewSlot electrolyticRightOutputSlot = null; // 电解分离器右侧输出槽
        for (PreviewSlot slot : currentRecipeSlots) {
            if (slot.x == -1 && slot.y == -1) {
                itemSecondaryOutputSlot = slot;
            } else if (slot.x == -2 && slot.y == -2) {
                gasSecondaryOutputSlot = slot;
            } else if (slot.x == -3 && slot.y == -3) {
                electrolyticLeftOutputSlot = slot;
            } else if (slot.x == -4 && slot.y == -4) {
                electrolyticRightOutputSlot = slot;
            } else {
                normalInputSlots.add(slot);
            }
        }
        
        // 渲染正常输入槽
        for (PreviewSlot slot : normalInputSlots) {
            RecipePreviewRenderer.renderSlot(g, slot, false);
        }
        
        // 检测是否是电解分离器（有电解分离器输出槽标记）
        boolean isElectrolyticSeparator = electrolyticLeftOutputSlot != null || electrolyticRightOutputSlot != null;
        
        // 渲染箭头和输出槽（输出槽紧挨着箭头）
        if (!isElectrolyticSeparator && !normalInputSlots.isEmpty()) {
            int lastInputX = getLastInputX();
            int inputY = getInputCenterY();
            
            // 计算并渲染箭头
            int[] arrowPos = calculateArrowPosition(lastInputX, inputY);
            g.drawString(font, "§e→", arrowPos[0], arrowPos[1], 0xFFAA00, false);
            
            // 如果有结果槽，更新其位置紧挨着箭头
            if (currentResultSlot != null) {
                int[] outputPos = calculateOutputPosition(lastInputX, inputY);
                // 创建新的PreviewSlot对象，使用计算出的位置
                PreviewSlot resultSlotAtPos = createSlotAtPosition(currentResultSlot, outputPos[0], outputPos[1]);
                RecipePreviewRenderer.renderSlot(g, resultSlotAtPos, true);
                
                // 如果有物品副输出槽，渲染在主输出槽下方（紧贴主输出槽）
                if (itemSecondaryOutputSlot != null) {
                    int secondaryOutputY = outputPos[1] + SLOT;
                    PreviewSlot secondarySlotAtPos = createSlotAtPosition(itemSecondaryOutputSlot, outputPos[0], secondaryOutputY);
                    RecipePreviewRenderer.renderSlot(g, secondarySlotAtPos, true);
                }
                
                // 如果有气体副输出槽，渲染在主输出槽右侧（右移2像素）
                if (gasSecondaryOutputSlot != null) {
                    int gasOutputX = outputPos[0] + SLOT + 2;
                    PreviewSlot gasSlotAtPos = createSlotAtPosition(gasSecondaryOutputSlot, gasOutputX, outputPos[1]);
                    RecipePreviewRenderer.renderSlot(g, gasSlotAtPos, true);
                }
            } else if (gasSecondaryOutputSlot != null) {
                // 加压反应室：如果物品输出为空但有气体输出，渲染气体输出
                int[] outputPos = calculateOutputPosition(lastInputX, inputY);
                PreviewSlot gasSlotAtPos = createSlotAtPosition(gasSecondaryOutputSlot, outputPos[0], outputPos[1]);
                RecipePreviewRenderer.renderSlot(g, gasSlotAtPos, true);
            }
        } else if (!isElectrolyticSeparator && currentResultSlot != null) {
            // 只有输出槽的情况（如能量转换）- 使用默认位置
            int defaultX = c3x + 8;
            int defaultY = contentY + 20;
            PreviewSlot resultSlotAtPos = createSlotAtPosition(currentResultSlot, defaultX, defaultY);
            RecipePreviewRenderer.renderSlot(g, resultSlotAtPos, true);
            
            // 如果有物品副输出槽，渲染在主输出槽下方（紧贴主输出槽）
            if (itemSecondaryOutputSlot != null) {
                int secondaryOutputY = defaultY + SLOT;
                PreviewSlot secondarySlotAtPos = createSlotAtPosition(itemSecondaryOutputSlot, defaultX, secondaryOutputY);
                RecipePreviewRenderer.renderSlot(g, secondarySlotAtPos, true);
            }
            
            // 如果有气体副输出槽，渲染在主输出槽右侧（右移2像素）
            if (gasSecondaryOutputSlot != null) {
                int gasOutputX = defaultX + SLOT + 2;
                PreviewSlot gasSlotAtPos = createSlotAtPosition(gasSecondaryOutputSlot, gasOutputX, defaultY);
                RecipePreviewRenderer.renderSlot(g, gasSlotAtPos, true);
            }
        } else if (isElectrolyticSeparator) {
            // 电解分离器特殊渲染：渲染箭头和两个输出槽
            if (!normalInputSlots.isEmpty()) {
                int lastInputX = getLastInputX();
                int inputY = getInputCenterY();
                
                // 渲染箭头
                int[] arrowPos = calculateArrowPosition(lastInputX, inputY);
                g.drawString(font, "§e→", arrowPos[0], arrowPos[1], 0xFFAA00, false);
                
                // 计算输出位置
                int[] outputPos = calculateOutputPosition(lastInputX, inputY);
                
                // 渲染左侧气体输出槽
                if (electrolyticLeftOutputSlot != null) {
                    PreviewSlot leftSlotAtPos = createSlotAtPosition(electrolyticLeftOutputSlot, outputPos[0], outputPos[1]);
                    RecipePreviewRenderer.renderSlot(g, leftSlotAtPos, true);
                }
                
                // 渲染右侧气体输出槽（紧贴左侧输出槽）
                if (electrolyticRightOutputSlot != null) {
                    int rightOutputX = outputPos[0] + SLOT + 2;
                    PreviewSlot rightSlotAtPos = createSlotAtPosition(electrolyticRightOutputSlot, rightOutputX, outputPos[1]);
                    RecipePreviewRenderer.renderSlot(g, rightSlotAtPos, true);
                }
            }
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
        boolean hasSelection = !targetItem.isEmpty() || !targetFluid.isEmpty() || targetChemical != null;
        int maxRecipeScroll = Math.max(0, recipeList.size() - listVisRows + 2);
        int recipeSbX = c2x + COL2_W - 3;
        if (hasSelection && maxRecipeScroll > 0 && mx >= recipeSbX && mx < recipeSbX + 3 && my >= contentY && my < contentY + contentH) {
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
                pickFluid(filteredFluids.get(idx)); return true;
            }
        }
        
        // 化学品网格
        if (mx>=c1x && mx<c1x+gridW && my>=chemicalGridTop && my<chemicalGridTop+chemicalGridH) {
            int col = ((int)mx-c1x)/(SLOT+2);
            int row = ((int)my-chemicalGridTop)/(SLOT+2);
            int idx = (row+chemicalScroll)*ITEM_COLS+col;
            if (idx >= 0 && idx < filteredChemicals.size()) {
                pickChemical(filteredChemicals.get(idx)); return true;
            }
        }
        
        // 配方列表点击检测（支持物品、流体、化学品）
        if (hasSelection && mx>=c2x && mx<c2x+COL2_W-3
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
        boolean hasSelection = !targetItem.isEmpty() || !targetFluid.isEmpty() || targetChemical != null;
        if (hasSelection && mx>=c2x && mx<c2x+COL2_W) {
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
            
            // 尝试从MekanismAPI获取正确的译名
            try {
                switch (type) {
                    case GAS -> {
                        Gas gas = mekanism.api.MekanismAPI.gasRegistry().getValue(id);
                        if (gas != null && !gas.isEmptyType()) {
                            Component textComponent = gas.getTextComponent();
                            if (textComponent != null) {
                                return textComponent.getString();
                            }
                        }
                    }
                    case INFUSE_TYPE -> {
                        InfuseType infuseType = mekanism.api.MekanismAPI.infuseTypeRegistry().getValue(id);
                        if (infuseType != null && !infuseType.isEmptyType()) {
                            Component textComponent = infuseType.getTextComponent();
                            if (textComponent != null) {
                                return textComponent.getString();
                            }
                        }
                    }
                    case PIGMENT -> {
                        Pigment pigment = mekanism.api.MekanismAPI.pigmentRegistry().getValue(id);
                        if (pigment != null && !pigment.isEmptyType()) {
                            Component textComponent = pigment.getTextComponent();
                            if (textComponent != null) {
                                return textComponent.getString();
                            }
                        }
                    }
                    case SLURRY -> {
                        Slurry slurry = mekanism.api.MekanismAPI.slurryRegistry().getValue(id);
                        if (slurry != null && !slurry.isEmptyType()) {
                            Component textComponent = slurry.getTextComponent();
                            if (textComponent != null) {
                                return textComponent.getString();
                            }
                        }
                    }
                }
            } catch (Exception e) {}
            
            // Fallback: 从ID路径生成名称
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
        final Object           outputPreview;  // 输出预览（可以是ItemStack、FluidStack、GasStack等）
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
            this.outputPreview = parseOutputPreview(r);
        }
        
        /**
         * 解析配方的输出预览
         */
        private static Object parseOutputPreview(Recipe<?> recipe) {
            String recipeTypeName = recipe.getType().toString().toLowerCase();
            
            // Mekanism配方特殊处理
            if (recipeTypeName.contains("mekanism") || recipeTypeName.contains("rotary") ||
                recipeTypeName.contains("condensentrator") || recipeTypeName.contains("separating") ||
                recipeTypeName.contains("reaction") || recipeTypeName.contains("pressurized") ||
                recipeTypeName.contains("energy_conversion") || recipeTypeName.contains("injecting") ||
                recipeTypeName.contains("nucleosynthesizing") || recipeTypeName.contains("compressing") ||
                recipeTypeName.contains("painting") || recipeTypeName.contains("enriching") ||
                recipeTypeName.contains("purifying") || recipeTypeName.contains("crushing") ||
                recipeTypeName.contains("smelting") || recipeTypeName.contains("sawing") ||
                recipeTypeName.contains("evaporating")) {
                
                try {
                    // 特殊处理电解分离器 - 返回输入流体作为代表物
                    if (recipeTypeName.contains("separating")) {
                        try {
                            java.lang.reflect.Method getInput = recipe.getClass().getMethod("getInput");
                            Object input = getInput.invoke(recipe);
                            
                            // 返回输入流体
                            if (input instanceof mekanism.api.recipes.ingredients.FluidStackIngredient fluidIngredient) {
                                List<FluidStack> matchingStacks = fluidIngredient.getRepresentations();
                                if (!matchingStacks.isEmpty()) {
                                    return matchingStacks.get(0);
                                }
                            } else if (input instanceof FluidStack fluidStack && !fluidStack.isEmpty()) {
                                return fluidStack;
                            }
                        } catch (Exception e) {}
                        // 如果电解分离器特殊处理失败，返回默认的result
                        return recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
                    }
                    
                    // 特殊处理加压反应室 - 优先物品输出，再气体输出
                    if (recipeTypeName.contains("reaction") || recipeTypeName.contains("pressurized")) {
                        try {
                            java.lang.reflect.Method getOutputDef = recipe.getClass().getMethod("getOutputDefinition");
                            Object outputDef = getOutputDef.invoke(recipe);
                            if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                                Object outputObj = outputList.get(0);
                                try {
                                    java.lang.reflect.Method gasMethod = outputObj.getClass().getMethod("gas");
                                    java.lang.reflect.Method itemMethod = outputObj.getClass().getMethod("item");
                                    
                                    Object gasOutput = gasMethod.invoke(outputObj);
                                    Object itemOutput = itemMethod.invoke(outputObj);
                                    
                                    // 优先返回物品输出
                                    if (itemOutput instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                                        return itemStack;
                                    }
                                    // 如果物品为空，返回气体输出
                                    if (gasOutput instanceof mekanism.api.chemical.gas.GasStack gasStack && !gasStack.isEmpty()) {
                                        return gasStack;
                                    }
                                    
                                    // 如果都为空，不继续执行通用处理，直接返回默认的result
                                    return recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
                                } catch (Exception e) {}
                            }
                        } catch (Exception e) {}
                        // 如果加压反应室特殊处理失败，返回默认的result
                        return recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
                    }
                    
                    // 特殊处理回旋式气液转换器 - 优先处理
                    if (recipeTypeName.contains("rotary") || recipeTypeName.contains("condensentrator")) {
                        try {
                            java.lang.reflect.Method hasFluidToGas = recipe.getClass().getMethod("hasFluidToGas");
                            boolean canFluidToGas = (boolean) hasFluidToGas.invoke(recipe);
                            
                            if (canFluidToGas) {
                                java.lang.reflect.Method getGasOutputDef = recipe.getClass().getMethod("getGasOutputDefinition");
                                Object gasOutputDef = getGasOutputDef.invoke(recipe);
                                if (gasOutputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                                    return outputList.get(0);
                                } else if (gasOutputDef instanceof mekanism.api.chemical.gas.GasStack gasStack) {
                                    return gasStack;
                                }
                                return gasOutputDef;
                            } else {
                                java.lang.reflect.Method getFluidOutputDef = recipe.getClass().getMethod("getFluidOutputDefinition");
                                Object fluidOutputDef = getFluidOutputDef.invoke(recipe);
                                if (fluidOutputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                                    return outputList.get(0);
                                } else if (fluidOutputDef instanceof FluidStack fluidStack) {
                                    return fluidStack;
                                }
                                return fluidOutputDef;
                            }
                        } catch (Exception e) {}
                    }
                    
                    // 通用处理 - 尝试获取输出定义
                    java.lang.reflect.Method getOutputDefinition = null;
                    try { getOutputDefinition = recipe.getClass().getMethod("getOutputDefinition"); } catch (Exception e) {}
                    java.lang.reflect.Method getOutput = null;
                    try { getOutput = recipe.getClass().getMethod("getOutput"); } catch (Exception e) {}
                    
                    if (getOutputDefinition != null) {
                        Object outputDef = getOutputDefinition.invoke(recipe);
                        if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                            return outputList.get(0);
                        } else if (outputDef != null) {
                            return outputDef;
                        }
                    }
                    
                    if (getOutput != null) {
                        Object output = getOutput.invoke(recipe);
                        if (output != null) {
                            return output;
                        }
                    }
                    
                } catch (Exception e) {}
            }
            
            // 默认返回物品结果
            return recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
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
    
    // ── 渲染预览辅助方法 ────────────────────────────────────────────
    private void renderFluidPreview(GuiGraphics guiGraphics, FluidStack fluidStack, int x, int y) {
        if (fluidStack.isEmpty()) return;
        
        net.minecraft.world.level.material.Fluid fluid = fluidStack.getFluid();
        IClientFluidTypeExtensions fluidExt = IClientFluidTypeExtensions.of(fluid);
        
        int tintColor = fluidExt.getTintColor(fluidStack);
        float r = ((tintColor >> 16) & 0xFF) / 255.0f;
        float g = ((tintColor >> 8) & 0xFF) / 255.0f;
        float b = (tintColor & 0xFF) / 255.0f;
        
        ResourceLocation stillTexture = fluidExt.getStillTexture(fluidStack);
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = null;
        
        if (stillTexture != null) {
            try {
                sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(stillTexture);
                if (sprite != null && sprite.contents().name().toString().contains("missingno")) {
                    sprite = null;
                }
            } catch (Exception e) {
                sprite = null;
            }
        }
        
        if (sprite != null) {
            RenderSystem.setShaderTexture(0, net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            
            RenderSystem.enableBlend();
            com.mojang.blaze3d.vertex.BufferBuilder vertexBuffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            vertexBuffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            org.joml.Matrix4f matrix4f = guiGraphics.pose().last().pose();
            
            float uMin = sprite.getU0();
            float uMax = sprite.getU1();
            float vMin = sprite.getV0();
            float vMax = sprite.getV1();
            
            vertexBuffer.vertex(matrix4f, x, y + 16, 0).uv(uMin, vMax).endVertex();
            vertexBuffer.vertex(matrix4f, x + 16, y + 16, 0).uv(uMax, vMax).endVertex();
            vertexBuffer.vertex(matrix4f, x + 16, y, 0).uv(uMax, vMin).endVertex();
            vertexBuffer.vertex(matrix4f, x, y, 0).uv(uMin, vMin).endVertex();
            
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(vertexBuffer.end());
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            guiGraphics.fill(x, y, x + 16, y + 16, 0xFFFFFFFF);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
    
    private void renderGasPreview(GuiGraphics guiGraphics, mekanism.api.chemical.gas.GasStack gasStack, int x, int y) {
        if (gasStack.isEmpty()) return;
        
        try {
            mekanism.api.chemical.gas.Gas gas = gasStack.getType();
            if (gas == null || gas.isEmptyType()) return;
            
            int color = gas.getTint();
            ResourceLocation texture = gas.getIcon();
            
            if (texture == null) return;
            
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = 
                Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(texture);
            
            if (sprite == null || sprite.contents().name().toString().contains("missingno")) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                return;
            }
            
            RenderSystem.setShaderTexture(0, sprite.atlasLocation());
            
            RenderSystem.enableBlend();
            com.mojang.blaze3d.vertex.BufferBuilder vertexBuffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            vertexBuffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            org.joml.Matrix4f matrix4f = guiGraphics.pose().last().pose();
            
            float uMin = sprite.getU0();
            float uMax = sprite.getU1();
            float vMin = sprite.getV0();
            float vMax = sprite.getV1();
            
            vertexBuffer.vertex(matrix4f, x, y + 16, 0).uv(uMin, vMax).endVertex();
            vertexBuffer.vertex(matrix4f, x + 16, y + 16, 0).uv(uMax, vMax).endVertex();
            vertexBuffer.vertex(matrix4f, x + 16, y, 0).uv(uMax, vMin).endVertex();
            vertexBuffer.vertex(matrix4f, x, y, 0).uv(uMin, vMin).endVertex();
            
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(vertexBuffer.end());
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } catch (Exception e) {
            guiGraphics.fill(x, y, x + 16, y + 16, 0xFF00DDFF);
        }
    }
    
    private void renderChemicalStackPreview(GuiGraphics guiGraphics, mekanism.api.chemical.ChemicalStack<?> chemicalStack, int x, int y) {
        if (chemicalStack.isEmpty()) return;
        
        try {
            Object chemical = chemicalStack.getType();
            
            if (chemical instanceof mekanism.api.chemical.gas.Gas gas) {
                renderGasPreview(guiGraphics, (mekanism.api.chemical.gas.GasStack) chemicalStack, x, y);
            } else if (chemical instanceof mekanism.api.chemical.slurry.Slurry slurry) {
                renderSlurryPreview(guiGraphics, slurry, x, y);
            } else if (chemical instanceof mekanism.api.chemical.pigment.Pigment pigment) {
                renderPigmentPreview(guiGraphics, pigment, x, y);
            } else if (chemical instanceof mekanism.api.chemical.infuse.InfuseType infuseType) {
                renderInfuseTypePreview(guiGraphics, infuseType, x, y);
            } else {
                guiGraphics.fill(x, y, x + 16, y + 16, 0xFF00DDFF);
            }
        } catch (Exception e) {
            guiGraphics.fill(x, y, x + 16, y + 16, 0xFFAA3333);
        }
    }
    
    private void renderInfuseTypePreview(GuiGraphics guiGraphics, mekanism.api.chemical.infuse.InfuseType infuseType, int x, int y) {
        if (infuseType == null || infuseType.isEmptyType()) return;
        
        try {
            int color = infuseType.getTint();
            ResourceLocation texture = infuseType.getIcon();
            
            if (texture == null) return;
            
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = 
                Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(texture);
            
            if (sprite == null || sprite.contents().name().toString().contains("missingno")) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                return;
            }
            
            RenderSystem.setShaderTexture(0, sprite.atlasLocation());
            
            RenderSystem.enableBlend();
            com.mojang.blaze3d.vertex.BufferBuilder vertexBuffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            vertexBuffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            org.joml.Matrix4f matrix4f = guiGraphics.pose().last().pose();
            
            float uMin = sprite.getU0();
            float uMax = sprite.getU1();
            float vMin = sprite.getV0();
            float vMax = sprite.getV1();
            
            vertexBuffer.vertex(matrix4f, x, y + 16, 0).uv(uMin, vMax).endVertex();
            vertexBuffer.vertex(matrix4f, x + 16, y + 16, 0).uv(uMax, vMax).endVertex();
            vertexBuffer.vertex(matrix4f, x + 16, y, 0).uv(uMax, vMin).endVertex();
            vertexBuffer.vertex(matrix4f, x, y, 0).uv(uMin, vMin).endVertex();
            
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(vertexBuffer.end());
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } catch (Exception e) {
            guiGraphics.fill(x, y, x + 16, y + 16, 0xFFDDDD00);
        }
    }
    
    private void renderPigmentPreview(GuiGraphics guiGraphics, mekanism.api.chemical.pigment.Pigment pigment, int x, int y) {
        if (pigment == null || pigment.isEmptyType()) return;
        
        try {
            int color = pigment.getTint();
            ResourceLocation texture = pigment.getIcon();
            
            if (texture == null) return;
            
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = 
                Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(texture);
            
            if (sprite == null || sprite.contents().name().toString().contains("missingno")) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                return;
            }
            
            RenderSystem.setShaderTexture(0, sprite.atlasLocation());
            
            RenderSystem.enableBlend();
            com.mojang.blaze3d.vertex.BufferBuilder vertexBuffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            vertexBuffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            org.joml.Matrix4f matrix4f = guiGraphics.pose().last().pose();
            
            float uMin = sprite.getU0();
            float uMax = sprite.getU1();
            float vMin = sprite.getV0();
            float vMax = sprite.getV1();
            
            vertexBuffer.vertex(matrix4f, x, y + 16, 0).uv(uMin, vMax).endVertex();
            vertexBuffer.vertex(matrix4f, x + 16, y + 16, 0).uv(uMax, vMax).endVertex();
            vertexBuffer.vertex(matrix4f, x + 16, y, 0).uv(uMax, vMin).endVertex();
            vertexBuffer.vertex(matrix4f, x, y, 0).uv(uMin, vMin).endVertex();
            
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(vertexBuffer.end());
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } catch (Exception e) {
            guiGraphics.fill(x, y, x + 16, y + 16, 0xFFFF00FF);
        }
    }
    
    private void renderSlurryPreview(GuiGraphics guiGraphics, mekanism.api.chemical.slurry.Slurry slurry, int x, int y) {
        if (slurry == null || slurry.isEmptyType()) return;
        
        try {
            int color = slurry.getTint();
            ResourceLocation texture = slurry.getIcon();
            
            if (texture == null) return;
            
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = 
                Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(texture);
            
            if (sprite == null || sprite.contents().name().toString().contains("missingno")) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                return;
            }
            
            RenderSystem.setShaderTexture(0, sprite.atlasLocation());
            
            RenderSystem.enableBlend();
            com.mojang.blaze3d.vertex.BufferBuilder vertexBuffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            vertexBuffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            org.joml.Matrix4f matrix4f = guiGraphics.pose().last().pose();
            
            float uMin = sprite.getU0();
            float uMax = sprite.getU1();
            float vMin = sprite.getV0();
            float vMax = sprite.getV1();
            
            vertexBuffer.vertex(matrix4f, x, y + 16, 0).uv(uMin, vMax).endVertex();
            vertexBuffer.vertex(matrix4f, x + 16, y + 16, 0).uv(uMax, vMax).endVertex();
            vertexBuffer.vertex(matrix4f, x + 16, y, 0).uv(uMax, vMin).endVertex();
            vertexBuffer.vertex(matrix4f, x, y, 0).uv(uMin, vMin).endVertex();
            
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(vertexBuffer.end());
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } catch (Exception e) {
            guiGraphics.fill(x, y, x + 16, y + 16, 0xFF555555);
        }
    }
}
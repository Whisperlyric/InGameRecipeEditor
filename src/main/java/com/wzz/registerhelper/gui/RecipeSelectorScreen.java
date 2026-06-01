package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.gui.util.RecipePreviewRenderer;
import com.wzz.registerhelper.gui.util.RecipePreviewRenderer.PreviewSlot;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import com.wzz.registerhelper.network.RecipeClientCache;
import com.wzz.registerhelper.network.RequestRecipeListPacket;
import com.wzz.registerhelper.network.SyncRecipeListPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
public class RecipeSelectorScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MIN_GUI_WIDTH = 650;
    private static final int MIN_GUI_HEIGHT = 350;
    private static final int RECIPE_DETAIL_WIDTH = 250; // 增加详情区域宽度
    private static final int RECIPE_ITEM_HEIGHT = 22;
    private static final int MAX_VISIBLE_RECIPES = 11;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_SPACING = 20;

    private final Screen parentScreen;
    private final Consumer<ResourceLocation> onRecipeSelected;

    // 动态尺寸变量
    private int contentWidth;
    private int contentHeight;
    private int leftPos, topPos;

    private final List<RecipeEntry> allRecipes = new ArrayList<>();
    private List<RecipeEntry> filteredRecipes = new ArrayList<>();
    private int scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    private int selectedRecipeIndex = -1;
    private String loadError = null;

    private final List<PreviewSlot> currentRecipeSlots = new ArrayList<>();
    private PreviewSlot currentResultSlot = null;
    private String currentRecipeTypeDisplay = "";

    private EditBox searchBox;
    private PinyinSearchHelper<RecipeEntry> searchHelper;
    private Button selectButton;
    private Button cancelButton;
    private Button scrollUpButton;
    private Button scrollDownButton;
    private Button refreshButton;
    private final Set<ResourceLocation> allowedRecipeIds = new HashSet<>();
    private final boolean useRecipeFilter;

    public RecipeSelectorScreen(Screen parentScreen, Consumer<ResourceLocation> onRecipeSelected) {
        super(Component.literal("选择配方"));
        this.minecraft = Minecraft.getInstance();
        this.parentScreen = parentScreen;
        this.onRecipeSelected = onRecipeSelected;
        this.useRecipeFilter = false;
        this.searchHelper = new PinyinSearchHelper<>(
                entry -> entry.resultItem.isEmpty() ? "" : entry.resultItem.getHoverName().getString(),
                entry -> entry.recipeId.toString()
        );
        loadRecipes();
    }

    public RecipeSelectorScreen(Screen parentScreen, Consumer<ResourceLocation> onRecipeSelected,
                                List<UnifiedRecipeInfo> allowedRecipes, String title) {
        super(Component.literal(title));
        this.minecraft = Minecraft.getInstance();
        this.parentScreen = parentScreen;
        this.onRecipeSelected = onRecipeSelected;
        this.useRecipeFilter = true;
        this.searchHelper = new PinyinSearchHelper<>(
                entry -> entry.resultItem.isEmpty() ? "" : entry.resultItem.getHoverName().getString(),
                entry -> entry.recipeId.toString()
        );
        for (UnifiedRecipeInfo recipe : allowedRecipes) {
            allowedRecipeIds.add(recipe.getRecipeId());
        }
        loadRecipes();
    }

    private void calculateDynamicSize() {
        // 计算所需的最小尺寸
        this.contentWidth = Math.max(MIN_GUI_WIDTH, this.width - 100);
        this.contentHeight = Math.max(MIN_GUI_HEIGHT, this.height - 100);

        // 确保不超过屏幕尺寸
        this.contentWidth = Math.min(this.contentWidth, this.width - 40);
        this.contentHeight = Math.min(this.contentHeight, this.height - 40);

        // 计算位置
        this.leftPos = (this.width - contentWidth) / 2;
        this.topPos = (this.height - contentHeight) / 2;
    }

    private void loadRecipes() {
        allRecipes.clear();
        loadError = null;
        try {
            if (minecraft == null) {
                loadError = "Minecraft实例为空";
                LOGGER.error("Minecraft instance is null");
                return;
            }
            if (minecraft.level == null) {
                loadError = "游戏世界未加载";
                LOGGER.error("Game level is null");
                return;
            }

            // 优先尝试从服务器直接获取（单人游戏或集成服务器）
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            RecipeManager recipeManager;

            if (server != null) {
                // 单人游戏或局域网主机，直接从服务器获取
                recipeManager = server.getRecipeManager();
                LOGGER.info("从集成服务器加载配方");
            } else {
                // 远程服务器，使用网络包请求
                LOGGER.info("检测到远程服务器，使用网络包获取配方列表");
                loadError = "正在从服务器加载配方...";

                // 清除旧缓存
                RecipeClientCache.clearCache();

                // 发送请求
                RequestRecipeListPacket.sendToServer();

                // 添加回调，当数据返回时更新列表
                RecipeClientCache.addLoadCallback(recipes -> {
                    // 在主线程更新UI
                    minecraft.execute(() -> {
                        loadError = null;
                        processRecipesFromCache(recipes);
                        // 也加载自定义配方
                        loadCustomRecipes();
                    });
                });

                return; // 异步加载，直接返回
            }

            if (recipeManager == null) {
                loadError = "配方管理器为空";
                LOGGER.error("Recipe manager is null");
                return;
            }
            Collection<Recipe<?>> recipes = recipeManager.getRecipes();
            if (recipes.isEmpty()) {
                loadError = "未找到任何配方，可能需要等待世界完全加载";
                LOGGER.warn("No recipes found in recipe manager");
                return;
            }
            int validRecipeCount = 0;
            for (Recipe<?> recipe : recipes) {
                try {
                    ResourceLocation id = recipe.getId();
                    if (id == null) {
                        LOGGER.warn("配方ID为空，跳过");
                        continue;
                    }
                    ItemStack resultItem = ItemStack.EMPTY;
                    try {
                        resultItem = recipe.getResultItem(minecraft.level.registryAccess());
                        
                        if (resultItem.isEmpty() && recipe instanceof mekanism.api.recipes.MekanismRecipe) {
                            try {
                                java.lang.reflect.Method getOutput = null;
                                java.lang.reflect.Method getOutputDefinition = null;
                                
                                for (java.lang.reflect.Method method : recipe.getClass().getMethods()) {
                                    String methodName = method.getName();
                                    if (methodName.equals("getOutput")) {
                                        getOutput = method;
                                    } else if (methodName.equals("getOutputDefinition")) {
                                        getOutputDefinition = method;
                                    }
                                }
                                
                                if (getOutput != null) {
                                    Object output = getOutput.invoke(recipe);
                                    if (output instanceof FluidStack fluidStack && !fluidStack.isEmpty()) {
                                        resultItem = fluidStack.getFluid().getBucket().getDefaultInstance();
                                        resultItem.setCount(1);
                                    }
                                }
                                
                                if (resultItem.isEmpty() && getOutputDefinition != null) {
                                    Object outputDef = getOutputDefinition.invoke(recipe);
                                    if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                                        Object output = outputList.get(0);
                                        if (output instanceof FluidStack fluidStack && !fluidStack.isEmpty()) {
                                            resultItem = fluidStack.getFluid().getBucket().getDefaultInstance();
                                            resultItem.setCount(1);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("获取配方 {} 的结果物品失败: {}", id, e.getMessage());
                    }
                    String recipeType = classifyRecipeType(recipe);
                    allRecipes.add(new RecipeEntry(id, resultItem, recipeType, recipe));
                    validRecipeCount++;
                } catch (Exception e) {
                    LOGGER.warn("处理配方时出错: {}", e.getMessage());
                }
            }

            // 加载自定义配方（酿造台、铁砧等）
            loadCustomRecipes();

            if (validRecipeCount == 0 && allRecipes.isEmpty()) {
                loadError = "没有可用的配方数据";
            }

            // 应用配方过滤器（但保留自定义配方）
            if (useRecipeFilter && !allowedRecipeIds.isEmpty()) {
                allRecipes.removeIf(entry -> {
                    // 保留自定义配方（registerhelper命名空间）
                    if (entry.recipeId.getNamespace().equals("registerhelper")) {
                        return false;
                    }
                    // 过滤其他不在白名单中的配方
                    return !allowedRecipeIds.contains(entry.recipeId);
                });
            }
        } catch (Exception e) {
            loadError = "加载配方时出错: " + e.getMessage();
            LOGGER.error("Error loading recipes", e);
        }

        allRecipes.sort(Comparator.comparing(entry -> entry.recipeId.toString()));
        filteredRecipes = new ArrayList<>(allRecipes);
        searchHelper.buildCache(allRecipes);
        filteredRecipes = new ArrayList<>(allRecipes);
    }

    /**
     * 加载自定义配方（酿造台、铁砧等）
     * 从config/registerhelper/custom_recipes/目录扫描JSON文件
     */
    private void loadCustomRecipes() {
        try {
            java.nio.file.Path customRecipesDir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve("registerhelper/custom_recipes");

            if (!java.nio.file.Files.exists(customRecipesDir)) {
                LOGGER.warn("自定义配方目录不存在: {}", customRecipesDir);
                return;
            }

            // 扫描酿造台配方
            loadCustomRecipesFromDirectory(customRecipesDir.resolve("brewing"), "自定义酿造台");

            // 扫描铁砧配方
            loadCustomRecipesFromDirectory(customRecipesDir.resolve("anvil"), "自定义铁砧");
        } catch (Exception e) {
            LOGGER.error("加载自定义配方时出错", e);
        }
    }

    /**
     * 从指定目录加载自定义配方JSON文件
     *
     * @return 加载的配方数量
     */
    private int loadCustomRecipesFromDirectory(java.nio.file.Path dir, String recipeType) {
        if (!java.nio.file.Files.exists(dir)) {
            LOGGER.debug("目录不存在: {}", dir);
            return 0;
        }

        final int[] count = {0};

        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(dir, 1)) {
            paths.filter(java.nio.file.Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(jsonFile -> {
                        try {

                            // 读取JSON获取输出物品
                            String content = java.nio.file.Files.readString(jsonFile);
                            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();

                            // 解析输出物品
                            ItemStack outputStack = ItemStack.EMPTY;
                            if (json.has("output")) {
                                com.google.gson.JsonElement outputElement = json.get("output");
                                if (outputElement.isJsonObject()) {
                                    com.google.gson.JsonObject outputObj = outputElement.getAsJsonObject();
                                    if (outputObj.has("item")) {
                                        String itemId = outputObj.get("item").getAsString();
                                        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                                                .getValue(new ResourceLocation(itemId));
                                        if (item != null) {
                                            int itemCount = outputObj.has("count") ? outputObj.get("count").getAsInt() : 1;
                                            outputStack = new ItemStack(item, itemCount);
                                        } else {
                                            LOGGER.warn("未找到物品: {}", itemId);
                                        }
                                    }
                                }
                            } else {
                                LOGGER.warn("JSON中没有output字段: {}", jsonFile);
                            }

                            // 创建RecipeEntry，使用文件路径作为ID
                            String fileName = jsonFile.getFileName().toString().replace(".json", "");
                            String category = dir.getFileName().toString(); // brewing 或 anvil
                            ResourceLocation id = new ResourceLocation("registerhelper", "custom_" + category + "/" + fileName);

                            RecipeEntry entry = new RecipeEntry(id, outputStack, recipeType, null);
                            allRecipes.add(entry);
                            count[0]++;
                        } catch (Exception e) {
                            LOGGER.error("加载自定义配方文件失败: {}", jsonFile, e);
                        }
                    });
        } catch (Exception e) {
            LOGGER.error("扫描自定义配方目录失败: {}", dir, e);
        }

        return count[0];
    }

    /**
     * 从网络缓存处理配方数据（用于远程服务器）
     */
    private void processRecipesFromCache(List<UnifiedRecipeInfo> recipes) {
        allRecipes.clear();

        if (recipes.isEmpty()) {
            loadError = RecipeClientCache.getErrorMessage();
            if (loadError == null) {
                loadError = "服务器返回了空的配方列表";
            }
            filteredRecipes = new ArrayList<>();
            updateButtons();
            return;
        }

        // 将 UnifiedRecipeInfo 转换为 RecipeEntry
        RecipeManager clientRecipeManager = minecraft.level != null ?
                minecraft.level.getRecipeManager() : null;

        for (UnifiedRecipeInfo info : recipes) {
            try {
                // 尝试从客户端获取配方详情（用于显示）
                Recipe<?> recipe = null;
                ItemStack resultItem = ItemStack.EMPTY;

                if (clientRecipeManager != null) {
                    recipe = clientRecipeManager.byKey(info.id).orElse(null);
                    if (recipe != null) {
                        try {
                            resultItem = recipe.getResultItem(minecraft.level.registryAccess());
                            
                            if (resultItem.isEmpty() && recipe instanceof mekanism.api.recipes.MekanismRecipe) {
                                try {
                                    java.lang.reflect.Method getOutput = null;
                                    java.lang.reflect.Method getOutputDefinition = null;
                                    
                                    for (java.lang.reflect.Method method : recipe.getClass().getMethods()) {
                                        String methodName = method.getName();
                                        if (methodName.equals("getOutput")) {
                                            getOutput = method;
                                        } else if (methodName.equals("getOutputDefinition")) {
                                            getOutputDefinition = method;
                                        }
                                    }
                                    
                                    if (getOutput != null) {
                                        Object output = getOutput.invoke(recipe);
                                        if (output instanceof FluidStack fluidStack && !fluidStack.isEmpty()) {
                                            resultItem = fluidStack.getFluid().getBucket().getDefaultInstance();
                                            resultItem.setCount(1);
                                        }
                                    }
                                    
                                    if (resultItem.isEmpty() && getOutputDefinition != null) {
                                        Object outputDef = getOutputDefinition.invoke(recipe);
                                        if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                                            Object output = outputList.get(0);
                                            if (output instanceof FluidStack fluidStack && !fluidStack.isEmpty()) {
                                                resultItem = fluidStack.getFluid().getBucket().getDefaultInstance();
                                                resultItem.setCount(1);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                }

                // 从描述中提取类型
                String recipeType = info.description.contains("->") ?
                        info.description.split("->")[0].trim() : "未知类型";

                // 应用过滤器
                if (useRecipeFilter && !allowedRecipeIds.isEmpty()) {
                    if (!allowedRecipeIds.contains(info.id)) {
                        continue;
                    }
                }

                allRecipes.add(new RecipeEntry(info.id, resultItem, recipeType, recipe));

            } catch (Exception e) {
                LOGGER.warn("处理配方 {} 时出错: {}", info.id, e.getMessage());
            }
            allRecipes.sort(Comparator.comparing(entry -> entry.recipeId.toString()));
            filteredRecipes = new ArrayList<>(allRecipes);
            searchHelper.buildCache(allRecipes);
            allRecipes.sort(Comparator.comparing(entry -> entry.recipeId.toString()));
            filteredRecipes = new ArrayList<>(allRecipes);

            // 重新应用搜索过滤
            if (searchBox != null && !searchBox.getValue().isEmpty()) {
                onSearchTextChanged(searchBox.getValue());
            }

            updateButtons();
        }
    }

    private String classifyRecipeType(Recipe<?> recipe) {
        try {
            String typeName = recipe.getType().toString().toLowerCase();

            if (typeName.contains("minecraft:crafting_shaped")) {
                return "有形状配方";
            } else if (typeName.contains("minecraft:crafting_shapeless")) {
                return "无形状配方";
            } else if (typeName.contains("minecraft:smelting")) {
                return "熔炼配方";
            } else if (typeName.contains("minecraft:blasting")) {
                return "高炉配方";
            } else if (typeName.contains("minecraft:smoking")) {
                return "烟熏配方";
            } else if (typeName.contains("minecraft:campfire_cooking")) {
                return "营火烹饪";
            } else if (typeName.contains("avaritia")) {
                if (typeName.contains("shaped")) {
                    return "Avaritia有形状配方";
                } else if (typeName.contains("shapeless")) {
                    return "Avaritia无形状配方";
                } else {
                    return "Avaritia配方";
                }
            } else {
                return typeName;
            }

        } catch (Exception e) {
            LOGGER.warn("分类配方类型失败: {}", e.getMessage());
            return "未知类型";
        }
    }

    @Override
    protected void init() {
        calculateDynamicSize();

        // 搜索框位置调整，为右侧列表区域
        int listAreaX = leftPos + RECIPE_DETAIL_WIDTH + 20;
        int listAreaWidth = contentWidth - RECIPE_DETAIL_WIDTH - 40;

        searchBox = new EditBox(this.font, listAreaX, topPos + 45, listAreaWidth, 20,
                Component.literal("搜索配方"));
        searchBox.setHint(Component.literal("输入配方ID或物品名称进行搜索..."));
        searchBox.setResponder(this::onSearchTextChanged);
        addRenderableWidget(searchBox);

        selectButton = addRenderableWidget(Button.builder(
                        Component.literal("选择配方"),
                        button -> selectRecipe())
                .bounds(listAreaX, topPos + 75, 80, 20)
                .build());
        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消"),
                        button -> onClose())
                .bounds(listAreaX + 90, topPos + 75, 60, 20)
                .build());
        refreshButton = addRenderableWidget(Button.builder(
                        Component.literal("刷新"),
                        button -> refreshRecipes())
                .bounds(listAreaX + 160, topPos + 75, 50, 20)
                .build());
        scrollUpButton = addRenderableWidget(Button.builder(
                        Component.literal("▲"),
                        button -> scrollUp())
                .bounds(leftPos + contentWidth - 40, topPos + 105, 30, 20)
                .build());

        scrollDownButton = addRenderableWidget(Button.builder(
                        Component.literal("▼"),
                        button -> scrollDown())
                .bounds(leftPos + contentWidth - 40, topPos + contentHeight - 50, 30, 20)
                .build());
        updateButtons();
    }

    private void refreshRecipes() {
        loadRecipes();
        scrollOffset = 0;
        selectedRecipeIndex = -1;
        if (searchBox != null && !searchBox.getValue().isEmpty()) {
            onSearchTextChanged(searchBox.getValue());
        }
        updateButtons();
    }

    private void onSearchTextChanged(String searchText) {
        if (searchText.isEmpty()) {
            filteredRecipes = new ArrayList<>(allRecipes);
        } else {
            String lowerSearch = searchText.toLowerCase();

            filteredRecipes = allRecipes.stream()
                    .filter(entry -> {
                        String recipeIdStr = entry.recipeId.toString().toLowerCase();
                        if (recipeIdStr.contains(lowerSearch)) {
                            return true;
                        }
                        String recipeTypeLower = entry.recipeType.toLowerCase();
                        if (recipeTypeLower.contains(lowerSearch)) {
                            return true;
                        }
                        if (lowerSearch.contains("自定义") || lowerSearch.contains("custom")) {
                            if (entry.recipeId.getNamespace().equals("registerhelper") &&
                                    entry.recipeId.getPath().startsWith("custom_")) {
                                return true;
                            }
                        }
                        if (lowerSearch.contains("酿造") || lowerSearch.contains("brew")) {
                            if (recipeTypeLower.contains("酿造台") ||
                                    entry.recipeId.getPath().contains("brewing")) {
                                return true;
                            }
                        }
                        if (lowerSearch.contains("铁砧") || lowerSearch.contains("anvil")) {
                            if (recipeTypeLower.contains("铁砧") ||
                                    entry.recipeId.getPath().contains("anvil")) {
                                return true;
                            }
                        }
                        try {
                            if (!entry.resultItem.isEmpty()) {
                                String itemName = entry.resultItem.getHoverName().getString().toLowerCase();
                                if (itemName.contains(lowerSearch)) {
                                    return true;
                                }
                            }
                        } catch (Exception e) {
                            // 忽略异常，继续其他匹配
                        }
                        return searchHelper.matches(entry, searchText);
                    })
                    .collect(Collectors.toList());
        }

        scrollOffset = 0;
        selectedRecipeIndex = -1;
        updateButtons();
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            updateButtons();
        }
    }

    private void scrollDown() {
        int maxScroll = Math.max(0, filteredRecipes.size() - MAX_VISIBLE_RECIPES);
        if (scrollOffset < maxScroll) {
            scrollOffset++;
            updateButtons();
        }
    }

    private void updateButtons() {
        if (scrollUpButton != null) {
            scrollUpButton.active = scrollOffset > 0;
        }
        if (scrollDownButton != null) {
            scrollDownButton.active = scrollOffset < Math.max(0, filteredRecipes.size() - MAX_VISIBLE_RECIPES);
        }
        if (selectButton != null) {
            selectButton.active = selectedRecipeIndex >= 0;
        }
    }

    private void selectRecipe() {
        if (selectedRecipeIndex >= 0 && selectedRecipeIndex < filteredRecipes.size()) {
            RecipeEntry selected = filteredRecipes.get(selectedRecipeIndex);

            // 检查是否是自定义配方
            if (selected.recipeId.getNamespace().equals("registerhelper") &&
                    selected.recipeId.getPath().startsWith("custom_")) {
                // 自定义配方不支持GUI编辑，需要手动编辑JSON
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.literal(
                            "§e自定义配方暂不支持GUI编辑，请手动编辑JSON文件：\n" +
                                    "§fconfig/registerhelper/custom_recipes/" +
                                    (selected.recipeId.getPath().contains("brewing") ? "brewing/" : "anvil/") +
                                    selected.recipeId.getPath().substring(selected.recipeId.getPath().lastIndexOf('/') + 1) + ".json"
                    ));
                }
                return;
            }

            onRecipeSelected.accept(selected.recipeId);
            minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parentScreen);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        // ── 外框 ──
        guiGraphics.fill(leftPos - 1, topPos - 1, leftPos + contentWidth + 1, topPos + contentHeight + 1, 0xFF0A0A0A);
        // ── 主背景 ──
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + contentHeight, 0xFF252525);
        // ── 标题栏 ──
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + 28, 0xFF1A3A6A);
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + 1, 0xFF4A7ACF);
        guiGraphics.fill(leftPos, topPos + 27, leftPos + contentWidth, topPos + 28, 0xFF223B80);
        // ── 标题文字 ──
        guiGraphics.drawCenteredString(this.font, "§b" + this.title.getString(), leftPos + contentWidth / 2, topPos + 10, 0xFFFFFF);

        // 渲染配方详情区域
        renderRecipeDetail(guiGraphics);

        // 分割线（双线带阴影）
        guiGraphics.fill(leftPos + RECIPE_DETAIL_WIDTH + 5, topPos + 30, leftPos + RECIPE_DETAIL_WIDTH + 6, topPos + contentHeight - 10, 0xFF111111);
        guiGraphics.fill(leftPos + RECIPE_DETAIL_WIDTH + 6, topPos + 30, leftPos + RECIPE_DETAIL_WIDTH + 7, topPos + contentHeight - 10, 0xFF3A3A3A);

        // 右侧列表区域
        int listAreaX = leftPos + RECIPE_DETAIL_WIDTH + 20;

        if (loadError != null) {
            // 检查是否正在加载
            if (loadError.contains("正在从服务器加载") || RecipeClientCache.isLoading()) {
                float progress = SyncRecipeListPacket.getProgress();
                String progressText = String.format("正在从服务器加载配方... %.0f%%", progress * 100);
                guiGraphics.drawCenteredString(this.font, "§e" + progressText,
                        listAreaX + (contentWidth - RECIPE_DETAIL_WIDTH - 40) / 2, topPos + 30, 0xFFCC66);

                // 绘制进度条
                int barX = listAreaX;
                int barY = topPos + 50;
                int barWidth = contentWidth - RECIPE_DETAIL_WIDTH - 60;
                int barHeight = 10;
                guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000);
                guiGraphics.fill(barX + 1, barY + 1, barX + (int) ((barWidth - 2) * progress), barY + barHeight - 1, 0xFF00AA00);
            } else {
                guiGraphics.drawCenteredString(this.font, "§c错误: " + loadError,
                        listAreaX + (contentWidth - RECIPE_DETAIL_WIDTH - 40) / 2, topPos + 30, 0xFFAAAA);
                guiGraphics.drawCenteredString(this.font, "§e点击刷新按钮重试",
                        listAreaX + (contentWidth - RECIPE_DETAIL_WIDTH - 40) / 2, topPos + 105, 0xFFCC66);
            }
        } else {
            String countText = String.format("显示 %d/%d 个配方", filteredRecipes.size(), allRecipes.size());
            guiGraphics.drawString(this.font, countText, listAreaX, topPos + 30, 0xCCCCCC, false);
        }

        int listTop = topPos + 105;
        int listBottom = listTop + MAX_VISIBLE_RECIPES * RECIPE_ITEM_HEIGHT;
        int listRight = leftPos + contentWidth - 50;

        // 配方列表背景
        guiGraphics.fill(listAreaX - 11, listTop - 1, listRight + 1, listBottom + 1, 0xFF0A0A0A);
        guiGraphics.fill(listAreaX - 10, listTop, listRight, listBottom, 0xFF1A1A1A);

        if (loadError == null) {
            renderRecipeList(guiGraphics, mouseX, mouseY, listTop, listAreaX, listRight);
        } else {
            guiGraphics.drawCenteredString(this.font, "无法加载配方列表",
                    listAreaX + (listRight - listAreaX) / 2, listTop + 50, 0xCCCCCC);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderRecipeDetail(GuiGraphics guiGraphics) {
        int detailX = leftPos + 10;
        int detailY = topPos + 30;
        int detailWidth = RECIPE_DETAIL_WIDTH - 10;
        int detailHeight = contentHeight - 40;

        guiGraphics.fill(detailX - 1, detailY - 1, detailX + detailWidth + 1, detailY + detailHeight + 1, 0xFF0A0A0A);
        guiGraphics.fill(detailX, detailY, detailX + detailWidth, detailY + detailHeight, 0xFF1E1E1E);
        // 详情区标题
        guiGraphics.fill(detailX, detailY, detailX + detailWidth, detailY + 22, 0xFF1A2A45);
        guiGraphics.fill(detailX, detailY, detailX + detailWidth, detailY + 1, 0xFF3A6AAF);
        guiGraphics.drawCenteredString(this.font, "§7配方预览", detailX + detailWidth / 2, detailY + 7, 0xCCCCCC);

        if (selectedRecipeIndex >= 0 && selectedRecipeIndex < filteredRecipes.size()) {
            RecipeEntry selected = filteredRecipes.get(selectedRecipeIndex);

            int typeColor = getTypeColor(currentRecipeTypeDisplay);
            guiGraphics.drawCenteredString(this.font, currentRecipeTypeDisplay,
                    detailX + detailWidth / 2, detailY + 25, typeColor);

            if (currentResultSlot != null) {
                guiGraphics.drawString(this.font, "输出:", detailX + 10, detailY + 45, 0xCCCCCC, false);
                renderRecipeSlot(guiGraphics, currentResultSlot, true);
            }

            if (!currentRecipeSlots.isEmpty()) {
                guiGraphics.drawString(this.font, "材料:", detailX + 10, detailY + 85, 0xCCCCCC, false);

                for (PreviewSlot slot : currentRecipeSlots) {
                    renderRecipeSlot(guiGraphics, slot, false);
                }

                if (currentRecipeTypeDisplay.contains("熔炼") || currentRecipeTypeDisplay.contains("高炉") ||
                        currentRecipeTypeDisplay.contains("烟熏") || currentRecipeTypeDisplay.contains("营火")) {
                    if (currentResultSlot != null && !currentRecipeSlots.isEmpty()) {
                        PreviewSlot inputSlot = currentRecipeSlots.get(0);
                        int arrowStartX = inputSlot.x + SLOT_SIZE + 5;
                        int arrowEndX = currentResultSlot.x - 5;
                        int arrowY = inputSlot.y + SLOT_SIZE / 2;

                        guiGraphics.drawString(this.font, "→", arrowStartX + 5, arrowY - 4, 0xFFFFFF, false);
                    }
                }
            }

            String shortId = selected.recipeId.toString();
            if (shortId.length() > 30) {
                shortId = shortId.substring(0, 27) + "...";
            }
            guiGraphics.drawString(this.font, "ID:", detailX + 10, detailY + detailHeight - 40, 0xCCCCCC, false);

            // 分行显示长ID
            int maxLineWidth = detailWidth - 20;
            String[] idLines = wrapText(shortId, maxLineWidth);
            for (int i = 0; i < Math.min(idLines.length, 2); i++) {
                guiGraphics.drawString(this.font, idLines[i], detailX + 10, detailY + detailHeight - 30 + i * 10, 0xFFFFFF, false);
            }

        } else {
            guiGraphics.drawCenteredString(this.font, "选择配方以查看详情",
                    detailX + detailWidth / 2, detailY + detailHeight / 2, 0xFFAAAA);
        }
    }

    private String[] wrapText(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return new String[]{text};
        }

        List<String> lines = new ArrayList<>();
        String remaining = text;

        while (!remaining.isEmpty() && this.font.width(remaining) > maxWidth) {
            int breakPoint = remaining.length();
            while (breakPoint > 0 && this.font.width(remaining.substring(0, breakPoint)) > maxWidth) {
                breakPoint--;
            }
            if (breakPoint == 0) breakPoint = 1; // 防止无限循环

            lines.add(remaining.substring(0, breakPoint));
            remaining = remaining.substring(breakPoint);
        }

        if (!remaining.isEmpty()) {
            lines.add(remaining);
        }

        return lines.toArray(new String[0]);
    }

    private void renderRecipeSlot(GuiGraphics guiGraphics, PreviewSlot slot, boolean isResultSlot) {
        RecipePreviewRenderer.renderSlot(guiGraphics, slot, isResultSlot);
    }

    private void renderRecipeList(GuiGraphics guiGraphics, int mouseX, int mouseY, int listTop, int listAreaX, int listRight) {
        if (filteredRecipes.isEmpty()) {
            String emptyMessage = allRecipes.isEmpty() ? "没有找到任何配方" : "没有匹配的配方";
            guiGraphics.drawCenteredString(this.font, emptyMessage,
                    listAreaX + (listRight - listAreaX) / 2, listTop + 50, 0xCCCCCC);
            return;
        }

        for (int i = 0; i < MAX_VISIBLE_RECIPES && i + scrollOffset < filteredRecipes.size(); i++) {
            int recipeIndex = i + scrollOffset;
            RecipeEntry recipe = filteredRecipes.get(recipeIndex);

            int itemY = listTop + i * RECIPE_ITEM_HEIGHT;
            int itemX = listAreaX - 5;

            boolean isHovered = mouseX >= itemX && mouseX < listRight &&
                    mouseY >= itemY && mouseY < itemY + RECIPE_ITEM_HEIGHT;
            boolean isSelected = recipeIndex == selectedRecipeIndex;

            if (isSelected) {
                guiGraphics.fill(itemX, itemY, listRight, itemY + RECIPE_ITEM_HEIGHT, 0xFF1E4080);
                guiGraphics.fill(itemX, itemY, itemX + 3, itemY + RECIPE_ITEM_HEIGHT, 0xFF4A90D9); // 左侧选中条
            } else if (isHovered) {
                guiGraphics.fill(itemX, itemY, listRight, itemY + RECIPE_ITEM_HEIGHT, 0xFF2A2A3A);
            }

            try {
                if (!recipe.resultItem.isEmpty()) {
                    RenderSystem.enableDepthTest();
                    guiGraphics.renderItem(recipe.resultItem, itemX + 2, itemY + 1);
                    RenderSystem.disableDepthTest();
                } else {
                    guiGraphics.fill(itemX + 2, itemY + 1, itemX + 18, itemY + 17, 0xFF333333);
                }
            } catch (Exception e) {
                guiGraphics.fill(itemX + 2, itemY + 1, itemX + 18, itemY + 17, 0xFFAA3333);
            }

            String displayText = recipe.recipeId.toString();
            int maxTextWidth = listRight - itemX - 80;
            if (this.font.width(displayText) > maxTextWidth) {
                while (this.font.width(displayText + "...") > maxTextWidth && displayText.length() > 0) {
                    displayText = displayText.substring(0, displayText.length() - 1);
                }
                displayText += "...";
            }

            int textColor = isSelected ? 0xFFFFFF : 0xFFFFFF;
            guiGraphics.drawString(this.font, displayText, itemX + 22, itemY + 2, textColor, false);

            String typeText = "[" + recipe.recipeType + "]";
            int typeColor = getTypeColor(recipe.recipeType);
            if (isSelected) typeColor = 0xFFFFFF;
            guiGraphics.drawString(this.font, typeText, itemX + 22, itemY + 12, typeColor, false);
        }

        if (filteredRecipes.size() > MAX_VISIBLE_RECIPES) {
            int scrollBarX = leftPos + contentWidth - 45;
            int scrollBarTop = listTop + 5;
            int scrollBarHeight = MAX_VISIBLE_RECIPES * RECIPE_ITEM_HEIGHT - 10;
            guiGraphics.fill(scrollBarX, scrollBarTop, scrollBarX + 3, scrollBarTop + scrollBarHeight, 0xFF333333);
            int thumbHeight = Math.max(10, scrollBarHeight * MAX_VISIBLE_RECIPES / filteredRecipes.size());
            int thumbY = scrollBarTop + (scrollBarHeight - thumbHeight) * scrollOffset /
                    Math.max(1, filteredRecipes.size() - MAX_VISIBLE_RECIPES);
            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 3, thumbY + thumbHeight, 0xFFAAAAAA);
        }
    }

    private int getTypeColor(String recipeType) {
        return switch (recipeType) {
            case "有形状配方" -> 0x66FF66;
            case "无形状配方" -> 0x6699FF;
            case "熔炼配方" -> 0xFFAA66;
            case "高炉配方" -> 0xFF9966;
            case "烟熏配方" -> 0xFFFF66;
            case "营火烹饪" -> 0xFF6666;
            case "Avaritia配方" -> 0xFF66AA;
            case "Avaritia有形状配方" -> 0xFF99AA;
            case "Avaritia无形状配方" -> 0xCC66AA;
            default -> 0xAAAAAA;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listTop = topPos + 105;
        int listBottom = listTop + MAX_VISIBLE_RECIPES * RECIPE_ITEM_HEIGHT;
        int listAreaX = leftPos + RECIPE_DETAIL_WIDTH + 15;
        int listRight = leftPos + contentWidth - 50;
        
        int scrollBarX = leftPos + contentWidth - 45;
        int scrollBarTop = listTop + 5;
        int scrollBarHeight = MAX_VISIBLE_RECIPES * RECIPE_ITEM_HEIGHT - 10;
        
        if (mouseX >= scrollBarX && mouseX < scrollBarX + 3 &&
                mouseY >= scrollBarTop && mouseY < scrollBarTop + scrollBarHeight && !filteredRecipes.isEmpty()) {
            isDraggingScrollbar = true;
            return true;
        }

        if (mouseX >= listAreaX && mouseX < listRight &&
                mouseY >= listTop && mouseY < listBottom && !filteredRecipes.isEmpty()) {

            int clickedIndex = (int) ((mouseY - listTop) / RECIPE_ITEM_HEIGHT) + scrollOffset;

            if (clickedIndex >= 0 && clickedIndex < filteredRecipes.size()) {
                if (selectedRecipeIndex == clickedIndex && button == 0) {
                    selectRecipe();
                    return true;
                } else {
                    selectedRecipeIndex = clickedIndex;
                    updateButtons();
                    parseSelectedRecipe(); // 解析选中的配方
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar && !filteredRecipes.isEmpty()) {
            int listTop = topPos + 105;
            int scrollBarTop = listTop + 5;
            int scrollBarHeight = MAX_VISIBLE_RECIPES * RECIPE_ITEM_HEIGHT - 10;
            int maxScroll = Math.max(0, filteredRecipes.size() - MAX_VISIBLE_RECIPES);
            
            double relativeY = mouseY - scrollBarTop;
            double scrollRatio = relativeY / scrollBarHeight;
            int newScrollOffset = (int) (scrollRatio * maxScroll);
            
            newScrollOffset = Math.max(0, Math.min(maxScroll, newScrollOffset));
            
            if (newScrollOffset != scrollOffset) {
                scrollOffset = newScrollOffset;
                updateButtons();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) {
            scrollUp();
        } else {
            scrollDown();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 264 && !filteredRecipes.isEmpty()) { // DOWN
            if (selectedRecipeIndex < filteredRecipes.size() - 1) {
                selectedRecipeIndex++;
                if (selectedRecipeIndex >= scrollOffset + MAX_VISIBLE_RECIPES) {
                    scrollDown();
                }
                updateButtons();
                parseSelectedRecipe();
            }
            return true;
        } else if (keyCode == 265 && !filteredRecipes.isEmpty()) { // UP
            if (selectedRecipeIndex > 0) {
                selectedRecipeIndex--;
                if (selectedRecipeIndex < scrollOffset) {
                    scrollUp();
                }
                updateButtons();
                parseSelectedRecipe();
            }
            return true;
        } else if (keyCode == 257 && selectedRecipeIndex >= 0) { // ENTER
            selectRecipe();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void parseSelectedRecipe() {
        currentRecipeSlots.clear();
        currentResultSlot = null;
        currentRecipeTypeDisplay = "";

        if (selectedRecipeIndex < 0 || selectedRecipeIndex >= filteredRecipes.size()) {
            return;
        }

        RecipeEntry entry = filteredRecipes.get(selectedRecipeIndex);
        Recipe<?> recipe = entry.recipe;

        // 检查是否是自定义配方（registerhelper命名空间且recipe为null）
        if (recipe == null && entry.recipeId.getNamespace().equals("registerhelper")) {
            // 处理自定义配方的预览
            parseCustomRecipe(entry);
            return;
        }

        if (recipe == null) {
            return;
        }

        try {
            String recipeTypeName = recipe.getType().toString().toLowerCase();
            currentRecipeTypeDisplay = entry.recipeType;

            int detailX = leftPos + 10;
            int detailY = topPos + 60;

            // 设置结果槽位
            currentResultSlot = new PreviewSlot(detailX + RECIPE_DETAIL_WIDTH - 50, detailY + 20, entry.resultItem.copy());

            if (recipeTypeName.contains("crafting_shaped") || recipeTypeName.contains("shaped")) {
                parseShapedRecipe(recipe, detailX + 20, detailY + 100);
            } else if (recipeTypeName.contains("crafting_shapeless") || recipeTypeName.contains("shapeless")) {
                parseShapelessRecipe(recipe, detailX + 20, detailY + 100);
            } else if (recipeTypeName.contains("smelting") || recipeTypeName.contains("blasting") ||
                    recipeTypeName.contains("smoking") || recipeTypeName.contains("campfire")) {
                parseSmeltingRecipe(recipe, detailX + 20, detailY + 100);
            } else if (recipeTypeName.contains("avaritia")) {
                if (recipeTypeName.contains("shaped")) {
                    parseAvaritiaShapedRecipe(recipe, detailX + 10, detailY + 100);
                } else {
                    parseAvaritiaShapelessRecipe(recipe, detailX + 10, detailY + 100);
                }
            } else if (recipeTypeName.contains("mekanism")) {
                parseMekanismRecipe(recipe, detailX + 20, detailY + 100);
            } else {
                // 默认作为无序配方处理
                parseShapelessRecipe(recipe, detailX + 20, detailY + 100);
            }

        } catch (Exception e) {
            LOGGER.warn("解析配方失败: {}", e.getMessage());
            currentRecipeTypeDisplay = "解析失败";
        }
    }

    /**
     * 解析自定义配方（酿造台、铁砧等）
     */
    private void parseCustomRecipe(RecipeEntry entry) {
        try {
            currentRecipeTypeDisplay = entry.recipeType;

            int detailX = leftPos + 10;
            int detailY = topPos + 60;

            java.nio.file.Path jsonFile = getCustomRecipeJsonPath(entry.recipeId);

            if (jsonFile != null && java.nio.file.Files.exists(jsonFile)) {
                String content = java.nio.file.Files.readString(jsonFile);
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                
                if (json.has("result")) {
                    currentResultSlot = parseJsonSlot(json.get("result"), detailX + RECIPE_DETAIL_WIDTH - 50, detailY + 20);
                } else if (json.has("output")) {
                    currentResultSlot = parseJsonSlot(json.get("output"), detailX + RECIPE_DETAIL_WIDTH - 50, detailY + 20);
                } else {
                    currentResultSlot = new PreviewSlot(detailX + RECIPE_DETAIL_WIDTH - 50, detailY + 20, entry.resultItem.copy());
                }

                if (entry.recipeId.getPath().contains("brewing")) {
                    parseCustomBrewingRecipe(json, detailX, detailY);
                } else if (entry.recipeId.getPath().contains("anvil")) {
                    parseCustomAnvilRecipe(json, detailX, detailY);
                }
            } else {
                currentResultSlot = new PreviewSlot(detailX + RECIPE_DETAIL_WIDTH - 50, detailY + 20, entry.resultItem.copy());
                LOGGER.warn("自定义配方JSON文件不存在: {}", jsonFile);
            }

        } catch (Exception e) {
            LOGGER.warn("解析自定义配方失败: {}", entry.recipeId, e);
            currentRecipeTypeDisplay = entry.recipeType + " (无法加载详情)";
        }
    }

    /**
     * 获取自定义配方的JSON文件路径
     */
    private java.nio.file.Path getCustomRecipeJsonPath(ResourceLocation recipeId) {
        // registerhelper:custom_brewing/custom_brew → custom_recipes/brewing/custom_brew.json
        String path = recipeId.getPath(); // custom_brewing/custom_brew
        if (path.startsWith("custom_")) {
            String[] parts = path.split("/", 2);
            if (parts.length == 2) {
                String category = parts[0].replace("custom_", ""); // brewing or anvil
                String filename = parts[1] + ".json"; // custom_brew.json

                return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                        .resolve("registerhelper/custom_recipes")
                        .resolve(category)
                        .resolve(filename);
            }
        }
        return null;
    }

    /**
     * 解析自定义酿造台配方
     */
    private void parseCustomBrewingRecipe(com.google.gson.JsonObject json, int detailX, int detailY) throws Exception {
        if (json.has("input")) {
            PreviewSlot inputSlot = parseJsonSlot(json.get("input"), detailX + 20, detailY + 120);
            currentRecipeSlots.add(inputSlot);
        }

        if (json.has("ingredient")) {
            PreviewSlot ingredientSlot = parseJsonSlot(json.get("ingredient"), detailX + 60, detailY + 80);
            currentRecipeSlots.add(ingredientSlot);
        }
    }

    /**
     * 解析自定义铁砧配方
     */
    private void parseCustomAnvilRecipe(com.google.gson.JsonObject json, int detailX, int detailY) throws Exception {
        if (json.has("left")) {
            PreviewSlot leftSlot = parseJsonSlot(json.get("left"), detailX + 20, detailY + 100);
            currentRecipeSlots.add(leftSlot);
        }

        if (json.has("right")) {
            PreviewSlot rightSlot = parseJsonSlot(json.get("right"), detailX + 80, detailY + 100);
            currentRecipeSlots.add(rightSlot);
        }
    }

    /**
     * 从JSON解析ItemStack、FluidStack或ChemicalStack
     */
    private PreviewSlot parseJsonSlot(com.google.gson.JsonElement element, int x, int y) throws Exception {
        if (element.isJsonObject()) {
            com.google.gson.JsonObject obj = element.getAsJsonObject();
            
            if (obj.has("fluid")) {
                String fluidId = obj.get("fluid").getAsString();
                ResourceLocation fluidLoc = ResourceLocation.tryParse(fluidId);
                if (fluidLoc != null) {
                    net.minecraft.world.level.material.Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidLoc);
                    if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                        int amount = obj.has("amount") ? obj.get("amount").getAsInt() : 1000;
                        FluidStack fluidStack = new FluidStack(fluid, amount);
                        return new PreviewSlot(x, y, fluidStack);
                    }
                }
            }
            
            if (obj.has("gas")) {
                String gasId = obj.get("gas").getAsString();
                long amount = obj.has("amount") ? obj.get("amount").getAsLong() : 1000;
                return new PreviewSlot(x, y, gasId, RecipePreviewRenderer.ContentType.GAS, amount);
            }
            
            if (obj.has("infuse_type")) {
                String infuseTypeId = obj.get("infuse_type").getAsString();
                long amount = obj.has("amount") ? obj.get("amount").getAsLong() : 1000;
                return new PreviewSlot(x, y, infuseTypeId, RecipePreviewRenderer.ContentType.INFUSE_TYPE, amount);
            }
            
            if (obj.has("pigment")) {
                String pigmentId = obj.get("pigment").getAsString();
                long amount = obj.has("amount") ? obj.get("amount").getAsLong() : 1000;
                return new PreviewSlot(x, y, pigmentId, RecipePreviewRenderer.ContentType.PIGMENT, amount);
            }
            
            if (obj.has("slurry")) {
                String slurryId = obj.get("slurry").getAsString();
                long amount = obj.has("amount") ? obj.get("amount").getAsLong() : 1000;
                return new PreviewSlot(x, y, slurryId, RecipePreviewRenderer.ContentType.SLURRY, amount);
            }
            
            if (obj.has("item")) {
                String itemId = obj.get("item").getAsString();
                net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
                if (item != null) {
                    int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
                    return new PreviewSlot(x, y, new ItemStack(item, count));
                }
            }
        }
        return new PreviewSlot(x, y, ItemStack.EMPTY);
    }
    
    /**
     * 从JSON解析ItemStack（向后兼容）
     */
    private ItemStack parseJsonItemStack(com.google.gson.JsonElement element) throws Exception {
        if (element.isJsonObject()) {
            com.google.gson.JsonObject obj = element.getAsJsonObject();
            if (obj.has("item")) {
                String itemId = obj.get("item").getAsString();
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(new ResourceLocation(itemId));
                if (item != null) {
                    int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
                    return new ItemStack(item, count);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 计算槽位间距（通用方法）
     */
    private int getSlotSpacing(int gridSize) {
        return gridSize > 3 ?
                Math.min(SLOT_SIZE + 2, (RECIPE_DETAIL_WIDTH - 40) / gridSize) :
                SLOT_SPACING;
    }

    private void parseShapedRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int width = 3;
        int height = 3;
        
        try {
            if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shapedRecipe) {
                width = shapedRecipe.getWidth();
                height = shapedRecipe.getHeight();
            }
        } catch (Exception e) {
            int gridSize = getGridSizeFromIngredientCount(ingredients.size());
            width = gridSize;
            height = gridSize;
        }
        
        if (width == 2 && height == 2) {
            int slotSpacing = SLOT_SPACING;
            int offsetX = startX + slotSpacing;
            int offsetY = startY + slotSpacing;
            
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) {
                    int index = y * 2 + x;
                    ItemStack item = ItemStack.EMPTY;
                    
                    if (index < ingredients.size() && !ingredients.get(index).isEmpty()) {
                        ItemStack[] items = ingredients.get(index).getItems();
                        if (items.length > 0) {
                            item = items[0].copy();
                        }
                    }
                    
                    currentRecipeSlots.add(new PreviewSlot(
                            offsetX + x * slotSpacing,
                            offsetY + y * slotSpacing,
                            item
                    ));
                }
            }
            return;
        }
        
        int gridSize = Math.max(width, height);
        if (gridSize < 2) gridSize = 2;
        
        int slotSpacing = gridSize > 3 ?
                Math.min(SLOT_SIZE + 2, (RECIPE_DETAIL_WIDTH - 40) / gridSize) :
                SLOT_SPACING;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                ItemStack item = ItemStack.EMPTY;

                if (index < ingredients.size() && !ingredients.get(index).isEmpty()) {
                    ItemStack[] items = ingredients.get(index).getItems();
                    if (items.length > 0) {
                        item = items[0].copy();
                    }
                }

                currentRecipeSlots.add(new PreviewSlot(
                        startX + x * slotSpacing,
                        startY + y * slotSpacing,
                        item
                ));
            }
        }
    }

    /**
     * 根据材料数量判断网格大小
     */
    private int getGridSizeFromIngredientCount(int ingredientCount) {
        if (ingredientCount <= 9) return 3;    // 3x3
        if (ingredientCount <= 25) return 5;   // 5x5
        if (ingredientCount <= 49) return 7;   // 7x7
        if (ingredientCount <= 81) return 9;   // 9x9
        if (ingredientCount <= 121) return 11;   // 11x11
        if (ingredientCount <= 256) return 16;   // 16x16
        return 21;
    }

    private void parseShapelessRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int ingredientCount = ingredients.size();

        // 动态计算显示网格大小 (3x3, 5x5, 7x7, 9x9...)
        int gridSize = getGridSizeFromIngredientCount(ingredientCount);
        int slotSpacing = getSlotSpacing(gridSize);

        // 计算每行能放几个，自动居中
        int cols = Math.min(ingredientCount, gridSize);
        int rows = (int) Math.ceil(ingredientCount / (double) gridSize);

        // 偏移量（让材料居中）
        int xOffset = (gridSize - cols) / 2;
        int yOffset = (gridSize - rows) / 2;

        for (int i = 0; i < ingredientCount; i++) {
            int col = i % gridSize;
            int row = i / gridSize;

            // 取 Ingredient
            ItemStack item = ItemStack.EMPTY;
            Ingredient ing = ingredients.get(i);
            if (!ing.isEmpty()) {
                ItemStack[] items = ing.getItems();
                if (items.length > 0) {
                    item = items[0].copy();
                }
            }

            // 计算实际渲染位置
            int drawX = startX + (col + xOffset) * slotSpacing;
            int drawY = startY + (row + yOffset) * slotSpacing;

            currentRecipeSlots.add(new PreviewSlot(drawX, drawY, item));
        }
    }

    private void parseSmeltingRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        ItemStack item = ItemStack.EMPTY;

        if (!ingredients.isEmpty() && !ingredients.get(0).isEmpty()) {
            ItemStack[] items = ingredients.get(0).getItems();
            if (items.length > 0) {
                item = items[0].copy();
            }
        }

        // 单个槽位用于熔炼原料
        currentRecipeSlots.add(new PreviewSlot(startX + SLOT_SPACING, startY + SLOT_SPACING, item));
    }

    private void parseAvaritiaShapedRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int gridSize = getAvaritiaGridSizeFromIngredientCount(ingredients.size());

        // 缩小槽位间距以适应详情区域
        int slotSpacing = Math.min(SLOT_SIZE + 2, (RECIPE_DETAIL_WIDTH - 40) / gridSize);

        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                int index = y * gridSize + x;
                ItemStack item = ItemStack.EMPTY;

                if (index < ingredients.size() && !ingredients.get(index).isEmpty()) {
                    ItemStack[] items = ingredients.get(index).getItems();
                    if (items.length > 0) {
                        item = items[0].copy();
                    }
                }

                currentRecipeSlots.add(new PreviewSlot(
                        startX + x * slotSpacing,
                        startY + y * slotSpacing,
                        item
                ));
            }
        }
    }

    private void parseAvaritiaShapelessRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int gridSize = getAvaritiaGridSizeFromIngredientCount(ingredients.size());

        // 缩小槽位间距以适应详情区域
        int slotSpacing = Math.min(SLOT_SIZE + 2, (RECIPE_DETAIL_WIDTH - 40) / gridSize);

        for (int i = 0; i < Math.min(ingredients.size(), gridSize * gridSize); i++) {
            int x = i % gridSize;
            int y = i / gridSize;
            ItemStack item = ItemStack.EMPTY;

            if (!ingredients.get(i).isEmpty()) {
                ItemStack[] items = ingredients.get(i).getItems();
                if (items.length > 0) {
                    item = items[0].copy();
                }
            }

            currentRecipeSlots.add(new PreviewSlot(
                    startX + x * slotSpacing,
                    startY + y * slotSpacing,
                    item
            ));
        }
    }
    
    /**
     * 解析Mekanism配方
     */
    private void parseMekanismRecipe(Recipe<?> recipe, int startX, int startY) {
        try {
            int slotX = startX;
            int slotY = startY;
            
            String recipeTypeName = recipe.getType().toString().toLowerCase();
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
                            if (output instanceof Long energyOutput) {
                                int detailX = leftPos + 10;
                                int detailY = topPos + 60;
                                currentResultSlot = new PreviewSlot(detailX + RECIPE_DETAIL_WIDTH - 50, detailY + 20, energyOutput);
                            }
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
                            
                            String chemicalClassName = chemical.getClass().getName();
                            if (chemicalClassName.contains("InfuseType")) {
                                mekanism.api.chemical.infuse.InfuseType infuseType = (mekanism.api.chemical.infuse.InfuseType) chemical;
                                chemicalId = mekanism.api.MekanismAPI.infuseTypeRegistry().getKey(infuseType);
                            }
                            
                            if (chemicalId != null) {
                                int detailX = leftPos + 10;
                                int detailY = topPos + 60;
                                int resultX = detailX + RECIPE_DETAIL_WIDTH - 50;
                                int resultY = detailY + 20;
                                currentResultSlot = new PreviewSlot(resultX, resultY, chemicalId.toString(), RecipePreviewRenderer.ContentType.INFUSE_TYPE, amount);
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
                    
                    int detailX = leftPos + 10;
                    int detailY = topPos + 60;
                    
                    Object mainOutput = getMainOutputDefinition.invoke(sawmillRecipe);
                    Object secondaryOutput = getSecondaryOutputDefinition.invoke(sawmillRecipe);
                    
                    if (mainOutput instanceof List<?> mainList && !mainList.isEmpty()) {
                        Object output = mainList.get(0);
                        if (output instanceof net.minecraft.world.item.ItemStack itemStack && !itemStack.isEmpty()) {
                            currentResultSlot = new PreviewSlot(detailX + RECIPE_DETAIL_WIDTH - 50, detailY + 20, itemStack.copy());
                        }
                    }
                    
                    if (secondaryOutput instanceof List<?> secondaryList && !secondaryList.isEmpty()) {
                        Object output = secondaryList.get(0);
                        if (output instanceof net.minecraft.world.item.ItemStack itemStack && !itemStack.isEmpty()) {
                            currentRecipeSlots.add(new PreviewSlot(detailX + RECIPE_DETAIL_WIDTH - 50, detailY + 50, itemStack.copy()));
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
                    
                    int detailX = leftPos + 10;
                    int detailY = topPos + 60;
                    
                    Object outputDef = getOutputDefinition.invoke(pressurizedRecipe);
                    
                    if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                        Object outputObj = outputList.get(0);
                        
                        try {
                            java.lang.reflect.Method itemMethod = outputObj.getClass().getMethod("item");
                            java.lang.reflect.Method gasMethod = outputObj.getClass().getMethod("gas");
                            
                            Object itemOutput = itemMethod.invoke(outputObj);
                            Object gasOutput = gasMethod.invoke(outputObj);
                            
                            int resultBaseX = detailX + RECIPE_DETAIL_WIDTH - 50;
                            int resultY = detailY + 20;
                            
                            if (itemOutput instanceof net.minecraft.world.item.ItemStack itemStack && !itemStack.isEmpty()) {
                                currentResultSlot = new PreviewSlot(resultBaseX, resultY, itemStack.copy());
                            }
                            
                            if (gasOutput instanceof mekanism.api.chemical.gas.GasStack gasStack && !gasStack.isEmpty()) {
                                ResourceLocation gasId = mekanism.api.MekanismAPI.gasRegistry().getKey(gasStack.getType());
                                if (gasId != null) {
                                    int gasResultX = resultBaseX - SLOT_SPACING;
                                    int gasResultY = resultY - (RecipePreviewRenderer.SMALL_SLOT_HEIGHT - RecipePreviewRenderer.SLOT_SIZE) / 2;
                                    currentRecipeSlots.add(new PreviewSlot(gasResultX, gasResultY, gasId.toString(), 
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
                                Class<?> inputClass = input.getClass();
                                try {
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
                                            currentRecipeSlots.add(new PreviewSlot(slotX, slotY, chemicalId.toString(), chemicalType, amount));
                                            slotX += SLOT_SPACING;
                                        }
                                    }
                                } catch (Exception e) {
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
                                        currentRecipeSlots.add(new PreviewSlot(slotX, slotY, chemicalId.toString(), chemicalType, amount));
                                        slotX += SLOT_SPACING;
                                    }
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                    
                    if (getOutput != null) {
                        try {
                            Object output = getOutput.invoke(recipe);
                            if (output instanceof net.minecraft.world.item.ItemStack itemStack) {
                                int detailX = leftPos + 10;
                                int detailY = topPos + 60;
                                currentResultSlot = new PreviewSlot(detailX + RECIPE_DETAIL_WIDTH - 50, detailY + 20, itemStack.copy());
                            } else if (output instanceof FluidStack fluidStack) {
                                int detailX = leftPos + 10;
                                int detailY = topPos + 60;
                                int resultX = detailX + RECIPE_DETAIL_WIDTH - 50;
                                int resultY = detailY + 20 - (RecipePreviewRenderer.SMALL_SLOT_HEIGHT - RecipePreviewRenderer.SLOT_SIZE) / 2;
                                currentResultSlot = new PreviewSlot(resultX, resultY, fluidStack.copy());
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
                                    int detailX = leftPos + 10;
                                    int detailY = topPos + 60;
                                    int resultX = detailX + RECIPE_DETAIL_WIDTH - 50;
                                    int resultY = detailY + 20 - (RecipePreviewRenderer.SMALL_SLOT_HEIGHT - RecipePreviewRenderer.SLOT_SIZE) / 2;
                                    currentResultSlot = new PreviewSlot(resultX, resultY, chemicalId.toString(), chemicalType, amount);
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                    
                    if (getOutputDefinition != null) {
                        try {
                            Object outputDef = getOutputDefinition.invoke(recipe);
                            if (outputDef instanceof List<?> outputList && !outputList.isEmpty()) {
                                Object output = outputList.get(0);
                                if (output instanceof mekanism.api.chemical.merged.BoxedChemicalStack boxedStack) {
                                    mekanism.api.chemical.ChemicalStack<?> chemicalStack = boxedStack.getChemicalStack();
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
                                        int detailX = leftPos + 10;
                                        int detailY = topPos + 60;
                                        int resultX = detailX + RECIPE_DETAIL_WIDTH - 50;
                                        int resultY = detailY + 20 - (RecipePreviewRenderer.SMALL_SLOT_HEIGHT - RecipePreviewRenderer.SLOT_SIZE) / 2;
                                        currentResultSlot = new PreviewSlot(resultX, resultY, 
                                                chemicalId.toString(), chemicalType, amount);
                                    }
                                } else if (output instanceof FluidStack fluidStack) {
                                    int detailX = leftPos + 10;
                                    int detailY = topPos + 60;
                                    int resultX = detailX + RECIPE_DETAIL_WIDTH - 50;
                                    int resultY = detailY + 20 - (RecipePreviewRenderer.SMALL_SLOT_HEIGHT - RecipePreviewRenderer.SLOT_SIZE) / 2;
                                    currentResultSlot = new PreviewSlot(resultX, resultY, fluidStack.copy());
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
                                        int detailX = leftPos + 10;
                                        int detailY = topPos + 60;
                                        int resultX = detailX + RECIPE_DETAIL_WIDTH - 50;
                                        int resultY = detailY + 20 - (RecipePreviewRenderer.SMALL_SLOT_HEIGHT - RecipePreviewRenderer.SLOT_SIZE) / 2;
                                        currentResultSlot = new PreviewSlot(resultX, resultY, chemicalId.toString(), chemicalType, amount);
                                    }
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
            int slotY = startY;
            
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
    }

    private int getAvaritiaGridSizeFromIngredientCount(int ingredientCount) {
        if (ingredientCount <= 9) return 3;
        if (ingredientCount <= 25) return 5;
        if (ingredientCount <= 49) return 7;
        return 9;
    }

    /**
     * @param recipe 添加原始配方引用
     */
    private record RecipeEntry(ResourceLocation recipeId, ItemStack resultItem, String recipeType, Recipe<?> recipe) {
    }
}
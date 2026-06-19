package dev.whisperlyric.ingamerecipeeditor.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.disabled.DisabledRecipesManager;
import dev.whisperlyric.ingamerecipeeditor.network.NetworkHandler;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.Internal;
import mezz.jei.library.focus.FocusGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 禁用配方列表界面
 * 复刻自src-old的BlacklistManagerScreen，适配当前模组的禁用配方系统
 * 每条配方左侧显示输出物品的代表图标
 * Shift+悬停预览配方详细内容
 */
@OnlyIn(Dist.CLIENT)
public class DisabledRecipesListScreen extends Screen {
    private final Screen parent;

    private List<RecipeEntry> allDisabledRecipes;
    private List<RecipeEntry> filteredRecipes;
    private EditBox searchBox;
    private Button enableButton;
    private Button clearAllButton;
    private Button closeButton;

    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private final int itemHeight = 22;
    private final int visibleItems = 12;
    private final int iconSize = 16;

    // JEI预览
    private IRecipeLayoutDrawable<?> previewLayout;
    private int hoveredIndex = -1;

    /**
     * 配方条目，包含ID和代表物品
     */
    private record RecipeEntry(ResourceLocation id, ItemStack representativeItem) {}

    public DisabledRecipesListScreen(Screen parent) {
        super(Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list"));
        this.parent = parent;
        refreshData();
    }

    private void refreshData() {
        Set<String> disabledIds = DisabledRecipesManager.getDisabledRecipes();
        Map<String, String> jsonCache = DisabledRecipesManager.getClientRecipeJsonCache();

        this.allDisabledRecipes = new ArrayList<>();
        for (String id : disabledIds) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                ItemStack representative = extractRepresentativeItem(id, jsonCache.get(id));
                allDisabledRecipes.add(new RecipeEntry(rl, representative));
            }
        }
        this.allDisabledRecipes.sort(Comparator.comparing(e -> e.id.toString()));
        this.filteredRecipes = new ArrayList<>(allDisabledRecipes);
        this.selectedIndex = -1;
    }

    // ==================== 物品提取 ====================

    private ItemStack extractRepresentativeItem(String recipeId, String recipeJson) {
        if (recipeJson == null || recipeJson.isEmpty()) return ItemStack.EMPTY;
        try {
            JsonObject json = JsonParser.parseString(recipeJson).getAsJsonObject();
            ItemStack result = extractItemFromResult(json);
            if (!result.isEmpty()) return result;
            ItemStack ingredient = extractFirstIngredientItem(json);
            if (!ingredient.isEmpty()) return ingredient;
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.debug("解析配方代表物品失败 {}: {}", recipeId, e.getMessage());
        }
        return ItemStack.EMPTY;
    }

    private ItemStack extractItemFromResult(JsonObject json) {
        if (json.has("result")) {
            JsonElement resultElement = json.get("result");
            if (resultElement.isJsonObject()) return parseItemFromJson(resultElement.getAsJsonObject());
            else if (resultElement.isJsonPrimitive()) return resolveItem(resultElement.getAsString());
        }
        if (json.has("output")) {
            JsonElement outputElement = json.get("output");
            if (outputElement.isJsonObject()) return parseItemFromJson(outputElement.getAsJsonObject());
            else if (outputElement.isJsonPrimitive()) return resolveItem(outputElement.getAsString());
        }
        if (json.has("results")) {
            JsonElement resultsElement = json.get("results");
            if (resultsElement.isJsonArray() && !resultsElement.getAsJsonArray().isEmpty()) {
                JsonElement first = resultsElement.getAsJsonArray().get(0);
                if (first.isJsonObject()) return parseItemFromJson(first.getAsJsonObject());
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack extractFirstIngredientItem(JsonObject json) {
        if (json.has("ingredient")) {
            ItemStack item = extractItemFromIngredient(json.get("ingredient"));
            if (!item.isEmpty()) return item;
        }
        if (json.has("ingredients")) {
            JsonElement ings = json.get("ingredients");
            if (ings.isJsonArray()) {
                for (JsonElement ing : ings.getAsJsonArray()) {
                    ItemStack item = extractItemFromIngredient(ing);
                    if (!item.isEmpty()) return item;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack extractItemFromIngredient(JsonElement ingredientElement) {
        if (ingredientElement.isJsonObject()) {
            JsonObject ingObj = ingredientElement.getAsJsonObject();
            if (ingObj.has("item")) return resolveItem(ingObj.get("item").getAsString());
            for (String key : new String[]{"items", "values"}) {
                if (ingObj.has(key) && ingObj.get(key).isJsonArray()) {
                    for (JsonElement elem : ingObj.get(key).getAsJsonArray()) {
                        if (elem.isJsonPrimitive()) {
                            ItemStack item = resolveItem(elem.getAsString());
                            if (!item.isEmpty()) return item;
                        } else if (elem.isJsonObject() && elem.getAsJsonObject().has("item")) {
                            ItemStack item = resolveItem(elem.getAsJsonObject().get("item").getAsString());
                            if (!item.isEmpty()) return item;
                        }
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack parseItemFromJson(JsonObject obj) {
        if (obj.has("item")) {
            ItemStack item = resolveItem(obj.get("item").getAsString());
            if (!item.isEmpty() && obj.has("count")) {
                item.setCount(Math.min(obj.get("count").getAsInt(), item.getMaxStackSize()));
            }
            return item;
        }
        if (obj.has("id")) return resolveItem(obj.get("id").getAsString());
        return ItemStack.EMPTY;
    }

    private ItemStack resolveItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return ItemStack.EMPTY;
        var item = ForgeRegistries.ITEMS.getValue(rl);
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    // ==================== JEI预览 ====================

    /**
     * 尝试为指定配方创建JEI布局预览
     * 配方被Mixin在加载阶段移除，JEI从未加载过，所以需要：
     * 1. 从JSON缓存或MC RecipeManager获取配方对象
     * 2. 临时注入到JEI中（addRecipes）
     * 3. 创建布局
     * 4. 隐藏注入的配方（hideRecipes）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private IRecipeLayoutDrawable<?> createPreviewLayout(ResourceLocation recipeId) {
        try {
            var runtime = Internal.getJeiRuntime();
            if (runtime == null) return null;

            IRecipeManager recipeManager = runtime.getRecipeManager();
            if (recipeManager == null) return null;

            // 1. 获取配方对象（优先MC RecipeManager，回退JSON缓存）
            Recipe<?> recipe = null;
            if (minecraft != null && minecraft.level != null) {
                var holder = minecraft.level.getRecipeManager().byKey(recipeId);
                if (holder.isPresent()) {
                    recipe = holder.get();
                }
            }
            if (recipe == null) {
                Map<String, String> jsonCache = DisabledRecipesManager.getClientRecipeJsonCache();
                String recipeJson = jsonCache.get(recipeId.toString());
                if (recipeJson != null && !recipeJson.isEmpty()) {
                    try {
                        JsonElement jsonElement = JsonParser.parseString(recipeJson);
                        recipe = RecipeManager.fromJson(recipeId, jsonElement.getAsJsonObject());
                    } catch (Exception e) {
                        InGameRecipeEditor.LOGGER.warn("从JSON缓存解析配方失败 {}: {}", recipeId, e.getMessage());
                    }
                }
            }
            if (recipe == null) {
                InGameRecipeEditor.LOGGER.warn("无法获取配方对象: {}", recipeId);
                return null;
            }

            // 2. 查找匹配的JEI分类
            List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();
            IRecipeCategory<?> matchedCategory = null;
            for (IRecipeCategory<?> category : categories) {
                RecipeType<?> recipeType = category.getRecipeType();
                if (!recipeType.getRecipeClass().isInstance(recipe)) continue;
                try {
                    if (!((IRecipeCategory) category).isHandled(recipe)) continue;
                } catch (Exception e) {
                    continue;
                }
                matchedCategory = category;
                break;
            }
            if (matchedCategory == null) {
                InGameRecipeEditor.LOGGER.warn("未找到匹配的JEI分类 for {}", recipeId);
                return null;
            }

            // 3. 临时注入配方到JEI，创建布局，然后隐藏
            RecipeType recipeType = matchedCategory.getRecipeType();
            try {
                // 注入配方
                recipeManager.addRecipes(recipeType, List.of(recipe));
                // 创建布局
                Optional<IRecipeLayoutDrawable<?>> layout = (Optional<IRecipeLayoutDrawable<?>>) (Optional<?>) recipeManager.createRecipeLayoutDrawable(
                    (IRecipeCategory) matchedCategory, recipe, FocusGroup.EMPTY);
                if (layout.isPresent()) {
                    return layout.get();
                }
            } catch (Exception e) {
                InGameRecipeEditor.LOGGER.error("临时注入配方创建布局失败 {}: {}", recipeId, e.getMessage());
            } finally {
                // 隐藏注入的配方（清理）
                try {
                    recipeManager.hideRecipes(recipeType, List.of(recipe));
                } catch (Exception ignored) {}
            }

            InGameRecipeEditor.LOGGER.warn("创建配方布局失败: {}", recipeId);
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.error("创建JEI预览布局失败 {}: {}", recipeId, e.getMessage());
        }
        return null;
    }

    // ==================== UI初始化 ====================

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int topY = 40;

        searchBox = new EditBox(this.font, centerX - 200, topY + 4, 400, 20, Component.literal("搜索"));
        searchBox.setHint(Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list.search_hint"));
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        int buttonY = this.height - 40;

        enableButton = addRenderableWidget(Button.builder(
                        Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list.enable_selected"),
                        button -> enableSelectedRecipe())
                .bounds(centerX - 160, buttonY, 70, 20)
                .build());
        enableButton.active = false;

        clearAllButton = addRenderableWidget(Button.builder(
                        Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list.clear_all"),
                        button -> confirmClearAll())
                .bounds(centerX - 80, buttonY, 70, 20)
                .build());

        closeButton = addRenderableWidget(Button.builder(
                        Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list.close"),
                        button -> minecraft.setScreen(parent))
                .bounds(centerX + 10, buttonY, 50, 20)
                .build());
    }

    private void onSearchChanged(String searchText) {
        filteredRecipes.clear();
        selectedIndex = -1;
        enableButton.active = false;
        scrollOffset = 0;

        String lowerSearch = searchText.toLowerCase();
        if (lowerSearch.isEmpty()) {
            filteredRecipes.addAll(allDisabledRecipes);
        } else {
            for (RecipeEntry entry : allDisabledRecipes) {
                if (entry.id.toString().toLowerCase().contains(lowerSearch) ||
                        entry.id.getNamespace().toLowerCase().contains(lowerSearch) ||
                        entry.id.getPath().toLowerCase().contains(lowerSearch)) {
                    filteredRecipes.add(entry);
                }
            }
        }
    }

    private void enableSelectedRecipe() {
        if (selectedIndex >= 0 && selectedIndex < filteredRecipes.size()) {
            ResourceLocation recipeId = filteredRecipes.get(selectedIndex).id;
            NetworkHandler.sendRecipeToggle(recipeId.toString(), false);
            refreshData();
            onSearchChanged(searchBox.getValue());
        }
    }

    private void confirmClearAll() {
        if (allDisabledRecipes.isEmpty()) {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(
                    Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list.already_empty"));
            }
            return;
        }
        minecraft.setScreen(new ConfirmClearAllScreen(this, allDisabledRecipes.size(), this::performClearAll));
    }

    private void performClearAll() {
        if (!allDisabledRecipes.isEmpty()) {
            // 批量启用所有禁用配方，服务器只发送一次合并的reload提示
            List<String> recipeIds = new ArrayList<>(allDisabledRecipes.size());
            for (RecipeEntry entry : allDisabledRecipes) {
                recipeIds.add(entry.id.toString());
            }
            NetworkHandler.sendRecipeBatchToggle(recipeIds, false);
        }
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(
                Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list.clearing"));
        }
        refreshData();
        onSearchChanged(searchBox.getValue());
    }

    // ==================== 渲染 ====================

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int listTop = 70;
        int listBottom = this.height - 60;

        // 面板背景（与标签预览界面相同）
        int px = centerX - 252, py = 5, pw = 504, ph = this.height - 10;
        guiGraphics.fill(px, py, px + pw, py + ph, 0xFFC6C6C6);
        guiGraphics.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, 0xFF8B8B8B);

        // 标题栏（深红色）
        guiGraphics.fill(px + 1, py + 1, px + pw - 1, py + 26, 0xFF8B2020);
        guiGraphics.fill(px + 1, py + 1, px + pw - 1, py + 2, 0xFFCF4A4A);
        guiGraphics.fill(px + 1, py + 25, px + pw - 1, py + 26, 0xFF601515);

        guiGraphics.drawCenteredString(this.font,
            Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list").getString(),
            centerX, py + 9, 0xFFFFFF);

        // 统计
        String statsText = String.format(
            Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list.stats",
                allDisabledRecipes.size(), filteredRecipes.size()).getString());
        guiGraphics.drawCenteredString(this.font, statsText, centerX, 33, 0xCCCCCC);

        // 列表背景
        guiGraphics.fill(centerX - 250, listTop, centerX + 250, listBottom, 0xFF8B8B8B);

        // 渲染配方列表
        renderRecipeList(guiGraphics, mouseX, mouseY, centerX - 240, listTop + 5, 480, listBottom - listTop - 10);

        // 滚动条
        if (filteredRecipes.size() > visibleItems) {
            renderScrollbar(guiGraphics, centerX + 240, listTop + 5, listBottom - listTop - 10);
        }

        // 悬浮提示
        renderHoverTooltip(guiGraphics, mouseX, mouseY, centerX - 240, listTop + 5);

        // Shift+悬停JEI预览（暂时禁用：配方被Mixin移除且JSON缓存同步问题未完全解决）
        // renderShiftPreview(guiGraphics, mouseX, mouseY, centerX - 240, listTop + 5, 480);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderRecipeList(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                  int x, int y, int width, int height) {
        int maxScroll = Math.max(0, filteredRecipes.size() - visibleItems);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        scrollOffset = Math.max(scrollOffset, 0);

        hoveredIndex = -1;

        for (int i = 0; i < Math.min(visibleItems, filteredRecipes.size()); i++) {
            int index = i + scrollOffset;
            if (index >= filteredRecipes.size()) break;

            RecipeEntry entry = filteredRecipes.get(index);
            int itemY = y + i * itemHeight;

            boolean isSelected = (index == selectedIndex);
            boolean isHovered = mouseX >= x && mouseX < x + width &&
                    mouseY >= itemY && mouseY < itemY + itemHeight;

            if (isHovered) hoveredIndex = index;

            // 背景
            if (isSelected) {
                guiGraphics.fill(x, itemY, x + width, itemY + itemHeight, 0xFF4040A0);
            } else if (isHovered) {
                guiGraphics.fill(x, itemY, x + width, itemY + itemHeight, 0xFF606060);
            } else if (i % 2 == 1) {
                guiGraphics.fill(x, itemY, x + width, itemY + itemHeight, 0xFF787878);
            }

            // 代表物品图标
            int iconX = x + 3;
            int iconY = itemY + (itemHeight - iconSize) / 2;
            if (!entry.representativeItem.isEmpty()) {
                guiGraphics.renderItem(entry.representativeItem, iconX, iconY);
            } else {
                guiGraphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF555555);
                guiGraphics.drawCenteredString(this.font, "?", iconX + iconSize / 2, iconY + 4, 0xAAAAAA);
            }

            // 配方ID
            String recipeText = entry.id.toString();
            int textX = x + iconSize + 8;
            int maxTextWidth = width - iconSize - 12;
            if (this.font.width(recipeText) > maxTextWidth) {
                while (recipeText.length() > 3 && this.font.width(recipeText + "...") > maxTextWidth) {
                    recipeText = recipeText.substring(0, recipeText.length() - 1);
                }
                recipeText += "...";
            }

            int textColor = getNamespaceColor(entry.id.getNamespace());
            guiGraphics.drawString(this.font, recipeText, textX, itemY + (itemHeight - 8) / 2, textColor, false);
        }

        // 空列表提示
        if (filteredRecipes.isEmpty()) {
            String emptyKey = allDisabledRecipes.isEmpty() ?
                "ingamerecipeeditor.screen.disabled_recipes_list.empty" :
                "ingamerecipeeditor.screen.disabled_recipes_list.no_match";
            guiGraphics.drawCenteredString(this.font,
                Component.translatable(emptyKey).getString(), x + width / 2, y + height / 2, 0xAAAAAA);
        }
    }

    /**
     * Shift+悬停时渲染JEI配方预览
     */
    private void renderShiftPreview(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                    int x, int y, int width) {
        if (!hasShiftDown() || hoveredIndex < 0 || hoveredIndex >= filteredRecipes.size()) {
            previewLayout = null;
            return;
        }

        RecipeEntry entry = filteredRecipes.get(hoveredIndex);

        // 创建或复用预览布局
        if (previewLayout == null || !entry.id.toString().equals(getLayoutRecipeId(previewLayout))) {
            previewLayout = createPreviewLayout(entry.id);
        }

        if (previewLayout != null) {
            // 获取布局尺寸
            Rect2i layoutRect = previewLayout.getRect();
            int layoutWidth = layoutRect.getWidth();
            int layoutHeight = layoutRect.getHeight();

            // 计算预览位置（鼠标右下方）
            int previewX = mouseX + 12;
            int previewY = mouseY + 12;

            // 边界检查
            if (previewX + layoutWidth + 10 > this.width) {
                previewX = mouseX - layoutWidth - 12;
            }
            if (previewY + layoutHeight + 10 > this.height) {
                previewY = mouseY - layoutHeight - 12;
            }
            // 确保不超出左上边界
            previewX = Math.max(2, previewX);
            previewY = Math.max(2, previewY);

            // 使用JEI的九宫格背景纹理（与工作区相同）
            int bgPadding = 8;
            int panelX = previewX - bgPadding;
            int panelY = previewY - bgPadding;
            int panelWidth = layoutWidth + bgPadding * 2;
            int panelHeight = layoutHeight + bgPadding * 2;
            try {
                mezz.jei.common.gui.textures.Textures textures = Internal.getTextures();
                mezz.jei.common.gui.elements.DrawableNineSliceTexture backgroundTexture = textures.getRecipeBackground();
                backgroundTexture.draw(guiGraphics, panelX, panelY, panelWidth, panelHeight);
            } catch (Exception e) {
                // 回退到简单背景
                guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFFC6C6C6);
                guiGraphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1, 0xFF8B8B8B);
            }

            // 设置布局位置并渲染（drawRecipe内部会使用setPosition的位置）
            previewLayout.setPosition(previewX, previewY);
            previewLayout.drawRecipe(guiGraphics, mouseX, mouseY);
            // 渲染叠加层（箭头、进度条等）
            previewLayout.drawOverlays(guiGraphics, mouseX, mouseY);

            // 配方ID标题
            String idText = entry.id.toString();
            if (this.font.width(idText) > panelWidth) {
                while (idText.length() > 3 && this.font.width(idText + "...") > panelWidth) {
                    idText = idText.substring(0, idText.length() - 1);
                }
                idText += "...";
            }
            guiGraphics.drawCenteredString(this.font, idText,
                previewX + layoutWidth / 2,
                panelY + panelHeight + 2,
                0xFFFFFF);
        } else {
            // 无JEI布局时显示JSON摘要
            renderJsonPreview(guiGraphics, mouseX, mouseY, entry);
        }
    }

    /**
     * 无JEI布局时，渲染配方JSON摘要
     */
    private void renderJsonPreview(GuiGraphics guiGraphics, int mouseX, int mouseY, RecipeEntry entry) {
        Map<String, String> jsonCache = DisabledRecipesManager.getClientRecipeJsonCache();
        String json = jsonCache.get(entry.id.toString());
        if (json == null) return;

        List<String> lines = new ArrayList<>();
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("type")) lines.add("Type: " + obj.get("type").getAsString());
            if (obj.has("group") && !obj.get("group").getAsString().isEmpty())
                lines.add("Group: " + obj.get("group").getAsString());

            // 简要输出信息
            if (obj.has("result")) {
                String resultStr = summarizeResult(obj.get("result"));
                if (resultStr != null) lines.add("Result: " + resultStr);
            }
            if (obj.has("output")) {
                String resultStr = summarizeResult(obj.get("output"));
                if (resultStr != null) lines.add("Output: " + resultStr);
            }
        } catch (Exception e) {
            lines.add("(parse error)");
        }

        if (lines.isEmpty()) return;

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, this.font.width(line));
        }

        int previewX = mouseX + 12;
        int previewY = mouseY + 12;
        int boxWidth = maxWidth + 10;
        int boxHeight = lines.size() * 12 + 8;

        if (previewX + boxWidth > this.width) previewX = mouseX - boxWidth - 12;
        if (previewY + boxHeight > this.height) previewY = mouseY - boxHeight - 12;

        guiGraphics.fill(previewX - 1, previewY - 1, previewX + boxWidth + 1, previewY + boxHeight + 1, 0xFF333333);
        guiGraphics.fill(previewX, previewY, previewX + boxWidth, previewY + boxHeight, 0xFFD8D8D8);

        for (int i = 0; i < lines.size(); i++) {
            guiGraphics.drawString(this.font, lines.get(i), previewX + 5, previewY + 4 + i * 12, 0x333333, false);
        }
    }

    private String summarizeResult(JsonElement resultElement) {
        if (resultElement.isJsonPrimitive()) return resultElement.getAsString();
        if (resultElement.isJsonObject()) {
            JsonObject obj = resultElement.getAsJsonObject();
            if (obj.has("item")) return obj.get("item").getAsString();
            if (obj.has("id")) return obj.get("id").getAsString();
            if (obj.has("count")) return "x" + obj.get("count").getAsInt();
        }
        return null;
    }

    private String getLayoutRecipeId(IRecipeLayoutDrawable<?> layout) {
        try {
            Object recipe = layout.getRecipe();
            if (recipe instanceof Recipe<?> mcRecipe) return mcRecipe.getId().toString();
        } catch (Exception ignored) {}
        return null;
    }

    private void renderHoverTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                    int x, int y) {
        for (int i = 0; i < Math.min(visibleItems, filteredRecipes.size()); i++) {
            int index = i + scrollOffset;
            if (index >= filteredRecipes.size()) break;

            int itemY = y + i * itemHeight;
            int iconX = x + 3;
            int iconY = itemY + (itemHeight - iconSize) / 2;

            if (mouseX >= iconX && mouseX < iconX + iconSize &&
                mouseY >= iconY && mouseY < iconY + iconSize) {
                RecipeEntry entry = filteredRecipes.get(index);
                if (!entry.representativeItem.isEmpty()) {
                    guiGraphics.renderTooltip(this.font, entry.representativeItem, mouseX, mouseY);
                }
                return;
            }
        }
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int x, int y, int height) {
        if (filteredRecipes.size() <= visibleItems) return;

        guiGraphics.fill(x, y, x + 6, y + height, 0xFF555555);

        int maxScroll = filteredRecipes.size() - visibleItems;
        int thumbHeight = Math.max(10, height * visibleItems / filteredRecipes.size());
        int thumbY = y + (height - thumbHeight) * scrollOffset / maxScroll;

        guiGraphics.fill(x + 1, thumbY, x + 5, thumbY + thumbHeight, 0xFFAAAAAA);
    }

    private int getNamespaceColor(String namespace) {
        // 深色背景上使用亮色文字
        return switch (namespace) {
            case "minecraft" -> 0xFF7BFF7B; // 亮绿
            case "ingamerecipeeditor" -> 0xFFFF7B7B; // 亮红
            default -> 0xFFFFCC66; // 亮黄
        };
    }

    // ==================== 输入处理 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        int listTop = 70;
        int listBottom = this.height - 60;
        int listX = centerX - 240;
        int listWidth = 480;

        if (mouseX >= listX && mouseX < listX + listWidth &&
                mouseY >= listTop + 5 && mouseY < listBottom - 5) {
            int clickedIndex = (int) ((mouseY - listTop - 5) / itemHeight) + scrollOffset;
            if (clickedIndex >= 0 && clickedIndex < filteredRecipes.size()) {
                selectedIndex = clickedIndex;
                enableButton.active = true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (filteredRecipes.size() > visibleItems) {
            scrollOffset -= (int) (delta * 3);
            int maxScroll = filteredRecipes.size() - visibleItems;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            minecraft.setScreen(parent);
            return true;
        }
        if (keyCode == 46 || keyCode == 261) {
            if (selectedIndex >= 0) {
                enableSelectedRecipe();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 确认对话框 ====================

    private static class ConfirmClearAllScreen extends Screen {
        private final DisabledRecipesListScreen parent;
        private final int recipeCount;
        private final Runnable onConfirm;

        public ConfirmClearAllScreen(DisabledRecipesListScreen parent, int recipeCount, Runnable onConfirm) {
            super(Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list.confirm_clear"));
            this.parent = parent;
            this.recipeCount = recipeCount;
            this.onConfirm = onConfirm;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            addRenderableWidget(Button.builder(
                            Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list.confirm"),
                            button -> {
                                onConfirm.run();
                                minecraft.setScreen(parent);
                            })
                    .bounds(centerX - 70, centerY + 20, 60, 20)
                    .build());

            addRenderableWidget(Button.builder(
                            Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list.cancel"),
                            button -> minecraft.setScreen(parent))
                    .bounds(centerX + 10, centerY + 20, 60, 20)
                    .build());
        }

        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(guiGraphics);

            int centerX = this.width / 2;
            int centerY = this.height / 2;

            guiGraphics.fill(centerX - 150, centerY - 50, centerX + 150, centerY + 60, 0xFFC6C6C6);
            guiGraphics.fill(centerX - 149, centerY - 49, centerX + 149, centerY + 59, 0xFF8B8B8B);

            guiGraphics.drawCenteredString(this.font,
                Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list.confirm_clear_title").getString(),
                centerX, centerY - 35, 0xFFFF5555);
            guiGraphics.drawCenteredString(this.font,
                Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list.confirm_clear_count", recipeCount).getString(),
                centerX, centerY - 15, 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font,
                Component.translatable("ingamerecipeeditor.screen.disabled_recipes_list.confirm_clear_warning").getString(),
                centerX, centerY + 5, 0xFFFFCC00);

            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
